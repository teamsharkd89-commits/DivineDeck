package com.divinedeck.launcher.feature.voice.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.navcenter.domain.QuickPlacesRepository
import com.divinedeck.launcher.feature.voice.domain.VoiceCommand
import com.divinedeck.launcher.feature.voice.domain.VoiceCommandParser
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado visible del asistente de voz (versión botón).
 */
sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState

    /** Descargando el modelo de voz sin internet (percent -1 = sin % aún). */
    data class DownloadingModel(val percent: Int) : VoiceState

    data class Heard(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Asistente de voz de DivineDeck — VERSIÓN BOTÓN.
 *
 * Se escucha SOLO mientras Daniel toca el botón del micrófono en el
 * Cockpit. Sin palabra de activación, sin escucha continua: usa el SpeechRecognizer integrado de Android (es-MX,
 * necesita internet) y TextToSpeech (es-MX) solo para confirmar los
 * comandos. SIN INTERNET cae automáticamente a Sherpa-ONNX offline
 * (Whisper tiny en español, modelo descargado una sola vez, ~104 MB,
 * ver [SherpaModelManager]): primero se comprueba la conectividad y si
 * no hay, se va directo a Sherpa; si el reconocedor online falla con
 * ERROR_NETWORK, también se cambia a Sherpa y se sigue escuchando.
 *
 * Blindaje contra crashes: el arranque del reconocedor/modelo y todas
 * las ejecuciones de comandos atrapan [Throwable] (no solo Exception),
 * así que cualquier fallo —incluido un error nativo del motor offline—
 * termina en un mensaje limpio en español en vez de tumbar la app.
 *
 * El reconocedor online debe crearse en el hilo principal: MainActivity lo
 * construye en un lazy val que se toca por primera vez desde setContent.
 * RECORD_AUDIO ya está declarado y concedido (Lowrider); si alguna vez
 * no está concedido, se informa el error en vez de fallar.
 */
class VoiceAssistant(
    private val context: Context,
    private val mediaRepository: MediaControllerRepository,
    private val navRepository: NavigationLauncherRepository,
    private val quickPlacesRepository: QuickPlacesRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    // Consultas reales de BitChord para la voz (biblioteca local, descargas,
    // playlists del usuario): la ejecución se mantiene delgada y delega aquí.
    private val voiceLibrary = BitChordVoiceLibrary(appContext)

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false
    private var utteranceCounter = 0

    // --- Respaldo offline (Sherpa-ONNX) ---
    // Una sola instancia para toda la vida de la app: el reconocedor
    // Sherpa carga el modelo (~104 MB) una vez y se reutiliza en cada
    // escucha; se libera solo en shutdown().
    private var sherpaRecognizer: SherpaSpeechRecognizer? = null
    private var sherpaWaitJob: Job? = null
    private var usingSherpaFallback = false

    init {
        // El crash catcher guarda el último estado de voz con cada crash.
        scope.launch {
            try {
                state.collect { CrashCatcher.lastVoiceState = it.toString() }
            } catch (_: Throwable) {
            }
        }
        // Revalida si el modelo offline ya se descargó en otra sesión.
        try {
            SherpaModelManager.refreshState(appContext)
        } catch (_: Throwable) {
        }
        // Primera descarga automática del modelo offline: si hay internet
        // y el modelo aún no está descargado, se baja en segundo plano
        // (silencioso, sin bloquear el micrófono) para que la primera
        // escucha sin internet ya lo tenga. ensureModel() es idempotente
        // (no hace nada si ya está listo o descargándose) y si falla no
        // se reintenta solo: quedan el botón "Descargar modelo" /
        // "Reintentar" de Ajustes y el próximo arranque de la app.
        try {
            if (!SherpaModelManager.isReady(appContext) && hasInternetCapability()) {
                SherpaModelManager.ensureModel(appContext)
            }
        } catch (_: Throwable) {
        }
        try {
            textToSpeech = TextToSpeech(appContext) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        val result = textToSpeech?.setLanguage(Locale("es", "MX"))
                        if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            // Sin voz es-MX instalada: se usa la voz por defecto.
                            textToSpeech?.setLanguage(Locale.getDefault())
                        }
                        ttsReady = true
                    }
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
            textToSpeech = null
        }
    }

    /** Empieza a escuchar. Si ya está escuchando, no hace nada. */
    fun startListening() {
        try {
            if (isListening) return
            usingSherpaFallback = false
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                _state.value = VoiceState.Error("Concede el permiso de micrófono en Ajustes")
                return
            }
            // Sin internet no se intenta el reconocedor online: se va directo
            // al respaldo offline (Sherpa), que no necesita conectividad.
            if (!hasInternetCapability()) {
                startSherpaListening()
                return
            }
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = VoiceState.Error("El reconocimiento de voz no está disponible en este dispositivo")
                speak("El reconocimiento de voz no está disponible en este dispositivo")
                return
            }
            try {
                val listener = createListener()
                val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                    setRecognitionListener(listener)
                }
                recognizer = speechRecognizer
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                isListening = true
                _state.value = VoiceState.Listening
                speechRecognizer.startListening(intent)
            } catch (_: Throwable) {
                isListening = false
                try {
                    recognizer?.destroy()
                } catch (_: Throwable) {
                }
                recognizer = null
                _state.value = VoiceState.Error("No se pudo iniciar el micrófono")
            }
        } catch (_: Throwable) {
            isListening = false
            usingSherpaFallback = false
            _state.value = VoiceState.Error("No se pudo iniciar el micrófono")
        }
    }

    /** Deja de escuchar y vuelve a Idle. */
    fun stopListening() {
        try {
            if (usingSherpaFallback) {
                // Si aún se está descargando el modelo, cancelar la espera.
                if (sherpaWaitJob != null) {
                    sherpaWaitJob?.cancel()
                    sherpaWaitJob = null
                    isListening = false
                    usingSherpaFallback = false
                    if (_state.value is VoiceState.Listening ||
                        _state.value is VoiceState.DownloadingModel
                    ) {
                        _state.value = VoiceState.Idle
                    }
                    return
                }
                // Escuchando con Sherpa: parar el micrófono y decodificar
                // en segundo plano; el resultado llega por onSherpaHeard /
                // onSherpaError (el estado sigue en Listening hasta entonces).
                // La descarga del modelo, si está en curso, sigue en
                // segundo plano (así la próxima escucha ya lo tiene).
                try {
                    sherpaRecognizer?.stop()
                } catch (_: Throwable) {
                    isListening = false
                    usingSherpaFallback = false
                    _state.value = VoiceState.Idle
                }
                return
            }
            if (!isListening) return
            isListening = false
            usingSherpaFallback = false
            sherpaWaitJob?.cancel()
            sherpaWaitJob = null
            try {
                recognizer?.stopListening()
            } catch (_: Throwable) {
            }
            if (_state.value is VoiceState.Listening ||
                _state.value is VoiceState.DownloadingModel
            ) {
                _state.value = VoiceState.Idle
            }
        } catch (_: Throwable) {
            isListening = false
            usingSherpaFallback = false
            _state.value = VoiceState.Idle
        }
    }

    // --- Respaldo offline (Sherpa-ONNX): SOLO escucha, nunca ejecuta directo.
    // El texto reconocido pasa por el mismo VoiceCommandParser que el
    // reconocedor online (ver onSherpaHeard). ---

    /**
     * true si hay una red con capacidad de internet.
     *
     * OJO: NO se exige NET_CAPABILITY_VALIDATED. Una red conectada pero
     * aún no validada (p. ej. portal cautivo a medio abrir, o validación
     * lenta) no debe mandar en silencio al camino offline: se prefiere
     * intentar el reconocedor online y, si falla con ERROR_NETWORK, ahí
     * sí cae al respaldo offline.
     */
    private fun hasInternetCapability(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Empieza a escuchar con Sherpa (offline). Si el modelo aún no está
     * descargado, arranca la descarga y espera a que esté listo; si la
     * descarga falla (p. ej. sin internet para descargarlo), publica el
     * error en español en vez de quedarse colgado.
     */
    private fun startSherpaListening() {
        try {
            if (isListening) return
            usingSherpaFallback = true
            isListening = true
            if (SherpaModelManager.isReady(appContext)) {
                startSherpaNow()
                return
            }
            _state.value = VoiceState.DownloadingModel(-1)
            SherpaModelManager.ensureModel(appContext)
            sherpaWaitJob?.cancel()
            sherpaWaitJob = scope.launch {
                try {
                    SherpaModelManager.state.collect { modelState ->
                        when (modelState) {
                            is SherpaModelState.Ready -> startSherpaNow()
                            is SherpaModelState.Downloading -> {
                                _state.value = VoiceState.DownloadingModel(modelState.percent)
                            }
                            is SherpaModelState.Error -> {
                                isListening = false
                                usingSherpaFallback = false
                                _state.value = VoiceState.Error(modelState.message)
                                speak(modelState.message)
                            }
                            is SherpaModelState.NotDownloaded -> {
                                // ensureModel ya arrancó la descarga; se sigue esperando.
                            }
                        }
                    }
                } catch (_: Throwable) {
                    isListening = false
                    usingSherpaFallback = false
                    _state.value = VoiceState.Error("No se pudo descargar el modelo de voz")
                }
            }
        } catch (_: Throwable) {
            isListening = false
            usingSherpaFallback = false
            _state.value = VoiceState.Error("No se pudo iniciar el micrófono sin internet")
        }
    }

    /** Crea (o reutiliza) el reconocedor Sherpa y empieza a escuchar
     * en hilo IO: cargar el modelo pesa ~104 MB y tarda un momento. */
    private fun startSherpaNow() {
        try {
            sherpaWaitJob?.cancel()
            sherpaWaitJob = null
            if (!isListening) return
            _state.value = VoiceState.Listening
            scope.launch(Dispatchers.IO) {
                try {
                    if (!isListening) return@launch
                    val sherpa = getOrCreateSherpa()
                    if (!isListening) return@launch
                    // Si start() falla, el motivo ya llega por onError.
                    sherpa.start()
                } catch (_: Throwable) {
                    onSherpaError("No se pudo iniciar el micrófono sin internet")
                }
            }
        } catch (_: Throwable) {
            isListening = false
            usingSherpaFallback = false
            _state.value = VoiceState.Error("No se pudo iniciar el micrófono sin internet")
        }
    }

    private fun getOrCreateSherpa(): SherpaSpeechRecognizer {
        sherpaRecognizer?.let { return it }
        val created = SherpaSpeechRecognizer(
            appContext,
            onFinalResult = { heard -> onSherpaHeard(heard) },
            onError = { message -> onSherpaError(message) },
            onTimeout = { onSherpaTimeout() }
        )
        sherpaRecognizer = created
        return created
    }

    /** Libera el reconocedor Sherpa (micrófono + modelo). Solo en shutdown. */
    private fun releaseSherpa() {
        val sherpa = sherpaRecognizer
        sherpaRecognizer = null
        try {
            sherpa?.release()
        } catch (_: Throwable) {
        }
    }

    /** Texto final de Sherpa: mismo camino que el resultado online. */
    private fun onSherpaHeard(heard: String) {
        scope.launch {
            try {
                if (!isListening) return@launch
                isListening = false
                usingSherpaFallback = false
                val text = heard.trim()
                if (text.isBlank()) {
                    _state.value = VoiceState.Idle
                    speak("No te escuché, inténtalo de nuevo")
                    return@launch
                }
                _state.value = VoiceState.Heard(text)
                execute(VoiceCommandParser.parse(text))
            } catch (_: Throwable) {
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Idle
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    private fun onSherpaError(message: String) {
        scope.launch {
            try {
                if (!isListening) return@launch
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Error(message)
                speak(message)
            } catch (_: Throwable) {
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Error(message)
            }
        }
    }

    private fun onSherpaTimeout() {
        scope.launch {
            try {
                if (!isListening) return@launch
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Idle
                speak("No te escuché, inténtalo de nuevo")
            } catch (_: Throwable) {
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Idle
            }
        }
    }

    /** Libera el reconocedor y el TTS. Llamar desde onDestroy. */
    fun shutdown() {
        isListening = false
        usingSherpaFallback = false
        sherpaWaitJob?.cancel()
        sherpaWaitJob = null
        try {
            releaseSherpa()
        } catch (_: Throwable) {
        }
        try {
            recognizer?.cancel()
        } catch (_: Throwable) {
        }
        try {
            recognizer?.destroy()
        } catch (_: Throwable) {
        }
        recognizer = null
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Throwable) {
        }
        textToSpeech = null
        ttsReady = false
        scope.cancel()
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {
            // Resultados parciales: no se ejecutan, solo se espera el final.
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            try {
                isListening = false
                usingSherpaFallback = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (heard.isNullOrBlank()) {
                    _state.value = VoiceState.Idle
                    speak("No te escuché, inténtalo de nuevo")
                    return
                }
                _state.value = VoiceState.Heard(heard)
                execute(VoiceCommandParser.parse(heard))
            } catch (_: Throwable) {
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Idle
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }

        override fun onError(error: Int) {
            try {
                isListening = false
                when (error) {
                    SpeechRecognizer.ERROR_NETWORK -> {
                        // Sin internet (o sin acceso al servicio de Google):
                        // respaldo offline con Sherpa en vez de rendirse. Solo
                        // una vez por escucha; si ya se estaba en Sherpa, el
                        // error online ya no aplica y se informa sin más.
                        if (!usingSherpaFallback) {
                            startSherpaListening()
                        } else {
                            usingSherpaFallback = false
                            _state.value = VoiceState.Error("Se necesita internet para escuchar")
                            speak("Se necesita internet para escuchar")
                        }
                    }
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        _state.value = VoiceState.Idle
                        speak("No te escuché, inténtalo de nuevo")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        _state.value = VoiceState.Error("Concede el permiso de micrófono en Ajustes")
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Cancelación nuestra (stopListening): no es un error real.
                        if (_state.value is VoiceState.Listening) {
                            _state.value = VoiceState.Idle
                        }
                    }
                    else -> {
                        _state.value = VoiceState.Error("No se pudo escuchar, inténtalo de nuevo")
                    }
                }
            } catch (_: Throwable) {
                isListening = false
                usingSherpaFallback = false
                _state.value = VoiceState.Error("No se pudo escuchar, inténtalo de nuevo")
            }
        }
    }

    private fun execute(command: VoiceCommand) {
        try {
            when (command) {
                is VoiceCommand.NavigateTo -> executeNavigation(command.placeName)
                VoiceCommand.Next -> {
                    mediaRepository.skipToNext()
                    speak("Siguiente canción")
                }
                VoiceCommand.Previous -> {
                    mediaRepository.skipToPrevious()
                    speak("Canción anterior")
                }
                VoiceCommand.Pause -> {
                    mediaRepository.pause()
                    speak("Pausa")
                }
                VoiceCommand.Play -> {
                    mediaRepository.play()
                    speak("Reproduciendo")
                }
                is VoiceCommand.PlaySong -> executePlaySong(command.query)
                is VoiceCommand.PlayPlaylist -> executePlayPlaylist(command.name)
                is VoiceCommand.PlayArtist -> executePlayArtist(command.name)
                VoiceCommand.PlayDownloads -> executePlayDownloads()
                VoiceCommand.PlayLocalLibrary -> executePlayLocalLibrary()
                VoiceCommand.Unknown -> speak("No entendí, inténtalo de nuevo")
            }
        } catch (_: Throwable) {
            speak("No pude hacer eso, inténtalo de nuevo")
        }
    }

    private fun executeNavigation(spokenName: String) {
        try {
            val normalized = VoiceCommandParser.normalize(spokenName)
            val places = quickPlacesRepository.places.value
            val match = places.firstOrNull { place ->
                val placeName = VoiceCommandParser.normalize(place.name)
                placeName.contains(normalized) || normalized.contains(placeName)
            }
            if (match != null) {
                navRepository.launchNavigationTo(appContext, match.address)
                speak("Abriendo ruta a ${match.name}")
                return
            }
            // Destinos fijos: mismas cadenas que usa la pantalla de Navegación.
            val fixed = listOf(
                "casa" to "Casa",
                "trabajo" to "Trabajo",
                "gasolinera" to "Gasolinera Cercana",
                "taller" to "Taller Mecánico"
            )
            val fixedMatch = fixed.firstOrNull { (key, _) ->
                normalized == key || normalized.contains(key)
            }
            if (fixedMatch != null) {
                navRepository.launchNavigationTo(appContext, fixedMatch.second)
                speak("Abriendo ruta a ${fixedMatch.second}")
                return
            }
            navRepository.launchMapsSearch(appContext, spokenName)
            speak("No encontré ese lugar, buscando en Maps")
        } catch (_: Throwable) {
            speak("No pude hacer eso, inténtalo de nuevo")
        }
    }

    /**
     * "pon X": primero la biblioteca REAL de BitChord, en este orden:
     * 1) playlist local con ese nombre, 2) canción local (descargas +
     * archivos del dispositivo), 3) artista local (toda su música en cola),
     * 4) búsqueda online (como antes, primer resultado).
     */
    private fun executePlaySong(query: String) {
        scope.launch {
            try {
                val localPlaylist = voiceLibrary.findLocalPlaylist(query)
                if (localPlaylist != null && localPlaylist.songs.isNotEmpty()) {
                    mediaRepository.playQueue(localPlaylist.songs)
                    speak("Reproduciendo la playlist ${localPlaylist.title}")
                    return@launch
                }
                val localSong = voiceLibrary.searchLocalSong(query)
                if (localSong != null) {
                    mediaRepository.playSong(localSong)
                    speak("Poniendo ${localSong.title}")
                    return@launch
                }
                val artistSongs = voiceLibrary.songsByArtist(query)
                if (artistSongs.isNotEmpty()) {
                    mediaRepository.playQueue(artistSongs)
                    speak("Poniendo canciones de ${artistSongs.first().artist}")
                    return@launch
                }
                val song = searchSong(query)
                if (song != null) {
                    mediaRepository.playSong(song)
                    speak("Poniendo ${song.title}")
                } else {
                    speak("No encontré esa canción")
                }
            } catch (_: Throwable) {
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    /** "pon la playlist X": local primero, remota de YouTube Music después. */
    private fun executePlayPlaylist(name: String) {
        scope.launch {
            try {
                val local = voiceLibrary.findLocalPlaylist(name)
                if (local != null) {
                    if (local.songs.isEmpty()) {
                        speak("La playlist ${local.title} no tiene canciones")
                    } else {
                        mediaRepository.playQueue(local.songs)
                        speak("Reproduciendo la playlist ${local.title}")
                    }
                    return@launch
                }
                val remote = voiceLibrary.findRemotePlaylist(name)
                if (remote != null && remote.songs.isNotEmpty()) {
                    mediaRepository.playQueue(remote.songs)
                    speak("Reproduciendo la playlist ${remote.title}")
                    return@launch
                }
                speak("No encontré la playlist $name")
            } catch (_: Throwable) {
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    /** "pon música de X": canciones locales del artista; si no hay ninguna,
     * cae a la búsqueda online (primer resultado). */
    private fun executePlayArtist(name: String) {
        scope.launch {
            try {
                val songs = voiceLibrary.songsByArtist(name)
                if (songs.isNotEmpty()) {
                    mediaRepository.playQueue(songs)
                    speak("Poniendo canciones de ${songs.first().artist}")
                    return@launch
                }
                val song = searchSong(name)
                if (song != null) {
                    mediaRepository.playSong(song)
                    speak("Poniendo ${song.title}")
                } else {
                    speak("No encontré música de $name")
                }
            } catch (_: Throwable) {
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    /** "pon mis descargas": toda la música descargada (offline) en cola. */
    private fun executePlayDownloads() {
        scope.launch {
            try {
                val songs = voiceLibrary.downloadedSongs()
                if (songs.isEmpty()) {
                    speak("No tienes canciones descargadas")
                    return@launch
                }
                mediaRepository.playQueue(songs)
                speak(
                    if (songs.size == 1) "Poniendo tu descarga, 1 canción"
                    else "Poniendo tus descargas, ${songs.size} canciones"
                )
            } catch (_: Throwable) {
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    /** "pon mis canciones" / "música local": biblioteca local completa en cola. */
    private fun executePlayLocalLibrary() {
        scope.launch {
            try {
                val songs = voiceLibrary.allLocalSongs()
                if (songs.isEmpty()) {
                    speak("No encontré música en el dispositivo")
                    return@launch
                }
                mediaRepository.playQueue(songs)
                speak(
                    if (songs.size == 1) "Poniendo tu música local, 1 canción"
                    else "Poniendo tu música local, ${songs.size} canciones"
                )
            } catch (_: Throwable) {
                speak("No pude hacer eso, inténtalo de nuevo")
            }
        }
    }

    /**
     * Misma búsqueda que usa la pantalla de Música (MusicSearchViewModel):
     * YtMusicRepository.searchPage con filtro ALL; primera canción
     * (TopTrack o Track) de los resultados.
     */
    private suspend fun searchSong(query: String): Song? {
        return withContext(Dispatchers.IO) {
            try {
                val result = YtMusicRepository.searchPage(query, SearchFilter.ALL)
                val page = result.getOrNull() ?: return@withContext null
                page.rows.firstNotNullOfOrNull { row ->
                    when (row) {
                        is SearchResult.TopTrack -> row.song
                        is SearchResult.Track -> row.song
                        else -> null
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    /** El TTS nunca habla encima de sí mismo: corta lo que suena antes. */
    private fun speak(text: String) {
        val tts = textToSpeech ?: return
        if (!ttsReady) return
        try {
            tts.stop()
            utteranceCounter++
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "divinedeck-voice-$utteranceCounter")
        } catch (_: Throwable) {
        }
    }
}

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
 * Cockpit. Sin palabra de activación, sin escucha continua, sin
 * Porcupine: usa el SpeechRecognizer integrado de Android (es-MX,
 * necesita internet) y TextToSpeech (es-MX) solo para confirmar los
 * comandos. SIN INTERNET cae automáticamente a Vosk offline (modelo
 * español descargado una sola vez, ~40 MB, ver [VoskModelManager]):
 * primero se comprueba la conectividad y si no hay, se va directo a
 * Vosk; si el reconocedor online falla con ERROR_NETWORK, también se
 * cambia a Vosk y se sigue escuchando.
 *
 * El reconocedor debe crearse en el hilo principal: MainActivity lo
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

    // --- Respaldo offline (Vosk) ---
    private var voskRecognizer: VoskSpeechRecognizer? = null
    private var voskWaitJob: Job? = null
    private var usingVoskFallback = false

    init {
        // Revalida si el modelo offline ya se descargó en otra sesión.
        VoskModelManager.refreshState(appContext)
        // Primera descarga automática del modelo offline: si hay internet
        // y el modelo aún no está descargado, se baja en segundo plano
        // (silencioso, sin bloquear el micrófono) para que la primera
        // escucha sin internet ya lo tenga. ensureModel() es idempotente
        // (no hace nada si ya está listo o descargándose) y si falla no
        // se reintenta solo: quedan el botón "Descargar modelo" /
        // "Reintentar" de Ajustes y el próximo arranque de la app.
        if (!VoskModelManager.isReady(appContext) && isInternetAvailable()) {
            VoskModelManager.ensureModel(appContext)
        }
        textToSpeech = TextToSpeech(appContext) { status ->
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
        }
    }

    /** Empieza a escuchar. Si ya está escuchando, no hace nada. */
    fun startListening() {
        if (isListening) return
        usingVoskFallback = false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = VoiceState.Error("Concede el permiso de micrófono en Ajustes")
            return
        }
        // Sin internet no se intenta el reconocedor online: se va directo
        // al respaldo offline (Vosk), que no necesita conectividad.
        if (!isInternetAvailable()) {
            startVoskListening()
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
        } catch (_: Exception) {
            isListening = false
            recognizer = null
            _state.value = VoiceState.Error("No se pudo iniciar el micrófono")
        }
    }

    /** Deja de escuchar y vuelve a Idle. */
    fun stopListening() {
        if (!isListening) return
        isListening = false
        usingVoskFallback = false
        voskWaitJob?.cancel()
        voskWaitJob = null
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        // La descarga del modelo, si está en curso, sigue en segundo plano
        // (así la próxima escucha ya lo tiene); solo se suelta el micrófono.
        releaseVosk()
        if (_state.value is VoiceState.Listening ||
            _state.value is VoiceState.DownloadingModel
        ) {
            _state.value = VoiceState.Idle
        }
    }

    // --- Respaldo offline (Vosk): SOLO escucha, nunca ejecuta directo.
    // El texto reconocido pasa por el mismo VoiceCommandParser que el
    // reconocedor online (ver onVoskHeard). ---

    /** true si hay una red validada con acceso a internet. */
    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Empieza a escuchar con Vosk (offline). Si el modelo aún no está
     * descargado, arranca la descarga y espera a que esté listo; si la
     * descarga falla (p. ej. sin internet para descargarlo), publica el
     * error en español en vez de quedarse colgado.
     */
    private fun startVoskListening() {
        if (isListening) return
        usingVoskFallback = true
        isListening = true
        if (VoskModelManager.isReady(appContext)) {
            startVoskNow()
            return
        }
        _state.value = VoiceState.DownloadingModel(-1)
        VoskModelManager.ensureModel(appContext)
        voskWaitJob?.cancel()
        voskWaitJob = scope.launch {
            VoskModelManager.state.collect { modelState ->
                when (modelState) {
                    is VoskModelState.Ready -> startVoskNow()
                    is VoskModelState.Downloading -> {
                        _state.value = VoiceState.DownloadingModel(modelState.percent)
                    }
                    is VoskModelState.Error -> {
                        isListening = false
                        usingVoskFallback = false
                        _state.value = VoiceState.Error(modelState.message)
                        speak(modelState.message)
                    }
                    is VoskModelState.NotDownloaded -> {
                        // ensureModel ya arrancó la descarga; se sigue esperando.
                    }
                }
            }
        }
    }

    /** Crea el reconocedor Vosk y empieza a escuchar (hilo IO: el modelo
     * pesa ~40 MB y cargarlo tarda un momento). */
    private fun startVoskNow() {
        voskWaitJob?.cancel()
        voskWaitJob = null
        if (!isListening) return
        _state.value = VoiceState.Listening
        scope.launch(Dispatchers.IO) {
            if (!isListening) return@launch
            releaseVosk()
            if (!isListening) return@launch
            val vosk = VoskSpeechRecognizer(
                appContext,
                onFinalResult = { heard -> onVoskHeard(heard) },
                onError = { message -> onVoskError(message) },
                onTimeout = { onVoskTimeout() }
            )
            voskRecognizer = vosk
            // Si start() falla, el motivo ya llega por onError.
            vosk.start()
        }
    }

    /** Libera el reconocedor Vosk (micrófono + modelo). */
    private fun releaseVosk() {
        val vosk = voskRecognizer
        voskRecognizer = null
        try {
            vosk?.release()
        } catch (_: Exception) {
        }
    }

    /** Texto final de Vosk: mismo camino que el resultado online. */
    private fun onVoskHeard(heard: String) {
        scope.launch {
            if (!isListening) return@launch
            isListening = false
            usingVoskFallback = false
            releaseVosk()
            val text = heard.trim()
            if (text.isBlank()) {
                _state.value = VoiceState.Idle
                speak("No te escuché, inténtalo de nuevo")
                return@launch
            }
            _state.value = VoiceState.Heard(text)
            execute(VoiceCommandParser.parse(text))
        }
    }

    private fun onVoskError(message: String) {
        scope.launch {
            if (!isListening) return@launch
            isListening = false
            usingVoskFallback = false
            releaseVosk()
            _state.value = VoiceState.Error(message)
            speak(message)
        }
    }

    private fun onVoskTimeout() {
        scope.launch {
            if (!isListening) return@launch
            isListening = false
            usingVoskFallback = false
            releaseVosk()
            _state.value = VoiceState.Idle
            speak("No te escuché, inténtalo de nuevo")
        }
    }

    /** Libera el reconocedor y el TTS. Llamar desde onDestroy. */
    fun shutdown() {
        isListening = false
        usingVoskFallback = false
        voskWaitJob?.cancel()
        voskWaitJob = null
        releaseVosk()
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
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
            isListening = false
            usingVoskFallback = false
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
        }

        override fun onError(error: Int) {
            isListening = false
            when (error) {
                SpeechRecognizer.ERROR_NETWORK -> {
                    // Sin internet (o sin acceso al servicio de Google):
                    // respaldo offline con Vosk en vez de rendirse. Solo
                    // una vez por escucha; si ya se estaba en Vosk, el
                    // error online ya no aplica y se informa sin más.
                    if (!usingVoskFallback) {
                        startVoskListening()
                    } else {
                        usingVoskFallback = false
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
        }
    }

    private fun execute(command: VoiceCommand) {
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
    }

    private fun executeNavigation(spokenName: String) {
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
    }

    /**
     * "pon X": primero la biblioteca REAL de BitChord, en este orden:
     * 1) playlist local con ese nombre, 2) canción local (descargas +
     * archivos del dispositivo), 3) artista local (toda su música en cola),
     * 4) búsqueda online (como antes, primer resultado).
     */
    private fun executePlaySong(query: String) {
        scope.launch {
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
        }
    }

    /** "pon la playlist X": local primero, remota de YouTube Music después. */
    private fun executePlayPlaylist(name: String) {
        scope.launch {
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
        }
    }

    /** "pon música de X": canciones locales del artista; si no hay ninguna,
     * cae a la búsqueda online (primer resultado). */
    private fun executePlayArtist(name: String) {
        scope.launch {
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
        }
    }

    /** "pon mis descargas": toda la música descargada (offline) en cola. */
    private fun executePlayDownloads() {
        scope.launch {
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
        }
    }

    /** "pon mis canciones" / "música local": biblioteca local completa en cola. */
    private fun executePlayLocalLibrary() {
        scope.launch {
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
            } catch (_: Exception) {
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
        } catch (_: Exception) {
        }
    }
}

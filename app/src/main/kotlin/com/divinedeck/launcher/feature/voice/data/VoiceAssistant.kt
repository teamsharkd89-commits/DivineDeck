package com.divinedeck.launcher.feature.voice.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
    data class Heard(val text: String) : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Asistente de voz de DivineDeck — VERSIÓN BOTÓN.
 *
 * Se escucha SOLO mientras Daniel toca el botón del micrófono en el
 * Cockpit. Sin palabra de activación, sin escucha continua, sin Vosk,
 * sin Porcupine, sin descargas de modelos: usa el SpeechRecognizer
 * integrado de Android (es-MX, necesita internet) y TextToSpeech
 * (es-MX) solo para confirmar los comandos.
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

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false
    private var isListening = false
    private var utteranceCounter = 0

    init {
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
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = VoiceState.Error("El reconocimiento de voz no está disponible en este dispositivo")
            speak("El reconocimiento de voz no está disponible en este dispositivo")
            return
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = VoiceState.Error("Concede el permiso de micrófono en Ajustes")
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
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }
        if (_state.value is VoiceState.Listening) {
            _state.value = VoiceState.Idle
        }
    }

    /** Libera el reconocedor y el TTS. Llamar desde onDestroy. */
    fun shutdown() {
        isListening = false
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
                    _state.value = VoiceState.Error("Se necesita internet para escuchar")
                    speak("Se necesita internet para escuchar")
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

    private fun executePlaySong(query: String) {
        scope.launch {
            val song = searchSong(query)
            if (song != null) {
                mediaRepository.playSong(song)
                speak("Poniendo ${song.title}")
            } else {
                speak("No encontré esa canción")
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

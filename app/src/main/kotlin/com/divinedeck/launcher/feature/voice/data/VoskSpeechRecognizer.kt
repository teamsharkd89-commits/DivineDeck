package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Reconocedor de voz sin internet con Vosk (vosk-android 0.3.75).
 *
 * - Carga el modelo español de [VoskModelManager] y escucha por el
 *   micrófono con [SpeechService] (que crea su propio AudioRecord a
 *   16 kHz, mono, PCM 16-bit; no se graba ni se guarda ningún audio).
 * - Los callbacks de Vosk llegan en el hilo principal (SpeechService los
 *   publica con un Handler), así que es seguro llamar a [stop]/[release]
 *   desde ellos.
 * - Emite el texto con la misma forma que el reconocedor online: un único
 *   resultado final por escucha, que el llamador pasa a
 *   [com.divinedeck.launcher.feature.voice.domain.VoiceCommandParser]
 *   tal cual. Los parciales solo se notifican, nunca se ejecutan.
 * - SIN gramática: el vocabulario de comandos incluye nombres de lugares
 *   y canciones arbitrarios ("llévame a la casa de Migue", "pon ...") y
 *   una gramática fija de Vosk los bloquearía. Se usa el modelo pequeño
 *   completo y el texto pasa tal cual al parser. (La gramática con
 *   `Recognizer(model, sampleRate, grammarJson)` existe en la librería,
 *   pero no sirve para este vocabulario abierto.)
 * - Robusto: cualquier fallo de carga/inicio se reporta por [onError] y
 *   nunca lanza; [release] libera micrófono, reconocedor y modelo.
 *
 * Solo se usa como respaldo cuando no hay internet (ver [VoiceAssistant]):
 * nunca escucha de forma continua ni con palabra de activación.
 */
class VoskSpeechRecognizer(
    context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onTimeout: () -> Unit = {}
) {

    companion object {
        private const val SAMPLE_RATE = 16000f
    }

    private val appContext = context.applicationContext

    /** Solo se entrega un resultado final por escucha (onResult y
     * onFinalResult pueden dispararse los dos para la misma frase). */
    private val delivered = AtomicBoolean(false)

    @Volatile
    private var stopped = false

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    /**
     * Carga el modelo y empieza a escuchar. false si el modelo no está
     * descargado o el micrófono no se pudo abrir (el motivo llega por
     * [onError]).
     */
    fun start(): Boolean {
        if (stopped) return false
        return try {
            if (!VoskModelManager.isReady(appContext)) {
                onError("El modelo de voz sin internet aún no está descargado")
                return false
            }
            val loadedModel = Model(VoskModelManager.modelDir(appContext).absolutePath)
            model = loadedModel
            val createdRecognizer = Recognizer(loadedModel, SAMPLE_RATE)
            recognizer = createdRecognizer
            val service = SpeechService(createdRecognizer, SAMPLE_RATE)
            speechService = service
            // Sin timeout: se escucha hasta que el usuario pare o hable.
            // startListening(listener) usa NO_TIMEOUT internamente.
            val started = service.startListening(createListener())
            if (!started) {
                release()
                onError("No se pudo iniciar el micrófono sin internet")
                return false
            }
            true
        } catch (_: IOException) {
            release()
            onError("No se pudo cargar el modelo de voz sin internet")
            false
        } catch (_: Exception) {
            release()
            onError("No se pudo iniciar el micrófono sin internet")
            false
        }
    }

    /**
     * Para de escuchar. El resultado final de lo dicho hasta ahora llega
     * por onFinalResult (si aún no se entregó nada).
     */
    fun stop() {
        stopped = true
        try {
            speechService?.stop()
        } catch (_: Exception) {
        }
    }

    /** Libera micrófono, reconocedor y modelo. Llamar al terminar. */
    fun release() {
        stopped = true
        try {
            speechService?.stop()
        } catch (_: Exception) {
        }
        try {
            speechService?.shutdown()
        } catch (_: Exception) {
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (_: Exception) {
        }
        recognizer = null
        try {
            model?.close()
        } catch (_: Exception) {
        }
        model = null
    }

    private fun createListener() = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val text = parseText(hypothesis, "partial")
            if (text.isNotBlank()) onPartialResult(text)
        }

        override fun onResult(hypothesis: String?) {
            // Fin de frase por endpointing: es el resultado que se ejecuta.
            deliverFinal(parseText(hypothesis, "text"))
        }

        override fun onFinalResult(hypothesis: String?) {
            // Resultado final al parar: solo si aún no se entregó nada.
            deliverFinal(parseText(hypothesis, "text"))
        }

        override fun onError(exception: Exception?) {
            if (!delivered.get()) {
                onError("No se pudo escuchar sin internet, inténtalo de nuevo")
            }
        }

        override fun onTimeout() {
            onTimeout()
        }
    }

    private fun deliverFinal(text: String) {
        if (text.isBlank()) return
        if (delivered.compareAndSet(false, true)) onFinalResult(text)
    }

    /** Los resultados de Vosk son JSON: {"text": "..."} / {"partial": "..."}. */
    private fun parseText(json: String?, field: String): String {
        if (json.isNullOrBlank()) return ""
        return try {
            JSONObject(json).optString(field, "")
        } catch (_: Exception) {
            ""
        }
    }
}

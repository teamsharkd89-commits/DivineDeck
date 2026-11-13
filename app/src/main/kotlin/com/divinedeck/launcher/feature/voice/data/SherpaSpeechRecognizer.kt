package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineStream
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reconocedor de voz sin internet con Sherpa-ONNX (Whisper tiny int8,
 * español) — reemplaza al motor offline anterior, que crasheaba la app
 * al tocar el micrófono.
 *
 * - Carga el modelo español de [SherpaModelManager] (una sola vez: la
 *   instancia de [OfflineRecognizer] se crea en el primer [start] y se
 *   REUTILIZA en todas las escuchas; se libera solo con [release], que
 *   [VoiceAssistant] llama al cerrar la app).
 * - Cada escucha crea su propio [OfflineStream]; el audio del micrófono
 *   se alimenta con [AudioRecord] a 16 kHz, mono, PCM 16-bit (no se graba
 *   ni se guarda ningún audio).
 * - [stop] para el micrófono y decodifica en un hilo de fondo (decode es
 *   bloqueante y tarda 1-3 s): el texto final llega por [onFinalResult],
 *   igual que con el reconocedor online, y el llamador lo pasa a
 *   [com.divinedeck.launcher.feature.voice.domain.VoiceCommandParser]
 *   tal cual. Los parciales solo se notifican, nunca se ejecutan
 *   (Whisper offline no emite parciales: [onPartialResult] queda
 *   reservado por compatibilidad con la forma del reconocedor anterior).
 * - Robusto: cualquier fallo de carga/inicio/decodificación se reporta
 *   por [onError] y nunca lanza; [start], [stop] y [release] atrapan
 *   [Throwable] (no solo Exception) porque un fallo nativo (Unsatisfied
 *   LinkError, Error de ONNX Runtime) no debe tumbar la app.
 * - La creación del reconocedor carga ~104 MB de modelo y tarda unos
 *   segundos: llamar a [start] SIEMPRE fuera del hilo principal
 *   (VoiceAssistant lo hace en Dispatchers.IO).
 *
 * Solo se usa como respaldo cuando no hay internet (ver [VoiceAssistant]):
 * nunca escucha de forma continua ni con palabra de activación.
 */
class SherpaSpeechRecognizer(
    context: Context,
    private val onPartialResult: (String) -> Unit = {},
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onTimeout: () -> Unit = {}
) {

    companion object {
        private const val SAMPLE_RATE = 16000
        /** 100 ms de audio por lectura (16 kHz). */
        private const val READ_CHUNK_SHORTS = 1600
    }

    private val appContext = context.applicationContext

    /** Solo se entrega un resultado final por escucha. */
    private val delivered = AtomicBoolean(false)

    /** true mientras el micrófono está grabando. */
    @Volatile
    private var recording = false

    /** true mientras decode() corre en su hilo (stop ya se llamó). */
    @Volatile
    private var decoding = false

    /** true tras release(): los callbacks pendientes ya no se entregan. */
    @Volatile
    private var released = false

    /** Instancia única reutilizada entre escuchas; null hasta el primer start. */
    private var recognizer: OfflineRecognizer? = null

    /** Stream de la escucha en curso; uno nuevo por cada start. */
    private var stream: OfflineStream? = null

    private var audioRecord: AudioRecord? = null
    private var feedThread: Thread? = null
    private var decodeThread: Thread? = null

    /**
     * Carga el modelo (si aún no está cargado) y empieza a escuchar.
     * false si el modelo no está descargado o el micrófono no se pudo
     * abrir (el motivo llega por [onError]). Nunca lanza.
     * Llamar fuera del hilo principal.
     */
    fun start(): Boolean {
        if (released) return false
        if (decoding) {
            onError("Espera a que termine de escuchar")
            return false
        }
        return try {
            if (recording) return true
            if (!SherpaModelManager.isReady(appContext)) {
                onError("El modelo de voz sin internet aún no está descargado")
                return false
            }
            val activeRecognizer = ensureRecognizer() ?: run {
                onError("No se pudo cargar el modelo de voz sin internet")
                return false
            }
            // Stream nuevo por escucha; el anterior ya se liberó en stop().
            try {
                stream?.release()
            } catch (_: Throwable) {
            }
            val newStream = activeRecognizer.createStream()
            stream = newStream
            val record = createAudioRecord() ?: run {
                try {
                    newStream.release()
                } catch (_: Throwable) {
                }
                stream = null
                onError("No se pudo abrir el micrófono")
                return false
            }
            audioRecord = record
            try {
                record.startRecording()
            } catch (_: Throwable) {
                releaseAudioRecord()
                try {
                    newStream.release()
                } catch (_: Throwable) {
                }
                stream = null
                onError("No se pudo iniciar el micrófono sin internet")
                return false
            }
            delivered.set(false)
            recording = true
            startFeedThread(newStream)
            true
        } catch (_: Throwable) {
            stopAudioOnly()
            onError("No se pudo iniciar el micrófono sin internet")
            false
        }
    }

    /**
     * Para el micrófono y decodifica lo grabado en un hilo de fondo.
     * El resultado final llega por [onFinalResult] (o [onError] si la
     * decodificación falla). Idempotente y nunca lanza: si no se estaba
     * grabando, no hace nada.
     */
    fun stop() {
        try {
            if (!recording) return
            recording = false
            stopAudioOnly()
            val activeRecognizer = recognizer
            val activeStream = stream
            if (activeRecognizer == null || activeStream == null) {
                onError("No se pudo escuchar sin internet, inténtalo de nuevo")
                return
            }
            if (decoding) return
            decoding = true
            val thread = Thread(
                {
                    try {
                        activeRecognizer.decode(activeStream)
                        val text = try {
                            activeRecognizer.getResult(activeStream).text.orEmpty()
                        } catch (_: Throwable) {
                            ""
                        }
                        if (!released && delivered.compareAndSet(false, true)) {
                            // Aunque el texto salga vacío se avisa: el
                            // llamador responde "No te escuché".
                            onFinalResult(text.trim())
                        }
                    } catch (_: Throwable) {
                        if (!released && delivered.compareAndSet(false, true)) {
                            onError("No se pudo escuchar sin internet, inténtalo de nuevo")
                        }
                    } finally {
                        decoding = false
                        try {
                            activeStream.release()
                        } catch (_: Throwable) {
                        }
                        if (stream === activeStream) stream = null
                    }
                },
                "sherpa-decode"
            )
            decodeThread = thread
            thread.start()
        } catch (_: Throwable) {
            decoding = false
            if (!released) {
                try {
                    onError("No se pudo escuchar sin internet, inténtalo de nuevo")
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Libera micrófono, stream y reconocedor. Llamar solo al cerrar la
     * app ([VoiceAssistant.shutdown]): entre escuchas el reconocedor se
     * mantiene cargado para no recargar el modelo (~104 MB) cada vez.
     * Nunca lanza. Si hay una decodificación en curso, su resultado se
     * descarta.
     */
    fun release() {
        released = true
        recording = false
        try {
            stopAudioOnly()
        } catch (_: Throwable) {
        }
        try {
            stream?.release()
        } catch (_: Throwable) {
        }
        stream = null
        try {
            recognizer?.release()
        } catch (_: Throwable) {
        }
        recognizer = null
    }

    /** Crea el reconocedor con el modelo Whisper tiny (o lo reutiliza). */
    private fun ensureRecognizer(): OfflineRecognizer? {
        recognizer?.let { return it }
        return try {
            val dir: File = SherpaModelManager.modelDir(appContext)
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(dir, SherpaModelManager.ENCODER_FILE_NAME).absolutePath,
                        decoder = File(dir, SherpaModelManager.DECODER_FILE_NAME).absolutePath,
                        language = "es",
                        task = "transcribe"
                    ),
                    tokens = File(dir, SherpaModelManager.TOKENS_FILE_NAME).absolutePath,
                    numThreads = 2
                )
            )
            // v2.0.1 exige el AssetManager como primer argumento aunque el
            // modelo se cargue por rutas de archivo (no desde assets).
            val created = OfflineRecognizer(appContext.assets, config)
            recognizer = created
            created
        } catch (_: Throwable) {
            try {
                recognizer?.release()
            } catch (_: Throwable) {
            }
            recognizer = null
            null
        }
    }

    private fun createAudioRecord(): AudioRecord? {
        return try {
            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) return null
            val bufferSize = maxOf(minBuffer * 2, READ_CHUNK_SHORTS * 2 * 2)
            val record = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                try {
                    record.release()
                } catch (_: Throwable) {
                }
                return null
            }
            record
        } catch (_: Throwable) {
            null
        }
    }

    /** Hilo que lee el micrófono y alimenta el stream con FloatArray 16 kHz. */
    private fun startFeedThread(targetStream: OfflineStream) {
        val record = audioRecord ?: return
        val thread = Thread(
            {
                val shortBuffer = ShortArray(READ_CHUNK_SHORTS)
                while (recording && !released) {
                    val read = try {
                        record.read(shortBuffer, 0, shortBuffer.size)
                    } catch (_: Throwable) {
                        break
                    }
                    if (read < 0) break
                    if (read == 0) continue
                    val samples = FloatArray(read) { shortBuffer[it] / 32768.0f }
                    try {
                        targetStream.acceptWaveform(samples, SAMPLE_RATE)
                    } catch (_: Throwable) {
                        break
                    }
                }
            },
            "sherpa-mic-feed"
        )
        feedThread = thread
        thread.start()
    }

    /** Para y libera el AudioRecord (el hilo de lectura termina solo). */
    private fun stopAudioOnly() {
        recording = false
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                record.stop()
            } catch (_: Throwable) {
            }
            try {
                record.release()
            } catch (_: Throwable) {
            }
        }
        feedThread = null
    }

    private fun releaseAudioRecord() {
        stopAudioOnly()
    }
}

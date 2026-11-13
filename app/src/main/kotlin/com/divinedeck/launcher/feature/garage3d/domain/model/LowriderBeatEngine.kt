package com.divinedeck.launcher.feature.garage3d.domain.model

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Motor de ritmo del Modo Lowrider (DivineDeck 06.11).
 *
 * Escucha la música que suena en el dispositivo con un [Visualizer] y sigue
 * el beat con hidráulicos de verdad (resorte amortiguado). Modos:
 *
 * - [Mode.BEAT]: Visualizer funcionando (sesión 0 = mezcla de salida del
 *   sistema, cualquier app; o la sesión real del ExoPlayer de BitChord).
 *   Onsets reales de la FFT mueven el resorte.
 * - [Mode.CLOCK]: ningún Visualizer funciona en este dispositivo (p. ej.
 *   Tab A11: `Visualizer(0)` falla aunque el permiso esté concedido). El
 *   motor NO se queda parado: mientras suena música
 *   (`AudioManager.isMusicActive()`), inyecta impulsos a ritmo fijo
 *   (~100 BPM) por el MISMO resorte y pulsa `beatEnergy`, así el Chevy
 *   brinca y el underglow late. Sin música, no inyecta nada y la energía
 *   decae. Es un ritmo constante, NO está sincronizado con el beat real.
 * - [Mode.STOPPED]: motor detenido.
 *
 * Arranque ([start]): (i) `Visualizer(0)` con el tamaño de captura ajustado
 * al rango del dispositivo; (ii) `Visualizer(sessionId)` con la sesión real
 * de BitChord; (iii) modo CLOCK si todo falla. Cada intento fallido registra
 * el último error en [lastError] (clase + mensaje) y se conserva en CLOCK
 * para que Ajustes pueda mostrar por qué no hay beat real.
 *
 * [setPlaybackSessionId] permite subir de CLOCK a BEAT cuando el
 * reproductor publica su sesión de audio (al crearse o al cambiar).
 *
 * - Energía de banda baja (50–180 Hz, el grave donde vive el beat) a partir de
 *   la FFT. Detección de inicio (onset): la energía actual supera claramente
 *   su media móvil (> 1.3x) y está por encima del umbral de silencio.
 * - Cada onset inyecta un impulso en un resorte amortiguado (posición +
 *   velocidad) que se integra con `dt` en el hilo GL: la carrocería sube y
 *   vuelve a asentarse como unos hidráulicos reales. Pico limitado a ~0.18 m.
 * - [beatEnergy] es la energía normalizada suavizada (0..1) para el pulso del
 *   underglow.
 *
 * El permiso RECORD_AUDIO es obligatorio para el Visualizer sobre la sesión 0
 * (sin él, el constructor lanza). NO se graba ni se guarda ningún audio: solo
 * se lee la energía de la FFT en memoria. Si el Visualizer falla en un
 * dispositivo concreto, el motor entra en modo CLOCK y expone el fallo en
 * [lastError] sin tumbar la app.
 *
 * Hilos: los callbacks del Visualizer llegan fuera del hilo GL; todo el estado
 * compartido es @Volatile o se protege con [lock]. El ticker del modo CLOCK
 * corre en el hilo principal con un [Handler].
 */
object LowriderBeatEngine {

    enum class Mode { STOPPED, BEAT, CLOCK }

    // --- Resorte amortiguado (hidráulicos) ---
    // k = 120 -> frecuencia natural ~1.75 Hz; amortiguamiento ~0.28 -> rebote
    // visible que se asienta en ~1 s. Impulso de 0.5–1.5 m/s -> pico ~0.05–0.14 m.
    private const val SPRING_K = 120f
    private const val SPRING_DAMPING = 6.1f
    private const val MAX_BOUNCE_METERS = 0.18f
    private const val MIN_BOUNCE_METERS = -0.03f

    // --- Detección de beat ---
    private const val ONSET_RATIO = 1.3f
    private const val SILENCE_FLOOR = 6f // Magnitud media FFT por debajo = silencio
    private const val MIN_ONSET_INTERVAL_NANOS = 140_000_000L // Anti-rebote del beat
    private const val LOW_FREQ_HZ = 50f
    private const val HIGH_FREQ_HZ = 180f

    // --- Visualizer ---
    private const val REQUESTED_CAPTURE_SIZE = 1024

    // --- Modo CLOCK (ritmo de respaldo) ---
    private const val CLOCK_INTERVAL_MS = 600L // ~100 BPM: un impulso cada 600 ms
    private const val CLOCK_IMPULSE = 1.2f // m/s, dentro del rango de un onset real (0.5–1.5)
    private const val CLOCK_ENERGY_PULSE = 0.85f // Pulso de underglow por impulso
    private const val CLOCK_ENERGY_DECAY = 0.8f // Decaimiento de beatEnergy por tick sin música

    @Volatile var bounceY: Float = 0f
        private set

    @Volatile var beatEnergy: Float = 0f
        private set

    @Volatile var isRunning: Boolean = false
        private set

    /** Modo actual del motor: STOPPED / BEAT (Visualizer) / CLOCK (ritmo fijo). */
    private val _mode = MutableStateFlow(Mode.STOPPED)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    /**
     * Último fallo al arrancar el Visualizer (null = sin fallo). Se conserva
     * en modo CLOCK para que Ajustes muestre por qué no hay beat real; null
     * cuando BEAT funciona o tras un stop() limpio.
     */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val lock = Any()
    private var visualizer: Visualizer? = null
    private var springPosition: Float = 0f
    private var springVelocity: Float = 0f
    private var lowBandAverage: Float = 0f
    private var energyPeak: Float = 0f
    private var lastOnsetNanos: Long = 0L
    private var appContext: Context? = null

    /** Sesión de audio del reproductor BitChord conocida por el motor (0 = aún no disponible). */
    @Volatile
    private var playbackSessionId: Int = 0

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            if (_mode.value != Mode.CLOCK) return
            val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val musicActive = try {
                audioManager?.isMusicActive == true
            } catch (_: Exception) {
                false
            }
            if (musicActive) {
                synchronized(lock) {
                    springVelocity += CLOCK_IMPULSE
                }
                beatEnergy = CLOCK_ENERGY_PULSE
            } else {
                beatEnergy *= CLOCK_ENERGY_DECAY
            }
            clockHandler.postDelayed(this, CLOCK_INTERVAL_MS)
        }
    }

    private val captureListener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer?,
            waveform: ByteArray?,
            samplingRate: Int
        ) {
            // Solo nos interesa la FFT.
        }

        override fun onFftDataCapture(
            visualizer: Visualizer?,
            fft: ByteArray?,
            samplingRate: Int
        ) {
            if (fft == null || fft.size < 4) return
            onFft(fft, samplingRate / 1000f)
        }
    }

    /**
     * Arranca el motor. No-op si ya está en marcha. Nunca lanza.
     * Cadena: (i) Visualizer(0) con captura ajustada al dispositivo,
     * (ii) Visualizer(sesión de BitChord), (iii) modo CLOCK si todo falla.
     */
    @Synchronized
    fun start(context: Context) {
        if (isRunning) return
        appContext = context.applicationContext
        _lastError.value = null
        if (tryStartVisualizer(0)) return
        playbackSessionId = DivineDeckPlaybackService.playbackAudioSessionId
        if (playbackSessionId > 0 && tryStartVisualizer(playbackSessionId)) return
        enterClockMode()
    }

    /** Detiene el motor, libera el Visualizer, para el reloj y pone el estado a cero. */
    @Synchronized
    fun stop() {
        stopClock()
        releaseQuietly()
        synchronized(lock) {
            springPosition = 0f
            springVelocity = 0f
            lowBandAverage = 0f
            energyPeak = 0f
        }
        bounceY = 0f
        beatEnergy = 0f
        isRunning = false
        _mode.value = Mode.STOPPED
        _lastError.value = null
        appContext = null
    }

    /**
     * Registra la sesión de audio del reproductor BitChord. Si el motor está
     * en modo CLOCK y la sesión es válida (> 0), intenta subir a BEAT con
     * `Visualizer(sessionId)` (para el reloj primero); si falla, sigue en
     * CLOCK con el error actualizado. Sin efecto en STOPPED o BEAT.
     */
    @Synchronized
    fun setPlaybackSessionId(sessionId: Int) {
        playbackSessionId = sessionId
        if (_mode.value != Mode.CLOCK || sessionId <= 0) return
        stopClock()
        if (tryStartVisualizer(sessionId)) return
        enterClockMode()
    }

    /**
     * Avanza el resorte con el `dt` del frame (lo llama el controlador de
     * efectos desde el hilo GL). Sin llamadas, el rebote se queda congelado.
     */
    fun advance(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.001f, 0.08f)
        synchronized(lock) {
            springVelocity += (-SPRING_K * springPosition - SPRING_DAMPING * springVelocity) * dt
            springPosition += springVelocity * dt
            if (springPosition > MAX_BOUNCE_METERS) {
                springPosition = MAX_BOUNCE_METERS
                if (springVelocity > 0f) springVelocity = 0f
            } else if (springPosition < MIN_BOUNCE_METERS) {
                springPosition = MIN_BOUNCE_METERS
                if (springVelocity < 0f) springVelocity = 0f
            }
            bounceY = springPosition
        }
    }

    /**
     * Intenta arrancar un Visualizer sobre [sessionId] con el tamaño de
     * captura ajustado al rango del dispositivo. Devuelve true si quedó en
     * modo BEAT; en caso contrario registra el fallo (clase + mensaje) en
     * [lastError], libera lo creado a medias y devuelve false.
     */
    private fun tryStartVisualizer(sessionId: Int): Boolean {
        try {
            val viz = Visualizer(sessionId)
            viz.captureSize = clampedCaptureSize(REQUESTED_CAPTURE_SIZE)
            viz.setDataCaptureListener(
                captureListener,
                Visualizer.getMaxCaptureRate(),
                false, // waveform: no
                true // fft: sí
            )
            viz.enabled = true
            visualizer = viz
            isRunning = true
            _mode.value = Mode.BEAT
            _lastError.value = null
            return true
        } catch (e: Exception) {
            _lastError.value = "${e.javaClass.simpleName}: ${e.message}"
            releaseQuietly()
            return false
        }
    }

    /**
     * Tamaño de captura pedido (1024) ajustado al rango que soporta el
     * dispositivo (`Visualizer.getCaptureSizeRange()`) y redondeado a potencia
     * de dos, como exige el Visualizer.
     */
    private fun clampedCaptureSize(requested: Int): Int {
        val range = runCatching { Visualizer.getCaptureSizeRange() }.getOrNull()
        val min = if (range != null && range.size == 2) range[0] else 128
        val max = if (range != null && range.size == 2) range[1] else 1024
        val clamped = requested.coerceIn(min, max)
        var pow = Integer.highestOneBit(clamped)
        if (pow < min) {
            // Subir a la siguiente potencia de dos que cumpla el mínimo.
            pow = if (min <= 1) 1 else Integer.highestOneBit(min - 1) shl 1
        }
        if (pow > max) {
            pow = Integer.highestOneBit(max)
        }
        return pow.coerceAtLeast(1)
    }

    /**
     * Entra en modo CLOCK: ritmo fijo por Handler mientras suena música.
     * [lastError] se conserva para explicar por qué no hay beat real.
     */
    private fun enterClockMode() {
        _mode.value = Mode.CLOCK
        isRunning = true
        clockHandler.removeCallbacks(clockTick)
        clockHandler.post(clockTick)
    }

    private fun stopClock() {
        clockHandler.removeCallbacks(clockTick)
    }

    private fun onFft(fft: ByteArray, sampleRateHz: Float) {
        val binCount = fft.size / 2
        if (binCount < 2 || sampleRateHz <= 0f) return
        val binHz = sampleRateHz / fft.size.toFloat()

        var sum = 0f
        var bins = 0
        for (i in 1 until binCount) {
            val freq = i * binHz
            if (freq < LOW_FREQ_HZ || freq > HIGH_FREQ_HZ) continue
            val re = fft[2 * i].toInt()
            val im = fft[2 * i + 1].toInt()
            sum += sqrt((re * re + im * im).toFloat())
            bins++
        }
        if (bins == 0) {
            // Muestreo inesperado: usa los primeros graves como respaldo.
            for (i in 1..minOf(4, binCount - 1)) {
                val re = fft[2 * i].toInt()
                val im = fft[2 * i + 1].toInt()
                sum += sqrt((re * re + im * im).toFloat())
                bins++
            }
        }
        if (bins == 0) return
        val energy = sum / bins

        // Pico de normalización: sube rápido, baja lento.
        energyPeak = maxOf(energyPeak * 0.995f, energy)
        val normalized = if (energyPeak > 0.01f) (energy / energyPeak).coerceIn(0f, 1f) else 0f

        if (energy < SILENCE_FLOOR) {
            beatEnergy = beatEnergy * 0.8f
            lowBandAverage = lowBandAverage * 0.95f
            return
        }
        beatEnergy += (normalized - beatEnergy) * 0.35f

        val now = System.nanoTime()
        val average = lowBandAverage
        val isOnset = average > 0f &&
            energy > average * ONSET_RATIO &&
            now - lastOnsetNanos >= MIN_ONSET_INTERVAL_NANOS
        if (isOnset) {
            lastOnsetNanos = now
            synchronized(lock) {
                springVelocity += 0.5f + 1.0f * normalized
            }
        }
        // La media móvil se actualiza despacio para que el onset destaque.
        lowBandAverage = if (average <= 0f) energy else average * 0.95f + energy * 0.05f
    }

    private fun releaseQuietly() {
        try {
            visualizer?.enabled = false
        } catch (_: Exception) {
        }
        try {
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
    }
}

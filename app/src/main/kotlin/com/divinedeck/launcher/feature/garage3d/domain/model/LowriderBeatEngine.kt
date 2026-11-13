package com.divinedeck.launcher.feature.garage3d.domain.model

import android.content.Context
import android.media.audiofx.Visualizer
import kotlin.math.sqrt

/**
 * Motor de ritmo del Modo Lowrider (DivineDeck 06.11).
 *
 * Escucha la música que suena en el dispositivo con un [Visualizer] sobre la
 * sesión de audio 0 (mezcla de salida del sistema), así que sigue a CUALQUIER
 * app de música (BitChord, Spotify, etc.) sin importar su origen.
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
 * dispositivo concreto, el motor queda detenido y expone el fallo en
 * [lastError] sin tumbar la app.
 *
 * Hilos: los callbacks del Visualizer llegan fuera del hilo GL; todo el estado
 * compartido es @Volatile o se protege con [lock].
 */
object LowriderBeatEngine {

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

    @Volatile var bounceY: Float = 0f
        private set

    @Volatile var beatEnergy: Float = 0f
        private set

    @Volatile var isRunning: Boolean = false
        private set

    /** Último fallo al arrancar el Visualizer (null = sin fallo). Visible para Ajustes. */
    @Volatile var lastError: String? = null
        private set

    private val lock = Any()
    private var visualizer: Visualizer? = null
    private var springPosition: Float = 0f
    private var springVelocity: Float = 0f
    private var lowBandAverage: Float = 0f
    private var energyPeak: Float = 0f
    private var lastOnsetNanos: Long = 0L

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
     * Arranca la escucha del beat. No-op si ya está en marcha. Nunca lanza:
     * si el Visualizer no se puede crear (permiso, dispositivo), queda
     * detenido y registra el fallo en [lastError].
     */
    @Synchronized
    fun start(context: Context) {
        if (isRunning) return
        lastError = null
        try {
            val viz = Visualizer(0)
            val captureSize = 1024
            viz.captureSize = captureSize
            viz.setDataCaptureListener(
                captureListener,
                Visualizer.getMaxCaptureRate(),
                false, // waveform: no
                true // fft: sí
            )
            viz.enabled = true
            visualizer = viz
            isRunning = true
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            releaseQuietly()
            isRunning = false
        }
    }

    /** Detiene la escucha, libera el Visualizer y pone el estado a cero. */
    @Synchronized
    fun stop() {
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

package com.divinedeck.launcher.feature.garage3d.domain.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Parámetros calculados en tiempo real para los Dynamic Speed Effects 3D del Chevrolet Chevy C2 2009 (Fase 4.1C).
 */
data class SpeedEffectsParameters(
    val visualMode: SpeedVisualMode = SpeedVisualMode.NORMAL,
    val wheelAngle: Float = 0f,
    val speedKmh: Float = 0f,
    val effectIntensity: Float = 0f,
    val rimEmissiveR: Float = 0f,
    val rimEmissiveG: Float = 0f,
    val rimEmissiveB: Float = 0f,
    val brakeEmissiveR: Float = 0f,
    val brakeEmissiveG: Float = 0f,
    val brakeEmissiveB: Float = 0f,
    val underglowR: Float = 0f,
    val underglowG: Float = 0.55f,
    val underglowB: Float = 1.0f,
    val underglowIntensity: Float = 0f,
    val exhaustGlow: Float = 0f,
    val bodyWarmth: Float = 0f,
    val vibrationY: Float = 0f,
    val vibrationPitch: Float = 0f,
    val bounceY: Float = 0f,
    val beatPulse: Float = 0f,
    val timeSeconds: Float = 0f,
    val particleSystem: AutomotiveParticleSystem? = null
) {
    val hasActiveEffects: Boolean get() = effectIntensity > 0.001f || speedKmh > 1.0f
}

/**
 * Controlador cinemático y máquina de estados para los Dynamic Speed Effects 3D (Fase 4.1C).
 *
 * Integra:
 * - Rotación cinemática continua de las 4 ruedas sincronizada con velocidad GPS real (omega = v / r).
 * - Iluminación neón inferior ambiental proyectada en el suelo (Underglow) con halo difuso y transiciones suaves de color.
 * - Vibración sutil del chasis por velocidad independiente de la interfaz y sin afectar 360° ni zoom.
 * - Simulación de partículas de escape, humo de neumáticos y estelas aerodinámicas traseras.
 * - Respeto innegociable a los materiales y colores originales de fábrica (rines plata aleación, carrocería gris metalizado).
 * - Histéresis de 2.0 km/h y retorno suave de seguridad ante pérdida de señal GPS.
 */
class DynamicSpeedEffectsController {

    val particleSystem = AutomotiveParticleSystem()

    private var activeMode: SpeedVisualMode = SpeedVisualMode.NORMAL
    private var accumulatedTime: Float = 0f
    private var currentWheelAngle: Float = 0f
    private var smoothedSpeedKmh: Float = 0f

    // Estado suavizado actual
    private var currentIntensity: Float = 0f
    private var currentBrakeR: Float = 0f
    private var currentBrakeG: Float = 0f
    private var currentBrakeB: Float = 0f
    private var currentExhaustGlow: Float = 0f

    // Underglow actual suavizado
    private var currentUnderglowR: Float = 0f
    private var currentUnderglowG: Float = 0.55f
    private var currentUnderglowB: Float = 1.0f
    private var currentUnderglowIntensity: Float = 0.05f

    // Vibración de chasis suavizada
    private var currentVibrationAmp: Float = 0f
    private var currentVibrationPitch: Float = 0f

    // Modo Lowrider: rebote hidráulico y pulso del beat (suavizados)
    private var currentBounceY: Float = 0f
    private var currentBeatPulse: Float = 0f

    val currentParameters: SpeedEffectsParameters
        get() = SpeedEffectsParameters(
            visualMode = activeMode,
            wheelAngle = currentWheelAngle,
            speedKmh = smoothedSpeedKmh,
            effectIntensity = currentIntensity,
            rimEmissiveR = 0f, // Rines conservan 100% sus materiales y brillo de aleación original (Regla 1)
            rimEmissiveG = 0f,
            rimEmissiveB = 0f,
            brakeEmissiveR = currentBrakeR,
            brakeEmissiveG = currentBrakeG,
            brakeEmissiveB = currentBrakeB,
            underglowR = currentUnderglowR,
            underglowG = currentUnderglowG,
            underglowB = currentUnderglowB,
            // El beat suma un pulso al underglow (encima del underglow por velocidad).
            underglowIntensity = (currentUnderglowIntensity + currentBeatPulse * 0.35f).coerceIn(0f, 1f),
            exhaustGlow = currentExhaustGlow,
            bodyWarmth = 0f,
            vibrationY = calculateVibrationY(),
            vibrationPitch = calculateVibrationPitch(),
            bounceY = currentBounceY,
            beatPulse = currentBeatPulse,
            timeSeconds = accumulatedTime,
            particleSystem = particleSystem
        )

    private fun calculateVibrationY(): Float {
        if (smoothedSpeedKmh < 0.5f || currentVibrationAmp < 0.00001f) return 0f
        val freq = activeMode.config.vibrationBaseFreqHz
        return (sin(accumulatedTime * freq * 2.0f * PI.toFloat()) +
                0.35f * cos(accumulatedTime * freq * 3.4f * PI.toFloat())) * currentVibrationAmp
    }

    private fun calculateVibrationPitch(): Float {
        if (smoothedSpeedKmh < 0.5f || currentVibrationPitch < 0.0001f) return 0f
        val freq = activeMode.config.vibrationBaseFreqHz
        return sin(accumulatedTime * freq * 1.8f * PI.toFloat()) * currentVibrationPitch
    }

    /**
     * Actualiza el estado de los efectos según la velocidad GPS real y la fiabilidad de la señal,
     * o mediante un override temporal de depuración (Fase 4.1C).
     */
    fun update(
        dtSeconds: Float,
        speedKmh: Float,
        isGpsReliable: Boolean,
        debugModeOverride: SpeedVisualMode? = null,
        lowriderEnabled: Boolean = false
    ): SpeedEffectsParameters {
        val dt = dtSeconds.coerceIn(0.001f, 0.08f)
        accumulatedTime += dt

        // 1. Determinar el modo objetivo
        val targetMode = if (debugModeOverride != null) {
            debugModeOverride
        } else if (isGpsReliable) {
            SpeedVisualMode.fromSpeed(speedKmh, activeMode, hysteresis = 2.0f)
        } else {
            SpeedVisualMode.NORMAL
        }
        activeMode = targetMode
        val cfg = targetMode.config

        // 2. Suavizado de velocidad para la cinemática de ruedas (filtro crítico sin saltos ni aceleraciones bruscas)
        val targetSpeed = if (isGpsReliable || debugModeOverride != null) speedKmh.coerceAtLeast(0f) else 0f
        val speedAlpha = (1.0f - exp(-dt * 4.2f)).coerceIn(0.05f, 0.60f)
        smoothedSpeedKmh = lerp(smoothedSpeedKmh, targetSpeed, speedAlpha)
        if (targetSpeed == 0f && smoothedSpeedKmh < 0.8f) {
            smoothedSpeedKmh = 0f
        }

        // 3. Cinemática real de giro de ruedas: omega = v / r (r_neumático = 0.295 m)
        val vMps = smoothedSpeedKmh / 3.6f
        val omega = vMps / 0.295f
        currentWheelAngle = (currentWheelAngle + omega * dt) % (2.0f * PI.toFloat())

        // 4. Intensidad de efectos de luz y frenos por modo
        val targetIntensity = when (targetMode) {
            SpeedVisualMode.NORMAL -> 0.10f
            SpeedVisualMode.CORREDOR -> 0.55f
            SpeedVisualMode.ADRENALINA -> 0.82f
            SpeedVisualMode.COHETE -> 1.00f
        }

        val targetBrakeR = when (targetMode) {
            SpeedVisualMode.NORMAL -> 0f
            SpeedVisualMode.CORREDOR -> 0.35f
            SpeedVisualMode.ADRENALINA -> 0.75f
            SpeedVisualMode.COHETE -> 0.98f
        }
        val targetBrakeG = when (targetMode) {
            SpeedVisualMode.NORMAL -> 0f
            SpeedVisualMode.CORREDOR -> 0.12f
            SpeedVisualMode.ADRENALINA -> 0.18f
            SpeedVisualMode.COHETE -> 0.30f
        }
        val targetBrakeB = when (targetMode) {
            SpeedVisualMode.NORMAL -> 0f
            SpeedVisualMode.CORREDOR -> 0.02f
            SpeedVisualMode.ADRENALINA -> 0.03f
            SpeedVisualMode.COHETE -> 0.05f
        }

        val targetExhaustGlow = when (targetMode) {
            SpeedVisualMode.NORMAL -> 0f
            SpeedVisualMode.CORREDOR -> 0.25f
            SpeedVisualMode.ADRENALINA -> 0.65f
            SpeedVisualMode.COHETE -> 1.00f
        }

        // 5. Iluminación neón inferior (Underglow - Parte D)
        // A velocidad 0, muy discreta (0.05). Al acelerar, incrementa progresivamente
        val speedFactor = (smoothedSpeedKmh / 100f).coerceIn(0f, 1f)
        val targetUnderglowIntensity = if (smoothedSpeedKmh < 1.0f) {
            0.05f // Muy discreta en reposo
        } else {
            lerp(0.18f, cfg.underglowBaseIntensity, speedFactor)
        }
        val targetUnderglowR = cfg.underglowR
        val targetUnderglowG = cfg.underglowG
        val targetUnderglowB = cfg.underglowB

        // 6. Vibración sutil del chasis por velocidad (Parte E)
        val speedRatio = (smoothedSpeedKmh / 160f).coerceIn(0f, 1f)
        val targetVibAmp = if (smoothedSpeedKmh < 1.0f) 0f else cfg.vibrationMaxAmpMeters * speedRatio
        val targetVibPitch = if (smoothedSpeedKmh < 1.0f) 0f else cfg.vibrationMaxPitchDeg * speedRatio

        // 7. Suavizado exponencial independiente de FPS
        val lambda = if (!isGpsReliable && debugModeOverride == null || targetMode == SpeedVisualMode.NORMAL) 3.5f else 4.8f
        val alpha = (1.0f - exp(-dt * lambda)).coerceIn(0.01f, 0.50f)

        currentIntensity = lerp(currentIntensity, targetIntensity, alpha)
        currentBrakeR = lerp(currentBrakeR, targetBrakeR, alpha)
        currentBrakeG = lerp(currentBrakeG, targetBrakeG, alpha)
        currentBrakeB = lerp(currentBrakeB, targetBrakeB, alpha)
        currentExhaustGlow = lerp(currentExhaustGlow, targetExhaustGlow, alpha)

        currentUnderglowIntensity = lerp(currentUnderglowIntensity, targetUnderglowIntensity, alpha)
        currentUnderglowR = lerp(currentUnderglowR, targetUnderglowR, alpha)
        currentUnderglowG = lerp(currentUnderglowG, targetUnderglowG, alpha)
        currentUnderglowB = lerp(currentUnderglowB, targetUnderglowB, alpha)

        currentVibrationAmp = lerp(currentVibrationAmp, targetVibAmp, alpha)
        currentVibrationPitch = lerp(currentVibrationPitch, targetVibPitch, alpha)

        // 7b. Modo Lowrider: hidráulicos al ritmo de la música (Visualizer, sesión 0).
        // Con el modo apagado (o sin música / sin energía) el rebote y el pulso
        // decaen suavemente a 0; la lógica de velocidad de arriba queda intacta.
        val beatAlpha = (1.0f - exp(-dt * 8f)).coerceIn(0.01f, 0.50f)
        val bounceAlpha = (1.0f - exp(-dt * 10f)).coerceIn(0.01f, 0.50f)
        if (lowriderEnabled) {
            LowriderBeatEngine.advance(dt)
            val energy = LowriderBeatEngine.beatEnergy
            currentBounceY = if (energy >= 0.08f) {
                LowriderBeatEngine.bounceY
            } else {
                lerp(currentBounceY, 0f, bounceAlpha)
            }
            currentBeatPulse = lerp(currentBeatPulse, energy, beatAlpha)
        } else {
            currentBounceY = lerp(currentBounceY, 0f, bounceAlpha)
            currentBeatPulse = lerp(currentBeatPulse, 0f, beatAlpha)
        }

        if (targetIntensity == 0f && currentIntensity < 0.002f) {
            resetToNormal()
        }

        // 8. Actualizar simulación de partículas y estelas aerodinámicas
        particleSystem.update(dt, smoothedSpeedKmh, activeMode)

        return currentParameters
    }

    fun forceMode(mode: SpeedVisualMode) {
        activeMode = mode
    }

    fun resetToNormal() {
        activeMode = SpeedVisualMode.NORMAL
        currentIntensity = 0f
        currentBrakeR = 0f
        currentBrakeG = 0f
        currentBrakeB = 0f
        currentExhaustGlow = 0f
        currentUnderglowIntensity = 0.05f
        currentUnderglowR = SpeedVisualMode.NORMAL.config.underglowR
        currentUnderglowG = SpeedVisualMode.NORMAL.config.underglowG
        currentUnderglowB = SpeedVisualMode.NORMAL.config.underglowB
        currentVibrationAmp = 0f
        currentVibrationPitch = 0f
        currentBounceY = 0f
        currentBeatPulse = 0f
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }
}

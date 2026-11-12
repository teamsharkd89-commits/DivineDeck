package com.divinedeck.launcher.feature.garage3d.domain.model

/**
 * Configuración visual centralizada para cada modo dinámico de velocidad (Fase 4.1C).
 *
 * Centraliza paletas cromáticas, emisiones de neón inferior, parámetros de estelas aerodinámicas,
 * partículas y vibración cinemática de chasis.
 */
data class ModeVisualConfig(
    val title: String,
    val minSpeed: Float,
    val maxSpeed: Float,
    val description: String,
    val colorName: String,
    val primaryHex: String,
    // Componentes RGB normalizados (0.0 .. 1.0) para shaders y OpenGL
    val primaryR: Float,
    val primaryG: Float,
    val primaryB: Float,
    // Iluminación neón inferior (Underglow)
    val underglowR: Float,
    val underglowG: Float,
    val underglowB: Float,
    val underglowBaseIntensity: Float,
    // Partículas (Humo y chispas)
    val particleMainR: Float,
    val particleMainG: Float,
    val particleMainB: Float,
    val particleSecR: Float,
    val particleSecG: Float,
    val particleSecB: Float,
    // Estela aerodinámica trasera (Aero ribbons)
    val trailR: Float,
    val trailG: Float,
    val trailB: Float,
    val trailBaseLength: Float, // metros
    // Parámetros de vibración sutil por velocidad
    val vibrationMaxAmpMeters: Float, // Amplitud máxima en metros (Y)
    val vibrationMaxPitchDeg: Float,  // Inclinación máxima en grados
    val vibrationBaseFreqHz: Float    // Frecuencia base en Hertz
)

/**
 * Modos dinámicos oficiales de velocidad para el Chevrolet Chevy C2 2009 (Fase 4.1C).
 *
 * | Modo       | Velocidad         | Color        |
 * | NORMAL     | 0–59 km/h         | Azul         |
 * | CORREDOR   | 60–79 km/h        | Amarillo     |
 * | ADRENALINA | 80–99 km/h        | Rojo         |
 * | COHETE     | 100 km/h o más    | Rojo intenso |
 */
enum class SpeedVisualMode(val config: ModeVisualConfig) {
    NORMAL(
        ModeVisualConfig(
            title = "Normal",
            minSpeed = 0f,
            maxSpeed = 59.9f,
            description = "Conducción urbana estándar. Iluminación azul discreta.",
            colorName = "Azul",
            primaryHex = "#0088FF",
            primaryR = 0.00f, primaryG = 0.55f, primaryB = 1.00f,
            underglowR = 0.00f, underglowG = 0.50f, underglowB = 1.00f,
            underglowBaseIntensity = 0.25f,
            particleMainR = 0.00f, particleMainG = 0.70f, particleMainB = 1.00f,
            particleSecR = 0.00f, particleSecG = 0.40f, particleSecB = 0.90f,
            trailR = 0.95f, trailG = 0.15f, trailB = 0.10f, // Estela roja conservada
            trailBaseLength = 0.65f,
            vibrationMaxAmpMeters = 0.0003f, // 0.3 mm (imperceptible en reposo)
            vibrationMaxPitchDeg = 0.02f,
            vibrationBaseFreqHz = 20f
        )
    ),
    CORREDOR(
        ModeVisualConfig(
            title = "Corredor",
            minSpeed = 60f,
            maxSpeed = 79.9f,
            description = "Tráfico ágil y dinamismo. Iluminación amarillo pista.",
            colorName = "Amarillo",
            primaryHex = "#FFC800",
            primaryR = 1.00f, primaryG = 0.78f, primaryB = 0.00f,
            underglowR = 1.00f, underglowG = 0.80f, underglowB = 0.00f,
            underglowBaseIntensity = 0.60f,
            particleMainR = 1.00f, particleMainG = 0.82f, particleMainB = 0.02f,
            particleSecR = 1.00f, particleSecG = 0.58f, particleSecB = 0.00f,
            trailR = 0.98f, trailG = 0.22f, trailB = 0.08f,
            trailBaseLength = 1.45f,
            vibrationMaxAmpMeters = 0.0009f, // 0.9 mm (vibración ligera)
            vibrationMaxPitchDeg = 0.06f,
            vibrationBaseFreqHz = 28f
        )
    ),
    ADRENALINA(
        ModeVisualConfig(
            title = "Adrenalina",
            minSpeed = 80f,
            maxSpeed = 99.9f,
            description = "Velocidad crucero de autopista. Iluminación roja viva.",
            colorName = "Rojo",
            primaryHex = "#FF2200",
            primaryR = 1.00f, primaryG = 0.13f, primaryB = 0.00f,
            underglowR = 1.00f, underglowG = 0.14f, underglowB = 0.00f,
            underglowBaseIntensity = 0.82f,
            particleMainR = 1.00f, particleMainG = 0.18f, particleMainB = 0.02f,
            particleSecR = 1.00f, particleSecG = 0.40f, particleSecB = 0.04f,
            trailR = 1.00f, trailG = 0.12f, trailB = 0.02f,
            trailBaseLength = 2.25f,
            vibrationMaxAmpMeters = 0.0018f, // 1.8 mm (más perceptible)
            vibrationMaxPitchDeg = 0.12f,
            vibrationBaseFreqHz = 34f
        )
    ),
    COHETE(
        ModeVisualConfig(
            title = "Cohete",
            minSpeed = 100f,
            maxSpeed = Float.MAX_VALUE,
            description = "Máxima exigencia en carretera. Iluminación rojo intenso.",
            colorName = "Rojo intenso",
            primaryHex = "#FF0800",
            primaryR = 1.00f, primaryG = 0.03f, primaryB = 0.00f,
            underglowR = 1.00f, underglowG = 0.04f, underglowB = 0.00f,
            underglowBaseIntensity = 1.00f,
            particleMainR = 1.00f, particleMainG = 0.08f, particleMainB = 0.00f,
            particleSecR = 1.00f, particleSecG = 0.28f, particleSecB = 0.01f,
            trailR = 1.00f, trailG = 0.04f, trailB = 0.00f,
            trailBaseLength = 3.40f,
            vibrationMaxAmpMeters = 0.0028f, // 2.8 mm (mayor intensidad, controlada y suave)
            vibrationMaxPitchDeg = 0.18f,
            vibrationBaseFreqHz = 40f
        )
    );

    // Accesores directos de conveniencia para mantener compatibilidad de API
    val title: String get() = config.title
    val minSpeed: Float get() = config.minSpeed
    val maxSpeed: Float get() = config.maxSpeed
    val description: String get() = config.description
    val colorHex: String get() = config.primaryHex

    companion object {
        /**
         * Determina el modo visual considerando un margen de histéresis de 2 km/h
         * para evitar parpadeos continuos si la velocidad fluctúa sobre el límite.
         */
        fun fromSpeed(speed: Float, currentMode: SpeedVisualMode, hysteresis: Float = 2.0f): SpeedVisualMode {
            return when (currentMode) {
                NORMAL -> when {
                    speed >= 100f -> COHETE
                    speed >= 80f -> ADRENALINA
                    speed >= 60f -> CORREDOR
                    else -> NORMAL
                }
                CORREDOR -> when {
                    speed < (60f - hysteresis) -> NORMAL
                    speed >= 100f -> COHETE
                    speed >= 80f -> ADRENALINA
                    else -> CORREDOR
                }
                ADRENALINA -> when {
                    speed < (60f - hysteresis) -> NORMAL
                    speed < (80f - hysteresis) -> CORREDOR
                    speed >= 100f -> COHETE
                    else -> ADRENALINA
                }
                COHETE -> when {
                    speed < (60f - hysteresis) -> NORMAL
                    speed < (80f - hysteresis) -> CORREDOR
                    speed < (100f - hysteresis) -> ADRENALINA
                    else -> COHETE
                }
            }
        }
    }
}

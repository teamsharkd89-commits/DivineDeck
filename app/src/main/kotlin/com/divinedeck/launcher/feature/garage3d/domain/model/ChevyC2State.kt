package com.divinedeck.launcher.feature.garage3d.domain.model

/**
 * Vistas de cámara predefinidas para el control compacto de cambio de vista (Fase 3.1 - Parte B).
 * Cada pulsación alterna secuencialmente entre:
 * 1. Perspectiva 3/4 -> 2. Vista frontal -> 3. Vista lateral -> 4. Vista trasera -> 1. Perspectiva 3/4.
 */
enum class CameraPreset(
    val label: String,
    val shortLabel: String,
    val yaw: Float,
    val pitch: Float,
    val zoom: Float
) {
    PERSPECTIVE_3_4(
        label = "Perspectiva 3/4",
        shortLabel = "VISTA: 3/4",
        yaw = 45f,
        pitch = 10f,
        zoom = 1.0f
    ),
    FRONT(
        label = "Vista frontal",
        shortLabel = "VISTA: FRENTE",
        yaw = 0f,
        pitch = 7f,
        zoom = 1.06f
    ),
    SIDE(
        label = "Vista lateral",
        shortLabel = "VISTA: LATERAL",
        yaw = 90f,
        pitch = 5f,
        zoom = 0.98f
    ),
    REAR(
        label = "Vista trasera",
        shortLabel = "VISTA: TRASERA",
        yaw = 180f,
        pitch = 9f,
        zoom = 1.06f
    );

    fun next(): CameraPreset {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

data class ChevyC2State(
    val rotationYaw: Float = 45f,
    val rotationPitch: Float = 10f,
    val zoomLevel: Float = 1.0f,
    val activePreset: CameraPreset = CameraPreset.PERSPECTIVE_3_4,
    val bodyColorHex: String = "#B8C2CA", // Gris Metálico Chevrolet Chevy C2 2009
    val visualMode: SpeedVisualMode = SpeedVisualMode.NORMAL,
    val currentSpeedKmh: Float = 0f,
    val isGpsReliable: Boolean = false,
    val isAutoRotating: Boolean = true,
    val areHeadlightsOn: Boolean = true,
    val debugVisualModeOverride: SpeedVisualMode? = null,
    val debugSimulatedSpeedKmh: Float? = null,
    val assetPath: String = "models/chevy_c2_2009.glb"
)

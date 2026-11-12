package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Lectura calculada y filtrada del velocímetro digital (Fase 4.0 Correctiva).
 *
 * @param currentSpeedKmh Velocidad actual procesada, suavizada y calibrada en km/h.
 * @param maxSpeedKmh Velocidad máxima registrada en la sesión actual de conducción.
 * @param avgSpeedKmh Velocidad promedio registrada en la sesión actual.
 * @param accuracyMeters Precisión horizontal estimada por el receptor GNSS en metros.
 * @param isReliable Indica si la lectura cumple con todos los filtros de frescura y precisión.
 * @param rawSpeedKmh Velocidad sin filtrar entregada por el sensor o derivada antes del filtro EMA.
 * @param source Origen de la lectura (ej. "FUSED_PLAY_SERVICES", "GNSS_HARDWARE", "NETWORK_PROVIDER", "DERIVED_CALCULATED", "ZERO_STANDSTILL").
 * @param timestamp Marca de tiempo del sistema en milisegundos.
 * @param movementState Estado de movimiento detectado por el motor FuelSense (STATIONARY, POSSIBLY_MOVING, MOVING).
 * @param speedAccuracyMps Precisión de velocidad en metros por segundo entregada por Android (si está disponible).
 */
data class SpeedReading(
    val currentSpeedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val accuracyMeters: Float = 0f,
    val isReliable: Boolean = false,
    val rawSpeedKmh: Float = currentSpeedKmh,
    val source: String = "GNSS",
    val timestamp: Long = System.currentTimeMillis(),
    val movementState: MovementState = MovementState.STATIONARY,
    val speedAccuracyMps: Float? = null
)

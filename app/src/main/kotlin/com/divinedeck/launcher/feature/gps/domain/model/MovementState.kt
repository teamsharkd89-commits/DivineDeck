package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Estados de movimiento del vehículo adaptados directamente del motor de viaje FuelSense
 * (Fase 4.5A Correctiva).
 *
 * - UNKNOWN: Estado no consolidado (arranque, timeout > 3500ms, glitches consecutivos o contradicción sensorial).
 * - STATIONARY: Vehículo detenido (en reposo, estacionado o en semáforo confirmado por tiempo y espacio).
 * - POSSIBLY_MOVING: Evidencia incipiente de movimiento bajo evaluación de ventana y compuertas espaciales.
 * - MOVING: Movimiento vehicular confirmado; lecturas válidas de aceleración y marcha.
 */
enum class MovementState(val displayName: String) {
    UNKNOWN("UNKNOWN"),
    STATIONARY("STATIONARY"),
    POSSIBLY_MOVING("POSSIBLY_MOVING"),
    MOVING("MOVING")
}

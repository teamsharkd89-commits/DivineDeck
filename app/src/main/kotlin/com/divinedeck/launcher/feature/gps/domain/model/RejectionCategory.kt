package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Categorías de filtrado y rechazo técnico de muestras de ubicación GNSS
 * adaptadas directamente de FuelSense.
 */
enum class RejectionCategory(val label: String) {
    POOR_ACCURACY("POOR_ACCURACY"),
    GPS_DRIFT("GPS_DRIFT"),
    IMPOSSIBLE_SPEED("IMPOSSIBLE_SPEED"),
    INVALID_TIMESTAMP("INVALID_TIMESTAMP"),
    DUPLICATE_POSITION("DUPLICATE_POSITION"),
    INSUFFICIENT_MOVEMENT_EVIDENCE("INSUFFICIENT_MOVEMENT_EVIDENCE"),
    NONE("NONE")
}

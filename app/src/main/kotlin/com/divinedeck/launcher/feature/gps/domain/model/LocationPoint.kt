package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Representación inmutable de una posición geográfica con metadatos asociados,
 * desacoplada del framework Android para posibilitar pruebas unitarias 100% deterministas.
 * Adaptada de FuelSense.
 */
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeters: Float? = null,
    val speedKmh: Float? = null,
    val bearingDegrees: Float? = null,
    val speedAccuracyMps: Float? = null
)

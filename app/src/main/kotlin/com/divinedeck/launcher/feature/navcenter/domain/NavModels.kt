package com.divinedeck.launcher.feature.navcenter.domain

/**
 * Modelos del mapa embebido de DivineDeck (Navegación).
 * Sin dependencias de MapLibre: la UI convierte [NavLatLng] al tipo del mapa.
 */
data class NavLatLng(
    val latitude: Double,
    val longitude: Double
) {
    /** Formato "lat,lon" para APIs (Photon, Nominatim, Overpass, Valhalla). */
    fun asLatLon(): String = "$latitude,$longitude"
}

enum class PoiKind {
    SEARCH,
    TAPPED,
    FUEL,
    REPAIR,
    HOME,
    WORK
}

/** Un lugar seleccionable en el mapa: resultado de búsqueda, punto tocado o POI cercano. */
data class NavPlace(
    val id: String,
    val name: String,
    val detail: String = "",
    val latLng: NavLatLng,
    val kind: PoiKind
)

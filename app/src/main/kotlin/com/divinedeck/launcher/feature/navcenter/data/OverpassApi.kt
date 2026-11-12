package com.divinedeck.launcher.feature.navcenter.data

import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

/**
 * Puntos de interés cercanos con Overpass (overpass-api.de), gratis y sin llave:
 * gasolineras (amenity=fuel) y talleres mecánicos (shop=car_repair) alrededor
 * de la ubicación actual. Guía de uso: ~10k consultas/día; aquí solo se
 * consulta cuando Daniel toca el chip correspondiente.
 */
object OverpassApi {
    private const val BASE_URL = "https://overpass-api.de/api/interpreter"

    @Serializable
    private data class OverpassResponse(
        val elements: List<OverpassElement> = emptyList()
    )

    @Serializable
    private data class OverpassElement(
        val type: String = "",
        val id: Long = 0,
        val lat: Double? = null,
        val lon: Double? = null,
        // Las vías y relaciones no tienen lat/lon propias: `out center`
        // devuelve su centroide aquí.
        val center: OverpassCenter? = null,
        val tags: Map<String, String> = emptyMap()
    )

    @Serializable
    private data class OverpassCenter(
        val lat: Double? = null,
        val lon: Double? = null
    )

    suspend fun nearby(
        kind: PoiKind,
        center: NavLatLng,
        radiusMeters: Int = 5_000,
        limit: Int = 30
    ): List<NavPlace> {
        require(kind == PoiKind.FUEL || kind == PoiKind.REPAIR) {
            "OverpassApi.nearby solo admite FUEL o REPAIR"
        }
        // Nodos Y vías Y relaciones: muchas gasolineras y talleres están
        // mapeados como vías (el área del predio), no como nodos. Solo
        // nodos, Overpass devolvía 23 gasolineras en 5 km de Tepic; con
        // vías, 30 (7 más, entre ellas "Gas Nayar" y "Global Gas").
        // Verificado en vivo 2026-11-12. `out center` da el centroide
        // de cada vía/relación para poder marcarla en el mapa.
        val tagFilter = if (kind == PoiKind.FUEL) {
            """["amenity"="fuel"]"""
        } else {
            """["shop"="car_repair"]"""
        }
        val query = """
            [out:json][timeout:25];
            (
              node$tagFilter(around:$radiusMeters,${center.latitude},${center.longitude});
              way$tagFilter(around:$radiusMeters,${center.latitude},${center.longitude});
              relation$tagFilter(around:$radiusMeters,${center.latitude},${center.longitude});
            );
            out center $limit;
        """.trimIndent()
        val response: OverpassResponse = NavHttp.client.get(BASE_URL) {
            parameter("data", query)
            header("User-Agent", "DivineDeck/1.0 (Android car launcher; Tepic, Nayarit, MX)")
        }.body()
        val defaultName = if (kind == PoiKind.FUEL) "Gasolinera" else "Taller mecánico"
        return response.elements.mapNotNull { element ->
            // Nodo: lat/lon propias. Vía/relación: centroide (`center`).
            val lat = element.lat ?: element.center?.lat ?: return@mapNotNull null
            val lon = element.lon ?: element.center?.lon ?: return@mapNotNull null
            val tags = element.tags
            val name = tags["name"]?.takeIf { it.isNotBlank() }
                ?: tags["brand"]?.takeIf { it.isNotBlank() }
                ?: defaultName
            val detail = listOfNotNull(
                tags["brand"]?.takeIf { it != name },
                tags["operator"],
                tags["addr:street"]
            ).joinToString(" · ")
            NavPlace(
                // El tipo va en el id: un nodo y una vía pueden compartir
                // el mismo id numérico en OSM y colisionarían.
                id = "overpass:${element.type}:${element.id}",
                name = name,
                detail = detail,
                latLng = NavLatLng(latitude = lat, longitude = lon),
                kind = kind
            )
        }
    }
}

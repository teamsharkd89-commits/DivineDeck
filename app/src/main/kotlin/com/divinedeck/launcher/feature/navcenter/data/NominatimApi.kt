package com.divinedeck.launcher.feature.navcenter.data

import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Reverse-geocodificación con Nominatim (nominatim.openstreetmap.org):
 * convierte un punto tocado en el mapa en una dirección legible.
 *
 * Política de uso oficial (obligatoria): máximo 1 petición/segundo por
 * aplicación, User-Agent propio, y NUNCA usar Nominatim para autocompletado
 * (para eso está Photon). Aquí solo se llama al tocar el mapa o al
 * seleccionar un destino, con un throttle interno de 1.1 s.
 */
object NominatimApi {
    private const val BASE_URL = "https://nominatim.openstreetmap.org/reverse"
    private const val SEARCH_URL = "https://nominatim.openstreetmap.org/search"
    private const val MIN_INTERVAL_MS = 1_100L
    private val mutex = Mutex()
    private var lastCallMs = 0L

    @Serializable
    private data class ReverseResponse(
        val display_name: String? = null
    )

    @Serializable
    private data class SearchResponse(
        val display_name: String? = null,
        val lat: String? = null,
        val lon: String? = null
    )

    /**
     * Búsqueda primaria para direcciones (build 28): Nominatim responde
     * direcciones con número ("begonia 32 fraccionamiento jacarandas"
     * → "Calle Begonia, Fraccionamiento Jacarandas…") donde Photon
     * devuelve ruido. Con timeout de 8 s como Photon: sin él una
     * llamada colgada dejaba el spinner para siempre.
     * Política oficial: Nominatim NO es para autocompletado y permite
     * 1 petición/segundo — solo se llama desde el ViewModel con la
     * consulta debounced, o desde el botón "Reintentar". Nunca por
     * cada tecla.
     */
    suspend fun search(query: String, near: NavLatLng?, limit: Int = 5): List<NavPlace> {
        val response: List<SearchResponse> = withTimeout(8_000) {
            mutex.withLock {
                val now = System.currentTimeMillis()
                val wait = MIN_INTERVAL_MS - (now - lastCallMs)
                if (wait > 0) kotlinx.coroutines.delay(wait)
                lastCallMs = System.currentTimeMillis()
            }
            NavHttp.client.get(SEARCH_URL) {
                parameter("format", "jsonv2")
                parameter("q", query)
                parameter("countrycodes", "mx")
                // `accept-language=es` SÍ es válido en Nominatim (a diferencia
                // del `lang=es` de Photon, que da HTTP 400): verificado en vivo
                // 2026-11-12, HTTP 200 con resultados en español. No tocar.
                parameter("accept-language", "es")
                // Viewbox de Tepic + bounded=1: sin él Nominatim devuelve
                // resultados de todo México. Formato viewbox: minLon,maxLat,
                // maxLon,minLat. Verificado en vivo 2026-11-12 ("bodega
                // aurrera" → 5× Tepic; "parque puerta paraíso" → []).
                parameter(
                    "viewbox",
                    "${OfflineNavConstants.MAP_WEST},${OfflineNavConstants.MAP_NORTH}," +
                        "${OfflineNavConstants.MAP_EAST},${OfflineNavConstants.MAP_SOUTH}"
                )
                parameter("bounded", 1)
                parameter("limit", limit)
                if (near != null) {
                    parameter("lat", near.latitude)
                    parameter("lon", near.longitude)
                }
                header("User-Agent", "DivineDeck/1.0 (Android car launcher; Tepic, Nayarit, MX)")
            }.body()
        }
        return response.mapNotNull { item ->
            val displayName = item.display_name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val lat = item.lat?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = item.lon?.toDoubleOrNull() ?: return@mapNotNull null
            val name = displayName.substringBefore(",").trim().takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            NavPlace(
                id = "nominatim:$lat,$lon:$name",
                name = name,
                detail = displayName,
                latLng = NavLatLng(latitude = lat, longitude = lon),
                kind = PoiKind.SEARCH
            )
        }
    }

    suspend fun reverse(point: NavLatLng): String? {
        return try {
            // Timeout de 8 s como en search: una geocodificación inversa
            // colgada no debe dejar "resolviendo dirección" para siempre.
            withTimeout(8_000) {
                // Throttle: nunca más de 1 llamada por segundo.
                mutex.withLock {
                    val now = System.currentTimeMillis()
                    val wait = MIN_INTERVAL_MS - (now - lastCallMs)
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                    lastCallMs = System.currentTimeMillis()
                }
                val response: ReverseResponse = NavHttp.client.get(BASE_URL) {
                    parameter("format", "jsonv2")
                    parameter("lat", point.latitude)
                    parameter("lon", point.longitude)
                    parameter("zoom", 18)
                    parameter("addressdetails", 1)
                    header("User-Agent", "DivineDeck/1.0 (Android car launcher; Tepic, Nayarit, MX)")
                }.body()
                response.display_name?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }
}

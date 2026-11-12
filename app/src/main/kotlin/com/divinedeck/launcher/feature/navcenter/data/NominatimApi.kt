package com.divinedeck.launcher.feature.navcenter.data

import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private const val MIN_INTERVAL_MS = 1_100L
    private val mutex = Mutex()
    private var lastCallMs = 0L

    @Serializable
    private data class ReverseResponse(
        val display_name: String? = null
    )

    suspend fun reverse(point: NavLatLng): String? {
        // Throttle: nunca más de 1 llamada por segundo.
        mutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_INTERVAL_MS - (now - lastCallMs)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastCallMs = System.currentTimeMillis()
        }
        return try {
            val response: ReverseResponse = NavHttp.client.get(BASE_URL) {
                parameter("format", "jsonv2")
                parameter("lat", point.latitude)
                parameter("lon", point.longitude)
                parameter("zoom", 18)
                parameter("addressdetails", 1)
                header("User-Agent", "DivineDeck/1.0 (Android car launcher; Tepic, Nayarit, MX)")
            }.body()
            response.display_name?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}

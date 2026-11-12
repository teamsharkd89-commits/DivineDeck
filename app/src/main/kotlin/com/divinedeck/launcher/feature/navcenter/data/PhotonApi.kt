package com.divinedeck.launcher.feature.navcenter.data

import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cliente HTTP compartido para los servicios gratuitos de OpenStreetMap
 * (Photon, Nominatim, Overpass). Sin llaves, sin cuentas, sin facturación.
 */
internal object NavHttp {
    val json = Json { ignoreUnknownKeys = true }
    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) { json(json) }
        }
    }
}

/**
 * Búsqueda de lugares con Photon (photon.komoot.io), hecho para autocompletado:
 * tolerante a typos, con sesgo por ubicación y sin llave.
 * Reglas: debounce en el ViewModel (≥300 ms), nunca más de ~1 petición por
 * tecleo pausado; `countrycode=mx` para priorizar México.
 */
object PhotonApi {
    private const val BASE_URL = "https://photon.komoot.io/api"

    @Serializable
    private data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

    @Serializable
    private data class PhotonFeature(
        val geometry: PhotonGeometry? = null,
        val properties: PhotonProperties? = null
    )

    @Serializable
    private data class PhotonGeometry(
        val coordinates: List<Double> = emptyList()
    )

    @Serializable
    private data class PhotonProperties(
        val name: String? = null,
        val street: String? = null,
        val housenumber: String? = null,
        val city: String? = null,
        val state: String? = null,
        val country: String? = null,
        val osm_key: String? = null,
        val osm_value: String? = null
    )

    suspend fun search(
        query: String,
        near: NavLatLng?,
        limit: Int = 5
    ): List<NavPlace> {
        val response: PhotonResponse = NavHttp.client.get(BASE_URL) {
            parameter("q", query)
            parameter("limit", limit)
            parameter("lang", "es")
            parameter("countrycode", "mx")
            if (near != null) {
                parameter("lat", near.latitude)
                parameter("lon", near.longitude)
            }
            header("User-Agent", "DivineDeck/1.0 (Android car launcher)")
        }.body()
        return response.features.mapNotNull { feature ->
            val coords = feature.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val props = feature.properties ?: return@mapNotNull null
            val name = props.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val detail = listOfNotNull(
                listOfNotNull(props.street, props.housenumber).joinToString(" ").takeIf { it.isNotBlank() },
                listOfNotNull(props.city, props.state).joinToString(", ").takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            NavPlace(
                id = "photon:${coords[1]},${coords[0]}:$name",
                name = name,
                detail = detail,
                latLng = NavLatLng(latitude = coords[1], longitude = coords[0]),
                kind = PoiKind.SEARCH
            )
        }
    }
}

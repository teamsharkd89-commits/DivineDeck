package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer

/**
 * Búsqueda y POIs offline de Tepic, sin Room ni base de datos: los datos
 * vienen de un JSON empaquetado en assets (offline/tepic_places.json,
 * generado desde Overpass: calles, colonias y POIs de Tepic) y se buscan en
 * memoria con coincidencia por prefijo sobre el nombre normalizado
 * (sin acentos, en minúsculas). Si existe una copia descargada en
 * filesDir/offline/tepic_places.json (paquete `offline-packs`), esa manda:
 * es la vía para actualizar los datos sin reinstalar el APK.
 *
 * ~8.500 lugares (~1.4 MB): el escaneo lineal es instantáneo en la tablet.
 * Sin números de casa fuera de línea: la geocodificación inversa solo
 * devuelve la calle más cercana ("Cerca de …").
 */
class OfflineSearchData(context: Context) {

    @Serializable
    private data class OfflinePlace(
        val id: String = "",
        val name: String = "",
        val name_norm: String = "",
        val detail: String = "",
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val kind: String = ""
    )

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private var places: List<OfflinePlace>? = null

    /** Carga el JSON (una sola vez, en IO). Devuelve false si no hay datos. */
    suspend fun load(): Boolean {
        if (places != null) return true
        val loaded = withContext(Dispatchers.IO) {
            try {
                val downloaded = File(
                    File(appContext.filesDir, OfflineNavConstants.OFFLINE_DIR),
                    OfflineNavConstants.SEARCH_FILE_NAME
                ).takeIf { it.exists() && it.length() > 0 }?.readText()
                val text = downloaded ?: appContext.assets
                    .open(OfflineNavConstants.SEARCH_ASSET_PATH)
                    .bufferedReader().use { it.readText() }
                json.decodeFromString<List<OfflinePlace>>(text)
            } catch (_: Exception) {
                null
            }
        }
        if (loaded != null) places = loaded
        return loaded != null
    }

    val isLoaded: Boolean get() = places != null
    val count: Int get() = places?.size ?: 0

    /**
     * Búsqueda por prefijo: cada palabra de la consulta debe ser prefijo de
     * alguna palabra del nombre. "av juarez" encuentra "Avenida Juárez".
     * Las calles vienen segmentadas en Overpass (mismo nombre muchas veces):
     * se agrupa por nombre y se queda el punto más cercano a [near].
     */
    fun search(query: String, near: NavLatLng?, limit: Int = 8): List<NavPlace> {
        val all = places ?: return emptyList()
        val tokens = normalize(query).split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val bestByName = LinkedHashMap<String, OfflinePlace>()
        for (place in all) {
            val words = place.name_norm.split(" ", "-", ".")
                .filter { it.isNotBlank() }
            if (words.isEmpty()) continue
            val matches = tokens.all { token ->
                words.any { it.startsWith(token) }
            }
            if (!matches) continue
            // Primero el nombre que empieza por la consulta, luego el resto.
            val startsWithQuery = place.name_norm.startsWith(tokens.first())
            val key = "${if (startsWithQuery) "0" else "1"}|${place.kind}|${place.name_norm}"
            val current = bestByName[key]
            if (current == null) {
                bestByName[key] = place
            } else if (near != null) {
                if (distanceMeters(place.lat, place.lon, near) <
                    distanceMeters(current.lat, current.lon, near)
                ) {
                    bestByName[key] = place
                }
            }
        }
        return bestByName.values
            .sortedWith(
                compareBy<OfflinePlace> { it.name_norm.startsWith(tokens.first()).not() }
                    .thenBy { it.name_norm }
            )
            .take(limit)
            .map { it.toNavPlace() }
    }

    /** Gasolineras / talleres del JSON, a menos de [radiusMeters] de [center]. */
    fun nearbyPois(kind: PoiKind, center: NavLatLng, radiusMeters: Int = 5_000, limit: Int = 30): List<NavPlace> {
        val all = places ?: return emptyList()
        val wantedDetail = when (kind) {
            PoiKind.FUEL -> "fuel"
            PoiKind.REPAIR -> "car_repair"
            else -> return emptyList()
        }
        return all.asSequence()
            .filter { it.kind == "poi" && it.detail == wantedDetail }
            .map { it to distanceMeters(it.lat, it.lon, center) }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .take(limit)
            .map { (place, _) -> place.toNavPlace() }
            .toList()
    }

    /** Calle nombrada más cercana al punto (geocodificación inversa offline). */
    fun nearestStreet(point: NavLatLng, maxMeters: Double = 300.0): String? {
        val all = places ?: return null
        var best: OfflinePlace? = null
        var bestDist = Double.MAX_VALUE
        for (place in all) {
            if (place.kind != "street") continue
            val d = distanceMeters(place.lat, place.lon, point)
            if (d < bestDist) {
                bestDist = d
                best = place
            }
        }
        return if (best != null && bestDist <= maxMeters) best.name else null
    }

    private fun OfflinePlace.toNavPlace(): NavPlace {
        val kind = when (this.kind) {
            "street" -> PoiKind.SEARCH
            "colonia" -> PoiKind.SEARCH
            else -> PoiKind.SEARCH
        }
        return NavPlace(
            id = "offline:${this.id}",
            name = this.name,
            detail = when {
                this.kind == "colonia" -> "Colonia · Tepic"
                this.detail.isNotBlank() -> this.detail
                else -> "Tepic"
            },
            latLng = NavLatLng(latitude = this.lat, longitude = this.lon),
            kind = kind
        )
    }

    private fun distanceMeters(lat: Double, lon: Double, to: NavLatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(to.latitude - lat)
        val dLon = Math.toRadians(to.longitude - lon)
        val x = dLon * Math.cos(Math.toRadians((to.latitude + lat) / 2.0))
        return Math.sqrt(x * x + dLat * dLat) * r
    }

    companion object {
        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase()
                .trim()
        }
    }
}

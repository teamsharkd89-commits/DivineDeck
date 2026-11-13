package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.util.Log
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
 * ~10.200 lugares (~1.7 MB): el escaneo lineal es instantáneo en la tablet.
 * Sin números de casa fuera de línea: la geocodificación inversa solo
 * devuelve la calle más cercana ("Cerca de …"). La búsqueda sí acepta
 * número de casa en la consulta y lo refleja en el detalle del resultado.
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

    /** Carga el JSON (una sola vez, en IO). Devuelve false si no hay datos.
     * Si la copia descargada existe pero no se puede leer/parsear, se
     * vuelve al asset empaquetado en vez de quedarse sin datos para
     * siempre (build 28: antes el `catch` tragaba la excepción en
     * silencio y la búsqueda offline moría sin dejar rastro). */
    suspend fun load(): Boolean {
        if (places != null) return true
        val loaded = withContext(Dispatchers.IO) {
            val downloadedFile = File(
                File(appContext.filesDir, OfflineNavConstants.OFFLINE_DIR),
                OfflineNavConstants.SEARCH_FILE_NAME
            ).takeIf { it.exists() && it.length() > 0 }
            if (downloadedFile != null) {
                try {
                    return@withContext json.decodeFromString<List<OfflinePlace>>(
                        downloadedFile.readText()
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "JSON offline descargado ilegible, uso el asset: ${e.message}")
                }
            }
            try {
                val text = appContext.assets
                    .open(OfflineNavConstants.SEARCH_ASSET_PATH)
                    .bufferedReader().use { it.readText() }
                json.decodeFromString<List<OfflinePlace>>(text)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar el JSON offline (asset): ${e.message}")
                null
            }
        }
        if (loaded != null) places = loaded
        return loaded != null
    }

    val isLoaded: Boolean get() = places != null
    val count: Int get() = places?.size ?: 0

    /**
     * Búsqueda tolerante (ver [OfflineSearchMatcher]): stop words fuera,
     * prefijo y subcadena, typos con distancia de edición 1-2 o
     * trigramas, y puntuación en vez de filtro todo-o-nada. "caminando
     * de jauja" encuentra "Calle Camino Viejo a Jauja"; "jurez" y
     * "juares" encuentran "Avenida Benito Juárez".
     * Las calles vienen segmentadas en Overpass (mismo nombre muchas
     * veces): se agrupa por nombre y se queda el punto más cercano a
     * [near].
     *
     * Direcciones con número de casa ("camichin de jauja 31 ejidal"):
     * el número se separa de los tokens (no estropea la coincidencia)
     * y se añade al detalle del resultado ("Calle Camichín de Jauja
     * 31, Ejidal"); un token que nombra una colonia del dataset se
     * usa como ancla: solo entran candidatos a menos de 6 km de su
     * centroide, para preferir el tramo de la calle en esa colonia.
     * Si la búsqueda sin número/colonia no da nada, o la consulta
     * parece una dirección y no entró ninguna calle, se reintenta en
     * cadena: sin ancla de colonia, con la consulta completa, y por
     * último con el token más informativo solo (ver el cuerpo de
     * `search`). Las calles del dataset v2 traen además la colonia en
     * `detail` ("Fraccionamiento Jacarandas"), que se muestra como
     * detalle del resultado cuando no hay número de casa.
     */
    fun search(query: String, near: NavLatLng?, limit: Int = 8): List<NavPlace> {
        val all = places ?: return emptyList()
        val entries = all.map {
            SearchEntry(
                id = it.id,
                name = it.name,
                nameNorm = it.name_norm.ifBlank { normalize(it.name) },
                detail = it.detail,
                lat = it.lat,
                lon = it.lon,
                kind = it.kind
            )
        }
        val tokens = OfflineSearchMatcher.tokenizeQuery(query)
        if (tokens.isEmpty()) return emptyList()
        val houseNumber = OfflineSearchMatcher.extractHouseNumber(tokens)
        val tokensNoNumber = if (houseNumber != null) tokens - houseNumber else tokens
        // El número solo se quita de la búsqueda si quedan al menos 2
        // tokens: en "5 de mayo" el "5" forma parte del nombre de la
        // calle, y quitarlo dejaba "Mayoral" por encima de
        // "Calle 5 de Mayo". Para el detalle el número se usa siempre
        // (salvo que ya esté en el nombre: ver toNavPlace).
        val stripNumber = tokensNoNumber.size >= 2
        val baseTokens = if (stripNumber) tokensNoNumber else tokens
        // Colonia solo si al quitar su token quedan al menos 2 tokens
        // para buscar la calle ("Colonia Centro" no se toca: [colonia,
        // centro] debe seguir encontrando la colonia "Centro").
        val colonia = if (tokensNoNumber.size >= 3) {
            OfflineSearchMatcher.findColonia(
                tokensNoNumber,
                entries,
                near?.let { it.latitude to it.longitude }
            )
        } else {
            null
        }
        val matchTokens = if (colonia != null) baseTokens - colonia.token else baseTokens
        val effectiveNear = if (colonia != null) {
            colonia.lat to colonia.lon
        } else {
            near?.let { it.latitude to it.longitude }
        }
        // Con colonia nombrada, solo candidatos cerca de ella: la calle
        // buscada está en esa colonia, y una carretera homónima a 15 km
        // ("Camichín de Jauja-San Luís de Lozada") no debe colarse por
        // encima de la calle de Tepic. 6 km da margen de sobra (una
        // colonia de Tepic mide ~1-2 km) y una calle larga sobrevive
        // mientras un tramo esté cerca: la deduplicación es por nombre.
        val searchEntries = if (colonia != null) {
            val anchor = colonia.lat to colonia.lon
            entries.filter {
                OfflineSearchMatcher.distanceMeters(it.lat, it.lon, anchor) <= COLONIA_ANCHOR_RADIUS_METERS
            }
        } else {
            entries
        }
        var matched = if (matchTokens.isNotEmpty()) {
            OfflineSearchMatcher.searchTokens(
                searchEntries,
                matchTokens,
                effectiveNear,
                limit
            )
        } else {
            emptyList()
        }
        var usedFallback = false
        // Cadena de respaldo (build 28): la búsqueda anclada no puede
        // ser la última palabra cuando la calle está en los datos.
        // Se dispara si no hay nada, o si la consulta parece una
        // dirección y ninguna calle entró (p. ej. solo devolvió la
        // colonia). Pasos: (b) sin ancla de colonia sobre todo el
        // dataset, (c) consulta completa sin ancla, (d) el token más
        // informativo solo. Cada paso se registra para diagnóstico.
        val looksLikeAddress = OfflineSearchMatcher.isAddressLike(tokens)
        val noStreetMatched = matched.none { it.kind == "street" }
        var needsFallback = matched.isEmpty() || (looksLikeAddress && noStreetMatched)
        if (needsFallback && colonia != null && matchTokens.isNotEmpty()) {
            val retry = OfflineSearchMatcher.searchTokens(
                entries,
                matchTokens,
                near?.let { it.latitude to it.longitude },
                limit
            )
            Log.d(TAG, "OfflineSearch paso b (sin ancla): ${retry.size} resultados para \"$query\"")
            if (retry.isNotEmpty()) {
                matched = retry
                usedFallback = true
                needsFallback = matched.isEmpty() || (looksLikeAddress && matched.none { it.kind == "street" })
            }
        }
        if (needsFallback && matchTokens != tokens) {
            // Reintento con la consulta completa y sin filtro de colonia
            // (por si el número o la colonia formaban parte del nombre:
            // "Calle 5 de Mayo").
            val retry = OfflineSearchMatcher.searchTokens(
                entries,
                tokens,
                near?.let { it.latitude to it.longitude },
                limit
            )
            Log.d(TAG, "OfflineSearch paso c (consulta completa): ${retry.size} resultados para \"$query\"")
            if (retry.isNotEmpty()) {
                matched = retry
                usedFallback = true
                needsFallback = matched.isEmpty() || (looksLikeAddress && matched.none { it.kind == "street" })
            }
        }
        if (needsFallback && looksLikeAddress) {
            // Último recurso, SOLO para direcciones: el token más
            // informativo solo (el más largo sin contar stop words ni
            // genéricas), y solo calles. Sin la restricción, una
            // consulta de nombre inexistente ("parque puerta paraíso")
            // colaba POIs por un solo token ("PARAÍSO17") y dejaba de
            // dar el "Sin resultados en Tepic" honesto. No puede dar
            // vacío si la calle está en el dataset.
            val singleToken = tokens
                .filter { it !in OfflineSearchMatcher.STOP_WORDS }
                .filter { it !in OfflineSearchMatcher.GENERIC_WORDS }
                .maxByOrNull { it.length }
            if (singleToken != null) {
                val retry = OfflineSearchMatcher.searchTokens(
                    entries,
                    listOf(singleToken),
                    near?.let { it.latitude to it.longitude },
                    limit
                ).filter { it.kind == "street" }
                Log.d(TAG, "OfflineSearch paso d (token \"$singleToken\", solo calles): ${retry.size} resultados para \"$query\"")
                if (retry.isNotEmpty()) {
                    matched = retry
                    usedFallback = true
                }
            }
        }
        Log.d(TAG, "OfflineSearch \"$query\": ${matched.size} resultados (fallback=$usedFallback)")
        // En el reintento la colonia ya no vale como ancla: no se añade
        // al detalle (la calle encontrada puede estar en otra colonia).
        val detailColonia = if (usedFallback) null else colonia?.name
        return matched.map { it.toNavPlace(houseNumber, detailColonia) }
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

    private fun SearchEntry.toNavPlace(
        houseNumber: String? = null,
        coloniaName: String? = null
    ): NavPlace {
        val kind = when (this.kind) {
            "street" -> PoiKind.SEARCH
            "colonia" -> PoiKind.SEARCH
            else -> PoiKind.SEARCH
        }
        // Dirección con número: el detalle lleva la dirección completa
        // ("Calle Camichín de Jauja 31, Ejidal"). El número no se añade
        // si ya forma parte del nombre ("Calle 5 de Mayo" buscada como
        // "5 de mayo" no se convierte en "Calle 5 de Mayo 5").
        val addressDetail = if (
            this.kind == "street" && houseNumber != null &&
            houseNumber !in OfflineSearchMatcher.wordsOf(this.nameNorm)
        ) {
            buildString {
                append(this@toNavPlace.name)
                append(' ')
                append(houseNumber)
                if (coloniaName != null) {
                    append(", ")
                    append(coloniaName.removePrefix("Colonia ").trim())
                }
            }
        } else {
            null
        }
        return NavPlace(
            id = "offline:${this.id}",
            name = this.name,
            detail = addressDetail ?: when {
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
        private const val TAG = "OfflineSearch"
        /** Radio alrededor del centroide de una colonia nombrada en la
         * consulta: solo las calles dentro entran en la búsqueda. */
        private const val COLONIA_ANCHOR_RADIUS_METERS = 6_000.0

        fun normalize(text: String): String {
            val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
                .lowercase()
                .trim()
        }
    }
}

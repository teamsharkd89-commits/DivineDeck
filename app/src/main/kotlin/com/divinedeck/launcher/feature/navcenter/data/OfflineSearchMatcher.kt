package com.divinedeck.launcher.feature.navcenter.data

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Coincidencia tolerante para la búsqueda offline de Tepic.
 *
 * Kotlin puro, sin Android: se puede probar en JVM sin Robolectric.
 * [OfflineSearchData] delega aquí su `search()`; el JSON no se toca.
 *
 * Reglas (verificadas contra el dataset real de 8.516 lugares):
 * - Se ignoran las stop words españolas ("de", "la", "el", …): la
 *   consulta "caminando de jauja" se busca como [caminando, jauja].
 * - Coincidencia por prefijo, subcadena y prefijo común largo:
 *   "caminando" encuentra "camino" (comparten la raíz "camin") y
 *   "gasolineras" encuentra "gasolinera".
 * - Typos tolerados: distancia de edición 1 (p. ej. "jurez" /
 *   "juares" → "juarez") y similitud por trigramas como respaldo.
 * - Puntuación en vez de filtro todo-o-nada: basta con acertar al
 *   menos la mitad de los tokens (redondeando hacia arriba); un
 *   resultado que acierta más tokens puntúa más y sale primero.
 * - Deduplicación por nombre (mismo nombre muchas veces por la
 *   segmentación de Overpass): se queda el punto más cercano a [near].
 */
internal data class SearchEntry(
    val id: String,
    val name: String,
    val nameNorm: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    val kind: String
)

internal object OfflineSearchMatcher {

    val STOP_WORDS = setOf(
        "de", "la", "el", "los", "las", "a", "en", "del", "y", "con", "por"
    )

    /**
     * Palabras genéricas de tipo de vía / asentamiento: están en cientos
     * de nombres ("Calle …", "Colonia …") y acertar solo con ellas no
     * dice nada ("Colonia Centro" debe encontrar la colonia "Centro",
     * no las 199 colonias). Un token que solo acierta con una palabra
     * genérica puntúa como mucho 1.0, y no da el bonus de "empieza por".
     */
    val GENERIC_WORDS = setOf(
        "calle", "avenida", "av", "andador", "privada", "circuito",
        "calzada", "boulevard", "blvd", "colonia"
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase()
            .trim()
    }

    /** Tokens de la consulta sin stop words; si solo había stop words, todos. */
    fun tokenizeQuery(query: String): List<String> {
        val tokens = normalize(query)
            .split(" ", "-", ".")
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val content = tokens.filter { it !in STOP_WORDS }
        return content.ifEmpty { tokens }
    }

    fun wordsOf(nameNorm: String): List<String> =
        nameNorm.split(" ", "-", ".").filter { it.isNotBlank() }

    /**
     * Mejor puntuación de [token] contra una palabra del nombre:
     * 3.0 exacta, 2.5 prefijo (token de ≥2 letras), 2.0 subcadena
     * (solo si la parte corta tiene ≥5 letras: si no, la "a" de
     * "Calle A" acertaría con casi cualquier consulta, y "mina" está
     * dentro de "caminando" o "sol" dentro de "gasolinera") o prefijo
     * común de ≥5 letras ("caminando"/"camino" comparten "camin"),
     * 1.5 distancia de edición 1, 1.0 distancia 2 o trigramas ≥ 0.5,
     * 0.0 sin parecido. Si la palabra es genérica ("calle", "colonia",
     * …) la puntuación se topa en 1.0.
     */
    fun bestTokenScore(token: String, word: String): Double {
        val raw = rawTokenScore(token, word)
        return if (word in GENERIC_WORDS) minOf(raw, 1.0) else raw
    }

    private fun rawTokenScore(token: String, word: String): Double {
        if (token == word) return 3.0
        if (token.length >= 2 && word.startsWith(token)) return 2.5
        if (minOf(token.length, word.length) >= 5 &&
            (token.contains(word) || word.contains(token))
        ) return 2.0
        // Prefijo común largo: "caminando" y "camino" comparten "camin"
        // (la raíz del verbo): es el caso reportado en Tepic.
        if (commonPrefixLength(token, word) >= 5) return 2.0
        val distance = levenshtein(token, word, maxDistance = 2)
        if (distance == 1) return 1.5
        if (distance == 2 && token.length >= 5 && word.length >= 4) return 1.0
        if (token.length >= 3 && word.length >= 3 &&
            trigramSimilarity(token, word) >= 0.5
        ) return 1.0
        return 0.0
    }

    fun commonPrefixLength(a: String, b: String): Int {
        var i = 0
        while (i < minOf(a.length, b.length) && a[i] == b[i]) i++
        return i
    }

    /** Levenshtein con tope: devuelve [maxDistance]+1 si lo supera. */
    fun levenshtein(a: String, b: String, maxDistance: Int = 2): Int {
        if (abs(a.length - b.length) > maxDistance) return maxDistance + 1
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                if (curr[j] < rowMin) rowMin = curr[j]
            }
            if (rowMin > maxDistance) return maxDistance + 1
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    /** Coeficiente de Dice sobre trigramas (0.0–1.0). */
    fun trigramSimilarity(a: String, b: String): Double {
        val ga = trigrams(a)
        val gb = trigrams(b)
        if (ga.isEmpty() || gb.isEmpty()) return 0.0
        val common = ga.intersect(gb).size
        return 2.0 * common / (ga.size + gb.size)
    }

    private fun trigrams(s: String): Set<String> {
        if (s.length < 3) return setOf(s)
        return (0..s.length - 3).map { s.substring(it, it + 3) }.toSet()
    }

    /** (tokens acertados, puntuación total) de una entrada. */
    fun scorePlace(tokens: List<String>, words: List<String>): Pair<Int, Double> {
        var matched = 0
        var total = 0.0
        for (token in tokens) {
            var best = 0.0
            for (word in words) {
                val s = bestTokenScore(token, word)
                if (s > best) best = s
            }
            if (best > 0.0) {
                matched++
                total += best
            }
        }
        return matched to total
    }

    /**
     * Búsqueda tolerante con puntuación. [near] es (lat, lon) o null.
     * Devuelve como mucho [limit] entradas, mejor puntuación primero.
     */
    fun search(
        entries: List<SearchEntry>,
        query: String,
        near: Pair<Double, Double>?,
        limit: Int = 8
    ): List<SearchEntry> {
        val tokens = tokenizeQuery(query)
        if (tokens.isEmpty()) return emptyList()
        // Basta acertar la mitad de los tokens (hacia arriba): con 1
        // token hay que acertarlo; con 2 basta 1 ("Colonia Centro":
        // "colonia" no está en el nombre, "centro" sí).
        val requiredMatches = (tokens.size + 1) / 2
        val firstToken = tokens.first()
        // Mejor entrada por nombre+tipo (mismo nombre, punto más cercano).
        val bestByName = LinkedHashMap<String, ScoredEntry>()
        for (entry in entries) {
            val nameNorm = entry.nameNorm.ifBlank { normalize(entry.name) }
            val words = wordsOf(nameNorm)
            if (words.isEmpty()) continue
            val (matched, baseScore) = scorePlace(tokens, words)
            if (matched < requiredMatches) continue
            var score = baseScore
            // Bonus solo si el primer token no es genérico: que el nombre
            // empiece por "colonia" no dice nada (lo hacen 199 colonias).
            if (firstToken !in GENERIC_WORDS && nameNorm.startsWith(firstToken)) score += 2.0
            if (matched == tokens.size) score += 1.0
            val key = "${entry.kind}|$nameNorm"
            val current = bestByName[key]
            if (current == null) {
                bestByName[key] = ScoredEntry(entry, score)
            } else if (near != null) {
                val dNew = distanceMeters(entry.lat, entry.lon, near)
                val dCur = distanceMeters(current.entry.lat, current.entry.lon, near)
                if (dNew < dCur) bestByName[key] = ScoredEntry(entry, score)
            }
        }
        return bestByName.values
            .sortedWith(
                compareByDescending<ScoredEntry> { it.score }
                    .thenBy { it.entry.nameNorm.startsWith(firstToken).not() }
                    .thenBy { it.entry.nameNorm }
                    .thenBy {
                        if (near != null) distanceMeters(it.entry.lat, it.entry.lon, near)
                        else 0.0
                    }
            )
            .take(limit)
            .map { it.entry }
    }

    private data class ScoredEntry(val entry: SearchEntry, val score: Double)

    internal fun distanceMeters(lat: Double, lon: Double, to: Pair<Double, Double>): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(to.first - lat)
        val dLon = Math.toRadians(to.second - lon)
        val x = dLon * Math.cos(Math.toRadians((to.first + lat) / 2.0))
        return sqrt(x * x + dLat * dLat) * r
    }
}

package com.divinedeck.launcher.feature.navcenter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas JVM (sin Android) del matcher tolerante de búsqueda offline.
 * Cubre los fallos reales reportados en Tepic (build 24): "caminando de
 * jauja" sin resultados, typos como "jurez"/"juares" sin resultados, y
 * la regla de que acertar más tokens debe ordenar primero.
 *
 * El matcher es Kotlin puro ([OfflineSearchMatcher]); estas pruebas no
 * necesitan Robolectric ni el JSON real: usan entradas sintéticas con
 * los mismos nombres que aparecen en tepic_places.json.
 */
class OfflineSearchMatcherTest {

    private fun entry(
        name: String,
        kind: String = "street",
        lat: Double = 21.5,
        lon: Double = -104.9
    ) = SearchEntry(
        id = "test:${name.hashCode()}",
        name = name,
        nameNorm = OfflineSearchMatcher.normalize(name),
        detail = "",
        lat = lat,
        lon = lon,
        kind = kind
    )

    private val dataset = listOf(
        entry("Calle Camichín de Jauja", lat = 21.4792474, lon = -104.9076467),
        entry("Calle Camino Viejo a Jauja", lat = 21.5211946, lon = -104.8946128),
        entry("Camino Viejo a Jauja", lat = 21.5267472, lon = -104.8931468),
        entry("Calle Camino Real", lat = 21.51, lon = -104.89),
        entry("Avenida Benito Juárez", lat = 21.5046579, lon = -104.8882155),
        entry("Andador Benito Juárez", lat = 21.5268901, lon = -104.8850162),
        entry("Avenida Allende", lat = 21.51, lon = -104.89),
        entry("Avenida Hidalgo", lat = 21.51, lon = -104.89),
        entry("Centro", kind = "colonia", lat = 21.4496, lon = -104.8202),
        entry("Centro Histórico", kind = "colonia", lat = 21.509, lon = -104.893),
        entry("Colonia 2 de Agosto", kind = "colonia", lat = 21.52, lon = -104.88),
        entry("Colonia Bugambilias", kind = "colonia", lat = 21.52, lon = -104.88),
        entry("Ciudad del Valle", kind = "colonia", lat = 21.52, lon = -104.88),
        // Colonias para probar el ancla de [OfflineSearchMatcher.findColonia]:
        // "Centro" (este, otra localidad) contra "Centro Histórico"
        // (centro de Tepic), y "Colonia Ejidal" contra "Crucero de
        // Camichin" (el token "camichin" no debe anclar ahí). El "Centro"
        // de arriba lleva las coordenadas reales del dataset (este).
        entry("Colonia Ejidal", kind = "colonia", lat = 21.4460, lon = -104.8963),
        entry("Ampliación Ejidal", kind = "colonia", lat = 21.4468, lon = -104.8955),
        entry("Crucero de Camichin", kind = "colonia", lat = 21.4658, lon = -104.8009),
        entry("Benito Juárez", kind = "colonia", lat = 21.4975, lon = -104.9078),
        entry("Plaza Fórum", kind = "poi", lat = 21.51, lon = -104.89),
        // Calle y colonia del fallo reportado en build 27 (build 28):
        // "begonia 32 fraccionamiento jacarandas" no daba nada porque
        // "fraccionamiento" no estaba en GENERIC_WORDS.
        entry("Calle Begonia", lat = 21.4860363, lon = -104.8537217),
        entry("Fraccionamiento Jacarandas", kind = "colonia", lat = 21.4829887, lon = -104.8546324),
        entry("Calle Baja", lat = 21.51, lon = -104.89),
        entry("Calle Caja", lat = 21.51, lon = -104.89),
        entry("Mercado Morelos", kind = "poi", lat = 21.51, lon = -104.89),
        entry("Hospital General", kind = "poi", lat = 21.51, lon = -104.89),
        // Basura que el matcher viejo (o un substring sin tope) colaba:
        // la "a" de "Calle A" acertaba con casi cualquier consulta.
        entry("Calle A", lat = 21.51, lon = -104.89),
        entry("Calle J", lat = 21.51, lon = -104.89),
        entry("Antiguo Camino a San Blas", lat = 21.51, lon = -104.89)
    )

    private fun search(query: String, limit: Int = 8) =
        OfflineSearchMatcher.search(dataset, query, near = 21.5 to -104.9, limit)

    // --- El fallo reportado: "caminando de jauja" ---

    @Test
    fun `caminando de jauja encuentra Camino Viejo a Jauja y Camichin de Jauja`() {
        val results = search("caminando de jauja")
        assertTrue(results.isNotEmpty())
        val names = results.map { it.name }
        assertTrue("Camino Viejo a Jauja" in names)
        assertTrue("Calle Camichín de Jauja" in names)
        // Los dos de Jauja, que aciertan los 2 tokens, van primero.
        assertEquals("Calle Camino Viejo a Jauja", results[0].name)
        assertEquals("Camino Viejo a Jauja", results[1].name)
        assertEquals("Calle Camichín de Jauja", results[2].name)
        // Ni "Calle A" ni "Antiguo Camino a San Blas" se cuelan arriba.
        assertTrue("Calle A" !in names.take(3))
    }

    @Test
    fun `stop words se ignoran al tokenizar`() {
        assertEquals(
            listOf("caminando", "jauja"),
            OfflineSearchMatcher.tokenizeQuery("caminando de jauja")
        )
        assertEquals(
            listOf("ciudad", "valle"),
            OfflineSearchMatcher.tokenizeQuery("Ciudad del Valle")
        )
    }

    @Test
    fun `subcadena y prefijo comun - caminando encuentra camino`() {
        // Prefijo común "camin" (5 letras): 2.0, no un trigramas justo.
        assertEquals(2.0, OfflineSearchMatcher.bestTokenScore("caminando", "camino"), 0.001)
        assertEquals(2.0, OfflineSearchMatcher.bestTokenScore("gasolineras", "gasolinera"), 0.001)
        val results = search("caminando")
        assertTrue(results.any { it.name == "Calle Camino Viejo a Jauja" })
    }

    // --- Typos ---

    @Test
    fun `jurez con una letra menos encuentra Juarez y no Calle J`() {
        val results = search("Jurez")
        assertTrue(results.any { "Juárez" in it.name })
        // Los Juárez (distancia 1) van primero; "Calle J" no aparece.
        assertTrue("Juárez" in results[0].name)
        assertTrue(results.none { it.name == "Calle J" })
    }

    @Test
    fun `juares con s por z encuentra Juarez`() {
        val results = search("Juares")
        assertTrue(results.any { "Juárez" in it.name })
        assertTrue("Juárez" in results[0].name)
    }

    @Test
    fun `av juarez encuentra Avenida Juarez por prefijo`() {
        val results = search("av juarez")
        assertTrue(results.isNotEmpty())
        assertEquals("Avenida Benito Juárez", results[0].name)
    }

    // --- Puntuación / orden ---

    @Test
    fun `acertar mas tokens ordena primero`() {
        // "camino jauja" acierta 2 tokens en las de Jauja y solo 1 en
        // "Camino Real": las de Jauja van primero.
        val results = search("camino jauja")
        val top2 = results.take(2).map { it.name }.toSet()
        assertEquals(
            setOf("Calle Camino Viejo a Jauja", "Camino Viejo a Jauja"), top2
        )
        assertTrue(results.indexOfFirst { it.name == "Calle Camino Real" } > 1)
    }

    @Test
    fun `colonia centro encuentra la colonia Centro aunque colonia no este en el nombre`() {
        val results = search("Colonia Centro")
        assertTrue(results.any { it.name == "Centro" && it.kind == "colonia" })
        // "Centro" (acierta "centro") va antes que las 199 colonias que
        // solo aciertan la palabra genérica "colonia".
        assertEquals("Centro", results[0].name)
    }

    @Test
    fun `ciudad del valle encuentra la colonia`() {
        val results = search("Ciudad del Valle")
        assertEquals("Ciudad del Valle", results[0].name)
    }

    @Test
    fun `consultas de un token funcionan - allende hidalgo mercado hospital`() {
        assertEquals("Avenida Allende", search("Allende")[0].name)
        assertEquals("Avenida Hidalgo", search("Hidalgo")[0].name)
        assertEquals("Mercado Morelos", search("Mercado")[0].name)
        assertEquals("Hospital General", search("Hospital")[0].name)
    }

    // --- Sin resultados de verdad ---

    @Test
    fun `catedral no encuentra nada si el dataset no la tiene`() {
        // Ni siquiera con "Calle A" o "Antiguo Camino a San Blas" en el
        // dataset: la "a" sola ya no cuenta como coincidencia.
        assertTrue(search("Catedral").isEmpty())
    }

    @Test
    fun `consulta vacia o solo stop words sin contenido no devuelve nada`() {
        assertTrue(search("").isEmpty())
        assertTrue(search("   ").isEmpty())
    }

    // --- Deduplicación y límite ---

    @Test
    fun `mismo nombre varias veces se deduplica quedando el punto mas cercano`() {
        val entries = listOf(
            entry("Avenida Benito Juárez", lat = 21.60, lon = -104.99),
            entry("Avenida Benito Juárez", lat = 21.5001, lon = -104.9001),
            entry("Avenida Benito Juárez", lat = 21.51, lon = -104.91)
        )
        val results = OfflineSearchMatcher.search(
            entries, "juarez", near = 21.5 to -104.9, limit = 8
        )
        assertEquals(1, results.size)
        assertEquals(21.5001, results[0].lat, 0.00001)
    }

    @Test
    fun `limit se respeta`() {
        assertEquals(2, search("avenida", limit = 2).size)
    }

    // --- Normalización y distancia ---

    @Test
    fun `normalize quita acentos y minusculas`() {
        assertEquals("juarez", OfflineSearchMatcher.normalize("Juárez"))
        assertEquals("camichin de jauja", OfflineSearchMatcher.normalize("Camichín de Jauja"))
    }

    @Test
    fun `levenshtein tolera un cambio y trigramas dan similitud`() {
        assertEquals(1, OfflineSearchMatcher.levenshtein("jurez", "juarez"))
        assertEquals(1, OfflineSearchMatcher.levenshtein("juares", "juarez"))
        assertTrue(OfflineSearchMatcher.trigramSimilarity("caminando", "camina") >= 0.5)
        assertEquals(5, OfflineSearchMatcher.commonPrefixLength("caminando", "camino"))
    }

    // --- Número de casa y colonia (build 26) ---

    @Test
    fun `extractHouseNumber separa el numero de casa`() {
        assertEquals(
            "31",
            OfflineSearchMatcher.extractHouseNumber(listOf("camichin", "jauja", "31", "ejidal"))
        )
        assertEquals(
            "123",
            OfflineSearchMatcher.extractHouseNumber(listOf("av", "juarez", "123"))
        )
        assertEquals(
            "45b",
            OfflineSearchMatcher.extractHouseNumber(listOf("calle", "45b"))
        )
        assertEquals(null, OfflineSearchMatcher.extractHouseNumber(listOf("av", "juarez")))
        assertEquals(null, OfflineSearchMatcher.extractHouseNumber(emptyList()))
    }

    @Test
    fun `findColonia ejidal ancla en Colonia Ejidal y no en Crucero de Camichin`() {
        // La colonia va al final de la dirección: manda "ejidal", no el
        // primer token "camichin" (que también nombra una colonia).
        val match = OfflineSearchMatcher.findColonia(
            listOf("camichin", "jauja", "ejidal"), dataset, near = 21.5 to -104.9
        )
        assertEquals("ejidal", match?.token)
        assertEquals("Colonia Ejidal", match?.name)
    }

    @Test
    fun `findColonia centro ancla en Centro Historico y no en el Centro de otra localidad`() {
        // El "Centro" exacto del dataset está a 8 km al este (otra
        // localidad); el "Centro Histórico" de Tepic está a ~1 km.
        val match = OfflineSearchMatcher.findColonia(
            listOf("calle", "juarez", "centro"), dataset, near = 21.5 to -104.9
        )
        assertEquals("centro", match?.token)
        assertEquals("Centro Histórico", match?.name)
    }

    @Test
    fun `findColonia no ancla en nombres parciales - paraiso y camichin`() {
        // "paraiso" solo cubre la mitad de "Ampliación Paraíso" y
        // "camichin" la mitad de "Crucero de Camichin": sin ancla, la
        // búsqueda usa los tokens tal cual (y "parque puerta paraíso"
        // sigue dando "Sin resultados").
        assertEquals(
            null,
            OfflineSearchMatcher.findColonia(
                listOf("parque", "puerta", "paraiso"), dataset, near = 21.5 to -104.9
            )
        )
        assertEquals(
            null,
            OfflineSearchMatcher.findColonia(
                listOf("camichin", "jauja"), dataset, near = 21.5 to -104.9
            )
        )
    }

    @Test
    fun `searchTokens con tokens ya calculados encuentra Camichin de Jauja`() {
        // "camichin de jauja 31 ejidal" sin número ni colonia: los dos
        // tokens que importan, con el centroide de Ejidal como `near`.
        val results = OfflineSearchMatcher.searchTokens(
            dataset,
            listOf("camichin", "jauja"),
            near = 21.4460 to -104.8963,
            limit = 8
        )
        assertTrue(results.isNotEmpty())
        assertEquals("Calle Camichín de Jauja", results[0].name)
    }

    // --- Fallo reportado en build 27 (build 28) ---

    @Test
    fun `fraccionamiento es palabra generica`() {
        assertTrue("fraccionamiento" in OfflineSearchMatcher.GENERIC_WORDS)
        assertTrue("fracc" in OfflineSearchMatcher.GENERIC_WORDS)
    }

    @Test
    fun `begonia 32 fraccionamiento jacarandas - jacarandas ancla la colonia`() {
        // Con "fraccionamiento" en GENERIC_WORDS, el nombre distintivo
        // de "Fraccionamiento Jacarandas" es solo [jacarandas] y el
        // token lo cubre al 100 %: antes la cobertura era 0.5 y no
        // había ancla, y la calle quedaba fuera.
        val match = OfflineSearchMatcher.findColonia(
            listOf("begonia", "fraccionamiento", "jacarandas"),
            dataset, near = 21.5 to -104.9
        )
        assertEquals("jacarandas", match?.token)
        assertEquals("Fraccionamiento Jacarandas", match?.name)
    }

    @Test
    fun `begonia con fraccionamiento encuentra Calle Begonia primero`() {
        // Sin número ni colonia: [begonia, fraccionamiento] con el
        // centroide de Jacarandas como `near` (el flujo completo con
        // número y ancla se verifica en el harness Python contra el
        // dataset real v2).
        val results = OfflineSearchMatcher.searchTokens(
            dataset,
            listOf("begonia", "fraccionamiento"),
            near = 21.4830 to -104.8546,
            limit = 8
        )
        assertTrue(results.isNotEmpty())
        assertEquals("Calle Begonia", results[0].name)
    }

    @Test
    fun `jauja con distancia 2 no confunde baja ni caja`() {
        // "jauja"–"baja" y "jauja"–"caja" son distancia 2 con prefijo
        // común 0: desde build 28 eso ya no puntúa y la basura no
        // llena los 8 primeros puestos.
        assertEquals(0.0, OfflineSearchMatcher.bestTokenScore("jauja", "baja"), 0.001)
        assertEquals(0.0, OfflineSearchMatcher.bestTokenScore("jauja", "caja"), 0.001)
        val results = search("jauja")
        assertTrue(results.none { it.name == "Calle Baja" || it.name == "Calle Caja" })
    }

    @Test
    fun `consulta de direccion se detecta como address-like`() {
        assertTrue(OfflineSearchMatcher.isAddressLikeQuery("begonia 32 fraccionamiento jacarandas"))
        assertTrue(OfflineSearchMatcher.isAddressLikeQuery("camichin de jauja 32 ejidal"))
        assertTrue(OfflineSearchMatcher.isAddressLikeQuery("Av. Juárez 123"))
        assertTrue(OfflineSearchMatcher.isAddressLikeQuery("calle juarez centro"))
        assertTrue(!OfflineSearchMatcher.isAddressLikeQuery("bodega aurrera"))
        assertTrue(!OfflineSearchMatcher.isAddressLikeQuery("catedral"))
        assertTrue(!OfflineSearchMatcher.isAddressLikeQuery("forum"))
    }
}

package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.5E.5:
 * BitChord Lyrics Source Control (Configuración de proveedores de letras).
 *
 * Valida:
 * 1. Consulta estricta solo a fuentes habilitadas en [AppSettings.lyricsSources].
 * 2. Evaluación según el orden de prioridad configurado en [AppSettings.lyricsSourceOrder].
 * 3. Fallback tolerante a fallos cuando el proveedor principal falla o lanza excepción.
 * 4. Priorización de sincronización por palabra cuando [AppSettings.prioritizeSyllableSync] = true.
 * 5. Comportamiento estándar veloz cuando [AppSettings.prioritizeSyllableSync] = false.
 * 6. Preservación 100% offline: las letras locales y sidecars nunca consultan la red ni dependen de proveedores remotos.
 * 7. Guardia de seguridad: no se permite dejar 0 fuentes habilitadas.
 * 8. Reordenamiento y restauración de valores originales.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordLyricsSourceControlTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var mockPrefs: InMemorySharedPreferences
    private lateinit var tempDir: File

    private class TestContext : ContextWrapper(null)

    @Before
    fun setUp() {
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        LyricsRepository.clearMemoryCache()
        LyricsRepository.lyricsDispatcher = testDispatcher
        lyricsOfflineModeOverride = null

        tempDir = Files.createTempDirectory("divine_deck_lyrics_source_test").toFile()
    }

    @After
    fun tearDown() {
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = null
        AppSettings.resetForTest()
        tempDir.deleteRecursively()
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 1: Solo se consultan proveedores habilitados
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test01_onlyEnabledSourcesAreQueried() = testScope.runTest {
        val askedSources = CopyOnWriteArrayList<LyricsSource>()

        // Habilitar únicamente LRCLIB y BETTER_LYRICS
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS))

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            askedSources.add(source)
            if (source == LyricsSource.LRCLIB) {
                listOf(LyricLine(1000L, "LRCLIB Line"))
            } else {
                null
            }
        }

        val song = Song(
            videoId = "vid01",
            title = "Test Song",
            artist = "Test Artist",
            thumbnailUrl = null,
            durationText = "03:00",
        )

        val state = LyricsRepository.resolveForSong(
            song = song,
            forceRefresh = true,
        )

        advanceUntilIdle()

        assertTrue("El estado debe ser Synced", state is LyricsUiState.Synced)
        assertEquals(LyricsSource.LRCLIB, (state as LyricsUiState.Synced).source)

        // Verificar que solo se preguntaron fuentes en el set habilitado
        assertTrue("LRCLIB fue consultada", askedSources.contains(LyricsSource.LRCLIB))
        for (source in askedSources) {
            assertTrue(
                "La fuente $source debe estar entre las habilitadas",
                source in setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS)
            )
        }
        assertFalse("GENIUS no debió ser consultado", askedSources.contains(LyricsSource.GENIUS))
        assertFalse("LRC_RED no debió ser consultado", askedSources.contains(LyricsSource.LRC_RED))
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 2: El orden de prioridad configurado determina el resultado
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test02_priorityOrderDeterminesResult() = testScope.runTest {
        // Habilitar dos fuentes con letras válidas de línea
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB, LyricsSource.KUGOU))

        // Caso A: LRCLIB adelante de KUGOU
        AppSettings.setLyricsSourceOrder(listOf(LyricsSource.LRCLIB, LyricsSource.KUGOU))
        AppSettings.setPrioritizeSyllableSync(false)

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRCLIB -> listOf(LyricLine(1000L, "LRCLIB Lyrics"))
                LyricsSource.KUGOU -> listOf(LyricLine(1000L, "KuGou Lyrics"))
                else -> null
            }
        }

        val songA = Song(videoId = "vidA", title = "Song A", artist = "Artist A", thumbnailUrl = null, durationText = "03:00")
        val stateA = LyricsRepository.resolveForSong(song = songA, forceRefresh = true)
        advanceUntilIdle()

        assertTrue("Debe resolverse como Synced", stateA is LyricsUiState.Synced)
        assertEquals(LyricsSource.LRCLIB, (stateA as LyricsUiState.Synced).source)
        assertEquals("LRCLIB Lyrics", stateA.lines.first { it.text.isNotBlank() }.text)

        // Caso B: Invertir prioridad -> KUGOU adelante de LRCLIB
        AppSettings.setLyricsSourceOrder(listOf(LyricsSource.KUGOU, LyricsSource.LRCLIB))
        LyricsRepository.clearMemoryCache()

        val songB = Song(videoId = "vidB", title = "Song B", artist = "Artist B", thumbnailUrl = null, durationText = "03:00")
        val stateB = LyricsRepository.resolveForSong(song = songB, forceRefresh = true)
        advanceUntilIdle()

        assertTrue("Debe resolverse como Synced", stateB is LyricsUiState.Synced)
        assertEquals(LyricsSource.KUGOU, (stateB as LyricsUiState.Synced).source)
        assertEquals("KuGou Lyrics", stateB.lines.first { it.text.isNotBlank() }.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 3: Fallback automático cuando el proveedor principal falla o no tiene letra
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test03_fallbackWhenPrimaryFailsOrThrows() = testScope.runTest {
        AppSettings.setLyricsSources(setOf(LyricsSource.LRC_RED, LyricsSource.LRCLIB))
        AppSettings.setLyricsSourceOrder(listOf(LyricsSource.LRC_RED, LyricsSource.LRCLIB))

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> throw RuntimeException("HTTP 503 Service Unavailable")
                LyricsSource.LRCLIB -> listOf(LyricLine(2000L, "Fallback from LRCLIB"))
                else -> null
            }
        }

        val song = Song(videoId = "vidFail", title = "Resilient Song", artist = "Band", thumbnailUrl = null, durationText = "03:30")
        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)
        advanceUntilIdle()

        assertTrue("Debe recuperarse con el proveedor de fallback", state is LyricsUiState.Synced)
        val synced = state as LyricsUiState.Synced
        assertEquals(LyricsSource.LRCLIB, synced.source)
        assertEquals("Fallback from LRCLIB", synced.lines.first { it.text.isNotBlank() }.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 4: prioritizeSyllableSync = true prefiere letras por palabra aunque sean de menor prioridad
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test04_prioritizeSyllableSync_prefersWordSynced() = testScope.runTest {
        // Orden: LRCLIB (#1) antes de BETTER_LYRICS (#2)
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS))
        AppSettings.setLyricsSourceOrder(listOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS))
        AppSettings.setPrioritizeSyllableSync(true)

        val lineSyncedOnly = listOf(
            LyricLine(1000L, "Regular Line 1"),
            LyricLine(3000L, "Regular Line 2"),
        )
        val wordSynced = listOf(
            LyricLine(
                timeMs = 1000L,
                text = "Enhanced Word Line",
                words = listOf(
                    LyricWord(1000L, 1400L, "Enhanced "),
                    LyricWord(1400L, 1800L, "Word "),
                    LyricWord(1800L, 2200L, "Line"),
                ),
            ),
        )

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRCLIB -> lineSyncedOnly
                LyricsSource.BETTER_LYRICS -> wordSynced
                else -> null
            }
        }

        val song = Song(videoId = "vidSyncWord", title = "Word Sync Track", artist = "Artist", thumbnailUrl = null, durationText = "02:45")
        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)
        advanceUntilIdle()

        assertTrue("Debe resolverse como Synced", state is LyricsUiState.Synced)
        val synced = state as LyricsUiState.Synced
        // Dado que prioritizeSyllableSync es true, DEBE tomar BETTER_LYRICS porque tiene palabra por palabra!
        assertEquals(LyricsSource.BETTER_LYRICS, synced.source)
        assertTrue(synced.isWordSynced)
        assertEquals("Enhanced Word Line", synced.lines.first().text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 5: prioritizeSyllableSync = false acepta la primera sincronizada según prioridad
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test05_standardMode_prefersFirstAnsweringSource() = testScope.runTest {
        // Orden: LRCLIB (#1) antes de BETTER_LYRICS (#2)
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS))
        AppSettings.setLyricsSourceOrder(listOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS))
        AppSettings.setPrioritizeSyllableSync(false)

        val lineSyncedOnly = listOf(LyricLine(1000L, "Fast Line Synced from LRCLIB"))
        val wordSynced = listOf(
            LyricLine(
                timeMs = 1000L,
                text = "Word Synced from BetterLyrics",
                words = listOf(LyricWord(1000L, 1500L, "Word ")),
            ),
        )

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRCLIB -> lineSyncedOnly
                LyricsSource.BETTER_LYRICS -> wordSynced
                else -> null
            }
        }

        val song = Song(videoId = "vidFast", title = "Fast Track", artist = "Artist", thumbnailUrl = null, durationText = "02:45")
        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)
        advanceUntilIdle()

        assertTrue("Debe resolverse como Synced", state is LyricsUiState.Synced)
        val synced = state as LyricsUiState.Synced
        // En modo estándar sin priorizar silábica, toma la primera (#1 LRCLIB)
        assertEquals(LyricsSource.LRCLIB, synced.source)
        assertEquals("Fast Line Synced from LRCLIB", synced.lines.first().text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 6: Preservación offline total — letras locales y sidecars se resuelven sin red
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test06_offlineIsolation_localLyricsWorkWithoutNetwork() = testScope.runTest {
        // Crear archivo de audio ficticio y su sidecar .lrc
        val audioFile = File(tempDir, "offline_track.mp3").apply { writeBytes(ByteArray(128)) }
        val sidecarFile = File(tempDir, "offline_track.lrc").apply {
            writeText("[00:05.00]Offline Sidecar Line 1\n[00:10.00]Offline Sidecar Line 2\n", Charsets.UTF_8)
        }

        // Simular modo sin internet
        lyricsOfflineModeOverride = true

        // Y simular que un proveedor remoto lanzaría excepción si intentara consultarse
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            throw AssertionError("No se debe consultar la red cuando hay archivo local sidecar!")
        }

        val song = Song(
            videoId = "offlineVid",
            title = "Offline Song",
            artist = "Offline Artist",
            thumbnailUrl = null,
            durationText = "03:15",
            localUri = audioFile.toURI().toString(),
        )

        val state = LyricsRepository.resolveForSong(song = song, context = null, forceRefresh = true)

        assertTrue("Debe resolverse como EmbeddedLocal", state is LyricsUiState.EmbeddedLocal)
        val local = state as LyricsUiState.EmbeddedLocal
        assertTrue(local.isSynced)
        val contentLines = local.lines.filter { it.text.isNotBlank() }
        assertEquals(2, contentLines.size)
        assertEquals("Offline Sidecar Line 1", contentLines[0].text)
        assertEquals(5000L, contentLines[0].timeMs)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 7: Guardia de seguridad — no se permite dejar 0 fuentes habilitadas
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test07_safetyGuard_minimumOneSourceEnforced() {
        // Reducir a 1 fuente
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB))
        assertEquals(1, AppSettings.lyricsSources.value.size)

        // Intentar apagar la última fuente
        val success = AppSettings.toggleLyricsSource(LyricsSource.LRCLIB)
        assertFalse("El toggle debe ser rechazado", success)
        assertEquals("LRCLIB debe permanecer habilitada", setOf(LyricsSource.LRCLIB), AppSettings.lyricsSources.value)

        // Intentar pasar un set vacío directamente
        AppSettings.setLyricsSources(emptySet())
        assertEquals("No se permite dejar 0 fuentes", setOf(LyricsSource.LRCLIB), AppSettings.lyricsSources.value)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 8: Reordenamiento y restauración de fábrica
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test08_reorderingAndResetToDefaults() {
        val originalFirst = AppSettings.lyricsSourceOrder.value.first()
        val originalSecond = AppSettings.lyricsSourceOrder.value[1]

        // Subir la segunda
        AppSettings.moveLyricsSourceUp(originalSecond)
        assertEquals(originalSecond, AppSettings.lyricsSourceOrder.value[0])
        assertEquals(originalFirst, AppSettings.lyricsSourceOrder.value[1])

        // Modificar fuentes y preferencia de sílaba
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB, LyricsSource.GENIUS))
        AppSettings.setPrioritizeSyllableSync(true)

        // Restaurar
        AppSettings.resetLyricsSourceSettings()

        assertEquals(LyricsSource.offered.toSet(), AppSettings.lyricsSources.value)
        assertEquals(LyricsSource.offered, AppSettings.lyricsSourceOrder.value)
        assertFalse(AppSettings.prioritizeSyllableSync.value)
    }

    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = backingMap
        override fun getString(key: String?, defValue: String?): String? = (backingMap[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (backingMap[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (backingMap[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (backingMap[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (backingMap[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (backingMap[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = backingMap.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) { removals.add(key); pending.remove(key) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true; pending.clear(); removals.clear()
                return this
            }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
            }
        }
    }
}

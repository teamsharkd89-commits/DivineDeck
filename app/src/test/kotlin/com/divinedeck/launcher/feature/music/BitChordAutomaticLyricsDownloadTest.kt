package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsStore
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.lyrics.forLyricsSearch
import com.music.bitchord.data.lyrics.lyricsNetworkCallCount
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.lyrics.resetLyricsNetworkStats
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.5D.2:
 * Adquisición y persistencia automática de letras durante las descargas de BitChord en DIVINE DECK.
 *
 * Resuelve y verifica el defecto reproducido en pruebas físicas:
 * 4 canciones descargadas online -> letras disponibles offline para TODAS,
 * incluso si el usuario jamás abrió el reproductor ni la pantalla de letras previamente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordAutomaticLyricsDownloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File
    private lateinit var mockPrefs: InMemorySharedPreferences
    private lateinit var testContext: Context

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
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) { pending[key] = values; removals.remove(key) }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
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
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    private class TestContextWithStorage(
        private val prefs: SharedPreferences,
        private val baseFilesDir: File
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getFilesDir(): File = baseFilesDir
    }

    private val fastStreamFetcher = object : Downloader.StreamFetcher {
        override suspend fun resolveAndFetch(
            videoId: String,
            maxKbps: Int,
            requireM4a: Boolean,
            openSink: (StreamResolver.Stream) -> OutputStream,
            onProgress: (written: Long, total: Long) -> Unit
        ): StreamResolver.Stream {
            val stream = StreamResolver.Stream(
                url = "https://example.com/audio/$videoId.m4a",
                kbps = 128,
                mimeType = "audio/mp4; codecs=\"mp4a.40.2\""
            )
            val audioBytes = ByteArray(1024) { 0x55 }
            openSink(stream).use { out ->
                out.write(audioBytes)
                onProgress(audioBytes.size.toLong(), audioBytes.size.toLong())
            }
            return stream
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempBaseDir = Files.createTempDirectory("bitchord_auto_lyrics_test_").toFile()
        mockPrefs = InMemorySharedPreferences()
        testContext = TestContextWithStorage(mockPrefs, tempBaseDir)

        LyricsStore.customStorageDir = File(tempBaseDir, "lyrics_store")
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        LyricsRepository.lyricsDispatcher = testDispatcher
        lyricsOfflineModeOverride = false
        resetLyricsNetworkStats()

        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = fastStreamFetcher,
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            maxConcurrentWorkers = 2,
            enableOnlineArtworkFetch = false
        )
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        LyricsRepository.lyricsDispatcher = Dispatchers.IO
        lyricsOfflineModeOverride = null
        resetLyricsNetworkStats()
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    private fun sampleLyricsFor(title: String): List<LyricLine> {
        return listOf(
            LyricLine(
                text = "First verse of $title",
                timeMs = 1000L,
                words = listOf(
                    LyricWord(text = "First", startMs = 1000L, endMs = 1400L),
                    LyricWord(text = "verse", startMs = 1400L, endMs = 1800L),
                    LyricWord(text = "of", startMs = 1800L, endMs = 2000L),
                    LyricWord(text = title, startMs = 2000L, endMs = 2600L)
                )
            ),
            LyricLine(
                text = "Second chorus of $title",
                timeMs = 3000L,
                words = listOf(
                    LyricWord(text = "Second", startMs = 3000L, endMs = 3500L),
                    LyricWord(text = "chorus", startMs = 3500L, endMs = 4000L),
                    LyricWord(text = "of", startMs = 4000L, endMs = 4200L),
                    LyricWord(text = title, startMs = 4200L, endMs = 5000L)
                )
            )
        )
    }

    /**
     * TEST 1 — ESCENARIO PRINCIPAL (DEFECTO IDENTIFICADO EN PRUEBA FÍSICA):
     * Se descargan 4 canciones con conexión a Internet.
     * El usuario NUNCA abre el reproductor ni la pantalla de letras para ninguna de ellas.
     * Al desconectar la red (100% offline), las 4 canciones tienen sus letras disponibles de inmediato
     * con 0 llamadas a la red.
     */
    @Test
    fun `automaticAcquisition_fourSongsDownloadedOnline_allLyricsAvailableOfflineWithoutPriorViewing`() = runTest(testDispatcher) {
        val songs = listOf(
            Song(videoId = "vid_alpha", title = "Alpha Song", artist = "Artist A", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "vid_beta", title = "Beta Song", artist = "Artist B", thumbnailUrl = null, durationText = "03:15"),
            Song(videoId = "vid_gamma", title = "Gamma Song", artist = "Artist C", thumbnailUrl = null, durationText = "02:45"),
            Song(videoId = "vid_delta", title = "Delta Song", artist = "Artist D", thumbnailUrl = null, durationText = "04:00")
        )

        // Mock ProviderFetcher: simula respuesta exitosa con letras sincronizadas
        val queriedVideoIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, videoId, title, artist, _, _, _, _, _ ->
            queriedVideoIds.add(videoId)
            sampleLyricsFor(title)
        }

        // Descargar las 4 canciones en batch
        Downloads.enqueueAll(songs)
        advanceUntilIdle()

        // 1. Verificar que las 4 canciones completaron la descarga de audio
        for (song in songs) {
            assertTrue("Audio para ${song.videoId} debe estar descargado", Downloads.isDownloaded(song.videoId))
            val savedUri = Downloads.verifiedSavedUriString(song.videoId)
            assertNotNull("URI guardada de ${song.videoId} no debe ser nula", savedUri)
            val audioFile = File(java.net.URI(savedUri!!))
            assertTrue("Archivo físico de audio debe existir y no estar vacío", audioFile.exists() && audioFile.length() > 0L)

            // 2. Verificar que se persistió el archivo sidecar .lrc contiguo
            val sidecarFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            assertTrue("Sidecar .lrc para ${song.videoId} debe existir en disco", sidecarFile.exists())
            assertTrue("Sidecar .lrc debe contener texto", sidecarFile.length() > 0L)

            // 3. Verificar que se persistió en LyricsStore
            assertTrue("LyricsStore debe tener letras para ${song.videoId}", LyricsStore.has(null, song.videoId))
            val storedLines = LyricsStore.getLines(null, song.videoId)
            assertNotNull(storedLines)
            assertEquals("Deben coincidir las líneas de letra", 2, storedLines!!.size)
            assertTrue(storedLines[0].text.contains(song.title))
        }

        assertEquals("Debieron consultarse exactamente las 4 pistas", 4, queriedVideoIds.size)

        // 4. Limpiar caché en memoria para asegurar que no hay lectura de RAM
        LyricsRepository.clearMemoryCache()

        // 5. APAGAR INTERNET (Simular desconexión Wi-Fi de la tablet)
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        // 6. Intentar resolver las letras de las 4 canciones en modo OFFLINE
        for (song in songs) {
            val offlineState = LyricsRepository.resolveForSong(song, testContext)
            assertTrue("La letra de ${song.videoId} debe ser Synced o EmbeddedLocal", offlineState is LyricsUiState.Synced || offlineState is LyricsUiState.EmbeddedLocal)
            val lines = when (offlineState) {
                is LyricsUiState.Synced -> offlineState.lines
                is LyricsUiState.EmbeddedLocal -> offlineState.lines
                else -> emptyList()
            }
            assertTrue("Debe contener líneas de letra", lines.isNotEmpty())
            assertTrue("Debe contener el título de la canción", lines[0].text.contains(song.title))
        }

        // 7. CERO LLAMADAS DE RED OFFLINE
        assertEquals("En modo offline no debe realizarse ninguna llamada de red", 0, lyricsNetworkCallCount.get())
    }

    /**
     * TEST 2 — CERO LLAMADAS DE RED SI LAS LETRAS YA EXISTÍAN LOCALMENTE:
     * Si una canción ya tenía letras persistidas antes de descargarse, la descarga no realiza
     * ninguna petición de red para letras (`lyricsNetworkCallCount == 0`) y genera el sidecar .lrc.
     */
    @Test
    fun `automaticAcquisition_lyricsAlreadyCachedLocally_zeroNetworkCallsDuringDownload`() = runTest(testDispatcher) {
        val song = Song(videoId = "vid_cached", title = "Already Cached Track", artist = "Artist Cache", thumbnailUrl = null, durationText = "03:00")
        val preLines = sampleLyricsFor(song.title)

        // Pre-guardar en LyricsStore
        LyricsStore.save(null, song.videoId, preLines, LyricsSource.LRCLIB)
        assertTrue(LyricsStore.has(null, song.videoId))

        var providerCalled = false
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            providerCalled = true
            preLines
        }

        resetLyricsNetworkStats()

        // Descargar la canción
        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded(song.videoId))
        assertFalse("El proveedor de red NO debió llamarse ya que existían letras locales", providerCalled)
        assertEquals("Cero peticiones de red", 0, lyricsNetworkCallCount.get())

        // Verificar que el sidecar se generó a partir de las letras locales
        val savedUri = Downloads.verifiedSavedUriString(song.videoId)!!
        val audioFile = File(java.net.URI(savedUri))
        val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        assertTrue("El sidecar .lrc debe haberse creado a partir de la caché", sidecar.exists() && sidecar.length() > 0L)
    }

    /**
     * TEST 3 — DESCARGA EN MODO OFFLINE NO FALLA EL AUDIO Y MARCA LETRAS COMO PENDIENTES:
     * Si el usuario descarga una canción mientras no hay Internet (p. ej. desde caché de streaming local),
     * el audio se descarga exitosamente y las letras quedan registradas en `pendingLyricsIds`.
     */
    @Test
    fun `automaticAcquisition_offlineModeDuringDownload_marksPendingWithoutFailingAudio`() = runTest(testDispatcher) {
        lyricsOfflineModeOverride = true // Sin internet

        val song = Song(videoId = "vid_offline_dl", title = "Offline DL Track", artist = "Artist Offline", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueue(song)
        advanceUntilIdle()

        // El audio completa sin problemas
        assertTrue("El audio debe completarse exitosamente", Downloads.isDownloaded(song.videoId))
        assertFalse("No debe haber letras en disco aún", LyricsStore.has(null, song.videoId))

        // Letras marcadas como pendientes
        assertTrue("Debe registrarse como pendiente", Downloads.hasPendingLyrics(song.videoId))
        assertEquals(1, Downloads.getPendingLyricsCount())
    }

    /**
     * TEST 4 — RECONEXIÓN Y REINTENTO DE LETRAS PENDIENTES:
     * Al volver la conectividad (`retryPendingLyrics`), se descargan y persisten las letras
     * de todas las pistas que estaban pendientes.
     */
    @Test
    fun `automaticAcquisition_reconnectionRetriesPendingLyrics`() = runTest(testDispatcher) {
        lyricsOfflineModeOverride = true // Comenzar offline
        val song = Song(videoId = "vid_reconnect", title = "Reconnect Track", artist = "Artist Reconnect", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded(song.videoId))
        assertTrue(Downloads.hasPendingLyrics(song.videoId))

        // Configurar provider para cuando regrese la red
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            sampleLyricsFor(song.title)
        }

        // Restablecer conexión
        lyricsOfflineModeOverride = false
        val retryJobs = Downloads.retryPendingLyrics()
        retryJobs.forEach { it.join() }
        advanceUntilIdle()

        // Verificar que ya no está pendiente y las letras se persistieron
        assertFalse("Ya no debe estar pendiente", Downloads.hasPendingLyrics(song.videoId))
        assertEquals(0, Downloads.getPendingLyricsCount())
        assertTrue("LyricsStore debe tener las letras", LyricsStore.has(null, song.videoId))

        val savedUri = Downloads.verifiedSavedUriString(song.videoId)!!
        val audioFile = File(java.net.URI(savedUri))
        val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        assertTrue("El sidecar .lrc debe existir tras el reintento exitoso", sidecar.exists())
    }

    /**
     * TEST 5 — PROVEEDOR SIN RESULTADOS (GRACEFUL HANDLING):
     * Si ningún proveedor tiene letras para una canción, el audio descarga correctamente,
     * no se crean letras ficticias/alucinadas, y no queda permanentemente bloqueada.
     */
    @Test
    fun `automaticAcquisition_providerReturnsNoResults_audioSucceedsWithoutCreatingFakeLyrics`() = runTest(testDispatcher) {
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            null // Ningún resultado
        }

        val song = Song(videoId = "vid_none", title = "Instrumental Track", artist = "Unknown", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("El audio debe descargarse correctamente", Downloads.isDownloaded(song.videoId))
        assertFalse("No deben guardarse letras inexistentes", LyricsStore.has(null, song.videoId))
        assertFalse("No debe quedar como pendiente de error ya que el proveedor respondió legítimamente vacío", Downloads.hasPendingLyrics(song.videoId))
    }

    /**
     * TEST 6 — ERROR DE RED TEMPORAL DURANTE LA BÚSQUEDA DE LETRAS:
     * Si la búsqueda de letras arroja una excepción de red, el audio se descarga sin fallar
     * y la pista se marca como pendiente para retry posterior.
     */
    @Test
    fun `automaticAcquisition_providerThrowsTransientNetworkError_audioSucceedsAndLyricsMarkedPending`() = runTest(testDispatcher) {
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            throw IOException("503 Service Temporarily Unavailable")
        }

        val song = Song(videoId = "vid_net_error", title = "Error Track", artist = "Artist Error", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("El audio no debe fallar si las letras tienen error de red", Downloads.isDownloaded(song.videoId))
        assertTrue("Debe quedar marcada como pendiente debido al fallo de red", Downloads.hasPendingLyrics(song.videoId))
    }

    /**
     * TEST 7 — DEDUPLICACIÓN CONCURRENTE:
     * Llamadas simultáneas para adquirir letras del mismo `videoId` reutilizan el Job activo
     * evitando consultas duplicadas a los proveedores.
     */
    @Test
    fun `automaticAcquisition_deduplicatesConcurrentQueriesForSameTrack`() = runTest(testDispatcher) {
        val queriedVideoIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, videoId, _, _, _, _, _, _, _ ->
            queriedVideoIds.add(videoId)
            kotlinx.coroutines.delay(50L)
            sampleLyricsFor("Concurrent Track")
        }

        val song = Song(videoId = "vid_concurrent", title = "Concurrent Track", artist = "Artist Concurrent", thumbnailUrl = null, durationText = "03:00")

        val job1 = Downloads.triggerLyricsAcquisition(song)
        val job2 = Downloads.triggerLyricsAcquisition(song)

        assertEquals("Ambas llamadas concurrentes deben compartir la misma instancia de Job", job1, job2)
        job1.join()
        advanceUntilIdle()

        assertEquals("Solo debe realizarse consulta para 1 pista única", 1, queriedVideoIds.size)
        assertTrue(LyricsStore.has(null, song.videoId))
    }

    /**
     * TEST 8 — ELIMINACIÓN DE DESCARGA:
     * Al borrar una descarga completada mediante `Downloads.delete(videoId)`, se eliminan
     * el archivo de audio, el archivo sidecar .lrc contiguo y el registro en `LyricsStore`.
     */
    @Test
    fun `deletion_removesAudioSidecarAndLyricsStore`() = runTest(testDispatcher) {
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            sampleLyricsFor("To Delete")
        }

        val song = Song(videoId = "vid_delete", title = "To Delete", artist = "Artist Delete", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded(song.videoId))
        assertTrue(LyricsStore.has(null, song.videoId))

        val savedUri = Downloads.verifiedSavedUriString(song.videoId)!!
        val audioFile = File(java.net.URI(savedUri))
        val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        assertTrue(audioFile.exists())
        assertTrue(sidecar.exists())

        // Ejecutar eliminación
        Downloads.delete(song.videoId)

        assertFalse("Audio físico debe eliminarse", audioFile.exists())
        assertFalse("Sidecar .lrc debe eliminarse", sidecar.exists())
        assertFalse("LyricsStore debe limpiar el registro", LyricsStore.has(null, song.videoId))
        assertFalse("No debe figurar como descargada", Downloads.isDownloaded(song.videoId))
    }

    /**
     * TEST 9 — PRESERVACIÓN DE ETIQUETAS DE VERSIÓN (REMIX, LIVE, ACOUSTIC):
     * La limpieza de títulos para consulta de letras debe eliminar artefactos de video
     * (Official Video, HD, 4K, feat.) pero PRESERVAR rigurosamente etiquetas de versión como
     * (Remix), (Live), (Acoustic), evitando asociar letras de la versión de estudio original.
     */
    @Test
    fun `versionLabelPreservation_remixAndLiveLabelsPreservedInLyricsSearchQuery`() {
        val remixTitle = "Levitating (Remix) (Official Music Video)"
        val liveTitle = "Hotel California (Live at the Forum) [HD]"
        val acousticTitle = "Everlong (Acoustic Version) [Lyrics Video]"
        val videoArtifactTitle = "Blinding Lights (Official Video) (feat. Rosalía)"

        assertEquals("Levitating (Remix)", remixTitle.forLyricsSearch())
        assertEquals("Hotel California (Live at the Forum)", liveTitle.forLyricsSearch())
        assertEquals("Everlong (Acoustic Version)", acousticTitle.forLyricsSearch())
        assertEquals("Blinding Lights", videoArtifactTitle.forLyricsSearch())
    }

    /**
     * TEST 10 — JERARQUÍA DE PREFERENCIA: WORD-SYNC > LINE-SYNC > TEXTO PLANO:
     * Al consultar proveedores, se debe priorizar la versión sincronizada por palabra;
     * si no está disponible, la sincronización por línea; y en última instancia, texto plano.
     */
    @Test
    fun `fallbackPreference_prefersWordSyncThenLineSyncThenPlainText`() = runTest(testDispatcher) {
        val wordSyncLine = listOf(
            LyricLine(
                text = "Karaoke line",
                timeMs = 1000L,
                words = listOf(LyricWord(startMs = 1000L, endMs = 1500L, text = "Karaoke"), LyricWord(startMs = 1500L, endMs = 2000L, text = "line"))
            )
        )
        val lineSyncLine = listOf(LyricLine(text = "Line sync line", timeMs = 1000L))
        val plainTextLine = listOf(LyricLine(text = "Plain text line", timeMs = 0L))

        // 1. Probar que palabra-sync es preferida sobre línea-sync
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> wordSyncLine
                LyricsSource.LRCLIB -> lineSyncLine
                else -> plainTextLine
            }
        }

        val songWord = Song(videoId = "vid_word_sync", title = "Word Sync Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val stateWord = LyricsRepository.resolveForSong(songWord, testContext)
        assertTrue(stateWord is LyricsUiState.Synced)
        assertTrue("Debe tener sincronización por palabra", (stateWord as LyricsUiState.Synced).isWordSynced)

        // 2. Probar que línea-sync es preferida sobre texto plano
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> null // Sin palabra-sync
                LyricsSource.LRCLIB -> lineSyncLine
                else -> plainTextLine
            }
        }

        val songLine = Song(videoId = "vid_line_sync", title = "Line Sync Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val stateLine = LyricsRepository.resolveForSong(songLine, testContext)
        assertTrue(stateLine is LyricsUiState.Synced)
        assertFalse("No debe tener palabra-sync", (stateLine as LyricsUiState.Synced).isWordSynced)

        // 3. Probar fallback a texto plano si ninguno tiene sincronización
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.GENIUS -> plainTextLine
                else -> null
            }
        }

        val songPlain = Song(videoId = "vid_plain", title = "Plain Text Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val statePlain = LyricsRepository.resolveForSong(songPlain, testContext)
        assertTrue(statePlain is LyricsUiState.Plain)
    }

    /**
     * TEST 11 — CANCELACIÓN DE DESCARGA:
     * Cancelar una descarga remueve la pista de la lista de letras pendientes.
     */
    @Test
    fun `cancelDownload_removesPendingLyricsTracking`() = runTest(testDispatcher) {
        lyricsOfflineModeOverride = true // Offline so it would be pending

        val song = Song(videoId = "vid_cancel_lyrics", title = "Cancel Lyrics", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.hasPendingLyrics(song.videoId))

        Downloads.cancel(song.videoId)
        assertFalse("Al cancelar descarga, se limpia de pendingLyrics", Downloads.hasPendingLyrics(song.videoId))
    }
}

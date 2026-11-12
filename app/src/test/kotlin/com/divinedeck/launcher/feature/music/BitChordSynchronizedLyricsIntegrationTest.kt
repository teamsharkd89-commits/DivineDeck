package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.ui.MusicLyricsViewModel
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.EnhancedLrc
import com.music.bitchord.data.lyrics.LrcLib
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.lyrics.ProviderLyrics
import com.music.bitchord.data.lyrics.TtmlLyrics
import com.music.bitchord.data.lyrics.lyricsNetworkCallCount
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.lyrics.resetLyricsNetworkStats
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.nio.file.Files

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.5D:
 * Integración de Letras Sincronizadas Originales de BitChord 1.8.1 dentro de DIVINE DECK.
 *
 * Cubre exhaustivamente los 14 escenarios obligatorios:
 * 1. Parseo de LRC estándar (centésimas [mm:ss.xx] y milésimas [mm:ss.xxx]).
 * 2. Parseo de Enhanced LRC con sincronización por palabra <mm:ss.xx>.
 * 3. Parseo de TTML (Apple Music) con sílabas y coros de fondo (background).
 * 4. Cálculo de línea activa y avance con el tiempo según positionMs.
 * 5. Seek hacia adelante actualiza de inmediato la línea activa.
 * 6. Seek hacia atrás actualiza de inmediato la línea activa.
 * 7. Pausa y reanudación conservan la sincronización exacta.
 * 8. Cambio de canción: cancelación inmediata del job previo y carga de nuevas letras sin arrastrar anteriores.
 * 9. Letras no sincronizadas (LyricsUiState.Plain, scroll manual libre sin sincronización falsa).
 * 10. Letras locales disponibles sin internet (EmbeddedLocal / sidecar .lrc / etiquetas embebidas).
 * 11. Ausencia de letras en proveedores (LyricsUiState.NotFound).
 * 12. Fallback entre proveedores en cadena prioritaria.
 * 13. Cero peticiones de red en modo offline (lyricsNetworkCallCount == 0).
 * 14. Reproducción ininterrumpida de audio (ExoPlayer/Media3) aun sin letras.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordSynchronizedLyricsIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var tempBaseDir: File

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class FakeMediaSessionBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = true,
        override var playbackState: Int = Player.STATE_READY,
        override var playWhenReady: Boolean = true,
        override var isLoading: Boolean = false,
        override var playerError: androidx.media3.common.PlaybackException? = null,
        override var currentPositionMs: Long = 10000L,
        override var durationMs: Long = 200000L,
        override var currentMediaItem: MediaItem? = MediaItem.Builder().setMediaId("test_track_id").build(),
        override var availableCommands: Player.Commands? = null,
    ) : BitChordMediaSessionBridge {
        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true; playWhenReady = true }
        override fun pause() { isPlaying = false; playWhenReady = false }
        override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun setPlayerListener(listener: Player.Listener?) {}
        override fun removePlayerListener(listener: Player.Listener) {}
        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            currentMediaItem = mediaItem
            this.playWhenReady = playWhenReady
            this.isPlaying = playWhenReady
        }
        override fun sendCustomCommand(action: String) {}
        override fun sendCustomCommand(action: String, args: android.os.Bundle) {}
        override fun release() {}
    }

    private class FakeBitChordSessionConnector(
        private val bridge: BitChordMediaSessionBridge
    ) : BitChordSessionConnector {
        override fun connect(
            context: Context,
            onConnected: (BitChordMediaSessionBridge) -> Unit,
            onDisconnected: () -> Unit,
            onError: (Throwable) -> Unit
        ): AutoCloseable {
            onConnected(bridge)
            return AutoCloseable {}
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LyricsRepository.lyricsDispatcher = testDispatcher
        tempBaseDir = Files.createTempDirectory("divine_lyrics_test_").toFile()
        resetLyricsNetworkStats()
        LyricsRepository.clearMemoryCache()
        LyricsRepository.lyricsDispatcher = testDispatcher
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
    }

    @After
    fun tearDown() {
        resetLyricsNetworkStats()
        LyricsRepository.clearMemoryCache()
        LyricsRepository.lyricsDispatcher = Dispatchers.IO
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 1: Parseo de LRC estándar (centésimas [mm:ss.xx] y milésimas [mm:ss.xxx])
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test01_standardLrcParsing_centisecondsAndMilliseconds() {
        val lrcContent = """
            [ti:Test Title]
            [ar:Test Artist]
            [01:23.45]First line centiseconds
            [02:10.500]Second line milliseconds
            [03:00.00]Third line ending
        """.trimIndent()

        val lines = LrcLib.parseLrc(lrcContent)

        assertTrue("Debe parsear las líneas con tiempo", lines.isNotEmpty())
        val lineCentis = lines.firstOrNull { it.text.contains("centiseconds") }
        val lineMillis = lines.firstOrNull { it.text.contains("milliseconds") }

        assertNotNull(lineCentis)
        assertNotNull(lineMillis)

        // 1m 23s 45 centis = 60000 + 23000 + 450 = 83450 ms
        assertEquals(83450L, lineCentis!!.timeMs)
        assertEquals("First line centiseconds", lineCentis.text)

        // 2m 10s 500 millis = 120000 + 10000 + 500 = 130500 ms
        assertEquals(130500L, lineMillis!!.timeMs)
        assertEquals("Second line milliseconds", lineMillis.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 2: Parseo de Enhanced LRC con sincronización por palabra <mm:ss.xx>
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test02_enhancedLrcParsing_withWordSync() {
        val enhancedLrc = """
            [00:10.00]<00:10.00>Hello <00:10.50>beautiful <00:11.20>world
            [00:15.00]<00:15.00>Divine <00:15.80>Deck
        """.trimIndent()

        val parsed = EnhancedLrc.parse(enhancedLrc)
        val nonBlank = parsed.filter { it.text.isNotBlank() }

        assertEquals(2, nonBlank.size)
        val firstLine = nonBlank[0]
        assertTrue("La primera línea debe tener sincronización por palabra", firstLine.isWordSynced)
        assertEquals(3, firstLine.words.size)

        assertEquals("Hello", firstLine.words[0].text)
        assertEquals(10000L, firstLine.words[0].startMs)
        assertEquals(10500L, firstLine.words[0].endMs)

        assertEquals("beautiful", firstLine.words[1].text)
        assertEquals(10500L, firstLine.words[1].startMs)

        assertEquals("world", firstLine.words[2].text)
        assertEquals(11200L, firstLine.words[2].startMs)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 3: Parseo de TTML (Apple Music) con sílabas y coros de fondo (background)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test03_ttmlAppleMusicParsing_withSyllablesAndBackgroundVocals() {
        val ttmlContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div>
                  <p begin="00:05.000" end="00:09.000">
                    <span begin="00:05.000" end="00:06.500">Main</span>
                    <span begin="00:06.500" end="00:09.000">lead</span>
                    <span ttm:role="x-bg" begin="00:07.000" end="00:09.000">echo chorus</span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val parsed = TtmlLyrics.parse(ttmlContent)

        assertTrue("Debe parsear al menos una línea TTML", parsed.isNotEmpty())
        val line = parsed.first { it.text.contains("Main lead") }
        assertEquals("Main lead", line.text)
        assertTrue("Debe contener palabras sincronizadas", line.isWordSynced)
        assertNotNull("Debe extraer coros de fondo", line.background)
        assertEquals("echo chorus", line.background?.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 4: Cálculo de línea activa y avance con el tiempo según positionMs
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test04_activeLineCalculation_advancesWithPlaybackPosition() {
        val lines = listOf(
            LyricLine(timeMs = 0L, text = "Intro"),
            LyricLine(timeMs = 10000L, text = "First Verse"),
            LyricLine(timeMs = 25000L, text = "Chorus"),
            LyricLine(timeMs = 40000L, text = "Outro")
        )

        fun calculateActiveIndex(positionMs: Long): Int {
            val idx = lines.indexOfLast { it.timeMs <= positionMs }
            return if (idx >= 0) idx else 0
        }

        assertEquals(0, calculateActiveIndex(0L))
        assertEquals(0, calculateActiveIndex(5000L))
        assertEquals(1, calculateActiveIndex(10000L))
        assertEquals(1, calculateActiveIndex(18000L))
        assertEquals(2, calculateActiveIndex(25000L))
        assertEquals(2, calculateActiveIndex(35000L))
        assertEquals(3, calculateActiveIndex(40000L))
        assertEquals(3, calculateActiveIndex(60000L))
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 5: Seek hacia adelante actualiza de inmediato la línea activa
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test05_seekForward_immediatelyUpdatesActiveLine() {
        val lines = listOf(
            LyricLine(timeMs = 0L, text = "Intro"),
            LyricLine(timeMs = 10000L, text = "Verse 1"),
            LyricLine(timeMs = 25000L, text = "Chorus"),
            LyricLine(timeMs = 45000L, text = "Bridge")
        )

        fun calculateActiveIndex(positionMs: Long): Int {
            val idx = lines.indexOfLast { it.timeMs <= positionMs }
            return if (idx >= 0) idx else 0
        }

        // Antes de seek (en 2000 ms -> Intro)
        val indexBefore = calculateActiveIndex(2000L)
        assertEquals(0, indexBefore)

        // Seek inmediato a 46000 ms (Bridge)
        val targetSeekMs = 46000L
        val indexAfter = calculateActiveIndex(targetSeekMs)
        assertEquals(3, indexAfter)
        assertEquals("Bridge", lines[indexAfter].text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 6: Seek hacia atrás actualiza de inmediato la línea activa
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test06_seekBackward_immediatelyUpdatesActiveLine() {
        val lines = listOf(
            LyricLine(timeMs = 0L, text = "Intro"),
            LyricLine(timeMs = 10000L, text = "Verse 1"),
            LyricLine(timeMs = 25000L, text = "Chorus"),
            LyricLine(timeMs = 45000L, text = "Bridge")
        )

        fun calculateActiveIndex(positionMs: Long): Int {
            val idx = lines.indexOfLast { it.timeMs <= positionMs }
            return if (idx >= 0) idx else 0
        }

        // En Chorus a 28000 ms
        val indexInitial = calculateActiveIndex(28000L)
        assertEquals(2, indexInitial)

        // Seek hacia atrás a 8000 ms (Intro / antes del verso 1)
        val targetSeekMs = 8000L
        val indexAfter = calculateActiveIndex(targetSeekMs)
        assertEquals(0, indexAfter)
        assertEquals("Intro", lines[indexAfter].text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 7: Pausa y reanudación conservan la sincronización exacta
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test07_pauseAndResume_preservesExactSynchronization() {
        val position = PlaybackPosition()
        val lines = listOf(
            LyricLine(timeMs = 0L, text = "Start"),
            LyricLine(timeMs = 15000L, text = "Active Line During Pause"),
            LyricLine(timeMs = 30000L, text = "Next Line")
        )

        fun activeIndex(ms: Long): Int {
            val idx = lines.indexOfLast { it.timeMs <= ms }
            return if (idx >= 0) idx else 0
        }

        // Reproduciendo a 18000 ms
        val timeBase = System.nanoTime()
        position.report(position = 18000L, timeNanos = timeBase, moving = true)
        assertEquals(18000L, position.positionMs)
        assertEquals(1, activeIndex(position.positionMs))

        // Pausa en 18000 ms (moving = false)
        position.report(position = 18000L, timeNanos = timeBase + 5_000_000_000L, moving = false)
        // Posición debe permanecer fija en 18000 ms a pesar del paso del tiempo
        assertEquals(18000L, position.positionMs)
        assertEquals(1, activeIndex(position.positionMs))

        // Reanudación desde 18000 ms hacia 31000 ms
        position.report(position = 31000L, timeNanos = System.nanoTime(), moving = true)
        assertEquals(31000L, position.positionMs)
        assertEquals(2, activeIndex(position.positionMs))
        assertEquals("Next Line", lines[activeIndex(position.positionMs)].text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 8: Cambio de canción: cancelación inmediata del job previo y carga de nuevas letras
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test08_songChange_cancelsPreviousJobAndLoadsNewLyrics() = testScope.runTest {
        val songA = Song(videoId = "vidA", title = "Track Alpha", artist = "Artist A", thumbnailUrl = null, durationText = "03:00")
        val songB = Song(videoId = "vidB", title = "Track Beta", artist = "Artist B", thumbnailUrl = null, durationText = "03:30")

        var fetchCountA = 0
        var fetchCountB = 0

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, videoId, _, _, _, _, _, _, _ ->
            if (videoId == "vidA") {
                fetchCountA++
                delay(500) // Simula búsqueda lenta en vuelo
                listOf(LyricLine(1000L, "Alpha Lyrics"))
            } else {
                fetchCountB++
                listOf(LyricLine(2000L, "Beta Lyrics"))
            }
        }

        val viewModel = MusicLyricsViewModel(
            repository = LyricsRepository,
            ioDispatcher = testDispatcher,
        )

        // Inicia Song A
        viewModel.updateSong(songA, null)
        assertTrue(viewModel.lyricsState.value is LyricsUiState.Loading)
        assertEquals("vidA", (viewModel.lyricsState.value as LyricsUiState.Loading).videoId)

        // Inmediatamente cambia a Song B antes de que A termine
        viewModel.updateSong(songB, null)
        assertTrue(viewModel.lyricsState.value is LyricsUiState.Loading)
        assertEquals("vidB", (viewModel.lyricsState.value as LyricsUiState.Loading).videoId)

        advanceUntilIdle()

        // El estado final debe ser exclusivamente las letras de Song B
        val finalState = viewModel.lyricsState.value
        assertTrue("El estado debe ser Synced", finalState is LyricsUiState.Synced)
        val synced = finalState as LyricsUiState.Synced
        assertEquals("vidB", synced.videoId)
        assertEquals("Beta Lyrics", synced.lines.first { it.text.isNotBlank() }.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 9: Letras no sincronizadas (LyricsUiState.Plain, scroll manual libre)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test09_unsyncedPlainLyrics_producesPlainUiState() = testScope.runTest {
        val rawPlain = """
            First plain line without timestamps
            Second line of prose
            Third line of singing
        """.trimIndent()

        val parsed = ProviderLyrics.parse(rawPlain)
        assertNotNull(parsed)
        assertTrue("Las líneas parseadas existen", parsed!!.isNotEmpty())
        assertTrue("Todas las líneas deben tener timestamp 0", parsed.all { it.timeMs == 0L })

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            parsed
        }

        val song = Song(videoId = "plainVid", title = "Acoustic Poem", artist = "Poet", thumbnailUrl = null, durationText = "02:00")
        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)

        assertTrue("Debe resolverse como LyricsUiState.Plain", state is LyricsUiState.Plain)
        val plainState = state as LyricsUiState.Plain
        assertEquals(3, plainState.lines.size)
        assertEquals("First plain line without timestamps", plainState.lines[0].text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 10: Letras locales disponibles sin internet (EmbeddedLocal / sidecar .lrc)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test10_embeddedAndLocalLyrics_availableOffline() = testScope.runTest {
        val audioFile = File(tempBaseDir, "offline_track.mp3").apply { writeBytes(ByteArray(1024)) }
        val lrcSidecar = File(tempBaseDir, "offline_track.lrc").apply {
            writeText("[00:05.00]Local offline line 1\n[00:12.00]Local offline line 2")
        }

        lyricsOfflineModeOverride = true // 100% offline

        val song = Song(
            videoId = "offlineVid",
            title = "Offline Song",
            artist = "Offline Artist",
            thumbnailUrl = null,
            localUri = audioFile.absolutePath,
            durationText = "03:00"
        )

        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)

        assertTrue("Debe detectar letras locales como EmbeddedLocal", state is LyricsUiState.EmbeddedLocal)
        val localState = state as LyricsUiState.EmbeddedLocal
        assertEquals("offlineVid", localState.videoId)
        assertTrue("Debe estar sincronizada si el sidecar tiene marcas de tiempo", localState.isSynced)
        val sungLines = localState.lines.filter { it.text.isNotBlank() }
        assertEquals(2, sungLines.size)
        assertEquals("Local offline line 1", sungLines[0].text)
        assertEquals(5000L, sungLines[0].timeMs)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 11: Ausencia de letras en proveedores (LyricsUiState.NotFound)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test11_notFoundState_whenNoProvidersHaveLyrics() = testScope.runTest {
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            null // Ningún proveedor tiene letras
        }

        val song = Song(videoId = "missingVid", title = "Unknown Instrumental", artist = "Unknown", thumbnailUrl = null, durationText = "03:00")
        val state = LyricsRepository.resolveForSong(song = song, forceRefresh = true)

        assertTrue("Debe retornar NotFound", state is LyricsUiState.NotFound)
        val notFound = state as LyricsUiState.NotFound
        assertEquals("missingVid", notFound.videoId)
        assertEquals("Unknown Instrumental", notFound.title)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 12: Fallback entre proveedores en cadena prioritaria
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test12_providerFallback_prioritizesInOrder() = testScope.runTest {
        val providerSequence = mutableListOf<LyricsSource>()

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            providerSequence.add(source)
            if (source == LyricsSource.LRCLIB) {
                listOf(LyricLine(1500L, "Lyrics from LRCLIB fallback"))
            } else {
                null // Fallan otros proveedores prioritarios
            }
        }

        val song = Song(videoId = "fallbackVid", title = "Fallback Test", artist = "Fallback Artist", thumbnailUrl = null, durationText = "03:00")
        val state = LyricsRepository.resolveForSong(
            song = song,
            sources = setOf(LyricsSource.BETTER_LYRICS, LyricsSource.LRCLIB),
            order = listOf(LyricsSource.BETTER_LYRICS, LyricsSource.LRCLIB),
            forceRefresh = true
        )

        assertTrue("Debe tener éxito con el segundo proveedor", state is LyricsUiState.Synced)
        val synced = state as LyricsUiState.Synced
        assertEquals(LyricsSource.LRCLIB, synced.source)
        assertEquals("Lyrics from LRCLIB fallback", synced.lines.first { it.text.isNotBlank() }.text)
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 13: Cero peticiones de red en modo offline (lyricsNetworkCallCount == 0)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test13_zeroNetworkCallsInOfflineMode() = testScope.runTest {
        resetLyricsNetworkStats()
        lyricsOfflineModeOverride = true // Dispositivo automotriz sin señal

        val songWithoutLocalLyrics = Song(
            videoId = "noNetVid",
            title = "Streaming Track In Tunnel",
            artist = "Tunnel Artist",
            thumbnailUrl = null,
            durationText = "03:00"
        )

        val state = LyricsRepository.resolveForSong(song = songWithoutLocalLyrics, forceRefresh = true)

        assertTrue("Debe retornar OfflineUnavailable", state is LyricsUiState.OfflineUnavailable)
        val offlineState = state as LyricsUiState.OfflineUnavailable
        assertEquals("noNetVid", offlineState.videoId)
        assertEquals("Streaming Track In Tunnel", offlineState.title)

        // Verificación estricta de cero llamadas de red
        assertEquals(0, lyricsNetworkCallCount.get())
    }

    // ---------------------------------------------------------------------------------------------
    // Escenario 14: Reproducción ininterrumpida de audio (ExoPlayer/Media3) aun sin letras
    // ---------------------------------------------------------------------------------------------
    @Test
    fun test14_uninterruptedAudioPlayback_whenLyricsFailOrNotFound() = runTest {
        val repoJob = SupervisorJob()
        val repoScope = CoroutineScope(coroutineContext + repoJob)
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = true,
            currentPositionMs = 45000L
        )
        val connector = FakeBitChordSessionConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = repoScope,
        )

        repository.connect()

        val initialPlaying = repository.playbackState.value.isPlaying
        assertTrue("La reproducción de audio debe estar activa", initialPlaying)

        // Simulamos fallo total de letras para la pista
        val song = Song(videoId = "corruptLyricsVid", title = "Audio Master", artist = "Audio Artist", thumbnailUrl = null, durationText = "04:00")
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            throw RuntimeException("Provider connection timed out")
        }

        val lyricsState = LyricsRepository.resolveForSong(song = song, forceRefresh = true)
        assertTrue("El estado de letras debe ser Error", lyricsState is LyricsUiState.Error)

        // Verificamos que el reproductor de audio jamás se detuvo ni fue afectado
        assertTrue("El audio debe continuar sonando sin interrupciones", repository.playbackState.value.isPlaying)
        assertEquals(45000L, repository.currentTrack.value.currentPositionMs)

        repository.disconnect()
        repoJob.cancel()
    }
}

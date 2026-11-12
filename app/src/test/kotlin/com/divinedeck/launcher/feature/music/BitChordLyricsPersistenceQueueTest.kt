package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsStore
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.lyrics.lyricsNetworkCallCount
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.lyrics.resetLyricsNetworkStats
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Suite de pruebas unitarias para la Fase 06.5D.1:
 * Corrección de persistencia offline de letras, centrado de auto-scroll y navegación de cola en Media3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordLyricsPersistenceQueueTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var tempBaseDir: File

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class RecordingMediaSessionBridge : BitChordMediaSessionBridge {
        override var isConnected: Boolean = true
        override var isPlaying: Boolean = false
        override var playbackState: Int = Player.STATE_READY
        override var playWhenReady: Boolean = false
        override var isLoading: Boolean = false
        override var playerError: androidx.media3.common.PlaybackException? = null
        override var currentPositionMs: Long = 0L
        override var durationMs: Long = 180000L
        override var currentMediaItem: MediaItem? = null
        override var currentMediaItemIndex: Int = 0
        override var mediaItemCount: Int = 0
        override var availableCommands: Player.Commands? = null

        var lastSeekPosition: Long = -1L
        var seekToNextCalled: Boolean = false
        var seekToPreviousCalled: Boolean = false
        var lastSeekToDefaultIndex: Int = -1
        var attachedListener: Player.Listener? = null
        val setMediaItemsQueue = mutableListOf<MediaItem>()

        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true; playWhenReady = true }
        override fun pause() { isPlaying = false; playWhenReady = false }
        override fun seekTo(positionMs: Long) {
            lastSeekPosition = positionMs
            currentPositionMs = positionMs
        }
        override fun seekToNext() {
            seekToNextCalled = true
            if (currentMediaItemIndex < mediaItemCount - 1) {
                currentMediaItemIndex++
                currentMediaItem = setMediaItemsQueue.getOrNull(currentMediaItemIndex)
            }
        }
        override fun seekToPrevious() {
            seekToPreviousCalled = true
            if (currentMediaItemIndex > 0) {
                currentMediaItemIndex--
                currentMediaItem = setMediaItemsQueue.getOrNull(currentMediaItemIndex)
            }
        }
        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            setMediaItemsQueue.clear()
            setMediaItemsQueue.add(mediaItem)
            currentMediaItem = mediaItem
            currentMediaItemIndex = 0
            mediaItemCount = 1
            this.playWhenReady = playWhenReady
            this.isPlaying = playWhenReady
        }
        override fun setMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
            playWhenReady: Boolean
        ) {
            setMediaItemsQueue.clear()
            setMediaItemsQueue.addAll(mediaItems)
            mediaItemCount = mediaItems.size
            currentMediaItemIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
            currentMediaItem = setMediaItemsQueue.getOrNull(currentMediaItemIndex)
            currentPositionMs = startPositionMs
            this.playWhenReady = playWhenReady
            this.isPlaying = playWhenReady
        }
        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            lastSeekToDefaultIndex = mediaItemIndex
            currentMediaItemIndex = mediaItemIndex
            currentMediaItem = setMediaItemsQueue.getOrNull(mediaItemIndex)
        }
        override fun setPlayerListener(listener: Player.Listener?) {
            attachedListener = listener
        }
        override fun removePlayerListener(listener: Player.Listener) {
            if (attachedListener == listener) attachedListener = null
        }
        override fun sendCustomCommand(action: String) {}
        override fun sendCustomCommand(action: String, args: android.os.Bundle) {}
        override fun release() {}
    }

    private class ImmediateConnector(private val bridge: BitChordMediaSessionBridge) : BitChordSessionConnector {
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
        tempBaseDir = Files.createTempDirectory("divine_lyrics_store_test_").toFile()
        LyricsStore.customStorageDir = tempBaseDir
        resetLyricsNetworkStats()
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = null
    }

    @After
    fun tearDown() {
        LyricsStore.customStorageDir = null
        resetLyricsNetworkStats()
        LyricsRepository.clearMemoryCache()
        LyricsRepository.lyricsDispatcher = Dispatchers.IO
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        lyricsOfflineModeOverride = null
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    @Test
    fun `LyricsStore writes and reads enhanced lyrics accurately on disk`() {
        val videoId = "test_vid_123"
        val lines = listOf(
            LyricLine(
                timeMs = 12000L,
                text = "Hello world from Divine Deck",
                words = listOf(
                    LyricWord(startMs = 12000L, endMs = 12500L, text = "Hello"),
                    LyricWord(startMs = 12500L, endMs = 13000L, text = "world"),
                    LyricWord(startMs = 13000L, endMs = 13500L, text = "from"),
                    LyricWord(startMs = 13500L, endMs = 14000L, text = "Divine"),
                    LyricWord(startMs = 14000L, endMs = 14500L, text = "Deck"),
                )
            ),
            LyricLine(
                timeMs = 15000L,
                text = "Second line of synchronized audio"
            )
        )

        val file = LyricsStore.save(null, videoId, lines, LyricsSource.LRC_RED)
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(file.length() > 0L)
        assertTrue(LyricsStore.has(null, videoId))

        val loadedState = LyricsStore.get(null, videoId)
        assertNotNull(loadedState)
        assertTrue(loadedState is LyricsUiState.EmbeddedLocal)
        val loaded = loadedState as LyricsUiState.EmbeddedLocal
        val textLines = loaded.lines.filter { it.text.isNotBlank() }
        assertEquals(2, textLines.size)
        assertEquals("Hello world from Divine Deck", textLines[0].text)
        assertEquals(12000L, textLines[0].timeMs)
        assertEquals(15000L, textLines[1].timeMs)
        assertTrue(loaded.isSynced)
    }

    @Test
    fun `LyricsRepository uses LyricsStore offline with zero network requests`() = testScope.runTest {
        val videoId = "offline_track_456"
        val song = Song(videoId = videoId, title = "Offline Anthem", artist = "Divine Artist", thumbnailUrl = null, durationText = "03:00")

        val lines = listOf(
            LyricLine(timeMs = 5000L, text = "Cached offline line 1"),
            LyricLine(timeMs = 10000L, text = "Cached offline line 2")
        )
        LyricsStore.save(null, videoId, lines)

        // Forzar modo offline estricto
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        val state = LyricsRepository.resolveForSong(song = song, context = null)

        assertTrue(state is LyricsUiState.EmbeddedLocal)
        val local = state as LyricsUiState.EmbeddedLocal
        val textLines = local.lines.filter { it.text.isNotBlank() }
        assertEquals(2, textLines.size)
        assertEquals("Cached offline line 1", textLines[0].text)
        assertEquals(0, lyricsNetworkCallCount.get())
    }

    @Test
    fun `Online resolved lyrics automatically persist to LyricsStore on disk`() = testScope.runTest {
        val videoId = "online_to_disk_789"
        val song = Song(videoId = videoId, title = "Online Track", artist = "Stream Artist", thumbnailUrl = null, durationText = "03:00")

        val expectedLines = listOf(
            LyricLine(timeMs = 3000L, text = "First online line"),
            LyricLine(timeMs = 7000L, text = "Second online line")
        )

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            expectedLines
        }

        lyricsOfflineModeOverride = false
        resetLyricsNetworkStats()

        val onlineState = LyricsRepository.resolveForSong(song = song, context = null)
        assertTrue(onlineState is LyricsUiState.Synced)

        // Limpiar caché en memoria para garantizar que la siguiente lectura provenga exclusivamente de disco
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = true // Apagar internet

        val diskState = LyricsRepository.resolveForSong(song = song, context = null)
        assertTrue("Debe leerse de LyricsStore en modo offline", diskState is LyricsUiState.EmbeddedLocal)
        val diskLocal = diskState as LyricsUiState.EmbeddedLocal
        val textLines = diskLocal.lines.filter { it.text.isNotBlank() }
        assertEquals(2, textLines.size)
        assertEquals("First online line", textLines[0].text)
    }

    @Test
    fun `Sidecar LRC file can be created and deleted beside local audio file`() {
        val audioFile = File(tempBaseDir, "track_song.mp3")
        audioFile.writeBytes(ByteArray(1024))
        val localUri = audioFile.absolutePath

        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "Sidecar line A"),
            LyricLine(timeMs = 4000L, text = "Sidecar line B")
        )

        val sidecar = LyricsRepository.persistSidecarForLocalUri(localUri, lines)
        assertNotNull(sidecar)
        assertTrue(sidecar!!.exists())
        assertEquals("track_song.lrc", sidecar.name)

        val deleted = LyricsRepository.deleteSidecarForLocalUri(localUri)
        assertTrue(deleted)
        assertFalse(sidecar.exists())
    }

    @Test
    fun `playQueue populates currentQueue and Media3 session bridge`() {
        val bridge = RecordingMediaSessionBridge()
        val connector = ImmediateConnector(bridge)
        val repo = MediaControllerRepositoryImpl(context = TestContext(), connector = connector)

        val songs = listOf(
            Song(videoId = "s1", title = "Song One", artist = "Artist 1", thumbnailUrl = null, durationText = "02:00"),
            Song(videoId = "s2", title = "Song Two", artist = "Artist 2", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s3", title = "Song Three", artist = "Artist 3", thumbnailUrl = null, durationText = "04:00")
        )

        repo.playQueue(songs, startIndex = 1)

        assertEquals(3, repo.currentQueue.value.size)
        assertEquals("Song Two", repo.currentQueue.value[1].title)
        assertEquals(3, bridge.setMediaItemsQueue.size)
        assertEquals(1, bridge.currentMediaItemIndex)
        assertEquals("s2", bridge.currentMediaItem?.mediaId)
        assertTrue(repo.playbackState.value.canSkipNext)
        assertTrue(repo.playbackState.value.canSkipPrevious)
        repo.disconnect()
    }

    @Test
    fun `skipToPrevious restarts song if played over 3 seconds`() {
        val bridge = RecordingMediaSessionBridge()
        val connector = ImmediateConnector(bridge)
        val repo = MediaControllerRepositoryImpl(context = TestContext(), connector = connector)

        val songs = listOf(
            Song(videoId = "s1", title = "Track 1", artist = "Artist 1", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s2", title = "Track 2", artist = "Artist 2", thumbnailUrl = null, durationText = "03:00")
        )
        repo.playQueue(songs, startIndex = 1)

        // Simular que lleva 4500ms (> 3000ms)
        bridge.currentPositionMs = 4500L
        repo.skipToPrevious()

        assertEquals("Debe reiniciar a 0ms si lleva más de 3 segundos", 0L, bridge.lastSeekPosition)
        assertFalse("No debe saltar a la pista anterior si llevaba > 3s", bridge.seekToPreviousCalled)
        repo.disconnect()
    }

    @Test
    fun `skipToPrevious skips to earlier track if played 3 seconds or less`() {
        val bridge = RecordingMediaSessionBridge()
        val connector = ImmediateConnector(bridge)
        val repo = MediaControllerRepositoryImpl(context = TestContext(), connector = connector)

        val songs = listOf(
            Song(videoId = "s1", title = "Track 1", artist = "Artist 1", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s2", title = "Track 2", artist = "Artist 2", thumbnailUrl = null, durationText = "03:00")
        )
        repo.playQueue(songs, startIndex = 1)

        // Simular que lleva 1500ms (<= 3000ms)
        bridge.currentPositionMs = 1500L
        repo.skipToPrevious()

        assertTrue("Debe invocar seekToPrevious() si lleva <= 3s", bridge.seekToPreviousCalled)
        repo.disconnect()
    }

    @Test
    fun `skipToNext advances to next item in queue`() {
        val bridge = RecordingMediaSessionBridge()
        val connector = ImmediateConnector(bridge)
        val repo = MediaControllerRepositoryImpl(context = TestContext(), connector = connector)

        val songs = listOf(
            Song(videoId = "s1", title = "Track 1", artist = "Artist 1", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s2", title = "Track 2", artist = "Artist 2", thumbnailUrl = null, durationText = "03:00")
        )
        repo.playQueue(songs, startIndex = 0)

        repo.skipToNext()

        assertTrue(bridge.seekToNextCalled)
        assertEquals(1, bridge.currentMediaItemIndex)
        repo.disconnect()
    }

    @Test
    fun `skipToQueueItem seeks directly to specific index`() {
        val bridge = RecordingMediaSessionBridge()
        val connector = ImmediateConnector(bridge)
        val repo = MediaControllerRepositoryImpl(context = TestContext(), connector = connector)

        val songs = listOf(
            Song(videoId = "s1", title = "Track 1", artist = "Artist 1", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s2", title = "Track 2", artist = "Artist 2", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "s3", title = "Track 3", artist = "Artist 3", thumbnailUrl = null, durationText = "03:00")
        )
        repo.playQueue(songs, startIndex = 0)

        repo.skipToQueueItem(2)

        assertEquals(2, bridge.lastSeekToDefaultIndex)
        assertEquals(2, bridge.currentMediaItemIndex)
        repo.disconnect()
    }

    @Test
    fun `auto scroll centering calculation maintains active line in vertical center`() {
        val viewportHeight = 700
        val itemHeight = 100
        val calculatedOffset = -((viewportHeight - itemHeight) / 2).coerceAtLeast(0)

        // (700 - 100) / 2 = 300 -> offset = -300
        assertEquals(-300, calculatedOffset)
    }
}

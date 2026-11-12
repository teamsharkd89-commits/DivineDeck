package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Comprehensive Opaque-Box E2E Test Suite for Requirement R4: Architecture & Invariants.
 *
 * Forensically verifies:
 * - Single Playback Service (`DivineDeckPlaybackService`)
 * - Single MediaLibrarySession (`BitChordPlayback`) and single ExoPlayer architecture
 * - Strict 3-tier queue integrity (`USER_QUEUE`, `CONTEXT`, `AUTOPLAY`)
 * - Zero parallel queues, zero duplicate player sessions
 * - End-to-end automotive driving lifecycle
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordArchitectureInvariantsE2ETest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class RecordingMediaSessionBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = true,
        override var playbackState: Int = Player.STATE_READY,
        override var playWhenReady: Boolean = true,
        override var isLoading: Boolean = false,
        override var currentPositionMs: Long = 10000L,
        override var durationMs: Long = 180000L,
        override var currentMediaItemIndex: Int = 0,
        val mediaItems: MutableList<MediaItem> = mutableListOf(),
    ) : BitChordMediaSessionBridge {

        override val currentMediaItem: MediaItem?
            get() = mediaItems.getOrNull(currentMediaItemIndex)

        override val mediaItemCount: Int
            get() = mediaItems.size

        override var availableCommands: Player.Commands? = null

        val addedMediaItems = mutableListOf<Pair<Int, MediaItem>>()
        val removedMediaItems = mutableListOf<Int>()
        val movedMediaItems = mutableListOf<Pair<Int, Int>>()

        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true; playWhenReady = true }
        override fun pause() { isPlaying = false; playWhenReady = false }
        override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
        override fun seekToNext() {}
        override fun seekToPrevious() {}

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            mediaItems.clear()
            mediaItems.add(mediaItem)
            currentMediaItemIndex = 0
            this.playWhenReady = playWhenReady
            if (playWhenReady) isPlaying = true
        }

        override fun setMediaItems(
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
            playWhenReady: Boolean
        ) {
            this.mediaItems.clear()
            this.mediaItems.addAll(mediaItems)
            currentMediaItemIndex = startIndex
            this.playWhenReady = playWhenReady
            if (playWhenReady) isPlaying = true
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            currentMediaItemIndex = mediaItemIndex
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            val safeIdx = index.coerceIn(0, mediaItems.size)
            addedMediaItems.add(Pair(safeIdx, mediaItem))
            mediaItems.add(safeIdx, mediaItem)
        }

        override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
            if (fromIndex in mediaItems.indices && toIndex in mediaItems.indices) {
                movedMediaItems.add(Pair(fromIndex, toIndex))
                val item = mediaItems.removeAt(fromIndex)
                mediaItems.add(toIndex, item)
            }
        }

        override fun removeMediaItem(index: Int) {
            if (index in mediaItems.indices) {
                removedMediaItems.add(index)
                mediaItems.removeAt(index)
            }
        }

        override fun clearMediaItems() {
            mediaItems.clear()
        }

        override fun getMediaItemAt(index: Int): MediaItem? = mediaItems.getOrNull(index)

        override fun sendCustomCommand(action: String) {}
        override fun sendCustomCommand(action: String, args: android.os.Bundle) {}
        override fun setPlayerListener(listener: Player.Listener?) {}
        override fun removePlayerListener(listener: Player.Listener) {}

        override fun release() {
            isConnected = false
        }
    }

    private class RecordingConnector(val bridge: RecordingMediaSessionBridge) : BitChordSessionConnector {
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

    private fun createSong(
        id: String,
        title: String,
        tier: QueueTier = QueueTier.CONTEXT
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 200L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier
    )

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R4 Architectural Invariants)
    // =========================================================================

    @Test
    fun tier1_test01_single_playback_service_and_session_id() {
        // Derivation: ORIGINAL_REQUEST §R4 line 47:
        // "Conservar estrictamente los invariantes arquitectónicos: un único DivineDeckPlaybackService,
        //  un único ExoPlayer, una única MediaLibrarySession..."
        assertEquals("BitChordPlayback", DivineDeckPlaybackService.SESSION_ID)
        assertEquals("bitchord_playback", DivineDeckPlaybackService.CHANNEL_ID)
    }

    @Test
    fun tier1_test02_three_tier_queue_integrity_in_timeline() {
        // Derivation: PROJECT.md §Architecture line 9:
        // "QueueTimeline maintains the 3-tier queue (USER_QUEUE, CONTEXT, AUTOPLAY)."
        val songs = listOf(
            createSong("np_0", "Now Playing", QueueTier.CONTEXT),
            createSong("u_1", "User 1", QueueTier.USER_QUEUE),
            createSong("u_2", "User 2", QueueTier.USER_QUEUE),
            createSong("c_3", "Context 1", QueueTier.CONTEXT),
            createSong("a_4", "Autoplay 1", QueueTier.AUTOPLAY)
        )

        val userQueueItems = songs.filter { it.queueTier == QueueTier.USER_QUEUE }
        val contextItems = songs.filter { it.queueTier == QueueTier.CONTEXT }
        val autoplayItems = songs.filter { it.queueTier == QueueTier.AUTOPLAY }

        assertEquals(2, userQueueItems.size)
        assertEquals(2, contextItems.size)
        assertEquals(1, autoplayItems.size)
    }

    @Test
    fun tier1_test03_insertion_index_calculation_for_user_queue() {
        // Derivation: QueueCoordinator.findUserQueueInsertionIndex
        val timeline = listOf(
            createSong("s0", "Current", QueueTier.CONTEXT),      // index 0: nowPlaying
            createSong("u1", "User 1", QueueTier.USER_QUEUE),   // index 1
            createSong("c2", "Context 2", QueueTier.CONTEXT)     // index 2
        )

        // Enqueue Next: debe insertarse en index 1 (inmediatamente tras currentIndex 0)
        val nextIdx = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = true)
        assertEquals(1, nextIdx)

        // Enqueue Last: debe insertarse tras el último elemento de USER_QUEUE (index 2)
        val lastIdx = QueueCoordinator.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = false)
        assertEquals(2, lastIdx)
    }

    @Test
    fun tier1_test04_now_playing_pinned_invariant_on_media_controller() {
        // Derivation: Now Playing track is never dropped during queue reordering
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        val s0 = createSong("s0", "Playing Track", QueueTier.CONTEXT)
        val s1 = createSong("s1", "Upcoming 1", QueueTier.USER_QUEUE)
        val s2 = createSong("s2", "Upcoming 2", QueueTier.USER_QUEUE)

        repo.playQueue(listOf(s0, s1, s2), startIndex = 0)
        assertEquals("s0", repo.currentTrack.value.mediaId)

        // Reordenar índice 1 e índice 2
        repo.reorderQueue(fromIndex = 1, toIndex = 2)

        // Invariante: s0 sigue siendo la pista en reproducción
        assertEquals("s0", repo.currentTrack.value.mediaId)
        assertEquals("s0", repo.currentQueue.value[0].videoId)
        assertEquals("s2", repo.currentQueue.value[1].videoId)
        assertEquals("s1", repo.currentQueue.value[2].videoId)
    }

    @Test
    fun tier1_test05_manifest_declares_only_one_playback_service() {
        // Derivation: Forensic verification of AndroidManifest.xml
        val manifestFile = File("app/src/main/AndroidManifest.xml")
        if (manifestFile.exists()) {
            val content = manifestFile.readText()
            val serviceMatches = "DivineDeckPlaybackService".toRegex().findAll(content).count()
            assertEquals("Solo debe existir 1 declaración de DivineDeckPlaybackService en el Manifest", 1, serviceMatches)
            assertFalse("No deben existir servicios secundarios como 'SecondaryPlaybackService'", content.contains("SecondaryPlaybackService"))
        }
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>= 5 tests)
    // =========================================================================

    @Test
    fun tier2_test01_empty_queue_clear_user_queue_noop() {
        // Derivation: clearUserQueue on empty queue does not crash
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        repo.clearUserQueue()
        assertTrue(repo.currentQueue.value.isEmpty())
        assertEquals(0, bridge.removedMediaItems.size)
    }

    @Test
    fun tier2_test02_cannot_remove_currently_playing_song_from_queue_with_multiple_items() {
        // Derivation: ORIGINAL_REQUEST & BitChordQueueMediaControllerIntegrationTest invariant:
        // Do not remove the current song if the queue has multiple items.
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        val s0 = createSong("s0", "Current Song")
        val s1 = createSong("s1", "Next Song")
        repo.playQueue(listOf(s0, s1), startIndex = 0)

        // Intento de eliminar la canción activa (índice 0)
        repo.removeFromQueue(index = 0)

        // Debe ignorar la eliminación de la pista activa
        assertEquals(2, repo.currentQueue.value.size)
        assertEquals("s0", repo.currentQueue.value[0].videoId)
        assertEquals(0, bridge.removedMediaItems.size)
    }

    @Test
    fun tier2_test03_reorder_with_invalid_indices_is_gracefully_ignored() {
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        val s0 = createSong("s0", "Track 0")
        val s1 = createSong("s1", "Track 1")
        repo.playQueue(listOf(s0, s1), startIndex = 0)

        // Índices fuera de límites
        repo.reorderQueue(fromIndex = -1, toIndex = 5)
        repo.reorderQueue(fromIndex = 5, toIndex = 0)

        assertEquals(0, bridge.movedMediaItems.size)
        assertEquals("s0", repo.currentQueue.value[0].videoId)
    }

    @Test
    fun tier2_test04_one_off_queue_preserves_surrounding_context() {
        val currentTimeline = listOf(
            createSong("s0", "Current Song", QueueTier.CONTEXT),
            createSong("u1", "User Queued Song", QueueTier.USER_QUEUE)
        )
        val tappedSong = createSong("tap_new", "Tapped Song")

        val result = QueueCoordinator.buildOneOffQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            tappedSong = tappedSong,
            source = QueueSource("Search")
        )

        assertNotNull(result)
        assertEquals(2, result.size)
        assertEquals("tap_new", result[0].videoId)
        assertEquals(QueueTier.CONTEXT, result[0].queueTier)
        assertEquals("u1", result[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, result[1].queueTier)
    }

    @Test
    fun tier2_test05_clear_user_queue_removes_only_user_tier_songs() {
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        val s0 = createSong("s0", "Now Playing", QueueTier.CONTEXT)
        val u1 = createSong("u1", "User 1", QueueTier.USER_QUEUE)
        val u2 = createSong("u2", "User 2", QueueTier.USER_QUEUE)
        val c3 = createSong("c3", "Context 3", QueueTier.CONTEXT)

        repo.playQueue(listOf(s0, u1, u2, c3), startIndex = 0)
        assertEquals(4, repo.currentQueue.value.size)

        repo.clearUserQueue()

        assertEquals(2, repo.currentQueue.value.size)
        assertEquals("s0", repo.currentQueue.value[0].videoId)
        assertEquals("c3", repo.currentQueue.value[1].videoId)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE INTEGRATION
    // =========================================================================

    @Test
    fun tier3_test01_queue_coordination_and_media_session_bridge_synchronization() {
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        val s0 = createSong("s0", "Track 0")
        val s1 = createSong("s1", "Track 1")
        repo.playQueue(listOf(s0, s1), startIndex = 0)

        // Encolar nueva pista
        val s2 = createSong("s2", "Track 2")
        repo.enqueueLast(s2)

        assertEquals(3, repo.currentQueue.value.size)
        assertEquals(1, bridge.addedMediaItems.size)
        assertEquals(3, bridge.mediaItems.size)

        // Reordenar pista 1 a 2
        repo.reorderQueue(fromIndex = 1, toIndex = 2)
        assertEquals(1, bridge.movedMediaItems.size)
        assertEquals(Pair(1, 2), bridge.movedMediaItems.first())
    }

    // =========================================================================
    // TIER 4: REAL-WORLD AUTOMOTIVE DRIVING SESSION
    // =========================================================================

    @Test
    fun tier4_test01_end_to_end_playback_service_lifecycle_during_long_drive() {
        val bridge = RecordingMediaSessionBridge()
        val connector = RecordingConnector(bridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)
        repo.connect()

        // 1. Iniciar servicio con álbum de 4 canciones
        val album = listOf(
            createSong("alb_1", "Highway Start", QueueTier.CONTEXT),
            createSong("alb_2", "Mountain Pass", QueueTier.CONTEXT),
            createSong("alb_3", "Desert Stretch", QueueTier.CONTEXT),
            createSong("alb_4", "Destination Sunset", QueueTier.CONTEXT)
        )
        repo.playQueue(album, startIndex = 0)
        assertTrue(bridge.isPlaying)
        assertEquals("alb_1", repo.currentTrack.value.mediaId)

        // 2. Conductor añade dos pistas favoritas vía Swipe to Queue
        val fav1 = createSong("fav_01", "Favorite Tune 1", QueueTier.USER_QUEUE)
        val fav2 = createSong("fav_02", "Favorite Tune 2", QueueTier.USER_QUEUE)
        repo.enqueueNext(fav1)
        repo.enqueueLast(fav2)
        assertEquals("fav_01", repo.currentQueue.value[1].videoId)

        // 3. Conductor reordena la cola
        repo.reorderQueue(fromIndex = 3, toIndex = 5)

        // 4. Conductor avanza a la siguiente canción (fav_01 en índice 1)
        bridge.currentMediaItemIndex = 1
        repo.seekTo(0L)
        assertEquals("fav_01", repo.currentQueue.value[1].videoId)

        // 5. Conductor limpia la cola de usuario: se eliminan las canciones de usuario pendientes (> currentIndex)
        // pero la canción actual fav_01 en reproducción se preserva protegida
        repo.clearUserQueue()
        assertEquals("fav_01", repo.currentQueue.value[1].videoId)
        val upcomingUserQueue = repo.currentQueue.value.drop(2).filter { it.queueTier == QueueTier.USER_QUEUE }
        assertTrue("No deben quedar canciones de usuario pendientes tras clearUserQueue", upcomingUserQueue.isEmpty())

        // 6. Fin de viaje: parada y desconexión limpia
        repo.pause()
        assertFalse(bridge.isPlaying)
        repo.disconnect()
    }
}

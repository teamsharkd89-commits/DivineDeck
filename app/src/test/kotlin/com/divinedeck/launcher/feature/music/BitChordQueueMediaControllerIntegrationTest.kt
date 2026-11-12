package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas de integración para el Repositorio MediaController y el Sistema de Cola (Fase 06.9 - R4):
 * 1. Encolar canciones (enqueueNext, enqueueLast) sin interrumpir ni reiniciar la reproducción activa.
 * 2. Manipulación interactiva de cola (reorderQueue, removeFromQueue, clearUserQueue).
 * 3. Invariante de seguridad: no eliminar la canción actual si la cola tiene múltiples elementos.
 * 4. Reactividad de StateFlows frente a operaciones sobre la cola autoritativa.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordQueueMediaControllerIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class QueueRecordingMediaSessionBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = true,
        override var playbackState: Int = Player.STATE_READY,
        override var playWhenReady: Boolean = true,
        override var isLoading: Boolean = false,
        override var currentPositionMs: Long = 15000L,
        override var durationMs: Long = 200000L,
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
        var setMediaItemCallCount = 0
        var setMediaItemsCallCount = 0
        var lastSeekToDefaultPositionIndex: Int? = null

        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true; playWhenReady = true }
        override fun pause() { isPlaying = false; playWhenReady = false }
        override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
        override fun seekToNext() {}
        override fun seekToPrevious() {}

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            setMediaItemCallCount++
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
            setMediaItemsCallCount++
            this.mediaItems.clear()
            this.mediaItems.addAll(mediaItems)
            currentMediaItemIndex = startIndex
            this.playWhenReady = playWhenReady
            if (playWhenReady) isPlaying = true
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            lastSeekToDefaultPositionIndex = mediaItemIndex
            currentMediaItemIndex = mediaItemIndex
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            val safeIdx = index.coerceIn(0, mediaItems.size)
            mediaItems.add(safeIdx, mediaItem)
            addedMediaItems.add(safeIdx to mediaItem)
        }

        override fun removeMediaItem(index: Int) {
            if (index in mediaItems.indices) {
                mediaItems.removeAt(index)
                removedMediaItems.add(index)
            }
        }

        override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
            if (fromIndex in mediaItems.indices && toIndex in mediaItems.indices) {
                val item = mediaItems.removeAt(fromIndex)
                mediaItems.add(toIndex, item)
                movedMediaItems.add(fromIndex to toIndex)
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
        override fun release() { isConnected = false }
    }

    private class RecordingConnector(val bridge: QueueRecordingMediaSessionBridge) : BitChordSessionConnector {
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
        tier: QueueTier = QueueTier.CONTEXT,
    ): Song = Song(
        videoId = id,
        title = title,
        artist = "Artist $id",
        durationSeconds = 210L,
        thumbnailUrl = "https://example.com/$id.jpg",
        queueTier = tier,
    )

    private fun buildSampleMediaItem(id: String, title: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("Sample Artist")
                    .build()
            )
            .build()
    }

    // =========================================================================
    // 1. Encolar sin Interrumpir ni Reiniciar la Reproducción (R4 Invariant)
    // =========================================================================

    @Test
    fun enqueueNext_insertsImmediatelyAfterCurrent_withoutStoppingOrRestartingAudio() {
        val initialItem = buildSampleMediaItem("song0", "Playing Track")
        val fakeBridge = QueueRecordingMediaSessionBridge(
            isPlaying = true,
            playWhenReady = true,
            currentMediaItemIndex = 0,
            mediaItems = mutableListOf(initialItem),
        )
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        // Inicializamos con una cola en reproducción
        val currentSong = createSong("song0", "Playing Track", QueueTier.CONTEXT)
        val nextSong = createSong("song1", "Next Queued Song", QueueTier.CONTEXT)
        repo.playQueue(listOf(currentSong), startIndex = 0)

        assertEquals(1, fakeBridge.setMediaItemCallCount + fakeBridge.setMediaItemsCallCount)
        assertTrue("La reproducción debe estar activa antes de encolar", repo.playbackState.value.isPlaying)

        // Encolar siguiente
        repo.enqueueNext(nextSong)

        // Verificación crítica de invariante:
        // 1. setMediaItem o setMediaItems NO deben haberse vuelto a invocar (0 reinicios de stream)
        assertEquals("Encolar NO debe reiniciar el reproductor", 1, fakeBridge.setMediaItemCallCount + fakeBridge.setMediaItemsCallCount)
        // 2. addMediaItem se invocó exactamente en el índice 1
        assertEquals(1, fakeBridge.addedMediaItems.size)
        assertEquals(1, fakeBridge.addedMediaItems[0].first)
        assertEquals("song1", fakeBridge.addedMediaItems[0].second.mediaId)
        // 3. La reproducción continúa activa sin pausarse
        assertTrue("La reproducción debe permanecer activa al encolar", repo.playbackState.value.isPlaying)
        assertTrue("playWhenReady debe permanecer true", repo.playbackState.value.playWhenReady)
        // 4. StateFlow currentQueue se actualizó inmediatamente
        assertEquals(2, repo.currentQueue.value.size)
        assertEquals("song0", repo.currentQueue.value[0].videoId)
        assertEquals("song1", repo.currentQueue.value[1].videoId)
        assertEquals(QueueTier.USER_QUEUE, repo.currentQueue.value[1].queueTier)
    }

    @Test
    fun enqueueLast_insertsAtEndOfUserQueueBlock_preservingPlaybackContinuity() {
        val fakeBridge = QueueRecordingMediaSessionBridge(
            isPlaying = true,
            playWhenReady = true,
            currentMediaItemIndex = 0,
        )
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val s0 = createSong("s0", "Current Song", QueueTier.CONTEXT)
        val u1 = createSong("u1", "Existing User 1", QueueTier.USER_QUEUE)
        val c2 = createSong("c2", "Context Song 2", QueueTier.CONTEXT)

        repo.playQueue(listOf(s0, u1, c2), startIndex = 0)
        val callsBefore = fakeBridge.setMediaItemCallCount + fakeBridge.setMediaItemsCallCount

        val uLast = createSong("u_last", "New User Queue Item")
        repo.enqueueLast(uLast)

        // Verificaciones:
        assertEquals("No debe haber reinicio de reproducción", callsBefore, fakeBridge.setMediaItemCallCount + fakeBridge.setMediaItemsCallCount)
        assertTrue("Reproducción activa preservada", repo.playbackState.value.isPlaying)
        // Inserción en índice 2 (después de u1 y antes de c2)
        assertEquals(1, fakeBridge.addedMediaItems.size)
        assertEquals(2, fakeBridge.addedMediaItems[0].first)
        assertEquals("u_last", fakeBridge.addedMediaItems[0].second.mediaId)

        // Validar lista en repo
        val q = repo.currentQueue.value
        assertEquals(4, q.size)
        assertEquals("s0", q[0].videoId)
        assertEquals("u1", q[1].videoId)
        assertEquals("u_last", q[2].videoId)
        assertEquals("c2", q[3].videoId)
    }

    // =========================================================================
    // 2. Reordenamiento de Cola (reorderQueue)
    // =========================================================================

    @Test
    fun reorderQueue_movesMediaItemOnBridge_andUpdatesCurrentQueueStateFlow() {
        val fakeBridge = QueueRecordingMediaSessionBridge()
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(
            createSong("s0", "Song 0"),
            createSong("s1", "Song 1"),
            createSong("s2", "Song 2"),
        )
        repo.playQueue(songs, startIndex = 0)

        // Reordenar: mover canción 1 a posición 2
        repo.reorderQueue(fromIndex = 1, toIndex = 2)

        assertEquals(1, fakeBridge.movedMediaItems.size)
        assertEquals(1 to 2, fakeBridge.movedMediaItems[0])

        val updated = repo.currentQueue.value
        assertEquals("s0", updated[0].videoId)
        assertEquals("s2", updated[1].videoId)
        assertEquals("s1", updated[2].videoId)
    }

    @Test
    fun reorderQueue_ignoresOutOfBoundsIndicesSafely() {
        val fakeBridge = QueueRecordingMediaSessionBridge()
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(createSong("s0", "Song 0"), createSong("s1", "Song 1"))
        repo.playQueue(songs, startIndex = 0)

        repo.reorderQueue(fromIndex = -1, toIndex = 5)
        assertTrue("No debe despachar movimientos fuera de rango", fakeBridge.movedMediaItems.isEmpty())
    }

    // =========================================================================
    // 3. Eliminación Individual y Vaciado de Cola (removeFromQueue & clearUserQueue)
    // =========================================================================

    @Test
    fun removeFromQueue_removesTargetSong_andUpdatesBridge() {
        val fakeBridge = QueueRecordingMediaSessionBridge()
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(
            createSong("s0", "Current"),
            createSong("s1", "To Remove"),
            createSong("s2", "Keep"),
        )
        repo.playQueue(songs, startIndex = 0)

        repo.removeFromQueue(index = 1)

        assertEquals(1, fakeBridge.removedMediaItems.size)
        assertEquals(1, fakeBridge.removedMediaItems[0])

        val q = repo.currentQueue.value
        assertEquals(2, q.size)
        assertEquals("s0", q[0].videoId)
        assertEquals("s2", q[1].videoId)
    }

    @Test
    fun removeFromQueue_preventsRemovingCurrentlyPlayingSongWhenQueueHasMultipleItems() {
        val fakeBridge = QueueRecordingMediaSessionBridge(currentMediaItemIndex = 0)
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(createSong("s0", "Current"), createSong("s1", "Next"))
        repo.playQueue(songs, startIndex = 0)

        // Intentar remover el índice 0 que se está reproduciendo
        repo.removeFromQueue(index = 0)

        assertTrue("No debe remover la canción en reproducción activa", fakeBridge.removedMediaItems.isEmpty())
        assertEquals(2, repo.currentQueue.value.size)
    }

    @Test
    fun clearUserQueue_removesAllUpcomingUserSongs_leavingContextIntact() {
        val fakeBridge = QueueRecordingMediaSessionBridge(currentMediaItemIndex = 0)
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(
            createSong("curr0", "Current Track", QueueTier.CONTEXT),
            createSong("u1", "User 1", QueueTier.USER_QUEUE),
            createSong("u2", "User 2", QueueTier.USER_QUEUE),
            createSong("ctx3", "Context Album", QueueTier.CONTEXT),
        )
        repo.playQueue(songs, startIndex = 0)

        repo.clearUserQueue()

        // Deben haberse removido las pistas de usuario (índices 2 y 1 en orden inverso)
        assertEquals(2, fakeBridge.removedMediaItems.size)
        assertEquals(2, fakeBridge.removedMediaItems[0])
        assertEquals(1, fakeBridge.removedMediaItems[1])

        val remaining = repo.currentQueue.value
        assertEquals(2, remaining.size)
        assertEquals("curr0", remaining[0].videoId)
        assertEquals("ctx3", remaining[1].videoId)
        assertEquals(QueueTier.CONTEXT, remaining[1].queueTier)
    }

    // =========================================================================
    // 4. Salto a Elemento de Cola (skipToQueueItem)
    // =========================================================================

    @Test
    fun skipToQueueItem_delegatesToBridgeSeekToDefaultPosition() {
        val fakeBridge = QueueRecordingMediaSessionBridge()
        val connector = RecordingConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope,
        )

        val songs = listOf(createSong("s0", "Track 0"), createSong("s1", "Track 1"), createSong("s2", "Track 2"))
        repo.playQueue(songs, startIndex = 0)

        repo.skipToQueueItem(index = 2)

        assertEquals(2, fakeBridge.lastSeekToDefaultPositionIndex)
    }
}

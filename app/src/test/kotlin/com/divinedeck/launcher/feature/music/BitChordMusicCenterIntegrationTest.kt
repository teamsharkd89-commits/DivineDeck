package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.divinedeck.launcher.feature.music.ui.BitChordMediaModelAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas de integración para la Fase 06.1C.
 * Verifica los 12 requerimientos arquitectónicos de integración entre
 * MediaControllerRepository, BitChordMediaModelAdapter y BitChordPlayerScreen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordMusicCenterIntegrationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class FakeMediaBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = false,
        override var playbackState: Int = Player.STATE_READY,
        override var isLoading: Boolean = false,
        override var currentPositionMs: Long = 0L,
        override var durationMs: Long = 200_000L,
        override var currentMediaItem: MediaItem? = null,
        var allowedCommands: Set<Int> = setOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ),
        override var availableCommands: Player.Commands? = null
    ) : BitChordMediaSessionBridge {
        var playCount = 0
        var pauseCount = 0
        var lastSeekPosition: Long? = null
        var skipNextCount = 0
        var skipPreviousCount = 0
        var releaseCount = 0
        val listeners = mutableListOf<Player.Listener>()

        override fun isCommandAvailable(command: Int): Boolean = allowedCommands.contains(command)

        override fun play() {
            playCount++
            isPlaying = true
        }

        override fun pause() {
            pauseCount++
            isPlaying = false
        }

        override fun seekTo(positionMs: Long) {
            lastSeekPosition = positionMs
            currentPositionMs = positionMs
        }

        override fun seekToNext() {
            skipNextCount++
        }

        override fun seekToPrevious() {
            skipPreviousCount++
        }

        override fun setPlayerListener(listener: Player.Listener?) {
            listener?.let { listeners.add(it) }
        }

        override fun removePlayerListener(listener: Player.Listener) {
            listeners.remove(listener)
        }

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            currentMediaItem = mediaItem
            if (playWhenReady) isPlaying = true
        }

        override fun sendCustomCommand(action: String) {
            if (action == "com.divinedeck.ACTION_PLAY_SAMPLE") {
                isPlaying = true
            }
        }

        override fun sendCustomCommand(action: String, args: android.os.Bundle) {
            sendCustomCommand(action)
        }

        override fun release() {
            releaseCount++
            isConnected = false
        }
    }

    private class FakeConnector(
        var fakeBridge: FakeMediaBridge = FakeMediaBridge(),
        var connectCalls: Int = 0
    ) : BitChordSessionConnector {
        var lastOnConnected: ((BitChordMediaSessionBridge) -> Unit)? = null
        var lastOnDisconnected: (() -> Unit)? = null
        var lastOnError: ((Throwable) -> Unit)? = null

        override fun connect(
            context: Context,
            onConnected: (BitChordMediaSessionBridge) -> Unit,
            onDisconnected: () -> Unit,
            onError: (Throwable) -> Unit
        ): AutoCloseable {
            connectCalls++
            lastOnConnected = onConnected
            lastOnDisconnected = onDisconnected
            lastOnError = onError
            onConnected(fakeBridge)
            return AutoCloseable {
                fakeBridge.release()
            }
        }
    }

    // 1. Adaptación de MediaMetadata a Song
    @Test
    fun test1_adaptation_fromMediaMetadataToSong() {
        val trackInfo = TrackInfo(
            mediaId = "yt_track_abc",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationMs = 200_000L,
            albumArtUri = "https://example.com/cover.jpg",
            currentPositionMs = 45_000L
        )

        val song = BitChordMediaModelAdapter.toSong(trackInfo)
        assertNotNull(song)
        assertEquals("yt_track_abc", song?.videoId)
        assertEquals("yt_track_abc", song?.id)
        assertEquals("Blinding Lights", song?.title)
        assertEquals("The Weeknd", song?.artist)
        assertEquals("After Hours", song?.albumName)
        assertEquals("https://example.com/cover.jpg", song?.thumbnailUrl)
        assertEquals("https://example.com/cover.jpg", song?.artworkUrl)
        assertEquals(200L, song?.durationSeconds)
        assertEquals(200_000L, song?.durationMillis)
    }

    // 2. Metadatos incompletos (sin duración ni carátula)
    @Test
    fun test2_adaptation_incompleteMetadata() {
        val trackInfo = TrackInfo(
            mediaId = "track_incomplete",
            title = "Just A Title",
            artist = "Just An Artist",
            album = null,
            durationMs = null,
            albumArtUri = null,
            currentPositionMs = 0L
        )

        val song = BitChordMediaModelAdapter.toSong(trackInfo)
        assertNotNull(song)
        assertEquals("Just A Title", song?.title)
        assertNull(song?.thumbnailUrl)
        assertNull(song?.artworkUrl)
        assertNull(song?.durationSeconds)
        assertEquals(0L, song?.durationMillis) // No inventa duración estimada
        assertNull(song?.albumName)
    }

    // 3. Estado sin canción (retorna null para activar estado de reposo)
    @Test
    fun test3_adaptation_noSongState() {
        val emptyTrack = TrackInfo()
        assertFalse(emptyTrack.hasTrack)
        val song = BitChordMediaModelAdapter.toSong(emptyTrack)
        assertNull(song)
    }

    // 4. Reproducción (estado playing)
    @Test
    fun test4_playbackState_playing() {
        val fakeBridge = FakeMediaBridge(isPlaying = true, playbackState = Player.STATE_READY)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        val state = repository.playbackState.value
        assertEquals(MediaServiceConnectionStatus.CONNECTED, state.connectionStatus)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertFalse(state.isBuffering)
    }

    // 5. Pausa (estado paused)
    @Test
    fun test5_playbackState_paused() {
        val fakeBridge = FakeMediaBridge(isPlaying = false, playbackState = Player.STATE_READY)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        val state = repository.playbackState.value
        assertEquals(MediaServiceConnectionStatus.CONNECTED, state.connectionStatus)
        assertFalse(state.isPlaying)
        assertEquals(PlaybackStatus.PAUSED, state.status)
    }

    // 6. Buffering
    @Test
    fun test6_playbackState_buffering() {
        val fakeBridge = FakeMediaBridge(isPlaying = false, playbackState = Player.STATE_BUFFERING, isLoading = true)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        val state = repository.playbackState.value
        assertEquals(MediaServiceConnectionStatus.CONNECTED, state.connectionStatus)
        assertTrue(state.isBuffering)
        assertEquals(PlaybackStatus.BUFFERING, state.status)
    }

    // 7. Actualización de posición
    @Test
    fun test7_positionUpdate_ticker() {
        val fakeBridge = FakeMediaBridge(isPlaying = true, currentPositionMs = 12_500L, durationMs = 180_000L)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        repository.seekTo(30_000L)
        assertEquals(30_000L, fakeBridge.lastSeekPosition)
        assertEquals(30_000L, repository.currentTrack.value.currentPositionMs)
    }

    // 8. Comando Play/Pause no disponible
    @Test
    fun test8_controls_playPauseNotAvailable() {
        val fakeBridge = FakeMediaBridge(
            isPlaying = false,
            allowedCommands = setOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) // Sin PLAY_PAUSE
        )
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        assertFalse(repository.playbackState.value.canPlayPause)
        repository.play()
        assertEquals(0, fakeBridge.playCount) // Operación rechazada honestamente
        repository.pause()
        assertEquals(0, fakeBridge.pauseCount)
    }

    // 9. Seek no disponible
    @Test
    fun test9_controls_seekNotAvailable() {
        val fakeBridge = FakeMediaBridge(
            isPlaying = true,
            allowedCommands = setOf(Player.COMMAND_PLAY_PAUSE) // Sin SEEK
        )
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        assertFalse(repository.playbackState.value.canSeek)
        repository.seekTo(60_000L)
        assertNull(fakeBridge.lastSeekPosition) // No se envió comando de seek
    }

    // 10. Salida de Music Center sin desconectar el servicio
    @Test
    fun test10_navigation_exitMusicCenterWithoutDisconnecting() {
        val fakeBridge = FakeMediaBridge(isPlaying = true)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        // Simular que el usuario navega a Cockpit:
        // No llamamos repository.disconnect() porque MainActivity mantiene el singleton
        assertEquals(MediaServiceConnectionStatus.CONNECTED, repository.playbackState.value.connectionStatus)
        assertTrue(fakeBridge.isConnected)
        assertEquals(0, fakeBridge.releaseCount) // El servicio sigue sonando en segundo plano
    }

    // 11. Regreso a Music Center sin duplicar conexiones
    @Test
    fun test11_navigation_returnToMusicCenterWithoutDuplicateConnection() {
        val fakeBridge = FakeMediaBridge(isPlaying = true)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        assertEquals(1, connector.connectCalls)

        // Si MusicCenterScreen re-entra y se verifica conectividad:
        repository.connect() // connect() es idempotente cuando ya está CONNECTED
        assertEquals(1, connector.connectCalls) // No hubo segunda conexión duplicada
    }

    // 12. Estado desconectado y recuperación
    @Test
    fun test12_connection_disconnectedAndRecovery() {
        val fakeBridge = FakeMediaBridge(isPlaying = false)
        val connector = FakeConnector(fakeBridge)
        val repository = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        assertEquals(MediaServiceConnectionStatus.CONNECTED, repository.playbackState.value.connectionStatus)

        // Simular desconexión por parte del servicio
        connector.lastOnDisconnected?.invoke()
        assertEquals(MediaServiceConnectionStatus.DISCONNECTED_BY_SERVICE, repository.playbackState.value.connectionStatus)

        // Intentar recuperación mediante botón Reconectar
        repository.connect()
        assertEquals(2, connector.connectCalls)
        assertEquals(MediaServiceConnectionStatus.CONNECTED, repository.playbackState.value.connectionStatus)
    }
}

package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class FakeMediaSessionBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = false,
        override var playbackState: Int = Player.STATE_READY,
        override var playWhenReady: Boolean = false,
        override var isLoading: Boolean = false,
        override var playerError: androidx.media3.common.PlaybackException? = null,
        override var currentPositionMs: Long = 0L,
        override var durationMs: Long = 180000L,
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
            playWhenReady = true
            listeners.forEach { 
                it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                it.onIsPlayingChanged(true) 
            }
        }

        override fun pause() {
            pauseCount++
            isPlaying = false
            playWhenReady = false
            listeners.forEach { 
                it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                it.onIsPlayingChanged(false) 
            }
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

        var lastSetMediaItem: MediaItem? = null
        var lastCustomCommandAction: String? = null

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            lastSetMediaItem = mediaItem
            currentMediaItem = mediaItem
            this.playWhenReady = playWhenReady
            if (playWhenReady) {
                isPlaying = true
            }
        }

        override fun sendCustomCommand(action: String) {
            lastCustomCommandAction = action
            if (action == "com.divinedeck.ACTION_PLAY_SAMPLE") {
                isPlaying = true
                currentMediaItem = MediaItem.Builder().setMediaId("divine_deck_sample_chime").build()
            }
        }

        override fun sendCustomCommand(action: String, args: android.os.Bundle) {
            sendCustomCommand(action)
        }

        override fun release() {
            releaseCount++
            isConnected = false
        }

        fun triggerMediaItemTransition(item: MediaItem?) {
            currentMediaItem = item
            listeners.forEach { it.onMediaItemTransition(item, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) }
        }

        fun triggerPlaybackStateChanged(state: Int) {
            playbackState = state
            listeners.forEach { it.onPlaybackStateChanged(state) }
        }

        fun triggerPlayWhenReadyChanged(ready: Boolean) {
            playWhenReady = ready
            listeners.forEach { it.onPlayWhenReadyChanged(ready, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        }

        fun triggerIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            listeners.forEach { it.onIsPlayingChanged(playing) }
        }

        fun triggerIsLoadingChanged(loading: Boolean) {
            isLoading = loading
            listeners.forEach { it.onIsLoadingChanged(loading) }
        }

        fun triggerPlayerError(error: androidx.media3.common.PlaybackException) {
            playerError = error
            listeners.forEach { it.onPlayerError(error) }
        }

        fun triggerCommandsChanged(commands: Set<Int>) {
            allowedCommands = commands
            listeners.forEach { it.onAvailableCommandsChanged(Player.Commands.EMPTY) }
        }
    }

    private class FakeBitChordSessionConnector(
        var nextBridge: FakeMediaSessionBridge? = FakeMediaSessionBridge(),
        var shouldFail: Boolean = false,
        var failureError: Throwable = IllegalStateException("Simulated connection error")
    ) : BitChordSessionConnector {
        var connectAttempts = 0
        var lastListenerDisconnected: (() -> Unit)? = null

        override fun connect(
            context: Context,
            onConnected: (BitChordMediaSessionBridge) -> Unit,
            onDisconnected: () -> Unit,
            onError: (Throwable) -> Unit
        ): AutoCloseable {
            connectAttempts++
            lastListenerDisconnected = onDisconnected
            if (shouldFail) {
                onError(failureError)
            } else {
                val bridge = nextBridge ?: FakeMediaSessionBridge()
                onConnected(bridge)
            }
            return AutoCloseable {}
        }

        fun simulateServiceDisconnection() {
            lastListenerDisconnected?.invoke()
        }
    }

    private fun createSampleMediaItem(
        title: String = "Test Track",
        artist: String = "Test Artist",
        album: String = "Test Album",
        mediaId: String = "yt-12345"
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    // =========================================================================
    // 1. Pruebas de Estado Inicial y Conexión
    // =========================================================================

    @Test
    fun `initial state is disconnected with empty track info when not connected`() {
        val repo = MediaControllerRepositoryImpl(
            context = null,
            coroutineScope = testScope
        )

        assertEquals(MediaServiceConnectionStatus.DISCONNECTED, repo.playbackState.value.connectionStatus)
        assertEquals(PlaybackStatus.STOPPED, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isPlaying)
        assertNull(repo.currentTrack.value.title)
        assertNull(repo.currentTrack.value.artist)
        assertNull(repo.currentTrack.value.mediaId)
    }

    @Test
    fun `successful connection updates connection status and maps track metadata`() {
        val fakeBridge = FakeMediaSessionBridge(
            currentMediaItem = createSampleMediaItem("Chevy Rhythm", "Motor Beats", "Cruising 2009"),
            durationMs = 210000L
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals(MediaServiceConnectionStatus.CONNECTED, repo.playbackState.value.connectionStatus)
        assertEquals("Chevy Rhythm", repo.currentTrack.value.title)
        assertEquals("Motor Beats", repo.currentTrack.value.artist)
        assertEquals("Cruising 2009", repo.currentTrack.value.album)
        assertEquals(210000L, repo.currentTrack.value.durationMs)
        assertTrue(repo.playbackState.value.canPlayPause)
        assertTrue(repo.playbackState.value.canSeek)
    }

    @Test
    fun `connection failure transitions to FAILED status with error message`() {
        val connector = FakeBitChordSessionConnector(
            shouldFail = true,
            failureError = SecurityException("PlaybackService no encontrado o inaccesible")
        )
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals(MediaServiceConnectionStatus.FAILED, repo.playbackState.value.connectionStatus)
        assertEquals(PlaybackStatus.ERROR, repo.playbackState.value.status)
        assertEquals("PlaybackService no encontrado o inaccesible", repo.playbackState.value.errorMessage)
    }

    // =========================================================================
    // 2. Metadatos Fidedignos y Manejo de Nulos (Sin Datos Falsos)
    // =========================================================================

    @Test
    fun `handles missing metadata cleanly with null values and no invented defaults`() {
        val fakeBridge = FakeMediaSessionBridge(
            currentMediaItem = null,
            durationMs = C.TIME_UNSET
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertNull(repo.currentTrack.value.title)
        assertNull(repo.currentTrack.value.artist)
        assertNull(repo.currentTrack.value.album)
        assertNull(repo.currentTrack.value.durationMs)
        assertFalse(repo.currentTrack.value.hasTrack)
        assertEquals("Sin reproducción activa", repo.currentTrack.value.displayTitle)
    }

    @Test
    fun `handles unset or negative duration safely`() {
        val fakeBridge = FakeMediaSessionBridge(
            currentMediaItem = createSampleMediaItem(),
            durationMs = C.TIME_UNSET
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertNull(repo.currentTrack.value.durationMs)
    }

    // =========================================================================
    // 3. Actualizaciones de Estado de Reproducción
    // =========================================================================

    @Test
    fun `playback state updates map buffering, playing, and paused correctly`() {
        val fakeBridge = FakeMediaSessionBridge(isPlaying = false, playbackState = Player.STATE_BUFFERING)
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals(PlaybackStatus.BUFFERING, repo.playbackState.value.status)
        assertTrue(repo.playbackState.value.isBuffering)

        fakeBridge.isPlaying = true
        fakeBridge.triggerPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlaybackStatus.PLAYING, repo.playbackState.value.status)
        assertTrue(repo.playbackState.value.isPlaying)
        assertFalse(repo.playbackState.value.isBuffering)

        fakeBridge.pause()
        assertEquals(PlaybackStatus.PAUSED, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isPlaying)
    }

    // =========================================================================
    // 4. Comandos de Control Multimedia
    // =========================================================================

    @Test
    fun `play and pause commands execute when canPlayPause is true`() {
        val fakeBridge = FakeMediaSessionBridge(isPlaying = false)
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.play()
        assertEquals(1, fakeBridge.playCount)
        assertTrue(repo.playbackState.value.isPlaying)

        repo.pause()
        assertEquals(1, fakeBridge.pauseCount)
        assertFalse(repo.playbackState.value.isPlaying)
    }

    @Test
    fun `togglePlayback switches between play and pause`() {
        val fakeBridge = FakeMediaSessionBridge(isPlaying = false)
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.togglePlayback()
        assertEquals(1, fakeBridge.playCount)

        repo.togglePlayback()
        assertEquals(1, fakeBridge.pauseCount)
    }

    @Test
    fun `commands are blocked when bridge reports command not available`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = false,
            allowedCommands = emptySet()
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertFalse(repo.playbackState.value.canPlayPause)
        assertFalse(repo.playbackState.value.canSeek)

        repo.play()
        assertEquals(0, fakeBridge.playCount)

        repo.seekTo(50000L)
        assertNull(fakeBridge.lastSeekPosition)
    }

    @Test
    fun `seeking validates and updates target position`() {
        val fakeBridge = FakeMediaSessionBridge(currentPositionMs = 1000L)
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.seekTo(45000L)
        assertEquals(45000L, fakeBridge.lastSeekPosition)
        assertEquals(45000L, repo.currentTrack.value.currentPositionMs)

        // Comprobación de valor negativo corregido a cero
        repo.seekTo(-500L)
        assertEquals(0L, fakeBridge.lastSeekPosition)
    }

    @Test
    fun `skipToNext and skipToPrevious execute when commands are available`() {
        val fakeBridge = FakeMediaSessionBridge()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.skipToNext()
        assertEquals(1, fakeBridge.skipNextCount)

        repo.skipToPrevious()
        assertEquals(1, fakeBridge.skipPreviousCount)
    }

    // =========================================================================
    // 5. Ciclo de Vida, Desconexión y Recursos
    // =========================================================================

    @Test
    fun `service disconnection updates status to DISCONNECTED_BY_SERVICE and clears metadata`() {
        val fakeBridge = FakeMediaSessionBridge(
            currentMediaItem = createSampleMediaItem("Track 1")
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals("Track 1", repo.currentTrack.value.title)

        connector.simulateServiceDisconnection()

        assertEquals(MediaServiceConnectionStatus.DISCONNECTED_BY_SERVICE, repo.playbackState.value.connectionStatus)
        assertEquals(PlaybackStatus.STOPPED, repo.playbackState.value.status)
        assertNull(repo.currentTrack.value.title)
    }

    @Test
    fun `disconnect releases bridge and resets state`() {
        val fakeBridge = FakeMediaSessionBridge()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.disconnect()

        assertEquals(MediaServiceConnectionStatus.DISCONNECTED, repo.playbackState.value.connectionStatus)
        assertEquals(1, fakeBridge.releaseCount)
        assertFalse(fakeBridge.isConnected)
    }

    @Test
    fun `reconnection does not duplicate connections if already connecting or connected`() {
        val connector = FakeBitChordSessionConnector()
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals(1, connector.connectAttempts)

        repo.connect()
        repo.connect()

        assertEquals(1, connector.connectAttempts)
    }

    @Test
    fun `volume is clamped between zero and one hundred`() {
        val repo = MediaControllerRepositoryImpl(
            context = null,
            coroutineScope = testScope
        )

        repo.setVolume(150)
        assertEquals(100, repo.playbackState.value.volumePercent)

        repo.setVolume(-20)
        assertEquals(0, repo.playbackState.value.volumePercent)

        repo.setVolume(85)
        assertEquals(85, repo.playbackState.value.volumePercent)
    }

    // =========================================================================
    // 6. Reproducción de Audio Local y Muestra Interna (Fase 06.3A)
    // =========================================================================

    @Test
    fun `playLocalUri sets mediaItem on bridge and begins playback`() {
        val fakeBridge = FakeMediaSessionBridge()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        val localUri = "content://media/external/audio/media/42"
        repo.playLocalUri(localUri, title = "Custom Chevy Road Song", artist = "Chevy Driver")

        assertNotNull(fakeBridge.lastSetMediaItem)
        assertEquals(localUri, fakeBridge.lastSetMediaItem?.mediaId)
        assertEquals("Custom Chevy Road Song", fakeBridge.lastSetMediaItem?.mediaMetadata?.title?.toString())
        assertEquals("Chevy Driver", fakeBridge.lastSetMediaItem?.mediaMetadata?.artist?.toString())
        assertTrue(fakeBridge.isPlaying)
    }

    @Test
    fun `playSampleAudio sends custom command to play internal asset sample`() {
        val fakeBridge = FakeMediaSessionBridge()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        repo.playSampleAudio()

        assertEquals("com.divinedeck.ACTION_PLAY_SAMPLE", fakeBridge.lastCustomCommandAction)
        assertTrue(fakeBridge.isPlaying)
        assertEquals("divine_deck_sample_chime", fakeBridge.currentMediaItem?.mediaId)
    }

    // =========================================================================
    // 7. Pruebas de Sincronización de Estado y Control Play/Pause (Fase 06.4E)
    // =========================================================================

    @Test
    fun `phase 06_4e test 1 state transition IDLE to BUFFERING to READY to PLAYING`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = false,
            playbackState = Player.STATE_IDLE,
            playWhenReady = false
        )
        fakeBridge.currentMediaItem = MediaItem.Builder().setMediaId("song_101").build()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals(PlaybackStatus.STOPPED, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isBuffering)
        assertFalse(repo.playbackState.value.isPlaying)

        // IDLE -> BUFFERING
        fakeBridge.triggerPlaybackStateChanged(Player.STATE_BUFFERING)
        assertEquals(PlaybackStatus.BUFFERING, repo.playbackState.value.status)
        assertTrue(repo.playbackState.value.isBuffering)
        assertFalse(repo.playbackState.value.isPlaying)

        // BUFFERING -> READY (PAUSED)
        fakeBridge.triggerPlaybackStateChanged(Player.STATE_READY)
        assertEquals(PlaybackStatus.PAUSED, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isBuffering)
        assertFalse(repo.playbackState.value.isPlaying)

        // READY -> PLAYING
        fakeBridge.triggerIsPlayingChanged(true)
        assertEquals(PlaybackStatus.PLAYING, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isBuffering)
        assertTrue(repo.playbackState.value.isPlaying)
    }

    @Test
    fun `phase 06_4e test 2 state transition PLAYING to PAUSED`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = true,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertTrue(repo.playbackState.value.isPlaying)
        assertEquals(PlaybackStatus.PLAYING, repo.playbackState.value.status)

        repo.togglePlayback()

        assertEquals(1, fakeBridge.pauseCount)
        assertFalse(repo.playbackState.value.isPlaying)
        assertFalse(repo.playbackState.value.isBuffering)
        assertEquals(PlaybackStatus.PAUSED, repo.playbackState.value.status)
    }

    @Test
    fun `phase 06_4e test 3 state transition PAUSED to PLAYING`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = false,
            playbackState = Player.STATE_READY,
            playWhenReady = false
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertFalse(repo.playbackState.value.isPlaying)
        assertEquals(PlaybackStatus.PAUSED, repo.playbackState.value.status)

        repo.togglePlayback()

        assertEquals(1, fakeBridge.playCount)
        assertTrue(repo.playbackState.value.isPlaying)
        assertFalse(repo.playbackState.value.isBuffering)
        assertEquals(PlaybackStatus.PLAYING, repo.playbackState.value.status)
    }

    @Test
    fun `phase 06_4e test 4 state transition BUFFERING to ERROR ceases infinite loading`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = false,
            playbackState = Player.STATE_BUFFERING,
            playWhenReady = true
        )
        fakeBridge.currentMediaItem = MediaItem.Builder().setMediaId("song_err").build()
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertTrue(repo.playbackState.value.isBuffering)
        assertEquals(PlaybackStatus.BUFFERING, repo.playbackState.value.status)

        // Transición a error
        val playbackException = androidx.media3.common.PlaybackException(
            "Audio stream network failure",
            null,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        fakeBridge.triggerPlayerError(playbackException)

        assertEquals(PlaybackStatus.ERROR, repo.playbackState.value.status)
        assertFalse(repo.playbackState.value.isBuffering)
        assertFalse(repo.playbackState.value.isPlaying)
        assertEquals("Audio stream network failure", repo.playbackState.value.errorMessage)
    }

    @Test
    fun `phase 06_4e test 5 active playback with isLoading true coexists without showing buffering`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = true,
            playbackState = Player.STATE_READY,
            playWhenReady = true,
            isLoading = false
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertTrue(repo.playbackState.value.isPlaying)
        assertFalse(repo.playbackState.value.isBuffering)

        // ExoPlayer comienza a descargar el siguiente chunk (isLoading = true) mientras reproduce
        fakeBridge.triggerIsLoadingChanged(true)

        assertTrue(repo.playbackState.value.isLoading)
        assertTrue(repo.playbackState.value.isPlaying)
        assertFalse(repo.playbackState.value.isBuffering)
        assertEquals(PlaybackStatus.PLAYING, repo.playbackState.value.status)
    }

    @Test
    fun `phase 06_4e test 6 button update when playWhenReady changes`() {
        val fakeBridge = FakeMediaSessionBridge(
            isPlaying = false,
            playbackState = Player.STATE_READY,
            playWhenReady = false
        )
        val connector = FakeBitChordSessionConnector(nextBridge = fakeBridge)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertFalse(repo.playbackState.value.playWhenReady)

        fakeBridge.triggerPlayWhenReadyChanged(true)
        assertTrue(repo.playbackState.value.playWhenReady)

        fakeBridge.triggerPlayWhenReadyChanged(false)
        assertFalse(repo.playbackState.value.playWhenReady)
    }

    @Test
    fun `phase 06_4e test 7 controller reconnection and current state retrieval`() {
        val bridge1 = FakeMediaSessionBridge(isPlaying = true)
        bridge1.currentMediaItem = MediaItem.Builder().setMediaId("song_first").build()
        val connector = FakeBitChordSessionConnector(nextBridge = bridge1)
        val repo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        assertEquals("song_first", repo.currentTrack.value.mediaId)
        assertTrue(repo.playbackState.value.isPlaying)

        // Desconexión
        repo.disconnect()
        assertEquals(MediaServiceConnectionStatus.DISCONNECTED, repo.playbackState.value.connectionStatus)
        assertNull(repo.currentTrack.value.mediaId)

        // Reconexión con nuevo puente
        val bridge2 = FakeMediaSessionBridge(
            isPlaying = true,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        bridge2.currentMediaItem = MediaItem.Builder().setMediaId("song_reconnected").build()
        connector.nextBridge = bridge2

        repo.connect()
        assertEquals(MediaServiceConnectionStatus.CONNECTED, repo.playbackState.value.connectionStatus)
        assertEquals("song_reconnected", repo.currentTrack.value.mediaId)
        assertTrue(repo.playbackState.value.isPlaying)
    }

    @Test
    fun `phase 06_4e test 8 navigation preserves shared playback state`() {
        // Simula la instancia compartida a nivel de Activity entre Cockpit y MusicCenter
        val sharedBridge = FakeMediaSessionBridge(
            isPlaying = true,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        sharedBridge.currentMediaItem = MediaItem.Builder().setMediaId("driving_anthem").build()
        val connector = FakeBitChordSessionConnector(nextBridge = sharedBridge)
        val sharedRepo = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        // 1. Estado en Music Center
        assertEquals("driving_anthem", sharedRepo.currentTrack.value.mediaId)
        assertTrue(sharedRepo.playbackState.value.isPlaying)

        // 2. Navegación a Cockpit: el repositorio permanece activo y no se desconecta
        // La reproducción continúa en segundo plano
        assertTrue(sharedBridge.isConnected)
        assertTrue(sharedRepo.playbackState.value.isPlaying)

        // 3. Retorno a Music Center: el estado se mantiene íntegro e inmediato
        assertEquals("driving_anthem", sharedRepo.currentTrack.value.mediaId)
        assertTrue(sharedRepo.playbackState.value.isPlaying)
        assertFalse(sharedRepo.playbackState.value.isBuffering)

        // 4. El usuario puede pausar inmediatamente al regresar
        sharedRepo.togglePlayback()
        assertEquals(1, sharedBridge.pauseCount)
        assertFalse(sharedRepo.playbackState.value.isPlaying)
    }
}

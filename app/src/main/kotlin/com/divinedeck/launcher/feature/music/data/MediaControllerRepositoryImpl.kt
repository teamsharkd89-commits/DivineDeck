package com.divinedeck.launcher.feature.music.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.music.domain.model.MediaPlaybackState
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Implementación del repositorio de control multimedia externo para DIVINE DECK.
 * Actúa estrictamente como controlador cliente sobre el PlaybackService de BitChord mediante AndroidX Media3.
 * BitChord es el propietario único y exclusivo del motor de reproducción real ExoPlayer.
 */
class MediaControllerRepositoryImpl(
    private val context: Context? = null,
    private val connector: BitChordSessionConnector = AndroidBitChordSessionConnector(),
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
) : MediaControllerRepository {

    private val _currentTrack = MutableStateFlow(TrackInfo())
    override val currentTrack: StateFlow<TrackInfo> = _currentTrack.asStateFlow()

    private val _playbackState = MutableStateFlow(MediaPlaybackState())
    override val playbackState: StateFlow<MediaPlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    override val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private var activeBridge: BitChordMediaSessionBridge? = null
    private var connectionHandle: AutoCloseable? = null
    private var positionTickerJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        if (context != null) {
            connect()
        }
    }

    override fun connect() {
        val currentStatus = _playbackState.value.connectionStatus
        if (currentStatus == MediaServiceConnectionStatus.CONNECTING ||
            currentStatus == MediaServiceConnectionStatus.CONNECTED
        ) {
            return
        }

        val ctx = context
        if (ctx == null) {
            _playbackState.update {
                it.copy(
                    connectionStatus = MediaServiceConnectionStatus.FAILED,
                    errorMessage = "Contexto no provisto para la conexión Media3"
                )
            }
            return
        }

        _playbackState.update {
            it.copy(
                connectionStatus = MediaServiceConnectionStatus.CONNECTING,
                errorMessage = null
            )
        }

        connectionHandle?.close()
        connectionHandle = connector.connect(
            context = ctx,
            onConnected = { bridge ->
                onSessionConnected(bridge)
            },
            onDisconnected = {
                onSessionDisconnected()
            },
            onError = { error ->
                onSessionError(error)
            }
        )
    }

    override fun disconnect() {
        stopPositionTicker()
        detachPlayerListener()
        connectionHandle?.close()
        connectionHandle = null
        activeBridge?.release()
        activeBridge = null

        _playbackState.update {
            it.copy(
                connectionStatus = MediaServiceConnectionStatus.DISCONNECTED,
                status = PlaybackStatus.STOPPED,
                isPlaying = false,
                isBuffering = false,
                isLoading = false,
                playWhenReady = false,
                playbackState = Player.STATE_IDLE,
                canPlayPause = false,
                canSkipNext = false,
                canSkipPrevious = false,
                canSeek = false,
                availablePlayerCommands = emptySet(),
                errorMessage = null
            )
        }
        _currentTrack.value = TrackInfo()
        _currentQueue.value = emptyList()
    }

    private fun onSessionConnected(bridge: BitChordMediaSessionBridge) {
        activeBridge = bridge
        setupPlayerListener(bridge)

        updateMetadataFromMediaItem(bridge.currentMediaItem)
        syncPlaybackState(bridge)

        _playbackState.update {
            it.copy(
                connectionStatus = MediaServiceConnectionStatus.CONNECTED,
                errorMessage = null
            )
        }
    }

    private fun onSessionDisconnected() {
        stopPositionTicker()
        detachPlayerListener()
        activeBridge = null

        _playbackState.update {
            it.copy(
                connectionStatus = MediaServiceConnectionStatus.DISCONNECTED_BY_SERVICE,
                status = PlaybackStatus.STOPPED,
                isPlaying = false,
                isBuffering = false,
                isLoading = false,
                playWhenReady = false,
                playbackState = Player.STATE_IDLE,
                canPlayPause = false,
                canSkipNext = false,
                canSkipPrevious = false,
                canSeek = false,
                availablePlayerCommands = emptySet()
            )
        }
        _currentTrack.value = TrackInfo()
        _currentQueue.value = emptyList()
    }

    private fun onSessionError(error: Throwable) {
        stopPositionTicker()
        detachPlayerListener()
        activeBridge = null

        _playbackState.update {
            it.copy(
                connectionStatus = MediaServiceConnectionStatus.FAILED,
                status = PlaybackStatus.ERROR,
                isPlaying = false,
                isBuffering = false,
                isLoading = false,
                playWhenReady = false,
                playbackState = Player.STATE_IDLE,
                errorMessage = error.message ?: "Fallo al conectar con PlaybackService de BitChord"
            )
        }
    }

    private fun setupPlayerListener(bridge: BitChordMediaSessionBridge) {
        detachPlayerListener()
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateMetadataFromMediaItem(mediaItem)
                syncPlaybackState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlaybackState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                syncPlaybackState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlaybackState()
            }

            override fun onIsLoadingChanged(isLoading: Boolean) {
                syncPlaybackState()
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                updateCommands(availableCommands)
                syncPlaybackState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _playbackState.update {
                    it.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = error.message ?: "Error de reproducción",
                        isBuffering = false,
                        isPlaying = false,
                        isLoading = false
                    )
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                syncQueueFromBridge()
                syncPlaybackState()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                syncPlaybackState()
            }
        }
        playerListener = listener
        bridge.setPlayerListener(listener)
    }

    private fun syncQueueFromBridge() {
        val bridge = activeBridge ?: return
        val count = bridge.mediaItemCount
        if (count == 0) return
        val items = (0 until count).mapNotNull { idx ->
            bridge.getMediaItemAt(idx)?.toSong()
        }
        if (items.isNotEmpty()) {
            _currentQueue.value = items
        }
    }

    private fun detachPlayerListener() {
        val listener = playerListener ?: return
        activeBridge?.removePlayerListener(listener)
        playerListener = null
    }

    private fun updateMetadataFromMediaItem(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            _currentTrack.value = TrackInfo()
            return
        }

        val metadata = mediaItem.mediaMetadata
        val bridge = activeBridge
        val dur = bridge?.durationMs?.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: _currentQueue.value.find { it.videoId == mediaItem.mediaId }?.durationMillis?.takeIf { it > 0L }

        _currentTrack.update {
            it.copy(
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                album = metadata.albumTitle?.toString(),
                albumArtUri = metadata.artworkUri?.toString(),
                mediaId = mediaItem.mediaId.takeIf { id -> id.isNotBlank() },
                durationMs = dur,
                currentPositionMs = bridge?.currentPositionMs?.coerceAtLeast(0L) ?: 0L
            )
        }
    }

    private fun syncPlaybackState(bridge: BitChordMediaSessionBridge? = activeBridge) {
        val b = bridge ?: return
        if (!b.isConnected) return

        val isPlaying = b.isPlaying
        val playWhenReady = b.playWhenReady
        val playbackStateInt = b.playbackState
        val isLoading = b.isLoading
        val playerError = b.playerError

        // COMPORTAMIENTO DEL ESTADO VISUAL:
        // 1. Si la canción está reproduciéndose realmente: isPlaying == true -> isBuffering = false.
        // 2. Si está en buffering real y todavía no puede reproducirse:
        //    playbackStateInt == STATE_BUFFERING, !isPlaying, y playerError == null.
        // 3. Si ocurre un error: isBuffering = false.
        // 4. Si el reproductor está inactivo o detenido: isBuffering = false.
        val isBuffering = !isPlaying && (playbackStateInt == Player.STATE_BUFFERING) && playerError == null

        val status = when {
            playerError != null -> PlaybackStatus.ERROR
            isPlaying -> PlaybackStatus.PLAYING
            isBuffering -> PlaybackStatus.BUFFERING
            playbackStateInt == Player.STATE_READY -> PlaybackStatus.PAUSED
            playbackStateInt == Player.STATE_ENDED || playbackStateInt == Player.STATE_IDLE -> PlaybackStatus.STOPPED
            else -> PlaybackStatus.STOPPED
        }

        val hasTrack = b.currentMediaItem != null || _currentTrack.value.title != null
        val canPlayPause = b.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
        val canSeek = b.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val canNext = b.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) || (_currentQueue.value.size > 1)
        val canPrev = hasTrack || b.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

        val commandSet = mutableSetOf<Int>()
        val commands = b.availableCommands
        if (commands != null) {
            for (i in 0 until commands.size()) {
                commandSet.add(commands.get(i))
            }
        }
        if (canPlayPause) commandSet.add(Player.COMMAND_PLAY_PAUSE)
        if (canSeek) commandSet.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (canNext) commandSet.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        if (canPrev) commandSet.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

        _playbackState.update { current ->
            current.copy(
                status = status,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                isLoading = isLoading,
                playWhenReady = playWhenReady,
                playbackState = playbackStateInt,
                canPlayPause = canPlayPause,
                canSeek = canSeek,
                canSkipNext = canNext,
                canSkipPrevious = canPrev,
                availablePlayerCommands = commandSet,
                errorMessage = playerError?.message ?: if (status != PlaybackStatus.ERROR) null else current.errorMessage
            )
        }

        if (isPlaying) {
            startPositionTicker()
        } else {
            stopPositionTicker()
        }

        // Actualizar duración si ahora está disponible
        val dur = b.durationMs.takeIf { it > 0 && it != C.TIME_UNSET }
        if (dur != null && _currentTrack.value.durationMs != dur) {
            _currentTrack.update { it.copy(durationMs = dur) }
        }
    }

    private fun updateCommands(commands: Player.Commands) {
        val bridge = activeBridge
        val hasTrack = bridge?.currentMediaItem != null || _currentTrack.value.title != null
        val canPlayPause = bridge?.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) ?: commands.contains(Player.COMMAND_PLAY_PAUSE)
        val canSeek = bridge?.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ?: commands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        val canNext = (bridge?.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ?: commands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) || (_currentQueue.value.size > 1)
        val canPrev = hasTrack || (bridge?.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ?: commands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))

        val commandSet = mutableSetOf<Int>()
        for (i in 0 until commands.size()) {
            commandSet.add(commands.get(i))
        }
        if (canPlayPause) commandSet.add(Player.COMMAND_PLAY_PAUSE)
        if (canSeek) commandSet.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (canNext) commandSet.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        if (canPrev) commandSet.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

        _playbackState.update {
            it.copy(
                canPlayPause = canPlayPause,
                canSeek = canSeek,
                canSkipNext = canNext,
                canSkipPrevious = canPrev,
                availablePlayerCommands = commandSet
            )
        }
    }

    private fun startPositionTicker() {
        if (positionTickerJob?.isActive == true) return
        positionTickerJob = coroutineScope.launch {
            while (isActive) {
                delay(500)
                val bridge = activeBridge ?: break
                if (!bridge.isConnected || !bridge.isPlaying) break
                val pos = bridge.currentPositionMs.coerceAtLeast(0L)
                val dur = bridge.durationMs.takeIf { it > 0 && it != C.TIME_UNSET }
                _currentTrack.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur ?: it.durationMs
                    )
                }
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    override fun play() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val canPlay = _playbackState.value.canPlayPause || bridge.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
        if (canPlay) {
            try {
                bridge.play()
                syncPlaybackState(bridge)
            } catch (_: Throwable) {}
        }
    }

    override fun pause() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val canPause = _playbackState.value.canPlayPause || bridge.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)
        if (canPause) {
            try {
                bridge.pause()
                syncPlaybackState(bridge)
            } catch (_: Throwable) {}
        }
    }

    override fun togglePlayback() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        // Consultar el estado real actual del reproductor
        val currentlyPlaying = bridge.isPlaying
        if (currentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    override fun skipToNext() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        if (_playbackState.value.canSkipNext || bridge.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            try {
                bridge.seekToNext()
            } catch (_: Throwable) {}
        }
    }

    override fun skipToPrevious() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val currentPos = bridge.currentPositionMs
        if (currentPos > 3000L) {
            seekTo(0L)
        } else {
            if (_playbackState.value.canSkipPrevious || bridge.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
                try {
                    bridge.seekToPrevious()
                } catch (_: Throwable) {
                    seekTo(0L)
                }
            } else {
                seekTo(0L)
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        if (_playbackState.value.canSeek || bridge.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            val target = positionMs.coerceAtLeast(0L)
            try {
                bridge.seekTo(target)
                _currentTrack.update { it.copy(currentPositionMs = target) }
            } catch (_: Throwable) {}
        }
    }

    override fun setVolume(percent: Int) {
        _playbackState.update { it.copy(volumePercent = percent.coerceIn(0, 100)) }
    }

    override fun playLocalUri(uriString: String, title: String?, artist: String?, artworkUri: String?) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val metadata = MediaMetadata.Builder()
            .setTitle(title ?: uriString.substringAfterLast('/').ifBlank { "Audio Local" })
            .setArtist(artist ?: "DIVINE DECK")
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(uriString)
            .setUri(uriString)
            .setMediaMetadata(metadata)
            .build()
        try {
            bridge.setMediaItem(mediaItem, playWhenReady = true)
        } catch (_: Throwable) {}
    }

    override fun playLocalUri(uri: Uri, title: String?, artist: String?, artworkUri: String?) {
        playLocalUri(uri.toString(), title, artist, artworkUri)
    }

    override fun playSong(song: Song) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val enrichedSong = Downloads.enrichWithLocalDownload(song)
        val mediaItem = enrichedSong.toMediaItem()
        _currentQueue.value = listOf(enrichedSong)
        try {
            bridge.setMediaItem(mediaItem, playWhenReady = true)
            updateMetadataFromMediaItem(mediaItem)
            val dur = enrichedSong.durationMillis
            if (dur > 0L) {
                _currentTrack.update { it.copy(durationMs = dur) }
            }
        } catch (_: Throwable) {}
    }

    override fun playQueue(songs: List<Song>, startIndex: Int) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected || songs.isEmpty()) return
        if (songs.size == 1 && startIndex == 0) {
            playSong(songs.first())
            return
        }
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val enrichedList = songs.map { Downloads.enrichWithLocalDownload(it) }
        _currentQueue.value = enrichedList
        val mediaItems = enrichedList.map { it.toMediaItem() }
        try {
            bridge.setMediaItems(mediaItems, startIndex = safeIndex, startPositionMs = 0L, playWhenReady = true)
            val targetSong = enrichedList[safeIndex]
            updateMetadataFromMediaItem(mediaItems[safeIndex])
            val dur = targetSong.durationMillis
            if (dur > 0L) {
                _currentTrack.update { it.copy(durationMs = dur) }
            }
        } catch (_: Throwable) {}
    }

    override fun skipToQueueItem(index: Int) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val queue = _currentQueue.value
        if (index in queue.indices) {
            try {
                bridge.seekToDefaultPosition(index)
            } catch (_: Throwable) {}
        }
    }

    override fun enqueueNext(song: Song) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val enrichedSong = Downloads.enrichWithLocalDownload(song).copy(
            queueTier = QueueTier.USER_QUEUE,
            queueEntryId = song.queueEntryId ?: UUID.randomUUID().toString(),
        )
        val currentList = _currentQueue.value
        if (currentList.isEmpty()) {
            playSong(enrichedSong)
            return
        }
        val currentIndex = bridge.currentMediaItemIndex
        val insertionIndex = QueueCoordinator.findUserQueueInsertionIndex(
            timeline = currentList,
            currentIndex = currentIndex,
            isNext = true,
        ).coerceIn(0, currentList.size)

        val updated = currentList.toMutableList().apply {
            add(insertionIndex, enrichedSong)
        }
        _currentQueue.value = updated
        val mediaItem = enrichedSong.toMediaItem()
        try {
            bridge.addMediaItem(insertionIndex, mediaItem)
        } catch (_: Throwable) {}
        syncPlaybackState(bridge)
    }

    override fun enqueueLast(song: Song) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val enrichedSong = Downloads.enrichWithLocalDownload(song).copy(
            queueTier = QueueTier.USER_QUEUE,
            queueEntryId = song.queueEntryId ?: UUID.randomUUID().toString(),
        )
        val currentList = _currentQueue.value
        if (currentList.isEmpty()) {
            playSong(enrichedSong)
            return
        }
        val currentIndex = bridge.currentMediaItemIndex
        val insertionIndex = QueueCoordinator.findUserQueueInsertionIndex(
            timeline = currentList,
            currentIndex = currentIndex,
            isNext = false,
        ).coerceIn(0, currentList.size)

        val updated = currentList.toMutableList().apply {
            add(insertionIndex, enrichedSong)
        }
        _currentQueue.value = updated
        val mediaItem = enrichedSong.toMediaItem()
        try {
            bridge.addMediaItem(insertionIndex, mediaItem)
        } catch (_: Throwable) {}
        syncPlaybackState(bridge)
    }

    override fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val currentList = _currentQueue.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)
        _currentQueue.value = currentList
        try {
            bridge.moveMediaItem(fromIndex, toIndex)
        } catch (_: Throwable) {}
        syncPlaybackState(bridge)
    }

    override fun removeFromQueue(index: Int) {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val currentList = _currentQueue.value.toMutableList()
        if (index !in currentList.indices) return
        val currentIndex = bridge.currentMediaItemIndex
        if (index == currentIndex && currentList.size > 1) {
            return
        }
        currentList.removeAt(index)
        _currentQueue.value = currentList
        try {
            bridge.removeMediaItem(index)
        } catch (_: Throwable) {}
        syncPlaybackState(bridge)
    }

    override fun clearUserQueue() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        val currentList = _currentQueue.value
        val currentIndex = bridge.currentMediaItemIndex
        val indicesToRemove = mutableListOf<Int>()
        for (i in (currentIndex + 1) until currentList.size) {
            if (currentList[i].queueTier == QueueTier.USER_QUEUE) {
                indicesToRemove.add(i)
            }
        }
        if (indicesToRemove.isEmpty()) return

        val updated = currentList.filterIndexed { idx, item ->
            !(idx > currentIndex && item.queueTier == QueueTier.USER_QUEUE)
        }
        _currentQueue.value = updated
        for (i in indicesToRemove.asReversed()) {
            try {
                bridge.removeMediaItem(i)
            } catch (_: Throwable) {}
        }
        syncPlaybackState(bridge)
    }

    override fun playOnlineTrack(
        videoId: String,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        durationMs: Long?
    ) {
        val song = Song(
            videoId = videoId,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationMs?.takeIf { it > 0L }?.let { it / 1000L }
        )
        playSong(song)
    }

    override fun playSampleAudio() {
        val bridge = activeBridge ?: return
        if (!bridge.isConnected) return
        try {
            bridge.sendCustomCommand("com.divinedeck.ACTION_PLAY_SAMPLE")
        } catch (_: Throwable) {}
    }
}

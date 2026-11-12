package com.divinedeck.launcher.feature.music.data

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.SessionCommand

/**
 * Abstracción de sesión multimedia Media3 para el motor de BitChord y DIVINE DECK.
 * Permite desacoplar MediaControllerRepositoryImpl de los detalles concretos de IPC en pruebas unitarias.
 */
interface BitChordMediaSessionBridge {
    val isConnected: Boolean
    val isPlaying: Boolean
    val playbackState: Int
    val playWhenReady: Boolean get() = isPlaying
    val isLoading: Boolean
    val playerError: androidx.media3.common.PlaybackException? get() = null
    val currentPositionMs: Long
    val durationMs: Long
    val currentMediaItem: MediaItem?
    val currentMediaItemIndex: Int get() = 0
    val mediaItemCount: Int get() = if (currentMediaItem != null) 1 else 0
    val availableCommands: Player.Commands?

    fun isCommandAvailable(command: Int): Boolean
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekToNext()
    fun seekToPrevious()
    fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean = true)
    fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (mediaItems.isNotEmpty()) {
            val safeIdx = startIndex.coerceIn(0, mediaItems.lastIndex)
            setMediaItem(mediaItems[safeIdx], playWhenReady)
        }
    }
    fun seekToDefaultPosition(mediaItemIndex: Int) {}
    fun addMediaItem(index: Int, mediaItem: MediaItem) {}
    fun removeMediaItem(index: Int) {}
    fun moveMediaItem(fromIndex: Int, toIndex: Int) {}
    fun clearMediaItems() {}
    fun getMediaItemAt(index: Int): MediaItem? = null
    fun sendCustomCommand(action: String)
    fun sendCustomCommand(action: String, args: Bundle)
    fun setPlayerListener(listener: Player.Listener?)
    fun removePlayerListener(listener: Player.Listener)
    fun release()
}

/**
 * Implementación puente para producción que envuelve una instancia real de [androidx.media3.session.MediaController].
 */
class AndroidMedia3SessionBridge(
    private val controller: androidx.media3.session.MediaController
) : BitChordMediaSessionBridge {
    override val isConnected: Boolean get() = controller.isConnected
    override val isPlaying: Boolean get() = controller.isPlaying
    override val playbackState: Int get() = controller.playbackState
    override val playWhenReady: Boolean get() = controller.playWhenReady
    override val isLoading: Boolean get() = controller.isLoading
    override val playerError: androidx.media3.common.PlaybackException? get() = controller.playerError
    override val currentPositionMs: Long get() = controller.currentPosition
    override val durationMs: Long get() = controller.duration
    override val currentMediaItem: MediaItem? get() = controller.currentMediaItem
    override val currentMediaItemIndex: Int get() = controller.currentMediaItemIndex
    override val mediaItemCount: Int get() = controller.mediaItemCount
    override val availableCommands: Player.Commands? get() = controller.availableCommands

    override fun isCommandAvailable(command: Int): Boolean {
        return controller.isCommandAvailable(command)
    }

    override fun play() {
        // Corrección técnica clave para resolver el estado infinito de buffering/carga:
        // Si el reproductor se encuentra en STATE_IDLE o no ha sido preparado,
        // se debe invocar prepare() antes de play() para inicializar el pipeline de audio.
        if (controller.playbackState == Player.STATE_IDLE) {
            controller.prepare()
        }
        controller.play()
    }

    override fun pause() {
        controller.pause()
    }

    override fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    override fun seekToNext() {
        controller.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        controller.seekToPreviousMediaItem()
    }

    override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
        controller.setMediaItem(mediaItem)
        controller.prepare()
        if (playWhenReady) {
            controller.play()
        }
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.prepare()
        if (playWhenReady) {
            controller.play()
        }
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        controller.seekToDefaultPosition(mediaItemIndex)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        val safeIndex = index.coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(safeIndex, mediaItem)
    }

    override fun removeMediaItem(index: Int) {
        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
        }
    }

    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        val count = controller.mediaItemCount
        if (fromIndex in 0 until count && toIndex in 0 until count) {
            controller.moveMediaItem(fromIndex, toIndex)
        }
    }

    override fun clearMediaItems() {
        controller.clearMediaItems()
    }

    override fun getMediaItemAt(index: Int): MediaItem? {
        return if (index in 0 until controller.mediaItemCount) {
            controller.getMediaItemAt(index)
        } else {
            null
        }
    }

    override fun sendCustomCommand(action: String) {
        val command = SessionCommand(action, Bundle())
        controller.sendCustomCommand(command, Bundle())
    }

    override fun sendCustomCommand(action: String, args: Bundle) {
        val command = SessionCommand(action, Bundle())
        controller.sendCustomCommand(command, args)
    }

    override fun setPlayerListener(listener: Player.Listener?) {
        listener?.let { controller.addListener(it) }
    }

    override fun removePlayerListener(listener: Player.Listener) {
        controller.removeListener(listener)
    }

    override fun release() {
        try {
            controller.release()
        } catch (_: Throwable) {}
    }
}

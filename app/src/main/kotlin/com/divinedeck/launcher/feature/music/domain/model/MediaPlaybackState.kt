package com.divinedeck.launcher.feature.music.domain.model

import androidx.media3.common.Player

enum class PlaybackStatus {
    STOPPED,
    PLAYING,
    PAUSED,
    BUFFERING,
    CONNECTING,
    ERROR
}

enum class MediaServiceConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED_BY_SERVICE
}

/**
 * Estado inmutable de reproducción y conectividad con el servicio Media3 de BitChord.
 */
data class MediaPlaybackState(
    val connectionStatus: MediaServiceConnectionStatus = MediaServiceConnectionStatus.DISCONNECTED,
    val status: PlaybackStatus = PlaybackStatus.STOPPED,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = false,
    val playWhenReady: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val volumePercent: Int = 75,
    val isBluetoothConnected: Boolean = true,
    val connectedDeviceName: String = "Bocinas Chevy C2",
    val canPlayPause: Boolean = false,
    val canSkipNext: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSeek: Boolean = false,
    val availablePlayerCommands: Set<Int> = emptySet(),
    val errorMessage: String? = null,
    val servicePackage: String = "com.music.bitchord",
    val serviceClass: String = "com.music.bitchord.playback.PlaybackService"
)

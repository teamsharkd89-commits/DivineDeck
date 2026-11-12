package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadState
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.theme.rememberArtworkPalette
import com.music.bitchord.ui.theme.rememberPlayerTint

/**
 * Public Composable entry-point for the BitChord Player UI library (:bitchord-player-ui).
 *
 * Fully modular and decoupled from background playback engines, Room databases,
 * or network providers. State is passed in as pure parameters; user actions
 * emit callbacks.
 *
 * Licensed under GNU General Public License v3.0 (GPLv3).
 */
@Composable
fun BitChordPlayerScreen(
    song: Song?,
    position: PlaybackPosition,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    durationMs: Long = song?.durationMillis ?: 0L,
    lyrics: List<LyricLine>? = null,
    lyricsState: LyricsUiState? = null,
    queue: List<Song> = emptyList(),
    activePane: PlayerPane = PlayerPane.Main,
    canPlayPause: Boolean = true,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
    canSeek: Boolean = true,
    onPaneChange: (PlayerPane) -> Unit = {},
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSeekFraction: (Float) -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onSongSelect: ((Song, Int) -> Unit)? = null,
    likeStatus: LikeStatus = LikeStatus.INDIFFERENT,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = RepeatModes.OFF,
    downloadState: DownloadState? = null,
    isDownloaded: Boolean = false,
    onDownloadSong: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onLyricClick: ((Long) -> Unit)? = null,
    onRetryLyrics: (() -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenLyricsSettings: (() -> Unit)? = null,
    currentIndex: Int = -1,
    onReorderQueue: ((Int, Int) -> Unit)? = null,
    onRemoveQueueItem: ((Int) -> Unit)? = null,
    onClearQueue: (() -> Unit)? = null,
    effectsEnabled: Boolean = true,
    effectStyle: LyricsEffectStyle = LyricsEffectStyle.PULSE,
    effectIntensity: Float = 1.0f,
    beatReaction: Boolean = true,
    lowPowerMode: Boolean = false,
    audioEnergy: Float = 0f,
    beatPulse: Float = 0f,
) {
    var internalPane by remember { mutableStateOf(activePane) }
    val currentPane = if (activePane != PlayerPane.Main) activePane else internalPane

    val palette = rememberArtworkPalette(song?.artworkUrl)
    val tint = rememberPlayerTint(song?.artworkUrl) ?: Color.White

    CompositionLocalProvider(LocalPlayerTint provides tint) {
        LandscapePlayerLayout(
            pane = currentPane,
            background = { bgModifier ->
                MeshGradient(
                    imageUrl = song?.artworkUrl,
                    modifier = bgModifier,
                )
            },
            artwork = { artModifier ->
                LandscapeArtworkCard(
                    song = song,
                    modifier = artModifier,
                )
            },
            actions = {
                PlayerActionRow(
                    lyricsOpen = currentPane == PlayerPane.Lyrics,
                    queueOpen = currentPane == PlayerPane.Queue,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    likeStatus = likeStatus,
                    onToggleLyrics = {
                        val next = if (currentPane == PlayerPane.Lyrics) PlayerPane.Main else PlayerPane.Lyrics
                        internalPane = next
                        onPaneChange(next)
                    },
                    onToggleQueue = {
                        val next = if (currentPane == PlayerPane.Queue) PlayerPane.Main else PlayerPane.Queue
                        internalPane = next
                        onPaneChange(next)
                    },
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleLike = onToggleLike,
                    currentSong = song,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded,
                    onDownloadSong = onDownloadSong,
                    onCancelDownload = onCancelDownload,
                    onRetryDownload = onRetryDownload,
                    onDeleteDownload = onDeleteDownload,
                    onOpenEqualizer = onOpenEqualizer,
                )
            },
            mainPane = {
                if (song == null) {
                    PlayerEmptyState()
                } else {
                    PlayerMainPane(
                        song = song,
                        position = position,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        lyrics = lyrics,
                        canPlayPause = canPlayPause,
                        canPrevious = canPrevious,
                        canNext = canNext,
                        canSeek = canSeek,
                        onSeekFraction = onSeekFraction,
                        onPrevious = onPrevious,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onToggleLyrics = {
                            val next = if (currentPane == PlayerPane.Lyrics) PlayerPane.Main else PlayerPane.Lyrics
                            internalPane = next
                            onPaneChange(next)
                        },
                    )
                }
            },
            lyricsPane = {
                SyncedLyricsPanel(
                    lines = lyrics,
                    uiState = lyricsState,
                    position = position,
                    isPlaying = isPlaying,
                    onSeekToLine = { timeMs ->
                        onSeek(timeMs)
                        onLyricClick?.invoke(timeMs)
                    },
                    onRetryLyrics = onRetryLyrics,
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    effectsEnabled = effectsEnabled,
                    effectStyle = effectStyle,
                    effectIntensity = effectIntensity,
                    beatReaction = beatReaction,
                    lowPowerMode = lowPowerMode,
                    audioEnergy = audioEnergy,
                    beatPulse = beatPulse,
                )
            },
            queuePane = {
                PlayerQueuePane(
                    queue = queue,
                    currentSong = song,
                    currentIndex = currentIndex,
                    onSongClick = { s, idx -> onSongSelect?.invoke(s, idx) },
                    onReorder = onReorderQueue,
                    onRemove = onRemoveQueueItem,
                    onClear = onClearQueue,
                )
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun PlayerMainPane(
    song: Song,
    position: PlaybackPosition,
    durationMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    lyrics: List<LyricLine>?,
    canPlayPause: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    canSeek: Boolean,
    onSeekFraction: (Float) -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentLyricText = remember(lyrics, position.positionMs) {
        val lines = lyrics
        if (!lines.isNullOrEmpty()) {
            val activeIndex = lines.indexOfLast { it.timeMs <= position.positionMs }
            if (activeIndex >= 0) {
                val text = lines[activeIndex].text.trim()
                if (text.isNotBlank() && !lines[activeIndex].isGap) text else null
            } else null
        } else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Playing from Mixes de canciones",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
            color = LocalPlayerTint.current.copy(alpha = 0.60f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        TrackTitleAndCredits(song = song)

        if (!currentLyricText.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleLyrics() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentLyricText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = Color.White.copy(alpha = 0.95f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Ver letras",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LosslessLabel()

        Spacer(Modifier.height(20.dp))

        PlayerScrubber(
            shown = {
                if (durationMs > 0L) {
                    (position.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            },
            durationMs = durationMs,
            enabled = canSeek,
            onScrub = onSeekFraction,
            onScrubFinished = {},
        )

        Spacer(Modifier.height(28.dp))

        TransportRow(
            isPlaying = isPlaying,
            isLoading = isLoading,
            previousEnabled = canPrevious,
            nextEnabled = canNext,
            playPauseEnabled = canPlayPause,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )
    }
}

@Composable
private fun PlayerEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No track currently playing",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.60f),
        )
    }
}

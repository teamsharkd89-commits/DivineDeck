package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import com.music.bitchord.download.DownloadState
import com.music.bitchord.ui.components.SongDownloadActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.CreditSegment
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.creditSegments
import com.music.bitchord.data.settings.MixBlend
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.icons.BitChordIcons
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

val LocalPlayerTint = compositionLocalOf { Color.White }

object RepeatModes {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

/** Formats a timestamp in milliseconds as mm:ss. */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return "%d:%02d".format(Locale.ROOT, minutes, seconds)
}

/**
 * Apple Music-style hairline scrubber with elapsed and remaining duration labels.
 */
@Composable
fun PlayerScrubber(
    shown: () -> Float,
    durationMs: Long,
    loading: Boolean = false,
    mixing: Boolean = false,
    enabled: Boolean = true,
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    mixBlend: MixBlend? = null,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
    centerLabel: @Composable BoxScope.() -> Unit = {},
) {
    val progress = shown()
    val mixPulse = rememberMixPulse({ mixBlend }, enabled = mixBlend != null)
    Column(modifier.fillMaxWidth()) {
        ThinSlider(
            value = progress,
            onValueChange = if (enabled) onScrub else { _ -> },
            onValueChangeFinished = if (enabled) onScrubFinished else null,
            loading = loading,
            mixing = mixing,
            mixPulse = mixPulse,
            transitionWindow = transitionWindow,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-9).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatTime((progress * durationMs).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalPlayerTint.current.copy(alpha = 0.55f),
                )
                Text(
                    text = "-" + formatTime((durationMs - (progress * durationMs).toLong()).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalPlayerTint.current.copy(alpha = 0.55f),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - mixPulse.cover },
                contentAlignment = Alignment.Center,
            ) {
                centerLabel()
            }
        }
    }
}

private val PLAYER_SKIP_ICON_SIZE = 48.dp
private val PLAYER_SKIP_TOUCH_SIZE = 56.dp
private const val PLAYER_SKIP_HEIGHT_SCALE = 0.90f

/**
 * Transport row: Skip Previous, Play/Pause, Skip Next.
 */
@Composable
fun TransportRow(
    isPlaying: Boolean,
    isLoading: Boolean,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    playPauseEnabled: Boolean = true,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val playSize = if (compact) 58.dp else 74.dp
    val playTouch = if (compact) 76.dp else 92.dp
    val skipSize = if (compact) 38.dp else PLAYER_SKIP_ICON_SIZE
    val skipTouch = if (compact) 48.dp else PLAYER_SKIP_TOUCH_SIZE

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            size = skipSize,
            touchSize = skipTouch,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            onClick = onPrevious,
            enabled = previousEnabled,
            haptic = Haptic.SkipPrevious,
        )

        if (isLoading && !isPlaying) {
            Box(
                modifier = Modifier
                    .size(playTouch)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = playPauseEnabled,
                        onClick = onPlayPause
                    ),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(if (compact) 30.dp else 38.dp),
                )
            }
        } else {
            TransportGlyph(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                size = playSize,
                touchSize = playTouch,
                onClick = onPlayPause,
                enabled = playPauseEnabled,
                haptic = if (isPlaying) Haptic.Pause else Haptic.Resume,
            )
        }

        TransportGlyph(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            size = skipSize,
            touchSize = skipTouch,
            heightScale = PLAYER_SKIP_HEIGHT_SCALE,
            onClick = onNext,
            enabled = nextEnabled,
            haptic = Haptic.SkipNext,
        )
    }
}

@Composable
private fun TransportGlyph(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    touchSize: Dp = size,
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: Haptic = Haptic.Tap,
    heightScale: Float = 1f,
) {
    val haptics = rememberHaptics()
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.3f,
        label = "transportAlpha",
    )
    Box(
        modifier = Modifier
            .size(touchSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.play(haptic)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier
                .size(size)
                .then(if (heightScale != 1f) Modifier.graphicsLayer { scaleY = heightScale } else Modifier),
        )
    }
}

val BOTTOM_ACTION_SIZE = 44.dp
private const val PILL_SLIDE_MS = 220

/**
 * BitChord capsule action row: Lyrics toggle, Playback modes (Shuffle, Repeat), Like button.
 */
@Composable
fun PlayerActionRow(
    lyricsOpen: Boolean,
    queueOpen: Boolean = false,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = RepeatModes.OFF,
    likeStatus: LikeStatus = LikeStatus.INDIFFERENT,
    currentSong: Song? = null,
    downloadState: DownloadState? = null,
    isDownloaded: Boolean = false,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onToggleLike: () -> Unit = {},
    onDownloadSong: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Lyrics toggle glyph
        BottomGlyph(
            icon = BitChordIcons.LyricsQuote,
            contentDescription = if (lyricsOpen) "Close lyrics" else "Open lyrics",
            onClick = onToggleLyrics,
            highlighted = lyricsOpen,
        )

        // Center pill for playback modes (Shuffle & Repeat)
        Pill {
            PillSegment(
                icon = Icons.Rounded.Shuffle,
                contentDescription = if (shuffleEnabled) "Shuffle on" else "Shuffle off",
                onClick = onToggleShuffle,
                highlighted = shuffleEnabled,
                haptic = if (shuffleEnabled) Haptic.ToggleOff else Haptic.ToggleOn,
            )
            PillDivider()
            PillSegment(
                icon = if (repeatMode == RepeatModes.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = when (repeatMode) {
                    RepeatModes.ONE -> "Repeat one"
                    RepeatModes.ALL -> "Repeat all"
                    else -> "Repeat off"
                },
                onClick = onCycleRepeat,
                highlighted = repeatMode != RepeatModes.OFF,
                haptic = if (repeatMode != RepeatModes.OFF) Haptic.ToggleOn else Haptic.ToggleOff,
            )
        }

        // Right actions: Download + Like / Favorite
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (currentSong != null && onDownloadSong != null) {
                SongDownloadActionButton(
                    song = currentSong,
                    downloadState = downloadState,
                    isDownloaded = isDownloaded || !currentSong.localUri.isNullOrBlank(),
                    onDownload = onDownloadSong,
                    onCancel = { onCancelDownload?.invoke(it) },
                    onRetry = { onRetryDownload?.invoke(it) ?: onDownloadSong(it) },
                    onDelete = { onDeleteDownload?.invoke(it) },
                )
            }

            val isLiked = likeStatus.isLiked || likeStatus == LikeStatus.LIKE
            BottomGlyph(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                onClick = onToggleLike,
                highlighted = isLiked,
                haptic = Haptic.Tap,
            )

            BottomGlyph(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = if (queueOpen) "Cerrar cola" else "Abrir cola de reproducción",
                onClick = onToggleQueue,
                highlighted = queueOpen,
                haptic = Haptic.Tap,
            )
        }
    }
}

@Composable
fun Pill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(LocalPlayerTint.current.copy(alpha = 0.12f))
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(LocalPlayerTint.current.copy(alpha = 0.20f)),
    )
}

@Composable
fun PillSegment(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    highlighted: Boolean = false,
    iconSize: Dp = 22.dp,
    haptic: Haptic = Haptic.Tap,
) {
    val haptics = rememberHaptics()
    val tint = LocalPlayerTint.current.copy(alpha = if (highlighted) 1f else 0.65f)
    Box(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
fun BottomGlyph(
    icon: ImageVector?,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    haptic: Haptic = Haptic.Tap,
    label: String? = null,
) {
    val haptics = rememberHaptics()
    Box(
        modifier = modifier
            .size(BOTTOM_ACTION_SIZE)
            .clip(CircleShape)
            .background(
                if (highlighted) LocalPlayerTint.current.copy(alpha = 0.20f) else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.play(haptic)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        val tint = LocalPlayerTint.current.copy(alpha = if (highlighted) 1f else 0.75f)
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        } else if (label != null) {
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private const val MARQUEE_DP_PER_SEC = 28f
private val MARQUEE_GAP = 32.dp

/**
 * Text that rolls smoothly when wider than its container.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayMillis: Long = 1200L,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    if (!enabled) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val maxWidthPx = constraints.maxWidth
        val layout = remember(text, style, maxWidthPx) {
            textMeasurer.measure(text = text, style = style, maxLines = 1, softWrap = false)
        }
        val overflowing = layout.size.width > maxWidthPx

        if (!overflowing) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
            )
        } else {
            val travelPx = layout.size.width + with(density) { MARQUEE_GAP.roundToPx() }
            val offsetX = remember { Animatable(0f) }

            LaunchedEffect(text, travelPx, startDelayMillis) {
                offsetX.snapTo(0f)
                if (travelPx <= 0) return@LaunchedEffect
                val pxPerMs = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() } / 1000f
                val scrollMs = (travelPx / pxPerMs).roundToInt().coerceAtLeast(800)
                delay(startDelayMillis)
                while (true) {
                    offsetX.animateTo(
                        targetValue = -travelPx.toFloat(),
                        animationSpec = tween(durationMillis = scrollMs, easing = LinearEasing),
                    )
                    offsetX.snapTo(0f)
                    delay(1600L)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationX = offsetX.value },
            ) {
                Row {
                    Text(
                        text = text,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(MARQUEE_GAP))
                    Text(
                        text = text,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/**
 * Renders song title and artist credits with Apple Music / BitChord typography hierarchy.
 */
@Composable
fun TrackTitleAndCredits(
    song: Song,
    modifier: Modifier = Modifier,
    onArtistClick: ((ArtistCredit) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MarqueeText(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    offset = Offset(0f, 2f),
                    blurRadius = 6f,
                ),
            ),
            color = Color.White,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        val segments = remember(song.artists, song.artist) {
            song.creditSegments()
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artistText = if (segments.isNotEmpty()) {
                segments.joinToString("") { it.text }
            } else {
                song.artist
            }

            MarqueeText(
                text = artistText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f,
                    ),
                ),
                color = LocalPlayerTint.current.copy(alpha = 0.78f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Hi-Res Lossless / audio quality badge.
 */
@Composable
fun LosslessLabel(
    text: String = "LOSSLESS",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LocalPlayerTint.current.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            ),
            color = LocalPlayerTint.current.copy(alpha = 0.85f),
        )
    }
}

package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.ui.haptics.rememberHaptics

/**
 * Horizontal gutter shared by every page header, shelf title, carousel and
 * song row so the whole app sits on one left edge.
 */
val PAGE_GUTTER = 20.dp

/** Standard square card width in a horizontal shelf carousel. */
val SHELF_CARD_WIDTH = 166.dp

/**
 * Width of a featured hero card at the head of Home — wide enough to read as a
 * banner on a phone with the next card peeking in from the right, and capped so
 * a tablet shows two or three side by side rather than one billboard.
 */
@Composable
fun heroCardWidth(): Dp = BoxWithConstraintsScopeWidth { maxWidth ->
    (maxWidth - PAGE_GUTTER * 2 - 28.dp).coerceIn(260.dp, 340.dp)
}

/** Width of one 4-track column in the Quick Picks / Recently Played shelf. */
@Composable
fun trackColumnWidth(): Dp = BoxWithConstraintsScopeWidth { maxWidth ->
    (maxWidth - PAGE_GUTTER * 2 - 24.dp).coerceIn(260.dp, 360.dp)
}

@Composable
private fun BoxWithConstraintsScopeWidth(compute: (Dp) -> Dp): Dp {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return compute(configuration.screenWidthDp.dp)
}

/** Aspect ratio of a hero card — taller than 16:9 so the artwork breathes behind the caption. */
const val HERO_CARD_RATIO = 1.28f

/** The red used on the active row of Search, matched to its three-bar graphic. */
val PlayingAccent = Color(0xFFFA2D48)

val LocalPullToRefreshEnabled = staticCompositionLocalOf { true }

/**
 * Three vertical bars that rise and fall out of phase while [playing], and
 * settle to their resting heights when paused.
 */
@Composable
fun SearchPlayingBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val transition = rememberInfiniteTransition(label = "searchPlayingBars")
    val first by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar1",
    )
    val second by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar2",
    )
    val third by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bar3",
    )
    val levels = if (playing) floatArrayOf(first, second, third) else floatArrayOf(0.35f, 0.6f, 0.42f)

    Canvas(modifier = modifier.size(16.dp)) {
        val barWidth = size.width * 0.2f
        val gap = (size.width - barWidth * 3f) / 2f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        levels.forEachIndexed { i, fraction ->
            val h = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * Hairline border around every album cover and thumbnail so dark artwork doesn't
 * bleed into a dark surface and white sleeves keep their edge on a light one.
 */
@Composable
fun Modifier.thumbnailBorder(shape: Shape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)
    return border(width = 0.5.dp, color = color, shape = shape)
}

/**
 * Clickable modifier with optional long-press support and haptic feedback.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.longPressMenuClickable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val haptics = rememberHaptics()
    combinedClickable(
        onClick = onClick,
        onLongClick = if (onLongClick != null) {
            {
                haptics.play(com.music.bitchord.ui.haptics.Haptic.LongPress)
                onLongClick()
            }
        } else {
            null
        },
    )
}

fun Modifier.contextClick(onContextClick: (() -> Unit)?): Modifier = this

/**
 * Three horizontal dots at the end of a track row.
 */
@Composable
fun RowMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .longPressMenuClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val r = 1.6.dp.toPx()
            val y = size.height / 2f
            drawCircle(color = tint, radius = r, center = Offset(size.width * 0.2f, y))
            drawCircle(color = tint, radius = r, center = Offset(size.width * 0.5f, y))
            drawCircle(color = tint, radius = r, center = Offset(size.width * 0.8f, y))
        }
    }
}

@Composable
fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun MessageState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    fillHeight: Boolean = true,
) {
    Box(
        modifier = if (fillHeight) {
            Modifier.fillMaxWidth().fillMaxHeight(0.75f)
        } else {
            Modifier.fillMaxWidth().padding(vertical = 48.dp)
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---- Pull to refresh --------------------------------------------------------

private val PULL_THRESHOLD = 72.dp
private const val PULL_DRAG_MULTIPLIER = 0.5f

/**
 * Shared state between [PullToRefresh] (which owns the gesture and content offset)
 * and the top bar's bottom edge.
 */
@Stable
class PullToRefreshState internal constructor(internal val thresholdPx: Float) {
    var pullPx by mutableFloatStateOf(0f)
        internal set

    var refreshing: Boolean = false
        internal set

    val progress: Float
        get() = if (thresholdPx > 0f) (pullPx / thresholdPx).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberPullToRefreshState(): PullToRefreshState {
    val thresholdPx = with(LocalDensity.current) { PULL_THRESHOLD.toPx() }
    return remember(thresholdPx) { PullToRefreshState(thresholdPx) }
}

/**
 * Over-scroll pull-to-refresh wrapper around a scrollable feed.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    scrollableState: ScrollableState,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    if (!LocalPullToRefreshEnabled.current) {
        Box(modifier = modifier) { content() }
        return
    }
    state.refreshing = refreshing

    LaunchedEffect(refreshing) {
        if (!refreshing) state.pullPx = 0f
    }

    val maxPullPx = state.thresholdPx * 1.6f
    val connection = remember(refreshing, scrollableState, state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0f && state.pullPx > 0f) {
                    val consumed = (available.y * PULL_DRAG_MULTIPLIER).coerceAtLeast(-state.pullPx)
                    state.pullPx += consumed
                    return Offset(0f, consumed / PULL_DRAG_MULTIPLIER)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (refreshing || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0f && !scrollableState.canScrollBackward) {
                    state.pullPx = (state.pullPx + available.y * PULL_DRAG_MULTIPLIER)
                        .coerceAtMost(maxPullPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!refreshing && state.pullPx >= state.thresholdPx) {
                    state.pullPx = 0f
                    onRefresh()
                } else {
                    state.pullPx = 0f
                }
                return Velocity.Zero
            }
        }
    }

    val visualOffset by animateFloatAsState(
        targetValue = state.pullPx,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pullOffset",
    )

    Box(
        modifier = modifier
            .nestedScroll(connection)
            .graphicsLayer { translationY = visualOffset },
    ) {
        content()
    }
}

// ---- Library Grid & PillTextField (BitChord 1.8.1 original) -----------------

/** How many cards sit across a library grid row, and how wide each lands. */
data class LibraryGridSpec(val columns: Int, val cardWidth: Dp)

private val LIBRARY_GRID_MIN_CARD_WIDTH = 140.dp

/** Gap between cards in a library grid, in both directions. */
val LIBRARY_GRID_SPACING = 12.dp

private const val LIBRARY_GRID_MIN_COLUMNS = 2
private const val LIBRARY_GRID_MAX_COLUMNS = 5

/**
 * How a Library shelf's full "Show all" page lays out as a grid, in
 * [available] dp of row — see `LibraryGridPage`.
 */
fun libraryGrid(available: Dp): LibraryGridSpec {
    val raw = ((available + LIBRARY_GRID_SPACING) / (LIBRARY_GRID_MIN_CARD_WIDTH + LIBRARY_GRID_SPACING))
        .toInt()
    val columns = raw.coerceIn(LIBRARY_GRID_MIN_COLUMNS, LIBRARY_GRID_MAX_COLUMNS)
    val cardWidth = (available - LIBRARY_GRID_SPACING * (columns - 1)) / columns
    return LibraryGridSpec(columns, cardWidth)
}

/**
 * The narrow, pill-shaped text field BitChord 1.8.1 uses for playlist creation
 * and renaming forms.
 */
@Composable
fun PillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(container, RoundedCornerShape(11.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}


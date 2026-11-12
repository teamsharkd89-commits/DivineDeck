package com.music.bitchord.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared sweep position for every [ShimmerBox] in the composition.
 */
private val LocalShimmerProgress = compositionLocalOf< Float? > { null }

@Composable
private fun WithShimmer(content: @Composable () -> Unit) {
    if (LocalShimmerProgress.current != null) {
        content()
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    CompositionLocalProvider(LocalShimmerProgress provides progress, content = content)
}

/**
 * Placeholder block with a soft diagonal highlight sweeping across it.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    WithShimmer {
        val progress = LocalShimmerProgress.current ?: 0f
        val onSurface = MaterialTheme.colorScheme.onSurface
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val base = onSurface.copy(alpha = if (dark) 0.07f else 0.06f)
        val highlight = onSurface.copy(alpha = if (dark) 0.15f else 0.12f)

        Box(
            modifier = modifier
                .clip(shape)
                .drawWithCache {
                    val band = size.width.coerceAtLeast(240f) * 1.2f
                    val travel = size.width + band * 2f
                    onDrawBehind {
                        val x = -band + travel * progress
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(base, highlight, base),
                                start = Offset(x, 0f),
                                end = Offset(x + band, size.height * 0.35f),
                            ),
                        )
                    }
                },
        )
    }
}

/**
 * Stand-in for a shelf feed (Home, Explore, Library) while its first response is in flight.
 */
fun LazyListScope.feedSkeleton(
    leadingTrackShelf: Boolean = false,
    leadingHeroShelf: Boolean = false,
    shelfCount: Int = 3,
) {
    if (leadingTrackShelf) {
        item(key = "skeleton_tracks") {
            WithShimmer { TrackColumnShelfSkeleton() }
        }
    }
    if (leadingHeroShelf) {
        item(key = "skeleton_hero") {
            WithShimmer { HeroShelfSkeleton() }
        }
    }
    items(count = shelfCount, key = { "skeleton_shelf_$it" }) { index ->
        WithShimmer {
            ShelfSkeleton(titleWidth = SHELF_TITLE_WIDTHS[index % SHELF_TITLE_WIDTHS.size])
        }
    }
}

/**
 * Stand-in for the next page of shelves being appended to the bottom of Home.
 */
fun LazyListScope.feedMoreSkeleton() {
    item(key = "skeleton_more") {
        WithShimmer {
            ShelfSkeleton(titleWidth = 148.dp)
        }
    }
}

/**
 * Stand-in for the "Recently played" shelf at the head of Home while it is loading.
 */
fun LazyListScope.recentlyPlayedSkeleton() {
    item(key = "skeleton_recently_played") {
        WithShimmer {
            ShelfSkeleton(titleWidth = 156.dp)
        }
    }
}

/**
 * Stand-in for the Library page while shelves and tracks load.
 */
fun LazyListScope.librarySkeleton() {
    item(key = "skeleton_library_shelf") {
        WithShimmer {
            ShelfSkeleton(titleWidth = 148.dp)
        }
    }
    songListSkeleton(count = 6)
}

/**
 * Stand-in for a vertical list of song rows (Search results, Detail tracks, History).
 */
fun LazyListScope.songListSkeleton(count: Int = 8) {
    items(count = count, key = { "skeleton_song_$it" }) { index ->
        WithShimmer {
            val (titleFraction, subtitleFraction) = ROW_WIDTHS[index % ROW_WIDTHS.size]
            SongRowSkeleton(
                titleFraction = titleFraction,
                subtitleFraction = subtitleFraction,
            )
        }
    }
}

@Composable
fun SongRowSkeleton(
    titleFraction: Float = 0.58f,
    subtitleFraction: Float = 0.38f,
    horizontalPadding: Dp = PAGE_GUTTER,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(6.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(titleFraction)
                    .height(14.dp),
            )
            Spacer(Modifier.height(7.dp))
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(subtitleFraction)
                    .height(12.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        ShimmerBox(
            modifier = Modifier.size(width = 18.dp, height = 6.dp),
            shape = CircleShape,
        )
    }
}

@Composable
private fun HeroShelfSkeleton() {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Column(modifier = Modifier.padding(horizontal = PAGE_GUTTER)) {
            ShimmerBox(modifier = Modifier.size(width = 88.dp, height = 11.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(modifier = Modifier.size(width = 172.dp, height = 22.dp))
        }
        Spacer(Modifier.height(12.dp))
        val cardWidth = heroCardWidth()
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false,
        ) {
            items(2) {
                ShimmerBox(
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(HERO_CARD_RATIO),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ShelfSkeleton(titleWidth: Dp) {
    Column(modifier = Modifier.padding(top = 28.dp)) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = PAGE_GUTTER)
                .size(width = titleWidth, height = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false,
        ) {
            items(4) { index ->
                val (titleFraction, subtitleFraction) = CARD_WIDTHS[index % CARD_WIDTHS.size]
                Column(modifier = Modifier.width(SHELF_CARD_WIDTH)) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(SHELF_CARD_WIDTH),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(titleFraction)
                            .height(13.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(subtitleFraction)
                            .height(11.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackColumnShelfSkeleton() {
    val columnWidth = trackColumnWidth()
    Column(modifier = Modifier.padding(top = 20.dp)) {
        ShimmerBox(
            modifier = Modifier
                .padding(horizontal = PAGE_GUTTER)
                .size(width = 128.dp, height = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
        ) {
            items(2) { col ->
                Column(modifier = Modifier.width(columnWidth)) {
                    repeat(4) { row ->
                        val (t, s) = ROW_WIDTHS[(col * 4 + row) % ROW_WIDTHS.size]
                        SongRowSkeleton(
                            titleFraction = t,
                            subtitleFraction = s,
                            horizontalPadding = 0.dp,
                        )
                    }
                }
            }
        }
    }
}

private val SHELF_TITLE_WIDTHS = listOf(136.dp, 168.dp, 118.dp, 152.dp)

private val CARD_WIDTHS = listOf(
    0.82f to 0.54f,
    0.68f to 0.46f,
    0.90f to 0.62f,
    0.74f to 0.50f,
)

private val ROW_WIDTHS = listOf(
    0.64f to 0.42f,
    0.48f to 0.34f,
    0.72f to 0.38f,
    0.54f to 0.46f,
    0.60f to 0.30f,
    0.44f to 0.40f,
    0.68f to 0.36f,
    0.52f to 0.44f,
)

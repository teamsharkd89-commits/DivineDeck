package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt

/** Which pane the landscape player's right column is displaying. */
enum class PlayerPane { Main, Lyrics, Queue }

val LANDSCAPE_ART_CORNER = 14.dp
private val LandscapeArtShape = RoundedCornerShape(LANDSCAPE_ART_CORNER)
private val LANDSCAPE_PLAYER_MAX_WIDTH = 1120.dp
private val PLAYER_GUTTER = 28.dp

/**
 * Tablet / landscape dual-column player layout adapted from BitChord 1.8.1 (GPLv3).
 *
 * Left column: Large square album artwork with soft ambient shadow and action buttons.
 * Right column: Swappable pane (Main controls, Synced lyrics, or Queue).
 */
@Composable
fun LandscapePlayerLayout(
    pane: PlayerPane,
    background: @Composable (Modifier) -> Unit,
    artwork: @Composable (Modifier) -> Unit,
    actions: @Composable () -> Unit,
    mainPane: @Composable () -> Unit,
    lyricsPane: @Composable () -> Unit,
    queuePane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Dynamic ambient background (mesh gradient or blur)
        background(Modifier.fillMaxSize())

        // Top drag handle indicator matching BitChord 1.8.1
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(width = 38.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.35f))
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = LANDSCAPE_PLAYER_MAX_WIDTH)
                    .fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // LEFT COLUMN: Artwork + Action Capsule Row
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val artSize = minOf(maxWidth, maxHeight).coerceAtMost(360.dp)
                        artwork(Modifier.size(artSize))
                    }

                    Spacer(Modifier.height(16.dp))

                    actions()
                }

                // RIGHT COLUMN: Animated switcher between Main, Lyrics, and Queue
                AnimatedContent(
                    targetState = pane,
                    transitionSpec = {
                        fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(160))
                    },
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight(),
                    label = "landscapeRightPane",
                ) { currentPane ->
                    when (currentPane) {
                        PlayerPane.Main -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            mainPane()
                        }
                        PlayerPane.Lyrics -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            lyricsPane()
                        }
                        PlayerPane.Queue -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            queuePane()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Landscape album cover card with rounded corners and drop shadow.
 */
@Composable
fun LandscapeArtworkCard(
    song: Song?,
    modifier: Modifier = Modifier,
) {
    val highResCover = remember(song?.artworkUrl) {
        song?.artworkUrl.artworkAt(PLAYER_ART_PX) ?: song?.artworkUrl
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = LandscapeArtShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.6f),
            )
            .clip(LandscapeArtShape)
            .background(Color(0xFF1E1E24))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = LandscapeArtShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (highResCover.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = "No Artwork",
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(72.dp),
            )
        } else {
            AsyncImage(
                model = highResCover,
                contentDescription = "Cover for ${song?.title ?: ""}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

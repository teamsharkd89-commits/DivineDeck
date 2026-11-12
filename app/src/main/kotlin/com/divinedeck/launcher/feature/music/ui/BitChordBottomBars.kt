package com.divinedeck.launcher.feature.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * 4 Pestañas principales oficiales de BitChord 1.8.1:
 * - INICIO (Escuchar ahora / Home)
 * - EXPLORAR (Explore / Moods & Genres)
 * - BIBLIOTECA (Library)
 * - BUSCAR (Search)
 */
data class BitChordNavTab(
    val destination: MusicCenterDestination,
    val label: String,
    val icon: ImageVector,
)

private val BITCHORD_TABS = listOf(
    BitChordNavTab(MusicCenterDestination.HOME, "Inicio", BitChordIcons.Home),
    BitChordNavTab(MusicCenterDestination.EXPLORE, "Explorar", BitChordIcons.TabExplore),
    BitChordNavTab(MusicCenterDestination.LIBRARY, "Biblioteca", BitChordIcons.TabLibrary),
    BitChordNavTab(MusicCenterDestination.SEARCH, "Buscar", BitChordIcons.TabSearch),
)

private val TabSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 320f)

/**
 * Barra flotante inferior de 4 pestañas idéntica a BitChord 1.8.1.
 * Diseñada en cápsula oscura con borde de cristal (Liquid Glass) e indicador elástico animado.
 */
@Composable
fun BitChordBottomFloatingBar(
    selectedDestination: MusicCenterDestination,
    onDestinationSelected: (MusicCenterDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(percent = 50)
    val density = LocalDensity.current

    val activeIndex = remember(selectedDestination) {
        BITCHORD_TABS.indexOfFirst { it.destination == selectedDestination }.coerceAtLeast(0)
    }

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val tabCount = BITCHORD_TABS.size
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) {
        rowSize.width.toFloat() / tabCount.toFloat()
    } else 0f

    val targetOffsetPx = activeIndex * tabWidthPx
    val animatedPillOffset by animateFloatAsState(
        targetValue = targetOffsetPx,
        animationSpec = TabSpring,
        label = "tabPillOffset",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(pillShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E222D).copy(alpha = 0.96f),
                        Color(0xFF12141C).copy(alpha = 0.98f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.14f),
                shape = pillShape,
            )
            .padding(4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Indicador de selección animado (cápsula más clara detrás de la pestaña activa)
        if (tabWidthPx > 0f) {
            val pillWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = animatedPillOffset.toInt(), y = 0) }
                    .width(pillWidthDp)
                    .fillMaxHeight()
                    .clip(pillShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rowSize = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BITCHORD_TABS.forEachIndexed { index, tab ->
                val isSelected = index == activeIndex
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = TabSpring,
                    label = "tabScale",
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.60f),
                    animationSpec = tween(180),
                    label = "tabTint",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(pillShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onDestinationSelected(tab.destination)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier
                            .size(23.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * MiniPlayer flotante original de BitChord 1.8.1.
 * Se suspende justo encima de la barra de pestañas cuando hay una pista en reproducción.
 * Al tocar el cuerpo se abre el LandscapePlayer a pantalla completa.
 */
@Composable
fun BitChordFloatingMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(18.dp)
    val coverUrl = remember(song.thumbnailUrl) {
        song.thumbnailUrl.artworkAt(ROW_ART_PX) ?: song.thumbnailUrl
    }

    val progressFraction = if (durationMs > 0L) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val lyricLines by produceState<List<LyricLine>?>(
        initialValue = song.videoId.let { LyricsRepository.getMemoryCachedLines(it) },
        key1 = song.videoId,
    ) {
        val id = song.videoId
        if (id.isBlank()) {
            value = null
            return@produceState
        }
        val cached = LyricsRepository.getMemoryCachedLines(id)
        if (cached != null) {
            value = cached
        } else {
            try {
                LyricsRepository.resolveForSong(song, null)
                value = LyricsRepository.getMemoryCachedLines(id)
            } catch (_: Throwable) {
                value = null
            }
        }
    }

    val currentLyricText = remember(lyricLines, currentPosMs) {
        val lines = lyricLines
        if (!lines.isNullOrEmpty()) {
            val activeIndex = lines.indexOfLast { it.timeMs <= currentPosMs }
            if (activeIndex >= 0) {
                val text = lines[activeIndex].text.trim()
                if (text.isNotBlank() && !lines[activeIndex].isGap) text else null
            } else null
        } else null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .shadow(16.dp, cardShape, ambientColor = Color.Black.copy(alpha = 0.6f))
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E2330).copy(alpha = 0.98f),
                        Color(0xFF10141E).copy(alpha = 0.98f),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isPlaying) Color(0xFF00E5FF).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.15f),
                shape = cardShape,
            )
            .clickable { onExpand() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Carátula de la canción con borde suave
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1B202B))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Metadatos: Título y Letra en vivo / Artista
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = song.title.ifBlank { "Sin título" },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                if (!currentLyricText.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = currentLyricText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = Color(0xFF00E5FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = song.artist.ifBlank { "BitChord Music" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                        ),
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Controles rápidos táctiles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Play / Pause
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlaying) Color(0xFF00E5FF).copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
                        )
                        .border(
                            1.dp,
                            if (isPlaying) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.22f),
                            CircleShape,
                        )
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF00E5FF),
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = if (isPlaying) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Siguiente
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { onNext() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }

        // Barra milimétrica de progreso en la base del MiniPlayer
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progressFraction)
                .height(2.dp)
                .background(Color(0xFF00E5FF))
        )
    }
}

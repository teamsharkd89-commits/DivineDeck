package com.divinedeck.launcher.core.designsystem.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.music.domain.model.MediaPlaybackState
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.divinedeck.launcher.feature.music.ui.BitChordMediaModelAdapter
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Mini Reproductor Automotriz nativo para el Cockpit de DIVINE DECK (Fase 06.6).
 *
 * Se ubica en la ranura superior del Cockpit como widget protagonista:
 * - Carátula en alta resolución con borde Liquid Glass y sombreado ambiental.
 * - Título y artista con contraste optimizado para visión periférica al volante.
 * - Barra de progreso en vivo sincronizada con el playhead.
 * - Controles ergonómicos de reproducción (Play/Pause, Siguiente).
 * - Conectado exclusivamente al [DivineDeckPlaybackService] y al ExoPlayer único.
 * - Al pulsar el cuerpo del reproductor abre Music Center directamente en Now Playing.
 */
@Composable
fun CockpitBitChordMiniPlayer(
    mediaRepository: MediaControllerRepository,
    onClick: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette
    val currentTrack by mediaRepository.currentTrack.collectAsState()
    val playbackState by mediaRepository.playbackState.collectAsState()

    val song = remember(currentTrack) {
        BitChordMediaModelAdapter.toSong(currentTrack)
    }

    val durationMs = currentTrack.durationMs ?: 0L
    val currentPosMs = currentTrack.currentPositionMs
    val progressFraction = if (durationMs > 0L) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val isPlaying = playbackState.isPlaying
    val isBuffering = !isPlaying && playbackState.isBuffering

    val lyricLines by produceState<List<LyricLine>?>(
        initialValue = song?.videoId?.let { LyricsRepository.getMemoryCachedLines(it) },
        key1 = song?.videoId
    ) {
        val id = song?.videoId
        if (id.isNullOrBlank()) {
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

    val activeIndex = remember(lyricLines, currentPosMs) {
        val lines = lyricLines
        if (!lines.isNullOrEmpty()) {
            lines.indexOfLast { it.timeMs <= currentPosMs }
        } else -1
    }

    val currentLyricText = remember(lyricLines, activeIndex) {
        val lines = lyricLines
        if (!lines.isNullOrEmpty() && activeIndex >= 0 && activeIndex < lines.size) {
            val text = lines[activeIndex].text.trim()
            if (text.isNotBlank() && !lines[activeIndex].isGap) text else null
        } else null
    }

    val nextLyricText = remember(lyricLines, activeIndex) {
        val lines = lyricLines
        if (!lines.isNullOrEmpty() && activeIndex >= 0 && activeIndex + 1 < lines.size) {
            val text = lines[activeIndex + 1].text.trim()
            if (text.isNotBlank() && !lines[activeIndex + 1].isGap) text else null
        } else null
    }

    val cardShape = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .clip(cardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surface,
                        palette.background
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (isPlaying) palette.accent.copy(alpha = 0.35f) else palette.borderSubtle,
                shape = cardShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // CABECERA SUPERIOR: Indicador de estado + Etiqueta de audio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isPlaying -> palette.statusPositive
                                    isBuffering -> palette.accentSecondary
                                    else -> palette.accent
                                }
                            )
                    )
                    Text(
                        text = "BITCHORD AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.3.sp,
                            fontSize = 9.sp
                        ),
                        color = if (isPlaying) palette.statusPositive else palette.textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (song?.localUri != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.accent.copy(alpha = 0.15f))
                                .border(0.8.dp, palette.accent.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = palette.accent
                            )
                        }
                    }

                    if (onConfigure != null) {
                        IconButton(
                            onClick = onConfigure,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configurar ranura",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // CONTENIDO DEL WIDGET COCKPIT
            if (song != null) {
                // 1. FILA SUPERIOR: Carátula, Título, Artista y Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val coverArtUrl = remember(song.thumbnailUrl) {
                        song.thumbnailUrl.artworkAt(ROW_ART_PX) ?: song.thumbnailUrl
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, RoundedCornerShape(10.dp), clip = false)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverArtUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = coverArtUrl,
                                contentDescription = song.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Título y Artista / Duración
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song.title.ifBlank { "Sin título" },
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            ),
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            text = "${song.artist.ifBlank { "BitChord" }} • ${formatDuration(currentPosMs)} / ${formatDuration(durationMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Botones de control táctiles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Play / Pause
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) palette.accent.copy(alpha = 0.22f) else palette.surface
                                )
                                .border(
                                    1.dp,
                                    if (isPlaying) palette.accent else palette.borderSubtle,
                                    CircleShape
                                )
                                .clickable { mediaRepository.togglePlayback() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.accent
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Siguiente
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(palette.surface)
                                .border(1.dp, palette.borderSubtle, CircleShape)
                                .clickable { mediaRepository.skipToNext() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Siguiente canción",
                                tint = palette.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. ÁREA DE LETRA
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (!currentLyricText.isNullOrBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(top = 2.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentLyricText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            lineHeight = 16.5.sp,
                                        ),
                                        color = palette.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!nextLyricText.isNullOrBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = nextLyricText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.5.sp,
                                            ),
                                            color = palette.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = palette.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Instrumental • ${song.artist.ifBlank { "BitChord Audio" }}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 3. BARRA DE PROGRESO INFERIOR
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = palette.accent,
                    trackColor = palette.borderSubtle
                )
            } else {
                // ESTADO INACTIVO (Sin pista activa en cola)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BitChord Music Center",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Toca para abrir catálogo y biblioteca",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(palette.accent.copy(alpha = 0.15f))
                            .border(1.dp, palette.accent.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Abrir reproductor",
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// =========================================================================
// COMPOSE PREVIEWS (10 DIVINE DECK THEMES)
// =========================================================================

private class PreviewMediaControllerRepository(
    title: String = "Neon Horizon",
    artist: String = "Synth Wave",
    durationMs: Long = 214000L,
    currentPositionMs: Long = 68000L,
    isPlaying: Boolean = true
) : MediaControllerRepository {
    override val currentTrack: StateFlow<TrackInfo> = MutableStateFlow(
        TrackInfo(
            mediaId = "preview_1",
            title = title,
            artist = artist,
            album = "Cyberpunk 2077",
            durationMs = durationMs,
            currentPositionMs = currentPositionMs,
            albumArtUri = null
        )
    )
    override val playbackState: StateFlow<MediaPlaybackState> = MutableStateFlow(
        MediaPlaybackState(
            connectionStatus = MediaServiceConnectionStatus.CONNECTED,
            status = if (isPlaying) PlaybackStatus.PLAYING else PlaybackStatus.PAUSED,
            isPlaying = isPlaying,
            isBuffering = false,
            playWhenReady = isPlaying
        )
    )
    override val currentQueue: StateFlow<List<Song>> = MutableStateFlow(emptyList())

    override fun connect() {}
    override fun disconnect() {}
    override fun play() {}
    override fun pause() {}
    override fun togglePlayback() {}
    override fun skipToNext() {}
    override fun skipToPrevious() {}
    override fun seekTo(positionMs: Long) {}
    override fun setVolume(percent: Int) {}
    override fun playQueue(songs: List<Song>, startIndex: Int) {}
    override fun skipToQueueItem(index: Int) {}
    override fun enqueueNext(song: Song) {}
    override fun enqueueLast(song: Song) {}
    override fun reorderQueue(fromIndex: Int, toIndex: Int) {}
    override fun removeFromQueue(index: Int) {}
    override fun clearUserQueue() {}
    override fun playLocalUri(uriString: String, title: String?, artist: String?, artworkUri: String?) {}
    override fun playLocalUri(uri: Uri, title: String?, artist: String?, artworkUri: String?) {}
    override fun playSong(song: Song) {}
    override fun playOnlineTrack(videoId: String, title: String, artist: String, thumbnailUrl: String?, durationMs: Long?) {}
    override fun playSampleAudio() {}
}

@Preview(name = "Obsidian", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerObsidian() {
    DivineDeckTheme(themePreset = ThemePreset.OBSIDIAN) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Arctic", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerArctic() {
    DivineDeckTheme(themePreset = ThemePreset.ARCTIC) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Monochrome", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerMonochrome() {
    DivineDeckTheme(themePreset = ThemePreset.MONOCHROME) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Bronze", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerBronze() {
    DivineDeckTheme(themePreset = ThemePreset.BRONZE) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Custom Emerald", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerCustom() {
    DivineDeckTheme(themePreset = ThemePreset.CUSTOM, customAccentHex = 0xFF34D399L) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Minimalista", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerMinimalista() {
    DivineDeckTheme(themePreset = ThemePreset.MINIMALISTA) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Rojo Sport", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerRojoSport() {
    DivineDeckTheme(themePreset = ThemePreset.ROJO_SPORT) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Espectro", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerEspectro() {
    DivineDeckTheme(themePreset = ThemePreset.ESPECTRO) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Vaporwave", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerVaporwave() {
    DivineDeckTheme(themePreset = ThemePreset.VAPORWAVE) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(name = "Acido Lima", group = "Themes", widthDp = 380, heightDp = 180)
@Composable
fun PreviewCockpitBitChordMiniPlayerAcidoLima() {
    DivineDeckTheme(themePreset = ThemePreset.ACIDO_LIMA) {
        CockpitBitChordMiniPlayer(
            mediaRepository = remember { PreviewMediaControllerRepository() },
            onClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

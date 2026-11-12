package com.music.bitchord.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.playback.PlaybackPosition
import kotlinx.coroutines.delay

/**
 * Rich synchronized lyrics panel adapted from BitChord 1.8.1 (GPLv3).
 *
 * Fully supports all 7 visual UI states required for Phase 06.5D:
 * 1. [LyricsUiState.Loading] — Indicador de carga automotriz con indicador de proveedor activo.
 * 2. [LyricsUiState.Synced] — Letras sincronizadas línea a línea y palabra a palabra, con badge de proveedor.
 * 3. [LyricsUiState.Plain] — Letras disponibles sin sincronización (desplazamiento manual, sin fingir sincronización).
 * 4. [LyricsUiState.NotFound] — Notificación limpia de letras no encontradas.
 * 5. [LyricsUiState.Error] — Notificación de error temporal con botón para reintentar.
 * 6. [LyricsUiState.EmbeddedLocal] — Letras leídas directamente de archivo local/sidecar (.lrc / MP4 / FLAC / WebM / ID3).
 * 7. [LyricsUiState.OfflineUnavailable] — Estado sin conexión a internet y sin letras guardadas localmente.
 *
 * Synchronized with the authoritative ExoPlayer / Media3 position with tap-to-seek support.
 */
@Composable
fun SyncedLyricsPanel(
    lines: List<LyricLine>? = null,
    uiState: LyricsUiState? = null,
    position: PlaybackPosition,
    isPlaying: Boolean,
    onSeekToLine: (Long) -> Unit,
    onRetryLyrics: (() -> Unit)? = null,
    onOpenLyricsSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No synchronized lyrics available",
    effectsEnabled: Boolean = true,
    effectStyle: LyricsEffectStyle = LyricsEffectStyle.PULSE,
    effectIntensity: Float = 1.0f,
    beatReaction: Boolean = true,
    lowPowerMode: Boolean = false,
    audioEnergy: Float = 0f,
    beatPulse: Float = 0f,
) {
    if (uiState != null) {
        when (uiState) {
            is LyricsUiState.Idle -> {
                LyricsCenteredMessage(
                    icon = Icons.Rounded.MusicNote,
                    title = "Sin reproducción activa",
                    subtitle = "Selecciona una canción para ver sus letras sincronizadas",
                    modifier = modifier,
                )
            }

            is LyricsUiState.Loading -> {
                LyricsLoadingState(
                    title = uiState.title,
                    providerLabel = uiState.activeProviderLabel,
                    modifier = modifier,
                )
            }

            is LyricsUiState.NotFound -> {
                LyricsCenteredMessage(
                    icon = Icons.Rounded.MusicNote,
                    title = "Letras no encontradas",
                    subtitle = "No hay letras disponibles para \"${uiState.title}\"",
                    modifier = modifier,
                )
            }

            is LyricsUiState.Error -> {
                LyricsErrorState(
                    message = uiState.message,
                    canRetry = uiState.canRetry,
                    onRetry = onRetryLyrics,
                    modifier = modifier,
                )
            }

            is LyricsUiState.OfflineUnavailable -> {
                LyricsCenteredMessage(
                    icon = Icons.Rounded.CloudOff,
                    title = "Modo sin conexión",
                    subtitle = "No hay letras guardadas localmente para esta pista",
                    tintColor = Color(0xFFFFB300),
                    modifier = modifier,
                )
            }

            is LyricsUiState.Plain -> {
                LyricsListView(
                    lines = uiState.lines,
                    position = position,
                    isSynced = false,
                    isWordSynced = false,
                    badgeLabel = "Letras sin sincronización (${uiState.source.label})",
                    onSeekToLine = onSeekToLine,
                    isPlaying = isPlaying,
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    modifier = modifier,
                    effectsEnabled = effectsEnabled,
                    effectStyle = effectStyle,
                    effectIntensity = effectIntensity,
                    beatReaction = beatReaction,
                    lowPowerMode = lowPowerMode,
                    audioEnergy = audioEnergy,
                    beatPulse = beatPulse,
                )
            }

            is LyricsUiState.Synced -> {
                LyricsListView(
                    lines = uiState.lines,
                    position = position,
                    isSynced = true,
                    isWordSynced = uiState.isWordSynced,
                    badgeLabel = uiState.source.label + if (uiState.isWordSynced) " • Sincronización por palabra" else "",
                    onSeekToLine = onSeekToLine,
                    isPlaying = isPlaying,
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    modifier = modifier,
                    effectsEnabled = effectsEnabled,
                    effectStyle = effectStyle,
                    effectIntensity = effectIntensity,
                    beatReaction = beatReaction,
                    lowPowerMode = lowPowerMode,
                    audioEnergy = audioEnergy,
                    beatPulse = beatPulse,
                )
            }

            is LyricsUiState.EmbeddedLocal -> {
                LyricsListView(
                    lines = uiState.lines,
                    position = position,
                    isSynced = uiState.isSynced,
                    isWordSynced = uiState.isWordSynced,
                    badgeLabel = "Letras locales (${uiState.containerOrFormat})",
                    onSeekToLine = onSeekToLine,
                    isPlaying = isPlaying,
                    onOpenLyricsSettings = onOpenLyricsSettings,
                    modifier = modifier,
                    effectsEnabled = effectsEnabled,
                    effectStyle = effectStyle,
                    effectIntensity = effectIntensity,
                    beatReaction = beatReaction,
                    lowPowerMode = lowPowerMode,
                    audioEnergy = audioEnergy,
                    beatPulse = beatPulse,
                )
            }
        }
        return
    }

    // Fallback when only raw lines are supplied
    if (lines.isNullOrEmpty()) {
        LyricsCenteredMessage(
            icon = Icons.Rounded.MusicNote,
            title = emptyMessage,
            modifier = modifier,
        )
        return
    }

    val isSynced = lines.any { it.timeMs > 0L }
    val isWordSynced = lines.any { it.isWordSynced }
    LyricsListView(
        lines = lines,
        position = position,
        isSynced = isSynced,
        isWordSynced = isWordSynced,
        badgeLabel = if (isSynced) "Letras sincronizadas" else "Letras sin sincronización",
        onSeekToLine = onSeekToLine,
        isPlaying = isPlaying,
        onOpenLyricsSettings = onOpenLyricsSettings,
        modifier = modifier,
        effectsEnabled = effectsEnabled,
        effectStyle = effectStyle,
        effectIntensity = effectIntensity,
        beatReaction = beatReaction,
        lowPowerMode = lowPowerMode,
        audioEnergy = audioEnergy,
        beatPulse = beatPulse,
    )
}

@Composable
private fun LyricsListView(
    lines: List<LyricLine>,
    position: PlaybackPosition,
    isSynced: Boolean,
    isWordSynced: Boolean,
    badgeLabel: String?,
    onSeekToLine: (Long) -> Unit,
    isPlaying: Boolean,
    onOpenLyricsSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    effectsEnabled: Boolean = true,
    effectStyle: LyricsEffectStyle = LyricsEffectStyle.PULSE,
    effectIntensity: Float = 1.0f,
    beatReaction: Boolean = true,
    lowPowerMode: Boolean = false,
    audioEnergy: Float = 0f,
    beatPulse: Float = 0f,
) {
    val listState = rememberLazyListState()
    var browsingByDrag by remember { mutableStateOf(false) }

    // Reiniciar posición al inicio cuando cambia la pista/conjunto de líneas
    LaunchedEffect(lines) {
        browsingByDrag = false
        if (lines.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Detección exclusiva de interacción táctil del usuario mediante DragInteraction.
    if (isSynced) {
        LaunchedEffect(listState.interactionSource) {
            listState.interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is DragInteraction.Start -> {
                        browsingByDrag = true
                    }
                    else -> Unit
                }
            }
        }
    }

    val activeIndex by remember(lines, position.positionMs, isSynced) {
        derivedStateOf {
            if (!isSynced) return@derivedStateOf -1
            val currentMs = position.positionMs
            val idx = lines.indexOfLast { it.timeMs <= currentMs }
            if (idx >= 0) idx else 0
        }
    }

    val currentActiveLine by rememberUpdatedState(activeIndex)
    val activeOnScreen by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == currentActiveLine }
        }
    }

    // Dual-timer auto-scroll resume:
    // Timer 1: 600ms short delay if active line is still visible on screen
    LaunchedEffect(browsingByDrag, activeOnScreen, listState.isScrollInProgress, isPlaying) {
        if (isPlaying && browsingByDrag && activeOnScreen && !listState.isScrollInProgress) {
            delay(600L)
            browsingByDrag = false
        }
    }

    // Timer 2: 5000ms long delay if user scrolled away
    LaunchedEffect(browsingByDrag, listState.isScrollInProgress, isPlaying) {
        if (isPlaying && browsingByDrag && !listState.isScrollInProgress) {
            delay(5000L)
            browsingByDrag = false
        }
    }

    // Auto-scroll centrado verticalmente con ScrollRun + animateScrollBy y LYRIC_EASING
    var scrollRun by remember(lines) { mutableStateOf(ScrollRun(0, 0f, 400)) }

    if (isSynced) {
        LaunchedEffect(activeIndex, browsingByDrag) {
            if (!browsingByDrag && activeIndex in lines.indices) {
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportSize.height
                val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }

                if (visible != null && viewportHeight > 0) {
                    val targetCenter = (viewportHeight - visible.size) / 2
                    val distance = (visible.offset - targetCenter).toFloat()
                    if (kotlin.math.abs(distance) > 4f) {
                        val span = 400
                        scrollRun = ScrollRun(scrollRun.id + 1, distance, span)
                        listState.animateScrollBy(
                            value = distance,
                            animationSpec = tween(durationMillis = span, easing = LYRIC_EASING),
                        )
                    }
                } else {
                    val centerOffset = if (viewportHeight > 0) {
                        -((viewportHeight - 120) / 2).coerceAtLeast(0)
                    } else {
                        -180
                    }
                    listState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = centerOffset,
                    )
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!badgeLabel.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = if (onOpenLyricsSettings != null) Modifier.clickable { onOpenLyricsSettings() } else Modifier,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E5FF).copy(alpha = 0.90f),
                            fontWeight = FontWeight.Medium,
                        )
                        if (onOpenLyricsSettings != null) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configuración de fuentes de letras",
                                tint = Color(0xFFFFB547),
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { index, item -> "${index}_${item.timeMs}" }) { index, line ->
                val isActive = isSynced && (index == activeIndex)
                LyricLineItem(
                    line = line,
                    isActive = isActive,
                    isSynced = isSynced,
                    positionMs = position.positionMs,
                    onClick = {
                        if (isSynced && line.timeMs >= 0L) {
                            browsingByDrag = false
                            onSeekToLine(line.timeMs)
                        }
                    },
                    effectsEnabled = effectsEnabled,
                    effectStyle = effectStyle,
                    effectIntensity = effectIntensity,
                    beatReaction = beatReaction,
                    lowPowerMode = lowPowerMode,
                    audioEnergy = audioEnergy,
                    beatPulse = beatPulse,
                )
            }
        }
    }
}

/**
 * Individual lyric line item with animated focus transition, word-synced highlight, and tap gesture.
 */
@Composable
fun LyricLineItem(
    line: LyricLine,
    isActive: Boolean,
    isSynced: Boolean,
    positionMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    effectsEnabled: Boolean = true,
    effectStyle: LyricsEffectStyle = LyricsEffectStyle.PULSE,
    effectIntensity: Float = 1.0f,
    beatReaction: Boolean = true,
    lowPowerMode: Boolean = false,
    audioEnergy: Float = 0f,
    beatPulse: Float = 0f,
    accentColor: Color = LocalPlayerTint.current,
) {
    if (line.isGap) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(28.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (isActive) 0.85f else 0.25f)),
                    )
                }
            }
        }
        return
    }

    val alpha by animateFloatAsState(
        targetValue = when {
            !isSynced -> 0.80f
            isActive -> 1f
            else -> 0.35f
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lyricAlpha",
    )

    val scale by animateFloatAsState(
        targetValue = when {
            !isSynced -> 1.0f
            isActive -> 1.03f
            else -> 0.98f
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lyricScale",
    )

    val effectivePulse = if (beatReaction) {
        if (beatPulse > 0.01f) beatPulse else calculateProceduralBeat(positionMs)
    } else {
        calculateProceduralBeat(positionMs)
    }

    val shadow = if (isActive && isSynced && effectsEnabled) {
        calculateLyricShadow(
            style = effectStyle,
            intensity = effectIntensity,
            beatPulse = effectivePulse,
            accentColor = accentColor,
            lowPowerMode = lowPowerMode,
        )
    } else if (isActive && isSynced) {
        Shadow(
            color = Color.White.copy(alpha = 0.50f),
            offset = Offset(0f, 0f),
            blurRadius = 14f,
        )
    } else {
        null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp)
            .lyricLineVisualEffect(
                isActive = isActive && isSynced,
                effectsEnabled = effectsEnabled,
                style = effectStyle,
                intensity = effectIntensity,
                beatPulseProvider = { effectivePulse },
                positionMsProvider = { positionMs },
                accentColor = accentColor,
                lowPowerMode = lowPowerMode,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            if (isActive && line.isWordSynced) {
                // Word-by-word active timing with progressive lift and highlight
                WordSyncedLineText(
                    line = line,
                    positionMs = positionMs,
                    shadow = shadow,
                    accentColor = accentColor,
                    intensity = effectIntensity,
                    lowPowerMode = lowPowerMode,
                    modifier = Modifier
                        .scale(scale)
                        .alpha(alpha),
                )
            } else if (isActive && isSynced && effectStyle == LyricsEffectStyle.KARAOKE && effectsEnabled) {
                val revealed = line.revealedChars(positionMs).toInt().coerceIn(0, line.text.length)
                val karaokeText = remember(line.text, revealed) {
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(
                            androidx.compose.ui.text.AnnotatedString(
                                text = line.text.substring(0, revealed),
                                spanStyle = androidx.compose.ui.text.SpanStyle(color = Color.White),
                            )
                        )
                        append(
                            androidx.compose.ui.text.AnnotatedString(
                                text = line.text.substring(revealed),
                                spanStyle = androidx.compose.ui.text.SpanStyle(color = Color.White.copy(alpha = 0.40f)),
                            )
                        )
                    }
                }
                Text(
                    text = karaokeText,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        shadow = shadow,
                        lineHeight = 34.sp,
                    ),
                    modifier = Modifier
                        .scale(scale)
                        .alpha(alpha),
                )
            } else {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = if (isActive) 26.sp else 22.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        shadow = shadow,
                        lineHeight = 34.sp,
                    ),
                    color = if (isActive && isSynced) Color.White else LocalPlayerTint.current.copy(alpha = alpha),
                    modifier = Modifier
                        .scale(scale)
                        .alpha(alpha),
                )
            }

            // Answering / background vocal line
            val bg = line.background
            if (bg != null && bg.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "(${bg.text.trim('(', ')')})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = (if (isActive && isSynced) accentColor else LocalPlayerTint.current).copy(
                        alpha = if (isActive) 0.85f else 0.35f,
                    ),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
    }
}

/**
 * Word-level highlighting for providers that supply syllable/word timestamps.
 */
@Composable
private fun WordSyncedLineText(
    line: LyricLine,
    positionMs: Long,
    shadow: Shadow?,
    accentColor: Color,
    intensity: Float,
    lowPowerMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        line.words.forEachIndexed { index, word ->
            val isSung = positionMs >= word.endMs
            val isSinging = positionMs in word.startMs..word.endMs
            val lift = if (!lowPowerMode && isSinging) line.wordLift(index, positionMs) * 5f * intensity else 0f

            val wordColor = when {
                isSung -> Color.White
                isSinging -> accentColor
                else -> Color.White.copy(alpha = 0.40f)
            }

            Text(
                text = word.text,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    shadow = shadow,
                    lineHeight = 34.sp,
                ),
                color = wordColor,
                modifier = Modifier.graphicsLayer {
                    translationY = -lift
                },
            )
            if (index != line.words.lastIndex) {
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

private fun buildAnnotatedStringForWords(
    words: List<LyricWord>,
    positionMs: Long,
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        words.forEachIndexed { index, word ->
            val isSung = positionMs >= word.startMs
            val color = if (isSung) Color.White else Color.White.copy(alpha = 0.45f)
            append(
                androidx.compose.ui.text.AnnotatedString(
                    text = word.text,
                    spanStyle = androidx.compose.ui.text.SpanStyle(color = color),
                ),
            )
            if (index != words.lastIndex) {
                append(" ")
            }
        }
    }
}

@Composable
private fun LyricsLoadingState(
    title: String,
    providerLabel: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                color = Color(0xFF00E5FF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Buscando letras...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.85f),
            )
            if (!providerLabel.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Consultando: $providerLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00E5FF).copy(alpha = 0.70f),
                )
            }
        }
    }
}

@Composable
private fun LyricsErrorState(
    message: String,
    canRetry: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Error al obtener letras",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f),
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (canRetry && onRetry != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reintentar",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsCenteredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    tintColor: Color = LocalPlayerTint.current.copy(alpha = 0.45f),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

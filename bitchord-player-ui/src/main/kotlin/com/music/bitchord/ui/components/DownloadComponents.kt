package com.music.bitchord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadBatchItem
import com.music.bitchord.download.DownloadBatchState
import com.music.bitchord.download.DownloadProgress
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.SavedSongMetadata
import java.util.Locale

private val AccentCyan = Color(0xFF00E5FF)
private val AccentGreen = Color(0xFF00E676)
private val AccentAmber = Color(0xFFFFB300)
private val AccentRed = Color(0xFFFF5252)
private val CardBg = Color(0xFF121722)
private val SurfaceBg = Color(0xFF0B0E14)
private val SubtleBorder = Color(0xFF232C3D)

/**
 * Per-song download state indicator and action control adapted from BitChord 1.8.1.
 *
 * States:
 * - Not downloaded & idle -> Download button (`⬇`)
 * - `DownloadState.Queued` -> Indeterminate progress ring + cancel on tap
 * - `DownloadState.Running(fraction)` -> Determinate progress ring + percentage + cancel on tap
 * - `DownloadState.Failed(reason)` -> Error/Retry icon (`↻`) -> retries on tap
 * - `isDownloaded == true` -> Downloaded badge (`✓`) -> deletes or manages offline copy on tap
 */
@Composable
fun SongDownloadActionButton(
    song: Song,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    onDownload: (Song) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (Song) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        downloadState is DownloadState.Queued -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.12f))
                    .clickable { onCancel(song.videoId) },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = AccentCyan,
                    strokeWidth = 2.dp
                )
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cancelar descarga en cola",
                    tint = AccentCyan,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        downloadState is DownloadState.Running -> {
            val animatedProgress by animateFloatAsState(
                targetValue = downloadState.fraction.coerceIn(0.05f, 1f),
                animationSpec = tween(durationMillis = 180),
                label = "songDownloadProgress"
            )
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.14f))
                    .clickable { onCancel(song.videoId) },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(28.dp),
                    color = AccentCyan,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = "${(downloadState.fraction * 100).toInt()}%",
                    color = AccentCyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        downloadState is DownloadState.Failed -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentRed.copy(alpha = 0.16f))
                    .border(1.dp, AccentRed.copy(alpha = 0.5f), CircleShape)
                    .clickable { onRetry(song) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Reintentar descarga (${downloadState.reason})",
                    tint = AccentRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        isDownloaded -> {
            Box(
                modifier = modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(alpha = 0.15f))
                    .border(1.dp, AccentGreen.copy(alpha = 0.45f), CircleShape)
                    .clickable { onDelete(song.videoId) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = "Descargada (tocar para eliminar copia offline)",
                    tint = AccentGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        else -> {
            IconButton(
                onClick = { onDownload(song) },
                modifier = modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Descargar canción para reproducción offline",
                    tint = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Top-bar download button adapted from BitChord 1.8.1's `TopBarDownloadButton` (`DownloadManagerSheet.kt`).
 * Shows live batch progress, failure badge, or offline track count and opens the Download Center.
 */
@Composable
fun TopBarDownloadButton(
    batchState: DownloadBatchState,
    downloadedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = batchState.fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "topBarDownloadFraction"
    )

    val borderColor = when {
        batchState.failed > 0 -> AccentRed.copy(alpha = 0.7f)
        batchState.busy -> AccentCyan
        downloadedCount > 0 -> AccentGreen.copy(alpha = 0.55f)
        else -> SubtleBorder
    }

    val bgColor = when {
        batchState.failed > 0 -> AccentRed.copy(alpha = 0.14f)
        batchState.busy -> AccentCyan.copy(alpha = 0.14f)
        downloadedCount > 0 -> AccentGreen.copy(alpha = 0.12f)
        else -> Color(0xFF151B26)
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
            if (batchState.busy) {
                CircularProgressIndicator(
                    progress = { animatedFraction.coerceAtLeast(0.08f) },
                    modifier = Modifier.size(20.dp),
                    color = AccentCyan,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    strokeWidth = 2.dp
                )
            }
            Icon(
                imageVector = when {
                    batchState.failed > 0 && !batchState.busy -> Icons.Rounded.ErrorOutline
                    downloadedCount > 0 && !batchState.busy -> Icons.Rounded.CloudDone
                    else -> Icons.Rounded.Download
                },
                contentDescription = "Centro de Descargas",
                tint = when {
                    batchState.failed > 0 && !batchState.busy -> AccentRed
                    batchState.busy -> AccentCyan
                    downloadedCount > 0 -> AccentGreen
                    else -> Color.White.copy(alpha = 0.8f)
                },
                modifier = Modifier.size(if (batchState.busy) 12.dp else 16.dp)
            )
        }

        val labelText = when {
            batchState.busy -> "Descargando ${(batchState.fraction * 100).toInt()}% (${batchState.waiting})"
            batchState.failed > 0 -> "Error (${batchState.failed})"
            downloadedCount > 0 -> "Offline ($downloadedCount)"
            else -> "Descargas"
        }

        Text(
            text = labelText,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * Full-screen / modal Download Center dialog optimized for 1340x800 automotive landscape.
 */
@Composable
fun DownloadManagerDialog(
    batchState: DownloadBatchState,
    activeStates: Map<String, DownloadState>,
    downloadedSongs: List<Song>,
    savedMetadata: Map<String, SavedSongMetadata>,
    onDismiss: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onPlayAllOffline: (List<Song>, Boolean) -> Unit,
    onCancelDownload: (String) -> Unit,
    onCancelAll: () -> Unit,
    onRetryDownload: (String) -> Unit,
    onDismissFailure: (String) -> Unit,
    onDeleteDownload: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SubtleBorder, RoundedCornerShape(18.dp)),
            color = SurfaceBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Column {
                            Text(
                                text = "CENTRO DE DESCARGAS Y MODO OFFLINE — BITCHORD",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val totalBytes = downloadedSongs.sumOf { song ->
                                savedMetadata[song.videoId]?.fileSizeBytes ?: 0L
                            }
                            Text(
                                text = "${downloadedSongs.size} canciones guardadas en dispositivo • ${formatBytes(totalBytes)} disponibles sin Internet",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (batchState.busy) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentRed.copy(alpha = 0.16f))
                                    .border(1.dp, AccentRed.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable(onClick = onCancelAll)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Cancelar activas (${batchState.waiting})",
                                    color = AccentRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cerrar Centro de Descargas",
                                tint = Color.White
                            )
                        }
                    }
                }

                HorizontalDivider(color = SubtleBorder)

                DownloadCenterBody(
                    batchState = batchState,
                    activeStates = activeStates,
                    downloadedSongs = downloadedSongs,
                    savedMetadata = savedMetadata,
                    onPlaySong = onPlaySong,
                    onPlayAllOffline = onPlayAllOffline,
                    onCancelDownload = onCancelDownload,
                    onCancelAll = onCancelAll,
                    onRetryDownload = onRetryDownload,
                    onDismissFailure = onDismissFailure,
                    onDeleteDownload = onDeleteDownload,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * Reusable Download Center body rendered both inside [DownloadManagerDialog] and inside
 * the `BIBLIOTECA -> Canciones descargadas (local:downloads)` view.
 */
@Composable
fun DownloadCenterBody(
    batchState: DownloadBatchState,
    activeStates: Map<String, DownloadState>,
    downloadedSongs: List<Song>,
    savedMetadata: Map<String, SavedSongMetadata>,
    onPlaySong: (Song) -> Unit,
    onPlayAllOffline: (List<Song>, Boolean) -> Unit,
    onCancelDownload: (String) -> Unit,
    onCancelAll: () -> Unit,
    onRetryDownload: (String) -> Unit,
    onDismissFailure: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeOrFailedBatchItems = batchState.items.filter { item ->
        item.progress !is DownloadProgress.Done
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Active / Queued / Failed downloads section
        if (activeOrFailedBatchItems.isNotEmpty() || activeStates.isNotEmpty()) {
            item(key = "header_active_downloads") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBg)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (batchState.busy) {
                                "DESCARGAS EN CURSO (${batchState.finished}/${batchState.items.size})"
                            } else {
                                "ESTADO RECIENTE DE DESCARGAS"
                            },
                            color = AccentCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (batchState.busy) {
                            Text(
                                text = "${(batchState.fraction * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (batchState.busy) {
                        LinearProgressIndicator(
                            progress = { batchState.fraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = AccentCyan,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    }
                }
            }

            items(
                items = activeOrFailedBatchItems,
                key = { "batch_${it.key}" }
            ) { item ->
                BatchDownloadItemRow(
                    item = item,
                    onCancel = { onCancelDownload(item.videoId) },
                    onRetry = { onRetryDownload(item.videoId) },
                    onDismiss = { onDismissFailure(item.key) }
                )
            }
        }

        // 2. Completed Offline Songs section
        item(key = "header_completed_downloads") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CANCIONES DESCARGADAS (DISPONIBLES SIN INTERNET)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Prioridad local activa: se reproducen directamente desde el almacenamiento interno sin usar datos",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }

                if (downloadedSongs.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan.copy(alpha = 0.18f))
                                .border(1.dp, AccentCyan, RoundedCornerShape(10.dp))
                                .clickable { onPlayAllOffline(downloadedSongs, false) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Reproducir Offline",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardBg)
                                .border(1.dp, SubtleBorder, RoundedCornerShape(10.dp))
                                .clickable { onPlayAllOffline(downloadedSongs, true) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Aleatorio",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        if (downloadedSongs.isEmpty()) {
            item(key = "empty_downloads_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, SubtleBorder, RoundedCornerShape(14.dp))
                        .padding(vertical = 36.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No hay canciones descargadas todavía",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Pulsa el icono de descarga (⬇) junto a cualquier canción en Inicio, Explorar, Buscar, Biblioteca o el Reproductor para guardarla y escucharla sin conexión.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(
                items = downloadedSongs,
                key = { "saved_${it.videoId}" }
            ) { song ->
                DownloadedSongRow(
                    song = song,
                    metadata = savedMetadata[song.videoId],
                    onPlay = { onPlaySong(song) },
                    onDelete = { onDeleteDownload(song.videoId) }
                )
            }
        }
    }
}

@Composable
private fun BatchDownloadItemRow(
    item: DownloadBatchItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val progress = item.progress
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2638)),
            contentAlignment = Alignment.Center
        ) {
            if (!item.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = AccentCyan
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.artist,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            when (progress) {
                DownloadProgress.Queued -> {
                    Text(
                        text = "En cola para descargar...",
                        color = AccentAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                is DownloadProgress.Running -> {
                    val pct = (progress.fraction * 100).toInt()
                    val bytesLabel = if (progress.totalBytes > 0L) {
                        " • ${formatBytes(progress.bytesWritten)} / ${formatBytes(progress.totalBytes)}"
                    } else if (progress.bytesWritten > 0L) {
                        " • ${formatBytes(progress.bytesWritten)}"
                    } else {
                        ""
                    }
                    Text(
                        text = "Descargando $pct%$bytesLabel",
                        color = AccentCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AccentCyan,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }

                is DownloadProgress.Failed -> {
                    Text(
                        text = "Error: ${progress.reason}",
                        color = AccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                DownloadProgress.Done -> {
                    Text(
                        text = "Completada",
                        color = AccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        when (progress) {
            DownloadProgress.Queued, is DownloadProgress.Running -> {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancelar descarga",
                        tint = AccentRed
                    )
                }
            }

            is DownloadProgress.Failed -> {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reintentar descarga",
                            tint = AccentCyan
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Descartar aviso de error",
                            tint = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            DownloadProgress.Done -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Completada",
                    tint = AccentGreen
                )
            }
        }
    }
}

@Composable
private fun DownloadedSongRow(
    song: Song,
    metadata: SavedSongMetadata?,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val sizeBytes = metadata?.fileSizeBytes ?: 0L
    val formatLabel = metadata?.downloadFormat ?: song.downloadFormat
    val formatBadge = buildString {
        append("✓ Offline")
        if (!formatLabel.isNullOrBlank()) {
            append(" • ")
            append(formatLabel)
        }
        if (sizeBytes > 0L) {
            append(" • ")
            append(formatBytes(sizeBytes))
        }
        if (!song.durationText.isNullOrBlank()) {
            append(" • ")
            append(song.durationText)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, SubtleBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2638)),
            contentAlignment = Alignment.Center
        ) {
            val artModel = metadata?.localArtworkUri?.takeIf { it.isNotBlank() } ?: song.thumbnailUrl
            if (!artModel.isNullOrBlank()) {
                AsyncImage(
                    model = artModel,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = AccentGreen
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = song.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(song.artist)
                if (!song.albumName.isNullOrBlank()) {
                    append(" • ")
                    append(song.albumName)
                }
            }
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatBadge,
                color = AccentGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Reproducir copia local offline",
                tint = AccentCyan
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Eliminar descarga del dispositivo",
                tint = AccentRed.copy(alpha = 0.85f)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = (bytes / 1024L).coerceAtLeast(1L)
        "$kb KB"
    }
}

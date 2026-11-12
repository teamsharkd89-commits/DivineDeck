package com.music.bitchord.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.music.bitchord.download.DownloadState
import com.music.bitchord.ui.components.SongDownloadActionButton
import com.music.bitchord.ui.components.SwipeToQueueBox
import com.music.bitchord.ui.icons.BitChordIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.PullToRefreshState
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.feedSkeleton
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.rememberPullToRefreshState
import com.music.bitchord.ui.components.songListSkeleton
import com.music.bitchord.ui.components.thumbnailBorder
import kotlin.math.absoluteValue

/**
 * Pantalla original de Explorar (Explore) de BitChord 1.8.1 integrada en DIVINE DECK.
 *
 * Muestra las categorías de estados de ánimo y géneros musicales (`FEmusic_moods_and_genres`)
 * organizadas en una cuadrícula adaptativa a pantalla horizontal (1340x800) con diseño de
 * carátula + vinilo (`MoodGenreCard` / `MoodGenreSleeve`).
 */
@Composable
fun ExploreScreen(
    state: UiState<List<MoodGenreSection>>,
    listState: LazyListState,
    onCategoryClick: (MoodGenre) -> Unit,
    onRetry: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = onRetry,
    pullState: PullToRefreshState = rememberPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(),
    title: String = "Explorar",
) {
    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        scrollableState = listState,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columns = ((maxWidth - PAGE_GUTTER * 2) / 172.dp).toInt().coerceIn(2, 6)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                item(key = "header") {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(
                            start = PAGE_GUTTER,
                            end = PAGE_GUTTER,
                            top = 8.dp,
                            bottom = 4.dp,
                        ),
                    )
                }

                when (state) {
                    UiState.Loading -> feedSkeleton()
                    is UiState.Error -> item(key = "error") {
                        MessageState(
                            message = state.message,
                            actionLabel = "Reintentar",
                            onAction = onRetry,
                        )
                    }
                    is UiState.Success -> {
                        val sections = state.data
                        sections.forEachIndexed { sectionIndex, section ->
                            item(key = "section_header_${sectionIndex}_${section.title}") {
                                Column(modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)) {
                                    SectionHeader(title = section.title)
                                }
                            }
                            val rows = section.items.chunked(columns)
                            items(
                                items = rows,
                                key = { row ->
                                    "row_${sectionIndex}_${row.first().let { "${it.browseId}_${it.params}" }}"
                                },
                            ) { row ->
                                MoodGenreRow(
                                    items = row,
                                    columns = columns,
                                    onClick = onCategoryClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodGenreRow(
    items: List<MoodGenre>,
    columns: Int,
    onClick: (MoodGenre) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items.forEach { item ->
            MoodGenreCard(
                item = item,
                modifier = Modifier.weight(1f),
                onClick = { onClick(item) },
            )
        }
        repeat(columns - items.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoodGenreCard(
    item: MoodGenre,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val (top, bottom) = remember(item.title, item.stripeColor) { paletteFor(item) }
    val discAccent = remember(top, bottom) { lerp(top, Color.White, 0.18f) }

    Box(
        modifier = modifier
            .height(92.dp)
            .clip(shape)
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(top, bottom),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
            }
            .thumbnailBorder(shape)
            .longPressMenuClickable(onClick = onClick),
    ) {
        MoodGenreSleeve(
            thumbnailUrl = item.thumbnailUrl,
            accent = discAccent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 10.dp),
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 11.dp, end = 66.dp),
        )
    }
}

/**
 * Tilted album sleeve with a vinyl record peeking out from behind it.
 */
@Composable
private fun MoodGenreSleeve(
    thumbnailUrl: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val sleeveShape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(width = 78.dp, height = 64.dp)
            .graphicsLayer { rotationZ = 19f },
    ) {
        Canvas(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.CenterStart),
        ) {
            val radius = size.minDimension / 2f
            drawCircle(color = Color(0xFF141417))
            drawCircle(
                brush = Brush.sweepGradient(
                    0.00f to Color.White.copy(alpha = 0.03f),
                    0.12f to Color.White.copy(alpha = 0.14f),
                    0.24f to Color.White.copy(alpha = 0.02f),
                    0.50f to Color.White.copy(alpha = 0.03f),
                    0.62f to Color.White.copy(alpha = 0.14f),
                    0.74f to Color.White.copy(alpha = 0.02f),
                    1.00f to Color.White.copy(alpha = 0.03f),
                ),
            )
            val grooveStroke = Stroke(width = 0.8.dp.toPx())
            drawCircle(
                color = Color.White.copy(alpha = 0.09f),
                radius = radius * 0.82f,
                style = grooveStroke,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = radius * 0.64f,
                style = grooveStroke,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = radius - 0.5.dp.toPx(),
                style = grooveStroke,
            )
            drawCircle(color = accent, radius = radius * 0.34f)
            drawCircle(color = Color(0xFF141417), radius = radius * 0.09f)
        }

        Box(
            modifier = Modifier
                .size(58.dp)
                .align(Alignment.CenterEnd)
                .shadow(
                    elevation = 8.dp,
                    shape = sleeveShape,
                    ambientColor = Color.Black.copy(alpha = 0.45f),
                    spotColor = Color.Black.copy(alpha = 0.45f),
                )
                .clip(sleeveShape)
                .background(lerp(accent, Color.Black, 0.35f))
                .thumbnailBorder(sleeveShape),
        ) {
            if (!thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl.artworkAt(CARD_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                lerp(accent, Color.White, 0.20f),
                                lerp(accent, Color.Black, 0.45f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Pantalla original de estantes de una categoría de estado de ánimo o género (`MoodGenrePlaylistsScreen`).
 */
@Composable
fun MoodGenrePlaylistsScreen(
    title: String,
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    onBack: (() -> Unit)? = null,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PAGE_GUTTER,
                        end = PAGE_GUTTER,
                        top = 8.dp,
                        bottom = 4.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onBack != null) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver a Explorar",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Explorar",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when (state) {
            UiState.Loading -> feedSkeleton()
            is UiState.Error -> item(key = "error") {
                MessageState(
                    message = state.message,
                    actionLabel = "Reintentar",
                    onAction = onRetry,
                )
            }
            is UiState.Success -> {
                state.data.forEachIndexed { index, shelf ->
                    item(key = "mood_shelf_${index}_${shelf.title}") {
                        Shelf(
                            shelf = shelf,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            onItemClick = onItemClick,
                            onItemPlay = onItemPlay,
                            onItemLongPress = onItemLongPress,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vista de detalle de una colección (Álbum, Playlist o Artista) seleccionada desde INICIO, EXPLORAR o BIBLIOTECA.
 * Permite explorar sus canciones, reproducir cualquier pista o iniciar la colección completa,
 * guardar/quitar la colección de la biblioteca, gestionar playlists editables (renombrar/eliminar)
 * y añadir/quitar canciones en playlists o favoritos.
 */
@Composable
fun BrowseCollectionDetailScreen(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    songsState: UiState<List<Song>>,
    listState: LazyListState,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onRetry: () -> Unit,
    sections: List<HomeShelf> = emptyList(),
    onSectionItemClick: ((ShelfItem) -> Unit)? = null,
    isCollectionSaved: Boolean = false,
    onToggleSaveCollection: (() -> Unit)? = null,
    isEditablePlaylist: Boolean = false,
    onRenamePlaylist: (() -> Unit)? = null,
    onDeletePlaylist: (() -> Unit)? = null,
    isSongLiked: ((Song) -> Boolean)? = null,
    onToggleLikeSong: ((Song) -> Unit)? = null,
    onAddSongToPlaylist: ((Song) -> Unit)? = null,
    onRemoveSongFromPlaylist: ((Song) -> Unit)? = null,
    downloadStates: Map<String, DownloadState> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    onDownloadAll: ((List<Song>) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "collection_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PAGE_GUTTER, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.08f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Volver",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Volver",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onToggleSaveCollection != null) {
                            Surface(
                                onClick = onToggleSaveCollection,
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCollectionSaved) Color(0xFF00E5FF).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCollectionSaved) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.14f)
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isCollectionSaved) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                                        contentDescription = if (isCollectionSaved) "Guardado en biblioteca" else "Guardar en biblioteca",
                                        tint = if (isCollectionSaved) Color(0xFF00E5FF) else Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (isCollectionSaved) "En Biblioteca" else "Guardar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCollectionSaved) Color(0xFF00E5FF) else Color.White,
                                    )
                                }
                            }
                        }

                        if (isEditablePlaylist && onRenamePlaylist != null) {
                            Surface(
                                onClick = onRenamePlaylist,
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Renombrar playlist",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Renombrar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }

                        if (isEditablePlaylist && onDeletePlaylist != null) {
                            Surface(
                                onClick = onDeletePlaylist,
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Eliminar playlist",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = "Eliminar",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val coverShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(coverShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .thumbnailBorder(coverShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = thumbnailUrl.artworkAt(CARD_ART_PX),
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(20.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.68f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        val readySongs = (songsState as? UiState.Success)?.data.orEmpty()
                        if (readySongs.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onPlayAll(readySongs) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00E5FF),
                                        contentColor = Color.Black,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Reproducir",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onShuffle(readySongs) },
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shuffle,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Aleatorio",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                if (onDownloadAll != null) {
                                    val allDownloaded = readySongs.isNotEmpty() && readySongs.all { it.videoId in downloadedIds }
                                    OutlinedButton(
                                        onClick = { onDownloadAll(readySongs) },
                                        shape = RoundedCornerShape(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = if (allDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                                            contentDescription = null,
                                            tint = if (allDownloaded) Color(0xFF00E676) else Color(0xFF00E5FF),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (allDownloaded) "Descargada" else "Descargar todo",
                                            color = if (allDownloaded) Color(0xFF00E676) else Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (songsState) {
            UiState.Loading -> songListSkeleton(count = 6)
            is UiState.Error -> item(key = "collection_error") {
                MessageState(
                    message = songsState.message,
                    actionLabel = "Reintentar",
                    onAction = onRetry,
                )
            }
            is UiState.Success -> {
                if (songsState.data.isEmpty() && sections.isEmpty()) {
                    item(key = "collection_empty") {
                        MessageState(
                            message = "Esta colección aún no tiene canciones. Añade pistas desde Buscar, Inicio o Explorar.",
                        )
                    }
                }
                itemsIndexed(
                    items = songsState.data,
                    key = { index, song -> "${index}_${song.videoId}" },
                ) { index, song ->
                    val isCurrent = currentSong?.videoId == song.videoId
                    val liked = isSongLiked?.invoke(song) == true
                    SwipeToQueueBox(
                        onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(song) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PAGE_GUTTER, vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) PlayingAccent.copy(alpha = 0.12f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .clickable { onSongClick(song) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                        val rowArtShape = RoundedCornerShape(6.dp)
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(rowArtShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .thumbnailBorder(rowArtShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            val artUrl = (song.thumbnailUrl ?: thumbnailUrl).artworkAt(ROW_ART_PX)
                            if (!artUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artUrl,
                                    contentDescription = song.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                )
                            }
                            if (isCurrent) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SearchPlayingBars(playing = isPlaying)
                                }
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isCurrent) PlayingAccent else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val meta = buildString {
                                append(song.artist)
                                if (!song.durationText.isNullOrBlank()) {
                                    append(" • ")
                                    append(song.durationText)
                                }
                                if (song.videoId in downloadedIds || !song.localUri.isNullOrBlank()) {
                                    append(" • ✓ Offline")
                                }
                            }
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.62f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (onDownloadSong != null) {
                            SongDownloadActionButton(
                                song = song,
                                downloadState = downloadStates[song.videoId],
                                isDownloaded = song.videoId in downloadedIds || !song.localUri.isNullOrBlank(),
                                onDownload = onDownloadSong,
                                onCancel = { onCancelDownload?.invoke(it) },
                                onRetry = { onRetryDownload?.invoke(it) ?: onDownloadSong(it) },
                                onDelete = { onDeleteDownload?.invoke(it) },
                            )
                        }

                        if (onToggleLikeSong != null) {
                            IconButton(
                                onClick = { onToggleLikeSong(song) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (liked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                                    contentDescription = if (liked) "Quitar de favoritas" else "Añadir a favoritas",
                                    tint = if (liked) Color(0xFFFF4081) else Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (onAddSongToPlaylist != null) {
                            IconButton(
                                onClick = { onAddSongToPlaylist(song) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = BitChordIcons.Plus,
                                    contentDescription = "Añadir a playlist",
                                    tint = Color.White.copy(alpha = 0.72f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        if (isEditablePlaylist && onRemoveSongFromPlaylist != null) {
                            IconButton(
                                onClick = { onRemoveSongFromPlaylist(song) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Quitar de la playlist",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

                if (sections.isNotEmpty() && onSectionItemClick != null) {
                    sections.forEachIndexed { idx, shelf ->
                        item(key = "artist_section_${idx}_${shelf.title}") {
                            Shelf(
                                shelf = shelf,
                                currentSong = currentSong,
                                isPlaying = isPlaying,
                                onItemClick = onSectionItemClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun paletteFor(item: MoodGenre): Pair<Color, Color> {
    item.stripeColor?.let { argb ->
        val raw = Color(argb.toInt() or (0xFF shl 24))
        val top = lerp(raw, Color.Black, 0.28f)
        val bottom = lerp(raw, Color.Black, 0.58f)
        return top to bottom
    }
    return CATEGORY_PALETTES[
        (item.title.hashCode().absoluteValue) % CATEGORY_PALETTES.size,
    ]
}

private val CATEGORY_PALETTES = listOf(
    Color(0xFFC93B4B) to Color(0xFF6E1A28),
    Color(0xFFD96B27) to Color(0xFF7A3410),
    Color(0xFFC4921D) to Color(0xFF6B4C0B),
    Color(0xFF2E9A68) to Color(0xFF135036),
    Color(0xFF2589A6) to Color(0xFF0F4659),
    Color(0xFF3E63C4) to Color(0xFF1C2F6B),
    Color(0xFF7B4BC4) to Color(0xFF3E2169),
    Color(0xFFB83E8E) to Color(0xFF5F1C49),
    Color(0xFF4F7A8C) to Color(0xFF243B46),
    Color(0xFF8C5A3C) to Color(0xFF472B1A),
)

package com.music.bitchord.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.playingRowMatcher
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.download.DownloadState
import com.music.bitchord.ui.components.LIBRARY_GRID_SPACING
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.ShelfRow
import com.music.bitchord.ui.components.SongDownloadActionButton
import com.music.bitchord.ui.components.SwipeToQueueBox
import com.music.bitchord.ui.components.libraryGrid
import com.music.bitchord.ui.components.librarySkeleton
import com.music.bitchord.ui.components.rememberPullToRefreshState
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Enlace rápido en la cabecera de `LibraryScreen` (BitChord 1.8.1 original):
 * carpetas de Canciones Favoritas (`local:liked`) y Música Local del Dispositivo (`local:all`).
 */
data class LibraryLink(
    val title: String,
    val subtitle: String,
    val browseId: String,
    val icon: ImageVector,
    val colors: List<Color>,
)

/**
 * Pantalla original `LibraryScreen` de BitChord 1.8.1 integrada en DIVINE DECK (Fase 06.5B).
 *
 * Muestra:
 * - Accesos directos rápidos (`LibraryLinkList`: Canciones favoritas, Música en el dispositivo).
 * - Estante de Playlists (`PlaylistShelf`) encabezado por la tarjeta original `NewShelfCard` ("Nueva playlist")
 *   seguido de las playlists creadas/fijadas con soporte de pulsación larga (fijar, renombrar, eliminar).
 * - Estantes de Álbumes guardados y Artistas guardados (`LibraryGridShelf`) con acceso a `LibraryGridPage` ("Ver todo").
 * - Lista de canciones favoritas (`likedSongs`) y canciones de biblioteca (`librarySongs`) con controles de
 *   reproducción directa, corazón de favorito y botón de añadir a playlist.
 * - Aviso honesto del modo de persistencia local frente a sincronización con cuenta remota de YouTube Music.
 */
@Composable
fun LibraryScreen(
    signedIn: Boolean,
    state: UiState<LibraryPage>,
    listState: LazyListState,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song, List<Song>, String?) -> Unit,
    onShelfItemClick: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
    onSongLongPress: ((Song) -> Unit)? = null,
    onSongFavoriteToggle: ((Song) -> Unit)? = null,
    onSongAddToPlaylist: ((Song) -> Unit)? = null,
    isSongLiked: ((Song) -> Boolean)? = null,
    onShelfItemPlay: ((ShelfItem) -> Unit)? = null,
    onShelfItemLongPress: ((ShelfItem) -> Unit)? = null,
    onShowAllShelf: (HomeShelf) -> Unit = {},
    onNewPlaylist: (() -> Unit)? = null,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    title: String? = "Biblioteca",
    pinnedPlaylists: Set<String> = emptySet(),
    localFolders: List<LibraryLink> = emptyList(),
    downloadedSongs: List<Song> = emptyList(),
    downloadStates: Map<String, DownloadState> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onOpenDownloadsClick: (() -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
) {
    val pullToRefreshState = rememberPullToRefreshState()

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        scrollableState = listState,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (title != null) {
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PAGE_GUTTER,
                                end = PAGE_GUTTER,
                                top = 8.dp,
                                bottom = 6.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (onOpenDownloadsClick != null) {
                                Surface(
                                    onClick = onOpenDownloadsClick,
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF00E676).copy(alpha = 0.14f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = Color(0xFF00E676).copy(alpha = 0.45f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDone,
                                            contentDescription = "Centro de Descargas",
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Descargas (${downloadedSongs.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676),
                                        )
                                    }
                                }
                            }

                            if (onNewPlaylist != null) {
                                Surface(
                                    onClick = onNewPlaylist,
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF00E5FF).copy(alpha = 0.14f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                                    ),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Nueva playlist",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = "Nueva playlist",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E5FF),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Aviso honesto de persistencia local vs cuenta remota de YouTube Music
            if (!signedIn) {
                item(key = "storage_mode_notice") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PAGE_GUTTER, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141824),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = Color(0xFF00E5FF).copy(alpha = 0.22f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Biblioteca local activa en este dispositivo • Tus playlists, canciones favoritas y colecciones se guardan localmente sin requerir inicio de sesión en YouTube Music.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f),
                            )
                        }
                    }
                }
            }

            // Carpetas rápidas (Canciones favoritas, Música local del dispositivo)
            if (localFolders.isNotEmpty()) {
                item(key = "local_folders") {
                    LibraryLinkList(
                        items = localFolders,
                        onItemClick = { link ->
                            onShelfItemClick(
                                ShelfItem(
                                    title = link.title,
                                    subtitle = link.subtitle,
                                    thumbnailUrl = null,
                                    videoId = null,
                                    browseId = link.browseId,
                                )
                            )
                        },
                    )
                }
            }

            when (state) {
                UiState.Loading -> librarySkeleton()
                is UiState.Error -> item(key = "error") {
                    MessageState(
                        message = state.message,
                        actionLabel = "Reintentar",
                        onAction = onRetry,
                    )
                }
                is UiState.Success -> {
                    val page = state.data
                    val playlistsShelf = page.shelves.firstOrNull {
                        it.title.equals("Playlists", ignoreCase = true)
                    } ?: HomeShelf("Playlists", emptyList())

                    // Estante principal de Playlists (siempre muestra NewShelfCard para crear nuevas playlists)
                    item(key = "shelf_playlists") {
                        PlaylistShelf(
                            shelf = playlistsShelf,
                            pinnedPlaylists = pinnedPlaylists,
                            onNewPlaylist = onNewPlaylist,
                            onItemClick = onShelfItemClick,
                            onItemPlay = onShelfItemPlay,
                            onItemLongPress = onShelfItemLongPress,
                            onShowAll = if (playlistsShelf.items.size > LIBRARY_SHELF_PREVIEW_LIMIT) {
                                { onShowAllShelf(playlistsShelf) }
                            } else {
                                null
                            },
                        )
                    }

                    // Otros estantes de la biblioteca (Álbumes guardados, Artistas guardados, etc.)
                    page.shelves
                        .filterNot { it.title.equals("Playlists", ignoreCase = true) }
                        .forEach { shelf ->
                            if (shelf.items.isNotEmpty()) {
                                item(key = "shelf_${shelf.title}") {
                                    LibraryGridShelf(
                                        shelf = shelf,
                                        onItemClick = onShelfItemClick,
                                        onItemPlay = onShelfItemPlay,
                                        onItemLongPress = onShelfItemLongPress,
                                        onShowAll = { onShowAllShelf(shelf) },
                                    )
                                }
                            }
                        }

                    // Canciones descargadas disponibles sin conexión (Offline)
                    if (downloadedSongs.isNotEmpty()) {
                        val isCurrentInDownloads = playingRowMatcher(downloadedSongs, currentSong)
                        item(key = "downloaded_songs_header") {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(
                                title = "Canciones descargadas (Offline)",
                                strapline = "${downloadedSongs.size} canciones en dispositivo • Prioridad local activa",
                                onShowAll = onOpenDownloadsClick,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(
                            items = downloadedSongs.take(8),
                            key = { _, song -> "dl:${song.videoId}" },
                        ) { _, song ->
                            val current = isCurrentInDownloads(song)
                            LibrarySongRow(
                                song = song,
                                isCurrent = current,
                                isPlaying = current && isPlaying,
                                isLiked = isSongLiked?.invoke(song) ?: false,
                                downloadState = downloadStates[song.videoId],
                                isDownloaded = true,
                                onClick = {
                                    onSongClick(song, downloadedSongs, "Canciones descargadas")
                                },
                                onDownload = onDownloadSong,
                                onCancelDownload = onCancelDownload,
                                onRetryDownload = onRetryDownload,
                                onDeleteDownload = onDeleteDownload,
                                onToggleFavorite = onSongFavoriteToggle?.let { { it(song) } },
                                onAddToPlaylist = onSongAddToPlaylist?.let { { it(song) } },
                                onLongPress = onSongLongPress?.let { { it(song) } },
                                onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(song) } },
                            )
                        }
                    }

                    // Canciones favoritas (Liked Music)
                    if (page.likedSongs.isNotEmpty()) {
                        val isCurrentInLiked = playingRowMatcher(page.likedSongs, currentSong)
                        item(key = "liked_header") {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(
                                title = "Canciones favoritas",
                                strapline = "${page.likedSongs.size} canciones guardadas",
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(
                            items = page.likedSongs,
                            key = { _, song -> "liked:${song.videoId}" },
                        ) { _, song ->
                            val current = isCurrentInLiked(song)
                            LibrarySongRow(
                                song = song,
                                isCurrent = current,
                                isPlaying = current && isPlaying,
                                isLiked = isSongLiked?.invoke(song) ?: true,
                                downloadState = downloadStates[song.videoId],
                                isDownloaded = song.videoId in downloadedIds || !song.localUri.isNullOrBlank(),
                                onClick = {
                                    onSongClick(song, page.likedSongs, "Canciones favoritas")
                                },
                                onDownload = onDownloadSong,
                                onCancelDownload = onCancelDownload,
                                onRetryDownload = onRetryDownload,
                                onDeleteDownload = onDeleteDownload,
                                onToggleFavorite = onSongFavoriteToggle?.let { { it(song) } },
                                onAddToPlaylist = onSongAddToPlaylist?.let { { it(song) } },
                                onLongPress = onSongLongPress?.let { { it(song) } },
                                onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(song) } },
                            )
                        }
                    }

                    // Canciones adicionales en biblioteca (si existen desde cuenta remota)
                    if (page.librarySongs.isNotEmpty()) {
                        val isCurrentInLibrary = playingRowMatcher(page.librarySongs, currentSong)
                        item(key = "library_songs_header") {
                            Spacer(Modifier.height(18.dp))
                            SectionHeader(
                                title = "En tu biblioteca",
                                strapline = "${page.librarySongs.size} pistas",
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        itemsIndexed(
                            items = page.librarySongs,
                            key = { _, song -> "lib:${song.videoId}" },
                        ) { _, song ->
                            val current = isCurrentInLibrary(song)
                            LibrarySongRow(
                                song = song,
                                isCurrent = current,
                                isPlaying = current && isPlaying,
                                isLiked = isSongLiked?.invoke(song) ?: false,
                                downloadState = downloadStates[song.videoId],
                                isDownloaded = song.videoId in downloadedIds || !song.localUri.isNullOrBlank(),
                                onClick = {
                                    onSongClick(song, page.librarySongs, "Biblioteca")
                                },
                                onDownload = onDownloadSong,
                                onCancelDownload = onCancelDownload,
                                onRetryDownload = onRetryDownload,
                                onDeleteDownload = onDeleteDownload,
                                onToggleFavorite = onSongFavoriteToggle?.let { { it(song) } },
                                onAddToPlaylist = onSongAddToPlaylist?.let { { it(song) } },
                                onLongPress = onSongLongPress?.let { { it(song) } },
                                onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(song) } },
                            )
                        }
                    }

                    // Estado orientativo cuando aún no hay canciones favoritas ni álbumes guardados
                    if (page.likedSongs.isEmpty() && page.librarySongs.isEmpty() && playlistsShelf.items.isEmpty()) {
                        item(key = "empty_library_hint") {
                            MessageState(
                                message = "Tu biblioteca está lista. Pulsa «Nueva playlist» para crear tu primera lista o marca canciones con el icono ♥ en Inicio, Explorar, Buscar o Reproductor.",
                                actionLabel = if (onNewPlaylist != null) "Crear playlist" else null,
                                onAction = onNewPlaylist,
                                fillHeight = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val LIBRARY_SHELF_PREVIEW_LIMIT = 6

/**
 * Lista de accesos rápidos en la parte superior de `LibraryScreen` (BitChord 1.8.1 original).
 */
@Composable
fun LibraryLinkList(
    items: List<LibraryLink>,
    onItemClick: (LibraryLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 8.dp),
    ) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onItemClick(item) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(item.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.subtitle.isNotBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = BitChordIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 64.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                )
            }
        }
    }
}

/**
 * Estante horizontal de Playlists en `LibraryScreen` (BitChord 1.8.1 original).
 * Comienza con `NewShelfCard` ("Nueva playlist") seguido de las playlists fijadas y creadas.
 */
@Composable
internal fun PlaylistShelf(
    shelf: HomeShelf,
    pinnedPlaylists: Set<String>,
    onNewPlaylist: (() -> Unit)?,
    onItemClick: (ShelfItem) -> Unit,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onShowAll: (() -> Unit)? = null,
) {
    val ordered = remember(shelf.items, pinnedPlaylists) {
        val (pinned, rest) = shelf.items.partition { it.browseId in pinnedPlaylists }
        pinned + rest
    }
    Column(modifier = Modifier.padding(top = 18.dp)) {
        SectionHeader(
            title = "Playlists",
            strapline = if (ordered.isEmpty()) "Crea y organiza tus listas" else "${ordered.size} listas",
            onShowAll = onShowAll,
        )
        Spacer(Modifier.height(10.dp))
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            if (onNewPlaylist != null) {
                item(key = "new_playlist_card") {
                    NewShelfCard(
                        icon = BitChordIcons.Plus,
                        label = "Nueva playlist",
                        subtitle = "Crear lista",
                        onClick = onNewPlaylist,
                    )
                }
            }
            items(
                items = ordered.take(LIBRARY_SHELF_PREVIEW_LIMIT),
                key = { it.browseId ?: it.title },
            ) { item ->
                ShelfCard(
                    item = item,
                    isPinned = item.browseId in pinnedPlaylists,
                    onClick = { onItemClick(item) },
                    onPlay = onItemPlay?.let { play -> { play(item) } },
                    onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

/**
 * Estante horizontal de colecciones guardadas en `LibraryScreen` (Álbumes, Artistas).
 */
@Composable
internal fun LibraryGridShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onShowAll: () -> Unit,
) {
    val preview = remember(shelf.items) { shelf.items.take(LIBRARY_SHELF_PREVIEW_LIMIT) }
    val hasMore = shelf.items.size > preview.size
    Column(modifier = Modifier.padding(top = 22.dp)) {
        SectionHeader(
            title = localizeLibraryShelfTitle(shelf.title),
            strapline = shelf.subtitle,
            onShowAll = if (hasMore) onShowAll else null,
        )
        Spacer(Modifier.height(10.dp))
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
        ) {
            items(
                items = preview,
                key = { it.browseId ?: "${it.title}_${it.subtitle}" },
            ) { item ->
                ShelfCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    onPlay = onItemPlay?.let { play -> { play(item) } },
                    onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

/**
 * Página completa en cuadrícula (`LibraryGridPage`) al pulsar "Ver todo" en un estante de `LibraryScreen`
 * (BitChord 1.8.1 original).
 */
@Composable
fun LibraryGridPage(
    shelf: HomeShelf,
    listState: LazyListState,
    onItemClick: (ShelfItem) -> Unit,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    sort: LibrarySort = LibrarySort.DEFAULT,
    onSortChange: ((LibrarySort) -> Unit)? = null,
    pinnedPlaylists: Set<String> = emptySet(),
    onNewPlaylist: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
) {
    val sorted = remember(shelf.items, sort, pinnedPlaylists) {
        val base = shelf.items.orderedBy(sort)
        if (pinnedPlaylists.isEmpty()) base
        else {
            val (pinned, rest) = base.partition { it.browseId in pinnedPlaylists }
            pinned + rest
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val spec = libraryGrid(maxWidth - PAGE_GUTTER * 2)
        val entries = remember(sorted, onNewPlaylist) {
            buildList<ShelfItem?> {
                if (onNewPlaylist != null) add(null)
                addAll(sorted)
            }
        }
        val rows = remember(entries, spec.columns) { entries.chunked(spec.columns) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "grid_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = PAGE_GUTTER,
                            end = PAGE_GUTTER,
                            top = 10.dp,
                            bottom = 14.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (onBack != null) {
                            Surface(
                                onClick = onBack,
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color.White.copy(alpha = 0.14f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Volver a Biblioteca",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        text = "Biblioteca",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                        Text(
                            text = localizeLibraryShelfTitle(shelf.title),
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    if (onSortChange != null) {
                        LibrarySortButton(
                            current = sort,
                            onSelect = onSortChange,
                        )
                    }
                }
            }

            items(rows) { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PAGE_GUTTER)
                        .padding(bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(LIBRARY_GRID_SPACING),
                ) {
                    row.forEach { item ->
                        if (item == null) {
                            NewShelfCard(
                                icon = BitChordIcons.Plus,
                                label = "Nueva playlist",
                                subtitle = "Crear lista",
                                onClick = { onNewPlaylist?.invoke() },
                                modifier = Modifier.width(spec.cardWidth),
                            )
                        } else {
                            ShelfCard(
                                item = item,
                                isPinned = item.browseId in pinnedPlaylists,
                                modifier = Modifier.width(spec.cardWidth),
                                onClick = { onItemClick(item) },
                                onPlay = onItemPlay?.let { play -> { play(item) } },
                                onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySortButton(
    current: LibrarySort,
    onSelect: (LibrarySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.07f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SortByAlpha,
                    contentDescription = "Ordenar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = when (current) {
                        LibrarySort.DEFAULT -> "Orden predeterminado"
                        LibrarySort.TITLE_ASC -> "Título (A–Z)"
                        LibrarySort.TITLE_DESC -> "Título (Z–A)"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibrarySort.entries.forEach { option ->
                val label = when (option) {
                    LibrarySort.DEFAULT -> "Orden predeterminado"
                    LibrarySort.TITLE_ASC -> "Título (A–Z)"
                    LibrarySort.TITLE_DESC -> "Título (Z–A)"
                }
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (option == current) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * Fila de canción en `LibraryScreen` con soporte de reproducción, favorito ♥ y añadir a playlist.
 */
@Composable
internal fun LibrarySongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onClick: () -> Unit,
    downloadState: DownloadState? = null,
    isDownloaded: Boolean = false,
    onDownload: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
) {
    SwipeToQueueBox(
        onSwipeToQueue = onSwipeToQueue,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PAGE_GUTTER, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isCurrent) PlayingAccent.copy(alpha = 0.14f)
                    else Color.White.copy(alpha = 0.03f)
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        val artShape = RoundedCornerShape(7.dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailBorder(artShape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = song.thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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
            val subtitle = buildString {
                append(song.artist)
                if (!song.durationText.isNullOrBlank()) {
                    append(" • ")
                    append(song.durationText)
                }
                if (isDownloaded || !song.localUri.isNullOrBlank()) {
                    append(" • ✓ Offline")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onDownload != null) {
            SongDownloadActionButton(
                song = song,
                downloadState = downloadState,
                isDownloaded = isDownloaded || !song.localUri.isNullOrBlank(),
                onDownload = onDownload,
                onCancel = { onCancelDownload?.invoke(it) },
                onRetry = { onRetryDownload?.invoke(it) ?: onDownload(it) },
                onDelete = { onDeleteDownload?.invoke(it) },
            )
        }

        if (onToggleFavorite != null) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isLiked) "Quitar de favoritos" else "Guardar en favoritos",
                    tint = if (isLiked) Color(0xFFFF4081) else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (onAddToPlaylist != null) {
            IconButton(
                onClick = onAddToPlaylist,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Añadir a playlist",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.14f))
                .clickable(onClick = onClick),
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

internal fun List<ShelfItem>.orderedBy(sort: LibrarySort): List<ShelfItem> = when (sort) {
    LibrarySort.DEFAULT -> this
    LibrarySort.TITLE_ASC -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    LibrarySort.TITLE_DESC -> sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
}

private fun localizeLibraryShelfTitle(title: String): String = when {
    title.equals("Playlists", ignoreCase = true) -> "Playlists"
    title.equals("Albums", ignoreCase = true) -> "Álbumes guardados"
    title.equals("Artists", ignoreCase = true) -> "Artistas guardados"
    title.equals("Subscriptions", ignoreCase = true) -> "Suscripciones"
    title.equals("Podcasts", ignoreCase = true) -> "Podcasts"
    else -> title
}

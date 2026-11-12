package com.divinedeck.launcher.feature.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.ui.components.DeletePlaylistConfirmDialog
import com.music.bitchord.ui.components.NewPlaylistForm
import com.music.bitchord.ui.components.PlaylistContextMenuDialog
import com.music.bitchord.ui.components.PlaylistPickerSheet
import com.music.bitchord.ui.components.RenamePlaylistForm
import com.music.bitchord.ui.screens.BrowseCollectionDetailScreen
import com.music.bitchord.ui.screens.LibraryGridPage
import com.music.bitchord.ui.screens.LibraryScreen

/**
 * Panel de Biblioteca (`BIBLIOTECA`) del Centro Multimedia de DIVINE DECK (Fase 06.5B).
 *
 * Conecta directamente las pantallas y diálogos originales de Biblioteca y Playlists de BitChord 1.8.1:
 * - [LibraryScreen]
 * - [LibraryGridPage]
 * - [BrowseCollectionDetailScreen]
 * - [PlaylistPickerSheet], [NewPlaylistForm], [RenamePlaylistForm], [DeletePlaylistConfirmDialog]
 */
@Composable
fun MusicLibraryPane(
    viewModel: MusicLibraryViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelect: (Song) -> Unit,
    onPlayQueue: ((List<Song>, Int) -> Unit)? = null,
    onOpenDownloadsCenter: (() -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.loadLocalDeviceSongs(context)
    }

    val libraryState by viewModel.libraryState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val librarySort by viewModel.librarySort.collectAsState()
    val pinnedPlaylists by viewModel.pinnedPlaylists.collectAsState()
    val localFolders by viewModel.localFolders.collectAsState()
    val userPlaylistsState by viewModel.userPlaylistsState.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val savedCollections by viewModel.savedCollections.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val savedDownloads by viewModel.savedDownloads.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()

    val selectedShelfForGrid by viewModel.selectedShelfForGrid.collectAsState()
    val selectedCollection by viewModel.selectedCollection.collectAsState()

    val collectionListState = rememberLazyListState()
    val gridListState = rememberLazyListState()

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var contextMenuTarget by remember { mutableStateOf<ShelfItem?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) }

    val collection = selectedCollection
    val gridShelf = selectedShelfForGrid

    Box(modifier = modifier.fillMaxSize()) {
        when {
            collection != null -> {
                BackHandler {
                    viewModel.closeCollection()
                }
                val isEditable = viewModel.isEditablePlaylist(collection.browseId)
                val isSaved = remember(collection.browseId, savedCollections) {
                    viewModel.isCollectionSaved(collection.browseId)
                }
                val canSaveCollection = !isEditable &&
                    collection.browseId != MusicLibraryViewModel.BROWSE_ID_LIKED &&
                    collection.browseId != MusicLibraryViewModel.BROWSE_ID_DOWNLOADS &&
                    collection.browseId != MusicLibraryViewModel.BROWSE_ID_LOCAL_ALL

                BrowseCollectionDetailScreen(
                    title = collection.title,
                    subtitle = collection.subtitle,
                    thumbnailUrl = collection.thumbnailUrl,
                    songsState = collection.songsState,
                    listState = collectionListState,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onBack = { viewModel.closeCollection() },
                    onSongClick = { s ->
                        val songs = (collection.songsState as? com.music.bitchord.data.model.UiState.Success)?.data
                        if (onPlayQueue != null && !songs.isNullOrEmpty()) {
                            val idx = songs.indexOfFirst { it.videoId == s.videoId }.coerceAtLeast(0)
                            onPlayQueue(songs, idx)
                        } else {
                            onSongSelect(s)
                        }
                    },
                    onPlayAll = { songs ->
                        if (songs.isNotEmpty()) {
                            if (onPlayQueue != null) onPlayQueue(songs, 0) else onSongSelect(songs.first())
                        }
                    },
                    onShuffle = { songs ->
                        if (songs.isNotEmpty()) {
                            val shuffled = songs.shuffled()
                            if (onPlayQueue != null) onPlayQueue(shuffled, 0) else onSongSelect(shuffled.first())
                        }
                    },
                    onRetry = { viewModel.retryCollection() },
                    sections = collection.sections,
                    onSectionItemClick = { item ->
                        viewModel.onShelfItemClick(
                            item = item,
                            onPlaySong = onSongSelect,
                        )
                    },
                    isCollectionSaved = isSaved,
                    onToggleSaveCollection = if (canSaveCollection) {
                        {
                            viewModel.toggleSaveCollection(
                                browseId = collection.browseId,
                                title = collection.title,
                                subtitle = collection.subtitle,
                                thumbnailUrl = collection.thumbnailUrl,
                            )
                        }
                    } else {
                        null
                    },
                    isEditablePlaylist = isEditable,
                    onRenamePlaylist = if (isEditable) {
                        { renameTarget = collection.browseId to collection.title }
                    } else {
                        null
                    },
                    onDeletePlaylist = if (isEditable) {
                        { deleteTarget = collection.browseId to collection.title }
                    } else {
                        null
                    },
                    isSongLiked = { song ->
                        likedSongs.any { it.videoId == song.videoId }
                    },
                    onToggleLikeSong = { song ->
                        viewModel.toggleFavoriteSong(song)
                    },
                    onAddSongToPlaylist = { song ->
                        songForPlaylistPicker = song
                    },
                    onRemoveSongFromPlaylist = if (isEditable) {
                        { song ->
                            viewModel.removeSongFromPlaylist(collection.browseId, song.videoId)
                        }
                    } else {
                        null
                    },
                    downloadStates = activeDownloads,
                    downloadedIds = savedDownloads.keys,
                    onDownloadSong = { song ->
                        viewModel.downloadSong(context, song)
                    },
                    onDownloadAll = { songs ->
                        viewModel.downloadAll(context, songs)
                    },
                    onCancelDownload = { videoId ->
                        viewModel.cancelDownload(videoId)
                    },
                    onRetryDownload = { song ->
                        viewModel.retryDownload(context, song.videoId, song)
                    },
                    onDeleteDownload = { videoId ->
                        viewModel.deleteDownload(context, videoId)
                    },
                    onSwipeToQueue = onSwipeToQueue,
                    contentPadding = PaddingValues(bottom = 120.dp),
                )
            }

            gridShelf != null -> {
                BackHandler {
                    viewModel.closeShelfGrid()
                }
                LibraryGridPage(
                    shelf = gridShelf,
                    listState = gridListState,
                    onItemClick = { item ->
                        viewModel.onShelfItemClick(
                            item = item,
                            onPlaySong = onSongSelect,
                        )
                    },
                    onItemPlay = { item ->
                        viewModel.onShelfItemPlay(
                            item = item,
                            onPlaySong = onSongSelect,
                        )
                    },
                    onItemLongPress = { item ->
                        contextMenuTarget = item
                    },
                    onBack = { viewModel.closeShelfGrid() },
                    sort = librarySort,
                    onSortChange = { viewModel.setLibrarySort(it) },
                    pinnedPlaylists = pinnedPlaylists,
                    onNewPlaylist = if (gridShelf.title.equals("Playlists", ignoreCase = true)) {
                        { showCreatePlaylistDialog = true }
                    } else {
                        null
                    },
                    contentPadding = PaddingValues(bottom = 120.dp),
                )
            }

            else -> {
                LibraryScreen(
                    signedIn = viewModel.isSignedIn,
                    state = libraryState,
                    listState = listState,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = { song, _, sourceTitle ->
                        onSongSelect(
                            song.copy(
                                playbackSource = sourceTitle ?: "Biblioteca",
                                playbackSourceType = PlaybackSourceType.BROWSE,
                            )
                        )
                    },
                    onShelfItemClick = { item ->
                        viewModel.onShelfItemClick(
                            item = item,
                            onPlaySong = onSongSelect,
                        )
                    },
                    onRetry = { viewModel.refreshLibrary(context) },
                    onSongFavoriteToggle = { song ->
                        viewModel.toggleFavoriteSong(song)
                    },
                    onSongAddToPlaylist = { song ->
                        songForPlaylistPicker = song
                    },
                    isSongLiked = { song ->
                        likedSongs.any { it.videoId == song.videoId }
                    },
                    onShelfItemPlay = { item ->
                        viewModel.onShelfItemPlay(
                            item = item,
                            onPlaySong = onSongSelect,
                        )
                    },
                    onShelfItemLongPress = { item ->
                        contextMenuTarget = item
                    },
                    onShowAllShelf = { shelf ->
                        viewModel.openShelfGrid(shelf)
                    },
                    onNewPlaylist = {
                        showCreatePlaylistDialog = true
                    },
                    refreshing = refreshing,
                    onRefresh = { viewModel.refreshLibrary(context) },
                    contentPadding = PaddingValues(bottom = 120.dp),
                    title = "Biblioteca",
                    pinnedPlaylists = pinnedPlaylists,
                    localFolders = localFolders,
                    downloadedSongs = downloadedSongs,
                    downloadStates = activeDownloads,
                    downloadedIds = savedDownloads.keys,
                    onDownloadSong = { song ->
                        viewModel.downloadSong(context, song)
                    },
                    onCancelDownload = { videoId ->
                        viewModel.cancelDownload(videoId)
                    },
                    onRetryDownload = { song ->
                        viewModel.retryDownload(context, song.videoId, song)
                    },
                    onDeleteDownload = { videoId ->
                        viewModel.deleteDownload(context, videoId)
                    },
                    onOpenDownloadsClick = onOpenDownloadsCenter ?: {
                        viewModel.openCollection(
                            browseId = MusicLibraryViewModel.BROWSE_ID_DOWNLOADS,
                            title = "Canciones descargadas (Offline)",
                            subtitle = "${downloadedSongs.size} canciones disponibles sin Internet",
                            thumbnailUrl = downloadedSongs.firstOrNull()?.thumbnailUrl,
                        )
                    },
                    onSwipeToQueue = onSwipeToQueue,
                )
            }
        }
    }

    // 1. Diálogo para crear nueva playlist
    if (showCreatePlaylistDialog) {
        Dialog(onDismissRequest = { showCreatePlaylistDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(vertical = 8.dp)) {
                    NewPlaylistForm(
                        onCreate = { title, privacy ->
                            val created = viewModel.createPlaylist(title, privacy)
                            showCreatePlaylistDialog = false
                            viewModel.openCollection(
                                browseId = "${MusicLibraryViewModel.LOCAL_PLAYLIST_PREFIX}${created.id}",
                                title = created.name,
                                subtitle = "Playlist personal • 0 canciones",
                                thumbnailUrl = null,
                            )
                        },
                        onDismiss = { showCreatePlaylistDialog = false },
                    )
                }
            }
        }
    }

    // 2. Selector de playlists para añadir una canción
    val pickerSong = songForPlaylistPicker
    if (pickerSong != null) {
        val currentPlaylists = (userPlaylistsState as? UiState.Success)?.data.orEmpty()
        PlaylistPickerSheet(
            playlists = currentPlaylists,
            songToAdd = pickerSong,
            onAddToPlaylists = { selectedPlaylists ->
                viewModel.addSongToUserPlaylists(pickerSong, selectedPlaylists)
                songForPlaylistPicker = null
            },
            onCreatePlaylist = { title, privacy ->
                viewModel.createPlaylist(title, privacy, initialSong = pickerSong)
                songForPlaylistPicker = null
            },
            onDismiss = { songForPlaylistPicker = null },
        )
    }

    // 3. Menú contextual al mantener pulsado un elemento de la Biblioteca
    val menuTarget = contextMenuTarget
    if (menuTarget != null) {
        val browseId = menuTarget.browseId
        val isEditable = viewModel.isEditablePlaylist(browseId)
        val isPinned = browseId != null && browseId in pinnedPlaylists
        PlaylistContextMenuDialog(
            title = menuTarget.title,
            isPinned = isPinned,
            isEditablePlaylist = isEditable,
            onPlay = {
                viewModel.onShelfItemPlay(
                    item = menuTarget,
                    onPlaySong = onSongSelect,
                )
            },
            onTogglePin = {
                if (browseId != null) {
                    viewModel.togglePinPlaylist(browseId)
                }
            },
            onRename = if (isEditable && browseId != null) {
                { renameTarget = browseId to menuTarget.title }
            } else {
                null
            },
            onDelete = if (isEditable && browseId != null) {
                { deleteTarget = browseId to menuTarget.title }
            } else {
                null
            },
            onDismiss = { contextMenuTarget = null },
        )
    }

    // 4. Diálogo para renombrar playlist
    val currentRename = renameTarget
    if (currentRename != null) {
        RenamePlaylistForm(
            currentTitle = currentRename.second,
            onRename = { newTitle ->
                viewModel.renamePlaylist(currentRename.first, newTitle)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    // 5. Diálogo de confirmación para eliminar playlist
    val currentDelete = deleteTarget
    if (currentDelete != null) {
        DeletePlaylistConfirmDialog(
            playlistTitle = currentDelete.second,
            onConfirmDelete = {
                viewModel.deletePlaylist(currentDelete.first)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

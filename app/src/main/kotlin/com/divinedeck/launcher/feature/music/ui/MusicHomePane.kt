package com.divinedeck.launcher.feature.music.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.DownloadState
import com.music.bitchord.ui.screens.BrowseCollectionDetailScreen
import com.music.bitchord.ui.screens.HomeScreen

/**
 * Panel de Inicio (Home) del Centro Multimedia de DIVINE DECK (Fase 06.5A).
 *
 * Conecta directamente la pantalla original [HomeScreen] de BitChord 1.8.1 y el visor de
 * colecciones [BrowseCollectionDetailScreen] con [MusicHomeViewModel] y el servicio de
 * reproducción unificado (`MediaControllerRepository`).
 */
@Composable
fun MusicHomePane(
    viewModel: MusicHomeViewModel,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelect: (Song) -> Unit,
    onPlayQueue: ((List<Song>, Int) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    isCollectionSaved: ((String) -> Boolean)? = null,
    onToggleSaveCollection: ((browseId: String, title: String, subtitle: String, thumbnailUrl: String?) -> Unit)? = null,
    isSongLiked: ((Song) -> Boolean)? = null,
    onToggleLikeSong: ((Song) -> Unit)? = null,
    onAddSongToPlaylist: ((Song) -> Unit)? = null,
    downloadStates: Map<String, DownloadState> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    onDownloadAll: ((List<Song>) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val homeState by viewModel.homeState.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val recentsViewType by viewModel.recentsViewType.collectAsState()
    val selectedCollection by viewModel.selectedCollection.collectAsState()

    val collectionListState = rememberLazyListState()

    val collection = selectedCollection
    if (collection != null) {
        BackHandler {
            viewModel.closeCollection()
        }
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
                    shelfTitle = collection.title,
                    onPlaySong = onSongSelect,
                )
            },
            isCollectionSaved = isCollectionSaved?.invoke(collection.browseId) == true,
            onToggleSaveCollection = if (onToggleSaveCollection != null) {
                {
                    onToggleSaveCollection(
                        collection.browseId,
                        collection.title,
                        collection.subtitle,
                        collection.thumbnailUrl,
                    )
                }
            } else {
                null
            },
            isSongLiked = isSongLiked,
            onToggleLikeSong = onToggleLikeSong,
            onAddSongToPlaylist = onAddSongToPlaylist,
            downloadStates = downloadStates,
            downloadedIds = downloadedIds,
            onDownloadSong = onDownloadSong,
            onDownloadAll = onDownloadAll,
            onCancelDownload = onCancelDownload,
            onRetryDownload = onRetryDownload,
            onDeleteDownload = onDeleteDownload,
            onSwipeToQueue = onSwipeToQueue,
        )
    } else {
        HomeScreen(
            state = homeState,
            listState = listState,
            currentSong = currentSong,
            isPlaying = isPlaying,
            title = "Escuchar ahora",
            onItemClick = { item, shelfTitle ->
                viewModel.onShelfItemClick(
                    item = item,
                    shelfTitle = shelfTitle,
                    onPlaySong = onSongSelect,
                )
            },
            onItemPlay = { item, shelfTitle ->
                viewModel.onShelfItemPlay(
                    item = item,
                    shelfTitle = shelfTitle,
                    onPlaySong = onSongSelect,
                )
            },
            onRetry = { viewModel.loadHome() },
            refreshing = refreshing,
            onRefresh = { viewModel.refreshHome() },
            contentPadding = PaddingValues(bottom = 120.dp),
            onLoadMore = { viewModel.loadMoreHome() },
            loadingMore = loadingMore,
            recentsViewType = recentsViewType,
            onRecentsViewTypeChange = { viewModel.setRecentsViewType(it) },
        )
    }
}

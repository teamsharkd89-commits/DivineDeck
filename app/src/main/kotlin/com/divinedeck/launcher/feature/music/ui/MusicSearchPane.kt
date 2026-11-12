package com.divinedeck.launcher.feature.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.music.bitchord.ui.components.SwipeToQueueBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.BrowseItem
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.DownloadState
import com.music.bitchord.ui.components.SongDownloadActionButton
import com.music.bitchord.ui.icons.BitChordIcons

/**
 * Panel de búsqueda original de BitChord para DIVINE DECK.
 * Adaptado con ergonomía automotriz para la Samsung Galaxy Tab A11 SM-X133 (1340×800 horizontal).
 */
@Composable
fun MusicSearchPane(
    viewModel: MusicSearchViewModel,
    onSongSelect: (Song) -> Unit,
    isSongLiked: ((Song) -> Boolean)? = null,
    onToggleLikeSong: ((Song) -> Unit)? = null,
    onAddSongToPlaylist: ((Song) -> Unit)? = null,
    isCollectionSaved: ((String) -> Boolean)? = null,
    onToggleSaveCollection: ((BrowseItem) -> Unit)? = null,
    onBrowseItemClick: ((BrowseItem) -> Unit)? = null,
    downloadStates: Map<String, DownloadState> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        // 1. Barra de entrada de búsqueda automotriz
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onQueryChanged(it) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                placeholder = {
                    Text(
                        text = "Buscar canciones, artistas, álbumes...",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Buscar",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(22.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onClearQuery() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Borrar búsqueda",
                                tint = Color.White.copy(alpha = 0.60f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF161922),
                    unfocusedContainerColor = Color(0xFF12141A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E5FF)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.submitQuery()
                    }
                )
            )
        }

        Spacer(Modifier.height(10.dp))

        // 2. Chips de filtros de búsqueda (Todas, Canciones, Videos, Álbumes, Artistas)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(SearchFilter.entries) { itemFilter ->
                val isSelected = itemFilter == filter
                Surface(
                    onClick = { viewModel.onFilterSelected(itemFilter) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.20f) else Color(0xFF1E212B),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Text(
                        text = itemFilter.label,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // 3. Sugerencias rápidas (si las hay)
        AnimatedVisibility(
            visible = suggestions.isNotEmpty() && query.isNotBlank() && uiState !is SearchUiState.Success,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions.take(5)) { suggestion ->
                    Surface(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.onSuggestionSelected(suggestion)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.06f)
                    ) {
                        Text(
                            text = suggestion,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.80f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // 4. Área de contenido dinámico (Resultados / Estados)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is SearchUiState.Empty -> {
                    SearchEmptyState()
                }

                is SearchUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E5FF),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Consultando catálogo de YouTube Music...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }

                is SearchUiState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("Reintentar", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is SearchUiState.Success -> {
                    if (state.results.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Sin resultados para \"${state.query}\"",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.70f)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Intenta buscar con otro artista o título",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                    } else {
                        SearchResultsList(
                            results = state.results,
                            onSongSelect = onSongSelect,
                            isSongLiked = isSongLiked,
                            onToggleLikeSong = onToggleLikeSong,
                            onAddSongToPlaylist = onAddSongToPlaylist,
                            isCollectionSaved = isCollectionSaved,
                            onToggleSaveCollection = onToggleSaveCollection,
                            onBrowseItemClick = onBrowseItemClick,
                            downloadStates = downloadStates,
                            downloadedIds = downloadedIds,
                            onDownloadSong = onDownloadSong,
                            onCancelDownload = onCancelDownload,
                            onRetryDownload = onRetryDownload,
                            onDeleteDownload = onDeleteDownload,
                            onSwipeToQueue = onSwipeToQueue,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E212B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color(0xFF00E5FF).copy(alpha = 0.8f),
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Buscador de Música BitChord",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Escribe para buscar millones de canciones en línea con streaming directo",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onSongSelect: (Song) -> Unit,
    isSongLiked: ((Song) -> Boolean)? = null,
    onToggleLikeSong: ((Song) -> Unit)? = null,
    onAddSongToPlaylist: ((Song) -> Unit)? = null,
    isCollectionSaved: ((String) -> Boolean)? = null,
    onToggleSaveCollection: ((BrowseItem) -> Unit)? = null,
    onBrowseItemClick: ((BrowseItem) -> Unit)? = null,
    downloadStates: Map<String, DownloadState> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    onDownloadSong: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onSwipeToQueue: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = results,
            key = { result ->
                when (result) {
                    is SearchResult.TopTrack -> "top_${result.song.videoId}"
                    is SearchResult.Track -> "track_${result.song.videoId}"
                    is SearchResult.Browse -> "browse_${result.item.browseId}"
                }
            }
        ) { result ->
            when (result) {
                is SearchResult.TopTrack -> {
                    SongResultCard(
                        song = result.song,
                        isTopResult = true,
                        isLiked = isSongLiked?.invoke(result.song) == true,
                        onToggleLike = onToggleLikeSong?.let { { it(result.song) } },
                        onAddToPlaylist = onAddSongToPlaylist?.let { { it(result.song) } },
                        downloadState = downloadStates[result.song.videoId],
                        isDownloaded = result.song.videoId in downloadedIds || !result.song.localUri.isNullOrBlank(),
                        onDownload = onDownloadSong,
                        onCancelDownload = onCancelDownload,
                        onRetryDownload = onRetryDownload,
                        onDeleteDownload = onDeleteDownload,
                        onClick = { onSongSelect(result.song) },
                        onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(result.song) } },
                    )
                }
                is SearchResult.Track -> {
                    SongResultCard(
                        song = result.song,
                        isTopResult = false,
                        isLiked = isSongLiked?.invoke(result.song) == true,
                        onToggleLike = onToggleLikeSong?.let { { it(result.song) } },
                        onAddToPlaylist = onAddSongToPlaylist?.let { { it(result.song) } },
                        downloadState = downloadStates[result.song.videoId],
                        isDownloaded = result.song.videoId in downloadedIds || !result.song.localUri.isNullOrBlank(),
                        onDownload = onDownloadSong,
                        onCancelDownload = onCancelDownload,
                        onRetryDownload = onRetryDownload,
                        onDeleteDownload = onDeleteDownload,
                        onClick = { onSongSelect(result.song) },
                        onSwipeToQueue = onSwipeToQueue?.let { cb -> { cb(result.song) } },
                    )
                }
                is SearchResult.Browse -> {
                    BrowseResultCard(
                        item = result.item,
                        isSaved = isCollectionSaved?.invoke(result.item.browseId) == true,
                        onToggleSave = onToggleSaveCollection?.let { { it(result.item) } },
                        onClick = onBrowseItemClick?.let { { it(result.item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SongResultCard(
    song: Song,
    isTopResult: Boolean,
    onClick: () -> Unit,
    isLiked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    downloadState: DownloadState? = null,
    isDownloaded: Boolean = false,
    onDownload: ((Song) -> Unit)? = null,
    onCancelDownload: ((String) -> Unit)? = null,
    onRetryDownload: ((Song) -> Unit)? = null,
    onDeleteDownload: ((String) -> Unit)? = null,
    onSwipeToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SwipeToQueueBox(
        onSwipeToQueue = onSwipeToQueue,
        modifier = modifier.fillMaxWidth(),
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = if (isTopResult) Color(0xFF1E2433) else Color(0xFF141720),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (isTopResult) Color(0xFF00E5FF).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura de carátula en alta resolución
            val artUrl = remember(song.thumbnailUrl) {
                song.thumbnailUrl.artworkAt(ROW_ART_PX) ?: song.thumbnailUrl
            }
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222733)),
                contentAlignment = Alignment.Center
            ) {
                if (!artUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = song.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Metadatos
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (isTopResult) {
                    Text(
                        text = "MEJOR COINCIDENCIA",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                val subtitle = buildString {
                    append(song.artist)
                    if (!song.albumName.isNullOrBlank()) {
                        append(" • ")
                        append(song.albumName)
                    }
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
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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

            if (onToggleLike != null) {
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isLiked) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        contentDescription = if (isLiked) "Quitar de favoritas" else "Añadir a favoritas",
                        tint = if (isLiked) Color(0xFFFF4081) else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
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

            Spacer(Modifier.width(6.dp))

            // Botón de reproducción
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
}

@Composable
private fun BrowseResultCard(
    item: BrowseItem,
    isSaved: Boolean = false,
    onToggleSave: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF12141A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val browseArtUrl = remember(item.thumbnailUrl) {
                item.thumbnailUrl.artworkAt(CARD_ART_PX) ?: item.thumbnailUrl
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF222733)),
                contentAlignment = Alignment.Center
            ) {
                if (!browseArtUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = browseArtUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onToggleSave != null) {
                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (isSaved) BitChordIcons.HeartFilled else BitChordIcons.Heart,
                        contentDescription = if (isSaved) "Quitar de biblioteca" else "Guardar en biblioteca",
                        tint = if (isSaved) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

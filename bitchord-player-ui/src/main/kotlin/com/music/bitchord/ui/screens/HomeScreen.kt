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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.ui.components.HERO_CARD_RATIO
import com.music.bitchord.ui.components.MessageState
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PlayingAccent
import com.music.bitchord.ui.components.PullToRefresh
import com.music.bitchord.ui.components.PullToRefreshState
import com.music.bitchord.ui.components.RowMoreButton
import com.music.bitchord.ui.components.SHELF_CARD_WIDTH
import com.music.bitchord.ui.components.SearchPlayingBars
import com.music.bitchord.ui.components.ShelfRow
import com.music.bitchord.ui.components.ShimmerBox
import com.music.bitchord.ui.components.contextClick
import com.music.bitchord.ui.components.feedMoreSkeleton
import com.music.bitchord.ui.components.feedSkeleton
import com.music.bitchord.ui.components.heroCardWidth
import com.music.bitchord.ui.components.longPressMenuClickable
import com.music.bitchord.ui.components.recentlyPlayedSkeleton
import com.music.bitchord.ui.components.rememberPullToRefreshState
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.components.trackColumnWidth
import com.music.bitchord.ui.icons.BitChordIcons
import com.music.bitchord.ui.player.MeshGradient
import com.music.bitchord.ui.theme.rememberArtworkPalette

/**
 * Pantalla original de Inicio (Home) de BitChord 1.8.1 integrada en DIVINE DECK.
 *
 * Muestra las recomendaciones reales de YouTube Music (`FEmusic_home`, `FEmusic_new_releases`,
 * `FEmusic_explore`), estantes destacados (HeroShelf), selecciones rápidas en columnas de 4 pistas
 * (RecentShelf) y carruseles de álbumes, listas y canciones (Shelf), con soporte de paginación
 * continua (`onLoadMore`) y actualización mediante arrastre (`PullToRefresh`).
 */
@Composable
fun HomeScreen(
    state: UiState<List<HomeShelf>>,
    listState: LazyListState,
    currentSong: Song?,
    isPlaying: Boolean,
    signedIn: Boolean = false,
    onSignIn: () -> Unit = {},
    title: String = "Inicio",
    onItemClick: (item: ShelfItem, shelfTitle: String) -> Unit,
    onItemPlay: ((item: ShelfItem, shelfTitle: String) -> Unit)? = null,
    onQuickPlay: (() -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onRetry: () -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = onRetry,
    pullState: PullToRefreshState = rememberPullToRefreshState(),
    contentPadding: PaddingValues = PaddingValues(),
    onLoadMore: () -> Unit = {},
    loadingMore: Boolean = false,
    recentlyPlayedLoading: Boolean = false,
    recentsViewType: LibraryViewType = LibraryViewType.LIST,
    onRecentsViewTypeChange: ((LibraryViewType) -> Unit)? = null,
) {
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state, loadingMore) {
        if (nearEnd && state is UiState.Success && !loadingMore) onLoadMore()
    }

    var internalRecentsViewType by remember { mutableStateOf(recentsViewType) }
    val activeRecentsViewType = if (onRecentsViewTypeChange != null) recentsViewType else internalRecentsViewType
    val setRecentsViewType: (LibraryViewType) -> Unit = { newType ->
        internalRecentsViewType = newType
        onRecentsViewTypeChange?.invoke(newType)
    }

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        scrollableState = listState,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
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
                UiState.Loading -> feedSkeleton(
                    leadingTrackShelf = true,
                    leadingHeroShelf = true,
                )
                is UiState.Error -> item(key = "error") {
                    MessageState(
                        message = state.message,
                        actionLabel = "Reintentar",
                        onAction = onRetry,
                    )
                }
                is UiState.Success -> {
                    if (recentlyPlayedLoading) {
                        recentlyPlayedSkeleton()
                    }
                    val shelves = state.data
                    val heroIndex = shelves.indexOfFirst { it.isHeroCandidate() }
                    shelves.forEachIndexed { index, shelf ->
                        item(key = "shelf_${index}_${shelf.title}") {
                            val onShelfItemClick: (ShelfItem) -> Unit = { item ->
                                onItemClick(item, shelf.title)
                            }
                            val onShelfItemPlay: ((ShelfItem) -> Unit)? = onItemPlay?.let { play ->
                                { item -> play(item, shelf.title) }
                            }
                            when {
                                shelf.isRecentTrackShelf(index) -> RecentShelf(
                                    shelf = shelf,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    viewType = activeRecentsViewType,
                                    onViewTypeChange = setRecentsViewType,
                                    onItemClick = onShelfItemClick,
                                    onItemPlay = onShelfItemPlay,
                                    onItemLongPress = onItemLongPress,
                                )
                                index == heroIndex -> HeroShelf(
                                    shelf = shelf,
                                    onItemClick = onShelfItemClick,
                                    onItemPlay = onShelfItemPlay,
                                    onItemLongPress = onItemLongPress,
                                )
                                else -> Shelf(
                                    shelf = shelf,
                                    currentSong = currentSong,
                                    isPlaying = isPlaying,
                                    onItemClick = onShelfItemClick,
                                    onItemPlay = onShelfItemPlay,
                                    onItemLongPress = onItemLongPress,
                                )
                            }
                        }
                    }
                    if (loadingMore) {
                        feedMoreSkeleton()
                    }
                }
            }
        }
    }
}

private fun HomeShelf.isRecentTrackShelf(index: Int): Boolean {
    if (items.size < 4 || !items.all { it.videoId != null && it.browseId == null }) return false
    if (index == 0) return true
    val lower = title.lowercase()
    return "quick picks" in lower || "recently played" in lower || "listen again" in lower
}

private fun HomeShelf.isHeroCandidate(): Boolean =
    items.size >= 2 && items.any { it.browseId != null || it.videoId != null } && !isRecentTrackShelf(1)

@Composable
private fun RecentShelf(
    shelf: HomeShelf,
    currentSong: Song?,
    isPlaying: Boolean,
    viewType: LibraryViewType,
    onViewTypeChange: (LibraryViewType) -> Unit,
    onItemClick: (ShelfItem) -> Unit,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
) {
    val heading = if (shelf.title.equals("Quick picks", ignoreCase = true) ||
        shelf.title.equals("Recently played", ignoreCase = true)
    ) {
        "Recientes"
    } else {
        shelf.title
    }
    val columns = remember(shelf.items) { shelf.items.chunked(4) }
    val rowState = rememberLazyListState()
    val columnWidth = trackColumnWidth()

    val playingVideoId = remember(shelf.items, currentSong?.videoId, currentSong?.title, currentSong?.artist) {
        val current = currentSong ?: return@remember null
        shelf.items.firstOrNull { it.videoId == current.videoId }?.videoId
            ?: shelf.items.firstOrNull { it.title == current.title && current.artist in it.subtitle }?.videoId
    }

    Column(modifier = Modifier.padding(top = 20.dp)) {
        SectionHeader(
            title = heading,
            strapline = shelf.subtitle,
            showChevron = false,
            trailing = {
                val isList = viewType == LibraryViewType.LIST
                IconButton(
                    onClick = {
                        onViewTypeChange(if (isList) LibraryViewType.GRID else LibraryViewType.LIST)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isList) BitChordIcons.GridView else BitChordIcons.ListView,
                        contentDescription = if (isList) "Cambiar a cuadrícula" else "Cambiar a lista",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        if (viewType == LibraryViewType.LIST) {
            ShelfRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    items = columns,
                    key = { col -> col.first().let { "${it.videoId}_${it.title}" } },
                ) { col ->
                    Column(
                        modifier = Modifier.width(columnWidth),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        col.forEach { item ->
                            CompactTrackRow(
                                item = item,
                                isCurrent = item.videoId != null && item.videoId == playingVideoId,
                                isPlaying = isPlaying,
                                onClick = { onItemClick(item) },
                                onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                            )
                        }
                    }
                }
            }
        } else {
            ShelfRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = shelf.items,
                    key = { "${it.videoId}_${it.title}" },
                ) { item ->
                    ShelfCard(
                        item = item,
                        isCurrent = item.videoId != null && item.videoId == playingVideoId,
                        isPlaying = isPlaying,
                        onClick = { onItemClick(item) },
                        onPlay = onItemPlay?.let { play -> { play(item) } },
                        onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTrackRow(
    item: ShelfItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) PlayingAccent.copy(alpha = 0.08f) else Color.Transparent)
            .contextClick(onLongPress)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbShape = RoundedCornerShape(6.dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(thumbShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailBorder(thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.thumbnailUrl.artworkAt(ROW_ART_PX),
                contentDescription = null,
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
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onLongPress != null) {
            RowMoreButton(onClick = onLongPress)
        }
    }
}

@Composable
private fun HeroShelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
) {
    val cardWidth = heroCardWidth()
    Column(modifier = Modifier.padding(top = 24.dp)) {
        SectionHeader(
            title = shelf.title,
            strapline = shelf.subtitle,
            showChevron = false,
        )
        Spacer(Modifier.height(10.dp))
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(
                items = shelf.items.take(8),
                key = { "${it.videoId ?: it.browseId}_${it.title}" },
            ) { item ->
                HeroCard(
                    item = item,
                    shelfTitle = shelf.title,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onItemClick(item) },
                    onPlay = onItemPlay?.let { play -> { play(item) } } ?: { onItemClick(item) },
                    onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

@Composable
private fun HeroCard(
    item: ShelfItem,
    shelfTitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(HERO_CARD_RATIO)
            .clip(shape)
            .thumbnailBorder(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .contextClick(onLongPress)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        AsyncImage(
            model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                )
                .padding(start = 16.dp, end = if (onPlay != null) 54.dp else 16.dp, top = 36.dp, bottom = 14.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                    ),
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onPlay != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 14.dp),
            ) {
                HeroPlayButton(onClick = onPlay)
            }
        }
    }
}

@Composable
private fun HeroPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.18f, h * 0.08f)
                lineTo(w * 0.95f, h * 0.5f)
                lineTo(w * 0.18f, h * 0.92f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
    }
}

@Composable
internal fun Shelf(
    shelf: HomeShelf,
    onItemClick: (ShelfItem) -> Unit,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onItemPlay: ((ShelfItem) -> Unit)? = null,
    onItemLongPress: ((ShelfItem) -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
) {
    val playingVideoId = remember(shelf.items, currentSong?.videoId, currentSong?.title, currentSong?.artist) {
        val current = currentSong ?: return@remember null
        shelf.items.firstOrNull { it.videoId == current.videoId }?.videoId
            ?: shelf.items.firstOrNull {
                it.videoId != null && it.title == current.title && current.artist in it.subtitle
            }?.videoId
    }

    Column(modifier = Modifier.padding(top = 28.dp)) {
        SectionHeader(
            title = shelf.title,
            strapline = shelf.subtitle,
            showChevron = onHeaderClick != null,
            onClick = onHeaderClick,
        )
        Spacer(Modifier.height(10.dp))
        ShelfRow(
            contentPadding = PaddingValues(horizontal = PAGE_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = shelf.items,
                key = { "${it.videoId ?: it.browseId}_${it.title}" },
            ) { item ->
                ShelfCard(
                    item = item,
                    isCurrent = item.videoId != null && item.videoId == playingVideoId,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(item) },
                    onPlay = onItemPlay?.let { play -> { play(item) } },
                    onLongPress = onItemLongPress?.let { handler -> { handler(item) } },
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    strapline: String = "",
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    onShowAll: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = PAGE_GUTTER),
    ) {
        if (strapline.isNotBlank()) {
            Text(
                text = strapline,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showChevron) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = BitChordIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onShowAll != null) {
                Text(
                    text = "Ver todo",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable(onClick = onShowAll)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

/**
 * Tarjeta "Nueva playlist" situada al inicio del estante de Playlists en `LibraryScreen`
 * (BitChord 1.8.1 original).
 */
@Composable
internal fun NewShelfCard(
    icon: ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailBorder(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                lineHeight = 16.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ShelfCard(
    item: ShelfItem,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    isPinned: Boolean = false,
    modifier: Modifier = Modifier.width(SHELF_CARD_WIDTH),
    onClick: () -> Unit,
    onPlay: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val isArtist = item.browseId?.startsWith("UC") == true && item.videoId == null
    val artShape = if (isArtist) CircleShape else RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .contextClick(onLongPress)
            .longPressMenuClickable(onClick = onClick, onLongClick = onLongPress),
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .thumbnailBorder(artShape),
        ) {
            when {
                item.browseId == "local:all" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF134E5E), Color(0xFF71B280))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                item.browseId == "local:liked" || item.browseId == "VLLM" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                item.thumbnailUrl.isNullOrBlank() && item.browseId?.startsWith("local:playlist:") == true -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF1E3C72), Color(0xFF0097A7))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
                item.thumbnailUrl.isNullOrBlank() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                else -> {
                    var loaded by remember(item.thumbnailUrl) { mutableStateOf(false) }
                    if (!loaded) {
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = artShape,
                        )
                    }
                    AsyncImage(
                        model = item.thumbnailUrl.artworkAt(CARD_ART_PX),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        onSuccess = { loaded = true },
                        onError = { loaded = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
            } else if (!isArtist && onPlay != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                ) {
                    HeroPlayButton(onClick = onPlay)
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isPinned) {
                Icon(
                    imageVector = BitChordIcons.Pin,
                    contentDescription = "Fijada",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                ),
                color = if (isCurrent) PlayingAccent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (item.subtitle.isNotBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

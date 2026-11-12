package com.divinedeck.launcher.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.withoutRepeatsOf
import com.music.bitchord.data.settings.LibraryViewType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Estado de una colección (Álbum, Playlist o Artista) abierta desde INICIO o EXPLORAR.
 */
data class BrowseCollectionState(
    val browseId: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val songsState: UiState<List<Song>>,
    val sections: List<HomeShelf> = emptyList(),
)

/**
 * Interfaz de fuente de datos para Inicio (Home), permitiendo inyección limpia en pruebas unitarias
 * y conectando en producción directamente con [YtMusicRepository] de BitChord 1.8.1.
 */
interface HomeDataSource {
    val supplementBrowseIds: List<String>
    suspend fun home(): Result<HomeFeed>
    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>>
    suspend fun moreHome(token: String): Result<HomeFeed>
    suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage>
    suspend fun artistPage(browseId: String): Result<com.music.bitchord.data.model.ArtistPage>
}

object YtMusicHomeDataSource : HomeDataSource {
    override val supplementBrowseIds: List<String>
        get() = YtMusicRepository.HOME_SUPPLEMENT_BROWSE_IDS

    override suspend fun home(): Result<HomeFeed> = YtMusicRepository.home()

    override suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> =
        YtMusicRepository.homeSupplement(browseId)

    override suspend fun moreHome(token: String): Result<HomeFeed> =
        YtMusicRepository.moreHome(token)

    override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
        YtMusicRepository.browseSongs(browseId)

    override suspend fun artistPage(browseId: String): Result<com.music.bitchord.data.model.ArtistPage> =
        YtMusicRepository.artistPage(browseId)
}

/**
 * ViewModel para gestionar la pantalla original INICIO (Home) de BitChord 1.8.1 dentro de DIVINE DECK.
 */
class MusicHomeViewModel(
    private val dataSource: HomeDataSource = YtMusicHomeDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    autoLoadOnInit: Boolean = true,
) : ViewModel() {

    private val _homeState = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val homeState: StateFlow<UiState<List<HomeShelf>>> = _homeState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _pagingMore = MutableStateFlow(false)
    private val _pendingShelves = MutableStateFlow(0)

    val loadingMore: StateFlow<Boolean> =
        combine(_pagingMore, _pendingShelves) { paging, pending -> paging || pending > 0 }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _recentsViewType = MutableStateFlow(LibraryViewType.LIST)
    val recentsViewType: StateFlow<LibraryViewType> = _recentsViewType.asStateFlow()

    private val _selectedCollection = MutableStateFlow<BrowseCollectionState?>(null)
    val selectedCollection: StateFlow<BrowseCollectionState?> = _selectedCollection.asStateFlow()

    private var homeContinuation: String? = null
    private val homeSeenTitles = mutableSetOf<String>()
    private val loadGeneration = AtomicLong(0L)

    init {
        if (autoLoadOnInit) {
            loadHome()
        }
    }

    fun setRecentsViewType(viewType: LibraryViewType) {
        _recentsViewType.value = viewType
    }

    fun loadHome() {
        val generation = loadGeneration.incrementAndGet()
        _homeState.value = UiState.Loading
        homeContinuation = null
        synchronized(homeSeenTitles) { homeSeenTitles.clear() }
        _pagingMore.value = false
        val supplements = dataSource.supplementBrowseIds
        _pendingShelves.value = 1 + supplements.size

        viewModelScope.launch(ioDispatcher) {
            launch {
                try {
                    dataSource.home()
                        .onSuccess { feed ->
                            if (generation != loadGeneration.get()) return@onSuccess
                            homeContinuation = feed.continuation
                            publishHomeShelves(feed.shelves)
                            if (_homeState.value is UiState.Loading && feed.shelves.isEmpty()) {
                                _homeState.value = UiState.Error("No se encontraron recomendaciones disponibles en este momento")
                            }
                        }
                        .onFailure { failure ->
                            if (failure is CancellationException) throw failure
                            if (generation == loadGeneration.get() && _homeState.value !is UiState.Success) {
                                _homeState.value = UiState.Error(
                                    failure.localizedMessage ?: "Error al cargar el feed de Inicio de YouTube Music"
                                )
                            }
                        }
                } finally {
                    settlePendingShelf(generation)
                }
            }

            supplements.forEach { browseId ->
                launch {
                    try {
                        dataSource.homeSupplement(browseId).onSuccess { shelves ->
                            if (generation == loadGeneration.get()) {
                                publishHomeShelves(shelves)
                            }
                        }
                    } finally {
                        settlePendingShelf(generation)
                    }
                }
            }
        }
    }

    fun refreshHome() {
        if (_refreshing.value) return
        _refreshing.value = true
        val generation = loadGeneration.incrementAndGet()
        viewModelScope.launch(ioDispatcher) {
            try {
                dataSource.home().onSuccess { feed ->
                    if (generation != loadGeneration.get()) return@onSuccess
                    homeContinuation = feed.continuation
                    synchronized(homeSeenTitles) {
                        homeSeenTitles.clear()
                        val shelves = feed.shelves.withoutRepeatsOf(emptyList())
                            .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                        if (shelves.isNotEmpty()) {
                            _homeState.value = UiState.Success(shelves)
                        }
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadMoreHome() {
        val token = homeContinuation ?: return
        if (_pagingMore.value) return
        val generation = loadGeneration.get()
        _pagingMore.value = true
        viewModelScope.launch(ioDispatcher) {
            try {
                dataSource.moreHome(token).onSuccess { feed ->
                    if (generation == loadGeneration.get()) {
                        val existing = (_homeState.value as? UiState.Success)?.data ?: emptyList()
                        val added = synchronized(homeSeenTitles) {
                            feed.shelves.withoutRepeatsOf(existing)
                                .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
                        }
                        homeContinuation = feed.continuation.takeIf { added.isNotEmpty() }
                        if (added.isNotEmpty()) {
                            _homeState.value = UiState.Success(existing + added)
                        }
                    }
                }
            } finally {
                if (generation == loadGeneration.get()) {
                    _pagingMore.value = false
                }
            }
        }
    }

    /**
     * Convierte un [ShelfItem] con `videoId` en un [Song] listo para reproducirse en el motor unificado.
     */
    fun shelfItemToSong(item: ShelfItem, shelfTitle: String): Song? {
        val videoId = item.videoId ?: return null
        return Song(
            videoId = videoId,
            title = item.title,
            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
            thumbnailUrl = item.thumbnailUrl,
            playbackSource = shelfTitle,
            playbackSourceType = PlaybackSourceType.HOME,
        )
    }

    /**
     * Gestiona el clic en un elemento de estante de INICIO:
     * - Si tiene `videoId`, reproduce la pista inmediatamente.
     * - Si tiene `browseId`, abre la vista de detalle de la colección (Álbum, Playlist o Artista).
     */
    fun onShelfItemClick(
        item: ShelfItem,
        shelfTitle: String,
        onPlaySong: (Song) -> Unit,
    ) {
        val song = shelfItemToSong(item, shelfTitle)
        val browseId = item.browseId
        when {
            song != null -> onPlaySong(song)
            browseId != null -> openCollection(
                browseId = browseId,
                title = item.title,
                subtitle = item.subtitle,
                thumbnailUrl = item.thumbnailUrl,
            )
        }
    }

    /**
     * Gestiona el botón de reproducción directa sobre una tarjeta de INICIO:
     * - Si es una canción, la reproduce de inmediato.
     * - Si es un álbum o playlist, carga sus pistas y reproduce la primera pista automáticamente.
     */
    fun onShelfItemPlay(
        item: ShelfItem,
        shelfTitle: String,
        onPlaySong: (Song) -> Unit,
    ) {
        val song = shelfItemToSong(item, shelfTitle)
        if (song != null) {
            onPlaySong(song)
            return
        }
        val browseId = item.browseId ?: return
        openCollection(
            browseId = browseId,
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            autoPlayFirstSong = onPlaySong,
        )
    }

    fun openCollection(
        browseId: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
        autoPlayFirstSong: ((Song) -> Unit)? = null,
    ) {
        _selectedCollection.value = BrowseCollectionState(
            browseId = browseId,
            title = title,
            subtitle = subtitle,
            thumbnailUrl = thumbnailUrl,
            songsState = UiState.Loading,
        )

        viewModelScope.launch(ioDispatcher) {
            val isArtist = browseId.startsWith("UC")
            if (isArtist) {
                dataSource.artistPage(browseId).fold(
                    onSuccess = { page ->
                        val cover = thumbnailUrl ?: page.thumbnailUrl
                        val stamped = page.songs.map { s ->
                            if (s.thumbnailUrl.isNullOrBlank()) s.copy(thumbnailUrl = cover) else s
                        }
                        _selectedCollection.value = BrowseCollectionState(
                            browseId = browseId,
                            title = page.name ?: title,
                            subtitle = listOfNotNull(page.subscriberCountText, page.monthlyListenerCount)
                                .joinToString(" • ")
                                .ifBlank { subtitle },
                            thumbnailUrl = cover,
                            songsState = if (stamped.isEmpty()) {
                                UiState.Error("No se encontraron pistas para este artista")
                            } else {
                                UiState.Success(stamped)
                            },
                            sections = page.sections,
                        )
                        if (autoPlayFirstSong != null && stamped.isNotEmpty()) {
                            autoPlayFirstSong(stamped.first())
                        }
                    },
                    onFailure = { err ->
                        if (err is CancellationException) throw err
                        _selectedCollection.value = _selectedCollection.value?.copy(
                            songsState = UiState.Error(err.localizedMessage ?: "Error al cargar el artista")
                        )
                    }
                )
            } else {
                dataSource.browseSongs(browseId).fold(
                    onSuccess = { page ->
                        val cover = thumbnailUrl ?: page.header?.thumbnailUrl
                        val stamped = page.songs.map { s ->
                            s.copy(
                                thumbnailUrl = s.thumbnailUrl?.takeIf { it.isNotBlank() } ?: cover,
                                albumName = s.albumName ?: title,
                            )
                        }
                        _selectedCollection.value = BrowseCollectionState(
                            browseId = browseId,
                            title = title.ifBlank { page.header?.title.orEmpty() },
                            subtitle = subtitle.ifBlank { page.header?.subtitle.orEmpty() },
                            thumbnailUrl = cover,
                            songsState = if (stamped.isEmpty()) {
                                UiState.Error("Esta colección no contiene pistas reproducibles")
                            } else {
                                UiState.Success(stamped)
                            },
                        )
                        if (autoPlayFirstSong != null && stamped.isNotEmpty()) {
                            autoPlayFirstSong(stamped.first())
                        }
                    },
                    onFailure = { err ->
                        if (err is CancellationException) throw err
                        _selectedCollection.value = _selectedCollection.value?.copy(
                            songsState = UiState.Error(err.localizedMessage ?: "Error al cargar la colección")
                        )
                    }
                )
            }
        }
    }

    fun closeCollection(): Boolean {
        if (_selectedCollection.value == null) return false
        _selectedCollection.value = null
        return true
    }

    fun retryCollection() {
        val current = _selectedCollection.value ?: return
        openCollection(
            browseId = current.browseId,
            title = current.title,
            subtitle = current.subtitle,
            thumbnailUrl = current.thumbnailUrl,
        )
    }

    private fun publishHomeShelves(shelves: List<HomeShelf>) {
        synchronized(homeSeenTitles) {
            val existing = (_homeState.value as? UiState.Success)?.data.orEmpty()
            val added = shelves.withoutRepeatsOf(existing)
                .filter { homeSeenTitles.add(it.title.lowercase(Locale.ROOT)) }
            if (added.isNotEmpty()) {
                _homeState.value = UiState.Success(existing + added)
            }
        }
    }

    private fun settlePendingShelf(generation: Long) {
        if (generation != loadGeneration.get()) return
        _pendingShelves.value = (_pendingShelves.value - 1).coerceAtLeast(0)
    }
}

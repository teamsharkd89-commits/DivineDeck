package com.divinedeck.launcher.feature.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Interfaz de fuente de datos para Explorar (Explore), permitiendo pruebas deterministas y
 * conectando en producción directamente con [YtMusicRepository] de BitChord 1.8.1.
 */
interface ExploreDataSource {
    suspend fun moodAndGenres(): Result<List<MoodGenreSection>>
    suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>>
    suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?>
    suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage>
    suspend fun artistPage(browseId: String): Result<com.music.bitchord.data.model.ArtistPage>
}

object YtMusicExploreDataSource : ExploreDataSource {
    override suspend fun moodAndGenres(): Result<List<MoodGenreSection>> =
        YtMusicRepository.moodAndGenres()

    override suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> =
        YtMusicRepository.moodGenreShelves(browseId, params)

    override suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> =
        YtMusicRepository.moodGenreArtwork(browseId, params)

    override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
        YtMusicRepository.browseSongs(browseId)

    override suspend fun artistPage(browseId: String): Result<com.music.bitchord.data.model.ArtistPage> =
        YtMusicRepository.artistPage(browseId)
}

/**
 * ViewModel para gestionar la pantalla original EXPLORAR (Explore) y sus categorías
 * (`MoodGenrePlaylistsScreen`) de BitChord 1.8.1 dentro de DIVINE DECK.
 */
class MusicExploreViewModel(
    private val dataSource: ExploreDataSource = YtMusicExploreDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    autoLoadOnInit: Boolean = true,
) : ViewModel() {

    private val _exploreState = MutableStateFlow<UiState<List<MoodGenreSection>>>(UiState.Loading)
    val exploreState: StateFlow<UiState<List<MoodGenreSection>>> = _exploreState.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MoodGenre?>(null)
    val selectedCategory: StateFlow<MoodGenre?> = _selectedCategory.asStateFlow()

    private val _categoryShelvesState = MutableStateFlow<UiState<List<HomeShelf>>>(UiState.Loading)
    val categoryShelvesState: StateFlow<UiState<List<HomeShelf>>> = _categoryShelvesState.asStateFlow()

    private val _selectedCollection = MutableStateFlow<BrowseCollectionState?>(null)
    val selectedCollection: StateFlow<BrowseCollectionState?> = _selectedCollection.asStateFlow()

    init {
        if (autoLoadOnInit) {
            loadExplore()
        }
    }

    fun loadExplore() {
        _exploreState.value = UiState.Loading
        viewModelScope.launch(ioDispatcher) {
            fetchExplore()
        }
    }

    fun refreshExplore() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch(ioDispatcher) {
            try {
                fetchExplore()
            } finally {
                _refreshing.value = false
            }
        }
    }

    private suspend fun fetchExplore() {
        val state = dataSource.moodAndGenres().fold(
            onSuccess = { sections ->
                if (sections.isEmpty()) {
                    UiState.Error("No hay categorías de Explorar disponibles en este momento")
                } else {
                    UiState.Success(sections)
                }
            },
            onFailure = { err ->
                if (err is CancellationException) throw err
                UiState.Error(err.localizedMessage ?: "Error al cargar Explorar desde YouTube Music")
            },
        )
        _exploreState.value = state
        (state as? UiState.Success)?.data?.let(::loadMoodGenreArtwork)
    }

    private fun loadMoodGenreArtwork(sections: List<MoodGenreSection>) {
        viewModelScope.launch(ioDispatcher) {
            val limiter = Semaphore(4)
            coroutineScope {
                sections.flatMap(MoodGenreSection::items).forEach { item ->
                    launch {
                        val artwork = limiter.withPermit {
                            dataSource.moodGenreArtwork(item.browseId, item.params).getOrNull()
                        } ?: return@launch
                        val current = (_exploreState.value as? UiState.Success)?.data ?: return@launch
                        _exploreState.value = UiState.Success(
                            current.map { section ->
                                section.copy(
                                    items = section.items.map { currentItem ->
                                        if (currentItem.browseId == item.browseId && currentItem.params == item.params) {
                                            currentItem.copy(thumbnailUrl = artwork)
                                        } else {
                                            currentItem
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun openCategory(category: MoodGenre) {
        _selectedCollection.value = null
        _selectedCategory.value = category
        _categoryShelvesState.value = UiState.Loading
        viewModelScope.launch(ioDispatcher) {
            _categoryShelvesState.value = dataSource.moodGenreShelves(category.browseId, category.params).fold(
                onSuccess = { shelves ->
                    if (shelves.isEmpty()) {
                        UiState.Error("No se encontraron listas en la categoría ${category.title}")
                    } else {
                        UiState.Success(shelves)
                    }
                },
                onFailure = { err ->
                    if (err is CancellationException) throw err
                    UiState.Error(err.localizedMessage ?: "Error al cargar la categoría ${category.title}")
                },
            )
        }
    }

    fun retryCategory() {
        val current = _selectedCategory.value ?: return
        openCategory(current)
    }

    fun closeCategory(): Boolean {
        if (_selectedCategory.value == null) return false
        _selectedCategory.value = null
        return true
    }

    fun shelfItemToSong(item: ShelfItem, sourceTitle: String, sourceBrowseId: String? = null): Song? {
        val videoId = item.videoId ?: return null
        return Song(
            videoId = videoId,
            title = item.title,
            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
            thumbnailUrl = item.thumbnailUrl,
            playbackSource = sourceTitle,
            playbackSourceType = PlaybackSourceType.EXPLORE,
            playbackSourceId = sourceBrowseId,
        )
    }

    fun onShelfItemClick(
        item: ShelfItem,
        onPlaySong: (Song) -> Unit,
    ) {
        val category = _selectedCategory.value
        val song = shelfItemToSong(
            item = item,
            sourceTitle = category?.title ?: "Explorar",
            sourceBrowseId = category?.browseId,
        )
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

    fun onShelfItemPlay(
        item: ShelfItem,
        onPlaySong: (Song) -> Unit,
    ) {
        val category = _selectedCategory.value
        val song = shelfItemToSong(
            item = item,
            sourceTitle = category?.title ?: "Explorar",
            sourceBrowseId = category?.browseId,
        )
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
}

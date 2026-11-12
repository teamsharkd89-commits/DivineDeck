package com.divinedeck.launcher.feature.music.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.settings.LibraryViewType
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.DownloadBatchState
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedSongMetadata
import com.music.bitchord.ui.screens.LibraryLink
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

/**
 * Fuente de datos para la Biblioteca de BitChord (Fase 06.5B).
 * Soporta tanto persistencia local inmediata ([LocalPlaylistStore], [LocalMediaRepository])
 * como sincronización remota opcional con YouTube Music ([YtMusicRepository]) cuando existe sesión.
 */
interface LibraryDataSource {
    val isSignedIn: Boolean
    suspend fun remoteLibrary(): Result<LibraryPage>
    suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage>
    suspend fun artistPage(browseId: String): Result<ArtistPage>
    suspend fun localDeviceSongs(context: Context?): List<Song>
}

object DefaultLibraryDataSource : LibraryDataSource {
    override val isSignedIn: Boolean
        get() = !Innertube.cookie.isNullOrBlank()

    override suspend fun remoteLibrary(): Result<LibraryPage> =
        YtMusicRepository.library()

    override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
        YtMusicRepository.browseSongs(browseId)

    override suspend fun artistPage(browseId: String): Result<ArtistPage> =
        YtMusicRepository.artistPage(browseId)

    override suspend fun localDeviceSongs(context: Context?): List<Song> {
        if (context == null) return emptyList()
        return LocalMediaRepository.getLocalSongs(context)
    }
}

/**
 * ViewModel para gestionar la pantalla original BIBLIOTECA (`LibraryScreen`) y la gestión
 * completa de Playlists y Canciones Favoritas de BitChord 1.8.1 dentro de DIVINE DECK.
 */
class MusicLibraryViewModel(
    private val dataSource: LibraryDataSource = DefaultLibraryDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _remoteShelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    private val _localDeviceSongs = MutableStateFlow<List<Song>>(emptyList())
    val localDeviceSongs: StateFlow<List<Song>> = _localDeviceSongs.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _libraryViewType = MutableStateFlow(LibraryViewType.GRID)
    val libraryViewType: StateFlow<LibraryViewType> = _libraryViewType.asStateFlow()

    val librarySort: StateFlow<LibrarySort> = LocalPlaylistStore.librarySort
    val pinnedPlaylists: StateFlow<Set<String>> = LocalPlaylistStore.pinnedPlaylists
    val likedSongs: StateFlow<List<Song>> = LocalPlaylistStore.likedSongs
    val localPlaylists: StateFlow<List<LocalPlaylist>> = LocalPlaylistStore.playlists
    val savedCollections: StateFlow<List<ShelfItem>> = LocalPlaylistStore.savedCollections

    // Estado reactivo del motor original de descargas de BitChord (Fase 06.5C)
    val activeDownloads: StateFlow<Map<String, DownloadState>> = Downloads.active
    val savedDownloads: StateFlow<Map<String, String>> = Downloads.saved
    val savedDownloadMetadata: StateFlow<Map<String, SavedSongMetadata>> = Downloads.savedMetadata
    val downloadedSongs: StateFlow<List<Song>> = Downloads.downloadedSongs
    val downloadBatchState: StateFlow<DownloadBatchState> = DownloadSession.state

    private val _selectedShelfForGrid = MutableStateFlow<HomeShelf?>(null)
    val selectedShelfForGrid: StateFlow<HomeShelf?> = _selectedShelfForGrid.asStateFlow()

    private val _selectedCollection = MutableStateFlow<BrowseCollectionState?>(null)
    val selectedCollection: StateFlow<BrowseCollectionState?> = _selectedCollection.asStateFlow()

    /**
     * Lista de playlists de usuario disponibles en el selector `PlaylistPickerSheet`.
     */
    val userPlaylistsState: StateFlow<UiState<List<UserPlaylist>>> =
        LocalPlaylistStore.playlists
            .combine(_remoteShelves) { localList, _ ->
                val mapped = localList.map { playlist ->
                    UserPlaylist(
                        playlistId = "$LOCAL_PLAYLIST_PREFIX${playlist.id}",
                        title = playlist.name,
                        subtitle = "${playlist.songCount} ${if (playlist.songCount == 1) "canción" else "canciones"}",
                        thumbnailUrl = playlist.thumbnailUrl ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                    )
                }
                UiState.Success(mapped) as UiState<List<UserPlaylist>>
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = UiState.Success(emptyList()),
            )

    /**
     * Accesos rápidos en la cabecera de Biblioteca (`LibraryLinkList`).
     */
    val localFolders: StateFlow<List<LibraryLink>> =
        combine(
            LocalPlaylistStore.likedSongs,
            Downloads.downloadedSongs,
            Downloads.active,
            _localDeviceSongs,
        ) { liked, downloaded, activeMap, deviceSongs ->
            buildList {
                add(
                    LibraryLink(
                        title = "Canciones favoritas",
                        subtitle = if (liked.isEmpty()) {
                            "Guarda canciones con el icono ♥"
                        } else {
                            "${liked.size} ${if (liked.size == 1) "canción guardada" else "canciones guardadas"}"
                        },
                        browseId = BROWSE_ID_LIKED,
                        icon = Icons.Rounded.Favorite,
                        colors = listOf(Color(0xFFE040FB), Color(0xFF00E5FF)),
                    )
                )
                val activeCount = activeMap.count {
                    it.value is DownloadState.Queued || it.value is DownloadState.Running
                }
                val downloadSubtitle = when {
                    activeCount > 0 -> "${downloaded.size} guardadas • $activeCount descargando..."
                    downloaded.isEmpty() -> "Descarga canciones con ⬇ para escuchar sin Internet"
                    else -> "${downloaded.size} ${if (downloaded.size == 1) "canción disponible offline" else "canciones disponibles offline"}"
                }
                add(
                    LibraryLink(
                        title = "Canciones descargadas (Offline)",
                        subtitle = downloadSubtitle,
                        browseId = BROWSE_ID_DOWNLOADS,
                        icon = Icons.Rounded.CloudDone,
                        colors = listOf(Color(0xFF00E676), Color(0xFF00B0FF)),
                    )
                )
                if (deviceSongs.isNotEmpty()) {
                    add(
                        LibraryLink(
                            title = "Música en el dispositivo",
                            subtitle = "${deviceSongs.size} pistas locales",
                            browseId = BROWSE_ID_LOCAL_ALL,
                            icon = Icons.Rounded.Folder,
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF2979FF)),
                        )
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    /**
     * Estado reactivo principal de `LibraryScreen`, combinando playlists personales,
     * canciones favoritas, colecciones guardadas y estantes remotos (si hay sesión).
     */
    val libraryState: StateFlow<UiState<LibraryPage>> =
        combine(
            LocalPlaylistStore.playlists,
            LocalPlaylistStore.likedSongs,
            LocalPlaylistStore.savedCollections,
            _remoteShelves,
        ) { playlists, liked, saved, remote ->
            val shelves = buildLibraryShelves(
                playlists = playlists,
                liked = liked,
                saved = saved,
                remote = remote,
            )
            UiState.Success(
                LibraryPage(
                    likedSongs = liked,
                    librarySongs = emptyList(),
                    shelves = shelves,
                )
            ) as UiState<LibraryPage>
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Success(
                LibraryPage(
                    likedSongs = LocalPlaylistStore.likedSongs.value,
                    librarySongs = emptyList(),
                    shelves = buildLibraryShelves(
                        playlists = LocalPlaylistStore.playlists.value,
                        liked = LocalPlaylistStore.likedSongs.value,
                        saved = LocalPlaylistStore.savedCollections.value,
                        remote = emptyList(),
                    ),
                )
            ),
        )

    init {
        // Mantener actualizada la vista de detalle si el usuario modifica la playlist o favoritos abiertos
        viewModelScope.launch {
            LocalPlaylistStore.playlists.collect { playlists ->
                val current = _selectedCollection.value ?: return@collect
                if (current.browseId.startsWith(LOCAL_PLAYLIST_PREFIX)) {
                    val rawId = current.browseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
                    val updated = playlists.firstOrNull { it.id == rawId }
                    if (updated != null) {
                        val cover = updated.thumbnailUrl ?: updated.songs.firstOrNull()?.thumbnailUrl
                        _selectedCollection.value = current.copy(
                            title = updated.title,
                            subtitle = "Playlist personal • ${updated.songs.size} ${if (updated.songs.size == 1) "canción" else "canciones"}",
                            thumbnailUrl = cover,
                            songsState = UiState.Success(
                                updated.songs.map {
                                    it.copy(
                                        playbackSource = updated.title,
                                        playbackSourceType = PlaybackSourceType.BROWSE,
                                    )
                                }
                            ),
                        )
                    } else {
                        _selectedCollection.value = null
                    }
                }
            }
        }

        viewModelScope.launch {
            LocalPlaylistStore.likedSongs.collect { liked ->
                // Sincronizar con LikeState global de BitChord
                liked.forEach { LikeState[it.videoId] = LikeStatus.LIKE }
                val current = _selectedCollection.value ?: return@collect
                if (current.browseId == BROWSE_ID_LIKED) {
                    _selectedCollection.value = current.copy(
                        subtitle = "${liked.size} ${if (liked.size == 1) "canción guardada" else "canciones guardadas"}",
                        thumbnailUrl = liked.firstOrNull()?.thumbnailUrl,
                        songsState = UiState.Success(
                            liked.map {
                                it.copy(
                                    playbackSource = "Canciones favoritas",
                                    playbackSourceType = PlaybackSourceType.BROWSE,
                                )
                            }
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            Downloads.downloadedSongs.collect { downloaded ->
                val current = _selectedCollection.value ?: return@collect
                if (current.browseId == BROWSE_ID_DOWNLOADS) {
                    _selectedCollection.value = current.copy(
                        subtitle = "${downloaded.size} ${if (downloaded.size == 1) "canción descargada" else "canciones descargadas"} • Disponible sin Internet",
                        thumbnailUrl = downloaded.firstOrNull()?.thumbnailUrl,
                        songsState = UiState.Success(
                            downloaded.map {
                                it.copy(
                                    playbackSource = "Canciones descargadas",
                                    playbackSourceType = PlaybackSourceType.BROWSE,
                                )
                            }
                        ),
                    )
                }
            }
        }
    }

    private fun buildLibraryShelves(
        playlists: List<LocalPlaylist>,
        liked: List<Song>,
        saved: List<ShelfItem>,
        remote: List<HomeShelf>,
    ): List<HomeShelf> {
        val shelves = mutableListOf<HomeShelf>()

        // 1. Estante de Playlists (siempre presente para mostrar el botón "+ Nueva playlist" y las playlists creadas)
        val playlistItems = buildList {
            add(
                ShelfItem(
                    title = "Canciones favoritas",
                    subtitle = "${liked.size} ${if (liked.size == 1) "canción" else "canciones"}",
                    thumbnailUrl = liked.firstOrNull()?.thumbnailUrl,
                    videoId = null,
                    browseId = BROWSE_ID_LIKED,
                )
            )
            playlists.forEach { playlist ->
                add(
                    ShelfItem(
                        title = playlist.title,
                        subtitle = "${playlist.songs.size} ${if (playlist.songs.size == 1) "canción" else "canciones"}",
                        thumbnailUrl = playlist.thumbnailUrl ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                        videoId = null,
                        browseId = "$LOCAL_PLAYLIST_PREFIX${playlist.id}",
                    )
                )
            }
            // Añadir playlists remotas si existen y no colisionan
            remote.firstOrNull { it.title.equals("Playlists", ignoreCase = true) }
                ?.items
                ?.forEach { remoteItem ->
                    if (none { it.browseId == remoteItem.browseId }) {
                        add(remoteItem)
                    }
                }
        }
        shelves.add(HomeShelf(title = "Playlists", items = playlistItems))

        // 2. Estante de Canciones favoritas (acceso directo a pistas guardadas)
        if (liked.isNotEmpty()) {
            shelves.add(
                HomeShelf(
                    title = "Canciones favoritas",
                    items = liked.map { song ->
                        ShelfItem(
                            title = song.title,
                            subtitle = song.artist,
                            thumbnailUrl = song.thumbnailUrl,
                            videoId = song.videoId,
                            browseId = null,
                        )
                    },
                )
            )
        }

        // 3. Estantes de Colecciones guardadas (Álbumes, Artistas, Playlists de YouTube Music)
        if (saved.isNotEmpty()) {
            val savedAlbums = saved.filter {
                val id = it.browseId.orEmpty()
                id.startsWith("MPREb") || id.startsWith("OLAK")
            }
            val savedArtists = saved.filter {
                it.browseId.orEmpty().startsWith("UC")
            }
            val savedOther = saved.filter { item ->
                item !in savedAlbums && item !in savedArtists
            }

            if (savedAlbums.isNotEmpty()) {
                shelves.add(HomeShelf(title = "Álbumes guardados", items = savedAlbums))
            }
            if (savedArtists.isNotEmpty()) {
                shelves.add(HomeShelf(title = "Artistas guardados", items = savedArtists))
            }
            if (savedOther.isNotEmpty()) {
                shelves.add(HomeShelf(title = "Colecciones guardadas", items = savedOther))
            }
        }

        // 4. Otros estantes remotos (si el usuario tiene sesión activa en YouTube Music)
        remote.filterNot { it.title.equals("Playlists", ignoreCase = true) }
            .forEach { remoteShelf ->
                if (shelves.none { it.title.equals(remoteShelf.title, ignoreCase = true) }) {
                    shelves.add(remoteShelf)
                }
            }

        return shelves
    }

    fun refreshLibrary(context: Context? = null) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch(ioDispatcher) {
            try {
                if (context != null) {
                    _localDeviceSongs.value = runCatching {
                        dataSource.localDeviceSongs(context)
                    }.getOrDefault(emptyList())
                }
                if (dataSource.isSignedIn) {
                    dataSource.remoteLibrary().onSuccess { page ->
                        _remoteShelves.value = page.shelves
                    }
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadLocalDeviceSongs(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            _localDeviceSongs.value = runCatching {
                dataSource.localDeviceSongs(context)
            }.getOrDefault(emptyList())
        }
    }

    fun setLibrarySort(sort: LibrarySort) {
        LocalPlaylistStore.setLibrarySort(sort)
    }

    fun setLibraryViewType(viewType: LibraryViewType) {
        _libraryViewType.value = viewType
    }

    fun togglePinPlaylist(browseId: String): Boolean {
        LocalPlaylistStore.togglePinPlaylist(browseId)
        return browseId in LocalPlaylistStore.pinnedPlaylists.value
    }

    // ── Gestión de Playlists ─────────────────────────────────────────────────

    fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy = PlaylistPrivacy.PRIVATE,
        initialSong: Song? = null,
    ): LocalPlaylist {
        val cleanTitle = title.trim().ifEmpty { "Nueva playlist" }
        val created = LocalPlaylistStore.createPlaylist(cleanTitle)
        if (initialSong != null) {
            LocalPlaylistStore.addSongToPlaylist(created.id, initialSong)
        }
        if (dataSource.isSignedIn) {
            viewModelScope.launch(ioDispatcher) {
                YtMusicRepository.createPlaylist(
                    title = cleanTitle,
                    privacy = privacy,
                    videoIds = listOfNotNull(initialSong?.videoId),
                )
            }
        }
        return LocalPlaylistStore.getPlaylist(created.id) ?: created
    }

    fun renamePlaylist(playlistBrowseId: String, newTitle: String) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return
        val rawId = playlistBrowseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
        LocalPlaylistStore.renamePlaylist(rawId, cleanTitle)
        if (!playlistBrowseId.startsWith(LOCAL_PLAYLIST_PREFIX) && dataSource.isSignedIn) {
            viewModelScope.launch(ioDispatcher) {
                YtMusicRepository.renamePlaylist(rawId, cleanTitle)
            }
        }
    }

    fun deletePlaylist(playlistBrowseId: String) {
        val rawId = playlistBrowseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
        LocalPlaylistStore.deletePlaylist(rawId)
        if (_selectedCollection.value?.browseId == playlistBrowseId ||
            _selectedCollection.value?.browseId == "$LOCAL_PLAYLIST_PREFIX$rawId"
        ) {
            _selectedCollection.value = null
        }
        if (!playlistBrowseId.startsWith(LOCAL_PLAYLIST_PREFIX) && dataSource.isSignedIn) {
            viewModelScope.launch(ioDispatcher) {
                YtMusicRepository.deletePlaylist(rawId)
            }
        }
    }

    fun addSongToPlaylist(playlistBrowseId: String, song: Song): Boolean {
        val rawId = playlistBrowseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
        return LocalPlaylistStore.addSongToPlaylist(rawId, song)
    }

    fun addSongToUserPlaylists(song: Song, playlists: List<UserPlaylist>) {
        playlists.forEach { target ->
            val rawId = target.playlistId.removePrefix(LOCAL_PLAYLIST_PREFIX)
            if (LocalPlaylistStore.getPlaylist(rawId) != null) {
                LocalPlaylistStore.addSongToPlaylist(rawId, song)
            } else if (dataSource.isSignedIn) {
                viewModelScope.launch(ioDispatcher) {
                    YtMusicRepository.addToPlaylist(rawId, listOf(song.videoId))
                }
            }
        }
    }

    val isSignedIn: Boolean
        get() = dataSource.isSignedIn

    fun removeSongFromPlaylist(playlistBrowseId: String, videoId: String) {
        val rawId = playlistBrowseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
        LocalPlaylistStore.removeSongFromPlaylist(rawId, videoId)
    }

    fun isEditablePlaylist(browseId: String?): Boolean {
        if (browseId == null) return false
        if (browseId.startsWith(LOCAL_PLAYLIST_PREFIX)) return true
        val rawId = browseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
        return LocalPlaylistStore.getPlaylist(rawId) != null
    }

    // ── Gestión de Canciones Favoritas y Colecciones Guardadas ───────────────

    fun toggleFavoriteSong(song: Song): LikeStatus {
        val newStatus = LocalPlaylistStore.toggleLike(song)
        LikeState[song.videoId] = newStatus
        if (dataSource.isSignedIn) {
            viewModelScope.launch(ioDispatcher) {
                YtMusicRepository.rate(song.videoId, newStatus)
            }
        }
        return newStatus
    }

    fun isSongLiked(song: Song?): Boolean {
        if (song == null) return false
        return LocalPlaylistStore.isLiked(song.videoId)
    }

    fun toggleSaveCollection(
        browseId: String,
        title: String,
        subtitle: String,
        thumbnailUrl: String?,
    ): Boolean {
        if (browseId == BROWSE_ID_LIKED ||
            browseId == BROWSE_ID_DOWNLOADS ||
            browseId == BROWSE_ID_LOCAL_ALL ||
            browseId.startsWith(LOCAL_PLAYLIST_PREFIX)
        ) {
            return true
        }
        return LocalPlaylistStore.toggleSavedCollection(
            ShelfItem(
                title = title,
                subtitle = subtitle,
                thumbnailUrl = thumbnailUrl,
                videoId = null,
                browseId = browseId,
            )
        )
    }

    // ── Gestión de Descargas Offline (BitChord 1.8.1 Downloads) ──────────────

    fun downloadSong(context: Context?, song: Song) {
        Downloads.enqueue(context, song)
    }

    fun downloadAll(context: Context?, songs: List<Song>) {
        Downloads.enqueueAll(context, songs)
    }

    fun cancelDownload(videoId: String) {
        Downloads.cancel(videoId)
    }

    fun cancelAllDownloads() {
        Downloads.cancelAll()
    }

    fun retryDownload(context: Context?, videoId: String, fallbackSong: Song? = null) {
        Downloads.retry(context, videoId, fallbackSong)
    }

    fun deleteDownload(context: Context?, videoId: String) {
        Downloads.delete(context, videoId)
    }

    fun isSongDownloaded(videoId: String): Boolean = Downloads.isDownloaded(videoId)

    fun isCollectionSaved(browseId: String?): Boolean {
        if (browseId.isNullOrBlank()) return false
        return LocalPlaylistStore.isCollectionSaved(browseId)
    }

    // ── Navegación por Estantes y Colecciones en Biblioteca ──────────────────

    fun openShelfGrid(shelf: HomeShelf) {
        _selectedShelfForGrid.value = shelf
    }

    fun closeShelfGrid(): Boolean {
        if (_selectedShelfForGrid.value == null) return false
        _selectedShelfForGrid.value = null
        return true
    }

    fun shelfItemToSong(item: ShelfItem, shelfTitle: String = "Biblioteca"): Song? {
        val videoId = item.videoId ?: return null
        // Si la canción ya está en favoritos, preservar sus metadatos completos
        val existingLiked = LocalPlaylistStore.likedSongs.value.firstOrNull { it.videoId == videoId }
        if (existingLiked != null) {
            return existingLiked.copy(
                playbackSource = shelfTitle,
                playbackSourceType = PlaybackSourceType.BROWSE,
            )
        }
        return Song(
            videoId = videoId,
            title = item.title,
            artist = InnertubeParser.artistFromSubtitle(item.subtitle),
            thumbnailUrl = item.thumbnailUrl,
            playbackSource = shelfTitle,
            playbackSourceType = PlaybackSourceType.BROWSE,
        )
    }

    fun onShelfItemClick(
        item: ShelfItem,
        onPlaySong: (Song) -> Unit,
    ) {
        val song = shelfItemToSong(item)
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
        val song = shelfItemToSong(item)
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
        // Caso 1: Canciones favoritas (local:liked)
        if (browseId == BROWSE_ID_LIKED) {
            val liked = LocalPlaylistStore.likedSongs.value.map {
                it.copy(
                    playbackSource = "Canciones favoritas",
                    playbackSourceType = PlaybackSourceType.BROWSE,
                )
            }
            _selectedCollection.value = BrowseCollectionState(
                browseId = BROWSE_ID_LIKED,
                title = "Canciones favoritas",
                subtitle = "${liked.size} ${if (liked.size == 1) "canción guardada" else "canciones guardadas"}",
                thumbnailUrl = thumbnailUrl ?: liked.firstOrNull()?.thumbnailUrl,
                songsState = UiState.Success(liked),
            )
            if (autoPlayFirstSong != null && liked.isNotEmpty()) {
                autoPlayFirstSong(liked.first())
            }
            return
        }

        // Caso 1B: Canciones descargadas (local:downloads)
        if (browseId == BROWSE_ID_DOWNLOADS) {
            val downloaded = Downloads.getDownloadedSongs().map {
                it.copy(
                    playbackSource = "Canciones descargadas",
                    playbackSourceType = PlaybackSourceType.BROWSE,
                )
            }
            _selectedCollection.value = BrowseCollectionState(
                browseId = BROWSE_ID_DOWNLOADS,
                title = "Canciones descargadas (Offline)",
                subtitle = "${downloaded.size} ${if (downloaded.size == 1) "canción descargada" else "canciones descargadas"} • Disponible sin Internet",
                thumbnailUrl = thumbnailUrl ?: downloaded.firstOrNull()?.thumbnailUrl,
                songsState = UiState.Success(downloaded),
            )
            if (autoPlayFirstSong != null && downloaded.isNotEmpty()) {
                autoPlayFirstSong(downloaded.first())
            }
            return
        }

        // Caso 2: Música en el dispositivo (local:all)
        if (browseId == BROWSE_ID_LOCAL_ALL) {
            val deviceSongs = _localDeviceSongs.value
            _selectedCollection.value = BrowseCollectionState(
                browseId = BROWSE_ID_LOCAL_ALL,
                title = title.ifBlank { "Música en el dispositivo" },
                subtitle = "${deviceSongs.size} pistas locales",
                thumbnailUrl = thumbnailUrl ?: deviceSongs.firstOrNull()?.thumbnailUrl,
                songsState = UiState.Success(deviceSongs),
            )
            if (autoPlayFirstSong != null && deviceSongs.isNotEmpty()) {
                autoPlayFirstSong(deviceSongs.first())
            }
            return
        }

        // Caso 3: Playlist personal local (local:playlist:<id>)
        if (browseId.startsWith(LOCAL_PLAYLIST_PREFIX)) {
            val rawId = browseId.removePrefix(LOCAL_PLAYLIST_PREFIX)
            val playlist = LocalPlaylistStore.getPlaylist(rawId)
            if (playlist != null) {
                val stamped = playlist.songs.map {
                    it.copy(
                        playbackSource = playlist.title,
                        playbackSourceType = PlaybackSourceType.BROWSE,
                    )
                }
                val cover = playlist.thumbnailUrl ?: stamped.firstOrNull()?.thumbnailUrl ?: thumbnailUrl
                _selectedCollection.value = BrowseCollectionState(
                    browseId = browseId,
                    title = playlist.title,
                    subtitle = "Playlist personal • ${stamped.size} ${if (stamped.size == 1) "canción" else "canciones"}",
                    thumbnailUrl = cover,
                    songsState = UiState.Success(stamped),
                )
                if (autoPlayFirstSong != null && stamped.isNotEmpty()) {
                    autoPlayFirstSong(stamped.first())
                }
                return
            }
        }

        // Caso 4: Colección remota (Álbum, Playlist o Artista de YouTube Music)
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

    companion object {
        const val BROWSE_ID_LIKED = "local:liked"
        const val BROWSE_ID_DOWNLOADS = Downloads.BROWSE_ID_DOWNLOADS
        const val BROWSE_ID_LOCAL_ALL = "local:all"
        const val LOCAL_PLAYLIST_PREFIX = "local:playlist:"
    }
}

package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LibraryState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaylistPrivacy
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.SongMenu
import com.music.bitchord.data.model.UserPlaylist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

/**
 * Repositorio de catálogo, Inicio (Home), Explorar (Explore) y Búsqueda de YouTube Music para DIVINE DECK.
 * Extraído y adaptado de BitChord 1.8.1 (GPLv3).
 */
object YtMusicRepository {

    /**
     * Páginas suplementarias que se cargan junto con `FEmusic_home` para enriquecer la pantalla INICIO,
     * tal y como hace el repositorio original de BitChord 1.8.1.
     */
    val HOME_SUPPLEMENT_BROWSE_IDS = listOf("FEmusic_new_releases", "FEmusic_explore")

    /**
     * Carga la primera página de recomendaciones de YouTube Music (`FEmusic_home`) y su token de continuación.
     */
    suspend fun home(): Result<HomeFeed> = catching {
        val raw = Innertube.browse("FEmusic_home")
        HomeFeed(
            shelves = InnertubeParser.parseHome(raw).filterNot { it.isMoodsShelf() },
            continuation = InnertubeParser.continuationToken(raw),
        )
    }

    /**
     * Carga un feed suplementario de Inicio (`FEmusic_new_releases` o `FEmusic_explore`).
     */
    suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> = catching {
        InnertubeParser.parseHome(Innertube.browse(browseId))
            .filterNot { it.isMoodsShelf() }
    }

    private fun HomeShelf.isMoodsShelf(): Boolean =
        title.contains("mood", ignoreCase = true)

    /**
     * Carga la siguiente página del feed de Inicio mediante el token de continuación.
     */
    suspend fun moreHome(token: String): Result<HomeFeed> = catching {
        val raw = Innertube.browseContinuation(token)
        HomeFeed(
            shelves = InnertubeParser.parseHomeContinuation(raw),
            continuation = InnertubeParser.continuationToken(raw),
        )
    }

    /**
     * Carga las secciones de estados de ánimo y géneros musicales para la pantalla EXPLORAR (`FEmusic_moods_and_genres`).
     */
    suspend fun moodAndGenres(): Result<List<MoodGenreSection>> = catching {
        InnertubeParser.parseMoodAndGenres(Innertube.browse("FEmusic_moods_and_genres"))
    }

    /**
     * Carga los estantes de listas de reproducción asociados a una categoría de estado de ánimo o género.
     */
    suspend fun moodGenreShelves(browseId: String, params: String? = null): Result<List<HomeShelf>> = catching {
        InnertubeParser.parseHome(Innertube.browse(browseId, params))
    }

    /**
     * Resuelve en segundo plano la primera carátula real de una categoría de EXPLORAR.
     */
    suspend fun moodGenreArtwork(browseId: String, params: String? = null): Result<String?> = catching {
        moodGenreShelves(browseId, params).getOrThrow()
            .asSequence()
            .flatMap { it.items.asSequence() }
            .mapNotNull(ShelfItem::thumbnailUrl)
            .firstOrNull { it.isNotBlank() }
    }

    /**
     * Una página de canciones de un álbum o lista de reproducción.
     */
    data class SongPage(
        val songs: List<Song>,
        val continuation: String?,
        val suggested: List<Song> = emptyList(),
        val library: LibraryState? = null,
        val owned: Boolean? = null,
        val header: InnertubeParser.BrowseHeader? = null,
        val description: String? = null,
    )

    /**
     * Carga las canciones de un álbum o playlist (`browseId`).
     */
    suspend fun browseSongs(browseId: String): Result<SongPage> = catching {
        val raw = Innertube.browse(browseId)
        val isAlbum = browseId.startsWith("MPREb") || browseId.startsWith("OLAK") || browseId.startsWith("VLOLAK")
        val isPlaylist = browseId.startsWith("VL") && !isAlbum
        val header = InnertubeParser.parseBrowseHeader(raw)
        val description = InnertubeParser.parseDescription(raw)
        val scoped = InnertubeParser.parsePlaylistShelf(raw)
        val library = InnertubeParser.parseLibraryState(raw)
        val owned = if (isPlaylist) InnertubeParser.parsePlaylistOwned(raw) else null

        if (scoped != null) {
            return@catching SongPage(
                songs = scoped.songs,
                continuation = scoped.continuation,
                suggested = scoped.suggested,
                library = library,
                owned = owned,
                header = header,
                description = description,
            )
        }

        val directPage = SongPage(
            songs = InnertubeParser.collectSongsDeep(raw),
            continuation = InnertubeParser.continuationToken(raw),
            library = library,
            owned = owned,
            header = header,
            description = description,
        )

        if (isAlbum) {
            val playlistId = (library?.playlistId ?: InnertubeParser.parseAlbumPlaylistId(raw))
                ?.removePrefix("VL")
                ?.takeIf { it.isNotBlank() }
            val playlistBrowseId = playlistId?.let { "VL$it" }
            if (playlistBrowseId != null && playlistBrowseId != browseId) {
                runCatching {
                    val playlistRaw = Innertube.browse(playlistBrowseId)
                    val playlistScoped = InnertubeParser.parsePlaylistShelf(playlistRaw)
                    val playlistPage = if (playlistScoped != null) {
                        SongPage(
                            songs = playlistScoped.songs,
                            continuation = playlistScoped.continuation,
                            suggested = playlistScoped.suggested,
                            library = library ?: InnertubeParser.parseLibraryState(playlistRaw),
                            owned = null,
                            header = header ?: InnertubeParser.parseBrowseHeader(playlistRaw),
                            description = description ?: InnertubeParser.parseDescription(playlistRaw),
                        )
                    } else {
                        SongPage(
                            songs = InnertubeParser.collectSongsDeep(playlistRaw),
                            continuation = InnertubeParser.continuationToken(playlistRaw),
                            library = library ?: InnertubeParser.parseLibraryState(playlistRaw),
                            owned = null,
                            header = header ?: InnertubeParser.parseBrowseHeader(playlistRaw),
                            description = description ?: InnertubeParser.parseDescription(playlistRaw),
                        )
                    }
                    if (playlistPage.songs.size > directPage.songs.size ||
                        (playlistPage.songs.size == directPage.songs.size &&
                            playlistPage.continuation != null &&
                            directPage.continuation == null)
                    ) {
                        playlistPage.copy(songs = mergeReleaseRowCredits(playlistPage.songs, directPage.songs))
                    } else {
                        directPage
                    }
                }.getOrDefault(directPage)
            } else {
                directPage
            }
        } else {
            directPage
        }
    }

    private fun mergeReleaseRowCredits(primary: List<Song>, fallback: List<Song>): List<Song> {
        if (primary.isEmpty() || fallback.isEmpty()) return primary
        val fallbackByVideoId = fallback.associateBy(Song::videoId)
        return primary.mapIndexed { index, song ->
            val source = fallbackByVideoId[song.videoId] ?: fallback.getOrNull(index) ?: return@mapIndexed song
            song.copy(
                artist = song.artist.takeUnless { it.isBlank() || it == "Unknown artist" } ?: source.artist,
                durationText = song.durationText ?: source.durationText,
                artistId = song.artistId ?: source.artistId,
                artists = song.artists.ifEmpty { source.artists },
                albumId = song.albumId ?: source.albumId,
                albumName = song.albumName ?: source.albumName,
                isExplicit = song.isExplicit ?: source.isExplicit,
            )
        }
    }

    /**
     * Carga la siguiente página de canciones de un álbum o playlist.
     */
    suspend fun moreSongs(token: String): Result<SongPage> = catching {
        val raw = Innertube.browseContinuation(token)
        val scoped = InnertubeParser.parsePlaylistShelf(raw)
        if (scoped != null) {
            SongPage(scoped.songs, scoped.continuation, scoped.suggested)
        } else {
            SongPage(InnertubeParser.collectSongsDeep(raw), InnertubeParser.continuationToken(raw))
        }
    }

    /**
     * Carga la página de un artista (`UC...`).
     */
    suspend fun artistPage(browseId: String): Result<ArtistPage> = catching {
        val page = InnertubeParser.parseArtistPage(Innertube.browse(browseId))
        val fullSongs = page.moreSongsBrowseId
            ?.let { runCatching { InnertubeParser.collectSongsDeep(Innertube.browse(it)) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }
        if (fullSongs != null) page.copy(songs = fullSongs) else page
    }

    /**
     * Obtiene la mezcla de radio (`RDAMVM`) para una canción dada.
     */
    suspend fun radio(videoId: String): Result<List<Song>> = catching {
        InnertubeParser.parseWatchQueue(Innertube.next(videoId))
            .filterNot { it.isVideo }
    }

    /**
     * Consulta una página de resultados de búsqueda para la consulta y filtro indicados.
     */
    suspend fun searchPage(
        query: String,
        filter: SearchFilter = SearchFilter.ALL
    ): Result<InnertubeParser.SearchPage> = catching {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return Result.success(InnertubeParser.SearchPage(emptyList(), null))
        }

        val json = Innertube.search(query = trimmed, params = filter.params)
        val page = InnertubeParser.parseSearchPage(
            response = json,
            includeVideos = filter == SearchFilter.VIDEOS
        )
        val distinctRows = page.rows.distinctBy { result ->
            when (result) {
                is SearchResult.TopTrack -> "v:${result.song.videoId}"
                is SearchResult.Track -> "v:${result.song.videoId}"
                is SearchResult.Browse -> "b:${result.item.browseId}"
            }
        }
        InnertubeParser.SearchPage(distinctRows, page.continuation)
    }

    /**
     * Consulta las sugerencias de autocompletado en tiempo real.
     */
    suspend fun searchSuggestions(input: String): Result<List<String>> = catching {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Result.success(emptyList())
        }
        val json = Innertube.searchSuggestions(trimmed)
        InnertubeParser.parseSearchSuggestions(json)
    }

    // ---- Biblioteca y Gestión de Playlists (BitChord 1.8.1 original) --------

    const val MAX_PAGES = 10
    const val LIKED_MUSIC = "VLLM"
    private const val LIBRARY_SONGS = "FEmusic_liked_videos"
    private const val LIBRARY_PLAYLISTS = "FEmusic_liked_playlists"
    const val PLAYLISTS_SHELF = "Playlists"

    private val LIBRARY_FEEDS = listOf(
        PLAYLISTS_SHELF to LIBRARY_PLAYLISTS,
        "Albums" to "FEmusic_liked_albums",
        "Artists" to "FEmusic_library_corpus_track_artists",
        "Subscriptions" to "FEmusic_library_corpus_artists",
        "Podcasts" to "FEmusic_library_non_music_audio_list",
    )

    /**
     * Carga la biblioteca completa de la cuenta de YouTube Music cuando existe sesión (`Innertube.cookie != null`).
     */
    suspend fun library(): Result<LibraryPage> = catching {
        coroutineScope {
            val liked = async { browseSongs(LIKED_MUSIC).getOrNull() }
            val added = async { runCatching { songsPaged(LIBRARY_SONGS) }.getOrDefault(emptyList()) }
            val shelves = LIBRARY_FEEDS
                .map { (title, browseId) ->
                    async {
                        HomeShelf(title, runCatching { libraryItemsPaged(browseId) }.getOrDefault(emptyList()))
                    }
                }
                .awaitAll()
                .filter { it.items.isNotEmpty() }

            val likedPage = liked.await()
            val likedSongs = likedPage?.songs.orEmpty()
            val likedIds = likedSongs.mapTo(HashSet()) { it.videoId }
            LikeState.seedLiked(likedIds)
            LibraryPage(
                likedSongs = likedSongs,
                librarySongs = added.await().filterNot { it.videoId in likedIds },
                shelves = shelves,
                likedContinuation = likedPage?.continuation,
            )
        }
    }

    suspend fun syncLikedMusic(
        firstToken: String?,
        loadNext: suspend (String) -> SongPage? = { moreSongs(it).getOrNull() },
    ) {
        if (firstToken == null) return
        val seen = HashSet<String>()
        var next: String? = firstToken
        while (next != null && seen.add(next)) {
            val page = loadNext(next) ?: return
            LikeState.seedLiked(page.songs.mapTo(HashSet()) { it.videoId })
            next = page.continuation
        }
    }

    private suspend fun songsPaged(browseId: String): List<Song> {
        val out = LinkedHashMap<String, Song>()
        var response = Innertube.browse(browseId)
        var page = 1
        while (true) {
            val shelf = InnertubeParser.parsePlaylistShelf(response)
            (shelf?.songs ?: InnertubeParser.collectSongsDeep(response)).forEach { out[it.videoId] = it }
            val token = shelf?.continuation ?: InnertubeParser.continuationToken(response)
            if (token == null || page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    private suspend fun itemsPaged(browseId: String, params: String? = null): List<ShelfItem> {
        val out = LinkedHashMap<String, ShelfItem>()
        var response = Innertube.browse(browseId, params)
        var page = 1
        while (true) {
            val parsed = InnertubeParser.parseLibraryItemPage(response)
            parsed.items.forEach { item ->
                val key = item.browseId ?: item.videoId ?: "${item.title}\n${item.subtitle}"
                out.putIfAbsent(key, item)
            }
            val token = parsed.continuation ?: break
            if (page++ >= MAX_PAGES) break
            response = runCatching { Innertube.browseContinuation(token) }.getOrNull() ?: break
        }
        return out.values.toList()
    }

    private suspend fun libraryItemsPaged(browseId: String): List<ShelfItem> = itemsPaged(browseId, null)

    suspend fun songMenu(videoId: String): Result<SongMenu> = catching {
        InnertubeParser.parseSongMenu(Innertube.next(videoId), videoId)
            ?: error("no menu for $videoId")
    }

    suspend fun rate(videoId: String, status: LikeStatus): Result<Unit> = catching {
        Innertube.rate(videoId, status)
    }

    suspend fun setLibraryStatus(token: String): Result<Unit> = catching {
        Innertube.sendFeedback(token)
    }

    suspend fun releaseLibraryState(browseId: String): Result<LibraryState?> = catching {
        InnertubeParser.parseLibraryState(Innertube.browse(browseId))
    }

    suspend fun setSaved(playlistId: String, saved: Boolean): Result<Unit> = catching {
        Innertube.ratePlaylist(playlistId, saved)
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean): Result<Unit> = catching {
        Innertube.setSubscribed(channelId, subscribed)
    }

    suspend fun userPlaylists(): Result<List<UserPlaylist>> = catching {
        InnertubeParser.parseUserPlaylists(libraryItemsPaged(LIBRARY_PLAYLISTS))
    }

    suspend fun libraryPlaylists(): Result<List<ShelfItem>> = catching {
        InnertubeParser.parseLibraryItems(Innertube.browse(LIBRARY_PLAYLISTS))
    }

    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        videoIds: List<String> = emptyList(),
    ): Result<String> = catching {
        Innertube.createPlaylist(title, privacy, videoIds = videoIds)
    }

    suspend fun addToPlaylist(
        playlistId: String,
        videoIds: List<String>,
    ): Result<Map<String, String>> = catching {
        Innertube.addToPlaylist(playlistId, videoIds)
    }

    suspend fun removeFromPlaylist(
        playlistId: String,
        entries: List<Pair<String, String>>,
    ): Result<Unit> = catching {
        Innertube.removeFromPlaylist(playlistId, entries)
    }

    suspend fun renamePlaylist(playlistId: String, title: String): Result<Unit> = catching {
        Innertube.renamePlaylist(playlistId, title)
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> = catching {
        Innertube.deletePlaylist(playlistId)
    }

    private inline fun <T> catching(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
}

package com.music.bitchord.data.spotify

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.LibrarySort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Playlist local persistida en el dispositivo (BitChord 1.8.1).
 *
 * Su identificador de navegación en `LibraryScreen` es `"local:playlist:$id"`.
 */
data class LocalPlaylist(
    val id: String,
    val title: String,
    val songs: List<Song>,
) {
    val name: String get() = title
    val songCount: Int get() = songs.size
    val browseId: String get() = "local:playlist:$id"
    val thumbnailUrl: String? get() = songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl
}

/**
 * Almacén persistente de playlists locales, canciones favoritas (`likedSongs`),
 * colecciones guardadas (`savedCollections`: álbumes, artistas, playlists) y fijación
 * de playlists (`pinnedPlaylists`) de BitChord 1.8.1 integrado en DIVINE DECK.
 *
 * Persiste todos los datos en `SharedPreferences("bitchord_local_playlists", Context.MODE_PRIVATE)`
 * para sobrevivir cierres de la aplicación y reinicios del dispositivo sin requerir una cuenta
 * remota de Google/YouTube Music, manteniendo compatibilidad con pruebas unitarias en memoria.
 */
object LocalPlaylistStore {
    private const val PREFS_NAME = "bitchord_local_playlists"
    private const val KEY_PLAYLISTS = "playlists_json"
    private const val KEY_LIKED_SONGS = "liked_songs_json"
    private const val KEY_SAVED_COLLECTIONS = "saved_collections_json"
    private const val KEY_PINNED_PLAYLISTS = "pinned_playlists_set"
    private const val KEY_LIBRARY_SORT = "library_sort"

    private var prefs: SharedPreferences? = null

    private val _playlists = MutableStateFlow<List<LocalPlaylist>>(emptyList())
    val playlists: StateFlow<List<LocalPlaylist>> = _playlists.asStateFlow()

    private val _likedSongs = MutableStateFlow<List<Song>>(emptyList())
    val likedSongs: StateFlow<List<Song>> = _likedSongs.asStateFlow()

    private val _savedCollections = MutableStateFlow<List<ShelfItem>>(emptyList())
    val savedCollections: StateFlow<List<ShelfItem>> = _savedCollections.asStateFlow()

    private val _pinnedPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val pinnedPlaylists: StateFlow<Set<String>> = _pinnedPlaylists.asStateFlow()

    private val _librarySort = MutableStateFlow(LibrarySort.DEFAULT)
    val librarySort: StateFlow<LibrarySort> = _librarySort.asStateFlow()

    /**
     * Inicializa el almacén con el contexto de aplicación Android.
     * Es idempotente si ya fue inicializado con el mismo almacén.
     */
    @Synchronized
    fun init(context: Context) {
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        init(sharedPrefs)
    }

    /**
     * Inicializa el almacén a partir de una instancia de [SharedPreferences] (producción o tests).
     */
    @Synchronized
    fun init(sharedPreferences: SharedPreferences) {
        prefs = sharedPreferences
        loadAllFromPrefs()
    }

    /**
     * Limpia el estado en memoria (y en SharedPreferences si está conectado) para pruebas unitarias.
     */
    @Synchronized
    fun resetForTests() {
        prefs?.edit()?.clear()?.commit()
        detachForRestartSimulation()
    }

    /**
     * Desconecta y limpia únicamente el estado en memoria sin borrar `SharedPreferences`,
     * permitiendo simular el cierre y reinicio completo de la aplicación en pruebas unitarias.
     */
    @Synchronized
    fun detachForRestartSimulation() {
        prefs = null
        _playlists.value = emptyList()
        _likedSongs.value = emptyList()
        _savedCollections.value = emptyList()
        _pinnedPlaylists.value = emptySet()
        _librarySort.value = LibrarySort.DEFAULT
        LikeState.clear()
    }

    /**
     * Crea una nueva playlist local con título [title] y canciones iniciales opcionales (sin duplicados).
     */
    @Synchronized
    fun createPlaylist(
        title: String,
        initialSongs: List<Song> = emptyList(),
        customId: String? = null,
    ): LocalPlaylist {
        val cleanTitle = title.trim().ifEmpty { "Nueva playlist" }
        val id = customId ?: UUID.randomUUID().toString()
        val distinctSongs = initialSongs.distinctBy { it.videoId }
        val created = LocalPlaylist(
            id = id,
            title = cleanTitle,
            songs = distinctSongs,
        )
        val current = _playlists.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == id }
        if (existingIdx >= 0) {
            current[existingIdx] = created
        } else {
            current.add(0, created)
        }
        _playlists.value = current
        persistPlaylists(current)
        return created
    }

    /**
     * Guarda o actualiza una playlist local completa.
     */
    @Synchronized
    fun savePlaylist(playlist: LocalPlaylist) {
        val sanitized = playlist.copy(
            title = playlist.title.trim().ifEmpty { "Playlist" },
            songs = playlist.songs.distinctBy { it.videoId },
        )
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == sanitized.id }
        if (index != -1) {
            current[index] = sanitized
        } else {
            current.add(0, sanitized)
        }
        _playlists.value = current
        persistPlaylists(current)
    }

    /**
     * Elimina una playlist local por su [id] (acepta tanto `id` puro como `local:playlist:id`).
     */
    @Synchronized
    fun deletePlaylist(id: String) {
        val cleanId = id.removePrefix("local:playlist:")
        val current = _playlists.value.filterNot { it.id == cleanId }
        _playlists.value = current
        persistPlaylists(current)
        val browseId = "local:playlist:$cleanId"
        if (browseId in _pinnedPlaylists.value) {
            val updatedPins = _pinnedPlaylists.value - browseId
            _pinnedPlaylists.value = updatedPins
            persistPinnedPlaylists(updatedPins)
        }
    }

    /**
     * Renombra una playlist local existente.
     */
    @Synchronized
    fun renamePlaylist(id: String, newTitle: String) {
        val cleanId = id.removePrefix("local:playlist:")
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val current = _playlists.value.map {
            if (it.id == cleanId) it.copy(title = trimmed) else it
        }
        _playlists.value = current
        persistPlaylists(current)
    }

    /**
     * Obtiene una playlist local por su [id] (o `local:playlist:id`).
     */
    fun getPlaylist(id: String): LocalPlaylist? {
        val cleanId = id.removePrefix("local:playlist:")
        return _playlists.value.find { it.id == cleanId }
    }

    /**
     * Añade una canción a la playlist local indicada, evitando duplicados por `videoId`.
     *
     * @return `true` si la canción se añadió, `false` si ya existía en la playlist o la playlist no existe.
     */
    @Synchronized
    fun addSongToPlaylist(playlistId: String, song: Song): Boolean {
        val cleanId = playlistId.removePrefix("local:playlist:")
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == cleanId }
        if (index == -1) return false
        val target = current[index]
        if (target.songs.any { it.videoId == song.videoId }) {
            return false
        }
        val updated = target.copy(songs = target.songs + song)
        current[index] = updated
        _playlists.value = current
        persistPlaylists(current)
        return true
    }

    /**
     * Añade múltiples canciones a una playlist local evitando duplicados.
     *
     * @return Número de canciones nuevas añadidas.
     */
    @Synchronized
    fun addSongsToPlaylist(playlistId: String, songs: List<Song>): Int {
        val cleanId = playlistId.removePrefix("local:playlist:")
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == cleanId }
        if (index == -1) return 0
        val target = current[index]
        val existingIds = target.songs.mapTo(HashSet()) { it.videoId }
        val toAdd = songs.filter { it.videoId.isNotBlank() && existingIds.add(it.videoId) }
        if (toAdd.isEmpty()) return 0
        current[index] = target.copy(songs = target.songs + toAdd)
        _playlists.value = current
        persistPlaylists(current)
        return toAdd.size
    }

    /**
     * Elimina una canción (`videoId`) de la playlist local indicada.
     */
    @Synchronized
    fun removeSongFromPlaylist(playlistId: String, videoId: String) {
        val cleanId = playlistId.removePrefix("local:playlist:")
        val current = _playlists.value.toMutableList()
        val index = current.indexOfFirst { it.id == cleanId }
        if (index == -1) return
        val target = current[index]
        val filtered = target.songs.filterNot { it.videoId == videoId }
        if (filtered.size == target.songs.size) return
        current[index] = target.copy(songs = filtered)
        _playlists.value = current
        persistPlaylists(current)
    }

    // ---- Canciones Favoritas (Liked Music) ----------------------------------

    /**
     * Indica si una canción (`videoId`) está marcada como favorita.
     */
    fun isLiked(videoId: String?): Boolean {
        if (videoId.isNullOrBlank()) return false
        return LikeState[videoId] == LikeStatus.LIKE || _likedSongs.value.any { it.videoId == videoId }
    }

    /**
     * Alterna el estado de favorito ([LikeStatus.LIKE] / [LikeStatus.INDIFFERENT]) de una canción,
     * sincronizando [LikeState] y la lista persistente [likedSongs] sin duplicados.
     */
    @Synchronized
    fun toggleLike(song: Song): LikeStatus {
        val nextStatus = if (isLiked(song.videoId)) LikeStatus.INDIFFERENT else LikeStatus.LIKE
        setLikeStatus(song, nextStatus)
        return nextStatus
    }

    /**
     * Establece explícitamente el estado [status] para [song] en [LikeState] y en el almacén persistente.
     */
    @Synchronized
    fun setLikeStatus(song: Song, status: LikeStatus) {
        LikeState[song.videoId] = status
        val current = _likedSongs.value.toMutableList()
        if (status == LikeStatus.LIKE) {
            val likedSong = song.copy(likeStatus = LikeStatus.LIKE)
            val existingIdx = current.indexOfFirst { it.videoId == song.videoId }
            if (existingIdx >= 0) {
                current[existingIdx] = likedSong
            } else {
                current.add(0, likedSong)
            }
        } else {
            current.removeAll { it.videoId == song.videoId }
        }
        _likedSongs.value = current
        persistLikedSongs(current)
    }

    /**
     * Fusiona canciones favoritas remotas (si existe sesión en YouTube Music) con las locales sin duplicados.
     */
    @Synchronized
    fun mergeRemoteLikedSongs(remoteSongs: List<Song>) {
        if (remoteSongs.isEmpty()) return
        val current = _likedSongs.value.toMutableList()
        val existingIds = current.mapTo(HashSet()) { it.videoId }
        var changed = false
        remoteSongs.forEach { remote ->
            LikeState[remote.videoId] = LikeStatus.LIKE
            if (existingIds.add(remote.videoId)) {
                current.add(remote.copy(likeStatus = LikeStatus.LIKE))
                changed = true
            }
        }
        if (changed) {
            _likedSongs.value = current
            persistLikedSongs(current)
        }
    }

    // ---- Colecciones Guardadas (Álbumes, Artistas, Playlists remotas) -------

    /**
     * Indica si una colección (`browseId`) está guardada en la Biblioteca.
     */
    fun isCollectionSaved(browseId: String?): Boolean {
        if (browseId.isNullOrBlank()) return false
        return _savedCollections.value.any { it.browseId == browseId }
    }

    /**
     * Alterna el guardado de una colección (Álbum, Artista o Playlist) en la Biblioteca.
     *
     * @return `true` si quedó guardada, `false` si se eliminó de guardados.
     */
    @Synchronized
    fun toggleSavedCollection(item: ShelfItem): Boolean {
        val browseId = item.browseId ?: return false
        val current = _savedCollections.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.browseId == browseId }
        val saved = if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            false
        } else {
            current.add(0, item)
            true
        }
        _savedCollections.value = current
        persistSavedCollections(current)
        return saved
    }

    // ---- Fijación (Pin) y Ordenación de Biblioteca --------------------------

    @Synchronized
    fun togglePinPlaylist(browseId: String) {
        val current = _pinnedPlaylists.value.toMutableSet()
        if (!current.add(browseId)) {
            current.remove(browseId)
        }
        _pinnedPlaylists.value = current
        persistPinnedPlaylists(current)
    }

    @Synchronized
    fun setLibrarySort(sort: LibrarySort) {
        _librarySort.value = sort
        prefs?.edit()?.putString(KEY_LIBRARY_SORT, sort.name)?.apply()
    }

    // ---- Serialización JSON en SharedPreferences ----------------------------

    private fun loadAllFromPrefs() {
        val p = prefs ?: return

        // 1. Playlists
        val playlistsJson = p.getString(KEY_PLAYLISTS, null)
        if (!playlistsJson.isNullOrBlank()) {
            runCatching {
                val arr = Json.parseToJsonElement(playlistsJson).jsonArray
                val list = ArrayList<LocalPlaylist>(arr.size)
                for (element in arr) {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                    val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Playlist"
                    val songsArr = obj["songs"]?.jsonArray ?: JsonArray(emptyList())
                    val songs = parseSongsJsonArray(songsArr)
                    list.add(LocalPlaylist(id = id, title = title, songs = songs))
                }
                _playlists.value = list
            }
        } else {
            _playlists.value = emptyList()
        }

        // 2. Liked Songs
        val likedJson = p.getString(KEY_LIKED_SONGS, null)
        if (!likedJson.isNullOrBlank()) {
            runCatching {
                val arr = Json.parseToJsonElement(likedJson).jsonArray
                val songs = parseSongsJsonArray(arr).map {
                    it.copy(likeStatus = LikeStatus.LIKE)
                }
                _likedSongs.value = songs
                LikeState.seedLiked(songs.mapTo(HashSet()) { it.videoId })
            }
        } else {
            _likedSongs.value = emptyList()
        }

        // 3. Saved Collections
        val collectionsJson = p.getString(KEY_SAVED_COLLECTIONS, null)
        if (!collectionsJson.isNullOrBlank()) {
            runCatching {
                val arr = Json.parseToJsonElement(collectionsJson).jsonArray
                val items = ArrayList<ShelfItem>(arr.size)
                for (element in arr) {
                    val obj = element.jsonObject
                    val browseId = obj["browseId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                    items.add(
                        ShelfItem(
                            title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            subtitle = obj["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            thumbnailUrl = obj["thumbnailUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                            videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                            browseId = browseId,
                        )
                    )
                }
                _savedCollections.value = items
            }
        } else {
            _savedCollections.value = emptyList()
        }

        // 4. Pinned Playlists
        val pinned = p.getStringSet(KEY_PINNED_PLAYLISTS, emptySet()).orEmpty()
        _pinnedPlaylists.value = pinned.toSet()

        // 5. Library Sort
        val sortName = p.getString(KEY_LIBRARY_SORT, LibrarySort.DEFAULT.name)
        _librarySort.value = runCatching {
            LibrarySort.valueOf(sortName ?: LibrarySort.DEFAULT.name)
        }.getOrDefault(LibrarySort.DEFAULT)
    }

    private fun parseSongsJsonArray(songsArr: JsonArray): List<Song> {
        val songs = ArrayList<Song>(songsArr.size)
        for (element in songsArr) {
            val sObj: JsonObject = element.jsonObject
            val videoId = sObj["videoId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            songs.add(
                Song(
                    videoId = videoId,
                    title = sObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown title",
                    artist = sObj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown artist",
                    thumbnailUrl = sObj["thumbnailUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    durationText = sObj["durationText"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    artistId = sObj["artistId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    albumId = sObj["albumId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    albumName = sObj["albumName"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    localUri = sObj["localUri"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    localPath = sObj["localPath"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
                    isExplicit = sObj["isExplicit"]?.jsonPrimitive?.booleanOrNull,
                )
            )
        }
        return songs.distinctBy { it.videoId }
    }

    private fun songsToJsonArray(songs: List<Song>): JsonArray = buildJsonArray {
        songs.forEach { s ->
            add(
                buildJsonObject {
                    put("videoId", s.videoId)
                    put("title", s.title)
                    put("artist", s.artist)
                    if (!s.thumbnailUrl.isNullOrBlank()) put("thumbnailUrl", s.thumbnailUrl)
                    if (!s.durationText.isNullOrBlank()) put("durationText", s.durationText)
                    if (!s.artistId.isNullOrBlank()) put("artistId", s.artistId)
                    if (!s.albumId.isNullOrBlank()) put("albumId", s.albumId)
                    if (!s.albumName.isNullOrBlank()) put("albumName", s.albumName)
                    if (!s.localUri.isNullOrBlank()) put("localUri", s.localUri)
                    if (!s.localPath.isNullOrBlank()) put("localPath", s.localPath)
                    if (s.isExplicit != null) put("isExplicit", s.isExplicit)
                }
            )
        }
    }

    private fun persistPlaylists(list: List<LocalPlaylist>) {
        val p = prefs ?: return
        val arr = buildJsonArray {
            list.forEach { playlist ->
                add(
                    buildJsonObject {
                        put("id", playlist.id)
                        put("title", playlist.title)
                        put("songs", songsToJsonArray(playlist.songs))
                    }
                )
            }
        }
        p.edit().putString(KEY_PLAYLISTS, arr.toString()).apply()
    }

    private fun persistLikedSongs(songs: List<Song>) {
        val p = prefs ?: return
        p.edit().putString(KEY_LIKED_SONGS, songsToJsonArray(songs).toString()).apply()
    }

    private fun persistSavedCollections(items: List<ShelfItem>) {
        val p = prefs ?: return
        val arr = buildJsonArray {
            items.forEach { item ->
                val browseId = item.browseId ?: return@forEach
                add(
                    buildJsonObject {
                        put("title", item.title)
                        put("subtitle", item.subtitle)
                        if (!item.thumbnailUrl.isNullOrBlank()) put("thumbnailUrl", item.thumbnailUrl)
                        if (!item.videoId.isNullOrBlank()) put("videoId", item.videoId)
                        put("browseId", browseId)
                    }
                )
            }
        }
        p.edit().putString(KEY_SAVED_COLLECTIONS, arr.toString()).apply()
    }

    private fun persistPinnedPlaylists(pinned: Set<String>) {
        val p = prefs ?: return
        p.edit().putStringSet(KEY_PINNED_PLAYLISTS, pinned.toSet()).apply()
    }
}

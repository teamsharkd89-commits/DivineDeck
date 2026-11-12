package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.ui.LibraryDataSource
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.divinedeck.launcher.feature.music.ui.MusicLibraryViewModel
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.model.UserPlaylist
import com.music.bitchord.data.settings.LibrarySort
import com.music.bitchord.data.spotify.LocalPlaylistStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.5B:
 * - Integración de la pantalla original `LibraryScreen` (BIBLIOTECA) de BitChord 1.8.1.
 * - Creación, renombrado, eliminación y apertura de playlists locales (`LocalPlaylistStore`).
 * - Adición, prevención de duplicados y eliminación de canciones en playlists.
 * - Guardado y eliminación de canciones favoritas (`LocalPlaylistStore` + `LikeState`) y colecciones guardadas.
 * - Persistencia real en `SharedPreferences` a través de reinicios simulados de la aplicación.
 * - Reproducción desde Biblioteca y Playlists conectada al único motor Media3 (`MediaControllerRepositoryImpl`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordLibraryPlaylistsIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        LocalPlaylistStore.resetForTests()
        LikeState.clear()
    }

    @After
    fun tearDown() {
        LocalPlaylistStore.resetForTests()
        LikeState.clear()
        Dispatchers.resetMain()
    }

    /**
     * Implementación en memoria de `SharedPreferences` para validar persistencia real
     * de `LocalPlaylistStore` entre reinicios de sesión.
     */
    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = backingMap.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            backingMap[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (backingMap[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            backingMap[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            backingMap[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            backingMap[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            backingMap[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = backingMap.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class EditorImpl(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    private class PrefsTestContext(
        private val prefs: SharedPreferences = InMemorySharedPreferences()
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private class FakeMediaSessionBridge : BitChordMediaSessionBridge {
        override var isConnected: Boolean = true
        override var isPlaying: Boolean = false
        override var playbackState: Int = Player.STATE_READY
        override var isLoading: Boolean = false
        override var currentPositionMs: Long = 0L
        override var durationMs: Long = 215_000L
        override var currentMediaItem: MediaItem? = null
        override var availableCommands: Player.Commands? = null

        var lastSetMediaItem: MediaItem? = null
        var lastPlayWhenReady: Boolean? = null
        val listeners = mutableListOf<Player.Listener>()

        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true }
        override fun pause() { isPlaying = false }
        override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun setPlayerListener(listener: Player.Listener?) { listener?.let { listeners.add(it) } }
        override fun removePlayerListener(listener: Player.Listener) { listeners.remove(listener) }
        override fun sendCustomCommand(action: String) {}
        override fun sendCustomCommand(action: String, args: android.os.Bundle) {}
        override fun release() { listeners.clear() }

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            lastSetMediaItem = mediaItem
            lastPlayWhenReady = playWhenReady
            currentMediaItem = mediaItem
            if (playWhenReady) isPlaying = true
            listeners.forEach {
                it.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
            }
        }
    }

    // =========================================================================
    // 1. NAVEGACIÓN DE 5 SECCIONES EN MUSIC CENTER
    // =========================================================================

    @Test
    fun `MusicCenterDestination exposes 5 tabs in exact order INICIO EXPLORAR BUSCAR BIBLIOTECA REPRODUCTOR`() {
        val tabs = MusicCenterDestination.entries
        assertEquals(5, tabs.size)
        assertEquals(MusicCenterDestination.HOME, tabs[0])
        assertEquals("INICIO", tabs[0].title)
        assertEquals(MusicCenterDestination.EXPLORE, tabs[1])
        assertEquals("EXPLORAR", tabs[1].title)
        assertEquals(MusicCenterDestination.SEARCH, tabs[2])
        assertEquals("BUSCAR", tabs[2].title)
        assertEquals(MusicCenterDestination.LIBRARY, tabs[3])
        assertEquals("BIBLIOTECA", tabs[3].title)
        assertEquals(MusicCenterDestination.PLAYER, tabs[4])
        assertEquals("REPRODUCTOR", tabs[4].title)
    }

    // =========================================================================
    // 2. GESTIÓN DE PLAYLISTS Y PERSISTENCIA REAL (LocalPlaylistStore)
    // =========================================================================

    @Test
    fun `LocalPlaylistStore creates renames adds songs prevents duplicates removes songs and deletes playlists`() {
        val prefs = InMemorySharedPreferences()
        LocalPlaylistStore.init(PrefsTestContext(prefs))

        val track1 = Song(
            videoId = "v_daft_01",
            title = "Harder, Better, Faster, Stronger",
            artist = "Daft Punk",
            thumbnailUrl = "https://img/discovery.jpg",
            durationText = "3:45",
        )
        val track2 = Song(
            videoId = "v_kavinsky_02",
            title = "Nightcall",
            artist = "Kavinsky",
            thumbnailUrl = "https://img/outrun.jpg",
            durationText = "4:18",
        )

        // 1. Crear playlist
        val created = LocalPlaylistStore.createPlaylist("Ruta Nocturna")
        assertEquals("Ruta Nocturna", created.name)
        assertEquals(0, created.songCount)
        assertEquals(1, LocalPlaylistStore.playlists.value.size)

        // 2. Añadir primera canción
        val addedFirst = LocalPlaylistStore.addSongToPlaylist(created.id, track1)
        assertTrue(addedFirst)
        assertEquals(1, LocalPlaylistStore.getPlaylist(created.id)?.songCount)
        assertEquals("https://img/discovery.jpg", LocalPlaylistStore.getPlaylist(created.id)?.thumbnailUrl)

        // 3. Evitar duplicados al intentar añadir la misma canción
        val addedDuplicate = LocalPlaylistStore.addSongToPlaylist(created.id, track1)
        assertFalse(addedDuplicate)
        assertEquals(1, LocalPlaylistStore.getPlaylist(created.id)?.songCount)

        // 4. Añadir segunda canción
        val addedSecond = LocalPlaylistStore.addSongToPlaylist(created.id, track2)
        assertTrue(addedSecond)
        assertEquals(2, LocalPlaylistStore.getPlaylist(created.id)?.songCount)

        // 5. Renombrar playlist
        LocalPlaylistStore.renamePlaylist(created.id, "Synthwave Drive")
        assertEquals("Synthwave Drive", LocalPlaylistStore.getPlaylist(created.id)?.name)

        // 6. Eliminar primera canción de la playlist
        LocalPlaylistStore.removeSongFromPlaylist(created.id, track1.videoId)
        val afterRemove = LocalPlaylistStore.getPlaylist(created.id)
        assertNotNull(afterRemove)
        assertEquals(1, afterRemove!!.songCount)
        assertEquals("v_kavinsky_02", afterRemove.songs.first().videoId)

        // 7. Eliminar playlist completa
        LocalPlaylistStore.deletePlaylist(created.id)
        assertNull(LocalPlaylistStore.getPlaylist(created.id))
        assertTrue(LocalPlaylistStore.playlists.value.isEmpty())
    }

    @Test
    fun `LocalPlaylistStore persists playlists favorites saved collections and pinned state across app restarts`() {
        val sharedBackingMap = mutableMapOf<String, Any?>()

        // Sesión 1: El usuario crea una playlist, añade una canción, marca una favorita y guarda un álbum
        val session1Context = PrefsTestContext(InMemorySharedPreferences(sharedBackingMap))
        LocalPlaylistStore.init(session1Context)

        val roadSong = Song(
            videoId = "v_starboy",
            title = "Starboy",
            artist = "The Weeknd",
            thumbnailUrl = "https://img/starboy.jpg",
            durationText = "3:50",
        )
        val favSong = Song(
            videoId = "v_blinding_lights",
            title = "Blinding Lights",
            artist = "The Weeknd",
            thumbnailUrl = "https://img/afterhours.jpg",
            durationText = "3:20",
        )
        val savedAlbum = ShelfItem(
            title = "Random Access Memories",
            subtitle = "Album • Daft Punk",
            thumbnailUrl = "https://img/ram.jpg",
            videoId = null,
            browseId = "MPREb_ram_2013",
        )

        val playlist = LocalPlaylistStore.createPlaylist("Favoritas del Viaje")
        LocalPlaylistStore.addSongToPlaylist(playlist.id, roadSong)
        val likeStatus = LocalPlaylistStore.toggleLike(favSong)
        assertEquals(LikeStatus.LIKE, likeStatus)
        assertTrue(LocalPlaylistStore.toggleSavedCollection(savedAlbum))
        LocalPlaylistStore.togglePinPlaylist("local:playlist:${playlist.id}")
        assertTrue("local:playlist:${playlist.id}" in LocalPlaylistStore.pinnedPlaylists.value)
        LocalPlaylistStore.setLibrarySort(LibrarySort.TITLE_ASC)

        // Simular cierre de la app y reinicio limpio en Sesión 2 con el mismo almacenamiento SharedPreferences
        LocalPlaylistStore.detachForRestartSimulation()
        assertTrue(LocalPlaylistStore.playlists.value.isEmpty())
        assertTrue(LocalPlaylistStore.likedSongs.value.isEmpty())

        val session2Context = PrefsTestContext(InMemorySharedPreferences(sharedBackingMap))
        LocalPlaylistStore.init(session2Context)

        // Verificar que todos los datos se restauraron íntegramente tras el reinicio
        assertEquals(1, LocalPlaylistStore.playlists.value.size)
        val restoredPlaylist = LocalPlaylistStore.playlists.value.first()
        assertEquals("Favoritas del Viaje", restoredPlaylist.name)
        assertEquals(1, restoredPlaylist.songs.size)
        assertEquals("v_starboy", restoredPlaylist.songs.first().videoId)

        assertEquals(1, LocalPlaylistStore.likedSongs.value.size)
        assertTrue(LocalPlaylistStore.isLiked("v_blinding_lights"))
        assertEquals("Blinding Lights", LocalPlaylistStore.likedSongs.value.first().title)

        assertEquals(1, LocalPlaylistStore.savedCollections.value.size)
        assertTrue(LocalPlaylistStore.isCollectionSaved("MPREb_ram_2013"))

        assertTrue("local:playlist:${playlist.id}" in LocalPlaylistStore.pinnedPlaylists.value)
        assertEquals(LibrarySort.TITLE_ASC, LocalPlaylistStore.librarySort.value)
    }

    // =========================================================================
    // 3. PARSING DE BIBLIOTECA Y PLAYLISTS DE YOUTUBE MUSIC (InnertubeParser)
    // =========================================================================

    @Test
    fun `InnertubeParser parseLibraryItems and parseUserPlaylists extract library shelves and user playlists`() {
        val sampleLibraryJson = """
        {
          "contents": {
            "singleColumnBrowseResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "gridRenderer": {
                              "items": [
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [ { "text": "Liked Music" } ] },
                                    "subtitle": { "runs": [ { "text": "Auto playlist • 42 songs" } ] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "VLLM" }
                                    },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://img/liked.jpg", "width": 544, "height": 544 }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                },
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [ { "text": "Chill Synth" } ] },
                                    "subtitle": { "runs": [ { "text": "18 tracks" } ] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "VLPL_chill_synth_01" }
                                    },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://img/chill.jpg", "width": 544, "height": 544 }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val json = Json.decodeFromString<JsonObject>(sampleLibraryJson)
        val items = InnertubeParser.parseLibraryItems(json)
        assertEquals(2, items.size)
        assertEquals("Liked Music", items[0].title)
        assertEquals("VLLM", items[0].browseId)
        assertEquals("Chill Synth", items[1].title)
        assertEquals("VLPL_chill_synth_01", items[1].browseId)

        val userPlaylists = InnertubeParser.parseUserPlaylists(json)
        // VLLM ("LM") es filtrado como playlist automática; "PL_chill_synth_01" es playlist editable del usuario
        assertEquals(1, userPlaylists.size)
        assertEquals("PL_chill_synth_01", userPlaylists[0].playlistId)
        assertEquals("Chill Synth", userPlaylists[0].title)
    }

    // =========================================================================
    // 4. MUSIC LIBRARY VIEW MODEL & REPRODUCCIÓN DESDE BIBLIOTECA Y PLAYLISTS
    // =========================================================================

    @Test
    fun `MusicLibraryViewModel manages playlists favorites opens playlist detail and plays song via MediaControllerRepository`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        LocalPlaylistStore.init(PrefsTestContext(prefs))

        val fakeLibraryDataSource = object : LibraryDataSource {
            override val isSignedIn: Boolean = false
            override suspend fun remoteLibrary(): Result<LibraryPage> =
                Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
            override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
                Result.failure(UnsupportedOperationException())
            override suspend fun artistPage(browseId: String): Result<ArtistPage> =
                Result.failure(UnsupportedOperationException())
            override suspend fun localDeviceSongs(context: Context?): List<Song> = emptyList()
        }

        val viewModel = MusicLibraryViewModel(
            dataSource = fakeLibraryDataSource,
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        val song1 = Song(
            videoId = "v_instant_crush",
            title = "Instant Crush",
            artist = "Daft Punk",
            thumbnailUrl = "https://img/ram.jpg",
            durationText = "5:37",
        )
        val song2 = Song(
            videoId = "v_Giorgio",
            title = "Giorgio by Moroder",
            artist = "Daft Punk",
            thumbnailUrl = "https://img/ram.jpg",
            durationText = "9:04",
        )

        // 1. Crear playlist con canción inicial desde el ViewModel
        val created = viewModel.createPlaylist(
            title = "Clásicos Electrónicos",
            initialSong = song1,
        )
        advanceUntilIdle()

        val playlistBrowseId = "${MusicLibraryViewModel.LOCAL_PLAYLIST_PREFIX}${created.id}"
        assertTrue(viewModel.isEditablePlaylist(playlistBrowseId))

        // Añadir segunda canción mediante el selector de playlists (UserPlaylist)
        viewModel.addSongToUserPlaylists(
            song = song2,
            playlists = listOf(
                UserPlaylist(
                    playlistId = playlistBrowseId,
                    title = created.name,
                    subtitle = "1 canción",
                    thumbnailUrl = null,
                )
            ),
        )
        advanceUntilIdle()

        // 2. Marcar canción como favorita y verificar sincronización con LikeState
        val statusAfterLike = viewModel.toggleFavoriteSong(song1)
        advanceUntilIdle()
        assertEquals(LikeStatus.LIKE, statusAfterLike)
        assertTrue(viewModel.isSongLiked(song1))
        assertEquals(LikeStatus.LIKE, LikeState[song1.videoId])

        // 3. Verificar estantes expuestos en libraryState
        val libPage = (viewModel.libraryState.value as UiState.Success).data
        assertTrue(libPage.shelves.any { it.title == "Playlists" })
        assertTrue(libPage.shelves.any { it.title == "Canciones favoritas" })

        val playlistsShelf = libPage.shelves.first { it.title == "Playlists" }
        val customPlaylistCard = playlistsShelf.items.firstOrNull { it.browseId == playlistBrowseId }
        assertNotNull(customPlaylistCard)
        assertEquals("Clásicos Electrónicos", customPlaylistCard!!.title)

        // 4. Abrir la playlist creada en Biblioteca y verificar sus canciones
        viewModel.onShelfItemClick(
            item = customPlaylistCard,
            onPlaySong = {},
        )
        advanceUntilIdle()

        val openCollection = viewModel.selectedCollection.value
        assertNotNull(openCollection)
        assertEquals(playlistBrowseId, openCollection!!.browseId)
        assertEquals("Clásicos Electrónicos", openCollection.title)
        val playlistSongs = (openCollection.songsState as UiState.Success).data
        assertEquals(2, playlistSongs.size)
        assertEquals("Instant Crush", playlistSongs[0].title)
        assertEquals(PlaybackSourceType.BROWSE, playlistSongs[0].playbackSourceType)

        // 5. Reproducir canción desde la playlist abierta a través del único motor MediaControllerRepositoryImpl
        val fakeBridge = FakeMediaSessionBridge()
        val repoJob = SupervisorJob()
        val repoScope = CoroutineScope(testDispatcher + repoJob)
        val repository = MediaControllerRepositoryImpl(
            context = PrefsTestContext(prefs),
            connector = object : BitChordSessionConnector {
                override fun connect(
                    context: Context,
                    onConnected: (BitChordMediaSessionBridge) -> Unit,
                    onDisconnected: () -> Unit,
                    onError: (Throwable) -> Unit,
                ): AutoCloseable {
                    onConnected(fakeBridge)
                    return AutoCloseable {}
                }
            },
            coroutineScope = repoScope,
        )

        try {
            repository.playSong(playlistSongs[0])

            assertEquals("v_instant_crush", fakeBridge.lastSetMediaItem?.mediaId)
            assertEquals(true, fakeBridge.lastPlayWhenReady)
            assertEquals("v_instant_crush", repository.currentTrack.value.mediaId)
            assertEquals("Instant Crush", repository.currentTrack.value.title)
            assertEquals("Daft Punk", repository.currentTrack.value.artist)
            assertTrue(repository.playbackState.value.isPlaying)
        } finally {
            repository.disconnect()
            repoJob.cancel()
        }

        // 6. Eliminar una pista de la playlist abierta y verificar actualización reactiva inmediata
        viewModel.removeSongFromPlaylist(playlistBrowseId, song1.videoId)
        advanceUntilIdle()

        val updatedSongs = (viewModel.selectedCollection.value!!.songsState as UiState.Success).data
        assertEquals(1, updatedSongs.size)
        assertEquals("v_Giorgio", updatedSongs[0].videoId)

        // 7. Cerrar la colección
        assertTrue(viewModel.closeCollection())
        assertNull(viewModel.selectedCollection.value)
    }
}

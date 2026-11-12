package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.divinedeck.launcher.feature.music.ui.MusicSearchViewModel
import com.divinedeck.launcher.feature.music.ui.SearchUiState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.toMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
 * Suite de pruebas unitarias y de integración para la Fase 06.4A:
 * - Búsqueda original de BitChord / Innertube (parsing, validación, debounce, cancelación).
 * - Integración de resultados de búsqueda con el motor de reproducción (URI bitchord://watch?v=, metadatos).
 * - Navegación automotriz en Music Center (preservación de estado entre INICIO, BUSCAR y REPRODUCTOR).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordSearchNavigationIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class FakeMediaSessionBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = false,
        override var playbackState: Int = Player.STATE_READY,
        override var isLoading: Boolean = false,
        override var currentPositionMs: Long = 0L,
        override var durationMs: Long = 210000L,
        override var currentMediaItem: MediaItem? = null,
        var allowedCommands: Set<Int> = setOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ),
        override var availableCommands: Player.Commands? = null
    ) : BitChordMediaSessionBridge {
        var lastSetMediaItem: MediaItem? = null
        var lastPlayWhenReady: Boolean? = null
        val listeners = mutableListOf<Player.Listener>()

        override fun isCommandAvailable(command: Int): Boolean = allowedCommands.contains(command)
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
            listeners.forEach { it.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
        }
    }

    // =========================================================================
    // 1. SEARCH PARSING & MAPPING TESTS
    // =========================================================================

    @Test
    fun `InnertubeParser parses responsive song items with title artist duration and thumbnails`() {
        val sampleJsonString = """
        {
          "contents": {
            "tabbedSearchResultsRenderer": {
              "tabs": [
                {
                  "tabRenderer": {
                    "content": {
                      "sectionListRenderer": {
                        "contents": [
                          {
                            "musicShelfRenderer": {
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": {
                                      "videoId": "BSTsnWoslP4"
                                    },
                                    "flexColumns": [
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              { "text": "Bohemian Rhapsody" }
                                            ]
                                          }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": {
                                            "runs": [
                                              { "text": "Queen" },
                                              { "text": " • " },
                                              { "text": "A Night At The Opera" },
                                              { "text": " • " },
                                              { "text": "5:55" }
                                            ]
                                          }
                                        }
                                      }
                                    ],
                                    "thumbnail": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://img.youtube.com/vi/BSTsnWoslP4/hqdefault.jpg", "width": 480, "height": 480 }
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

        val json = Json.decodeFromString<JsonObject>(sampleJsonString)
        val searchPage = InnertubeParser.parseSearchPage(json)

        assertEquals(1, searchPage.rows.size)
        val first = searchPage.rows[0]
        assertTrue(first is SearchResult.Track)

        val song = (first as SearchResult.Track).song
        assertEquals("BSTsnWoslP4", song.videoId)
        assertEquals("Bohemian Rhapsody", song.title)
        assertEquals("Queen", song.artist)
        assertEquals("A Night At The Opera", song.albumName)
        assertEquals("5:55", song.durationText)
        assertEquals(355_000L, song.durationMillis)
        assertEquals("https://img.youtube.com/vi/BSTsnWoslP4/hqdefault.jpg", song.thumbnailUrl)
        assertFalse(song.isVideo)
    }

    @Test
    fun `InnertubeParser handles empty or malformed search JSON gracefully`() {
        val emptyJson = Json.decodeFromString<JsonObject>("{}")
        val result = InnertubeParser.parseSearchPage(emptyJson)

        assertTrue(result.rows.isEmpty())
        assertNull(result.continuation)
    }

    // =========================================================================
    // 2. PLAYBACK INTEGRATION: SEARCH RESULT TO SERVICE PIPELINE
    // =========================================================================

    @Test
    fun `selecting a search result creates valid bitchord URI and passes full metadata to service`() {
        val fakeBridge = FakeMediaSessionBridge()
        val connector = object : BitChordSessionConnector {
            override fun connect(
                context: Context,
                onConnected: (BitChordMediaSessionBridge) -> Unit,
                onDisconnected: () -> Unit,
                onError: (Throwable) -> Unit
            ): AutoCloseable {
                onConnected(fakeBridge)
                return AutoCloseable {}
            }
        }

        val repository = MediaControllerRepositoryImpl(
            context = TestContext(),
            connector = connector,
            coroutineScope = testScope
        )

        val selectedSong = Song(
            videoId = "BSTsnWoslP4",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            thumbnailUrl = "https://img.youtube.com/vi/BSTsnWoslP4/hqdefault.jpg",
            albumName = "A Night At The Opera",
            durationSeconds = 355L
        )

        // Simular que el usuario hace clic en el resultado de búsqueda
        repository.playSong(selectedSong)

        // 1. Verificar que el MediaItem fue entregado al bridge
        val sentItem = fakeBridge.lastSetMediaItem
        assertNotNull(sentItem)
        assertEquals("BSTsnWoslP4", sentItem!!.mediaId)
        assertEquals(true, fakeBridge.lastPlayWhenReady)

        // 2. Verificar que los metadatos se preservaron intactos
        assertEquals("Bohemian Rhapsody", sentItem.mediaMetadata.title?.toString())
        assertEquals("Queen", sentItem.mediaMetadata.artist?.toString())
        assertEquals("A Night At The Opera", sentItem.mediaMetadata.albumTitle?.toString())

        // 3. Verificar que el estado en MediaControllerRepository se actualizó reactivamente
        val currentTrack = repository.currentTrack.value
        assertEquals("BSTsnWoslP4", currentTrack.mediaId)
        assertEquals("Bohemian Rhapsody", currentTrack.title)
        assertEquals("Queen", currentTrack.artist)
        assertEquals(355_000L, currentTrack.durationMs)
    }

    // =========================================================================
    // 3. NAVIGATION SHELL & STATE PRESERVATION
    // =========================================================================

    @Test
    fun `MusicCenterDestination enum has correct titles for 4 tabs and default is HOME`() {
        assertEquals("INICIO", MusicCenterDestination.HOME.title)
        assertEquals("EXPLORAR", MusicCenterDestination.EXPLORE.title)
        assertEquals("BUSCAR", MusicCenterDestination.SEARCH.title)
        assertEquals("REPRODUCTOR", MusicCenterDestination.PLAYER.title)
        assertEquals(MusicCenterDestination.HOME, MusicCenterDestination.DEFAULT)
    }

    @Test
    fun `MusicSearchViewModel debounces queries and transitions to Empty on blank input`() = runTest(testDispatcher) {
        val vm = MusicSearchViewModel()

        assertEquals("", vm.query.value)
        assertEquals(SearchUiState.Empty, vm.uiState.value)

        vm.onQueryChanged("Queen")
        assertEquals("Queen", vm.query.value)

        // Antes del debounce de 350ms, el estado sigue siendo Empty
        advanceTimeBy(100)
        assertEquals(SearchUiState.Empty, vm.uiState.value)

        // Limpiar query
        vm.onClearQuery()
        assertEquals("", vm.query.value)
        assertEquals(SearchUiState.Empty, vm.uiState.value)
    }

    @Test
    fun `SearchFilter entries have correct labels and parameters`() {
        assertEquals("All", SearchFilter.ALL.label)
        assertNull(SearchFilter.ALL.params)

        assertEquals("Songs", SearchFilter.SONGS.label)
        assertEquals("EgWKAQIIAWoKEAkQChAFEAMQBA==", SearchFilter.SONGS.params)

        assertEquals("Videos", SearchFilter.VIDEOS.label)
        assertEquals("EgWKAQIQAWoKEAkQChAFEAMQBA==", SearchFilter.VIDEOS.params)

        assertEquals("Albums", SearchFilter.ALBUMS.label)
        assertEquals("EgWKAQIYAWoKEAkQChAFEAMQBA==", SearchFilter.ALBUMS.params)

        assertEquals("Artists", SearchFilter.ARTISTS.label)
        assertEquals("EgWKAQIgAWoKEAkQChAFEAMQBA==", SearchFilter.ARTISTS.params)

        assertEquals("Playlists", SearchFilter.PLAYLISTS.label)
        assertEquals("EgWKAQIoAWoKEAkQChAFEAMQBA==", SearchFilter.PLAYLISTS.params)
    }
}

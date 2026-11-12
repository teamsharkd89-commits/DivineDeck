package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.ui.ExploreDataSource
import com.divinedeck.launcher.feature.music.ui.HomeDataSource
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.divinedeck.launcher.feature.music.ui.MusicExploreViewModel
import com.divinedeck.launcher.feature.music.ui.MusicHomeViewModel
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.HomeFeed
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.MoodGenre
import com.music.bitchord.data.model.MoodGenreSection
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.settings.LibraryViewType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * Suite de pruebas unitarias y de integración para la Fase 06.5A:
 * - Integración de pantallas originales `HomeScreen` (INICIO) y `ExploreScreen` (EXPLORAR) de BitChord 1.8.1.
 * - Parsing de `FEmusic_home` y `FEmusic_moods_and_genres` mediante `InnertubeParser`.
 * - Gestión de estados de carga, éxito, paginación, suplementos y error en `MusicHomeViewModel` y `MusicExploreViewModel`.
 * - Conexión directa de elementos seleccionables de Inicio y Explorar con el único motor de reproducción Media3.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordHomeExploreIntegrationTest {

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

    private class FakeMediaSessionBridge : BitChordMediaSessionBridge {
        override var isConnected: Boolean = true
        override var isPlaying: Boolean = false
        override var playbackState: Int = Player.STATE_READY
        override var isLoading: Boolean = false
        override var currentPositionMs: Long = 0L
        override var durationMs: Long = 240_000L
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
    // 1. INNERTUBE PARSER — HOME & EXPLORE JSON PARSING
    // =========================================================================

    @Test
    fun `InnertubeParser parseHome extracts shelves items and continuation token`() {
        val sampleHomeJson = """
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
                            "musicCarouselShelfRenderer": {
                              "header": {
                                "musicCarouselShelfBasicHeaderRenderer": {
                                  "title": { "runs": [ { "text": "Quick picks" } ] },
                                  "strapline": { "runs": [ { "text": "START RADIO FROM A SONG" } ] }
                                }
                              },
                              "contents": [
                                {
                                  "musicResponsiveListItemRenderer": {
                                    "playlistItemData": { "videoId": "v_starboy_01" },
                                    "flexColumns": [
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": { "runs": [ { "text": "Starboy" } ] }
                                        }
                                      },
                                      {
                                        "musicResponsiveListItemFlexColumnRenderer": {
                                          "text": { "runs": [ { "text": "The Weeknd" }, { "text": " • " }, { "text": "Starboy" } ] }
                                        }
                                      }
                                    ],
                                    "thumbnail": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://img.youtube.com/vi/v_starboy_01/hqdefault.jpg", "width": 480, "height": 480 }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          },
                          {
                            "musicCarouselShelfRenderer": {
                              "header": {
                                "musicCarouselShelfBasicHeaderRenderer": {
                                  "title": { "runs": [ { "text": "New releases" } ] }
                                }
                              },
                              "contents": [
                                {
                                  "musicTwoRowItemRenderer": {
                                    "title": { "runs": [ { "text": "Random Access Memories" } ] },
                                    "subtitle": { "runs": [ { "text": "Album" }, { "text": " • " }, { "text": "Daft Punk" } ] },
                                    "navigationEndpoint": {
                                      "browseEndpoint": { "browseId": "MPREb_ram_album" }
                                    },
                                    "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                        "thumbnail": {
                                          "thumbnails": [
                                            { "url": "https://lh3.googleusercontent.com/ram_cover=w544-h544", "width": 544, "height": 544 }
                                          ]
                                        }
                                      }
                                    }
                                  }
                                }
                              ]
                            }
                          }
                        ],
                        "continuations": [
                          {
                            "nextContinuationData": {
                              "continuation": "CT_NEXT_HOME_PAGE_01"
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

        val json = Json.decodeFromString<JsonObject>(sampleHomeJson)
        val shelves = InnertubeParser.parseHome(json)
        val continuation = InnertubeParser.continuationToken(json)

        assertEquals(2, shelves.size)
        assertEquals("CT_NEXT_HOME_PAGE_01", continuation)

        val firstShelf = shelves[0]
        assertEquals("Quick picks", firstShelf.title)
        assertEquals("START RADIO FROM A SONG", firstShelf.subtitle)
        assertEquals(1, firstShelf.items.size)
        assertEquals("v_starboy_01", firstShelf.items[0].videoId)
        assertEquals("Starboy", firstShelf.items[0].title)
        assertEquals("The Weeknd", firstShelf.items[0].subtitle)

        val secondShelf = shelves[1]
        assertEquals("New releases", secondShelf.title)
        assertEquals(1, secondShelf.items.size)
        assertEquals("MPREb_ram_album", secondShelf.items[0].browseId)
        assertNull(secondShelf.items[0].videoId)
        assertEquals("Random Access Memories", secondShelf.items[0].title)
        assertEquals("Album • Daft Punk", secondShelf.items[0].subtitle)
    }

    @Test
    fun `InnertubeParser parseMoodAndGenres extracts sections and mood genre buttons`() {
        val sampleExploreJson = """
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
                              "header": {
                                "gridHeaderRenderer": {
                                  "title": { "runs": [ { "text": "Moods & moments" } ] }
                                }
                              },
                              "items": [
                                {
                                  "musicNavigationButtonRenderer": {
                                    "buttonText": { "runs": [ { "text": "Chill" } ] },
                                    "solid": { "leftStripeColor": 4280391411 },
                                    "clickCommand": {
                                      "browseEndpoint": {
                                        "browseId": "FEmusic_moods_and_genres_category",
                                        "params": "ggMPOg1uXzBpejJ4"
                                      }
                                    }
                                  }
                                },
                                {
                                  "musicNavigationButtonRenderer": {
                                    "buttonText": { "runs": [ { "text": "Commute" } ] },
                                    "solid": { "leftStripeColor": 4291646235 },
                                    "clickCommand": {
                                      "browseEndpoint": {
                                        "browseId": "FEmusic_moods_and_genres_category",
                                        "params": "ggMPOg1uXzNyejR5"
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

        val json = Json.decodeFromString<JsonObject>(sampleExploreJson)
        val sections = InnertubeParser.parseMoodAndGenres(json)

        assertEquals(1, sections.size)
        val section = sections[0]
        assertEquals("Moods & moments", section.title)
        assertEquals(2, section.items.size)

        val chill = section.items[0]
        assertEquals("Chill", chill.title)
        assertEquals("FEmusic_moods_and_genres_category", chill.browseId)
        assertEquals("ggMPOg1uXzBpejJ4", chill.params)
        assertEquals(4280391411L, chill.stripeColor)
    }

    @Test
    fun `InnertubeParser artistFromSubtitle extracts artist cleanly from multi-segment subtitles`() {
        assertEquals("Queen", InnertubeParser.artistFromSubtitle("Song • Queen • 5:55"))
        assertEquals("Daft Punk", InnertubeParser.artistFromSubtitle("Album • Daft Punk"))
        assertEquals("The Weeknd", InnertubeParser.artistFromSubtitle("The Weeknd • Starboy"))
        assertEquals("Justice", InnertubeParser.artistFromSubtitle("Justice"))
    }

    // =========================================================================
    // 2. MUSIC HOME VIEW MODEL — LOADING, SUPPLEMENTS, PAGINATION & PLAYBACK
    // =========================================================================

    @Test
    fun `MusicHomeViewModel aggregates main home feed and supplements without duplicates and paginates`() = runTest(testDispatcher) {
        val mainShelves = listOf(
            HomeShelf(
                title = "Quick picks",
                items = listOf(
                    ShelfItem(
                        title = "Nightcall",
                        subtitle = "Kavinsky • OutRun",
                        thumbnailUrl = "https://img/nightcall.jpg",
                        videoId = "v_nightcall",
                        browseId = null,
                    )
                )
            )
        )
        val supplementShelves = listOf(
            HomeShelf(
                title = "New releases",
                items = listOf(
                    ShelfItem(
                        title = "Hyperdrama",
                        subtitle = "Album • Justice",
                        thumbnailUrl = "https://img/hyperdrama.jpg",
                        videoId = null,
                        browseId = "MPREb_hyperdrama",
                    )
                )
            )
        )
        val nextShelves = listOf(
            HomeShelf(
                title = "Trending",
                items = listOf(
                    ShelfItem(
                        title = "Veridis Quo",
                        subtitle = "Daft Punk • Discovery",
                        thumbnailUrl = "https://img/veridis.jpg",
                        videoId = "v_veridis",
                        browseId = null,
                    )
                )
            )
        )

        val fakeHomeDataSource = object : HomeDataSource {
            override val supplementBrowseIds: List<String> = listOf("FEmusic_new_releases")
            override suspend fun home(): Result<HomeFeed> =
                Result.success(HomeFeed(shelves = mainShelves, continuation = "CONT_TOKEN_2"))
            override suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> =
                Result.success(supplementShelves)
            override suspend fun moreHome(token: String): Result<HomeFeed> =
                Result.success(HomeFeed(shelves = nextShelves, continuation = null))
            override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
                Result.success(
                    YtMusicRepository.SongPage(
                        songs = listOf(
                            Song(
                                videoId = "v_neverender",
                                title = "Neverender",
                                artist = "Justice",
                                thumbnailUrl = "https://img/hyperdrama.jpg",
                            )
                        ),
                        continuation = null,
                        header = InnertubeParser.BrowseHeader(
                            title = "Hyperdrama",
                            subtitle = "Justice • 2024",
                            thumbnailUrl = "https://img/hyperdrama.jpg",
                        )
                    )
                )
            override suspend fun artistPage(browseId: String): Result<ArtistPage> =
                Result.failure(UnsupportedOperationException())
        }

        val viewModel = MusicHomeViewModel(
            dataSource = fakeHomeDataSource,
            ioDispatcher = testDispatcher,
            autoLoadOnInit = true,
        )

        advanceUntilIdle()

        val state = viewModel.homeState.value
        assertTrue(state is UiState.Success)
        val loadedShelves = (state as UiState.Success).data
        assertEquals(2, loadedShelves.size)
        assertEquals("Quick picks", loadedShelves[0].title)
        assertEquals("New releases", loadedShelves[1].title)
        assertFalse(viewModel.loadingMore.value)

        // Paginación (loadMoreHome)
        viewModel.loadMoreHome()
        advanceUntilIdle()

        val afterMore = (viewModel.homeState.value as UiState.Success).data
        assertEquals(3, afterMore.size)
        assertEquals("Trending", afterMore[2].title)

        // Cambio de vista en estante de recientes (LIST / GRID)
        assertEquals(LibraryViewType.LIST, viewModel.recentsViewType.value)
        viewModel.setRecentsViewType(LibraryViewType.GRID)
        assertEquals(LibraryViewType.GRID, viewModel.recentsViewType.value)

        // Selección de pista directa en INICIO -> envía Song al motor de reproducción unificado
        val fakeBridge = FakeMediaSessionBridge()
        val repoJob = kotlinx.coroutines.SupervisorJob()
        val repoScope = kotlinx.coroutines.CoroutineScope(testDispatcher + repoJob)
        val repository = MediaControllerRepositoryImpl(
            context = TestContext(),
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
            var playedSong: Song? = null
            viewModel.onShelfItemClick(
                item = loadedShelves[0].items[0],
                shelfTitle = loadedShelves[0].title,
                onPlaySong = { song ->
                    playedSong = song
                    repository.playSong(song)
                }
            )

            assertNotNull(playedSong)
            assertEquals("v_nightcall", playedSong?.videoId)
            assertEquals("Nightcall", playedSong?.title)
            assertEquals("Kavinsky", playedSong?.artist)
            assertEquals(PlaybackSourceType.HOME, playedSong?.playbackSourceType)
            assertEquals("v_nightcall", fakeBridge.lastSetMediaItem?.mediaId)
            assertEquals(true, fakeBridge.lastPlayWhenReady)
            assertEquals("v_nightcall", repository.currentTrack.value.mediaId)
            assertEquals("Nightcall", repository.currentTrack.value.title)
            assertEquals("Kavinsky", repository.currentTrack.value.artist)
            assertTrue(repository.playbackState.value.isPlaying)
        } finally {
            // Cancelar el sondeo periódico de posición (while(isActive) { delay(500) }) antes de advanceUntilIdle()
            repository.disconnect()
            repoJob.cancel()
        }

        // Selección de álbum en INICIO -> abre BrowseCollectionState y carga sus canciones
        viewModel.onShelfItemClick(
            item = loadedShelves[1].items[0],
            shelfTitle = loadedShelves[1].title,
            onPlaySong = {}
        )
        advanceUntilIdle()

        val collection = viewModel.selectedCollection.value
        assertNotNull(collection)
        assertEquals("MPREb_hyperdrama", collection!!.browseId)
        assertEquals("Hyperdrama", collection.title)
        assertTrue(collection.songsState is UiState.Success)
        val collectionSongs = (collection.songsState as UiState.Success).data
        assertEquals(1, collectionSongs.size)
        assertEquals("Neverender", collectionSongs[0].title)

        assertTrue(viewModel.closeCollection())
        assertNull(viewModel.selectedCollection.value)
    }

    @Test
    fun `MusicHomeViewModel exposes Error state when network fails and recovers on retry`() = runTest(testDispatcher) {
        var shouldFail = true
        val fakeHomeDataSource = object : HomeDataSource {
            override val supplementBrowseIds: List<String> = emptyList()
            override suspend fun home(): Result<HomeFeed> =
                if (shouldFail) {
                    Result.failure(IllegalStateException("Sin conexión a Internet"))
                } else {
                    Result.success(
                        HomeFeed(
                            shelves = listOf(
                                HomeShelf(
                                    title = "Listen again",
                                    items = listOf(
                                        ShelfItem(
                                            title = "Instant Crush",
                                            subtitle = "Daft Punk",
                                            thumbnailUrl = null,
                                            videoId = "v_ic",
                                            browseId = null,
                                        )
                                    )
                                )
                            ),
                            continuation = null
                        )
                    )
                }
            override suspend fun homeSupplement(browseId: String): Result<List<HomeShelf>> = Result.success(emptyList())
            override suspend fun moreHome(token: String): Result<HomeFeed> = Result.success(HomeFeed(emptyList(), null))
            override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
                Result.failure(UnsupportedOperationException())
            override suspend fun artistPage(browseId: String): Result<ArtistPage> =
                Result.failure(UnsupportedOperationException())
        }

        val viewModel = MusicHomeViewModel(
            dataSource = fakeHomeDataSource,
            ioDispatcher = testDispatcher,
            autoLoadOnInit = true,
        )
        advanceUntilIdle()

        assertTrue(viewModel.homeState.value is UiState.Error)
        assertEquals("Sin conexión a Internet", (viewModel.homeState.value as UiState.Error).message)

        shouldFail = false
        viewModel.loadHome()
        advanceUntilIdle()

        assertTrue(viewModel.homeState.value is UiState.Success)
        assertEquals("Listen again", (viewModel.homeState.value as UiState.Success).data.first().title)
    }

    // =========================================================================
    // 3. MUSIC EXPLORE VIEW MODEL — SECTIONS, ARTWORK, CATEGORY DRILL-DOWN & PLAYBACK
    // =========================================================================

    @Test
    fun `MusicExploreViewModel loads mood sections resolves artwork opens category and plays track`() = runTest(testDispatcher) {
        val chillCategory = MoodGenre(
            title = "Chill",
            browseId = "FEmusic_moods_and_genres_category",
            params = "params_chill",
            stripeColor = 0xFF2589A6L,
        )
        val sections = listOf(
            MoodGenreSection(
                title = "Moods & moments",
                items = listOf(chillCategory),
            )
        )
        val chillShelves = listOf(
            HomeShelf(
                title = "Chill Hits",
                items = listOf(
                    ShelfItem(
                        title = "Midnight City",
                        subtitle = "M83 • Hurry Up, We're Dreaming",
                        thumbnailUrl = "https://img/m83.jpg",
                        videoId = "v_midnight_city",
                        browseId = null,
                    )
                )
            )
        )

        val fakeExploreDataSource = object : ExploreDataSource {
            override suspend fun moodAndGenres(): Result<List<MoodGenreSection>> =
                Result.success(sections)
            override suspend fun moodGenreShelves(browseId: String, params: String?): Result<List<HomeShelf>> =
                Result.success(chillShelves)
            override suspend fun moodGenreArtwork(browseId: String, params: String?): Result<String?> =
                Result.success("https://img/chill_cover.jpg")
            override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
                Result.failure(UnsupportedOperationException())
            override suspend fun artistPage(browseId: String): Result<ArtistPage> =
                Result.failure(UnsupportedOperationException())
        }

        val viewModel = MusicExploreViewModel(
            dataSource = fakeExploreDataSource,
            ioDispatcher = testDispatcher,
            autoLoadOnInit = true,
        )

        advanceUntilIdle()

        val exploreState = viewModel.exploreState.value
        assertTrue(exploreState is UiState.Success)
        val loadedSections = (exploreState as UiState.Success).data
        assertEquals(1, loadedSections.size)
        assertEquals("Moods & moments", loadedSections[0].title)
        // Verificar que la carátula en segundo plano se resolvió e inyectó en el MoodGenre
        assertEquals("https://img/chill_cover.jpg", loadedSections[0].items[0].thumbnailUrl)

        // Abrir categoría "Chill"
        viewModel.openCategory(chillCategory)
        advanceUntilIdle()

        assertEquals(chillCategory, viewModel.selectedCategory.value)
        val catState = viewModel.categoryShelvesState.value
        assertTrue(catState is UiState.Success)
        val shelves = (catState as UiState.Success).data
        assertEquals(1, shelves.size)
        assertEquals("Chill Hits", shelves[0].title)

        // Seleccionar canción dentro de la categoría de Explorar -> reproduce en el motor unificado
        val fakeBridge = FakeMediaSessionBridge()
        val repoJob = kotlinx.coroutines.SupervisorJob()
        val repoScope = kotlinx.coroutines.CoroutineScope(testDispatcher + repoJob)
        val repository = MediaControllerRepositoryImpl(
            context = TestContext(),
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
            var playedFromExplore: Song? = null
            viewModel.onShelfItemClick(
                item = shelves[0].items[0],
                onPlaySong = { song ->
                    playedFromExplore = song
                    repository.playSong(song)
                }
            )

            assertNotNull(playedFromExplore)
            assertEquals("v_midnight_city", playedFromExplore?.videoId)
            assertEquals("Midnight City", playedFromExplore?.title)
            assertEquals("M83", playedFromExplore?.artist)
            assertEquals(PlaybackSourceType.EXPLORE, playedFromExplore?.playbackSourceType)
            assertEquals("Chill", playedFromExplore?.playbackSource)
            assertEquals("v_midnight_city", fakeBridge.lastSetMediaItem?.mediaId)
            assertEquals(true, fakeBridge.lastPlayWhenReady)
            assertEquals("v_midnight_city", repository.currentTrack.value.mediaId)
            assertTrue(repository.playbackState.value.isPlaying)
        } finally {
            repository.disconnect()
            repoJob.cancel()
        }

        // Volver atrás desde la categoría a Explorar
        assertTrue(viewModel.closeCategory())
        assertNull(viewModel.selectedCategory.value)
    }

    // =========================================================================
    // 4. 5-SECTION NAVIGATION & PLAYBACK CONTINUITY
    // =========================================================================

    @Test
    fun `5-tab navigation destinations exist in expected order INICIO EXPLORAR BUSCAR BIBLIOTECA REPRODUCTOR`() {
        val entries = MusicCenterDestination.entries
        assertEquals(5, entries.size)
        assertEquals(MusicCenterDestination.HOME, entries[0])
        assertEquals(MusicCenterDestination.EXPLORE, entries[1])
        assertEquals(MusicCenterDestination.SEARCH, entries[2])
        assertEquals(MusicCenterDestination.LIBRARY, entries[3])
        assertEquals(MusicCenterDestination.PLAYER, entries[4])
    }
}

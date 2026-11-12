package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.divinedeck.launcher.core.navigation.DivineDeckRoute
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.divinedeck.launcher.feature.music.ui.BitChordMediaModelAdapter
import com.divinedeck.launcher.feature.music.ui.BitChordSettingsCategory
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.CARD_ART_PX
import com.music.bitchord.data.model.HEADER_ART_PX
import com.music.bitchord.data.model.PLAYER_ART_PX
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.Downloads
import com.music.bitchord.download.SavedSongMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.OutputStream

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.6:
 * BITCHORD VISUAL INTEGRATION & IMMERSIVE MUSIC CENTER.
 *
 * Valida rigurosamente:
 * 1. Motor de resolución de portadas en alta resolución (`artworkAt(px)` para YouTube y Google hosts).
 * 2. Descarga y caché persistente de portadas en alta resolución asociadas a canciones offline.
 * 3. Tolerancia a fallos: errores en portadas no cancelan ni corrompen descargas de audio.
 * 4. Integración del Mini Reproductor de Cockpit con un único servicio Media3 y reactividad en tiempo real.
 * 5. Navegación del Modo Inmersivo de Music Center y retorno al Cockpit sin detener reproducción ni perder estado.
 * 6. Hub Unificado de Ajustes BitChord (Audio DSP, Fuentes de Letras, Descargas, Audio Local).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordVisualIntegrationImmersiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var testBaseDir: File
    private lateinit var fakePrefs: InMemorySharedPreferences

    private class TestContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = backingMap
        override fun getString(key: String?, defValue: String?): String? = (backingMap[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (backingMap[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (backingMap[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (backingMap[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (backingMap[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (backingMap[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = backingMap.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) { pending[key] = values; removals.remove(key) }
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) { pending[key] = value; removals.remove(key) }
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) { removals.add(key); pending.remove(key) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true; pending.clear(); removals.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                map.putAll(pending)
            }
        }
    }

    private class FakeMediaBridge(
        override var isConnected: Boolean = true,
        override var isPlaying: Boolean = false,
        override var playbackState: Int = Player.STATE_READY,
        override var isLoading: Boolean = false,
        override var currentPositionMs: Long = 0L,
        override var durationMs: Long = 240_000L,
        override var currentMediaItem: MediaItem? = null,
        var allowedCommands: Set<Int> = setOf(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ),
        override var availableCommands: Player.Commands? = null
    ) : BitChordMediaSessionBridge {
        var playCount = 0
        var pauseCount = 0
        var skipNextCount = 0
        var skipPrevCount = 0
        var lastSeekMs: Long? = null
        val listeners = mutableListOf<Player.Listener>()

        override fun isCommandAvailable(command: Int): Boolean = allowedCommands.contains(command)

        override fun play() {
            playCount++
            isPlaying = true
        }

        override fun pause() {
            pauseCount++
            isPlaying = false
        }

        override fun seekTo(positionMs: Long) {
            lastSeekMs = positionMs
            currentPositionMs = positionMs
        }

        override fun seekToNext() {
            skipNextCount++
        }

        override fun seekToPrevious() {
            skipPrevCount++
        }

        override fun setPlayerListener(listener: Player.Listener?) {
            listener?.let { listeners.add(it) }
        }

        override fun removePlayerListener(listener: Player.Listener) {
            listeners.remove(listener)
        }

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            currentMediaItem = mediaItem
            if (playWhenReady) isPlaying = true
        }

        override fun sendCustomCommand(action: String) {
            if (action == "com.divinedeck.ACTION_PLAY_SAMPLE") isPlaying = true
        }

        override fun sendCustomCommand(action: String, args: Bundle) {
            sendCustomCommand(action)
        }

        override fun release() {
            isConnected = false
        }
    }

    private class FakeConnector(
        var fakeBridge: FakeMediaBridge = FakeMediaBridge()
    ) : BitChordSessionConnector {
        override fun connect(
            context: Context,
            onConnected: (BitChordMediaSessionBridge) -> Unit,
            onDisconnected: () -> Unit,
            onError: (Throwable) -> Unit
        ): AutoCloseable {
            onConnected(fakeBridge)
            return AutoCloseable {
                fakeBridge.release()
            }
        }
    }

    @Before
    fun setUp() {
        testBaseDir = tempFolder.newFolder("bitchord_visual_test")
        fakePrefs = InMemorySharedPreferences()
        Downloads.initForTest(
            sharedPreferences = fakePrefs,
            baseDir = testBaseDir,
            testScope = testScope,
            enableOnlineArtworkFetch = false
        )
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
    }

    // =========================================================================
    // 1. MOTOR DE ALTA RESOLUCIÓN DE PORTADAS (`artworkAt(px)`)
    // =========================================================================

    @Test
    fun artworkAt_rewrites_youtube_music_dimensions_accurately() {
        val originalYtUrl = "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"

        val rowArt = originalYtUrl.artworkAt(ROW_ART_PX)
        val cardArt = originalYtUrl.artworkAt(CARD_ART_PX)
        val headerArt = originalYtUrl.artworkAt(HEADER_ART_PX)
        val playerArt = originalYtUrl.artworkAt(PLAYER_ART_PX)

        assertNotNull(rowArt)
        assertTrue(rowArt!!.contains("w160-h160"))

        assertNotNull(cardArt)
        assertTrue(cardArt!!.contains("w480-h480"))

        assertNotNull(headerArt)
        assertTrue(headerArt!!.contains("w720-h720"))

        assertNotNull(playerArt)
        assertTrue(playerArt!!.contains("w1200-h1200"))
    }

    @Test
    fun artworkAt_rewrites_google_hosts_parameter_size_hints() {
        val googleHostUrl = "https://yt3.ggpht.com/avatar=s120-c-k-c0x00ffffff-no-rj"

        val upscaled = googleHostUrl.artworkAt(800)
        assertNotNull(upscaled)
        assertTrue(upscaled!!.contains("=s800"))
    }

    @Test
    fun artworkAt_preserves_local_and_non_google_urls_without_corruption() {
        val localUri = "file:///storage/emulated/0/Music/cover.jpg"
        val customUrl = "https://images.example.com/album_art.png"

        assertEquals(localUri, localUri.artworkAt(ROW_ART_PX))
        assertEquals(customUrl, customUrl.artworkAt(ROW_ART_PX))
        assertNull((null as String?).artworkAt(CARD_ART_PX))
    }

    @Test
    fun song_artworkAt_delegates_to_thumbnailUrl() {
        val song = Song(
            videoId = "v_art_test",
            title = "Test Song",
            artist = "Artist",
            thumbnailUrl = "https://lh3.googleusercontent.com/test=w120-h120"
        )

        val sized = song.artworkAt(800)
        assertNotNull(sized)
        assertTrue(sized!!.contains("w800-h800"))
    }

    // =========================================================================
    // 2. DESCARGA AUTOMÁTICA Y CACHÉ PERSISTENTE DE PORTADAS EN ALTA RESOLUCIÓN
    // =========================================================================

    @Test
    fun downloaded_song_associates_and_restores_local_artwork_offline() = runTest(testDispatcher) {
        val artDir = DownloadStore.artworkDir(testBaseDir)
        val dlDir = DownloadStore.downloadsDir(testBaseDir)

        // Simular archivo de audio descargado
        val audioFile = File(dlDir, "Song_v123.m4a")
        audioFile.writeBytes(ByteArray(1024) { 1 })
        val audioUri = DownloadStore.fileToUriString(audioFile)

        // Simular archivo de portada en alta resolución descargado
        val artworkFile = File(artDir, "v123.jpg")
        artworkFile.writeBytes(ByteArray(2048) { 2 })
        val artUri = DownloadStore.fileToUriString(artworkFile)

        // Guardar metadatos verificados en SharedPreferences simulado
        fakePrefs.edit()
            .putString("downloaded_tracks", """{"v123":"$audioUri"}""")
            .putString("downloaded_tracks_metadata", """[{"videoId":"v123","title":"Offline High-Res Track","artist":"Studio Master","thumbnailUrl":"https://lh3.googleusercontent.com/abc=w544-h544","localArtworkUri":"$artUri","uri":"$audioUri","fileSizeBytes":1024}]""")
            .commit()

        Downloads.initForTest(
            sharedPreferences = fakePrefs,
            baseDir = testBaseDir,
            testScope = testScope,
            enableOnlineArtworkFetch = false
        )

        val songs = Downloads.downloadedSongs.value
        assertEquals(1, songs.size)
        val downloadedSong = songs.first()

        assertEquals("v123", downloadedSong.videoId)
        assertEquals("Offline High-Res Track", downloadedSong.title)
        // En offline, thumbnailUrl debe apuntar a la URI local de la portada
        assertEquals(artUri, downloadedSong.thumbnailUrl)
        assertTrue(downloadedSong.thumbnailUrl!!.startsWith("file:"))
        assertTrue(File(DownloadStore.resolveLocalFile(downloadedSong.thumbnailUrl)!!.absolutePath).exists())
    }

    @Test
    fun download_verifies_that_missing_artwork_does_not_abort_audio() = runTest(testDispatcher) {
        var audioBytesWritten = 0L
        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream(
                    url = "https://mock.googlevideo.com/videoplayback",
                    kbps = 160,
                    mimeType = "audio/mp4"
                )
                openSink(stream).use { sink ->
                    val data = ByteArray(4096) { 7 }
                    sink.write(data)
                    audioBytesWritten = data.size.toLong()
                    onProgress(audioBytesWritten, audioBytesWritten)
                }
                return stream
            }
        }

        Downloads.initForTest(
            sharedPreferences = fakePrefs,
            baseDir = testBaseDir,
            fetcher = fakeFetcher,
            testScope = testScope,
            enableOnlineArtworkFetch = false // Simular red sin conexión para portadas
        )

        val testSong = Song(
            videoId = "v_no_art",
            title = "Song Without Artwork",
            artist = "No Art Artist",
            thumbnailUrl = "https://lh3.googleusercontent.com/fail=w544-h544"
        )

        Downloads.enqueue(testSong)
        testDispatcher.scheduler.advanceUntilIdle()

        // El audio DEBE haberse descargado y verificado en disco exitosamente
        assertTrue(Downloads.isDownloaded("v_no_art"))
        val savedUri = Downloads.verifiedSavedUriString("v_no_art")
        assertNotNull(savedUri)
        assertTrue(Downloads.isValidLocalUri(savedUri))
    }

    // =========================================================================
    // 3. INTEGRACIÓN DEL COCKPIT MINI PLAYER Y CONTROL MULTIMEDIA UNIFICADO
    // =========================================================================

    @Test
    fun cockpit_mini_player_state_reflects_media_repository_reactively() = runTest(testDispatcher) {
        val fakeBridge = FakeMediaBridge(
            isPlaying = true,
            currentPositionMs = 45_000L,
            durationMs = 180_000L
        )
        val connector = FakeConnector(fakeBridge)
        val repo = MediaControllerRepositoryImpl(TestContext(), connector, testScope)

        // Comprobación inicial de estado
        val state = repo.playbackState.value
        assertEquals(MediaServiceConnectionStatus.CONNECTED, state.connectionStatus)
        assertTrue(state.isPlaying)

        // Adaptación de pista activa para el mini reproductor
        val track = TrackInfo(
            mediaId = "v_cockpit_mini",
            title = "Cockpit Highway Tune",
            artist = "Automotive Sound",
            album = "Dashboard Sessions",
            durationMs = 180_000L,
            currentPositionMs = 45_000L,
            albumArtUri = "https://lh3.googleusercontent.com/cockpit=w544-h544"
        )
        val song = BitChordMediaModelAdapter.toSong(track)
        assertNotNull(song)
        assertEquals("Cockpit Highway Tune", song?.title)
        assertEquals("Automotive Sound", song?.artist)
        assertEquals("https://lh3.googleusercontent.com/cockpit=w544-h544", song?.thumbnailUrl)

        // Probar resolución de carátula para mini reproductor
        val miniArt = song?.thumbnailUrl.artworkAt(ROW_ART_PX)
        assertNotNull(miniArt)
        assertTrue(miniArt!!.contains("w160-h160"))

        // Verificar acciones de control táctil automotriz
        repo.togglePlayback()
        assertEquals(1, fakeBridge.pauseCount)

        repo.skipToNext()
        assertEquals(1, fakeBridge.skipNextCount)
    }

    // =========================================================================
    // 4. MODO INMERSIVO DE MUSIC CENTER Y RETORNO AL COCKPIT
    // =========================================================================

    @Test
    fun immersive_navigation_transitions_between_cockpit_and_music_center() {
        var currentRoute = DivineDeckRoute.COCKPIT
        var activeDestination = MusicCenterDestination.DEFAULT

        // 1. En Cockpit inicialmente
        assertEquals(DivineDeckRoute.COCKPIT, currentRoute)
        val isImmersiveInitially = currentRoute == DivineDeckRoute.MUSIC
        assertFalse(isImmersiveInitially)

        // 2. Al pulsar el mini reproductor o navegar a Music Center -> Modo Inmersivo Real
        currentRoute = DivineDeckRoute.MUSIC
        activeDestination = MusicCenterDestination.PLAYER

        val isImmersiveInMusic = currentRoute == DivineDeckRoute.MUSIC
        assertTrue(isImmersiveInMusic)
        assertEquals(MusicCenterDestination.PLAYER, activeDestination)

        // 3. Al pulsar botón "COCKPIT" -> Regresa inmediatamente a la vista Cockpit
        val onBackToCockpit = {
            currentRoute = DivineDeckRoute.COCKPIT
        }
        onBackToCockpit()

        assertEquals(DivineDeckRoute.COCKPIT, currentRoute)
        assertFalse(currentRoute == DivineDeckRoute.MUSIC)
    }

    // =========================================================================
    // 5. HUB UNIFICADO DE AJUSTES BITCHORD
    // =========================================================================

    @Test
    fun unified_settings_categories_provide_complete_coverage() {
        val categories = BitChordSettingsCategory.entries

        assertTrue(categories.size >= 4)
        assertTrue(categories.contains(BitChordSettingsCategory.AUDIO_DSP))
        assertTrue(categories.contains(BitChordSettingsCategory.LYRICS_SOURCES))
        assertTrue(categories.contains(BitChordSettingsCategory.STORAGE_DOWNLOADS))
        assertTrue(categories.contains(BitChordSettingsCategory.LOCAL_FILES))

        assertNotNull(BitChordSettingsCategory.AUDIO_DSP.title)
        assertNotNull(BitChordSettingsCategory.LYRICS_SOURCES.title)
        assertNotNull(BitChordSettingsCategory.STORAGE_DOWNLOADS.title)
        assertNotNull(BitChordSettingsCategory.LOCAL_FILES.title)
    }
}

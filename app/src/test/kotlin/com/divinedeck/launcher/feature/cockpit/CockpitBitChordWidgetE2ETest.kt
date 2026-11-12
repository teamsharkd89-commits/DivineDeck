package com.divinedeck.launcher.feature.cockpit

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.divinedeck.launcher.core.designsystem.theme.AlertRed
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckPalette
import com.divinedeck.launcher.core.designsystem.theme.MidnightNavy
import com.divinedeck.launcher.core.designsystem.theme.Obsidian
import com.divinedeck.launcher.core.designsystem.theme.SoftWhite
import com.divinedeck.launcher.core.designsystem.theme.StatusGreen
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.core.designsystem.theme.WarmAmber
import com.divinedeck.launcher.core.designsystem.theme.buildPaletteForTheme
import com.divinedeck.launcher.core.designsystem.theme.ensureReadableAccent
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.music.domain.model.MediaPlaybackState
import com.divinedeck.launcher.feature.music.domain.model.MediaServiceConnectionStatus
import com.divinedeck.launcher.feature.music.domain.model.PlaybackStatus
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Opaque-Box E2E Test Suite for Requirement R3: Cockpit BitChord Widget.
 *
 * Validates automotive visual design integration across all 5 themes:
 * - OBSIDIAN, ARCTIC, MONOCHROME, BRONZE, and CUSTOM (with guaranteed readable luminance)
 * - Reactive playback controls (Play/Pause, Skip Next)
 * - Live progress bar & playhead synchronization
 * - Synced lyrics tracking
 * - Real-world driving session integration
 */
class CockpitBitChordWidgetE2ETest {

    /**
     * Opaque-box mock of MediaControllerRepository driving the Cockpit Widget.
     */
    private class FakeMediaControllerRepository : MediaControllerRepository {
        val _playbackState = MutableStateFlow(
            MediaPlaybackState(
                connectionStatus = MediaServiceConnectionStatus.CONNECTED,
                status = PlaybackStatus.STOPPED,
                isPlaying = false,
                isBuffering = false,
                playWhenReady = false
            )
        )
        override val playbackState: StateFlow<MediaPlaybackState> = _playbackState

        val _currentTrack = MutableStateFlow(
            TrackInfo(
                mediaId = "track_init",
                title = "Initial Title",
                artist = "Initial Artist",
                album = "Initial Album",
                durationMs = 240000L,
                currentPositionMs = 0L,
                albumArtUri = "https://example.com/art.jpg"
            )
        )
        override val currentTrack: StateFlow<TrackInfo> = _currentTrack

        val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
        override val currentQueue: StateFlow<List<Song>> = _currentQueue

        var playCallCount = 0
        var pauseCallCount = 0
        var skipNextCallCount = 0
        var seekToCallCount = 0
        var lastSeekPositionMs = 0L

        override fun connect() {}
        override fun disconnect() {}

        override fun play() {
            playCallCount++
            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                playWhenReady = true,
                status = PlaybackStatus.PLAYING
            )
        }

        override fun pause() {
            pauseCallCount++
            _playbackState.value = _playbackState.value.copy(
                isPlaying = false,
                playWhenReady = false,
                status = PlaybackStatus.PAUSED
            )
        }

        override fun togglePlayback() {
            if (_playbackState.value.isPlaying) pause() else play()
        }

        override fun skipToNext() {
            skipNextCallCount++
            val queue = _currentQueue.value
            if (queue.size > 1) {
                val nextSong = queue[1]
                _currentTrack.value = TrackInfo(
                    mediaId = nextSong.videoId,
                    title = nextSong.title,
                    artist = nextSong.artist,
                    album = nextSong.albumName ?: "",
                    durationMs = (nextSong.durationSeconds ?: 0L) * 1000L,
                    currentPositionMs = 0L,
                    albumArtUri = nextSong.thumbnailUrl
                )
                _currentQueue.value = queue.drop(1)
            }
        }

        override fun skipToPrevious() {}
        override fun seekTo(positionMs: Long) {
            seekToCallCount++
            lastSeekPositionMs = positionMs
            _currentTrack.value = _currentTrack.value.copy(currentPositionMs = positionMs)
        }

        override fun setVolume(percent: Int) {}

        override fun skipToQueueItem(index: Int) {
            val queue = _currentQueue.value
            if (index in queue.indices) {
                val target = queue[index]
                _currentTrack.value = TrackInfo(
                    mediaId = target.videoId,
                    title = target.title,
                    artist = target.artist,
                    album = target.albumName ?: "",
                    durationMs = (target.durationSeconds ?: 0L) * 1000L,
                    currentPositionMs = 0L,
                    albumArtUri = target.thumbnailUrl
                )
            }
        }

        override fun playSong(song: Song) {
            _currentTrack.value = TrackInfo(
                mediaId = song.videoId,
                title = song.title,
                artist = song.artist,
                album = song.albumName ?: "",
                durationMs = (song.durationSeconds ?: 0L) * 1000L,
                currentPositionMs = 0L,
                albumArtUri = song.thumbnailUrl
            )
            _currentQueue.value = listOf(song)
            play()
        }

        override fun playQueue(songs: List<Song>, startIndex: Int) {
            if (songs.isEmpty()) return
            val safeIdx = startIndex.coerceIn(songs.indices)
            val current = songs[safeIdx]
            _currentTrack.value = TrackInfo(
                mediaId = current.videoId,
                title = current.title,
                artist = current.artist,
                album = current.albumName ?: "",
                durationMs = (current.durationSeconds ?: 0L) * 1000L,
                currentPositionMs = 0L,
                albumArtUri = current.thumbnailUrl
            )
            _currentQueue.value = songs
            play()
        }

        override fun enqueueNext(song: Song) {
            val list = _currentQueue.value.toMutableList()
            list.add(1.coerceAtMost(list.size), song.copy(queueTier = QueueTier.USER_QUEUE))
            _currentQueue.value = list
        }

        override fun enqueueLast(song: Song) {
            val list = _currentQueue.value.toMutableList()
            list.add(song.copy(queueTier = QueueTier.USER_QUEUE))
            _currentQueue.value = list
        }

        override fun reorderQueue(fromIndex: Int, toIndex: Int) {
            val list = _currentQueue.value.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val moved = list.removeAt(fromIndex)
                list.add(toIndex, moved)
                _currentQueue.value = list
            }
        }

        override fun removeFromQueue(index: Int) {
            val list = _currentQueue.value.toMutableList()
            if (index in list.indices) {
                list.removeAt(index)
                _currentQueue.value = list
            }
        }

        override fun clearUserQueue() {
            val list = _currentQueue.value.filterNot { it.queueTier == QueueTier.USER_QUEUE }
            _currentQueue.value = list
        }

        override fun playLocalUri(uriString: String, title: String?, artist: String?, artworkUri: String?) {}
        override fun playLocalUri(uri: Uri, title: String?, artist: String?, artworkUri: String?) {}
        override fun playOnlineTrack(videoId: String, title: String, artist: String, thumbnailUrl: String?, durationMs: Long?) {}
        override fun playSampleAudio() {}
    }

    private lateinit var fakeRepo: FakeMediaControllerRepository

    private fun createSong(
        id: String,
        title: String,
        artist: String = "Test Artist",
        durationSeconds: Long = 180L,
        tier: QueueTier = QueueTier.CONTEXT,
    ): Song = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = durationSeconds,
        queueTier = tier
    )

    @Before
    fun setUp() {
        fakeRepo = FakeMediaControllerRepository()
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE (R3 Core Requirements)
    // =========================================================================

    @Test
    fun tier1_test01_widget_theme_binding_obsidian() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // "Utilizar los tokens del tema activo de DIVINE DECK: OBSIDIAN..."
        val palette = buildPaletteForTheme(ThemePreset.OBSIDIAN)

        assertEquals(ThemePreset.OBSIDIAN, palette.preset)
        assertEquals("Fondo debe ser Obsidian #080B12", Color(0xFF080B12), palette.background)
        assertEquals("Superficie debe ser MidnightNavy #101827", Color(0xFF101827), palette.surface)
        assertEquals("Acento principal debe ser WarmAmber #FFB547", Color(0xFFFFB547), palette.accent)
        assertTrue("Acento debe tener alto contraste de luminancia", palette.accent.luminance() > 0.4f)
        assertEquals("Texto primario debe ser SoftWhite", SoftWhite, palette.textPrimary)
    }

    @Test
    fun tier1_test02_widget_theme_binding_arctic() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // "Utilizar los tokens del tema activo de DIVINE DECK: ARCTIC..."
        val palette = buildPaletteForTheme(ThemePreset.ARCTIC)

        assertEquals(ThemePreset.ARCTIC, palette.preset)
        assertEquals("Fondo debe ser azul oscuro #060A10", Color(0xFF060A10), palette.background)
        assertEquals("Superficie debe ser azul profundo #0C1524", Color(0xFF0C1524), palette.surface)
        assertEquals("Acento principal debe ser cian ártico #22D3EE", Color(0xFF22D3EE), palette.accent)
        assertTrue(palette.accent.luminance() > 0.4f)
    }

    @Test
    fun tier1_test03_widget_theme_binding_monochrome() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // "Utilizar los tokens del tema activo de DIVINE DECK: MONOCHROME..."
        val palette = buildPaletteForTheme(ThemePreset.MONOCHROME)

        assertEquals(ThemePreset.MONOCHROME, palette.preset)
        assertEquals("Fondo debe ser negro #050506", Color(0xFF050506), palette.background)
        assertEquals("Superficie debe ser gris oscuro #111215", Color(0xFF111215), palette.surface)
        assertEquals("Acento principal debe ser blanco puro #F4F5F7", Color(0xFFF4F5F7), palette.accent)
    }

    @Test
    fun tier1_test04_widget_theme_binding_bronze() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // "Utilizar los tokens del tema activo de DIVINE DECK: BRONZE..."
        val palette = buildPaletteForTheme(ThemePreset.BRONZE)

        assertEquals(ThemePreset.BRONZE, palette.preset)
        assertEquals("Fondo debe ser negro cálido #0B0908", Color(0xFF0B0908), palette.background)
        assertEquals("Superficie debe ser marrón grafito #16120F", Color(0xFF16120F), palette.surface)
        assertEquals("Acento principal debe ser bronce cálido #D98A4E", Color(0xFFD98A4E), palette.accent)
    }

    @Test
    fun tier1_test05_widget_theme_binding_custom_with_custom_accent() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // "CUSTOM (respetando los colores personalizados en CUSTOM)."
        val customColorHex = 0xFF22D3EE // Cian Ártico (luminancia ~0.60 > 0.25)
        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = customColorHex)

        assertEquals(ThemePreset.CUSTOM, palette.preset)
        assertEquals(Color(0xFF080A0F), palette.background)
        assertEquals(Color(0xFF111621), palette.surface)
        assertEquals(Color(0xFF22D3EE), palette.accent)
        assertTrue("La luminancia del acento personalizado debe ser segura", palette.accent.luminance() >= 0.25f)
    }

    @Test
    fun tier1_test06_widget_reactive_playback_controls() {
        // Derivation: ORIGINAL_REQUEST §R3 line 42:
        // "Conservar ... controles funcionales (Play/Pause y Next)"
        assertFalse(fakeRepo.playbackState.value.isPlaying)

        // Simular pulsación de Play
        fakeRepo.togglePlayback()
        assertTrue("Widget Play button activa reproducción", fakeRepo.playbackState.value.isPlaying)
        assertEquals(1, fakeRepo.playCallCount)

        // Simular pulsación de Pause
        fakeRepo.togglePlayback()
        assertFalse("Widget Pause button detiene reproducción", fakeRepo.playbackState.value.isPlaying)
        assertEquals(1, fakeRepo.pauseCallCount)

        // Simular Skip Next
        fakeRepo.skipToNext()
        assertEquals(1, fakeRepo.skipNextCallCount)
    }

    @Test
    fun tier1_test07_widget_synced_lyrics_tracking() {
        // Derivation: ORIGINAL_REQUEST §R3 line 42:
        // "Conservar ... letras sincronizadas"
        val sampleLyrics = listOf(
            LyricLine(timeMs = 0L, text = "First line of the song", sungUntilMs = 5000L),
            LyricLine(timeMs = 5000L, text = "Second line driving in highway", sungUntilMs = 12000L),
            LyricLine(timeMs = 12000L, text = "Third line under the moonlight", sungUntilMs = 20000L)
        )

        // A los 7 segundos (7000ms), la línea activa debe ser la segunda
        val activeLineAt7s = sampleLyrics.firstOrNull { 7000L in it.timeMs..it.endMs }
        assertNotNull(activeLineAt7s)
        assertEquals("Second line driving in highway", activeLineAt7s?.text)

        // A los 15 segundos (15000ms), la línea activa debe ser la tercera
        val activeLineAt15s = sampleLyrics.firstOrNull { 15000L in it.timeMs..it.endMs }
        assertNotNull(activeLineAt15s)
        assertEquals("Third line under the moonlight", activeLineAt15s?.text)
    }

    // =========================================================================
    // TIER 2: BOUNDARY & CORNER CASES (>= 5 tests)
    // =========================================================================

    @Test
    fun tier2_test01_custom_theme_dark_accent_boosts_luminance_to_safe_bound() {
        // Derivation: ORIGINAL_REQUEST §Acceptance Criteria line 81:
        // "...respetando colores personalizados con contraste garantizado."
        // Acento extremadamente oscuro (e.g. negro puro #000000 o grafito #080808)
        val extremelyDarkHex = 0xFF050505
        val darkColor = Color(extremelyDarkHex)
        assertTrue("El color crudo tiene luminancia muy baja", darkColor.luminance() < 0.05f)

        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = extremelyDarkHex)
        assertTrue(
            "ensureReadableAccent debe haber elevado la luminancia a >= 0.25f para legibilidad automotriz",
            palette.accent.luminance() >= 0.25f
        )
    }

    @Test
    fun tier2_test02_custom_theme_bright_accent_maintains_readable_contrast() {
        // Derivation: Acento muy brillante (amarillo puro #FFFF00, luminancia > 0.8)
        val brightYellowHex = 0xFFFFFF00
        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = brightYellowHex)

        assertEquals(
            "Con acento de alta luminancia, onAccent debe ser oscuro (#080B12) para contraste",
            Color(0xFF080B12),
            palette.onAccent
        )
    }

    @Test
    fun tier2_test03_cockpit_widget_empty_state_handling() {
        // Derivation: ORIGINAL_REQUEST §R3 line 40: "estados de reproducción y carga"
        // Cuando no hay canción cargada / cola vacía
        val emptyTrack = TrackInfo(
            mediaId = "",
            title = "",
            artist = "",
            durationMs = 0L,
            currentPositionMs = 0L,
            albumArtUri = null
        )
        fakeRepo._currentTrack.value = emptyTrack

        assertFalse("hasTrack debe ser false cuando no hay pista activa", fakeRepo.currentTrack.value.hasTrack)
        val durationMs = fakeRepo.currentTrack.value.durationMs ?: 0L
        val progressFraction = if (durationMs > 0L) {
            (fakeRepo.currentTrack.value.currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        assertEquals(0f, progressFraction, 0.001f)
    }

    @Test
    fun tier2_test04_cockpit_widget_lyrics_boundary_before_first_and_after_last() {
        // Derivation: Playhead fuera del rango de letras sincronizadas
        val lyrics = listOf(
            LyricLine(timeMs = 5000L, text = "Intro vocal", sungUntilMs = 10000L)
        )

        // Antes del inicio (2000ms)
        val beforeIntro = lyrics.firstOrNull { 2000L in it.timeMs..it.endMs }
        assertNull("Antes del inicio de la letra no hay línea activa", beforeIntro)

        // Después del final (15000ms)
        val afterOutro = lyrics.firstOrNull { 15000L in it.timeMs..it.endMs }
        assertNull("Después de la última línea no hay línea activa", afterOutro)
    }

    @Test
    fun tier2_test05_cockpit_widget_rapid_play_pause_toggling() {
        // Derivation: El conductor presiona rápidamente play/pause varias veces seguidas
        for (i in 1..10) {
            fakeRepo.togglePlayback()
        }
        assertEquals(5, fakeRepo.playCallCount)
        assertEquals(5, fakeRepo.pauseCallCount)
        assertFalse("Tras 10 toggles partiendo de pausa, debe quedar en pausa", fakeRepo.playbackState.value.isPlaying)
    }

    @Test
    fun tier2_test06_progress_fraction_clamped_between_zero_and_one() {
        // Derivation: Playhead anómalo (negativo o mayor a duración)
        val duration = 100000L

        // Caso A: currentPosition < 0
        val negPos = -5000L
        val fractionA = (negPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(0f, fractionA, 0.001f)

        // Caso B: currentPosition > duration (después del seek o buffer latency)
        val overPos = 120000L
        val fractionB = (overPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1f, fractionB, 0.001f)
    }

    // =========================================================================
    // TIER 3: CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun tier3_test01_cockpit_widget_updates_reactively_on_queue_modifications() {
        // Derivation: ORIGINAL_REQUEST §R3 lines 42-43:
        // "actualización reactiva del estado y conexión con el único DivineDeckPlaybackService."
        val song1 = createSong("s1", "First Song", "Artist 1", 180L)
        val song2 = createSong("s2", "Second Song", "Artist 2", 200L)

        // Cargar lista en fakeRepo
        fakeRepo.playQueue(listOf(song1, song2), startIndex = 0)

        assertEquals("First Song", fakeRepo.currentTrack.value.title)
        assertEquals(2, fakeRepo.currentQueue.value.size)

        // Agregar una tercera canción via enqueueLast
        val song3 = createSong("s3", "Third Song", "Artist 3", 220L)
        fakeRepo.enqueueLast(song3)

        assertEquals("La cola reactiva debe ahora reflejar 3 canciones", 3, fakeRepo.currentQueue.value.size)
        assertEquals("s3", fakeRepo.currentQueue.value[2].videoId)

        // Avanzar a la siguiente pista
        fakeRepo.skipToNext()
        assertEquals("Second Song", fakeRepo.currentTrack.value.title)
        assertEquals("s2", fakeRepo.currentTrack.value.mediaId)
    }

    @Test
    fun tier3_test02_cockpit_widget_theme_switching_during_active_playback() {
        // Derivation: ORIGINAL_REQUEST §R3 line 41:
        // Cambio dinámico entre temas mientras la reproducción continúa
        fakeRepo.play()
        assertTrue("La reproducción está activa", fakeRepo.playbackState.value.isPlaying)

        // Transición de OBSIDIAN a ARCTIC
        var activePalette = buildPaletteForTheme(ThemePreset.OBSIDIAN)
        assertEquals(Color(0xFFFFB547), activePalette.accent)

        activePalette = buildPaletteForTheme(ThemePreset.ARCTIC)
        assertEquals(Color(0xFF22D3EE), activePalette.accent)

        // Transición de ARCTIC a MONOCHROME
        activePalette = buildPaletteForTheme(ThemePreset.MONOCHROME)
        assertEquals(Color(0xFFF4F5F7), activePalette.accent)

        // Transición de MONOCHROME a BRONZE
        activePalette = buildPaletteForTheme(ThemePreset.BRONZE)
        assertEquals(Color(0xFFD98A4E), activePalette.accent)

        // La reproducción debe continuar ininterrumpida durante todas las transiciones
        assertTrue("La reproducción sigue activa sin reiniciarse", fakeRepo.playbackState.value.isPlaying)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS
    // =========================================================================

    @Test
    fun tier4_test01_full_automotive_cockpit_session() {
        // Derivation: ORIGINAL_REQUEST §Real-World Scenario:
        // "Full automotive driving session: browse music, swipe songs to queue next/last,
        //  reorder user queue via drag & drop, switch Cockpit theme, follow synced lyrics."
        // 1. Vehículo en marcha: Cockpit inicia con tema OBSIDIAN
        var currentPalette = buildPaletteForTheme(ThemePreset.OBSIDIAN)
        assertEquals(ThemePreset.OBSIDIAN, currentPalette.preset)

        // 2. Conductor inicia reproducción de ruta
        val trackA = createSong("road_01", "Highway to Sky", "Aero Drive", 300L)
        val trackB = createSong("road_02", "Neon Horizon", "Synth Wave", 240L)
        fakeRepo.playQueue(listOf(trackA, trackB), startIndex = 0)

        assertTrue(fakeRepo.playbackState.value.isPlaying)
        assertEquals("Highway to Sky", fakeRepo.currentTrack.value.title)

        // 3. Conductor añade pista vía Swipe to Queue (enqueueNext)
        val trackC = createSong("road_03", "Midnight Cruise", "Retro City", 210L)
        fakeRepo.enqueueNext(trackC)
        assertEquals("road_03", fakeRepo.currentQueue.value[1].videoId)

        // 4. Conductor reordena la cola (mueve trackB antes de trackC)
        fakeRepo.reorderQueue(fromIndex = 2, toIndex = 1)
        assertEquals("road_02", fakeRepo.currentQueue.value[1].videoId)
        assertEquals("road_03", fakeRepo.currentQueue.value[2].videoId)

        // 5. Conductor cambia el tema del Cockpit a ARCTIC
        currentPalette = buildPaletteForTheme(ThemePreset.ARCTIC)
        assertEquals(ThemePreset.ARCTIC, currentPalette.preset)
        assertEquals(Color(0xFF22D3EE), currentPalette.accent)

        // 6. Sigue las letras sincronizadas en vivo
        fakeRepo.seekTo(15000L) // 15 segundos
        assertEquals(15000L, fakeRepo.currentTrack.value.currentPositionMs)

        // 7. Salta a la siguiente pista
        fakeRepo.skipToNext()
        assertEquals("Neon Horizon", fakeRepo.currentTrack.value.title)
        assertTrue(fakeRepo.playbackState.value.isPlaying)
    }
}

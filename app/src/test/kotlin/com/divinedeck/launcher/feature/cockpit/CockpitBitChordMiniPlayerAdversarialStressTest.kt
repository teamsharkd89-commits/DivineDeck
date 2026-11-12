package com.divinedeck.launcher.feature.cockpit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.divinedeck.launcher.core.designsystem.components.formatDuration
import com.divinedeck.launcher.core.designsystem.theme.SoftWhite
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
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
import org.junit.Test
import kotlin.random.Random

/**
 * Adversarial Empirical Stress Test Suite for Milestone M3 (Cockpit BitChord MiniPlayer).
 * Authored by challenger_m3_1 to empirically probe failure modes, boundary extremes,
 * contrast guarantees, duration arithmetic, and high-frequency event sequences.
 */
class CockpitBitChordMiniPlayerAdversarialStressTest {

    // =========================================================================
    // 1. THEME & CONTRAST STRESS TESTING (ensureReadableAccent)
    // =========================================================================

    @Test
    fun stress01_extreme_custom_accent_pure_black() {
        // Pure black #000000 has 0.0f relative luminance
        val blackHex = 0xFF000000L
        val rawBlack = Color(blackHex)
        assertEquals(0f, rawBlack.luminance(), 0.0001f)

        val safeColor = ensureReadableAccent(rawBlack)
        assertTrue(
            "Pure black accent MUST be boosted to luminance >= 0.25f, got ${safeColor.luminance()}",
            safeColor.luminance() >= 0.25f
        )
        // Also verify through buildPaletteForTheme
        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = blackHex)
        assertTrue(
            "CUSTOM palette accent MUST have luminance >= 0.25f even if input is pure black",
            palette.accent.luminance() >= 0.25f
        )
        // On dark background (#080A0F, luminance ~0.007), contrast ratio must exceed 4.5:1
        val bgLuminance = palette.background.luminance()
        val contrastRatio = (safeColor.luminance() + 0.05f) / (bgLuminance + 0.05f)
        assertTrue("Contrast ratio against dark cockpit surface must be >= 4.5:1, got $contrastRatio", contrastRatio >= 4.5f)
    }

    @Test
    fun stress02_extreme_custom_accent_pure_white() {
        // Pure white #FFFFFF has 1.0f relative luminance
        val whiteHex = 0xFFFFFFFFL
        val rawWhite = Color(whiteHex)
        assertEquals(1.0f, rawWhite.luminance(), 0.0001f)

        val safeColor = ensureReadableAccent(rawWhite)
        assertEquals(1.0f, safeColor.luminance(), 0.0001f)

        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = whiteHex)
        assertEquals(1.0f, palette.accent.luminance(), 0.0001f)
        // OnAccent should provide dark text contrast against pure white
        assertEquals(Color(0xFF080B12), palette.onAccent)
    }

    @Test
    fun stress03_extreme_custom_accent_neon_green() {
        // Neon green #00FF00 (or #39FF14) has very high luminance (~0.715)
        val neonGreenHex = 0xFF00FF00L
        val rawGreen = Color(neonGreenHex)
        assertTrue("Neon green has high luminance", rawGreen.luminance() > 0.7f)

        val safeColor = ensureReadableAccent(rawGreen)
        assertTrue("Neon green remains >= 0.25f", safeColor.luminance() >= 0.25f)

        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = neonGreenHex)
        assertTrue(palette.accent.luminance() >= 0.25f)
        assertEquals(Color(0xFF080B12), palette.onAccent)
    }

    @Test
    fun stress04_extreme_custom_accent_navy_blue() {
        // Deep Navy Blue #000080 has luminance ~0.005, extremely illegible on dark surfaces
        val navyHex = 0xFF000080L
        val rawNavy = Color(navyHex)
        assertTrue("Raw navy blue has critically low luminance (< 0.02f)", rawNavy.luminance() < 0.02f)

        val safeColor = ensureReadableAccent(rawNavy)
        assertTrue(
            "Navy blue MUST be lifted by ensureReadableAccent to >= 0.25f, got ${safeColor.luminance()}",
            safeColor.luminance() >= 0.25f
        )

        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = navyHex)
        assertTrue(
            "CUSTOM palette accent from Navy Blue must meet >= 0.25f luminance threshold",
            palette.accent.luminance() >= 0.25f
        )
    }

    @Test
    fun stress05_random_custom_accent_fuzzing_1000_colors() {
        // Fuzz 1,000 random 24-bit RGB colors (from #000000 to #FFFFFF)
        val rng = Random(42)
        for (i in 1..1000) {
            val r = rng.nextInt(256)
            val g = rng.nextInt(256)
            val b = rng.nextInt(256)
            val hex = (0xFFL shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            val rawColor = Color(hex)
            val safeColor = ensureReadableAccent(rawColor)

            assertTrue(
                "ensureReadableAccent failed for color #%06X (luminance was ${safeColor.luminance()})".format(hex and 0xFFFFFF),
                safeColor.luminance() >= 0.25f
            )
        }
    }

    @Test
    fun stress06_dynamic_theme_switching_during_progressing_playback() {
        // Simulate rapid theme toggling across all 5 themes while playhead position increments
        val presets = listOf(
            ThemePreset.OBSIDIAN,
            ThemePreset.ARCTIC,
            ThemePreset.MONOCHROME,
            ThemePreset.BRONZE,
            ThemePreset.CUSTOM
        )

        var simulatedPosition = 0L
        for (cycle in 1..50) {
            for (preset in presets) {
                simulatedPosition += 500L
                val palette = if (preset == ThemePreset.CUSTOM) {
                    buildPaletteForTheme(preset, customAccentHex = 0xFF34D399L)
                } else {
                    buildPaletteForTheme(preset)
                }

                assertNotNull(palette.accent)
                assertNotNull(palette.surface)
                assertNotNull(palette.background)
                assertNotNull(palette.borderSubtle)
                assertNotNull(palette.textPrimary)
                assertTrue("Palette accent must be readable", palette.accent.luminance() >= 0.25f)
            }
        }
        assertEquals(125000L, simulatedPosition)
    }

    // =========================================================================
    // 2. CONTENT EDGE CASES: TITLES, ARTISTS, DURATION & LYRICS
    // =========================================================================

    @Test
    fun stress07_extremely_long_titles_and_artists() {
        // Track title with 5,000 characters and artist with 5,000 characters
        val ultraLongTitle = "A".repeat(5000)
        val ultraLongArtist = "B".repeat(5000)
        val track = TrackInfo(
            mediaId = "long_1",
            title = ultraLongTitle,
            artist = ultraLongArtist,
            album = "C".repeat(1000),
            durationMs = 300000L,
            currentPositionMs = 50000L,
            albumArtUri = null
        )

        assertTrue(track.hasTrack)
        assertEquals(5000, track.title?.length)
        assertEquals(5000, track.artist?.length)

        // Verify formatting logic does not choke
        val formattedTitle = track.title?.ifBlank { "Sin título" } ?: "Sin título"
        assertEquals(5000, formattedTitle.length)
        val formattedArtist = track.artist?.ifBlank { "BitChord" } ?: "BitChord"
        val formattedArtistDuration = "$formattedArtist • ${formatDuration(track.currentPositionMs)} / ${formatDuration(track.durationMs ?: 0L)}"
        assertTrue(formattedArtistDuration.contains("0:50 / 5:00"))
    }

    @Test
    fun stress08_duration_arithmetic_extremes() {
        // Zero duration
        assertEquals("0:00", formatDuration(0L))
        val zeroDuration = 0L
        val zeroFraction = if (zeroDuration > 0L) (5000L.toFloat() / zeroDuration.toFloat()).coerceIn(0f, 1f) else 0f
        assertEquals(0f, zeroFraction, 0.0001f)

        // Negative duration
        assertEquals("0:00", formatDuration(-10000L))
        val negDuration = -5000L
        val negFraction = if (negDuration > 0L) (1000L.toFloat() / negDuration.toFloat()).coerceIn(0f, 1f) else 0f
        assertEquals(0f, negFraction, 0.0001f)

        // Position exceeds duration (e.g. streaming overflow, buffer lag)
        val duration = 180000L
        val position = 250000L
        val overflowFraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        assertEquals(1.0f, overflowFraction, 0.0001f)

        // Extremely large duration (Long.MAX_VALUE / 2)
        val hugeDurationMs = 360000000L // 100 hours
        val hugeFormatted = formatDuration(hugeDurationMs)
        assertEquals("6000:00", hugeFormatted)
    }

    @Test
    fun stress09_lyrics_empty_single_line_and_500_lines() {
        // Case A: Empty lyrics list
        val emptyLyrics = emptyList<LyricLine>()
        val activeEmpty = emptyLyrics.indexOfLast { it.timeMs <= 5000L }
        assertEquals(-1, activeEmpty)

        // Case B: Single line lyrics
        val singleLine = listOf(LyricLine(timeMs = 10000L, text = "Only line in the song", sungUntilMs = 15000L))
        // Before line
        val activeBefore = singleLine.indexOfLast { it.timeMs <= 5000L }
        assertEquals(-1, activeBefore)
        // During line
        val activeDuring = singleLine.indexOfLast { it.timeMs <= 12000L }
        assertEquals(0, activeDuring)
        val textDuring = singleLine[activeDuring].text.trim()
        assertEquals("Only line in the song", textDuring)
        // Next line check (must be safely out of bounds)
        val hasNext = (activeDuring + 1) < singleLine.size
        assertFalse("Single line has no next line", hasNext)

        // Case C: 500-line lyrics stress test with periodic empty gap lines
        val lines500 = (0 until 500).map { i ->
            val isPeriodicGap = (i % 20 == 0)
            LyricLine(
                timeMs = (i * 3000L),
                text = if (isPeriodicGap) "" else "Lyric line number $i in symphony",
                sungUntilMs = (i * 3000L + 2800L)
            )
        }
        assertEquals(500, lines500.size)

        // Query active index at various playhead positions
        val posA = 45000L // should be line 15 (15 * 3000 = 45000)
        val idxA = lines500.indexOfLast { it.timeMs <= posA }
        assertEquals(15, idxA)
        assertEquals("Lyric line number 15 in symphony", lines500[idxA].text)
        assertFalse(lines500[idxA].isGap)
        val nextIdxA = idxA + 1
        assertTrue(nextIdxA < lines500.size)
        assertEquals("Lyric line number 16 in symphony", lines500[nextIdxA].text)

        // Gap line check (line 20)
        val posGap = 60000L // 20 * 3000 = 60000
        val idxGap = lines500.indexOfLast { it.timeMs <= posGap }
        assertEquals(20, idxGap)
        assertTrue("Line 20 must be gap (empty text)", lines500[idxGap].isGap)

        // Last line boundary check
        val posLast = 1499000L
        val idxLast = lines500.indexOfLast { it.timeMs <= posLast }
        assertEquals(499, idxLast)
        val hasNextAfterLast = (idxLast + 1) < lines500.size
        assertFalse("Last lyric line has no subsequent line", hasNextAfterLast)

        // Beyond last line
        val posBeyond = 2000000L
        val idxBeyond = lines500.indexOfLast { it.timeMs <= posBeyond }
        assertEquals(499, idxBeyond)
    }

    // =========================================================================
    // 3. RAPID USER INTERACTION & DEBOUNCING / REACTIVITY
    // =========================================================================

    private class StressMockMediaRepository : MediaControllerRepository {
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
                mediaId = "init",
                title = "Initial",
                artist = "Initial",
                album = "Initial",
                durationMs = 180000L,
                currentPositionMs = 0L,
                albumArtUri = null
            )
        )
        override val currentTrack: StateFlow<TrackInfo> = _currentTrack

        val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
        override val currentQueue: StateFlow<List<Song>> = _currentQueue

        var toggleCount = 0
        var skipCount = 0

        override fun connect() {}
        override fun disconnect() {}
        override fun play() {
            _playbackState.value = _playbackState.value.copy(isPlaying = true, status = PlaybackStatus.PLAYING)
        }
        override fun pause() {
            _playbackState.value = _playbackState.value.copy(isPlaying = false, status = PlaybackStatus.PAUSED)
        }
        override fun togglePlayback() {
            toggleCount++
            if (_playbackState.value.isPlaying) pause() else play()
        }
        override fun skipToNext() {
            skipCount++
            val queue = _currentQueue.value
            if (queue.size > 1) {
                val next = queue[1]
                _currentTrack.value = TrackInfo(
                    mediaId = next.videoId,
                    title = next.title,
                    artist = next.artist,
                    album = next.albumName ?: "",
                    durationMs = (next.durationSeconds ?: 0L) * 1000L,
                    currentPositionMs = 0L,
                    albumArtUri = next.thumbnailUrl
                )
                _currentQueue.value = queue.drop(1)
            }
        }
        override fun skipToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun setVolume(percent: Int) {}
        override fun playQueue(songs: List<Song>, startIndex: Int) {
            _currentQueue.value = songs
            if (songs.isNotEmpty()) {
                val s = songs[startIndex.coerceIn(songs.indices)]
                _currentTrack.value = TrackInfo(
                    mediaId = s.videoId,
                    title = s.title,
                    artist = s.artist,
                    album = s.albumName ?: "",
                    durationMs = (s.durationSeconds ?: 0L) * 1000L,
                    currentPositionMs = 0L,
                    albumArtUri = s.thumbnailUrl
                )
                play()
            }
        }
        override fun skipToQueueItem(index: Int) {}
        override fun enqueueNext(song: Song) {}
        override fun enqueueLast(song: Song) {}
        override fun reorderQueue(fromIndex: Int, toIndex: Int) {}
        override fun removeFromQueue(index: Int) {}
        override fun clearUserQueue() {}
        override fun playLocalUri(uriString: String, title: String?, artist: String?, artworkUri: String?) {}
        override fun playLocalUri(uri: android.net.Uri, title: String?, artist: String?, artworkUri: String?) {}
        override fun playSong(song: Song) {}
        override fun playOnlineTrack(videoId: String, title: String, artist: String, thumbnailUrl: String?, durationMs: Long?) {}
        override fun playSampleAudio() {}
    }

    @Test
    fun stress10_rapid_clicks_500_toggles_and_skips() {
        val repo = StressMockMediaRepository()
        val songs = (1..50).map { i ->
            Song(
                videoId = "song_$i",
                title = "Title $i",
                artist = "Artist $i",
                thumbnailUrl = null,
                durationSeconds = 180L,
                queueTier = QueueTier.CONTEXT
            )
        }
        repo.playQueue(songs, 0)
        assertTrue(repo.playbackState.value.isPlaying)

        // 500 rapid play/pause toggles
        for (i in 1..500) {
            repo.togglePlayback()
        }
        assertEquals(500, repo.toggleCount)
        assertTrue("After 500 toggles starting from playing, must end in playing", repo.playbackState.value.isPlaying)

        // 40 skips
        for (i in 1..40) {
            repo.skipToNext()
        }
        assertEquals(40, repo.skipCount)
        assertEquals("song_41", repo.currentTrack.value.mediaId)
    }

    @Test
    fun stress11_empty_state_transitions() {
        val repo = StressMockMediaRepository()

        // 1. Initial State: active track exists
        assertTrue(repo.currentTrack.value.hasTrack)

        // 2. Transition to empty state (null/blank track)
        repo._currentTrack.value = TrackInfo(
            mediaId = "",
            title = "",
            artist = "",
            album = "",
            durationMs = null,
            currentPositionMs = 0L,
            albumArtUri = null
        )
        assertFalse("Empty track hasTrack must be false", repo.currentTrack.value.hasTrack)
        val song = com.divinedeck.launcher.feature.music.ui.BitChordMediaModelAdapter.toSong(repo.currentTrack.value)
        assertNull("Empty track must adapt to null Song", song)

        // 3. Transition back to active song
        val newSong = Song(
            videoId = "resurrected",
            title = "Awakening",
            artist = "Soundtrack",
            thumbnailUrl = "https://example.com/art.png",
            durationSeconds = 240L
        )
        repo._currentTrack.value = TrackInfo(
            mediaId = newSong.videoId,
            title = newSong.title,
            artist = newSong.artist,
            album = "Awakening OST",
            durationMs = 240000L,
            currentPositionMs = 10000L,
            albumArtUri = newSong.thumbnailUrl
        )
        assertTrue(repo.currentTrack.value.hasTrack)
        val adapted = com.divinedeck.launcher.feature.music.ui.BitChordMediaModelAdapter.toSong(repo.currentTrack.value)
        assertNotNull(adapted)
        assertEquals("resurrected", adapted?.videoId)
        assertEquals("Awakening", adapted?.title)
    }
}

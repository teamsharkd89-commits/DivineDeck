package com.music.bitchord.ui.player

import com.music.bitchord.data.lyrics.LyricAlignment
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricSyllable
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.CreditSegment
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.PlaybackPosition
import com.music.bitchord.ui.theme.adaptedArtworkSaturation
import com.music.bitchord.ui.theme.relativeLuminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit test suite for :bitchord-player-ui extracted components.
 * Tests pure models, formatting, lyrics sync calculations, and credit segments.
 */
class BitChordPlayerUiTest {

    @Test
    fun formatTime_formatsCorrectly() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:00", formatTime(-1000L))
        assertEquals("0:05", formatTime(5_000L))
        assertEquals("0:59", formatTime(59_000L))
        assertEquals("1:00", formatTime(60_000L))
        assertEquals("3:45", formatTime(225_000L))
        assertEquals("10:02", formatTime(602_000L))
    }

    @Test
    fun songModel_instantiationAndProperties() {
        val song = Song(
            videoId = "track_123",
            title = "Starboy",
            artist = "The Weeknd feat. Daft Punk",
            durationSeconds = 230L,
            thumbnailUrl = "https://example.com/art.jpg",
            albumName = "Starboy",
            artists = listOf(
                ArtistRef(name = "The Weeknd", browseId = "artist_1"),
                ArtistRef(name = "Daft Punk", browseId = "artist_2")
            ),
            likeStatus = LikeStatus.LIKED,
            queueTier = QueueTier.USER_QUEUE,
        )

        assertEquals("track_123", song.id)
        assertEquals("track_123", song.videoId)
        assertEquals("Starboy", song.title)
        assertEquals(230_000L, song.durationMillis)
        assertEquals(2, song.artists.size)
        assertEquals(LikeStatus.LIKED, song.likeStatus)
        assertEquals("https://example.com/art.jpg", song.artworkUrl)
    }

    @Test
    fun artistCreditSegments_parsesMultipleArtists() {
        val song = Song(
            videoId = "t1",
            title = "Test Track",
            artist = "Drake, 21 Savage",
            thumbnailUrl = null,
            artists = listOf(
                ArtistRef(name = "Drake", browseId = "a1"),
                ArtistRef(name = "21 Savage", browseId = "a2")
            ),
        )

        val segments = song.creditSegments()
        assertTrue(segments.isNotEmpty())
        val artistNames = segments.mapNotNull { it.credit?.name }
        assertTrue(artistNames.contains("Drake"))
        assertTrue(artistNames.contains("21 Savage"))
    }

    @Test
    fun lyricLine_timingAndProperties() {
        val word1 = LyricWord(startMs = 1000L, endMs = 1500L, text = "Hello")
        val word2 = LyricWord(startMs = 1600L, endMs = 2200L, text = "World")

        val line = LyricLine(
            timeMs = 1000L,
            text = "Hello World",
            words = listOf(word1, word2),
            sungUntilMs = 2300L,
            alignment = LyricAlignment.Start,
        )

        assertEquals(1000L, line.timeMs)
        assertEquals("Hello World", line.text)
        assertTrue(line.isWordSynced)
        assertTrue(line.hasKnownEnd)
        assertEquals(2200L, line.endMs)
        assertFalse(line.isGap)

        // For a line-synced lyric without individual words, sungUntilMs is used
        val lineSynced = LyricLine(timeMs = 1000L, text = "Line synced", sungUntilMs = 2300L)
        assertEquals(2300L, lineSynced.endMs)
    }

    @Test
    fun lyricWord_charsSungAt_interpolation() {
        val word = LyricWord(startMs = 1000L, endMs = 2000L, text = "Antigravity")
        // Before start
        assertEquals(0f, word.charsSungAt(500L), 0.01f)
        // Midpoint
        assertEquals(5.5f, word.charsSungAt(1500L), 0.01f)
        // After end
        assertEquals(11f, word.charsSungAt(2500L), 0.01f)
    }

    @Test
    fun lyricSyllable_normalization() {
        val s1 = LyricSyllable(startMs = 100L, endMs = 300L, startChar = 0, endChar = 4)
        val s2 = LyricSyllable(startMs = 300L, endMs = 600L, startChar = 4, endChar = 8)
        val normalized = LyricSyllable.normalized(listOf(s1, s2), length = 8)

        assertEquals(2, normalized.size)
        assertEquals(0, normalized[0].startChar)
        assertEquals(4, normalized[0].endChar)
        assertEquals(4, normalized[1].startChar)
        assertEquals(8, normalized[1].endChar)
    }

    @Test
    fun playbackPosition_reportAndSeek() {
        val pos = PlaybackPosition()
        assertEquals(0L, pos.positionMs)
        assertEquals(0, pos.seeks)
        assertFalse(pos.moving)

        pos.report(position = 15000L, timeNanos = 1_000_000_000L, moving = true)
        assertEquals(15000L, pos.positionMs)
        assertEquals(1_000_000_000L, pos.sampledAtNanos)
        assertTrue(pos.moving)

        pos.markSeek()
        assertEquals(1, pos.seeks)
    }

    @Test
    fun repeatModes_constants() {
        assertEquals(0, RepeatModes.OFF)
        assertEquals(1, RepeatModes.ONE)
        assertEquals(2, RepeatModes.ALL)
    }

    @Test
    fun relativeLuminance_calculation() {
        // Pure black
        assertEquals(0f, relativeLuminance(0xFF000000.toInt()), 0.001f)
        // Pure white
        assertEquals(1f, relativeLuminance(0xFFFFFFFF.toInt()), 0.001f)
    }

    @Test
    fun adaptedArtworkSaturation_boundaryChecks() {
        // Under chromatic threshold
        assertEquals(0.05f, adaptedArtworkSaturation(0.05f, 0.2f, 0.6f), 0.001f)
        // Normal clamping
        assertEquals(0.35f, adaptedArtworkSaturation(0.35f, 0.2f, 0.6f), 0.001f)
        // Over max
        assertEquals(0.6f, adaptedArtworkSaturation(0.85f, 0.2f, 0.6f), 0.001f)
    }
}

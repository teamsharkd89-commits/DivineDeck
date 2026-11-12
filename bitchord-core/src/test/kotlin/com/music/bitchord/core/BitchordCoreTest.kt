package com.music.bitchord.core

import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.model.durationMillis
import com.music.bitchord.data.model.isSameTrackAs
import com.music.bitchord.playback.PlaybackPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitchordCoreTest {

    @Test
    fun `Song model preserves full metadata and duration parsing`() {
        val song = Song(
            videoId = "test_vid_123",
            title = "Midnight City",
            artist = "M83",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "4:03",
            albumName = "Hurry Up, We're Dreaming",
            queueTier = QueueTier.CONTEXT
        )

        assertEquals("test_vid_123", song.videoId)
        assertEquals("test_vid_123", song.id)
        assertEquals("Midnight City", song.title)
        assertEquals("M83", song.artist)
        assertEquals("M83", song.artistName)
        assertEquals(243_000L, song.durationMillis)
        assertEquals(243_000L, song.durationMillis())
        assertFalse(song.fromAutoplay)
        assertEquals(QueueTier.CONTEXT, song.queueTier)
    }

    @Test
    fun `isSameTrackAs accurately compares recording title and artist`() {
        val s1 = Song(videoId = "v1", title = "Starboy", artist = "The Weeknd", thumbnailUrl = null)
        val s2 = Song(videoId = "v2", title = "Starboy", artist = "The Weeknd", thumbnailUrl = null)
        val s3 = Song(videoId = "v3", title = "I Feel It Coming", artist = "The Weeknd", thumbnailUrl = null)

        assertTrue(s1.isSameTrackAs(s2))
        assertFalse(s1.isSameTrackAs(s3))
        assertFalse(s1.isSameTrackAs(null))
    }

    @Test
    fun `PlaybackPosition updates and reports atomic playhead progress`() {
        val position = PlaybackPosition()
        assertEquals(0L, position.positionMs)

        position.report(15_000L)
        assertEquals(15_000L, position.positionMs)
        assertTrue(position.sampledAtNanos > 0L)

        position.report(0L)
        assertEquals(0L, position.positionMs)
    }

    @Test
    fun `TrackLog and MonotonicClock operate without throwing`() {
        TrackLog.about("test_track_789")
        TrackLog.d("TestTag", "Diagnostic message")
        TrackLog.w("TestTag", "Warning message")
        assertTrue(MonotonicClock.nowMs() > 0L)
    }

    @Test
    fun `NerdStats records and updates stream telemetry`() {
        NerdStats.recordSource("test_vid", "YouTube Music")
        NerdStats.onStreamPicked("test_vid", 256)

        val stats = NerdStats.currentStats.value
        assertEquals("test_vid", stats.videoId)
        assertEquals("YouTube Music", stats.source)
        assertEquals(256, stats.bitrateKbps)
    }
}

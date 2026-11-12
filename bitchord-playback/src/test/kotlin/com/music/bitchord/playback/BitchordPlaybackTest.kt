package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.QueueTimeline.asQueueEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitchordPlaybackTest {

    @Test
    fun testQueueTimelineAsQueueEntry() {
        val song = Song(
            videoId = "test_vid_1",
            title = "Playback Engine Track",
            artist = "Test Artist",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "3:00",
        )
        val userQueueItem = song.asQueueEntry(QueueTier.USER_QUEUE)

        assertEquals(QueueTier.USER_QUEUE, userQueueItem.queueTier)
        assertNotNull(userQueueItem.queueEntryId)
        assertEquals("test_vid_1", userQueueItem.videoId)
    }

    @Test
    fun testQueueShuffleToggle() {
        QueueShuffle.setEnabled(false)
        assertFalse(QueueShuffle.enabled.value)

        QueueShuffle.setEnabled(true)
        assertTrue(QueueShuffle.enabled.value)

        QueueShuffle.setEnabled(false)
        assertFalse(QueueShuffle.enabled.value)
    }

    @Test
    fun testDynamicLruCacheEvictorInitialization() {
        val evictor = DynamicLruCacheEvictor(
            maxBytes = 256L * 1024 * 1024,
            headBytes = 256L * 1024,
            headBudgetBytes = 32L * 1024 * 1024,
        )

        assertEquals(256L * 1024 * 1024, evictor.maxBytes)
        evictor.maxBytes = 512L * 1024 * 1024
        assertEquals(512L * 1024 * 1024, evictor.maxBytes)
        assertTrue(evictor.requiresCacheSpanTouches())
    }

    @Test
    fun testShuffledStartingOrderPreservesStartTrack() {
        val songs = (1..5).map {
            Song(
                videoId = "vid_$it",
                title = "Track $it",
                artist = "Artist",
                thumbnailUrl = "https://example.com/thumb$it.jpg",
                durationText = "3:20",
            )
        }

        val result = QueueTimeline.shuffledStartingOrder(songs, startIndex = 2)
        assertEquals(5, result.size)
        // The first song in the shuffled result must be the one at startIndex
        assertEquals("vid_3", result[0].videoId)
    }
}

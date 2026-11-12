package com.divinedeck.launcher.feature.music

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.music.bitchord.data.innertube.PlayerClient
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.playback.DynamicLruCacheEvictor
import com.music.bitchord.playback.QueueCoordinator
import com.music.bitchord.playback.QueueCoordinator.asQueueEntry
import com.music.bitchord.playback.QueueSource
import com.music.bitchord.playback.QueueTimeline
import com.music.bitchord.playback.StreamContainer
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias para el motor de reproducción original de BitChord 1.8.1
 * integrado dentro de DIVINE DECK.
 */
class BitChordPlaybackEngineTest {

    private fun createSong(id: String, tier: QueueTier = QueueTier.CONTEXT): Song {
        return Song(
            videoId = id,
            title = "Title $id",
            artist = "Artist $id",
            thumbnailUrl = "https://example.com/thumb/$id.jpg",
            queueTier = tier
        )
    }

    @Test
    fun `QueueTimeline buildContextQueue preserves preceding, selected, user queue, and following context`() {
        val currentTimeline = listOf(
            createSong("current", QueueTier.CONTEXT),
            createSong("user_next_1", QueueTier.USER_QUEUE)
        )
        val newContext = listOf(
            createSong("ctx_1"),
            createSong("ctx_2"),
            createSong("ctx_3")
        )
        val result = QueueTimeline.buildContextQueue(
            currentTimeline = currentTimeline,
            currentIndex = 0,
            newContextSongs = newContext,
            selectedIndex = 1,
            contextSource = QueueSource("Test Album")
        )

        assertEquals(1, result.startIndex)
        assertEquals(4, result.timeline.size)
        assertEquals("ctx_1", result.timeline[0].videoId)
        assertEquals("ctx_2", result.timeline[1].videoId)
        assertEquals("user_next_1", result.timeline[2].videoId)
        assertEquals("ctx_3", result.timeline[3].videoId)
    }

    @Test
    fun `QueueTimeline findUserQueueInsertionIndex correctly differentiates Play Next and Add to Queue`() {
        val timeline = listOf(
            createSong("cur", QueueTier.CONTEXT),
            createSong("u1", QueueTier.USER_QUEUE),
            createSong("u2", QueueTier.USER_QUEUE),
            createSong("c2", QueueTier.CONTEXT)
        )

        val nextIndex = QueueTimeline.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = true)
        val appendIndex = QueueTimeline.findUserQueueInsertionIndex(timeline, currentIndex = 0, isNext = false)

        assertEquals(1, nextIndex)
        assertEquals(3, appendIndex)
    }

    @Test
    fun `QueueTimeline computeJumpOrder handles backward jump as pure seek`() {
        val tiers = listOf(QueueTier.CONTEXT, QueueTier.CONTEXT, QueueTier.CONTEXT)
        val plan = QueueTimeline.computeJumpOrder(tiers, currentIndex = 2, targetIndex = 0)

        assertNotNull(plan)
        assertTrue(plan!!.isPureSeek)
        assertEquals(0, plan.newTargetIndex)
    }

    @Test
    fun `QueueTimeline avoidIdentityShuffle alters ordering if at least two items exist`() {
        val original = listOf(0, 1)
        val shuffled = listOf(0, 1)
        val avoided = QueueTimeline.avoidIdentityShuffle(original, shuffled)

        assertNotEquals(original, avoided)
        assertEquals(listOf(1, 0), avoided)
    }

    @Test
    fun `Song asQueueEntry generates deterministic immutable queue entry id`() {
        val song = createSong("sample_id")
        val queueEntry = song.asQueueEntry(QueueTier.USER_QUEUE)

        assertEquals(QueueTier.USER_QUEUE, queueEntry.queueTier)
        assertNotNull(queueEntry.queueEntryId)
        assertTrue(queueEntry.queueEntryId!!.isNotBlank())
    }

    @Test
    fun `Song toMediaItem and MediaItem toSong roundtrip maintains properties`() {
        val song = Song(
            videoId = "test_vid_123",
            title = "BitChord Symphony",
            artist = "Open Source Composer",
            thumbnailUrl = "https://img.youtube.com/vi/test_vid_123/hqdefault.jpg",
            albumName = "Automotive Suite",
            queueTier = QueueTier.USER_QUEUE,
            localUri = "content://media/external/audio/media/42"
        )

        val mediaItem = song.toMediaItem()
        assertEquals("test_vid_123", mediaItem.mediaId)
        assertEquals("BitChord Symphony", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Open Source Composer", mediaItem.mediaMetadata.artist?.toString())
        assertEquals("Automotive Suite", mediaItem.mediaMetadata.albumTitle?.toString())
        assertEquals(QueueTier.USER_QUEUE, mediaItem.queueTier)

        val restoredSong = mediaItem.toSong()
        assertEquals(song.videoId, restoredSong.videoId)
        assertEquals(song.title, restoredSong.title)
        assertEquals(song.artist, restoredSong.artist)
        assertEquals(song.albumName, restoredSong.albumName)
        assertEquals(QueueTier.USER_QUEUE, restoredSong.queueTier)
        assertEquals("content://media/external/audio/media/42", restoredSong.localUri)
    }

    @Test
    fun `PlayerClient forStreamUrl identifies client profiles and headers from query parameters`() {
        val iosUrl = "https://rr3---sn-4g5ednkk.googlevideo.com/videoplayback?c=IOS&cver=21.26.4"
        val client = PlayerClient.forStreamUrl(iosUrl)
        assertEquals("IOS", client.clientName)
        assertTrue(client.mediaHeaders().containsKey("User-Agent"))
        assertTrue(client.mediaHeaders()["User-Agent"]!!.contains("iPhone"))

        val webRemixUrl = "https://rr3---sn-4g5ednkk.googlevideo.com/videoplayback?c=WEB_REMIX&cver=1.20260707.12.00"
        val webClient = PlayerClient.forStreamUrl(webRemixUrl)
        assertEquals("WEB_REMIX", webClient.clientName)
        assertEquals("https://music.youtube.com", webClient.origin)
    }

    @Test
    fun `StreamContainer identifies manifest mime types correctly`() {
        assertEquals(MimeTypes.APPLICATION_M3U8, StreamContainer.manifestMimeOf("https://stream.example.com/audio.m3u8?token=xyz"))
        assertEquals(MimeTypes.APPLICATION_MPD, StreamContainer.manifestMimeOf("https://stream.example.com/manifest.mpd"))
        assertEquals(null, StreamContainer.manifestMimeOf("https://googlevideo.com/videoplayback?id=123"))
    }
}

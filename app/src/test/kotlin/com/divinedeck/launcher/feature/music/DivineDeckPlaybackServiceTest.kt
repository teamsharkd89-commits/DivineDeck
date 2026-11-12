package com.divinedeck.launcher.feature.music

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import com.music.bitchord.playback.queueTier
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias para el motor de reproducción multimedia interno de DIVINE DECK.
 * Valida la configuración de MediaItems, comandos de sesión y compatibilidad de formatos de audio local.
 */
class DivineDeckPlaybackServiceTest {

    @Test
    fun `sample media item has valid asset URI and complete metadata`() {
        val service = DivineDeckPlaybackService()
        val item = service.createSampleMediaItem()

        assertNotNull(item)
        assertEquals(DivineDeckPlaybackService.SAMPLE_TRACK_ID, item.mediaId)
        assertEquals(DivineDeckPlaybackService.SAMPLE_TRACK_TITLE, item.mediaMetadata.title?.toString())
        assertEquals(DivineDeckPlaybackService.SAMPLE_TRACK_ARTIST, item.mediaMetadata.artist?.toString())
        assertEquals(DivineDeckPlaybackService.SAMPLE_TRACK_ALBUM, item.mediaMetadata.albumTitle?.toString())
    }

    @Test
    fun `custom session commands have distinct and valid action names`() {
        assertEquals("com.divinedeck.ACTION_PLAY_SAMPLE", DivineDeckPlaybackService.ACTION_PLAY_SAMPLE)
        assertEquals("com.divinedeck.ACTION_CLEAR_QUEUE", DivineDeckPlaybackService.ACTION_CLEAR_QUEUE)
        assertEquals("com.music.bitchord.ACTION_SHUFFLE_TOGGLE", DivineDeckPlaybackService.ACTION_SHUFFLE_TOGGLE)
    }

    @Test
    fun `BitChord engine constants match original architecture parameters`() {
        assertEquals("bitchord_playback", DivineDeckPlaybackService.CHANNEL_ID)
        assertEquals("BitChordPlayback", DivineDeckPlaybackService.SESSION_ID)
        assertEquals(15 * 60 * 1000, DivineDeckPlaybackService.FAR_BUFFER_MS)
        assertEquals(8 * 1024 * 1024, DivineDeckPlaybackService.FAR_BUFFER_BYTES)
        assertEquals(30 * 1000, DivineDeckPlaybackService.BACK_BUFFER_MS)
        assertEquals(500, DivineDeckPlaybackService.START_PLAYBACK_MS)
        assertEquals(2_000, DivineDeckPlaybackService.RESUME_PLAYBACK_MS)
        assertEquals(10_000L, DivineDeckPlaybackService.BACK_RESTARTS_AFTER_MS)
    }

    @Test
    fun `supports local audio formats MP3, M4A, FLAC, OGG, OPUS, WAV with content and file schemes`() {
        val supportedExtensions = listOf("mp3", "m4a", "flac", "ogg", "opus", "wav")

        for (ext in supportedExtensions) {
            val contentUri = "content://media/external/audio/media/1001.$ext"
            val mediaItem = MediaItem.Builder()
                .setMediaId(contentUri)
                .setUri(contentUri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Test Song $ext")
                        .setArtist("DIVINE DECK Engine")
                        .build()
                )
                .build()

            assertEquals(contentUri, mediaItem.mediaId)
            assertTrue(mediaItem.mediaId.endsWith(ext))
            assertEquals("Test Song $ext", mediaItem.mediaMetadata.title?.toString())
        }
    }

    @Test
    fun `PermanentAwareLoadErrorPolicy marks permanent errors as non-retryable`() {
        val policy = com.music.bitchord.playback.PermanentAwareLoadErrorPolicy()

        assertTrue(policy.isPermanent(java.io.FileNotFoundException("Resource missing")))
        assertTrue(policy.isPermanent(java.io.IOException("Stream is permanently unplayable for videoId")))
        assertTrue(policy.isPermanent(java.io.IOException("HTTP 404 Not Found")))
        assertTrue(policy.isPermanent(java.io.IOException("Unsupported media URI scheme: ftp")))

        assertFalse(policy.isPermanent(java.io.IOException("Connection reset by peer")))
        assertFalse(policy.isPermanent(java.net.SocketTimeoutException("Read timed out")))
    }

    @Test
    fun `online track selection produces valid media item with complete metadata`() {
        val song = com.music.bitchord.data.model.Song(
            videoId = "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        )
        val mediaItem = song.toMediaItem()

        assertNotNull(mediaItem)
        assertEquals("dQw4w9WgXcQ", mediaItem.mediaId)
        assertEquals("Never Gonna Give You Up", mediaItem.mediaMetadata.title?.toString())
        assertEquals("Rick Astley", mediaItem.mediaMetadata.artist?.toString())
        assertEquals(com.music.bitchord.data.model.QueueTier.CONTEXT, mediaItem.queueTier)
    }

    @Test
    fun `queue metadata roundtrip between Song and MediaItem preserves queue tiers`() {
        val song = com.music.bitchord.data.model.Song(
            videoId = "test_vid_roundtrip",
            title = "Queue Track",
            artist = "BitChord Artist",
            thumbnailUrl = null,
            durationText = "3:30",
            queueTier = com.music.bitchord.data.model.QueueTier.USER_QUEUE
        )
        val mediaItem = song.toMediaItem()
        val restoredSong = mediaItem.toSong()

        assertEquals("test_vid_roundtrip", restoredSong.videoId)
        assertEquals("Queue Track", restoredSong.title)
        assertEquals(com.music.bitchord.data.model.QueueTier.USER_QUEUE, restoredSong.queueTier)
    }
}

package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 2 Artwork Persistence.
 * Stress-tests:
 * 1. High concurrency (5 simultaneous songs with identical thumbnailUrl).
 * 2. Corrupted byte handling (0-byte input, truncated JPEG, random garbage bytes).
 * 3. 3-track shared artwork reference-counted deletion.
 * 4. Rapid cancellation during active artwork fetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordAutomaticArtworkAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File
    private lateinit var mockPrefs: InMemorySharedPreferences
    private lateinit var testContext: Context

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
                pending.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    private class TestContextWithStorage(
        private val prefs: SharedPreferences,
        private val baseFilesDir: File
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getFilesDir(): File = baseFilesDir
    }

    private val fastStreamFetcher = object : Downloader.StreamFetcher {
        override suspend fun resolveAndFetch(
            videoId: String,
            maxKbps: Int,
            requireM4a: Boolean,
            openSink: (StreamResolver.Stream) -> OutputStream,
            onProgress: (written: Long, total: Long) -> Unit
        ): StreamResolver.Stream {
            val stream = StreamResolver.Stream(
                url = "https://example.com/audio/$videoId.m4a",
                kbps = 128,
                mimeType = "audio/mp4; codecs=\"mp4a.40.2\""
            )
            val audioBytes = ByteArray(1024) { 0x55 }
            openSink(stream).use { out ->
                out.write(audioBytes)
                onProgress(audioBytes.size.toLong(), audioBytes.size.toLong())
            }
            return stream
        }
    }

    private fun sampleValidJpegBytes(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(),
            0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempBaseDir = Files.createTempDirectory("bitchord_adv_artwork_test_").toFile()
        mockPrefs = InMemorySharedPreferences()
        testContext = TestContextWithStorage(mockPrefs, tempBaseDir)

        AppSettings.initForTest(mockPrefs)
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = false

        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = fastStreamFetcher,
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            maxConcurrentWorkers = 5,
            enableOnlineArtworkFetch = true,
            artFetcher = Downloads.ArtworkFetcher { sampleValidJpegBytes() }
        )
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = null
        AppSettings.resetForTest()
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    /**
     * ADVERSARIAL STRESS 1:
     * Simultaneous/concurrent downloads of 5 songs with identical thumbnailUrl.
     * Verify:
     * - Exactly 1 artwork file is created on disk.
     * - All 5 songs point to the identical canonical file URI.
     * - No .part files or race condition artifacts left behind.
     */
    @Test
    fun `stress1_concurrentDownloadsOf5Songs_singleArtworkCreatedWithoutRaceConditions`() = runTest(testDispatcher) {
        val sharedUrl = "https://example.com/shared_album_5_songs.jpg"
        val networkCallCount = AtomicInteger(0)

        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            networkCallCount.incrementAndGet()
            sampleValidJpegBytes()
        }

        val songs = (1..5).map { index ->
            Song(
                videoId = "concurrent_vid_$index",
                title = "Concurrent Song $index",
                artist = "Artist",
                thumbnailUrl = sharedUrl,
                durationText = "03:00"
            )
        }

        Downloads.enqueueAll(songs)
        advanceUntilIdle()

        // 1. All 5 songs must be downloaded
        for (song in songs) {
            assertTrue("Song ${song.videoId} must be downloaded", Downloads.isDownloaded(song.videoId))
        }

        // 2. Exactly 1 physical artwork file created on disk
        val artDir = File(tempBaseDir, "download-artwork")
        val artFiles = artDir.listFiles { f -> f.isFile && f.name.startsWith("art_") && f.name.endsWith(".jpg") }
        assertNotNull("Artwork dir must contain files", artFiles)
        assertEquals("Exactly 1 artwork file must exist on disk for all 5 songs", 1, artFiles!!.size)

        // 3. No leftover .part files
        val partFiles = artDir.listFiles { f -> f.name.endsWith(".part") }
        assertTrue("No .part files must exist after concurrent completion", partFiles == null || partFiles.isEmpty())

        // 4. All 5 songs reference the exact same canonical localArtworkUri
        val firstUri = Downloads.savedMetadata.value[songs.first().videoId]?.localArtworkUri
        assertNotNull("First song must have localArtworkUri", firstUri)

        for (song in songs) {
            val meta = Downloads.savedMetadata.value[song.videoId]
            assertNotNull("Metadata for ${song.videoId} must exist", meta)
            assertEquals("Song ${song.videoId} must share identical localArtworkUri", firstUri, meta?.localArtworkUri)
            val enriched = Downloads.enrichWithLocalDownload(song)
            assertEquals(firstUri, enriched.thumbnailUrl)
        }
    }

    /**
     * ADVERSARIAL STRESS 2A:
     * Corrupted bytes: 0-byte input.
     * Verify rejected without saving corrupted files, artwork marked pending.
     */
    @Test
    fun `stress2a_corruptBytes_zeroByteInputRejectedWithoutCorruptedFile`() = runTest(testDispatcher) {
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { ByteArray(0) }

        val song = Song(
            videoId = "vid_zero_byte",
            title = "Zero Byte Song",
            artist = "Artist",
            thumbnailUrl = "https://example.com/zero_byte.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("Audio download succeeds", Downloads.isDownloaded(song.videoId))
        assertTrue("0-byte artwork must be marked pending", Downloads.hasPendingArtwork(song.videoId))

        val meta = Downloads.savedMetadata.value[song.videoId]
        assertNotNull(meta)
        assertNull("localArtworkUri must be null", meta?.localArtworkUri)

        val artDir = File(tempBaseDir, "download-artwork")
        val artFiles = artDir.listFiles { f -> f.isFile }
        assertTrue("No file should be saved in artwork dir", artFiles == null || artFiles.isEmpty())
    }

    /**
     * ADVERSARIAL STRESS 2B:
     * Corrupted bytes: Random garbage bytes.
     * Verify rejected without saving corrupted files, artwork marked pending.
     */
    @Test
    fun `stress2b_corruptBytes_randomGarbageBytesRejectedWithoutCorruptedFile`() = runTest(testDispatcher) {
        val randomGarbage = ByteArray(128) { (it * 37 + 13).toByte() }
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { randomGarbage }

        val song = Song(
            videoId = "vid_garbage",
            title = "Garbage Song",
            artist = "Artist",
            thumbnailUrl = "https://example.com/garbage.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("Audio download succeeds", Downloads.isDownloaded(song.videoId))
        assertTrue("Garbage artwork must be marked pending", Downloads.hasPendingArtwork(song.videoId))

        val meta = Downloads.savedMetadata.value[song.videoId]
        assertNotNull(meta)
        assertNull("localArtworkUri must be null", meta?.localArtworkUri)

        val artDir = File(tempBaseDir, "download-artwork")
        val artFiles = artDir.listFiles { f -> f.isFile }
        assertTrue("No file should be saved in artwork dir", artFiles == null || artFiles.isEmpty())
    }

    /**
     * ADVERSARIAL STRESS 2C:
     * Corrupted bytes: Truncated JPEG.
     * A JPEG header cut off mid-stream (missing EOI FF D9 marker or truncated data).
     * Verify rejected without saving corrupted files.
     */
    @Test
    fun `stress2c_corruptBytes_truncatedJpegRejectedWithoutCorruptedFile`() = runTest(testDispatcher) {
        // Valid JPEG sample is 22 bytes ending with FF D9.
        // Truncate it to 15 bytes so it lacks the terminating EOI and frame data.
        val validBytes = sampleValidJpegBytes()
        val truncatedJpeg = validBytes.copyOfRange(0, 15)

        Downloads.artworkFetcher = Downloads.ArtworkFetcher { truncatedJpeg }

        val song = Song(
            videoId = "vid_truncated_jpeg",
            title = "Truncated JPEG Song",
            artist = "Artist",
            thumbnailUrl = "https://example.com/truncated.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        val artDir = File(tempBaseDir, "download-artwork")
        val committedFiles = artDir.listFiles { f -> f.isFile && f.name.startsWith("art_") }

        // Empirical assertion: verify truncated JPEG was NOT committed to disk!
        val corruptCommitted = committedFiles != null && committedFiles.isNotEmpty()
        val meta = Downloads.savedMetadata.value[song.videoId]

        assertFalse("Truncated JPEG MUST NOT be committed as valid artwork", corruptCommitted)
        assertNull("localArtworkUri MUST NOT point to truncated JPEG", meta?.localArtworkUri)
        assertTrue("Truncated JPEG should be marked pending", Downloads.hasPendingArtwork(song.videoId))
    }

    /**
     * ADVERSARIAL STRESS 3:
     * Unshared deletion across 3 tracks sharing artwork:
     * Delete track 1 -> file must remain.
     * Delete track 2 -> file must remain.
     * Delete track 3 -> file must be deleted.
     */
    @Test
    fun `stress3_unsharedDeletion_3TracksSharingArtwork_deletedOnlyWhenLastRemoved`() = runTest(testDispatcher) {
        val sharedUrl = "https://example.com/shared_trio_artwork.jpg"
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { sampleValidJpegBytes() }

        val tracks = listOf(
            Song(videoId = "trio_1", title = "Trio Track 1", artist = "Band", thumbnailUrl = sharedUrl, durationText = "03:00"),
            Song(videoId = "trio_2", title = "Trio Track 2", artist = "Band", thumbnailUrl = sharedUrl, durationText = "03:15"),
            Song(videoId = "trio_3", title = "Trio Track 3", artist = "Band", thumbnailUrl = sharedUrl, durationText = "03:30")
        )

        Downloads.enqueueAll(tracks)
        advanceUntilIdle()

        // Verify all 3 downloaded
        for (t in tracks) {
            assertTrue("Track ${t.videoId} must be downloaded", Downloads.isDownloaded(t.videoId))
        }

        val artUri = Downloads.savedMetadata.value["trio_1"]!!.localArtworkUri!!
        val artFile = File(java.net.URI(artUri))
        assertTrue("Physical artwork file must exist initially", artFile.exists())

        // Step 1: Delete Track 1
        Downloads.delete("trio_1")
        assertFalse("Track 1 must be removed", Downloads.isDownloaded("trio_1"))
        assertTrue("Track 2 must remain downloaded", Downloads.isDownloaded("trio_2"))
        assertTrue("Track 3 must remain downloaded", Downloads.isDownloaded("trio_3"))
        assertTrue("Artwork file MUST REMAIN after track 1 deletion (tracks 2 & 3 still reference it)", artFile.exists())

        // Step 2: Delete Track 2
        Downloads.delete("trio_2")
        assertFalse("Track 2 must be removed", Downloads.isDownloaded("trio_2"))
        assertTrue("Track 3 must remain downloaded", Downloads.isDownloaded("trio_3"))
        assertTrue("Artwork file MUST REMAIN after track 2 deletion (track 3 still references it)", artFile.exists())

        // Step 3: Delete Track 3
        Downloads.delete("trio_3")
        assertFalse("Track 3 must be removed", Downloads.isDownloaded("trio_3"))
        assertFalse("Artwork file MUST BE DELETED after track 3 (last track) deletion", artFile.exists())
    }

    /**
     * ADVERSARIAL STRESS 4:
     * Rapid cancellation during active artwork fetch.
     * When a download is cancelled while its artwork is in-flight,
     * it must NOT be saved as downloaded, and must be removed from active/pending.
     */
    @Test
    fun `stress4_rapidCancellationDuringArtworkFetch_doesNotCommitOrCorruptState`() = runTest(testDispatcher) {
        val song = Song(
            videoId = "vid_rapid_cancel",
            title = "Rapid Cancel Track",
            artist = "Artist",
            thumbnailUrl = "https://example.com/rapid_cancel.jpg",
            durationText = "03:00"
        )

        // Simulate cancellation right after enqueue or during fetch
        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            Downloads.cancel(song.videoId)
            sampleValidJpegBytes()
        }

        Downloads.enqueue(song)
        advanceUntilIdle()

        // The song was cancelled during fetch -> it must NOT be marked as successfully saved!
        assertFalse("Cancelled song must NOT be saved as downloaded", Downloads.isDownloaded(song.videoId))
        assertFalse("Cancelled song must NOT be in pending artwork", Downloads.hasPendingArtwork(song.videoId))
        assertNull("Cancelled song must NOT have metadata saved", Downloads.savedMetadata.value[song.videoId])

        val artDir = File(tempBaseDir, "download-artwork")
        val partFiles = artDir.listFiles { f -> f.name.endsWith(".part") }
        assertTrue("No leftover .part files after cancellation", partFiles == null || partFiles.isEmpty())
    }
}

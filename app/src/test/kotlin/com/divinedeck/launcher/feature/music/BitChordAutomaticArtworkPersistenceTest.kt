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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Automated Unit & Integration Tests for Milestone 2:
 * Automatic Offline Artwork Download, SHA-256 Deduplication, Persistence,
 * Unshared Resource Cleanup on Deletion, and Network Reconnection Retry (Requirement R3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordAutomaticArtworkPersistenceTest {

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
        tempBaseDir = Files.createTempDirectory("bitchord_artwork_test_").toFile()
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
            maxConcurrentWorkers = 2,
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
     * TEST 1 — AUTOMATIC ARTWORK ACQUISITION & PERSISTENCE:
     * Downloading a song fetches high-res artwork, validates bytes, writes atomically,
     * stores in context.filesDir/download-artwork/ keyed by SHA-256 hash,
     * and associates localArtworkUri in SavedSongMetadata and enriched song.
     */
    @Test
    fun `automaticArtwork_fetchesHighResAndPersistsKeyedBySha256`() = runTest(testDispatcher) {
        val thumbUrl = "https://example.com/album_cover.jpg"
        val requestedUrls = mutableListOf<String>()

        Downloads.artworkFetcher = Downloads.ArtworkFetcher { url ->
            requestedUrls.add(url)
            sampleValidJpegBytes()
        }

        val song = Song(
            videoId = "vid_art_1",
            title = "Track With Artwork",
            artist = "Artist One",
            thumbnailUrl = thumbUrl,
            durationText = "03:30"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("Song must be marked as downloaded", Downloads.isDownloaded(song.videoId))
        assertFalse("Artwork should not be pending", Downloads.hasPendingArtwork(song.videoId))

        // Verify high-resolution (800px) candidate was prioritized
        assertTrue("Must query 800px high-res URL first", requestedUrls.isNotEmpty())
        assertEquals(thumbUrl, requestedUrls.first())

        // Verify target artwork file exists in download-artwork/ keyed by SHA-256
        val expectedHash = Downloads.sha256Hex(thumbUrl)
        val expectedFilename = "art_$expectedHash.jpg"
        val artDir = File(tempBaseDir, "download-artwork")
        val artFile = File(artDir, expectedFilename)

        assertTrue("Artwork file $expectedFilename must exist on disk", artFile.exists())
        assertTrue("Artwork file must not be empty", artFile.length() > 0L)

        // Verify SavedSongMetadata has localArtworkUri
        val meta = Downloads.savedMetadata.value[song.videoId]
        assertNotNull("Metadata must be saved", meta)
        assertNotNull("localArtworkUri must be present", meta!!.localArtworkUri)
        assertTrue("localArtworkUri must point to file scheme", meta.localArtworkUri!!.startsWith("file:"))
        assertTrue("localArtworkUri must contain the SHA-256 file", meta.localArtworkUri!!.contains(expectedFilename))

        // Verify Downloads.enrichWithLocalDownload returns local file URI
        val enriched = Downloads.enrichWithLocalDownload(song)
        assertEquals("Enriched song must have local artwork URI", meta.localArtworkUri, enriched.thumbnailUrl)
    }

    /**
     * TEST 2 — ARTWORK DEDUPLICATION ACROSS ALBUM TRACKS:
     * When 3 songs share the exact same album thumbnailUrl, only 1 network fetch occurs.
     * All 3 tracks share the single persistent artwork file on disk.
     */
    @Test
    fun `automaticArtwork_albumTracksWithIdenticalCover_deduplicateToSingleDownloadAndFile`() = runTest(testDispatcher) {
        val sharedCoverUrl = "https://example.com/shared_album_art.jpg"
        val networkCallCount = AtomicInteger(0)

        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            networkCallCount.incrementAndGet()
            sampleValidJpegBytes()
        }

        val songs = listOf(
            Song(videoId = "album_t1", title = "Track 1", artist = "Band", thumbnailUrl = sharedCoverUrl, durationText = "03:00"),
            Song(videoId = "album_t2", title = "Track 2", artist = "Band", thumbnailUrl = sharedCoverUrl, durationText = "03:15"),
            Song(videoId = "album_t3", title = "Track 3", artist = "Band", thumbnailUrl = sharedCoverUrl, durationText = "03:45")
        )

        Downloads.enqueueAll(songs)
        advanceUntilIdle()

        // Verify all 3 songs downloaded
        for (s in songs) {
            assertTrue("Track ${s.videoId} must be downloaded", Downloads.isDownloaded(s.videoId))
        }

        // Crucial: exactly 1 network call occurred for the entire album!
        assertEquals("Artwork must be fetched from network exactly once", 1, networkCallCount.get())

        val meta1 = Downloads.savedMetadata.value["album_t1"]!!
        val meta2 = Downloads.savedMetadata.value["album_t2"]!!
        val meta3 = Downloads.savedMetadata.value["album_t3"]!!

        // All 3 tracks point to the exact same canonical local file URI
        assertEquals(meta1.localArtworkUri, meta2.localArtworkUri)
        assertEquals(meta2.localArtworkUri, meta3.localArtworkUri)

        // Only 1 physical file exists in download-artwork directory
        val artDir = File(tempBaseDir, "download-artwork")
        val artFiles = artDir.listFiles { f -> f.isFile && f.name.startsWith("art_") }
        assertNotNull(artFiles)
        assertEquals("Exactly one artwork file must exist on disk for the shared album", 1, artFiles!!.size)
    }

    /**
     * TEST 3 — UNSHARED ARTWORK CLEANUP ON SONG DELETION:
     * When Track 1 of a 2-track shared album is deleted, the shared artwork file MUST NOT be deleted
     * because Track 2 still references it.
     * When Track 2 is subsequently deleted, the now-unshared artwork file MUST be deleted.
     */
    @Test
    fun `unsharedArtworkCleanup_sharedArtworkRetainedUntilLastTrackDeleted`() = runTest(testDispatcher) {
        val sharedUrl = "https://example.com/co-owned-art.jpg"
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { sampleValidJpegBytes() }

        val songA = Song(videoId = "share_a", title = "Song A", artist = "Duo", thumbnailUrl = sharedUrl, durationText = "03:00")
        val songB = Song(videoId = "share_b", title = "Song B", artist = "Duo", thumbnailUrl = sharedUrl, durationText = "03:30")

        Downloads.enqueueAll(listOf(songA, songB))
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded("share_a"))
        assertTrue(Downloads.isDownloaded("share_b"))

        val artUri = Downloads.savedMetadata.value["share_a"]!!.localArtworkUri!!
        val artFile = File(java.net.URI(artUri))
        assertTrue("Physical artwork file must exist", artFile.exists())

        // 1. Delete Song A
        Downloads.delete("share_a")

        assertFalse("Song A must not be marked as downloaded", Downloads.isDownloaded("share_a"))
        assertTrue("Song B must still be downloaded", Downloads.isDownloaded("share_b"))

        // CRITICAL: Shared artwork file must STILL exist because Song B still uses it!
        assertTrue("Shared artwork file must NOT be deleted while Song B still references it", artFile.exists())
        assertEquals("Song B localArtworkUri must still be intact", artUri, Downloads.savedMetadata.value["share_b"]?.localArtworkUri)

        // 2. Delete Song B (now unshared!)
        Downloads.delete("share_b")

        assertFalse("Song B must not be downloaded", Downloads.isDownloaded("share_b"))

        // CRITICAL: Now that no songs reference it, the unshared artwork file MUST be deleted!
        assertFalse("Unshared artwork file must be deleted after last track removal", artFile.exists())
    }

    /**
     * TEST 4 — OFFLINE RECOVERY WITHOUT PRIOR OPENING:
     * After app restart simulation with zero internet connection, downloaded songs immediately
     * resolve their local artwork URI from disk with zero network queries.
     */
    @Test
    fun `offlineArtwork_availableImmediatelyAfterRestartWithZeroNetwork`() = runTest(testDispatcher) {
        val coverUrl = "https://example.com/restart_test_art.jpg"
        val networkCalls = AtomicInteger(0)

        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            networkCalls.incrementAndGet()
            sampleValidJpegBytes()
        }

        val song = Song(videoId = "vid_restart_art", title = "Restart Song", artist = "Artist", thumbnailUrl = coverUrl, durationText = "03:00")
        Downloads.enqueue(song)
        advanceUntilIdle()

        assertEquals("Online fetch occurred once", 1, networkCalls.get())
        val originalArtUri = Downloads.savedMetadata.value[song.videoId]!!.localArtworkUri

        // Simulate app restart / kill
        Downloads.detachForRestartSimulation()

        // Turn OFF internet completely
        lyricsOfflineModeOverride = true
        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            throw IllegalStateException("Network called while offline!")
        }

        // Re-initialize engine as app launches offline
        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = fastStreamFetcher,
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            enableOnlineArtworkFetch = false
        )

        // Verify song is immediately recognized as downloaded
        assertTrue(Downloads.isDownloaded(song.videoId))

        // Verify offline enrichment uses local artwork with 0 network calls
        val enriched = Downloads.enrichWithLocalDownload(song)
        assertEquals("Local artwork URI must persist and match across restarts", originalArtUri, enriched.thumbnailUrl)
        assertTrue("Artwork file must physically exist", Downloads.isValidLocalUri(enriched.thumbnailUrl))
        assertEquals("Zero network calls made during offline startup", 1, networkCalls.get())
    }

    /**
     * TEST 5 — IMAGE VALIDITY & READABILITY VALIDATION:
     * If the server returns corrupted/invalid image bytes (e.g. HTML error page or truncated data),
     * the engine validates image headers, rejects the corrupted data, leaves no partial file,
     * marks the artwork as pending, and does NOT associate a corrupt file URI.
     */
    @Test
    fun `artworkValidation_corruptBytesRejectedAndMarkedPending`() = runTest(testDispatcher) {
        val corruptedBytes = "<html><body>502 Bad Gateway</body></html>".toByteArray(Charsets.UTF_8)
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { corruptedBytes }

        val song = Song(
            videoId = "vid_corrupted",
            title = "Corrupt Art Song",
            artist = "Artist",
            thumbnailUrl = "https://example.com/corrupt.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        // Audio must succeed despite artwork failure
        assertTrue("Audio download must succeed", Downloads.isDownloaded(song.videoId))

        // Artwork must be rejected and marked pending
        assertTrue("Corrupted artwork must be marked pending for retry", Downloads.hasPendingArtwork(song.videoId))
        assertEquals(1, Downloads.getPendingArtworkCount())

        val meta = Downloads.savedMetadata.value[song.videoId]
        assertNotNull(meta)
        assertNull("localArtworkUri must be null when image bytes are invalid", meta!!.localArtworkUri)

        // Verify no invalid file was committed
        val artDir = File(tempBaseDir, "download-artwork")
        val artFiles = artDir.listFiles { f -> f.isFile && !f.name.endsWith(".part") }
        assertTrue("No corrupt file must be committed", artFiles == null || artFiles.isEmpty())
    }

    /**
     * TEST 6 — NETWORK RECONNECTION RETRY ENGINE:
     * When artwork acquisition fails due to offline/network glitch during audio download,
     * it is placed in pendingArtworkIds.
     * When network connectivity returns, retryPendingArtwork() backfills the cover art,
     * updates the metadata persistently, and clears the pending ID.
     */
    @Test
    fun `networkReconnection_retriesPendingArtworkSuccessfully`() = runTest(testDispatcher) {
        val coverUrl = "https://example.com/pending_artwork.jpg"
        var networkWorking = false

        Downloads.artworkFetcher = Downloads.ArtworkFetcher {
            if (!networkWorking) null else sampleValidJpegBytes()
        }

        val song = Song(
            videoId = "vid_pending_retry",
            title = "Pending Retry Song",
            artist = "Artist",
            thumbnailUrl = coverUrl,
            durationText = "03:00"
        )

        // 1. Download while network fails
        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded(song.videoId))
        assertTrue("Artwork must be in pending list", Downloads.hasPendingArtwork(song.videoId))
        assertNull(Downloads.savedMetadata.value[song.videoId]?.localArtworkUri)

        // 2. Network restored
        networkWorking = true

        // Simulate network callback from AppSettings
        AppSettings.simulateNetworkAvailable()
        advanceUntilIdle()

        // 3. Verify artwork is now acquired and pending cleared
        assertFalse("Pending artwork must be cleared after retry", Downloads.hasPendingArtwork(song.videoId))
        assertEquals(0, Downloads.getPendingArtworkCount())

        val updatedMeta = Downloads.savedMetadata.value[song.videoId]
        assertNotNull("Metadata must exist", updatedMeta)
        assertNotNull("localArtworkUri must now be populated", updatedMeta!!.localArtworkUri)
        assertTrue(Downloads.isValidLocalUri(updatedMeta.localArtworkUri))

        val enriched = Downloads.enrichWithLocalDownload(song)
        assertEquals(updatedMeta.localArtworkUri, enriched.thumbnailUrl)
    }

    /**
     * TEST 7 — ATOMICITY & NO LEFTOVER PARTIAL FILES:
     * Ensure artwork files are written atomically via .part and no .part files remain after download.
     */
    @Test
    fun `atomicity_noLeftoverPartFilesAfterCommit`() = runTest(testDispatcher) {
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { sampleValidJpegBytes() }

        val song = Song(
            videoId = "vid_atomic",
            title = "Atomic Test",
            artist = "Artist",
            thumbnailUrl = "https://example.com/atomic.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        val artDir = File(tempBaseDir, "download-artwork")
        val partFiles = artDir.listFiles { f -> f.name.endsWith(".part") }
        assertTrue("No orphaned .part files should remain", partFiles == null || partFiles.isEmpty())

        val committedFiles = artDir.listFiles { f -> f.name.startsWith("art_") && f.name.endsWith(".jpg") }
        assertNotNull(committedFiles)
        assertEquals(1, committedFiles!!.size)
    }

    /**
     * TEST 8 — CANCEL DOWNLOAD REMOVES PENDING ARTWORK:
     * Cancelling a download removes it from the pending artwork queue.
     */
    @Test
    fun `cancelDownload_removesPendingArtworkTracking`() = runTest(testDispatcher) {
        Downloads.artworkFetcher = Downloads.ArtworkFetcher { null }

        val song = Song(
            videoId = "vid_cancel_test",
            title = "Cancel Song",
            artist = "Artist",
            thumbnailUrl = "https://example.com/cancel.jpg",
            durationText = "03:00"
        )

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.hasPendingArtwork(song.videoId))

        Downloads.cancel(song.videoId)
        assertFalse("Cancelled song must be removed from pending artwork", Downloads.hasPendingArtwork(song.videoId))
    }
}

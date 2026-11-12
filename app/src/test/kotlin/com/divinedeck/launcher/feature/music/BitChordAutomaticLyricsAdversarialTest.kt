package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.lyrics.EmbeddedLyrics
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.LyricsStore
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.lyrics.artistForLyricsSearch
import com.music.bitchord.data.lyrics.forLyricsSearch
import com.music.bitchord.data.lyrics.lyricsNetworkCallCount
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.data.lyrics.resetLyricsNetworkStats
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 2:
 * Automatic Offline Lyrics Persistence, Fallback Hierarchy, Version Label Matching, and Reconnection Retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordAutomaticLyricsAdversarialTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File
    private lateinit var mockPrefs: InMemoryPrefsHelper
    private lateinit var testContext: Context

    private class InMemoryPrefsHelper(
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
        override fun edit(): SharedPreferences.Editor = EditorHelper(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class EditorHelper(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
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

    private class TestStorageContextHelper(
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
            val audioBytes = ByteArray(1024) { 0x33 }
            openSink(stream).use { out ->
                out.write(audioBytes)
                onProgress(audioBytes.size.toLong(), audioBytes.size.toLong())
            }
            return stream
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempBaseDir = Files.createTempDirectory("bitchord_lyrics_adversarial_").toFile()
        mockPrefs = InMemoryPrefsHelper()
        testContext = TestStorageContextHelper(mockPrefs, tempBaseDir)

        LyricsStore.customStorageDir = File(tempBaseDir, "lyrics_store")
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        LyricsRepository.lyricsDispatcher = testDispatcher
        EmbeddedLyrics.ioDispatcher = testDispatcher
        lyricsOfflineModeOverride = false
        resetLyricsNetworkStats()

        AppSettings.init(testContext)

        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = fastStreamFetcher,
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            maxConcurrentWorkers = 3,
            enableOnlineArtworkFetch = false
        )
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.DefaultProviderFetcher
        LyricsRepository.lyricsDispatcher = Dispatchers.IO
        resetLyricsNetworkStats()
        lyricsOfflineModeOverride = null
        EmbeddedLyrics.ioDispatcher = Dispatchers.IO
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    private fun sampleWordSyncLyrics(title: String): List<LyricLine> {
        return listOf(
            LyricLine(
                text = "Verse one of $title",
                timeMs = 1200L,
                words = listOf(
                    LyricWord(text = "Verse", startMs = 1200L, endMs = 1500L),
                    LyricWord(text = "one", startMs = 1500L, endMs = 1800L),
                    LyricWord(text = "of", startMs = 1800L, endMs = 2000L),
                    LyricWord(text = title, startMs = 2000L, endMs = 2700L)
                )
            ),
            LyricLine(
                text = "Chorus line of $title",
                timeMs = 3500L,
                words = listOf(
                    LyricWord(text = "Chorus", startMs = 3500L, endMs = 4000L),
                    LyricWord(text = "line", startMs = 4000L, endMs = 4500L)
                )
            )
        )
    }

    private fun sampleLineSyncLyrics(title: String): List<LyricLine> {
        return listOf(
            LyricLine(text = "Line sync verse of $title", timeMs = 1500L),
            LyricLine(text = "Line sync chorus of $title", timeMs = 4000L)
        )
    }

    private fun samplePlainLyrics(title: String): List<LyricLine> {
        return listOf(
            LyricLine(text = "Unsynchronized plain lyrics 1 for $title", timeMs = 0L),
            LyricLine(text = "Unsynchronized plain lyrics 2 for $title", timeMs = 0L)
        )
    }

    // =========================================================================
    // 1. OFFLINE RESOLUTION ADVERSARIAL STRESS TESTS
    // =========================================================================

    /**
     * STRESS 1.1: Offline resolution from local .lrc sidecar with 0 network calls.
     */
    @Test
    fun `stress1a_offlineResolution_fromSidecarLrc_zeroNetworkRequests`() = runTest(testDispatcher) {
        val song = Song(videoId = "vid_sidecar_offline", title = "Sidecar Offline Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            sampleWordSyncLyrics(song.title)
        }

        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue("Song must be downloaded", Downloads.isDownloaded(song.videoId))
        val audioUri = Downloads.verifiedSavedUriString(song.videoId)!!
        val audioFile = File(java.net.URI(audioUri))
        val sidecarFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        assertTrue("Sidecar .lrc must exist", sidecarFile.exists())

        // Clear memory cache and delete from LyricsStore to force resolution STRICTLY from .lrc sidecar!
        LyricsRepository.clearMemoryCache()
        LyricsStore.delete(null, song.videoId)
        assertFalse("LyricsStore must NOT have lyrics for this test", LyricsStore.has(null, song.videoId))

        // Set total offline mode
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        val offlineState = LyricsRepository.resolveForSong(song, testContext)
        assertTrue("Must resolve as EmbeddedLocal or Synced", offlineState is LyricsUiState.EmbeddedLocal || offlineState is LyricsUiState.Synced)

        val resolvedLines = when (offlineState) {
            is LyricsUiState.EmbeddedLocal -> offlineState.lines
            is LyricsUiState.Synced -> offlineState.lines
            else -> emptyList()
        }
        assertTrue("Must resolve lines from sidecar", resolvedLines.isNotEmpty())
        assertTrue(resolvedLines[0].text.contains(song.title))
        assertEquals("Strictly zero network requests while offline", 0, lyricsNetworkCallCount.get())
    }

    /**
     * STRESS 1.2: Missing sidecar .lrc is recovered from LyricsStore and re-persisted automatically.
     */
    @Test
    fun `stress1b_offlineResolution_missingSidecarRecoveredFromLyricsStoreAndRepersisted`() = runTest(testDispatcher) {
        val song = Song(videoId = "vid_store_recover", title = "Store Recovery Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            sampleWordSyncLyrics(song.title)
        }

        Downloads.enqueue(song)
        advanceUntilIdle()

        val audioUri = Downloads.verifiedSavedUriString(song.videoId)!!
        val audioFile = File(java.net.URI(audioUri))
        val sidecarFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        assertTrue(sidecarFile.exists())

        // Deliberately destroy the .lrc sidecar file
        assertTrue("Sidecar file deleted", sidecarFile.delete())
        assertFalse("Sidecar file should be gone", sidecarFile.exists())

        // Verify LyricsStore still has it
        assertTrue("LyricsStore has the lyrics", LyricsStore.has(null, song.videoId))

        // Clear memory cache and go completely offline
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        val offlineState = LyricsRepository.resolveForSong(song, testContext)
        assertTrue("Must resolve from LyricsStore", offlineState is LyricsUiState.EmbeddedLocal)
        val localState = offlineState as LyricsUiState.EmbeddedLocal
        assertEquals("Caché Offline", localState.containerOrFormat)

        // Verify sidecar was automatically re-created and healed on disk!
        assertTrue("Sidecar .lrc must be self-healed on disk", sidecarFile.exists())
        assertTrue("Sidecar .lrc must contain text", sidecarFile.length() > 0L)
        assertEquals("Strictly zero network requests", 0, lyricsNetworkCallCount.get())
    }

    /**
     * STRESS 1.3: Total offline state with no lyrics cached returns OfflineUnavailable without crashing or querying network.
     */
    @Test
    fun `stress1c_offlineResolution_neitherSidecarNorStore_returnsOfflineUnavailable`() = runTest(testDispatcher) {
        resetLyricsNetworkStats()
        lyricsOfflineModeOverride = true

        val nonCachedSong = Song(videoId = "vid_never_cached", title = "Never Cached", artist = "Ghost Artist", thumbnailUrl = null, durationText = "03:00")

        var networkAttempted = false
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, _, _, _, _, _, _, _ ->
            networkAttempted = true
            samplePlainLyrics(nonCachedSong.title)
        }

        val state = LyricsRepository.resolveForSong(nonCachedSong, testContext)
        assertTrue("Must return OfflineUnavailable", state is LyricsUiState.OfflineUnavailable)
        assertEquals("vid_never_cached", (state as LyricsUiState.OfflineUnavailable).videoId)
        assertFalse("No network provider must ever be contacted while offline", networkAttempted)
        assertEquals("Zero network calls recorded", 0, lyricsNetworkCallCount.get())
    }

    /**
     * STRESS 1.4: Concurrent resolution of multiple downloaded songs while offline.
     */
    @Test
    fun `stress1d_offlineResolution_concurrentResolutionOfTenSongsOffline`() = runTest(testDispatcher) {
        val count = 10
        val songs = (1..count).map { i ->
            Song(videoId = "vid_stress_$i", title = "Song Stress Number $i", artist = "Batch Artist", thumbnailUrl = null, durationText = "03:00")
        }

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, _, title, _, _, _, _, _, _ ->
            sampleWordSyncLyrics(title)
        }

        Downloads.enqueueAll(songs)
        advanceUntilIdle()

        for (s in songs) {
            assertTrue("Song ${s.videoId} must be downloaded", Downloads.isDownloaded(s.videoId))
        }

        // Drop memory cache and go completely offline
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        // Concurrently resolve all 10 songs in parallel
        val resolvedStates = songs.map { song ->
            async {
                LyricsRepository.resolveForSong(song, testContext)
            }
        }.awaitAll()

        assertEquals(count, resolvedStates.size)
        for (i in 0 until count) {
            val state = resolvedStates[i]
            assertTrue("Song $i must resolve offline", state is LyricsUiState.EmbeddedLocal || state is LyricsUiState.Synced)
        }
        assertEquals("Zero network calls across all concurrent offline queries", 0, lyricsNetworkCallCount.get())
    }

    // =========================================================================
    // 2. VERSION MATCHING & QUERY SANITIZATION TESTS
    // =========================================================================

    /**
     * STRESS 2.1: Version markers (Remix, Live, Acoustic, etc.) must NEVER be stripped.
     */
    @Test
    fun `stress2a_versionMatching_preservesAllVersionLabels`() {
        val testCases = mapOf(
            "Levitating (Remix)" to "Levitating (Remix)",
            "Hotel California (Live)" to "Hotel California (Live)",
            "Hotel California (Live at the Forum)" to "Hotel California (Live at the Forum)",
            "Everlong (Acoustic)" to "Everlong (Acoustic)",
            "Everlong (Acoustic Version)" to "Everlong (Acoustic Version)",
            "Wonderwall [Acoustic]" to "Wonderwall [Acoustic]",
            "Song (Instrumental)" to "Song (Instrumental)",
            "Dance Track (Extended Mix)" to "Dance Track (Extended Mix)",
            "Pop Hit (Radio Edit)" to "Pop Hit (Radio Edit)",
            "Special Track (Deluxe Edition)" to "Special Track (Deluxe Edition)",
            "DJ Track (Club Mix)" to "DJ Track (Club Mix)"
        )

        for ((input, expected) in testCases) {
            assertEquals("Version marker in '$input' must be preserved", expected, input.forLyricsSearch())
        }
    }

    /**
     * STRESS 2.2: Video upload artifacts and bracketed credits are stripped while preserving version labels.
     */
    @Test
    fun `stress2b_versionMatching_stripsVideoArtifactsAndPreservesCompoundVersions`() {
        val testCases = mapOf(
            "Levitating (Remix) (Official Music Video)" to "Levitating (Remix)",
            "Hotel California (Live) [Official Video]" to "Hotel California (Live)",
            "Blinding Lights (Official Video) (feat. Rosalía)" to "Blinding Lights",
            "Midnight City [HD] [4K]" to "Midnight City",
            "Song Title (feat. Big Artist) (Live at Wembley) [Lyric Video]" to "Song Title (Live at Wembley)",
            "Acoustic Session (Acoustic Version) (with Guest) [Visualizer]" to "Acoustic Session (Acoustic Version)",
            "Track (Official) (ft. Someone)" to "Track",
            "Single (full song)" to "Single",
            "Clip (m/v)" to "Clip",
            "Another Clip (mv)" to "Another Clip"
        )

        for ((input, expected) in testCases) {
            assertEquals("Compound title '$input' must sanitize correctly", expected, input.forLyricsSearch())
        }
    }

    /**
     * STRESS 2.3: Artist sanitization strips ' - Topic' suffix.
     */
    @Test
    fun `stress2c_artistSanitization_stripsTopicSuffix`() {
        assertEquals("Queen", "Queen - Topic".artistForLyricsSearch())
        assertEquals("Pink Floyd", "Pink Floyd - Topic".artistForLyricsSearch())
        assertEquals("Dua Lipa", "Dua Lipa".artistForLyricsSearch())
    }

    // =========================================================================
    // 3. FALLBACK HIERARCHY TESTS (WORD-SYNC -> LINE-SYNC -> PLAIN)
    // =========================================================================

    /**
     * STRESS 3.1: Complete hierarchy race: Word-sync is strictly preferred over line-sync and plain.
     */
    @Test
    fun `stress3a_fallbackHierarchy_wordSyncPreferredOverLineSyncAndPlain`() = runTest(testDispatcher) {
        val song1 = Song(videoId = "vid_fallback_hier_word", title = "Hierarchy Song Word", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val wordLines1 = sampleWordSyncLyrics(song1.title)
        val lineLines1 = sampleLineSyncLyrics(song1.title)
        val plainLines1 = samplePlainLyrics(song1.title)

        // Scenario 1: All three tiers available -> Word-sync chosen
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> wordLines1
                LyricsSource.LRCLIB -> lineLines1
                LyricsSource.GENIUS -> plainLines1
                else -> null
            }
        }

        val state1 = LyricsRepository.resolveForSong(song1, testContext)
        assertTrue(state1 is LyricsUiState.Synced)
        assertTrue("Must be word-synced", (state1 as LyricsUiState.Synced).isWordSynced)

        // Scenario 2: Word-sync absent, line-sync and plain present -> Line-sync chosen
        val song2 = Song(videoId = "vid_fallback_hier_line", title = "Hierarchy Song Line", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val lineLines2 = sampleLineSyncLyrics(song2.title)
        val plainLines2 = samplePlainLyrics(song2.title)
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> null
                LyricsSource.LRCLIB -> lineLines2
                LyricsSource.GENIUS -> plainLines2
                else -> null
            }
        }

        val state2 = LyricsRepository.resolveForSong(song2, testContext)
        assertTrue(state2 is LyricsUiState.Synced)
        assertFalse("Must NOT be word-synced", (state2 as LyricsUiState.Synced).isWordSynced)

        // Scenario 3: Only plain lyrics available -> Plain chosen
        val song3 = Song(videoId = "vid_fallback_hier_plain", title = "Hierarchy Song Plain", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val plainLines3 = samplePlainLyrics(song3.title)
        LyricsRepository.clearMemoryCache()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.GENIUS -> plainLines3
                else -> null
            }
        }

        val state3 = LyricsRepository.resolveForSong(song3, testContext)
        assertTrue(state3 is LyricsUiState.Plain)
    }

    /**
     * STRESS 3.2: Provider exception tolerance: if a higher-priority provider throws, fallback continues smoothly.
     */
    @Test
    fun `stress3b_fallbackHierarchy_providerExceptionToleratedWithSmoothFallback`() = runTest(testDispatcher) {
        val song = Song(videoId = "vid_prov_error", title = "Provider Error Song", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val lineLines = sampleLineSyncLyrics(song.title)

        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { source, _, _, _, _, _, _, _, _ ->
            when (source) {
                LyricsSource.LRC_RED -> throw IOException("503 Service Unavailable")
                LyricsSource.BETTER_LYRICS -> throw RuntimeException("SocketTimeoutException")
                LyricsSource.LRCLIB -> lineLines
                else -> null
            }
        }

        val state = LyricsRepository.resolveForSong(song, testContext)
        assertTrue("Must smoothly recover via LRCLIB despite earlier provider crashes", state is LyricsUiState.Synced)
        assertEquals(lineLines.size, (state as LyricsUiState.Synced).lines.size)
    }

    // =========================================================================
    // 4. RECONNECTION RETRY TESTS (simulateNetworkAvailable)
    // =========================================================================

    /**
     * STRESS 4.1: Offline downloaded songs become pending, and simulateNetworkAvailable backfills lyrics for all.
     */
    @Test
    fun `stress4a_reconnectionRetry_simulateNetworkAvailableFetchesAllPendingLyrics`() = runTest(testDispatcher) {
        lyricsOfflineModeOverride = true // Offline at start

        val songs = listOf(
            Song(videoId = "vid_retry_1", title = "Retry Song 1", artist = "Artist", thumbnailUrl = null, durationText = "03:00"),
            Song(videoId = "vid_retry_2", title = "Retry Song 2", artist = "Artist", thumbnailUrl = null, durationText = "03:15"),
            Song(videoId = "vid_retry_3", title = "Retry Song 3", artist = "Artist", thumbnailUrl = null, durationText = "02:45")
        )

        Downloads.enqueueAll(songs)
        advanceUntilIdle()

        // 1. Audio downloaded, but lyrics are pending for all 3
        for (song in songs) {
            assertTrue("Audio for ${song.videoId} must be downloaded", Downloads.isDownloaded(song.videoId))
            assertTrue("Lyrics for ${song.videoId} must be pending", Downloads.hasPendingLyrics(song.videoId))
            assertFalse("LyricsStore must not have lyrics yet", LyricsStore.has(null, song.videoId))
        }
        assertEquals(3, Downloads.getPendingLyricsCount())

        // 2. Prepare provider response for when network returns
        val fetchedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, videoId, title, _, _, _, _, _, _ ->
            fetchedIds.add(videoId)
            sampleWordSyncLyrics(title)
        }

        // 3. Reconnection event: simulate network becoming available
        lyricsOfflineModeOverride = false
        AppSettings.simulateNetworkAvailable()
        advanceUntilIdle()

        // 4. Verify all pending IDs were fetched
        assertEquals("All 3 tracks must have been queried", 3, fetchedIds.size)
        assertEquals("Pending lyrics count must be zero", 0, Downloads.getPendingLyricsCount())

        for (song in songs) {
            assertFalse("Track ${song.videoId} must no longer be pending", Downloads.hasPendingLyrics(song.videoId))
            assertTrue("LyricsStore must now contain lyrics for ${song.videoId}", LyricsStore.has(null, song.videoId))

            val savedUri = Downloads.verifiedSavedUriString(song.videoId)!!
            val audioFile = File(java.net.URI(savedUri))
            val sidecar = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            assertTrue("Sidecar .lrc file must exist on disk for ${song.videoId}", sidecar.exists())
            assertTrue("Sidecar .lrc must not be empty", sidecar.length() > 0L)
        }

        // 5. Subsequent total offline test: all 3 tracks resolve immediately with 0 network calls!
        LyricsRepository.clearMemoryCache()
        lyricsOfflineModeOverride = true
        resetLyricsNetworkStats()

        for (song in songs) {
            val offlineState = LyricsRepository.resolveForSong(song, testContext)
            assertTrue(offlineState is LyricsUiState.EmbeddedLocal || offlineState is LyricsUiState.Synced)
        }
        assertEquals("Zero network calls when resolving backfilled songs offline", 0, lyricsNetworkCallCount.get())
    }

    /**
     * STRESS 4.2: Song deleted while pending lyrics does not trigger unwanted fetch on reconnect.
     */
    @Test
    fun `stress4b_reconnectionRetry_deletedPendingSongCleanedUpWithoutFetch`() = runTest(testDispatcher) {
        lyricsOfflineModeOverride = true

        val songToKeep = Song(videoId = "vid_keep", title = "Keep Track", artist = "Artist", thumbnailUrl = null, durationText = "03:00")
        val songToDelete = Song(videoId = "vid_delete_pending", title = "Delete Pending Track", artist = "Artist", thumbnailUrl = null, durationText = "03:00")

        Downloads.enqueueAll(listOf(songToKeep, songToDelete))
        advanceUntilIdle()

        assertTrue(Downloads.hasPendingLyrics("vid_keep"))
        assertTrue(Downloads.hasPendingLyrics("vid_delete_pending"))
        assertEquals(2, Downloads.getPendingLyricsCount())

        // Delete one of the pending songs
        Downloads.delete("vid_delete_pending")
        assertFalse("Deleted song must be removed from pending lyrics", Downloads.hasPendingLyrics("vid_delete_pending"))
        assertEquals(1, Downloads.getPendingLyricsCount())

        val fetchedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        LyricsRepository.providerFetcher = LyricsRepository.ProviderFetcher { _, videoId, title, _, _, _, _, _, _ ->
            fetchedIds.add(videoId)
            sampleWordSyncLyrics(title)
        }

        lyricsOfflineModeOverride = false
        AppSettings.simulateNetworkAvailable()
        advanceUntilIdle()

        assertEquals("Only the non-deleted track must be fetched", 1, fetchedIds.size)
        assertTrue("Only vid_keep should be fetched", fetchedIds.contains("vid_keep"))
        assertFalse("vid_delete_pending must NOT be fetched", fetchedIds.contains("vid_delete_pending"))
    }
}

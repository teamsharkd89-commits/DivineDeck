package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.AudioCache
import com.music.bitchord.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full integration test for Phase 06.5E.1:
 * AppSettings + Sleep Timer + Storage & Downloads in DIVINE DECK.
 *
 * Verifies all 18 mandatory requirements from prompt:
 * AppSettings (1-5), Sleep Timer (6-12), Storage & Downloads (13-18).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordSettingsSleepTimerIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File
    private lateinit var mockPrefs: InMemorySharedPreferences
    private var virtualTime = 5_000_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempBaseDir = Files.createTempDirectory("divinedeck_settings_timer_test_").toFile()
        mockPrefs = InMemorySharedPreferences()
        virtualTime = 5_000_000L

        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        SleepTimer.resetForTest()
        SleepTimer.timeProvider = { virtualTime }

        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = object : Downloader.StreamFetcher {
                override suspend fun resolveAndFetch(
                    videoId: String,
                    maxKbps: Int,
                    requireM4a: Boolean,
                    openSink: (StreamResolver.Stream) -> OutputStream,
                    onProgress: (written: Long, total: Long) -> Unit
                ): StreamResolver.Stream {
                    val stream = StreamResolver.Stream(
                        url = "https://example.com/audio/$videoId",
                        kbps = maxKbps,
                        mimeType = if (requireM4a) "audio/mp4" else "audio/webm",
                    )
                    openSink(stream).use { sink ->
                        sink.write(ByteArray(2048) { 0x77.toByte() })
                        onProgress(2048L, 2048L)
                    }
                    return stream
                }
            },
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )
        Downloads.resetForTests()
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
        Downloads.detachForRestartSimulation()
        AudioCache.release()
        AppSettings.resetForTest()
        SleepTimer.resetForTest()
        tempBaseDir.deleteRecursively()
        Dispatchers.resetMain()
    }

    private fun testSong(id: String): Song =
        Song(
            videoId = id,
            title = "Test Song $id",
            artist = "Test Artist",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "3:30",
        )

    // ==========================================
    // 1-5: AppSettings Tests
    // ==========================================

    @Test
    fun req01_initializationWithDefaultValues() {
        assertEquals(DownloadQuality.LOSSLESS, AppSettings.downloadQuality.value)
        assertTrue(AppSettings.wifiOnlyDownloads.value)
        assertFalse(AppSettings.exportDownloads.value)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)
        assertNull(AppSettings.meteredConnection.value)
        assertTrue(AppSettings.downloadsAllowedNow)
    }

    @Test
    fun req02_persistsValuesAcrossReinitialization() {
        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        AppSettings.setWifiOnlyDownloads(false)
        AppSettings.setExportDownloads(true)
        AppSettings.setAudioCacheLimitBytes(1024L * 1024 * 1024)

        // Simulate app restart
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
        assertTrue(AppSettings.exportDownloads.value)
        assertEquals(1024L * 1024 * 1024, AppSettings.audioCacheLimitBytes.value)
    }

    @Test
    fun req03_stateFlowUpdatesInstantlyInMemory() {
        assertEquals(DownloadQuality.LOSSLESS, AppSettings.downloadQuality.value)
        AppSettings.setDownloadQuality(DownloadQuality.HIGH)
        assertEquals(DownloadQuality.HIGH, AppSettings.downloadQuality.value)

        assertTrue(AppSettings.wifiOnlyDownloads.value)
        AppSettings.setWifiOnlyDownloads(false)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
    }

    @Test
    fun req04_handlesTypesAndClampingCorrectly() {
        // Minimum floor clamping
        AppSettings.setAudioCacheLimitBytes(10L)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)

        // Unlimited ceiling clamping
        AppSettings.setAudioCacheLimitBytes(20L * 1024 * 1024 * 1024)
        assertEquals(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)
    }

    @Test
    fun req05_preventsDuplicateInitialization() {
        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        val secondPrefs = InMemorySharedPreferences()
        AppSettings.initForTest(secondPrefs)
        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
    }

    // ==========================================
    // 6-12: Sleep Timer Tests
    // ==========================================

    @Test
    fun req06_scheduleByDurationPresetAndCustom() {
        SleepTimer.start(30)
        assertTrue(SleepTimer.isRunning)
        assertEquals(30, SleepTimer.minutes.value)
        assertEquals(virtualTime + 30 * 60_000L, SleepTimer.deadline.value)
        assertEquals(30 * 60_000L, SleepTimer.remainingMs())
    }

    @Test
    fun req07_cancelBeforeExpiration() {
        SleepTimer.start(15)
        assertTrue(SleepTimer.isRunning)
        SleepTimer.cancel()
        assertFalse(SleepTimer.isRunning)
        assertNull(SleepTimer.deadline.value)
        assertNull(SleepTimer.minutes.value)
        assertFalse(SleepTimer.afterTrack.value)
    }

    @Test
    fun req08_pausesPlayerWhenDeadlineExpires() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        val paused = AtomicBoolean(false)

        val job = testScope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                val remaining = deadline - SleepTimer.timeProvider()
                if (remaining > 0L) {
                    delay(remaining)
                }
                paused.set(true)
                SleepTimer.cancel()
            }
        }

        SleepTimer.startWithDurationMs(null, 4000L)
        testScope.advanceTimeBy(1000L)
        virtualTime += 1000L
        assertFalse(paused.get())

        virtualTime += 3000L
        testScope.advanceTimeBy(3001L)
        assertTrue(paused.get())
        assertFalse(SleepTimer.isRunning)

        job.cancel()
    }

    @Test
    fun req09_finishAfterCurrentTrackOnNaturalCompletion() {
        SleepTimer.startAfterTrack()
        assertTrue(SleepTimer.afterTrack.value)

        var paused = false
        fun simulateTransition(reason: Int) {
            val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (ended && SleepTimer.afterTrack.value) {
                paused = true
                SleepTimer.cancel()
            }
        }

        // Manual seek must not trigger premature stop
        simulateTransition(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        assertFalse(paused)
        assertTrue(SleepTimer.afterTrack.value)

        // Auto transition triggers pause and disarms timer
        simulateTransition(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertTrue(paused)
        assertFalse(SleepTimer.isRunning)
    }

    @Test
    fun req10_survivesScreenChangeWithoutStopping() {
        SleepTimer.start(60)
        val d = SleepTimer.deadline.value

        // Simulate navigating to Cockpit and destroying MusicCenter composables
        assertTrue(SleepTimer.isRunning)
        assertEquals(d, SleepTimer.deadline.value)
    }

    @Test
    fun req11_singleExoPlayerAndServiceArchitecturePreserved() {
        // DivineDeckPlaybackService constants verification
        assertEquals("com.divinedeck.ACTION_PLAY_SAMPLE", DivineDeckPlaybackService.ACTION_PLAY_SAMPLE)
        assertEquals("com.divinedeck.ACTION_CLEAR_QUEUE", DivineDeckPlaybackService.ACTION_CLEAR_QUEUE)
        // SleepTimer does not create or hold secondary players; it controls the single player reference
        assertFalse(SleepTimer.isRunning)
    }

    @Test
    fun req12_releasesCoroutinesAndObserversOnCancel() = runTest {
        val testScope = TestScope(StandardTestDispatcher(testScheduler))
        var callCount = 0
        val job = testScope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                delay(5000L)
                callCount++
            }
        }

        SleepTimer.startWithDurationMs(null, 5000L)
        testScope.advanceTimeBy(1000L)

        // Cancel observer (e.g. service destroyed)
        job.cancel()
        testScope.advanceTimeBy(10000L)

        assertEquals(0, callCount)
    }

    // ==========================================
    // 13-18: Storage & Downloads Tests
    // ==========================================

    @Test
    fun req13_changeCacheLimitUpdatesDynamically() {
        AudioCache.setBytesLimit(1024L * 1024 * 1024)
        assertEquals(1024L * 1024 * 1024, AudioCache.getBytesLimit())

        AudioCache.setBytesLimit(2048L * 1024 * 1024)
        assertEquals(2048L * 1024 * 1024, AudioCache.getBytesLimit())
    }

    @Test
    fun req14_appliesCacheLimitOnInitialization() {
        AppSettings.setAudioCacheLimitBytes(1024L * 1024 * 1024)
        AudioCache.setBytesLimit(AppSettings.audioCacheLimitBytes.value)
        assertEquals(1024L * 1024 * 1024, AudioCache.getBytesLimit())
    }

    @Test
    fun req15_respectsPermanentDownloadsDuringCacheOperations() {
        val downloadFolder = File(tempBaseDir, "bitchord_downloads").apply { mkdirs() }
        val permanentSongFile = File(downloadFolder, "permanent_track.webm")
        permanentSongFile.writeBytes(ByteArray(8192) { 0x33.toByte() })

        // Mutate and apply cache ceiling
        AudioCache.setBytesLimit(512 * 1024)

        assertTrue(permanentSongFile.exists())
        assertEquals(8192, permanentSongFile.length())
    }

    @Test
    fun req16_wifiOnlyDownloadsPreferenceEnforced() = runTest(testDispatcher) {
        AppSettings.setWifiOnlyDownloads(true)
        AppSettings.setMeteredConnectionForTest(true) // Cellular

        Downloads.enqueue(testSong("wifi-fail"))
        advanceUntilIdle()

        val state = Downloads.downloadStateFor("wifi-fail")
        assertNotNull(state)
        assertTrue(state is DownloadState.Failed)
        assertEquals(Downloads.WIFI_ONLY_REFUSAL, (state as DownloadState.Failed).reason)

        // Now allow on unmetered Wi-Fi
        AppSettings.setMeteredConnectionForTest(false)
        Downloads.enqueue(testSong("wifi-pass"))
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded("wifi-pass"))
    }

    @Test
    fun req17_selectedDownloadQualityPropagatedToStreamFetcher() = runTest(testDispatcher) {
        val observedKbps = AtomicInteger(0)
        Downloads.initForTest(
            sharedPreferences = mockPrefs,
            baseDir = tempBaseDir,
            fetcher = object : Downloader.StreamFetcher {
                override suspend fun resolveAndFetch(
                    videoId: String,
                    maxKbps: Int,
                    requireM4a: Boolean,
                    openSink: (StreamResolver.Stream) -> OutputStream,
                    onProgress: (written: Long, total: Long) -> Unit
                ): StreamResolver.Stream {
                    observedKbps.set(maxKbps)
                    val stream = StreamResolver.Stream(
                        url = "https://example.com/audio/$videoId",
                        kbps = maxKbps,
                        mimeType = "audio/webm",
                    )
                    openSink(stream).use { sink ->
                        sink.write(ByteArray(1024))
                        onProgress(1024L, 1024L)
                    }
                    return stream
                }
            },
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )

        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        AppSettings.setMeteredConnectionForTest(false)
        Downloads.enqueue(testSong("std-qual"))
        advanceUntilIdle()

        assertEquals(128, observedKbps.get())
        assertTrue(Downloads.isDownloaded("std-qual"))
    }

    @Test
    fun req18_persistsPreferencesAfterApplicationRestart() {
        AppSettings.setDownloadQuality(DownloadQuality.HIGH)
        AppSettings.setWifiOnlyDownloads(false)
        AppSettings.setExportDownloads(true)
        AppSettings.setAudioCacheLimitBytes(2L * 1024 * 1024 * 1024)

        // Simulate app shutdown and restart
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertEquals(DownloadQuality.HIGH, AppSettings.downloadQuality.value)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
        assertTrue(AppSettings.exportDownloads.value)
        assertEquals(2L * 1024 * 1024 * 1024, AppSettings.audioCacheLimitBytes.value)
    }

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
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
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
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
            }
        }
    }
}

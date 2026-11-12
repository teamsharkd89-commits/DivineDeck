package com.music.bitchord.download

import android.content.SharedPreferences
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.DownloadQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for download settings integration in DIVINE DECK (Phase 06.5E.1).
 * Verifies Wi-Fi-only enforcement, download quality propagation, and settings persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsSettingsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File
    private lateinit var mockPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        tempBaseDir = Files.createTempDirectory("bitchord_dl_settings_test_").toFile()
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

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
                        kbps = 128,
                        mimeType = "audio/webm",
                    )
                    openSink(stream).use { sink ->
                        val bytes = ByteArray(4096) { 0x55.toByte() }
                        ByteArrayInputStream(bytes).copyTo(sink)
                        onProgress(4096L, 4096L)
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
        AppSettings.resetForTest()
        tempBaseDir.deleteRecursively()
    }

    private fun testSong(id: String, title: String = "Test Track"): Song =
        Song(
            videoId = id,
            title = title,
            artist = "Artist",
            thumbnailUrl = "https://example.com/thumb.jpg",
            durationText = "4:00",
        )

    @Test
    fun test1_refusesDownloadWhenWifiOnlyAndMetered() = runTest(testDispatcher) {
        AppSettings.setWifiOnlyDownloads(true)
        AppSettings.setMeteredConnectionForTest(true) // Mobile cellular data
        assertFalse(AppSettings.downloadsAllowedNow)

        val song = testSong("refused-1")
        Downloads.enqueue(song)
        advanceUntilIdle()

        val state = Downloads.downloadStateFor("refused-1")
        assertNotNull(state)
        assertTrue(state is DownloadState.Failed)
        assertEquals(Downloads.WIFI_ONLY_REFUSAL, (state as DownloadState.Failed).reason)
        assertFalse(Downloads.isDownloaded("refused-1"))
    }

    @Test
    fun test2_allowsDownloadWhenWifiOnlyAndUnmetered() = runTest(testDispatcher) {
        AppSettings.setWifiOnlyDownloads(true)
        AppSettings.setMeteredConnectionForTest(false) // Unmetered Wi-Fi
        assertTrue(AppSettings.downloadsAllowedNow)

        val song = testSong("allowed-1")
        Downloads.enqueue(song)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded("allowed-1"))
        assertTrue(Downloads.getDownloadedSongs().any { it.videoId == "allowed-1" })
    }

    @Test
    fun test3_passesConfiguredQualityCeilingToFetcher() = runTest(testDispatcher) {
        val capturedMaxKbps = AtomicInteger(-1)
        val capturedRequireM4a = java.util.concurrent.atomic.AtomicBoolean(false)

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
                    capturedMaxKbps.set(maxKbps)
                    capturedRequireM4a.set(requireM4a)
                    val stream = StreamResolver.Stream(
                        url = "https://example.com/audio/$videoId",
                        kbps = maxKbps,
                        mimeType = if (requireM4a) "audio/mp4" else "audio/webm",
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

        // Standard quality (128 kbps max) and export = true (requireM4a = true)
        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        AppSettings.setExportDownloads(true)
        AppSettings.setMeteredConnectionForTest(false)

        Downloads.enqueue(testSong("quality-std"))
        advanceUntilIdle()

        assertEquals(128, capturedMaxKbps.get())
        assertTrue(capturedRequireM4a.get())
        assertTrue(Downloads.isDownloaded("quality-std"))

        // High quality (Int.MAX_VALUE) and export = false
        AppSettings.setDownloadQuality(DownloadQuality.HIGH)
        AppSettings.setExportDownloads(false)

        Downloads.enqueue(testSong("quality-high"))
        advanceUntilIdle()

        assertEquals(Int.MAX_VALUE, capturedMaxKbps.get())
        assertFalse(capturedRequireM4a.get())
        assertTrue(Downloads.isDownloaded("quality-high"))
    }

    @Test
    fun test4_inFlightDownloadsAreImmuneToMidDownloadSettingsChange() = runTest(testDispatcher) {
        var resolveCount = 0
        val observedKbpsList = mutableListOf<Int>()

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
                    resolveCount++
                    observedKbpsList.add(maxKbps)
                    // Simulate setting change right during download
                    AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
                    AppSettings.setWifiOnlyDownloads(true)
                    AppSettings.setMeteredConnectionForTest(true) // Turn to cellular mid-stream

                    val stream = StreamResolver.Stream(
                        url = "https://example.com/audio/$videoId",
                        kbps = maxKbps,
                        mimeType = "audio/webm",
                    )
                    openSink(stream).use { sink ->
                        sink.write(ByteArray(2048))
                        onProgress(2048L, 2048L)
                    }
                    return stream
                }
            },
            testScope = kotlinx.coroutines.CoroutineScope(testDispatcher)
        )

        AppSettings.setDownloadQuality(DownloadQuality.LOSSLESS)
        AppSettings.setMeteredConnectionForTest(false)

        Downloads.enqueue(testSong("mid-change-1"))
        advanceUntilIdle()

        assertEquals(1, resolveCount)
        assertEquals(Int.MAX_VALUE, observedKbpsList.first())
        // The active download completed successfully even though connection turned to cellular mid-transfer
        assertTrue(Downloads.isDownloaded("mid-change-1"))
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

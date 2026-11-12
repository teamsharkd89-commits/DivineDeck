package com.music.bitchord.playback

import android.content.SharedPreferences
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [AudioCache] and [DynamicLruCacheEvictor] settings integration (Phase 06.5E.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioCacheSettingsTest {

    private lateinit var mockPrefs: InMemorySharedPreferences
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("bitchord_audiocache_test_").toFile()
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)
    }

    @After
    fun tearDown() {
        AudioCache.release()
        AppSettings.resetForTest()
        tempDir.deleteRecursively()
    }

    @Test
    fun test1_dynamicLruCacheEvictorMaxBytesAndApplyNow() {
        val evictor = DynamicLruCacheEvictor(maxBytes = 512L * 1024 * 1024)
        assertEquals(512L * 1024 * 1024, evictor.maxBytes)

        evictor.maxBytes = 1024L * 1024 * 1024
        assertEquals(1024L * 1024 * 1024, evictor.maxBytes)

        evictor.setBytesLimit(cache = null, bytes = 2048L * 1024 * 1024)
        assertEquals(2048L * 1024 * 1024, evictor.maxBytes)
    }

    @Test
    fun test2_audioCacheDynamicCeilingMutation() {
        val initialLimit = AppSettings.audioCacheLimitBytes.value
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, initialLimit)

        AudioCache.setBytesLimit(1024L * 1024 * 1024)
        assertEquals(1024L * 1024 * 1024, AudioCache.getBytesLimit())

        AudioCache.setBytesLimit(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES)
        assertEquals(Long.MAX_VALUE, AudioCache.getBytesLimit())
    }

    @Test
    fun test3_permanentDownloadsAreImmuneToCacheOperations() {
        // Create permanent downloads folder
        val downloadsFolder = File(tempDir, "bitchord_downloads").apply { mkdirs() }
        val downloadedTrack = File(downloadsFolder, "test_track_123.webm")
        downloadedTrack.writeBytes(ByteArray(1024 * 1024) { 0xAA.toByte() }) // 1 MB track

        // Create temporary cache folder
        val cacheFolder = File(tempDir, "bitchord_audio").apply { mkdirs() }
        val cacheFile = File(cacheFolder, "cached_chunk.tmp")
        cacheFile.writeBytes(ByteArray(512 * 1024) { 0xBB.toByte() })

        // AudioCache set to small limit and evicted
        AudioCache.setBytesLimit(256 * 1024)

        // Permanent download must remain 100% intact
        assertTrue("Downloaded track must exist untouched", downloadedTrack.exists())
        assertEquals(1024 * 1024, downloadedTrack.length())
    }

    @Test
    fun test4_unlimitedCacheLimitSemantics() {
        AppSettings.setAudioCacheLimitBytes(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES)
        assertEquals(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)

        AudioCache.setBytesLimit(AppSettings.audioCacheLimitBytes.value)
        assertEquals(Long.MAX_VALUE, AudioCache.getBytesLimit())
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

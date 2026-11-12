package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.settings.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Adversarial stress and boundary tests for [AppSettings] lyrics visual effects:
 * 1. Extreme setting values (negative, zero, 1000f, Infinity).
 * 2. Corrupted and invalid preference data directly injected into storage.
 * 3. Concurrent multi-threaded settings mutations.
 * 4. Factory reset guarantees.
 */
class BitChordLyricsEffectsSettingsAdversarialTest {

    private lateinit var fakePrefs: TestInMemorySharedPreferences

    @Before
    fun setUp() {
        fakePrefs = TestInMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
    }

    @Test
    fun intensity_extremeValuesClampedStrictly() {
        val testCases = listOf(
            -1000f to 0.2f,
            -1f to 0.2f,
            0f to 0.2f,
            0.1f to 0.2f,
            0.1999f to 0.2f,
            0.2f to 0.2f,
            1.0f to 1.0f,
            1.5f to 1.5f,
            2.0f to 2.0f,
            2.0001f to 2.0f,
            10f to 2.0f,
            1000f to 2.0f,
            Float.POSITIVE_INFINITY to 2.0f,
            Float.NEGATIVE_INFINITY to 0.2f,
        )

        for ((input, expected) in testCases) {
            AppSettings.setLyricsEffectIntensity(input)
            val flowVal = AppSettings.lyricsEffectIntensity.value
            val prefVal = fakePrefs.getFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, -1f)

            assertEquals("StateFlow intensity for input $input must clamp to $expected", expected, flowVal, 0.0001f)
            assertEquals("Stored intensity for input $input must clamp to $expected", expected, prefVal, 0.0001f)
            assertTrue("Intensity must be >= 0.2f", flowVal >= 0.2f)
            assertTrue("Intensity must be <= 2.0f", flowVal <= 2.0f)
        }
    }

    @Test
    fun corruptedPreferencesStorage_healsOnReload() {
        // 1. Inject corrupted out-of-range negative intensity into raw SharedPreferences
        fakePrefs.edit().putFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, -999.0f).commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals(0.2f, AppSettings.lyricsEffectIntensity.value, 0.0001f)

        // 2. Inject corrupted out-of-range huge intensity
        fakePrefs.edit().putFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, 8888.0f).commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals(2.0f, AppSettings.lyricsEffectIntensity.value, 0.0001f)

        // 3. Inject corrupted unknown style string
        fakePrefs.edit().putString(AppSettings.KEY_LYRICS_EFFECT_STYLE, "NOT_A_REAL_STYLE").commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals(LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)

        // 4. Inject empty style string
        fakePrefs.edit().putString(AppSettings.KEY_LYRICS_EFFECT_STYLE, "").commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals(LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)

        // 5. Inject lowercase style string
        fakePrefs.edit().putString(AppSettings.KEY_LYRICS_EFFECT_STYLE, "flow").commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals(LyricsEffectStyle.FLOW, AppSettings.lyricsEffectStyle.value)
    }

    @Test
    fun concurrentSettingsMutations_threadSafetyStress() {
        val numThreads = 8
        val iterationsPerThread = 250
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        val errors = AtomicInteger(0)

        val styles = LyricsEffectStyle.entries

        for (t in 0 until numThreads) {
            executor.submit {
                try {
                    for (i in 0 until iterationsPerThread) {
                        val style = styles[(t + i) % styles.size]
                        val intensity = (i % 30) / 10f // Values from 0.0f to 2.9f
                        val enabled = (i % 2 == 0)

                        AppSettings.setLyricsEffectsEnabled(enabled)
                        AppSettings.setLyricsEffectStyle(style)
                        AppSettings.setLyricsEffectIntensity(intensity)
                        AppSettings.setLyricsBeatReaction(!enabled)
                        AppSettings.setLyricsLowPowerMode(enabled)

                        val currentIntensity = AppSettings.lyricsEffectIntensity.value
                        if (currentIntensity < 0.2f || currentIntensity > 2.0f) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent mutations must complete within 10s", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("Zero errors during concurrent settings writes", 0, errors.get())

        val finalIntensity = AppSettings.lyricsEffectIntensity.value
        assertTrue("Final intensity must remain clamped in [0.2f, 2.0f], was $finalIntensity", finalIntensity in 0.2f..2.0f)
    }

    @Test
    fun factoryReset_cleansAllFiveKeys() {
        // Set everything to non-default
        AppSettings.setLyricsEffectsEnabled(false)
        AppSettings.setLyricsEffectStyle(LyricsEffectStyle.BEAT_REACTIVE)
        AppSettings.setLyricsEffectIntensity(1.95f)
        AppSettings.setLyricsBeatReaction(false)
        AppSettings.setLyricsLowPowerMode(true)

        // Execute factory reset
        AppSettings.resetAllDefaults()

        // Verify all 5 keys reset to factory defaults
        assertTrue("lyricsEffectsEnabled must reset to true", AppSettings.lyricsEffectsEnabled.value)
        assertEquals("lyricsEffectStyle must reset to PULSE", LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)
        assertEquals("lyricsEffectIntensity must reset to 1.0f", 1.0f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertTrue("lyricsBeatReaction must reset to true", AppSettings.lyricsBeatReaction.value)
        assertFalse("lyricsLowPowerMode must reset to false", AppSettings.lyricsLowPowerMode.value)
    }

    private class TestInMemorySharedPreferences(
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
                synchronized(map) {
                    if (clearAll) map.clear()
                    removals.forEach { map.remove(it) }
                    pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
                }
            }
        }
    }
}

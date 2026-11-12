package com.music.bitchord.data.settings

import android.content.SharedPreferences
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for DSP Equalizer & Spatial Audio settings in [AppSettings].
 * Validates default values, range clamping, preset matching, and cross-session persistence.
 */
class EqualizerSettingsTest {

    private lateinit var mockPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
    }

    @Test
    fun testDefaultValuesAreSafeAndFlat() {
        AppSettings.initForTest(mockPrefs)

        assertFalse("Equalizer should be disabled by default", AppSettings.equalizerEnabled.value)
        assertFalse("Spatial Audio should be disabled by default", AppSettings.spatialAudio.value)
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)
        assertEquals(0, AppSettings.equalizerToneX.value)
        assertEquals(0, AppSettings.equalizerToneY.value)
        assertFalse(AppSettings.equalizerFocused.value)
        assertEquals(0.0f, AppSettings.equalizerBalance.value, 1e-6f)
        assertEquals(EqLayout.MANUAL_COUNT, AppSettings.equalizerBands.value.size)
        assertTrue(AppSettings.equalizerBands.value.all { it == 0.0f })
        assertEquals(EqualizerPreset.FLAT, AppSettings.equalizerPreset.value)
    }

    @Test
    fun testRangeValidationAndClamping() {
        AppSettings.initForTest(mockPrefs)

        // Tone Pad: out-of-range values must clamp to [-5, 5]
        AppSettings.setEqualizerTone(-10, 10)
        assertEquals(-EqLayout.TONE_STEPS, AppSettings.equalizerToneX.value)
        assertEquals(EqLayout.TONE_STEPS, AppSettings.equalizerToneY.value)

        // Balance: out-of-range values must clamp to [-1.0, 1.0]
        AppSettings.setEqualizerBalance(-2.5f)
        assertEquals(-1.0f, AppSettings.equalizerBalance.value, 1e-6f)
        AppSettings.setEqualizerBalance(3.7f)
        assertEquals(1.0f, AppSettings.equalizerBalance.value, 1e-6f)

        // Manual Bands: out-of-range values must clamp to [-12.0, 12.0]
        val extremeBands = listOf(-20f, -15f, 0f, 15f, 25f, 5f, -3f)
        AppSettings.setEqualizerBands(extremeBands)
        val clampedBands = AppSettings.equalizerBands.value

        assertEquals(-EqLayout.MANUAL_RANGE_DB, clampedBands[0], 1e-6f)
        assertEquals(-EqLayout.MANUAL_RANGE_DB, clampedBands[1], 1e-6f)
        assertEquals(0f, clampedBands[2], 1e-6f)
        assertEquals(EqLayout.MANUAL_RANGE_DB, clampedBands[3], 1e-6f)
        assertEquals(EqLayout.MANUAL_RANGE_DB, clampedBands[4], 1e-6f)
        assertEquals(5f, clampedBands[5], 1e-6f)
        assertEquals(-3f, clampedBands[6], 1e-6f)
    }

    @Test
    fun testPresetApplicationAndMatching() {
        AppSettings.initForTest(mockPrefs)

        // Applying factory preset updates both bands and preset StateFlow
        AppSettings.setEqualizerPreset(EqualizerPreset.ROCK)
        assertEquals(EqualizerPreset.ROCK, AppSettings.equalizerPreset.value)
        assertEquals(EqualizerPreset.ROCK.bands, AppSettings.equalizerBands.value)

        // Slightly altering one band turns preset into CUSTOM
        val customBands = EqualizerPreset.ROCK.bands.toMutableList()
        customBands[0] = customBands[0] + 2.5f
        AppSettings.setEqualizerBands(customBands)
        assertEquals(EqualizerPreset.CUSTOM, AppSettings.equalizerPreset.value)

        // Dragging bands back to exact preset restores preset name
        AppSettings.setEqualizerBands(EqualizerPreset.BASS_BOOST.bands)
        assertEquals(EqualizerPreset.BASS_BOOST, AppSettings.equalizerPreset.value)
    }

    @Test
    fun testPersistenceAcrossRestart() {
        AppSettings.initForTest(mockPrefs)

        AppSettings.setEqualizerEnabled(true)
        AppSettings.setSpatialAudio(true)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerTone(3, -2)
        AppSettings.setEqualizerFocused(true)
        AppSettings.setEqualizerBalance(0.45f)
        AppSettings.setEqualizerPreset(EqualizerPreset.ELECTRONIC)

        // Simulate complete app restart
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertTrue(AppSettings.equalizerEnabled.value)
        assertTrue(AppSettings.spatialAudio.value)
        assertEquals(EqualizerMode.MANUAL, AppSettings.equalizerMode.value)
        assertEquals(3, AppSettings.equalizerToneX.value)
        assertEquals(-2, AppSettings.equalizerToneY.value)
        assertTrue(AppSettings.equalizerFocused.value)
        assertEquals(0.45f, AppSettings.equalizerBalance.value, 1e-6f)
        assertEquals(EqualizerPreset.ELECTRONIC, AppSettings.equalizerPreset.value)
        assertEquals(EqualizerPreset.ELECTRONIC.bands, AppSettings.equalizerBands.value)
    }

    @Test
    fun testCorruptedOrMalformedPreferencesFallbackGracefully() {
        // Pre-populate SharedPreferences with malformed data
        mockPrefs.edit()
            .putString(AppSettings.KEY_EQ_MODE, "INVALID_MODE_NAME")
            .putString(AppSettings.KEY_EQ_BANDS, "abc,invalid,12345.67,-999")
            .putInt(AppSettings.KEY_EQ_TONE_X, 999)
            .putFloat(AppSettings.KEY_EQ_BALANCE, -50.0f)
            .apply()

        AppSettings.initForTest(mockPrefs)

        // Must fallback safely without crashing
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)
        assertEquals(EqLayout.TONE_STEPS, AppSettings.equalizerToneX.value)
        assertEquals(-1.0f, AppSettings.equalizerBalance.value, 1e-6f)
        assertEquals(EqLayout.MANUAL_COUNT, AppSettings.equalizerBands.value.size)
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

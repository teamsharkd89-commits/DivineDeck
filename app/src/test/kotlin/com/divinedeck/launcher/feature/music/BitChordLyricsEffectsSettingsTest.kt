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

/**
 * Automated unit tests verifying Milestone 1 lyrics visual effect settings:
 * 1. Default values and StateFlow reactivity.
 * 2. Key persistence across preferences reloads.
 * 3. Range clamping for effect intensity [0.2f .. 2.0f].
 * 4. Style enumeration support for all 5 visual modes (PULSE, FLOW, KARAOKE, GLOW, BEAT_REACTIVE).
 * 5. Low-power mode and beat reaction toggle behaviors.
 * 6. Clean factory reset via [AppSettings.resetAllDefaults].
 */
class BitChordLyricsEffectsSettingsTest {

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
    fun defaultSettings_haveExpectedFactoryValues() {
        assertTrue("Lyrics effects must be enabled by default", AppSettings.lyricsEffectsEnabled.value)
        assertEquals("Default style must be PULSE", LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)
        assertEquals("Default intensity must be 1.0f", 1.0f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertTrue("Beat reaction must be enabled by default", AppSettings.lyricsBeatReaction.value)
        assertFalse("Low-power mode must be disabled by default", AppSettings.lyricsLowPowerMode.value)
    }

    @Test
    fun lyricsEffectsEnabled_persistsAndUpdatesFlow() {
        AppSettings.setLyricsEffectsEnabled(false)
        assertFalse(AppSettings.lyricsEffectsEnabled.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_EFFECTS_ENABLED, true))

        AppSettings.setLyricsEffectsEnabled(true)
        assertTrue(AppSettings.lyricsEffectsEnabled.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_EFFECTS_ENABLED, false))
    }

    @Test
    fun lyricsEffectStyle_supportsAllFiveStylesAndPersists() {
        for (style in LyricsEffectStyle.entries) {
            AppSettings.setLyricsEffectStyle(style)
            assertEquals("StateFlow must reflect selected style", style, AppSettings.lyricsEffectStyle.value)
            assertEquals("SharedPreferences must store style name", style.name, fakePrefs.getString(AppSettings.KEY_LYRICS_EFFECT_STYLE, null))
        }

        // Test fallback from invalid string in preferences
        fakePrefs.edit().putString(AppSettings.KEY_LYRICS_EFFECT_STYLE, "UNKNOWN_STYLE").commit()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
        assertEquals("Unknown style must fallback to PULSE", LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)
    }

    @Test
    fun lyricsEffectIntensity_clampsWithinValidRange() {
        // Nominal value
        AppSettings.setLyricsEffectIntensity(1.5f)
        assertEquals(1.5f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertEquals(1.5f, fakePrefs.getFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, 0f), 0.001f)

        // Lower boundary clamp (< 0.2f -> 0.2f)
        AppSettings.setLyricsEffectIntensity(0.05f)
        assertEquals(0.2f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertEquals(0.2f, fakePrefs.getFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, 0f), 0.001f)

        // Upper boundary clamp (> 2.0f -> 2.0f)
        AppSettings.setLyricsEffectIntensity(3.5f)
        assertEquals(2.0f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertEquals(2.0f, fakePrefs.getFloat(AppSettings.KEY_LYRICS_EFFECT_INTENSITY, 0f), 0.001f)
    }

    @Test
    fun lyricsBeatReaction_persistsAndToggles() {
        AppSettings.setLyricsBeatReaction(false)
        assertFalse(AppSettings.lyricsBeatReaction.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_BEAT_REACTION, true))

        AppSettings.setLyricsBeatReaction(true)
        assertTrue(AppSettings.lyricsBeatReaction.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_BEAT_REACTION, false))
    }

    @Test
    fun lyricsLowPowerMode_persistsAndToggles() {
        AppSettings.setLyricsLowPowerMode(true)
        assertTrue(AppSettings.lyricsLowPowerMode.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_LOW_POWER_MODE, false))

        AppSettings.setLyricsLowPowerMode(false)
        assertFalse(AppSettings.lyricsLowPowerMode.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_LYRICS_LOW_POWER_MODE, true))
    }

    @Test
    fun resetAllDefaults_restoresFactoryValues() {
        // Alter all values
        AppSettings.setLyricsEffectsEnabled(false)
        AppSettings.setLyricsEffectStyle(LyricsEffectStyle.GLOW)
        AppSettings.setLyricsEffectIntensity(1.8f)
        AppSettings.setLyricsBeatReaction(false)
        AppSettings.setLyricsLowPowerMode(true)

        // Reset
        AppSettings.resetAllDefaults()

        assertTrue(AppSettings.lyricsEffectsEnabled.value)
        assertEquals(LyricsEffectStyle.PULSE, AppSettings.lyricsEffectStyle.value)
        assertEquals(1.0f, AppSettings.lyricsEffectIntensity.value, 0.001f)
        assertTrue(AppSettings.lyricsBeatReaction.value)
        assertFalse(AppSettings.lyricsLowPowerMode.value)
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
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
            }
        }
    }
}

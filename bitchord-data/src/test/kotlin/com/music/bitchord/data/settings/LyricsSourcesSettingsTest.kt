package com.music.bitchord.data.settings

import com.music.bitchord.data.lyrics.LyricsSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Lyrics Source Control preferences in [AppSettings] (Phase 06.5E.5).
 * Validates default values, persistence, reordering, safety guards, and reset behavior.
 */
class LyricsSourcesSettingsTest {

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
    fun test1_initializationWithDefaultValues() {
        AppSettings.initForTest(mockPrefs)

        // All offered sources must be enabled by default (15 active non-hidden sources)
        assertEquals(LyricsSource.offered.toSet(), AppSettings.lyricsSources.value)
        assertEquals(LyricsSource.offered.size, AppSettings.lyricsSources.value.size)

        // Default order must match LyricsSource.offered
        assertEquals(LyricsSource.offered, AppSettings.lyricsSourceOrder.value)
        assertEquals(LyricsSource.offered.size, AppSettings.lyricsSourceOrder.value.size)

        // Syllable sync priority is off by default
        assertFalse(AppSettings.prioritizeSyllableSync.value)
    }

    @Test
    fun test2_persistsAcrossReinitialization() {
        AppSettings.initForTest(mockPrefs)

        // Disable a few sources
        val customSources = setOf(LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS, LyricsSource.GENIUS)
        AppSettings.setLyricsSources(customSources)

        // Reorder sources
        val customOrder = listOf(LyricsSource.GENIUS, LyricsSource.LRCLIB, LyricsSource.BETTER_LYRICS)
        AppSettings.setLyricsSourceOrder(customOrder)

        // Enable syllable sync priority
        AppSettings.setPrioritizeSyllableSync(true)

        // Reset in-memory state and re-initialize from same prefs
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertEquals(customSources, AppSettings.lyricsSources.value)
        assertEquals(LyricsSource.ordered(customOrder), AppSettings.lyricsSourceOrder.value)
        assertTrue(AppSettings.prioritizeSyllableSync.value)
    }

    @Test
    fun test3_safetyGuard_cannotDisableLastSource() {
        AppSettings.initForTest(mockPrefs)

        // Set to exactly 1 source
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB))
        assertEquals(setOf(LyricsSource.LRCLIB), AppSettings.lyricsSources.value)

        // Attempt to toggle off the last remaining source
        val toggleResult = AppSettings.toggleLyricsSource(LyricsSource.LRCLIB)
        assertFalse(toggleResult)
        assertEquals(setOf(LyricsSource.LRCLIB), AppSettings.lyricsSources.value)

        // Direct set with empty set is also rejected
        AppSettings.setLyricsSources(emptySet())
        assertEquals(setOf(LyricsSource.LRCLIB), AppSettings.lyricsSources.value)
    }

    @Test
    fun test4_toggleLyricsSource() {
        AppSettings.initForTest(mockPrefs)

        assertTrue(LyricsSource.LRCLIB in AppSettings.lyricsSources.value)

        // Toggle OFF
        val turnedOff = AppSettings.toggleLyricsSource(LyricsSource.LRCLIB)
        assertTrue(turnedOff)
        assertFalse(LyricsSource.LRCLIB in AppSettings.lyricsSources.value)

        // Toggle ON
        val turnedOn = AppSettings.toggleLyricsSource(LyricsSource.LRCLIB)
        assertTrue(turnedOn)
        assertTrue(LyricsSource.LRCLIB in AppSettings.lyricsSources.value)
    }

    @Test
    fun test5_reorderUpAndDown() {
        AppSettings.initForTest(mockPrefs)

        val first = AppSettings.lyricsSourceOrder.value[0]
        val second = AppSettings.lyricsSourceOrder.value[1]

        // Moving first up should return false (already at top)
        assertFalse(AppSettings.moveLyricsSourceUp(first))
        assertEquals(first, AppSettings.lyricsSourceOrder.value[0])

        // Moving second up should swap with first
        assertTrue(AppSettings.moveLyricsSourceUp(second))
        assertEquals(second, AppSettings.lyricsSourceOrder.value[0])
        assertEquals(first, AppSettings.lyricsSourceOrder.value[1])

        // Moving second down should swap back
        assertTrue(AppSettings.moveLyricsSourceDown(second))
        assertEquals(first, AppSettings.lyricsSourceOrder.value[0])
        assertEquals(second, AppSettings.lyricsSourceOrder.value[1])

        // Moving last item down should return false
        val last = AppSettings.lyricsSourceOrder.value.last()
        assertFalse(AppSettings.moveLyricsSourceDown(last))
    }

    @Test
    fun test6_resetLyricsSourceSettings() {
        AppSettings.initForTest(mockPrefs)

        // Modify settings
        AppSettings.setLyricsSources(setOf(LyricsSource.LRCLIB))
        AppSettings.moveLyricsSourceUp(AppSettings.lyricsSourceOrder.value[5])
        AppSettings.setPrioritizeSyllableSync(true)

        // Reset
        AppSettings.resetLyricsSourceSettings()

        assertEquals(LyricsSource.offered.toSet(), AppSettings.lyricsSources.value)
        assertEquals(LyricsSource.offered, AppSettings.lyricsSourceOrder.value)
        assertFalse(AppSettings.prioritizeSyllableSync.value)
    }
}

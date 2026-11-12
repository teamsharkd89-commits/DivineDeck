package com.music.bitchord.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Automated unit tests verifying the visual lyrics engine calculations:
 * 1. Procedural beat generator waveform, attack, and exponential decay.
 * 2. Shadow generation across all 5 visual modes (PULSE, FLOW, KARAOKE, GLOW, BEAT_REACTIVE).
 * 3. Low-power mode sharp rendering and blur disablement.
 * 4. Active lyric line time-matching calculations.
 * 5. Syllable/word-level karaoke progressive reveals and vertical note lift.
 * 6. Smooth scrolling [ScrollRun] parameters and [LYRIC_EASING] bounds.
 */
class LyricsVisualEffectsTest {

    @Test
    fun calculateProceduralBeat_waveformAndAttack() {
        assertEquals(0f, calculateProceduralBeat(-50L), 0.001f)
        assertEquals(0f, calculateProceduralBeat(0L), 0.001f)

        // Linear attack up to 60ms
        val beat20 = calculateProceduralBeat(20L)
        val beat40 = calculateProceduralBeat(40L)
        val beat60 = calculateProceduralBeat(60L)

        assertEquals(20f / 60f, beat20, 0.01f)
        assertEquals(40f / 60f, beat40, 0.01f)
        assertEquals(1.0f, beat60, 0.01f)

        // Exponential decay after 60ms up to 500ms
        val beat150 = calculateProceduralBeat(150L)
        val beat300 = calculateProceduralBeat(300L)
        val beat450 = calculateProceduralBeat(450L)

        assertTrue(beat150 < beat60)
        assertTrue(beat300 < beat150)
        assertTrue(beat450 < beat300)

        // Periodicity across 500ms cycles
        assertEquals(calculateProceduralBeat(0L), calculateProceduralBeat(500L), 0.01f)
        assertEquals(calculateProceduralBeat(60L), calculateProceduralBeat(560L), 0.01f)
        assertEquals(calculateProceduralBeat(0L), calculateProceduralBeat(1000L), 0.01f)
    }

    @Test
    fun calculateLyricShadow_glowMode_scalesWithIntensity() {
        val accent = Color(0xFF00E5FF)

        val shadowNominal = calculateLyricShadow(
            style = LyricsEffectStyle.GLOW,
            intensity = 1.0f,
            beatPulse = 0f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(16f, shadowNominal.blurRadius, 0.01f)
        assertEquals(0.50f, shadowNominal.color.alpha, 0.01f)
        assertEquals(Offset.Zero, shadowNominal.offset)

        val shadowHigh = calculateLyricShadow(
            style = LyricsEffectStyle.GLOW,
            intensity = 2.0f,
            beatPulse = 0f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(32f, shadowHigh.blurRadius, 0.01f)
        assertEquals(0.95f, shadowHigh.color.alpha, 0.05f)
    }

    @Test
    fun calculateLyricShadow_beatReactiveMode_modulatesWithBeatPulse() {
        val accent = Color(0xFFFFB547)

        val shadowQuiet = calculateLyricShadow(
            style = LyricsEffectStyle.BEAT_REACTIVE,
            intensity = 1.0f,
            beatPulse = 0.0f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(10f, shadowQuiet.blurRadius, 0.01f)

        val shadowPunch = calculateLyricShadow(
            style = LyricsEffectStyle.BEAT_REACTIVE,
            intensity = 1.0f,
            beatPulse = 1.0f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(24f, shadowPunch.blurRadius, 0.01f)
        assertTrue(shadowPunch.color.alpha > shadowQuiet.color.alpha)
    }

    @Test
    fun calculateLyricShadow_lowPowerMode_disablesHeavyGpuBlur() {
        val accent = Color(0xFF00E5FF)

        // Regardless of style or intensity, low-power mode forces sharp, battery-efficient styling
        for (style in LyricsEffectStyle.entries) {
            val shadow = calculateLyricShadow(
                style = style,
                intensity = 2.0f,
                beatPulse = 1.0f,
                accentColor = accent,
                lowPowerMode = true,
            )
            assertEquals(4f, shadow.blurRadius, 0.01f)
            assertEquals(Offset(0f, 1f), shadow.offset)
            assertEquals(0.50f, shadow.color.alpha, 0.01f)
        }
    }

    @Test
    fun calculateLyricShadow_pulseAndFlowModes() {
        val accent = Color(0xFF00E5FF)

        val pulseShadow = calculateLyricShadow(
            style = LyricsEffectStyle.PULSE,
            intensity = 1.0f,
            beatPulse = 0.5f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(Color.White.copy(alpha = 0.45f), pulseShadow.color)
        assertEquals(13f, pulseShadow.blurRadius, 0.01f)

        val flowShadow = calculateLyricShadow(
            style = LyricsEffectStyle.FLOW,
            intensity = 1.0f,
            beatPulse = 0.5f,
            accentColor = accent,
            lowPowerMode = false,
        )
        assertEquals(accent.copy(alpha = 0.40f), flowShadow.color)
        assertEquals(12f, flowShadow.blurRadius, 0.01f)
    }

    @Test
    fun scrollRun_propertiesAndDefaults() {
        val run = ScrollRun(id = 1, distance = 42.5f, spanMs = 400)
        assertEquals(1, run.id)
        assertEquals(42.5f, run.distance, 0.001f)
        assertEquals(400, run.spanMs)
    }

    @Test
    fun lyricEasing_continuousSmoothInterpolation() {
        // LYRIC_EASING must satisfy boundary conditions
        assertEquals(0f, LYRIC_EASING.transform(0f), 0.001f)
        assertEquals(1f, LYRIC_EASING.transform(1f), 0.001f)

        // Monotonic progression
        val t25 = LYRIC_EASING.transform(0.25f)
        val t50 = LYRIC_EASING.transform(0.50f)
        val t75 = LYRIC_EASING.transform(0.75f)

        assertTrue(t25 in 0f..1f)
        assertTrue(t50 in t25..1f)
        assertTrue(t75 in t50..1f)
    }

    @Test
    fun activeLineIndex_calculationAccuracy() {
        val lines = listOf(
            LyricLine(timeMs = 1000L, text = "Line 1"),
            LyricLine(timeMs = 5000L, text = "Line 2"),
            LyricLine(timeMs = 9000L, text = "Line 3"),
        )

        // Before first line
        val idxBefore = lines.indexOfLast { it.timeMs <= 500L }.let { if (it >= 0) it else 0 }
        assertEquals(0, idxBefore)

        // At exact start of Line 1
        val idxExact1 = lines.indexOfLast { it.timeMs <= 1000L }
        assertEquals(0, idxExact1)

        // Between Line 1 and Line 2
        val idxBetween = lines.indexOfLast { it.timeMs <= 3500L }
        assertEquals(0, idxBetween)

        // At exact start of Line 2
        val idxExact2 = lines.indexOfLast { it.timeMs <= 5000L }
        assertEquals(1, idxExact2)

        // After last line
        val idxAfter = lines.indexOfLast { it.timeMs <= 12000L }
        assertEquals(2, idxAfter)
    }

    @Test
    fun wordLevelKaraoke_charsSungAtAndLineWordLift() {
        val word = LyricWord(
            startMs = 2000L,
            endMs = 3000L,
            text = "Fantastic",
        )
        val line = LyricLine(
            timeMs = 2000L,
            text = "Fantastic",
            words = listOf(word),
        )

        // Before word starts: 0 chars sung, 0 lift
        assertEquals(0f, word.charsSungAt(1500L), 0.01f)
        assertEquals(0f, line.wordLift(0, 1500L), 0.01f)

        // Mid-word: partial characters sung, positive word lift
        val charsMid = word.charsSungAt(2500L)
        assertEquals(4.5f, charsMid, 0.1f)
        assertTrue("Mid-word note must lift above baseline", line.wordLift(0, 2500L) > 0f)

        // After word finishes: all 9 characters sung, lift settles back
        assertEquals(9.0f, word.charsSungAt(3000L), 0.01f)
        assertEquals(9.0f, word.charsSungAt(4500L), 0.01f)
        assertEquals(0f, line.wordLift(0, 4500L), 0.01f)
    }
}

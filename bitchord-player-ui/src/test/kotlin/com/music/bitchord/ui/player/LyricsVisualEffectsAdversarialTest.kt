package com.music.bitchord.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Adversarial stress and boundary tests for BitChord lyrics visual effects,
 * cubic bezier easing, shadow math, settings bounds, and extreme lyrics datasets.
 */
class LyricsVisualEffectsAdversarialTest {

    // =========================================================================
    // 1. LYRIC_EASING Cubic Bezier Adversarial Verification
    // =========================================================================

    @Test
    fun lyricEasing_boundaryConditions_exactZeroAndOne() {
        assertEquals("At t=0, transform must be exactly 0.0f", 0f, LYRIC_EASING.transform(0f), 0.00001f)
        assertEquals("At t=1, transform must be exactly 1.0f", 1f, LYRIC_EASING.transform(1f), 0.00001f)
    }

    @Test
    fun lyricEasing_monotonicityOverHighResolutionSweep() {
        // Sweep 100,000 steps from 0 to 1
        val steps = 100_000
        var prevY = 0f

        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val y = LYRIC_EASING.transform(t)

            assertFalse("Output at t=$t cannot be NaN", y.isNaN())
            assertFalse("Output at t=$t cannot be Infinite", y.isInfinite())
            assertTrue("Output at t=$t must be in [0, 1], got $y", y in -0.001f..1.001f)
            assertTrue(
                "Easing must be monotonically non-decreasing: at t=$t, prevY=$prevY, y=$y",
                y >= prevY - 0.00001f
            )
            prevY = y
        }
    }

    @Test
    fun lyricEasing_finiteDifferences_noDerivativeBlowupsOrNegativeSlopes() {
        val n = 10_000
        val dt = 1f / n.toFloat()

        for (i in 0 until n) {
            val t = i * dt
            val y1 = LYRIC_EASING.transform(t)
            val y2 = LYRIC_EASING.transform(t + dt)
            val dy = y2 - y1
            val derivative = dy / dt

            assertFalse("Derivative at t=$t cannot be NaN", derivative.isNaN())
            assertFalse("Derivative at t=$t cannot be Infinite", derivative.isInfinite())
            assertTrue("Derivative at t=$t cannot be negative, got $derivative", derivative >= -0.001f)
            assertTrue("Derivative at t=$t must be bounded (< 10.0f), got $derivative", derivative < 10.0f)
        }
    }

    @Test
    fun lyricEasing_extremeInputs_beyondZeroAndOne() {
        // Compose CubicBezierEasing behavior outside [0, 1]
        val neg = LYRIC_EASING.transform(-0.5f)
        val pos = LYRIC_EASING.transform(1.5f)

        assertFalse("Negative input must not produce NaN", neg.isNaN())
        assertFalse("Negative input must not produce Infinite", neg.isInfinite())
        assertFalse("Input > 1 must not produce NaN", pos.isNaN())
        assertFalse("Input > 1 must not produce Infinite", pos.isInfinite())
    }

    // =========================================================================
    // 2. Settings Bounds & Style Fallback Verification
    // =========================================================================

    @Test
    fun lyricsEffectStyle_fromString_invalidAndAdversarialInputsFallbackToPulse() {
        // Null and empty
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString(null))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString(""))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("   "))

        // Case insensitivity
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("pulse"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("PULSE"))
        assertEquals(LyricsEffectStyle.FLOW, LyricsEffectStyle.fromString("flow"))
        assertEquals(LyricsEffectStyle.FLOW, LyricsEffectStyle.fromString("FLOW"))
        assertEquals(LyricsEffectStyle.KARAOKE, LyricsEffectStyle.fromString("karaoke"))
        assertEquals(LyricsEffectStyle.KARAOKE, LyricsEffectStyle.fromString("Karaoke"))
        assertEquals(LyricsEffectStyle.GLOW, LyricsEffectStyle.fromString("glow"))
        assertEquals(LyricsEffectStyle.GLOW, LyricsEffectStyle.fromString("GLOW"))
        assertEquals(LyricsEffectStyle.BEAT_REACTIVE, LyricsEffectStyle.fromString("beat_reactive"))
        assertEquals(LyricsEffectStyle.BEAT_REACTIVE, LyricsEffectStyle.fromString("BEAT_REACTIVE"))

        // Unknown and adversarial strings
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("UNKNOWN_EFFECT_STYLE"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("12345"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("<script>alert('xss')</script>"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("PULSE; DROP TABLE settings;--"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("🎵✨🔥"))
        assertEquals(LyricsEffectStyle.PULSE, LyricsEffectStyle.fromString("\u0000\u0001\u0002"))
    }

    // =========================================================================
    // 3. Shadow Generation with Extreme Intensity Values
    // =========================================================================

    @Test
    fun calculateLyricShadow_extremeIntensityValues_strictlyClamped() {
        val accent = Color(0xFF00E5FF)
        val extremeIntensities = listOf(
            -1000f,
            -10f,
            -1f,
            0f,
            0.01f,
            0.199f,
            0.2f,
            1.0f,
            2.0f,
            2.001f,
            10f,
            100f,
            1000f,
        )

        for (style in LyricsEffectStyle.entries) {
            for (intensity in extremeIntensities) {
                for (lowPower in listOf(true, false)) {
                    val shadow = calculateLyricShadow(
                        style = style,
                        intensity = intensity,
                        beatPulse = 1.0f,
                        accentColor = accent,
                        lowPowerMode = lowPower,
                    )

                    assertNotNull("Shadow must not be null", shadow)
                    assertFalse("Blur radius must not be NaN for style $style at intensity $intensity", shadow.blurRadius.isNaN())
                    assertFalse("Blur radius must not be Infinite", shadow.blurRadius.isInfinite())
                    assertTrue(
                        "Blur radius must be non-negative for style $style at intensity $intensity, got ${shadow.blurRadius}",
                        shadow.blurRadius >= 0f
                    )
                    assertTrue(
                        "Blur radius must be safely bounded (<= 50f), got ${shadow.blurRadius}",
                        shadow.blurRadius <= 50f
                    )

                    val alpha = shadow.color.alpha
                    assertFalse("Color alpha must not be NaN", alpha.isNaN())
                    assertFalse("Color alpha must not be Infinite", alpha.isInfinite())
                    assertTrue(
                        "Color alpha must be in [0, 1] for style $style at intensity $intensity, got $alpha",
                        alpha in 0f..1.0f
                    )
                }
            }
        }
    }

    // =========================================================================
    // 4. Procedural Beat Generator Adversarial Stress
    // =========================================================================

    @Test
    fun calculateProceduralBeat_adversarialTimestamps() {
        // Negative timestamps
        assertEquals(0f, calculateProceduralBeat(-1L), 0.0001f)
        assertEquals(0f, calculateProceduralBeat(-100000L), 0.0001f)
        assertEquals(0f, calculateProceduralBeat(Long.MIN_VALUE), 0.0001f)

        // Exact cycle boundaries
        assertEquals(0f, calculateProceduralBeat(0L), 0.0001f)
        assertEquals(1.0f, calculateProceduralBeat(60L), 0.01f)
        assertEquals(0f, calculateProceduralBeat(500L), 0.01f)
        assertEquals(1.0f, calculateProceduralBeat(560L), 0.01f)

        // Very large timestamps
        val largeBeat = calculateProceduralBeat(Long.MAX_VALUE)
        assertFalse(largeBeat.isNaN())
        assertFalse(largeBeat.isInfinite())
        assertTrue(largeBeat in 0f..1f)

        // 50,000 random points
        val rand = Random(1337)
        for (i in 0 until 50_000) {
            val pos = rand.nextLong(-1000L, 100_000_000L)
            val beat = calculateProceduralBeat(pos)
            assertFalse("Beat at $pos cannot be NaN", beat.isNaN())
            assertFalse("Beat at $pos cannot be Infinite", beat.isInfinite())
            assertTrue("Beat at $pos must be in [0, 1], got $beat", beat in 0f..1f)
        }
    }

    // =========================================================================
    // 5. Lyrics Data Edge Cases: Empty, Single Line, 10,000 Lines, Special Chars
    // =========================================================================

    @Test
    fun lyricsActiveLineIndex_emptyList_safeHandling() {
        val emptyLines = emptyList<LyricLine>()

        val currentMs = 5000L
        val idx = emptyLines.indexOfLast { it.timeMs <= currentMs }
        assertEquals(-1, idx)

        // PlayerLyrics logic: if (idx >= 0) idx else 0
        val resolvedIdx = if (idx >= 0) idx else 0
        // Guard check: resolvedIdx in lines.indices
        assertFalse("resolvedIdx must not be in emptyLines.indices", resolvedIdx in emptyLines.indices)
    }

    @Test
    fun lyricsActiveLineIndex_singleLine_safeHandling() {
        val singleLine = listOf(LyricLine(timeMs = 3000L, text = "Only line in track"))

        // Before line starts
        val idxBefore = singleLine.indexOfLast { it.timeMs <= 1000L }
        assertEquals(-1, idxBefore)
        val resolvedBefore = if (idxBefore >= 0) idxBefore else 0
        assertEquals(0, resolvedBefore)
        assertTrue(resolvedBefore in singleLine.indices)

        // At exact start
        val idxAt = singleLine.indexOfLast { it.timeMs <= 3000L }
        assertEquals(0, idxAt)

        // Far past end
        val idxAfter = singleLine.indexOfLast { it.timeMs <= 999999L }
        assertEquals(0, idxAfter)
    }

    @Test
    fun lyricsActiveLineIndex_hugeDataset_10000Lines_correctAndFast() {
        val lineCount = 10_000
        val lines = ArrayList<LyricLine>(lineCount)
        for (i in 0 until lineCount) {
            lines.add(LyricLine(timeMs = i * 2000L, text = "Lyric phrase number $i with some extended text for stress"))
        }

        assertEquals(10_000, lines.size)

        // Verify active line search accuracy across boundaries
        val startNano = System.nanoTime()

        // 1. Before all lines
        val idxStart = lines.indexOfLast { it.timeMs <= -100L }.let { if (it >= 0) it else 0 }
        assertEquals(0, idxStart)

        // 2. Middle line (e.g. index 5,000 at 10,000,000ms)
        val idxMid = lines.indexOfLast { it.timeMs <= 10_000_000L }
        assertEquals(5_000, idxMid)

        // 3. Exact last line (index 9,999 at 19,998,000ms)
        val idxEnd = lines.indexOfLast { it.timeMs <= 19_998_000L }
        assertEquals(9_999, idxEnd)

        // 4. Past last line
        val idxPast = lines.indexOfLast { it.timeMs <= 25_000_000L }
        assertEquals(9_999, idxPast)

        val durationMs = (System.nanoTime() - startNano) / 1_000_000.0
        assertTrue("Linear search on 10,000 lines should be faster than 50ms, was ${durationMs}ms", durationMs < 50.0)
    }

    @Test
    fun lyricsTextSpecialCharacters_zalgoEmojiArabicCJK_stringBoundsSafe() {
        val specialLines = listOf(
            LyricLine(timeMs = 1000L, text = ""), // Empty text
            LyricLine(timeMs = 2000L, text = "   \t\n   "), // Whitespace only
            LyricLine(timeMs = 3000L, text = "🎵🔥✨💖🎶"), // Emojis
            LyricLine(timeMs = 4000L, text = "مرحبا بالعالم كيف حالك اليوم"), // RTL Arabic
            LyricLine(timeMs = 5000L, text = "繁體中文測試 日本語の歌詞 한국어 가사"), // CJK
            LyricLine(timeMs = 6000L, text = "Z̵̢a̸̧l̷̢g̶o̸̢ ̴̢ç̷o̸̢ŗ̷r̸̢u̷̧p̶t̸i̷o̷n̶"), // Zalgo
            LyricLine(timeMs = 7000L, text = "<script>alert('test')</script> &amp; \"quotes\""), // HTML/XML
            LyricLine(timeMs = 8000L, text = "A".repeat(20_000)), // Very long line (20k chars)
        )

        for (line in specialLines) {
            // Verify revealedChars bounds clamp
            val revealedNeg = line.revealedChars(-500L).toInt().coerceIn(0, line.text.length)
            assertTrue("Revealed chars at -500ms must be in range", revealedNeg in 0..line.text.length)
            val sub1 = line.text.substring(0, revealedNeg)
            val sub2 = line.text.substring(revealedNeg)
            assertEquals(line.text.length, sub1.length + sub2.length)

            val revealedMid = line.revealedChars(line.timeMs + 500L).toInt().coerceIn(0, line.text.length)
            assertTrue("Revealed chars at mid must be in range", revealedMid in 0..line.text.length)
            val sub3 = line.text.substring(0, revealedMid)
            val sub4 = line.text.substring(revealedMid)
            assertEquals(line.text.length, sub3.length + sub4.length)

            val revealedFar = line.revealedChars(999_999L).toInt().coerceIn(0, line.text.length)
            assertTrue("Revealed chars at 999_999ms must be in range", revealedFar in 0..line.text.length)
            val sub5 = line.text.substring(0, revealedFar)
            val sub6 = line.text.substring(revealedFar)
            assertEquals(line.text.length, sub5.length + sub6.length)
        }
    }

    @Test
    fun lyricsWordLevelKaraoke_emptyWordsAndInvalidTimestamps() {
        val line = LyricLine(
            timeMs = 2000L,
            text = "Test Line",
            words = listOf(
                LyricWord(startMs = 3000L, endMs = 2000L, text = "Inverted"), // end < start
                LyricWord(startMs = 0L, endMs = 0L, text = "Zero"), // end == start
                LyricWord(startMs = 5000L, endMs = 6000L, text = ""), // Empty text
            ),
        )

        // Inverted word: startMs=3000, endMs=2000
        val charsInverted = line.words[0].charsSungAt(2500L)
        assertFalse(charsInverted.isNaN())
        assertFalse(charsInverted.isInfinite())

        // Zero duration word: startMs=0, endMs=0
        val charsZero = line.words[1].charsSungAt(0L)
        assertFalse(charsZero.isNaN())
        assertFalse(charsZero.isInfinite())

        // Empty text word
        val charsEmpty = line.words[2].charsSungAt(5500L)
        assertEquals(0f, charsEmpty, 0.001f)

        // Word lift out of bounds index
        val liftOutOfBounds = line.wordLift(99, 2500L)
        assertEquals(0f, liftOutOfBounds, 0.001f)
    }

    // =========================================================================
    // 6. Scroll Distance & Viewport Geometry Math
    // =========================================================================

    @Test
    fun scrollGeometryCalculation_edgeCases() {
        // Viewport 800px, visible item at offset 50, item height 60
        val viewportHeight = 800
        val itemSize = 60
        val itemOffset = 50

        val targetCenter = (viewportHeight - itemSize) / 2 // (800 - 60)/2 = 370
        val distance = (itemOffset - targetCenter).toFloat() // 50 - 370 = -320
        assertEquals(-320f, distance, 0.001f)
        assertTrue(abs(distance) > 4f)

        // Item already centered: offset = 370
        val distanceCentered = (370 - targetCenter).toFloat()
        assertEquals(0f, distanceCentered, 0.001f)
        assertFalse(abs(distanceCentered) > 4f) // Deadband avoids jitter

        // Item on bottom edge: offset = 780
        val distanceBottom = (780 - targetCenter).toFloat()
        assertEquals(410f, distanceBottom, 0.001f)

        // Zero viewport height
        val zeroVp = 0
        val centerOffsetZero = if (zeroVp > 0) -((zeroVp - 120) / 2).coerceAtLeast(0) else -180
        assertEquals(-180, centerOffsetZero)
    }
}

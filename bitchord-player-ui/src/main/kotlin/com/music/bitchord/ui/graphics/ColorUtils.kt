package com.music.bitchord.ui.graphics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Color math utilities for HSL and luminance calculations.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object ColorUtils {

    fun colorToHSL(color: Int, outHsl: FloatArray) {
        RGBToHSL((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, outHsl)
    }

    @Suppress("FunctionName")
    fun RGBToHSL(rf: Int, gf: Int, bf: Int, outHsl: FloatArray) {
        val r = rf / 255f
        val g = gf / 255f
        val b = bf / 255f

        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val deltaMaxMin = max - min

        var h: Float
        val s: Float
        val l = (max + min) / 2f

        if (max == min) {
            h = 0f
            s = 0f
        } else {
            h = when (max) {
                r -> ((g - b) / deltaMaxMin) % 6f
                g -> ((b - r) / deltaMaxMin) + 2f
                else -> ((r - g) / deltaMaxMin) + 4f
            }
            s = deltaMaxMin / (1f - abs(2f * l - 1f))
        }

        h = (h * 60f) % 360f
        if (h < 0) h += 360f

        outHsl[0] = h.coerceIn(0f, 360f)
        outHsl[1] = s.coerceIn(0f, 1f)
        outHsl[2] = l.coerceIn(0f, 1f)
    }

    @Suppress("FunctionName")
    fun HSLToColor(hsl: FloatArray): Int {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        val c = (1f - abs(2 * l - 1f)) * s
        val m = l - 0.5f * c
        val x = c * (1f - abs((h / 60f % 2f) - 1f))

        val hueSegment = h.toInt() / 60

        var r = 0f
        var g = 0f
        var b = 0f

        when (hueSegment) {
            0 -> { r = c; g = x; b = 0f }
            1 -> { r = x; g = c; b = 0f }
            2 -> { r = 0f; g = c; b = x }
            3 -> { r = 0f; g = x; b = c }
            4 -> { r = x; g = 0f; b = c }
            5 -> { r = c; g = 0f; b = x }
        }

        val red = ((r + m) * 255f).roundToInt().coerceIn(0, 255)
        val green = ((g + m) * 255f).roundToInt().coerceIn(0, 255)
        val blue = ((b + m) * 255f).roundToInt().coerceIn(0, 255)

        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }

    fun calculateLuminance(color: Int): Double {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0

        val rL = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gL = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bL = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }
}

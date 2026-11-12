package com.music.bitchord.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import kotlin.math.exp

/**
 * Standard cubic bezier easing used across BitChord lyrics transitions for
 * continuous velocity matching without abrupt spring snaps.
 */
val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)

/**
 * Padding inset preventing blur/glow clipping at line item boundaries.
 */
val GLOW_ROOM: Dp = 8.dp

/**
 * Represents an active auto-scroll run with its pixel distance and duration.
 */
data class ScrollRun(
    val id: Int = 0,
    val distance: Float = 0f,
    val spanMs: Int = 400,
)

/**
 * Procedural beat calculation identical to in-pipeline tempo generator.
 * Employs an asymmetric 60ms attack and exponential decay at 120 BPM.
 */
fun calculateProceduralBeat(positionMs: Long): Float {
    if (positionMs < 0L) return 0f
    val periodMs = 500L // 120 BPM
    val phaseMs = positionMs % periodMs
    val attackMs = 60L
    return if (phaseMs < attackMs) {
        (phaseMs.toFloat() / attackMs).coerceIn(0f, 1f)
    } else {
        val decaySpan = (periodMs - attackMs).toFloat()
        val decayTime = (phaseMs - attackMs).toFloat()
        exp(-3.5f * (decayTime / decaySpan)).coerceIn(0f, 1f)
    }
}

/**
 * Calculates the active shadow for a lyric line based on effect style, intensity, and low-power mode.
 */
fun calculateLyricShadow(
    style: LyricsEffectStyle,
    intensity: Float,
    beatPulse: Float,
    accentColor: Color,
    lowPowerMode: Boolean,
): Shadow {
    if (lowPowerMode) {
        return Shadow(
            color = accentColor.copy(alpha = 0.50f),
            offset = Offset(0f, 1f),
            blurRadius = 4f,
        )
    }

    return when (style) {
        LyricsEffectStyle.GLOW -> {
            val radius = (16f * intensity).coerceIn(6f, 32f)
            val alpha = (0.50f * intensity).coerceIn(0.2f, 0.95f)
            Shadow(
                color = accentColor.copy(alpha = alpha),
                offset = Offset.Zero,
                blurRadius = radius,
            )
        }

        LyricsEffectStyle.BEAT_REACTIVE -> {
            val radius = (10f + 14f * beatPulse * intensity).coerceIn(6f, 36f)
            val alpha = (0.35f + 0.60f * beatPulse * intensity).coerceIn(0.2f, 0.95f)
            Shadow(
                color = accentColor.copy(alpha = alpha),
                offset = Offset.Zero,
                blurRadius = radius,
            )
        }

        LyricsEffectStyle.PULSE -> {
            val radius = (10f + 6f * beatPulse * intensity).coerceIn(6f, 22f)
            Shadow(
                color = Color.White.copy(alpha = 0.45f),
                offset = Offset.Zero,
                blurRadius = radius,
            )
        }

        LyricsEffectStyle.FLOW,
        LyricsEffectStyle.KARAOKE -> {
            Shadow(
                color = accentColor.copy(alpha = 0.40f),
                offset = Offset.Zero,
                blurRadius = 12f * intensity.coerceIn(0.5f, 1.5f),
            )
        }
    }
}

/**
 * Draw-time Compose modifier executing visual lyrics effects strictly on RenderNodes.
 * Bypasses Compose recomposition and layout passes to maintain 60fps on Mali-G57 MC2.
 */
fun Modifier.lyricLineVisualEffect(
    isActive: Boolean,
    effectsEnabled: Boolean,
    style: LyricsEffectStyle,
    intensity: Float,
    beatPulseProvider: () -> Float,
    positionMsProvider: () -> Long,
    accentColor: Color,
    lowPowerMode: Boolean,
): Modifier {
    if (!isActive || !effectsEnabled) return this

    return when (style) {
        LyricsEffectStyle.PULSE -> {
            this.graphicsLayer {
                val pulse = beatPulseProvider()
                val scale = if (lowPowerMode) 1.0f else (1.0f + 0.045f * intensity * pulse)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                alpha = if (lowPowerMode) (0.85f + 0.15f * pulse) else 1.0f
            }
        }

        LyricsEffectStyle.BEAT_REACTIVE -> {
            this.graphicsLayer {
                val pulse = beatPulseProvider()
                val scale = if (lowPowerMode) 1.0f else (1.0f + 0.08f * intensity * pulse)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
                alpha = if (lowPowerMode) (0.85f + 0.15f * pulse) else 1.0f
            }
        }

        LyricsEffectStyle.FLOW -> {
            this.drawWithContent {
                drawContent()
                if (!lowPowerMode) {
                    val pos = positionMsProvider()
                    val width = size.width
                    if (width > 0f) {
                        val phase = ((pos % 2400L).toFloat() / 2400f)
                        val sweepX = width * phase
                        val feather = 70.dp.toPx() * intensity.coerceIn(0.5f, 2.0f)
                        val sweepBrush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.85f),
                                accentColor.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                            start = Offset(sweepX - feather, 0f),
                            end = Offset(sweepX + feather, 0f),
                        )
                        drawRect(brush = sweepBrush, blendMode = BlendMode.SrcAtop)
                    }
                }
            }
        }

        LyricsEffectStyle.GLOW -> {
            this.graphicsLayer {
                // Glow is primarily driven by shadow and ambient alpha breathing
                val pulse = beatPulseProvider()
                val scale = if (lowPowerMode) 1.0f else (1.0f + 0.02f * intensity * pulse)
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        }

        LyricsEffectStyle.KARAOKE -> {
            // Word-level karaoke uses progressive syllable highlight and vertical word lift
            this.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
        }
    }
}

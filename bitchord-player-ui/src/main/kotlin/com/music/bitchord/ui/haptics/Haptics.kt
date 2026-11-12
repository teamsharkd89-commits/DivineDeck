package com.music.bitchord.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Haptic patterns for touch feedback.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
enum class Haptic {
    Select,
    Tick,
    Impact,
    LongPress,
    Tap,
    SkipPrevious,
    SkipNext,
    Pause,
    Resume,
    ToggleOn,
    ToggleOff,
}

class Haptics internal constructor(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun play(haptic: Haptic) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (haptic) {
                Haptic.Select, Haptic.Tap -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Haptic.Tick, Haptic.ToggleOff -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                Haptic.Impact, Haptic.ToggleOn -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                Haptic.SkipPrevious, Haptic.SkipNext -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Haptic.Pause, Haptic.Resume -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                Haptic.LongPress -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(25)
        }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}

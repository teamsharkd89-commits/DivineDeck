package com.music.bitchord.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep timer for DIVINE DECK (Phase 06.5E.1).
 *
 * Responsibilities:
 * - Holds a deadline on [SystemClock.elapsedRealtime]; [DivineDeckPlaybackService] watches it
 *   and pauses playback once it passes.
 * - Supports duration-based timer (15, 30, 45, 60 min presets or custom) and "after current track" mode.
 * - Reactive state via [StateFlow] so UI pickers and notifications stay in sync without ticking threads.
 * - Survives player UI dismissal and navigation between Music Center and Cockpit.
 * Extracted and adapted from BitChord 1.8.1 (GPLv3).
 */
object SleepTimer {

    /** Clock source, overridable in unit tests. */
    @Volatile
    var timeProvider: () -> Long = { SystemClock.elapsedRealtime() }

    private val _deadline = MutableStateFlow<Long?>(null)
    /** Deadline on [SystemClock.elapsedRealtime], or null when no duration timer is armed. */
    val deadline: StateFlow<Long?> = _deadline.asStateFlow()

    private val _minutes = MutableStateFlow<Int?>(null)
    /** The preset in minutes that was chosen, so pickers can highlight it. Null when off. */
    val minutes: StateFlow<Int?> = _minutes.asStateFlow()

    private val _afterTrack = MutableStateFlow(false)
    /**
     * Pause when the current track ends instead of after a fixed duration.
     * Evaluated on natural track completion / auto-advance in [DivineDeckPlaybackService].
     */
    val afterTrack: StateFlow<Boolean> = _afterTrack.asStateFlow()

    /** Durations offered in presets (minutes). */
    val PRESETS = listOf(15, 30, 45, 60)

    /** Whether any timer mode is currently active. */
    val isRunning: Boolean get() = _deadline.value != null || _afterTrack.value

    /**
     * Starts a timer for [minutes] minutes.
     */
    fun start(minutes: Int) {
        startWithDurationMs(minutes, minutes * 60_000L)
    }

    /**
     * Starts a timer with custom millisecond duration, optionally associating a preset minute label.
     */
    fun startWithDurationMs(presetMinutes: Int?, durationMs: Long) {
        _afterTrack.value = false
        _minutes.value = presetMinutes
        _deadline.value = timeProvider() + durationMs.coerceAtLeast(0L)
    }

    /**
     * Pauses playback once the track playing right now finishes naturally.
     */
    fun startAfterTrack() {
        _minutes.value = null
        _deadline.value = null
        _afterTrack.value = true
    }

    /**
     * Cancels any armed timer.
     */
    fun cancel() {
        _minutes.value = null
        _deadline.value = null
        _afterTrack.value = false
    }

    /**
     * Returns remaining milliseconds, or null if no duration timer is armed.
     */
    fun remainingMs(): Long? =
        _deadline.value?.let { (it - timeProvider()).coerceAtLeast(0L) }

    /**
     * Resets state and clock provider for deterministic unit testing.
     */
    fun resetForTest() {
        cancel()
        timeProvider = { SystemClock.elapsedRealtime() }
    }
}

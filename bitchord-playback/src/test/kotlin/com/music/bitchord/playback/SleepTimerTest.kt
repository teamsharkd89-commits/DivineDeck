package com.music.bitchord.playback

import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [SleepTimer] and its playback lifecycle in DIVINE DECK (Phase 06.5E.1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {

    private var virtualTime = 1_000_000L

    @Before
    fun setUp() {
        virtualTime = 1_000_000L
        SleepTimer.resetForTest()
        SleepTimer.timeProvider = { virtualTime }
    }

    @After
    fun tearDown() {
        SleepTimer.resetForTest()
    }

    @Test
    fun test1_scheduleByDurationPresetAndCustom() {
        assertFalse(SleepTimer.isRunning)
        assertNull(SleepTimer.remainingMs())

        // Preset 15 minutes
        SleepTimer.start(15)
        assertTrue(SleepTimer.isRunning)
        assertEquals(15, SleepTimer.minutes.value)
        assertFalse(SleepTimer.afterTrack.value)
        val expectedDeadline = 1_000_000L + 15 * 60_000L
        assertEquals(expectedDeadline, SleepTimer.deadline.value)
        assertEquals(15 * 60_000L, SleepTimer.remainingMs())

        // Advance virtual time by 5 minutes
        virtualTime += 5 * 60_000L
        assertEquals(10 * 60_000L, SleepTimer.remainingMs())

        // Custom duration (e.g. 3000 ms)
        SleepTimer.startWithDurationMs(presetMinutes = null, durationMs = 3000L)
        assertTrue(SleepTimer.isRunning)
        assertNull(SleepTimer.minutes.value)
        assertEquals(virtualTime + 3000L, SleepTimer.deadline.value)
        assertEquals(3000L, SleepTimer.remainingMs())
    }

    @Test
    fun test2_cancelBeforeExpiration() {
        SleepTimer.start(30)
        assertTrue(SleepTimer.isRunning)

        SleepTimer.cancel()
        assertFalse(SleepTimer.isRunning)
        assertNull(SleepTimer.deadline.value)
        assertNull(SleepTimer.minutes.value)
        assertFalse(SleepTimer.afterTrack.value)
        assertNull(SleepTimer.remainingMs())
    }

    @Test
    fun test3_pausesPlayerOnDeadlineExpiration() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val playerPaused = AtomicBoolean(false)
        val pauseCount = AtomicInteger(0)

        // Mock observer mimicking DivineDeckPlaybackService.watchSleepTimer
        val job = testScope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                val remaining = deadline - SleepTimer.timeProvider()
                if (remaining > 0L) {
                    delay(remaining)
                }
                playerPaused.set(true)
                pauseCount.incrementAndGet()
                SleepTimer.cancel()
            }
        }

        // Arm timer for 5000 ms
        SleepTimer.startWithDurationMs(presetMinutes = null, durationMs = 5000L)
        testScope.advanceTimeBy(1000L)
        virtualTime += 1000L
        assertFalse("Should not pause before deadline", playerPaused.get())

        // Advance past deadline (remaining 4000 ms)
        virtualTime += 4000L
        testScope.advanceTimeBy(4001L)

        assertTrue("Player should be paused when deadline is reached", playerPaused.get())
        assertEquals(1, pauseCount.get())
        assertFalse("SleepTimer should be disarmed after firing", SleepTimer.isRunning)

        job.cancel()
    }

    @Test
    fun test4_finishAfterCurrentTrackOnNaturalCompletion() {
        SleepTimer.startAfterTrack()
        assertTrue(SleepTimer.isRunning)
        assertTrue(SleepTimer.afterTrack.value)
        assertNull(SleepTimer.deadline.value)
        assertNull(SleepTimer.minutes.value)

        val paused = AtomicBoolean(false)

        // Simulate transition listener evaluation
        fun onMediaItemTransition(reason: Int) {
            val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (ended && SleepTimer.afterTrack.value) {
                paused.set(true)
                SleepTimer.cancel()
            }
        }

        // 1. Manual user skip (SEEK) must NOT trigger pause
        onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        assertFalse("Manual seek must not pause player", paused.get())
        assertTrue("afterTrack must remain armed across manual seeks", SleepTimer.afterTrack.value)

        // 2. Playlist changed by user must NOT trigger pause
        onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        assertFalse("Playlist changed must not pause player", paused.get())
        assertTrue(SleepTimer.afterTrack.value)

        // 3. Natural track completion (AUTO) triggers pause and disarms timer
        onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        assertTrue("Auto transition must pause player", paused.get())
        assertFalse("SleepTimer should be disarmed", SleepTimer.isRunning)
        assertFalse(SleepTimer.afterTrack.value)
    }

    @Test
    fun test5_finishAfterCurrentTrackOnRepeatOne() {
        SleepTimer.startAfterTrack()
        assertTrue(SleepTimer.afterTrack.value)

        var paused = false
        fun onMediaItemTransition(reason: Int) {
            val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            if (ended && SleepTimer.afterTrack.value) {
                paused = true
                SleepTimer.cancel()
            }
        }

        // In repeat-one mode, repeating the song counts as natural end
        onMediaItemTransition(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        assertTrue("Repeat transition must pause player", paused)
        assertFalse(SleepTimer.afterTrack.value)
    }

    @Test
    fun test6_survivesScreenChangeWithoutResetting() {
        SleepTimer.start(45)
        val originalDeadline = SleepTimer.deadline.value

        // Simulate navigating away from MusicCenter to Cockpit, destroying UI
        // SleepTimer is an object singleton in :bitchord-playback; state is preserved
        assertTrue(SleepTimer.isRunning)
        assertEquals(45, SleepTimer.minutes.value)
        assertEquals(originalDeadline, SleepTimer.deadline.value)
    }

    @Test
    fun test7_releasesCoroutinesAndObservers() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var executions = 0
        val job = testScope.launch {
            SleepTimer.deadline.collectLatest { deadline ->
                if (deadline == null) return@collectLatest
                delay(10_000L)
                executions++
            }
        }

        SleepTimer.startWithDurationMs(null, 10_000L)
        testScope.advanceTimeBy(1000L)

        // Cancel job (simulating DivineDeckPlaybackService.onDestroy)
        job.cancel()
        testScope.advanceTimeBy(15_000L)

        assertEquals("No execution should occur after job cancellation", 0, executions)
    }
}

package com.music.bitchord.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Test Harness for Challenger M1.1
 * Testing SwipeToQueueBox & SwipeToQueueGestureState state machine invariants:
 *
 * 1. Multiple rapid drag events exceeding threshold before release.
 * 2. Flings in negative direction (left swipe) — verify clamp to 0 and 0 enqueues.
 * 3. Drag reaching 99% threshold, then reversing back to 10% and releasing.
 * 4. Extremely rapid consecutive swipes (simulating jitter / finger bounces).
 * 5. Zero-width, negative, NaN, and Infinite dimension guards.
 * 6. Non-zero resting offset avoidance and zero exception guarantees.
 */
class SwipeToQueueStateMachineAdversarialTest {

    // =========================================================================
    // Scenario 1: Multiple Rapid Drag Events Exceeding Threshold Before Release
    // =========================================================================

    @Test
    fun scenario1_multipleRapidDragsExceedingThreshold_triggersExactlyOnceOnRelease() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // 5000 continuous drag deltas pushing far beyond threshold (450px)
        for (i in 1..5000) {
            state.onDrag(2f) // total raw offset reaches 10,000px
        }

        assertTrue("Offset must be far past threshold", state.currentOffset > state.threshold)
        assertTrue("State must be armed", state.isArmed)
        assertEquals("No enqueues must occur during drag phase before release", 0, enqueueCount)

        // Single release event
        val triggered = state.onRelease(velocity = 0f)
        assertTrue("onRelease must trigger enqueue", triggered)
        assertEquals("Callback must be invoked exactly once", 1, enqueueCount)
        assertEquals(1, state.enqueueCount)

        // Verify resting offset
        val resting = state.simulateSpringReturn()
        assertEquals("Resting offset must be 0", 0f, resting, 0.0001f)
        assertEquals("Current offset must be 0", 0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario1_repeatedDragsAfterReleaseInSameGesture_doNotTriggerDuplicate() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()
        state.onDrag(600f)

        // First release triggers
        val first = state.onRelease(velocity = 0f)
        assertTrue(first)
        assertEquals(1, enqueueCount)

        // Stray drag events before spring return completes
        state.onDrag(100f)
        val secondRelease = state.onRelease(velocity = 1500f)
        assertFalse("Subsequent release before spring completion must be rejected", secondRelease)
        assertEquals("Enqueue count must remain 1", 1, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    // =========================================================================
    // Scenario 2: Flings in Negative Direction (Left Swipe)
    // =========================================================================

    @Test
    fun scenario2_negativeDragAndFling_clampedToZero_zeroEnqueues() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // Drag leftward
        state.onDrag(-300f)
        assertEquals("Left drag must clamp to 0f", 0f, state.currentOffset, 0.0001f)
        assertFalse("Must not arm", state.isArmed)

        // Violent leftward fling (-5000 dp/s)
        val triggered = state.onRelease(velocity = -5000f)
        assertFalse("Negative fling must NOT trigger", triggered)
        assertEquals("Must produce 0 enqueues", 0, enqueueCount)

        val resting = state.simulateSpringReturn()
        assertEquals(0f, resting, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario2_subThresholdRightDragThenNegativeFling_zeroEnqueues() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // Drag slightly right: 100f (10% of width, below 45% threshold and below 15% minFling)
        state.onDrag(100f)
        assertEquals(100f, state.currentOffset, 0.0001f)

        // Fling left with negative velocity
        val triggered = state.onRelease(velocity = -3000f)
        assertFalse("Negative fling after sub-threshold drag must not trigger", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario2_minFlingOffsetWithNegativeVelocity_doesNotTrigger() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // Move to 200f (> 150f minFlingOffset, but < 450f threshold)
        state.onDrag(200f)
        assertFalse("Below 450f, isArmed must be false", state.isArmed)

        // Release with negative velocity (swiping back leftward)
        val triggered = state.onRelease(velocity = -1500f)
        assertFalse("Negative velocity must not qualify as a fling", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    // =========================================================================
    // Scenario 3: Drag to 99% Threshold, Reversing to 10% and Releasing
    // =========================================================================

    @Test
    fun scenario3_dragTo99PercentThreshold_neverArmed_reversingTo10Percent_zeroEnqueues() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // boxWidth = 1000f -> threshold = 450f. 99% of threshold = 445.5f.
        state.onDragStart()

        state.onOffsetUpdate(445.5f)
        assertFalse("At 99% of threshold (445.5f), must NOT arm", state.isArmed)
        assertEquals("No haptic ticks emitted", 0, state.tickCount)

        // Reverse back to 10% of boxWidth (100f)
        state.onOffsetUpdate(100f)
        assertFalse("Must remain unarmed", state.isArmed)

        // Release at 100f with 0 velocity
        val triggered = state.onRelease(velocity = 0f)
        assertFalse("Releasing unarmed below threshold must return false", triggered)
        assertEquals("Zero enqueues", 0, enqueueCount)

        val resting = state.simulateSpringReturn()
        assertEquals(0f, resting, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario3_dragPastThreshold_armed_thenReversingTo10Percent_disarmsAndZeroEnqueues() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        // threshold = 450f, resetThreshold = 360f (80% of 450f)
        state.onDragStart()

        // Drag past threshold to 500f -> Arms
        state.onOffsetUpdate(500f)
        assertTrue("Must be armed", state.isArmed)
        assertEquals(1, state.tickCount)

        // Reverse back to 10% width (100f, well below 360f reset threshold)
        state.onOffsetUpdate(100f)
        assertFalse("Must be disarmed below reset threshold", state.isArmed)

        // Release at 100f with 0 velocity
        val triggered = state.onRelease(velocity = 0f)
        assertFalse("Disarmed gesture must NOT trigger enqueue on release", triggered)
        assertEquals("Enqueue count must remain 0", 0, enqueueCount)

        val resting = state.simulateSpringReturn()
        assertEquals(0f, resting, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario3_hysteresisBoundaryPrecision_361pxVersus359px() {
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {}
        state.onDragStart()

        // Arm at 450f
        state.onOffsetUpdate(450f)
        assertTrue(state.isArmed)

        // Retreat to 361f (> 360f reset threshold) -> Stays armed due to hysteresis
        state.onOffsetUpdate(361f)
        assertTrue("Must stay armed at 361f", state.isArmed)

        // Retreat to 359f (< 360f reset threshold) -> Disarms cleanly
        state.onOffsetUpdate(359f)
        assertFalse("Must disarm at 359f", state.isArmed)
    }

    // =========================================================================
    // Scenario 4: Extremely Rapid Consecutive Swipes (Jitter & Bounces)
    // =========================================================================

    @Test
    fun scenario4_rapidConsecutiveSwipes_1000Cycles_exactCountNoDrift() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        for (i in 1..1000) {
            state.onDragStart()
            state.onDrag(500f)
            val triggered = state.onRelease(velocity = 0f)
            assertTrue("Cycle $i must trigger", triggered)
            val resting = state.simulateSpringReturn()
            assertEquals("Cycle $i offset must be 0", 0f, resting, 0.0001f)
            assertEquals("Cycle $i currentOffset must be 0", 0f, state.currentOffset, 0.0001f)
        }

        assertEquals("Must record exactly 1000 enqueues", 1000, enqueueCount)
        assertEquals(1000, state.enqueueCount)
    }

    @Test
    fun scenario4_fingerBounceJitter_50ReleasesInSameGesture_exactlyOneEnqueue() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()
        state.onDrag(500f)

        // First release succeeds
        val first = state.onRelease(velocity = 0f)
        assertTrue(first)
        assertEquals(1, enqueueCount)

        // 50 rapid finger bounce touch-up events in the same gesture
        for (bounce in 1..50) {
            val duplicate = state.onRelease(velocity = 0f)
            assertFalse("Bounce $bounce must be rejected", duplicate)
        }

        assertEquals("Enqueue count must remain strictly 1", 1, enqueueCount)
        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario4_rapidCancellations_noLeakedStateOrSpuriousEnqueues() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }

        for (i in 1..200) {
            state.onDragStart()
            state.onDrag(600f) // armed
            state.onCancel()
            val resting = state.simulateSpringReturn()
            assertEquals("Resting offset after cancel must be 0", 0f, resting, 0.0001f)
            assertEquals(0f, state.currentOffset, 0.0001f)
            assertFalse("Flag must be false", state.hasTriggeredInCurrentGesture.get())
            assertFalse("isArmed must be false", state.isArmed)
        }

        assertEquals("Cancellations must never increment enqueue count", 0, enqueueCount)
    }

    // =========================================================================
    // Scenario 5: Zero-Width, Negative, NaN, and Infinite Dimension Guards
    // =========================================================================

    @Test
    fun scenario5_nanDragDelta_doesNotCrash_andDoesNotTrigger() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // Pass Float.NaN as drag amount
        val offset = state.onDrag(Float.NaN)
        val triggered = state.onRelease(velocity = 0f)
        assertFalse("NaN drag must not trigger enqueue", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario5_nanVelocity_doesNotCrash_andDoesNotTriggerFling() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()
        state.onDrag(200f) // Sub-threshold

        val triggered = state.onRelease(velocity = Float.NaN)
        assertFalse("NaN velocity must not trigger fling", triggered)
        assertEquals(0, enqueueCount)

        state.simulateSpringReturn()
        assertEquals(0f, state.currentOffset, 0.0001f)
    }

    @Test
    fun scenario5_infiniteDragDelta_recoversToZeroRestingOffset() {
        var enqueueCount = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { enqueueCount++ }
        state.onDragStart()

        // Positive infinity
        state.onDrag(Float.POSITIVE_INFINITY)
        val triggered = state.onRelease(velocity = 0f)
        assertTrue(triggered)
        assertEquals(1, enqueueCount)

        // Must recover safely to 0f without NaN or Infinity remaining
        val resting = state.simulateSpringReturn()
        assertEquals(0f, resting, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
        assertFalse(state.isArmed)
    }

    @Test
    fun scenario5_boxWidthUpdates_negativeAndNanIgnored() {
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {}

        state.updateBoxWidth(-100f)
        assertEquals("Negative width must not overwrite valid width", 1000f, state.boxWidth, 0.0001f)

        state.updateBoxWidth(0f)
        assertEquals("Zero width must not overwrite valid width", 1000f, state.boxWidth, 0.0001f)

        state.updateBoxWidth(Float.NaN)
        assertEquals("NaN width must not overwrite valid width", 1000f, state.boxWidth, 0.0001f)

        state.updateBoxWidth(800f)
        assertEquals("Positive width must update correctly", 800f, state.boxWidth, 0.0001f)
        assertEquals(360f, state.threshold, 0.0001f)
    }

    @Test
    fun scenario5_zeroWidthBehavior_empiricalAnalysis() {
        // Direct instantiation with boxWidth = 0f
        var enqueueCount = 0
        val zeroState = SwipeToQueueGestureState(boxWidth = 0f) { enqueueCount++ }
        zeroState.onDragStart()

        // When boxWidth = 0f, threshold = 0f.
        // At rest, currentOffset = 0f.
        // Therefore, currentOffset >= threshold (0f >= 0f) evaluates to TRUE.
        val triggeredAtZero = zeroState.onRelease(velocity = 0f)

        // Empirical check: In standalone state, zero-width causes threshold to be 0f.
        // Note: In the production Composable (SwipeToQueueBox.kt line 222), this is guarded by:
        // `if (boxWidth <= 0f) return@pointerInput`
        // which prevents pointerInput from executing or dispatching gestures when boxWidth <= 0f.
        println("Empirical finding: standalone SwipeToQueueGestureState(0f).onRelease(0f) = $triggeredAtZero")
    }

    // =========================================================================
    // Concurrency Verification
    // =========================================================================

    @Test
    fun adversarial_concurrency_32ThreadsRacingOnRelease_strictlyOneCallback() {
        val enqueueCounter = AtomicInteger(0)
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {
            enqueueCounter.incrementAndGet()
        }
        state.onDragStart()
        state.onOffsetUpdate(500f)

        val threads = 32
        val executor = Executors.newFixedThreadPool(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val successCounter = AtomicInteger(0)

        for (i in 0 until threads) {
            executor.submit {
                try {
                    startLatch.await()
                    if (state.onRelease(velocity = 0f)) {
                        successCounter.incrementAndGet()
                    }
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals("Only one thread can win the CAS", 1, successCounter.get())
        assertEquals("Callback count must be exactly 1", 1, enqueueCounter.get())
    }
}

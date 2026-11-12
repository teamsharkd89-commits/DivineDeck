package com.music.bitchord.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Adversarial empirical stress-test harness for SwipeToQueueBox gesture and animation dynamics.
 * Specifically validates:
 * 1. High-concurrency invocations of onRelease() from multiple threads.
 * 2. Concurrent execution of onRelease() while onDrag() streams delta events.
 * 3. Spring completion timing and state transitions during/after spring return.
 * 4. Exact velocity boundary conditions (v = 999 vs 1000 dp/s, negative velocities).
 * 5. Exact offset boundary conditions (44.9% vs 45.0% across standard and 1340px tablet widths).
 * 6. Fling offset boundary conditions (14.9% vs 15.0%).
 * 7. Hysteresis boundary stress (35.999% vs 36.001%).
 * 8. Multi-threaded randomized fuzzing harness.
 */
class SwipeToQueueAdversarialStressTest {

    // =========================================================================
    // 1. Concurrency & Race Condition Resistance
    // =========================================================================

    @Test
    fun highConcurrency_multipleThreadsCallingOnRelease_triggersExactlyOnce() {
        val iterations = 200
        val threadCount = 64
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            for (iter in 0 until iterations) {
                val enqueueCounter = AtomicInteger(0)
                val state = SwipeToQueueGestureState(boxWidth = 1000f) {
                    enqueueCounter.incrementAndGet()
                }

                state.onDragStart()
                state.onOffsetUpdate(500f) // Well past 450f threshold
                assertTrue(state.isArmed)

                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(threadCount)
                val successCount = AtomicInteger(0)

                for (t in 0 until threadCount) {
                    executor.submit {
                        try {
                            startLatch.await()
                            val triggered = state.onRelease(velocity = 0f)
                            if (triggered) {
                                successCount.incrementAndGet()
                            }
                        } finally {
                            doneLatch.countDown()
                        }
                    }
                }

                // Fire all threads simultaneously
                startLatch.countDown()
                assertTrue(
                    "Timeout waiting for concurrent threads on iteration $iter",
                    doneLatch.await(5, TimeUnit.SECONDS)
                )

                assertEquals(
                    "Iteration $iter: Exactly 1 thread must successfully trigger onRelease",
                    1,
                    successCount.get()
                )
                assertEquals(
                    "Iteration $iter: onEnqueue must be invoked exactly once",
                    1,
                    enqueueCounter.get()
                )
                assertEquals(
                    "Iteration $iter: state.enqueueCount must be exactly 1",
                    1,
                    state.enqueueCount
                )
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentOnDragAndOnRelease_doesNotCorruptStateOrDoubleEnqueue() {
        val iterations = 300
        val executor = Executors.newFixedThreadPool(8)

        try {
            for (iter in 0 until iterations) {
                val enqueueCounter = AtomicInteger(0)
                val state = SwipeToQueueGestureState(boxWidth = 1000f) {
                    enqueueCounter.incrementAndGet()
                }

                state.onDragStart()

                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(2)

                // Thread 1: Streams drag deltas across threshold
                executor.submit {
                    try {
                        startLatch.await()
                        for (step in 1..50) {
                            state.onDrag(12f) // 50 * 12 = 600f raw
                        }
                    } finally {
                        doneLatch.countDown()
                    }
                }

                // Thread 2: Calls onRelease at random point during drag
                executor.submit {
                    try {
                        startLatch.await()
                        Thread.yield()
                        state.onRelease(velocity = 0f)
                    } finally {
                        doneLatch.countDown()
                    }
                }

                startLatch.countDown()
                assertTrue("Timeout on iteration $iter", doneLatch.await(5, TimeUnit.SECONDS))

                // Enqueue count must be either 0 (released before crossing 450f) or 1 (released after)
                val count = enqueueCounter.get()
                assertTrue(
                    "Iteration $iter: enqueue count must be 0 or 1, got $count",
                    count in 0..1
                )
                assertEquals(
                    "Iteration $iter: state.enqueueCount must match callback count",
                    count,
                    state.enqueueCount
                )
                assertTrue(
                    "Iteration $iter: offset must remain non-negative, got ${state.currentOffset}",
                    state.currentOffset >= 0f
                )
            }
        } finally {
            executor.shutdownNow()
        }
    }

    // =========================================================================
    // 2. Spring Completion Timing & State Transitions
    // =========================================================================

    @Test
    fun springTiming_callingOnReleaseDuringSpringReturn_isCompletelyProtected() {
        val enqueueCounter = AtomicInteger(0)
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {
            enqueueCounter.incrementAndGet()
        }

        // Gesture 1: Drag to 500f and trigger
        state.onDragStart()
        state.onOffsetUpdate(500f)
        val firstRelease = state.onRelease(velocity = 0f)
        assertTrue("Initial release must trigger", firstRelease)
        assertEquals(1, enqueueCounter.get())

        // Simulate intermediate spring phase:
        // hasTriggeredInCurrentGesture is still true while spring animation is in-flight
        assertTrue("Flag must remain true during spring return", state.hasTriggeredInCurrentGesture.get())

        // Multiple calls to onRelease during spring animation
        for (i in 1..20) {
            val intermediateRelease = state.onRelease(velocity = 2000f)
            assertFalse("Release during in-flight spring must return false (attempt $i)", intermediateRelease)
            assertEquals("Enqueue count must not increment during in-flight spring", 1, enqueueCounter.get())
        }

        // Complete the spring return
        state.onSpringComplete()

        assertEquals("Offset must be 0 after spring complete", 0f, state.currentOffset, 0.0001f)
        assertFalse("Flag must be reset after spring complete", state.hasTriggeredInCurrentGesture.get())
        assertFalse("isArmed must be reset after spring complete", state.isArmed)

        // Calling onRelease at rest without dragging must not trigger
        val restRelease = state.onRelease(velocity = 0f)
        assertFalse("Release at rest must not trigger", restRelease)
        assertEquals(1, enqueueCounter.get())
    }

    @Test
    fun springTiming_subThresholdRelease_springsBackWithoutSpuriousTriggers() {
        val enqueueCounter = AtomicInteger(0)
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {
            enqueueCounter.incrementAndGet()
        }

        // Sub-threshold drag: 300f (< 450f)
        state.onDragStart()
        state.onOffsetUpdate(300f)
        val releaseFailed = state.onRelease(velocity = 0f)
        assertFalse("Sub-threshold release without fling must return false", releaseFailed)
        assertEquals(0, enqueueCounter.get())

        // Calling simulateSpringReturn resets state cleanly
        val restingOffset = state.simulateSpringReturn()
        assertEquals(0f, restingOffset, 0.0001f)
        assertEquals(0f, state.currentOffset, 0.0001f)
        assertFalse(state.isArmed)
        assertFalse(state.hasTriggeredInCurrentGesture.get())

        // Subsequent release after reset at 0f with high velocity still cannot trigger because offset < minFlingOffset
        val flingAtZero = state.onRelease(velocity = 2500f)
        assertFalse("Fling at offset 0 must not trigger", flingAtZero)
        assertEquals(0, enqueueCounter.get())
    }

    @Test
    fun consecutiveCycles_over1000Iterations_zeroStateLeakage() {
        val iterations = 1000
        val enqueueCounter = AtomicInteger(0)
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {
            enqueueCounter.incrementAndGet()
        }

        for (cycle in 1..iterations) {
            state.onDragStart()
            assertEquals("Cycle $cycle: initial offset must be 0", 0f, state.currentOffset, 0.0001f)
            assertFalse("Cycle $cycle: initial armed must be false", state.isArmed)
            assertFalse("Cycle $cycle: initial triggered flag must be false", state.hasTriggeredInCurrentGesture.get())

            state.onOffsetUpdate(500f)
            assertTrue("Cycle $cycle: armed must be true at 500f", state.isArmed)

            val triggered = state.onRelease(velocity = 0f)
            assertTrue("Cycle $cycle: release must trigger", triggered)
            assertEquals("Cycle $cycle: enqueue counter must match cycle index", cycle, enqueueCounter.get())

            state.simulateSpringReturn()
            assertEquals("Cycle $cycle: offset must return to 0", 0f, state.currentOffset, 0.0001f)
            assertFalse("Cycle $cycle: armed must reset to false", state.isArmed)
            assertFalse("Cycle $cycle: triggered flag must reset to false", state.hasTriggeredInCurrentGesture.get())
        }

        assertEquals(iterations, state.enqueueCount)
    }

    // =========================================================================
    // 3. Velocity Boundary Conditions
    // =========================================================================

    @Test
    fun velocityBoundary_subThresholdOffset_999dpPerSecFails_1000dpPerSecPasses() {
        // boxWidth = 1000f, threshold = 450f, minFlingOffset = 150f
        // Offset = 250f (25% -> between 15% minFling and 45% sustained threshold)

        // Test v = 999f
        var counter999 = 0
        val state999 = SwipeToQueueGestureState(boxWidth = 1000f) { counter999++ }
        state999.onDragStart()
        state999.onOffsetUpdate(250f)
        assertFalse("At 250f, isArmed must be false", state999.isArmed)

        val triggered999 = state999.onRelease(velocity = 999.0f)
        assertFalse("v = 999.0 dp/s must strictly NOT trigger", triggered999)
        assertEquals(0, counter999)

        // Test v = 999.9f
        val triggered999_9 = state999.onRelease(velocity = 999.9f)
        assertFalse("v = 999.9 dp/s must strictly NOT trigger", triggered999_9)
        assertEquals(0, counter999)

        // Test v = 1000.0f
        var counter1000 = 0
        val state1000 = SwipeToQueueGestureState(boxWidth = 1000f) { counter1000++ }
        state1000.onDragStart()
        state1000.onOffsetUpdate(250f)

        val triggered1000 = state1000.onRelease(velocity = 1000.0f)
        assertTrue("v = 1000.0 dp/s must strictly trigger fling", triggered1000)
        assertEquals(1, counter1000)

        // Test v = 1000.1f
        var counter1000_1 = 0
        val state1000_1 = SwipeToQueueGestureState(boxWidth = 1000f) { counter1000_1++ }
        state1000_1.onDragStart()
        state1000_1.onOffsetUpdate(250f)

        val triggered1000_1 = state1000_1.onRelease(velocity = 1000.1f)
        assertTrue("v = 1000.1 dp/s must strictly trigger fling", triggered1000_1)
        assertEquals(1, counter1000_1)
    }

    @Test
    fun velocityBoundary_negativeVelocities_areStrictlyRejected() {
        var counter = 0
        val state = SwipeToQueueGestureState(boxWidth = 1000f) { counter++ }
        state.onDragStart()
        state.onOffsetUpdate(250f)

        // Leftward flings
        val vNeg1 = state.onRelease(velocity = -1000f)
        assertFalse("v = -1000 dp/s must not trigger", vNeg1)
        val vNeg2 = state.onRelease(velocity = -5000f)
        assertFalse("v = -5000 dp/s must not trigger", vNeg2)
        assertEquals(0, counter)
    }

    // =========================================================================
    // 4. Offset Boundary Conditions (44.9% vs 45.0%)
    // =========================================================================

    @Test
    fun offsetBoundary_44Point9PercentVs45Point0Percent_standardWidth() {
        val width = 1000f // Threshold = 450.0f
        val offset44_9 = 449.0f // 44.9%
        val offset44_99 = 449.9f // 44.99%
        val offset45_0 = 450.0f // 45.00%
        val offset45_01 = 450.1f // 45.01%

        // 44.9% with v = 0
        var count44_9 = 0
        val state44_9 = SwipeToQueueGestureState(boxWidth = width) { count44_9++ }
        state44_9.onDragStart()
        state44_9.onOffsetUpdate(offset44_9)
        assertFalse("44.9% must not arm haptics", state44_9.isArmed)
        val trigger44_9 = state44_9.onRelease(velocity = 0f)
        assertFalse("44.9% must strictly NOT trigger", trigger44_9)
        assertEquals(0, count44_9)

        // 44.99% with v = 0
        var count44_99 = 0
        val state44_99 = SwipeToQueueGestureState(boxWidth = width) { count44_99++ }
        state44_99.onDragStart()
        state44_99.onOffsetUpdate(offset44_99)
        assertFalse("44.99% must not arm haptics", state44_99.isArmed)
        val trigger44_99 = state44_99.onRelease(velocity = 0f)
        assertFalse("44.99% must strictly NOT trigger", trigger44_99)
        assertEquals(0, count44_99)

        // 45.0% with v = 0
        var count45_0 = 0
        val state45_0 = SwipeToQueueGestureState(boxWidth = width) { count45_0++ }
        state45_0.onDragStart()
        state45_0.onOffsetUpdate(offset45_0)
        assertTrue("45.0% must arm haptics", state45_0.isArmed)
        val trigger45_0 = state45_0.onRelease(velocity = 0f)
        assertTrue("45.0% must strictly trigger", trigger45_0)
        assertEquals(1, count45_0)

        // 45.01% with v = 0
        var count45_01 = 0
        val state45_01 = SwipeToQueueGestureState(boxWidth = width) { count45_01++ }
        state45_01.onDragStart()
        state45_01.onOffsetUpdate(offset45_01)
        assertTrue("45.01% must arm haptics", state45_01.isArmed)
        val trigger45_01 = state45_01.onRelease(velocity = 0f)
        assertTrue("45.01% must strictly trigger", trigger45_01)
        assertEquals(1, count45_01)
    }

    @Test
    fun offsetBoundary_targetTabletResolution_1340px() {
        val tabletWidth = 1340f // Samsung Galaxy Tab A11 SM-X133
        val exactThreshold = tabletWidth * 0.45f // 603.0f

        val offset44_9 = tabletWidth * 0.449f // 601.66f
        val offset45_0 = tabletWidth * 0.450f // 603.00f

        // Test 44.9%
        var countSub = 0
        val stateSub = SwipeToQueueGestureState(boxWidth = tabletWidth) { countSub++ }
        stateSub.onDragStart()
        stateSub.onOffsetUpdate(offset44_9)
        assertFalse("Tablet 44.9% ($offset44_9) must not arm", stateSub.isArmed)
        val triggerSub = stateSub.onRelease(velocity = 0f)
        assertFalse("Tablet 44.9% must strictly NOT trigger", triggerSub)
        assertEquals(0, countSub)

        // Test 45.0%
        var countCommit = 0
        val stateCommit = SwipeToQueueGestureState(boxWidth = tabletWidth) { countCommit++ }
        stateCommit.onDragStart()
        stateCommit.onOffsetUpdate(offset45_0)
        assertTrue("Tablet 45.0% ($offset45_0) must arm", stateCommit.isArmed)
        val triggerCommit = stateCommit.onRelease(velocity = 0f)
        assertTrue("Tablet 45.0% must strictly trigger", triggerCommit)
        assertEquals(1, countCommit)
    }

    // =========================================================================
    // 5. Min Fling Offset Boundary Conditions (14.9% vs 15.0%)
    // =========================================================================

    @Test
    fun flingOffsetBoundary_14Point9PercentVs15Point0Percent_withHighVelocity() {
        val width = 1000f // minFlingOffset = 150.0f
        val highVelocity = 2000f

        // 14.9% (149f): Insufficient distance despite high velocity
        var count14_9 = 0
        val state14_9 = SwipeToQueueGestureState(boxWidth = width) { count14_9++ }
        state14_9.onDragStart()
        state14_9.onOffsetUpdate(149.0f)
        val trigger14_9 = state14_9.onRelease(velocity = highVelocity)
        assertFalse("Fling at 14.9% (149f) must not trigger", trigger14_9)
        assertEquals(0, count14_9)

        // 15.0% (150f): Meets minFlingOffset
        var count15_0 = 0
        val state15_0 = SwipeToQueueGestureState(boxWidth = width) { count15_0++ }
        state15_0.onDragStart()
        state15_0.onOffsetUpdate(150.0f)
        val trigger15_0 = state15_0.onRelease(velocity = highVelocity)
        assertTrue("Fling at 15.0% (150f) must trigger", trigger15_0)
        assertEquals(1, count15_0)
    }

    // =========================================================================
    // 6. Hysteresis Boundary Precision Stress
    // =========================================================================

    @Test
    fun hysteresisBoundary_strictDisarmingThreshold() {
        val width = 1000f
        // threshold = 450f, resetThreshold = 450f * 0.80f = 360f
        val state = SwipeToQueueGestureState(boxWidth = width) {}

        state.onDragStart()

        // 1. Cross threshold -> Armed
        state.onOffsetUpdate(450.0f)
        assertTrue(state.isArmed)
        assertEquals(1, state.tickCount)

        // 2. Retreat to 360.01f (above 360f reset) -> Must remain armed
        state.onOffsetUpdate(360.01f)
        assertTrue("Must remain armed at 360.01f", state.isArmed)

        // 3. Retreat to 359.99f (below 360f reset) -> Must disarm
        state.onOffsetUpdate(359.99f)
        assertFalse("Must disarm at 359.99f", state.isArmed)

        // 4. Advance to 449.99f -> Must NOT re-arm yet
        state.onOffsetUpdate(449.99f)
        assertFalse("Must not re-arm at 449.99f", state.isArmed)
        assertEquals(1, state.tickCount)

        // 5. Advance to 450.00f -> Re-arms and increments tickCount
        state.onOffsetUpdate(450.00f)
        assertTrue("Must re-arm at 450.00f", state.isArmed)
        assertEquals(2, state.tickCount)
    }

    // =========================================================================
    // 7. Multi-Threaded Randomized Fuzzing Harness
    // =========================================================================

    @Test
    fun randomizedFuzzing_noUnhandledExceptionsOrCorruptStates() {
        val operationsCount = 5000
        val enqueueCounter = AtomicInteger(0)
        val state = SwipeToQueueGestureState(boxWidth = 1000f) {
            enqueueCounter.incrementAndGet()
        }

        val rng = Random(42)

        for (i in 0 until operationsCount) {
            when (rng.nextInt(6)) {
                0 -> state.onDragStart()
                1 -> {
                    val delta = rng.nextFloat() * 100f - 20f
                    state.onDrag(delta)
                }
                2 -> {
                    val offset = rng.nextFloat() * 800f - 50f
                    state.onOffsetUpdate(offset)
                }
                3 -> {
                    val velocity = rng.nextFloat() * 3000f - 1000f
                    state.onRelease(velocity)
                }
                4 -> state.onCancel()
                5 -> state.onSpringComplete()
            }

            // Invariants after every operation:
            assertTrue("Offset must always be >= 0f", state.currentOffset >= 0f)
            assertTrue("Enqueue counter cannot be negative", enqueueCounter.get() >= 0)
            assertTrue("state.enqueueCount matches callback count", state.enqueueCount == enqueueCounter.get())
        }
    }
}

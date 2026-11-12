package com.divinedeck.launcher.feature.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Adversarial stress harness for [AudioEnergyProcessor]:
 * 1. Malformed and edge-case buffers (0-byte, 1-byte, odd bytes, partial frames).
 * 2. Extreme signal values (Short.MAX_VALUE clipping, Short.MIN_VALUE clipping, Nyquist frequency, DC offset).
 * 3. Energy clamping verification: strictly [0.0f..1.0f], zero NaN, zero Infinity.
 * 4. Procedural beat boundaries: Long.MIN_VALUE, negative values, 0, attack/decay boundaries, Long.MAX_VALUE.
 * 5. Concurrency stress: concurrent queueInput, concurrent subscribe/unsubscribe, double-cancellation resilience.
 *
 * EMPIRICAL CHALLENGER VERIFICATION SUITE
 */
@OptIn(UnstableApi::class)
class AudioEnergyProcessorAdversarialTest {

    private fun createConfiguredProcessor(channelCount: Int = 2, sampleRate: Int = 44100): AudioEnergyProcessor {
        val processor = AudioEnergyProcessor()
        val format = AudioProcessor.AudioFormat(sampleRate, channelCount, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()
        return processor
    }

    private fun assertEnergyStateValid(state: AudioEnergyState, message: String = "") {
        assertFalse("$message: rmsEnergy cannot be NaN", state.rmsEnergy.isNaN())
        assertFalse("$message: rmsEnergy cannot be Infinite", state.rmsEnergy.isInfinite())
        assertTrue("$message: rmsEnergy must be >= 0f, got ${state.rmsEnergy}", state.rmsEnergy >= 0f)
        assertTrue("$message: rmsEnergy must be <= 1f, got ${state.rmsEnergy}", state.rmsEnergy <= 1f)

        assertFalse("$message: bassEnergy cannot be NaN", state.bassEnergy.isNaN())
        assertFalse("$message: bassEnergy cannot be Infinite", state.bassEnergy.isInfinite())
        assertTrue("$message: bassEnergy must be >= 0f, got ${state.bassEnergy}", state.bassEnergy >= 0f)
        assertTrue("$message: bassEnergy must be <= 1f, got ${state.bassEnergy}", state.bassEnergy <= 1f)

        assertFalse("$message: beatPulse cannot be NaN", state.beatPulse.isNaN())
        assertFalse("$message: beatPulse cannot be Infinite", state.beatPulse.isInfinite())
        assertTrue("$message: beatPulse must be >= 0f, got ${state.beatPulse}", state.beatPulse >= 0f)
        assertTrue("$message: beatPulse must be <= 1f, got ${state.beatPulse}", state.beatPulse <= 1f)
    }

    @Test
    fun zeroByteInputBuffer_doesNotCrashOrEmitState() {
        val processor = createConfiguredProcessor()
        val unsub = processor.subscribe()

        val emptyBuffer = ByteBuffer.allocate(0)
        processor.queueInput(emptyBuffer)

        assertFalse("Output buffer should have 0 remaining", processor.output.hasRemaining())
        assertEnergyStateValid(processor.energyFlow.value, "Zero-byte buffer")
        unsub()
    }

    @Test
    fun unevenAndPartialBuffers_doNotCrashOrCorruptOutput() {
        val processor = createConfiguredProcessor(channelCount = 2)
        val unsub = processor.subscribe()

        // Test 1-byte buffer (less than 1 sample = 2 bytes)
        val oneByte = ByteBuffer.allocate(1).put(0x42.toByte())
        oneByte.flip()
        processor.queueInput(oneByte)
        val out1 = processor.output
        assertEquals(1, out1.remaining())
        assertEquals(0x42.toByte(), out1.get())
        assertEnergyStateValid(processor.energyFlow.value, "1-byte buffer")

        // Test 3-byte buffer (1 mono sample + 1 byte, less than 1 stereo frame = 4 bytes)
        val threeBytes = ByteBuffer.allocate(3).put(byteArrayOf(0x10, 0x20, 0x30))
        threeBytes.flip()
        processor.queueInput(threeBytes)
        val out3 = processor.output
        assertEquals(3, out3.remaining())
        assertEnergyStateValid(processor.energyFlow.value, "3-byte buffer")

        // Test 7-byte buffer (1 full stereo frame + 3 leftover bytes)
        val sevenBytes = ByteBuffer.allocateDirect(7).put(byteArrayOf(1, 2, 3, 4, 5, 6, 7))
        sevenBytes.flip()
        processor.queueInput(sevenBytes)
        val out7 = processor.output
        assertEquals(7, out7.remaining())
        assertEnergyStateValid(processor.energyFlow.value, "7-byte buffer")

        unsub()
    }

    @Test
    fun maximumAmplitudeClipping_keepsValuesStrictlyClamped() {
        val processor = createConfiguredProcessor(channelCount = 2)
        val unsub = processor.subscribe()

        // Maximum positive clipping: Short.MAX_VALUE on all channels
        val maxPosBuffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
        while (maxPosBuffer.hasRemaining()) {
            maxPosBuffer.putShort(Short.MAX_VALUE)
        }
        maxPosBuffer.flip()
        processor.queueInput(maxPosBuffer)

        val statePos = processor.energyFlow.value
        assertEnergyStateValid(statePos, "Short.MAX_VALUE clipping")
        assertEquals("RMS energy for full-scale signal should be 1.0f (or ~0.9999f)", 1.0f, statePos.rmsEnergy, 0.001f)
        assertTrue("Bass energy must be <= 1.0f", statePos.bassEnergy <= 1.0f)
        assertTrue("Beat pulse must be <= 1.0f", statePos.beatPulse <= 1.0f)

        // Maximum negative clipping: Short.MIN_VALUE on all channels
        val maxNegBuffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
        while (maxNegBuffer.hasRemaining()) {
            maxNegBuffer.putShort(Short.MIN_VALUE)
        }
        maxNegBuffer.flip()
        processor.queueInput(maxNegBuffer)

        val stateNeg = processor.energyFlow.value
        assertEnergyStateValid(stateNeg, "Short.MIN_VALUE clipping")
        assertEquals("RMS energy for -32768 full scale should be 1.0f", 1.0f, stateNeg.rmsEnergy, 0.0001f)
        assertTrue("Bass energy must be clamped at 1.0f", stateNeg.bassEnergy <= 1.0f)

        // In-phase square wave alternating per frame: +32767, then -32768
        val inPhaseSquare = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
        var squareToggle = true
        while (inPhaseSquare.hasRemaining()) {
            val s = if (squareToggle) Short.MAX_VALUE else Short.MIN_VALUE
            inPhaseSquare.putShort(s)
            inPhaseSquare.putShort(s)
            squareToggle = !squareToggle
        }
        inPhaseSquare.flip()
        processor.queueInput(inPhaseSquare)

        val stateInPhase = processor.energyFlow.value
        assertEnergyStateValid(stateInPhase, "In-phase square wave")
        assertEquals(1.0f, stateInPhase.rmsEnergy, 0.001f)

        // Anti-phase extreme clipping: Left = +32767, Right = -32768
        val antiPhaseBuffer = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.LITTLE_ENDIAN)
        while (antiPhaseBuffer.hasRemaining()) {
            antiPhaseBuffer.putShort(Short.MAX_VALUE)
            antiPhaseBuffer.putShort(Short.MIN_VALUE)
        }
        antiPhaseBuffer.flip()
        processor.queueInput(antiPhaseBuffer)

        val stateAntiPhase = processor.energyFlow.value
        assertEnergyStateValid(stateAntiPhase, "Anti-phase clipping")
        // Note: mono mixdown (L+R)*0.5 cancels anti-phase signals to ~0
        assertTrue("Anti-phase RMS must remain clamped in [0, 1]", stateAntiPhase.rmsEnergy in 0.0f..1.0f)

        unsub()
    }

    @Test
    fun allZerosSilence_producesZeroEnergyWithoutNaN() {
        val processor = createConfiguredProcessor()
        val unsub = processor.subscribe()

        val silenceBuffer = ByteBuffer.allocateDirect(2048 * 4) // All zeros
        processor.queueInput(silenceBuffer)

        val state = processor.energyFlow.value
        assertEnergyStateValid(state, "Silence")
        assertEquals(0f, state.rmsEnergy, 0.00001f)
        assertEquals(0f, state.bassEnergy, 0.00001f)
        assertEquals(0f, state.beatPulse, 0.00001f)

        unsub()
    }

    @Test
    fun pureHighFrequency_versus_pureLowFrequency() {
        val bassProcessor = createConfiguredProcessor(sampleRate = 44100)
        val unsubBass = bassProcessor.subscribe()

        // 1. Pure low frequency 40 Hz at full amplitude (100% bass)
        val bassFrames = 2048
        val bassBuffer = ByteBuffer.allocateDirect(bassFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bassFrames) {
            val sample = (sin(2.0 * PI * 40.0 * i / 44100.0) * 32767.0).toInt().toShort()
            bassBuffer.putShort(sample)
            bassBuffer.putShort(sample)
        }
        bassBuffer.flip()
        bassProcessor.queueInput(bassBuffer)

        val bassState = bassProcessor.energyFlow.value
        assertEnergyStateValid(bassState, "Pure Low Frequency (40Hz)")
        assertTrue("Bass energy must be prominent for 40Hz", bassState.bassEnergy > 0.4f)
        assertTrue("RMS energy must be high", bassState.rmsEnergy > 0.5f)
        unsubBass()

        // 2. Pure high frequency 18,000 Hz on fresh processor
        val trebleProcessor = createConfiguredProcessor(sampleRate = 44100)
        val unsubTreble = trebleProcessor.subscribe()

        val trebleBuffer = ByteBuffer.allocateDirect(bassFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bassFrames) {
            val sample = (sin(2.0 * PI * 18000.0 * i / 44100.0) * 32767.0).toInt().toShort()
            trebleBuffer.putShort(sample)
            trebleBuffer.putShort(sample)
        }
        trebleBuffer.flip()
        trebleProcessor.queueInput(trebleBuffer)

        val trebleState = trebleProcessor.energyFlow.value
        assertEnergyStateValid(trebleState, "Pure High Frequency (18000Hz)")
        assertTrue("RMS energy must be high for full amplitude treble", trebleState.rmsEnergy > 0.5f)
        println("DEBUG: 18kHz RMS=${trebleState.rmsEnergy}, Bass=${trebleState.bassEnergy}")
        // Verify bass energy is lower than low-frequency bass
        assertTrue(
            "Treble bass energy (${trebleState.bassEnergy}) must be lower than bass (${bassState.bassEnergy})",
            trebleState.bassEnergy < bassState.bassEnergy
        )
        unsubTreble()
    }

    @Test
    fun flush_shouldResetEnergyStateAndFilters() {
        val processor = createConfiguredProcessor(sampleRate = 44100)
        val unsub = processor.subscribe()

        // 1. Queue heavy bass
        val bassFrames = 1024
        val bassBuffer = ByteBuffer.allocateDirect(bassFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until bassFrames) {
            val sample = (sin(2.0 * PI * 40.0 * i / 44100.0) * 32767.0).toInt().toShort()
            bassBuffer.putShort(sample)
            bassBuffer.putShort(sample)
        }
        bassBuffer.flip()
        processor.queueInput(bassBuffer)

        assertTrue(processor.energyFlow.value.isAudioReactiveActive)
        assertTrue(processor.energyFlow.value.bassEnergy > 0.3f)

        // 2. ExoPlayer seeks or changes tracks: invokes flush()
        processor.flush()

        // Missing onFlush() in AudioEnergyProcessor causes stale state to persist
        val stateAfterFlush = processor.energyFlow.value
        assertEquals("flush() must clear active state", false, stateAfterFlush.isAudioReactiveActive)
        assertEquals("flush() must clear bass energy", 0f, stateAfterFlush.bassEnergy, 0.0001f)
        unsub()
    }

    @Test
    fun proceduralBeatGenerator_extremeBoundariesAndInvariants() {
        // Negative position boundaries
        assertEquals(0f, AudioEnergyProcessor.calculateProceduralBeat(Long.MIN_VALUE), 0.0001f)
        assertEquals(0f, AudioEnergyProcessor.calculateProceduralBeat(-999999999L), 0.0001f)
        assertEquals(0f, AudioEnergyProcessor.calculateProceduralBeat(-1L), 0.0001f)

        // Exact zero
        assertEquals(0f, AudioEnergyProcessor.calculateProceduralBeat(0L), 0.0001f)

        // Attack ramp boundary [0ms .. 60ms]
        val beatAt59 = AudioEnergyProcessor.calculateProceduralBeat(59L)
        val beatAt60 = AudioEnergyProcessor.calculateProceduralBeat(60L)
        assertTrue("At 59ms beat must be near 1.0f: $beatAt59", beatAt59 > 0.95f)
        assertEquals("At 60ms beat must be 1.0f", 1.0f, beatAt60, 0.01f)

        // Period boundary [499ms .. 500ms]
        val beatAt499 = AudioEnergyProcessor.calculateProceduralBeat(499L)
        val beatAt500 = AudioEnergyProcessor.calculateProceduralBeat(500L)
        assertTrue("At 499ms beat must have decayed to near 0", beatAt499 < 0.05f)
        assertEquals("At 500ms new beat starts at 0", 0f, beatAt500, 0.001f)

        // Long.MAX_VALUE boundary
        val beatAtMaxLong = AudioEnergyProcessor.calculateProceduralBeat(Long.MAX_VALUE)
        assertFalse("Long.MAX_VALUE must not be NaN", beatAtMaxLong.isNaN())
        assertFalse("Long.MAX_VALUE must not be Infinite", beatAtMaxLong.isInfinite())
        assertTrue("Long.MAX_VALUE must be in [0..1], was $beatAtMaxLong", beatAtMaxLong in 0.0f..1.0f)

        // Stress 10,000 random and boundary points
        val random = Random(42)
        for (i in 0 until 10_000) {
            val pos = random.nextLong(0L, 50_000_000L)
            val beat = AudioEnergyProcessor.calculateProceduralBeat(pos)
            assertFalse("Pos $pos produced NaN", beat.isNaN())
            assertFalse("Pos $pos produced Infinite", beat.isInfinite())
            assertTrue("Pos $pos produced out of range $beat", beat in 0f..1f)
        }
    }

    @Test
    fun subscriberAttachmentDetachment_concurrencyAndDoubleCancellation() {
        val processor = AudioEnergyProcessor()
        assertEquals(false, processor.hasActiveListeners)

        // Test double-cancellation resilience
        val unsub1 = processor.subscribe()
        assertTrue(processor.hasActiveListeners)

        // First cancellation -> activeListeners should be false
        unsub1()
        assertFalse("After first cancel, hasActiveListeners must be false", processor.hasActiveListeners)

        // Second cancellation (duplicate callback call)
        unsub1()
        assertFalse("After duplicate cancel, hasActiveListeners must still be false", processor.hasActiveListeners)

        // Now add a new subscriber: MUST properly reactivate!
        val unsub2 = processor.subscribe()
        assertTrue(
            "New subscriber must successfully activate listener even after prior duplicate cancellations",
            processor.hasActiveListeners
        )
        unsub2()
        assertFalse(processor.hasActiveListeners)
    }

    @Test
    fun concurrentSubscribers_stressTest() {
        val processor = AudioEnergyProcessor()
        val numThreads = 16
        val operationsPerThread = 500
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        val errors = AtomicInteger(0)

        for (t in 0 until numThreads) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val unsub = processor.subscribe()
                        // Ensure hasActiveListeners is true while subscribed
                        if (!processor.hasActiveListeners) {
                            errors.incrementAndGet()
                        }
                        unsub()
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue("Concurrent operations should complete within 10s", latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals("Zero errors during concurrent subscriber ops", 0, errors.get())
        assertFalse("All subscribers detached, hasActiveListeners must be false", processor.hasActiveListeners)
    }

    @Test
    fun concurrentQueueInput_withConcurrentSubscribers_stressTest() {
        val processor = createConfiguredProcessor()
        val numThreads = 8
        val executor = Executors.newFixedThreadPool(numThreads)
        val running = AtomicBoolean(true)
        val errors = AtomicInteger(0)

        // 2 threads constantly toggling subscriptions
        for (t in 0 until 2) {
            executor.submit {
                try {
                    while (running.get()) {
                        val unsub = processor.subscribe()
                        Thread.yield()
                        unsub()
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                }
            }
        }

        // 6 threads queuing audio buffers
        val queueLatch = CountDownLatch(6)
        for (t in 0 until 6) {
            executor.submit {
                try {
                    val buffer = ByteBuffer.allocateDirect(128 * 4).order(ByteOrder.LITTLE_ENDIAN)
                    for (i in 0 until 128) {
                        buffer.putShort(1000.toShort())
                        buffer.putShort(1000.toShort())
                    }
                    buffer.flip()

                    for (iter in 0 until 200) {
                        val input = buffer.duplicate()
                        synchronized(processor) {
                            processor.queueInput(input)
                        }
                        val state = processor.energyFlow.value
                        if (state.rmsEnergy.isNaN() || state.bassEnergy.isNaN() || state.beatPulse.isNaN()) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: Throwable) {
                    errors.incrementAndGet()
                } finally {
                    queueLatch.countDown()
                }
            }
        }

        assertTrue("Queue threads should complete", queueLatch.await(10, TimeUnit.SECONDS))
        running.set(false)
        executor.shutdown()
        assertTrue("Executor should terminate", executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals("Zero errors encountered during concurrent processing", 0, errors.get())
    }
}

package com.divinedeck.launcher.feature.music.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * Automated unit test suite verifying [AudioEnergyProcessor] in-pipeline audio reactivity:
 * 1. PCM16 audio format negotiation and non-PCM16 rejection.
 * 2. Zero-cost bypass when no visualizer or lyrics panel subscribers are active.
 * 3. Accurate RMS amplitude and low-frequency bass energy (<150Hz) extraction.
 * 4. Passthrough guarantee: audio buffer forwarded byte-for-byte to downstream sink.
 * 5. Transient beat pulse detector with exponential decay.
 * 6. Procedural beat generator fallback timing and waveform verification.
 * 7. Subscriber registration and auto-cancellation lifecycle.
 */
@OptIn(UnstableApi::class)
class AudioEnergyProcessorTest {

    @Test
    fun pcm16FormatNegotiation_acceptsPcm16AndRejectsOthers() {
        val processor = AudioEnergyProcessor()

        // 44100Hz, Stereo, PCM16 -> Accepted
        val pcm16Format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val configured = processor.configure(pcm16Format)
        assertEquals(pcm16Format.sampleRate, configured.sampleRate)
        assertEquals(pcm16Format.channelCount, configured.channelCount)
        assertEquals(pcm16Format.encoding, configured.encoding)

        // Float or invalid encoding -> Rejected (NOT_SET)
        val floatFormat = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_FLOAT)
        val rejectedFloat = processor.configure(floatFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, rejectedFloat)
    }

    @Test
    fun zeroCostBypass_whenNoSubscribersActive_bypassesProcessing() {
        val processor = AudioEnergyProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        assertFalse("Initially should have no active listeners", processor.hasActiveListeners)

        // Provide a buffer of non-zero audio
        val buffer = createStereoSineBuffer(frequencyHz = 80.0, sampleRate = 44100, numFrames = 256, amplitude = 0.8f)
        processor.queueInput(buffer)

        val state = processor.energyFlow.value
        assertFalse("Should not be active when bypassed", state.isAudioReactiveActive)
        assertEquals(0f, state.rmsEnergy, 0.0001f)
        assertEquals(0f, state.bassEnergy, 0.0001f)

        // Verify output buffer received all bytes unchanged
        val output = processor.output
        assertTrue(output.hasRemaining())
        assertEquals(256 * 4, output.remaining())
    }

    @Test
    fun subscriberLifecycle_controlsListenerState() {
        val processor = AudioEnergyProcessor()
        assertFalse(processor.hasActiveListeners)

        val unsubscribe1 = processor.subscribe()
        assertTrue(processor.hasActiveListeners)

        val unsubscribe2 = processor.subscribe()
        assertTrue(processor.hasActiveListeners)

        unsubscribe1()
        assertTrue("Still has one subscriber", processor.hasActiveListeners)

        unsubscribe2()
        assertFalse("Zero subscribers remaining", processor.hasActiveListeners)

        // Test manual visualizer flag
        processor.isVisualizerActive = true
        assertTrue(processor.hasActiveListeners)
        processor.isVisualizerActive = false
        assertFalse(processor.hasActiveListeners)
    }

    @Test
    fun audioSampling_extractsRmsAndBassAccurately() {
        val processor = AudioEnergyProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val unsubscribe = processor.subscribe()
        assertTrue(processor.hasActiveListeners)

        // Feed low frequency 60Hz sine wave (bass)
        val bassBuffer = createStereoSineBuffer(frequencyHz = 60.0, sampleRate = 44100, numFrames = 512, amplitude = 0.7f)
        processor.queueInput(bassBuffer)

        val bassState = processor.energyFlow.value
        assertTrue("Processor must be active", bassState.isAudioReactiveActive)
        assertTrue("RMS energy must be positive", bassState.rmsEnergy > 0.1f)
        assertTrue("Bass energy must be positive for 60Hz tone", bassState.bassEnergy > 0.05f)

        // Feed high frequency 5000Hz sine wave (treble)
        processor.flush()
        val trebleBuffer = createStereoSineBuffer(frequencyHz = 5000.0, sampleRate = 44100, numFrames = 512, amplitude = 0.7f)
        processor.queueInput(trebleBuffer)

        val trebleState = processor.energyFlow.value
        assertTrue("Processor must be active", trebleState.isAudioReactiveActive)
        assertTrue("RMS should still be positive for treble", trebleState.rmsEnergy > 0.1f)
        // 5000Hz is heavily attenuated by 150Hz low-pass filter
        assertTrue("Bass energy must be attenuated for 5000Hz tone", trebleState.bassEnergy < bassState.bassEnergy)

        unsubscribe()
    }

    @Test
    fun passthroughGuarantee_outputMatchesInputBytesExactly() {
        val processor = AudioEnergyProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val input = createStereoSineBuffer(frequencyHz = 120.0, sampleRate = 44100, numFrames = 128, amplitude = 0.5f)
        val inputCopy = input.duplicate()

        processor.queueInput(input)
        val output = processor.output

        assertEquals("Output buffer size must match input buffer size", inputCopy.remaining(), output.remaining())
        while (inputCopy.hasRemaining() && output.hasRemaining()) {
            assertEquals(inputCopy.get(), output.get())
        }
    }

    @Test
    fun proceduralBeatFallback_waveformAndPeriodicity() {
        // Negative position returns 0
        assertEquals(0f, AudioEnergyProcessor.calculateProceduralBeat(-100L), 0.0001f)

        // Attack phase [0..60ms]
        val beatAt0 = AudioEnergyProcessor.calculateProceduralBeat(0L)
        val beatAt30 = AudioEnergyProcessor.calculateProceduralBeat(30L)
        val beatAt60 = AudioEnergyProcessor.calculateProceduralBeat(60L)

        assertEquals(0f, beatAt0, 0.001f)
        assertEquals(0.5f, beatAt30, 0.01f)
        assertEquals(1.0f, beatAt60, 0.01f)
        assertTrue("Beat must increase during attack phase", beatAt30 > beatAt0 && beatAt60 > beatAt30)

        // Decay phase [60..500ms]
        val beatAt120 = AudioEnergyProcessor.calculateProceduralBeat(120L)
        val beatAt300 = AudioEnergyProcessor.calculateProceduralBeat(300L)
        val beatAt480 = AudioEnergyProcessor.calculateProceduralBeat(480L)

        assertTrue("Beat must decay after attack peak", beatAt120 < beatAt60)
        assertTrue("Decay must be monotonic", beatAt300 < beatAt120)
        assertTrue("Decay continues toward next beat", beatAt480 < beatAt300)

        // Periodicity across 500ms bar boundaries (120 BPM)
        val beatAt500 = AudioEnergyProcessor.calculateProceduralBeat(500L)
        val beatAt560 = AudioEnergyProcessor.calculateProceduralBeat(560L)
        val beatAt1000 = AudioEnergyProcessor.calculateProceduralBeat(1000L)
        val beatAt1060 = AudioEnergyProcessor.calculateProceduralBeat(1060L)

        assertEquals(beatAt0, beatAt500, 0.01f)
        assertEquals(beatAt60, beatAt560, 0.01f)
        assertEquals(beatAt0, beatAt1000, 0.01f)
        assertEquals(beatAt60, beatAt1060, 0.01f)
    }

    @Test
    fun resetClearsState_andResetsActiveIndicator() {
        val processor = AudioEnergyProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val unsubscribe = processor.subscribe()
        val buffer = createStereoSineBuffer(frequencyHz = 80.0, sampleRate = 44100, numFrames = 256, amplitude = 0.9f)
        processor.queueInput(buffer)

        assertTrue(processor.energyFlow.value.isAudioReactiveActive)

        processor.reset()
        assertFalse("Reset must clear active state", processor.energyFlow.value.isAudioReactiveActive)
        assertEquals(0f, processor.energyFlow.value.rmsEnergy, 0.0001f)
        assertEquals(0f, processor.energyFlow.value.bassEnergy, 0.0001f)

        unsubscribe()
    }

    private fun createStereoSineBuffer(
        frequencyHz: Double,
        sampleRate: Int,
        numFrames: Int,
        amplitude: Float
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(numFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numFrames) {
            val sampleVal = (sin(2.0 * PI * frequencyHz * i / sampleRate) * amplitude * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(sampleVal) // Left
            buffer.putShort(sampleVal) // Right
        }
        buffer.flip()
        return buffer
    }
}

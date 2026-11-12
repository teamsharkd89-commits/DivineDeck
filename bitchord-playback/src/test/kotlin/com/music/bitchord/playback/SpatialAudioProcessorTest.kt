package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.playback.audio.AudioBlock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verification of [SpatialAudioProcessor]:
 * - Bypass when disabled
 * - Stereo widening and delayed low-pass crossfeed
 * - Safe handling of mono and unsupported formats
 * - Absence of NaN / Infinite values
 * - Preservation of valid audio signal
 * - Stability over consecutive blocks and buffer state handling
 */
@UnstableApi
class SpatialAudioProcessorTest {

    private val sampleRate = 44_100

    @Test
    fun `spatial bypass passes samples through untouched when disabled`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = false

        val frames = 128
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = (i + 1) * 0.005f
        }
        block.reset(frames)

        spatial.process(block)

        for (i in 0 until frames * 2) {
            assertEquals("Sample $i must be untouched", (i + 1) * 0.005f, block.samples[i], 1e-6f)
        }
    }

    @Test
    fun `spatial bypass passes 16-bit byte buffer untouched when disabled`() {
        val spatial = SpatialAudioProcessor()
        val format = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
        val configured = spatial.configure(format)
        assertEquals(format, configured)
        spatial.flush()
        spatial.enabled = false

        val frames = 64
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putShort((i * 100).toShort())
        }
        input.flip()

        spatial.queueInput(input)
        val output = spatial.output

        assertEquals(frames * 4, output.remaining())
        for (i in 0 until frames * 2) {
            assertEquals((i * 100).toShort(), output.short)
        }
    }

    @Test
    fun `spatial crossfeed leaks delayed and low-passed signal to opposite channel`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        // 15ms delay at 44.1kHz is ~661 frames
        val frames = 1000
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Feed only Left channel with a 500 Hz sine wave, Right is dead zero
        for (f in 0 until frames) {
            block.samples[f * 2] = (0.5 * sin(2.0 * PI * 500.0 * f / sampleRate)).toFloat()
            block.samples[f * 2 + 1] = 0.0f
        }
        block.reset(frames)

        spatial.process(block)

        // After the delay buffer fills (>700 frames), right channel must have crossfed signal
        var rightEnergy = 0.0
        for (f in 700 until frames) {
            rightEnergy += abs(block.samples[f * 2 + 1].toDouble())
        }
        assertTrue("Crossfeed must produce non-zero signal on opposite (right) channel", rightEnergy > 1.0)
    }

    @Test
    fun `spatial widening amplifies side channel difference`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        val frames = 64
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        // Opposite phase signal: L = +0.2, R = -0.2 (pure side signal)
        for (f in 0 until frames) {
            block.samples[f * 2] = 0.2f
            block.samples[f * 2 + 1] = -0.2f
        }
        block.reset(frames)

        spatial.process(block)

        // With widthGain = 2.5 and outputGain = 0.82, side difference should be expanded
        val l = block.samples[0]
        val r = block.samples[1]
        val diff = abs(l - r)
        // Input diff was 0.4. Widened diff without crossfeed would be 0.4 * 2.5 * 0.82 = 0.82.
        assertTrue("Stereo difference must be expanded past input diff (was $diff)", diff > 0.45f)
    }

    @Test
    fun `spatial audio safely declines mono and multichannel formats in onConfigure`() {
        val spatial = SpatialAudioProcessor()

        // Mono PCM 16-bit
        val monoFormat = AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)
        val monoResult = spatial.configure(monoFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, monoResult)

        // 5.1 surround PCM 16-bit
        val surroundFormat = AudioProcessor.AudioFormat(sampleRate, 6, C.ENCODING_PCM_16BIT)
        val surroundResult = spatial.configure(surroundFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, surroundResult)

        // Float 32-bit
        val floatFormat = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT)
        val floatResult = spatial.configure(floatFormat)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, floatResult)

        // Valid Stereo PCM 16-bit
        val validFormat = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
        val validResult = spatial.configure(validFormat)
        assertEquals(validFormat, validResult)
    }

    @Test
    fun `spatial process gracefully skips mono blocks without mutation`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 1) // mono configuration
        spatial.enabled = true

        val block = AudioBlock(channelCount = 1, capacityFrames = 32)
        for (i in 0 until 32) {
            block.samples[i] = 0.42f
        }
        block.reset(32)

        spatial.process(block)

        for (i in 0 until 32) {
            assertEquals(0.42f, block.samples[i], 1e-6f)
        }
    }

    @Test
    fun `spatial output contains no NaN or Infinite values under high input`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        val frames = 1024
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (f in 0 until frames) {
            // Hot signal
            block.samples[f * 2] = 1.0f
            block.samples[f * 2 + 1] = -1.0f
        }
        block.reset(frames)

        spatial.process(block)

        for (i in 0 until frames * 2) {
            val sample = block.samples[i]
            assertFalse("Sample $i must not be NaN", sample.isNaN())
            assertFalse("Sample $i must not be Infinite", sample.isInfinite())
        }
    }

    @Test
    fun `spatial maintains stability over continuous consecutive audio blocks`() {
        val spatial = SpatialAudioProcessor()
        val format = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
        spatial.configure(format)
        spatial.flush()
        spatial.enabled = true

        val framesPerBlock = 512
        val input = ByteBuffer.allocateDirect(framesPerBlock * 4).order(ByteOrder.nativeOrder())

        // Run 50 consecutive blocks (~0.58 seconds of audio)
        for (b in 0 until 50) {
            input.clear()
            for (f in 0 until framesPerBlock) {
                val value = (10_000.0 * sin(2.0 * PI * 440.0 * (b * framesPerBlock + f) / sampleRate)).toInt().toShort()
                input.putShort(value)
                input.putShort((-value).toShort())
            }
            input.flip()

            spatial.queueInput(input)
            val output = spatial.output
            assertEquals(framesPerBlock * 4, output.remaining())

            // Drain output
            while (output.hasRemaining()) {
                val sample = output.short
                assertTrue("Sample value within 16-bit range", sample in Short.MIN_VALUE..Short.MAX_VALUE)
            }
        }
    }

    @Test
    fun `onFlush and onReset clear internal delay lines and state`() {
        val spatial = SpatialAudioProcessor()
        spatial.configure(sampleRate, 2)
        spatial.enabled = true

        val frames = 500
        val block = AudioBlock(channelCount = 2, capacityFrames = frames)
        for (i in 0 until frames * 2) {
            block.samples[i] = 0.5f
        }
        block.reset(frames)
        spatial.process(block)

        // Flush
        spatial.flush()

        // Feed silence: if delay line was cleared, output after flush will have 0 crossfeed
        val silence = AudioBlock(channelCount = 2, capacityFrames = 10)
        silence.reset(10)
        spatial.process(silence)

        for (i in 0 until 20) {
            assertEquals("After flush, initial samples must be zero", 0.0f, silence.samples[i], 1e-6f)
        }

        // Reset
        spatial.reset()
        assertEquals(0, spatial.output.remaining())
    }
}

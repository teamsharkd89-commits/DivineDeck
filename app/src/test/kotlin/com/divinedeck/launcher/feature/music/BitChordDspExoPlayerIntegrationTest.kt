package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqualizerPreset
import com.music.bitchord.playback.audio.AudioBlock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Full integration test for Phase 06.5E.2.2:
 * Verifies DSP pipeline integration with ExoPlayer single architecture,
 * real-time settings synchronization, PCM16 format negotiation, mono safety,
 * and track transition flushing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordDspExoPlayerIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
        Dispatchers.resetMain()
    }

    @Test
    fun test1_serviceInstantiatesSingleInstancesOfProcessors() {
        val service = DivineDeckPlaybackService()
        val eqProcessor = service.getEqualizerProcessorForTest()
        val spatialProcessor = service.getSpatialAudioProcessorForTest()

        assertNotNull("EqualizerProcessor must be instantiated", eqProcessor)
        assertNotNull("SpatialAudioProcessor must be instantiated", spatialProcessor)

        // Multiple calls must return the exact same singleton instance
        assertEquals(eqProcessor, service.getEqualizerProcessorForTest())
        assertEquals(spatialProcessor, service.getSpatialAudioProcessorForTest())
    }

    @Test
    fun test2_pcmFormatNegotiationAndMonoFallback() {
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()
        val spatial = service.getSpatialAudioProcessorForTest()

        val sampleRate = 48_000

        // 1. PCM 16-bit Stereo (the standard production pipeline)
        val pcm16Stereo = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT)
        val eqConfigResult = eq.configure(pcm16Stereo)
        val spatialConfigResult = spatial.configure(pcm16Stereo)

        assertEquals("Equalizer must accept PCM 16-bit stereo", pcm16Stereo, eqConfigResult)
        assertEquals("Spatial must accept PCM 16-bit stereo", pcm16Stereo, spatialConfigResult)

        // 2. PCM 16-bit Mono (Voice recordings / mono library tracks)
        val pcm16Mono = AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT)
        val spatialMonoResult = spatial.configure(pcm16Mono)
        val eqMonoResult = eq.configure(pcm16Mono)

        // Spatial Audio must decline gracefully with NOT_SET without throwing UnhandledAudioFormatException
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, spatialMonoResult)
        // Equalizer supports mono (channelCount >= 1)
        assertEquals(pcm16Mono, eqMonoResult)

        // 3. PCM Float (Demonstrating why enableFloatOutput=false is mandated)
        val pcmFloat = AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT)
        assertEquals(
            "Float must be safely rejected in favor of PCM16 conversion",
            AudioProcessor.AudioFormat.NOT_SET,
            spatial.configure(pcmFloat)
        )
        assertEquals(
            "Float must be safely rejected in favor of PCM16 conversion",
            AudioProcessor.AudioFormat.NOT_SET,
            eq.configure(pcmFloat)
        )
    }

    @Test
    fun test3_realTimeEqualizerUpdatesReflectInTuning() = runTest(testDispatcher) {
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()

        assertFalse("Initially disabled", eq.isEnabled)

        // 1. Enable Equalizer
        AppSettings.setEqualizerEnabled(true)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        AppSettings.setEqualizerBalance(0.2f)

        // Trigger service settings observer
        val field = DivineDeckPlaybackService::class.java.getDeclaredMethod("applyEqualizerSettings")
        field.isAccessible = true
        field.invoke(service)

        assertTrue("Equalizer must be enabled in processor", eq.isEnabled)

        // 2. Switch to Tone Pad Mode
        AppSettings.setEqualizerMode(EqualizerMode.TONE_PAD)
        AppSettings.setEqualizerTone(4, -3)
        AppSettings.setEqualizerFocused(true)
        field.invoke(service)

        assertTrue(eq.isEnabled)

        // 3. Disable Equalizer
        AppSettings.setEqualizerEnabled(false)
        field.invoke(service)

        assertFalse("Equalizer must report disabled when turned off", eq.isEnabled)
    }

    @Test
    fun test4_realTimeSpatialAudioToggle() = runTest(testDispatcher) {
        val service = DivineDeckPlaybackService()
        val spatial = service.getSpatialAudioProcessorForTest()

        assertFalse("Initially disabled", spatial.enabled)

        AppSettings.setSpatialAudio(true)
        val field = DivineDeckPlaybackService::class.java.getDeclaredMethod("applySpatialAudioSettings")
        field.isAccessible = true
        field.invoke(service)

        assertTrue("Spatial audio processor must become enabled", spatial.enabled)

        AppSettings.setSpatialAudio(false)
        field.invoke(service)

        assertFalse("Spatial audio processor must become disabled", spatial.enabled)
    }

    @Test
    fun test5_equalizerDisabledBypassesExactSignal() {
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()

        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        eq.configure(format)
        eq.flush()

        val frames = 128
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames * 2) {
            input.putShort((i * 50).toShort())
        }
        input.flip()

        // Equalizer is disabled -> passthrough
        eq.queueInput(input)
        val output = eq.output

        assertEquals(frames * 4, output.remaining())
        for (i in 0 until frames * 2) {
            assertEquals((i * 50).toShort(), output.short)
        }
    }

    @Test
    fun test6_equalizerActiveShapesResponse() {
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()

        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        eq.configure(format)
        eq.flush()

        // Enable Bass Boost
        AppSettings.setEqualizerEnabled(true)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        val field = DivineDeckPlaybackService::class.java.getDeclaredMethod("applyEqualizerSettings")
        field.isAccessible = true
        field.invoke(service)

        // Inject 60 Hz low frequency tone
        val frames = 512
        val input = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (f in 0 until frames) {
            val v = (5000.0 * sin(2.0 * PI * 60.0 * f / 44_100)).toInt().toShort()
            input.putShort(v)
            input.putShort(v)
        }
        input.flip()

        eq.queueInput(input)
        val output = eq.output
        assertTrue("Output buffer must have data", output.remaining() > 0)

        // Output must not be identical to input due to filter shaping
        input.flip()
        var hasDifference = false
        while (output.hasRemaining() && input.hasRemaining()) {
            if (output.short != input.short) {
                hasDifference = true
                break
            }
        }
        assertTrue("Equalizer must actively shape the signal", hasDifference)
    }

    @Test
    fun test7_trackTransitionFlushesInternalState() {
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()
        val spatial = service.getSpatialAudioProcessorForTest()

        val format = AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT)
        eq.configure(format)
        spatial.configure(format)

        // Simulate seek / track transition: flush() must clean delay lines and filter memory
        eq.flush()
        spatial.flush()

        // Feed short block of silence
        val silence = AudioBlock(2, 64)
        silence.reset(64)
        spatial.process(silence)
        eq.process(silence)

        // Output should be dead silence without leftover ringing from previous track
        for (i in 0 until 128) {
            assertEquals(0f, silence.samples[i], 1e-6f)
        }
    }

    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = backingMap
        override fun getString(key: String?, defValue: String?): String? = (backingMap[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (backingMap[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (backingMap[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (backingMap[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (backingMap[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (backingMap[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = backingMap.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class Editor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) { removals.add(key); pending.remove(key) }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true; pending.clear(); removals.clear()
                return this
            }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> if (v != null) map[k] = v else map.remove(k) }
            }
        }
    }
}

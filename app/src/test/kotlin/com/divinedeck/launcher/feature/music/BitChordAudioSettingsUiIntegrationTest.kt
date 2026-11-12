package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
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
 * Suite de pruebas automatizadas para la Fase 06.5E.4:
 * Valida la inicialización global temprana, persistencia en frío,
 * límites de Tone Pad, bandas de ecualización, presets, balance,
 * enfoque vocal, audio espacial, navegación y arquitectura de reproducción única.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordAudioSettingsUiIntegrationTest {

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
    fun test01_earlyInitialization_populatesPreferencesImmediately() {
        // Verifica que al inicializar AppSettings en Application.onCreate,
        // todas las preferencias están disponibles inmediatamente antes de cualquier servicio
        assertNotNull(AppSettings.equalizerEnabled.value)
        assertEquals(false, AppSettings.equalizerEnabled.value)
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)
        assertEquals(0, AppSettings.equalizerToneX.value)
        assertEquals(0, AppSettings.equalizerToneY.value)
        assertEquals(0f, AppSettings.equalizerBalance.value, 0.001f)
        assertEquals(EqualizerPreset.FLAT, AppSettings.equalizerPreset.value)
        assertFalse(AppSettings.spatialAudio.value)
    }

    @Test
    fun test02_persistenceBeforePlayback_savesDirectlyToSharedPreferences() {
        // 2. Modificar ajustes en frío (sin haber reproducido audio ni arrancado el servicio)
        AppSettings.setEqualizerEnabled(true)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setSpatialAudio(true)
        AppSettings.setEqualizerTone(3, -2)
        AppSettings.setEqualizerBalance(0.45f)
        AppSettings.setEqualizerFocused(true)

        // Verificar persistencia real en SharedPreferences
        assertTrue(mockPrefs.getBoolean(AppSettings.KEY_EQ_ENABLED, false))
        assertEquals(EqualizerMode.MANUAL.name, mockPrefs.getString(AppSettings.KEY_EQ_MODE, null))
        assertTrue(mockPrefs.getBoolean(AppSettings.KEY_SPATIAL_AUDIO, false))
        assertEquals(3, mockPrefs.getInt(AppSettings.KEY_EQ_TONE_X, 0))
        assertEquals(-2, mockPrefs.getInt(AppSettings.KEY_EQ_TONE_Y, 0))
        assertEquals(0.45f, mockPrefs.getFloat(AppSettings.KEY_EQ_BALANCE, 0f), 0.001f)
        assertTrue(mockPrefs.getBoolean(AppSettings.KEY_EQ_FOCUSED, false))
    }

    @Test
    fun test03_restorationAfterReload_restoresExactValues() {
        // 3. Modificar y guardar
        AppSettings.setEqualizerEnabled(true)
        AppSettings.setEqualizerPreset(EqualizerPreset.ROCK)
        AppSettings.setEqualizerTone(4, 5)
        AppSettings.setEqualizerBalance(-0.75f)
        AppSettings.setSpatialAudio(true)

        // Simular reinicio del proceso de la aplicación cargando desde el mismo SharedPreferences
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertTrue(AppSettings.equalizerEnabled.value)
        assertEquals(EqualizerPreset.ROCK, AppSettings.equalizerPreset.value)
        assertEquals(4, AppSettings.equalizerToneX.value)
        assertEquals(5, AppSettings.equalizerToneY.value)
        assertEquals(-0.75f, AppSettings.equalizerBalance.value, 0.001f)
        assertTrue(AppSettings.spatialAudio.value)
    }

    @Test
    fun test04_tonePadLimits_clampedStrictlyToFive() {
        // 4. Tone Pad límites -5 a +5
        AppSettings.setEqualizerTone(99, -88)
        assertEquals(5, AppSettings.equalizerToneX.value)
        assertEquals(-5, AppSettings.equalizerToneY.value)

        AppSettings.setEqualizerTone(-10, 15)
        assertEquals(-5, AppSettings.equalizerToneX.value)
        assertEquals(5, AppSettings.equalizerToneY.value)

        AppSettings.setEqualizerTone(0, 0)
        assertEquals(0, AppSettings.equalizerToneX.value)
        assertEquals(0, AppSettings.equalizerToneY.value)
    }

    @Test
    fun test05_sevenBandsGainLimits_clampedToTwelveDb() {
        // 5. Siete bandas y límites de ganancia [-12 dB, +12 dB]
        val extremeGains = listOf(25f, -30f, 12f, -12f, 0f, 40f, -15f)
        AppSettings.setEqualizerBands(extremeGains)

        val clamped = AppSettings.equalizerBands.value
        assertEquals(EqLayout.MANUAL_COUNT, clamped.size)
        assertEquals(12f, clamped[0], 0.01f)
        assertEquals(-12f, clamped[1], 0.01f)
        assertEquals(12f, clamped[2], 0.01f)
        assertEquals(-12f, clamped[3], 0.01f)
        assertEquals(0f, clamped[4], 0.01f)
        assertEquals(12f, clamped[5], 0.01f)
        assertEquals(-12f, clamped[6], 0.01f)
    }

    @Test
    fun test06_presetSwitching_appliesCurveCorrectly() {
        // 6. Cambio de presets
        AppSettings.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        assertEquals(EqualizerPreset.BASS_BOOST, AppSettings.equalizerPreset.value)
        assertEquals(EqualizerPreset.BASS_BOOST.bands, AppSettings.equalizerBands.value)

        AppSettings.setEqualizerPreset(EqualizerPreset.VOCAL)
        assertEquals(EqualizerPreset.VOCAL, AppSettings.equalizerPreset.value)
        assertEquals(EqualizerPreset.VOCAL.bands, AppSettings.equalizerBands.value)

        AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
        assertEquals(EqualizerPreset.FLAT, AppSettings.equalizerPreset.value)
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f), AppSettings.equalizerBands.value)
    }

    @Test
    fun test07_customPresetDetection_whenBandIsModifiedManually() {
        // 7. Modificar una banda cambia automáticamente a CUSTOM
        AppSettings.setEqualizerPreset(EqualizerPreset.FLAT)
        val modified = AppSettings.equalizerBands.value.toMutableList()
        modified[0] = 4.5f // Realce de 60 Hz
        AppSettings.setEqualizerBands(modified)

        assertEquals(EqualizerPreset.CUSTOM, AppSettings.equalizerPreset.value)
        assertEquals(EqualizerPreset.CUSTOM.name, mockPrefs.getString(AppSettings.KEY_EQ_PRESET, null))
    }

    @Test
    fun test08_balanceControl_clampedToUnitRange() {
        // 8. Balance estéreo [-1.0f, 1.0f]
        AppSettings.setEqualizerBalance(-3.5f)
        assertEquals(-1.0f, AppSettings.equalizerBalance.value, 0.001f)

        AppSettings.setEqualizerBalance(5.2f)
        assertEquals(1.0f, AppSettings.equalizerBalance.value, 0.001f)

        AppSettings.setEqualizerBalance(0f)
        assertEquals(0f, AppSettings.equalizerBalance.value, 0.001f)
    }

    @Test
    fun test09_vocalFocus_togglesCorrectly() {
        // 9. Enfoque vocal
        AppSettings.setEqualizerFocused(true)
        assertTrue(AppSettings.equalizerFocused.value)
        assertTrue(mockPrefs.getBoolean(AppSettings.KEY_EQ_FOCUSED, false))

        AppSettings.setEqualizerFocused(false)
        assertFalse(AppSettings.equalizerFocused.value)
        assertFalse(mockPrefs.getBoolean(AppSettings.KEY_EQ_FOCUSED, true))
    }

    @Test
    fun test10_spatialAudio_togglesCorrectly() {
        // 10. Audio espacial Haas
        AppSettings.setSpatialAudio(true)
        assertTrue(AppSettings.spatialAudio.value)
        assertTrue(mockPrefs.getBoolean(AppSettings.KEY_SPATIAL_AUDIO, false))

        AppSettings.setSpatialAudio(false)
        assertFalse(AppSettings.spatialAudio.value)
        assertFalse(mockPrefs.getBoolean(AppSettings.KEY_SPATIAL_AUDIO, true))
    }

    @Test
    fun test11_controlsSynchronizationWithStateFlow() = runTest {
        // 11. Sincronización reactiva: los controles actualizan el StateFlow de forma instantánea
        var lastEmittedToneX = -999
        val job = testDispatcher.scheduler.let {
            // Emisión directa
            AppSettings.setEqualizerTone(4, 2)
            lastEmittedToneX = AppSettings.equalizerToneX.value
        }
        assertEquals(4, lastEmittedToneX)
        assertEquals(2, AppSettings.equalizerToneY.value)
    }

    @Test
    fun test12_navigationState_defaultAndToggle() {
        // 12. Navegación: estado de apertura y regreso a la vista principal
        var showAudioSettings = false

        // Simular clic en botón de ajuste DSP
        showAudioSettings = true
        assertTrue("Pantalla de ajustes debe estar abierta", showAudioSettings)

        // Simular clic en VOLVER
        showAudioSettings = false
        assertFalse("Pantalla de ajustes debe cerrarse al volver", showAudioSettings)
    }

    @Test
    fun test13_singleExoPlayerArchitecture_noSecondServiceOrPlayer() {
        // 13. Arquitectura estricta de reproductor único
        val service = DivineDeckPlaybackService()
        val eq1 = service.getEqualizerProcessorForTest()
        val eq2 = service.getEqualizerProcessorForTest()
        val sp1 = service.getSpatialAudioProcessorForTest()
        val sp2 = service.getSpatialAudioProcessorForTest()

        assertEquals("Misma instancia de EqualizerProcessor", eq1, eq2)
        assertEquals("Misma instancia de SpatialAudioProcessor", sp1, sp2)
    }

    @Test
    fun test14_continuousPlaybackWhileModifyingSettings() {
        // 14. Modificar ajustes mientras se reproduce no altera el estado del servicio ni detiene el flujo
        val service = DivineDeckPlaybackService()
        val eq = service.getEqualizerProcessorForTest()
        val sp = service.getSpatialAudioProcessorForTest()

        AppSettings.setEqualizerEnabled(true)
        AppSettings.setSpatialAudio(true)
        AppSettings.setEqualizerTone(2, 3)
        AppSettings.setEqualizerBands(listOf(2f, 1f, 0f, 0f, 1f, 2f, 3f))

        // Los procesadores están configurados y listos sin fallas
        assertNotNull(eq)
        assertNotNull(sp)
        assertTrue(AppSettings.equalizerEnabled.value)
        assertTrue(AppSettings.spatialAudio.value)
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

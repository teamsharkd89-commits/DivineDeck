package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.feature.music.ui.BitChordSettingsCategory
import com.divinedeck.launcher.feature.music.ui.settings.ColorAmoledCard
import com.divinedeck.launcher.feature.music.ui.settings.ColorAmoledCardDark
import com.divinedeck.launcher.feature.music.ui.settings.ColorCyanAccent
import com.divinedeck.launcher.feature.music.ui.settings.ColorDestructiveRed
import com.divinedeck.launcher.feature.music.ui.settings.GROUP_INSET
import com.divinedeck.launcher.feature.music.ui.settings.GroupShape
import com.divinedeck.launcher.feature.music.ui.settings.ICON_GAP
import com.divinedeck.launcher.feature.music.ui.settings.ICON_SIZE
import com.divinedeck.launcher.feature.music.ui.settings.ROW_INSET
import com.divinedeck.launcher.feature.music.ui.settings.TEXT_INSET
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.EqualizerPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite de pruebas unitarias para Tokens de Diseño de BitChord 1.8.1 y DSP en Ajustes (Fase 06.9 - R3 & R5):
 * 1. Tokens geométricos y paleta AMOLED de BitChord 1.8.1 SettingsSheet (GroupShape, TEXT_INSET, colores).
 * 2. Cobertura completa de las 12 categorías oficiales y alias de compatibilidad.
 * 3. Integración estricta de DSP (Tone Pad 2D, EQ paramétrico, presets, spatial) en la categoría ECUALIZADOR_DSP.
 * 4. Persistencia y reactividad bidireccional de StateFlows.
 */
class BitChordSettings181TokensAndDspTest {

    private lateinit var fakePrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        fakePrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
    }

    // =========================================================================
    // 1. Tokens de Geometría y Diseño de BitChord 1.8.1 Original (R3)
    // =========================================================================

    @Test
    fun bitChord181Tokens_matchExactSpecificationFromSettingsSheet() {
        // En BitChord 1.8.1 (SettingsSheet.kt líneas 2279-2286):
        // internal val GroupShape = RoundedCornerShape(14.dp)
        // internal val GROUP_INSET = 16.dp
        // internal val ROW_INSET = 16.dp
        // internal val ICON_SIZE = 22.dp
        // internal val ICON_GAP = 14.dp
        // internal val TEXT_INSET = ROW_INSET + ICON_SIZE + ICON_GAP // 52.dp

        assertEquals(16.dp, GROUP_INSET)
        assertEquals(16.dp, ROW_INSET)
        assertEquals(22.dp, ICON_SIZE)
        assertEquals(14.dp, ICON_GAP)

        // TEXT_INSET debe ser exactamente 16.dp + 22.dp + 14.dp = 52.dp
        assertEquals(52.dp, TEXT_INSET)

        // GroupShape
        assertEquals(RoundedCornerShape(14.dp), GroupShape)

        // Paleta AMOLED
        assertEquals(Color(0xFF141414), ColorAmoledCard)
        assertEquals(Color(0xFF101010), ColorAmoledCardDark)
        assertEquals(Color(0xFF00E5FF), ColorCyanAccent)
        assertEquals(Color(0xFFEF4444), ColorDestructiveRed)
    }

    // =========================================================================
    // 2. Cobertura de las 12 Categorías Oficiales
    // =========================================================================

    @Test
    fun bitChordSettingsCategories_haveAll12CategoriesAndCompleteMetadata() {
        val categories = BitChordSettingsCategory.entries
        assertEquals("Debe haber exactamente 12 categorías oficiales", 12, categories.size)

        val expected = listOf(
            "REPRODUCCION",
            "AUDIO_CALIDAD",
            "ECUALIZADOR_DSP",
            "DESCARGAS",
            "APARIENCIA",
            "RENDIMIENTO",
            "BIBLIOTECA",
            "ALMACENAMIENTO",
            "LETRAS",
            "IDIOMA",
            "COPIAS_SEGURIDAD",
            "AVANZADO",
        )

        categories.forEach { category ->
            assertTrue("Categoría esperada: ${category.name}", expected.contains(category.name))
            assertNotNull("Cada categoría debe tener un título", category.title)
            assertTrue("El título no debe estar vacío", category.title.isNotBlank())
            assertNotNull("Cada categoría debe tener un icono", category.icon)
            assertNotNull("Cada categoría debe tener un subtítulo", category.subtitle)
            assertTrue("El subtítulo no debe estar vacío", category.subtitle.isNotBlank())
        }

        // Alias de compatibilidad hacia atrás
        assertEquals(BitChordSettingsCategory.ECUALIZADOR_DSP, BitChordSettingsCategory.AUDIO_DSP)
        assertEquals(BitChordSettingsCategory.LETRAS, BitChordSettingsCategory.LYRICS_SOURCES)
        assertEquals(BitChordSettingsCategory.ALMACENAMIENTO, BitChordSettingsCategory.STORAGE_DOWNLOADS)
        assertEquals(BitChordSettingsCategory.BIBLIOTECA, BitChordSettingsCategory.LOCAL_FILES)
    }

    // =========================================================================
    // 3. Integración de DSP y Ecualizador en Ajustes (R5)
    // =========================================================================

    @Test
    fun dspControls_areIntegratedInSettings_andPersistReactively() {
        // En Requisito R5: El acceso a controles DSP (Tone Pad, EQ paramétrico, presets)
        // se consolida en la categoría ECUALIZADOR_DSP de Ajustes.

        // Estado inicial
        assertFalse(AppSettings.equalizerEnabled.value)
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)

        // 1. Activar ecualizador
        AppSettings.setEqualizerEnabled(true)
        assertTrue(AppSettings.equalizerEnabled.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_EQ_ENABLED, false))

        // 2. Modo Tone Pad 2D
        AppSettings.setEqualizerMode(EqualizerMode.TONE_PAD)
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)
        assertEquals("TONE_PAD", fakePrefs.getString(AppSettings.KEY_EQ_MODE, null))

        // 3. Modo EQ Manual (gráfico de bandas)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        assertEquals(EqualizerMode.MANUAL, AppSettings.equalizerMode.value)
        assertEquals("MANUAL", fakePrefs.getString(AppSettings.KEY_EQ_MODE, null))

        // 4. Presets acústicos
        AppSettings.setEqualizerPreset(EqualizerPreset.VOCAL)
        assertEquals(EqualizerPreset.VOCAL, AppSettings.equalizerPreset.value)
        assertEquals("VOCAL", fakePrefs.getString(AppSettings.KEY_EQ_PRESET, null))

        // 5. Coordenadas de Tone Pad (X, Y) dentro de rango (-5..5)
        AppSettings.setEqualizerTone(4, -3)
        assertEquals(4, AppSettings.equalizerToneX.value)
        assertEquals(-3, AppSettings.equalizerToneY.value)
        assertEquals(4, fakePrefs.getInt(AppSettings.KEY_EQ_TONE_X, 0))
        assertEquals(-3, fakePrefs.getInt(AppSettings.KEY_EQ_TONE_Y, 0))

        // Clamping a límites de Tone Pad (-5..5)
        AppSettings.setEqualizerTone(12, -8)
        assertEquals(5, AppSettings.equalizerToneX.value)
        assertEquals(-5, AppSettings.equalizerToneY.value)

        // 6. Balance de ecualizador
        AppSettings.setEqualizerBalance(0.5f)
        assertEquals(0.5f, AppSettings.equalizerBalance.value, 0.001f)
        assertEquals(0.5f, fakePrefs.getFloat(AppSettings.KEY_EQ_BALANCE, 0f), 0.001f)

        // 7. Audio Espacial Haas
        AppSettings.setSpatialAudio(true)
        assertTrue(AppSettings.spatialAudio.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_SPATIAL_AUDIO, false))

        // 8. Salida PCM Audiofilo
        AppSettings.setOutputPcmMode(OutputPcmMode.FLOAT_32)
        assertEquals(OutputPcmMode.FLOAT_32, AppSettings.outputPcmMode.value)
        assertEquals("FLOAT_32", fakePrefs.getString(AppSettings.KEY_OUTPUT_PCM_MODE, null))
    }

    // =========================================================================
    // 4. Preferencia de Cola Musical en Ajustes (R4 Connection)
    // =========================================================================

    @Test
    fun swipeToPlayNextPreference_persistsAndConnectsWithQueueSystem() {
        // En Requisito R4: El comportamiento del gesto Swipe-To-Queue se configura
        // mediante AppSettings.swipeToPlayNext (true = reproducir siguiente, false = añadir al final)
        assertTrue("Por defecto swipeToPlayNext es true", AppSettings.swipeToPlayNext.value)

        AppSettings.setSwipeToPlayNext(false)
        assertFalse(AppSettings.swipeToPlayNext.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_SWIPE_TO_PLAY_NEXT, true))

        AppSettings.setSwipeToPlayNext(true)
        assertTrue(AppSettings.swipeToPlayNext.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_SWIPE_TO_PLAY_NEXT, false))
    }

    // =========================================================================
    // 5. Categorías Críticas: Audio Quality, Crossfade, Repeat, Autoplay, Theme
    // =========================================================================

    @Test
    fun criticalSettingsCategories_maintainBidirectionalStateFlowReactivity() {
        // Calidad de Audio
        AppSettings.setAudioQualityWifi(AudioQuality.HIGH)
        assertEquals(AudioQuality.HIGH, AppSettings.audioQualityWifi.value)
        assertEquals("HIGH", fakePrefs.getString(AppSettings.KEY_QUALITY_WIFI, null))

        AppSettings.setAudioQualityCellular(AudioQuality.MEDIUM)
        assertEquals(AudioQuality.MEDIUM, AppSettings.audioQualityCellular.value)
        assertEquals("MEDIUM", fakePrefs.getString(AppSettings.KEY_QUALITY_CELLULAR, null))

        // Crossfade & SmartFade
        AppSettings.setCrossfadeSeconds(5)
        assertEquals(5, AppSettings.crossfadeSeconds.value)
        assertEquals(5, fakePrefs.getInt(AppSettings.KEY_CROSSFADE, 0))

        AppSettings.setSmartFadeEnabled(true)
        assertTrue(AppSettings.smartFadeEnabled.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_SMART_FADE, false))

        // Autoplay & Repeat
        AppSettings.setAutoplay(false)
        assertFalse(AppSettings.autoplay.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_AUTOPLAY, true))

        AppSettings.setRepeatMode(AppSettings.REPEAT_MODE_ONE)
        assertEquals(AppSettings.REPEAT_MODE_ONE, AppSettings.repeatMode.value)
        assertEquals(AppSettings.REPEAT_MODE_ONE, fakePrefs.getInt(AppSettings.KEY_REPEAT_MODE, 0))

        // Descargas
        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)

        AppSettings.setWifiOnlyDownloads(true)
        assertTrue(AppSettings.wifiOnlyDownloads.value)

        // Tema
        AppSettings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppSettings.themeMode.value)
        assertEquals("DARK", fakePrefs.getString(AppSettings.KEY_THEME, null))
    }

    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf(),
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

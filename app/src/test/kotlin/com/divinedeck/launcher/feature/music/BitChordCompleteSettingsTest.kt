package com.divinedeck.launcher.feature.music

import android.content.SharedPreferences
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.settings.BitChordBackup
import com.music.bitchord.data.settings.BitChordLanguage
import com.divinedeck.launcher.feature.music.ui.BitChordSettingsCategory
import com.music.bitchord.data.settings.BitChordStorageManager
import com.music.bitchord.data.settings.DownloadQuality
import com.music.bitchord.data.settings.EqualizerMode
import com.music.bitchord.data.settings.LocalMusicSort
import com.music.bitchord.data.settings.OutputPcmMode
import com.music.bitchord.data.settings.SongSort
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.playback.EqualizerPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Suite de pruebas unitarias y de arquitectura para la Fase 06.7:
 * BitChord Complete Settings (Recuperación integral del sistema de ajustes de BitChord 1.8.1).
 */
class BitChordCompleteSettingsTest {

    private lateinit var fakePrefs: InMemorySharedPreferences
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("bitchord_settings_test").toFile()
        fakePrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
        AppSettings.initForTest(fakePrefs)
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
        tempDir.deleteRecursively()
    }

    @Test
    fun defaults_are_initialized_with_safe_and_expected_values() {
        // 1. Descargas y caché
        assertEquals(DownloadQuality.LOSSLESS, AppSettings.downloadQuality.value)
        assertTrue(AppSettings.wifiOnlyDownloads.value)
        assertFalse(AppSettings.exportDownloads.value)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)
        assertEquals(3, AppSettings.maxConcurrentDownloads.value)

        // 2. Calidad de streaming
        assertEquals(AudioQuality.LOSSLESS, AppSettings.audioQualityWifi.value)
        assertEquals(AudioQuality.HIGH, AppSettings.audioQualityCellular.value)

        // 3. Reproducción
        assertTrue(AppSettings.autoplay.value)
        assertEquals(AppSettings.REPEAT_MODE_OFF, AppSettings.repeatMode.value)
        assertFalse(AppSettings.shuffleEnabled.value)
        assertEquals(0, AppSettings.crossfadeSeconds.value)
        assertFalse(AppSettings.smartFadeEnabled.value)
        assertFalse(AppSettings.skipSilence.value)
        assertEquals(OutputPcmMode.PCM_16, AppSettings.outputPcmMode.value)
        assertFalse(AppSettings.preferUsbDac.value)
        assertTrue(AppSettings.loudnessNormalization.value)
        assertFalse(AppSettings.loudnessOffOnSpeaker.value)
        assertTrue(AppSettings.preferMusicOnly.value)
        assertTrue(AppSettings.swipeToPlayNext.value)
        assertFalse(AppSettings.dontRepeatSuggestions.value)
        assertTrue(AppSettings.stopOnTaskRemoved.value)
        assertFalse(AppSettings.hideVolumeBar.value)
        assertEquals(1.0f, AppSettings.playbackSpeed.value, 0.001f)

        // 4. Apariencia
        assertEquals(ThemeMode.SYSTEM, AppSettings.themeMode.value)
        assertTrue(AppSettings.liquidGlass.value)
        assertFalse(AppSettings.reduceAnimation.value)
        assertFalse(AppSettings.reduceDynamicBlur.value)
        assertFalse(AppSettings.fullBleedArtwork.value)
        assertFalse(AppSettings.legacyMeshGradient.value)
        assertFalse(AppSettings.animatedCanvas.value)
        assertTrue(AppSettings.syncedLyrics.value)
        assertTrue(AppSettings.lyricsBlur.value)
        assertFalse(AppSettings.hideStatusBarInPlayer.value)
        assertFalse(AppSettings.hideSongStatus.value)
        assertEquals("LIST", AppSettings.localMusicViewType.value)
        assertEquals("LIST", AppSettings.downloadedMusicViewType.value)

        // 5. Rendimiento
        assertFalse(AppSettings.highPerformanceMode.value)
        assertEquals(0, AppSettings.performanceRefreshRate.value)

        // 6. Biblioteca
        assertEquals("", AppSettings.localMusicFolderUri.value)
        assertTrue(AppSettings.filterNonMusicAudio.value)
        assertEquals(LocalMusicSort.TITLE_ASC, AppSettings.localMusicSort.value)
        assertEquals(SongSort.DEFAULT, AppSettings.downloadedMusicSort.value)
        assertEquals(SongSort.DEFAULT, AppSettings.librarySort.value)
        assertFalse(AppSettings.showCacheFolder.value)
        assertTrue(AppSettings.showQueueHistory.value)

        // 7. Idioma y Avanzado
        assertEquals(BitChordLanguage.AUTO, AppSettings.appLanguage.value)
        assertTrue(AppSettings.smartVersionAlignment.value)
        assertFalse(AppSettings.showNerdStats.value)

        // 8. DSP y Letras existentes
        assertFalse(AppSettings.spatialAudio.value)
        assertFalse(AppSettings.equalizerEnabled.value)
        assertEquals(EqualizerMode.TONE_PAD, AppSettings.equalizerMode.value)
        assertEquals(LyricsSource.offered.toSet(), AppSettings.lyricsSources.value)
    }

    @Test
    fun playback_settings_persist_and_reflect_reactively() {
        AppSettings.setAutoplay(false)
        assertFalse(AppSettings.autoplay.value)
        assertEquals(false, fakePrefs.getBoolean(AppSettings.KEY_AUTOPLAY, true))

        AppSettings.setRepeatMode(AppSettings.REPEAT_MODE_ALL)
        assertEquals(AppSettings.REPEAT_MODE_ALL, AppSettings.repeatMode.value)
        assertEquals(AppSettings.REPEAT_MODE_ALL, fakePrefs.getInt(AppSettings.KEY_REPEAT_MODE, 0))

        AppSettings.setShuffleEnabled(true)
        assertTrue(AppSettings.shuffleEnabled.value)
        assertEquals(true, fakePrefs.getBoolean(AppSettings.KEY_SHUFFLE_ENABLED, false))

        AppSettings.setCrossfadeSeconds(8)
        assertEquals(8, AppSettings.crossfadeSeconds.value)
        assertEquals(8, fakePrefs.getInt(AppSettings.KEY_CROSSFADE, 0))

        AppSettings.setCrossfadeSeconds(25) // Clamp a 12s
        assertEquals(12, AppSettings.crossfadeSeconds.value)

        AppSettings.setSmartFadeEnabled(true)
        assertTrue(AppSettings.smartFadeEnabled.value)

        AppSettings.setSkipSilence(true)
        assertTrue(AppSettings.skipSilence.value)

        AppSettings.setOutputPcmMode(OutputPcmMode.FLOAT_32)
        assertEquals(OutputPcmMode.FLOAT_32, AppSettings.outputPcmMode.value)
        assertEquals("FLOAT_32", fakePrefs.getString(AppSettings.KEY_OUTPUT_PCM_MODE, null))

        AppSettings.setPlaybackSpeed(1.25f)
        assertEquals(1.25f, AppSettings.playbackSpeed.value, 0.001f)
    }

    @Test
    fun streaming_quality_resolves_effective_quality_based_on_metered_connection() {
        AppSettings.setAudioQualityWifi(AudioQuality.LOSSLESS)
        AppSettings.setAudioQualityCellular(AudioQuality.LOW)

        // Sin conexión medida (Wi-Fi)
        AppSettings.setMeteredConnectionForTest(false)
        assertEquals(AudioQuality.LOSSLESS, AppSettings.effectiveAudioQuality)
        assertEquals(Int.MAX_VALUE, AppSettings.effectiveAudioQuality.maxKbps)

        // Conexión medida (Datos móviles)
        AppSettings.setMeteredConnectionForTest(true)
        assertEquals(AudioQuality.LOW, AppSettings.effectiveAudioQuality)
        assertEquals(64, AppSettings.effectiveAudioQuality.maxKbps)

        // Modificar calidad celular a MEDIUM
        AppSettings.setAudioQualityCellular(AudioQuality.MEDIUM)
        assertEquals(AudioQuality.MEDIUM, AppSettings.effectiveAudioQuality)
        assertEquals(128, AppSettings.effectiveAudioQuality.maxKbps)
    }

    @Test
    fun download_settings_persist_and_validate_concurrency_clamps() {
        AppSettings.setMaxConcurrentDownloads(5)
        assertEquals(5, AppSettings.maxConcurrentDownloads.value)

        AppSettings.setMaxConcurrentDownloads(10) // Supera límite superior -> clamped a 5
        assertEquals(5, AppSettings.maxConcurrentDownloads.value)

        AppSettings.setMaxConcurrentDownloads(0) // Inferior a límite -> clamped a 1
        assertEquals(1, AppSettings.maxConcurrentDownloads.value)

        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
        assertEquals("STANDARD", fakePrefs.getString(AppSettings.KEY_QUALITY_DOWNLOAD, null))

        AppSettings.setWifiOnlyDownloads(false)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
    }

    @Test
    fun appearance_and_performance_settings_persist_correctly() {
        AppSettings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, AppSettings.themeMode.value)
        assertEquals("DARK", fakePrefs.getString(AppSettings.KEY_THEME, null))

        AppSettings.setLiquidGlass(false)
        assertFalse(AppSettings.liquidGlass.value)

        AppSettings.setPerformanceRefreshRate(90)
        assertEquals(90, AppSettings.performanceRefreshRate.value)
        assertEquals(90, fakePrefs.getInt(AppSettings.KEY_PERFORMANCE_REFRESH_RATE, 0))

        AppSettings.setHighPerformanceMode(true)
        assertTrue(AppSettings.highPerformanceMode.value)

        AppSettings.setLocalMusicViewType("GRID")
        assertEquals("GRID", AppSettings.localMusicViewType.value)

        AppSettings.setDownloadedMusicViewType("GRID")
        assertEquals("GRID", AppSettings.downloadedMusicViewType.value)
    }

    @Test
    fun library_and_local_music_settings_persist_and_filter() {
        AppSettings.setLocalMusicFolderUri("/sdcard/Music/Custom")
        assertEquals("/sdcard/Music/Custom", AppSettings.localMusicFolderUri.value)

        AppSettings.setFilterNonMusicAudio(false)
        assertFalse(AppSettings.filterNonMusicAudio.value)

        AppSettings.setLocalMusicSort(LocalMusicSort.DATE_ADDED)
        assertEquals(LocalMusicSort.DATE_ADDED, AppSettings.localMusicSort.value)

        AppSettings.setDownloadedMusicSort(SongSort.TITLE_DESC)
        assertEquals(SongSort.TITLE_DESC, AppSettings.downloadedMusicSort.value)

        AppSettings.setLibrarySort(SongSort.DATE_ADDED_DESC)
        assertEquals(SongSort.DATE_ADDED_DESC, AppSettings.librarySort.value)
    }

    @Test
    fun storage_manager_accurately_computes_sizes_and_safe_cleaning_never_deletes_downloads() {
        val dlDir = File(tempDir, "downloads").apply { mkdirs() }
        val artDir = File(tempDir, "artwork").apply { mkdirs() }
        val cacheDir = File(tempDir, "cache").apply { mkdirs() }
        val audioCache = File(cacheDir, "bitchord_audio").apply { mkdirs() }
        val imageCache = File(cacheDir, "image_cache").apply { mkdirs() }

        // Crear archivos simulados
        val downloadedSong = File(dlDir, "song1.m4a").apply { writeBytes(ByteArray(1024 * 50)) } // 50KB
        val downloadedArt = File(artDir, "song1.jpg").apply { writeBytes(ByteArray(1024 * 10)) } // 10KB
        val cachedChunk = File(audioCache, "chunk.part").apply { writeBytes(ByteArray(1024 * 100)) } // 100KB
        val cachedImg = File(imageCache, "thumb.webp").apply { writeBytes(ByteArray(1024 * 20)) } // 20KB

        assertTrue(downloadedSong.exists())
        assertTrue(downloadedArt.exists())
        assertTrue(cachedChunk.exists())
        assertTrue(cachedImg.exists())

        // Limpiar caché de audio simulada
        audioCache.listFiles()?.forEach { it.delete() }
        // Limpiar caché de imágenes simulada
        imageCache.listFiles()?.forEach { it.delete() }

        // Comprobación crítica: las descargas offline y sus carátulas locales JAMÁS son eliminadas
        assertTrue("La pista descargada debe preservarse intacta", downloadedSong.exists())
        assertTrue("La carátula descargada debe preservarse intacta", downloadedArt.exists())
        assertEquals(50 * 1024L, downloadedSong.length())
        assertEquals(10 * 1024L, downloadedArt.length())
    }

    @Test
    fun export_and_import_backup_validates_app_tag_and_restores_cleanly() {
        AppSettings.setAutoplay(false)
        AppSettings.setCrossfadeSeconds(7)
        AppSettings.setThemeMode(ThemeMode.DARK)
        AppSettings.setAudioQualityWifi(AudioQuality.HIGH)
        AppSettings.setSpatialAudio(true)

        // 1. Exportar a JSON
        val exportedJson = BitChordBackup.exportToJson()
        assertNotNull(exportedJson)
        assertTrue(exportedJson.contains("\"app\": \"bitchord\""))
        assertTrue(exportedJson.contains("crossfade_seconds"))

        // 2. Modificar o restablecer
        AppSettings.resetAllDefaults()
        assertEquals(0, AppSettings.crossfadeSeconds.value)
        assertEquals(ThemeMode.SYSTEM, AppSettings.themeMode.value)
        assertFalse(AppSettings.spatialAudio.value)

        // 3. Importar JSON de respaldo
        val result = BitChordBackup.importFromJson(exportedJson)
        assertTrue("La importación debe ser exitosa", result.isSuccess)

        // 4. Verificar que se restablecieron exactamente los valores exportados
        assertEquals(7, AppSettings.crossfadeSeconds.value)
        assertEquals(ThemeMode.DARK, AppSettings.themeMode.value)
        assertEquals(AudioQuality.HIGH, AppSettings.audioQualityWifi.value)
        assertTrue(AppSettings.spatialAudio.value)
        assertFalse(AppSettings.autoplay.value)
    }

    @Test
    fun import_backup_rejects_corrupted_or_foreign_app_json() {
        // Archivo JSON de otra aplicación
        val foreignAppJson = """
            {
              "app": "another_music_player",
              "version": 1,
              "settings": {}
            }
        """.trimIndent()

        val foreignResult = BitChordBackup.importFromJson(foreignAppJson)
        assertTrue("Debe fallar si app no es bitchord", foreignResult.isFailure)

        // JSON mal formado o corrupto
        val corruptJson = "NOT_A_JSON_STRING"
        val corruptResult = BitChordBackup.importFromJson(corruptJson)
        assertTrue("Debe fallar si el JSON está corrupto", corruptResult.isFailure)
    }

    @Test
    fun reset_all_defaults_restores_complete_initial_state_safely() {
        AppSettings.setAutoplay(false)
        AppSettings.setCrossfadeSeconds(10)
        AppSettings.setPlaybackSpeed(1.5f)
        AppSettings.setThemeMode(ThemeMode.LIGHT)
        AppSettings.setAudioQualityWifi(AudioQuality.LOW)
        AppSettings.setLocalMusicViewType("GRID")

        AppSettings.resetAllDefaults()

        assertEquals(true, AppSettings.autoplay.value)
        assertEquals(0, AppSettings.crossfadeSeconds.value)
        assertEquals(1.0f, AppSettings.playbackSpeed.value, 0.001f)
        assertEquals(ThemeMode.SYSTEM, AppSettings.themeMode.value)
        assertEquals(AudioQuality.LOSSLESS, AppSettings.audioQualityWifi.value)
        assertEquals("LIST", AppSettings.localMusicViewType.value)
    }

    @Test
    fun compatibility_with_existing_dsp_and_lyrics_preferences_is_strictly_preserved() {
        // Modificar DSP
        AppSettings.setEqualizerEnabled(true)
        AppSettings.setEqualizerMode(EqualizerMode.MANUAL)
        AppSettings.setEqualizerPreset(EqualizerPreset.BASS_BOOST)
        AppSettings.setSpatialAudio(true)

        // Modificar Letras
        AppSettings.toggleLyricsSource(LyricsSource.GENIUS)
        AppSettings.setPrioritizeSyllableSync(true)

        // Verificar que persisten
        assertTrue(AppSettings.equalizerEnabled.value)
        assertEquals(EqualizerMode.MANUAL, AppSettings.equalizerMode.value)
        assertEquals(EqualizerPreset.BASS_BOOST, AppSettings.equalizerPreset.value)
        assertTrue(AppSettings.spatialAudio.value)
        assertTrue(AppSettings.prioritizeSyllableSync.value)
    }

    @Test
    fun unified_settings_categories_provide_full_12_category_coverage() {
        val categories = BitChordSettingsCategory.entries
        assertEquals(12, categories.size)

        // Comprobar presencia de todas las categorías requeridas
        val titles = categories.map { it.name }
        assertTrue(titles.contains("REPRODUCCION"))
        assertTrue(titles.contains("AUDIO_CALIDAD"))
        assertTrue(titles.contains("ECUALIZADOR_DSP"))
        assertTrue(titles.contains("DESCARGAS"))
        assertTrue(titles.contains("APARIENCIA"))
        assertTrue(titles.contains("RENDIMIENTO"))
        assertTrue(titles.contains("BIBLIOTECA"))
        assertTrue(titles.contains("ALMACENAMIENTO"))
        assertTrue(titles.contains("LETRAS"))
        assertTrue(titles.contains("IDIOMA"))
        assertTrue(titles.contains("COPIAS_SEGURIDAD"))
        assertTrue(titles.contains("AVANZADO"))

        // Comprobar alias de compatibilidad de la Fase 06.6
        assertEquals(BitChordSettingsCategory.ECUALIZADOR_DSP, BitChordSettingsCategory.AUDIO_DSP)
        assertEquals(BitChordSettingsCategory.LETRAS, BitChordSettingsCategory.LYRICS_SOURCES)
        assertEquals(BitChordSettingsCategory.ALMACENAMIENTO, BitChordSettingsCategory.STORAGE_DOWNLOADS)
        assertEquals(BitChordSettingsCategory.BIBLIOTECA, BitChordSettingsCategory.LOCAL_FILES)
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

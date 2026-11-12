package com.music.bitchord.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.music.bitchord.data.lyrics.LyricsEffectStyle
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.lyricsOfflineModeOverride
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.EqLayout
import com.music.bitchord.playback.EqualizerPreset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Operating mode of the equalizer.
 */
enum class EqualizerMode {
    /** Seven graphic sliders with preset selector. */
    MANUAL,

    /** 2D Tone Pad: warmth/brightness and contour. */
    TONE_PAD;

    companion object {
        fun fromString(value: String?): EqualizerMode = when (value?.uppercase()) {
            "MANUAL" -> MANUAL
            "TONE_PAD", "DYNAMIC" -> TONE_PAD
            else -> TONE_PAD
        }
    }
}

/**
 * Central preferences singleton for BitChord multimedia integration in DIVINE DECK.
 *
 * Exposes reactive [StateFlow]s with instant in-memory reflection, thread-safe lazy
 * initialization, persistence via SharedPreferences ("bitchord_settings"), and export/import
 * capabilities for backup and restoration.
 * Extracted and adapted from BitChord 1.8.1 (GPLv3).
 */
object AppSettings {

    const val PREFS_NAME = "bitchord_settings"

    const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024 // 512 MB
    const val MAX_CACHE_LIMIT_BYTES = 10L * 1024 * 1024 * 1024 // 10 GB
    const val UNLIMITED_CACHE_LIMIT_BYTES = Long.MAX_VALUE

    // Repeat Modes
    const val REPEAT_MODE_OFF = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2

    // Keys: Downloads & Cache
    const val KEY_QUALITY_DOWNLOAD = "audio_quality_download"
    const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
    const val KEY_EXPORT_DOWNLOADS = "export_downloads"
    const val KEY_CACHE_LIMIT = "audio_cache_limit_bytes"
    const val KEY_MAX_CONCURRENT_DOWNLOADS = "max_concurrent_downloads"

    // Keys: Audio & Streaming Quality
    const val KEY_QUALITY_WIFI = "audio_quality_wifi"
    const val KEY_QUALITY_CELLULAR = "audio_quality_cellular"

    // Keys: Playback
    const val KEY_AUTOPLAY = "autoplay"
    const val KEY_REPEAT_MODE = "repeat_mode"
    const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"
    const val KEY_CROSSFADE = "crossfade_seconds"
    const val KEY_SMART_FADE = "smart_fade_enabled"
    const val KEY_SKIP_SILENCE = "skip_silence"
    const val KEY_OUTPUT_PCM_MODE = "output_pcm_mode"
    const val KEY_PREFER_USB_DAC = "prefer_usb_dac"
    const val KEY_LOUDNESS_NORMALIZATION = "loudness_normalization"
    const val KEY_LOUDNESS_OFF_ON_SPEAKER = "loudness_off_on_speaker"
    const val KEY_PREFER_MUSIC_ONLY = "prefer_music_only"
    const val KEY_SWIPE_TO_PLAY_NEXT = "swipe_to_play_next"
    const val KEY_DONT_REPEAT_SUGGESTIONS = "dont_repeat_suggestions"
    const val KEY_STOP_ON_TASK_REMOVED = "stop_on_task_removed"
    const val KEY_HIDE_VOLUME_BAR = "hide_volume_bar"
    const val KEY_SPEED = "playback_speed"

    // Keys: Appearance
    const val KEY_THEME = "theme_mode"
    const val KEY_LIQUID_GLASS = "liquid_glass"
    const val KEY_REDUCE_ANIMATION = "reduce_animation"
    const val KEY_REDUCE_BLUR = "reduce_dynamic_blur"
    const val KEY_FULL_BLEED_ARTWORK = "full_bleed_artwork"
    const val KEY_LEGACY_MESH_GRADIENT = "legacy_mesh_gradient"
    const val KEY_ANIMATED_CANVAS = "animated_canvas"
    const val KEY_SYNCED_LYRICS = "synced_lyrics"
    const val KEY_LYRICS_BLUR = "lyrics_blur"
    const val KEY_HIDE_STATUS_BAR_IN_PLAYER = "hide_status_bar_in_player"
    const val KEY_HIDE_SONG_STATUS = "hide_song_status"
    const val KEY_LOCAL_MUSIC_VIEW_TYPE = "local_music_view_type"
    const val KEY_DOWNLOADED_MUSIC_VIEW_TYPE = "downloaded_music_view_type"

    // Keys: Performance
    const val KEY_HIGH_PERFORMANCE_MODE = "high_performance_mode"
    const val KEY_PERFORMANCE_REFRESH_RATE = "performance_refresh_rate"

    // Keys: Library & Local Music
    const val KEY_LOCAL_MUSIC_FOLDER_URI = "local_music_folder_uri"
    const val KEY_FILTER_NON_MUSIC_AUDIO = "filter_non_music_audio"
    const val KEY_LOCAL_MUSIC_SORT = "local_music_sort"
    const val KEY_DOWNLOADED_MUSIC_SORT = "downloaded_music_sort"
    const val KEY_LIBRARY_SORT = "library_sort"
    const val KEY_SHOW_CACHE_FOLDER = "show_cache_folder"
    const val KEY_SHOW_QUEUE_HISTORY = "show_queue_history"

    // Keys: Language
    const val KEY_APP_LANGUAGE = "app_language"

    // Keys: Advanced & Diagnostics
    const val KEY_SMART_VERSION_ALIGNMENT = "smart_version_alignment"
    const val KEY_NERD_STATS = "show_nerd_stats"

    // Keys: Audio DSP
    const val KEY_SPATIAL_AUDIO = "spatial_audio"
    const val KEY_EQ_ENABLED = "equalizer_enabled"
    const val KEY_EQ_MODE = "equalizer_mode"
    const val KEY_EQ_TONE_X = "equalizer_tone_x"
    const val KEY_EQ_TONE_Y = "equalizer_tone_y"
    const val KEY_EQ_FOCUSED = "equalizer_focused"
    const val KEY_EQ_BALANCE = "equalizer_balance"
    const val KEY_EQ_BANDS = "equalizer_bands"
    const val KEY_EQ_PRESET = "equalizer_preset"

    // Keys: Lyrics Sources
    const val KEY_LYRICS_SOURCES = "lyrics_sources"
    const val KEY_LYRICS_SOURCES_SEEN = "lyrics_sources_seen"
    const val KEY_LYRICS_SOURCE_ORDER = "lyrics_source_order"
    const val KEY_PRIORITIZE_SYLLABLE_SYNC = "prioritize_syllable_sync"

    // Keys: Lyrics Visual Effects (Milestone 1)
    const val KEY_LYRICS_EFFECTS_ENABLED = "lyrics_effects_enabled"
    const val KEY_LYRICS_EFFECT_STYLE = "lyrics_effect_style"
    const val KEY_LYRICS_EFFECT_INTENSITY = "lyrics_effect_intensity"
    const val KEY_LYRICS_BEAT_REACTION = "lyrics_beat_reaction"
    const val KEY_LYRICS_LOW_POWER_MODE = "lyrics_low_power_mode"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var initialized = false

    // ---- 1. Downloads & Cache State ---------------------------------------

    private val _downloadQuality = MutableStateFlow(DownloadQuality.LOSSLESS)
    val downloadQuality: StateFlow<DownloadQuality> = _downloadQuality.asStateFlow()
    val currentDownloadQuality: DownloadQuality get() = _downloadQuality.value

    private val _wifiOnlyDownloads = MutableStateFlow(true)
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()
    val isWifiOnlyDownloads: Boolean get() = _wifiOnlyDownloads.value

    private val _exportDownloads = MutableStateFlow(false)
    val exportDownloads: StateFlow<Boolean> = _exportDownloads.asStateFlow()
    val isExportDownloads: Boolean get() = _exportDownloads.value

    private val _audioCacheLimitBytes = MutableStateFlow(DEFAULT_CACHE_LIMIT_BYTES)
    val audioCacheLimitBytes: StateFlow<Long> = _audioCacheLimitBytes.asStateFlow()
    val currentAudioCacheLimitBytes: Long get() = _audioCacheLimitBytes.value

    private val _maxConcurrentDownloads = MutableStateFlow(3)
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads.asStateFlow()

    private val _meteredConnection = MutableStateFlow<Boolean?>(null)
    val meteredConnection: StateFlow<Boolean?> = _meteredConnection.asStateFlow()

    // ---- 2. Audio & Streaming Quality State --------------------------------

    private val _audioQualityWifi = MutableStateFlow(AudioQuality.LOSSLESS)
    val audioQualityWifi: StateFlow<AudioQuality> = _audioQualityWifi.asStateFlow()

    private val _audioQualityCellular = MutableStateFlow(AudioQuality.HIGH)
    val audioQualityCellular: StateFlow<AudioQuality> = _audioQualityCellular.asStateFlow()

    val effectiveAudioQuality: AudioQuality
        get() = if (_meteredConnection.value == true) _audioQualityCellular.value else _audioQualityWifi.value

    // ---- 3. Playback State -------------------------------------------------

    private val _autoplay = MutableStateFlow(true)
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(0)
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    private val _smartFadeEnabled = MutableStateFlow(false)
    val smartFadeEnabled: StateFlow<Boolean> = _smartFadeEnabled.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    private val _outputPcmMode = MutableStateFlow(OutputPcmMode.PCM_16)
    val outputPcmMode: StateFlow<OutputPcmMode> = _outputPcmMode.asStateFlow()

    private val _preferUsbDac = MutableStateFlow(false)
    val preferUsbDac: StateFlow<Boolean> = _preferUsbDac.asStateFlow()

    private val _loudnessNormalization = MutableStateFlow(true)
    val loudnessNormalization: StateFlow<Boolean> = _loudnessNormalization.asStateFlow()

    private val _loudnessOffOnSpeaker = MutableStateFlow(false)
    val loudnessOffOnSpeaker: StateFlow<Boolean> = _loudnessOffOnSpeaker.asStateFlow()

    private val _preferMusicOnly = MutableStateFlow(true)
    val preferMusicOnly: StateFlow<Boolean> = _preferMusicOnly.asStateFlow()

    private val _swipeToPlayNext = MutableStateFlow(true)
    val swipeToPlayNext: StateFlow<Boolean> = _swipeToPlayNext.asStateFlow()

    private val _dontRepeatSuggestions = MutableStateFlow(false)
    val dontRepeatSuggestions: StateFlow<Boolean> = _dontRepeatSuggestions.asStateFlow()

    private val _stopOnTaskRemoved = MutableStateFlow(true)
    val stopOnTaskRemoved: StateFlow<Boolean> = _stopOnTaskRemoved.asStateFlow()

    private val _hideVolumeBar = MutableStateFlow(false)
    val hideVolumeBar: StateFlow<Boolean> = _hideVolumeBar.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // ---- 4. Appearance State -----------------------------------------------

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _liquidGlass = MutableStateFlow(true)
    val liquidGlass: StateFlow<Boolean> = _liquidGlass.asStateFlow()

    private val _reduceAnimation = MutableStateFlow(false)
    val reduceAnimation: StateFlow<Boolean> = _reduceAnimation.asStateFlow()

    private val _reduceDynamicBlur = MutableStateFlow(false)
    val reduceDynamicBlur: StateFlow<Boolean> = _reduceDynamicBlur.asStateFlow()

    private val _fullBleedArtwork = MutableStateFlow(false)
    val fullBleedArtwork: StateFlow<Boolean> = _fullBleedArtwork.asStateFlow()

    private val _legacyMeshGradient = MutableStateFlow(false)
    val legacyMeshGradient: StateFlow<Boolean> = _legacyMeshGradient.asStateFlow()

    private val _animatedCanvas = MutableStateFlow(false)
    val animatedCanvas: StateFlow<Boolean> = _animatedCanvas.asStateFlow()

    private val _syncedLyrics = MutableStateFlow(true)
    val syncedLyrics: StateFlow<Boolean> = _syncedLyrics.asStateFlow()

    private val _lyricsBlur = MutableStateFlow(true)
    val lyricsBlur: StateFlow<Boolean> = _lyricsBlur.asStateFlow()

    private val _hideStatusBarInPlayer = MutableStateFlow(false)
    val hideStatusBarInPlayer: StateFlow<Boolean> = _hideStatusBarInPlayer.asStateFlow()

    private val _hideSongStatus = MutableStateFlow(false)
    val hideSongStatus: StateFlow<Boolean> = _hideSongStatus.asStateFlow()

    private val _localMusicViewType = MutableStateFlow("LIST")
    val localMusicViewType: StateFlow<String> = _localMusicViewType.asStateFlow()

    private val _downloadedMusicViewType = MutableStateFlow("LIST")
    val downloadedMusicViewType: StateFlow<String> = _downloadedMusicViewType.asStateFlow()

    // ---- 5. Performance State ----------------------------------------------

    private val _highPerformanceMode = MutableStateFlow(false)
    val highPerformanceMode: StateFlow<Boolean> = _highPerformanceMode.asStateFlow()

    private val _performanceRefreshRate = MutableStateFlow(0)
    val performanceRefreshRate: StateFlow<Int> = _performanceRefreshRate.asStateFlow()

    // ---- 6. Library & Local Music State ------------------------------------

    private val _localMusicFolderUri = MutableStateFlow("")
    val localMusicFolderUri: StateFlow<String> = _localMusicFolderUri.asStateFlow()

    private val _filterNonMusicAudio = MutableStateFlow(true)
    val filterNonMusicAudio: StateFlow<Boolean> = _filterNonMusicAudio.asStateFlow()

    private val _localMusicSort = MutableStateFlow(LocalMusicSort.TITLE_ASC)
    val localMusicSort: StateFlow<LocalMusicSort> = _localMusicSort.asStateFlow()

    private val _downloadedMusicSort = MutableStateFlow(SongSort.DEFAULT)
    val downloadedMusicSort: StateFlow<SongSort> = _downloadedMusicSort.asStateFlow()

    private val _librarySort = MutableStateFlow(SongSort.DEFAULT)
    val librarySort: StateFlow<SongSort> = _librarySort.asStateFlow()

    private val _showCacheFolder = MutableStateFlow(false)
    val showCacheFolder: StateFlow<Boolean> = _showCacheFolder.asStateFlow()

    private val _showQueueHistory = MutableStateFlow(true)
    val showQueueHistory: StateFlow<Boolean> = _showQueueHistory.asStateFlow()

    // ---- 7. Language State -------------------------------------------------

    private val _appLanguage = MutableStateFlow(BitChordLanguage.AUTO)
    val appLanguage: StateFlow<BitChordLanguage> = _appLanguage.asStateFlow()

    // ---- 8. Advanced & Diagnostics State -----------------------------------

    private val _smartVersionAlignment = MutableStateFlow(true)
    val smartVersionAlignment: StateFlow<Boolean> = _smartVersionAlignment.asStateFlow()

    private val _showNerdStats = MutableStateFlow(false)
    val showNerdStats: StateFlow<Boolean> = _showNerdStats.asStateFlow()

    // ---- 9. Audio DSP State ------------------------------------------------

    private val _spatialAudio = MutableStateFlow(false)
    val spatialAudio: StateFlow<Boolean> = _spatialAudio.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerMode = MutableStateFlow(EqualizerMode.TONE_PAD)
    val equalizerMode: StateFlow<EqualizerMode> = _equalizerMode.asStateFlow()

    private val _equalizerToneX = MutableStateFlow(0)
    val equalizerToneX: StateFlow<Int> = _equalizerToneX.asStateFlow()

    private val _equalizerToneY = MutableStateFlow(0)
    val equalizerToneY: StateFlow<Int> = _equalizerToneY.asStateFlow()

    private val _equalizerFocused = MutableStateFlow(false)
    val equalizerFocused: StateFlow<Boolean> = _equalizerFocused.asStateFlow()

    private val _equalizerBalance = MutableStateFlow(0f)
    val equalizerBalance: StateFlow<Float> = _equalizerBalance.asStateFlow()

    private val _equalizerBands = MutableStateFlow(EqualizerPreset.FLAT.bands)
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _equalizerPreset = MutableStateFlow(EqualizerPreset.FLAT)
    val equalizerPreset: StateFlow<EqualizerPreset> = _equalizerPreset.asStateFlow()

    // ---- 10. Lyrics Sources State ------------------------------------------

    private val _lyricsSources = MutableStateFlow<Set<LyricsSource>>(LyricsSource.offered.toSet())
    val lyricsSources: StateFlow<Set<LyricsSource>> = _lyricsSources.asStateFlow()

    private val _lyricsSourceOrder = MutableStateFlow<List<LyricsSource>>(LyricsSource.offered)
    val lyricsSourceOrder: StateFlow<List<LyricsSource>> = _lyricsSourceOrder.asStateFlow()

    private val _prioritizeSyllableSync = MutableStateFlow(false)
    val prioritizeSyllableSync: StateFlow<Boolean> = _prioritizeSyllableSync.asStateFlow()

    // ---- 11. Lyrics Visual Effects State (Milestone 1) ---------------------

    private val _lyricsEffectsEnabled = MutableStateFlow(true)
    val lyricsEffectsEnabled: StateFlow<Boolean> = _lyricsEffectsEnabled.asStateFlow()

    private val _lyricsEffectStyle = MutableStateFlow(LyricsEffectStyle.PULSE)
    val lyricsEffectStyle: StateFlow<LyricsEffectStyle> = _lyricsEffectStyle.asStateFlow()

    private val _lyricsEffectIntensity = MutableStateFlow(1.0f)
    val lyricsEffectIntensity: StateFlow<Float> = _lyricsEffectIntensity.asStateFlow()

    private val _lyricsBeatReaction = MutableStateFlow(true)
    val lyricsBeatReaction: StateFlow<Boolean> = _lyricsBeatReaction.asStateFlow()

    private val _lyricsLowPowerMode = MutableStateFlow(false)
    val lyricsLowPowerMode: StateFlow<Boolean> = _lyricsLowPowerMode.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var connectivityManager: ConnectivityManager? = null

    val downloadsAllowedNow: Boolean
        get() = !_wifiOnlyDownloads.value || _meteredConnection.value != true

    /**
     * Initializes [AppSettings] with the application context.
     * Safe to invoke multiple times; duplicates are ignored.
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext ?: context
            val sp = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            readAll(sp)
            watchConnection(appContext)
            initialized = true
        }
    }

    /**
     * Test isolation initialiser accepting an explicit [SharedPreferences].
     */
    fun initForTest(sharedPreferences: SharedPreferences) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = sharedPreferences
            readAll(sharedPreferences)
            initialized = true
        }
    }

    /**
     * Resets in-memory state and detaches preferences for test cleanup.
     */
    fun resetForTest() {
        synchronized(this) {
            unregisterNetworkCallback()
            prefs = null
            initialized = false
            _downloadQuality.value = DownloadQuality.LOSSLESS
            _wifiOnlyDownloads.value = true
            _exportDownloads.value = false
            _audioCacheLimitBytes.value = DEFAULT_CACHE_LIMIT_BYTES
            _maxConcurrentDownloads.value = 3
            _meteredConnection.value = null

            _audioQualityWifi.value = AudioQuality.LOSSLESS
            _audioQualityCellular.value = AudioQuality.HIGH

            _autoplay.value = true
            _repeatMode.value = REPEAT_MODE_OFF
            _shuffleEnabled.value = false
            _crossfadeSeconds.value = 0
            _smartFadeEnabled.value = false
            _skipSilence.value = false
            _outputPcmMode.value = OutputPcmMode.PCM_16
            _preferUsbDac.value = false
            _loudnessNormalization.value = true
            _loudnessOffOnSpeaker.value = false
            _preferMusicOnly.value = true
            _swipeToPlayNext.value = true
            _dontRepeatSuggestions.value = false
            _stopOnTaskRemoved.value = true
            _hideVolumeBar.value = false
            _playbackSpeed.value = 1.0f

            _themeMode.value = ThemeMode.SYSTEM
            _liquidGlass.value = true
            _reduceAnimation.value = false
            _reduceDynamicBlur.value = false
            _fullBleedArtwork.value = false
            _legacyMeshGradient.value = false
            _animatedCanvas.value = false
            _syncedLyrics.value = true
            _lyricsBlur.value = true
            _hideStatusBarInPlayer.value = false
            _hideSongStatus.value = false
            _localMusicViewType.value = "LIST"
            _downloadedMusicViewType.value = "LIST"

            _highPerformanceMode.value = false
            _performanceRefreshRate.value = 0

            _localMusicFolderUri.value = ""
            _filterNonMusicAudio.value = true
            _localMusicSort.value = LocalMusicSort.TITLE_ASC
            _downloadedMusicSort.value = SongSort.DEFAULT
            _librarySort.value = SongSort.DEFAULT
            _showCacheFolder.value = false
            _showQueueHistory.value = true

            _appLanguage.value = BitChordLanguage.AUTO
            _smartVersionAlignment.value = true
            _showNerdStats.value = false

            _spatialAudio.value = false
            _equalizerEnabled.value = false
            _equalizerMode.value = EqualizerMode.TONE_PAD
            _equalizerToneX.value = 0
            _equalizerToneY.value = 0
            _equalizerFocused.value = false
            _equalizerBalance.value = 0f
            _equalizerBands.value = EqualizerPreset.FLAT.bands
            _equalizerPreset.value = EqualizerPreset.FLAT

            _lyricsSources.value = LyricsSource.offered.toSet()
            _lyricsSourceOrder.value = LyricsSource.offered
            _prioritizeSyllableSync.value = false

            _lyricsEffectsEnabled.value = true
            _lyricsEffectStyle.value = LyricsEffectStyle.PULSE
            _lyricsEffectIntensity.value = 1.0f
            _lyricsBeatReaction.value = true
            _lyricsLowPowerMode.value = false
        }
    }

    fun setMeteredConnectionForTest(isMetered: Boolean?) {
        _meteredConnection.value = isMetered
    }

    private fun readAll(sp: SharedPreferences) {
        val rawDlQuality = sp.getString(KEY_QUALITY_DOWNLOAD, null)
        _downloadQuality.value = if (rawDlQuality != null) {
            runCatching { DownloadQuality.valueOf(rawDlQuality) }.getOrDefault(DownloadQuality.LOSSLESS)
        } else DownloadQuality.LOSSLESS

        _wifiOnlyDownloads.value = sp.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true)
        _exportDownloads.value = sp.getBoolean(KEY_EXPORT_DOWNLOADS, false)
        _audioCacheLimitBytes.value = clampCacheLimit(sp.getLong(KEY_CACHE_LIMIT, DEFAULT_CACHE_LIMIT_BYTES))
        _maxConcurrentDownloads.value = sp.getInt(KEY_MAX_CONCURRENT_DOWNLOADS, 3).coerceIn(1, 5)

        _audioQualityWifi.value = AudioQuality.fromString(sp.getString(KEY_QUALITY_WIFI, null))
        _audioQualityCellular.value = AudioQuality.fromString(sp.getString(KEY_QUALITY_CELLULAR, "HIGH"))

        _autoplay.value = sp.getBoolean(KEY_AUTOPLAY, true)
        _repeatMode.value = sp.getInt(KEY_REPEAT_MODE, REPEAT_MODE_OFF)
        _shuffleEnabled.value = sp.getBoolean(KEY_SHUFFLE_ENABLED, false)
        _crossfadeSeconds.value = sp.getInt(KEY_CROSSFADE, 0).coerceIn(0, 12)
        _smartFadeEnabled.value = sp.getBoolean(KEY_SMART_FADE, false)
        _skipSilence.value = sp.getBoolean(KEY_SKIP_SILENCE, false)
        _outputPcmMode.value = OutputPcmMode.fromString(sp.getString(KEY_OUTPUT_PCM_MODE, null))
        _preferUsbDac.value = sp.getBoolean(KEY_PREFER_USB_DAC, false)
        _loudnessNormalization.value = sp.getBoolean(KEY_LOUDNESS_NORMALIZATION, true)
        _loudnessOffOnSpeaker.value = sp.getBoolean(KEY_LOUDNESS_OFF_ON_SPEAKER, false)
        _preferMusicOnly.value = sp.getBoolean(KEY_PREFER_MUSIC_ONLY, true)
        _swipeToPlayNext.value = sp.getBoolean(KEY_SWIPE_TO_PLAY_NEXT, true)
        _dontRepeatSuggestions.value = sp.getBoolean(KEY_DONT_REPEAT_SUGGESTIONS, false)
        _stopOnTaskRemoved.value = sp.getBoolean(KEY_STOP_ON_TASK_REMOVED, true)
        _hideVolumeBar.value = sp.getBoolean(KEY_HIDE_VOLUME_BAR, false)
        _playbackSpeed.value = sp.getFloat(KEY_SPEED, 1.0f).coerceIn(0.25f, 3.0f)

        _themeMode.value = ThemeMode.fromString(sp.getString(KEY_THEME, null))
        _liquidGlass.value = sp.getBoolean(KEY_LIQUID_GLASS, true)
        _reduceAnimation.value = sp.getBoolean(KEY_REDUCE_ANIMATION, false)
        _reduceDynamicBlur.value = sp.getBoolean(KEY_REDUCE_BLUR, false)
        _fullBleedArtwork.value = sp.getBoolean(KEY_FULL_BLEED_ARTWORK, false)
        _legacyMeshGradient.value = sp.getBoolean(KEY_LEGACY_MESH_GRADIENT, false)
        _animatedCanvas.value = sp.getBoolean(KEY_ANIMATED_CANVAS, false)
        _syncedLyrics.value = sp.getBoolean(KEY_SYNCED_LYRICS, true)
        _lyricsBlur.value = sp.getBoolean(KEY_LYRICS_BLUR, true)
        _hideStatusBarInPlayer.value = sp.getBoolean(KEY_HIDE_STATUS_BAR_IN_PLAYER, false)
        _hideSongStatus.value = sp.getBoolean(KEY_HIDE_SONG_STATUS, false)
        _localMusicViewType.value = sp.getString(KEY_LOCAL_MUSIC_VIEW_TYPE, "LIST") ?: "LIST"
        _downloadedMusicViewType.value = sp.getString(KEY_DOWNLOADED_MUSIC_VIEW_TYPE, "LIST") ?: "LIST"

        _highPerformanceMode.value = sp.getBoolean(KEY_HIGH_PERFORMANCE_MODE, false)
        _performanceRefreshRate.value = sp.getInt(KEY_PERFORMANCE_REFRESH_RATE, 0)

        _localMusicFolderUri.value = sp.getString(KEY_LOCAL_MUSIC_FOLDER_URI, "") ?: ""
        _filterNonMusicAudio.value = sp.getBoolean(KEY_FILTER_NON_MUSIC_AUDIO, true)
        _localMusicSort.value = LocalMusicSort.fromString(sp.getString(KEY_LOCAL_MUSIC_SORT, null))
        _downloadedMusicSort.value = SongSort.fromString(sp.getString(KEY_DOWNLOADED_MUSIC_SORT, null))
        _librarySort.value = SongSort.fromString(sp.getString(KEY_LIBRARY_SORT, null))
        _showCacheFolder.value = sp.getBoolean(KEY_SHOW_CACHE_FOLDER, false)
        _showQueueHistory.value = sp.getBoolean(KEY_SHOW_QUEUE_HISTORY, true)

        _appLanguage.value = BitChordLanguage.fromCode(sp.getString(KEY_APP_LANGUAGE, null))
        _smartVersionAlignment.value = sp.getBoolean(KEY_SMART_VERSION_ALIGNMENT, true)
        _showNerdStats.value = sp.getBoolean(KEY_NERD_STATS, false)

        _spatialAudio.value = sp.getBoolean(KEY_SPATIAL_AUDIO, false)
        _equalizerEnabled.value = sp.getBoolean(KEY_EQ_ENABLED, false)
        _equalizerMode.value = EqualizerMode.fromString(sp.getString(KEY_EQ_MODE, null))
        _equalizerToneX.value = sp.getInt(KEY_EQ_TONE_X, 0).coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        _equalizerToneY.value = sp.getInt(KEY_EQ_TONE_Y, 0).coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        _equalizerFocused.value = sp.getBoolean(KEY_EQ_FOCUSED, false)
        _equalizerBalance.value = sp.getFloat(KEY_EQ_BALANCE, 0f).coerceIn(-1f, 1f)

        val bands = readEqualizerBands(sp)
        _equalizerBands.value = bands

        val rawPresetName = sp.getString(KEY_EQ_PRESET, null)
        val matchedPreset = EqualizerPreset.matching(bands)
        _equalizerPreset.value = if (rawPresetName != null) {
            runCatching { EqualizerPreset.valueOf(rawPresetName) }.getOrDefault(matchedPreset)
        } else {
            matchedPreset
        }

        _lyricsSources.value = readLyricsSources(sp)
        _lyricsSourceOrder.value = readLyricsSourceOrder(sp)
        _prioritizeSyllableSync.value = sp.getBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, false)

        _lyricsEffectsEnabled.value = sp.getBoolean(KEY_LYRICS_EFFECTS_ENABLED, true)
        _lyricsEffectStyle.value = LyricsEffectStyle.fromString(sp.getString(KEY_LYRICS_EFFECT_STYLE, null))
        _lyricsEffectIntensity.value = sp.getFloat(KEY_LYRICS_EFFECT_INTENSITY, 1.0f).coerceIn(0.2f, 2.0f)
        _lyricsBeatReaction.value = sp.getBoolean(KEY_LYRICS_BEAT_REACTION, true)
        _lyricsLowPowerMode.value = sp.getBoolean(KEY_LYRICS_LOW_POWER_MODE, false)
    }

    // ---- Setters: Downloads & Cache ----------------------------------------

    fun setDownloadQuality(quality: DownloadQuality) {
        _downloadQuality.value = quality
        prefs?.edit()?.putString(KEY_QUALITY_DOWNLOAD, quality.name)?.apply()
    }

    fun setWifiOnlyDownloads(value: Boolean) {
        _wifiOnlyDownloads.value = value
        prefs?.edit()?.putBoolean(KEY_WIFI_ONLY_DOWNLOADS, value)?.apply()
    }

    fun setExportDownloads(value: Boolean) {
        _exportDownloads.value = value
        prefs?.edit()?.putBoolean(KEY_EXPORT_DOWNLOADS, value)?.apply()
    }

    fun setAudioCacheLimitBytes(value: Long) {
        val clamped = clampCacheLimit(value)
        _audioCacheLimitBytes.value = clamped
        prefs?.edit()?.putLong(KEY_CACHE_LIMIT, clamped)?.apply()
    }

    fun setMaxConcurrentDownloads(value: Int) {
        val clamped = value.coerceIn(1, 5)
        _maxConcurrentDownloads.value = clamped
        prefs?.edit()?.putInt(KEY_MAX_CONCURRENT_DOWNLOADS, clamped)?.apply()
    }

    fun clampCacheLimit(value: Long): Long =
        if (value > MAX_CACHE_LIMIT_BYTES || value == UNLIMITED_CACHE_LIMIT_BYTES) UNLIMITED_CACHE_LIMIT_BYTES
        else value.coerceAtLeast(DEFAULT_CACHE_LIMIT_BYTES)

    // ---- Setters: Audio & Streaming Quality --------------------------------

    fun setAudioQualityWifi(quality: AudioQuality) {
        _audioQualityWifi.value = quality
        prefs?.edit()?.putString(KEY_QUALITY_WIFI, quality.name)?.apply()
    }

    fun setAudioQualityCellular(quality: AudioQuality) {
        _audioQualityCellular.value = quality
        prefs?.edit()?.putString(KEY_QUALITY_CELLULAR, quality.name)?.apply()
    }

    // ---- Setters: Playback -------------------------------------------------

    fun setAutoplay(value: Boolean) {
        _autoplay.value = value
        prefs?.edit()?.putBoolean(KEY_AUTOPLAY, value)?.apply()
    }

    fun setRepeatMode(mode: Int) {
        _repeatMode.value = mode
        prefs?.edit()?.putInt(KEY_REPEAT_MODE, mode)?.apply()
    }

    fun setShuffleEnabled(value: Boolean) {
        _shuffleEnabled.value = value
        prefs?.edit()?.putBoolean(KEY_SHUFFLE_ENABLED, value)?.apply()
    }

    fun setCrossfadeSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        _crossfadeSeconds.value = clamped
        prefs?.edit()?.putInt(KEY_CROSSFADE, clamped)?.apply()
    }

    fun setSmartFadeEnabled(value: Boolean) {
        _smartFadeEnabled.value = value
        prefs?.edit()?.putBoolean(KEY_SMART_FADE, value)?.apply()
    }

    fun setSkipSilence(value: Boolean) {
        _skipSilence.value = value
        prefs?.edit()?.putBoolean(KEY_SKIP_SILENCE, value)?.apply()
    }

    fun setOutputPcmMode(mode: OutputPcmMode) {
        _outputPcmMode.value = mode
        prefs?.edit()?.putString(KEY_OUTPUT_PCM_MODE, mode.name)?.apply()
    }

    fun setPreferUsbDac(value: Boolean) {
        _preferUsbDac.value = value
        prefs?.edit()?.putBoolean(KEY_PREFER_USB_DAC, value)?.apply()
    }

    fun setLoudnessNormalization(value: Boolean) {
        _loudnessNormalization.value = value
        prefs?.edit()?.putBoolean(KEY_LOUDNESS_NORMALIZATION, value)?.apply()
    }

    fun setLoudnessOffOnSpeaker(value: Boolean) {
        _loudnessOffOnSpeaker.value = value
        prefs?.edit()?.putBoolean(KEY_LOUDNESS_OFF_ON_SPEAKER, value)?.apply()
    }

    fun setPreferMusicOnly(value: Boolean) {
        _preferMusicOnly.value = value
        prefs?.edit()?.putBoolean(KEY_PREFER_MUSIC_ONLY, value)?.apply()
    }

    fun setSwipeToPlayNext(value: Boolean) {
        _swipeToPlayNext.value = value
        prefs?.edit()?.putBoolean(KEY_SWIPE_TO_PLAY_NEXT, value)?.apply()
    }

    fun setDontRepeatSuggestions(value: Boolean) {
        _dontRepeatSuggestions.value = value
        prefs?.edit()?.putBoolean(KEY_DONT_REPEAT_SUGGESTIONS, value)?.apply()
    }

    fun setStopOnTaskRemoved(value: Boolean) {
        _stopOnTaskRemoved.value = value
        prefs?.edit()?.putBoolean(KEY_STOP_ON_TASK_REMOVED, value)?.apply()
    }

    fun setHideVolumeBar(value: Boolean) {
        _hideVolumeBar.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE_VOLUME_BAR, value)?.apply()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 3.0f)
        _playbackSpeed.value = clamped
        prefs?.edit()?.putFloat(KEY_SPEED, clamped)?.apply()
    }

    // ---- Setters: Appearance -----------------------------------------------

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs?.edit()?.putString(KEY_THEME, mode.name)?.apply()
    }

    fun setLiquidGlass(value: Boolean) {
        _liquidGlass.value = value
        prefs?.edit()?.putBoolean(KEY_LIQUID_GLASS, value)?.apply()
    }

    fun setReduceAnimation(value: Boolean) {
        _reduceAnimation.value = value
        prefs?.edit()?.putBoolean(KEY_REDUCE_ANIMATION, value)?.apply()
    }

    fun setReduceDynamicBlur(value: Boolean) {
        _reduceDynamicBlur.value = value
        prefs?.edit()?.putBoolean(KEY_REDUCE_BLUR, value)?.apply()
    }

    fun setFullBleedArtwork(value: Boolean) {
        _fullBleedArtwork.value = value
        prefs?.edit()?.putBoolean(KEY_FULL_BLEED_ARTWORK, value)?.apply()
    }

    fun setLegacyMeshGradient(value: Boolean) {
        _legacyMeshGradient.value = value
        prefs?.edit()?.putBoolean(KEY_LEGACY_MESH_GRADIENT, value)?.apply()
    }

    fun setAnimatedCanvas(value: Boolean) {
        _animatedCanvas.value = value
        prefs?.edit()?.putBoolean(KEY_ANIMATED_CANVAS, value)?.apply()
    }

    fun setSyncedLyrics(value: Boolean) {
        _syncedLyrics.value = value
        prefs?.edit()?.putBoolean(KEY_SYNCED_LYRICS, value)?.apply()
    }

    fun setLyricsBlur(value: Boolean) {
        _lyricsBlur.value = value
        prefs?.edit()?.putBoolean(KEY_LYRICS_BLUR, value)?.apply()
    }

    fun setHideStatusBarInPlayer(value: Boolean) {
        _hideStatusBarInPlayer.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE_STATUS_BAR_IN_PLAYER, value)?.apply()
    }

    fun setHideSongStatus(value: Boolean) {
        _hideSongStatus.value = value
        prefs?.edit()?.putBoolean(KEY_HIDE_SONG_STATUS, value)?.apply()
    }

    fun setLocalMusicViewType(viewType: String) {
        _localMusicViewType.value = viewType
        prefs?.edit()?.putString(KEY_LOCAL_MUSIC_VIEW_TYPE, viewType)?.apply()
    }

    fun setDownloadedMusicViewType(viewType: String) {
        _downloadedMusicViewType.value = viewType
        prefs?.edit()?.putString(KEY_DOWNLOADED_MUSIC_VIEW_TYPE, viewType)?.apply()
    }

    // ---- Setters: Performance ----------------------------------------------

    fun setHighPerformanceMode(value: Boolean) {
        _highPerformanceMode.value = value
        prefs?.edit()?.putBoolean(KEY_HIGH_PERFORMANCE_MODE, value)?.apply()
    }

    fun setPerformanceRefreshRate(rate: Int) {
        _performanceRefreshRate.value = rate
        prefs?.edit()?.putInt(KEY_PERFORMANCE_REFRESH_RATE, rate)?.apply()
    }

    // ---- Setters: Library & Local Music ------------------------------------

    fun setLocalMusicFolderUri(uriString: String) {
        _localMusicFolderUri.value = uriString
        prefs?.edit()?.putString(KEY_LOCAL_MUSIC_FOLDER_URI, uriString)?.apply()
    }

    fun setFilterNonMusicAudio(value: Boolean) {
        _filterNonMusicAudio.value = value
        prefs?.edit()?.putBoolean(KEY_FILTER_NON_MUSIC_AUDIO, value)?.apply()
    }

    fun setLocalMusicSort(sort: LocalMusicSort) {
        _localMusicSort.value = sort
        prefs?.edit()?.putString(KEY_LOCAL_MUSIC_SORT, sort.name)?.apply()
    }

    fun setDownloadedMusicSort(sort: SongSort) {
        _downloadedMusicSort.value = sort
        prefs?.edit()?.putString(KEY_DOWNLOADED_MUSIC_SORT, sort.name)?.apply()
    }

    fun setLibrarySort(sort: SongSort) {
        _librarySort.value = sort
        prefs?.edit()?.putString(KEY_LIBRARY_SORT, sort.name)?.apply()
    }

    fun setShowCacheFolder(value: Boolean) {
        _showCacheFolder.value = value
        prefs?.edit()?.putBoolean(KEY_SHOW_CACHE_FOLDER, value)?.apply()
    }

    fun setShowQueueHistory(value: Boolean) {
        _showQueueHistory.value = value
        prefs?.edit()?.putBoolean(KEY_SHOW_QUEUE_HISTORY, value)?.apply()
    }

    // ---- Setters: Language -------------------------------------------------

    fun setAppLanguage(language: BitChordLanguage) {
        _appLanguage.value = language
        prefs?.edit()?.putString(KEY_APP_LANGUAGE, language.code)?.apply()
    }

    // ---- Setters: Advanced & Diagnostics -----------------------------------

    fun setSmartVersionAlignment(value: Boolean) {
        _smartVersionAlignment.value = value
        prefs?.edit()?.putBoolean(KEY_SMART_VERSION_ALIGNMENT, value)?.apply()
    }

    fun setShowNerdStats(value: Boolean) {
        _showNerdStats.value = value
        prefs?.edit()?.putBoolean(KEY_NERD_STATS, value)?.apply()
    }

    // ---- Setters: Equalizer & Spatial Audio --------------------------------

    fun setSpatialAudio(value: Boolean) {
        _spatialAudio.value = value
        prefs?.edit()?.putBoolean(KEY_SPATIAL_AUDIO, value)?.apply()
    }

    fun setEqualizerEnabled(value: Boolean) {
        _equalizerEnabled.value = value
        prefs?.edit()?.putBoolean(KEY_EQ_ENABLED, value)?.apply()
    }

    fun setEqualizerMode(value: EqualizerMode) {
        _equalizerMode.value = value
        prefs?.edit()?.putString(KEY_EQ_MODE, value.name)?.apply()
    }

    fun setEqualizerTone(x: Int, y: Int) {
        val clampedX = x.coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        val clampedY = y.coerceIn(-EqLayout.TONE_STEPS, EqLayout.TONE_STEPS)
        _equalizerToneX.value = clampedX
        _equalizerToneY.value = clampedY
        prefs?.edit()
            ?.putInt(KEY_EQ_TONE_X, clampedX)
            ?.putInt(KEY_EQ_TONE_Y, clampedY)
            ?.apply()
    }

    fun setEqualizerFocused(value: Boolean) {
        _equalizerFocused.value = value
        prefs?.edit()?.putBoolean(KEY_EQ_FOCUSED, value)?.apply()
    }

    fun setEqualizerBalance(value: Float) {
        val clamped = value.coerceIn(-1f, 1f)
        _equalizerBalance.value = clamped
        prefs?.edit()?.putFloat(KEY_EQ_BALANCE, clamped)?.apply()
    }

    fun setEqualizerBands(values: List<Float>) {
        val clamped = List(EqLayout.MANUAL_COUNT) { index ->
            values.getOrElse(index) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
        _equalizerBands.value = clamped
        val matched = EqualizerPreset.matching(clamped)
        _equalizerPreset.value = matched
        prefs?.edit()
            ?.putString(KEY_EQ_BANDS, clamped.joinToString(","))
            ?.putString(KEY_EQ_PRESET, matched.name)
            ?.apply()
    }

    fun setEqualizerPreset(preset: EqualizerPreset) {
        if (preset == EqualizerPreset.CUSTOM) return
        _equalizerPreset.value = preset
        setEqualizerBands(preset.bands)
    }

    // ---- Setters: Lyrics Sources & Priority ---------------------------------

    fun setLyricsSources(value: Set<LyricsSource>) {
        val valid = value.filter { it in LyricsSource.offered }.toSet()
        if (valid.isEmpty()) return
        _lyricsSources.value = valid
        prefs?.edit()
            ?.putString(KEY_LYRICS_SOURCES, valid.joinToString(",") { it.name })
            ?.putString(KEY_LYRICS_SOURCES_SEEN, LyricsSource.offered.joinToString(",") { it.name })
            ?.apply()
    }

    fun toggleLyricsSource(source: LyricsSource): Boolean {
        val current = _lyricsSources.value
        return if (source in current) {
            if (current.size <= 1) {
                false
            } else {
                setLyricsSources(current - source)
                true
            }
        } else {
            setLyricsSources(current + source)
            true
        }
    }

    fun setLyricsSourceOrder(value: List<LyricsSource>) {
        val validOrder = LyricsSource.ordered(value)
        _lyricsSourceOrder.value = validOrder
        prefs?.edit()?.putString(KEY_LYRICS_SOURCE_ORDER, validOrder.joinToString(",") { it.name })?.apply()
    }

    fun moveLyricsSourceUp(source: LyricsSource): Boolean {
        val currentList = _lyricsSourceOrder.value.toMutableList()
        val index = currentList.indexOf(source)
        if (index <= 0) return false
        val temp = currentList[index - 1]
        currentList[index - 1] = source
        currentList[index] = temp
        setLyricsSourceOrder(currentList)
        return true
    }

    fun moveLyricsSourceDown(source: LyricsSource): Boolean {
        val currentList = _lyricsSourceOrder.value.toMutableList()
        val index = currentList.indexOf(source)
        if (index < 0 || index >= currentList.size - 1) return false
        val temp = currentList[index + 1]
        currentList[index + 1] = source
        currentList[index] = temp
        setLyricsSourceOrder(currentList)
        return true
    }

    fun setPrioritizeSyllableSync(value: Boolean) {
        _prioritizeSyllableSync.value = value
        prefs?.edit()?.putBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, value)?.apply()
    }

    fun resetLyricsSourceSettings() {
        _lyricsSources.value = LyricsSource.offered.toSet()
        _lyricsSourceOrder.value = LyricsSource.offered
        _prioritizeSyllableSync.value = false
        prefs?.edit()
            ?.putString(KEY_LYRICS_SOURCES, LyricsSource.offered.joinToString(",") { it.name })
            ?.putString(KEY_LYRICS_SOURCES_SEEN, LyricsSource.offered.joinToString(",") { it.name })
            ?.putString(KEY_LYRICS_SOURCE_ORDER, LyricsSource.offered.joinToString(",") { it.name })
            ?.putBoolean(KEY_PRIORITIZE_SYLLABLE_SYNC, false)
            ?.apply()
    }

    // ---- Setters: Lyrics Visual Effects (Milestone 1) -----------------------

    fun setLyricsEffectsEnabled(value: Boolean) {
        _lyricsEffectsEnabled.value = value
        prefs?.edit()?.putBoolean(KEY_LYRICS_EFFECTS_ENABLED, value)?.apply()
    }

    fun setLyricsEffectStyle(style: LyricsEffectStyle) {
        _lyricsEffectStyle.value = style
        prefs?.edit()?.putString(KEY_LYRICS_EFFECT_STYLE, style.name)?.apply()
    }

    fun setLyricsEffectIntensity(value: Float) {
        val clamped = value.coerceIn(0.2f, 2.0f)
        _lyricsEffectIntensity.value = clamped
        prefs?.edit()?.putFloat(KEY_LYRICS_EFFECT_INTENSITY, clamped)?.apply()
    }

    fun setLyricsBeatReaction(value: Boolean) {
        _lyricsBeatReaction.value = value
        prefs?.edit()?.putBoolean(KEY_LYRICS_BEAT_REACTION, value)?.apply()
    }

    fun setLyricsLowPowerMode(value: Boolean) {
        _lyricsLowPowerMode.value = value
        prefs?.edit()?.putBoolean(KEY_LYRICS_LOW_POWER_MODE, value)?.apply()
    }

    // ---- Import, Export & Reset ---------------------------------------------

    private val SECRETS = setOf(
        "lastfm_session_key",
        "lastfm_api_key",
        "lastfm_secret",
        "listenbrainz_token",
        "spotify_spdc_token",
        "paxsenix_api_key",
    )

    private val DEVICE_LOCAL = setOf(
        "onboarding_complete",
        "last_version_code",
    )

    fun exportPrefs(): Map<String, Any?> {
        val sp = prefs ?: return emptyMap()
        return sp.all.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
    }

    fun importPrefs(values: Map<String, Any?>) {
        val sp = prefs ?: return
        val kept = sp.all.filterKeys { it in SECRETS || it in DEVICE_LOCAL }
        sp.edit().apply {
            clear()
            val incoming = values.filterKeys { it !in SECRETS && it !in DEVICE_LOCAL }
            (kept + incoming).forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> Unit
                }
            }
        }.apply()
        readAll(sp)
    }

    fun resetAllDefaults() {
        val sp = prefs ?: return
        sp.edit().apply {
            remove(KEY_QUALITY_DOWNLOAD)
            remove(KEY_WIFI_ONLY_DOWNLOADS)
            remove(KEY_EXPORT_DOWNLOADS)
            remove(KEY_CACHE_LIMIT)
            remove(KEY_MAX_CONCURRENT_DOWNLOADS)

            remove(KEY_QUALITY_WIFI)
            remove(KEY_QUALITY_CELLULAR)

            remove(KEY_AUTOPLAY)
            remove(KEY_REPEAT_MODE)
            remove(KEY_SHUFFLE_ENABLED)
            remove(KEY_CROSSFADE)
            remove(KEY_SMART_FADE)
            remove(KEY_SKIP_SILENCE)
            remove(KEY_OUTPUT_PCM_MODE)
            remove(KEY_PREFER_USB_DAC)
            remove(KEY_LOUDNESS_NORMALIZATION)
            remove(KEY_LOUDNESS_OFF_ON_SPEAKER)
            remove(KEY_PREFER_MUSIC_ONLY)
            remove(KEY_SWIPE_TO_PLAY_NEXT)
            remove(KEY_DONT_REPEAT_SUGGESTIONS)
            remove(KEY_STOP_ON_TASK_REMOVED)
            remove(KEY_HIDE_VOLUME_BAR)
            remove(KEY_SPEED)

            remove(KEY_THEME)
            remove(KEY_LIQUID_GLASS)
            remove(KEY_REDUCE_ANIMATION)
            remove(KEY_REDUCE_BLUR)
            remove(KEY_FULL_BLEED_ARTWORK)
            remove(KEY_LEGACY_MESH_GRADIENT)
            remove(KEY_ANIMATED_CANVAS)
            remove(KEY_SYNCED_LYRICS)
            remove(KEY_LYRICS_BLUR)
            remove(KEY_HIDE_STATUS_BAR_IN_PLAYER)
            remove(KEY_HIDE_SONG_STATUS)
            remove(KEY_LOCAL_MUSIC_VIEW_TYPE)
            remove(KEY_DOWNLOADED_MUSIC_VIEW_TYPE)

            remove(KEY_HIGH_PERFORMANCE_MODE)
            remove(KEY_PERFORMANCE_REFRESH_RATE)

            remove(KEY_LOCAL_MUSIC_FOLDER_URI)
            remove(KEY_FILTER_NON_MUSIC_AUDIO)
            remove(KEY_LOCAL_MUSIC_SORT)
            remove(KEY_DOWNLOADED_MUSIC_SORT)
            remove(KEY_LIBRARY_SORT)
            remove(KEY_SHOW_CACHE_FOLDER)
            remove(KEY_SHOW_QUEUE_HISTORY)

            remove(KEY_APP_LANGUAGE)
            remove(KEY_SMART_VERSION_ALIGNMENT)
            remove(KEY_NERD_STATS)

            remove(KEY_SPATIAL_AUDIO)
            remove(KEY_EQ_ENABLED)
            remove(KEY_EQ_MODE)
            remove(KEY_EQ_TONE_X)
            remove(KEY_EQ_TONE_Y)
            remove(KEY_EQ_FOCUSED)
            remove(KEY_EQ_BALANCE)
            remove(KEY_EQ_BANDS)
            remove(KEY_EQ_PRESET)

            remove(KEY_LYRICS_SOURCES)
            remove(KEY_LYRICS_SOURCES_SEEN)
            remove(KEY_LYRICS_SOURCE_ORDER)
            remove(KEY_PRIORITIZE_SYLLABLE_SYNC)

            remove(KEY_LYRICS_EFFECTS_ENABLED)
            remove(KEY_LYRICS_EFFECT_STYLE)
            remove(KEY_LYRICS_EFFECT_INTENSITY)
            remove(KEY_LYRICS_BEAT_REACTION)
            remove(KEY_LYRICS_LOW_POWER_MODE)
        }.apply()
        readAll(sp)
    }

    private fun readLyricsSources(sp: SharedPreferences): Set<LyricsSource> {
        val stored = sp.getString(KEY_LYRICS_SOURCES, null)
            ?: return LyricsSource.offered.toSet()
        val chosen = stored.split(",").toSources()
        val seen = sp.getString(KEY_LYRICS_SOURCES_SEEN, null)
            ?.split(",")?.toSources()
            ?: LEGACY_SOURCES
        val merged = (chosen + LyricsSource.entries.filter { it !in seen }).filterNot { it.hidden }.toSet()
        return if (merged.isNotEmpty()) merged else LyricsSource.offered.toSet()
    }

    private fun List<String>.toSources(): Set<LyricsSource> =
        mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }.toSet()

    private val LEGACY_SOURCES = setOf(
        LyricsSource.LYRICS_PLUS,
        LyricsSource.PAXSENIX,
        LyricsSource.BETTER_LYRICS,
        LyricsSource.SIMP_MUSIC,
        LyricsSource.KUGOU,
        LyricsSource.LRCLIB,
        LyricsSource.MUSIXMATCH,
        LyricsSource.GENIUS,
    )

    private fun readLyricsSourceOrder(sp: SharedPreferences): List<LyricsSource> {
        val stored = sp.getString(KEY_LYRICS_SOURCE_ORDER, null)
            ?: return LyricsSource.offered
        val saved = stored.split(",")
            .mapNotNull { name -> LyricsSource.entries.firstOrNull { it.name == name } }
        val ordered = LyricsSource.ordered(saved)
        return if (ordered.isNotEmpty()) ordered else LyricsSource.offered
    }

    private fun readEqualizerBands(sp: SharedPreferences): List<Float> {
        val stored = sp.getString(KEY_EQ_BANDS, null)
            ?.split(",")
            ?.mapNotNull { it.trim().toFloatOrNull() }
            .orEmpty()
        return List(EqLayout.MANUAL_COUNT) { index ->
            stored.getOrElse(index) { 0f }.coerceIn(-EqLayout.MANUAL_RANGE_DB, EqLayout.MANUAL_RANGE_DB)
        }
    }

    private fun watchConnection(context: Context) {
        val manager = runCatching {
            context.getSystemService(ConnectivityManager::class.java)
        }.getOrNull() ?: return
        connectivityManager = manager

        val refresh = {
            _meteredConnection.value = runCatching {
                if (manager.activeNetwork == null) null else manager.isActiveNetworkMetered
            }.getOrNull()
        }
        refresh()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refresh()
                lyricsOfflineModeOverride = false
                Downloads.retryPendingLyrics()
                Downloads.retryPendingArtwork()
            }
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        }
        networkCallback = callback
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }
    }

    /**
     * Test helper to simulate network reconnection.
     */
    fun simulateNetworkAvailable(network: Network? = null): List<Job> {
        _meteredConnection.value = false
        lyricsOfflineModeOverride = false
        val lyricsJobs = Downloads.retryPendingLyrics()
        val artJobs = Downloads.retryPendingArtwork()
        return lyricsJobs + artJobs
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        val cm = connectivityManager ?: return
        runCatching {
            cm.unregisterNetworkCallback(cb)
        }
        networkCallback = null
        connectivityManager = null
    }
}

package com.divinedeck.launcher.feature.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.feature.settings.domain.SettingsRepository
import com.divinedeck.launcher.feature.settings.model.OperatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación de persistencia de preferencias y temas de DIVINE DECK.
 * Cuando recibe el Context de Android, almacena y restaura automáticamente
 * la configuración del usuario en almacenamiento local persistente.
 */
class SettingsRepositoryImpl(
    context: Context? = null
) : SettingsRepository {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _operatingMode = MutableStateFlow(
        prefs?.getString(KEY_OPERATING_MODE, null)?.let { saved ->
            OperatingMode.entries.find { it.name == saved }
        } ?: OperatingMode.AUTOMOTIVE_COCKPIT
    )
    override val operatingMode: StateFlow<OperatingMode> = _operatingMode.asStateFlow()

    private val _speedLimitWarningKmh = MutableStateFlow(
        prefs?.getInt(KEY_SPEED_LIMIT, 110) ?: 110
    )
    override val speedLimitWarningKmh: StateFlow<Int> = _speedLimitWarningKmh.asStateFlow()

    private val _isAutoNightModeEnabled = MutableStateFlow(
        prefs?.getBoolean(KEY_AUTO_NIGHT_MODE, true) ?: true
    )
    override val isAutoNightModeEnabled: StateFlow<Boolean> = _isAutoNightModeEnabled.asStateFlow()

    private val _selectedTheme = MutableStateFlow(
        ThemePreset.fromId(prefs?.getString(KEY_THEME_PRESET, ThemePreset.DEFAULT.id))
    )
    override val selectedTheme: StateFlow<ThemePreset> = _selectedTheme.asStateFlow()

    private val _customAccentHex = MutableStateFlow(
        prefs?.getLong(KEY_CUSTOM_ACCENT_HEX, DEFAULT_CUSTOM_ACCENT_HEX) ?: DEFAULT_CUSTOM_ACCENT_HEX
    )
    override val customAccentHex: StateFlow<Long> = _customAccentHex.asStateFlow()

    private val _cockpitLayout = MutableStateFlow(
        CockpitLayout.fromId(prefs?.getString(KEY_COCKPIT_LAYOUT, CockpitLayout.DEFAULT.id))
    )
    override val cockpitLayout: StateFlow<CockpitLayout> = _cockpitLayout.asStateFlow()

    override fun setOperatingMode(mode: OperatingMode) {
        _operatingMode.value = mode
        prefs?.edit()?.putString(KEY_OPERATING_MODE, mode.name)?.apply()
    }

    override fun setSpeedLimitWarning(kmh: Int) {
        _speedLimitWarningKmh.value = kmh
        prefs?.edit()?.putInt(KEY_SPEED_LIMIT, kmh)?.apply()
    }

    override fun setAutoNightMode(enabled: Boolean) {
        _isAutoNightModeEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_NIGHT_MODE, enabled)?.apply()
    }

    override fun setSelectedTheme(theme: ThemePreset) {
        _selectedTheme.value = theme
        prefs?.edit()?.putString(KEY_THEME_PRESET, theme.id)?.apply()
    }

    override fun setCustomAccentHex(hex: Long) {
        _customAccentHex.value = hex
        prefs?.edit()?.putLong(KEY_CUSTOM_ACCENT_HEX, hex)?.apply()
    }

    override fun setCockpitLayout(layout: CockpitLayout) {
        _cockpitLayout.value = layout
        prefs?.edit()?.putString(KEY_COCKPIT_LAYOUT, layout.id)?.apply()
    }

    companion object {
        const val PREFS_NAME = "divine_deck_settings_prefs"
        const val KEY_OPERATING_MODE = "key_operating_mode"
        const val KEY_SPEED_LIMIT = "key_speed_limit_kmh"
        const val KEY_AUTO_NIGHT_MODE = "key_auto_night_mode"
        const val KEY_THEME_PRESET = "key_theme_preset"
        const val KEY_CUSTOM_ACCENT_HEX = "key_custom_accent_hex"
        const val KEY_COCKPIT_LAYOUT = "key_cockpit_layout"
        const val DEFAULT_CUSTOM_ACCENT_HEX = 0xFFFFB547L
    }
}

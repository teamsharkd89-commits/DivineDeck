package com.divinedeck.launcher.feature.settings.domain

import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.feature.settings.model.OperatingMode
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val operatingMode: StateFlow<OperatingMode>
    val speedLimitWarningKmh: StateFlow<Int>
    val isAutoNightModeEnabled: StateFlow<Boolean>
    val selectedTheme: StateFlow<ThemePreset>
    val customAccentHex: StateFlow<Long>
    val cockpitLayout: StateFlow<CockpitLayout>

    fun setOperatingMode(mode: OperatingMode)
    fun setSpeedLimitWarning(kmh: Int)
    fun setAutoNightMode(enabled: Boolean)
    fun setSelectedTheme(theme: ThemePreset)
    fun setCustomAccentHex(hex: Long)
    fun setCockpitLayout(layout: CockpitLayout)
}

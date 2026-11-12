package com.divinedeck.launcher.feature.settings

import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.feature.settings.data.SettingsRepositoryImpl
import com.divinedeck.launcher.feature.settings.model.OperatingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {

    @Test
    fun `initial operating mode is automotive cockpit and initial theme is minimalista`() {
        val repo = SettingsRepositoryImpl()
        assertEquals(OperatingMode.AUTOMOTIVE_COCKPIT, repo.operatingMode.value)
        assertEquals(110, repo.speedLimitWarningKmh.value)
        assertTrue(repo.isAutoNightModeEnabled.value)
        assertEquals(ThemePreset.MINIMALISTA, repo.selectedTheme.value)
        assertEquals(SettingsRepositoryImpl.DEFAULT_CUSTOM_ACCENT_HEX, repo.customAccentHex.value)
    }

    @Test
    fun `switching operating mode updates state flow`() {
        val repo = SettingsRepositoryImpl()
        repo.setOperatingMode(OperatingMode.PERSONAL_TABLET)
        assertEquals(OperatingMode.PERSONAL_TABLET, repo.operatingMode.value)
    }

    @Test
    fun `setting speed limit warning updates threshold`() {
        val repo = SettingsRepositoryImpl()
        repo.setSpeedLimitWarning(120)
        assertEquals(120, repo.speedLimitWarningKmh.value)
    }

    @Test
    fun `selecting themes updates selectedTheme state flow across all presets`() {
        val repo = SettingsRepositoryImpl()
        ThemePreset.entries.forEach { preset ->
            repo.setSelectedTheme(preset)
            assertEquals(preset, repo.selectedTheme.value)
        }
    }

    @Test
    fun `updating custom accent hex stores selected color value`() {
        val repo = SettingsRepositoryImpl()
        repo.setCustomAccentHex(0xFF22D3EE)
        assertEquals(0xFF22D3EE, repo.customAccentHex.value)
    }
}

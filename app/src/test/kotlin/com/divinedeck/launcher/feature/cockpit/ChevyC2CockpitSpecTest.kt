package com.divinedeck.launcher.feature.cockpit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.divinedeck.launcher.core.designsystem.theme.AlertRed
import com.divinedeck.launcher.core.designsystem.theme.ChampagneGold
import com.divinedeck.launcher.core.designsystem.theme.ElevatedSurface
import com.divinedeck.launcher.core.designsystem.theme.Graphite
import com.divinedeck.launcher.core.designsystem.theme.MidnightNavy
import com.divinedeck.launcher.core.designsystem.theme.Obsidian
import com.divinedeck.launcher.core.designsystem.theme.SecondaryGray
import com.divinedeck.launcher.core.designsystem.theme.SoftWhite
import com.divinedeck.launcher.core.designsystem.theme.StatusGreen
import com.divinedeck.launcher.core.designsystem.theme.SurfaceBorder
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.core.designsystem.theme.WarmAmber
import com.divinedeck.launcher.core.designsystem.theme.buildPaletteForTheme
import com.divinedeck.launcher.core.designsystem.theme.ensureReadableAccent
import com.divinedeck.launcher.feature.vehicle.domain.ChevyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de especificaciones de ingeniería y sistema de temas para la Fase 02.1.
 */
class ChevyC2CockpitSpecTest {

    @Test
    fun `chevy c2 2009 engineering specs match physical vehicle`() {
        val profile = ChevyProfile()
        assertEquals("Chevrolet", profile.make)
        assertEquals("Chevy C2", profile.model)
        assertEquals(2009, profile.year)
        assertEquals("1.6L MPFI SOHC 8V", profile.engine)
        assertEquals(100, profile.horsepowerHp)
        assertEquals(102, profile.torqueLbFt)
        assertEquals(46.0f, profile.fuelCapacityLiters, 0.01f)
    }

    @Test
    fun `official base color palette is strictly defined`() {
        assertNotNull(Obsidian)
        assertNotNull(MidnightNavy)
        assertNotNull(Graphite)
        assertNotNull(ElevatedSurface)
        assertNotNull(SurfaceBorder)
        assertNotNull(WarmAmber)
        assertNotNull(ChampagneGold)
        assertNotNull(SoftWhite)
        assertNotNull(SecondaryGray)
        assertNotNull(StatusGreen)
        assertNotNull(AlertRed)

        assertEquals(Color(0xFF080B12), Obsidian)
        assertEquals(Color(0xFF101827), MidnightNavy)
        assertEquals(Color(0xFF171D27), Graphite)
        assertEquals(Color(0xFF202A38), ElevatedSurface)
        assertEquals(Color(0xFF344052), SurfaceBorder)
        assertEquals(Color(0xFFFFB547), WarmAmber)
        assertEquals(Color(0xFFE6B86A), ChampagneGold)
        assertEquals(Color(0xFFF4F5F7), SoftWhite)
        assertEquals(Color(0xFF9AA4B2), SecondaryGray)
        assertEquals(Color(0xFF76C893), StatusGreen)
        assertEquals(Color(0xFFE45B5B), AlertRed)
    }

    @Test
    fun `all five theme presets build coherent high contrast palettes`() {
        val obsidian = buildPaletteForTheme(ThemePreset.OBSIDIAN)
        assertEquals(Color(0xFFFFB547), obsidian.accent)

        val arctic = buildPaletteForTheme(ThemePreset.ARCTIC)
        assertEquals(Color(0xFF22D3EE), arctic.accent)

        val monochrome = buildPaletteForTheme(ThemePreset.MONOCHROME)
        assertEquals(Color(0xFFF4F5F7), monochrome.accent)

        val bronze = buildPaletteForTheme(ThemePreset.BRONZE)
        assertEquals(Color(0xFFD98A4E), bronze.accent)

        val custom = buildPaletteForTheme(ThemePreset.CUSTOM, 0xFF34D399)
        assertEquals(Color(0xFF34D399), custom.accent)
    }

    @Test
    fun `custom accent contrast protection prevents illegible dark colors`() {
        val tooDarkColor = Color(0xFF050505)
        val safeAccent = ensureReadableAccent(tooDarkColor)
        assertTrue(safeAccent.luminance() >= 0.22f)
    }
}

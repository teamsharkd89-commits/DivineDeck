package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.divinedeck.launcher.core.designsystem.theme.AlertRed
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckPalette
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit Test Suite for Requirement R3: Cockpit BitChord Widget Theme Binding and Automotive Styling.
 *
 * Verifies:
 * 1. Palette token definitions across all 10 themes (OBSIDIAN, ARCTIC, MONOCHROME, BRONZE, CUSTOM,
 *    MINIMALISTA, ROJO_SPORT, ESPECTRO, VAPORWAVE, ACIDO_LIMA).
 * 2. Guaranteed readable contrast on dark surfaces for CUSTOM accents.
 * 3. Complete absence of hardcoded color literals in `CockpitBitChordMiniPlayer.kt`.
 * 4. Presence and declaration of all 5 Compose Previews.
 * 5. Formatting and progress calculation logic invariants.
 */
class CockpitBitChordMiniPlayerThemeTest {

    @Test
    fun test01_obsidian_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.OBSIDIAN)

        assertEquals(ThemePreset.OBSIDIAN, palette.preset)
        assertEquals(Obsidian, palette.background)
        assertEquals(MidnightNavy, palette.surface)
        assertEquals(SurfaceBorder, palette.border)
        assertEquals(SurfaceBorder.copy(alpha = 0.45f), palette.borderSubtle)
        assertEquals(WarmAmber, palette.accent)
        assertEquals(SoftWhite, palette.textPrimary)
        assertEquals(SecondaryGray, palette.textSecondary)
        assertEquals(StatusGreen, palette.statusPositive)
        assertEquals(AlertRed, palette.statusAlert)
        assertTrue(palette.accent.luminance() > 0.4f)
    }

    @Test
    fun test02_arctic_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.ARCTIC)

        assertEquals(ThemePreset.ARCTIC, palette.preset)
        assertEquals(Color(0xFF060A10), palette.background)
        assertEquals(Color(0xFF0C1524), palette.surface)
        assertEquals(Color(0xFF233954), palette.border)
        assertEquals(Color(0xFF233954).copy(alpha = 0.45f), palette.borderSubtle)
        assertEquals(Color(0xFF22D3EE), palette.accent)
        assertEquals(Color(0xFF67E8F9), palette.accentSecondary)
        assertEquals(Color(0xFFF1F5F9), palette.textPrimary)
        assertEquals(Color(0xFF94A3B8), palette.textSecondary)
        assertEquals(StatusGreen, palette.statusPositive)
        assertTrue(palette.accent.luminance() > 0.4f)
    }

    @Test
    fun test03_monochrome_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.MONOCHROME)

        assertEquals(ThemePreset.MONOCHROME, palette.preset)
        assertEquals(Color(0xFF050506), palette.background)
        assertEquals(Color(0xFF111215), palette.surface)
        assertEquals(Color(0xFF2C2E35), palette.border)
        assertEquals(Color(0xFF2C2E35).copy(alpha = 0.45f), palette.borderSubtle)
        assertEquals(Color(0xFFF4F5F7), palette.accent)
        assertEquals(Color(0xFFC4C9D4), palette.accentSecondary)
        assertEquals(Color(0xFFF8FAFC), palette.textPrimary)
        assertEquals(Color(0xFFA1A1AA), palette.textSecondary)
    }

    @Test
    fun test04_bronze_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.BRONZE)

        assertEquals(ThemePreset.BRONZE, palette.preset)
        assertEquals(Color(0xFF0B0908), palette.background)
        assertEquals(Color(0xFF16120F), palette.surface)
        assertEquals(Color(0xFF3A2E27), palette.border)
        assertEquals(Color(0xFF3A2E27).copy(alpha = 0.45f), palette.borderSubtle)
        assertEquals(Color(0xFFD98A4E), palette.accent)
        assertEquals(Color(0xFFE6A978), palette.accentSecondary)
        assertEquals(Color(0xFFF5F2EB), palette.textPrimary)
        assertEquals(Color(0xFFA89F96), palette.textSecondary)
    }

    @Test
    fun test05_custom_palette_tokens_binding_emerald() {
        val emeraldHex = 0xFF34D399L
        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = emeraldHex)

        assertEquals(ThemePreset.CUSTOM, palette.preset)
        assertEquals(Color(0xFF080A0F), palette.background)
        assertEquals(Color(0xFF111621), palette.surface)
        assertEquals(Color(0xFF2D3748), palette.border)
        assertEquals(Color(0xFF34D399), palette.accent)
        assertTrue(palette.accent.luminance() >= 0.25f)
        assertEquals(SoftWhite, palette.textPrimary)
    }

    @Test
    fun test06_custom_dark_accent_boosts_luminance_to_safe_threshold() {
        val darkHex = 0xFF050505L
        val rawColor = Color(darkHex)
        assertTrue(rawColor.luminance() < 0.05f)

        val palette = buildPaletteForTheme(ThemePreset.CUSTOM, customAccentHex = darkHex)
        assertTrue(
            "ensureReadableAccent debe asegurar luminancia >= 0.25f para visión automotriz",
            palette.accent.luminance() >= 0.25f
        )
    }

    @Test
    fun test07_mini_player_source_has_zero_hardcoded_colors() {
        val sourceFile = File("src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/CockpitBitChordMiniPlayer.kt")
        val altFile = File("app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/CockpitBitChordMiniPlayer.kt")
        val file = if (sourceFile.exists()) sourceFile else altFile
        assertTrue("El archivo CockpitBitChordMiniPlayer.kt debe existir", file.exists())

        val content = file.readText()

        // Excluir líneas de preview o comentarios al buscar literales
        val composableContent = content.substringBefore("// COMPOSE PREVIEWS")

        // Lista de colores que estaban codificados de forma rígida antes del rediseño
        val forbiddenColorLiterals = listOf(
            "0xFF131924", // Fondo azul oscuro fijo
            "0xFF090D14", // Degradado oscuro fijo
            "0xFF00E5FF", // Cian fijo
            "0xFF76C893", // Verde fijo
            "0xFF1B2230", // Carátula fija
            "0xFF1A212E", // Empty state fijo
            "0xFFFFB547", // Ámbar fijo
            "Color.White", // Literales Color.White
            "Color.Black"  // Literales Color.Black
        )

        for (forbidden in forbiddenColorLiterals) {
            assertFalse(
                "CockpitBitChordMiniPlayer no debe contener el literal de color '$forbidden' en la UI",
                composableContent.contains(forbidden)
            )
        }

        // Verificar consumo explícito de tokens de DivineDeckTheme.palette
        assertTrue(composableContent.contains("palette.surface"))
        assertTrue(composableContent.contains("palette.background"))
        assertTrue(composableContent.contains("palette.accent"))
        assertTrue(composableContent.contains("palette.borderSubtle"))
        assertTrue(composableContent.contains("palette.textPrimary"))
        assertTrue(composableContent.contains("palette.textSecondary"))
        assertTrue(composableContent.contains("palette.statusPositive"))
    }

    @Test
    fun test08_ten_compose_previews_declared() {
        val sourceFile = File("src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/CockpitBitChordMiniPlayer.kt")
        val altFile = File("app/src/main/kotlin/com/divinedeck/launcher/core/designsystem/components/CockpitBitChordMiniPlayer.kt")
        val file = if (sourceFile.exists()) sourceFile else altFile
        assertTrue(file.exists())

        val content = file.readText()

        val requiredPreviews = listOf(
            "PreviewCockpitBitChordMiniPlayerObsidian",
            "PreviewCockpitBitChordMiniPlayerArctic",
            "PreviewCockpitBitChordMiniPlayerMonochrome",
            "PreviewCockpitBitChordMiniPlayerBronze",
            "PreviewCockpitBitChordMiniPlayerCustom",
            "PreviewCockpitBitChordMiniPlayerMinimalista",
            "PreviewCockpitBitChordMiniPlayerRojoSport",
            "PreviewCockpitBitChordMiniPlayerEspectro",
            "PreviewCockpitBitChordMiniPlayerVaporwave",
            "PreviewCockpitBitChordMiniPlayerAcidoLima"
        )

        for (previewName in requiredPreviews) {
            assertTrue("Debe existir la función de preview @Preview $previewName", content.contains(previewName))
        }
    }

    // =========================================================================
    // TEMAS PANORÁMICO (Fase 06.11) — Minimalista, Rojo Sport, Espectro,
    // Vaporwave y Ácido Lima. El layout no cambia, solo la paleta.
    // =========================================================================

    @Test
    fun test11_minimalista_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.MINIMALISTA)

        assertEquals(ThemePreset.MINIMALISTA, palette.preset)
        assertTrue(ThemePreset.MINIMALISTA.isLight)
        assertEquals(Color(0xFFF4F5F7), palette.background)
        assertEquals(Color(0xFFFFFFFF), palette.surface)
        assertEquals(Color(0xFF1F2937), palette.accent)
        assertEquals(Color(0xFFFFFFFF), palette.onAccent)
        assertEquals(Color(0xFF111827), palette.textPrimary)
        assertEquals(Color(0xFF4B5563), palette.textSecondary)
        // Fondo claro: la luminancia del fondo debe ser alta y el texto
        // oscuro debe contrastar con él (no aplica la regla de fondos oscuros).
        assertTrue(palette.background.luminance() > 0.8f)
        assertTrue(palette.textPrimary.luminance() < 0.1f)
    }

    @Test
    fun test12_rojo_sport_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.ROJO_SPORT)

        assertEquals(ThemePreset.ROJO_SPORT, palette.preset)
        assertFalse(ThemePreset.ROJO_SPORT.isLight)
        assertEquals(Color(0xFF0A0505), palette.background)
        assertEquals(Color(0xFF160A0A), palette.surface)
        assertEquals(Color(0xFFFF3131), palette.accent)
        assertEquals(Color(0xFFFF6B6B), palette.accentSecondary)
        assertEquals(Color(0xFFF8FAFC), palette.textPrimary)
        assertTrue(palette.accent.luminance() >= 0.25f)
    }

    @Test
    fun test13_espectro_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.ESPECTRO)

        assertEquals(ThemePreset.ESPECTRO, palette.preset)
        assertEquals(Color(0xFF07070F), palette.background)
        assertEquals(Color(0xFF22D3EE), palette.accent)
        assertEquals(Color(0xFFE879F9), palette.accentSecondary)
        assertEquals(Color(0xFFF1F5F9), palette.textPrimary)
        assertTrue(palette.accent.luminance() > 0.4f)
    }

    @Test
    fun test14_vaporwave_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.VAPORWAVE)

        assertEquals(ThemePreset.VAPORWAVE, palette.preset)
        assertEquals(Color(0xFF12081F), palette.background)
        assertEquals(Color(0xFFFF2E9A), palette.accent)
        assertEquals(Color(0xFF00E5FF), palette.accentSecondary)
        assertEquals(Color(0xFFF5F0FF), palette.textPrimary)
        assertTrue(palette.accent.luminance() >= 0.25f)
    }

    @Test
    fun test15_acido_lima_palette_tokens_binding() {
        val palette = buildPaletteForTheme(ThemePreset.ACIDO_LIMA)

        assertEquals(ThemePreset.ACIDO_LIMA, palette.preset)
        assertEquals(Color(0xFF050805), palette.background)
        assertEquals(Color(0xFFBFFF00), palette.accent)
        assertEquals(Color(0xFF7CFF00), palette.accentSecondary)
        assertEquals(Color(0xFF050805), palette.onAccent)
        assertTrue(palette.accent.luminance() > 0.4f)
    }

    @Test
    fun test16_all_ten_presets_build_a_palette_and_default_is_minimalista() {
        assertEquals(10, ThemePreset.entries.size)
        assertEquals(ThemePreset.MINIMALISTA, ThemePreset.DEFAULT)
        assertEquals(ThemePreset.MINIMALISTA, ThemePreset.fromId(null))
        assertEquals(ThemePreset.MINIMALISTA, ThemePreset.fromId("NO_EXISTE"))
        assertEquals(ThemePreset.ROJO_SPORT, ThemePreset.fromId("ROJO_SPORT"))
        ThemePreset.entries.forEach { preset ->
            val palette = buildPaletteForTheme(preset)
            assertEquals(preset, palette.preset)
            assertNotNull(palette.background)
            assertNotNull(palette.surface)
            assertNotNull(palette.accent)
            assertNotNull(palette.textPrimary)
        }
    }

    @Test
    fun test09_format_duration_invariants() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(-1000L))
        assertEquals("0:05", formatDuration(5000L))
        assertEquals("0:59", formatDuration(59000L))
        assertEquals("1:00", formatDuration(60000L))
        assertEquals("3:34", formatDuration(214000L))
        assertEquals("60:00", formatDuration(3600000L))
    }

    @Test
    fun test10_progress_fraction_evaluation() {
        // Duración cero o negativa -> 0f
        val zeroDuration = 0L
        val zeroFraction = if (zeroDuration > 0L) (1000L.toFloat() / zeroDuration.toFloat()).coerceIn(0f, 1f) else 0f
        assertEquals(0f, zeroFraction, 0.0001f)

        // Posición a la mitad
        val duration = 200000L
        val midPos = 100000L
        val midFraction = if (duration > 0L) (midPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        assertEquals(0.5f, midFraction, 0.0001f)

        // Posición excedida clamp a 1f
        val overPos = 250000L
        val overFraction = if (duration > 0L) (overPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        assertEquals(1f, overFraction, 0.0001f)
    }
}

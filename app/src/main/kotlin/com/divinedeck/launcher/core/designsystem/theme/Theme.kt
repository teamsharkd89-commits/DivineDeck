package com.divinedeck.launcher.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Paleta centralizada reactiva de DIVINE DECK (Fase 02.1).
 * Ningún componente debe definir colores fijos directamente; todos consumen esta paleta.
 */
@Immutable
data class DivineDeckPalette(
    val preset: ThemePreset,
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val border: Color,
    val borderSubtle: Color,
    val accent: Color,
    val accentSecondary: Color,
    val accentGlow: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusPositive: Color,
    val statusAlert: Color
)

fun buildPaletteForTheme(
    preset: ThemePreset,
    customAccentHex: Long = 0xFFFFB547
): DivineDeckPalette {
    return when (preset) {
        ThemePreset.OBSIDIAN -> DivineDeckPalette(
            preset = ThemePreset.OBSIDIAN,
            background = Obsidian,                   // #080B12 (Fondo grafito profundo)
            surface = MidnightNavy,                  // #101827 (Azul marino oscuro)
            elevatedSurface = ElevatedSurface,       // #202A38
            border = SurfaceBorder,                  // #344052
            borderSubtle = SurfaceBorder.copy(alpha = 0.45f),
            accent = WarmAmber,                      // #FFB547 (Oro cálido)
            accentSecondary = ChampagneGold,         // #E6B86A
            accentGlow = WarmAmber.copy(alpha = 0.16f),
            onAccent = Color(0xFF080B12),
            textPrimary = SoftWhite,
            textSecondary = SecondaryGray,
            textMuted = TextMutedDark,
            statusPositive = StatusGreen,
            statusAlert = AlertRed
        )

        ThemePreset.ARCTIC -> {
            val cyanAccent = Color(0xFF22D3EE)
            val cyanSecondary = Color(0xFF67E8F9)
            val deepBlueBorder = Color(0xFF233954)
            DivineDeckPalette(
                preset = ThemePreset.ARCTIC,
                background = Color(0xFF060A10),      // Fondo oscuro
                surface = Color(0xFF0C1524),         // Superficie azul profundo
                elevatedSurface = Color(0xFF142238), // Azul profundo elevado
                border = deepBlueBorder,
                borderSubtle = deepBlueBorder.copy(alpha = 0.45f),
                accent = cyanAccent,                 // Acento cian
                accentSecondary = cyanSecondary,
                accentGlow = cyanAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFF041016),
                textPrimary = Color(0xFFF1F5F9),
                textSecondary = Color(0xFF94A3B8),
                textMuted = Color(0xFF52637A),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        ThemePreset.MONOCHROME -> {
            val whiteAccent = Color(0xFFF4F5F7)
            val silverSecondary = Color(0xFFC4C9D4)
            val monoBorder = Color(0xFF2C2E35)
            DivineDeckPalette(
                preset = ThemePreset.MONOCHROME,
                background = Color(0xFF050506),      // Fondo negro
                surface = Color(0xFF111215),         // Superficie gris oscuro
                elevatedSurface = Color(0xFF1B1C21), // Gris elevado
                border = monoBorder,
                borderSubtle = monoBorder.copy(alpha = 0.45f),
                accent = whiteAccent,                // Acento blanco
                accentSecondary = silverSecondary,
                accentGlow = whiteAccent.copy(alpha = 0.12f),
                onAccent = Color(0xFF050506),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFA1A1AA),
                textMuted = Color(0xFF52525B),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        ThemePreset.BRONZE -> {
            val bronzeAccent = Color(0xFFD98A4E)
            val bronzeSecondary = Color(0xFFE6A978)
            val bronzeBorder = Color(0xFF3A2E27)
            DivineDeckPalette(
                preset = ThemePreset.BRONZE,
                background = Color(0xFF0B0908),      // Fondo negro cálido
                surface = Color(0xFF16120F),         // Superficie marrón grafito
                elevatedSurface = Color(0xFF221C18), // Marrón grafito elevado
                border = bronzeBorder,
                borderSubtle = bronzeBorder.copy(alpha = 0.45f),
                accent = bronzeAccent,               // Acento bronce
                accentSecondary = bronzeSecondary,
                accentGlow = bronzeAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFF0B0908),
                textPrimary = Color(0xFFF5F2EB),
                textSecondary = Color(0xFFA89F96),
                textMuted = Color(0xFF665E57),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        // =====================================================================
        // TEMAS PANORÁMICO (Fase 06.11) — solo paleta de colores, mismo layout.
        // =====================================================================
        ThemePreset.MINIMALISTA -> {
            // Tema claro: el acento es oscuro (legible sobre fondo claro) y
            // onAccent es blanco para los botones con fondo de acento.
            // Nota: la regla de luminancia >= 0.25 de ensureReadableAccent
            // aplica a fondos oscuros; aquí el fondo es claro, así que un
            // acento oscuro es lo correcto.
            val lightBorder = Color(0xFFC9CED6)
            DivineDeckPalette(
                preset = ThemePreset.MINIMALISTA,
                background = Color(0xFFF4F5F7),      // Gris muy claro
                surface = Color(0xFFFFFFFF),         // Blanco
                elevatedSurface = Color(0xFFE9EBEE), // Gris claro elevado
                border = lightBorder,
                borderSubtle = lightBorder.copy(alpha = 0.45f),
                accent = Color(0xFF1F2937),          // Gris pizarra oscuro
                accentSecondary = Color(0xFF6B7280),
                accentGlow = Color(0xFF1F2937).copy(alpha = 0.08f),
                onAccent = Color(0xFFFFFFFF),
                textPrimary = Color(0xFF111827),
                textSecondary = Color(0xFF4B5563),
                textMuted = Color(0xFF9CA3AF),
                statusPositive = Color(0xFF15803D),
                statusAlert = Color(0xFFDC2626)
            )
        }

        ThemePreset.ROJO_SPORT -> {
            val redAccent = Color(0xFFFF3131)
            val redSecondary = Color(0xFFFF6B6B)
            val redBorder = Color(0xFF4A1F1F)
            DivineDeckPalette(
                preset = ThemePreset.ROJO_SPORT,
                background = Color(0xFF0A0505),      // Negro con tinte rojo
                surface = Color(0xFF160A0A),         // Fibra de carbono
                elevatedSurface = Color(0xFF221010),
                border = redBorder,
                borderSubtle = redBorder.copy(alpha = 0.45f),
                accent = redAccent,                  // Rojo racing
                accentSecondary = redSecondary,
                accentGlow = redAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFFFFFFFF),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFFD6A3A3),
                textMuted = Color(0xFF7A5252),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        ThemePreset.ESPECTRO -> {
            // El arcoíris no cabe en un solo token: accent = cian y
            // accentSecondary = magenta son los dos extremos del espectro;
            // el resto de colores del arcoíris se usan en gradientes.
            val spectrumAccent = Color(0xFF22D3EE)
            val spectrumSecondary = Color(0xFFE879F9)
            val spectrumBorder = Color(0xFF2E2E4A)
            DivineDeckPalette(
                preset = ThemePreset.ESPECTRO,
                background = Color(0xFF07070F),
                surface = Color(0xFF0E0E1E),
                elevatedSurface = Color(0xFF181830),
                border = spectrumBorder,
                borderSubtle = spectrumBorder.copy(alpha = 0.45f),
                accent = spectrumAccent,
                accentSecondary = spectrumSecondary,
                accentGlow = spectrumAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFF041016),
                textPrimary = Color(0xFFF1F5F9),
                textSecondary = Color(0xFFA5B4FC),
                textMuted = Color(0xFF6366A0),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        ThemePreset.VAPORWAVE -> {
            val pinkAccent = Color(0xFFFF2E9A)
            val cyanSecondary = Color(0xFF00E5FF)
            val vaporBorder = Color(0xFF4A2A6B)
            DivineDeckPalette(
                preset = ThemePreset.VAPORWAVE,
                background = Color(0xFF12081F),      // Morado oscuro
                surface = Color(0xFF1E0F33),
                elevatedSurface = Color(0xFF2A1845),
                border = vaporBorder,
                borderSubtle = vaporBorder.copy(alpha = 0.45f),
                accent = pinkAccent,                 // Rosa neón
                accentSecondary = cyanSecondary,     // Cian neón
                accentGlow = pinkAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFFFFFFFF),
                textPrimary = Color(0xFFF5F0FF),
                textSecondary = Color(0xFFC4B5FD),
                textMuted = Color(0xFF7E6BA8),
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }

        ThemePreset.ACIDO_LIMA -> {
            val limeAccent = Color(0xFFBFFF00)
            val limeSecondary = Color(0xFF7CFF00)
            val limeBorder = Color(0xFF243524)
            DivineDeckPalette(
                preset = ThemePreset.ACIDO_LIMA,
                background = Color(0xFF050805),
                surface = Color(0xFF0B120B),
                elevatedSurface = Color(0xFF142014),
                border = limeBorder,
                borderSubtle = limeBorder.copy(alpha = 0.45f),
                accent = limeAccent,                 // Verde lima fosforescente
                accentSecondary = limeSecondary,
                accentGlow = limeAccent.copy(alpha = 0.16f),
                onAccent = Color(0xFF050805),
                textPrimary = Color(0xFFF0FFF0),
                textSecondary = Color(0xFFA3C9A3),
                textMuted = Color(0xFF5A7A5A),
                statusPositive = limeAccent,
                statusAlert = AlertRed
            )
        }

        ThemePreset.CUSTOM -> {
            val safeAccent = ensureReadableAccent(Color(customAccentHex))
            val onAccentColor = if (safeAccent.luminance() > 0.35f) Color(0xFF080B12) else SoftWhite
            val customBorder = Color(0xFF2D3748)
            DivineDeckPalette(
                preset = ThemePreset.CUSTOM,
                background = Color(0xFF080A0F),
                surface = Color(0xFF111621),
                elevatedSurface = Color(0xFF1A2232),
                border = customBorder,
                borderSubtle = customBorder.copy(alpha = 0.45f),
                accent = safeAccent,
                accentSecondary = safeAccent.copy(alpha = 0.85f),
                accentGlow = safeAccent.copy(alpha = 0.16f),
                onAccent = onAccentColor,
                textPrimary = SoftWhite,
                textSecondary = SecondaryGray,
                textMuted = TextMutedDark,
                statusPositive = StatusGreen,
                statusAlert = AlertRed
            )
        }
    }
}

val LocalDivineDeckPalette = staticCompositionLocalOf {
    buildPaletteForTheme(ThemePreset.DEFAULT)
}

object DivineDeckTheme {
    val palette: DivineDeckPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalDivineDeckPalette.current
}

@Composable
fun DivineDeckTheme(
    themePreset: ThemePreset = ThemePreset.DEFAULT,
    customAccentHex: Long = 0xFFFFB547,
    content: @Composable () -> Unit
) {
    val palette = buildPaletteForTheme(themePreset, customAccentHex)

    // Los temas claros (Minimalista) usan lightColorScheme para que los
    // componentes de Material 3 (botones, switches, diálogos) no dibujen
    // texto oscuro sobre fondo oscuro. El resto usa darkColorScheme.
    val colorScheme = if (palette.preset.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.elevatedSurface,
            onPrimaryContainer = palette.accent,
            secondary = palette.accentSecondary,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.elevatedSurface,
            onSecondaryContainer = palette.accentSecondary,
            tertiary = palette.statusPositive,
            onTertiary = palette.background,
            tertiaryContainer = palette.surface,
            onTertiaryContainer = palette.statusPositive,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.elevatedSurface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.borderSubtle,
            error = palette.statusAlert,
            onError = palette.textPrimary,
            errorContainer = palette.statusAlert.copy(alpha = 0.2f),
            onErrorContainer = palette.statusAlert
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.elevatedSurface,
            onPrimaryContainer = palette.accent,
            secondary = palette.accentSecondary,
            onSecondary = palette.onAccent,
            secondaryContainer = palette.elevatedSurface,
            onSecondaryContainer = palette.accentSecondary,
            tertiary = palette.statusPositive,
            onTertiary = palette.background,
            tertiaryContainer = palette.surface,
            onTertiaryContainer = palette.statusPositive,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.elevatedSurface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            outlineVariant = palette.borderSubtle,
            error = palette.statusAlert,
            onError = palette.textPrimary,
            errorContainer = palette.statusAlert.copy(alpha = 0.2f),
            onErrorContainer = palette.statusAlert
        )
    }

    CompositionLocalProvider(LocalDivineDeckPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DivineDeckTypography,
            shapes = DivineDeckShapes,
            content = content
        )
    }
}

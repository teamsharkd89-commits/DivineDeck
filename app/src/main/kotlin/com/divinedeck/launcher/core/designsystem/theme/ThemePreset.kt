package com.divinedeck.launcher.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Temas oficiales del sistema de personalización de DIVINE DECK (Fase 02.1).
 */
enum class ThemePreset(
    val id: String,
    val displayName: String,
    val description: String,
    /** True solo para temas claros (fondo claro, texto oscuro). */
    val isLight: Boolean = false
) {
    OBSIDIAN(
        id = "OBSIDIAN",
        displayName = "Obsidian",
        description = "Fondo grafito, superficies azul marino oscuro y acentos oro cálido."
    ),
    ARCTIC(
        id = "ARCTIC",
        displayName = "Arctic",
        description = "Fondo oscuro, superficies azul profundo y acentos cian."
    ),
    MONOCHROME(
        id = "MONOCHROME",
        displayName = "Monochrome",
        description = "Fondo negro puro, superficies grises neutras y acentos blancos."
    ),
    BRONZE(
        id = "BRONZE",
        displayName = "Bronze",
        description = "Fondo negro cálido, superficies marrón grafito y acentos bronce."
    ),
    CUSTOM(
        id = "CUSTOM",
        displayName = "Personalizado",
        description = "Superficies oscuras de alto contraste con color de acento personalizado."
    ),
    // =========================================================================
    // TEMAS PANORÁMICO (Fase 06.11) — variantes de color del layout Panorámico,
    // aprobadas por Daniel el 2026-11-11. Solo cambian colores, nunca funciones.
    // =========================================================================
    MINIMALISTA(
        id = "MINIMALISTA",
        displayName = "Minimalista",
        description = "Tema principal. Fondo claro, gris y blanco, líneas finas, máxima legibilidad.",
        isLight = true
    ),
    ROJO_SPORT(
        id = "ROJO_SPORT",
        displayName = "Rojo Sport",
        description = "Fibra de carbono negra con rojo racing intenso."
    ),
    ESPECTRO(
        id = "ESPECTRO",
        displayName = "Espectro",
        description = "Arcoíris completo sobre fondo negro, un color por tarjeta."
    ),
    VAPORWAVE(
        id = "VAPORWAVE",
        displayName = "Vaporwave",
        description = "Rosa neón, cian y sol retro de los 80."
    ),
    ACIDO_LIMA(
        id = "ACIDO_LIMA",
        displayName = "Ácido Lima",
        description = "Negro total con verde lima fosforescente."
    );

    companion object {
        /** Tema por defecto al instalar / sin preferencia guardada. */
        val DEFAULT: ThemePreset = MINIMALISTA

        fun fromId(id: String?): ThemePreset {
            return entries.find { it.id == id } ?: DEFAULT
        }
    }
}

/**
 * Opciones curadas de color de acento personalizado con alto contraste garantizado
 * para uso automotriz seguro (evita combinaciones ilegibles sobre fondos oscuros).
 */
data class CustomAccentOption(
    val name: String,
    val hex: Long,
    val color: Color = Color(hex)
)

val CuratedCustomAccents = listOf(
    CustomAccentOption("Oro Cálido", 0xFFFFB547),
    CustomAccentOption("Cian Ártico", 0xFF22D3EE),
    CustomAccentOption("Blanco Puro", 0xFFF4F5F7),
    CustomAccentOption("Bronce Cobre", 0xFFD98A4E),
    CustomAccentOption("Rojo C2 Sport", 0xFFF43F5E),
    CustomAccentOption("Verde Esmeralda", 0xFF34D399),
    CustomAccentOption("Azul Cobalto", 0xFF60A5FA),
    CustomAccentOption("Ámbar Solar", 0xFFFB923C),
    CustomAccentOption("Violeta GT", 0xFFC084FC)
)

/**
 * Garantiza que cualquier color de acento personalizado mantenga suficiente luminancia relativa
 * (>= 0.25) frente a las superficies oscuras del cockpit, evitando combinaciones ilegibles.
 */
fun ensureReadableAccent(rawColor: Color): Color {
    var candidate = rawColor.copy(alpha = 1f)
    var step = 0
    while (candidate.luminance() < 0.25f && step < 10) {
        candidate = Color(
            red = (candidate.red + (1f - candidate.red) * 0.35f).coerceIn(0f, 1f),
            green = (candidate.green + (1f - candidate.green) * 0.35f).coerceIn(0f, 1f),
            blue = (candidate.blue + (1f - candidate.blue) * 0.35f).coerceIn(0f, 1f),
            alpha = 1f
        )
        step++
    }
    return candidate
}

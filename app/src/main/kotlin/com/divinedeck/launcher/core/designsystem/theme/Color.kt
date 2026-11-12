package com.divinedeck.launcher.core.designsystem.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// DIVINE DECK — PALETA AUTOMOTRIZ OFICIAL (FASE 02: PREMIUM COCKPIT)
// Samsung Galaxy Tab A11 SM-X133 (1340 x 800) • Chevrolet Chevy C2 2009
// =========================================================================

// Fondos y Superficies Profundas
val Obsidian = Color(0xFF080B12)
val MidnightNavy = Color(0xFF101827)
val Graphite = Color(0xFF171D27)
val ElevatedSurface = Color(0xFF202A38)
val SurfaceBorder = Color(0xFF344052)
val SurfaceBorderSubtle = Color(0x40344052)
val SurfaceBorderHighlight = Color(0xFF4A5A72)

// Acentos de Instrumentación Automotriz Cálida
val WarmAmber = Color(0xFFFFB547)          // Acento Primario: Cockpit, diales, selección
val WarmAmberDark = Color(0xFF8A5B15)
val WarmAmberGlow = Color(0x33FFB547)

val ChampagneGold = Color(0xFFE6B86A)       // Acento Secundario: Detalles, insignias Chevy
val ChampagneGoldDark = Color(0xFF6B5120)

// Indicadores de Estado y Sistema
val StatusGreen = Color(0xFF76C893)         // Estado positivo / Conectado / Sistema OK
val StatusGreenDark = Color(0xFF285437)

val AlertRed = Color(0xFFE45B5B)            // Salida / Alerta / Desconexión
val AlertRedGlow = Color(0x33E45B5B)

// Tipografía y Legibilidad en Habitáculo
val SoftWhite = Color(0xFFF4F5F7)           // Lectura principal de alta visibilidad
val SecondaryGray = Color(0xFF9AA4B2)       // Lectura secundaria y unidades
val TextMutedDark = Color(0xFF5E6A7B)

// Superficies Translúcidas y Efectos de Estudio
val StudioBacklight = Color(0x22FFB547)
val GlassSurface = Color(0xD9101827)
val GlassBorder = Color(0x55344052)

// =========================================================================
// COMPATIBILIDAD Y ALIASES ARQUITECTÓNICOS (Fase 01 -> Fase 02)
// =========================================================================
val ObsidianBlack = Obsidian
val CarbonDark = MidnightNavy
val CarbonSurface = Graphite
val CarbonElevated = ElevatedSurface
val CarbonBorder = SurfaceBorder
val CarbonBorderHighlight = SurfaceBorderHighlight

val NeonCyan = WarmAmber // Reemplazo oficial: el ámbar automotriz sustituye al cyan genérico
val NeonCyanDark = ChampagneGold
val NeonCyanGlow = WarmAmberGlow

val ChevyGold = WarmAmber
val ChevyGoldDark = WarmAmberDark

val FlameCrimson = AlertRed
val FlameCrimsonGlow = AlertRedGlow

val SpeedEmerald = StatusGreen
val SpeedEmeraldDark = StatusGreenDark

val TextPureWhite = SoftWhite
val TextPlatinum = SoftWhite
val TextMuted = SecondaryGray
val TextDisabled = TextMutedDark

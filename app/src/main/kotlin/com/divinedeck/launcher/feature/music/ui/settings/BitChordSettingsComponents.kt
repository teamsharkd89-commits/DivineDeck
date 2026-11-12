package com.divinedeck.launcher.feature.music.ui.settings

import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// TOKENS EXACTOS DE DISEÑO DE BITCHORD 1.8.1 (SettingsSheet.kt)
// =============================================================================

val GroupShape = RoundedCornerShape(14.dp)
val GROUP_INSET: Dp = 16.dp
val ROW_INSET: Dp = 16.dp
val ICON_SIZE: Dp = 22.dp
val ICON_GAP: Dp = 14.dp

/** Where a row's text starts — dividers and sliders are inset to match (52.dp). */
val TEXT_INSET: Dp = ROW_INSET + ICON_SIZE + ICON_GAP

val ColorAmoledCard = Color(0xFF141414)
val ColorAmoledCardDark = Color(0xFF101010)
val ColorCardBorder = Color.White.copy(alpha = 0.08f)
val ColorCyanAccent = Color(0xFF00E5FF)
val ColorAmberAccent = Color(0xFFFFB547)
val ColorDestructiveRed = Color(0xFFEF4444)
val ColorTextPrimary = Color.White
val ColorTextSecondary = Color.White.copy(alpha = 0.55f)
val ColorTextMuted = Color.White.copy(alpha = 0.40f)

// =============================================================================
// COMPONENTES CLAVE RECUPERADOS DE BITCHORD 1.8.1
// =============================================================================

/**
 * Cabecera descriptiva para cada uno de los sub-paneles de categoría.
 */
@Composable
fun PaneHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(horizontal = GROUP_INSET, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
            ),
            color = ColorTextPrimary,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = ColorTextSecondary,
        )
    }
}

/**
 * Tarjeta de grupo estilo BitChord 1.8.1:
 * Inset card con esquinas redondeadas (14.dp), fondo AMOLED (0xFF141414),
 * borde sutil y cabecera en mayúsculas acentuada en cian.
 */
@Composable
fun SettingsCard(
    title: String? = null,
    footer: String? = null,
    topSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (title != null) {
        Text(
            text = title.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            ),
            color = ColorCyanAccent,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET,
                top = topSpacing,
                bottom = 8.dp,
            ),
        )
    } else {
        Spacer(Modifier.height(topSpacing))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GROUP_INSET),
        shape = GroupShape,
        color = ColorAmoledCard,
        border = BorderStroke(0.5.dp, ColorCardBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
    if (footer != null) {
        Text(
            text = footer,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = ColorTextSecondary,
            modifier = Modifier.padding(
                start = GROUP_INSET + 4.dp,
                end = GROUP_INSET + 4.dp,
                top = 6.dp,
            ),
        )
    }
}

/**
 * Separador horizontal con inicio indentado a TEXT_INSET (52.dp),
 * alineado milimétricamente con el texto de la fila.
 */
@Composable
fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = TEXT_INSET),
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.07f),
    )
}

/**
 * Fila estándar de BitChord 1.8.1:
 * Glifo líder de 22.dp, título principal, subtítulo descriptivo en gris,
 * y elemento trailing (Switch, Chevron o valor textual).
 */
@Composable
fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    value: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorTextPrimary,
                modifier = Modifier.size(ICON_SIZE),
            )
        } else {
            Spacer(Modifier.size(ICON_SIZE))
        }
        Spacer(Modifier.width(ICON_GAP))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = ColorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            if (subtitleContent != null) {
                subtitleContent()
            } else if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = ColorTextSecondary,
                    maxLines = 5,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing != null) {
            trailing()
        } else if (value != null || onClick != null) {
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = ColorTextSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(4.dp))
            }
            Chevron()
        }
    }
}

/**
 * Fila hija de ajuste secundario dependiente:
 * Sin icono líder, alineada e indentada a TEXT_INSET (52.dp),
 * sin divider separador con el elemento padre.
 */
@Composable
fun SettingsSubRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    badge: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(start = TEXT_INSET, end = ROW_INSET, top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = ColorTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(badge)
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = ColorTextSecondary,
                    maxLines = 3,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ColorCyanAccent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.60f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.20f),
            ),
        )
    }
}

/**
 * Fila de control deslizante continuo de BitChord 1.8.1:
 * Icono de 22.dp, título, valor actual a la derecha en cian,
 * y barra Slider en la línea inferior con pista limpia (sin ticks ruidosos)
 * y padding inicial alineado a TEXT_INSET.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    icon: ImageVector? = null,
    title: String,
    value: String,
    sliderValue: Float,
    onSliderValue: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    subtitle: String? = null,
) {
    val colors = SliderDefaults.colors(
        thumbColor = ColorCyanAccent,
        activeTrackColor = ColorCyanAccent,
        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ROW_INSET, end = ROW_INSET, top = 12.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ColorTextPrimary,
                    modifier = Modifier.size(ICON_SIZE),
                )
            } else {
                Spacer(Modifier.size(ICON_SIZE))
            }
            Spacer(Modifier.width(ICON_GAP))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = ColorTextPrimary,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = ColorTextSecondary,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = ColorCyanAccent,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = onSliderValue,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = colors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                )
            },
            modifier = Modifier.padding(start = TEXT_INSET - ROW_INSET),
        )
    }
}

/**
 * Selector segmentado estilo pastilla deslizante de BitChord 1.8.1:
 * Fondo de pista encapsulado (10.dp), píldora activa con animación suave
 * (animateColorAsState) y retroalimentación háptica TextHandleMove.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val chosen = index == selectedIndex
            val pill by animateColorAsState(
                targetValue = if (chosen) ColorCyanAccent else Color.Transparent,
                animationSpec = tween(160),
                label = "segmentPill",
            )
            val labelColor by animateColorAsState(
                targetValue = if (chosen) Color.Black else Color.White.copy(alpha = 0.70f),
                animationSpec = tween(160),
                label = "segmentLabel",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pill)
                    .clickable(enabled = enabled) {
                        if (!chosen) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = labelColor,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Etiqueta badge de estado acentuada.
 */
@Composable
fun Badge(text: String) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        ),
        color = ColorCyanAccent,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(ColorCyanAccent.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Chevron indicador de navegación o apertura.
 */
@Composable
fun Chevron() {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = Color.White.copy(alpha = 0.45f),
        modifier = Modifier.size(20.dp),
    )
}

/**
 * Fila destructiva de BitChord 1.8.1:
 * Texto de advertencia centrado en color rojo para acciones críticas.
 */
@Composable
fun DestructiveRow(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            ),
            color = ColorDestructiveRed,
        )
    }
}

/**
 * Fila de acción con botón a la derecha (ej. selector de carpeta o exportar).
 */
@Composable
fun SettingsActionRow(
    icon: ImageVector? = null,
    title: String,
    subtitle: String? = null,
    buttonText: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ColorTextPrimary,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(Modifier.width(ICON_GAP))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = ColorTextPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = ColorTextSecondary,
                )
            }
        }
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isDestructive) ColorDestructiveRed.copy(alpha = 0.20f) else ColorCyanAccent.copy(alpha = 0.20f),
                contentColor = if (isDestructive) ColorDestructiveRed else ColorCyanAccent,
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDestructive) ColorDestructiveRed.copy(alpha = 0.60f) else ColorCyanAccent.copy(alpha = 0.60f),
            ),
        ) {
            Text(
                text = buttonText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

/**
 * Píldora de estadística de almacenamiento (Descargas, Streaming, Portadas).
 */
@Composable
fun StorageStatPill(
    label: String,
    value: String,
    detail: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = ColorTextSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                ),
                color = color,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ColorTextMuted,
            )
        }
    }
}

/**
 * Fila de especificación de hardware y ficha técnica.
 */
@Composable
fun HardwareSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_INSET, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = ColorTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = ColorTextPrimary,
        )
    }
}

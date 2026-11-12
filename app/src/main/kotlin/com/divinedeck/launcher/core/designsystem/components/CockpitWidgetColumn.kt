package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetCandidateType
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot
import com.divinedeck.launcher.feature.widgets.ui.NativeWidgetHostContainer

/**
 * Columna Derecha con DOS Espacios Verticales Configurables para Widgets (Zona 3 - Fase 02.1 & 05.3 & 06.6).
 * Distribución equilibrada:
 * - WIDGET SUPERIOR (Slot 1): Mini reproductor automotriz BitChord o AppWidget Android vinculado.
 * - WIDGET INFERIOR (Slot 2): Fuel Sense o AppWidget Android vinculado.
 * Renderiza AppWidgetHostView reales interactivos mediante NativeWidgetHostContainer (Fase 05.3).
 */
@Composable
fun CockpitWidgetColumn(
    widgetManager: AndroidWidgetHostManager,
    mediaRepository: MediaControllerRepository? = null,
    onOpenMusicPlayer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNavigateToWidgets: (() -> Unit)? = null
) {
    val configuredSlots by widgetManager.configuredSlots.collectAsState()
    var activePickerSlot by remember { mutableStateOf<WidgetSlot?>(null) }

    val topSlot = configuredSlots.getOrNull(0) ?: WidgetSlot(slotId = 1, positionLabel = "WIDGET SUPERIOR")
    val bottomSlot = configuredSlots.getOrNull(1) ?: WidgetSlot(slotId = 2, positionLabel = "WIDGET INFERIOR")

    val onSlotConfigure: (WidgetSlot) -> Unit = { slot ->
        if (onNavigateToWidgets != null) {
            onNavigateToWidgets()
        } else {
            activePickerSlot = slot
        }
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ESPACIO 1: WIDGET SUPERIOR -> BitChord Mini Player si no hay AppWidget Android externo vinculado
        val showMiniPlayer = mediaRepository != null && (
            topSlot.assignedCandidate == WidgetCandidateType.BITCHORD_MUSIC ||
            topSlot.isEmpty ||
            (!topSlot.isBound && topSlot.assignedCandidate != WidgetCandidateType.NONE)
        )

        if (showMiniPlayer) {
            CockpitBitChordMiniPlayer(
                mediaRepository = mediaRepository,
                onClick = { onOpenMusicPlayer?.invoke() },
                onConfigure = { onSlotConfigure(topSlot) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            NativeWidgetHostContainer(
                slot = topSlot,
                widgetManager = widgetManager,
                onConfigureClick = { onSlotConfigure(topSlot) },
                onClearClick = { widgetManager.clearSlot(topSlot.slotId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // ESPACIO 2: WIDGET INFERIOR -> Conserva Fuel Sense o widget asignado
        NativeWidgetHostContainer(
            slot = bottomSlot,
            widgetManager = widgetManager,
            onConfigureClick = { onSlotConfigure(bottomSlot) },
            onClearClick = { widgetManager.clearSlot(bottomSlot.slotId) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }

    // Selector modal de widgets para el espacio seleccionado (fallback si no hay navegación directa)
    activePickerSlot?.let { targetSlot ->
        WidgetCandidateSelectorDialog(
            slot = targetSlot,
            onDismiss = { activePickerSlot = null },
            onSelectCandidate = { candidate ->
                widgetManager.assignCandidateToSlot(targetSlot.slotId, candidate)
                activePickerSlot = null
            }
        )
    }
}

@Composable
fun ConfigurableWidgetSlotCard(
    slot: WidgetSlot,
    onConfigureClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surface.copy(alpha = 0.92f),
                        palette.background.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = if (slot.isEmpty) palette.borderSubtle else palette.border,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable { onConfigureClick() }
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Cabecera del espacio de widget
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                if (slot.isEmpty) palette.textMuted else palette.accent,
                                CircleShape
                            )
                    )
                    Text(
                        text = slot.positionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        ),
                        color = palette.textSecondary
                    )
                }

                if (slot.isEmpty) {
                    Text(
                        text = "VACÍO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        ),
                        color = palette.textMuted
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Botón cambiar widget
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.elevatedSurface)
                                .border(0.8.dp, palette.border, RoundedCornerShape(8.dp))
                                .clickable { onConfigureClick() }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Cambiar widget",
                                    tint = palette.accent,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "CAMBIAR",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = palette.textPrimary
                                )
                            }
                        }

                        // Botón vaciar ranura
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.elevatedSurface)
                                .border(0.8.dp, palette.borderSubtle, CircleShape)
                                .clickable { onClearClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Vaciar espacio",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            // Contenido central según el estado (Vacío o Seleccionado)
            if (slot.isEmpty) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(palette.elevatedSurface)
                            .border(1.dp, palette.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Elegir widget",
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Configurar widget",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Toca para elegir contenido Android",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }

                // Pie discreto en estado vacío
                Text(
                    text = "Bitchord • Fuel Sense • Maps • Clima • Reloj",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        letterSpacing = 0.4.sp
                    ),
                    color = palette.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                val candidate = slot.assignedCandidate
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentGlow)
                            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconForWidgetCandidate(candidate),
                            contentDescription = candidate.displayName,
                            tint = palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = candidate.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = palette.textPrimary
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = candidate.categorySubtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.5.sp),
                        color = palette.textSecondary
                    )
                }

                // Pie honesto de arquitectura AppWidgetHost
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.elevatedSurface.copy(alpha = 0.65f))
                        .border(0.8.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${candidate.providerHint} • AppWidgetHost en espera",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            letterSpacing = 0.3.sp
                        ),
                        color = palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetCandidateSelectorDialog(
    slot: WidgetSlot,
    onDismiss: () -> Unit,
    onSelectCandidate: (WidgetCandidateType) -> Unit
) {
    val palette = DivineDeckTheme.palette

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = palette.surface,
            border = BorderStroke(1.dp, palette.border),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONFIGURAR ${slot.positionLabel}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Elige el widget que deseas asignar a este espacio del Cockpit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.elevatedSurface)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Opciones de widgets seleccionables
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetCandidateType.selectableCandidates.forEach { candidate ->
                        val isSelected = slot.assignedCandidate == candidate
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) palette.accentGlow else palette.elevatedSurface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) palette.accent else palette.borderSubtle,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectCandidate(candidate) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = iconForWidgetCandidate(candidate),
                                    contentDescription = candidate.displayName,
                                    tint = if (isSelected) palette.accent else palette.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = candidate.displayName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = palette.textPrimary
                                    )
                                    Text(
                                        text = "${candidate.categorySubtitle} • ${candidate.providerHint}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.5.sp),
                                        color = palette.textSecondary
                                    )
                                }
                            }

                            if (isSelected) {
                                Text(
                                    text = "ACTIVO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    color = palette.accent
                                )
                            }
                        }
                    }

                    // Opción para dejar vacío
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.background)
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
                            .clickable { onSelectCandidate(WidgetCandidateType.NONE) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Dejar espacio vacío (Sin widget)",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

fun iconForWidgetCandidate(candidate: WidgetCandidateType): ImageVector {
    return when (candidate) {
        WidgetCandidateType.NONE -> Icons.Default.Add
        WidgetCandidateType.BITCHORD_MUSIC -> Icons.Default.MusicNote
        WidgetCandidateType.FUEL_SENSE -> Icons.Default.LocalGasStation
        WidgetCandidateType.GOOGLE_MAPS -> Icons.Default.Map
        WidgetCandidateType.WEATHER -> Icons.Default.WbSunny
        WidgetCandidateType.CLOCK -> Icons.Default.Schedule
        WidgetCandidateType.GENERIC_WIDGET,
        WidgetCandidateType.OTHER_ANDROID_WIDGET -> Icons.Default.Widgets
    }
}

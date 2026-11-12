package com.divinedeck.launcher.feature.widgets.ui

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager
import com.divinedeck.launcher.feature.widgets.domain.model.DiscoveredWidgetProvider
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetBindingStatus
import com.divinedeck.launcher.feature.widgets.domain.model.WidgetSlot

/**
 * Centro de Configuración y Vinculación Universal de Widgets de DIVINE DECK (Fase 05.2.1).
 * Descubrimiento dinámico de cualquier widget instalado y asignación a las dos ranuras del Cockpit.
 */
@Composable
fun WidgetCenterScreen(
    widgetManager: AndroidWidgetHostManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val palette = DivineDeckTheme.palette

    val configuredSlots by widgetManager.configuredSlots.collectAsState()
    val isOperational by widgetManager.isOperational.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }

    val topSlot = configuredSlots.getOrNull(0) ?: WidgetSlot(slotId = 1, positionLabel = "WIDGET SUPERIOR")
    val bottomSlot = configuredSlots.getOrNull(1) ?: WidgetSlot(slotId = 2, positionLabel = "WIDGET INFERIOR")

    val discoveredProviders = remember(searchQuery, refreshTrigger) {
        val all = widgetManager.discoverProviders()
        if (searchQuery.isBlank()) {
            all
        } else {
            val q = searchQuery.trim().lowercase()
            all.filter {
                it.resolvedAppName.lowercase().contains(q) ||
                it.label.lowercase().contains(q) ||
                it.packageName.lowercase().contains(q)
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // COLUMNA IZQUIERDA: GESTIÓN DE RANURAS DEL COCKPIT
        AutomotiveCard(
            modifier = Modifier
                .weight(1.05f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RANURAS DEL COCKPIT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Asignación universal para las dos ranuras.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Botón para intercambiar ranuras
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.elevatedSurface)
                                .border(1.dp, palette.borderSubtle, CircleShape)
                                .clickable { widgetManager.swapSlots() }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Intercambiar ranuras",
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Botón para recargar estado
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.elevatedSurface)
                                .border(1.dp, palette.borderSubtle, CircleShape)
                                .clickable {
                                    widgetManager.refreshAndRestoreBindings()
                                    refreshTrigger++
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar estado",
                                tint = palette.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // RANURA 1: SUPERIOR
                SlotManagementCard(
                    slot = topSlot,
                    onUnbindClick = { widgetManager.unbindSlot(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // RANURA 2: INFERIOR
                SlotManagementCard(
                    slot = bottomSlot,
                    onUnbindClick = { widgetManager.unbindSlot(2) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }

        // COLUMNA DERECHA: CATÁLOGO UNIVERSAL DE WIDGETS INSTALADOS
        AutomotiveCard(
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CATÁLOGO DE WIDGETS",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "${discoveredProviders.size} proveedores Android detectados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }

                    // Indicador de operatividad del host
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOperational) palette.statusPositive.copy(alpha = 0.15f) else palette.statusAlert.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isOperational) palette.statusPositive.copy(alpha = 0.4f) else palette.statusAlert.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (isOperational) "Host Operativo (0xDD05)" else "Host No Operativo",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isOperational) palette.statusPositive else palette.statusAlert,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Campo de búsqueda universal
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar por nombre de app o widget...",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary.copy(alpha = 0.7f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar búsqueda",
                                tint = palette.textSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.borderSubtle,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = palette.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Lista de proveedores descubiertos
                if (discoveredProviders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No se detectaron widgets instalados compatibles." else "Sin coincidencias para '$searchQuery'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = discoveredProviders,
                            key = { it.componentShortString }
                        ) { provider ->
                            ProviderCatalogItemCard(
                                provider = provider,
                                topSlot = topSlot,
                                bottomSlot = bottomSlot,
                                isOperational = isOperational,
                                onAssignToTop = {
                                    if (activity != null) {
                                        widgetManager.startBindingFlow(activity, 1, provider)
                                    }
                                },
                                onAssignToBottom = {
                                    if (activity != null) {
                                        widgetManager.startBindingFlow(activity, 2, provider)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotManagementCard(
    slot: WidgetSlot,
    onUnbindClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, if (slot.isBound) palette.accent.copy(alpha = 0.5f) else palette.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = slot.positionLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = palette.accent
                )

                BindingStatusBadge(status = slot.bindingStatus)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (slot.isBound) slot.title else "Ranura Vacía",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = palette.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = if (slot.isBound) {
                        "ID: ${slot.appWidgetId} • ${slot.providerPackage ?: ""}"
                    } else {
                        "Selecciona un widget del catálogo para asignar."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (slot.isBound) {
                    OutlinedButton(
                        onClick = onUnbindClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = palette.statusAlert
                        ),
                        border = BorderStroke(1.dp, palette.statusAlert.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Desvincular", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCatalogItemCard(
    provider: DiscoveredWidgetProvider,
    topSlot: WidgetSlot,
    bottomSlot: WidgetSlot,
    isOperational: Boolean,
    onAssignToTop: () -> Unit,
    onAssignToBottom: () -> Unit
) {
    val palette = DivineDeckTheme.palette
    val isAssignedToTop = topSlot.isBound && topSlot.providerPackage == provider.packageName && topSlot.providerClass == provider.className
    val isAssignedToBottom = bottomSlot.isBound && bottomSlot.providerPackage == provider.packageName && bottomSlot.providerClass == provider.className

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.elevatedSurface,
        border = BorderStroke(1.dp, if (isAssignedToTop || isAssignedToBottom) palette.accent.copy(alpha = 0.6f) else palette.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = provider.resolvedAppName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = palette.textPrimary
                        )
                        Text(
                            text = provider.label.ifBlank { provider.shortLabel },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                }

                if (isAssignedToTop) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.accent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "En Ranura Superior",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (isAssignedToBottom) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.accent.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "En Ranura Inferior",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = palette.accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Metadatos compactos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Dimensiones: ${provider.formattedDimensions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
                Text(
                    text = if (provider.configure != null) "Requiere configuración" else "Sin configuración",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (provider.configure != null) palette.accentSecondary else palette.textSecondary
                )
            }

            // Botones de acción para asignar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAssignToTop,
                    enabled = isOperational && !isAssignedToTop,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAssignedToTop) palette.surface else palette.accent,
                        contentColor = if (isAssignedToTop) palette.textSecondary else palette.onAccent
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isAssignedToTop) "Asignado Arriba" else "Asignar a Slot 1 (Superior)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = onAssignToBottom,
                    enabled = isOperational && !isAssignedToBottom,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAssignedToBottom) palette.surface else palette.accent,
                        contentColor = if (isAssignedToBottom) palette.textSecondary else palette.onAccent
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isAssignedToBottom) "Asignado Abajo" else "Asignar a Slot 2 (Inferior)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BindingStatusBadge(status: WidgetBindingStatus) {
    val palette = DivineDeckTheme.palette

    val (bgColor, textColor, borderCol) = when (status) {
        WidgetBindingStatus.BOUND -> Triple(
            palette.statusPositive.copy(alpha = 0.15f),
            palette.statusPositive,
            palette.statusPositive.copy(alpha = 0.4f)
        )
        WidgetBindingStatus.AVAILABLE_TO_BIND -> Triple(
            palette.accent.copy(alpha = 0.15f),
            palette.accent,
            palette.accent.copy(alpha = 0.4f)
        )
        WidgetBindingStatus.AWAITING_AUTHORIZATION,
        WidgetBindingStatus.REQUIRES_CONFIGURATION -> Triple(
            palette.accentSecondary.copy(alpha = 0.15f),
            palette.accentSecondary,
            palette.accentSecondary.copy(alpha = 0.4f)
        )
        WidgetBindingStatus.UNINSTALLED,
        WidgetBindingStatus.PROVIDER_NOT_FOUND -> Triple(
            palette.textMuted.copy(alpha = 0.15f),
            palette.textMuted,
            palette.textMuted.copy(alpha = 0.3f)
        )
        WidgetBindingStatus.BINDING_ERROR,
        WidgetBindingStatus.HOST_NOT_OPERATIONAL -> Triple(
            palette.statusAlert.copy(alpha = 0.15f),
            palette.statusAlert,
            palette.statusAlert.copy(alpha = 0.4f)
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderCol)
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

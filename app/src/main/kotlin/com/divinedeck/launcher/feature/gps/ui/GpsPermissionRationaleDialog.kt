package com.divinedeck.launcher.feature.gps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme

/**
 * Diálogo explicativo de permisos de ubicación para DIVINE DECK (Fase 4.0).
 *
 * Explica con transparencia al usuario por qué el launcher automotriz requiere acceso al sensor GPS:
 * - Cálculo de velocidad en tiempo real para el velocímetro digital del Chevrolet Chevy C2.
 * - Operación 100% local en la tablet, sin servidores externos ni rastreo en segundo plano.
 */
@Composable
fun GpsPermissionRationaleDialog(
    onDismissRequest: () -> Unit,
    onConfirmGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            palette.surface,
                            palette.background
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = palette.border,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Encabezado con icono temático
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accent.copy(alpha = 0.15f))
                            .border(1.dp, palette.accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Sensor GPS",
                            tint = palette.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "PERMISO DE UBICACIÓN Y VELOCÍMETRO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = palette.textPrimary
                        )
                        Text(
                            text = "DIVINE DECK • CHEVROLET CHEVY C2 2009",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.8.sp
                            ),
                            color = palette.textSecondary
                        )
                    }
                }

                // Explicación técnica y de privacidad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.elevatedSurface.copy(alpha = 0.5f))
                        .border(0.6.dp, palette.borderSubtle, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Para mostrar la velocidad real de conducción en el Cockpit digital y calcular las métricas de viaje, Divine Deck necesita acceso al sensor de ubicación GPS de la tablet.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        ),
                        color = palette.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Privacidad",
                            tint = palette.statusPositive,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Privacidad total: El procesamiento es 100% local. No se rastrea en segundo plano ni se envían datos a ningún servidor externo.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            ),
                            color = palette.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón secundario: Más tarde
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.elevatedSurface)
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismissRequest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MÁS TARDE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            ),
                            color = palette.textSecondary
                        )
                    }

                    // Botón primario: Conceder permiso
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onConfirmGrant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CONCEDER PERMISO",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = palette.background
                        )
                    }
                }
            }
        }
    }
}

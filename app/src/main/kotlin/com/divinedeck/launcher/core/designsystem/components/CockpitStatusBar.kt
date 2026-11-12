package com.divinedeck.launcher.core.designsystem.components

import android.content.Context
import android.os.BatteryManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Barra Superior Discreta y Minimalista (Zona 1 - Fase 02.1).
 * Muestra Hora, Fecha, Wi-Fi, Bluetooth, Estado GPS honesto, Batería real del sistema
 * y acceso discreto de salida controlada sin competir visualmente con el vehículo central.
 */
@Composable
fun CockpitStatusBar(
    operatingModeTitle: String,
    onToggleMode: () -> Unit,
    onRequestExit: () -> Unit,
    isGpsAvailable: Boolean = false,
    gpsStatus: GpsStatus = if (isGpsAvailable) GpsStatus.CONNECTED else GpsStatus.NOT_AVAILABLE,
    onGpsStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = DivineDeckTheme.palette

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryPercent by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.forLanguageTag("es-MX"))
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now).uppercase(Locale.forLanguageTag("es-MX"))
            val level = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (level in 0..100) {
                batteryPercent = level
            }
            delay(5000)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // IZQUIERDA: Hora actual y Fecha
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = currentTime.ifEmpty { "12:40" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = palette.accent
                )

                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(palette.textMuted, CircleShape)
                )

                Text(
                    text = currentDate.ifEmpty { "MIÉ, 4 NOV" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp
                    ),
                    color = palette.textSecondary
                )
            }

            // DERECHA: Estado GPS, Wi-Fi, Bluetooth, Batería y Salida discreta
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Estado GPS honesto (5 estados oficiales Fase 4.0)
                val gpsIcon = when (gpsStatus) {
                    GpsStatus.CONNECTED -> Icons.Default.LocationOn
                    GpsStatus.WEAK_SIGNAL, GpsStatus.SEARCHING -> Icons.Default.LocationSearching
                    GpsStatus.UNRELIABLE_SPEED, GpsStatus.NOT_AVAILABLE -> Icons.Default.LocationDisabled
                }
                val gpsColor = when (gpsStatus) {
                    GpsStatus.CONNECTED -> palette.statusPositive
                    GpsStatus.WEAK_SIGNAL -> palette.accentSecondary
                    GpsStatus.SEARCHING -> palette.accent
                    GpsStatus.UNRELIABLE_SPEED -> palette.statusAlert
                    GpsStatus.NOT_AVAILABLE -> palette.accent
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = if (onGpsStatusClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onGpsStatusClick
                        )
                    } else {
                        Modifier
                    }
                ) {
                    Icon(
                        imageVector = gpsIcon,
                        contentDescription = "Estado GPS: ${gpsStatus.label}",
                        tint = gpsColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = gpsStatus.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.8.sp
                        ),
                        color = gpsColor
                    )
                }

                // Separador sutil
                Box(
                    modifier = Modifier
                        .height(12.dp)
                        .width(1.dp)
                        .background(palette.borderSubtle)
                )

                // Iconos de conectividad (Wi-Fi + Bluetooth)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = "Estado Wi-Fi",
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = "Estado Bluetooth",
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Batería del sistema cuando está disponible
                if (batteryPercent in 0..100) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryFull,
                            contentDescription = "Batería",
                            tint = palette.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$batteryPercent%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = palette.accent
                        )
                    }
                }

                // Modo Automóvil discreto
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surface)
                        .border(0.8.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { onToggleMode() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(palette.accent, CircleShape)
                        )
                        Text(
                            text = operatingModeTitle.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.6.sp
                            ),
                            color = palette.accent
                        )
                    }
                }

                // Control discreto de salida hacia One UI Home
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.statusAlert.copy(alpha = 0.1f))
                        .border(0.8.dp, palette.statusAlert.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable { onRequestExit() }
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salir",
                            tint = palette.statusAlert,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "SALIR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = palette.statusAlert
                        )
                    }
                }
            }
        }

        // Línea divisoria inferior minimalista
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.borderSubtle)
        )
    }
}

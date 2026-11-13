package com.divinedeck.launcher.feature.garage3d.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode

@Composable
fun Garage3DScreen(
    chevyRenderer: Chevy3DRenderer,
    modifier: Modifier = Modifier,
    lowriderEnabled: Boolean = false
) {
    val palette = DivineDeckTheme.palette
    val chevyState by chevyRenderer.modelState.collectAsState()
    var simulatedSpeed by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ÁREA PRINCIPAL: VISUALIZADOR 3D INTERACTIVO EN TIEMPO REAL
        AutomotiveCard(
            modifier = Modifier
                .weight(2.0f)
                .fillMaxHeight(),
            borderColor = when (chevyState.visualMode) {
                SpeedVisualMode.NORMAL -> palette.border
                SpeedVisualMode.CORREDOR -> Color(0xFFFFC800)
                SpeedVisualMode.ADRENALINA -> Color(0xFFFF2200)
                SpeedVisualMode.COHETE -> Color(0xFFFF0800)
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHEVROLET CHEVY C2 • 2009 • 2P GRIS METÁLICO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp
                        ),
                        color = palette.textPrimary
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Control único compacto de cambio de vista (3/4 -> Frente -> Lateral -> Trasera)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.elevatedSurface)
                                .border(1.dp, palette.accent.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { chevyRenderer.cycleCameraPreset() }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Cambiar vista",
                                    tint = palette.accent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = chevyState.activePreset.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = palette.textPrimary
                                )
                            }
                        }

                        // Botón Auto-Rotación 360°
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (chevyState.isAutoRotating) palette.accent.copy(alpha = 0.16f)
                                    else palette.elevatedSurface
                                )
                                .border(
                                    1.dp,
                                    if (chevyState.isAutoRotating) palette.accent else palette.border,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { chevyRenderer.setAutoRotate(!chevyState.isAutoRotating) }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ViewInAr,
                                    contentDescription = "Auto-rotación",
                                    tint = if (chevyState.isAutoRotating) palette.accent else palette.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (chevyState.isAutoRotating) "AUTO 360°: ON" else "AUTO 360°: OFF",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (chevyState.isAutoRotating) palette.accent else palette.textSecondary
                                )
                            }
                        }

                        IconButton(onClick = { chevyRenderer.onZoom(1.15f) }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Acercar", tint = palette.textPrimary)
                        }
                        IconButton(onClick = { chevyRenderer.onZoom(0.85f) }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Alejar", tint = palette.textPrimary)
                        }
                        IconButton(onClick = { chevyRenderer.resetCamera() }) {
                            Icon(Icons.Default.RestartAlt, contentDescription = "Reiniciar vista inicial", tint = palette.textPrimary)
                        }
                    }
                }

                // Viewport OpenGL ES 3.0 con modelo glTF 2.0 real ampliado
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.background)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(20.dp))
                ) {
                    ChevyGlbStudioView(
                        state = chevyState,
                        onRotate = { deltaYaw, deltaPitch ->
                            if (chevyState.isAutoRotating) {
                                chevyRenderer.setAutoRotate(false)
                            }
                            chevyRenderer.onRotate(deltaYaw, deltaPitch)
                        },
                        onZoom = { scaleDelta ->
                            chevyRenderer.onZoom(scaleDelta)
                        },
                        onUserTouchInteraction = {
                            if (chevyState.isAutoRotating) {
                                chevyRenderer.setAutoRotate(false)
                            }
                        },
                        lowriderEnabled = lowriderEnabled,
                        modifier = Modifier.fillMaxSize()
                    )

                    Text(
                        text = "${chevyState.activePreset.label} • Yaw: ${chevyState.rotationYaw.toInt()}° | Pitch: ${chevyState.rotationPitch.toInt()}° | Zoom: ${"%.1f".format(chevyState.zoomLevel)}x",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // PANEL LATERAL: SIMULADOR DE EFECTOS POR VELOCIDAD CON HISTÉRESIS
        AutomotiveCard(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SIMULADOR DE EFECTOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary
                )

                Column {
                    Text(
                        text = "Velocidad Simulada: ${simulatedSpeed.toInt()} km/h",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = simulatedSpeed,
                        onValueChange = { newSpeed ->
                            simulatedSpeed = newSpeed
                            val nextMode = SpeedVisualMode.fromSpeed(newSpeed, chevyState.visualMode)
                            chevyRenderer.setVisualMode(nextMode)
                        },
                        valueRange = 0f..140f,
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent,
                            activeTrackColor = palette.accent,
                            inactiveTrackColor = palette.background
                        )
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedVisualMode.entries.forEach { mode ->
                        val isActive = chevyState.visualMode == mode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isActive) palette.elevatedSurface else palette.background,
                            border = BorderStroke(
                                1.dp,
                                if (isActive) palette.accent else palette.borderSubtle
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${mode.title} (${mode.minSpeed.toInt()}–${if (mode.maxSpeed > 500f) "+" else mode.maxSpeed.toInt().toString()} km/h)",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = if (isActive) palette.accent else palette.textPrimary
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = palette.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

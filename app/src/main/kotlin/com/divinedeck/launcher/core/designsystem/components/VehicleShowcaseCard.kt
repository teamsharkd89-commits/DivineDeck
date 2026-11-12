package com.divinedeck.launcher.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.garage3d.data.Chevy3DContractImpl
import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import com.divinedeck.launcher.feature.garage3d.ui.ChevyGlbStudioView
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus

/**
 * Escenario Central Protagonista 3D del Vehículo + Velocidad Digital Inferior (Fase 3.1).
 *
 * Refinamiento visual y ergonómico del visor 3D:
 * - Área de renderizado OpenGL ES 3.0 ampliada al 100% del contenedor para dar máxima presencia
 *   al Chevrolet Chevy C2 2009 (Hatchback 2 puertas, gris metálico).
 * - Encabezado superior ultracompacto en una sola línea elegante.
 * - Único control cíclico de cambio de vista (`VISTA: 3/4` -> `VISTA: FRENTE` -> `VISTA: LATERAL` -> `VISTA: TRASERA`)
 *   con transición suave de cámara, conservando rotación táctil 360° y zoom pellizco.
 * - Indicador digital de velocidad integrado limpiamente en la base del visor.
 */
@Composable
fun VehicleShowcaseCard(
    currentSpeedKmh: Float,
    isSpeedReliable: Boolean = false,
    isGpsAvailable: Boolean = isSpeedReliable,
    gpsStatus: GpsStatus = if (isSpeedReliable) GpsStatus.CONNECTED else GpsStatus.NOT_AVAILABLE,
    onGpsStatusClick: (() -> Unit)? = null,
    onOpenGarage3D: () -> Unit,
    modifier: Modifier = Modifier,
    chevyRenderer: Chevy3DRenderer = remember { Chevy3DContractImpl() },
    /**
     * Oculta el indicador digital de velocidad de la base del visor.
     * El layout Panorámica lo pone en false porque la velocidad vive en
     * su propia tarjeta analógica ([SpeedGaugeCard]).
     */
    showSpeedIndicator: Boolean = true
) {
    val palette = DivineDeckTheme.palette
    val chevyState by chevyRenderer.modelState.collectAsState()

    // Sincronización continua de velocidad GPS real y fiabilidad con el motor de efectos 3D (Fase 4.1A / 4.1A.1)
    LaunchedEffect(currentSpeedKmh, isSpeedReliable) {
        chevyRenderer.onSpeedUpdated(currentSpeedKmh, isSpeedReliable)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        palette.surface.copy(alpha = 0.90f),
                        palette.background
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.55f),
                        palette.borderSubtle.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        // El fondo animado del estudio vive DENTRO de la escena OpenGL
        // (ChevyGlbStudioView): el GLSurfaceView es opaco y tapa cualquier
        // fondo de Compose. Ver drawAnimatedBackground en el renderer.
        // 1. ÁREA DE RENDERIZADO 3D AMPLIADA AL 100% DEL VISOR (Protagonismo absoluto del vehículo)
        ChevyStudioViewport(
            chevyRenderer = chevyRenderer,
            modifier = Modifier.fillMaxSize()
        )

        // 2. ENCABEZADO SUPERIOR EN UNA LÍNEA COMPACTA Y ELEGANTE + CONTROL ÚNICO DE CAMBIO DE VISTA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 10.dp, top = 6.dp, end = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Identidad del vehículo en una sola línea compacta (sin salto de línea ni invasión vertical)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.background.copy(alpha = 0.62f))
                    .border(0.6.dp, palette.borderSubtle.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp)
            ) {
                Text(
                    text = "CHEVROLET CHEVY C2",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp
                    ),
                    color = palette.textPrimary
                )
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .background(palette.accent, CircleShape)
                )
                Text(
                    text = "2009 • 2P GRIS METÁLICO",
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp
                    ),
                    color = palette.textSecondary
                )
            }

            // Controles compactos: Único selector cíclico de vista (3/4 -> Frente -> Lateral -> Trasera), Auto 360°/Manual, Reset y Estudio
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Control único compacto de cambio de vista (Parte B)
                StudioChipButton(
                    label = chevyState.activePreset.shortLabel,
                    isActive = !chevyState.isAutoRotating,
                    onClick = { chevyRenderer.cycleCameraPreset() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Cambiar vista de cámara (${chevyState.activePreset.label})",
                            tint = if (!chevyState.isAutoRotating) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                )

                // Toggle de Auto-Rotación 360°
                StudioChipButton(
                    label = if (chevyState.isAutoRotating) "AUTO 360°" else "MANUAL",
                    isActive = chevyState.isAutoRotating,
                    onClick = { chevyRenderer.setAutoRotate(!chevyState.isAutoRotating) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ViewInAr,
                            contentDescription = "Alternar rotación automática",
                            tint = if (chevyState.isAutoRotating) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                )

                // Volver a la vista inicial (Perspectiva 3/4)
                StudioIconPill(
                    icon = Icons.Default.RestartAlt,
                    contentDescription = "Vista inicial",
                    onClick = { chevyRenderer.resetCamera() }
                )

                // Acceso al Estudio / Garaje 3D
                StudioChipButton(
                    label = "ESTUDIO",
                    isActive = false,
                    onClick = onOpenGarage3D,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ViewInAr,
                            contentDescription = "Garaje 3D",
                            tint = palette.accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                )
            }
        }

        // 3. INDICADOR DIGITAL DE VELOCIDAD CENTRADO EN LA PARTE INFERIOR
        // (oculto en el layout Panorámica: la velocidad vive en SpeedGaugeCard)
        if (showSpeedIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            ) {
                DigitalSpeedIndicator(
                    speedKmh = if (gpsStatus == GpsStatus.CONNECTED || gpsStatus == GpsStatus.WEAK_SIGNAL) currentSpeedKmh else 0f,
                    gpsStatus = gpsStatus,
                    onStatusClick = onGpsStatusClick
                )
            }
        }

        // 4. SELECTOR TEMPORAL DE DEPURACIÓN (Exclusivo en compilaciones DEBUG - Fase 4.1B)
        if (com.divinedeck.launcher.BuildConfig.DEBUG) {
            DebugSpeedEffectsSelector(
                activeMode = chevyState.debugVisualModeOverride ?: chevyState.visualMode,
                isOverrideActive = chevyState.debugVisualModeOverride != null || chevyState.debugSimulatedSpeedKmh != null,
                simulatedSpeedKmh = chevyState.debugSimulatedSpeedKmh,
                onSelectMode = { mode ->
                    chevyRenderer.setDebugVisualMode(mode)
                },
                onSelectSpeed = { speed ->
                    chevyRenderer.setDebugSimulatedSpeed(speed)
                },
                onResetToGps = {
                    chevyRenderer.setDebugVisualMode(null)
                    chevyRenderer.setDebugSimulatedSpeed(null)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 8.dp)
            )
        }
    }
}

/**
 * Contenedor de superficie completa para el renderizador OpenGL ES 3.0 del Chevrolet Chevy C2 2009.
 */
@Composable
fun ChevyStudioViewport(
    modifier: Modifier = Modifier,
    chevyRenderer: Chevy3DRenderer = remember { Chevy3DContractImpl() }
) {
    val chevyState by chevyRenderer.modelState.collectAsState()

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        ChevyGlbStudioView(
            state = chevyState,
            onRotate = { dYaw, dPitch ->
                if (chevyState.isAutoRotating) {
                    chevyRenderer.setAutoRotate(false)
                }
                chevyRenderer.onRotate(dYaw, dPitch)
            },
            onZoom = { scale ->
                chevyRenderer.onZoom(scale)
            },
            onUserTouchInteraction = {
                if (chevyState.isAutoRotating) {
                    chevyRenderer.setAutoRotate(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun StudioChipButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val palette = DivineDeckTheme.palette
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) palette.accent.copy(alpha = 0.18f)
                else palette.elevatedSurface.copy(alpha = 0.82f)
            )
            .border(
                width = 0.8.dp,
                color = if (isActive) palette.accent else palette.border,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                ),
                color = if (isActive) palette.accent else palette.textSecondary
            )
        }
    }
}

@Composable
private fun StudioIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val palette = DivineDeckTheme.palette
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.elevatedSurface.copy(alpha = 0.82f))
            .border(0.8.dp, palette.border, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = palette.textPrimary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Indicador Digital de Velocidad Minimalista (Fase 02.1 / Fase 03 / Fase 3.1 / Fase 4.0).
 * Situado debajo del vehículo en una cápsula translúcida discreta para no restar área al visor 3D.
 * Muestra el formato digital textual "0 KM/H" y los 5 estados visuales oficiales de GPS.
 */
@Composable
private fun DigitalSpeedIndicator(
    speedKmh: Float,
    gpsStatus: GpsStatus,
    onStatusClick: (() -> Unit)? = null
) {
    val palette = DivineDeckTheme.palette

    val statusDotColor = when (gpsStatus) {
        GpsStatus.CONNECTED -> palette.statusPositive
        GpsStatus.WEAK_SIGNAL -> palette.accentSecondary
        GpsStatus.SEARCHING -> palette.accent
        GpsStatus.UNRELIABLE_SPEED -> palette.statusAlert
        GpsStatus.NOT_AVAILABLE -> palette.textMuted
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.background.copy(alpha = 0.58f))
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "%.0f".format(speedKmh.coerceAtLeast(0f)),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.5).sp
                ),
                color = palette.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "KM/H",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.8.sp
                ),
                color = palette.accent,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = if (onStatusClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStatusClick
                )
            } else {
                Modifier
            }
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(statusDotColor, CircleShape)
            )
            Text(
                text = gpsStatus.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                ),
                color = palette.textSecondary
            )
        }
    }
}

/**
 * Selector temporal de depuración para probar los 4 modos de Speed Visual Effects en el vehículo
 * mientras permanece estacionado (Fase 4.1A.1).
 *
 * Exclusivo de compilaciones DEBUG (completamente eliminado en RELEASE por BuildConfig.DEBUG y R8).
 */
@Composable
private fun DebugSpeedEffectsSelector(
    activeMode: SpeedVisualMode,
    isOverrideActive: Boolean,
    simulatedSpeedKmh: Float?,
    onSelectMode: (SpeedVisualMode) -> Unit,
    onSelectSpeed: (Float?) -> Unit,
    onResetToGps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = DivineDeckTheme.palette

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.background.copy(alpha = 0.88f))
            .border(
                width = 0.8.dp,
                color = if (isOverrideActive) palette.accent.copy(alpha = 0.80f) else palette.borderSubtle.copy(alpha = 0.50f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Fila 1: Selector de Modo Visual
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "MODO:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = if (isOverrideActive) palette.accent else palette.textSecondary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            val modes = listOf(
                SpeedVisualMode.NORMAL,
                SpeedVisualMode.CORREDOR,
                SpeedVisualMode.ADRENALINA,
                SpeedVisualMode.COHETE
            )

            modes.forEach { mode ->
                val isSelected = activeMode == mode
                val label = when (mode) {
                    SpeedVisualMode.NORMAL -> "NORM"
                    SpeedVisualMode.CORREDOR -> "CORR"
                    SpeedVisualMode.ADRENALINA -> "ADREN"
                    SpeedVisualMode.COHETE -> "COHETE"
                }
                val modeColor = when (mode) {
                    SpeedVisualMode.NORMAL -> Color(0xFF0088FF)
                    SpeedVisualMode.CORREDOR -> Color(0xFFFFC800)
                    SpeedVisualMode.ADRENALINA -> Color(0xFFFF2200)
                    SpeedVisualMode.COHETE -> Color(0xFFFF0800)
                }

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) modeColor.copy(alpha = 0.28f)
                            else palette.elevatedSurface.copy(alpha = 0.50f)
                        )
                        .border(
                            width = 0.6.dp,
                            color = if (isSelected) modeColor else palette.borderSubtle.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectMode(mode) }
                        )
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) modeColor else palette.textPrimary
                    )
                }
            }

            if (isOverrideActive) {
                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.elevatedSurface.copy(alpha = 0.50f))
                        .border(
                            width = 0.6.dp,
                            color = palette.borderSubtle.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onResetToGps
                        )
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "GPS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = palette.textSecondary
                    )
                }
            }
        }

        // Fila 2: Velocidad Simulada para probar giro de ruedas y partículas en reposo (Fase 4.1B)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "VEL:",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                ),
                color = if (simulatedSpeedKmh != null) palette.accent else palette.textSecondary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            val speedPresets = listOf(0f, 45f, 70f, 90f, 120f, 160f)
            speedPresets.forEach { spd ->
                val isSelected = simulatedSpeedKmh != null && simulatedSpeedKmh == spd
                val label = "${spd.toInt()}"

                Box(
                    modifier = Modifier
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (isSelected) palette.accent.copy(alpha = 0.28f)
                            else palette.elevatedSurface.copy(alpha = 0.50f)
                        )
                        .border(
                            width = 0.6.dp,
                            color = if (isSelected) palette.accent else palette.borderSubtle.copy(alpha = 0.40f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectSpeed(spd) }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isSelected) palette.accent else palette.textPrimary
                    )
                }
            }
        }
    }
}

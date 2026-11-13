package com.divinedeck.launcher.feature.cockpit.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.components.AutomotiveBottomDock
import com.divinedeck.launcher.core.designsystem.components.CockpitWidgetColumn
import com.divinedeck.launcher.core.designsystem.components.SpeedGaugeCard
import com.divinedeck.launcher.core.designsystem.components.VehicleShowcaseCard
import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.navigation.DivineDeckRoute
import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedometerRepository
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.voice.data.VoiceAssistant
import com.divinedeck.launcher.feature.voice.data.VoiceState
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager
import kotlinx.coroutines.delay

/**
 * Pantalla Principal Cockpit (Fase 03 — Chevrolet Chevy C2 2009 3D Vehicle Experience).
 *
 * Dos distribuciones seleccionables en Ajustes > Distribución (Fase 06.11):
 * - ORIGINAL: el vehículo es el protagonista. Composición horizontal para
 *   1340 × 800 sin scroll vertical: vehículo 3D centrado a la izquierda con
 *   lectura digital de velocidad debajo, y columna derecha con dos espacios
 *   verticales configurables para widgets Android.
 * - PANORAMICA: el vehículo como pancarta ancha arriba ocupando todo el
 *   ancho; abajo, tres tarjetas en fila: velocímetro analógico, widget
 *   superior y widget inferior (mockup Panorámico aprobado por Daniel).
 *   El dock de navegación va lateral, a la derecha del modelo 3D (en vez
 *   del dock inferior), para que la pancarta tenga más alto, el modelo 3D
 *   no se corte y las tres tarjetas bajen.
 * Ambas distribuciones usan los mismos componentes y funciones; solo cambia
 * la distribución y el orden.
 */
@Composable
fun CockpitScreen(
    gpsRepository: GpsSpeedometerRepository,
    chevyRenderer: Chevy3DRenderer,
    mediaRepository: MediaControllerRepository,
    navRepository: NavigationLauncherRepository,
    widgetManager: AndroidWidgetHostManager,
    onNavigate: (DivineDeckRoute) -> Unit,
    onOpenMusicPlayer: (() -> Unit)? = null,
    onGpsStatusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    layout: CockpitLayout = CockpitLayout.ORIGINAL,
    currentRoute: DivineDeckRoute = DivineDeckRoute.COCKPIT,
    lowriderEnabled: Boolean = false,
    voiceAssistant: VoiceAssistant? = null
) {
    val speedReading by gpsRepository.speedReading.collectAsState()
    val gpsStatus by gpsRepository.gpsStatus.collectAsState()
    val displaySpeedKmh =
        if (gpsStatus == GpsStatus.CONNECTED || gpsStatus == GpsStatus.WEAK_SIGNAL) {
            speedReading.currentSpeedKmh
        } else {
            0f
        }

    if (layout == CockpitLayout.PANORAMICA) {
        // DISTRIBUCIÓN PANORÁMICA: pancarta ancha arriba + fila de 3 tarjetas
        // abajo + dock lateral a la derecha del modelo 3D. Sin dock inferior,
        // la pancarta gana alto (el 3D ya no se corta) y las tarjetas bajan.
        // El Box exterior solo existe para anclar el botón del micrófono
        // flotante sin tocar la distribución aprobada.
        Box(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ZONA 1: VEHÍCULO 3D COMO PANCARTA ANCHA (sin velocímetro dentro:
                // la velocidad vive en su propia tarjeta analógica de la fila inferior)
                VehicleShowcaseCard(
                    currentSpeedKmh = speedReading.currentSpeedKmh,
                    isSpeedReliable = speedReading.isReliable,
                    isGpsAvailable = speedReading.isReliable,
                    gpsStatus = gpsStatus,
                    onGpsStatusClick = onGpsStatusClick,
                    onOpenGarage3D = { onNavigate(DivineDeckRoute.GARAGE_3D) },
                    chevyRenderer = chevyRenderer,
                    showSpeedIndicator = false,
                    lowriderEnabled = lowriderEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.4f)
                )

                // ZONA 2: FILA INFERIOR — VELOCÍMETRO + WIDGET SUPERIOR + WIDGET INFERIOR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SpeedGaugeCard(
                        speedKmh = displaySpeedKmh,
                        gpsStatus = gpsStatus,
                        onStatusClick = onGpsStatusClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    CockpitWidgetColumn(
                        widgetManager = widgetManager,
                        mediaRepository = mediaRepository,
                        onOpenMusicPlayer = onOpenMusicPlayer ?: { onNavigate(DivineDeckRoute.MUSIC) },
                        onNavigateToWidgets = { onNavigate(DivineDeckRoute.WIDGETS) },
                        horizontal = true,
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxHeight()
                    )
                }
            }

            // ZONA 3: DOCK LATERAL DERECHO (mismos 7 apartados, misma navegación)
            AutomotiveBottomDock(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                vertical = true,
                modifier = Modifier.fillMaxHeight()
            )
            }

            VoiceMicButton(
                voiceAssistant = voiceAssistant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp)
            )
        }
    } else {
        // DISTRIBUCIÓN ORIGINAL (sin cambios). El Box exterior solo existe
        // para anclar el botón del micrófono flotante sin tocar la
        // distribución aprobada.
        Box(modifier = modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
            // ZONA 2: VEHÍCULO 3D PROTAGONISTA + VELOCIDAD DIGITAL DEBAJO
            VehicleShowcaseCard(
                currentSpeedKmh = speedReading.currentSpeedKmh,
                isSpeedReliable = speedReading.isReliable,
                isGpsAvailable = speedReading.isReliable,
                gpsStatus = gpsStatus,
                onGpsStatusClick = onGpsStatusClick,
                onOpenGarage3D = { onNavigate(DivineDeckRoute.GARAGE_3D) },
                chevyRenderer = chevyRenderer,
                lowriderEnabled = lowriderEnabled,
                modifier = Modifier
                    .weight(1.75f)
                    .fillMaxHeight()
            )

            // ZONA 3: COLUMNA DERECHA CON DOS ESPACIOS VERTICALES PARA WIDGETS
            CockpitWidgetColumn(
                widgetManager = widgetManager,
                mediaRepository = mediaRepository,
                onOpenMusicPlayer = onOpenMusicPlayer ?: { onNavigate(DivineDeckRoute.MUSIC) },
                onNavigateToWidgets = { onNavigate(DivineDeckRoute.WIDGETS) },
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
            )
            }

            VoiceMicButton(
                voiceAssistant = voiceAssistant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Botón flotante del micrófono del asistente de voz (versión botón:
 * se escucha solo al tocarlo). Anclado en la esquina superior izquierda
 * del Cockpit para no tapar el velocímetro ni el dock.
 *
 * - Escuchando: pulso de escala + borde de acento y texto "Escuchando…".
 * - Escuchado: muestra "Escuché: <texto>" unos segundos.
 * - Error: muestra el mensaje unos segundos.
 * - Tocar mientras escucha detiene la escucha.
 */
@Composable
private fun VoiceMicButton(
    voiceAssistant: VoiceAssistant?,
    modifier: Modifier = Modifier
) {
    if (voiceAssistant == null) return
    val palette = DivineDeckTheme.palette
    val voiceState by voiceAssistant.state.collectAsState()

    // El texto de Heard/Error se muestra unos segundos y luego se oculta.
    var showStatus by remember { mutableStateOf(false) }
    LaunchedEffect(voiceState) {
        when (voiceState) {
            is VoiceState.Heard, is VoiceState.Error -> {
                showStatus = true
                delay(4000)
                showStatus = false
            }
            else -> {
                showStatus = false
            }
        }
    }

    val isListening = voiceState is VoiceState.Listening ||
        voiceState is VoiceState.DownloadingModel
    val pulse = rememberInfiniteTransition(label = "voiceMicPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.12f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceMicPulseScale"
    )

    val statusText = when (val s = voiceState) {
        VoiceState.Listening -> "Escuchando…"
        is VoiceState.DownloadingModel -> if (s.percent >= 0) {
            "Descargando modelo de voz… ${s.percent}%"
        } else {
            "Descargando modelo de voz…"
        }
        is VoiceState.Heard -> if (showStatus) "Escuché: ${s.text}" else null
        is VoiceState.Error -> if (showStatus) s.message else null
        VoiceState.Idle -> null
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(palette.accent)
                .then(
                    if (isListening) {
                        Modifier.border(3.dp, palette.accentGlow, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable {
                    if (isListening) {
                        voiceAssistant.stopListening()
                    } else {
                        voiceAssistant.startListening()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = if (isListening) {
                    "Detener escucha"
                } else {
                    "Hablar con DivineDeck"
                },
                tint = palette.onAccent,
                modifier = Modifier.size(30.dp)
            )
        }

        if (statusText != null) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.elevatedSurface)
                    .border(1.dp, palette.accent, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

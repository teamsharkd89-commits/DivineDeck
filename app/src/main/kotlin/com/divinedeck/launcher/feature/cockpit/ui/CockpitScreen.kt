package com.divinedeck.launcher.feature.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.components.AutomotiveBottomDock
import com.divinedeck.launcher.core.designsystem.components.CockpitWidgetColumn
import com.divinedeck.launcher.core.designsystem.components.SpeedGaugeCard
import com.divinedeck.launcher.core.designsystem.components.VehicleShowcaseCard
import com.divinedeck.launcher.core.designsystem.theme.CockpitLayout
import com.divinedeck.launcher.core.navigation.DivineDeckRoute
import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedometerRepository
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager

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
    lowriderEnabled: Boolean = false
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
        Row(
            modifier = modifier
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
    } else {
        // DISTRIBUCIÓN ORIGINAL (sin cambios)
        Row(
            modifier = modifier
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
    }
}

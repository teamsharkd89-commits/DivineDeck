package com.divinedeck.launcher.feature.cockpit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.components.CockpitWidgetColumn
import com.divinedeck.launcher.core.designsystem.components.VehicleShowcaseCard
import com.divinedeck.launcher.core.navigation.DivineDeckRoute
import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedometerRepository
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import com.divinedeck.launcher.feature.music.domain.MediaControllerRepository
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.widgets.domain.AndroidWidgetHostManager

/**
 * Pantalla Principal Cockpit (Fase 03 — Chevrolet Chevy C2 2009 3D Vehicle Experience).
 *
 * Filosofía: EL VEHÍCULO ES EL PROTAGONISTA.
 * Composición horizontal equilibrada para 1340 × 800 sin scroll vertical:
 * - Zona Central Protagonista: Modelo 3D real (`chevy_c2_2009.glb`) del Chevrolet Chevy C2 2009
 *   (2 puertas, gris metálico) renderizado en tiempo real con OpenGL ES 3.0 PBR, rotación táctil 360°,
 *   zoom, vistas rápidas, auto-rotación activable/desactivable y lectura digital de velocidad debajo.
 * - Columna Derecha: Dos espacios verticales equilibrados y configurables para widgets Android
 *   (`WIDGET SUPERIOR` y `WIDGET INFERIOR`).
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
    modifier: Modifier = Modifier
) {
    val speedReading by gpsRepository.speedReading.collectAsState()
    val gpsStatus by gpsRepository.gpsStatus.collectAsState()

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

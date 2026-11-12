package com.divinedeck.launcher.feature.navcenter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository

@Composable
fun NavCenterScreen(
    navRepository: NavigationLauncherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = DivineDeckTheme.palette

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // LANZADOR PRINCIPAL DE GOOGLE MAPS
        AutomotiveCard(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        text = "CENTRO DE NAVEGACIÓN",
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Integración directa con Google Maps y mapas sin conexión para el Chevy C2.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.elevatedSurface,
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Mapas",
                            tint = palette.accent,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Google Maps Native Intent",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lanza la aplicación de navegación instalada sin consumo de APIs comerciales ni costos por licencia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AutomotivePrimaryButton(
                            onClick = { navRepository.launchGoogleMaps(context) }
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, tint = palette.onAccent)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir Google Maps", color = palette.onAccent)
                        }
                    }
                }

                Text(
                    text = "• Compatible con mapas descargados sin conexión para rutas sin señal celular.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.statusPositive
                )
            }
        }

        // DESTINOS RÁPIDOS AUTOMOTRICES
        AutomotiveCard(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "DESTINOS RÁPIDOS",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val destinations = listOf(
                        Triple("Gasolinera Cercana", "Buscar gasolineras en la ruta", Icons.Default.LocalGasStation),
                        Triple("Taller Mecánico", "Servicio automotriz y refaccionarias", Icons.Default.Build),
                        Triple("Casa", "Ruta directa al hogar", Icons.Default.Home),
                        Triple("Trabajo", "Ruta de traslado laboral", Icons.Default.Work)
                    )

                    destinations.forEach { (title, subtitle, icon) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = palette.elevatedSurface,
                            border = BorderStroke(1.dp, palette.borderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { navRepository.launchNavigationTo(context, title) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

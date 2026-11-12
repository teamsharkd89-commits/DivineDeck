package com.divinedeck.launcher.feature.navcenter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.components.AutomotiveSecondaryButton
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.rememberNavigationMapState
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import kotlinx.coroutines.delay
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.milliseconds

/**
 * Centro de Navegación con el mapa EMBEBIDO dentro de DivineDeck:
 * mapa de OpenFreeMap (OpenStreetMap, gratis), búsqueda con Photon,
 * gasolineras y talleres con Overpass, y navegación guiada con flechas
 * y voz con Ferrostar + Valhalla (servidor gratuito FOSSGIS).
 *
 * Sin llaves, sin cuentas, sin facturación. El botón de Google Maps queda
 * como alternativa externa (por ejemplo, para tráfico en vivo).
 */
@Composable
fun NavCenterScreen(
    navRepository: NavigationLauncherRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: NavCenterViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = DivineDeckTheme.palette
    val mapState = rememberNavigationMapState()

    // Cámara inicial: Tepic, Nayarit.
    LaunchedEffect(Unit) {
        mapState.cameraState.position = mapState.cameraState.position.copy(
            target = Position(
                longitude = NavCenterViewModel.TEPIC_CENTER.longitude,
                latitude = NavCenterViewModel.TEPIC_CENTER.latitude
            ),
            zoom = 13.0
        )
    }

    // Movimientos de cámara pedidos por el ViewModel (búsqueda / POI / Casa / Trabajo).
    LaunchedEffect(uiState.cameraTargetNonce) {
        val target = uiState.cameraTarget ?: return@LaunchedEffect
        mapState.cameraMode = NavigationCameraMode.FREE
        mapState.cameraState.animateTo(
            finalPosition = mapState.cameraState.position.copy(
                target = Position(longitude = target.longitude, latitude = target.latitude),
                zoom = 15.0
            ),
            duration = 800.milliseconds
        )
    }

    // El mensaje de estado se esconde solo a los pocos segundos.
    if (uiState.statusMessage != null) {
        LaunchedEffect(uiState.statusMessage) {
            delay(3_500)
            viewModel.dismissStatus()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        DynamicallyOrientingNavigationView(
            modifier = Modifier.fillMaxSize(),
            baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
            navigationMapState = mapState,
            viewModel = viewModel.navigationViewModel,
            showDefaultPuck = true,
            onTapExit = { viewModel.stopNavigation() },
            onMapClick = { coord, _ ->
                viewModel.onMapTap(NavLatLng(latitude = coord.lat, longitude = coord.lng))
                NavigationMapClickResult.Consume
            },
            onMapLongClick = { coord, _ ->
                viewModel.onMapTap(NavLatLng(latitude = coord.lat, longitude = coord.lng))
                NavigationMapClickResult.Consume
            },
            mapContent = {
                // Destino seleccionado (pin grande con el color del tema).
                uiState.selectedPlace?.let { place ->
                    PlaceMarker(
                        layerId = "marker-sel-${place.id}",
                        place = place,
                        color = palette.accent,
                        radiusDp = 11f
                    )
                }
                // Gasolineras / talleres cercanos.
                uiState.poiResults.forEach { poi ->
                    PlaceMarker(
                        layerId = "marker-poi-${poi.id}",
                        place = poi,
                        color = if (poi.kind == PoiKind.FUEL) Color(0xFFFF8A00) else Color(0xFF9C27B0),
                        radiusDp = 8f
                    )
                }
                // Resultados de búsqueda.
                uiState.searchResults.forEach { result ->
                    PlaceMarker(
                        layerId = "marker-search-${result.id}",
                        place = result,
                        color = palette.accentSecondary,
                        radiusDp = 8f
                    )
                }
            }
        )

        if (!uiState.isNavigating) {
            // --- Barra superior: búsqueda + destinos rápidos ---
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .width(460.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Buscar en Tepic… calle, colonia o lugar", color = palette.textMuted)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = palette.accent)
                    },
                    trailingIcon = {
                        if (uiState.isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = palette.accent,
                                strokeWidth = 2.dp
                            )
                        } else if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = viewModel::clearSearch) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = palette.textSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface.copy(alpha = 0.95f),
                        unfocusedContainerColor = palette.surface.copy(alpha = 0.95f),
                        focusedBorderColor = palette.accent,
                        unfocusedBorderColor = palette.border,
                        focusedTextColor = palette.textPrimary,
                        unfocusedTextColor = palette.textPrimary,
                        cursorColor = palette.accent
                    )
                )

                // Resultados de búsqueda.
                if (uiState.searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = palette.surface.copy(alpha = 0.97f),
                        border = BorderStroke(1.dp, palette.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .padding(vertical = 4.dp)
                        ) {
                            items(uiState.searchResults, key = { it.id }) { result ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onSearchResultSelected(result) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(result.name, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    if (result.detail.isNotBlank()) {
                                        Text(result.detail, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Chips: gasolinera, taller, casa, trabajo.
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NavChip(
                        label = "Gasolinera",
                        icon = Icons.Default.LocalGasStation,
                        selected = uiState.activePoiKind == PoiKind.FUEL,
                        loading = uiState.isLoadingPois && uiState.activePoiKind == PoiKind.FUEL,
                        onClick = { viewModel.togglePois(PoiKind.FUEL) }
                    )
                    NavChip(
                        label = "Taller",
                        icon = Icons.Default.Build,
                        selected = uiState.activePoiKind == PoiKind.REPAIR,
                        loading = uiState.isLoadingPois && uiState.activePoiKind == PoiKind.REPAIR,
                        onClick = { viewModel.togglePois(PoiKind.REPAIR) }
                    )
                    NavChip(
                        label = "Casa",
                        icon = Icons.Default.Home,
                        selected = false,
                        loading = false,
                        onClick = viewModel::goHome
                    )
                    NavChip(
                        label = "Trabajo",
                        icon = Icons.Default.Work,
                        selected = false,
                        loading = false,
                        onClick = viewModel::goWork
                    )
                }
                if (uiState.poiResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${uiState.poiResults.size} ${if (uiState.activePoiKind == PoiKind.FUEL) "gasolineras" else "talleres"} cerca — toca una en el mapa",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                        modifier = Modifier
                            .background(palette.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // --- Alternativa externa: Google Maps (tráfico en vivo) ---
            AutomotiveSecondaryButton(
                onClick = { navRepository.launchGoogleMaps(context) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Map, contentDescription = null, tint = palette.accent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google Maps", color = palette.textPrimary)
            }

            // --- Mensaje de estado (guardado, avisos) ---
            uiState.statusMessage?.let { message ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.surface.copy(alpha = 0.97f),
                    border = BorderStroke(1.dp, palette.border),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clickable { viewModel.dismissStatus() }
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            // --- Hoja del destino seleccionado ---
            uiState.selectedPlace?.let { place ->
                AutomotiveCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .width(600.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                tint = palette.accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(place.name, style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                                val address = when {
                                    uiState.isResolvingAddress -> "Buscando dirección…"
                                    uiState.selectedAddress?.isNotBlank() == true -> uiState.selectedAddress
                                    else -> place.detail
                                }
                                if (!address.isNullOrBlank()) {
                                    Text(address, style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                }
                            }
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = palette.textSecondary)
                            }
                        }
                        if (uiState.routeError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.routeError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.statusAlert
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AutomotivePrimaryButton(
                                onClick = { viewModel.startNavigation(place) },
                                enabled = !uiState.isCalculatingRoute
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, tint = palette.onAccent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (uiState.isCalculatingRoute) "Calculando…" else "Navegar",
                                    color = palette.onAccent
                                )
                            }
                            AutomotiveSecondaryButton(onClick = { viewModel.saveAsHome(place) }) {
                                Icon(Icons.Default.Home, contentDescription = null, tint = palette.accent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Casa", color = palette.textPrimary)
                            }
                            AutomotiveSecondaryButton(onClick = { viewModel.saveAsWork(place) }) {
                                Icon(Icons.Default.Work, contentDescription = null, tint = palette.accent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trabajo", color = palette.textPrimary)
                            }
                            AutomotiveSecondaryButton(
                                onClick = {
                                    navRepository.launchNavigationTo(
                                        context,
                                        "${place.latLng.latitude},${place.latLng.longitude}"
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = palette.accent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Google Maps", color = palette.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // --- Llegada al destino ---
        if (uiState.hasArrived && !uiState.isNavigating) {
            AutomotiveCard(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("¡Llegaste a tu destino!", style = MaterialTheme.typography.titleLarge, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        uiState.selectedPlace?.name ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    AutomotivePrimaryButton(onClick = viewModel::stopNavigation) {
                        Text("Terminar", color = palette.onAccent)
                    }
                }
            }
        }
    }
}

/** Pin circular en el mapa (capa de MapLibre Compose). */
@Composable
@MaplibreComposable
private fun PlaceMarker(
    layerId: String,
    place: NavPlace,
    color: Color,
    radiusDp: Float
) {
    val json = remember(place.id, place.latLng) {
        """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${place.latLng.longitude},${place.latLng.latitude}]},"properties":{}}]}"""
    }
    val source = rememberGeoJsonSource(GeoJsonData.JsonString(json))
    CircleLayer(
        id = layerId,
        source = source,
        color = const(color),
        radius = const(radiusDp.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(2.dp)
    )
}

/** Chip de destino rápido (gasolinera, taller, casa, trabajo). */
@Composable
private fun NavChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val palette = DivineDeckTheme.palette
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) palette.accent else palette.surface.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = if (selected) palette.onAccent else palette.accent,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) palette.onAccent else palette.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) palette.onAccent else palette.textPrimary
            )
        }
    }
}

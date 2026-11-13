package com.divinedeck.launcher.feature.navcenter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.divinedeck.launcher.BuildConfig
import com.divinedeck.launcher.core.designsystem.components.AutomotiveCard
import com.divinedeck.launcher.core.designsystem.components.AutomotivePrimaryButton
import com.divinedeck.launcher.core.designsystem.components.AutomotiveSecondaryButton
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.feature.navcenter.data.MapPackState
import com.divinedeck.launcher.feature.navcenter.data.RoutingPackState
import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import com.stadiamaps.ferrostar.composeui.config.NavigationViewComponentBuilder
import com.stadiamaps.ferrostar.composeui.config.VisualNavigationViewConfig
import com.stadiamaps.ferrostar.composeui.config.withInstructionsView
import com.stadiamaps.ferrostar.composeui.config.withProgressView
import com.stadiamaps.ferrostar.composeui.theme.DefaultInstructionRowTheme
import com.stadiamaps.ferrostar.composeui.theme.DefaultTripProgressViewTheme
import com.stadiamaps.ferrostar.composeui.theme.InstructionRowTheme
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewStyle
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewTheme
import com.stadiamaps.ferrostar.composeui.views.components.InstructionsView
import com.stadiamaps.ferrostar.composeui.views.components.TripProgressView
import com.stadiamaps.ferrostar.maplibreui.NavigationMapClickResult
import com.stadiamaps.ferrostar.maplibreui.NavigationMapPuckStyle
import com.stadiamaps.ferrostar.maplibreui.routeline.BorderedPolyline
import com.stadiamaps.ferrostar.maplibreui.routeline.RouteOverlayBuilder
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationMapState
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationCameraOptions
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

    // Opciones de cámara: la vista ya aplica el tilt de navegación (45°
    // Automotive) solo al navegar; en exploración el tilt es 0 por
    // construcción (browsingUser). Con destino seleccionado, el padding
    // inferior deja hueco para la hoja del destino.
    val cameraOptions = navigationCameraOptions()
    val effectiveCameraOptions = if (uiState.isNavigating) {
        cameraOptions
    } else {
        cameraOptions.copy(
            browsingPadding = PaddingValues(
                bottom = if (uiState.selectedPlace != null) 220.dp else 0.dp
            )
        )
    }
    val mapState = rememberNavigationMapState(navigationCameraOptions = effectiveCameraOptions)

    // Temas Ferrostar re-teñidos con la paleta DivineDeck, construidos una
    // sola vez por tema (no en cada fix GPS de 1 Hz). Los formateadores de
    // distancia/duración son los de Ferrostar por defecto (localizados).
    // Nota: el radio del banner (10 dp) y la píldora del progreso (50 dp)
    // los dibuja Ferrostar por dentro y el tema no los puede cambiar.
    val navViews = remember(palette) {
        val instructionTheme = object : InstructionRowTheme {
            override val distanceTextStyle: TextStyle
                @Composable get() = DefaultInstructionRowTheme.distanceTextStyle.copy(color = palette.accent)
            override val instructionTextStyle: TextStyle
                @Composable get() = DefaultInstructionRowTheme.instructionTextStyle.copy(color = palette.textPrimary)
            override val iconTintColor: Color
                @Composable get() = palette.accent
            override val backgroundColor: Color
                @Composable get() = palette.elevatedSurface
        }
        val progressTheme = object : TripProgressViewTheme {
            override val style: TripProgressViewStyle
                @Composable get() = TripProgressViewStyle.SIMPLIFIED
            override val measurementTextStyle: TextStyle
                @Composable get() = DefaultTripProgressViewTheme.measurementTextStyle.copy(color = palette.textPrimary)
            override val secondaryTextStyle: TextStyle
                @Composable get() = DefaultTripProgressViewTheme.secondaryTextStyle.copy(color = palette.textSecondary)
            // Botón de salida con fondo de acento: el icono va en onAccent
            // para seguir siendo legible (icono en acento sobre acento no
            // se vería). El botón de silencio lo dibuja Ferrostar con los
            // colores del tema Material de DivineDeck.
            override val exitIconColor: Color
                @Composable get() = palette.onAccent
            override val exitButtonBackgroundColor: Color
                @Composable get() = palette.accent
            override val backgroundColor: Color
                @Composable get() = palette.surface
        }
        NavigationViewComponentBuilder.Default()
            .withInstructionsView { viewModifier, navUiState ->
                navUiState.visualInstruction?.let { instructions ->
                    InstructionsView(
                        modifier = viewModifier,
                        instructions = instructions,
                        theme = instructionTheme,
                        remainingSteps = navUiState.remainingSteps,
                        distanceToNextManeuver = navUiState.progress?.distanceToNextManeuver
                    )
                }
            }
            .withProgressView { viewModifier, navUiState, onTapExit ->
                navUiState.progress?.let { progress ->
                    TripProgressView(
                        modifier = viewModifier,
                        theme = progressTheme,
                        progress = progress,
                        onTapExit = onTapExit
                    )
                }
            }
    }

    val puckStyle = remember(palette) {
        NavigationMapPuckStyle(
            dotFillColorCurrentLocation = palette.accent,
            dotFillColorOldLocation = palette.accent,
            accuracyStrokeColor = palette.accent,
            accuracyFillColor = palette.accent.copy(alpha = 0.16f),
            bearingColor = palette.accentSecondary
        )
    }

    val routeOverlay = remember(palette) {
        RouteOverlayBuilder { navUiState ->
            navUiState.routeGeometry?.let { geometry ->
                BorderedPolyline(
                    points = geometry,
                    color = palette.accent,
                    borderColor = palette.accentSecondary
                )
            }
        }
    }

    // Cámara inicial: Tepic, Nayarit. FREE antes de escribir la posición
    // directamente: en modo FOLLOW la vista de seguimiento la sobrescribe.
    LaunchedEffect(Unit) {
        mapState.cameraMode = NavigationCameraMode.FREE
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

    // Al arrancar la navegación, seguir al usuario con rumbo (centrado,
    // mapa girando con el heading). recenter() pone FOLLOW_USER_WITH_BEARING.
    LaunchedEffect(uiState.isNavigating) {
        if (uiState.isNavigating) mapState.recenter(isNavigating = true)
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
            navigationCameraOptions = effectiveCameraOptions,
            viewModel = viewModel.navigationViewModel,
            locationPuckStyle = puckStyle,
            // Sin zoom ni recenter de Ferrostar: el recenter es el botón
            // propio (estilo Automotive) y el silencio se mantiene.
            config = VisualNavigationViewConfig(showMute = true, showZoom = false, showRecenter = false),
            views = navViews,
            routeOverlayBuilder = routeOverlay,
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
                // Gasolineras / talleres cercanos. Radio 10 dp (antes 8):
                // a zoom de ciudad los círculos de 8 dp casi no se veían.
                // Color distinto por tipo: gasolinera = acento (ámbar),
                // taller = verde de estado (el accentSecondary es un dorado
                // casi igual al ámbar y no se distinguía). Borde blanco.
                uiState.poiResults.forEach { poi ->
                    PlaceMarker(
                        layerId = "marker-poi-${poi.id}",
                        place = poi,
                        color = if (poi.kind == PoiKind.FUEL) palette.accent else palette.statusPositive,
                        radiusDp = 10f
                    )
                }
                // Resultados de búsqueda.
                uiState.searchResults.forEach { result ->
                    PlaceMarker(
                        layerId = "marker-search-${result.id}",
                        place = result,
                        color = palette.textSecondary,
                        radiusDp = 8f
                    )
                }
            }
        )

        // Botón propio de recentrar: solo navegando y solo si el usuario
        // movió el mapa (la cámara ya no sigue al usuario).
        RecenterButton(
            mapState = mapState,
            isNavigating = uiState.isNavigating,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 132.dp)
        )

        // Chip de conectividad + nota de ruta offline: siempre visible,
        // también navegando (la columna de búsqueda se oculta al navegar).
        if (uiState.isNavigating) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ConnectivityChip(isOnline = uiState.isOnline)
                if (!uiState.isOnline && uiState.lastRouteOffline) {
                    Text(
                        text = "Ruta calculada sin internet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier
                            .background(Color(0xFFB45309).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (!uiState.isNavigating) {
            // --- Barra superior: búsqueda + destinos rápidos ---
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .width(460.dp)
            ) {
                // Chip de conectividad: siempre visible en esta pantalla.
                ConnectivityChip(isOnline = uiState.isOnline)
                // Número de build (mismo que el tag del release
                // debug-06.11-<n>): para confirmar qué build está
                // instalada al probar la búsqueda.
                Text(
                    text = if (BuildConfig.GITHUB_RUN_NUMBER > 0) {
                        "build ${BuildConfig.GITHUB_RUN_NUMBER}"
                    } else {
                        "build local"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted
                )
                Spacer(modifier = Modifier.height(8.dp))

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
                    shape = RoundedCornerShape(18.dp),
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
                    AutomotiveCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
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
                } else if (uiState.searchQuery.isNotBlank() && !uiState.isSearching) {
                    // Nunca dejar un hueco en blanco: error con reintento,
                    // o "sin resultados" cuando el servicio sí respondió.
                    Spacer(modifier = Modifier.height(8.dp))
                    AutomotiveCard(
                        contentPadding = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val error = uiState.searchError
                            if (error != null) {
                                Text(error, style = MaterialTheme.typography.bodyMedium, color = palette.textPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                AutomotiveSecondaryButton(onClick = viewModel::retrySearch) {
                                    Text("Reintentar", color = palette.textPrimary)
                                }
                            } else {
                                Text(
                                    "Sin resultados en Tepic para «${uiState.searchQuery}»",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textSecondary
                                )
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
                // Nota persistente del chip de POIs ("No encontré…",
                // "Mostrando guardadas…"): no se auto-descarta como el
                // mensaje de estado general, que desaparece a los 3.5 s.
                val poiMessage = uiState.poiMessage
                if (uiState.activePoiKind != null && poiMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = poiMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        modifier = Modifier
                            .background(palette.surface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                // Paquetes offline (mapa + rutas de Tepic): solo se muestra
                // mientras falte algo por descargar.
                OfflinePacksCard(
                    isOnline = uiState.isOnline,
                    mapPackState = uiState.mapPackState,
                    routingPackState = uiState.routingPackState,
                    onDownloadClick = viewModel::downloadOfflinePacks
                )
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
                AutomotiveCard(
                    contentPadding = 0.dp,
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
                    contentPadding = 0.dp,
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
                                    Text(address, style = MaterialTheme.typography.bodyLarge, color = palette.textSecondary)
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
                contentPadding = 0.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(420.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
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

/**
 * Botón redondo propio para recentrar el mapa al navegar. Solo se muestra
 * cuando la cámara ya no sigue al usuario (el usuario movió el mapa).
 * Lee mapState.cameraMode en este composable estrecho para que solo este
 * botón se recomponga cuando un gesto cambia el modo de cámara.
 */
@Composable
private fun RecenterButton(
    mapState: NavigationMapState,
    isNavigating: Boolean,
    modifier: Modifier = Modifier
) {
    val cameraMode = mapState.cameraMode
    if (!isNavigating) return
    if (cameraMode == NavigationCameraMode.FOLLOW_USER ||
        cameraMode == NavigationCameraMode.FOLLOW_USER_WITH_BEARING
    ) {
        return
    }
    val palette = DivineDeckTheme.palette
    Surface(
        onClick = { mapState.recenter(isNavigating = true) },
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = palette.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, palette.border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Navigation,
                contentDescription = "Recentrar",
                tint = palette.accent,
                modifier = Modifier.size(24.dp)
            )
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

/** Chip de conectividad: siempre visible en Navegación. */
@Composable
private fun ConnectivityChip(isOnline: Boolean) {
    val (label, background) = if (isOnline) {
        "● En línea" to Color(0xFF1E7A4C)
    } else {
        "● Sin internet · Mapa offline" to Color(0xFFB45309)
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        modifier = Modifier
            .background(background.copy(alpha = 0.95f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/** Estado de los paquetes offline + acción de descarga manual. */
@Composable
private fun OfflinePacksCard(
    isOnline: Boolean,
    mapPackState: MapPackState,
    routingPackState: RoutingPackState,
    onDownloadClick: () -> Unit
) {
    val mapReady = mapPackState is MapPackState.Ready
    val routesReady = routingPackState is RoutingPackState.Ready
    if (mapReady && routesReady) return
    val palette = DivineDeckTheme.palette
    val isBusy = mapPackState is MapPackState.Downloading ||
        routingPackState is RoutingPackState.Downloading ||
        routingPackState is RoutingPackState.Verifying
    Spacer(modifier = Modifier.height(8.dp))
    AutomotiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Modo offline de Tepic",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary
            )
            Text(
                text = "Mapa offline: " + when (mapPackState) {
                    is MapPackState.Ready -> "listo"
                    is MapPackState.Downloading -> "descargando ${(mapPackState.fraction * 100).toInt()}%"
                    is MapPackState.Error -> "error — reintenta con internet"
                    is MapPackState.NotDownloaded -> "no descargado"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            Text(
                text = "Rutas offline: " + when (routingPackState) {
                    is RoutingPackState.Ready -> "listas"
                    is RoutingPackState.Downloading -> "descargando ${(routingPackState.fraction * 100).toInt()}%"
                    is RoutingPackState.Verifying -> "verificando…"
                    is RoutingPackState.Error -> "error — reintenta con internet"
                    is RoutingPackState.NotDownloaded -> "no descargadas"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
            if (!isOnline) {
                Text(
                    text = "Descarga el paquete offline con internet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary
                )
            } else if (!isBusy) {
                Spacer(modifier = Modifier.height(4.dp))
                AutomotiveSecondaryButton(onClick = onDownloadClick) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = palette.accent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Descargar paquetes offline", color = palette.textPrimary)
                }
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp
                )
            }
        }
    }
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
        border = BorderStroke(1.dp, if (selected) palette.accent else palette.borderSubtle),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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

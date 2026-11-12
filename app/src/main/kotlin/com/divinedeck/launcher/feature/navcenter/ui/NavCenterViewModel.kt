package com.divinedeck.launcher.feature.navcenter.ui

import android.app.Application
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.divinedeck.launcher.feature.navcenter.data.ConnectivityMonitor
import com.divinedeck.launcher.feature.navcenter.data.DivineDeckRouteProvider
import com.divinedeck.launcher.feature.navcenter.data.MapPackState
import com.divinedeck.launcher.feature.navcenter.data.NavLocationProvider
import com.divinedeck.launcher.feature.navcenter.data.NavPlacesPreferences
import com.divinedeck.launcher.feature.navcenter.data.NominatimApi
import com.divinedeck.launcher.feature.navcenter.data.OfflineMapPackManager
import com.divinedeck.launcher.feature.navcenter.data.OfflineNavConstants
import com.divinedeck.launcher.feature.navcenter.data.OfflinePackDownloader
import com.divinedeck.launcher.feature.navcenter.data.OfflineSearchData
import com.divinedeck.launcher.feature.navcenter.data.OfflineValhallaRouter
import com.divinedeck.launcher.feature.navcenter.data.OverpassApi
import com.divinedeck.launcher.feature.navcenter.data.PhotonApi
import com.divinedeck.launcher.feature.navcenter.data.RoutingPackState
import com.divinedeck.launcher.feature.navcenter.domain.NavLatLng
import com.divinedeck.launcher.feature.navcenter.domain.NavPlace
import com.divinedeck.launcher.feature.navcenter.domain.PoiKind
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationViewModel
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider
import com.stadiamaps.ferrostar.core.isNavigating
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.googleplayservices.FusedNavigationLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.TripState
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.time.Duration
import java.util.Locale

/**
 * Estado de la pantalla de Navegación (mapa embebido + guía por voz).
 * El mapa en sí lo dibuja Ferrostar (DynamicallyOrientingNavigationView);
 * aquí vive la lógica: búsqueda, POIs, Casa/Trabajo, rutas y voz.
 *
 * Modo offline (Tepic): con internet todo funciona como antes; sin
 * internet el mapa usa el paquete descargado, la búsqueda y los POIs salen
 * del JSON local, y las rutas las calcula Valhalla en el dispositivo.
 */
data class NavCenterUiState(
    val selectedPlace: NavPlace? = null,
    val selectedAddress: String? = null,
    val isResolvingAddress: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<NavPlace> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val poiResults: List<NavPlace> = emptyList(),
    val activePoiKind: PoiKind? = null,
    val isLoadingPois: Boolean = false,
    val isCalculatingRoute: Boolean = false,
    val routeError: String? = null,
    val isNavigating: Boolean = false,
    val hasArrived: Boolean = false,
    val currentLocation: NavLatLng? = null,
    val home: NavPlace? = null,
    val work: NavPlace? = null,
    val statusMessage: String? = null,
    val cameraTarget: NavLatLng? = null,
    val cameraTargetNonce: Int = 0,
    val isOnline: Boolean = true,
    val lastRouteOffline: Boolean = false,
    val mapPackState: MapPackState = MapPackState.NotDownloaded,
    val routingPackState: RoutingPackState = RoutingPackState.NotDownloaded
)

class NavCenterViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NavCenter"
        val TEPIC_CENTER = NavLatLng(latitude = 21.5, longitude = -104.9)
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val TAP_SELECT_RADIUS_METERS = 80.0

        /** Sin internet y fuera del grafo de Tepic: no hay ruta posible. */
        const val MSG_OFFLINE_OUT_OF_COVERAGE =
            "Fuera del mapa offline de Tepic — conecta a internet para esta ruta"

        /** Sin internet y sin paquete de rutas instalado. */
        const val MSG_OFFLINE_PACK_MISSING =
            "Descarga el paquete offline con internet"

        /** Búsqueda / POIs sin internet y sin resultados locales. */
        const val MSG_OFFLINE_SEARCH_EMPTY =
            "Sin internet: solo lugares guardados y calles de Tepic"
    }

    private val appContext = application.applicationContext
    private val placesPrefs = NavPlacesPreferences(appContext)
    private val locationProvider = NavLocationProvider(appContext)

    // --- Modo offline ---
    private val connectivityMonitor = ConnectivityMonitor(appContext)
    private val offlineSearchData = OfflineSearchData(appContext)
    private val mapPackManager = OfflineMapPackManager(appContext)
    private val packDownloader = OfflinePackDownloader(appContext)
    private val offlineRouter = OfflineValhallaRouter(appContext)

    // --- Ferrostar: proveedor de ubicación, voz y servicio en primer plano ---
    private val ferrostarLocationProvider = FusedNavigationLocationProvider(appContext)

    private val ttsObserver: AndroidTtsObserver = AndroidTtsObserver(
        context = appContext,
        engine = googleTtsEngineOrNull(),
        statusObserver = object : AndroidTtsStatusListener {
            override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
                if (status != TextToSpeech.SUCCESS || tts == null) return
                // Instrucciones en español de México; si el motor no tiene
                // esa voz, español genérico.
                val result = tts.setLanguage(Locale("es", "MX"))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    tts.setLanguage(Locale("es"))
                }
            }

            override fun onTtsShutdownAndRelease() = Unit

            override fun onTtsSpeakError(utteranceId: String, status: Int) {
                Log.w(TAG, "Error de voz utterance=$utteranceId status=$status")
            }
        }
    )

    /**
     * Motor Google TTS si está instalado (en Android 15 el TTS de Samsung
     * puede quedarse callado en apps de terceros); si no, motor del sistema.
     */
    private fun googleTtsEngineOrNull(): String? {
        return try {
            appContext.packageManager.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
            GOOGLE_TTS_PACKAGE
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Núcleo Ferrostar con el proveedor de rutas propio: la petición es la
     * misma de siempre (Valhalla, perfil auto, km, español) y solo cambia
     * el transporte: servidor FOSSGIS con internet, motor Valhalla local
     * sin internet. Así el recálculo por desvío también funciona offline.
     */
    val ferrostarCore: FerrostarCore by lazy {
        val routeProvider = DivineDeckRouteProvider(
            isOnline = { connectivityMonitor.isOnline.value },
            offlineRouter = offlineRouter,
            onRouteComputed = { offline ->
                _uiState.update { it.copy(lastRouteOffline = offline) }
            }
        )
        val core = FerrostarCore(
            customRouteProvider = routeProvider,
            httpClient = OkHttpClientProvider(
                OkHttpClient.Builder()
                    .callTimeout(Duration.ofSeconds(15))
                    .build()
            ),
            locationProvider = ferrostarLocationProvider,
            navigationControllerConfig = NavigationControllerConfig(
                WaypointAdvanceMode.WaypointWithinRange(100.0),
                stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
                stepAdvanceDistanceToEndOfStep(30u, 32u),
                RouteDeviationTracking.StaticThreshold(15U, 50.0),
                CourseFiltering.SNAP_TO_ROUTE
            ),
            foregroundServiceManager = FerrostarForegroundServiceManager(
                appContext,
                DefaultForegroundNotificationBuilder(appContext)
            )
        )
        // La voz DEBE conectarse antes de crear el ViewModel de navegación:
        // DefaultNavigationViewModel captura el estado de silencio en su
        // constructor y si no, el botón de silencio no funciona.
        core.spokenInstructionObserver = ttsObserver
        core
    }

    val navigationViewModel: NavigationViewModel by lazy {
        DefaultNavigationViewModel(ferrostarCore)
    }

    private val _uiState = MutableStateFlow(
        NavCenterUiState(
            home = placesPrefs.getHome(),
            work = placesPrefs.getWork()
        )
    )
    val uiState: StateFlow<NavCenterUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        ttsObserver.start()
        viewModelScope.launch {
            ferrostarCore.state.collect { navState ->
                _uiState.update {
                    it.copy(
                        isNavigating = navState.isNavigating(),
                        hasArrived = navState.tripState is TripState.Complete
                    )
                }
            }
        }
        viewModelScope.launch {
            locationProvider.lastLocation()?.let { last ->
                _uiState.update {
                    it.copy(currentLocation = NavLatLng(last.latitude, last.longitude))
                }
            }
        }
        // Conectividad y paquetes offline -> estado de la UI.
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
        viewModelScope.launch {
            mapPackManager.state.collect { state ->
                _uiState.update { it.copy(mapPackState = state) }
            }
        }
        viewModelScope.launch {
            packDownloader.state.collect { state ->
                _uiState.update { it.copy(routingPackState = state) }
            }
        }
        viewModelScope.launch {
            mapPackManager.refreshState()
            packDownloader.refreshState()
            offlineSearchData.load()
            // Primera ejecución con internet: descargar los paquetes
            // offline en segundo plano para que el modo sin internet esté
            // listo cuando Daniel lo necesite.
            if (connectivityMonitor.isOnline.value) {
                if (mapPackManager.state.value !is MapPackState.Ready) {
                    launch { mapPackManager.ensurePack() }
                }
                if (!packDownloader.isInstalled()) {
                    launch { packDownloader.downloadPack() }
                }
            }
        }
    }

    override fun onCleared() {
        ttsObserver.shutdown()
        connectivityMonitor.unregister()
        offlineRouter.close()
        super.onCleared()
    }

    /** Descarga manual de los paquetes offline (botón de la pantalla). */
    fun downloadOfflinePacks() {
        if (!connectivityMonitor.isOnline.value) {
            _uiState.update { it.copy(statusMessage = MSG_OFFLINE_PACK_MISSING) }
            return
        }
        viewModelScope.launch {
            if (mapPackManager.state.value !is MapPackState.Ready) {
                launch { mapPackManager.ensurePack() }
            }
            if (!packDownloader.isInstalled()) {
                launch { packDownloader.downloadPack() }
            }
        }
    }

    // --- Búsqueda (Photon online; JSON local offline, con debounce) ---

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchError = null) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, searchError = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearching = true, searchError = null) }
            val trimmed = query.trim()
            if (!connectivityMonitor.isOnline.value) {
                val loaded = offlineSearchData.load()
                val results = if (loaded) {
                    offlineSearchData.search(trimmed, _uiState.value.currentLocation)
                } else {
                    emptyList()
                }
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                        searchError = if (results.isEmpty()) MSG_OFFLINE_SEARCH_EMPTY else null
                    )
                }
                return@launch
            }
            // Photon primero; si lanza (incluido su timeout de 8 s), un solo
            // intento de respaldo con Nominatim (1 req/s, no autocompletado).
            val photonResults: List<NavPlace>? = try {
                withContext(Dispatchers.IO) {
                    PhotonApi.search(trimmed, _uiState.value.currentLocation)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Búsqueda Photon falló: ${e.message}")
                null
            }
            if (photonResults != null) {
                _uiState.update { it.copy(searchResults = photonResults, isSearching = false, searchError = null) }
                return@launch
            }
            // "Colonia Centro, Tepic" da 0 resultados en Nominatim;
            // "Centro Histórico, Tepic" sí da 1: quitar el prefijo.
            val nominatimQuery = trimmed.removePrefix("Colonia ").trim()
            val fallbackResults: List<NavPlace>? = try {
                withContext(Dispatchers.IO) {
                    NominatimApi.search(nominatimQuery, _uiState.value.currentLocation)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Búsqueda Nominatim falló: ${e.message}")
                null
            }
            if (fallbackResults.isNullOrEmpty()) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        searchError = "No se pudo buscar: el servicio no responde. Revisa tu internet e inténtalo de nuevo."
                    )
                }
            } else {
                _uiState.update { it.copy(searchResults = fallbackResults, isSearching = false, searchError = null) }
            }
        }
    }

    /** Reintenta la búsqueda actual (botón "Reintentar" del estado vacío). */
    fun retrySearch() {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return
        onSearchQueryChange(query)
    }

    fun onSearchResultSelected(place: NavPlace) {
        selectPlace(place, moveCamera = true)
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), searchError = null) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false, searchError = null) }
    }

    // --- Selección de destino (toque en el mapa) ---

    fun onMapTap(point: NavLatLng) {
        // Si tocó cerca de un POI o de un resultado visible, selecciónalo.
        val nearby = (_uiState.value.poiResults + _uiState.value.searchResults)
            .minByOrNull { distanceMeters(it.latLng, point) }
            ?.takeIf { distanceMeters(it.latLng, point) < TAP_SELECT_RADIUS_METERS }
        if (nearby != null) {
            selectPlace(nearby, moveCamera = false)
            return
        }
        val place = NavPlace(
            id = "tap:${point.latitude},${point.longitude}",
            name = "Punto en el mapa",
            detail = "%.5f, %.5f".format(point.latitude, point.longitude),
            latLng = point,
            kind = PoiKind.TAPPED
        )
        selectPlace(place, moveCamera = false)
        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingAddress = true) }
            val address = if (!connectivityMonitor.isOnline.value) {
                offlineSearchData.load()
                offlineSearchData.nearestStreet(point)
                    ?.let { "Cerca de $it" }
            } else {
                withContext(Dispatchers.IO) { NominatimApi.reverse(point) }
            }
            _uiState.update {
                if (it.selectedPlace?.id == place.id) {
                    it.copy(selectedAddress = address, isResolvingAddress = false)
                } else {
                    it.copy(isResolvingAddress = false)
                }
            }
        }
    }

    fun selectPlace(place: NavPlace, moveCamera: Boolean = true) {
        _uiState.update {
            it.copy(
                selectedPlace = place,
                selectedAddress = place.detail.takeIf { it.isNotBlank() },
                routeError = null,
                cameraTarget = if (moveCamera) place.latLng else it.cameraTarget,
                cameraTargetNonce = if (moveCamera) it.cameraTargetNonce + 1 else it.cameraTargetNonce
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPlace = null, selectedAddress = null, routeError = null) }
    }

    // --- Puntos de interés (Overpass online; JSON local offline) ---

    fun togglePois(kind: PoiKind) {
        val current = _uiState.value
        if (current.activePoiKind == kind) {
            _uiState.update { it.copy(activePoiKind = null, poiResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingPois = true, activePoiKind = kind, poiResults = emptyList())
            }
            val center = current.currentLocation ?: TEPIC_CENTER
            if (!connectivityMonitor.isOnline.value) {
                offlineSearchData.load()
                val results = offlineSearchData.nearbyPois(kind, center)
                _uiState.update {
                    it.copy(
                        poiResults = results,
                        isLoadingPois = false,
                        statusMessage = if (results.isEmpty()) MSG_OFFLINE_SEARCH_EMPTY else null
                    )
                }
                return@launch
            }
            val results = try {
                withContext(Dispatchers.IO) { OverpassApi.nearby(kind, center) }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass falló: ${e.message}")
                emptyList()
            }
            _uiState.update {
                it.copy(
                    poiResults = results,
                    isLoadingPois = false,
                    statusMessage = if (results.isEmpty()) {
                        "No encontré ${if (kind == PoiKind.FUEL) "gasolineras" else "talleres"} cerca"
                    } else {
                        null
                    }
                )
            }
        }
    }

    // --- Casa / Trabajo ---

    fun saveAsHome(place: NavPlace) {
        placesPrefs.setHome(place)
        _uiState.update { it.copy(home = place, statusMessage = "Guardado como Casa: ${place.name}") }
    }

    fun saveAsWork(place: NavPlace) {
        placesPrefs.setWork(place)
        _uiState.update { it.copy(work = place, statusMessage = "Guardado como Trabajo: ${place.name}") }
    }

    fun goHome() {
        val home = _uiState.value.home
        if (home == null) {
            _uiState.update {
                it.copy(statusMessage = "Casa no guardada: toca un punto en el mapa y elige «Guardar como Casa»")
            }
        } else {
            selectPlace(home, moveCamera = true)
            startNavigation(home)
        }
    }

    fun goWork() {
        val work = _uiState.value.work
        if (work == null) {
            _uiState.update {
                it.copy(statusMessage = "Trabajo no guardado: toca un punto en el mapa y elige «Guardar como Trabajo»")
            }
        } else {
            selectPlace(work, moveCamera = true)
            startNavigation(work)
        }
    }

    // --- Rutas y navegación guiada (Ferrostar + Valhalla) ---

    fun startNavigation(place: NavPlace) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCalculatingRoute = true, routeError = null) }
            try {
                val origin = withContext(Dispatchers.IO) {
                    locationProvider.awaitLocation(10_000)
                        ?: ferrostarLocationProvider.lastLocation()
                }
                val userLocation = origin?.toUserLocation()
                    ?: throw IllegalStateException("sin ubicación GPS todavía")
                // Sin internet, la ruta solo existe si el paquete está
                // instalado y origen y destino están dentro del grafo.
                if (!connectivityMonitor.isOnline.value) {
                    val engineReady = withContext(Dispatchers.IO) {
                        packDownloader.isInstalled() && offlineRouter.ensureEngine() != null
                    }
                    if (!engineReady) {
                        throw IllegalStateException(MSG_OFFLINE_PACK_MISSING)
                    }
                    val dest = place.latLng
                    val originLatLng = NavLatLng(
                        latitude = userLocation.coordinates.lat,
                        longitude = userLocation.coordinates.lng
                    )
                    if (!OfflineNavConstants.isInsideRouteBounds(dest.latitude, dest.longitude) ||
                        !OfflineNavConstants.isInsideRouteBounds(originLatLng.latitude, originLatLng.longitude)
                    ) {
                        throw IllegalStateException(MSG_OFFLINE_OUT_OF_COVERAGE)
                    }
                }
                val routes = withContext(Dispatchers.IO) {
                    ferrostarCore.getRoutes(
                        userLocation,
                        listOf(
                            Waypoint(
                                GeographicCoordinate(place.latLng.latitude, place.latLng.longitude),
                                WaypointKind.BREAK
                            )
                        )
                    )
                }
                val route = routes.firstOrNull()
                    ?: throw IllegalStateException("Valhalla no devolvió ninguna ruta")
                navigationViewModel.setDestination(place.name)
                ferrostarCore.startNavigation(route)
                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        selectedPlace = place,
                        searchResults = emptyList(),
                        searchQuery = "",
                        searchError = null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo calcular la ruta: ${e.message}", e)
                val message = e.message
                val userMessage = when {
                    message == MSG_OFFLINE_PACK_MISSING -> MSG_OFFLINE_PACK_MISSING
                    message == MSG_OFFLINE_OUT_OF_COVERAGE -> MSG_OFFLINE_OUT_OF_COVERAGE
                    message?.contains("paquete de rutas offline") == true -> MSG_OFFLINE_PACK_MISSING
                    else -> "No se pudo calcular la ruta (${e.message ?: "sin GPS o sin internet"}). Inténtalo de nuevo."
                }
                _uiState.update {
                    it.copy(
                        isCalculatingRoute = false,
                        routeError = userMessage
                    )
                }
            }
        }
    }

    fun stopNavigation() {
        ferrostarCore.stopNavigation()
        navigationViewModel.setDestination(null)
        _uiState.update { it.copy(isNavigating = false, hasArrived = false) }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun dismissRouteError() {
        _uiState.update { it.copy(routeError = null) }
    }

    private fun distanceMeters(a: NavLatLng, b: NavLatLng): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val x = dLon * Math.cos(Math.toRadians((a.latitude + b.latitude) / 2.0))
        return Math.sqrt(x * x + dLat * dLat) * r
    }
}

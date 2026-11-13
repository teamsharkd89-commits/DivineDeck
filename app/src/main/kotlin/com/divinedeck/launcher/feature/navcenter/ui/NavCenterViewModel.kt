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
import com.divinedeck.launcher.feature.navcenter.data.OfflineSearchMatcher
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
    /** Nota persistente del chip de POIs ("No encontré…", "Mostrando
     * guardadas…"): a diferencia de [statusMessage] no se auto-descarta
     * a los pocos segundos. */
    val poiMessage: String? = null,
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
        /** Radio para tocar un marcador de POI: los círculos del mapa son
         * pequeños en la tablet y el toque no siempre cae exacto. */
        private const val POI_TAP_RADIUS_METERS = 120.0
        /** Distancia bajo la que dos resultados con el mismo nombre se
         * consideran el mismo lugar (fusión offline + online). */
        private const val MERGE_SAME_PLACE_METERS = 100.0
        private const val MERGED_RESULTS_LIMIT = 10

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

    // --- Búsqueda (offline siempre primero; online: Nominatim para
    // direcciones, Photon para nombres/lugares; con debounce) ---

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
            // 1. Offline SIEMPRE primero: instantáneo y solo de Tepic.
            // Antes, con internet, Photon (sin bbox) reemplazaba los
            // resultados locales aunque devolviera Jalisco o Colima.
            val loaded = offlineSearchData.load()
            val offlineResults = if (loaded) {
                offlineSearchData.search(trimmed, _uiState.value.currentLocation)
            } else {
                emptyList()
            }
            if (!connectivityMonitor.isOnline.value) {
                _uiState.update {
                    it.copy(
                        searchResults = offlineResults,
                        isSearching = false,
                        searchError = if (offlineResults.isEmpty()) MSG_OFFLINE_SEARCH_EMPTY else null
                    )
                }
                return@launch
            }
            // 2. Online (build 28): el motor depende de la consulta.
            // Dirección (número de casa o palabra de vía/asentamiento):
            // Nominatim primero — Photon devuelve ruido con números
            // ("camichin de jauja 32 ejidal" → "Salón Ejidal"). Nombre
            // o lugar ("Bodega Aurrerá", "Catedral", "Forum"): Photon
            // primero, que es su fuerte. Si el primario lanza (incluido
            // su timeout de 8 s) O devuelve vacío, se intenta el otro:
            // vacío no es fallo, pero el otro motor puede responder.
            // A Nominatim se le añade ", Tepic, Nayarit" si no trae
            // ciudad, y se le quita el prefijo "Colonia " ("Colonia
            // Centro, Tepic" da 0; "Centro Histórico, Tepic" sí da 1).
            val addressLike = OfflineSearchMatcher.isAddressLikeQuery(trimmed)
            val nominatimQuery = trimmed.removePrefix("Colonia ").trim()
                .let { q ->
                    if (q.contains("tepic", ignoreCase = true)) q
                    else "$q, Tepic, Nayarit"
                }
            suspend fun tryPhoton(): List<NavPlace>? = try {
                withContext(Dispatchers.IO) {
                    PhotonApi.search(trimmed, _uiState.value.currentLocation)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Búsqueda Photon falló: ${e.message}")
                null
            }
            suspend fun tryNominatim(): List<NavPlace>? = try {
                withContext(Dispatchers.IO) {
                    NominatimApi.search(nominatimQuery, _uiState.value.currentLocation)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Búsqueda Nominatim falló: ${e.message}")
                null
            }
            val onlineResults: List<NavPlace>? = if (addressLike) {
                val primary = tryNominatim()
                if (!primary.isNullOrEmpty()) {
                    Log.d(TAG, "Búsqueda online: Nominatim (${primary.size}) para \"$trimmed\"")
                    primary
                } else {
                    val secondary = tryPhoton()
                    if (secondary != null) {
                        Log.d(TAG, "Búsqueda online: Photon (${secondary.size}) tras Nominatim vacío/fallo para \"$trimmed\"")
                    }
                    secondary
                }
            } else {
                val primary = tryPhoton()
                if (!primary.isNullOrEmpty()) {
                    primary
                } else {
                    val secondary = tryNominatim()
                    if (secondary != null) {
                        Log.d(TAG, "Búsqueda online: Nominatim (${secondary.size}) tras Photon vacío/fallo para \"$trimmed\"")
                    }
                    secondary
                }
            }
            // 3. Fusión offline-primero: lo local arriba, lo online solo
            // si no duplica un resultado local y está dentro del bbox de
            // Tepic (un Tepic nunca debe mostrar Jalisco/Colima por
            // encima de lo local). Si los dos servicios online fallan y
            // tampoco hay nada local, error + "Reintentar" (fallo real).
            // Si fallan pero hay resultados locales, se muestran sin error.
            if (onlineResults == null && offlineResults.isEmpty()) {
                _uiState.update {
                    it.copy(
                        searchResults = emptyList(),
                        isSearching = false,
                        searchError = "No se pudo buscar: el servicio no responde. Revisa tu internet e inténtalo de nuevo."
                    )
                }
                return@launch
            }
            val merged = mergePlaceLists(
                primary = offlineResults,
                secondary = onlineResults ?: emptyList(),
                dropOutsideTepic = true
            )
            _uiState.update { it.copy(searchResults = merged, isSearching = false, searchError = null) }
        }
    }

    /**
     * Fusión offline-primero: [primary] arriba tal cual; de [secondary]
     * solo entran los que no duplican uno primario (mismo nombre
     * normalizado y a menos de ~100 m) y, si [dropOutsideTepic], están
     * dentro del bbox de Tepic.
     */
    private fun mergePlaceLists(
        primary: List<NavPlace>,
        secondary: List<NavPlace>,
        dropOutsideTepic: Boolean = false,
        limit: Int = MERGED_RESULTS_LIMIT
    ): List<NavPlace> {
        val merged = primary.toMutableList()
        for (place in secondary) {
            if (dropOutsideTepic && !OfflineNavConstants.isInsideMapBounds(
                    place.latLng.latitude, place.latLng.longitude
                )
            ) {
                continue
            }
            val key = placeNameKey(place)
            val duplicate = merged.any { existing ->
                placeNameKey(existing) == key &&
                    distanceMeters(existing.latLng, place.latLng) < MERGE_SAME_PLACE_METERS
            }
            if (!duplicate) merged.add(place)
        }
        return merged.take(limit)
    }

    /**
     * Clave de nombre para deduplicar: nombre normalizado sin acentos y
     * sin palabras genéricas ni stop words, tokens ordenados. Así
     * "Calle Camino Viejo a Jauja" (offline) y "Camino Viejo a Jauja"
     * (Photon) son el mismo lugar.
     */
    private fun placeNameKey(place: NavPlace): String {
        val tokens = OfflineSearchData.normalize(place.name)
            .split(" ", "-", ".")
            .filter { it.isNotBlank() }
            .filter { it !in OfflineSearchMatcher.STOP_WORDS }
            .filter { it !in OfflineSearchMatcher.GENERIC_WORDS }
        return tokens.sorted().joinToString(" ")
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
        // Tocar un marcador de POI lo selecciona y abre su hoja con
        // Navegar: las capas de círculos no son tocables por sí mismas,
        // así que el toque en el mapa se resuelve por cercanía. Los POIs
        // van primero y con más radio (120 m) que los resultados de
        // búsqueda (80 m): el círculo es pequeño en la tablet y el toque
        // no siempre cae exacto.
        val nearbyPoi = _uiState.value.poiResults
            .minByOrNull { distanceMeters(it.latLng, point) }
            ?.takeIf { distanceMeters(it.latLng, point) < POI_TAP_RADIUS_METERS }
        if (nearbyPoi != null) {
            selectPlace(nearbyPoi, moveCamera = false)
            return
        }
        val nearbySearch = _uiState.value.searchResults
            .minByOrNull { distanceMeters(it.latLng, point) }
            ?.takeIf { distanceMeters(it.latLng, point) < TAP_SELECT_RADIUS_METERS }
        if (nearbySearch != null) {
            selectPlace(nearbySearch, moveCamera = false)
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
            _uiState.update { it.copy(activePoiKind = null, poiResults = emptyList(), poiMessage = null) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoadingPois = true, activePoiKind = kind, poiResults = emptyList(), poiMessage = null)
            }
            val center = current.currentLocation ?: TEPIC_CENTER
            // 1. Offline SIEMPRE primero: instantáneo y local (52
            // gasolineras y 19 talleres en el JSON). Se muestra en cuanto
            // está, sin esperar a Overpass. Antes, con internet, solo se
            // consultaba Overpass y si fallaba el mapa quedaba vacío.
            offlineSearchData.load()
            val offlineResults = offlineSearchData.nearbyPois(kind, center)
            if (offlineResults.isNotEmpty()) {
                _uiState.update { it.copy(poiResults = offlineResults) }
            }
            if (!connectivityMonitor.isOnline.value) {
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        poiMessage = if (offlineResults.isEmpty()) MSG_OFFLINE_SEARCH_EMPTY else null
                    )
                }
                return@launch
            }
            // 2. Online: Overpass (nodos + vías) y fusión con lo local.
            // Si Overpass falla o agota el timeout, se quedan los locales
            // con una nota persistente en vez de un mapa vacío.
            val overpassResults: List<NavPlace>? = try {
                withContext(Dispatchers.IO) { OverpassApi.nearby(kind, center) }
            } catch (e: Exception) {
                Log.w(TAG, "Overpass falló: ${e.message}")
                null
            }
            val poiLabel = if (kind == PoiKind.FUEL) "gasolineras" else "talleres"
            if (overpassResults == null) {
                _uiState.update {
                    it.copy(
                        isLoadingPois = false,
                        poiMessage = if (offlineResults.isEmpty()) {
                            "No encontré $poiLabel cerca"
                        } else {
                            "Mostrando $poiLabel guardadas: no se pudo actualizar con internet"
                        }
                    )
                }
                return@launch
            }
            // Unión offline + Overpass ordenada por distancia: el JSON
            // trae las 30 más cercanas y Overpass otras 30; la unión
            // (hasta 60) se reordena y se queda con las 30 más cercanas
            // al centro. Sin reordenar, las de Overpass nunca entrarían
            // cuando el JSON ya llena el cupo.
            val merged = mergePlaceLists(
                primary = offlineResults,
                secondary = overpassResults,
                limit = 60
            ).sortedBy { distanceMeters(it.latLng, center) }.take(30)
            _uiState.update {
                it.copy(
                    poiResults = merged,
                    isLoadingPois = false,
                    poiMessage = if (merged.isEmpty()) "No encontré $poiLabel cerca" else null
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

package com.divinedeck.launcher.feature.gps.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.divinedeck.launcher.feature.gps.domain.AutomotiveMotionEngine
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedFilter
import com.divinedeck.launcher.feature.gps.domain.GpsStatusDebouncer
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedometerRepository
import com.divinedeck.launcher.feature.gps.domain.MotionProcessResult
import com.divinedeck.launcher.feature.gps.domain.model.GpsSpeedometerConfig
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.LocationPoint
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import com.divinedeck.launcher.feature.gps.domain.model.SpeedReading
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Implementación de producción del subsistema de velocímetro y posicionamiento GPS (Fase 4.0 Correctiva).
 *
 * Integra la arquitectura de ubicación y motor de movimiento probados en FuelSense:
 * - Proveedor principal: [FusedLocationProviderClient] de Google Play Services con [Priority.PRIORITY_HIGH_ACCURACY].
 * - Proveedor de respaldo: [LocationManager] nativo con [LocationManager.GPS_PROVIDER] y [LocationManager.NETWORK_PROVIDER].
 * - Semilla rápida con `lastLocation` (si la antigüedad es < 30s) para evitar demoras iniciales de búsqueda satelital.
 * - Motor de detección de movimiento automotriz [AutomotiveMotionEngine] con discriminación de deriva en reposo y estados
 *   STATIONARY, POSSIBLY_MOVING y MOVING.
 * - Diagnóstico exhaustivo mediante logs estructurados con el prefijo `[DivineDeck][Location]`.
 */
class GpsSpeedometerRepositoryImpl(
    private val context: Context? = null,
    override val config: GpsSpeedometerConfig = GpsSpeedometerConfig(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : GpsSpeedometerRepository {

    companion object {
        private const val TAG = "DivineDeckGPS"
    }

    private object GpsLog {
        fun d(tag: String, msg: String) { try { Log.d(tag, msg) } catch (_: Throwable) { } }
        fun i(tag: String, msg: String) { try { Log.i(tag, msg) } catch (_: Throwable) { } }
        fun w(tag: String, msg: String, tr: Throwable? = null) { try { if (tr != null) Log.w(tag, msg, tr) else Log.w(tag, msg) } catch (_: Throwable) { } }
        fun e(tag: String, msg: String, tr: Throwable? = null) { try { if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg) } catch (_: Throwable) { } }
        fun v(tag: String, msg: String) { try { Log.v(tag, msg) } catch (_: Throwable) { } }
    }

    // Constructor sin argumentos para compatibilidad de instanciación básica y tests unitarios
    constructor() : this(null, GpsSpeedometerConfig(), Dispatchers.Default)

    private val scope = CoroutineScope(dispatcher)
    private var watchdogJob: Job? = null

    private val _speedReading = MutableStateFlow(
        SpeedReading(
            currentSpeedKmh = 0f,
            maxSpeedKmh = 0f,
            avgSpeedKmh = 0f,
            accuracyMeters = 0f,
            isReliable = false,
            source = "STANDSTILL",
            movementState = MovementState.UNKNOWN
        )
    )
    override val speedReading: StateFlow<SpeedReading> = _speedReading.asStateFlow()

    private val _gpsStatus = MutableStateFlow(GpsStatus.NOT_AVAILABLE)
    override val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private val _accuracyState = MutableStateFlow(SpeedAccuracyState.SEARCHING)
    override val accuracyState: StateFlow<SpeedAccuracyState> = _accuracyState.asStateFlow()

    private val _movementState = MutableStateFlow(MovementState.UNKNOWN)
    override val movementState: StateFlow<MovementState> = _movementState.asStateFlow()

    // Motor de movimiento automotriz adaptado de FuelSense
    val motionEngine = AutomotiveMotionEngine(config)

    // Máquina de estados con antirrebote e histéresis (Fase 4.3)
    val statusDebouncer = GpsStatusDebouncer(
        confirmationThreshold = 2,
        initialStatus = GpsStatus.NOT_AVAILABLE
    )

    // Clientes de ubicación
    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null

    private var locationManager: LocationManager? = null
    private var nativeGpsListener: LocationListener? = null
    private var nativeNetworkListener: LocationListener? = null

    private var isTracking = false
    private var hasFinePermission = false
    private var hasCoarsePermission = false
    private var isGpsProviderAvailable = false
    private var isNetworkProviderAvailable = false
    private var isPlayServicesAvailable = false
    private var activeProviderName = "NONE"

    // Diagnóstico
    private var callbackCount = 0L

    // Estado interno para cálculo diferencial y filtro de velocidad
    private var prevLat: Double? = null
    private var prevLon: Double? = null
    private var prevTimeMs: Long = 0L
    private var prevAccuracy: Float = 0f
    private var lastFixTimestamp: Long = 0L
    private var lastFilteredSpeedKmh: Float = 0f

    // Métricas de viaje acumuladas
    private var maxSessionSpeedKmh: Float = 0f
    private var movingSpeedSum: Double = 0.0
    private var movingSpeedCount: Long = 0L

    private val providersChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                Log.d(TAG, "[DivineDeck][Location] Cambio de estado en proveedores de ubicación detectado.")
                refreshProviderStatus()
            }
        }
    }
    private var isReceiverRegistered = false

    init {
        context?.let { ctx ->
            locationManager = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            checkPermissions(ctx)
            checkPlayServicesAvailability(ctx)
            refreshProviderStatus()
        }
    }

    private fun checkPlayServicesAvailability(ctx: Context) {
        isPlayServicesAvailable = try {
            val availability = GoogleApiAvailability.getInstance()
            val resultCode = availability.isGooglePlayServicesAvailable(ctx)
            val available = resultCode == ConnectionResult.SUCCESS
            GpsLog.d(TAG, "[DivineDeck][Location] Disponibilidad Google Play Services: $available (código: $resultCode)")
            available
        } catch (e: Exception) {
            GpsLog.w(TAG, "[DivineDeck][Location] Error verificando Google Play Services", e)
            false
        }
    }

    private fun checkPermissions(ctx: Context): Boolean {
        hasFinePermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        hasCoarsePermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val granted = hasFinePermission || hasCoarsePermission
        GpsLog.d(TAG, "[DivineDeck][Location] Estado permisos: FINE=$hasFinePermission, COARSE=$hasCoarsePermission")
        return granted
    }

    @SuppressLint("MissingPermission")
    override fun startTracking() {
        if (isTracking) {
            GpsLog.d(TAG, "[DivineDeck][Location] startTracking() invocado pero ya se encuentra en ejecución.")
            return
        }
        isTracking = true
        callbackCount = 0L
        GpsLog.i(TAG, "[DivineDeck][Location] >>> INICIANDO SEGUIMIENTO DE UBICACIÓN Y VELOCIDAD <<<")

        val ctx = context
        if (ctx == null) {
            GpsLog.w(TAG, "[DivineDeck][Location] Contexto nulo, operando en modo headless.")
            startWatchdog()
            return
        }

        checkPermissions(ctx)
        refreshProviderStatus()

        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
                ctx.registerReceiver(providersChangedReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                GpsLog.w(TAG, "[DivineDeck][Location] Error registrando providersChangedReceiver", e)
            }
        }

        if (!hasFinePermission && !hasCoarsePermission) {
            GpsLog.w(TAG, "[DivineDeck][Location] Permisos no concedidos. Estado: NOT_AVAILABLE")
            updateStatus(rawStatus = GpsStatus.NOT_AVAILABLE, source = "PERMISSIONS", reason = "Permissions missing")
            startWatchdog()
            return
        }

        // 1. Intentar iniciar con FusedLocationProviderClient (Solución moderna y estándar de FuelSense)
        if (isPlayServicesAvailable) {
            startFusedLocation(ctx)
        } else {
            GpsLog.i(TAG, "[DivineDeck][Location] Play Services no disponible. Activando respaldo nativo directo.")
            startNativeFallback(ctx)
        }

        startWatchdog()
    }

    @SuppressLint("MissingPermission")
    private fun startFusedLocation(ctx: Context) {
        try {
            val client = LocationServices.getFusedLocationProviderClient(ctx)
            fusedClient = client
            activeProviderName = "FUSED_PLAY_SERVICES"
            GpsLog.d(TAG, "[DivineDeck][Location] Configurando FusedLocationProviderClient (Intervalo: ${config.updateIntervalMs}ms, Rápido: ${config.fastestIntervalMs}ms)")

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, config.updateIntervalMs)
                .setMinUpdateIntervalMillis(config.fastestIntervalMs)
                .setMinUpdateDistanceMeters(config.minDistanceMeters)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (!isTracking) return
                    for (location in result.locations) {
                        callbackCount++
                        handleAndroidLocation(location, "FUSED")
                    }
                }
            }
            fusedCallback = callback

            client.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener { exception ->
                GpsLog.w(TAG, "[DivineDeck][Location] FusedLocationProviderClient falló al solicitar actualizaciones. Activando respaldo nativo", exception)
                startNativeFallback(ctx)
            }

            // Consultar última ubicación conocida para arranque veloz (patrón FuelSense)
            client.lastLocation.addOnSuccessListener { lastLoc ->
                if (isTracking && lastLoc != null) {
                    val ageMs = System.currentTimeMillis() - lastLoc.time
                    GpsLog.d(TAG, "[DivineDeck][Location] Última ubicación conocida obtenida. Antigüedad: ${ageMs}ms, Precisión: ${lastLoc.accuracy}m")
                    if (ageMs < 30_000L && GpsSpeedFilter.isAccuracyAcceptable(lastLoc.accuracy, config.minAcceptableAccuracyMeters)) {
                        handleAndroidLocation(lastLoc, "LAST_KNOWN_FUSED")
                    }
                }
            }

        } catch (e: SecurityException) {
            GpsLog.e(TAG, "[DivineDeck][Location] SecurityException iniciando FusedLocationProvider", e)
            updateStatus(rawStatus = GpsStatus.NOT_AVAILABLE, source = "FUSED_SECURITY", reason = "SecurityException in FusedLocationClient")
        } catch (e: Exception) {
            GpsLog.e(TAG, "[DivineDeck][Location] Error inesperado en FusedLocationClient, intentando respaldo nativo", e)
            startNativeFallback(ctx)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNativeFallback(ctx: Context) {
        if (!isTracking || (!hasFinePermission && !hasCoarsePermission)) return

        val lm = locationManager ?: return
        activeProviderName = "NATIVE_LOCATION_MANAGER"
        GpsLog.d(TAG, "[DivineDeck][Location] Activando respaldo nativo (GPS=${lm.isProviderEnabled(LocationManager.GPS_PROVIDER)}, NETWORK=${lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)})")

        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!isTracking) return
                    callbackCount++
                    handleAndroidLocation(location, "NATIVE_${location.provider?.uppercase() ?: "UNKNOWN"}")
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                    refreshProviderStatus()
                }

                override fun onProviderEnabled(provider: String) {
                    GpsLog.d(TAG, "[DivineDeck][Location] Proveedor nativo habilitado: $provider")
                    refreshProviderStatus()
                }

                override fun onProviderDisabled(provider: String) {
                    GpsLog.d(TAG, "[DivineDeck][Location] Proveedor nativo deshabilitado: $provider")
                    refreshProviderStatus()
                }
            }
            nativeGpsListener = listener

            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    config.updateIntervalMs,
                    config.minDistanceMeters,
                    listener,
                    Looper.getMainLooper()
                )
                GpsLog.d(TAG, "[DivineDeck][Location] Registrada escucha nativa en GPS_PROVIDER")
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                val netListener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (!isTracking) return
                        callbackCount++
                        handleAndroidLocation(location, "NATIVE_NETWORK")
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                nativeNetworkListener = netListener
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    config.updateIntervalMs,
                    config.minDistanceMeters,
                    netListener,
                    Looper.getMainLooper()
                )
                GpsLog.d(TAG, "[DivineDeck][Location] Registrada escucha nativa en NETWORK_PROVIDER")
            }

            // Consultar última ubicación conocida nativa
            val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                lastGps != null && lastNet != null -> if (lastGps.time >= lastNet.time) lastGps else lastNet
                lastGps != null -> lastGps
                else -> lastNet
            }
            if (bestLast != null) {
                val ageMs = System.currentTimeMillis() - bestLast.time
                if (ageMs < 30_000L && GpsSpeedFilter.isAccuracyAcceptable(bestLast.accuracy, config.minAcceptableAccuracyMeters)) {
                    handleAndroidLocation(bestLast, "LAST_KNOWN_NATIVE")
                }
            }

        } catch (e: SecurityException) {
            GpsLog.e(TAG, "[DivineDeck][Location] SecurityException en respaldo nativo", e)
            updateStatus(rawStatus = GpsStatus.NOT_AVAILABLE, source = "NATIVE_SECURITY", reason = "SecurityException in native fallback")
        } catch (e: Exception) {
            GpsLog.e(TAG, "[DivineDeck][Location] Error en respaldo nativo", e)
        }
    }

    override fun stopTracking() {
        val wasTracking = isTracking
        isTracking = false
        if (wasTracking) {
            GpsLog.i(TAG, "[DivineDeck][Location] Deteniendo seguimiento de ubicación (Callbacks totales recibidos: $callbackCount)")
        }

        watchdogJob?.cancel()
        watchdogJob = null

        fusedCallback?.let { callback ->
            try {
                fusedClient?.removeLocationUpdates(callback)
            } catch (e: Exception) {
                GpsLog.w(TAG, "[DivineDeck][Location] Error liberando FusedLocationClient", e)
            }
            fusedCallback = null
        }

        context?.let { ctx ->
            nativeGpsListener?.let { listener ->
                try {
                    locationManager?.removeUpdates(listener)
                } catch (e: Exception) {
                    GpsLog.w(TAG, "[DivineDeck][Location] Error liberando GPS listener", e)
                }
                nativeGpsListener = null
            }

            nativeNetworkListener?.let { listener ->
                try {
                    locationManager?.removeUpdates(listener)
                } catch (e: Exception) {
                    GpsLog.w(TAG, "[DivineDeck][Location] Error liberando Network listener", e)
                }
                nativeNetworkListener = null
            }

            if (isReceiverRegistered) {
                try {
                    ctx.unregisterReceiver(providersChangedReceiver)
                } catch (e: Exception) {
                    GpsLog.w(TAG, "[DivineDeck][Location] Error desregistrando receiver", e)
                }
                isReceiverRegistered = false
            }
        }

        // Restablecimiento estricto de ciclo de vida (Fase 4.3):
        // 1. Reiniciar motor de movimiento automotriz
        motionEngine.reset()
        // 2. Reiniciar antirrebote de estados
        statusDebouncer.reset(GpsStatus.NOT_AVAILABLE)
        // 3. Limpiar variables de cálculo diferencial y referencias previas
        lastFixTimestamp = 0L
        lastFilteredSpeedKmh = 0f
        prevLat = null
        prevLon = null
        prevTimeMs = 0L
        prevAccuracy = 0f
        callbackCount = 0L

        _speedReading.value = _speedReading.value.copy(
            currentSpeedKmh = 0f,
            isReliable = false,
            source = "STOPPED",
            movementState = MovementState.UNKNOWN
        )
        _movementState.value = MovementState.UNKNOWN
        updateStatus(GpsStatus.NOT_AVAILABLE, source = "LIFECYCLE", reason = "Tracking stopped")
    }

    override fun onPermissionStatusChanged(isGranted: Boolean) {
        GpsLog.i(TAG, "[DivineDeck][Location] Cambio de permiso informado: isGranted=$isGranted")
        context?.let { checkPermissions(it) } ?: run {
            hasFinePermission = isGranted
            hasCoarsePermission = isGranted
        }
        refreshProviderStatus()

        if (isTracking) {
            if (isGranted) {
                stopTracking()
                startTracking()
            } else {
                updateStatus(rawStatus = GpsStatus.NOT_AVAILABLE, source = "PERMISSIONS", reason = "Permission revoked")
            }
        }
    }

    override fun refreshProviderStatus() {
        val lm = locationManager
        val gpsEnabled = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val netEnabled = lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        isGpsProviderAvailable = gpsEnabled
        isNetworkProviderAvailable = netEnabled

        val hasAnyProvider = gpsEnabled || netEnabled || isPlayServicesAvailable
        val hasPermission = hasFinePermission || hasCoarsePermission

        GpsLog.d(TAG, "[DivineDeck][Location] refreshProviderStatus: GPS=$gpsEnabled, NET=$netEnabled, PlayServices=$isPlayServicesAvailable, Permiso=$hasPermission")

        val newStatus = GpsSpeedFilter.determineGpsStatus(
            hasPermission = hasPermission,
            isGpsProviderEnabled = hasAnyProvider,
            hasReceivedFix = lastFixTimestamp > 0L,
            timeSinceLastFixMs = if (lastFixTimestamp > 0L) System.currentTimeMillis() - lastFixTimestamp else Long.MAX_VALUE,
            accuracyMeters = _speedReading.value.accuracyMeters,
            isSpeedReliable = _speedReading.value.isReliable,
            config = config
        )
        updateStatus(rawStatus = newStatus, source = "PROVIDER_EVENT", reason = "Provider availability refreshed")
    }

    override fun resetTripMetrics() {
        maxSessionSpeedKmh = 0f
        movingSpeedSum = 0.0
        movingSpeedCount = 0L
        motionEngine.reset()
        _movementState.value = MovementState.UNKNOWN
        _speedReading.value = _speedReading.value.copy(
            maxSpeedKmh = 0f,
            avgSpeedKmh = 0f,
            currentSpeedKmh = 0f,
            isReliable = false,
            movementState = MovementState.UNKNOWN
        )
    }

    /**
     * Procesa un objeto android.location.Location real extrayendo velocidad y precisión.
     */
    private fun handleAndroidLocation(location: Location, providerSource: String) {
        val speedMps = if (location.hasSpeed() && location.speed >= 0f) location.speed else null
        val accuracy = if (location.hasAccuracy()) location.accuracy else 999f
        val timeMs = if (location.time > 0L) location.time else System.currentTimeMillis()
        val speedAccuracyMps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
            location.speedAccuracyMetersPerSecond
        } else {
            null
        }

        GpsLog.v(TAG, "[DivineDeck][Location] Callback #$callbackCount ($providerSource): Speed=${speedMps?.let { "%.2f m/s (%.1f km/h)".format(it, it * 3.6f) } ?: "null"}, Acc=±${accuracy}m, SpeedAcc=±${speedAccuracyMps ?: "n/a"}")

        processLocationUpdate(
            lat = location.latitude,
            lon = location.longitude,
            timeMs = timeMs,
            speedMps = speedMps,
            accuracyMeters = accuracy,
            speedAccuracyMps = speedAccuracyMps,
            sourcePrefix = providerSource,
            referenceTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * Procesa una actualización de ubicación mediante el motor de movimiento FuelSense
     * y actualiza el velocímetro digital.
     * Puede invocarse directamente por pruebas unitarias sin dependencias del sistema operativo.
     */
    fun processLocationUpdate(
        lat: Double,
        lon: Double,
        timeMs: Long,
        speedMps: Float?,
        accuracyMeters: Float,
        speedAccuracyMps: Float? = null,
        sourcePrefix: String = "GNSS",
        referenceTimeMs: Long = timeMs
    ) {
        val now = referenceTimeMs
        val isFresh = GpsSpeedFilter.isLocationFresh(timeMs, now, config.maxReadingAgeMs)
        val isAccurate = GpsSpeedFilter.isAccuracyAcceptable(accuracyMeters, config.minAcceptableAccuracyMeters)

        lastFixTimestamp = now

        // 1. Detección y filtrado con el motor de movimiento automotriz FuelSense
        val rawSpeedKmh = if (speedMps != null && speedMps >= 0f) {
            GpsSpeedFilter.convertMpsToKmh(speedMps)
        } else {
            null
        }

        val point = LocationPoint(
            latitude = lat,
            longitude = lon,
            timestampMs = timeMs,
            accuracyMeters = accuracyMeters,
            speedKmh = rawSpeedKmh,
            speedAccuracyMps = speedAccuracyMps
        )

        val motionResult = motionEngine.processPoint(point)
        val currentMovementState = motionEngine.movementState
        _movementState.value = currentMovementState

        // 2. Determinación de la velocidad cruda (priorizando hardware GNSS o derivada Haversine)
        val calculatedRawKmh: Float
        val source: String
        val isSpeedCalculable: Boolean

        when (motionResult) {
            is MotionProcessResult.Accepted -> {
                if (currentMovementState != MovementState.MOVING) {
                    calculatedRawKmh = 0f
                    source = when (currentMovementState) {
                        MovementState.STATIONARY -> "${sourcePrefix}_STATIONARY"
                        MovementState.POSSIBLY_MOVING -> "${sourcePrefix}_POSSIBLY_MOVING"
                        else -> "${sourcePrefix}_UNKNOWN"
                    }
                    isSpeedCalculable = (currentMovementState == MovementState.STATIONARY)
                } else if (motionResult.speedKmh > 0) {
                    calculatedRawKmh = motionResult.speedKmh.toFloat()
                    source = if (rawSpeedKmh != null) "${sourcePrefix}_HARDWARE" else "${sourcePrefix}_HAVERSINE"
                    isSpeedCalculable = true
                } else {
                    calculatedRawKmh = 0f
                    source = "${sourcePrefix}_ZERO"
                    isSpeedCalculable = true
                }
            }
            is MotionProcessResult.Rejected -> {
                GpsLog.d(TAG, "[DivineDeck][Location] Punto descartado por filtro FuelSense: ${motionResult.category} (${motionResult.reason})")
                calculatedRawKmh = 0f
                source = "${sourcePrefix}_REJECTED_${motionResult.category}"
                isSpeedCalculable = false
            }
        }

        val dtSeconds = if (prevTimeMs > 0L) (timeMs - prevTimeMs).coerceAtLeast(100L) / 1000.0f else 1.0f

        prevLat = lat
        prevLon = lon
        prevTimeMs = timeMs
        prevAccuracy = accuracyMeters

        val isReliable = when (currentMovementState) {
            MovementState.UNKNOWN, MovementState.POSSIBLY_MOVING -> false
            MovementState.STATIONARY -> isFresh && isAccurate && (motionResult is MotionProcessResult.Accepted)
            MovementState.MOVING -> isFresh && isAccurate && isSpeedCalculable && (motionResult is MotionProcessResult.Accepted)
        }

        val filteredKmh = if (isReliable && currentMovementState == MovementState.MOVING) {
            GpsSpeedFilter.filterSpeed(
                rawSpeedKmh = calculatedRawKmh,
                previousFilteredSpeedKmh = lastFilteredSpeedKmh,
                dtSeconds = dtSeconds,
                config = config,
                movementState = currentMovementState
            )
        } else {
            0f
        }
        lastFilteredSpeedKmh = filteredKmh

        // Actualización de métricas acumuladas del viaje
        if (isReliable && currentMovementState == MovementState.MOVING && filteredKmh > 0f) {
            maxSessionSpeedKmh = max(maxSessionSpeedKmh, filteredKmh)
            movingSpeedSum += filteredKmh.toDouble()
            movingSpeedCount++
        }
        val avgKmh = if (movingSpeedCount > 0L) (movingSpeedSum / movingSpeedCount).toFloat() else 0f

        _speedReading.value = SpeedReading(
            currentSpeedKmh = filteredKmh,
            maxSpeedKmh = maxSessionSpeedKmh,
            avgSpeedKmh = avgKmh,
            accuracyMeters = accuracyMeters,
            isReliable = isReliable,
            rawSpeedKmh = calculatedRawKmh,
            source = source,
            timestamp = now,
            movementState = currentMovementState,
            speedAccuracyMps = speedAccuracyMps
        )

        val hasAnyProvider = isGpsProviderAvailable || isNetworkProviderAvailable || isPlayServicesAvailable
        val hasPermission = hasFinePermission || hasCoarsePermission

        val newStatus = GpsSpeedFilter.determineGpsStatus(
            hasPermission = hasPermission,
            isGpsProviderEnabled = hasAnyProvider,
            hasReceivedFix = true,
            timeSinceLastFixMs = 0L,
            accuracyMeters = accuracyMeters,
            isSpeedReliable = isReliable,
            config = config
        )

        val dtMs = if (prevTimeMs > 0L) (timeMs - prevTimeMs).coerceAtLeast(0L) else 0L
        val fixAgeMs = (now - timeMs).coerceAtLeast(0L)
        val reasonStr = when (motionResult) {
            is MotionProcessResult.Accepted -> motionResult.stateReason
            is MotionProcessResult.Rejected -> motionResult.reason
        }

        updateStatus(
            rawStatus = newStatus,
            source = sourcePrefix,
            reason = reasonStr,
            dtMs = dtMs,
            fixAgeMs = fixAgeMs,
            accuracyMeters = accuracyMeters,
            speedRawMps = speedMps,
            speedKmh = filteredKmh,
            speedAccuracyMps = speedAccuracyMps,
            isReliable = isReliable
        )
    }

    /**
     * Bucle de verificación temporal (Watchdog) para detectar pérdida de señal o envejecimiento de lecturas.
     */
    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(1000L)
                if (!isTracking) break

                val now = System.currentTimeMillis()
                val timeSinceLastFix = if (lastFixTimestamp > 0L) now - lastFixTimestamp else Long.MAX_VALUE

                if (lastFixTimestamp > 0L && timeSinceLastFix > config.maxReadingAgeMs) {
                    if (_speedReading.value.currentSpeedKmh > 0f || _speedReading.value.isReliable || _movementState.value != MovementState.UNKNOWN) {
                        GpsLog.d(TAG, "[DivineDeck][Location] Watchdog: Lectura desactualizada (${timeSinceLastFix}ms > ${config.maxReadingAgeMs}ms). Desconectando velocidad.")
                        lastFilteredSpeedKmh = 0f
                        prevTimeMs = 0L
                        motionEngine.onSignalStale()
                        _movementState.value = MovementState.UNKNOWN
                        _speedReading.value = _speedReading.value.copy(
                            currentSpeedKmh = 0f,
                            isReliable = false,
                            source = "TIMEOUT_STALE",
                            movementState = MovementState.UNKNOWN
                        )
                    }
                }

                val hasAnyProvider = isGpsProviderAvailable || isNetworkProviderAvailable || isPlayServicesAvailable
                val hasPermission = hasFinePermission || hasCoarsePermission

                val currentGpsStatus = GpsSpeedFilter.determineGpsStatus(
                    hasPermission = hasPermission,
                    isGpsProviderEnabled = hasAnyProvider,
                    hasReceivedFix = lastFixTimestamp > 0L,
                    timeSinceLastFixMs = timeSinceLastFix,
                    accuracyMeters = _speedReading.value.accuracyMeters,
                    isSpeedReliable = _speedReading.value.isReliable,
                    config = config
                )

                val watchdogReason = when {
                    timeSinceLastFix > config.signalLossTimeoutMs -> "SIGNAL_LOSS_TIMEOUT"
                    timeSinceLastFix > config.maxReadingAgeMs -> "TIMEOUT_STALE"
                    else -> "WATCHDOG_HEARTBEAT"
                }

                // Evitar doble conteo en statusDebouncer durante heartbeats normales del Watchdog (Fase 4.5A)
                if (watchdogReason != "WATCHDOG_HEARTBEAT") {
                    updateStatus(
                        rawStatus = currentGpsStatus,
                        source = "WATCHDOG",
                        reason = watchdogReason,
                        dtMs = 1000L,
                        fixAgeMs = if (timeSinceLastFix == Long.MAX_VALUE) -1L else timeSinceLastFix,
                        accuracyMeters = _speedReading.value.accuracyMeters,
                        speedRawMps = null,
                        speedKmh = _speedReading.value.currentSpeedKmh,
                        speedAccuracyMps = _speedReading.value.speedAccuracyMps,
                        isReliable = _speedReading.value.isReliable
                    )
                }
            }
        }
    }

    /**
     * Registra eventos de telemetría GPS estructurados con formato Zero-PII.
     * No registra coordenadas ni identificadores personales.
     */
    private fun logGpsTelemetry(
        source: String,
        dtMs: Long,
        fixAgeMs: Long,
        accuracyMeters: Float,
        speedRawMps: Float?,
        speedKmh: Float,
        speedAccuracyMps: Float?,
        movementState: MovementState,
        confidence: Int,
        prevStatus: GpsStatus,
        newStatus: GpsStatus,
        reason: String,
        isReliable: Boolean
    ) {
        val spdRawStr = if (speedRawMps != null) String.format(java.util.Locale.US, "%.2f", speedRawMps) else "null"
        val spdKmhStr = String.format(java.util.Locale.US, "%.1f", speedKmh)
        val accStr = String.format(java.util.Locale.US, "%.1f", accuracyMeters)
        val spdAccStr = if (speedAccuracyMps != null) String.format(java.util.Locale.US, "%.2f", speedAccuracyMps) else "null"
        val now = System.currentTimeMillis()

        GpsLog.i(
            TAG,
            "t=$now | src=$source | dt=${dtMs}ms | fixAge=${fixAgeMs}ms | acc=${accStr}m | spdRaw=$spdRawStr | spdKmh=$spdKmhStr | spdAcc=$spdAccStr | state=$movementState | conf=$confidence | prevSt=$prevStatus | newSt=$newStatus | reason=$reason | reliable=$isReliable"
        )
    }

    private fun updateStatus(
        rawStatus: GpsStatus,
        source: String = "STATUS",
        reason: String = "State update",
        dtMs: Long = 0L,
        fixAgeMs: Long = 0L,
        accuracyMeters: Float = _speedReading.value.accuracyMeters,
        speedRawMps: Float? = null,
        speedKmh: Float = _speedReading.value.currentSpeedKmh,
        speedAccuracyMps: Float? = _speedReading.value.speedAccuracyMps,
        isReliable: Boolean = _speedReading.value.isReliable
    ) {
        val prevStatus = _gpsStatus.value
        val confirmedStatus = statusDebouncer.update(rawStatus)

        if (confirmedStatus != prevStatus) {
            logGpsTelemetry(
                source = source,
                dtMs = dtMs,
                fixAgeMs = fixAgeMs,
                accuracyMeters = accuracyMeters,
                speedRawMps = speedRawMps,
                speedKmh = speedKmh,
                speedAccuracyMps = speedAccuracyMps,
                movementState = _movementState.value,
                confidence = motionEngine.movementConfidence,
                prevStatus = prevStatus,
                newStatus = confirmedStatus,
                reason = reason,
                isReliable = isReliable
            )
        }

        _gpsStatus.value = confirmedStatus
        _accuracyState.value = GpsSpeedFilter.determineAccuracyState(confirmedStatus)
    }
}

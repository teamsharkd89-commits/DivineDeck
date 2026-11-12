package com.divinedeck.launcher.feature.gps.domain

import com.divinedeck.launcher.feature.gps.domain.model.GpsSpeedometerConfig
import com.divinedeck.launcher.feature.gps.domain.model.LocationPoint
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.domain.model.RejectionCategory
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Resultado del procesamiento de un punto geográfico por el motor de movimiento.
 */
sealed interface MotionProcessResult {
    data class Accepted(
        val point: LocationPoint,
        val deltaDistanceKm: Double,
        val speedKmh: Double,
        val movementState: MovementState,
        val movementConfidence: Int,
        val stateReason: String,
        val usedForDistance: Boolean
    ) : MotionProcessResult

    data class Rejected(
        val point: LocationPoint,
        val category: RejectionCategory,
        val reason: String
    ) : MotionProcessResult
}

/**
 * Motor de detección de movimiento automotriz adaptado de FuelSense (TripEngine)
 * con corrección forense integral para la Fase 4.5A.
 *
 * Características principales:
 * - Máquina de 4 estados: UNKNOWN, STATIONARY, POSSIBLY_MOVING, MOVING.
 * - Eliminación del salto directo de una sola lectura hacia MOVING.
 * - En POSSIBLY_MOVING la velocidad emitida es estrictamente 0.0 km/h.
 * - Evaluación por ventanas temporales independientes (>= 450ms) y acumuladas (>= 2000ms / >= 3000ms).
 * - Distinción semántica estricta entre velocidad ausente (null), velocidad cero (0.0f) y subumbral (< 1.5 km/h).
 * - Protección contra contradicciones sensoriales (degradación inmediata a UNKNOWN ante deriva en reposo).
 * - Confirmación temporal de detención (>= 3 fixes independientes y ventana >= 1800ms).
 * - Protección del odómetro y congelamiento de referencias espaciales ante rechazos aislados en MOVING.
 * - Degradación a UNKNOWN tras 2 rechazos consecutivos o brecha temporal > 3500ms.
 */
class AutomotiveMotionEngine(
    val config: GpsSpeedometerConfig = GpsSpeedometerConfig()
) {
    companion object {
        private const val EARTH_RADIUS_KM = 6371.0

        /**
         * Calcula la distancia geodésica en kilómetros entre dos coordenadas mediante Haversine.
         */
        fun calculateHaversineKm(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)

            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)

            val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                    cos(lat1Rad) * cos(lat2Rad) *
                    sin(dLon / 2.0) * sin(dLon / 2.0)

            val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
            return EARTH_RADIUS_KM * c
        }
    }

    private var _movementState: MovementState = MovementState.UNKNOWN
    val movementState: MovementState get() = _movementState

    private var _movementConfidence: Int = 0
    val movementConfidence: Int get() = _movementConfidence

    private var _stateReason: String = "Inicializando motor de movimiento (UNKNOWN)"
    val stateReason: String get() = _stateReason

    private var _stationaryAnchor: LocationPoint? = null
    val stationaryAnchor: LocationPoint? get() = _stationaryAnchor

    private var _lastMovingPoint: LocationPoint? = null
    private var _lastValidPoint: LocationPoint? = null
    val lastValidPoint: LocationPoint? get() = _lastValidPoint

    private val _candidateMovingPoints = mutableListOf<LocationPoint>()
    private val _stationaryCandidatePoints = mutableListOf<LocationPoint>()
    private var _consecutiveRejectedPoints: Int = 0

    private var _totalDistanceKm: Double = 0.0
    val totalDistanceKm: Double get() = _totalDistanceKm

    private var _currentSpeedKmh: Double = 0.0
    val currentSpeedKmh: Double get() = _currentSpeedKmh

    private var _pointsReceived: Int = 0
    val pointsReceived: Int get() = _pointsReceived

    private var _pointsValid: Int = 0
    val pointsValid: Int get() = _pointsValid

    private var _pointsRejected: Int = 0
    val pointsRejected: Int get() = _pointsRejected

    private var _lastRejectionCategory: RejectionCategory = RejectionCategory.NONE
    val lastRejectionCategory: RejectionCategory get() = _lastRejectionCategory

    fun reset() {
        _movementState = MovementState.UNKNOWN
        _movementConfidence = 0
        _stateReason = "Motor de movimiento reiniciado (UNKNOWN)"
        _stationaryAnchor = null
        _lastMovingPoint = null
        _lastValidPoint = null
        _candidateMovingPoints.clear()
        _stationaryCandidatePoints.clear()
        _consecutiveRejectedPoints = 0
        _totalDistanceKm = 0.0
        _currentSpeedKmh = 0.0
        _pointsReceived = 0
        _pointsValid = 0
        _pointsRejected = 0
        _lastRejectionCategory = RejectionCategory.NONE
    }

    /**
     * Invocado cuando la señal se vuelve obsoleta (timeout > 3500ms o pérdida de señal en Watchdog).
     */
    fun onSignalStale() {
        _movementState = MovementState.UNKNOWN
        _movementConfidence = 0
        _stateReason = "Señal GNSS obsoleta o perdida (Watchdog)"
        _candidateMovingPoints.clear()
        _stationaryCandidatePoints.clear()
        _stationaryAnchor = null
        _consecutiveRejectedPoints = 0
        _currentSpeedKmh = 0.0
    }

    /**
     * Procesa un punto de ubicación aplicando los filtros físicos y la máquina de 4 estados.
     */
    fun processPoint(point: LocationPoint): MotionProcessResult {
        _pointsReceived++

        // 1. Filtrado por precisión horizontal
        if (point.accuracyMeters != null && point.accuracyMeters > config.minAcceptableAccuracyMeters) {
            return handleRejectedPoint(
                point = point,
                category = RejectionCategory.POOR_ACCURACY,
                reason = "Precisión insuficiente: ±${point.accuracyMeters}m (límite: ${config.minAcceptableAccuracyMeters}m)"
            )
        }

        // 2. Validación de precisión de velocidad en Android (si está disponible y es excesiva)
        if (point.speedAccuracyMps != null && point.speedAccuracyMps > config.maxSpeedAccuracyMps && point.speedKmh != null && point.speedKmh > 5.0f) {
            return handleRejectedPoint(
                point = point,
                category = RejectionCategory.POOR_ACCURACY,
                reason = "Precisión de velocidad degradada: ±${point.speedAccuracyMps} m/s"
            )
        }

        // Evaluación de coherencia Doppler y velocidad reportada vs ausente/cero/subumbral
        val reportedSpeed = point.speedKmh
        val speedMps = reportedSpeed?.let { (it / 3.6).toFloat() } ?: 0f

        val isSpeedAccuracyCoherent = if (point.speedAccuracyMps != null && point.speedAccuracyMps > 0f) {
            speedMps >= (config.minSpeedAccuracyCoherenceRatio * point.speedAccuracyMps)
        } else {
            true // Si speedAccuracyMps es null, no se rechaza pero se exige compuerta reforzada
        }

        val hasAndroidSpeed = reportedSpeed != null &&
                reportedSpeed >= config.minMovingSpeedKmh &&
                isSpeedAccuracyCoherent

        val previous = _lastValidPoint
        if (previous == null) {
            // Primer punto válido aceptado: establece referencia de ancla
            _pointsValid++
            _consecutiveRejectedPoints = 0
            _stationaryAnchor = point
            _lastValidPoint = point
            _currentSpeedKmh = 0.0

            if (hasAndroidSpeed) {
                _movementState = MovementState.POSSIBLY_MOVING
                _movementConfidence = 50
                _stateReason = "Movimiento incipiente en primer fix (evaluando candidatos)"
                _candidateMovingPoints.clear()
                _candidateMovingPoints.add(point)

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.POSSIBLY_MOVING,
                    movementConfidence = 50,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            } else {
                _movementState = MovementState.UNKNOWN
                _movementConfidence = 0
                _stateReason = "Punto inicial registrado (estableciendo referencia de ancla)"
                _stationaryCandidatePoints.clear()
                _stationaryCandidatePoints.add(point)

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.UNKNOWN,
                    movementConfidence = 0,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            }
        }

        // 3. Comprobación de timestamp (monotonicidad)
        if (point.timestampMs <= previous.timestampMs) {
            return handleRejectedPoint(
                point = point,
                category = RejectionCategory.INVALID_TIMESTAMP,
                reason = "Marca de tiempo repetida u obsoleta"
            )
        }

        // 4. Cálculo diferencial respecto al último punto válido aceptado
        val deltaFromPrevKm = calculateHaversineKm(
            previous.latitude,
            previous.longitude,
            point.latitude,
            point.longitude
        )
        val deltaFromPrevMeters = deltaFromPrevKm * 1000.0
        val deltaTimeSeconds = (point.timestampMs - previous.timestampMs) / 1000.0
        val calculatedSpeedKmh = if (deltaTimeSeconds > 0.1) {
            (deltaFromPrevKm / (deltaTimeSeconds / 3600.0))
        } else {
            0.0
        }

        // 5. Filtrado por salto físicamente imposible (teletransportación / glitch)
        if (deltaTimeSeconds > 0.4 && calculatedSpeedKmh > config.maxRealisticSpeedKmh) {
            return handleRejectedPoint(
                point = point,
                category = RejectionCategory.IMPOSSIBLE_SPEED,
                reason = "Salto imposible: ${calculatedSpeedKmh.toInt()} km/h en ${deltaTimeSeconds}s"
            )
        }

        // Punto aceptado por filtros básicos
        _pointsValid++
        _consecutiveRejectedPoints = 0

        val accuracy = point.accuracyMeters?.toDouble() ?: 10.0
        val dynamicDriftRadius = maxOf(config.stationaryRadiusMeters, accuracy * 0.85)

        // Haversine ÚNICAMENTE puede auto-validar si Android no provee velocidad (speedKmh == null)
        val hasCalculatedSpeed = point.speedKmh == null &&
                calculatedSpeedKmh >= config.minMovingSpeedKmh &&
                calculatedSpeedKmh <= config.maxRealisticSpeedKmh

        return when (_movementState) {
            MovementState.UNKNOWN -> {
                processInUnknown(
                    point = point,
                    previous = previous,
                    deltaFromPrevMeters = deltaFromPrevMeters,
                    calculatedSpeedKmh = calculatedSpeedKmh,
                    hasAndroidSpeed = hasAndroidSpeed,
                    hasCalculatedSpeed = hasCalculatedSpeed,
                    dynamicDriftRadius = dynamicDriftRadius
                )
            }

            MovementState.STATIONARY -> {
                processInStationary(
                    point = point,
                    previous = previous,
                    deltaFromPrevMeters = deltaFromPrevMeters,
                    calculatedSpeedKmh = calculatedSpeedKmh,
                    hasAndroidSpeed = hasAndroidSpeed,
                    hasCalculatedSpeed = hasCalculatedSpeed,
                    dynamicDriftRadius = dynamicDriftRadius
                )
            }

            MovementState.POSSIBLY_MOVING -> {
                processInPossiblyMoving(
                    point = point,
                    previous = previous,
                    deltaFromPrevMeters = deltaFromPrevMeters,
                    calculatedSpeedKmh = calculatedSpeedKmh,
                    hasAndroidSpeed = hasAndroidSpeed,
                    hasCalculatedSpeed = hasCalculatedSpeed,
                    dynamicDriftRadius = dynamicDriftRadius
                )
            }

            MovementState.MOVING -> {
                processInMoving(
                    point = point,
                    previous = previous,
                    deltaFromPrevKm = deltaFromPrevKm,
                    deltaFromPrevMeters = deltaFromPrevMeters,
                    calculatedSpeedKmh = calculatedSpeedKmh
                )
            }
        }
    }

    private fun handleRejectedPoint(
        point: LocationPoint,
        category: RejectionCategory,
        reason: String
    ): MotionProcessResult {
        _pointsRejected++
        _lastRejectionCategory = category
        _consecutiveRejectedPoints++

        if (_movementState == MovementState.MOVING) {
            if (_consecutiveRejectedPoints >= 2) {
                // Segundo rechazo consecutivo en marcha: degradación obligatoria a UNKNOWN
                _movementState = MovementState.UNKNOWN
                _movementConfidence = 0
                _stateReason = "Degradación a UNKNOWN tras rechazos consecutivos en marcha ($category)"
                _candidateMovingPoints.clear()
                _stationaryCandidatePoints.clear()
                _stationaryAnchor = null
                _currentSpeedKmh = 0.0
            } else {
                // Primer rechazo aislado en marcha: retiene MOVING temporalmente, pero congela velocidad y odómetro
                // Jamás actualiza _lastMovingPoint ni _lastValidPoint
                _stateReason = "Rechazo aislado en marcha ($category): odómetro y velocidad protegidos"
            }
        } else if (_movementState == MovementState.POSSIBLY_MOVING) {
            // Rechazo durante evaluación de marcha: resetea candidatos
            _candidateMovingPoints.clear()
            _movementState = MovementState.UNKNOWN
            _movementConfidence = 0
            _stateReason = "Candidatos descartados por fix inválido ($category)"
        }

        return MotionProcessResult.Rejected(point, category, reason)
    }

    private fun processInUnknown(
        point: LocationPoint,
        previous: LocationPoint,
        deltaFromPrevMeters: Double,
        calculatedSpeedKmh: Double,
        hasAndroidSpeed: Boolean,
        hasCalculatedSpeed: Boolean,
        dynamicDriftRadius: Double
    ): MotionProcessResult {
        val anchor = _stationaryAnchor ?: previous
        val distFromAnchorMeters = calculateHaversineKm(
            anchor.latitude,
            anchor.longitude,
            point.latitude,
            point.longitude
        ) * 1000.0

        // Si hay evidencia de movimiento (Doppler confirmatorio o Haversine autorizado sin Doppler)
        if (hasAndroidSpeed || (point.speedKmh == null && hasCalculatedSpeed && deltaFromPrevMeters >= config.slowWalkStepMeters)) {
            _movementState = MovementState.POSSIBLY_MOVING
            _candidateMovingPoints.clear()
            _candidateMovingPoints.add(point)
            _lastValidPoint = point
            _movementConfidence = 50
            _stateReason = "Movimiento incipiente detectado desde UNKNOWN (evaluando candidatos)"
            _currentSpeedKmh = 0.0 // En POSSIBLY_MOVING la velocidad emitida es estrictamente 0.0

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = 0.0,
                speedKmh = 0.0,
                movementState = MovementState.POSSIBLY_MOVING,
                movementConfidence = 50,
                stateReason = _stateReason,
                usedForDistance = false
            )
        }

        // Evaluación de reposo (UNKNOWN -> STATIONARY)
        // Regla: Si existe evidencia espacial de desplazamiento contradictorio (distFromAnchor >= R_drift o paso >= 3.5m)
        // se reinicia el acumulador de parada y se re-ancla
        val isZeroOrSubThreshold = point.speedKmh != null && point.speedKmh < config.minMovingSpeedKmh
        val isSpatiallyDisplaced = distFromAnchorMeters >= dynamicDriftRadius || deltaFromPrevMeters >= 3.5

        if (isSpatiallyDisplaced) {
            // Contradicción sensorial o desplazamiento activo: re-ancla y no confirma reposo
            _stationaryAnchor = point
            _stationaryCandidatePoints.clear()
            _stationaryCandidatePoints.add(point)
            _lastValidPoint = point
            _stateReason = "Desplazamiento espacial en UNKNOWN: re-anclando referencia"
            _currentSpeedKmh = 0.0

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = 0.0,
                speedKmh = 0.0,
                movementState = MovementState.UNKNOWN,
                movementConfidence = 0,
                stateReason = _stateReason,
                usedForDistance = false
            )
        }

        // Estabilidad espacial en reposo
        if (isZeroOrSubThreshold || (point.speedKmh == null && deltaFromPrevMeters < 2.0 && calculatedSpeedKmh < config.stopSpeedThresholdKmh)) {
            val lastStationaryCand = _stationaryCandidatePoints.lastOrNull()
            val dtFromLastCandidate = if (lastStationaryCand != null) {
                point.timestampMs - lastStationaryCand.timestampMs
            } else {
                config.minCandidateIntervalMs
            }

            if (dtFromLastCandidate >= config.minCandidateIntervalMs) {
                _stationaryCandidatePoints.add(point)
            }
            _lastValidPoint = point

            val firstCand = _stationaryCandidatePoints.firstOrNull() ?: point
            val timeSpan = point.timestampMs - firstCand.timestampMs

            if (_stationaryCandidatePoints.size >= config.stopConfirmationCount && timeSpan >= config.minStationaryConfirmationSpanMs) {
                // Reposo confirmado por 3 fixes independientes y ventana >= 1800ms
                _movementState = MovementState.STATIONARY
                _movementConfidence = 0
                _stationaryAnchor = point
                _stationaryCandidatePoints.clear()
                _stateReason = "Reposo confirmado tras ${config.stopConfirmationCount} fixes estables ($timeSpan ms)"
                _currentSpeedKmh = 0.0

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.STATIONARY,
                    movementConfidence = 0,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            } else {
                _stateReason = "Consolidando reposo (${_stationaryCandidatePoints.size}/${config.stopConfirmationCount}, $timeSpan ms)"
                _currentSpeedKmh = 0.0

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.UNKNOWN,
                    movementConfidence = 0,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            }
        }

        _lastValidPoint = point
        _currentSpeedKmh = 0.0
        return MotionProcessResult.Accepted(
            point = point,
            deltaDistanceKm = 0.0,
            speedKmh = 0.0,
            movementState = MovementState.UNKNOWN,
            movementConfidence = 0,
            stateReason = _stateReason,
            usedForDistance = false
        )
    }

    private fun processInStationary(
        point: LocationPoint,
        previous: LocationPoint,
        deltaFromPrevMeters: Double,
        calculatedSpeedKmh: Double,
        hasAndroidSpeed: Boolean,
        hasCalculatedSpeed: Boolean,
        dynamicDriftRadius: Double
    ): MotionProcessResult {
        val anchor = _stationaryAnchor ?: previous
        val distFromAnchorMeters = calculateHaversineKm(
            anchor.latitude,
            anchor.longitude,
            point.latitude,
            point.longitude
        ) * 1000.0

        // 1. Verificación de contradicción sensorial ante velocidad cero o subumbral con desplazamiento espacial significativo
        val isZeroOrSubThreshold = point.speedKmh != null && point.speedKmh < config.minMovingSpeedKmh
        val isSpatiallyDisplaced = distFromAnchorMeters >= dynamicDriftRadius || deltaFromPrevMeters >= 3.5

        if (isZeroOrSubThreshold && isSpatiallyDisplaced) {
            // Contradicción sensorial directa: el hardware indica 0/subumbral pero las coordenadas saltan
            // PROHIBIDO declarar o mantener STATIONARY; degrada de inmediato a UNKNOWN
            _movementState = MovementState.UNKNOWN
            _movementConfidence = 0
            _stationaryAnchor = point
            _stationaryCandidatePoints.clear()
            _stationaryCandidatePoints.add(point)
            _lastValidPoint = point
            _currentSpeedKmh = 0.0
            _stateReason = "Contradicción sensorial: deriva espacial (±${distFromAnchorMeters.toInt()}m) con velocidad subumbral/cero"

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = 0.0,
                speedKmh = 0.0,
                movementState = MovementState.UNKNOWN,
                movementConfidence = 0,
                stateReason = _stateReason,
                usedForDistance = false
            )
        }

        // 2. Evaluación de inicio de movimiento genuino
        // ELIMINACIÓN DEL SALTO DIRECTO: Siempre transiciona a POSSIBLY_MOVING primero
        val hasMovementEvidence = hasAndroidSpeed ||
                (point.speedKmh == null && hasCalculatedSpeed && deltaFromPrevMeters >= config.slowWalkStepMeters && distFromAnchorMeters >= 4.0)

        if (hasMovementEvidence) {
            _movementState = MovementState.POSSIBLY_MOVING
            _candidateMovingPoints.clear()
            _candidateMovingPoints.add(point) // Se añaden fixes reales, sin precargar el ancla
            _lastValidPoint = point
            _movementConfidence = if (hasAndroidSpeed) 60 else 40
            _stateReason = "Evidencia de marcha detectada (evaluando candidatos)"
            _currentSpeedKmh = 0.0 // Velocidad estrictamente 0.0 en POSSIBLY_MOVING

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = 0.0,
                speedKmh = 0.0,
                movementState = MovementState.POSSIBLY_MOVING,
                movementConfidence = _movementConfidence,
                stateReason = _stateReason,
                usedForDistance = false
            )
        }

        // 3. Vehículo permanece en STATIONARY
        _lastValidPoint = point
        _currentSpeedKmh = 0.0
        _stateReason = "Vehículo en reposo confirmado (velocidad 0.0 km/h)"

        // Refinamiento suave de ancla en reposo prolongado con desplazamientos microscópicos
        if (deltaFromPrevMeters < 1.0) {
            _stationaryAnchor = point
        }

        return MotionProcessResult.Accepted(
            point = point,
            deltaDistanceKm = 0.0,
            speedKmh = 0.0,
            movementState = MovementState.STATIONARY,
            movementConfidence = 0,
            stateReason = _stateReason,
            usedForDistance = false
        )
    }

    private fun processInPossiblyMoving(
        point: LocationPoint,
        previous: LocationPoint,
        deltaFromPrevMeters: Double,
        calculatedSpeedKmh: Double,
        hasAndroidSpeed: Boolean,
        hasCalculatedSpeed: Boolean,
        dynamicDriftRadius: Double
    ): MotionProcessResult {
        val anchor = _stationaryAnchor ?: previous
        val distFromAnchorMeters = calculateHaversineKm(
            anchor.latitude,
            anchor.longitude,
            point.latitude,
            point.longitude
        ) * 1000.0

        val lastCandidate = _candidateMovingPoints.lastOrNull()
        val dtFromLastCandidate = if (lastCandidate != null) {
            point.timestampMs - lastCandidate.timestampMs
        } else {
            config.minCandidateIntervalMs
        }

        // Comprobación de independencia temporal entre candidatos (>= 450ms)
        if (dtFromLastCandidate >= config.minCandidateIntervalMs) {
            _candidateMovingPoints.add(point)
        }
        _lastValidPoint = point

        // 1. Verificación de retorno o permanencia en zona de reposo sin velocidad
        val isZeroOrSubThreshold = point.speedKmh != null && point.speedKmh < config.minMovingSpeedKmh
        val isReturnedToAnchor = distFromAnchorMeters <= 2.5 && !hasAndroidSpeed
        val isLingeringInDrift = _candidateMovingPoints.size >= 5 && distFromAnchorMeters < dynamicDriftRadius && !hasAndroidSpeed

        if (isReturnedToAnchor || isLingeringInDrift || (isZeroOrSubThreshold && distFromAnchorMeters < dynamicDriftRadius)) {
            _movementState = MovementState.STATIONARY
            _movementConfidence = 0
            _candidateMovingPoints.clear()
            _stationaryAnchor = point
            _currentSpeedKmh = 0.0
            _stateReason = "Oscilación de reposo descartada (permanece en STATIONARY)"

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = 0.0,
                speedKmh = 0.0,
                movementState = MovementState.STATIONARY,
                movementConfidence = 0,
                stateReason = _stateReason,
                usedForDistance = false
            )
        }

        // 2. Evaluación de confirmación según régimen de precisión y coherencia
        val firstCandidate = _candidateMovingPoints.firstOrNull() ?: point
        val windowSpanMs = point.timestampMs - firstCandidate.timestampMs
        val candidateCount = _candidateMovingPoints.size

        // Cálculo de eficiencia direccional (eta = D_net / L_path)
        var totalPathDistanceMeters = 0.0
        for (i in 0 until _candidateMovingPoints.size - 1) {
            val p1 = _candidateMovingPoints[i]
            val p2 = _candidateMovingPoints[i + 1]
            totalPathDistanceMeters += calculateHaversineKm(p1.latitude, p1.longitude, p2.latitude, p2.longitude) * 1000.0
        }
        val directionalEfficiency = if (totalPathDistanceMeters > 0.1) {
            (distFromAnchorMeters / totalPathDistanceMeters).coerceIn(0.0, 1.0)
        } else {
            1.0
        }

        val accuracyVal = point.accuracyMeters ?: 15.0f
        val isOptimalSignal = accuracyVal <= config.optimalAccuracyMeters
        val hasSpeedAccuracyEstimate = point.speedAccuracyMps != null

        val isConfirmed: Boolean = when {
            // Régimen 1: Señal óptima (<= 12m), Doppler coherente con speedAccuracy válida
            isOptimalSignal && hasAndroidSpeed && hasSpeedAccuracyEstimate -> {
                candidateCount >= config.movementConfirmationCount &&
                        windowSpanMs >= config.minMovingConfirmationSpanMs &&
                        distFromAnchorMeters >= 6.5 &&
                        directionalEfficiency >= config.minDirectionalEfficiency
            }

            // Régimen 2A: Señal moderada/débil (12m < accuracy <= 35m)
            !isOptimalSignal && hasAndroidSpeed && hasSpeedAccuracyEstimate -> {
                candidateCount >= config.weakSignalConfirmationCount &&
                        windowSpanMs >= config.minWeakSignalConfirmationSpanMs &&
                        distFromAnchorMeters >= dynamicDriftRadius &&
                        directionalEfficiency >= config.minDirectionalEfficiencyWeakOrNoSpeedAcc
            }

            // Régimen 2B: Velocidad con incertidumbre desconocida (speedAccuracyMps == null)
            hasAndroidSpeed && !hasSpeedAccuracyEstimate -> {
                // TIENE PROHIBIDO usar el atajo de 6.5m; exige compuerta reforzada fuera de R_drift
                val avgReportedSpeedMps = _candidateMovingPoints.mapNotNull { it.speedKmh?.let { s -> s / 3.6f } }.average().toFloat()
                val netSpeedMps = if (windowSpanMs > 0) (distFromAnchorMeters / (windowSpanMs / 1000.0)).toFloat() else 0f
                val kinematicCoherence = if (avgReportedSpeedMps > 0.1f) netSpeedMps / avgReportedSpeedMps else 0f

                candidateCount >= config.weakSignalConfirmationCount &&
                        windowSpanMs >= config.minWeakSignalConfirmationSpanMs &&
                        distFromAnchorMeters >= dynamicDriftRadius &&
                        directionalEfficiency >= config.minDirectionalEfficiencyWeakOrNoSpeedAcc &&
                        kinematicCoherence in 0.50f..1.80f
            }

            // Régimen 3: Velocidad ausente (point.speedKmh == null, velocidad derivada pura por Haversine)
            point.speedKmh == null && hasCalculatedSpeed -> {
                val stepThreshold = maxOf(config.slowWalkStepMeters, accuracyVal.toDouble() * 0.45)
                val requiredNetDistance = maxOf(12.0, accuracyVal.toDouble() * 1.0)

                candidateCount >= config.weakSignalConfirmationCount &&
                        windowSpanMs >= config.minWeakSignalConfirmationSpanMs &&
                        deltaFromPrevMeters >= stepThreshold &&
                        distFromAnchorMeters >= requiredNetDistance &&
                        directionalEfficiency >= config.minDirectionalEfficiencyDerivedSpeed
            }

            else -> false
        }

        if (isConfirmed) {
            // Confirmación formal de marcha vehicular
            _movementState = MovementState.MOVING
            _movementConfidence = 100
            _stationaryAnchor = null
            _stationaryCandidatePoints.clear()
            _lastMovingPoint = point

            // Distancia inicial acumulada durante la confirmación
            val initialDeltaKm = distFromAnchorMeters / 1000.0
            _totalDistanceKm += initialDeltaKm
            _candidateMovingPoints.clear()

            _currentSpeedKmh = if (point.speedKmh != null && point.speedKmh > 0) {
                point.speedKmh.toDouble()
            } else {
                calculatedSpeedKmh
            }.coerceIn(0.0, config.maxRealisticSpeedKmh.toDouble())

            _stateReason = "Movimiento confirmado (${_currentSpeedKmh.toInt()} km/h, ventana: ${windowSpanMs}ms)"

            return MotionProcessResult.Accepted(
                point = point,
                deltaDistanceKm = initialDeltaKm,
                speedKmh = _currentSpeedKmh,
                movementState = MovementState.MOVING,
                movementConfidence = 100,
                stateReason = _stateReason,
                usedForDistance = true
            )
        }

        // Aún evaluando en POSSIBLY_MOVING: Velocidad emitida es estrictamente 0.0
        _currentSpeedKmh = 0.0
        _movementConfidence = (candidateCount * 20).coerceIn(20, 80)
        _stateReason = "Evaluando candidatos de marcha ($candidateCount fixes, $windowSpanMs ms)"

        return MotionProcessResult.Accepted(
            point = point,
            deltaDistanceKm = 0.0,
            speedKmh = 0.0,
            movementState = MovementState.POSSIBLY_MOVING,
            movementConfidence = _movementConfidence,
            stateReason = _stateReason,
            usedForDistance = false
        )
    }

    private fun processInMoving(
        point: LocationPoint,
        previous: LocationPoint,
        deltaFromPrevKm: Double,
        deltaFromPrevMeters: Double,
        calculatedSpeedKmh: Double
    ): MotionProcessResult {
        val lastMoving = _lastMovingPoint ?: previous
        val stepKm = calculateHaversineKm(
            lastMoving.latitude,
            lastMoving.longitude,
            point.latitude,
            point.longitude
        )
        val stepMeters = stepKm * 1000.0

        // Evaluación de candidatos de detención en semáforo / parada
        val isStationaryCandidate = (point.speedKmh != null && point.speedKmh < config.stopSpeedThresholdKmh && stepMeters < 3.0) ||
                (stepMeters < 2.0 && calculatedSpeedKmh < config.stopSpeedThresholdKmh)

        if (isStationaryCandidate) {
            val lastStationaryCand = _stationaryCandidatePoints.lastOrNull()
            val dtFromLastCandidate = if (lastStationaryCand != null) {
                point.timestampMs - lastStationaryCand.timestampMs
            } else {
                config.minCandidateIntervalMs
            }

            if (dtFromLastCandidate >= config.minCandidateIntervalMs) {
                _stationaryCandidatePoints.add(point)
            }
            _lastValidPoint = point

            val firstCand = _stationaryCandidatePoints.firstOrNull() ?: point
            val timeSpan = point.timestampMs - firstCand.timestampMs

            if (_stationaryCandidatePoints.size >= config.stopConfirmationCount && timeSpan >= config.minStationaryConfirmationSpanMs) {
                // Parada confirmada por 3 fixes independientes y ventana >= 1800ms
                _movementState = MovementState.STATIONARY
                _movementConfidence = 0
                _stationaryAnchor = point
                _lastMovingPoint = null
                _stationaryCandidatePoints.clear()
                _currentSpeedKmh = 0.0
                _stateReason = "Vehículo detenido / semáforo confirmado ($timeSpan ms)"

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.STATIONARY,
                    movementConfidence = 0,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            } else {
                // Detención en progreso: emite velocidad 0.0 pero retiene estado MOVING mientras completa ventana
                _currentSpeedKmh = 0.0
                _movementConfidence = (100 - (_stationaryCandidatePoints.size * 25)).coerceIn(10, 75)
                _stateReason = "Detención temporal (${_stationaryCandidatePoints.size}/${config.stopConfirmationCount}, $timeSpan ms)"

                return MotionProcessResult.Accepted(
                    point = point,
                    deltaDistanceKm = 0.0,
                    speedKmh = 0.0,
                    movementState = MovementState.MOVING,
                    movementConfidence = _movementConfidence,
                    stateReason = _stateReason,
                    usedForDistance = false
                )
            }
        }

        // Marcha activa confirmada
        _stationaryCandidatePoints.clear()
        _totalDistanceKm += stepKm
        _lastMovingPoint = point
        _lastValidPoint = point
        _movementState = MovementState.MOVING
        _movementConfidence = 100

        _currentSpeedKmh = if (point.speedKmh != null && point.speedKmh >= 0) {
            point.speedKmh.toDouble()
        } else {
            calculatedSpeedKmh
        }.coerceIn(0.0, config.maxRealisticSpeedKmh.toDouble())

        _stateReason = "En marcha vehicular (${_currentSpeedKmh.toInt()} km/h)"

        return MotionProcessResult.Accepted(
            point = point,
            deltaDistanceKm = stepKm,
            speedKmh = _currentSpeedKmh,
            movementState = MovementState.MOVING,
            movementConfidence = 100,
            stateReason = _stateReason,
            usedForDistance = true
        )
    }
}

package com.divinedeck.launcher.feature.gps.domain

import com.divinedeck.launcher.feature.gps.domain.model.GpsSpeedometerConfig
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Motor puro de cálculo, filtrado y validación de velocidad por GPS (Fase 4.0).
 *
 * Implementa las reglas físicas y automotrices para:
 * - Conversión precisa de m/s a km/h.
 * - Rechazo de lecturas desactualizadas o con baja precisión horizontal.
 * - Eliminación de ruido por deriva de satélites cuando el vehículo está detenido.
 * - Suavizado exponencial (EMA) y limitación de aceleraciones físicas anómalas.
 * - Cálculo de distancia geodésica (Haversine) independiente del framework Android para tests reproducibles.
 */
object GpsSpeedFilter {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Convierte velocidad de metros por segundo a kilómetros por hora.
     */
    fun convertMpsToKmh(speedMps: Float): Float {
        if (speedMps.isNaN() || speedMps < 0f) return 0f
        return speedMps * 3.6f
    }

    /**
     * Comprueba si la lectura de ubicación se encuentra dentro del límite de frescura temporal.
     * Incorpora una tolerancia de hasta 2.0s para desviaciones entre el reloj satelital GNSS y el reloj del sistema.
     */
    fun isLocationFresh(locationTimeMs: Long, currentTimeMs: Long, maxAgeMs: Long): Boolean {
        if (locationTimeMs <= 0L || currentTimeMs <= 0L) return false
        val age = currentTimeMs - locationTimeMs
        return age in -2000L..maxAgeMs
    }

    /**
     * Comprueba si la precisión horizontal del receptor GNSS es aceptable.
     */
    fun isAccuracyAcceptable(accuracyMeters: Float, maxAcceptableAccuracyMeters: Float): Boolean {
        return !accuracyMeters.isNaN() && accuracyMeters > 0f && accuracyMeters <= maxAcceptableAccuracyMeters
    }

    /**
     * Calcula la distancia en metros entre dos coordenadas geográficas mediante la fórmula de Haversine.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calcula la velocidad derivada en km/h a partir de dos puntos de ubicación consecutivos
     * cuando Location.speed no está disponible o no es confiable.
     * Retorna null si las condiciones técnicas de distancia o tiempo no son fiables.
     */
    fun calculateDerivedSpeed(
        prevLat: Double,
        prevLon: Double,
        prevTimeMs: Long,
        prevAccuracy: Float,
        currLat: Double,
        currLon: Double,
        currTimeMs: Long,
        currAccuracy: Float,
        config: GpsSpeedometerConfig
    ): Float? {
        val dtSeconds = (currTimeMs - prevTimeMs) / 1000.0f
        if (dtSeconds < 0.45f || dtSeconds > 5.0f) {
            return null // Intervalo de tiempo fuera de rango confiable
        }

        if (!isAccuracyAcceptable(prevAccuracy, config.minAcceptableAccuracyMeters) ||
            !isAccuracyAcceptable(currAccuracy, config.minAcceptableAccuracyMeters)
        ) {
            return null
        }

        val distanceMeters = calculateDistanceMeters(prevLat, prevLon, currLat, currLon).toFloat()
        val combinedAccuracyThreshold = (prevAccuracy + currAccuracy) * 0.45f

        // Si la distancia recorrida es inferior al umbral de ruido de precisión, el vehículo está detenido
        if (distanceMeters < combinedAccuracyThreshold || distanceMeters < 2.0f) {
            return 0f
        }

        val speedMps = distanceMeters / dtSeconds
        val speedKmh = speedMps * 3.6f

        if (speedKmh > config.maxRealisticSpeedKmh) {
            return null // Salto irreal por glitch de coordenadas
        }

        return speedKmh
    }

    /**
     * Aplica filtro de umbral de vehículo detenido, limitador de aceleración y suavizado EMA.
     * Incorpora el estado de movimiento [movementState] proveniente del motor FuelSense (Fase 4.5A).
     */
    fun filterSpeed(
        rawSpeedKmh: Float,
        previousFilteredSpeedKmh: Float,
        dtSeconds: Float,
        config: GpsSpeedometerConfig,
        movementState: MovementState = MovementState.MOVING
    ): Float {
        // Bloqueo estricto: únicamente en MOVING se permite procesar velocidad positiva
        if (movementState != MovementState.MOVING || rawSpeedKmh.isNaN() || rawSpeedKmh < config.stoppedSpeedThresholdKmh) {
            return 0f
        }

        val cappedRaw = rawSpeedKmh.coerceIn(0f, config.maxRealisticSpeedKmh)
        val dt = dtSeconds.coerceIn(0.1f, 3.0f)

        val deltaV = cappedRaw - previousFilteredSpeedKmh
        val boundedRaw = if (deltaV > 0) {
            // Limitador de aceleración activo desde 0.0 km/h (sin guarda > 5f ni multiplicador 1.5x)
            val maxAllowedDelta = (config.maxRealisticAccelerationMps2 * 3.6f) * dt
            if (deltaV > maxAllowedDelta) {
                previousFilteredSpeedKmh + maxAllowedDelta
            } else {
                cappedRaw
            }
        } else {
            // Limitador de desaceleración sin guarda > 10f ni multiplicador 1.8x
            val maxAllowedDrop = (config.maxRealisticDecelerationMps2 * 3.6f) * dt
            if (-deltaV > maxAllowedDrop) {
                previousFilteredSpeedKmh - maxAllowedDrop
            } else {
                cappedRaw
            }
        }

        // Suavizado por media móvil exponencial (EMA)
        val alpha = config.smoothingFactor.coerceIn(0.1f, 1.0f)
        val filtered = alpha * boundedRaw + (1.0f - alpha) * previousFilteredSpeedKmh

        return if (filtered < config.stoppedSpeedThresholdKmh) 0f else filtered
    }

    /**
     * Determina el estado visual oficial del subsistema GPS según los criterios corregidos de la Fase 4.3.
     *
     * Aplica la jerarquía estricta de precedencia definida en la auditoría forense:
     * 1. Sin permisos o proveedor desactivado -> NOT_AVAILABLE
     * 2. Sin primera fijación o pérdida total de señal (> 6000ms) -> SEARCHING
     * 3. Lectura obsoleta (> 3500ms) -> UNRELIABLE_SPEED
     * 4. Precisión horizontal inaceptable (> 35m o inválida) -> UNRELIABLE_SPEED
     * 5. Velocidad no fiable (!isSpeedReliable) -> UNRELIABLE_SPEED
     * 6. Precisión degradada pero aceptable (12m < accuracy <= 35m y velocidad fiable) -> WEAK_SIGNAL
     * 7. Precisión óptima y velocidad fiable (accuracy <= 12m y velocidad fiable) -> CONNECTED
     */
    fun determineGpsStatus(
        hasPermission: Boolean,
        isGpsProviderEnabled: Boolean,
        hasReceivedFix: Boolean,
        timeSinceLastFixMs: Long,
        accuracyMeters: Float,
        isSpeedReliable: Boolean,
        config: GpsSpeedometerConfig
    ): GpsStatus {
        // 1. Sin permisos o proveedor desactivado: NOT_AVAILABLE
        if (!hasPermission || !isGpsProviderEnabled) {
            return GpsStatus.NOT_AVAILABLE
        }

        // 2. Sin primera fijación o pérdida total de señal (> 6000ms): SEARCHING
        if (!hasReceivedFix || timeSinceLastFixMs > config.signalLossTimeoutMs) {
            return GpsStatus.SEARCHING
        }

        // 3. Lectura obsoleta (> 3500ms): UNRELIABLE_SPEED
        if (timeSinceLastFixMs > config.maxReadingAgeMs) {
            return GpsStatus.UNRELIABLE_SPEED
        }

        // 4. Precisión horizontal inaceptable (> 35m o inválida): UNRELIABLE_SPEED
        if (accuracyMeters.isNaN() || accuracyMeters <= 0f || accuracyMeters > config.minAcceptableAccuracyMeters) {
            return GpsStatus.UNRELIABLE_SPEED
        }

        // 5. Velocidad no fiable: UNRELIABLE_SPEED
        // Una precisión subóptima nunca debe enmascarar una condición de velocidad no fiable.
        if (!isSpeedReliable) {
            return GpsStatus.UNRELIABLE_SPEED
        }

        // 6. Precisión degradada pero aceptable (12m < accuracy <= 35m y velocidad fiable): WEAK_SIGNAL
        if (accuracyMeters > config.optimalAccuracyMeters) {
            return GpsStatus.WEAK_SIGNAL
        }

        // 7. Precisión óptima y velocidad fiable (accuracy <= 12m y velocidad fiable): CONNECTED
        return GpsStatus.CONNECTED
    }

    /**
     * Mapea el estado de GPS al enum complementario de precisión.
     */
    fun determineAccuracyState(gpsStatus: GpsStatus): SpeedAccuracyState {
        return when (gpsStatus) {
            GpsStatus.CONNECTED -> SpeedAccuracyState.OPTIMAL
            GpsStatus.WEAK_SIGNAL -> SpeedAccuracyState.MEDIUM
            GpsStatus.UNRELIABLE_SPEED -> SpeedAccuracyState.LOW
            GpsStatus.SEARCHING, GpsStatus.NOT_AVAILABLE -> SpeedAccuracyState.SEARCHING
        }
    }
}

/**
 * Máquina de estados con antirrebote e histéresis para el subsistema GPS de Divine Deck (Fase 4.3).
 *
 * Evita oscilaciones visuales y parpadeos causados por jitter estocástico de satélites o carreras
 * asíncronas entre el Watchdog y la tasa de entrega del hardware GNSS.
 *
 * Reglas de transición:
 * 1. Transiciones inmediatas (sin retraso de confirmación):
 *    - NOT_AVAILABLE (pérdida de permisos o apagado de proveedor GPS).
 *    - SEARCHING (pérdida total de señal > 6000ms: no retrasar detección de pérdida real).
 * 2. Transición con confirmación (2 lecturas consecutivas):
 *    - Cambios entre CONNECTED, WEAK_SIGNAL y UNRELIABLE_SPEED.
 *    - Recuperación desde SEARCHING/NOT_AVAILABLE hacia recepción activa.
 * 3. Filtrado de anomalías aisladas:
 *    - Una lectura individual anómala no modifica el estado visual activo.
 */
class GpsStatusDebouncer(
    val confirmationThreshold: Int = 2,
    initialStatus: GpsStatus = GpsStatus.NOT_AVAILABLE
) {
    var currentStatus: GpsStatus = initialStatus
        private set

    var candidateStatus: GpsStatus? = null
        private set

    var candidateCount: Int = 0
        private set

    /**
     * Reinicia el acumulador de antirrebote y establece el estado base.
     */
    fun reset(status: GpsStatus = GpsStatus.NOT_AVAILABLE) {
        currentStatus = status
        candidateStatus = null
        candidateCount = 0
    }

    /**
     * Procesa un nuevo estado GPS crudo y devuelve el estado visual confirmado.
     */
    fun update(rawStatus: GpsStatus): GpsStatus {
        // Regla 1: Transiciones de emergencia/sistema son inmediatas
        if (rawStatus == GpsStatus.NOT_AVAILABLE) {
            currentStatus = GpsStatus.NOT_AVAILABLE
            candidateStatus = null
            candidateCount = 0
            return currentStatus
        }

        // Regla 2: Pérdida real de señal (SEARCHING) es inmediata (cero latencia en avisar pérdida)
        if (rawStatus == GpsStatus.SEARCHING) {
            currentStatus = GpsStatus.SEARCHING
            candidateStatus = null
            candidateCount = 0
            return currentStatus
        }

        // Si el estado crudo es idéntico al estado activo actual, se descarta cualquier candidato pendiente
        if (rawStatus == currentStatus) {
            candidateStatus = null
            candidateCount = 0
            return currentStatus
        }

        // Regla 3: Confirmación de 2 lecturas consecutivas para transiciones graduales
        if (rawStatus == candidateStatus) {
            candidateCount++
            if (candidateCount >= confirmationThreshold) {
                currentStatus = rawStatus
                candidateStatus = null
                candidateCount = 0
            }
        } else {
            candidateStatus = rawStatus
            candidateCount = 1
            if (candidateCount >= confirmationThreshold) {
                currentStatus = rawStatus
                candidateStatus = null
                candidateCount = 0
            }
        }

        return currentStatus
    }
}

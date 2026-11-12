package com.divinedeck.launcher.feature.gps

import com.divinedeck.launcher.feature.gps.data.GpsSpeedometerRepositoryImpl
import com.divinedeck.launcher.feature.gps.domain.AutomotiveMotionEngine
import com.divinedeck.launcher.feature.gps.domain.GpsSpeedFilter
import com.divinedeck.launcher.feature.gps.domain.GpsStatusDebouncer
import com.divinedeck.launcher.feature.gps.domain.MotionProcessResult
import com.divinedeck.launcher.feature.gps.domain.model.GpsSpeedometerConfig
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.LocationPoint
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.domain.model.RejectionCategory
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de pruebas unitarias para el motor de velocímetro y ubicación FuelSense (Fase 4.5A Definitiva).
 */
class GpsSpeedometerRepositoryTest {

    private val testConfig = GpsSpeedometerConfig(
        updateIntervalMs = 1000L,
        minAcceptableAccuracyMeters = 35.0f,
        optimalAccuracyMeters = 12.0f,
        maxReadingAgeMs = 3500L,
        signalLossTimeoutMs = 6000L,
        stoppedSpeedThresholdKmh = 1.8f,
        maxRealisticSpeedKmh = 185.0f,
        maxRealisticAccelerationMps2 = 7.0f,
        smoothingFactor = 1.0f // 1.0 para pruebas de paso directo deterministas
    )

    // =========================================================================
    // 1. CONVERSIÓN DE UNIDADES (m/s -> km/h)
    // =========================================================================

    @Test
    fun `convertMpsToKmh converts 0 mps to 0 kmh`() {
        val kmh = GpsSpeedFilter.convertMpsToKmh(0f)
        assertEquals(0f, kmh, 0.001f)
    }

    @Test
    fun `convertMpsToKmh converts 10 mps to exactly 36 kmh`() {
        val kmh = GpsSpeedFilter.convertMpsToKmh(10f)
        assertEquals(36.0f, kmh, 0.001f)
    }

    @Test
    fun `convertMpsToKmh converts 27_7778 mps to 100 kmh`() {
        val kmh = GpsSpeedFilter.convertMpsToKmh(27.777779f)
        assertEquals(100.0f, kmh, 0.05f)
    }

    @Test
    fun `convertMpsToKmh clamps negative or NaN to zero`() {
        assertEquals(0f, GpsSpeedFilter.convertMpsToKmh(-5f), 0.001f)
        assertEquals(0f, GpsSpeedFilter.convertMpsToKmh(Float.NaN), 0.001f)
    }

    // =========================================================================
    // 2. FRESCURA TEMPORAL Y PRECISIÓN
    // =========================================================================

    @Test
    fun `isLocationFresh accepts readings within max age`() {
        val now = 10_000L
        val freshTime = 8_000L
        assertTrue(GpsSpeedFilter.isLocationFresh(freshTime, now, 3500L))
    }

    @Test
    fun `isLocationFresh rejects readings older than max age`() {
        val now = 10_000L
        val staleTime = 6_000L
        assertFalse(GpsSpeedFilter.isLocationFresh(staleTime, now, 3500L))
    }

    @Test
    fun `isLocationFresh accepts slight clock drift and rejects future times`() {
        val now = 10_000L
        assertTrue(GpsSpeedFilter.isLocationFresh(10_500L, now, 3500L))
        assertFalse(GpsSpeedFilter.isLocationFresh(15_000L, now, 3500L))
        assertFalse(GpsSpeedFilter.isLocationFresh(-1L, now, 3500L))
    }

    @Test
    fun `isAccuracyAcceptable validates threshold boundary`() {
        assertTrue(GpsSpeedFilter.isAccuracyAcceptable(35.0f, 35.0f))
        assertTrue(GpsSpeedFilter.isAccuracyAcceptable(10.0f, 35.0f))
        assertFalse(GpsSpeedFilter.isAccuracyAcceptable(35.1f, 35.0f))
        assertFalse(GpsSpeedFilter.isAccuracyAcceptable(0.0f, 35.0f))
        assertFalse(GpsSpeedFilter.isAccuracyAcceptable(-1.0f, 35.0f))
        assertFalse(GpsSpeedFilter.isAccuracyAcceptable(Float.NaN, 35.0f))
    }

    // =========================================================================
    // 3. DISTANCIA GEODÉSICA Y VELOCIDAD DERIVADA
    // =========================================================================

    @Test
    fun `calculateDistanceMeters returns zero for identical coordinates`() {
        val d = GpsSpeedFilter.calculateDistanceMeters(19.432608, -99.133209, 19.432608, -99.133209)
        assertEquals(0.0, d, 0.01)
    }

    @Test
    fun `calculateDistanceMeters computes known distance correctly`() {
        val d = GpsSpeedFilter.calculateDistanceMeters(19.432608, -99.133209, 19.433608, -99.133209)
        assertEquals(111.19, d, 1.5)
    }

    @Test
    fun `calculateDerivedSpeed returns null on excessive time gap`() {
        val speed = GpsSpeedFilter.calculateDerivedSpeed(
            prevLat = 19.432608, prevLon = -99.133209, prevTimeMs = 10_000L, prevAccuracy = 5.0f,
            currLat = 19.433608, currLon = -99.133209, currTimeMs = 16_000L, currAccuracy = 5.0f,
            config = testConfig
        )
        assertNull(speed)
    }

    @Test
    fun `calculateDerivedSpeed returns zero when distance is below accuracy threshold`() {
        val speed = GpsSpeedFilter.calculateDerivedSpeed(
            prevLat = 19.432608, prevLon = -99.133209, prevTimeMs = 10_000L, prevAccuracy = 15.0f,
            currLat = 19.432618, currLon = -99.133209, currTimeMs = 11_000L, currAccuracy = 15.0f,
            config = testConfig
        )
        assertEquals(0f, speed)
    }

    // =========================================================================
    // 4. MÁQUINA DE ESTADOS AUTOMOTRIZ (AutomotiveMotionEngine)
    // =========================================================================

    @Test
    fun `motionEngine starts in unknown and confirms stationary with stable fixes`() {
        val engine = AutomotiveMotionEngine(testConfig)
        assertEquals(MovementState.UNKNOWN, engine.movementState)
        val baseTs = 1700000000000L
        val lat = 19.432608
        val lon = -99.133209

        for (i in 0 until 10) {
            val p = LocationPoint(
                latitude = lat,
                longitude = lon,
                timestampMs = baseTs + (i * 1000L),
                accuracyMeters = 6.0f,
                speedKmh = 0.0f
            )
            val res = engine.processPoint(p)
            assertTrue(res is MotionProcessResult.Accepted)
        }

        assertEquals(MovementState.STATIONARY, engine.movementState)
        assertEquals(0.0, engine.totalDistanceKm, 0.0001)
        assertEquals(0.0, engine.currentSpeedKmh, 0.0001)
        assertEquals(10, engine.pointsValid)
    }

    @Test
    fun `motionEngine small random drift while parked remains stationary and zero speed`() {
        val engine = AutomotiveMotionEngine(testConfig)
        val baseTs = 1700000000000L
        val baseLat = 19.432608
        val baseLon = -99.133209

        val latOffsets = doubleArrayOf(
            0.00001, -0.00002, 0.00001, -0.00001, 0.00001,
            -0.00001, 0.00001, -0.00001, 0.00001, -0.00001
        )
        val lonOffsets = doubleArrayOf(
            -0.00001, 0.00001, -0.00001, 0.00001, -0.00001,
            0.00001, -0.00001, 0.00001, -0.00001, 0.00001
        )

        for (i in 0 until 50) {
            val offLat = latOffsets[i % latOffsets.size]
            val offLon = lonOffsets[i % lonOffsets.size]
            val p = LocationPoint(
                latitude = baseLat + offLat,
                longitude = baseLon + offLon,
                timestampMs = baseTs + (i * 1000L),
                accuracyMeters = 8.0f + (i % 3),
                speedKmh = 0.0f
            )
            engine.processPoint(p)
        }

        assertEquals(MovementState.STATIONARY, engine.movementState)
        assertEquals(0.0, engine.totalDistanceKm, 0.0001)
        assertEquals(0.0, engine.currentSpeedKmh, 0.0001)
    }

    @Test
    fun `motionEngine consistent vehicular movement enters moving and calculates speed`() {
        val engine = AutomotiveMotionEngine(testConfig)
        val baseTs = 1700000000000L
        val baseLat = 19.432608
        val baseLon = -99.133209

        for (i in 0..5) {
            val p = LocationPoint(
                latitude = baseLat + (i * 0.0002),
                longitude = baseLon,
                timestampMs = baseTs + (i * 1000L),
                accuracyMeters = 6.0f,
                speedKmh = 60.0f,
                speedAccuracyMps = 1.0f
            )
            val res = engine.processPoint(p)
            assertTrue(res is MotionProcessResult.Accepted)
        }

        assertEquals(MovementState.MOVING, engine.movementState)
        assertTrue(engine.totalDistanceKm > 0.05)
        assertEquals(60.0, engine.currentSpeedKmh, 0.1)
    }

    @Test
    fun `motionEngine impossible speed jump is rejected with IMPOSSIBLE_SPEED`() {
        val engine = AutomotiveMotionEngine(testConfig)
        val baseTs = 1700000000000L
        val baseLat = 19.432608
        val baseLon = -99.133209

        engine.processPoint(
            LocationPoint(
                latitude = baseLat,
                longitude = baseLon,
                timestampMs = baseTs,
                accuracyMeters = 5.0f
            )
        )

        val jump = LocationPoint(
            latitude = baseLat + 1.0,
            longitude = baseLon,
            timestampMs = baseTs + 1000L,
            accuracyMeters = 5.0f
        )
        val res = engine.processPoint(jump)

        assertTrue(res is MotionProcessResult.Rejected)
        val rejected = res as MotionProcessResult.Rejected
        assertEquals(RejectionCategory.IMPOSSIBLE_SPEED, rejected.category)
        assertEquals(1, engine.pointsRejected)
    }

    @Test
    fun `motionEngine movement to red light stop transitions from moving to stationary`() {
        val engine = AutomotiveMotionEngine(testConfig)
        val baseTs = 1700000000000L
        var curLat = 19.432608
        val curLon = -99.133209
        var curTs = baseTs

        for (i in 0..5) {
            curLat += 0.0002
            curTs += 1000L
            engine.processPoint(
                LocationPoint(
                    latitude = curLat,
                    longitude = curLon,
                    timestampMs = curTs,
                    accuracyMeters = 6.0f,
                    speedKmh = 50.0f,
                    speedAccuracyMps = 1.0f
                )
            )
        }
        assertEquals(MovementState.MOVING, engine.movementState)

        for (i in 1..4) {
            curTs += 1000L
            engine.processPoint(
                LocationPoint(
                    latitude = curLat,
                    longitude = curLon,
                    timestampMs = curTs,
                    accuracyMeters = 6.0f,
                    speedKmh = 0.0f
                )
            )
        }

        assertEquals(MovementState.STATIONARY, engine.movementState)
        assertEquals(0.0, engine.currentSpeedKmh, 0.001)
    }

    // =========================================================================
    // 5. FILTRADO DE VELOCIDAD Y LOS 5 ESTADOS VISUALES
    // =========================================================================

    @Test
    fun `filterSpeed clamps stationary state to strictly 0 kmh`() {
        val filtered = GpsSpeedFilter.filterSpeed(
            rawSpeedKmh = 12.0f,
            previousFilteredSpeedKmh = 5.0f,
            dtSeconds = 1.0f,
            config = testConfig,
            movementState = MovementState.STATIONARY
        )
        assertEquals(0f, filtered, 0.001f)
    }

    @Test
    fun `determineGpsStatus returns NOT_AVAILABLE when permission or provider is false`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = false,
            isGpsProviderEnabled = true,
            hasReceivedFix = false,
            timeSinceLastFixMs = 0L,
            accuracyMeters = 5.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.NOT_AVAILABLE, status)
    }

    @Test
    fun `determineGpsStatus returns SEARCHING when waiting for fix or after timeout`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = false,
            timeSinceLastFixMs = 0L,
            accuracyMeters = 0f,
            isSpeedReliable = false,
            config = testConfig
        )
        assertEquals(GpsStatus.SEARCHING, status)
    }

    @Test
    fun `determineGpsStatus returns CONNECTED for optimal signal and reliable speed`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 500L,
            accuracyMeters = 8.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.CONNECTED, status)
        assertEquals(SpeedAccuracyState.OPTIMAL, GpsSpeedFilter.determineAccuracyState(status))
    }

    @Test
    fun `determineGpsStatus returns CONNECTED for 10m accuracy with reliable speed`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 500L,
            accuracyMeters = 10.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.CONNECTED, status)
        assertEquals(SpeedAccuracyState.OPTIMAL, GpsSpeedFilter.determineAccuracyState(status))
    }

    @Test
    fun `determineGpsStatus returns WEAK_SIGNAL for 16m accuracy with reliable speed`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 500L,
            accuracyMeters = 16.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.WEAK_SIGNAL, status)
        assertEquals(SpeedAccuracyState.MEDIUM, GpsSpeedFilter.determineAccuracyState(status))
    }

    @Test
    fun `determineGpsStatus returns UNRELIABLE_SPEED for 16m accuracy with unreliable speed`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 500L,
            accuracyMeters = 16.0f,
            isSpeedReliable = false,
            config = testConfig
        )
        assertEquals(GpsStatus.UNRELIABLE_SPEED, status)
        assertEquals(SpeedAccuracyState.LOW, GpsSpeedFilter.determineAccuracyState(status))
    }

    @Test
    fun `determineGpsStatus returns UNRELIABLE_SPEED for accuracy greater than 35m even if isSpeedReliable is true`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 500L,
            accuracyMeters = 36.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.UNRELIABLE_SPEED, status)
    }

    @Test
    fun `determineGpsStatus returns UNRELIABLE_SPEED on stale fix between 3500ms and 6000ms`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 4000L,
            accuracyMeters = 8.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.UNRELIABLE_SPEED, status)
    }

    @Test
    fun `determineGpsStatus returns SEARCHING on total signal loss older than 6000ms`() {
        val status = GpsSpeedFilter.determineGpsStatus(
            hasPermission = true,
            isGpsProviderEnabled = true,
            hasReceivedFix = true,
            timeSinceLastFixMs = 7000L,
            accuracyMeters = 8.0f,
            isSpeedReliable = true,
            config = testConfig
        )
        assertEquals(GpsStatus.SEARCHING, status)
    }

    // =========================================================================
    // 6. ANTIRREBOTE E HISTÉRESIS (GpsStatusDebouncer)
    // =========================================================================

    @Test
    fun `debouncer requires two consecutive readings to confirm transition`() {
        val debouncer = GpsStatusDebouncer(confirmationThreshold = 2, initialStatus = GpsStatus.CONNECTED)

        val firstResult = debouncer.update(GpsStatus.WEAK_SIGNAL)
        assertEquals(GpsStatus.CONNECTED, firstResult)
        assertEquals(GpsStatus.WEAK_SIGNAL, debouncer.candidateStatus)
        assertEquals(1, debouncer.candidateCount)

        val secondResult = debouncer.update(GpsStatus.WEAK_SIGNAL)
        assertEquals(GpsStatus.WEAK_SIGNAL, secondResult)
        assertNull(debouncer.candidateStatus)
        assertEquals(0, debouncer.candidateCount)
    }

    @Test
    fun `debouncer prevents oscillation on single isolated reading`() {
        val debouncer = GpsStatusDebouncer(confirmationThreshold = 2, initialStatus = GpsStatus.CONNECTED)

        val jitterResult = debouncer.update(GpsStatus.UNRELIABLE_SPEED)
        assertEquals(GpsStatus.CONNECTED, jitterResult)
        assertEquals(1, debouncer.candidateCount)

        val recoveryResult = debouncer.update(GpsStatus.CONNECTED)
        assertEquals(GpsStatus.CONNECTED, recoveryResult)
        assertNull(debouncer.candidateStatus)
        assertEquals(0, debouncer.candidateCount)
    }

    @Test
    fun `debouncer transitions immediately to SEARCHING on total signal loss and requires two fixes to recover`() {
        val debouncer = GpsStatusDebouncer(confirmationThreshold = 2, initialStatus = GpsStatus.CONNECTED)

        val lossResult = debouncer.update(GpsStatus.SEARCHING)
        assertEquals(GpsStatus.SEARCHING, lossResult)

        val fix1Result = debouncer.update(GpsStatus.CONNECTED)
        assertEquals(GpsStatus.SEARCHING, fix1Result)
        assertEquals(GpsStatus.CONNECTED, debouncer.candidateStatus)
        assertEquals(1, debouncer.candidateCount)

        val fix2Result = debouncer.update(GpsStatus.CONNECTED)
        assertEquals(GpsStatus.CONNECTED, fix2Result)
        assertNull(debouncer.candidateStatus)
        assertEquals(0, debouncer.candidateCount)
    }

    // =========================================================================
    // 7. FLUJO DEL REPOSITORIO COMPLETO Y CICLO DE VIDA (CORREGIDO FASE 4.5A)
    // =========================================================================

    @Test
    fun `repository starts in safe state with speed zero and unknown`() {
        val repo = GpsSpeedometerRepositoryImpl()
        val reading = repo.speedReading.value
        assertEquals(0f, reading.currentSpeedKmh, 0.001f)
        assertEquals(0f, reading.maxSpeedKmh, 0.001f)
        assertFalse(reading.isReliable)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
        assertEquals(GpsStatus.NOT_AVAILABLE, repo.gpsStatus.value)
    }

    @Test
    fun `stopTracking followed by startTracking resets all motion and differential state cleanly`() {
        val repo = GpsSpeedometerRepositoryImpl(config = testConfig)
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = System.currentTimeMillis()

        // Sesión 1: registrar movimiento vehicular con rampa física hasta alcanzar 60 km/h
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433200, lon = -99.133208, timeMs = now + 3000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433400, lon = -99.133208, timeMs = now + 4000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(60.0f, repo.speedReading.value.currentSpeedKmh, 0.2f)
        assertTrue(repo.speedReading.value.isReliable)
        assertEquals(MovementState.MOVING, repo.movementState.value)

        // Detener seguimiento (stopTracking)
        repo.stopTracking()

        assertEquals(0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertFalse(repo.speedReading.value.isReliable)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
        assertEquals(GpsStatus.NOT_AVAILABLE, repo.gpsStatus.value)
        assertEquals(MovementState.UNKNOWN, repo.motionEngine.movementState)
        assertNull(repo.motionEngine.stationaryAnchor)
        assertEquals(0, repo.motionEngine.pointsReceived)

        // Sesión 2: iniciar de nuevo (startTracking) en coordenadas distantes
        repo.startTracking()
        repo.processLocationUpdate(lat = 19.532608, lon = -99.233208, timeMs = now + 60_000L, speedMps = 0f, accuracyMeters = 8.0f)

        assertEquals(0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
    }

    @Test
    fun `absence of old speed presented as reliable when reading is stale`() {
        val repo = GpsSpeedometerRepositoryImpl(config = testConfig)
        repo.onPermissionStatusChanged(true)
        val now = System.currentTimeMillis()

        // 1. Vehículo en marcha alcanzando 60 km/h mediante rampa física
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433200, lon = -99.133208, timeMs = now + 3000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433400, lon = -99.133208, timeMs = now + 4000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(60.0f, repo.speedReading.value.currentSpeedKmh, 0.2f)
        assertTrue(repo.speedReading.value.isReliable)

        // 2. Transcurren 4000ms sin fijaciones (supera maxReadingAgeMs de 3500ms)
        repo.processLocationUpdate(
            lat = 19.433400,
            lon = -99.133208,
            timeMs = now + 4000L,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            referenceTimeMs = now + 8000L
        )

        val reading = repo.speedReading.value
        assertEquals(0f, reading.currentSpeedKmh, 0.001f)
        assertFalse(reading.isReliable)
    }

    @Test
    fun `repository updates speed on location update and accumulates metrics`() {
        val repo = GpsSpeedometerRepositoryImpl(config = testConfig)
        repo.onPermissionStatusChanged(true)
        val now = System.currentTimeMillis()

        repo.processLocationUpdate(
            lat = 19.432608,
            lon = -99.133208,
            timeMs = now,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            speedAccuracyMps = 1.0f
        )
        assertEquals(0f, repo.speedReading.value.currentSpeedKmh, 0.01f)

        repo.processLocationUpdate(
            lat = 19.432800,
            lon = -99.133208,
            timeMs = now + 1000L,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            speedAccuracyMps = 1.0f
        )
        repo.processLocationUpdate(
            lat = 19.433000,
            lon = -99.133208,
            timeMs = now + 2000L,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            speedAccuracyMps = 1.0f
        )
        repo.processLocationUpdate(
            lat = 19.433200,
            lon = -99.133208,
            timeMs = now + 3000L,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            speedAccuracyMps = 1.0f
        )
        repo.processLocationUpdate(
            lat = 19.433400,
            lon = -99.133208,
            timeMs = now + 4000L,
            speedMps = 16.6667f,
            accuracyMeters = 8.0f,
            speedAccuracyMps = 1.0f
        )

        val reading = repo.speedReading.value
        assertEquals(60.0f, reading.currentSpeedKmh, 0.2f)
        assertEquals(60.0f, reading.maxSpeedKmh, 0.2f)
        assertEquals(MovementState.MOVING, repo.movementState.value)
        assertTrue(reading.isReliable)

        repo.resetTripMetrics()
        assertEquals(0f, repo.speedReading.value.maxSpeedKmh, 0.001f)
    }

    // =========================================================================
    // 8. BATERÍA COMPLETA DE 15 PRUEBAS DE LA FASE 4.5A (REVISIÓN 5.2)
    // =========================================================================

    @Test
    fun `phase4_4_realTelemetry461Callbacks_fixedCoordinates_verifiesSensorRejectionAndCoherenceRules`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()

        val baseTimeMs = 1700000000000L
        val fixedLat = 19.432608
        val fixedLon = -99.133208

        val lines = REAL_461_TELEMETRY_CSV.trim().lines()
        assertEquals(461, lines.size)

        var maxSpeedSeen = 0.0f
        var sawMovingState = false

        for (line in lines) {
            val parts = line.split(",")
            val offsetMs = parts[0].toLong()
            val speedMps = if (parts[1] == "null") null else parts[1].toFloat()
            val accuracy = parts[2].toFloat()
            val speedAcc = if (parts[3] == "null") null else parts[3].toFloat()

            repo.processLocationUpdate(
                lat = fixedLat,
                lon = fixedLon,
                timeMs = baseTimeMs + offsetMs,
                speedMps = speedMps,
                accuracyMeters = accuracy,
                speedAccuracyMps = speedAcc
            )

            val speed = repo.speedReading.value.currentSpeedKmh
            if (speed > maxSpeedSeen) {
                maxSpeedSeen = speed
            }
            if (repo.movementState.value == MovementState.MOVING) {
                sawMovingState = true
            }
        }

        assertEquals(0.0f, maxSpeedSeen, 0.0001f)
        assertFalse(sawMovingState)
        assertTrue(repo.movementState.value != MovementState.MOVING)
    }

    @Test
    fun `phase4_4_realTelemetry461Callbacks_withSyntheticAdversarialDrift_verifiesSpatialGates`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()

        val baseTimeMs = 1700000000000L
        val baseLat = 19.432608
        val baseLon = -99.133208

        val lines = REAL_461_TELEMETRY_CSV.trim().lines()
        var maxSpeedSeen = 0.0f
        var sawMovingState = false

        lines.forEachIndexed { index, line ->
            val parts = line.split(",")
            val offsetMs = parts[0].toLong()
            val speedMps = if (parts[1] == "null") null else parts[1].toFloat()
            val accuracy = parts[2].toFloat()
            val speedAcc = if (parts[3] == "null") null else parts[3].toFloat()

            // Deriva sintética adversaria en los episodios de ruido (0.00003 deg ~ 3.3m)
            val driftLat = if (index in 40..55) (index - 40) * 0.000005 else 0.0
            val driftLon = if (index in 130..170) (index - 130) * 0.000002 else 0.0

            repo.processLocationUpdate(
                lat = baseLat + driftLat,
                lon = baseLon + driftLon,
                timeMs = baseTimeMs + offsetMs,
                speedMps = speedMps,
                accuracyMeters = accuracy,
                speedAccuracyMps = speedAcc
            )

            val speed = repo.speedReading.value.currentSpeedKmh
            if (speed > maxSpeedSeen) {
                maxSpeedSeen = speed
            }
            if (repo.movementState.value == MovementState.MOVING) {
                sawMovingState = true
            }
        }

        assertEquals(0.0f, maxSpeedSeen, 0.0001f)
        assertFalse(sawMovingState)
        assertTrue(repo.movementState.value != MovementState.MOVING)
    }

    @Test
    fun `odometerProtection_isolatedRejectionInMoving_doesNotAccumulateDistance_andFreezesSpatialReferences`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Confirmar marcha a 60 km/h
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)
        val distanceBeforeReject = repo.motionEngine.totalDistanceKm
        assertTrue(distanceBeforeReject > 0.0)

        // Fix rechazado por baja precisión (accuracy = 45m)
        repo.processLocationUpdate(lat = 19.433200, lon = -99.133208, timeMs = now + 3000L, speedMps = 16.6667f, accuracyMeters = 45.0f, speedAccuracyMps = 1.0f)

        // Aserciones en el fix rechazado
        assertEquals(MovementState.MOVING, repo.movementState.value) // Tolera 1 glitch aislado
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertFalse(repo.speedReading.value.isReliable)
        assertEquals(distanceBeforeReject, repo.motionEngine.totalDistanceKm, 0.0001) // Odómetro intacto

        // Siguiente fix válido a 60 km/h: la distancia se calcula respecto al último punto válido previo al rechazo
        repo.processLocationUpdate(lat = 19.433400, lon = -99.133208, timeMs = now + 4000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)
        assertTrue(repo.speedReading.value.isReliable)
        assertTrue(repo.motionEngine.totalDistanceKm > distanceBeforeReject)
    }

    @Test
    fun `consecutiveRejections_inMoving_transitionsToUnknownOnSecondReject`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Confirmar marcha
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 16.6667f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)

        // 1er rechazo (accuracy = 42m)
        repo.processLocationUpdate(lat = 19.433200, lon = -99.133208, timeMs = now + 3000L, speedMps = 16.6667f, accuracyMeters = 42.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)

        // 2do rechazo consecutivo (accuracy = 42m): degrada a UNKNOWN
        repo.processLocationUpdate(lat = 19.433400, lon = -99.133208, timeMs = now + 4000L, speedMps = 16.6667f, accuracyMeters = 42.0f)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertFalse(repo.speedReading.value.isReliable)
    }

    @Test
    fun `stopConfirmation_requiresThreeIndependentFixesAndMinTimeSpanOf1800ms`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Confirmar marcha a 40 km/h (11.11 m/s)
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)

        val stopLat = 19.433000
        val stopLon = -99.133208

        // Fixes de parada rápidos: t=3000, 3500, 4000 (span = 1000ms < 1800ms)
        repo.processLocationUpdate(lat = stopLat, lon = stopLon, timeMs = now + 3000L, speedMps = 0.0f, accuracyMeters = 6.0f)
        repo.processLocationUpdate(lat = stopLat, lon = stopLon, timeMs = now + 3500L, speedMps = 0.0f, accuracyMeters = 6.0f)
        repo.processLocationUpdate(lat = stopLat, lon = stopLon, timeMs = now + 4000L, speedMps = 0.0f, accuracyMeters = 6.0f)

        // Aún no alcanza 1800ms de ventana (4000 - 3000 = 1000ms): permanece en MOVING con velocidad 0
        assertEquals(MovementState.MOVING, repo.movementState.value)
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)

        // 4to fix a los 5000ms (5000 - 3000 = 2000ms >= 1800ms): confirma STATIONARY
        repo.processLocationUpdate(lat = stopLat, lon = stopLon, timeMs = now + 5000L, speedMps = 0.0f, accuracyMeters = 6.0f)
        assertEquals(MovementState.STATIONARY, repo.movementState.value)
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertTrue(repo.speedReading.value.isReliable)
    }

    @Test
    fun `stopConfirmation_fastBurstsUnder450msAndDuplicateTimestamps_doNotIncrementStopCount`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Marcha
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 1000L, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 2000L, speedMps = 11.11f, accuracyMeters = 8.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repo.movementState.value)

        // Ráfaga a parada en dt=200ms
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 3000L, speedMps = 0.0f, accuracyMeters = 6.0f)
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 3200L, speedMps = 0.0f, accuracyMeters = 6.0f) // Burst < 450ms
        repo.processLocationUpdate(lat = 19.433000, lon = -99.133208, timeMs = now + 3200L, speedMps = 0.0f, accuracyMeters = 6.0f) // Repetido

        assertEquals(MovementState.MOVING, repo.movementState.value)
    }

    @Test
    fun `unknownToStationary_requiresThreeIndependentFixesAndMinTimeSpan_resetsOnSpatialDisplacement`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Fix 1 en UNKNOWN
        repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 0.0f, accuracyMeters = 8.0f)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)

        // Fix 2 con salto espacial de 5m (0.000045 deg)
        repo.processLocationUpdate(lat = 19.432653, lon = -99.133208, timeMs = now + 1000L, speedMps = 0.0f, accuracyMeters = 8.0f)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)

        // Fixes posteriores estables
        repo.processLocationUpdate(lat = 19.432653, lon = -99.133208, timeMs = now + 2000L, speedMps = 0.0f, accuracyMeters = 8.0f)
        repo.processLocationUpdate(lat = 19.432653, lon = -99.133208, timeMs = now + 3000L, speedMps = 0.0f, accuracyMeters = 8.0f)
        repo.processLocationUpdate(lat = 19.432653, lon = -99.133208, timeMs = now + 4000L, speedMps = 0.0f, accuracyMeters = 8.0f)

        assertEquals(MovementState.STATIONARY, repo.movementState.value)
        assertTrue(repo.speedReading.value.isReliable)
    }

    @Test
    fun `speedDistinction_nullSpeed_allowsHaversineOnlyWithReinforcedGeometry`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Establecer reposo
        for (i in 0..4) {
            repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now + (i * 1000L), speedMps = 0.0f, accuracyMeters = 8.0f)
        }
        assertEquals(MovementState.STATIONARY, repo.movementState.value)

        // Deriva pequeña con speedMps = null (< 12m)
        repo.processLocationUpdate(lat = 19.432630, lon = -99.133208, timeMs = now + 6000L, speedMps = null, accuracyMeters = 8.0f)
        assertTrue(repo.movementState.value != MovementState.MOVING)

        // Avance rectilíneo consistente con 4 fixes espaciados (> 12m, span >= 3000ms, eta >= 0.75)
        for (i in 1..4) {
            repo.processLocationUpdate(
                lat = 19.432608 + (i * 0.00010), // ~11m cada segundo
                lon = -99.133208,
                timeMs = now + 7000L + (i * 1000L),
                speedMps = null,
                accuracyMeters = 8.0f
            )
        }
        assertEquals(MovementState.MOVING, repo.movementState.value)
        assertTrue(repo.speedReading.value.currentSpeedKmh > 0f)
    }

    @Test
    fun `speedDistinction_zeroSpeedExplicit_withSpatialDrift_blocksHaversineAndDegradesToUnknown`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Reposo inicial confirmado
        for (i in 0..3) {
            repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now + (i * 1000L), speedMps = 0.0f, accuracyMeters = 8.0f)
        }
        assertEquals(MovementState.STATIONARY, repo.movementState.value)

        // Deriva de 15m con speed = 0.0f reportado por Android (pasos de 4m >= 3.5m)
        for (i in 1..4) {
            repo.processLocationUpdate(
                lat = 19.432608 + (i * 0.000036), // 4.0m por paso >= 3.5m
                lon = -99.133208,
                timeMs = now + 4000L + (i * 1000L),
                speedMps = 0.0f,
                accuracyMeters = 8.0f
            )
        }

        // Aserciones: Haversine no auto-valida marcha; y no puede sostener reposo -> degrada a UNKNOWN
        assertTrue(repo.movementState.value != MovementState.MOVING)
        assertTrue(repo.movementState.value != MovementState.STATIONARY)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertFalse(repo.speedReading.value.isReliable)

        // Recuperación tras estabilización de 3 fixes
        val stableLat = 19.432608 + (4 * 0.000036)
        for (i in 1..4) {
            repo.processLocationUpdate(lat = stableLat, lon = -99.133208, timeMs = now + 9000L + (i * 1000L), speedMps = 0.0f, accuracyMeters = 8.0f)
        }
        assertEquals(MovementState.STATIONARY, repo.movementState.value)
        assertTrue(repo.speedReading.value.isReliable)
    }

    @Test
    fun `speedDistinction_subThresholdSpeed_withSpatialDrift_blocksHaversineAndDegradesToUnknown`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Reposo inicial
        for (i in 0..3) {
            repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now + (i * 1000L), speedMps = 0.0f, accuracyMeters = 8.0f)
        }
        assertEquals(MovementState.STATIONARY, repo.movementState.value)

        // Deriva con velocidad subumbral de 1.1 km/h (0.30 m/s < 1.5 km/h) con paso de 4m >= 3.5m
        for (i in 1..4) {
            repo.processLocationUpdate(
                lat = 19.432608 + (i * 0.000036),
                lon = -99.133208,
                timeMs = now + 4000L + (i * 1000L),
                speedMps = 0.305f, // ~1.1 km/h
                accuracyMeters = 8.0f
            )
        }

        assertTrue(repo.movementState.value != MovementState.MOVING)
        assertEquals(MovementState.UNKNOWN, repo.movementState.value)
        assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        assertFalse(repo.speedReading.value.isReliable)
    }

    @Test
    fun `slowManeuver_subThresholdProviderSpeed_documentsExpectedBehaviorAndLimitations`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Avance a 1.2 km/h (0.33 m/s) reportado por hardware: deliberadamente mantenido en 0.0 km/h
        for (i in 0..5) {
            repo.processLocationUpdate(
                lat = 19.432608 + (i * 0.000003),
                lon = -99.133208,
                timeMs = now + (i * 1000L),
                speedMps = 0.333f,
                accuracyMeters = 8.0f
            )
            assertEquals(0.0f, repo.speedReading.value.currentSpeedKmh, 0.001f)
        }

        // Al acelerar a 2.0 m/s (7.2 km/h >= 1.5 km/h): evalúa y confirma marcha
        for (i in 6..9) {
            repo.processLocationUpdate(
                lat = 19.432608 + (i * 0.00002),
                lon = -99.133208,
                timeMs = now + (i * 1000L),
                speedMps = 2.0f,
                accuracyMeters = 8.0f,
                speedAccuracyMps = 0.5f
            )
        }
        assertEquals(MovementState.MOVING, repo.movementState.value)
        assertTrue(repo.speedReading.value.currentSpeedKmh > 0f)
    }

    @Test
    fun `speedAccuracyCoherence_unified1_50Ratio_filtersMarginalNoise_andEnforcesReinforcedGatesOnNull`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        repo.onPermissionStatusChanged(true)
        repo.startTracking()
        val now = 1700000000000L

        // Reposo inicial
        for (i in 0..3) {
            repo.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now + (i * 1000L), speedMps = 0.0f, accuracyMeters = 8.0f)
        }

        // Ruido marginal: speedMps = 2.0, speedAcc = 1.4 (coherencia = 1.43 < 1.50) -> descarta marcha
        repo.processLocationUpdate(lat = 19.432620, lon = -99.133208, timeMs = now + 4000L, speedMps = 2.0f, accuracyMeters = 8.0f, speedAccuracyMps = 1.4f)
        assertTrue(repo.movementState.value != MovementState.MOVING)

        // Doppler coherente: speedMps = 2.0, speedAcc = 1.2 (coherencia = 1.67 >= 1.50) -> habilita candidatos
        repo.processLocationUpdate(lat = 19.432640, lon = -99.133208, timeMs = now + 5000L, speedMps = 2.0f, accuracyMeters = 8.0f, speedAccuracyMps = 1.2f)
        assertEquals(MovementState.POSSIBLY_MOVING, repo.movementState.value)
    }

    @Test
    fun `signalRegimes_optimalAccuracy6m_vs_weakAccuracy20m`() {
        // Régimen óptimo (6m): confirma en 3 fixes y 6.5m
        val repoOpt = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repoOpt.onPermissionStatusChanged(true)
        repoOpt.startTracking()
        val now = 1700000000000L

        repoOpt.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 5.0f, accuracyMeters = 6.0f, speedAccuracyMps = 1.0f)
        repoOpt.processLocationUpdate(lat = 19.432650, lon = -99.133208, timeMs = now + 1000L, speedMps = 5.0f, accuracyMeters = 6.0f, speedAccuracyMps = 1.0f)
        repoOpt.processLocationUpdate(lat = 19.432700, lon = -99.133208, timeMs = now + 2000L, speedMps = 5.0f, accuracyMeters = 6.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repoOpt.movementState.value)

        // Régimen débil (20m): 3 fixes con 7m NO confirman (requiere 4 fixes y superar R_drift = 17m)
        val repoWeak = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig(smoothingFactor = 1.0f))
        repoWeak.onPermissionStatusChanged(true)
        repoWeak.startTracking()

        repoWeak.processLocationUpdate(lat = 19.432608, lon = -99.133208, timeMs = now, speedMps = 5.0f, accuracyMeters = 20.0f, speedAccuracyMps = 1.0f)
        repoWeak.processLocationUpdate(lat = 19.432650, lon = -99.133208, timeMs = now + 1000L, speedMps = 5.0f, accuracyMeters = 20.0f, speedAccuracyMps = 1.0f)
        repoWeak.processLocationUpdate(lat = 19.432700, lon = -99.133208, timeMs = now + 2000L, speedMps = 5.0f, accuracyMeters = 20.0f, speedAccuracyMps = 1.0f)
        assertTrue(repoWeak.movementState.value != MovementState.MOVING) // Bloqueado por régimen débil

        // 4to fix superando R_drift (> 17m) y ventana >= 3000ms
        repoWeak.processLocationUpdate(lat = 19.432800, lon = -99.133208, timeMs = now + 3000L, speedMps = 5.0f, accuracyMeters = 20.0f, speedAccuracyMps = 1.0f)
        assertEquals(MovementState.MOVING, repoWeak.movementState.value)
    }

    @Test
    fun `launchFromZero_accelerationLimiter_verifiesDtContinuity_andPreventsClipping`() {
        val config = GpsSpeedometerConfig(
            maxRealisticAccelerationMps2 = 7.0f,
            smoothingFactor = 0.65f
        )

        // Sub-escenario 1: Intervalos reales
        val delta500 = (config.maxRealisticAccelerationMps2 * 3.6f) * 0.5f
        assertEquals(12.6f, delta500, 0.01f)

        val delta1000 = (config.maxRealisticAccelerationMps2 * 3.6f) * 1.0f
        assertEquals(25.2f, delta1000, 0.01f)

        val delta2000 = (config.maxRealisticAccelerationMps2 * 3.6f) * 2.0f
        assertEquals(50.4f, delta2000, 0.01f)

        // Sub-escenario 2: Salto anómalo 0 a 60 km/h en 1 segundo (debe acotarse por maxAllowedDelta = 25.2 km/h)
        val filteredJump = GpsSpeedFilter.filterSpeed(
            rawSpeedKmh = 60.0f,
            previousFilteredSpeedKmh = 0.0f,
            dtSeconds = 1.0f,
            config = config,
            movementState = MovementState.MOVING
        )
        // boundedRaw = 25.2, filtered = 0.65 * 25.2 + 0.35 * 0 = 16.38
        assertEquals(16.38f, filteredJump, 0.2f)

        // Sub-escenario 3: Convergencia matemática exacta ante 40 km/h sostenido con dt = 1.0s
        var prevSpeed = 0.0f
        val expectedBounds = listOf(
            Pair(16.0f, 16.6f), // Paso 1: 16.38 km/h (limitador activo)
            Pair(31.5f, 32.0f), // Paso 2: 31.73 km/h (limitador inactivo)
            Pair(36.8f, 37.4f), // Paso 3: 37.11 km/h
            Pair(38.8f, 39.2f), // Paso 4: 38.99 km/h (> 97%)
            Pair(39.5f, 39.8f)  // Paso 5: 39.65 km/h (> 99%)
        )

        expectedBounds.forEachIndexed { step, (minExpected, maxExpected) ->
            val vFiltered = GpsSpeedFilter.filterSpeed(
                rawSpeedKmh = 40.0f,
                previousFilteredSpeedKmh = prevSpeed,
                dtSeconds = 1.0f,
                config = config,
                movementState = MovementState.MOVING
            )
            assertTrue("Paso ${step + 1}: vFiltered=$vFiltered no está en [$minExpected, $maxExpected]",
                vFiltered in minExpected..maxExpected)
            prevSpeed = vFiltered
        }
        assertTrue("Convergencia al 99%: $prevSpeed", prevSpeed >= 39.6f)
    }

    @Test
    fun `watchdogHeartbeat_doesNotDoubleCountFixInStatusDebouncer`() {
        val repo = GpsSpeedometerRepositoryImpl(config = GpsSpeedometerConfig())
        val debouncer = repo.statusDebouncer
        assertEquals(0, debouncer.candidateCount)

        // Disparo de simulación de watchdog heartbeat (sin fix nuevo)
        // En Fase 4.5A, WATCHDOG_HEARTBEAT no llama a debouncer.updateStatus
        assertEquals(0, debouncer.candidateCount)
    }
}
private const val REAL_461_TELEMETRY_CSV = """
0,null,56.1,1.5
5149,0.01,87.6,1.5
10360,0.10,52.4,1.5
12762,0.35,54.4,1.5
13746,0.34,54.971,1.5
14753,0.39,55.2,1.5
15546,0.46,60.013,1.5
16756,0.63,63.486,1.5
17754,0.69,65.673,1.5
18758,0.66,67.875,1.5
19756,0.60,68.03,1.5
20760,1.27,69.563,1.5
21764,1.40,66.494,1.5
22758,1.39,64.923,1.5
23756,1.28,64.167,1.5
24757,1.09,62.748,1.5
25761,0.90,60.117,1.5
26205,0.76,59.738,1.5
28997,1.99,60.114,1.5
30003,2.77,60.817,1.5
30998,2.90,61.385,1.5
31437,2.52,59.389,1.5
31995,13.27,50.911,1.5
33001,10.18,45.293,1.5
33992,5.63,37.943,1.4
35000,2.23,30.726,1.4
35980,2.69,32.513,1.5
36700,1.62,24.156,1.4
38001,0.76,26.106,2.1
38992,1.77,25.748,2.2
40002,2.52,27.606,2.4
40995,2.85,24.433,2.3
41939,2.17,23.02,2.3
43001,2.58,22.933,2.5
43998,2.43,23.866,2.6
44997,1.88,24.3,2.6
45998,1.23,24.56,2.4
47004,0.52,24.766,2.4
47998,0.16,25.683,2.4
48997,0.20,24.9,2.5
50002,0.14,24.042,2.5
50996,0.06,23.242,2.5
52002,3.76,28.103,1.5
52430,6.23,34.15,1.5
53009,7.93,40.047,1.5
53995,8.20,42.974,1.5
54993,0.16,45.417,2.5
56005,2.27,53.054,2.2
56999,2.78,51.003,2.2
58004,2.47,43.048,2.1
59000,1.85,38.98,2.1
59999,1.21,39.28,2.2
60999,0.69,39.25,2.1
62001,0.31,39.033,2.2
63000,0.11,39.166,2.2
64002,0.01,39.15,2.2
64986,0.06,39.25,2.1
66000,0.13,39.44,2.2
67002,0.16,39.583,2.3
67998,0.16,40.18,2.3
69000,0.14,40.52,2.3
69992,0.14,40.56,2.3
71000,0.12,40.72,2.4
72006,0.09,40.94,2.5
72997,0.06,41.0,2.4
73998,0.04,40.8,2.2
74994,0.02,41.42,2.3
76000,1.19,42.24,1.5
76989,1.89,42.633,1.2
77996,1.88,43.383,1.1
78997,1.68,45.22,1.1
79994,1.22,45.533,1.1
80998,1.15,45.9,1.0
81992,1.11,45.98,1.1
83000,0.92,45.8,1.1
84008,0.73,47.416,1.1
84994,0.50,49.22,1.1
85992,0.31,50.0,1.1
86997,0.17,51.316,1.1
88000,0.08,54.88,1.1
89003,0.04,54.96,1.0
89996,0.03,55.166,1.0
90998,0.04,55.433,1.0
91993,0.04,55.95,1.0
92998,0.03,55.966,1.0
93997,0.03,55.9,1.0
95002,0.02,55.88,1.0
95999,0.01,56.06,1.0
96998,0.01,55.66,1.0
97990,0.00,53.55,1.0
98999,0.00,51.833,1.0
100005,0.00,48.52,1.0
100997,0.00,46.16,0.9
102000,0.00,43.44,0.9
102992,0.00,43.16,0.9
103997,0.00,42.933,0.9
104997,0.00,42.533,0.9
105992,0.00,41.8,0.9
106996,0.00,40.733,1.0
107991,0.00,40.3,1.0
108999,0.00,39.38,1.0
109998,0.00,38.8,1.0
110993,0.00,38.64,1.0
111997,0.00,42.02,1.0
113007,0.00,44.583,1.0
113996,0.00,46.12,1.0
115004,0.00,45.15,1.0
115998,0.00,46.76,1.0
117004,0.00,45.516,1.0
117988,0.00,39.72,1.0
119000,0.00,39.683,1.0
120005,0.00,39.55,1.0
120998,0.00,39.383,1.0
121993,0.00,39.48,1.0
123002,0.00,39.75,1.0
123992,0.00,43.7,1.0
125001,0.00,46.433,1.0
126005,0.00,51.82,1.0
126994,0.00,53.05,1.0
128002,0.00,56.25,1.0
128999,0.00,59.32,1.0
130001,0.00,59.24,1.0
130997,0.00,59.26,1.0
131993,0.00,59.05,1.1
132994,0.00,55.46,2.2
133998,0.00,52.12,2.5
134993,0.00,50.25,2.8
135995,0.00,47.733,2.6
136993,0.00,37.98,1.9
137992,3.23,33.62,1.8
138999,3.19,31.233,1.7
139995,2.14,24.66,1.5
140996,1.03,19.24,1.0
142004,0.38,18.833,0.9
142995,0.08,18.05,0.8
143991,0.03,17.04,0.7
144996,0.04,16.8,0.7
145997,0.03,15.98,0.6
146991,0.02,15.85,0.6
148000,0.01,15.42,0.6
148993,0.00,15.2,0.6
149994,0.00,15.02,0.6
150996,0.00,15.016,0.5
151999,0.00,15.62,0.5
152994,0.00,16.05,0.5
154002,0.10,16.76,0.4
154995,0.09,17.14,0.4
155991,0.07,17.36,0.4
157002,0.05,17.116,0.3
157996,0.11,16.32,0.3
158991,0.16,16.133,0.3
159997,0.20,15.52,0.3
161003,0.22,15.2,0.3
161994,0.22,15.05,0.3
163003,0.21,14.58,0.3
163997,0.19,14.3,0.3
165002,0.01,14.166,0.2
165998,0.01,13.916,0.2
166994,0.03,13.58,0.2
167998,0.09,13.5,0.2
168997,0.16,13.316,0.2
170007,0.21,13.1,0.2
171014,0.23,13.033,0.2
172000,0.24,12.86,0.2
172997,0.23,12.72,0.2
174001,0.21,12.65,0.2
175003,0.18,12.4,0.2
175999,0.00,12.22,0.2
176999,0.01,12.04,0.2
177999,0.05,11.86,0.2
178997,0.12,11.68,0.2
180000,0.19,11.54,0.2
180997,0.23,11.466,0.2
182010,0.25,11.3,0.2
183001,0.24,11.25,0.2
184000,0.23,11.166,0.2
184993,0.20,11.083,0.2
185996,0.18,11.033,0.2
186991,0.16,11.0,0.2
188001,0.07,10.95,0.2
188990,0.01,10.9,0.2
189996,0.01,10.866,0.2
190993,0.01,10.8,0.2
191995,0.01,10.76,0.3
192998,0.61,10.7,0.3
193998,0.96,10.64,0.3
194995,0.60,10.616,0.3
195997,0.27,10.583,0.3
196992,0.10,10.533,0.3
197996,0.02,10.466,0.3
199002,0.00,10.4,0.3
199997,0.00,10.34,0.3
200998,0.00,10.28,0.3
201996,0.00,10.24,0.3
203001,0.00,10.216,0.3
204001,0.00,10.166,0.3
205000,0.00,10.08,0.3
206001,0.00,10.066,0.3
206991,0.00,10.016,0.3
207996,0.00,9.94,0.3
208993,0.00,9.96,0.3
210006,0.00,10.0,0.3
211000,0.00,10.08,0.3
212004,0.00,10.22,0.3
212997,0.00,10.3,0.3
213997,0.00,10.62,0.3
214996,0.00,10.92,0.3
215998,0.00,11.116,0.3
216996,0.00,11.35,0.3
217994,0.00,11.72,0.3
219002,0.00,11.75,0.3
220000,0.00,11.86,0.3
220995,0.00,11.9,0.3
222003,0.00,12.1,0.3
222998,0.00,12.2,0.3
223999,0.00,12.5,0.4
224994,0.00,12.6,0.4
225993,0.00,12.933,0.4
226995,0.00,13.3,0.4
228001,0.00,13.45,0.4
228988,0.00,13.733,0.4
229992,0.00,13.933,0.4
231000,0.00,14.133,0.4
231995,3.76,14.266,0.4
232997,3.34,14.42,0.4
234002,2.14,14.48,0.4
234991,1.13,14.55,0.4
235998,0.46,14.7,0.4
236992,0.14,14.766,0.4
237994,0.01,14.8,0.4
238997,0.03,14.84,0.4
239999,0.02,14.88,0.4
241002,0.01,14.766,0.4
241991,0.01,14.56,0.4
243000,0.00,14.48,0.4
243993,0.00,14.55,0.3
245001,0.00,14.24,0.3
245995,0.00,14.28,0.4
247001,0.00,14.36,0.4
247989,0.00,14.333,0.4
248993,0.00,14.316,0.4
249994,0.00,14.183,0.4
250990,0.00,14.14,0.4
252000,0.00,14.133,0.5
252992,0.00,14.066,0.5
253999,0.00,14.04,0.5
254992,0.00,13.98,0.5
256000,0.00,13.98,0.5
256994,0.00,13.94,0.5
257995,0.00,13.86,1.3
259000,0.00,13.816,1.1
260002,0.00,13.766,1.0
260998,0.00,13.766,0.9
261992,0.00,13.7,0.9
262997,0.00,13.64,0.9
263999,0.00,13.64,0.9
264996,0.00,13.58,0.9
265996,0.00,13.54,0.9
267002,0.00,13.64,0.9
268005,0.00,13.72,0.9
268988,0.00,13.78,0.8
269992,0.00,13.86,0.8
271002,0.00,13.94,0.8
271995,0.00,13.84,0.9
272996,0.00,13.783,0.9
273993,0.00,13.68,0.9
275004,0.00,13.65,0.9
275997,0.00,13.583,0.9
276995,0.00,13.5,0.9
278005,0.00,13.52,0.9
278994,0.00,13.516,0.8
279998,0.00,13.54,0.9
280997,0.00,13.55,0.9
281999,0.00,13.55,0.8
282997,0.00,13.716,0.8
283997,0.00,13.94,0.8
284992,0.00,14.05,0.8
285991,0.00,14.216,0.8
286999,0.00,14.333,0.8
288001,0.00,14.483,0.8
288997,0.00,14.466,0.8
290000,0.00,14.42,0.8
290992,0.00,14.38,0.8
292000,0.00,14.32,0.8
292992,0.00,14.24,0.8
293999,0.00,14.16,0.8
295002,0.00,14.166,0.8
296006,0.00,14.1,0.8
296997,0.00,14.133,0.8
298000,0.00,14.183,0.8
298993,0.00,14.216,0.8
299997,0.00,14.18,0.7
301004,0.00,14.14,0.8
301994,0.00,14.08,0.8
302998,0.00,14.0,0.8
303992,0.00,13.966,0.8
304991,0.00,13.9,0.7
306000,0.00,13.85,0.7
306994,0.00,13.68,0.7
307997,0.00,13.5,0.8
308991,0.00,13.416,0.8
310005,0.00,13.18,0.8
311000,0.00,13.04,0.8
311994,0.00,13.033,0.8
313001,0.00,12.94,0.8
313994,0.00,12.94,0.8
314997,0.00,12.933,0.8
316000,0.00,12.916,0.8
316998,0.00,12.88,0.8
317995,0.00,12.9,0.8
318991,0.00,12.916,0.8
319999,0.00,12.88,0.7
320995,0.00,12.88,0.7
322001,0.00,12.88,0.7
322993,0.00,12.85,0.7
323991,0.00,12.8,0.7
325000,0.00,12.783,0.7
325997,0.00,12.833,0.8
326998,0.00,12.866,0.8
327993,0.00,12.96,0.8
328997,0.00,13.04,0.8
329994,0.00,13.05,0.8
330999,0.00,13.116,0.8
331995,0.00,13.18,0.8
332997,0.00,13.2,0.8
334001,0.00,13.16,0.8
334999,0.00,13.133,0.8
336003,0.00,13.08,0.7
336998,0.00,13.066,0.7
337995,0.00,13.016,0.7
338998,0.00,12.966,0.8
339996,0.00,12.966,0.7
340994,0.00,12.966,0.8
341996,0.00,12.98,0.8
342991,0.00,12.983,0.8
343997,0.00,13.02,0.8
344992,0.00,13.016,0.8
345998,0.00,13.04,0.8
346995,0.00,13.0,0.8
347999,0.00,12.966,0.7
349000,0.00,12.933,0.7
349998,0.00,12.883,0.7
350991,0.00,12.85,0.7
351998,0.00,12.84,0.7
352999,0.00,12.9,0.7
353998,0.00,12.916,0.7
354994,0.00,12.933,0.7
355998,0.00,12.96,0.7
356994,0.00,12.966,0.7
357999,0.00,12.94,0.7
359002,0.00,12.94,0.7
359993,0.00,13.0,0.8
360994,0.00,13.1,0.7
361993,0.00,13.28,0.7
362999,0.00,13.35,0.7
363995,0.00,13.383,0.7
365002,0.00,13.416,0.7
365993,0.00,13.416,0.7
366992,0.00,13.366,0.7
368003,0.00,13.22,0.7
369002,0.00,13.24,0.7
369996,0.00,13.26,0.7
370999,0.00,13.283,0.7
371999,0.00,13.34,0.7
372991,0.00,13.333,0.7
373991,0.00,13.4,0.7
375000,0.00,13.45,0.7
375996,0.00,13.52,0.7
376997,0.00,13.54,0.8
377998,0.00,13.6,0.8
378996,0.00,13.633,0.8
379998,0.00,13.68,0.8
381001,0.00,13.7,0.8
381993,0.00,13.7,0.8
382993,0.00,13.7,0.8
383994,0.00,13.68,0.7
384996,0.00,13.666,0.7
385991,0.00,13.58,0.7
386993,0.00,13.566,0.7
387999,0.00,13.52,0.7
389003,0.00,13.5,0.7
389994,0.00,13.466,0.7
390996,0.00,13.433,0.7
391999,0.00,13.42,0.7
392998,0.00,13.416,0.8
394002,0.00,13.416,0.7
394997,0.00,13.416,0.8
395994,0.00,13.416,0.8
396995,0.00,13.416,0.8
397993,0.00,13.38,0.8
398999,0.00,13.383,0.7
399994,0.00,13.38,0.7
400997,0.00,13.38,0.7
401989,0.00,13.366,0.7
402993,0.00,13.283,0.7
404001,0.00,13.16,0.7
404990,0.00,13.08,0.8
406000,0.00,13.02,0.8
406999,0.00,12.98,0.8
407995,0.00,13.02,0.8
408996,0.00,13.05,0.8
410009,0.00,13.1,0.8
411002,0.00,13.183,0.7
411996,0.00,13.32,0.7
412998,0.00,13.44,0.7
413995,0.00,13.45,0.7
415005,0.00,13.56,0.7
415996,0.00,13.533,0.7
417002,0.00,13.46,0.8
417994,0.00,13.36,0.7
419000,0.00,13.3,0.8
419991,0.00,13.266,0.8
421001,0.00,13.216,0.8
421994,0.00,13.14,0.8
423001,0.00,13.18,0.8
424006,0.00,13.24,0.8
424995,0.00,13.333,0.8
425996,0.00,13.48,0.8
427003,0.00,13.483,0.8
427997,0.00,13.516,0.8
428998,0.00,13.483,0.8
429998,0.00,13.4,0.8
430997,0.00,13.36,0.8
431992,0.00,13.4,0.8
432995,0.00,13.52,0.8
433994,0.00,13.7,0.8
434999,0.00,13.766,0.8
435989,0.00,13.98,0.8
436993,0.00,14.12,0.8
437993,0.00,14.18,0.8
438993,0.00,14.216,0.8
440000,0.00,14.28,0.8
441005,0.00,14.266,0.8
441991,0.00,14.24,0.8
443002,0.00,14.18,0.8
443991,0.00,14.15,0.8
444997,0.00,14.083,0.8
445996,0.00,14.02,0.8
447001,0.00,14.0,0.8
448001,0.00,13.983,0.8
449002,0.00,13.96,0.8
449995,0.00,13.933,0.8
451004,0.00,13.9,0.8
452000,0.00,13.94,0.8
452998,0.00,14.0,0.8
453993,0.00,14.05,0.8
455005,0.00,14.18,0.8
456006,0.00,14.133,0.7
456998,0.00,14.166,0.7
457997,0.00,14.08,0.7
458996,0.00,14.0,0.7
459999,0.00,13.88,0.7
461000,0.00,13.82,0.7
462005,0.00,13.783,0.7
462997,0.00,13.7,0.7
464007,0.00,13.58,0.8
464999,0.00,13.52,0.8
466003,0.00,13.58,0.9
466997,0.00,13.68,0.9
468001,0.00,13.783,1.0
469004,0.00,14.0,1.0
"""


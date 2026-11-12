package com.divinedeck.launcher.feature.gps.domain

import com.divinedeck.launcher.feature.gps.domain.model.GpsSpeedometerConfig
import com.divinedeck.launcher.feature.gps.domain.model.GpsStatus
import com.divinedeck.launcher.feature.gps.domain.model.MovementState
import com.divinedeck.launcher.feature.gps.domain.model.SpeedAccuracyState
import com.divinedeck.launcher.feature.gps.domain.model.SpeedReading
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de repositorio para el subsistema de posicionamiento y velocímetro real por GPS y FuelSense
 * (Fase 4.0 Correctiva).
 */
interface GpsSpeedometerRepository {
    val speedReading: StateFlow<SpeedReading>
    val accuracyState: StateFlow<SpeedAccuracyState>
    val gpsStatus: StateFlow<GpsStatus>
    val movementState: StateFlow<MovementState>
    val config: GpsSpeedometerConfig

    fun startTracking()
    fun stopTracking()
    fun resetTripMetrics()
    fun onPermissionStatusChanged(isGranted: Boolean)
    fun refreshProviderStatus()
}

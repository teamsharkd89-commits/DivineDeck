package com.divinedeck.launcher.feature.gps.domain.model

/**
 * Parámetros internos configurables para el velocímetro digital por GPS y motor de movimiento FuelSense
 * (Fase 4.5A Correctiva).
 *
 * Combina los filtros físicos del Chevrolet Chevy C2 2009 con la máquina de estados probada en FuelSense:
 * - Evita lecturas fantasma cuando el vehículo está detenido.
 * - Discrimina movimiento vehicular genuino contra ruido de satélites (drift).
 * - Rechaza aceleraciones imposibles producidas por derivas o saltos de satélites.
 * - Desconecta lecturas antiguas para evitar mostrar velocidades congeladas.
 * - Valida coherencia de velocidad y ventanas temporales independientes.
 */
data class GpsSpeedometerConfig(
    val updateIntervalMs: Long = 1000L,
    val fastestIntervalMs: Long = 500L,
    val minDistanceMeters: Float = 0f,
    val minAcceptableAccuracyMeters: Float = 35.0f,
    val optimalAccuracyMeters: Float = 12.0f,
    val maxReadingAgeMs: Long = 3500L,
    val signalLossTimeoutMs: Long = 6000L,
    val stoppedSpeedThresholdKmh: Float = 1.8f,
    val maxRealisticSpeedKmh: Float = 185.0f,
    val maxRealisticAccelerationMps2: Float = 7.0f,
    val maxRealisticDecelerationMps2: Float = 10.0f,
    val smoothingFactor: Float = 0.65f,
    // Parámetros del motor de movimiento FuelSense (Fase 4.5A)
    val stationaryRadiusMeters: Double = 8.0,
    val minMovingSpeedKmh: Double = 1.5,
    val stopSpeedThresholdKmh: Double = 1.8,
    val stopConfirmationCount: Int = 3,
    val movementConfirmationCount: Int = 3,
    val weakSignalConfirmationCount: Int = 4,
    val minCandidateIntervalMs: Long = 450L,
    val minMovingConfirmationSpanMs: Long = 2000L,
    val minWeakSignalConfirmationSpanMs: Long = 3000L,
    val minStationaryConfirmationSpanMs: Long = 1800L,
    val minDirectionalEfficiency: Float = 0.65f,
    val minDirectionalEfficiencyWeakOrNoSpeedAcc: Float = 0.70f,
    val minDirectionalEfficiencyDerivedSpeed: Float = 0.75f,
    val minSpeedAccuracyCoherenceRatio: Float = 1.50f,
    val slowWalkStepMeters: Double = 2.0,
    val maxSpeedAccuracyMps: Float = 2.5f
)

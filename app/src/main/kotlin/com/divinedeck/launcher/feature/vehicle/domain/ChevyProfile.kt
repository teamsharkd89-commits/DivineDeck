package com.divinedeck.launcher.feature.vehicle.domain

/**
 * Ficha técnica y perfil del vehículo objetivo real:
 * Chevrolet Chevy C2 2009 Hatchback 2 Puertas • Gris Metálico (Plataforma GM 4200 / Corsa B evolution).
 */
data class ChevyProfile(
    val make: String = "Chevrolet",
    val model: String = "Chevy C2",
    val year: Int = 2009,
    val bodyStyle: String = "Hatchback 2 Puertas",
    val paintColor: String = "Gris Metálico",
    val engine: String = "1.6L MPFI SOHC 8V",
    val horsepowerHp: Int = 100,
    val torqueLbFt: Int = 102,
    val fuelCapacityLiters: Float = 46.0f,
    val maxFactorySpeedKmh: Int = 175,
    val targetTirePressurePsi: Float = 30.0f
)

package com.divinedeck.launcher.feature.garage3d.domain

import com.divinedeck.launcher.feature.garage3d.domain.model.ChevyC2State
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de renderizado 3D en tiempo real para el Chevrolet Chevy C2 2009 (2 Puertas • Gris Metálico).
 */
interface Chevy3DRenderer {
    val modelState: StateFlow<ChevyC2State>

    fun onRotate(deltaYaw: Float, deltaPitch: Float)
    fun onZoom(scaleDelta: Float)
    fun setVisualMode(mode: SpeedVisualMode)
    fun setAutoRotate(enabled: Boolean)
    fun resetCamera()
    fun setCameraAngle(yaw: Float, pitch: Float = 10f) {}
    fun cycleCameraPreset() {}
    fun onSpeedUpdated(speedKmh: Float, isReliable: Boolean) {}
    fun setDebugVisualMode(mode: SpeedVisualMode?) {}
    fun setDebugSimulatedSpeed(speedKmh: Float?) {}
}

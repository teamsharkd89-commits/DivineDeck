package com.divinedeck.launcher.feature.garage3d.data

import com.divinedeck.launcher.feature.garage3d.domain.Chevy3DRenderer
import com.divinedeck.launcher.feature.garage3d.domain.model.CameraPreset
import com.divinedeck.launcher.feature.garage3d.domain.model.ChevyC2State
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class Chevy3DContractImpl : Chevy3DRenderer {

    private val _modelState = MutableStateFlow(ChevyC2State())
    override val modelState: StateFlow<ChevyC2State> = _modelState.asStateFlow()

    override fun onRotate(deltaYaw: Float, deltaPitch: Float) {
        _modelState.update { current ->
            val wrappedYaw = ((current.rotationYaw + deltaYaw) % 360f + 360f) % 360f
            current.copy(
                rotationYaw = wrappedYaw,
                rotationPitch = (current.rotationPitch + deltaPitch).coerceIn(-15f, 45f)
            )
        }
    }

    override fun onZoom(scaleDelta: Float) {
        _modelState.update { current ->
            current.copy(zoomLevel = (current.zoomLevel * scaleDelta).coerceIn(0.5f, 2.5f))
        }
    }

    override fun setVisualMode(mode: SpeedVisualMode) {
        setDebugVisualMode(mode)
    }

    override fun setDebugVisualMode(mode: SpeedVisualMode?) {
        _modelState.update { current ->
            val effectiveSpeed = current.debugSimulatedSpeedKmh ?: current.currentSpeedKmh
            val resolvedMode = if (mode != null) {
                mode
            } else if (current.debugSimulatedSpeedKmh != null || current.isGpsReliable) {
                SpeedVisualMode.fromSpeed(effectiveSpeed, current.visualMode)
            } else {
                SpeedVisualMode.NORMAL
            }
            current.copy(
                debugVisualModeOverride = mode,
                visualMode = resolvedMode
            )
        }
    }

    override fun setDebugSimulatedSpeed(speedKmh: Float?) {
        _modelState.update { current ->
            val effectiveSpeed = speedKmh ?: current.currentSpeedKmh
            val resolvedMode = if (current.debugVisualModeOverride != null) {
                current.debugVisualModeOverride
            } else if (speedKmh != null) {
                SpeedVisualMode.fromSpeed(speedKmh, current.visualMode)
            } else if (current.isGpsReliable) {
                SpeedVisualMode.fromSpeed(current.currentSpeedKmh, current.visualMode)
            } else {
                SpeedVisualMode.NORMAL
            }
            current.copy(
                debugSimulatedSpeedKmh = speedKmh,
                visualMode = resolvedMode
            )
        }
    }

    override fun onSpeedUpdated(speedKmh: Float, isReliable: Boolean) {
        _modelState.update { current ->
            val effectiveSpeed = current.debugSimulatedSpeedKmh ?: speedKmh
            val effectiveReliable = current.debugSimulatedSpeedKmh != null || isReliable
            val resolvedMode = if (current.debugVisualModeOverride != null) {
                current.debugVisualModeOverride
            } else if (effectiveReliable) {
                SpeedVisualMode.fromSpeed(effectiveSpeed, current.visualMode)
            } else {
                SpeedVisualMode.NORMAL
            }
            current.copy(
                currentSpeedKmh = speedKmh,
                isGpsReliable = isReliable,
                visualMode = resolvedMode
            )
        }
    }

    override fun setAutoRotate(enabled: Boolean) {
        _modelState.update { it.copy(isAutoRotating = enabled) }
    }

    override fun resetCamera() {
        _modelState.update {
            it.copy(
                activePreset = CameraPreset.PERSPECTIVE_3_4,
                rotationYaw = CameraPreset.PERSPECTIVE_3_4.yaw,
                rotationPitch = CameraPreset.PERSPECTIVE_3_4.pitch,
                zoomLevel = CameraPreset.PERSPECTIVE_3_4.zoom
            )
        }
    }

    override fun setCameraAngle(yaw: Float, pitch: Float) {
        val normYaw = ((yaw % 360f) + 360f) % 360f
        val matchedPreset = CameraPreset.entries.firstOrNull {
            kotlin.math.abs(it.yaw - normYaw) < 8f
        } ?: _modelState.value.activePreset
        _modelState.update {
            it.copy(
                activePreset = matchedPreset,
                rotationYaw = normYaw,
                rotationPitch = pitch.coerceIn(-15f, 45f),
                isAutoRotating = false
            )
        }
    }

    override fun cycleCameraPreset() {
        _modelState.update { current ->
            val nextPreset = current.activePreset.next()
            current.copy(
                activePreset = nextPreset,
                rotationYaw = nextPreset.yaw,
                rotationPitch = nextPreset.pitch,
                zoomLevel = nextPreset.zoom,
                isAutoRotating = false
            )
        }
    }
}

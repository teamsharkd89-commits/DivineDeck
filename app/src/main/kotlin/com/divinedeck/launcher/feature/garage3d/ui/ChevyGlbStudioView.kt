package com.divinedeck.launcher.feature.garage3d.ui

import android.content.Context
import android.opengl.EGL14
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.divinedeck.launcher.core.designsystem.theme.DivineDeckTheme
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.feature.garage3d.data.glb.GlbMaterial
import com.divinedeck.launcher.feature.garage3d.data.glb.GlbModelData
import com.divinedeck.launcher.feature.garage3d.data.glb.GlbModelLoader
import com.divinedeck.launcher.feature.garage3d.domain.model.AutomotiveParticleSystem
import com.divinedeck.launcher.feature.garage3d.domain.model.ChevyC2State
import com.divinedeck.launcher.feature.garage3d.domain.model.DynamicSpeedEffectsController
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedEffectsParameters
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Visor 3D en tiempo real basado en OpenGL ES 3.0 + glTF 2.0 (`.glb`) para el
 * Chevrolet Chevy C2 2009 Hatchback 2 Puertas Gris Metálico (Fase 03 / Fase 3.1).
 *
 * Refinamientos de Fase 3.1 para Samsung Galaxy Tab A11 (SM-X133, ARM Mali-G57 MC2):
 * - Encuadre teleobjetivo automotriz (FOV 28°, distancia base 3.32m, centro vertical 0.65m)
 *   para que el vehículo ocupe una proporción protagonista sin recortes en ningún ángulo.
 * - Transición exponencial suave e independiente de fotogramas al alternar entre
 *   Perspectiva 3/4, Vista frontal, Vista lateral y Vista trasera.
 * - Anti-aliasing hardware 4x MSAA con fallback seguro.
 * - Sombreado PBR con clear-coat automotriz, reflejos de estudio y ACES Filmic Tone Mapping.
 */
@Composable
fun ChevyGlbStudioView(
    state: ChevyC2State,
    onRotate: (deltaYaw: Float, deltaPitch: Float) -> Unit,
    onZoom: (scaleDelta: Float) -> Unit,
    onUserTouchInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val palette = DivineDeckTheme.palette

    val glRenderer = remember(context) {
        ChevyGlbOpenGLRenderer(context.applicationContext)
    }

    val effectiveSpeed = state.debugSimulatedSpeedKmh ?: state.currentSpeedKmh
    val isSimulated = state.debugSimulatedSpeedKmh != null
    val effectiveReliable = state.isGpsReliable || isSimulated

    LaunchedEffect(
        state.rotationYaw,
        state.rotationPitch,
        state.zoomLevel,
        state.isAutoRotating,
        state.visualMode,
        state.currentSpeedKmh,
        state.debugSimulatedSpeedKmh,
        state.isGpsReliable,
        state.debugVisualModeOverride,
        palette.background,
        palette.accent,
        palette.preset
    ) {
        glRenderer.updateSceneState(
            yaw = state.rotationYaw,
            pitch = state.rotationPitch,
            zoom = state.zoomLevel,
            autoRotate = state.isAutoRotating,
            visualMode = state.visualMode,
            speedKmh = effectiveSpeed,
            isGpsReliable = effectiveReliable,
            debugModeOverride = state.debugVisualModeOverride,
            bgColor = palette.background,
            accentColor = palette.accent,
            hideFloorShadow = palette.preset == ThemePreset.ROJO_SPORT
        )
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onUserTouchInteraction()
                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        if (!canceled) {
                            val pointerCount = event.changes.count { it.pressed }
                            if (pointerCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                if (abs(zoomChange - 1f) > 0.004f) {
                                    onZoom(zoomChange)
                                }
                                val pan = event.calculatePan()
                                if (abs(pan.x) > 0.5f || abs(pan.y) > 0.5f) {
                                    onRotate(pan.x * 0.28f, pan.y * 0.18f)
                                }
                            } else if (pointerCount == 1) {
                                val pan = event.calculatePan()
                                if (abs(pan.x) > 0.2f || abs(pan.y) > 0.2f) {
                                    onRotate(pan.x * 0.42f, pan.y * 0.24f)
                                }
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val surfaceView = remember(context) {
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(3)
                setEGLConfigChooser(SafeMsaaConfigChooser())
                setRenderer(glRenderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        }

        DisposableEffect(lifecycleOwner, surfaceView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                    Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                surfaceView.onPause()
            }
        }

        AndroidView(
            factory = { surfaceView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Selector EGL con soporte 4x MSAA hardware (óptimo en GPU tile-based ARM Mali-G57 MC2)
 * y fallback automático a configuración estándar de 16 bits de profundidad.
 */
private class SafeMsaaConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        val msaaAttribs = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL10.EGL_SAMPLE_BUFFERS, 1,
            EGL10.EGL_SAMPLES, 4,
            EGL10.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfig = IntArray(1)
        if (egl.eglChooseConfig(display, msaaAttribs, configs, 1, numConfig) && numConfig[0] > 0 && configs[0] != null) {
            return configs[0]!!
        }

        val fallbackAttribs = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL10.EGL_NONE
        )
        egl.eglChooseConfig(display, fallbackAttribs, configs, 1, numConfig)
        return requireNotNull(configs[0]) { "No se encontró una configuración EGL compatible para OpenGL ES 3.0" }
    }
}

/**
 * Motor OpenGL ES 3.0 con sombreado PBR automotriz, ACES Filmic Tone Mapping
 * e interpolación suave de cámara para el Chevrolet Chevy C2 2009.
 */
private class ChevyGlbOpenGLRenderer(
    private val appContext: Context
) : GLSurfaceView.Renderer {

    @Volatile private var targetYaw: Float = 45f
    @Volatile private var targetPitch: Float = 10f
    @Volatile private var targetZoom: Float = 1.0f
    @Volatile private var isAutoRotating: Boolean = true
    @Volatile private var bgR: Float = 0.03f
    @Volatile private var bgG: Float = 0.04f
    @Volatile private var bgB: Float = 0.07f
    @Volatile private var accR: Float = 1.0f
    @Volatile private var accG: Float = 0.71f
    @Volatile private var accB: Float = 0.28f
    @Volatile private var hideFloorShadow: Boolean = false
    @Volatile private var targetSpeedKmh: Float = 0f
    @Volatile private var targetGpsReliable: Boolean = false
    @Volatile private var targetDebugModeOverride: SpeedVisualMode? = null

    private val speedEffectsController = DynamicSpeedEffectsController()

    private var currentYaw: Float = 45f
    private var currentPitch: Float = 10f
    private var currentZoom: Float = 1.0f
    private var autoYawOffset: Float = 0f
    private var lastFrameNanos: Long = 0L

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)

    private var pbrProgram = 0
    private var floorProgram = 0
    private var particleProgram = 0
    private var trailProgram = 0
    private var backgroundProgram = 0
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var floorVbo = 0
    private var trailIbo = 0

    // Ubicaciones en caché de uniformes PBR (Optimización Mali-G57 MC2)
    private var uPbrMvp = -1
    private var uPbrModel = -1
    private var uPbrCameraPos = -1
    private var uPbrBaseColor = -1
    private var uPbrMetallic = -1
    private var uPbrRoughness = -1
    private var uPbrEmissive = -1
    private var uPbrAccent = -1
    private var uPbrMaterialIndex = -1
    private var uPbrVisualMode = -1
    private var uPbrTime = -1
    private var uPbrEffectIntensity = -1
    private var uPbrDynamicEmissive = -1
    private var uPbrExhaustOrigin = -1
    private var uPbrExhaustGlow = -1
    private var uPbrBodyWarmth = -1
    private var uPbrWheelAngle = -1

    // Ubicaciones en caché de uniformes Floor & Underglow 3D
    private var uFloorMvp = -1
    private var uFloorAccent = -1
    private var uFloorUnderglowColor = -1
    private var uFloorUnderglowIntensity = -1
    private var uFloorTime = -1
    private var uFloorHideShadow = -1

    // Ubicaciones en caché de uniformes Partículas y Estelas
    private var uParticleMvp = -1
    private var uTrailMvp = -1

    // Ubicaciones en caché de uniformes Fondo animado
    private var uBgResolution = -1
    private var uBgAccent = -1
    private var uBgTime = -1
    private var uBgSpeedFactor = -1

    private class GpuSubMesh(
        val vboPositions: Int,
        val vboNormals: Int,
        val iboIndices: Int,
        val indexCount: Int,
        val materialIndex: Int,
        val material: GlbMaterial
    )

    private val gpuMeshes = ArrayList<GpuSubMesh>()

    fun updateSceneState(
        yaw: Float,
        pitch: Float,
        zoom: Float,
        autoRotate: Boolean,
        visualMode: SpeedVisualMode,
        speedKmh: Float = 0f,
        isGpsReliable: Boolean = false,
        debugModeOverride: SpeedVisualMode? = null,
        bgColor: Color,
        accentColor: Color,
        hideFloorShadow: Boolean = false
    ) {
        if (!autoRotate && isAutoRotating) {
            // Conserva la orientación visual actual al detener la rotación automática para evitar saltos
            currentYaw = ((currentYaw + autoYawOffset) % 360f + 360f) % 360f
            autoYawOffset = 0f
        }
        targetYaw = yaw
        targetPitch = pitch
        targetZoom = zoom
        isAutoRotating = autoRotate
        targetSpeedKmh = speedKmh
        targetGpsReliable = isGpsReliable
        targetDebugModeOverride = debugModeOverride
        bgR = bgColor.red
        bgG = bgColor.green
        bgB = bgColor.blue
        accR = accentColor.red
        accG = accentColor.green
        accB = accentColor.blue
        this.hideFloorShadow = hideFloorShadow
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_CULL_FACE)

        pbrProgram = buildShaderProgram(PBR_VERTEX_SHADER, PBR_FRAGMENT_SHADER)
        floorProgram = buildShaderProgram(FLOOR_VERTEX_SHADER, FLOOR_FRAGMENT_SHADER)
        particleProgram = buildShaderProgram(PARTICLE_VERTEX_SHADER, PARTICLE_FRAGMENT_SHADER)
        trailProgram = buildShaderProgram(TRAIL_VERTEX_SHADER, TRAIL_FRAGMENT_SHADER)
        backgroundProgram = buildShaderProgram(BACKGROUND_VERTEX_SHADER, BACKGROUND_FRAGMENT_SHADER)

        // Registrar uniformes PBR en caché
        uPbrMvp = GLES30.glGetUniformLocation(pbrProgram, "uMVP")
        uPbrModel = GLES30.glGetUniformLocation(pbrProgram, "uModel")
        uPbrCameraPos = GLES30.glGetUniformLocation(pbrProgram, "uCameraPos")
        uPbrBaseColor = GLES30.glGetUniformLocation(pbrProgram, "uBaseColor")
        uPbrMetallic = GLES30.glGetUniformLocation(pbrProgram, "uMetallic")
        uPbrRoughness = GLES30.glGetUniformLocation(pbrProgram, "uRoughness")
        uPbrEmissive = GLES30.glGetUniformLocation(pbrProgram, "uEmissive")
        uPbrAccent = GLES30.glGetUniformLocation(pbrProgram, "uAccentColor")
        uPbrMaterialIndex = GLES30.glGetUniformLocation(pbrProgram, "uMaterialIndex")
        uPbrVisualMode = GLES30.glGetUniformLocation(pbrProgram, "uVisualMode")
        uPbrTime = GLES30.glGetUniformLocation(pbrProgram, "uTime")
        uPbrEffectIntensity = GLES30.glGetUniformLocation(pbrProgram, "uEffectIntensity")
        uPbrDynamicEmissive = GLES30.glGetUniformLocation(pbrProgram, "uDynamicEmissive")
        uPbrExhaustOrigin = GLES30.glGetUniformLocation(pbrProgram, "uExhaustOrigin")
        uPbrExhaustGlow = GLES30.glGetUniformLocation(pbrProgram, "uExhaustGlow")
        uPbrBodyWarmth = GLES30.glGetUniformLocation(pbrProgram, "uBodyWarmth")
        uPbrWheelAngle = GLES30.glGetUniformLocation(pbrProgram, "uWheelAngle")

        // Registrar uniformes Floor & Underglow en caché
        uFloorMvp = GLES30.glGetUniformLocation(floorProgram, "uMVP")
        uFloorAccent = GLES30.glGetUniformLocation(floorProgram, "uAccentColor")
        uFloorUnderglowColor = GLES30.glGetUniformLocation(floorProgram, "uUnderglowColor")
        uFloorUnderglowIntensity = GLES30.glGetUniformLocation(floorProgram, "uUnderglowIntensity")
        uFloorTime = GLES30.glGetUniformLocation(floorProgram, "uTime")
        uFloorHideShadow = GLES30.glGetUniformLocation(floorProgram, "uHideShadow")

        // Registrar uniformes Partículas y Estelas
        uParticleMvp = GLES30.glGetUniformLocation(particleProgram, "uMVP")
        uTrailMvp = GLES30.glGetUniformLocation(trailProgram, "uMVP")

        // Registrar uniformes Fondo animado
        uBgResolution = GLES30.glGetUniformLocation(backgroundProgram, "uResolution")
        uBgAccent = GLES30.glGetUniformLocation(backgroundProgram, "uAccentColor")
        uBgTime = GLES30.glGetUniformLocation(backgroundProgram, "uTime")
        uBgSpeedFactor = GLES30.glGetUniformLocation(backgroundProgram, "uSpeedFactor")

        setupFloorQuad()
        setupTrailIndexBuffer()
        val modelData: GlbModelData = GlbModelLoader.loadFromAssets(appContext)
        uploadModelToGpu(modelData)
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        // Lente de estudio automotriz de 28° (minimiza distorsión de perspectiva y amplía presencia del vehículo)
        Matrix.perspectiveM(projMatrix, 0, 28f, aspect, 0.25f, 30f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) * 1e-9f).coerceIn(0.001f, 0.1f)
        lastFrameNanos = now

        if (isAutoRotating) {
            autoYawOffset = (autoYawOffset + dt * 13.5f) % 360f
        }

        // Interpolación exponencial independiente de FPS para transiciones suaves entre vistas (3/4, Frente, Lateral, Trasera)
        val smoothAlpha = (1f - exp(-dt * 9.5f)).coerceIn(0.05f, 0.45f)
        val desiredYaw = targetYaw + if (isAutoRotating) autoYawOffset else 0f
        currentYaw = ((currentYaw + shortestAngleDelta(currentYaw, desiredYaw) * smoothAlpha) % 360f + 360f) % 360f
        currentPitch += (targetPitch - currentPitch) * smoothAlpha
        currentZoom += (targetZoom - currentZoom) * smoothAlpha

        // Actualización de efectos dinámicos 3D gobernados por la velocidad GPS real o override de depuración (Fase 4.1B)
        val effectsParams = speedEffectsController.update(
            dtSeconds = dt,
            speedKmh = targetSpeedKmh,
            isGpsReliable = targetGpsReliable,
            debugModeOverride = targetDebugModeOverride
        )

        GLES30.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // 0. Fondo animado y reactivo detrás del vehículo (Fase 06.11):
        // gradiente del tema + brillo pulsante + rejilla en perspectiva que
        // se desplaza con la velocidad. Se dibuja primero, sin depth test,
        // para que el piso 3D y el auto queden por encima.
        drawAnimatedBackground(effectsParams)

        // Distancia calibrada (3.52m base con FOV 28°) para que el Chevy C2 ocupe ~78% del visor centrado sin cortarse
        val distance = (3.52f / currentZoom.coerceIn(0.5f, 2.5f)).coerceIn(1.60f, 6.8f)
        val yawRad = Math.toRadians(currentYaw.toDouble())
        val pitchRad = Math.toRadians(currentPitch.coerceIn(-15f, 45f).toDouble())

        val camTargetY = 0.60f
        val eyeX = (distance * cos(pitchRad) * sin(yawRad)).toFloat()
        val eyeY = (camTargetY + distance * sin(pitchRad)).toFloat().coerceAtLeast(0.16f)
        val eyeZ = (distance * cos(pitchRad) * cos(yawRad)).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            0f, camTargetY, 0f,
            0f, 1f, 0f
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Plataforma de estudio bajo el chasis del Chevy C2 (suelo firme sin vibración)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        drawStudioFloor(effectsParams)

        // 2. Modelo 3D del Chevrolet Chevy C2 con vibración sutil por velocidad (Parte E)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, effectsParams.vibrationY, 0f)
        Matrix.rotateM(modelMatrix, 0, effectsParams.vibrationPitch, 1f, 0f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)

        // Sub-mallas opacas del modelo .glb (carrocería, rines, neumáticos, cálipers, escape, parrilla)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        for (mesh in gpuMeshes) {
            if (!mesh.material.isBlend) {
                drawSubMesh(mesh, eyeX, eyeY, eyeZ, effectsParams)
            }
        }

        // 3. Sub-mallas translúcidas del modelo .glb (cristales polarizados, parabrisas y micas de faros)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        for (mesh in gpuMeshes) {
            if (mesh.material.isBlend) {
                drawSubMesh(mesh, eyeX, eyeY, eyeZ, effectsParams)
            }
        }
        GLES30.glDisable(GLES30.GL_BLEND)

        // 4. Cintas aerodinámicas de velocidad (Aero speed trails en spoilers y difusor)
        effectsParams.particleSystem?.let { drawAeroTrails(it) }

        // 5. Partículas dinámicas (Humo de escape, chispas incandescentes, humo de fricción de neumáticos)
        effectsParams.particleSystem?.let { drawParticles(it) }
    }

    /**
     * Fondo animado y reactivo del estudio 3D (Fase 06.11).
     * Triángulo a pantalla completa en clip-space con el color de acento del
     * tema: gradiente oscuro arriba, brillo radial pulsante detrás del auto y
     * rejilla en perspectiva en el piso que se desplaza con la velocidad
     * (uSpeedFactor = effectIntensity del controlador de efectos).
     */
    private fun drawAnimatedBackground(params: SpeedEffectsParameters) {
        if (backgroundProgram == 0) return
        GLES30.glUseProgram(backgroundProgram)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        GLES30.glUniform2f(uBgResolution, viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES30.glUniform3f(uBgAccent, accR, accG, accB)
        GLES30.glUniform1f(uBgTime, params.timeSeconds)
        GLES30.glUniform1f(uBgSpeedFactor, params.effectIntensity.coerceIn(0f, 1f))

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(0)
    }

    private fun drawStudioFloor(params: SpeedEffectsParameters) {
        if (floorProgram == 0 || floorVbo == 0) return
        GLES30.glUseProgram(floorProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glUniformMatrix4fv(uFloorMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniform3f(uFloorAccent, accR, accG, accB)
        GLES30.glUniform3f(uFloorUnderglowColor, params.underglowR, params.underglowG, params.underglowB)
        GLES30.glUniform1f(uFloorUnderglowIntensity, params.underglowIntensity)
        GLES30.glUniform1f(uFloorTime, params.timeSeconds)
        GLES30.glUniform1f(uFloorHideShadow, if (hideFloorShadow) 1f else 0f)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, floorVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(0)
    }

    private fun drawSubMesh(
        mesh: GpuSubMesh,
        eyeX: Float,
        eyeY: Float,
        eyeZ: Float,
        params: SpeedEffectsParameters
    ) {
        if (pbrProgram == 0) return
        GLES30.glUseProgram(pbrProgram)

        GLES30.glUniformMatrix4fv(uPbrMvp, 1, false, mvpMatrix, 0)
        GLES30.glUniformMatrix4fv(uPbrModel, 1, false, modelMatrix, 0)
        GLES30.glUniform3f(uPbrCameraPos, eyeX, eyeY, eyeZ)

        val mat = mesh.material
        GLES30.glUniform4f(
            uPbrBaseColor,
            mat.baseColor[0],
            mat.baseColor[1],
            mat.baseColor[2],
            mat.baseColor[3]
        )
        GLES30.glUniform1f(uPbrMetallic, mat.metallicFactor)
        GLES30.glUniform1f(uPbrRoughness, mat.roughnessFactor)
        GLES30.glUniform3f(
            uPbrEmissive,
            mat.emissiveFactor[0],
            mat.emissiveFactor[1],
            mat.emissiveFactor[2]
        )
        GLES30.glUniform3f(uPbrAccent, accR, accG, accB)

        // Dynamic Speed Effects Uniforms (Fase 4.1B)
        GLES30.glUniform1i(uPbrMaterialIndex, mesh.materialIndex)
        GLES30.glUniform1i(uPbrVisualMode, params.visualMode.ordinal)
        GLES30.glUniform1f(uPbrTime, params.timeSeconds)
        GLES30.glUniform1f(uPbrEffectIntensity, params.effectIntensity)
        GLES30.glUniform1f(uPbrWheelAngle, params.wheelAngle)

        // Emisión dinámica contextual según el material auditado del Chevy C2
        when (mesh.materialIndex) {
            4 -> { // ChevyC2_AlloyRim (4 rines de aluminio) - Respeto innegociable a aleación original (Regla 1)
                GLES30.glUniform3f(uPbrDynamicEmissive, 0f, 0f, 0f)
            }
            8 -> { // ChevyC2_BrakeRed (cálipers y frenos)
                GLES30.glUniform3f(uPbrDynamicEmissive, params.brakeEmissiveR, params.brakeEmissiveG, params.brakeEmissiveB)
            }
            else -> {
                GLES30.glUniform3f(uPbrDynamicEmissive, 0f, 0f, 0f)
            }
        }

        // Colilla de escape en coordenadas locales reales [-0.45, 0.22, -1.91]
        GLES30.glUniform3f(uPbrExhaustOrigin, -0.450f, 0.220f, -1.910f)
        GLES30.glUniform1f(uPbrExhaustGlow, params.exhaustGlow)
        GLES30.glUniform1f(uPbrBodyWarmth, params.bodyWarmth)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vboPositions)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, mesh.vboNormals)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 0, 0)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, mesh.iboIndices)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, mesh.indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glUseProgram(0)
    }

    private fun setupFloorQuad() {
        val r = 2.65f
        val coords = floatArrayOf(
            -r, 0.002f, -r,
             r, 0.002f, -r,
            -r, 0.002f,  r,
             r, 0.002f,  r
        )
        val fb = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(coords)
                position(0)
            }
        val buf = IntArray(1)
        GLES30.glGenBuffers(1, buf, 0)
        floorVbo = buf[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, floorVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, coords.size * 4, fb, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun setupTrailIndexBuffer() {
        val indices = ShortArray(7 * 6)
        for (i in 0 until 7) {
            val b = (i * 4).toShort()
            val outIdx = i * 6
            indices[outIdx + 0] = b
            indices[outIdx + 1] = (b + 1).toShort()
            indices[outIdx + 2] = (b + 2).toShort()
            indices[outIdx + 3] = b
            indices[outIdx + 4] = (b + 2).toShort()
            indices[outIdx + 5] = (b + 3).toShort()
        }
        val ib = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }
        val buf = IntArray(1)
        GLES30.glGenBuffers(1, buf, 0)
        trailIbo = buf[0]
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, trailIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, ib, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawAeroTrails(particleSystem: AutomotiveParticleSystem) {
        val vertCount = particleSystem.activeTrailVertexCount
        if (vertCount < 4 || trailProgram == 0 || trailIbo == 0) return

        GLES30.glUseProgram(trailProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glUniformMatrix4fv(uTrailMvp, 1, false, mvpMatrix, 0)

        // Ensure client array pointer does not inherit any active VBO
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val tb = particleSystem.trailBuffer
        val stride = AutomotiveParticleSystem.FLOATS_PER_TRAIL_VERTEX * 4 // 28 bytes

        tb.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, tb)

        tb.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, tb)

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, trailIbo)
        val quadCount = vertCount / 4
        val indexCount = quadCount * 6
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_SHORT, 0)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(0)
    }

    private fun drawParticles(particleSystem: AutomotiveParticleSystem) {
        val count = particleSystem.activeParticleCount
        if (count <= 0 || particleProgram == 0) return

        GLES30.glUseProgram(particleProgram)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)

        GLES30.glUniformMatrix4fv(uParticleMvp, 1, false, mvpMatrix, 0)

        // Ensure client array pointer does not inherit any active VBO
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)

        val pb = particleSystem.particleBuffer
        val stride = AutomotiveParticleSystem.FLOATS_PER_PARTICLE * 4 // 32 bytes

        pb.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, pb)

        pb.position(3)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, pb)

        pb.position(7)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, stride, pb)

        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, count)

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(0)
    }

    private fun uploadModelToGpu(model: GlbModelData) {
        gpuMeshes.clear()
        for (sub in model.subMeshes) {
            val buffers = IntArray(3)
            GLES30.glGenBuffers(3, buffers, 0)

            val posBuf = sub.createPositionBuffer()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[0])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                sub.positions.size * 4,
                posBuf,
                GLES30.GL_STATIC_DRAW
            )

            val normBuf = sub.createNormalBuffer()
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buffers[1])
            GLES30.glBufferData(
                GLES30.GL_ARRAY_BUFFER,
                sub.normals.size * 4,
                normBuf,
                GLES30.GL_STATIC_DRAW
            )

            val idxBuf = sub.createIndexBuffer()
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buffers[2])
            GLES30.glBufferData(
                GLES30.GL_ELEMENT_ARRAY_BUFFER,
                sub.indices.size * 2,
                idxBuf,
                GLES30.GL_STATIC_DRAW
            )

            gpuMeshes.add(
                GpuSubMesh(
                    vboPositions = buffers[0],
                    vboNormals = buffers[1],
                    iboIndices = buffers[2],
                    indexCount = sub.indexCount,
                    materialIndex = sub.materialIndex,
                    material = sub.material
                )
            )
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun shortestAngleDelta(from: Float, to: Float): Float {
        var diff = (to - from) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }

    private fun buildShaderProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        if (vs == 0) return 0
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fs == 0) {
            GLES30.glDeleteShader(vs)
            return 0
        }
        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(prog)
            android.util.Log.e("ChevyGlbStudio", "Error enlazando shader program: $log")
            GLES30.glDeleteProgram(prog)
            GLES30.glDeleteShader(vs)
            GLES30.glDeleteShader(fs)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            val typeStr = if (type == GLES30.GL_VERTEX_SHADER) "VERTEX" else "FRAGMENT"
            android.util.Log.e("ChevyGlbStudio", "Error compilando $typeStr shader: $log\nFuente:\n$source")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        private const val BACKGROUND_VERTEX_SHADER = """#version 300 es
            // Triángulo a pantalla completa vía gl_VertexID (sin VBO).
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
        """

        private const val BACKGROUND_FRAGMENT_SHADER = """#version 300 es
            precision highp float;

            uniform vec2 uResolution;
            uniform vec3 uAccentColor;
            uniform float uTime;
            uniform float uSpeedFactor;

            out vec4 fragColor;

            void main() {
                vec2 uv = gl_FragCoord.xy / uResolution;
                vec2 p = uv * 2.0 - 1.0;
                p.x *= uResolution.x / uResolution.y;

                // Pulso suave siempre vivo + más intenso con la velocidad.
                float pulse = 0.75 + 0.25 * sin(uTime * 1.6);
                float energy = 0.35 + 0.65 * uSpeedFactor;

                // Cielo: oscuro arriba, tinte del acento del tema abajo.
                vec3 col = mix(
                    uAccentColor * 0.16,
                    vec3(0.008, 0.010, 0.020),
                    smoothstep(-0.7, 0.9, p.y)
                );

                // Brillo radial detrás del auto (centro levemente arriba).
                vec2 glowCenter = vec2(0.0, 0.08);
                float d = length((p - glowCenter) * vec2(0.72, 1.0));
                float glow = exp(-d * d * 2.0) * pulse * energy;
                col += uAccentColor * glow * 0.55;

                // Piso con rejilla en perspectiva bajo el horizonte, como el mockup.
                float horizon = -0.18;
                if (p.y < horizon) {
                    float depth = horizon - p.y;
                    float z = 1.0 / (depth + 0.10);
                    float scroll = uTime * (0.25 + 2.4 * uSpeedFactor);
                    vec2 g = vec2(p.x * z, z + scroll);
                    vec2 grid = abs(fract(g) - 0.5);
                    float line = 1.0 - smoothstep(0.0, 0.06, min(grid.x, grid.y));
                    float fade = smoothstep(0.0, 0.30, depth) * (1.0 - smoothstep(0.55, 1.15, depth));
                    col += uAccentColor * line * fade * (0.35 + 0.45 * energy);
                    col = mix(col, uAccentColor * 0.07, fade * 0.45);
                }

                fragColor = vec4(col, 1.0);
            }
        """

        private const val PBR_VERTEX_SHADER = """#version 300 es
            precision highp float;
            precision highp int;

            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aNormal;

            uniform mat4 uMVP;
            uniform mat4 uModel;
            uniform int uMaterialIndex;
            uniform float uWheelAngle;

            out vec3 vWorldPos;
            out vec3 vWorldNormal;

            void main() {
                vec3 pos = aPosition;
                vec3 norm = aNormal;

                // Real kinematic wheel rotation for tires (3) and rims (4)
                if (uMaterialIndex == 3 || uMaterialIndex == 4) {
                    float centerX = (pos.x < 0.0) ? -0.695 : 0.695;
                    float centerZ = (pos.z > 0.0) ? 1.180 : -1.160;
                    vec3 center = vec3(centerX, 0.295, centerZ);

                    vec3 localP = pos - center;
                    float s = sin(uWheelAngle);
                    float c = cos(uWheelAngle);

                    pos.y = center.y + localP.y * c - localP.z * s;
                    pos.z = center.z + localP.y * s + localP.z * c;

                    float normY = norm.y * c - norm.z * s;
                    float normZ = norm.y * s + norm.z * c;
                    norm.y = normY;
                    norm.z = normZ;
                }

                vec4 worldPos = uModel * vec4(pos, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = mat3(uModel) * norm;
                gl_Position = uMVP * vec4(pos, 1.0);
            }
        """

        private const val PBR_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            precision highp int;

            in vec3 vWorldPos;
            in vec3 vWorldNormal;

            uniform vec3 uCameraPos;
            uniform vec4 uBaseColor;
            uniform float uMetallic;
            uniform float uRoughness;
            uniform vec3 uEmissive;
            uniform vec3 uAccentColor;

            // Dynamic Speed Effects Uniforms
            uniform int uMaterialIndex;
            uniform int uVisualMode;
            uniform float uTime;
            uniform float uEffectIntensity;
            uniform vec3 uDynamicEmissive;
            uniform vec3 uExhaustOrigin;
            uniform float uExhaustGlow;
            uniform float uBodyWarmth;

            out vec4 fragColor;

            const float PI = 3.14159265359;

            float distributionGGX(vec3 N, vec3 H, float roughness) {
                float a = roughness * roughness;
                float a2 = a * a;
                float NdotH = max(dot(N, H), 0.0);
                float NdotH2 = NdotH * NdotH;
                float denom = (NdotH2 * (a2 - 1.0) + 1.0);
                denom = PI * denom * denom;
                return a2 / max(denom, 0.0001);
            }

            vec3 fresnelSchlick(float cosTheta, vec3 F0) {
                return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
            }

            vec3 acesToneMap(vec3 x) {
                const float a = 2.51;
                const float b = 0.03;
                const float c = 2.43;
                const float d = 0.59;
                const float e = 0.14;
                return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
            }

            void main() {
                float nLen = length(vWorldNormal);
                vec3 N = (nLen > 0.0001) ? (vWorldNormal / nLen) : vec3(0.0, 1.0, 0.0);

                vec3 viewDir = uCameraPos - vWorldPos;
                float vLen = length(viewDir);
                vec3 V = (vLen > 0.0001) ? (viewDir / vLen) : vec3(0.0, 0.0, 1.0);

                if (dot(N, V) < 0.0) {
                    N = -N;
                }

                float rough = clamp(uRoughness, 0.06, 0.96);
                vec3 albedo = uBaseColor.rgb;

                // Tire rubber rotation sensation (Part B)
                if (uMaterialIndex == 3) {
                    float wheelCenterZ = (vWorldPos.z > 0.0) ? 1.180 : -1.160;
                    float treadAngle = atan(vWorldPos.y - 0.295, vWorldPos.z - wheelCenterZ);
                    float treadMotion = sin(treadAngle * 24.0) * 0.06;
                    albedo = clamp(albedo + vec3(treadMotion), 0.06, 0.22);
                }

                vec3 F0 = mix(vec3(0.04), albedo, clamp(uMetallic, 0.0, 1.0));

                // 3-Point Automotive Studio Lighting
                vec3 L1 = normalize(vec3(0.42, 0.80, 0.43));
                vec3 L2 = normalize(vec3(-0.62, 0.48, -0.62));
                vec3 L3 = normalize(vec3(0.0, 0.35, -0.94));

                vec3 H1 = normalize(V + L1);
                vec3 H2 = normalize(V + L2);

                float NdotL1 = max(dot(N, L1), 0.0);
                float NdotL2 = max(dot(N, L2), 0.0);
                float NdotL3 = max(dot(N, L3), 0.0);
                float NdotV = clamp(dot(N, V), 0.001, 1.0);

                vec3 F1 = fresnelSchlick(max(dot(H1, V), 0.0), F0);
                float D1 = distributionGGX(N, H1, rough);
                vec3 spec1 = (D1 * F1) * 0.24 * NdotL1;

                vec3 F2 = fresnelSchlick(max(dot(H2, V), 0.0), F0);
                float D2 = distributionGGX(N, H2, rough * 1.15);
                vec3 spec2 = (D2 * F2) * 0.14 * NdotL2;

                // Clear-coat automotive lacquer layer
                float ccRough = 0.09;
                float ccD = distributionGGX(N, H1, ccRough);
                float ccFresnel = 0.04 + 0.96 * pow(1.0 - NdotV, 5.0);
                float metallicFactor = step(0.5, uMetallic);
                vec3 clearCoatSpec = vec3(ccD * ccFresnel * 0.16 * NdotL1 * metallicFactor);

                // Procedural Automotive Studio Softbox Reflection (IBL)
                vec3 R = reflect(-V, N);
                float softboxTop = smoothstep(0.18, 0.88, clamp(R.y, -1.0, 1.0));
                float horizonBand = exp(-abs(clamp(R.y, -1.0, 1.0) - 0.12) * 7.5);
                vec3 studioEnv = mix(vec3(0.08, 0.10, 0.14), vec3(0.82, 0.86, 0.92), clamp(softboxTop * 0.58 + horizonBand * 0.36, 0.0, 1.0));
                studioEnv += uAccentColor * (0.10 * pow(1.0 - NdotV, 3.2) + 0.06 * NdotL3);

                vec3 envFresnel = fresnelSchlick(NdotV, F0);
                vec3 envReflection = studioEnv * envFresnel * (1.0 - rough * 0.68);

                // Ground bounce & ambient occlusion near chassis bottom
                float groundAO = smoothstep(0.02, 0.32, vWorldPos.y) * 0.70 + 0.30;
                vec3 kD = (vec3(1.0) - envFresnel) * (1.0 - uMetallic * 0.68);
                vec3 diffuse = kD * albedo * (0.35 + 0.60 * NdotL1 + 0.25 * NdotL2 + 0.15 * NdotL3);

                // Dynamic emissive
                vec3 dynamicEmissiveTotal = vec3(0.0);

                if (uMaterialIndex == 8) { // Calipers
                    dynamicEmissiveTotal += uDynamicEmissive * uEffectIntensity;
                } else if (uMaterialIndex == 7) { // Taillights
                    dynamicEmissiveTotal += vec3(0.35, 0.02, 0.02) * uEffectIntensity;
                }

                // Exhaust tip glow
                float distExhaust = length(vWorldPos - uExhaustOrigin);
                float exhaustFactor = smoothstep(0.18, 0.02, distExhaust) * uExhaustGlow * uEffectIntensity;
                vec3 exhaustColor = vec3(1.0, 0.28, 0.04) * exhaustFactor * (0.88 + 0.12 * sin(uTime * 14.0));
                dynamicEmissiveTotal += exhaustColor;

                vec3 linearColor = ((diffuse + envReflection) * groundAO + spec1 + spec2 + clearCoatSpec + uEmissive + dynamicEmissiveTotal) * 0.85;
                vec3 mapped = acesToneMap(linearColor);
                vec3 srgbColor = pow(clamp(mapped, 0.0, 1.0), vec3(1.0 / 2.15));

                fragColor = vec4(srgbColor, clamp(uBaseColor.a, 0.0, 1.0));
            }
        """

        private const val FLOOR_VERTEX_SHADER = """#version 300 es
            precision highp float;
            precision highp int;
            layout(location = 0) in vec3 aPosition;
            uniform mat4 uMVP;
            out vec3 vPos;
            void main() {
                vPos = aPosition;
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val FLOOR_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            precision highp int;
            in vec3 vPos;
            uniform vec3 uAccentColor;

            uniform vec3 uUnderglowColor;
            uniform float uUnderglowIntensity;
            uniform float uTime;
            uniform float uHideShadow; // 1.0 = sin sombra ovalada (tema Rojo Sport)

            out vec4 fragColor;

            void main() {
                vec2 carBox = abs(vPos.xz) / vec2(0.76, 1.84);
                float boxDist = length(max(carBox - vec2(0.62, 0.72), 0.0));
                float chassisShadow = (1.0 - uHideShadow) * (1.0 - smoothstep(0.0, 0.48, boxDist));

                float r = length(vPos.xz / vec2(1.42, 2.22));
                float platform = 1.0 - smoothstep(0.75, 1.0, r);
                float ring = exp(-abs(r - 0.86) * 28.0);

                vec3 floorCol = mix(vec3(0.02, 0.03, 0.05), uAccentColor * 0.42, ring * 0.62);
                float alpha = clamp(chassisShadow * 0.80 + platform * 0.20 + ring * 0.30, 0.0, 0.86);
                floorCol *= (1.0 - chassisShadow * 0.84);

                // Environmental neon underglow (Part D)
                float dSill = min(
                    length(vPos.xz - vec2(0.70, clamp(vPos.z, -1.25, 1.15))),
                    length(vPos.xz - vec2(-0.70, clamp(vPos.z, -1.25, 1.15)))
                );
                float dBumper = min(
                    length(vPos.xz - vec2(clamp(vPos.x, -0.62, 0.62), 1.65)),
                    length(vPos.xz - vec2(clamp(vPos.x, -0.62, 0.62), -1.75))
                );
                float dNeon = min(dSill, dBumper * 1.08);

                float neonCore = exp(-dNeon * 4.8) * 0.88;
                float neonWash = exp(-dNeon * 1.8) * 0.42;
                float neonPulse = 1.0 + 0.05 * sin(uTime * 8.5);
                float neonPower = (neonCore + neonWash) * uUnderglowIntensity * neonPulse;

                float underglowMask = 1.0 - chassisShadow * 0.30;
                vec3 underglowLight = uUnderglowColor * neonPower * underglowMask;

                vec3 finalFloorCol = floorCol + underglowLight;
                float finalAlpha = clamp(alpha + neonPower * 0.72, 0.0, 0.94);

                fragColor = vec4(finalFloorCol, finalAlpha);
            }
        """

        private const val PARTICLE_VERTEX_SHADER = """#version 300 es
            precision highp float;
            precision highp int;
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec4 aColor;
            layout(location = 2) in float aSize;

            uniform mat4 uMVP;

            out vec4 vColor;

            void main() {
                vColor = aColor;
                vec4 clipPos = uMVP * vec4(aPosition, 1.0);
                gl_Position = clipPos;
                gl_PointSize = clamp((aSize * 650.0) / max(clipPos.w, 0.2), 3.0, 96.0);
            }
        """

        private const val PARTICLE_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            precision highp int;

            in vec4 vColor;
            out vec4 fragColor;

            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                float distSq = dot(coord, coord);
                if (distSq > 0.25) {
                    discard;
                }
                float edge = smoothstep(0.25, 0.02, distSq);
                fragColor = vec4(vColor.rgb, vColor.a * edge);
            }
        """

        private const val TRAIL_VERTEX_SHADER = """#version 300 es
            precision highp float;
            precision highp int;
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec4 aColor;

            uniform mat4 uMVP;

            out vec4 vColor;

            void main() {
                vColor = aColor;
                gl_Position = uMVP * vec4(aPosition, 1.0);
            }
        """

        private const val TRAIL_FRAGMENT_SHADER = """#version 300 es
            precision highp float;
            precision highp int;

            in vec4 vColor;
            out vec4 fragColor;

            void main() {
                fragColor = vColor;
            }
        """
    }
}

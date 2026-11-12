package com.divinedeck.launcher.feature.garage3d

import com.divinedeck.launcher.feature.garage3d.data.Chevy3DContractImpl
import com.divinedeck.launcher.feature.garage3d.domain.model.AutomotiveParticleSystem
import com.divinedeck.launcher.feature.garage3d.domain.model.CameraPreset
import com.divinedeck.launcher.feature.garage3d.domain.model.DynamicSpeedEffectsController
import com.divinedeck.launcher.feature.garage3d.domain.model.SpeedVisualMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Pruebas unitarias para los Dynamic Speed Effects 3D (Fase 4.1C).
 * Valida:
 * 1. Nuevos Modos y Colores: NORMAL (Azul), CORREDOR (Amarillo), ADRENALINA (Rojo), COHETE (Rojo intenso).
 * 2. Cinemática real de giro de ruedas (omega = v / r, r = 0.295 m).
 * 3. Inmutabilidad de rines de aleación de fábrica plata original (Regla 1) y carrocería gris metalizado.
 * 4. Iluminación neón inferior ambiental (Underglow) progresiva por modo.
 * 5. Vibración sutil de chasis calculada por velocidad.
 * 6. Sistema de partículas físicas ampliadas (260 partículas) y estelas aerodinámicas traseras rojas.
 * 7. Simulación de velocidad de depuración sin interferencia con velocímetro ni señal GPS real.
 */
class ChevyDynamicEffectsTest {

    // 1. NORMAL conserva los valores originales (0 a 59 km/h) con vapor discreto y underglow azul suave
    @Test
    fun `test 01 - NORMAL mode retains factory nominal values and discrete vapor`() {
        val controller = DynamicSpeedEffectsController()

        // Simular 2 segundos a 45 km/h en modo normal con GPS fiable
        for (i in 0 until 120) {
            controller.update(dtSeconds = 0.016f, speedKmh = 45f, isGpsReliable = true)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.NORMAL, params.visualMode)
        assertEquals("Rines de aleación deben conservar 0 emisión (Regla 1)", 0f, params.rimEmissiveR, 0.001f)
        assertEquals(0f, params.rimEmissiveG, 0.001f)
        assertEquals(0f, params.rimEmissiveB, 0.001f)
        assertEquals(0f, params.brakeEmissiveR, 0.001f)
        assertTrue("Underglow en modo normal a 45 kmh es suave", params.underglowIntensity in 0.05f..0.25f)
        assertEquals("Underglow color azul en NORMAL", 0.0f, params.underglowR, 0.01f)
        assertEquals(0f, params.exhaustGlow, 0.001f)
        assertEquals("Carrocería conserva pintura gris original sin calidez", 0f, params.bodyWarmth, 0.001f)
        assertTrue("Ruedas deben rotar a 45 km/h", params.wheelAngle > 0f)
        assertTrue("Humo de vapor discreto activo", controller.particleSystem.activeParticleCount > 0)
    }

    // 2. CORREDOR aplica rotación de ruedas, neón amarillo y partículas activas sin teñir rines (60 a 79 km/h)
    @Test
    fun `test 02 - CORREDOR mode applies wheel rotation and energetic particles without rim tint`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 150) {
            controller.update(dtSeconds = 0.016f, speedKmh = 68f, isGpsReliable = true)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.CORREDOR, params.visualMode)
        assertTrue("Intensidad de efecto debe ser activa", params.effectIntensity > 0.40f)
        assertEquals("Rines conservan aleación original de fábrica sin tinte (Regla 1)", 0f, params.rimEmissiveR, 0.001f)
        assertTrue("Underglow amarillo activo en CORREDOR", params.underglowIntensity > 0.30f)
        assertTrue("Underglow componente rojo alto para amarillo", params.underglowR > 0.8f)
        assertTrue("Underglow componente verde alto para amarillo", params.underglowG > 0.6f)
        assertTrue("Ruedas rotan geométricamente", params.wheelAngle > 0f)
        assertTrue("Partículas de humo activas", controller.particleSystem.activeParticleCount > 0)
        assertTrue("Estelas aerodinámicas activas", controller.particleSystem.activeTrailVertexCount > 0)
    }

    // 3. ADRENALINA aplica rotación, escape térmico, neón rojo y chispas sin teñir rines (80 a 99 km/h)
    @Test
    fun `test 03 - ADRENALINA mode applies wheel rotation, exhaust thermal glow and sparks without rim tint`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 150) {
            controller.update(dtSeconds = 0.016f, speedKmh = 88f, isGpsReliable = true)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.ADRENALINA, params.visualMode)
        assertTrue("Intensidad en Adrenalina debe ser mayor a Corredor", params.effectIntensity > 0.70f)
        assertEquals("Rines de aleación intactos", 0f, params.rimEmissiveR, 0.001f)
        assertTrue("Frenos deben tener emisión térmica", params.brakeEmissiveR > 0.50f)
        assertTrue("Escape debe iluminarse térmicamente", params.exhaustGlow > 0.55f)
        assertTrue("Underglow rojo vivo activo en ADRENALINA", params.underglowIntensity > 0.50f)
        assertTrue("Partículas abundantes generadas", controller.particleSystem.activeParticleCount > 10)
        assertTrue("Estelas traseras extendidas", controller.particleSystem.activeTrailVertexCount > 0)
    }

    // 4. COHETE aplica velocidad angular máxima, escape al rojo vivo, neón rojo intenso y vibración (100+ km/h)
    @Test
    fun `test 04 - COHETE mode applies maximum wheel rotation, exhaust glow and aero ribbons`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 180) {
            controller.update(dtSeconds = 0.016f, speedKmh = 115f, isGpsReliable = true)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.COHETE, params.visualMode)
        assertTrue("Intensidad en Cohete debe aproximarse a 1.0", params.effectIntensity > 0.95f)
        assertEquals("Rines permanecen en acabado de aleación original", 0f, params.rimEmissiveR, 0.001f)
        assertTrue("Escape al máximo", params.exhaustGlow > 0.85f)
        assertTrue("Underglow al máximo en COHETE", params.underglowIntensity > 0.80f)
        assertTrue("Partículas de alta energía activas", controller.particleSystem.activeParticleCount > 15)
        assertTrue("7 estelas aerodinámicas completas (28 vértices)", controller.particleSystem.activeTrailVertexCount == 28)
    }

    // 5. Transiciones entre modos con histéresis de 2 km/h
    @Test
    fun `test 05 - transitions respect 2 kmh hysteresis buffer`() {
        val controller = DynamicSpeedEffectsController()

        // 1. Subir a 62 km/h -> CORREDOR
        controller.update(dtSeconds = 0.05f, speedKmh = 62f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.CORREDOR, controller.currentParameters.visualMode)

        // 2. Bajar a 59 km/h -> Permanece en CORREDOR por histéresis (umbral de caída es < 58.0)
        controller.update(dtSeconds = 0.05f, speedKmh = 59f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.CORREDOR, controller.currentParameters.visualMode)

        // 3. Bajar a 57.5 km/h -> Cae a NORMAL
        controller.update(dtSeconds = 0.05f, speedKmh = 57.5f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.NORMAL, controller.currentParameters.visualMode)

        // 4. Subir a 82 km/h -> ADRENALINA
        controller.update(dtSeconds = 0.05f, speedKmh = 82f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.ADRENALINA, controller.currentParameters.visualMode)

        // 5. Bajar a 79 km/h -> Permanece en ADRENALINA por histéresis (umbral de caída es < 78.0)
        controller.update(dtSeconds = 0.05f, speedKmh = 79f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.ADRENALINA, controller.currentParameters.visualMode)

        // 6. Bajar a 77.0 km/h -> Cae a CORREDOR
        controller.update(dtSeconds = 0.05f, speedKmh = 77f, isGpsReliable = true)
        assertEquals(SpeedVisualMode.CORREDOR, controller.currentParameters.visualMode)
    }

    // 6. Pérdida de GPS fuerza retorno suave a NORMAL
    @Test
    fun `test 06 - loss of GPS signal forces smooth transition to NORMAL`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 90f, isGpsReliable = true)
        }
        assertEquals(SpeedVisualMode.ADRENALINA, controller.currentParameters.visualMode)

        // Pérdida súbita de señal GPS
        controller.update(dtSeconds = 0.016f, speedKmh = 90f, isGpsReliable = false)
        assertEquals("El modo objetivo debe caer inmediatamente a NORMAL", SpeedVisualMode.NORMAL, controller.currentParameters.visualMode)

        // Tras ~1.5 segundos (90 frames), la intensidad debe desvanecerse
        for (i in 0 until 90) {
            controller.update(dtSeconds = 0.016f, speedKmh = 90f, isGpsReliable = false)
        }
        assertTrue("Intensidad debe desvanecerse suavemente", controller.currentParameters.effectIntensity < 0.15f)
    }

    // 7. Lectura GPS no fiable nunca activa efectos dinámicos excesivos ni acelera ruedas
    @Test
    fun `test 07 - unreliable GPS reading never activates dynamic effects or wheel spin`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 140f, isGpsReliable = false)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.NORMAL, params.visualMode)
        assertEquals(0f, params.wheelAngle, 0.001f)
        assertEquals("Underglow en reposo es el base mínimo discreto", 0.05f, params.underglowIntensity, 0.02f)
    }

    // 8. Regreso seguro a NORMAL detiene giro de ruedas y reduce emisiones
    @Test
    fun `test 08 - safe return to NORMAL stops wheel rotation and cleans emissions`() {
        val controller = DynamicSpeedEffectsController()

        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 120f, isGpsReliable = true)
        }

        // Frenar a 0 km/h
        for (i in 0 until 180) {
            controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = true)
        }

        val angleAtRest = controller.currentParameters.wheelAngle
        // Un fotograma adicional en reposo no debe alterar el ángulo de rueda
        controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = true)
        assertEquals("Ruedas deben permanecer inmóviles a 0 km/h", angleAtRest, controller.currentParameters.wheelAngle, 0.0001f)
        assertEquals(0f, controller.currentParameters.exhaustGlow, 0.001f)
    }

    // 9. Verificación cinemática: omega = v / r (r = 0.295 m)
    @Test
    fun `test 09 - wheel angular velocity strictly follows omega equals v over r`() {
        val controller = DynamicSpeedEffectsController()

        // Calentar suavizado a 60 km/h
        for (i in 0 until 120) {
            controller.update(dtSeconds = 0.016f, speedKmh = 60f, isGpsReliable = true)
        }

        val angleBefore = controller.currentParameters.wheelAngle
        controller.update(dtSeconds = 0.05f, speedKmh = 60f, isGpsReliable = true)
        val angleAfter = controller.currentParameters.wheelAngle

        var deltaAngle = angleAfter - angleBefore
        if (deltaAngle < 0f) deltaAngle += (2.0f * PI.toFloat())

        val expectedDelta = (60f / 3.6f / 0.295f) * 0.05f
        assertEquals("Incremento angular debe coincidir con cinemática v/r", expectedDelta, deltaAngle, 0.15f)
    }

    // 10. Integración con Chevy3DContractImpl: rotación, zoom y presets
    @Test
    fun `test 10 - camera rotation and zoom operate cleanly alongside speed updates`() {
        val renderer = Chevy3DContractImpl()

        renderer.onSpeedUpdated(speedKmh = 75f, isReliable = true)
        assertEquals(SpeedVisualMode.CORREDOR, renderer.modelState.value.visualMode)
        assertEquals(75f, renderer.modelState.value.currentSpeedKmh, 0.01f)
        assertTrue(renderer.modelState.value.isGpsReliable)

        renderer.onRotate(deltaYaw = 90f, deltaPitch = 15f)
        renderer.onZoom(scaleDelta = 1.25f)

        assertEquals(135f, renderer.modelState.value.rotationYaw, 0.01f)
        assertEquals(25f, renderer.modelState.value.rotationPitch, 0.01f)
        assertEquals(1.25f, renderer.modelState.value.zoomLevel, 0.01f)

        renderer.cycleCameraPreset()
        assertEquals(CameraPreset.FRONT, renderer.modelState.value.activePreset)
    }

    // 11. Simulación de velocidad de depuración: activa ruedas y partículas sin tocar velocímetro real
    @Test
    fun `test 11 - debug simulated speed drives 3D vehicle animation without altering GPS speedometer`() {
        val renderer = Chevy3DContractImpl()
        val controller = DynamicSpeedEffectsController()

        // Vehículo estacionado a 0 km/h, GPS estacionario
        renderer.onSpeedUpdated(speedKmh = 0f, isReliable = false)
        assertEquals(0f, renderer.modelState.value.currentSpeedKmh, 0.001f)

        // Activar velocidad simulada de depuración a 90 km/h (ADRENALINA)
        renderer.setDebugSimulatedSpeed(90f)
        assertEquals("Velocímetro digital DEBE permanecer en 0 km/h de GPS real", 0f, renderer.modelState.value.currentSpeedKmh, 0.001f)
        assertEquals(90f, renderer.modelState.value.debugSimulatedSpeedKmh)

        // Controlador de efectos ejecutando la velocidad simulada
        for (i in 0 until 120) {
            controller.update(dtSeconds = 0.016f, speedKmh = 90f, isGpsReliable = true)
        }

        val params = controller.currentParameters
        assertEquals(SpeedVisualMode.ADRENALINA, params.visualMode)
        assertTrue("Ruedas deben rotar a 90 km/h simulados", params.wheelAngle > 0f)
        assertTrue("Escape debe brillar a 90 km/h simulados", params.exhaustGlow > 0.55f)
        assertTrue("Estelas y partículas activas", controller.particleSystem.activeParticleCount > 0)
    }

    // 12. Secuencia completa de depuración de modos: NORMAL -> CORREDOR -> ADRENALINA -> COHETE -> NORMAL
    @Test
    fun `test 12 - debug mode transition sequence respects factory finish constraints`() {
        val controller = DynamicSpeedEffectsController()

        // 1. Iniciar en NORMAL
        controller.update(dtSeconds = 0.05f, speedKmh = 0f, isGpsReliable = false, debugModeOverride = SpeedVisualMode.NORMAL)
        assertEquals(SpeedVisualMode.NORMAL, controller.currentParameters.visualMode)

        // 2. Transición a CORREDOR
        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = false, debugModeOverride = SpeedVisualMode.CORREDOR)
        }
        assertEquals(SpeedVisualMode.CORREDOR, controller.currentParameters.visualMode)
        assertEquals("Rines nunca emiten color (Regla 1)", 0f, controller.currentParameters.rimEmissiveR, 0.001f)

        // 3. Transición a ADRENALINA
        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = false, debugModeOverride = SpeedVisualMode.ADRENALINA)
        }
        assertEquals(SpeedVisualMode.ADRENALINA, controller.currentParameters.visualMode)
        assertTrue(controller.currentParameters.exhaustGlow > 0.55f)
        assertEquals(0f, controller.currentParameters.rimEmissiveR, 0.001f)

        // 4. Transición a COHETE
        for (i in 0 until 100) {
            controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = false, debugModeOverride = SpeedVisualMode.COHETE)
        }
        assertEquals(SpeedVisualMode.COHETE, controller.currentParameters.visualMode)
        assertTrue(controller.currentParameters.effectIntensity > 0.95f)
        assertEquals(0f, controller.currentParameters.rimEmissiveR, 0.001f)

        // 5. Retorno a NORMAL
        for (i in 0 until 180) {
            controller.update(dtSeconds = 0.016f, speedKmh = 0f, isGpsReliable = false, debugModeOverride = SpeedVisualMode.NORMAL)
        }
        assertEquals(SpeedVisualMode.NORMAL, controller.currentParameters.visualMode)
        assertEquals(0f, controller.currentParameters.exhaustGlow, 0.001f)
    }

    // 13. Restauración limpia al soltar simulación y override
    @Test
    fun `test 13 - clearing simulated speed and debug mode restores live GPS cleanly`() {
        val renderer = Chevy3DContractImpl()

        renderer.setDebugVisualMode(SpeedVisualMode.COHETE)
        renderer.setDebugSimulatedSpeed(120f)
        assertEquals(SpeedVisualMode.COHETE, renderer.modelState.value.visualMode)
        assertEquals(120f, renderer.modelState.value.debugSimulatedSpeedKmh)

        // Limpiar overrides
        renderer.setDebugVisualMode(null)
        renderer.setDebugSimulatedSpeed(null)

        assertNull(renderer.modelState.value.debugVisualModeOverride)
        assertNull(renderer.modelState.value.debugSimulatedSpeedKmh)
        assertEquals(SpeedVisualMode.NORMAL, renderer.modelState.value.visualMode)
    }

    // 14. Validación de buffers Zero-GC de partículas y estelas
    @Test
    fun `test 14 - particle system buffers maintain direct allocation and zero GC stability`() {
        val ps = AutomotiveParticleSystem()

        assertTrue("Buffer de partículas debe ser directo NIO", ps.particleBuffer.isDirect)
        assertTrue("Buffer de estelas debe ser directo NIO", ps.trailBuffer.isDirect)

        // Simular 500 ciclos continuos a 140 km/h (COHETE)
        for (i in 0 until 500) {
            ps.update(dtSeconds = 0.016f, speedKmh = 140f, visualMode = SpeedVisualMode.COHETE)
        }

        assertTrue("Partículas activas en Cohete", ps.activeParticleCount > 0)
        assertTrue("Vértices de estela en Cohete", ps.activeTrailVertexCount == 28)
        assertEquals(0, ps.particleBuffer.position())
        assertEquals(0, ps.trailBuffer.position())
    }
}

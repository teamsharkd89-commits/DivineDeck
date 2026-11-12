package com.divinedeck.launcher.feature.garage3d

import com.divinedeck.launcher.feature.garage3d.data.Chevy3DContractImpl
import com.divinedeck.launcher.feature.garage3d.data.glb.GlbModelLoader
import com.divinedeck.launcher.feature.garage3d.domain.model.CameraPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Chevy3DRendererTest {

    @Test
    fun `rotation yaw wraps around 360 degrees`() {
        val renderer = Chevy3DContractImpl()
        // Initial yaw is 45f
        renderer.onRotate(deltaYaw = 320f, deltaPitch = 0f)
        assertEquals(5f, renderer.modelState.value.rotationYaw, 0.01f)
    }

    @Test
    fun `negative rotation yaw wraps cleanly within 0 to 360 degrees`() {
        val renderer = Chevy3DContractImpl()
        renderer.onRotate(deltaYaw = -90f, deltaPitch = 0f)
        assertEquals(315f, renderer.modelState.value.rotationYaw, 0.01f)
    }

    @Test
    fun `rotation pitch is clamped within safe viewing angles`() {
        val renderer = Chevy3DContractImpl()
        renderer.onRotate(deltaYaw = 0f, deltaPitch = 100f)
        assertEquals(45f, renderer.modelState.value.rotationPitch, 0.01f)

        renderer.onRotate(deltaYaw = 0f, deltaPitch = -100f)
        assertEquals(-15f, renderer.modelState.value.rotationPitch, 0.01f)
    }

    @Test
    fun `zoom is clamped within ergonomic limits`() {
        val renderer = Chevy3DContractImpl()
        renderer.onZoom(scaleDelta = 5.0f)
        assertEquals(2.5f, renderer.modelState.value.zoomLevel, 0.01f)

        renderer.onZoom(scaleDelta = 0.05f)
        assertEquals(0.5f, renderer.modelState.value.zoomLevel, 0.01f)
    }

    @Test
    fun `reset camera restores initial angles and preset`() {
        val renderer = Chevy3DContractImpl()
        renderer.cycleCameraPreset()
        renderer.onRotate(deltaYaw = 90f, deltaPitch = 20f)
        renderer.resetCamera()
        assertEquals(CameraPreset.PERSPECTIVE_3_4, renderer.modelState.value.activePreset)
        assertEquals(45f, renderer.modelState.value.rotationYaw, 0.01f)
        assertEquals(10f, renderer.modelState.value.rotationPitch, 0.01f)
        assertEquals(1.0f, renderer.modelState.value.zoomLevel, 0.01f)
    }

    @Test
    fun `cycleCameraPreset cycles through 3_4 front side rear and wraps back to 3_4`() {
        val renderer = Chevy3DContractImpl()
        assertEquals(CameraPreset.PERSPECTIVE_3_4, renderer.modelState.value.activePreset)
        assertTrue(renderer.modelState.value.isAutoRotating)

        // 1st tap -> Vista frontal
        renderer.cycleCameraPreset()
        assertEquals(CameraPreset.FRONT, renderer.modelState.value.activePreset)
        assertEquals(0f, renderer.modelState.value.rotationYaw, 0.01f)
        assertFalse(renderer.modelState.value.isAutoRotating)

        // 2nd tap -> Vista lateral
        renderer.cycleCameraPreset()
        assertEquals(CameraPreset.SIDE, renderer.modelState.value.activePreset)
        assertEquals(90f, renderer.modelState.value.rotationYaw, 0.01f)

        // 3rd tap -> Vista trasera
        renderer.cycleCameraPreset()
        assertEquals(CameraPreset.REAR, renderer.modelState.value.activePreset)
        assertEquals(180f, renderer.modelState.value.rotationYaw, 0.01f)

        // 4th tap -> Vuelve a Perspectiva 3/4
        renderer.cycleCameraPreset()
        assertEquals(CameraPreset.PERSPECTIVE_3_4, renderer.modelState.value.activePreset)
        assertEquals(45f, renderer.modelState.value.rotationYaw, 0.01f)
    }

    @Test
    fun `setCameraAngle positions camera to inspect front side and rear and pauses auto rotate`() {
        val renderer = Chevy3DContractImpl()
        assertTrue(renderer.modelState.value.isAutoRotating)

        renderer.setCameraAngle(yaw = 0f, pitch = 8f)
        assertEquals(0f, renderer.modelState.value.rotationYaw, 0.01f)
        assertEquals(8f, renderer.modelState.value.rotationPitch, 0.01f)
        assertFalse(renderer.modelState.value.isAutoRotating)

        renderer.setCameraAngle(yaw = 180f, pitch = 12f)
        assertEquals(180f, renderer.modelState.value.rotationYaw, 0.01f)
        assertEquals(12f, renderer.modelState.value.rotationPitch, 0.01f)

        renderer.setAutoRotate(true)
        assertTrue(renderer.modelState.value.isAutoRotating)
    }

    @Test
    fun `chevy c2 2009 glb binary model v31 loads with high density spline geometry and pbr materials`() {
        val glbFile = sequenceOf(
            File("src/main/assets/models/chevy_c2_2009.glb"),
            File("app/src/main/assets/models/chevy_c2_2009.glb")
        ).firstOrNull { it.exists() }

        assertNotNull("El archivo 3D real chevy_c2_2009.glb debe existir en assets/models/", glbFile)
        val model = glbFile!!.inputStream().use { GlbModelLoader.loadFromStream(it) }

        assertEquals("2.0", model.version)
        assertEquals("Chevrolet_Chevy_C2_2009_2Door", model.nodeName)
        assertEquals(11, model.materials.size)
        assertEquals(11, model.subMeshes.size)

        // Verifica presupuesto geométrico óptimo para MediaTek Helio G99 / Mali-G57 MC2 (~15k vértices, ~20k triángulos)
        assertTrue("El modelo v3.1 debe tener >14,000 vértices con normales suaves", model.totalVertices > 14_000)
        assertTrue("El modelo v3.1 debe tener >18,000 triángulos", model.totalTriangles > 18_000)

        // Verifica materiales PBR diferenciados del Chevy C2 2009 gris metálico
        val materialNames = model.materials.map { it.name }.toSet()
        assertTrue(materialNames.contains("ChevyC2_MetallicSilverGrey"))
        assertTrue(materialNames.contains("ChevyC2_TintedGlass"))
        assertTrue(materialNames.contains("ChevyC2_BlackTrim"))
        assertTrue(materialNames.contains("ChevyC2_TireRubber"))
        assertTrue(materialNames.contains("ChevyC2_AlloyRim"))
        assertTrue(materialNames.contains("ChevyC2_ChromeReflector"))
        assertTrue(materialNames.contains("ChevyC2_SmokedHousing"))
        assertTrue(materialNames.contains("ChevyC2_TaillightRed"))
        assertTrue(materialNames.contains("ChevyC2_BrakeRed"))
        assertTrue(materialNames.contains("ChevyC2_LicensePlate"))
        assertTrue(materialNames.contains("ChevyC2_UnderbodyShadow"))

        // Verifica proporciones reales del Chevrolet Chevy C2 2009 Hatchback 2 Puertas
        assertTrue("Ancho con espejos y plataforma ~1.7m..1.95m", model.widthX in 1.60f..1.95f)
        assertTrue("Alto con antena ~1.4m..1.8m", model.heightY in 1.40f..1.80f)
        assertTrue("Largo total ~3.7m..4.1m", model.lengthZ in 3.70f..4.15f)
    }
}

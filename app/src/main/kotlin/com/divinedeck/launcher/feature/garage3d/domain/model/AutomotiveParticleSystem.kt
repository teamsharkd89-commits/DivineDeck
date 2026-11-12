package com.divinedeck.launcher.feature.garage3d.domain.model

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sistema de partículas avanzado y estelas aerodinámicas 3D para el Chevrolet Chevy C2 2009 (Fase 4.1C).
 *
 * Características para ARM Mali-G57 MC2:
 * - Cero asignaciones en bucle de renderizado (Zero-GC): estructuras y FloatBuffers pre-asignados.
 * - Simulación cinemática física automotriz: humo de escape con elevación térmica y vórtice aerodinámico,
 *   chispas incandescentes con gravedad y enfriamiento de color, humo de fricción en los 4 neumáticos
 *   y estelas aerodinámicas traseras rojas (*ribbons*) con flutter dinámico ancladas a alerones y difusor.
 * - Paletas cromáticas centralizadas en [SpeedVisualMode.config]:
 *   - NORMAL: Azul (#0088FF).
 *   - CORREDOR: Amarillo (#FFC800).
 *   - ADRENALINA: Rojo (#FF2200).
 *   - COHETE: Rojo intenso (#FF0800).
 */
class AutomotiveParticleSystem {

    companion object {
        const val MAX_SMOKE_PARTICLES = 120
        const val MAX_TIRE_SMOKE_PARTICLES = 80
        const val MAX_SPARK_PARTICLES = 60
        const val TOTAL_PARTICLES = MAX_SMOKE_PARTICLES + MAX_TIRE_SMOKE_PARTICLES + MAX_SPARK_PARTICLES

        // Floats por vértice de partícula: X, Y, Z, R, G, B, A, Size (8 floats)
        const val FLOATS_PER_PARTICLE = 8

        // Coordenadas locales de emisores físicos del Chevy C2 2009
        val EXHAUST_TIP = floatArrayOf(-0.450f, 0.220f, -1.910f)

        val TIRE_CONTACT_FL = floatArrayOf(-0.695f, 0.015f, 1.050f)
        val TIRE_CONTACT_FR = floatArrayOf(0.695f, 0.015f, 1.050f)
        val TIRE_CONTACT_RL = floatArrayOf(-0.695f, 0.015f, -1.280f)
        val TIRE_CONTACT_RR = floatArrayOf(0.695f, 0.015f, -1.280f)

        // Anclajes de estelas aerodinámicas (alerón superior, alerón intermedio y difusor trasero)
        val SPOILER_TOP_L = floatArrayOf(-0.52f, 1.334f, -1.335f)
        val SPOILER_TOP_C = floatArrayOf(0.00f, 1.340f, -1.335f)
        val SPOILER_TOP_R = floatArrayOf(0.52f, 1.334f, -1.335f)
        val SPOILER_MID_L = floatArrayOf(-0.54f, 0.956f, -1.714f)
        val SPOILER_MID_R = floatArrayOf(0.54f, 0.956f, -1.714f)
        val DIFFUSER_L = floatArrayOf(-0.68f, 0.220f, -1.860f)
        val DIFFUSER_R = floatArrayOf(0.68f, 0.220f, -1.860f)

        // 7 estelas con 4 vértices cada una (quads) = 28 vértices. Cada vértice tiene X, Y, Z, R, G, B, A (7 floats)
        const val MAX_TRAIL_VERTICES = 7 * 4
        const val FLOATS_PER_TRAIL_VERTEX = 7
    }

    private class Particle {
        var active: Boolean = false
        var x: Float = 0f
        var y: Float = 0f
        var z: Float = 0f
        var vx: Float = 0f
        var vy: Float = 0f
        var vz: Float = 0f
        var size: Float = 0.05f
        var growRate: Float = 0.1f
        var life: Float = 0f
        var maxLife: Float = 1f
        var r: Float = 1f
        var g: Float = 1f
        var b: Float = 1f
        var a: Float = 1f
        var initialA: Float = 0.8f
    }

    private val smokePool = Array(MAX_SMOKE_PARTICLES) { Particle() }
    private val tireSmokePool = Array(MAX_TIRE_SMOKE_PARTICLES) { Particle() }
    private val sparkPool = Array(MAX_SPARK_PARTICLES) { Particle() }

    // Búferes directos pre-asignados para dibujo OpenGL
    val particleBuffer: FloatBuffer = ByteBuffer.allocateDirect(TOTAL_PARTICLES * FLOATS_PER_PARTICLE * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    val trailBuffer: FloatBuffer = ByteBuffer.allocateDirect(MAX_TRAIL_VERTICES * FLOATS_PER_TRAIL_VERTEX * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    var activeParticleCount: Int = 0
        private set
    var activeTrailVertexCount: Int = 0
        private set

    private var exhaustTimer: Float = 0f
    private var tireSmokeTimer: Float = 0f
    private var accumulatedTime: Float = 0f

    fun update(
        dtSeconds: Float,
        speedKmh: Float,
        visualMode: SpeedVisualMode
    ) {
        val dt = dtSeconds.coerceIn(0.001f, 0.08f)
        accumulatedTime += dt
        val speedMps = speedKmh / 3.6f

        // 1. Configuración de paleta por modo desde la configuración centralizada
        val cfg = visualMode.config
        val mainR = cfg.particleMainR
        val mainG = cfg.particleMainG
        val mainB = cfg.particleMainB
        val secR = cfg.particleSecR
        val secG = cfg.particleSecG
        val secB = cfg.particleSecB

        val smokeRate: Float
        val sparkRate: Float
        val tireSmokeRate: Float

        when (visualMode) {
            SpeedVisualMode.NORMAL -> {
                smokeRate = if (speedKmh > 1f) 16f else 8f
                sparkRate = 0f
                tireSmokeRate = 0f
            }
            SpeedVisualMode.CORREDOR -> {
                smokeRate = 38f
                sparkRate = 0f
                tireSmokeRate = if (speedKmh >= 60f) 22f else 0f
            }
            SpeedVisualMode.ADRENALINA -> {
                smokeRate = 65f
                sparkRate = 22f
                tireSmokeRate = 38f
            }
            SpeedVisualMode.COHETE -> {
                smokeRate = 95f
                sparkRate = 50f
                tireSmokeRate = 55f
            }
        }

        // 2. Actualización de partículas existentes con dispersión física y vórtice de flujo
        updateParticleList(smokePool, dt, hasGravity = false, vortexCurl = true)
        updateParticleList(tireSmokePool, dt, hasGravity = false, vortexCurl = false)
        updateParticleList(sparkPool, dt, hasGravity = true, vortexCurl = false)

        // 3. Emisión de humo de escape y chispas
        exhaustTimer += dt
        val exhaustInterval = 1f / smokeRate.coerceAtLeast(1f)
        while (exhaustTimer >= exhaustInterval) {
            exhaustTimer -= exhaustInterval
            spawnExhaustSmoke(speedMps, visualMode, mainR, mainG, mainB, secR, secG, secB)
            if (sparkRate > 0f && Random.nextFloat() < (sparkRate * dt * 3.8f)) {
                spawnExhaustSpark(speedMps, mainR, mainG, mainB, secR, secG, secB)
            }
        }

        // 4. Emisión de humo de neumáticos (Fricción / Tracción en 4 ruedas)
        if (tireSmokeRate > 0f) {
            tireSmokeTimer += dt
            val tireInterval = 1f / tireSmokeRate.coerceAtLeast(1f)
            while (tireSmokeTimer >= tireInterval) {
                tireSmokeTimer -= tireInterval
                spawnTireSmoke(speedMps, mainR, mainG, mainB)
            }
        }

        // 5. Empaquetar partículas activas en el FloatBuffer
        particleBuffer.clear()
        var pCount = 0
        pCount += writeParticlesToBuffer(smokePool)
        pCount += writeParticlesToBuffer(tireSmokePool)
        pCount += writeParticlesToBuffer(sparkPool)
        activeParticleCount = pCount
        particleBuffer.position(0)

        // 6. Generar estelas aerodinámicas traseras (rojas continuas y reactivas a velocidad)
        generateAeroTrails(speedKmh, visualMode)
    }

    private fun updateParticleList(pool: Array<Particle>, dt: Float, hasGravity: Boolean, vortexCurl: Boolean) {
        for (p in pool) {
            if (!p.active) continue
            p.life += dt
            if (p.life >= p.maxLife) {
                p.active = false
                continue
            }
            val progress = p.life / p.maxLife
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt

            // Vórtice aerodinámico de estela
            if (vortexCurl) {
                p.x += sin(p.z * 3.5f + p.life * 5.0f) * 0.035f * dt
            }

            if (hasGravity) {
                p.vy -= 2.6f * dt // Gravedad hacia abajo para chispas
            }
            p.size += p.growRate * dt

            // Decaimiento cúbico suave de opacidad para evitar cortes bruscos
            val fade = (1f - progress)
            p.a = p.initialA * fade * fade
        }
    }

    private fun spawnExhaustSmoke(
        speedMps: Float,
        mode: SpeedVisualMode,
        mR: Float, mG: Float, mB: Float,
        sR: Float, sG: Float, sB: Float
    ) {
        val p = smokePool.firstOrNull { !it.active } ?: return
        p.active = true
        p.x = EXHAUST_TIP[0] + (Random.nextFloat() - 0.5f) * 0.040f
        p.y = EXHAUST_TIP[1] + (Random.nextFloat() - 0.5f) * 0.040f
        p.z = EXHAUST_TIP[2] + (Random.nextFloat() - 0.5f) * 0.025f

        // Velocidad: expulsión hacia atrás (-Z) fuertemente impulsada por velocidad del vehículo
        p.vx = (Random.nextFloat() - 0.5f) * 0.10f
        p.vy = 0.08f + Random.nextFloat() * 0.14f
        p.vz = -(1.1f + speedMps * 0.65f + Random.nextFloat() * 0.35f)

        // Variaciones automotrices de tamaño y duración
        p.size = 0.035f + Random.nextFloat() * 0.025f
        p.growRate = 0.22f + Random.nextFloat() * 0.12f
        p.life = 0f
        p.maxLife = 0.65f + Random.nextFloat() * 0.55f

        val useSec = Random.nextFloat() > 0.45f
        val tr = if (useSec) sR else mR
        val tg = if (useSec) sG else mG
        val tb = if (useSec) sB else mB

        // Mezcla con vapor/humo para apariencia realista
        p.r = 0.70f * tr + 0.30f * 0.85f
        p.g = 0.70f * tg + 0.30f * 0.85f
        p.b = 0.70f * tb + 0.30f * 0.85f
        p.initialA = if (mode == SpeedVisualMode.NORMAL) 0.38f else 0.78f
        p.a = p.initialA
    }

    private fun spawnExhaustSpark(
        speedMps: Float,
        mR: Float, mG: Float, mB: Float,
        sR: Float, sG: Float, sB: Float
    ) {
        val sp = sparkPool.firstOrNull { !it.active } ?: return
        sp.active = true
        sp.x = EXHAUST_TIP[0] + (Random.nextFloat() - 0.5f) * 0.025f
        sp.y = EXHAUST_TIP[1] + (Random.nextFloat() - 0.5f) * 0.025f
        sp.z = EXHAUST_TIP[2]
        sp.vx = (Random.nextFloat() - 0.45f) * 0.40f
        sp.vy = 0.28f + Random.nextFloat() * 0.60f
        sp.vz = -(2.0f + speedMps * 0.85f + Random.nextFloat() * 0.6f)
        sp.size = 0.020f + Random.nextFloat() * 0.012f
        sp.growRate = -0.006f
        sp.life = 0f
        sp.maxLife = 0.30f + Random.nextFloat() * 0.38f

        // Color incandescente con transición de brillo
        sp.r = 1.0f
        sp.g = 0.35f + Random.nextFloat() * 0.45f
        sp.b = 0.05f
        sp.initialA = 1.0f
        sp.a = 1.0f
    }

    private fun spawnTireSmoke(
        speedMps: Float,
        mR: Float, mG: Float, mB: Float
    ) {
        val p = tireSmokePool.firstOrNull { !it.active } ?: return
        val wheelIdx = Random.nextInt(4)
        val anchor = when (wheelIdx) {
            0 -> TIRE_CONTACT_FL
            1 -> TIRE_CONTACT_FR
            2 -> TIRE_CONTACT_RL
            else -> TIRE_CONTACT_RR
        }
        p.active = true
        p.x = anchor[0] + (Random.nextFloat() - 0.5f) * 0.08f
        p.y = anchor[1] + Random.nextFloat() * 0.035f
        p.z = anchor[2] - Random.nextFloat() * 0.06f

        p.vx = (if (anchor[0] < 0f) -1f else 1f) * (0.05f + Random.nextFloat() * 0.07f)
        p.vy = 0.05f + Random.nextFloat() * 0.08f
        p.vz = -(0.6f + speedMps * 0.45f)

        p.size = 0.045f + Random.nextFloat() * 0.025f
        p.growRate = 0.25f + Random.nextFloat() * 0.10f
        p.life = 0f
        p.maxLife = 0.55f + Random.nextFloat() * 0.40f

        // Humo blanco con sutil tintura del modo
        p.r = 0.85f + 0.15f * mR
        p.g = 0.85f + 0.15f * mG
        p.b = 0.85f + 0.15f * mB
        p.initialA = 0.48f
        p.a = 0.48f
    }

    private fun writeParticlesToBuffer(pool: Array<Particle>): Int {
        var count = 0
        for (p in pool) {
            if (p.active && p.a > 0.005f) {
                particleBuffer.put(p.x)
                particleBuffer.put(p.y)
                particleBuffer.put(p.z)
                particleBuffer.put(p.r)
                particleBuffer.put(p.g)
                particleBuffer.put(p.b)
                particleBuffer.put(p.a)
                particleBuffer.put(p.size)
                count++
            }
        }
        return count
    }

    private fun generateAeroTrails(
        speedKmh: Float,
        visualMode: SpeedVisualMode
    ) {
        trailBuffer.clear()
        if (speedKmh < 10f) {
            activeTrailVertexCount = 0
            return
        }

        val cfg = visualMode.config

        // Longitud de estela roja escalada progresivamente con la velocidad
        val speedFactor = (speedKmh / 100f).coerceIn(0.1f, 1.8f)
        val trailLength = cfg.trailBaseLength * speedFactor

        val flutterFreq = 24.0f
        val flutterTime = accumulatedTime * flutterFreq

        // Variación de anchura y pulsación para que no sea una línea estática
        val widthPulse = 1.0f + 0.12f * sin(accumulatedTime * 9.0f)
        val trailWidth = 0.042f * widthPulse

        // Anclajes activos en la trasera del Chevy C2
        val anchors = arrayOf(
            SPOILER_TOP_L, SPOILER_TOP_C, SPOILER_TOP_R,
            SPOILER_MID_L, SPOILER_MID_R,
            DIFFUSER_L, DIFFUSER_R
        )

        val alphaStart = when (visualMode) {
            SpeedVisualMode.NORMAL -> 0.40f
            SpeedVisualMode.CORREDOR -> 0.70f
            SpeedVisualMode.ADRENALINA -> 0.88f
            SpeedVisualMode.COHETE -> 0.98f
        }

        // Color de la estela: Rojo conservado según especificación de Parte C
        val trailR = cfg.trailR
        val trailG = cfg.trailG
        val trailB = cfg.trailB

        var vertCount = 0
        for ((idx, anchor) in anchors.withIndex()) {
            val flutterX = sin(flutterTime + idx * 1.35f) * 0.020f
            val flutterY = cos(flutterTime + idx * 0.95f) * 0.016f

            val ax = anchor[0]
            val ay = anchor[1]
            val az = anchor[2]

            // Vértice 0: Base izquierda (en el automóvil)
            trailBuffer.put(ax - trailWidth * 0.5f); trailBuffer.put(ay); trailBuffer.put(az)
            trailBuffer.put(trailR); trailBuffer.put(trailG); trailBuffer.put(trailB); trailBuffer.put(alphaStart)

            // Vértice 1: Base derecha (en el automóvil)
            trailBuffer.put(ax + trailWidth * 0.5f); trailBuffer.put(ay); trailBuffer.put(az)
            trailBuffer.put(trailR); trailBuffer.put(trailG); trailBuffer.put(trailB); trailBuffer.put(alphaStart)

            // Vértice 2: Punta derecha (atrás de la carrocería)
            trailBuffer.put(ax + trailWidth * 0.28f + flutterX); trailBuffer.put(ay + flutterY); trailBuffer.put(az - trailLength)
            trailBuffer.put(trailR); trailBuffer.put(trailG * 0.6f); trailBuffer.put(trailB * 0.6f); trailBuffer.put(0.0f)

            // Vértice 3: Punta izquierda (atrás de la carrocería)
            trailBuffer.put(ax - trailWidth * 0.28f + flutterX); trailBuffer.put(ay + flutterY); trailBuffer.put(az - trailLength)
            trailBuffer.put(trailR); trailBuffer.put(trailG * 0.6f); trailBuffer.put(trailB * 0.6f); trailBuffer.put(0.0f)

            vertCount += 4
        }

        activeTrailVertexCount = vertCount
        trailBuffer.position(0)
    }
}

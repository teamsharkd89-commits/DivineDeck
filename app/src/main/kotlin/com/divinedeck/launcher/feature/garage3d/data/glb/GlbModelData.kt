package com.divinedeck.launcher.feature.garage3d.data.glb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Material PBR extraído de un archivo binario glTF 2.0 (`.glb`).
 */
data class GlbMaterial(
    val name: String,
    val baseColor: FloatArray,
    val metallicFactor: Float,
    val roughnessFactor: Float,
    val emissiveFactor: FloatArray,
    val isBlend: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlbMaterial) return false
        return name == other.name &&
            baseColor.contentEquals(other.baseColor) &&
            metallicFactor == other.metallicFactor &&
            roughnessFactor == other.roughnessFactor &&
            emissiveFactor.contentEquals(other.emissiveFactor) &&
            isBlend == other.isBlend
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + baseColor.contentHashCode()
        result = 31 * result + metallicFactor.hashCode()
        result = 31 * result + roughnessFactor.hashCode()
        result = 31 * result + emissiveFactor.contentHashCode()
        result = 31 * result + isBlend.hashCode()
        return result
    }
}

/**
 * Primitiva geométrica 3D con buffers nativos listos para OpenGL ES 3.0.
 */
data class GlbSubMesh(
    val index: Int,
    val materialIndex: Int,
    val material: GlbMaterial,
    val vertexCount: Int,
    val indexCount: Int,
    val positions: FloatArray,
    val normals: FloatArray,
    val indices: ShortArray
) {
    val triangleCount: Int get() = indexCount / 3

    fun createPositionBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(positions)
                position(0)
            }

    fun createNormalBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(normals)
                position(0)
            }

    fun createIndexBuffer(): ShortBuffer =
        ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlbSubMesh) return false
        return index == other.index &&
            materialIndex == other.materialIndex &&
            vertexCount == other.vertexCount &&
            indexCount == other.indexCount
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + materialIndex
        result = 31 * result + vertexCount
        result = 31 * result + indexCount
        return result
    }
}

/**
 * Modelo 3D glTF 2.0 (`.glb`) cargado y validado para el Chevrolet Chevy C2 2009.
 */
data class GlbModelData(
    val generator: String,
    val version: String,
    val nodeName: String,
    val meshName: String,
    val materials: List<GlbMaterial>,
    val subMeshes: List<GlbSubMesh>,
    val minBounds: FloatArray,
    val maxBounds: FloatArray
) {
    val totalVertices: Int get() = subMeshes.sumOf { it.vertexCount }
    val totalTriangles: Int get() = subMeshes.sumOf { it.triangleCount }
    val widthX: Float get() = maxBounds[0] - minBounds[0]
    val heightY: Float get() = maxBounds[1] - minBounds[1]
    val lengthZ: Float get() = maxBounds[2] - minBounds[2]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GlbModelData) return false
        return generator == other.generator &&
            version == other.version &&
            nodeName == other.nodeName &&
            meshName == other.meshName &&
            materials == other.materials &&
            subMeshes == other.subMeshes
    }

    override fun hashCode(): Int {
        var result = generator.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + nodeName.hashCode()
        result = 31 * result + meshName.hashCode()
        result = 31 * result + materials.hashCode()
        result = 31 * result + subMeshes.hashCode()
        return result
    }
}

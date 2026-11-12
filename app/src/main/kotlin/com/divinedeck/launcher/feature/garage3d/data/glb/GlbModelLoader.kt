package com.divinedeck.launcher.feature.garage3d.data.glb

import android.content.Context
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Cargador nativo de archivos binarios glTF 2.0 (`.glb`) para DIVINE DECK (Fase 03).
 *
 * Lee la cabecera IEEE glTF 2.0 (`0x46546C67`), el chunk JSON (`0x4E4F534A`) y el
 * chunk binario (`0x004E4942`), extrayendo sub-mallas, posiciones, normales,
 * índices y materiales PBR sin requerir dependencias externas pesadas.
 */
object GlbModelLoader {

    const val DEFAULT_ASSET_PATH = "models/chevy_c2_2009.glb"
    private const val GLB_MAGIC = 0x46546C67 // "glTF"
    private const val GLB_VERSION_2 = 2
    private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON"
    private const val CHUNK_TYPE_BIN = 0x004E4942  // "BIN\0"

    @Volatile
    private var cachedModel: GlbModelData? = null

    fun loadFromAssets(context: Context, assetPath: String = DEFAULT_ASSET_PATH): GlbModelData {
        cachedModel?.let { return it }
        synchronized(this) {
            cachedModel?.let { return it }
            val loaded = context.assets.open(assetPath).use { stream ->
                loadFromStream(stream)
            }
            cachedModel = loaded
            return loaded
        }
    }

    fun loadFromStream(inputStream: InputStream): GlbModelData {
        return loadFromBytes(inputStream.readBytes())
    }

    @Suppress("UNCHECKED_CAST")
    fun loadFromBytes(bytes: ByteArray): GlbModelData {
        require(bytes.size >= 28) { "Archivo GLB inválido: tamaño insuficiente (${bytes.size} bytes)." }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int
        val version = buffer.int
        val totalLength = buffer.int

        require(magic == GLB_MAGIC) {
            "Cabecera mágica GLB inválida: 0x${magic.toUInt().toString(16)}"
        }
        require(version == GLB_VERSION_2) {
            "Versión glTF no soportada: $version (se requiere glTF 2.0)"
        }
        require(totalLength <= bytes.size) {
            "Longitud declarada GLB ($totalLength) excede bytes disponibles (${bytes.size})"
        }

        // Chunk 0: JSON
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int
        require(jsonChunkType == CHUNK_TYPE_JSON) {
            "El primer chunk del GLB debe ser JSON (0x4E4F534A)"
        }
        val jsonString = String(bytes, 20, jsonChunkLength, Charsets.UTF_8)

        // Chunk 1: BIN
        val binHeaderOffset = 20 + jsonChunkLength
        buffer.position(binHeaderOffset)
        val binChunkLength = buffer.int
        val binChunkType = buffer.int
        require(binChunkType == CHUNK_TYPE_BIN) {
            "El segundo chunk del GLB debe ser BIN (0x004E4942)"
        }
        val binStartOffset = binHeaderOffset + 8
        require(binStartOffset + binChunkLength <= bytes.size) {
            "El chunk binario GLB excede el tamaño del archivo"
        }

        val root = MiniJsonParser(jsonString).parseObject()

        val assetObj = root["asset"] as? Map<String, Any?> ?: emptyMap()
        val generator = assetObj["generator"] as? String ?: "Unknown"
        val gltfVersion = assetObj["version"] as? String ?: "2.0"

        val nodesList = root["nodes"] as? List<Map<String, Any?>> ?: emptyList()
        val nodeName = nodesList.firstOrNull()?.get("name") as? String ?: "ChevyC2_2009"

        val materialsJson = root["materials"] as? List<Map<String, Any?>> ?: emptyList()
        val materials = materialsJson.map { matMap ->
            val name = matMap["name"] as? String ?: "Material"
            val pbr = matMap["pbrMetallicRoughness"] as? Map<String, Any?> ?: emptyMap()
            val baseColorList = pbr["baseColorFactor"] as? List<Number>
            val baseColor = if (baseColorList != null && baseColorList.size >= 4) {
                floatArrayOf(
                    baseColorList[0].toFloat(),
                    baseColorList[1].toFloat(),
                    baseColorList[2].toFloat(),
                    baseColorList[3].toFloat()
                )
            } else {
                floatArrayOf(0.75f, 0.78f, 0.80f, 1.0f)
            }
            val metallic = (pbr["metallicFactor"] as? Number)?.toFloat() ?: 0.5f
            val roughness = (pbr["roughnessFactor"] as? Number)?.toFloat() ?: 0.3f
            val emissiveList = matMap["emissiveFactor"] as? List<Number>
            val emissive = if (emissiveList != null && emissiveList.size >= 3) {
                floatArrayOf(
                    emissiveList[0].toFloat(),
                    emissiveList[1].toFloat(),
                    emissiveList[2].toFloat()
                )
            } else {
                floatArrayOf(0f, 0f, 0f)
            }
            val alphaMode = matMap["alphaMode"] as? String ?: "OPAQUE"
            GlbMaterial(
                name = name,
                baseColor = baseColor,
                metallicFactor = metallic,
                roughnessFactor = roughness,
                emissiveFactor = emissive,
                isBlend = alphaMode == "BLEND"
            )
        }

        val bufferViews = root["bufferViews"] as? List<Map<String, Any?>> ?: emptyList()
        val accessors = root["accessors"] as? List<Map<String, Any?>> ?: emptyList()
        val meshes = root["meshes"] as? List<Map<String, Any?>> ?: emptyList()
        require(meshes.isNotEmpty()) { "El archivo GLB no contiene mallas 3D" }

        val firstMesh = meshes.first()
        val meshName = firstMesh["name"] as? String ?: "ChevyC2_Mesh"
        val primitives = firstMesh["primitives"] as? List<Map<String, Any?>> ?: emptyList()

        val globalMin = floatArrayOf(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
        val globalMax = floatArrayOf(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)

        val subMeshes = primitives.mapIndexed { primIdx, prim ->
            val attrs = prim["attributes"] as? Map<String, Any?> ?: emptyMap()
            val posAccIdx = (attrs["POSITION"] as Number).toInt()
            val normAccIdx = (attrs["NORMAL"] as Number).toInt()
            val indicesAccIdx = (prim["indices"] as Number).toInt()
            val matIdx = (prim["material"] as? Number)?.toInt() ?: 0

            val posAcc = accessors[posAccIdx]
            val normAcc = accessors[normAccIdx]
            val idxAcc = accessors[indicesAccIdx]

            val vertexCount = (posAcc["count"] as Number).toInt()
            val indexCount = (idxAcc["count"] as Number).toInt()

            val posBv = bufferViews[(posAcc["bufferView"] as Number).toInt()]
            val normBv = bufferViews[(normAcc["bufferView"] as Number).toInt()]
            val idxBv = bufferViews[(idxAcc["bufferView"] as Number).toInt()]

            val posByteOffset = binStartOffset + ((posBv["byteOffset"] as? Number)?.toInt() ?: 0) +
                ((posAcc["byteOffset"] as? Number)?.toInt() ?: 0)
            val normByteOffset = binStartOffset + ((normBv["byteOffset"] as? Number)?.toInt() ?: 0) +
                ((normAcc["byteOffset"] as? Number)?.toInt() ?: 0)
            val idxByteOffset = binStartOffset + ((idxBv["byteOffset"] as? Number)?.toInt() ?: 0) +
                ((idxAcc["byteOffset"] as? Number)?.toInt() ?: 0)

            val positions = FloatArray(vertexCount * 3)
            buffer.position(posByteOffset)
            for (i in 0 until vertexCount) {
                val x = buffer.float
                val y = buffer.float
                val z = buffer.float
                positions[i * 3] = x
                positions[i * 3 + 1] = y
                positions[i * 3 + 2] = z
                globalMin[0] = min(globalMin[0], x)
                globalMin[1] = min(globalMin[1], y)
                globalMin[2] = min(globalMin[2], z)
                globalMax[0] = max(globalMax[0], x)
                globalMax[1] = max(globalMax[1], y)
                globalMax[2] = max(globalMax[2], z)
            }

            val normals = FloatArray(vertexCount * 3)
            buffer.position(normByteOffset)
            for (i in 0 until vertexCount * 3) {
                normals[i] = buffer.float
            }

            val componentType = (idxAcc["componentType"] as Number).toInt()
            val indices = ShortArray(indexCount)
            buffer.position(idxByteOffset)
            for (i in 0 until indexCount) {
                indices[i] = when (componentType) {
                    5123 -> buffer.short // UNSIGNED_SHORT
                    5125 -> buffer.int.toShort() // UNSIGNED_INT
                    else -> (buffer.get().toInt() and 0xFF).toShort()
                }
            }

            GlbSubMesh(
                index = primIdx,
                materialIndex = matIdx,
                material = materials.getOrElse(matIdx) { materials.first() },
                vertexCount = vertexCount,
                indexCount = indexCount,
                positions = positions,
                normals = normals,
                indices = indices
            )
        }

        return GlbModelData(
            generator = generator,
            version = gltfVersion,
            nodeName = nodeName,
            meshName = meshName,
            materials = materials,
            subMeshes = subMeshes,
            minBounds = globalMin,
            maxBounds = globalMax
        )
    }

    /**
     * Parser JSON ligero y autónomo (compatible con JVM local tests y Android runtime).
     */
    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseObject(): Map<String, Any?> {
            skipWhitespace()
            expect('{')
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                val value = parseValue()
                map[key] = value
                skipWhitespace()
                when (val ch = peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("Carácter inesperado '$ch' en posición $pos")
                }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                skipWhitespace()
                list.add(parseValue())
                skipWhitespace()
                when (val ch = peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("Carácter inesperado '$ch' en arreglo JSON pos $pos")
                }
            }
            return list
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            return when (val ch = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> {
                    pos += 4
                    null
                }
                else -> if (ch == '-' || ch.isDigit()) parseNumber()
                else throw IllegalArgumentException("Valor JSON inválido en pos $pos: '$ch'")
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\' && pos < src.length) {
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = src.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < src.length && (src[pos].isDigit() || src[pos] in "-+.eE")) {
                pos++
            }
            return src.substring(start, pos).toDouble()
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else {
                pos += 5
                false
            }
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        private fun expect(expected: Char) {
            val actual = peek()
            require(actual == expected) { "Se esperaba '$expected' pero se encontró '$actual' en pos $pos" }
            pos++
        }
    }
}

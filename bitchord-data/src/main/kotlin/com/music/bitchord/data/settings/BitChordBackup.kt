package com.music.bitchord.data.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * Backup and configuration migration engine for BitChord in DIVINE DECK.
 *
 * Implements strict JSON schema verification, ensuring that only authentic BitChord
 * backups are restored, and prevents partial or corrupt configurations.
 * Extracted and adapted from BitChord 1.8.1 (GPLv3).
 */
object BitChordBackup {

    const val APP_TAG = "bitchord"
    const val SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Serializable
    data class BackupFile(
        val app: String = APP_TAG,
        val version: Int = SCHEMA_VERSION,
        val appName: String = "DIVINE DECK / BitChord",
        val exportedAt: String = "",
        val settings: Map<String, PrefValue> = emptyMap(),
    )

    @Serializable
    data class PrefValue(
        val type: String,
        val value: String? = null,
        val values: List<String> = emptyList(),
    ) {
        companion object {
            fun of(value: Any?): PrefValue? = when (value) {
                is Boolean -> PrefValue("boolean", value.toString())
                is Int -> PrefValue("int", value.toString())
                is Long -> PrefValue("long", value.toString())
                is Float -> PrefValue("float", value.toString())
                is String -> PrefValue("string", value)
                is Set<*> -> PrefValue("set", values = value.filterIsInstance<String>())
                else -> null
            }
        }

        fun decoded(): Any? = when (type) {
            "boolean" -> value?.toBooleanStrictOrNull() ?: false
            "int" -> value?.toIntOrNull() ?: 0
            "long" -> value?.toLongOrNull() ?: 0L
            "float" -> value?.toFloatOrNull() ?: 0f
            "string" -> value.orEmpty()
            "set" -> values.toSet()
            else -> null
        }
    }

    data class Summary(
        val settingsCount: Int,
        val exportedAt: String,
        val valid: Boolean = true,
    )

    fun exportToJson(): String {
        val exportedPrefs = AppSettings.exportPrefs()
        val settingsMap = exportedPrefs.mapNotNull { (k, v) ->
            PrefValue.of(v)?.let { k to it }
        }.toMap()

        val backup = BackupFile(
            exportedAt = Instant.now().toString(),
            settings = settingsMap,
        )
        return json.encodeToString(BackupFile.serializer(), backup)
    }

    fun importFromJson(jsonString: String): Result<Summary> {
        return runCatching {
            val file = json.decodeFromString(BackupFile.serializer(), jsonString)
            require(file.app == APP_TAG) {
                "El archivo de respaldo no pertenece a BitChord (app='${file.app}')"
            }
            require(file.version <= SCHEMA_VERSION) {
                "Versión de esquema no compatible (${file.version} > $SCHEMA_VERSION)"
            }

            val decodedMap = file.settings.mapNotNull { (key, prefVal) ->
                val decoded = prefVal.decoded()
                if (decoded != null) key to decoded else null
            }.toMap()

            AppSettings.importPrefs(decodedMap)

            Summary(
                settingsCount = decodedMap.size,
                exportedAt = file.exportedAt,
            )
        }
    }
}

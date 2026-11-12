package com.music.bitchord.data.settings

/**
 * Stream bitrate ceiling on playback paths.
 * Extracted and adapted from BitChord 1.8.1 (GPLv3).
 */
enum class AudioQuality(
    val maxKbps: Int,
    val label: String,
    val detail: String,
    val hourly: String,
) {
    LOW(64, "Baja", "~64 kbps · Menor consumo de datos", "29 MB/h"),
    MEDIUM(128, "Media", "Estándar · ~128 kbps Opus", "77 MB/h"),
    HIGH(256, "Alta", "Alta fidelidad · hasta 256 kbps", "144 MB/h"),
    LOSSLESS(Int.MAX_VALUE, "Máxima / Sin pérdidas", "Máxima calidad disponible · Bit-exact donde esté disponible", "300+ MB/h");

    companion object {
        fun fromString(value: String?): AudioQuality = when (value?.uppercase()) {
            "LOW" -> LOW
            "MEDIUM" -> MEDIUM
            "HIGH" -> HIGH
            "LOSSLESS" -> LOSSLESS
            else -> LOSSLESS
        }
    }
}

/** The surface that was last open inside the expanded player. */
enum class LastPlayerScreen {
    MAIN,
    LYRICS,
    QUEUE;

    companion object {
        fun fromString(value: String?): LastPlayerScreen = when (value?.uppercase()) {
            "LYRICS" -> LYRICS
            "QUEUE" -> QUEUE
            else -> MAIN
        }
    }
}

/** Interface theme mode. */
enum class ThemeMode(val label: String) {
    SYSTEM("Sistema"),
    LIGHT("Claro"),
    DARK("Oscuro");

    companion object {
        fun fromString(value: String?): ThemeMode = when (value?.uppercase()) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}

/** PCM output mode for AudioTrack sink. */
enum class OutputPcmMode(val label: String) {
    PCM_16("16-bit PCM"),
    FLOAT_32("32-bit Float");

    companion object {
        fun fromString(value: String?): OutputPcmMode = when (value?.uppercase()) {
            "FLOAT_32" -> FLOAT_32
            else -> PCM_16
        }
    }
}

/** Stable sorting for on-device local music library. */
enum class LocalMusicSort(val label: String) {
    TITLE_ASC("Título (A-Z)"),
    TITLE_DESC("Título (Z-A)"),
    DATE_ADDED("Fecha de adición"),
    DATE_MODIFIED("Fecha de modificación");

    companion object {
        fun fromString(value: String?): LocalMusicSort = when (value?.uppercase()) {
            "TITLE_DESC" -> TITLE_DESC
            "DATE_ADDED" -> DATE_ADDED
            "DATE_MODIFIED" -> DATE_MODIFIED
            else -> TITLE_ASC
        }
    }
}

/** Ordering for track lists on playlists and downloads. */
enum class SongSort(val label: String) {
    DEFAULT("Predeterminado"),
    TITLE_ASC("Título (A-Z)"),
    TITLE_DESC("Título (Z-A)"),
    DATE_ADDED_ASC("Más antiguos primero"),
    DATE_ADDED_DESC("Más recientes primero");

    companion object {
        fun fromString(value: String?): SongSort = when (value?.uppercase()) {
            "TITLE_ASC" -> TITLE_ASC
            "TITLE_DESC" -> TITLE_DESC
            "DATE_ADDED_ASC" -> DATE_ADDED_ASC
            "DATE_ADDED_DESC" -> DATE_ADDED_DESC
            else -> DEFAULT
        }
    }
}

/** Supported application language options. */
enum class BitChordLanguage(val code: String, val label: String) {
    AUTO("auto", "Predeterminado del sistema"),
    SPANISH("es", "Español"),
    ENGLISH("en", "English");

    companion object {
        fun fromCode(code: String?): BitChordLanguage = when (code?.lowercase()) {
            "es" -> SPANISH
            "en" -> ENGLISH
            else -> AUTO
        }
    }
}

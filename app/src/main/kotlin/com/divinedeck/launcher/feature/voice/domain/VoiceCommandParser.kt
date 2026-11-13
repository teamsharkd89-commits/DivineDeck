package com.divinedeck.launcher.feature.voice.domain

import java.text.Normalizer

/**
 * Comandos de voz del asistente de DivineDeck (versión botón: se escucha
 * solo mientras Daniel toca el micrófono; sin palabra de activación).
 *
 * Parser puro en Kotlin, sin dependencias de Android: normaliza el texto
 * (minúsculas + sin acentos) y lo clasifica en un comando ejecutable.
 */
sealed interface VoiceCommand {
    /** "llévame a X" — X es el nombre del lugar tal como se escuchó. */
    data class NavigateTo(val placeName: String) : VoiceCommand
    data object Next : VoiceCommand
    data object Previous : VoiceCommand
    data object Pause : VoiceCommand
    data object Play : VoiceCommand
    /** "pon X" / "reproduce X" — X es la búsqueda de la canción. La ejecución
     * busca primero en la biblioteca local (playlist con ese nombre, canción
     * local, artista local) y solo si no hay nada usa la búsqueda online. */
    data class PlaySong(val query: String) : VoiceCommand
    /** "pon la playlist X" / "reproduce la playlist X" — X es el nombre. */
    data class PlayPlaylist(val name: String) : VoiceCommand
    /** "pon música de X" / "pon canciones de X" — X es el artista. */
    data class PlayArtist(val name: String) : VoiceCommand
    /** "pon mis descargas" / "música descargada". */
    data object PlayDownloads : VoiceCommand
    /** "pon mis canciones" / "música local". */
    data object PlayLocalLibrary : VoiceCommand
    data object Unknown : VoiceCommand
}

object VoiceCommandParser {

    /** Minúsculas, sin acentos, espacios colapsados. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase().trim(), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parse(rawText: String): VoiceCommand {
        val text = normalize(rawText)
        if (text.isBlank()) return VoiceCommand.Unknown

        // --- Navegación: prefijos más largos primero ("llevame a la "
        // debe ganar a "llevame a "). ---
        val navPrefixes = listOf(
            "llevame a la ",
            "llevame a los ",
            "llevame a las ",
            "llevame al ",
            "llevame a ",
            "navega a ",
            "ir a ",
            "ve a "
        )
        for (prefix in navPrefixes) {
            if (text.startsWith(prefix)) {
                val place = text.removePrefix(prefix).trim()
                return if (place.isNotBlank()) VoiceCommand.NavigateTo(place)
                else VoiceCommand.Unknown
            }
        }

        // --- Música: siguiente ---
        if (text == "siguiente" ||
            text == "siguiente cancion" ||
            text == "cambia de musica" ||
            text == "cambia la musica"
        ) {
            return VoiceCommand.Next
        }

        // --- Música: anterior ---
        if (text == "anterior" || text == "cancion anterior") {
            return VoiceCommand.Previous
        }

        // --- Música: pausa ---
        if (text == "pausa" || text == "pausar") {
            return VoiceCommand.Pause
        }

        // --- Música: descargas (ANTES del "pon " genérico) ---
        if (text == "pon mis descargas" ||
            text == "reproduce mis descargas" ||
            text == "pon musica descargada" ||
            text == "reproduce musica descargada" ||
            text == "pon mis canciones descargadas" ||
            text == "musica descargada" ||
            text == "mis descargas" ||
            text == "descargas"
        ) {
            return VoiceCommand.PlayDownloads
        }

        // --- Música: biblioteca local (ANTES del "pon " genérico) ---
        if (text == "pon mis canciones" ||
            text == "reproduce mis canciones" ||
            text == "pon musica local" ||
            text == "reproduce musica local" ||
            text == "musica local" ||
            text == "pon mi musica" ||
            text == "reproduce mi musica" ||
            text == "mi musica" ||
            text == "mis canciones"
        ) {
            return VoiceCommand.PlayLocalLibrary
        }

        // --- Música: playlist por nombre (ANTES del "pon " genérico,
        // porque "pon la playlist X" también empieza por "pon "). ---
        val playlistPrefixes = listOf(
            "pon la playlist ",
            "reproduce la playlist ",
            "pon playlist ",
            "reproduce playlist "
        )
        for (prefix in playlistPrefixes) {
            if (text.startsWith(prefix)) {
                val name = text.removePrefix(prefix).trim()
                return if (name.isNotBlank()) VoiceCommand.PlayPlaylist(name)
                else VoiceCommand.Unknown
            }
        }

        // --- Música: artista ("pon musica de X", "pon canciones de X"). ---
        val artistPrefixes = listOf(
            "pon canciones de ",
            "reproduce canciones de ",
            "pon musica de ",
            "reproduce musica de ",
            "pon canciones del ",
            "reproduce canciones del "
        )
        for (prefix in artistPrefixes) {
            if (text.startsWith(prefix)) {
                val name = text.removePrefix(prefix).trim()
                return if (name.isNotBlank()) VoiceCommand.PlayArtist(name)
                else VoiceCommand.Unknown
            }
        }

        // Frases con prefijo de playlist/artista pero sin nombre: no caen
        // en el "pon X" genérico (buscarían "la playlist" como canción).
        if (text == "pon la playlist" ||
            text == "reproduce la playlist" ||
            text == "pon playlist" ||
            text == "reproduce playlist" ||
            text == "pon musica de" ||
            text == "reproduce musica de" ||
            text == "pon canciones de" ||
            text == "reproduce canciones de"
        ) {
            return VoiceCommand.Unknown
        }

        // --- Música: poner una canción (ANTES de la regla "reproduce" solo,
        // porque "reproduce X" también empieza por "reproduce"). ---
        if (text.startsWith("pon la cancion ")) {
            val query = text.removePrefix("pon la cancion ").trim()
            return if (query.isNotBlank()) VoiceCommand.PlaySong(query)
            else VoiceCommand.Unknown
        }
        if (text.startsWith("pon ")) {
            val query = text.removePrefix("pon ").trim()
            return if (query.isNotBlank()) VoiceCommand.PlaySong(query)
            else VoiceCommand.Unknown
        }
        if (text.startsWith("reproduce ")) {
            val query = text.removePrefix("reproduce ").trim()
            return if (query.isNotBlank()) VoiceCommand.PlaySong(query)
            else VoiceCommand.Unknown
        }

        // --- Música: reanudar (solo, sin canción) ---
        if (text == "sigue" ||
            text == "continua" ||
            text == "reanuda" ||
            text == "reproduce"
        ) {
            return VoiceCommand.Play
        }

        return VoiceCommand.Unknown
    }
}

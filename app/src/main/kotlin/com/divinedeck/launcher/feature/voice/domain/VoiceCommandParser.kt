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
    /** "pon X" / "reproduce X" — X es la búsqueda de la canción. */
    data class PlaySong(val query: String) : VoiceCommand
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

package com.music.bitchord.data.lyrics

/**
 * Visual animation styles supported by the BitChord synchronized lyrics rendering engine.
 *
 * Designed for 60fps rendering on mobile/automotive hardware (Samsung Galaxy Tab A11 SM-X133
 * with Mali-G57 MC2 GPU) using draw-time Compose modifiers.
 */
enum class LyricsEffectStyle(
    val label: String,
    val description: String,
) {
    /**
     * Rhythmic breathing/scale oscillation aligned to musical tempo and beat energy.
     */
    PULSE(
        label = "Pulso Rítmico",
        description = "Respiración sutil y oscilación al compás de la música",
    ),

    /**
     * Feathered shimmering gradient sweep flowing across active words.
     */
    FLOW(
        label = "Flujo Continuo",
        description = "Barrido luminoso dinámico que recorre las frases",
    ),

    /**
     * Syllable-by-syllable progressive highlighting with vertical note lift.
     */
    KARAOKE(
        label = "Karaoke Progresivo",
        description = "Resaltado y elevación continua palabra por palabra",
    ),

    /**
     * Ambient luminous glow blooming around active lyrics text.
     */
    GLOW(
        label = "Resplandor Ambiente",
        description = "Aura difuminada alrededor de las frases activas",
    ),

    /**
     * Dynamic beat-transient punch reacting directly to low-frequency audio (<150Hz).
     */
    BEAT_REACTIVE(
        label = "Reactivo al Bajo",
        description = "Impulso reactivo a los graves y energía de audio",
    );

    companion object {
        fun fromString(value: String?): LyricsEffectStyle = when (value?.uppercase()) {
            "PULSE" -> PULSE
            "FLOW" -> FLOW
            "KARAOKE" -> KARAOKE
            "GLOW" -> GLOW
            "BEAT_REACTIVE" -> BEAT_REACTIVE
            else -> PULSE
        }
    }
}

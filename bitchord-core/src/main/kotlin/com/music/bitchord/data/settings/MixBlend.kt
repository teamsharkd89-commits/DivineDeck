package com.music.bitchord.data.settings

/**
 * Automix beat matching representation for the scrubber pulse.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
data class MixBlend(
    val beatMs: Float = 0f,
    val beatAnchorNanos: Long = 0L,
    val playing: Boolean = true,
)

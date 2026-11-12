package com.divinedeck.launcher.feature.music.domain.model

/**
 * Modelo inmutable de metadatos de la pista multimedia reportada por Media3.
 * Utiliza valores nulos cuando no hay metadatos disponibles para evitar canciones ficticias.
 */
data class TrackInfo(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val currentPositionMs: Long = 0L,
    val albumArtUri: String? = null,
    val mediaId: String? = null
) {
    val hasTrack: Boolean
        get() = !title.isNullOrBlank() || !artist.isNullOrBlank() || !mediaId.isNullOrBlank()

    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "Sin reproducción activa"

    val displayArtist: String
        get() = artist?.takeIf { it.isNotBlank() } ?: "BitChord / Media3"

    val displayAlbum: String
        get() = album?.takeIf { it.isNotBlank() } ?: ""
}

package com.divinedeck.launcher.feature.music.ui

import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.music.bitchord.data.model.Song

/**
 * Adaptador explícito entre los metadatos Media3 de DIVINE DECK ([TrackInfo])
 * y el modelo de dominio puro de la biblioteca BitChord ([Song]).
 *
 * Sigue reglas estrictas de fidelidad:
 * - Si no hay canción activa (!hasTrack), devuelve null (activando estado de reposo).
 * - No inventa carátulas ni duraciones estimadas.
 * - Mapea únicamente los metadatos genuinamente provistos por Media3.
 */
object BitChordMediaModelAdapter {

    /**
     * Mapea un [TrackInfo] a un [Song] para ser renderizado por [com.music.bitchord.ui.player.BitChordPlayerScreen].
     */
    fun toSong(trackInfo: TrackInfo): Song? {
        if (!trackInfo.hasTrack) return null

        return Song(
            videoId = trackInfo.mediaId ?: "",
            title = trackInfo.title ?: "",
            artist = trackInfo.artist ?: "",
            thumbnailUrl = trackInfo.albumArtUri?.takeIf { it.isNotBlank() },
            durationSeconds = trackInfo.durationMs?.takeIf { it > 0L }?.let { it / 1000L },
            albumName = trackInfo.album?.takeIf { it.isNotBlank() },
        )
    }
}

package com.divinedeck.launcher.feature.music.domain

import android.net.Uri
import com.divinedeck.launcher.feature.music.domain.model.MediaPlaybackState
import com.divinedeck.launcher.feature.music.domain.model.TrackInfo
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato de integración multimedia para DIVINE DECK.
 * Controla el servicio multimedia interno de BitChord mediante AndroidX Media3.
 */
interface MediaControllerRepository {
    val currentTrack: StateFlow<TrackInfo>
    val playbackState: StateFlow<MediaPlaybackState>
    val currentQueue: StateFlow<List<Song>>

    fun connect()
    fun disconnect()
    fun play()
    fun pause()
    fun togglePlayback()
    fun skipToNext()
    fun skipToPrevious()
    fun seekTo(positionMs: Long)
    fun setVolume(percent: Int)

    /**
     * Carga y reproduce una lista completa de canciones en la cola autoritativa de Media3,
     * posicionándose en [startIndex].
     */
    fun playQueue(songs: List<Song>, startIndex: Int = 0)

    /**
     * Salta directamente a la canción ubicada en [index] dentro de la cola autoritativa.
     */
    fun skipToQueueItem(index: Int)

    /**
     * Encola una canción inmediatamente después de la canción en reproducción (siguiente en reproducirse).
     */
    fun enqueueNext(song: Song)

    /**
     * Encola una canción al final de las canciones solicitadas por el usuario.
     */
    fun enqueueLast(song: Song)

    /**
     * Reordena dos elementos de la cola autoritativa.
     */
    fun reorderQueue(fromIndex: Int, toIndex: Int)

    /**
     * Elimina un elemento de la cola ubicado en [index].
     */
    fun removeFromQueue(index: Int)

    /**
     * Vacía todas las canciones añadidas por el usuario en la cola activa, preservando el contexto.
     */
    fun clearUserQueue()

    /**
     * Reproduce un archivo de audio local mediante su URI en formato String.
     */
    fun playLocalUri(uriString: String, title: String? = null, artist: String? = null, artworkUri: String? = null)

    /**
     * Sobrecarga para reproducir un archivo de audio local mediante Android Uri.
     */
    fun playLocalUri(uri: Uri, title: String? = null, artist: String? = null, artworkUri: String? = null)

    /**
     * Reproduce una pista multimedia online o de BitChord a partir de su objeto [Song].
     */
    fun playSong(song: Song)

    /**
     * Reproduce una pista online a partir de su videoId y metadatos explícitos.
     */
    fun playOnlineTrack(videoId: String, title: String, artist: String, thumbnailUrl: String? = null, durationMs: Long? = null)

    /**
     * Reproduce la pista de prueba interna (chime de audio local en assets).
     */
    fun playSampleAudio()
}

package com.divinedeck.launcher.feature.music.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel encargado de orquestar la obtención, resolución y ciclo de vida
 * de las letras originales de BitChord dentro de DIVINE DECK (Fase 06.5D).
 *
 * Sincronizado estrictamente con la canción en reproducción emitida por Media3.
 * Cancela inmediatamente cualquier petición previa en vuelo cuando la canción cambia,
 * para evitar estados mezclados o letras obsoletas en pantalla.
 */
class MusicLyricsViewModel(
    private val repository: LyricsRepository = LyricsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private var activeJob: Job? = null
    private var currentVideoId: String? = null

    /**
     * Notifica el cambio de pista en reproducción.
     * Si la pista es nula, limpia el estado a [LyricsUiState.Idle].
     * Si es una nueva pista, cancela de inmediato la búsqueda anterior
     * y transiciona a [LyricsUiState.Loading].
     */
    fun updateSong(song: Song?, context: Context?) {
        if (song == null || song.videoId.isBlank()) {
            cancelActiveJob()
            currentVideoId = null
            _lyricsState.value = LyricsUiState.Idle
            return
        }

        // Si ya estamos procesando o completamos esta misma canción, evitar reiniciar
        if (song.videoId == currentVideoId &&
            _lyricsState.value !is LyricsUiState.Idle &&
            _lyricsState.value !is LyricsUiState.Error
        ) {
            return
        }

        cancelActiveJob()
        currentVideoId = song.videoId
        loadLyricsForSong(song, context, forceRefresh = false)
    }

    /**
     * Reintenta la búsqueda de letras para la pista actual, forzando invalidación de caché.
     */
    fun retry(context: Context?, song: Song?) {
        val targetSong = song ?: return
        cancelActiveJob()
        currentVideoId = targetSong.videoId
        loadLyricsForSong(targetSong, context, forceRefresh = true)
    }

    private fun loadLyricsForSong(
        song: Song,
        context: Context?,
        forceRefresh: Boolean
    ) {
        val videoId = song.videoId
        val appContext = context?.applicationContext

        _lyricsState.value = LyricsUiState.Loading(
            videoId = videoId,
            title = song.title,
            activeProviderLabel = null
        )

        activeJob = viewModelScope.launch(ioDispatcher) {
            try {
                val resultState = repository.resolveForSong(
                    song = song,
                    context = appContext,
                    durationMs = song.durationMillis,
                    forceRefresh = forceRefresh,
                    onSourceStarted = { source ->
                        _lyricsState.value = LyricsUiState.Loading(
                            videoId = videoId,
                            title = song.title,
                            activeProviderLabel = source.label
                        )
                    }
                )
                _lyricsState.value = resultState
            } catch (c: CancellationException) {
                // Cancelación intencional por cambio de canción o nuevo ciclo
                throw c
            } catch (t: Throwable) {
                _lyricsState.value = LyricsUiState.Error(
                    videoId = videoId,
                    message = t.message ?: "Error al obtener letras",
                    canRetry = true
                )
            }
        }
    }

    fun cancelActiveJob() {
        activeJob?.cancel()
        activeJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveJob()
    }
}

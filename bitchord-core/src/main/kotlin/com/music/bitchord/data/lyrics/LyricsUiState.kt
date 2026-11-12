package com.music.bitchord.data.lyrics

/**
 * Authoritative UI states for the BitChord synchronized lyrics panel inside DIVINE DECK.
 *
 * Explicitly differentiates all 7 states required by Phase 06.5D:
 * 1. [Loading] — Cargando letras para la pista activa.
 * 2. [Synced] — Letras sincronizadas disponibles (por línea o por palabra/sílaba).
 * 3. [Plain] — Letras disponibles sin sincronización (texto plano desplazable, sin fingir sincronización).
 * 4. [NotFound] — Letras no encontradas en ningún proveedor.
 * 5. [Error] — Error temporal del proveedor o de red (con opción de reintentar).
 * 6. [EmbeddedLocal] — Canción local o descargada con letras incrustadas/sidecar (.lrc / átomos MP4/WebM/FLAC/ID3).
 * 7. [OfflineUnavailable] — Canción reproducida sin conexión a Internet y sin letras locales guardadas.
 */
sealed interface LyricsUiState {

    /** No song is currently loaded in the player. */
    data object Idle : LyricsUiState

    /** State 1: Cargando letras. */
    data class Loading(
        val videoId: String,
        val title: String,
        val activeProviderLabel: String? = null,
    ) : LyricsUiState

    /** State 2: Letras sincronizadas disponibles (de proveedor online o caché). */
    data class Synced(
        val videoId: String,
        val lines: List<LyricLine>,
        val source: LyricsSource,
        val isWordSynced: Boolean = lines.any { it.isWordSynced },
    ) : LyricsUiState

    /** State 3: Letras disponibles sin sincronización (texto plano desplazable). */
    data class Plain(
        val videoId: String,
        val lines: List<LyricLine>,
        val source: LyricsSource,
    ) : LyricsUiState

    /** State 4: Letras no encontradas. */
    data class NotFound(
        val videoId: String,
        val title: String,
    ) : LyricsUiState

    /** State 5: Error temporal del proveedor. */
    data class Error(
        val videoId: String,
        val message: String,
        val canRetry: Boolean = true,
    ) : LyricsUiState

    /** State 6: Canción local o descargada con letras incrustadas o sidecar local. */
    data class EmbeddedLocal(
        val videoId: String,
        val lines: List<LyricLine>,
        val isSynced: Boolean = lines.any { it.timeMs > 0L },
        val isWordSynced: Boolean = lines.any { it.isWordSynced },
        val containerOrFormat: String = "Local LRC",
    ) : LyricsUiState

    /** State 7: Canción offline sin letras disponibles localmente (sin llamadas de red). */
    data class OfflineUnavailable(
        val videoId: String,
        val title: String,
    ) : LyricsUiState
}

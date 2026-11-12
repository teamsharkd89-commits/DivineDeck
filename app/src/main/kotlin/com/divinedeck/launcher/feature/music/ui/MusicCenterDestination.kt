package com.divinedeck.launcher.feature.music.ui

/**
 * Destinos de navegación interna dentro del Centro Multimedia de DIVINE DECK (Fase 06.5B).
 *
 * Incluye las 5 secciones principales en orden exacto:
 * - [HOME]: Pantalla original de Inicio de BitChord (`HomeScreen`).
 * - [EXPLORE]: Pantalla original de Explorar de BitChord (`ExploreScreen` + `MoodGenrePlaylistsScreen`).
 * - [SEARCH]: Buscador en tiempo real de YouTube Music de BitChord.
 * - [LIBRARY]: Biblioteca personal y gestión de playlists/favoritos de BitChord (`LibraryScreen`).
 * - [PLAYER]: Reproductor completo de BitChord sincronizado con Media3.
 */
enum class MusicCenterDestination(val title: String) {
    HOME("INICIO"),
    EXPLORE("EXPLORAR"),
    SEARCH("BUSCAR"),
    LIBRARY("BIBLIOTECA"),
    PLAYER("REPRODUCTOR");

    companion object {
        val DEFAULT = HOME
    }
}

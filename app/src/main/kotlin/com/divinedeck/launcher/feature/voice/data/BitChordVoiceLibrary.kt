package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import com.divinedeck.launcher.feature.voice.domain.VoiceCommandParser
import com.music.bitchord.data.LocalMediaRepository
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Consultas reales de BitChord para los comandos de voz (sin inventar datos).
 *
 * Fuentes reales que usa:
 * - Biblioteca local del dispositivo: [LocalMediaRepository.getLocalSongs]
 *   (MediaStore; vacía si falta el permiso READ_MEDIA_AUDIO). Cada canción
 *   trae `videoId = "local:<id>"` y `localUri` (content://).
 * - Descargas offline: [Downloads.getDownloadedSongs] (solo archivos
 *   verificados en disco, con su `localUri` file://). [Downloads.init] es
 *   idempotente; el servicio de reproducción ya lo inicializa.
 * - Playlists del usuario creadas en la app: [LocalPlaylistStore.playlists]
 *   (persistidas en SharedPreferences "bitchord_local_playlists"). Cada
 *   [LocalPlaylist] trae su lista completa de [Song].
 * - Playlists remotas de YouTube Music: [YtMusicRepository.userPlaylists]
 *   + [YtMusicRepository.browseSongs] (necesita internet y sesión).
 *
 * La reproducción en sí la hace `MediaControllerRepository` (`playSong` /
 * `playQueue`), que ya enriquece cada canción con su descarga local
 * (`Downloads.enrichWithLocalDownload`) antes de ponerla en cola.
 */
class BitChordVoiceLibrary(private val context: Context) {

    /** Una playlist lista para poner en cola, con su título original. */
    data class PlaylistPlayback(val title: String, val songs: List<Song>)

    /** Canciones descargadas (offline, verificadas en disco), más recientes primero. */
    suspend fun downloadedSongs(): List<Song> = withContext(Dispatchers.IO) {
        Downloads.init(context)
        Downloads.getDownloadedSongs()
    }

    /** Archivos de audio del dispositivo (MediaStore). Vacía sin permiso de audio. */
    suspend fun deviceSongs(): List<Song> = LocalMediaRepository.getLocalSongs(context)

    /**
     * Biblioteca local completa: descargas + archivos del dispositivo,
     * sin duplicados por `videoId` (las descargas van primero).
     */
    suspend fun allLocalSongs(): List<Song> {
        val downloaded = downloadedSongs()
        val device = deviceSongs()
        val seen = HashSet<String>(downloaded.size + device.size)
        val merged = ArrayList<Song>(downloaded.size + device.size)
        for (song in downloaded + device) {
            if (seen.add(song.videoId)) merged.add(song)
        }
        return merged
    }

    /**
     * Mejor coincidencia en la biblioteca local para [query] (por título),
     * o null si nada coincide. Puntuación: título exacto > empieza por la
     * búsqueda > todas las palabras en el título > título contiene la búsqueda.
     * El artista NO puntúa aquí: se resuelve aparte en [songsByArtist].
     */
    suspend fun searchLocalSong(query: String): Song? {
        val needle = VoiceCommandParser.normalize(query)
        if (needle.isBlank()) return null
        var best: Song? = null
        var bestScore = 0
        for (song in allLocalSongs()) {
            val score = scoreSongTitle(song, needle)
            if (score > bestScore) {
                bestScore = score
                best = song
            }
        }
        return best
    }

    private fun scoreSongTitle(song: Song, needle: String): Int {
        val title = VoiceCommandParser.normalize(song.title)
        if (title.isBlank()) return 0
        return when {
            title == needle -> 100
            title.startsWith(needle) -> 80
            needle.split(" ").filter { it.length > 1 }.all { it in title } -> 70
            title.contains(needle) -> 60
            else -> 0
        }
    }

    /**
     * Todas las canciones locales de un artista ([name] parcial, sin acentos
     * ni mayúsculas): compara el campo `artist` y cada crédito en `artists`.
     * Ignora el artista genérico "Unknown Artist" de MediaStore.
     */
    suspend fun songsByArtist(name: String): List<Song> {
        val needle = VoiceCommandParser.normalize(name)
        if (needle.isBlank()) return emptyList()
        return allLocalSongs().filter { song ->
            val artist = VoiceCommandParser.normalize(song.artist)
            val knownArtist = artist.isNotBlank() && artist != "unknown artist"
            (knownArtist && (artist.contains(needle) || needle.contains(artist))) ||
                song.artists.any { ref ->
                    val refName = VoiceCommandParser.normalize(ref.name)
                    refName.isNotBlank() && refName.contains(needle)
                }
        }
    }

    /**
     * Playlist local del usuario por nombre (sin acentos ni mayúsculas):
     * primero coincidencia exacta, luego que el título contenga el nombre
     * o viceversa. Null si no existe ninguna playlist con ese nombre.
     */
    suspend fun findLocalPlaylist(name: String): LocalPlaylist? = withContext(Dispatchers.IO) {
        LocalPlaylistStore.init(context)
        val needle = VoiceCommandParser.normalize(name)
        if (needle.isBlank()) return@withContext null
        val playlists = LocalPlaylistStore.playlists.value
        playlists.firstOrNull { VoiceCommandParser.normalize(it.title) == needle }
            ?: playlists.firstOrNull { VoiceCommandParser.normalize(it.title).contains(needle) }
            ?: playlists.firstOrNull { needle.contains(VoiceCommandParser.normalize(it.title)) }
    }

    /**
     * Playlist remota de YouTube Music por nombre (necesita internet y
     * sesión): busca en [YtMusicRepository.userPlaylists] y carga sus
     * canciones con [YtMusicRepository.browseSongs]. Null si no hay sesión,
     * no hay red, no existe la playlist o no tiene canciones.
     */
    suspend fun findRemotePlaylist(name: String): PlaylistPlayback? {
        val needle = VoiceCommandParser.normalize(name)
        if (needle.isBlank()) return null
        val remote = YtMusicRepository.userPlaylists().getOrNull() ?: return null
        val match = remote.firstOrNull { VoiceCommandParser.normalize(it.title) == needle }
            ?: remote.firstOrNull { VoiceCommandParser.normalize(it.title).contains(needle) }
            ?: return null
        val page = YtMusicRepository.browseSongs(match.browseId).getOrNull() ?: return null
        if (page.songs.isEmpty()) return null
        return PlaylistPlayback(title = match.title, songs = page.songs)
    }
}

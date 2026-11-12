package com.music.bitchord.data

import com.music.bitchord.data.model.Song
import java.util.Locale

private val ARTIST_SPLIT_REGEX = Regex("""[,&;/]|\b(?:feat|ft)\.?\b""", RegexOption.IGNORE_CASE)

private fun splitArtistCredits(artist: String): List<String> =
    artist.split(ARTIST_SPLIT_REGEX).map { it.trim() }.filter { it.isNotEmpty() }

data class LocalGroup(
    val name: String,
    val subtitle: String?,
    val thumbnailUrl: String?,
    val songs: List<Song>,
)

fun filterLocalSongs(songs: List<Song>, query: String): List<Song> {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return songs
    return songs.filter { song ->
        song.title.lowercase(Locale.ROOT).contains(q) ||
            song.artist.lowercase(Locale.ROOT).contains(q) ||
            song.albumName?.lowercase(Locale.ROOT)?.contains(q) == true
    }
}

fun groupLocalAlbums(songs: List<Song>, unknownAlbum: String = "Unknown Album"): List<LocalGroup> =
    songs
        .groupBy { song -> song.albumName?.trim()?.takeIf { it.isNotEmpty() } ?: unknownAlbum }
        .map { (album, albumSongs) ->
            val orderedSongs = albumSongs.sortedWith(
                compareBy<Song>(
                    { it.trackNumber == null },
                    { it.trackNumber ?: Int.MAX_VALUE },
                ),
            )
            val artists = orderedSongs.mapNotNull { it.artist.trim().takeIf(String::isNotEmpty) }.distinct()
            LocalGroup(
                name = album,
                subtitle = when {
                    artists.isEmpty() -> null
                    artists.size == 1 -> artists.single()
                    else -> artists.take(2).joinToString(", ")
                },
                thumbnailUrl = orderedSongs.firstNotNullOfOrNull { it.thumbnailUrl },
                songs = orderedSongs,
            )
        }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

fun groupLocalArtists(songs: List<Song>, unknownArtist: String = "Unknown Artist"): List<LocalGroup> {
    val byKey = LinkedHashMap<String, ArtistBucket>()
    for (song in songs) {
        val names = splitArtistCredits(song.artist)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .ifEmpty { listOf(unknownArtist) }
        for (name in names) {
            val key = name.lowercase(Locale.ROOT)
            val bucket = byKey.getOrPut(key) { ArtistBucket(name) }
            bucket.songs.add(song)
        }
    }
    return byKey.values
        .map { bucket ->
            LocalGroup(
                name = bucket.displayName,
                subtitle = null,
                thumbnailUrl = bucket.songs.firstNotNullOfOrNull { it.thumbnailUrl },
                songs = bucket.songs,
            )
        }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
}

private class ArtistBucket(val displayName: String) {
    val songs = ArrayList<Song>()
}

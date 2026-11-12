package com.music.bitchord.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Repositorio de música local almacenada en el dispositivo (`MediaStore.Audio.Media`).
 * Extraído y adaptado de BitChord 1.8.1 (GPLv3).
 *
 * Nota: Las descargas offline (`local:downloads` / `Downloader`) están fuera del alcance
 * de la Fase 06.5B (reservadas para la Fase 06.5C). Este repositorio consulta exclusivamente
 * archivos de audio reales del dispositivo cuando el permiso de almacenamiento de audio
 * está concedido.
 */
object LocalMediaRepository {

    fun hasAudioPermission(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getLocalSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        if (!hasAudioPermission(context)) return@withContext emptyList()

        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.TRACK,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Title"
                    val rawArtist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val artist = if (rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum == "<unknown>") null else rawAlbum
                    val durationMs = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)
                    val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val dateAdded = if (dateAddedCol >= 0 && !cursor.isNull(dateAddedCol)) {
                        cursor.getLong(dateAddedCol).takeIf { it > 0L }
                    } else null
                    val dateModified = if (dateModifiedCol >= 0 && !cursor.isNull(dateModifiedCol)) {
                        cursor.getLong(dateModifiedCol).takeIf { it > 0L }
                    } else null
                    val trackNumber = if (trackCol >= 0 && !cursor.isNull(trackCol)) {
                        (cursor.getInt(trackCol) % 1000).takeIf { it > 0 }
                    } else null

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    val artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId,
                    )
                    val seconds = durationMs / 1000
                    val durationText = String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

                    songs.add(
                        Song(
                            videoId = "local:$id",
                            title = title,
                            artist = artist,
                            thumbnailUrl = artUri.toString(),
                            durationText = durationText,
                            albumName = album,
                            localUri = contentUri.toString(),
                            localPath = path,
                            localDateAddedSeconds = dateAdded,
                            localDateModifiedSeconds = dateModified,
                            trackNumber = trackNumber,
                        ),
                    )
                }
            }
        }
        songs
    }
}

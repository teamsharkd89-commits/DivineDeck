package com.music.bitchord.download

import androidx.compose.runtime.Immutable
import com.music.bitchord.data.model.Song
import kotlinx.serialization.Serializable

/**
 * Original BitChord 1.8.1 active per-song download state.
 */
@Immutable
sealed interface DownloadState {
    data object Queued : DownloadState
    data class Running(
        val fraction: Float,
        val bytesWritten: Long = 0L,
        val totalBytes: Long = 0L
    ) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Original BitChord 1.8.1 session progress state per batch item.
 */
@Immutable
sealed interface DownloadProgress {
    val settled: Boolean

    data object Queued : DownloadProgress {
        override val settled: Boolean = false
    }

    data class Running(
        val fraction: Float,
        val bytesWritten: Long = 0L,
        val totalBytes: Long = 0L
    ) : DownloadProgress {
        override val settled: Boolean = false
    }

    data object Done : DownloadProgress {
        override val settled: Boolean = true
    }

    data class Failed(val reason: String) : DownloadProgress {
        override val settled: Boolean = true
    }
}

/**
 * Original BitChord 1.8.1 DownloadSession batch item.
 */
@Immutable
data class DownloadBatchItem(
    val key: String,
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val progress: DownloadProgress
)

/**
 * Original BitChord 1.8.1 DownloadSession aggregated state.
 */
@Immutable
data class DownloadBatchState(
    val items: List<DownloadBatchItem> = emptyList(),
    val seenFailureKeys: Set<String> = emptySet()
) {
    val busy: Boolean = items.any { !it.progress.settled }
    val waiting: Int = items.count { !it.progress.settled }
    val finished: Int = items.count { it.progress is DownloadProgress.Done }
    val failed: Int = items.count { it.progress is DownloadProgress.Failed }
    val visible: Boolean = busy || items.any {
        it.progress is DownloadProgress.Failed && it.key !in seenFailureKeys
    }

    val fraction: Float = if (items.isEmpty()) {
        0f
    } else {
        items.sumOf { item ->
            when (val p = item.progress) {
                DownloadProgress.Queued -> 0.0
                is DownloadProgress.Running -> p.fraction.coerceIn(0f, 1f).toDouble()
                DownloadProgress.Done -> 1.0
                is DownloadProgress.Failed -> 1.0
            }
        }.toFloat() / items.size
    }
}

/**
 * Original BitChord 1.8.1 persisted metadata for downloaded songs (`downloaded_tracks_metadata`).
 */
@Immutable
@Serializable
data class SavedSongMetadata(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String? = null,
    val localArtworkUri: String? = null,
    val durationText: String? = null,
    val albumName: String? = null,
    val uri: String,
    val downloadFormat: String? = null,
    val fileSizeBytes: Long? = null,
    val dateAddedSeconds: Long? = null
) {
    fun toSong(verifiedLocalUri: String = uri): Song = Song(
        videoId = videoId,
        title = title,
        artist = artist,
        thumbnailUrl = localArtworkUri?.takeIf { it.isNotBlank() } ?: thumbnailUrl,
        durationText = durationText,
        albumName = albumName,
        localUri = verifiedLocalUri,
        downloadFormat = downloadFormat,
        localDateAddedSeconds = dateAddedSeconds
    )
}

@Immutable
data class SavedCollection(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val songs: List<Song> = emptyList()
)

@Immutable
data class DownloadedCollection(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val songs: List<Song> = emptyList(),
    val savedCount: Int = 0
) {
    val totalCount: Int get() = songs.size
    val fullyDownloaded: Boolean get() = songs.isNotEmpty() && savedCount >= songs.size
}

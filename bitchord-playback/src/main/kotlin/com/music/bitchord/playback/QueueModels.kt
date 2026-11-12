package com.music.bitchord.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.music.bitchord.data.model.PlaybackSourceType
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.download.Downloads
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

const val EXTRA_QUEUE_TIER = "bitchord.queueTier"
const val EXTRA_QUEUE_ENTRY_ID = "bitchord.queueEntryId"
const val EXTRA_CANONICAL_INDEX = "bitchord.canonicalIndex"
const val EXTRA_LOCAL_URI = "bitchord.localUri"

internal val mediaItemTiers = ConcurrentHashMap<MediaItem, QueueTier>()
internal val mediaItemEntryIds = ConcurrentHashMap<MediaItem, String>()
internal val mediaItemCanonicalIndices = ConcurrentHashMap<MediaItem, Int>()
internal val mediaItemLocalUris = ConcurrentHashMap<MediaItem, String>()
internal val mediaItemResolvedUris = ConcurrentHashMap<MediaItem, String>()

val MediaItem.resolvedUriString: String?
    get() = localConfiguration?.uri?.toString()
        ?: requestMetadata.mediaUri?.toString()
        ?: mediaItemResolvedUris[this]

val MediaItem.queueTier: QueueTier
    get() {
        val tierStr = mediaMetadata.extras?.getString(EXTRA_QUEUE_TIER)
        return when (tierStr) {
            "USER_QUEUE" -> QueueTier.USER_QUEUE
            "CONTEXT" -> QueueTier.CONTEXT
            "AUTOPLAY" -> QueueTier.AUTOPLAY
            else -> mediaItemTiers[this] ?: QueueTier.CONTEXT
        }
    }

val MediaItem.queueEntryId: String?
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_ENTRY_ID) ?: mediaItemEntryIds[this]

val MediaItem.canonicalIndex: Int?
    get() {
        val extras = mediaMetadata.extras
        if (extras != null && extras.containsKey(EXTRA_CANONICAL_INDEX)) {
            val idx = extras.getInt(EXTRA_CANONICAL_INDEX, -1)
            if (idx >= 0) return idx
        }
        return mediaItemCanonicalIndices[this]
    }

/**
 * Resolves the authoritative playback URI for [Song], prioritizing a verified local download
 * (`file://`, `content://`, `asset://`) over the online stream scheme (`bitchord://watch?v=`).
 *
 * If a song's local file was deleted or corrupted on disk, automatically falls back to
 * `bitchord://watch?v=<videoId>`.
 */
fun Song.resolvePlaybackUriString(): String {
    val enriched = Downloads.enrichWithLocalDownload(this)
    return enriched.localUri ?: when {
        videoId.startsWith("content://") || videoId.startsWith("file://") || videoId.startsWith("asset://") -> videoId
        else -> "bitchord://watch?v=$videoId"
    }
}

fun Song.toMediaItem(): MediaItem {
    val enriched = Downloads.enrichWithLocalDownload(this)
    val uriString = enriched.resolvePlaybackUriString()

    val metadata = MediaMetadata.Builder()
        .setTitle(enriched.title)
        .setArtist(enriched.artist)
        .setAlbumTitle(enriched.albumName)
        .setArtworkUri(enriched.thumbnailUrl?.let { Uri.parse(it) })
        .build()

    val item = MediaItem.Builder()
        .setMediaId(enriched.videoId)
        .setUri(Uri.parse(uriString))
        .setMediaMetadata(metadata)
        .build()

    val configuredItem = QueueShuffle.withQueueMetadata(
        item = item,
        newEntryId = enriched.queueEntryId ?: UUID.randomUUID().toString(),
        newTier = enriched.queueTier
    )

    mediaItemResolvedUris[configuredItem] = uriString
    val loc = enriched.localUri
    if (loc != null) {
        mediaItemLocalUris[configuredItem] = loc
    }

    return configuredItem
}

fun MediaItem.toSong(): Song {
    val uri = resolvedUriString ?: mediaId
    val candidateLocalUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_URI)
        ?: mediaItemLocalUris[this]
        ?: if (uri.startsWith("content://") || uri.startsWith("file://") || uri.startsWith("asset://")) uri else null
    val resolvedLocalUri = candidateLocalUri?.takeIf { Downloads.isValidLocalUri(it) }
        ?: Downloads.verifiedSavedUriString(mediaId)

    return Song(
        videoId = mediaId,
        title = mediaMetadata.title?.toString() ?: "Unknown Title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown Artist",
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        albumName = mediaMetadata.albumTitle?.toString(),
        queueTier = queueTier,
        queueEntryId = queueEntryId,
        localUri = resolvedLocalUri
    )
}

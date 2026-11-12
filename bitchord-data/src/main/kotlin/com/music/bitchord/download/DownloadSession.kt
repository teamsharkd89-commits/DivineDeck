package com.music.bitchord.download

import com.music.bitchord.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * Original BitChord 1.8.1 DownloadSession adapted for internal module integration.
 *
 * Tracks the active download batch (`DownloadBatchState`) for the top-bar progress ring
 * and the Download Center / DownloadManagerSheet.
 */
object DownloadSession {

    private val nextKey = AtomicLong(1L)
    private val _state = MutableStateFlow(DownloadBatchState())
    val state: StateFlow<DownloadBatchState> = _state.asStateFlow()

    fun enqueue(song: Song): Unit = enqueue(listOf(song))

    fun enqueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        _state.update { current ->
            val base = if (!current.busy) emptyList() else current.items
            val baseSeen = if (!current.busy) emptySet() else current.seenFailureKeys

            val existingActiveIds = base.asSequence()
                .filter { !it.progress.settled }
                .map { it.videoId }
                .toHashSet()

            val additions = ArrayList<DownloadBatchItem>(songs.size)
            for (song in songs) {
                if (song.videoId.isBlank() || !existingActiveIds.add(song.videoId)) continue
                additions += DownloadBatchItem(
                    key = "${song.videoId}#${nextKey.getAndIncrement()}",
                    videoId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    thumbnailUrl = song.thumbnailUrl,
                    progress = DownloadProgress.Queued
                )
            }
            if (additions.isEmpty()) current
            else DownloadBatchState(items = base + additions, seenFailureKeys = baseSeen)
        }
    }

    internal fun running(
        videoId: String,
        fraction: Float,
        bytesWritten: Long = 0L,
        totalBytes: Long = 0L
    ) {
        updateActive(
            videoId,
            DownloadProgress.Running(
                fraction = fraction.coerceIn(0f, 1f),
                bytesWritten = bytesWritten,
                totalBytes = totalBytes
            )
        )
    }

    internal fun done(videoId: String) {
        updateActive(videoId, DownloadProgress.Done)
    }

    internal fun failed(videoId: String, reason: String) {
        updateActive(
            videoId,
            DownloadProgress.Failed(reason.ifBlank { "Error de descarga" })
        )
    }

    internal fun cancel(videoId: String) {
        _state.update { current ->
            val targetIdx = current.items.indexOfLast { it.videoId == videoId && !it.progress.settled }
            if (targetIdx < 0) return@update current
            val targetKey = current.items[targetIdx].key
            val nextItems = current.items.filterIndexed { index, _ -> index != targetIdx }
            current.copy(
                items = nextItems,
                seenFailureKeys = current.seenFailureKeys - targetKey
            )
        }
    }

    internal fun clear() {
        _state.value = DownloadBatchState()
    }

    fun dismissFailure(key: String) {
        _state.update { current ->
            val nextItems = current.items.filterNot { it.key == key }
            current.copy(
                items = nextItems,
                seenFailureKeys = current.seenFailureKeys - key
            )
        }
    }

    fun markSeen() {
        _state.update { current ->
            val failureKeys = current.items
                .asSequence()
                .filter { it.progress is DownloadProgress.Failed }
                .map { it.key }
                .toSet()
            if (failureKeys.isEmpty()) current
            else current.copy(seenFailureKeys = current.seenFailureKeys + failureKeys)
        }
    }

    private fun updateActive(videoId: String, next: DownloadProgress) {
        _state.update { current ->
            val idx = current.items.indexOfLast { it.videoId == videoId && !it.progress.settled }
            if (idx < 0) return@update current
            val updated = current.items.toMutableList()
            updated[idx] = updated[idx].copy(progress = next)
            current.copy(items = updated)
        }
    }
}

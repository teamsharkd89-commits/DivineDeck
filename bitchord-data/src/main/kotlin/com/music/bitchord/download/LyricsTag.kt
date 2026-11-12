package com.music.bitchord.download

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsSource
import com.music.bitchord.data.lyrics.toEnhancedLrc
import com.music.bitchord.data.lyrics.toLrc
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The lyrics to write into a track [Downloads] is about to save, as LRC text.
 *
 * Adapted from BitChord 1.8.1 (GPLv3).
 */
object LyricsTag {

    private const val TAG = "BitChordLyricsTag"
    private const val MAX_LRC_CHARS = 64_000
    private const val LOOKUP_MS = 15_000L

    data class Embeddable(
        val plain: String,
        val enhanced: String?,
        val lines: List<LyricLine> = emptyList(),
        val source: LyricsSource? = null,
    )

    suspend fun forTrack(
        track: Song,
        sources: Set<LyricsSource> = LyricsSource.offered.toSet(),
        order: List<LyricsSource> = LyricsSource.offered,
        prioritizeSyllableSync: Boolean = true,
    ): Embeddable? {
        if (sources.isEmpty()) return null

        val durationMs = track.durationMillis
        val found = try {
            withTimeoutOrNull(LOOKUP_MS) {
                LyricsRepository.lyrics(
                    videoId = track.videoId,
                    title = track.title,
                    artist = track.artist,
                    durationMs = durationMs,
                    album = track.albumName,
                    sources = sources,
                    order = order,
                    prioritizeSyllableSync = prioritizeSyllableSync,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TrackLog.d(TAG, "no lyrics for ${track.videoId}: ${e.message}")
            return null
        } ?: return null

        if (found.lines.none { it.text.isNotBlank() }) return null

        val lrc = found.lines.toLrc()
        if (lrc.length > MAX_LRC_CHARS || lrc.isBlank()) return null

        val enhanced = found.lines.toEnhancedLrc().takeIf {
            it.isNotBlank() && it.length <= MAX_LRC_CHARS * 2
        }
        return Embeddable(plain = lrc, enhanced = enhanced, lines = found.lines, source = found.source)
    }
}

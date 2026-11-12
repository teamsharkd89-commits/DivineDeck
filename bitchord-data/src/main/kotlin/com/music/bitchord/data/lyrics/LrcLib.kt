package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.abs

/**
 * Lyrics from LRCLIB — a free, key-less, community lyrics database.
 *
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"

    private val json = Json { ignoreUnknownKeys = true }

    /** Synced lyrics for a track, or null when nothing usable is published. */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleanTitle = title.clean()
            val cleanArtist = artist.clean()
            val seconds = (durationMs / 1000).toInt()

            val exact = runCatching { exactMatch(cleanTitle, cleanArtist, seconds) }.getOrNull()
            val synced = exact ?: runCatching { bestSearchHit(cleanTitle, cleanArtist, seconds) }
                .getOrNull()
            synced?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        }

    private fun exactMatch(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .addQueryParameter("duration", seconds.toString())
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        return (json.parseToJsonElement(body) as? JsonObject)
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    private fun bestSearchHit(title: String, artist: String, seconds: Int): String? {
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        val hits = json.parseToJsonElement(body) as? JsonArray ?: return null
        return hits.mapNotNull { it as? JsonObject }
            .filter { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }
            .minByOrNull {
                val d = it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                abs(d - seconds)
            }
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    /**
     * `[mm:ss.xx] words` or `[mm:ss.xxx] words`.
     * Metadata tags carry no timestamp and fall out on their own.
     */
    fun parseLrc(lrc: String): List<LyricLine> {
        val all = lrc.lineSequence().mapNotNull { line ->
            val match = STAMP.find(line) ?: return@mapNotNull null
            val (minutes, seconds, fraction) = match.destructured
            // Two digits mean centiseconds, three mean milliseconds.
            val fractionMs = when (fraction.length) {
                2 -> fraction.toLong() * 10
                3 -> fraction.toLong()
                else -> 0L
            }
            val body = line.substring(match.range.last + 1)
            val (text, alignment) = stripAlignmentMarker(body)
            LyricLine(
                timeMs = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs,
                text = text.replace(WORD_STAMP, "").trim(),
                words = parseWordRuns(text),
                alignment = alignment,
            )
        }.sortedBy { it.timeMs }.toList()

        val kept = all.filterIndexed { index, line ->
            if (!line.isGap) return@filterIndexed true
            val next = all.getOrNull(index + 1) ?: return@filterIndexed true
            next.timeMs - line.timeMs >= MIN_GAP_MS
        }

        val first = kept.firstOrNull() ?: return kept
        return if (!first.isGap && first.timeMs >= MIN_GAP_MS) {
            listOf(LyricLine(0L, "")) + kept
        } else {
            kept
        }
    }

    private fun stripAlignmentMarker(body: String): Pair<String, LyricAlignment> {
        if (body.startsWith(ALIGNMENT_MARKER)) {
            return body.substringAfter(ALIGNMENT_MARKER) to LyricAlignment.End
        }
        return body to LyricAlignment.Start
    }

    private const val ALIGNMENT_MARKER = "<R>"

    private fun String.clean(): String = this
        .replace(NOISE, " ")
        .substringBefore(" | ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { this }

    private fun parseWordRuns(body: String): List<LyricWord> {
        val marks = WORD_STAMP.findAll(body).toList()
        if (marks.isEmpty()) return emptyList()
        val runs = marks.mapIndexed { index, mark ->
            val until = marks.getOrNull(index + 1)?.range?.first ?: body.length
            msOf(mark) to body.substring(mark.range.last + 1, until)
        }
        return wordsFromRuns(
            runs.mapIndexed { index, (startMs, text) ->
                TimedRun(startMs, runs.getOrNull(index + 1)?.first ?: startMs, text)
            },
        )
    }

    private fun msOf(mark: MatchResult): Long {
        val (minutes, seconds, fraction) = mark.destructured
        val fractionMs = when (fraction.length) {
            2 -> fraction.toLong() * 10
            3 -> fraction.toLong()
            else -> 0L
        }
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>""")
    private val NOISE = Regex(
        """\((?:from|feat\.?|official|lyrical|video|audio|remix)[^)]*\)|\[[^]]*]|""" +
            """\b(?:official (?:video|audio|music video)|lyrical|full song|4k video)\b""",
        RegexOption.IGNORE_CASE,
    )
}

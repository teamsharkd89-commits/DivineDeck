package com.music.bitchord.data.lyrics

/**
 * Enhanced ("A2") LRC — a normal LRC line with a stamp in front of each word:
 *
 * ```
 * [00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna <00:28.07>call
 * ```
 *
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object EnhancedLrc {

    private val LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val WORD = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")

    /** Empty when [lrc] carries no word stamps — the caller can then fall back. */
    fun parse(lrc: String): List<LyricLine> {
        val rows = lrc.lineSequence()
            .mapNotNull { line -> LINE.matchEntire(line.trim()) }
            .map { match ->
                Row(
                    timeMs = stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3]),
                    words = WORD.findAll(match.groupValues[4]).toList(),
                    plain = match.groupValues[4].trim(),
                )
            }
            .sortedBy { it.timeMs }
            .toList()
        if (rows.none { it.words.isNotEmpty() }) return emptyList()

        return rows.mapIndexedNotNull { index, row ->
            if (row.words.isEmpty()) {
                val text = decodeEntities(row.plain)
                return@mapIndexedNotNull if (text.isEmpty()) null else LyricLine(row.timeMs, text)
            }
            val lineEnd = rows.getOrNull(index + 1)?.timeMs
                ?: (stamp(row.words.last()) + TAIL_MS)

            val runs = row.words.mapIndexed { i, match ->
                val wordStart = stamp(match)
                val wordEnd = row.words.getOrNull(i + 1)?.let { stamp(it) } ?: lineEnd
                TimedRun(wordStart, wordEnd, decodeEntities(match.groupValues[4]))
            }
            val words = wordsFromRuns(runs)
            if (words.isEmpty()) return@mapIndexedNotNull null
            LyricLine(
                timeMs = minOf(row.timeMs, words.first().startMs),
                text = words.joinToString(" ") { it.text },
                words = words,
            )
        }.withInstrumentalGaps()
    }

    private class Row(val timeMs: Long, val words: List<MatchResult>, val plain: String)

    private fun stamp(match: MatchResult): Long =
        stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])

    private fun stamp(minutes: String, seconds: String, fraction: String): Long {
        val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }

    fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return text
            .replace(Regex("&#x([0-9a-fA-F]+);")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private const val TAIL_MS = 800L
}

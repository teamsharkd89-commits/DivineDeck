package com.music.bitchord.data.lyrics

/**
 * A YouTube title, as a lyrics database would have indexed it.
 *
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
fun String.forLyricsSearch(): String {
    var name = this
    CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name.replace(WHITESPACE, " ").trim().trimEnd(',', '-', '–', '—').trim()
        .ifBlank { trim() }
}

/**
 * Trims " - Topic" off an auto-generated channel name.
 */
fun String.artistForLyricsSearch(): String =
    removeSuffix(" - Topic").trim().ifBlank { trim() }

private val WHITESPACE = Regex("""\s+""")

private val CREDITS = listOf(
    // Bracketed credits: (feat. X), [ft. X], (with X).
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    // The same, unbracketed and running to the end of the title.
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    // How the upload was labelled, not what was recorded.
    Regex(
        """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
            """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
            """\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    ),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
)

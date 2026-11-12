package com.music.bitchord.ui.player

import com.music.bitchord.data.model.ArtistCredit
import com.music.bitchord.data.model.ArtistRef
import com.music.bitchord.data.model.CreditJoiner
import com.music.bitchord.data.model.CreditSegment
import com.music.bitchord.data.model.Song

/**
 * Splits and maps artist credits.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
fun artistCredits(
    artist: String,
    artists: List<ArtistRef>,
    primaryArtistId: String?,
): List<ArtistCredit> {
    val stated = artists.filter { it.name.isNotBlank() }.map { credit ->
        ArtistCredit(name = credit.name, browseId = credit.browseId)
    }
    return stated.ifEmpty { splitCreditLine(artist, primaryArtistId) }
}

fun Song.creditSegments(): List<CreditSegment> {
    val credits = artistCredits(artist, artists, artistId)
    return com.music.bitchord.data.model.creditSegments(artist, credits)
}

private fun splitCreditLine(artist: String, primaryArtistId: String?): List<ArtistCredit> {
    val text = artist.trim()
    if (text.isEmpty()) return emptyList()

    val cuts = CreditJoiner.findAll(text).toList()
    if (cuts.isEmpty()) {
        return listOf(ArtistCredit(name = text, browseId = primaryArtistId))
    }

    val out = mutableListOf<ArtistCredit>()
    var start = 0
    for (cut in cuts) {
        val name = text.substring(start, cut.range.first).trim()
        if (name.isNotEmpty()) {
            out += ArtistCredit(
                name = name,
                browseId = if (out.isEmpty()) primaryArtistId else null,
            )
        }
        start = cut.range.last + 1
    }
    val tail = text.substring(start).trim()
    if (tail.isNotEmpty()) out += ArtistCredit(name = tail, browseId = null)
    if (out.isEmpty()) out += ArtistCredit(name = text, browseId = primaryArtistId)
    return out
}

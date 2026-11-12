package com.music.bitchord.playback

import androidx.media3.common.MimeTypes
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Determines stream envelope and MIME type (progressive audio vs HLS/DASH manifests).
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object StreamContainer {

    private val declared = ConcurrentHashMap<String, String>()

    fun manifestMimeOf(url: String): String? =
        declared[url]
            ?: when (url.substringBefore('?').substringAfterLast('.').lowercase(Locale.ROOT)) {
                "m3u8" -> MimeTypes.APPLICATION_M3U8
                "mpd" -> MimeTypes.APPLICATION_MPD
                else -> null
            }

    fun declare(url: String, mime: String) {
        declared[url] = mime
    }

    fun clear() {
        declared.clear()
    }
}

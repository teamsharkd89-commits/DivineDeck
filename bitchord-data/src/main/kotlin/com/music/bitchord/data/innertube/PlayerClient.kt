package com.music.bitchord.data.innertube

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.util.Locale

/**
 * Client identity metadata for YouTube / googlevideo media requests.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
data class PlayerClient(
    val clientName: String,
    val clientVersion: String,
    val userAgent: String,
    val origin: String? = null,
) {
    val referer: String? get() = origin?.let { "$it/" }

    fun mediaHeaders(): Map<String, String> = buildMap {
        put("User-Agent", userAgent)
        origin?.let { put("Origin", it) }
        referer?.let { put("Referer", it) }
    }

    companion object {
        private const val MUSIC_ORIGIN = "https://music.youtube.com"
        private const val YOUTUBE_ORIGIN = "https://www.youtube.com"

        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        private val IOS = PlayerClient(
            clientName = "IOS",
            clientVersion = "21.26.4",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        )

        private val IOS_RECENT = IOS.copy(
            clientVersion = "21.29.1",
            userAgent = "com.google.ios.youtube/21.29.1 (iPhone16,2; U; CPU iOS 18_5 like Mac OS X;)",
        )

        private val ANDROID = PlayerClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            userAgent = "com.google.android.youtube/21.26.364 " +
                "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip",
        )

        private val ANDROID_MUSIC = PlayerClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "8.39.42",
            userAgent = "com.google.android.apps.youtube.music/8.39.42 " +
                "(Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip",
        )

        private val WEB_REMIX = PlayerClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260707.12.00",
            userAgent = WEB_USER_AGENT,
            origin = MUSIC_ORIGIN,
        )

        private val WEB = PlayerClient(
            clientName = "WEB",
            clientVersion = "2.20260708.00.00",
            userAgent = WEB_USER_AGENT,
            origin = YOUTUBE_ORIGIN,
        )

        fun forStreamUrl(url: String): PlayerClient {
            val parsed = url.toHttpUrlOrNull() ?: return IOS
            val name = parsed.queryParameter("c")?.uppercase(Locale.ROOT) ?: return IOS
            val version = parsed.queryParameter("cver")
            return when {
                name.startsWith("IOS") ->
                    if (version == IOS_RECENT.clientVersion) IOS_RECENT else IOS
                name == "ANDROID_MUSIC" -> ANDROID_MUSIC
                name.startsWith("ANDROID") -> ANDROID
                name == "WEB_REMIX" -> WEB_REMIX
                name.startsWith("WEB") || name == "MWEB" -> WEB
                else -> IOS
            }
        }

        fun rangeBytesFor(url: String): Long {
            val parsed = url.toHttpUrlOrNull() ?: return Long.MAX_VALUE
            if (!parsed.host.endsWith("googlevideo.com")) return Long.MAX_VALUE
            return RANGE_BYTES
        }

        private const val RANGE_BYTES = 1024L * 1024
    }
}

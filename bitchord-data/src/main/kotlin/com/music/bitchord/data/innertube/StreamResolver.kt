package com.music.bitchord.data.innertube

import com.music.bitchord.data.Http
import com.music.bitchord.data.MonotonicClock
import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Turns a videoId into a direct stream URL ExoPlayer can play.
 *
 * Tier 1: InnerTubeX client catalog and cipher engine.
 * Tier 2: NewPipeExtractor failsafe.
 * Tier 3: HTTP range probe to guarantee byte validity before handing to ExoPlayer.
 *
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object StreamResolver {

    private const val TAG = "BitChord"
    private const val URL_TTL_MS = 3 * 3600 * 1000L
    private const val NEXT_ENDPOINT = "/youtubei/v1/next"
    private const val EMPTY_NEXT_RESPONSE =
        """{"responseContext":{},"contents":{},"currentVideoEndpoint":{},"trackingParams":""}"""

    class Stream(
        val url: String,
        val kbps: Int,
        val mimeType: String,
        val loudnessDb: Double? = null,
    ) {
        val downloadExtension: String
            get() = if ("webm" in mimeType.lowercase()) "webm" else "m4a"

        val downloadMimeType: String
            get() = if (downloadExtension == "webm") "audio/webm" else "audio/mp4"
    }

    private class CachedUrl(val url: String, val at: Long)
    private val recent = ConcurrentHashMap<String, CachedUrl>()

    private class OkHttpDownloader : Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
            if (NEXT_ENDPOINT in request.url()) {
                return Response(200, "OK", emptyMap(), EMPTY_NEXT_RESPONSE, request.url())
            }

            val builder = Request.Builder()
                .method(request.httpMethod(), null)
                .url(request.url())

            request.headers().forEach { (name, values) ->
                values.forEach { builder.addHeader(name, it) }
            }

            builder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )

            val call = Http.client.newCall(builder.build())
            val response = call.execute()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }

    private val initNewPipe by lazy {
        NewPipe.init(OkHttpDownloader())
    }

    private val resolverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun resolve(videoId: String): String {
        recent[videoId]?.takeIf { MonotonicClock.nowMs() - it.at < URL_TTL_MS }?.let {
            return it.url
        }

        val stream = withContext(Dispatchers.IO) {
            coalescedResolve(videoId)
        }

        NerdStats.onStreamPicked(videoId, stream.kbps)
        recent[videoId] = CachedUrl(stream.url, MonotonicClock.nowMs())
        return stream.url
    }

    fun mediaHeadersFor(url: String): Map<String, String> {
        return InnerTubeXResolver.headersFor(url) ?: PlayerClient.forStreamUrl(url).mediaHeaders()
    }

    /**
     * Resolves an audio stream specifically for downloading (original BitChord 1.8.1 contract).
     */
    suspend fun resolveForDownload(
        videoId: String,
        maxKbps: Int = AppSettings.currentDownloadQuality.maxKbps,
        requireM4a: Boolean = false
    ): Stream = withContext(Dispatchers.IO) {
        val stream = coalescedResolve(videoId)
        recent[videoId] = CachedUrl(stream.url, MonotonicClock.nowMs())
        stream
    }

    /**
     * Evicts a cached stream URL if mid-stream playback/download is refused by googlevideo.
     */
    fun onPlaybackRefused(url: String, code: Int) {
        TrackLog.w(TAG, "Stream playback/download refused ($code) for URL, evicting from cache")
        recent.entries.removeIf { it.value.url == url }
    }

    private suspend fun coalescedResolve(videoId: String): Stream {
        val streamCeiling = AppSettings.effectiveAudioQuality.maxKbps
        // 1. InnerTubeX First
        try {
            val itx = InnerTubeXResolver.extract(videoId, maxKbps = streamCeiling)
            if (itx != null) {
                val ok = probe(itx.url)
                if (ok) {
                    TrackLog.d(TAG, "Stream resolved via InnerTubeX for $videoId @ ${itx.kbps}kbps")
                    return Stream(itx.url, itx.kbps, itx.mimeType, itx.loudnessDb)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            TrackLog.w(TAG, "InnerTubeX resolution failed for $videoId: ${e.message}")
        }

        // 2. NewPipeExtractor Failsafe
        initNewPipe
        return try {
            newPipeExtract(videoId)
        } catch (e: Throwable) {
            TrackLog.e(TAG, "NewPipeExtractor fallback failed for $videoId: ${e.message}", e)
            throw IOException("No playable audio stream available for videoId: $videoId", e)
        }
    }

    private fun newPipeExtract(videoId: String): Stream {
        val service = ServiceList.YouTube
        val link = "https://www.youtube.com/watch?v=$videoId"
        val extractor = service.getStreamExtractor(link) as YoutubeStreamExtractor
        extractor.fetchPage()

        val audioStreams = extractor.audioStreams
        if (audioStreams.isNullOrEmpty()) {
            throw IOException("NewPipe found no audio streams for $videoId")
        }

        val progressiveCandidates = audioStreams.filter {
            !it.content.isNullOrBlank() && it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP
        }
        val best = progressiveCandidates.maxByOrNull { it.averageBitrate }
            ?: audioStreams.firstOrNull { !it.content.isNullOrBlank() }
            ?: throw IOException("NewPipe found no usable audio streams for $videoId")

        val streamUrl = best.content
        val kbps = if (best.averageBitrate > 0) best.averageBitrate else 128
        TrackLog.d(TAG, "Stream resolved via NewPipeExtractor for $videoId @ ${kbps}kbps")
        return Stream(streamUrl, kbps, best.format?.mimeType ?: "audio/mp4")
    }

    private fun probe(url: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1024")
                .apply {
                    PlayerClient.forStreamUrl(url).mediaHeaders().forEach { (k, v) ->
                        header(k, v)
                    }
                }
                .build()
            Http.client.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        } catch (_: Throwable) {
            false
        }
    }
}

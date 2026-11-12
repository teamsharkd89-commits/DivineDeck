package com.music.bitchord.download

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.innertube.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.OutputStream
import kotlin.math.min

/**
 * Original BitChord 1.8.1 Downloader adapted for internal module integration.
 *
 * Fetches a resolved audio stream in bounded 2 MB HTTP range requests to bypass
 * googlevideo single-GET throttling, reporting real byte progress and re-resolving
 * once if googlevideo refuses a range request mid-stream (403/404/410).
 */
object Downloader {

    private const val TAG = "BitChordDownloader"
    private const val CHUNK_BYTES = 2L * 1024L * 1024L
    private const val BUFFER_BYTES = 32 * 1024

    /**
     * Abstraction allowing deterministic unit testing while defaulting to the
     * production BitChord 2 MB range-request engine.
     */
    interface StreamFetcher {
        suspend fun resolveAndFetch(
            videoId: String,
            maxKbps: Int,
            requireM4a: Boolean,
            openSink: (StreamResolver.Stream) -> OutputStream,
            onProgress: (written: Long, total: Long) -> Unit
        ): StreamResolver.Stream
    }

    object DefaultStreamFetcher : StreamFetcher {
        override suspend fun resolveAndFetch(
            videoId: String,
            maxKbps: Int,
            requireM4a: Boolean,
            openSink: (StreamResolver.Stream) -> OutputStream,
            onProgress: (written: Long, total: Long) -> Unit
        ): StreamResolver.Stream {
            val stream = StreamResolver.resolveForDownload(
                videoId = videoId,
                maxKbps = maxKbps,
                requireM4a = requireM4a
            )
            openSink(stream).use { output ->
                fetch(
                    initialUrl = stream.url,
                    reResolve = {
                        StreamResolver.resolveForDownload(
                            videoId = videoId,
                            maxKbps = stream.kbps,
                            requireM4a = stream.downloadExtension == "m4a"
                        ).url
                    },
                    sink = output,
                    onProgress = onProgress
                )
            }
            return stream
        }
    }

    suspend fun fetch(
        initialUrl: String,
        reResolve: (suspend () -> String)? = null,
        sink: OutputStream,
        onProgress: (written: Long, total: Long) -> Unit,
    ) {
        var url = initialUrl
        var headers = StreamResolver.mediaHeadersFor(url)
        var reResolved = false

        var total = -1L
        var written = 0L
        var supportsRanges = true
        val buf = ByteArray(BUFFER_BYTES)

        suspend fun openRange(start: Long, endInclusive: Long): Response {
            val req = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                if (supportsRanges) {
                    header("Range", "bytes=$start-$endInclusive")
                }
            }.build()
            val call = Http.client.newCall(req)
            return try {
                call.execute()
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                throw t
            }
        }

        while (total < 0L || written < total) {
            currentCoroutineContext().ensureActive()
            val start = written
            val end = if (total > 0L) {
                min(start + CHUNK_BYTES - 1L, total - 1L)
            } else {
                start + CHUNK_BYTES - 1L
            }

            val resp = openRange(start, end)
            resp.use { r ->
                val code = r.code
                if (code == 403 || code == 404 || code == 410) {
                    StreamResolver.onPlaybackRefused(url, code)
                    if (!reResolved && reResolve != null) {
                        reResolved = true
                        TrackLog.w(TAG, "Range $start-$end refused with HTTP $code, re-resolving stream")
                        url = reResolve()
                        headers = StreamResolver.mediaHeadersFor(url)
                        return@use
                    }
                    throw IOException("HTTP $code")
                }
                if (!r.isSuccessful && code != 206) {
                    throw IOException("HTTP $code")
                }

                val body = r.body ?: throw IOException("Empty response body")
                if (total < 0L) {
                    total = parseTotalLength(r, code, body.contentLength())
                    if (code == 200) {
                        supportsRanges = false
                    }
                } else if (code == 200 && written > 0L) {
                    throw IOException("Server ignored Range header at offset $written")
                }

                val source = body.source()
                var chunkBytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = try {
                        source.read(buf)
                    } catch (e: CancellationException) {
                        throw e
                    }
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                    written += n
                    chunkBytes += n
                    onProgress(written, total)
                }

                if (!supportsRanges) {
                    total = written
                } else if (chunkBytes == 0L && (total < 0L || written < total)) {
                    throw IOException("Zero-byte range response at offset $written")
                }
            }
        }
        sink.flush()
    }

    private fun parseTotalLength(resp: Response, code: Int, bodyLength: Long): Long {
        if (code == 206) {
            val cr = resp.header("Content-Range")
            if (!cr.isNullOrBlank()) {
                val slash = cr.lastIndexOf('/')
                if (slash >= 0 && slash + 1 < cr.length) {
                    val totalStr = cr.substring(slash + 1).trim()
                    totalStr.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
                }
            }
        }
        return if (bodyLength > 0L) bodyLength else -1L
    }
}

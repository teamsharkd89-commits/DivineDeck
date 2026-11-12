package com.music.bitchord.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.music.bitchord.data.TrackLog
import java.io.InterruptedIOException
import kotlin.math.min

/**
 * Fetches progressive audio streams as a sequence of bounded byte ranges.
 * Bypasses googlevideo throttling by requesting high-throughput range chunks.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
@OptIn(UnstableApi::class)
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
) : DataSource {

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val chunkBytes: Long = DEFAULT_CHUNK_BYTES,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstreamFactory.createDataSource(), chunkBytes)
    }

    companion object {
        const val DEFAULT_CHUNK_BYTES = 2L * 1024 * 1024 // 2MB
    }

    private var baseSpec: DataSpec? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var chunkRemaining = 0L
    private var chunkOpen = false
    private var passthrough = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        baseSpec = dataSpec
        position = dataSpec.position

        val total = dataSpec.uri.getQueryParameter("clen")?.toLongOrNull()
        if (total == null) {
            passthrough = true
            chunkOpen = true
            return upstream.open(dataSpec)
        }

        passthrough = false
        val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else position + dataSpec.length
        bytesRemaining = end - position
        chunkRemaining = 0
        chunkOpen = false
        openNextChunk()
        return bytesRemaining
    }

    private fun openNextChunk() {
        if (chunkOpen) {
            upstream.close()
            chunkOpen = false
        }
        if (bytesRemaining <= 0) return

        val thisChunk = min(chunkBytes, bytesRemaining)
        val spec = requireNotNull(baseSpec).buildUpon()
            .setPosition(position)
            .setLength(thisChunk)
            .build()

        upstream.open(spec)
        chunkOpen = true
        chunkRemaining = thisChunk
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (passthrough) {
            return upstream.read(buffer, offset, length)
        }

        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) openNextChunk()

        val toRead = min(length.toLong(), chunkRemaining).toInt()
        val read = upstream.read(buffer, offset, toRead)
        if (read == C.RESULT_END_OF_INPUT) return C.RESULT_END_OF_INPUT

        position += read
        bytesRemaining -= read
        chunkRemaining -= read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        if (chunkOpen || passthrough) {
            upstream.close()
            chunkOpen = false
            passthrough = false
        }
    }
}

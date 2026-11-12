package com.music.bitchord.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * Least-Recently-Used cache evictor with dynamically configurable size ceiling
 * and head-span protection to prevent cache thrashing on track beginnings.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
@OptIn(UnstableApi::class)
class DynamicLruCacheEvictor(
    @Volatile var maxBytes: Long,
    private val headBytes: Long = DEFAULT_HEAD_BYTES,
    private val headBudgetBytes: Long = DEFAULT_HEAD_BUDGET_BYTES,
) : CacheEvictor {

    companion object {
        const val DEFAULT_HEAD_BYTES = 512L * 1024
        const val DEFAULT_HEAD_BUDGET_BYTES = 64L * 1024 * 1024
        const val DEFAULT_CACHE_LIMIT_BYTES = 512L * 1024 * 1024 // 512MB
    }

    private val leastRecentlyUsed = TreeSet<CacheSpan>(::compare)
    private val protectedHeads = TreeSet<CacheSpan>(::compare)
    private var protectedSize = 0L
    private var currentSize = 0L

    private fun isHead(span: CacheSpan): Boolean = span.position < headBytes

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() = Unit

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evictCache(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        if (isHead(span) && protectedSize + span.length <= headBudgetBytes) {
            protectedHeads.add(span)
            protectedSize += span.length
        } else {
            leastRecentlyUsed.add(span)
        }
        currentSize += span.length
        evictCache(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (protectedHeads.remove(span)) {
            protectedSize -= span.length
        } else {
            leastRecentlyUsed.remove(span)
        }
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Reclaims space immediately when [maxBytes] decreases, rather than waiting
     * for the next write to evict expired spans.
     */
    fun applyNow(cache: Cache) = evictCache(cache, 0)

    fun setBytesLimit(cache: Cache?, bytes: Long) {
        maxBytes = bytes
        if (cache != null) {
            applyNow(cache)
        }
    }

    private fun evictCache(cache: Cache, requiredBytes: Long) {
        while (currentSize + requiredBytes > maxBytes && leastRecentlyUsed.isNotEmpty()) {
            val oldest = leastRecentlyUsed.first()
            cache.removeSpan(oldest)
        }
        while (currentSize + requiredBytes > maxBytes && protectedHeads.isNotEmpty()) {
            val oldestProtected = protectedHeads.first()
            cache.removeSpan(oldestProtected)
        }
    }

    private fun compare(a: CacheSpan, b: CacheSpan): Int {
        val diff = a.lastTouchTimestamp - b.lastTouchTimestamp
        if (diff == 0L) return a.compareTo(b)
        return if (diff < 0) -1 else 1
    }
}

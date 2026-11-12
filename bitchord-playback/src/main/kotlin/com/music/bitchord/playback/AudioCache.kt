package com.music.bitchord.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * On-disk persistent audio cache for seeking and streaming playback efficiency.
 * Extracted and adapted from BitChord 1.8.1 (GPLv3).
 *
 * Integrated with [AppSettings.audioCacheLimitBytes] to dynamically adjust disk cache
 * quota and evict LRU spans without service or player re-instantiation.
 */
@OptIn(UnstableApi::class)
object AudioCache {

    private var cacheInstance: SimpleCache? = null
    private val evictor = DynamicLruCacheEvictor(DynamicLruCacheEvictor.DEFAULT_CACHE_LIMIT_BYTES)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var limitJob: Job? = null

    @Synchronized
    fun init(context: Context) {
        if (cacheInstance != null) return
        try {
            AppSettings.init(context)
            evictor.maxBytes = AppSettings.audioCacheLimitBytes.value
            val cacheDir = File(context.cacheDir, "bitchord_audio").apply { mkdirs() }
            val databaseProvider = StandaloneDatabaseProvider(context)
            val cache = SimpleCache(cacheDir, evictor, databaseProvider)
            cacheInstance = cache

            limitJob?.cancel()
            limitJob = scope.launch {
                AppSettings.audioCacheLimitBytes.collect { limit ->
                    setBytesLimit(limit)
                }
            }
        } catch (_: Throwable) {
            // Failsafe fallback: if cache cannot initialize, playback continues without caching
            cacheInstance = null
        }
    }

    val cache: SimpleCache? get() = cacheInstance

    /**
     * Updates the dynamic byte ceiling of the evictor and reclaims space immediately
     * if the new limit is lower than current cache usage.
     */
    fun setBytesLimit(bytes: Long) {
        evictor.maxBytes = bytes
        cacheInstance?.let { c ->
            runCatching { evictor.applyNow(c) }
        }
    }

    /**
     * Returns current active byte ceiling of the evictor.
     */
    fun getBytesLimit(): Long = evictor.maxBytes

    /**
     * Test isolation method to inject a mock or test [SimpleCache] and observe limits.
     */
    fun initForTest(cache: SimpleCache, initialBytesLimit: Long = AppSettings.DEFAULT_CACHE_LIMIT_BYTES) {
        synchronized(this) {
            cacheInstance = cache
            evictor.maxBytes = initialBytesLimit
            limitJob?.cancel()
            limitJob = scope.launch {
                AppSettings.audioCacheLimitBytes.collect { limit ->
                    setBytesLimit(limit)
                }
            }
        }
    }

    fun playbackFactory(upstream: DataSource.Factory): DataSource.Factory {
        val c = cacheInstance ?: return upstream
        val cacheFactory = CacheDataSource.Factory()
            .setCache(c)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // Original BitChord 1.8.1 pattern: local files (`file://`, `content://`, `asset://`)
        // bypass SimpleCache so downloaded tracks read directly from disk without duplicating bytes.
        return DataSource.Factory {
            val cacheDs = cacheFactory.createDataSource()
            val directDs = upstream.createDataSource()
            object : DataSource {
                private var active: DataSource = cacheDs

                override fun addTransferListener(transferListener: TransferListener) {
                    cacheDs.addTransferListener(transferListener)
                    directDs.addTransferListener(transferListener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val scheme = dataSpec.uri.scheme
                    active = if (scheme == "file" || scheme == "content" || scheme == "asset") {
                        directDs
                    } else {
                        cacheDs
                    }
                    return active.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (length == 0) return 0
                    return active.read(buffer, offset, length)
                }

                override fun getUri() = active.uri

                override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

                override fun close() {
                    active.close()
                }
            }
        }
    }

    @Synchronized
    fun release() {
        limitJob?.cancel()
        limitJob = null
        try {
            cacheInstance?.release()
        } catch (_: Throwable) {}
        cacheInstance = null
    }
}

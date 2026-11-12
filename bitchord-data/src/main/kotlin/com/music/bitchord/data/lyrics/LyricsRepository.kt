package com.music.bitchord.data.lyrics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where the player gets its lyrics.
 *
 * Adapted from BitChord 1.8.1 `LyricsRepository.kt` (GPLv3), preserving:
 * - Concurrent racing of enabled providers while taking answers strictly in [order].
 * - Word-timed priority (`prioritizeSyllableSync`) vs line-timed fallback.
 * - ISRC recording resolution via [BiniLyrics] and shared document deduplication (`SharedDocuments`).
 * - Background vocal extraction (`withBackgroundVocals()`).
 * - Local/embedded lyrics priority ([EmbeddedLyrics] for MP4/M4A `©lyr` / `BITCHORD_LYRICS`,
 *   FLAC `VORBIS_COMMENT`, WebM `SimpleTag`, ID3v2 `USLT`, and `.lrc` sidecar files).
 * - Automatic offline persistence: when a downloaded song resolves lyrics online, a `.lrc` sidecar
 *   is written beside the downloaded file so subsequent offline playback loads lyrics with zero network calls.
 * - Pluggable [ProviderFetcher] for deterministic unit testing of provider fallback, errors, and cancellation.
 */
object LyricsRepository {

    /** Lyrics, and which source they turned out to come from. */
    data class Result(val source: LyricsSource, val lines: List<LyricLine>)

    /**
     * Pluggable provider fetcher interface so unit tests can inject custom provider responses,
     * simulate network errors, or test fallback and cancellation deterministically.
     */
    fun interface ProviderFetcher {
        suspend fun fetch(
            source: LyricsSource,
            videoId: String,
            title: String,
            artist: String,
            durationMs: Long,
            album: String?,
            isrc: String?,
            hit: BiniLyrics.Hit?,
            get: suspend (String) -> String?,
        ): List<LyricLine>?
    }

    val DefaultProviderFetcher = ProviderFetcher { source, videoId, title, artist, durationMs, album, isrc, hit, get ->
        defaultFetch(source, videoId, title, artist, durationMs, album, isrc, hit, get)
    }

    @Volatile
    var providerFetcher: ProviderFetcher = DefaultProviderFetcher

    @Volatile
    var lyricsDispatcher: CoroutineDispatcher = Dispatchers.IO

    /**
     * In-memory LRU cache of resolved lyrics by `videoId` so re-visiting the same track
     * in the same session does not re-trigger redundant network lookups.
     */
    private const val CACHE_CAPACITY = 64
    private val memoryCache: MutableMap<String, Result> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Result>(CACHE_CAPACITY, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Result>) = size > CACHE_CAPACITY
        },
    )

    fun clearMemoryCache() {
        memoryCache.clear()
        isrcs.clear()
        lyricsDispatcher = Dispatchers.IO
        EmbeddedLyrics.ioDispatcher = Dispatchers.IO
    }

    fun putInMemoryCache(videoId: String, result: Result) {
        if (videoId.isNotBlank()) {
            memoryCache[videoId] = result
        }
    }

    /**
     * Checks if the device currently has an active internet connection (or returns the test override).
     */
    fun isOffline(context: Context? = null): Boolean {
        lyricsOfflineModeOverride?.let { return it }
        if (context == null) return false
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching false
            val network = cm.activeNetwork ?: return@runCatching true
            val caps = cm.getNetworkCapabilities(network) ?: return@runCatching true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    /**
     * Resolves the complete [LyricsUiState] for [song], respecting:
     * 1. Embedded/local lyrics first (`song.localUri` or `Downloads.verifiedSavedUriString(song.videoId)`).
     * 2. In-memory cache (`memoryCache[song.videoId]`).
     * 3. Offline guard: if [isOffline] is true, NEVER performs network requests — returns
     *    [LyricsUiState.OfflineUnavailable] immediately when no local/cached lyrics exist.
     * 4. Online provider chain via [lyrics], mapping synced results to [LyricsUiState.Synced],
     *    unsynced plain lyrics to [LyricsUiState.Plain], provider exceptions to [LyricsUiState.Error],
     *    and genuine misses across all providers to [LyricsUiState.NotFound].
     * 5. Automatic sidecar persistence for downloaded tracks.
     */
    suspend fun resolveForSong(
        song: Song,
        context: Context? = null,
        durationMs: Long = song.durationMillis,
        sources: Set<LyricsSource> = AppSettings.lyricsSources.value,
        order: List<LyricsSource> = AppSettings.lyricsSourceOrder.value,
        prioritizeSyllableSync: Boolean = AppSettings.prioritizeSyllableSync.value,
        forceRefresh: Boolean = false,
        onSourceStarted: ((LyricsSource) -> Unit)? = null,
    ): LyricsUiState {
        val videoId = song.videoId
        val localUri = song.localUri?.takeIf { Downloads.isValidLocalUri(it) }
            ?: Downloads.verifiedSavedUriString(videoId)

        // 1. Check embedded or sidecar local lyrics first (works both online and 100% offline!)
        if (!localUri.isNullOrBlank()) {
            val embedded = EmbeddedLyrics.forUriDetailed(context, localUri)
            if (embedded != null && embedded.lines.any { it.text.isNotBlank() }) {
                return LyricsUiState.EmbeddedLocal(
                    videoId = videoId,
                    lines = embedded.lines,
                    isSynced = embedded.lines.any { it.timeMs > 0L },
                    isWordSynced = embedded.lines.any { it.isWordSynced },
                    containerOrFormat = embedded.formatLabel,
                )
            }
        }

        // 2. Check in-memory cache if not forcing refresh
        if (!forceRefresh && videoId.isNotBlank()) {
            memoryCache[videoId]?.let { cached ->
                return toUiState(videoId, cached)
            }
        }

        // 3. Check persistent disk store in DIVINE DECK (LyricsStore)
        if (!forceRefresh && videoId.isNotBlank()) {
            val lines = LyricsStore.getLines(context, videoId)
            if (lines != null && lines.any { it.text.isNotBlank() }) {
                val isSynced = lines.any { it.timeMs > 0L }
                val isWordSynced = lines.any { it.isWordSynced }
                memoryCache[videoId] = Result(
                    source = if (isSynced) LyricsSource.LRCLIB else LyricsSource.GENIUS,
                    lines = lines,
                )
                // If this track is also downloaded locally, ensure sidecar is written
                if (!localUri.isNullOrBlank()) {
                    persistSidecarForLocalUri(localUri, lines)
                }
                return LyricsUiState.EmbeddedLocal(
                    videoId = videoId,
                    lines = lines,
                    isSynced = isSynced,
                    isWordSynced = isWordSynced,
                    containerOrFormat = "Caché Offline",
                )
            }
        }

        // 4. Offline check: NEVER contact network providers when offline
        if (isOffline(context)) {
            return LyricsUiState.OfflineUnavailable(
                videoId = videoId,
                title = song.title,
            )
        }

        // 5. Online lookup across BitChord providers
        val hadProviderException = AtomicBoolean(false)
        val lastExceptionMessage = StringBuilder()

        val result = lyrics(
            videoId = videoId,
            title = song.title,
            artist = song.artist,
            durationMs = durationMs,
            album = song.albumName,
            sources = sources,
            order = order,
            prioritizeSyllableSync = prioritizeSyllableSync,
            onSourceStarted = onSourceStarted,
            onSourceError = { _, ex ->
                hadProviderException.set(true)
                synchronized(lastExceptionMessage) {
                    if (lastExceptionMessage.isEmpty() && !ex.message.isNullOrBlank()) {
                        lastExceptionMessage.append(ex.message)
                    }
                }
            },
        )

        if (result != null && result.lines.any { it.text.isNotBlank() }) {
            if (videoId.isNotBlank()) {
                memoryCache[videoId] = result
                // Persist to internal disk store for permanent offline availability
                LyricsStore.save(context, videoId, result.lines, result.source)
            }
            // If this track is downloaded locally, persist a sidecar `.lrc` so future offline plays have it!
            if (!localUri.isNullOrBlank()) {
                persistSidecarForLocalUri(localUri, result.lines)
            }
            return toUiState(videoId, result)
        }

        if (hadProviderException.get()) {
            val msg = synchronized(lastExceptionMessage) {
                lastExceptionMessage.toString().ifBlank { "Error temporal al consultar proveedores de letras" }
            }
            return LyricsUiState.Error(
                videoId = videoId,
                message = msg,
                canRetry = true,
            )
        }

        return LyricsUiState.NotFound(
            videoId = videoId,
            title = song.title,
        )
    }

    private fun toUiState(videoId: String, result: Result): LyricsUiState {
        val hasTimestamps = result.lines.any { it.timeMs > 0L }
        return if (hasTimestamps) {
            LyricsUiState.Synced(
                videoId = videoId,
                lines = result.lines,
                source = result.source,
                isWordSynced = result.lines.any { it.isWordSynced },
            )
        } else {
            LyricsUiState.Plain(
                videoId = videoId,
                lines = result.lines,
                source = result.source,
            )
        }
    }

    /**
     * Writes resolved [lines] to a `.lrc` sidecar file next to a downloaded audio file
     * (using Enhanced LRC when word-synced, or standard LRC otherwise).
     */
    fun persistSidecarForLocalUri(localUri: String, lines: List<LyricLine>): File? = runCatching {
        if (lines.none { it.text.isNotBlank() }) return null
        val audioFile = DownloadStore.verifyLocalFile(localUri) ?: run {
            val path = if (localUri.startsWith("file:")) URI(localUri).path else localUri
            File(path).takeIf { it.parentFile?.exists() == true }
        } ?: return null
        val parent = audioFile.parentFile ?: return null
        val sidecarFile = if (audioFile.name.endsWith(".m3u8", ignoreCase = true)) {
            File(parent, "lyrics.lrc")
        } else {
            File(parent, "${audioFile.nameWithoutExtension}.lrc")
        }
        val content = lines.toEnhancedLrc().takeIf { it.isNotBlank() } ?: lines.toLrc()
        if (content.isBlank()) return null
        sidecarFile.writeText(content, Charsets.UTF_8)
        sidecarFile
    }.getOrNull()

    /**
     * Deletes the `.lrc` sidecar associated with [localUri] when a download is removed.
     */
    fun deleteSidecarForLocalUri(localUri: String?): Boolean = runCatching {
        if (localUri.isNullOrBlank()) return false
        val audioFile = DownloadStore.verifyLocalFile(localUri) ?: run {
            val path = if (localUri.startsWith("file:")) URI(localUri).path else localUri
            File(path).takeIf { it.parentFile?.exists() == true }
        } ?: return false
        val parent = audioFile.parentFile ?: return false
        val sidecarFile = if (audioFile.name.endsWith(".m3u8", ignoreCase = true)) {
            File(parent, "lyrics.lrc")
        } else {
            File(parent, "${audioFile.nameWithoutExtension}.lrc")
        }
        if (sidecarFile.exists()) sidecarFile.delete() else false
    }.getOrDefault(false)

    /**
     * Returns the currently cached lines in memory for [videoId], if any.
     */
    fun getMemoryCachedLines(videoId: String): List<LyricLine>? {
        return memoryCache[videoId]?.lines
    }

    /**
     * Original BitChord `LyricsRepository.lyrics` concurrent provider race with priority ordering.
     */
    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = AppSettings.lyricsSources.value,
        order: List<LyricsSource> = AppSettings.lyricsSourceOrder.value,
        prioritizeSyllableSync: Boolean = AppSettings.prioritizeSyllableSync.value,
        isrc: String? = null,
        onSourceStarted: ((LyricsSource) -> Unit)? = null,
        onSourceResult: ((LyricsSource, Result?) -> Unit)? = null,
        onSourceCancelled: ((LyricsSource) -> Unit)? = null,
        onSourceError: ((LyricsSource, Throwable) -> Unit)? = null,
    ): Result? = coroutineScope {
        if (lyricsOfflineModeOverride == true) return@coroutineScope null

        val sequence = (order + LyricsSource.offered)
            .distinct()
            .filter { it in sources && !it.hidden }
        if (sequence.isEmpty()) return@coroutineScope null

        val searchTitle = title.forLyricsSearch()
        val searchArtist = artist.artistForLyricsSearch()

        val known = isrc?.takeIf { it.isNotBlank() } ?: isrcs[videoId]
        val hit = if (known == null && providerFetcher === DefaultProviderFetcher) {
            identify(videoId, searchTitle, searchArtist, durationMs, album, sequence)
        } else {
            null
        }
        val recording = known ?: hit?.isrc?.takeIf { it.isNotBlank() }
        val documents = SharedDocuments(this)
        val activeFetcher = providerFetcher

        val racing: List<Pair<LyricsSource, Deferred<Result?>>> = sequence.map { source ->
            val startMode = if (source == LyricsSource.GENIUS && sequence.size > 1) {
                CoroutineStart.LAZY
            } else {
                CoroutineStart.DEFAULT
            }
            source to async(lyricsDispatcher, start = startMode) {
                onSourceStarted?.invoke(source)
                try {
                    val found = activeFetcher.fetch(
                        source,
                        videoId,
                        searchTitle,
                        searchArtist,
                        durationMs,
                        album,
                        recording,
                        hit,
                        documents::get,
                    )?.takeIf { lines -> lines.any { it.text.isNotBlank() } }
                        ?.let { result(source, it) }
                    onSourceResult?.invoke(source, found)
                    found
                } catch (cancelled: CancellationException) {
                    onSourceCancelled?.invoke(source)
                    throw cancelled
                } catch (e: Exception) {
                    onSourceError?.invoke(source, e)
                    onSourceResult?.invoke(source, null)
                    null
                }
            }
        }

        try {
            var lineSynced: Result? = null
            var plainFallback: Result? = null
            for ((source, job) in racing) {
                if (lineSynced != null && source == LyricsSource.GENIUS) continue

                val found = runCatching { job.await() }.getOrNull() ?: continue
                if (found.lines.any { it.isWordSynced }) return@coroutineScope found
                val hasLineSync = found.lines.any { it.timeMs > 0L }
                if (!prioritizeSyllableSync && hasLineSync) {
                    return@coroutineScope found
                }
                if (hasLineSync) {
                    if (lineSynced == null) lineSynced = found
                } else {
                    if (plainFallback == null) plainFallback = found
                }
            }
            lineSynced ?: plainFallback
        } finally {
            racing.forEach { it.second.cancel() }
            documents.cancel()
        }
    }

    private suspend fun defaultFetch(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        isrc: String?,
        hit: BiniLyrics.Hit?,
        get: suspend (String) -> String?,
    ): List<LyricLine>? {
        return when (source) {
            LyricsSource.LRC_RED -> LrcRed.lyrics(title, artist, durationMs, isrc, get)
            LyricsSource.BETTER_LYRICS -> BetterLyrics.lyrics(title, artist, durationMs, album)
            LyricsSource.BETTER_LYRICS_PORTATO -> BetterLyrics.portato(title, artist, durationMs, album)
            LyricsSource.LYRICS_PLUS -> LyricsPlus.lyrics(title, artist, durationMs, album, isrc)
            LyricsSource.BINI_LYRICS ->
                (hit?.let { BiniLyrics.lyricsFor(it, get) }
                    ?: BiniLyrics.lyrics(title, artist, durationMs, album, isrc, get))
                    ?.also { remember(videoId, it.isrc) }
                    ?.lines
            LyricsSource.UNISON -> Unison.lyrics(title, artist, durationMs, album)
            LyricsSource.SIMP_MUSIC -> SimpMusicLyrics.lyrics(videoId, durationMs)
            LyricsSource.YOUTUBE_TRANSCRIPT -> YouTubeTranscriptLyrics.lyrics(videoId)
            LyricsSource.YOUTUBE_MUSIC -> YouTubeMusicLyrics.lyrics(videoId)
            LyricsSource.LRCLIB -> LrcLib.lyrics(title, artist, durationMs)
            LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)
            LyricsSource.PAXSENIX_SPOTIFY -> PaxSenix.spotifyLyrics(title, artist, durationMs)
            LyricsSource.PAXSENIX_MUSIXMATCH -> PaxSenix.musixmatchLyrics(title, artist, durationMs)
            LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)
            LyricsSource.MEGALOBIZ -> Megalobiz.lyrics(title, artist)
            LyricsSource.GENIUS -> Genius.lyrics(title, artist)
        }
    }

    private fun result(source: LyricsSource, lines: List<LyricLine>) =
        Result(source, lines.withBackgroundVocals())

    private const val IDENTIFY_TIMEOUT_MS = 2_500L

    private suspend fun identify(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
        sequence: List<LyricsSource>,
    ): BiniLyrics.Hit? {
        if (LyricsSource.BINI_LYRICS !in sequence) return null
        val hit = withTimeoutOrNull(IDENTIFY_TIMEOUT_MS) {
            runCatching { BiniLyrics.identify(title, artist, durationMs, album) }.getOrNull()
        }
        if (hit == null) return null
        remember(videoId, hit.isrc)
        return hit
    }

    private const val REMEMBERED = 100

    private val isrcs: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(REMEMBERED, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, String>) = size > REMEMBERED
        },
    )

    private fun remember(videoId: String, isrc: String?) {
        if (isrc.isNullOrBlank() || videoId.isEmpty()) return
        isrcs[videoId] = isrc
    }

    private class SharedDocuments(private val scope: CoroutineScope) {
        private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()

        suspend fun get(url: String): String? =
            inFlight.computeIfAbsent(url) { scope.async(lyricsDispatcher) { lyricsGet(url) } }.await()

        fun cancel() = inFlight.values.forEach { it.cancel() }
    }
}

package com.music.bitchord.download

import android.content.Context
import android.content.SharedPreferences
import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.lyrics.LyricsRepository
import com.music.bitchord.data.lyrics.LyricsStore
import com.music.bitchord.data.lyrics.LyricsUiState
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.DownloadQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Original BitChord 1.8.1 Downloads engine adapted for internal module integration in DIVINE DECK.
 *
 * Responsibilities:
 * - Queues and executes concurrent song downloads (up to `maxConcurrent` workers).
 * - Writes audio streams atomically via [DownloadStore.Pending] (`.part` files) so interrupted
 *   or failed downloads never leave corrupted partial files marked as completed.
 * - Reports real byte/fraction progress via [active] and [DownloadSession.state].
 * - Prevents duplicate downloads of the same `videoId`.
 * - Supports cancelling active/queued downloads, retrying failed downloads, and deleting
 *   completed downloads (removing both the file on disk and the persisted record without
 *   modifying playlists or favorites in `LocalPlaylistStore`).
 * - Persists completed downloads and full metadata (`SavedSongMetadata`) in `SharedPreferences`
 *   using `kotlinx.serialization.json.Json` and verifies physical file integrity on startup and
 *   before offline playback resolution.
 */
object Downloads {

    private const val TAG = "BitChordDownloads"
    private const val PREFS_NAME = "bitchord_settings"
    private const val KEY_SAVED = "downloaded_tracks"
    private const val KEY_SAVED_METADATA = "downloaded_tracks_metadata"
    private const val DEFAULT_MAX_CONCURRENT = 3

    const val BROWSE_ID_DOWNLOADS = "local:downloads"
    const val WIFI_ONLY_REFUSAL = "Downloads are set to Wi-Fi only"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val lock = Any()

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var downloadsDir: File? = null

    @Volatile
    private var artworkDir: File? = null

    @Volatile
    private var streamFetcher: Downloader.StreamFetcher = Downloader.DefaultStreamFetcher

    @Volatile
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var maxConcurrent: Int = DEFAULT_MAX_CONCURRENT

    @Volatile
    private var fetchArtworkOnline: Boolean = true

    private val artworkLock = Any()

    fun interface ArtworkFetcher {
        fun fetch(url: String): ByteArray?
    }

    val DefaultArtworkFetcher = ArtworkFetcher { url ->
        runCatching {
            val req = Request.Builder().url(url).build()
            Http.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.bytes()?.takeIf { it.isNotEmpty() }
                } else null
            }
        }.getOrNull()
    }

    @Volatile
    var artworkFetcher: ArtworkFetcher = DefaultArtworkFetcher

    private data class QueuedDownload(
        val song: Song,
        val from: SavedCollection?
    )

    private val queue = ConcurrentLinkedQueue<QueuedDownload>()
    private val pendingIds = ConcurrentHashMap.newKeySet<String>()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val activePends = ConcurrentHashMap<String, DownloadStore.Pending>()
    private val failedSongs = ConcurrentHashMap<String, Song>()
    private val inFlightLyrics = ConcurrentHashMap<String, Job>()
    private val pendingLyricsIds = ConcurrentHashMap.newKeySet<String>()
    private val pendingArtworkIds = ConcurrentHashMap.newKeySet<String>()

    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    private val _saved = MutableStateFlow<Map<String, String>>(emptyMap())
    val saved: StateFlow<Map<String, String>> = _saved.asStateFlow()

    private val _savedMetadata = MutableStateFlow<Map<String, SavedSongMetadata>>(emptyMap())
    val savedMetadata: StateFlow<Map<String, SavedSongMetadata>> = _savedMetadata.asStateFlow()

    private val _downloadedSongs = MutableStateFlow<List<Song>>(emptyList())
    val downloadedSongs: StateFlow<List<Song>> = _downloadedSongs.asStateFlow()

    /**
     * Initializes the download engine with the Android application/service [Context].
     * Safe to call multiple times; idempotent once initialized.
     */
    fun init(context: Context) {
        synchronized(lock) {
            val appCtx = context.applicationContext ?: context
            AppSettings.init(appCtx)
            if (prefs != null && downloadsDir != null) return
            val sharedPrefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val dlDir = DownloadStore.downloadsDir(appCtx)
            val artDir = DownloadStore.artworkDir(appCtx)
            prefs = sharedPrefs
            downloadsDir = dlDir
            artworkDir = artDir
            maxConcurrent = AppSettings.maxConcurrentDownloads.value
            streamFetcher = Downloader.DefaultStreamFetcher
            fetchArtworkOnline = true
            DownloadStore.cleanupOrphanedPartFiles(dlDir)
            DownloadStore.cleanupOrphanedPartFiles(artDir)
            loadAndVerifyPersistedLocked(sharedPrefs)
        }
    }

    /**
     * Initializes the download engine for deterministic unit/integration tests.
     */
    fun initForTest(
        sharedPreferences: SharedPreferences,
        baseDir: File,
        fetcher: Downloader.StreamFetcher = Downloader.DefaultStreamFetcher,
        testScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        maxConcurrentWorkers: Int = DEFAULT_MAX_CONCURRENT,
        enableOnlineArtworkFetch: Boolean = false,
        artFetcher: ArtworkFetcher = DefaultArtworkFetcher
    ) {
        synchronized(lock) {
            cancelAllInternalLocked()
            inFlightLyrics.clear()
            pendingLyricsIds.clear()
            pendingArtworkIds.clear()
            val dlDir = DownloadStore.downloadsDir(baseDir)
            val artDir = DownloadStore.artworkDir(baseDir)
            prefs = sharedPreferences
            downloadsDir = dlDir
            artworkDir = artDir
            streamFetcher = fetcher
            artworkFetcher = artFetcher
            scope = testScope
            maxConcurrent = maxConcurrentWorkers.coerceAtLeast(1)
            fetchArtworkOnline = enableOnlineArtworkFetch
            DownloadStore.cleanupOrphanedPartFiles(dlDir)
            DownloadStore.cleanupOrphanedPartFiles(artDir)
            loadAndVerifyPersistedLocked(sharedPreferences)
        }
    }

    /**
     * Simulates an application restart by clearing in-memory state without touching
     * `SharedPreferences` or files on disk.
     */
    fun detachForRestartSimulation() {
        synchronized(lock) {
            cancelAllInternalLocked()
            inFlightLyrics.clear()
            pendingArtworkIds.clear()
            prefs = null
            downloadsDir = null
            artworkDir = null
            _active.value = emptyMap()
            _saved.value = emptyMap()
            _savedMetadata.value = emptyMap()
            _downloadedSongs.value = emptyList()
            DownloadSession.clear()
        }
    }

    /**
     * Resets all in-memory state, persisted preferences, and downloaded files for clean test isolation.
     */
    fun resetForTests() {
        synchronized(lock) {
            cancelAllInternalLocked()
            inFlightLyrics.clear()
            pendingLyricsIds.clear()
            pendingArtworkIds.clear()
            artworkFetcher = DefaultArtworkFetcher
            prefs?.edit()?.remove(KEY_SAVED)?.remove(KEY_SAVED_METADATA)?.commit()
            downloadsDir?.listFiles()?.forEach { runCatching { it.delete() } }
            artworkDir?.listFiles()?.forEach { runCatching { it.delete() } }
            _active.value = emptyMap()
            _saved.value = emptyMap()
            _savedMetadata.value = emptyMap()
            _downloadedSongs.value = emptyList()
            DownloadSession.clear()
        }
    }

    private fun loadAndVerifyPersistedLocked(sharedPreferences: SharedPreferences) {
        val loadedSaved = LinkedHashMap<String, String>()
        val loadedMeta = LinkedHashMap<String, SavedSongMetadata>()

        val rawSaved = sharedPreferences.getString(KEY_SAVED, null)
        if (!rawSaved.isNullOrBlank()) {
            runCatching {
                val map = json.decodeFromString<Map<String, String>>(rawSaved)
                loadedSaved.putAll(map)
            }.onFailure {
                // Backwards compatibility with legacy line-delimited format if ever present
                for (line in rawSaved.lineSequence()) {
                    val tab = line.indexOf('\t')
                    if (tab > 0 && tab < line.length - 1) {
                        loadedSaved[line.substring(0, tab)] = line.substring(tab + 1)
                    }
                }
            }
        }

        val rawMeta = sharedPreferences.getString(KEY_SAVED_METADATA, null)
        if (!rawMeta.isNullOrBlank()) {
            runCatching {
                val list = json.decodeFromString<List<SavedSongMetadata>>(rawMeta)
                for (item in list) {
                    if (item.videoId.isNotBlank() && item.uri.isNotBlank()) {
                        loadedMeta[item.videoId] = item
                        if (item.videoId !in loadedSaved) {
                            loadedSaved[item.videoId] = item.uri
                        }
                    }
                }
            }
        }

        // Verify physical file existence and non-zero size on startup
        var prunedAny = false
        val verifiedSaved = LinkedHashMap<String, String>()
        val verifiedMeta = LinkedHashMap<String, SavedSongMetadata>()

        for ((videoId, uriStr) in loadedSaved) {
            val verifiedFile = DownloadStore.verifyLocalFile(uriStr)
            if (verifiedFile != null) {
                val canonicalUri = DownloadStore.fileToUriString(verifiedFile)
                verifiedSaved[videoId] = canonicalUri
                val existingMeta = loadedMeta[videoId]
                val verifiedArtUri = existingMeta?.localArtworkUri?.takeIf {
                    DownloadStore.verifyLocalFile(it) != null
                }
                val existingThumb = existingMeta?.thumbnailUrl
                if (verifiedArtUri == null && !existingThumb.isNullOrBlank() && !existingThumb.startsWith("file:")) {
                    pendingArtworkIds.add(videoId)
                }
                if (!LyricsStore.has(null, videoId)) {
                    val hasSidecar = verifiedFile.parentFile?.let { p ->
                        File(p, "${verifiedFile.nameWithoutExtension}.lrc").exists()
                    } ?: false
                    if (!hasSidecar) {
                        pendingLyricsIds.add(videoId)
                    }
                }
                verifiedMeta[videoId] = (existingMeta ?: SavedSongMetadata(
                    videoId = videoId,
                    title = verifiedFile.nameWithoutExtension,
                    artist = "",
                    uri = canonicalUri
                )).copy(
                    uri = canonicalUri,
                    localArtworkUri = verifiedArtUri,
                    fileSizeBytes = existingMeta?.fileSizeBytes ?: verifiedFile.length()
                )
            } else {
                prunedAny = true
            }
        }

        _saved.value = verifiedSaved
        _savedMetadata.value = verifiedMeta
        rebuildDownloadedSongsFlow(verifiedSaved, verifiedMeta)

        if (prunedAny) {
            persistLocked()
        }
    }

    private fun persistLocked() {
        val p = prefs ?: return
        val savedJson = runCatching { json.encodeToString(_saved.value) }.getOrDefault("{}")
        val metaJson = runCatching {
            json.encodeToString(_savedMetadata.value.values.toList())
        }.getOrDefault("[]")
        p.edit()
            .putString(KEY_SAVED, savedJson)
            .putString(KEY_SAVED_METADATA, metaJson)
            .commit()
    }

    private fun rebuildDownloadedSongsFlow(
        savedMap: Map<String, String> = _saved.value,
        metaMap: Map<String, SavedSongMetadata> = _savedMetadata.value
    ) {
        val list = savedMap.entries.mapNotNull { (videoId, uriStr) ->
            val meta = metaMap[videoId]
            meta?.toSong(verifiedLocalUri = uriStr) ?: Song(
                videoId = videoId,
                title = videoId,
                artist = "",
                thumbnailUrl = null,
                localUri = uriStr
            )
        }.sortedByDescending { it.localDateAddedSeconds ?: 0L }
        _downloadedSongs.value = list
    }

    /**
     * Returns the verified `file://` URI string for [videoId] if and only if the track
     * is marked as downloaded AND its local file exists on disk with `length() > 0L`.
     *
     * If the file was deleted externally or corrupted (0 bytes), automatically prunes the
     * stale record so online fallback can take over cleanly.
     */
    fun verifiedSavedUriString(videoId: String): String? {
        if (videoId.isBlank()) return null
        val uriStr = _saved.value[videoId] ?: return null
        val verifiedFile = DownloadStore.verifyLocalFile(uriStr)
        if (verifiedFile != null) {
            return DownloadStore.fileToUriString(verifiedFile)
        }
        // Stale entry detected (file missing or 0 bytes on disk) -> prune immediately
        synchronized(lock) {
            if (_saved.value.containsKey(videoId)) {
                _saved.update { it - videoId }
                _savedMetadata.update { it - videoId }
                rebuildDownloadedSongsFlow()
                persistLocked()
            }
        }
        return null
    }

    /**
     * Checks whether [uriString] points to a valid, non-empty local file on disk.
     */
    fun isValidLocalUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        if (uriString.startsWith("file:", ignoreCase = true)) {
            return DownloadStore.verifyLocalFile(uriString) != null
        }
        if (uriString.startsWith("content://") || uriString.startsWith("asset://")) {
            return true
        }
        val direct = File(uriString)
        return direct.exists() && direct.isFile && direct.length() > 0L
    }

    /**
     * Returns true if [videoId] is downloaded and verified on disk.
     */
    fun isDownloaded(videoId: String): Boolean = verifiedSavedUriString(videoId) != null

    /**
     * Returns the current active [DownloadState] for [videoId], or null if not queued/running/failed.
     */
    fun downloadStateFor(videoId: String): DownloadState? = _active.value[videoId]

    /**
     * Returns all verified downloaded songs sorted newest first.
     */
    fun getDownloadedSongs(): List<Song> {
        val currentSaved = _saved.value.keys.toList()
        for (videoId in currentSaved) {
            verifiedSavedUriString(videoId)
        }
        return _downloadedSongs.value
    }

    /**
     * Enriches [song] with its verified local file URI and offline artwork sidecar if downloaded.
     * If the song had a stale `localUri` whose file no longer exists, clears `localUri` so online
     * stream resolution is used as fallback.
     */
    fun enrichWithLocalDownload(song: Song): Song {
        val existingValidLocal = song.localUri?.takeIf { isValidLocalUri(it) }
        val verifiedSaved = verifiedSavedUriString(song.videoId)
        val effectiveLocalUri = existingValidLocal ?: verifiedSaved
        val meta = _savedMetadata.value[song.videoId]

        return if (effectiveLocalUri != null) {
            val localArt = meta?.localArtworkUri?.takeIf { DownloadStore.verifyLocalFile(it) != null }
            song.copy(
                title = song.title.ifBlank { meta?.title ?: song.title },
                artist = song.artist.ifBlank { meta?.artist ?: song.artist },
                albumName = song.albumName ?: meta?.albumName,
                durationText = song.durationText ?: meta?.durationText,
                thumbnailUrl = localArt ?: song.thumbnailUrl ?: meta?.thumbnailUrl,
                localUri = effectiveLocalUri,
                downloadFormat = song.downloadFormat ?: meta?.downloadFormat,
                localDateAddedSeconds = song.localDateAddedSeconds ?: meta?.dateAddedSeconds
            )
        } else if (song.localUri != null) {
            // Had a localUri that failed verification on disk -> clear it so online resolver runs
            song.copy(localUri = null)
        } else {
            song
        }
    }

    /**
     * Enqueues a single song for download. Prevents duplicate downloads of the same `videoId`.
     */
    fun enqueue(context: Context?, song: Song, from: SavedCollection? = null) {
        if (context != null) {
            init(context)
        }
        enqueue(song, from)
    }

    fun enqueue(song: Song, from: SavedCollection? = null) {
        val videoId = song.videoId.trim()
        if (videoId.isBlank()) return

        if (!AppSettings.downloadsAllowedNow) {
            synchronized(lock) {
                if (verifiedSavedUriString(videoId) != null) return
                val inFlight = _active.value[videoId]
                if (inFlight !is DownloadState.Queued && inFlight !is DownloadState.Running) {
                    failedSongs[videoId] = song
                    _active.update { it + (videoId to DownloadState.Failed(WIFI_ONLY_REFUSAL)) }
                    DownloadSession.failed(videoId, WIFI_ONLY_REFUSAL)
                }
            }
            return
        }

        synchronized(lock) {
            // Prevent duplicate download if already verified on disk or currently queued/running
            if (verifiedSavedUriString(videoId) != null) {
                return
            }
            if (videoId in pendingIds || runningJobs.containsKey(videoId)) {
                return
            }
            failedSongs[videoId] = song
            pendingIds.add(videoId)
            queue.add(QueuedDownload(song = song, from = from))
            _active.update { it + (videoId to DownloadState.Queued) }
            DownloadSession.enqueue(song)
            pumpQueueLocked()
        }
    }

    /**
     * Enqueues multiple songs for batch download (e.g. an entire playlist or album).
     */
    fun enqueueAll(context: Context?, songs: List<Song>, from: SavedCollection? = null) {
        if (context != null) {
            init(context)
        }
        enqueueAll(songs, from)
    }

    fun enqueueAll(songs: List<Song>, from: SavedCollection? = null) {
        if (songs.isEmpty()) return
        for (song in songs) {
            enqueue(song, from)
        }
    }

    /**
     * Retries a failed download for [videoId] (or using [fallbackSong] if provided).
     */
    fun retry(context: Context?, videoId: String, fallbackSong: Song? = null) {
        if (context != null) {
            init(context)
        }
        retry(videoId, fallbackSong)
    }

    fun retry(videoId: String, fallbackSong: Song? = null) {
        val songToRetry = synchronized(lock) {
            val current = _active.value[videoId]
            if (current is DownloadState.Queued || current is DownloadState.Running) {
                return
            }
            _active.update { it - videoId }
            failedSongs[videoId] ?: fallbackSong
        } ?: return

        enqueue(songToRetry)
    }

    /**
     * Cancels an active or queued download for [videoId], deleting any temporary `.part` file.
     */
    fun cancel(videoId: String) {
        synchronized(lock) {
            pendingIds.remove(videoId)
            queue.removeIf { it.song.videoId == videoId }
            activePends.remove(videoId)?.abort()
            runningJobs.remove(videoId)?.cancel()
            inFlightLyrics.remove(videoId)?.cancel()
            pendingLyricsIds.remove(videoId)
            pendingArtworkIds.remove(videoId)
            _active.update { it - videoId }
            DownloadSession.cancel(videoId)
            pumpQueueLocked()
        }
    }

    /**
     * Cancels all active and queued downloads.
     */
    fun cancelAll() {
        synchronized(lock) {
            cancelAllInternalLocked()
        }
    }

    private fun cancelAllInternalLocked() {
        queue.clear()
        pendingIds.clear()
        for ((_, pending) in activePends) {
            pending.abort()
        }
        activePends.clear()
        for ((_, job) in runningJobs) {
            job.cancel()
        }
        runningJobs.clear()
        for ((_, job) in inFlightLyrics) {
            job.cancel()
        }
        inFlightLyrics.clear()
        pendingLyricsIds.clear()
        pendingArtworkIds.clear()
        _active.value = emptyMap()
        DownloadSession.clear()
    }

    /**
     * Deletes a completed download (both the physical file on disk and the persisted record)
     * without affecting playlists or favorites in `LocalPlaylistStore`.
     */
    fun delete(context: Context?, videoId: String) {
        if (context != null) {
            init(context)
        }
        delete(videoId)
    }

    fun delete(videoId: String) {
        if (videoId.isBlank()) return
        synchronized(lock) {
            pendingIds.remove(videoId)
            queue.removeIf { it.song.videoId == videoId }
            activePends.remove(videoId)?.abort()
            runningJobs.remove(videoId)?.cancel()
            inFlightLyrics.remove(videoId)?.cancel()
            pendingLyricsIds.remove(videoId)
            pendingArtworkIds.remove(videoId)
            _active.update { it - videoId }
            failedSongs.remove(videoId)

            val uriStr = _saved.value[videoId]
            val meta = _savedMetadata.value[videoId]
            DownloadStore.deleteFile(uriStr)

            // Unshared Artwork Deletion Cleanup:
            // Delete physical artwork file ONLY if no other downloaded song references it
            val artUri = meta?.localArtworkUri
            if (!artUri.isNullOrBlank()) {
                val isShared = _savedMetadata.value.values.any { other ->
                    other.videoId != videoId && other.localArtworkUri == artUri
                }
                if (!isShared) {
                    DownloadStore.deleteFile(artUri)
                }
            }

            LyricsRepository.deleteSidecarForLocalUri(uriStr)
            LyricsStore.delete(null, videoId)

            _saved.update { it - videoId }
            _savedMetadata.update { it - videoId }
            rebuildDownloadedSongsFlow()
            persistLocked()
        }
    }

    /**
     * Checks if [videoId] has lyrics acquisition pending (e.g. download happened while offline).
     */
    fun hasPendingLyrics(videoId: String): Boolean = pendingLyricsIds.contains(videoId)

    /**
     * Returns total count of tracks whose lyrics could not be acquired or are pending.
     */
    fun getPendingLyricsCount(): Int = pendingLyricsIds.size

    /**
     * Returns all videoIds currently waiting for lyrics acquisition.
     */
    fun getPendingLyricsIds(): Set<String> = pendingLyricsIds.toSet()

    /**
     * Clears pending lyrics tracking set.
     */
    fun clearPendingLyrics() {
        pendingLyricsIds.clear()
    }

    /**
     * Retries lyrics acquisition for all pending downloaded songs when connectivity is restored.
     * Returns the dispatched background acquisition [Job]s.
     */
    fun retryPendingLyrics(context: Context? = null): List<Job> {
        if (context != null) {
            init(context)
        }
        if (LyricsRepository.isOffline(context)) {
            TrackLog.d(TAG, "retryPendingLyrics skipped: currently offline")
            return emptyList()
        }
        val idsToRetry = pendingLyricsIds.toSet()
        if (idsToRetry.isEmpty()) return emptyList()

        val jobs = mutableListOf<Job>()
        for (videoId in idsToRetry) {
            val meta = _savedMetadata.value[videoId]
            val song = if (meta != null) {
                val effectiveUri = verifiedSavedUriString(videoId) ?: meta.uri
                meta.toSong(effectiveUri)
            } else {
                _downloadedSongs.value.find { it.videoId == videoId }
            }
            if (song != null) {
                jobs.add(triggerLyricsAcquisition(song, song.localUri ?: verifiedSavedUriString(videoId)))
            }
        }
        return jobs
    }

    /**
     * Checks if [videoId] has artwork acquisition pending (e.g. download happened while offline).
     */
    fun hasPendingArtwork(videoId: String): Boolean = pendingArtworkIds.contains(videoId)

    /**
     * Returns total count of tracks whose artwork could not be acquired or is pending.
     */
    fun getPendingArtworkCount(): Int = pendingArtworkIds.size

    /**
     * Returns all videoIds currently waiting for artwork acquisition.
     */
    fun getPendingArtworkIds(): Set<String> = pendingArtworkIds.toSet()

    /**
     * Clears pending artwork tracking set.
     */
    fun clearPendingArtwork() {
        pendingArtworkIds.clear()
    }

    /**
     * Retries artwork acquisition for all pending downloaded songs when connectivity is restored.
     * Returns the dispatched background acquisition [Job]s.
     */
    fun retryPendingArtwork(context: Context? = null): List<Job> {
        if (context != null) {
            init(context)
        }
        if (LyricsRepository.isOffline(context)) {
            TrackLog.d(TAG, "retryPendingArtwork skipped: currently offline")
            return emptyList()
        }
        val idsToRetry = pendingArtworkIds.toSet()
        if (idsToRetry.isEmpty()) return emptyList()

        val jobs = mutableListOf<Job>()
        for (videoId in idsToRetry) {
            val meta = _savedMetadata.value[videoId]
            val song = if (meta != null) {
                val effectiveUri = verifiedSavedUriString(videoId) ?: meta.uri
                meta.toSong(effectiveUri)
            } else {
                _downloadedSongs.value.find { it.videoId == videoId }
            }
            val thumbUrl = meta?.thumbnailUrl ?: song?.thumbnailUrl
            if (song != null && !thumbUrl.isNullOrBlank()) {
                val job = scope.launch {
                    val artUri = cacheArtworkSidecar(thumbUrl, context)
                    if (artUri != null) {
                        pendingArtworkIds.remove(videoId)
                        synchronized(lock) {
                            val currentMeta = _savedMetadata.value[videoId]
                            if (currentMeta != null && currentMeta.localArtworkUri != artUri) {
                                val updatedMeta = currentMeta.copy(localArtworkUri = artUri)
                                _savedMetadata.update { it + (videoId to updatedMeta) }
                                rebuildDownloadedSongsFlow()
                                persistLocked()
                            }
                        }
                    }
                }
                jobs.add(job)
            }
        }
        return jobs
    }

    fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    internal fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false

        // 1. JPEG: Starts with SOI (0xFF, 0xD8, 0xFF), ends with EOI (0xFF, 0xD9)
        val isJpeg = isJpegValid(bytes)

        // 2. PNG: Starts with 8-byte signature, ends with IEND chunk (49 45 4E 44 AE 42 60 82)
        val isPng = isPngValid(bytes)

        // 3. WebP: Starts with RIFF...WEBP...VP8, length matches or exceeds declared RIFF payload
        val isWebp = isWebpValid(bytes)

        // 4. GIF: Starts with "GIF87a" or "GIF89a", ends with trailer byte 0x3B (';')
        val isGif = isGifValid(bytes)

        if (!isJpeg && !isPng && !isWebp && !isGif) {
            return false
        }

        if (Build.VERSION.SDK_INT > 0) {
            return runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.outWidth > 0 && options.outHeight > 0
            }.getOrDefault(false)
        }

        return true
    }

    private fun isJpegValid(bytes: ByteArray): Boolean {
        if (bytes.size < 16) return false
        val hasSoi = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        if (!hasSoi) return false

        // Direct check: last 2 bytes are FF D9
        if (bytes[bytes.size - 2] == 0xFF.toByte() && bytes[bytes.size - 1] == 0xD9.toByte()) {
            return true
        }

        // Scan last 34 bytes allowing trailing whitespace/null padding
        val scanLimit = maxOf(0, bytes.size - 34)
        for (i in bytes.size - 2 downTo scanLimit) {
            if (bytes[i] == 0xFF.toByte() && bytes[i + 1] == 0xD9.toByte()) {
                var onlyPadding = true
                for (j in (i + 2) until bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    if (b != 0x00 && b != 0x20 && b != 0x0A && b != 0x0D) {
                        onlyPadding = false
                        break
                    }
                }
                if (onlyPadding) return true
            }
        }
        return false
    }

    private fun isPngValid(bytes: ByteArray): Boolean {
        if (bytes.size < 24) return false
        val isHeader = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
                bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
                bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()
        if (!isHeader) return false

        val iendCrc = byteArrayOf(
            0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(),
            0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte()
        )
        if (bytes.copyOfRange(bytes.size - 8, bytes.size).contentEquals(iendCrc)) {
            return true
        }

        val scanLimit = maxOf(8, bytes.size - 64)
        for (i in bytes.size - 8 downTo scanLimit) {
            var match = true
            for (k in 0 until 8) {
                if (bytes[i + k] != iendCrc[k]) {
                    match = false
                    break
                }
            }
            if (match) {
                var onlyPadding = true
                for (j in (i + 8) until bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    if (b != 0x00 && b != 0x20 && b != 0x0A && b != 0x0D) {
                        onlyPadding = false
                        break
                    }
                }
                if (onlyPadding) return true
            }
        }
        return false
    }

    private fun isWebpValid(bytes: ByteArray): Boolean {
        if (bytes.size < 20) return false
        val isRiff = bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        val isWebp = bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        if (!isRiff || !isWebp) return false

        val isVp8 = bytes[12] == 0x56.toByte() && bytes[13] == 0x50.toByte() && bytes[14] == 0x38.toByte()
        if (!isVp8) return false

        val riffSize = (bytes[4].toLong() and 0xFF) or
                ((bytes[5].toLong() and 0xFF) shl 8) or
                ((bytes[6].toLong() and 0xFF) shl 16) or
                ((bytes[7].toLong() and 0xFF) shl 24)
        if (riffSize <= 0 || bytes.size < (riffSize + 7)) return false
        return true
    }

    private fun isGifValid(bytes: ByteArray): Boolean {
        if (bytes.size < 14) return false
        val isGifHeader = bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x38.toByte() && (bytes[4] == 0x37.toByte() || bytes[4] == 0x39.toByte()) &&
                bytes[5] == 0x61.toByte()
        if (!isGifHeader) return false

        if (bytes[bytes.size - 1] == 0x3B.toByte()) return true
        val scanLimit = maxOf(6, bytes.size - 16)
        for (i in bytes.size - 1 downTo scanLimit) {
            if (bytes[i] == 0x3B.toByte()) {
                var onlyPadding = true
                for (j in (i + 1) until bytes.size) {
                    val b = bytes[j].toInt() and 0xFF
                    if (b != 0x00 && b != 0x20 && b != 0x0A && b != 0x0D) {
                        onlyPadding = false
                        break
                    }
                }
                if (onlyPadding) return true
            }
        }
        return false
    }

    fun getArtworkFile(thumbnailUrl: String): File? {
        val artDir = artworkDir ?: return null
        val artHash = sha256Hex(thumbnailUrl.trim())
        return File(artDir, "art_$artHash.jpg")
    }

    fun getCachedArtworkUri(thumbnailUrl: String?): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        if (thumbnailUrl.startsWith("file:")) {
            return if (DownloadStore.verifyLocalFile(thumbnailUrl) != null) thumbnailUrl else null
        }
        val target = getArtworkFile(thumbnailUrl) ?: return null
        return if (target.exists() && target.length() > 0L) {
            DownloadStore.fileToUriString(target)
        } else {
            null
        }
    }

    /**
     * Triggers asynchronous background acquisition and persistence of lyrics for [song].
     * Deduplicates in-flight requests for the same [videoId].
     */
    fun triggerLyricsAcquisition(song: Song, targetLocalUri: String? = null): Job {
        val videoId = song.videoId.trim()
        return synchronized(lock) {
            val existing = inFlightLyrics[videoId]
            if (existing != null && existing.isActive) {
                return@synchronized existing
            }
            val job = scope.launch {
                try {
                    acquireLyricsForSong(song, targetLocalUri)
                } finally {
                    inFlightLyrics.remove(videoId)
                }
            }
            inFlightLyrics[videoId] = job
            job
        }
    }

    /**
     * Acquires and persists lyrics for [song]:
     * 1. Resolves via [LyricsRepository.resolveForSong], which handles embedded files, .lrc sidecars,
     *    in-memory cache, and [LyricsStore] with 0 network calls when available offline.
     * 2. If online, races BitChord providers, saves to [LyricsStore], in-memory cache, and creates
     *    a contiguous `.lrc` sidecar next to the downloaded audio file.
     * 3. If offline or experiencing transient provider errors, marks [videoId] in [pendingLyricsIds]
     *    without failing the audio download.
     */
    suspend fun acquireLyricsForSong(song: Song, targetLocalUri: String? = null): Boolean {
        val videoId = song.videoId.trim()
        if (videoId.isBlank()) return false

        val effectiveUri = targetLocalUri ?: verifiedSavedUriString(videoId)
        val songToResolve = if (effectiveUri != null && song.localUri != effectiveUri) {
            song.copy(localUri = effectiveUri)
        } else {
            song
        }

        return try {
            val state = LyricsRepository.resolveForSong(songToResolve, null)
            when (state) {
                is LyricsUiState.Synced,
                is LyricsUiState.Plain,
                is LyricsUiState.EmbeddedLocal -> {
                    pendingLyricsIds.remove(videoId)
                    val uriForSidecar = effectiveUri ?: verifiedSavedUriString(videoId)
                    if (uriForSidecar != null) {
                        val lines = when (state) {
                            is LyricsUiState.Synced -> state.lines
                            is LyricsUiState.Plain -> state.lines
                            is LyricsUiState.EmbeddedLocal -> state.lines
                        }
                        if (lines.isNotEmpty()) {
                            LyricsRepository.persistSidecarForLocalUri(uriForSidecar, lines)
                        }
                    }
                    TrackLog.d(TAG, "Successfully acquired and persisted lyrics for $videoId (${song.title})")
                    true
                }
                is LyricsUiState.OfflineUnavailable,
                is LyricsUiState.Error -> {
                    TrackLog.w(TAG, "Lyrics acquisition for $videoId (${song.title}) pending/failed: $state")
                    pendingLyricsIds.add(videoId)
                    false
                }
                is LyricsUiState.NotFound -> {
                    TrackLog.d(TAG, "No lyrics found from providers for $videoId (${song.title})")
                    pendingLyricsIds.remove(videoId)
                    false
                }
                LyricsUiState.Idle,
                is LyricsUiState.Loading -> false
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            TrackLog.w(TAG, "Unexpected error acquiring lyrics for $videoId: ${t.message}")
            pendingLyricsIds.add(videoId)
            false
        }
    }

    private fun pumpQueueLocked() {
        while (runningJobs.size < maxConcurrent) {
            val next = queue.poll() ?: break
            val song = next.song
            val videoId = song.videoId
            if (!pendingIds.remove(videoId)) {
                continue
            }
            val parentJob = SupervisorJob(scope.coroutineContext[Job])
            runningJobs[videoId] = parentJob
            val job = scope.launch(parentJob as kotlin.coroutines.CoroutineContext) {
                runDownloadWorker(song)
            }
            if (runningJobs[videoId] === parentJob) {
                runningJobs[videoId] = job
            }
        }
    }

    private suspend fun runDownloadWorker(song: Song) {
        val videoId = song.videoId
        val dir = downloadsDir ?: throw IllegalStateException("Downloads not initialized")
        var pendingFile: DownloadStore.Pending? = null
        var committedUri: String? = null

        // Trigger asynchronous lyrics acquisition in parallel with audio stream download
        val lyricsJob = triggerLyricsAcquisition(song, null)

        try {
            _active.update { it + (videoId to DownloadState.Running(0f, 0L, 0L)) }
            DownloadSession.running(videoId, 0f, 0L, 0L)

            var lastReportedPercent = -1
            var targetFileName = ""

            val quality = AppSettings.downloadQuality.value
            val requireM4a = AppSettings.exportDownloads.value

            val stream = streamFetcher.resolveAndFetch(
                videoId = videoId,
                maxKbps = quality.maxKbps,
                requireM4a = requireM4a,
                openSink = { resolvedStream ->
                    val ext = resolvedStream.downloadExtension
                    targetFileName = buildFileName(song, ext)
                    val p = DownloadStore.begin(dir, targetFileName)
                    pendingFile = p
                    activePends[videoId] = p
                    p.openStream()
                },
                onProgress = { written, total ->
                    val fraction = if (total > 0L) {
                        (written.toFloat() / total.toFloat()).coerceIn(0f, 0.99f)
                    } else {
                        0f
                    }
                    val pct = (fraction * 100f).toInt()
                    if (pct != lastReportedPercent || written == total) {
                        lastReportedPercent = pct
                        _active.update {
                            it + (videoId to DownloadState.Running(
                                fraction = fraction,
                                bytesWritten = written,
                                totalBytes = total
                            ))
                        }
                        DownloadSession.running(
                            videoId = videoId,
                            fraction = fraction,
                            bytesWritten = written,
                            totalBytes = total
                        )
                    }
                }
            )

            val p = pendingFile ?: throw IllegalStateException("Download sink was not opened for $videoId")
            val uri = p.commit()
            committedUri = uri
            activePends.remove(videoId)

            val committedFile = DownloadStore.verifyLocalFile(uri)
                ?: throw IllegalStateException("Committed file failed verification for $videoId")

            // Check cancellation after audio stream commit
            if (!currentCoroutineContext().isActive || synchronized(lock) { !runningJobs.containsKey(videoId) }) {
                DownloadStore.deleteFile(uri)
                throw CancellationException("Download of $videoId was cancelled after audio commit")
            }

            val localArtworkUri = if (fetchArtworkOnline) {
                cacheArtworkSidecar(song.thumbnailUrl)
            } else {
                getCachedArtworkUri(song.thumbnailUrl)
            }

            // Check cancellation immediately after artwork fetch
            if (!currentCoroutineContext().isActive || synchronized(lock) { !runningJobs.containsKey(videoId) }) {
                DownloadStore.deleteFile(uri)
                throw CancellationException("Download of $videoId was cancelled during or after artwork fetch")
            }

            val songThumb = song.thumbnailUrl
            if (!songThumb.isNullOrBlank() && !songThumb.startsWith("file:")) {
                if (localArtworkUri == null) {
                    pendingArtworkIds.add(videoId)
                } else {
                    pendingArtworkIds.remove(videoId)
                }
            }

            val formatLabel = buildFormatLabel(stream.downloadExtension, stream.kbps)
            val nowSec = System.currentTimeMillis() / 1000L
            val metadata = SavedSongMetadata(
                videoId = videoId,
                title = song.title,
                artist = song.artist,
                thumbnailUrl = song.thumbnailUrl,
                localArtworkUri = localArtworkUri,
                durationText = song.durationText,
                albumName = song.albumName,
                uri = uri,
                downloadFormat = formatLabel,
                fileSizeBytes = committedFile.length(),
                dateAddedSeconds = nowSec
            )

            // Await lyrics acquisition without blocking audio indefinitely (up to 2500ms max)
            // Re-throw CancellationException so cancelled coroutines are never swallowed
            try {
                withTimeoutOrNull(2500L) {
                    lyricsJob.join()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                TrackLog.w(TAG, "Lyrics acquisition join non-fatal exception for $videoId: ${t.message}")
            }

            // Check cancellation before writing sidecar .lrc and entering synchronized commit
            if (!currentCoroutineContext().isActive || synchronized(lock) { !runningJobs.containsKey(videoId) }) {
                DownloadStore.deleteFile(uri)
                throw CancellationException("Download of $videoId was cancelled before commit")
            }

            // Persist sidecar .lrc if lyrics are available
            val finalLyrics = LyricsStore.getLines(null, videoId)
                ?: LyricsRepository.getMemoryCachedLines(videoId)
            if (finalLyrics != null && finalLyrics.isNotEmpty()) {
                LyricsRepository.persistSidecarForLocalUri(uri, finalLyrics)
            }

            val isJobActive = currentCoroutineContext().isActive
            synchronized(lock) {
                if (!runningJobs.containsKey(videoId) || !isJobActive) {
                    DownloadStore.deleteFile(uri)
                    pendingArtworkIds.remove(videoId)
                    pendingLyricsIds.remove(videoId)
                    _active.update { it - videoId }
                    pumpQueueLocked()
                    return
                }
                runningJobs.remove(videoId)
                failedSongs.remove(videoId)
                _active.update { it - videoId }
                _saved.update { it + (videoId to uri) }
                _savedMetadata.update { it + (videoId to metadata) }
                rebuildDownloadedSongsFlow()
                persistLocked()
                DownloadSession.done(videoId)
                pumpQueueLocked()
            }
        } catch (ce: CancellationException) {
            pendingFile?.abort()
            activePends.remove(videoId)
            committedUri?.let { DownloadStore.deleteFile(it) }
            synchronized(lock) {
                runningJobs.remove(videoId)
                pendingArtworkIds.remove(videoId)
                pendingLyricsIds.remove(videoId)
                if (videoId !in pendingIds) {
                    _active.update { it - videoId }
                    DownloadSession.cancel(videoId)
                }
                pumpQueueLocked()
            }
            throw ce
        } catch (t: Throwable) {
            TrackLog.w(TAG, "Download failed for $videoId (${song.title}): ${t.message}")
            pendingFile?.abort()
            activePends.remove(videoId)
            committedUri?.let { DownloadStore.deleteFile(it) }
            val reason = (t.message ?: t.javaClass.simpleName).ifBlank { "Error de red" }
            synchronized(lock) {
                runningJobs.remove(videoId)
                failedSongs[videoId] = song
                _active.update { it + (videoId to DownloadState.Failed(reason)) }
                DownloadSession.failed(videoId, reason)
                pumpQueueLocked()
            }
        }
    }

    fun cacheArtworkSidecar(thumbnailUrl: String?, context: Context? = null): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        if (thumbnailUrl.startsWith("file:")) {
            return if (DownloadStore.verifyLocalFile(thumbnailUrl) != null) thumbnailUrl else null
        }
        if (!thumbnailUrl.startsWith("http://", ignoreCase = true) && !thumbnailUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        if (context != null && artworkDir == null) {
            artworkDir = DownloadStore.artworkDir(context)
        }
        val artDir = artworkDir ?: return null

        val cleanUrl = thumbnailUrl.trim()
        val artHash = sha256Hex(cleanUrl)
        val target = File(artDir, "art_$artHash.jpg")

        // 1. Deduplication: if target artwork file exists and is non-empty, return local URI immediately without network download.
        // Multiple songs from the same album share the single artwork file.
        if (target.exists() && target.length() > 0L) {
            return DownloadStore.fileToUriString(target)
        }

        // 2. High-res artwork fetch (800px: cleanUrl.artworkAt(800) ?: cleanUrl)
        val highResUrl = cleanUrl.artworkAt(800) ?: cleanUrl
        val candidateUrls = if (highResUrl != cleanUrl) listOf(highResUrl, cleanUrl) else listOf(cleanUrl)
        var bytes: ByteArray? = null

        for (candUrl in candidateUrls) {
            val fetched = artworkFetcher.fetch(candUrl)
            if (fetched != null && fetched.isNotEmpty()) {
                bytes = fetched
                break
            }
        }

        if (bytes == null || bytes.isEmpty()) return null

        // 3. Atomicity & Readability: write via temporary .part file, validate image bytes before commit
        if (!isValidImageBytes(bytes)) {
            TrackLog.w(TAG, "Fetched artwork for $cleanUrl failed image validity check")
            return null
        }

        return synchronized(artworkLock) {
            if (target.exists() && target.length() > 0L) {
                return@synchronized DownloadStore.fileToUriString(target)
            }
            if (!artDir.exists() && !artDir.mkdirs()) {
                TrackLog.w(TAG, "Could not create artwork directory: ${artDir.absolutePath}")
                return@synchronized null
            }
            val partFile = File(artDir, ".art_$artHash-${System.nanoTime()}.part")
            try {
                FileOutputStream(partFile).use { it.write(bytes) }
                if (!partFile.renameTo(target) && !target.exists()) {
                    partFile.copyTo(target, overwrite = true)
                }
            } catch (t: Throwable) {
                TrackLog.w(TAG, "Error committing artwork file for $cleanUrl: ${t.message}")
            } finally {
                if (partFile.exists()) {
                    partFile.delete()
                }
            }
            if (target.exists() && target.length() > 0L) {
                DownloadStore.fileToUriString(target)
            } else {
                null
            }
        }
    }

    fun cacheArtworkSidecar(videoId: String, thumbnailUrl: String?): String? =
        cacheArtworkSidecar(thumbnailUrl)

    private fun buildFileName(song: Song, ext: String): String {
        val rawBase = if (song.artist.isNotBlank()) {
            "${song.artist} - ${song.title}"
        } else {
            song.title.ifBlank { song.videoId }
        }
        val sanitized = rawBase
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim()
            .take(96)
            .ifBlank { song.videoId }
        val safeId = song.videoId.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        return "${sanitized}_${safeId}.$ext"
    }

    private fun buildFormatLabel(ext: String, kbps: Int): String {
        val codec = if (ext.equals("webm", ignoreCase = true)) "OPUS" else "AAC"
        return if (kbps > 0) "$codec ${kbps}kbps" else codec
    }
}

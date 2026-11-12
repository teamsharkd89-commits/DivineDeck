package com.music.bitchord.data.settings

import android.content.Context
import android.os.StatFs
import com.music.bitchord.download.DownloadStore
import java.io.File

/**
 * Storage and cache inspector for BitChord in DIVINE DECK.
 *
 * Provides accurate byte counts and safe cache reclamation
 * without affecting downloaded audio tracks, playlists, or sidecar artwork.
 */
object BitChordStorageManager {

    /**
     * Total bytes occupied by verified offline downloaded tracks and artwork sidecars.
     */
    fun getDownloadsSizeBytes(context: Context): Long {
        val appCtx = context.applicationContext ?: context
        var total = 0L
        runCatching {
            val dlDir = DownloadStore.downloadsDir(appCtx)
            total += calculateDirectorySize(dlDir)
            val artDir = DownloadStore.artworkDir(appCtx)
            total += calculateDirectorySize(artDir)
        }
        return total
    }

    /**
     * Total bytes occupied by the transient streaming audio cache.
     */
    fun getAudioCacheSizeBytes(context: Context): Long {
        val appCtx = context.applicationContext ?: context
        val cacheDir = File(appCtx.cacheDir, "bitchord_audio")
        return calculateDirectorySize(cacheDir)
    }

    /**
     * Total bytes occupied by Coil / network image cache.
     */
    fun getImageCacheSizeBytes(context: Context): Long {
        val appCtx = context.applicationContext ?: context
        val imageDir = File(appCtx.cacheDir, "image_cache")
        return calculateDirectorySize(imageDir)
    }

    /**
     * Available free storage space on the internal data partition.
     */
    fun getAvailableStorageBytes(context: Context): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.path)
            stat.availableBlocksLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    /**
     * Total storage space on the internal data partition.
     */
    fun getTotalStorageBytes(context: Context): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.path)
            stat.blockCountLong * stat.blockSizeLong
        }.getOrDefault(0L)
    }

    /**
     * Safely evicts transient streaming audio cache spans.
     * Guaranteed never to touch downloaded offline songs in [DownloadStore.downloadsDir].
     */
    fun clearAudioCache(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        return runCatching {
            val cacheDir = File(appCtx.cacheDir, "bitchord_audio")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Safely evicts cached remote image files.
     * Offline cover sidecars in [DownloadStore.artworkDir] remain untouched.
     */
    fun clearImageCache(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        return runCatching {
            val imageDir = File(appCtx.cacheDir, "image_cache")
            if (imageDir.exists()) {
                imageDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Formats bytes into a human-readable string (e.g., "12.4 MB", "1.2 GB").
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun calculateDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }
}

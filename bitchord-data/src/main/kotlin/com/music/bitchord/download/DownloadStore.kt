package com.music.bitchord.download

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.URI

/**
 * Original BitChord 1.8.1 DownloadStore adapted for internal module integration.
 *
 * Writes every download to an atomic `.part` file (`Pending`) inside the app's
 * private `downloads` directory and only promotes it to the final target path
 * on `commit()` after verifying the file exists and is non-empty (`length() > 0L`).
 *
 * If a download is cancelled, interrupted, or fails, `abort()` deletes the `.part`
 * file immediately so partial/corrupted files are never exposed as completed downloads.
 */
object DownloadStore {

    private const val DOWNLOAD_SUBDIR = "downloads"
    private const val ARTWORK_SUBDIR = "download-artwork"

    class Pending internal constructor(
        val partFile: File,
        val targetFile: File
    ) {
        fun openStream(): OutputStream = FileOutputStream(partFile)

        /**
         * Promotes the temporary `.part` file to `targetFile` only if it is valid and non-empty.
         * Returns the canonical `file://` URI string for the committed file.
         */
        fun commit(): String {
            if (!partFile.exists() || partFile.length() <= 0L) {
                partFile.delete()
                throw IOException("Downloaded file is empty or missing: ${partFile.absolutePath}")
            }
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = partFile.renameTo(targetFile)
            if (!renamed) {
                partFile.inputStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                partFile.delete()
            }
            if (!targetFile.exists() || targetFile.length() <= 0L) {
                targetFile.delete()
                throw IOException("Failed to commit downloaded file: ${targetFile.absolutePath}")
            }
            return fileToUriString(targetFile)
        }

        /**
         * Deletes the temporary `.part` file when a download is cancelled or fails.
         */
        fun abort() {
            runCatching {
                if (partFile.exists()) {
                    partFile.delete()
                }
            }
        }
    }

    fun downloadsDir(context: Context): File {
        val dir = File(context.filesDir, DOWNLOAD_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun artworkDir(context: Context): File {
        val dir = File(context.filesDir, ARTWORK_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun downloadsDir(baseDir: File): File {
        val dir = File(baseDir, DOWNLOAD_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun artworkDir(baseDir: File): File {
        val dir = File(baseDir, ARTWORK_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Checks if a valid, non-empty completed file already exists in `dir`.
     */
    fun existing(dir: File, fileName: String): String? {
        val f = File(dir, fileName)
        return if (f.exists() && f.isFile && f.length() > 0L) {
            fileToUriString(f)
        } else {
            null
        }
    }

    /**
     * Starts an atomic `.part` download operation in `dir`.
     */
    fun begin(dir: File, fileName: String): Pending {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Could not create download directory: ${dir.absolutePath}")
        }
        val target = File(dir, fileName)
        val part = File(dir, ".$fileName.part")
        if (part.exists()) {
            part.delete()
        }
        return Pending(partFile = part, targetFile = target)
    }

    /**
     * Cleans up any leftover `.part` files from interrupted sessions on startup.
     */
    fun cleanupOrphanedPartFiles(dir: File) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".part")) {
                runCatching { file.delete() }
            }
        }
    }

    /**
     * Converts a `File` to a canonical `file:///...` URI string that works on both Android
     * (`Uri.parse` / `ExoPlayer`) and pure JVM unit tests.
     */
    fun fileToUriString(file: File): String {
        val rawUri = file.toURI().toString()
        return if (rawUri.startsWith("file:/") && !rawUri.startsWith("file:///")) {
            "file:///" + rawUri.removePrefix("file:/")
        } else {
            rawUri
        }
    }

    /**
     * Resolves a `file://` URI string or local path back to a `File` object, or null if not a local file URI.
     */
    fun resolveLocalFile(uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null
        val trimmed = uriString.trim()
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            runCatching {
                val f = File(URI(trimmed))
                if (f.exists() && f.isFile) return f
            }
            var path = trimmed.removePrefix("file://").removePrefix("file:")
            if (path.startsWith("/") && path.length > 2 && path[2] == ':') {
                path = path.substring(1)
            }
            val f = runCatching { File(java.net.URLDecoder.decode(path, "UTF-8")) }.getOrNull() ?: File(path)
            return if (f.exists() && f.isFile) f else null
        }
        val direct = File(trimmed)
        return if (direct.exists() && direct.isFile) direct else null
    }

    /**
     * Verifies that `uriString` points to an existing, non-empty regular file on disk (`length() > 0L`)
     * and is NOT a partial `.part` file.
     */
    fun verifyLocalFile(uriString: String?): File? {
        val file = resolveLocalFile(uriString) ?: return null
        if (file.name.endsWith(".part")) return null
        return if (file.exists() && file.isFile && file.length() > 0L) file else null
    }

    /**
     * Deletes the underlying local file for `uriString`. Returns true if deleted or already absent.
     */
    fun deleteFile(uriString: String?): Boolean {
        val file = resolveLocalFile(uriString) ?: return false
        return if (file.exists()) {
            runCatching { file.delete() }.getOrDefault(false)
        } else {
            true
        }
    }
}

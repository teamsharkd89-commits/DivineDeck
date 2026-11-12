package com.music.bitchord.data.lyrics

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistencia en disco de letras sincronizadas y planas para DIVINE DECK (Fase 06.5D.1).
 *
 * Responsabilidades:
 * - Almacenar las letras resueltas en archivos `<videoId>.lrc` dentro del almacenamiento
 *   interno de la app (`context.filesDir/lyrics_store/`).
 * - Conservar el formato Enhanced LRC (sincronización por palabra `<mm:ss.xx>`) o LRC estándar.
 * - Permitir recuperación inmediata 100% offline sin llamadas a la red tras reiniciar la app,
 *   reiniciar el dispositivo o desactivar el Wi-Fi.
 * - Soporte para inyección de directorio personalizado en pruebas unitarias mediante [customStorageDir].
 */
object LyricsStore {

    private const val DIRECTORY_NAME = "lyrics_store"

    @Volatile
    var customStorageDir: File? = null

    private val memoryIndex = ConcurrentHashMap<String, File>()

    fun getStorageDir(context: Context?): File? {
        customStorageDir?.let {
            if (!it.exists()) it.mkdirs()
            return it
        }
        val base = context?.filesDir ?: return null
        val dir = File(base, DIRECTORY_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun sanitizeVideoId(videoId: String): String {
        return videoId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    fun has(context: Context?, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val dir = getStorageDir(context) ?: return false
        val file = File(dir, "${sanitizeVideoId(videoId)}.lrc")
        return file.exists() && file.length() > 0L
    }

    fun get(context: Context?, videoId: String): LyricsUiState? {
        val lines = getLines(context, videoId) ?: return null
        val isSynced = lines.any { it.timeMs > 0L }
        val isWordSynced = lines.any { it.isWordSynced }
        return LyricsUiState.EmbeddedLocal(
            videoId = videoId,
            lines = lines,
            isSynced = isSynced,
            isWordSynced = isWordSynced,
            containerOrFormat = "Caché Offline",
        )
    }

    fun getLines(context: Context?, videoId: String): List<LyricLine>? {
        if (videoId.isBlank()) return null
        val dir = getStorageDir(context) ?: return null
        val file = File(dir, "${sanitizeVideoId(videoId)}.lrc")
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val content = file.readText(Charsets.UTF_8)
            EmbeddedLyrics.parseEmbeddedText(content)?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    fun save(
        context: Context?,
        videoId: String,
        lines: List<LyricLine>,
        source: LyricsSource? = null,
    ): File? {
        if (videoId.isBlank() || lines.none { it.text.isNotBlank() }) return null
        val dir = getStorageDir(context) ?: return null

        val content = lines.toEnhancedLrc().takeIf { it.isNotBlank() } ?: lines.toLrc()
        if (content.isBlank()) return null

        return runCatching {
            val finalFile = File(dir, "${sanitizeVideoId(videoId)}.lrc")
            val tempFile = File(dir, "${sanitizeVideoId(videoId)}.lrc.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            if (tempFile.renameTo(finalFile) || (finalFile.delete() && tempFile.renameTo(finalFile))) {
                memoryIndex[videoId] = finalFile
                finalFile
            } else {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
                memoryIndex[videoId] = finalFile
                finalFile
            }
        }.getOrNull()
    }

    fun delete(context: Context?, videoId: String): Boolean {
        if (videoId.isBlank()) return false
        val dir = getStorageDir(context) ?: return false
        val file = File(dir, "${sanitizeVideoId(videoId)}.lrc")
        memoryIndex.remove(videoId)
        return if (file.exists()) file.delete() else false
    }

    fun clear(context: Context? = null) {
        memoryIndex.clear()
        val dir = getStorageDir(context) ?: return
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}

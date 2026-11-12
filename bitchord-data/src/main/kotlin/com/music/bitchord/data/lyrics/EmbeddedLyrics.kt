package com.music.bitchord.data.lyrics

import android.content.Context
import android.net.Uri
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI

/**
 * The lyrics already sitting inside a downloaded or local audio file, or in a sidecar `.lrc` file.
 *
 * Adapted from BitChord 1.8.1 `EmbeddedLyrics.kt` (GPLv3).
 *
 * Supports:
 * - `.lrc` sidecar files (`<audioFile>.lrc`, `<baseName>.lrc`, or `lyrics.lrc` next to `.m3u8` packages).
 * - MP4/M4A `©lyr` and `BITCHORD_LYRICS` freeform atoms (`moov/udta/meta/ilst`).
 * - FLAC `VORBIS_COMMENT` (`BITCHORD_LYRICS` and `LYRICS`).
 * - Matroska/WebM `SimpleTag` (`BITCHORD_LYRICS` and `LYRICS`).
 * - ID3v2 `USLT` (Unsynchronised lyrics) for local `.mp3` files.
 * - Both synced (`[mm:ss.xx]`, `<mm:ss.xx>word`) and plain unsynchronized embedded lyrics.
 */
object EmbeddedLyrics {

    private const val TAG = "BitChordLyrics"
    private const val MAX_TAG_BYTES = 8 * 1024 * 1024

    @Volatile
    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    data class LocalReadResult(
        val lines: List<LyricLine>,
        val formatLabel: String,
    )

    /**
     * Reads embedded or sidecar lyrics from [uriString] (supports `file://` without Context,
     * and `content://` when [context] is provided).
     */
    suspend fun forUri(context: Context?, uriString: String): List<LyricLine>? =
        forUriDetailed(context, uriString)?.lines

    suspend fun forUriDetailed(context: Context?, uriString: String): LocalReadResult? =
        withContext(ioDispatcher) {
            if (uriString.isBlank()) return@withContext null
            val (raw, formatLabel) = runCatching { readRawWithFormat(context, uriString) }
                .onFailure { TrackLog.d(TAG, "no embedded lyrics in $uriString: ${it.message}") }
                .getOrNull()
                ?: return@withContext null

            val lines = parseEmbeddedText(raw) ?: return@withContext null
            LocalReadResult(lines = lines, formatLabel = formatLabel)
        }

    /**
     * Parses raw text extracted from an embedded tag or sidecar `.lrc` file into [LyricLine]s.
     * Supports Enhanced LRC, standard LRC, TTML, and plain unsynchronized lyrics.
     */
    fun parseEmbeddedText(raw: String): List<LyricLine>? {
        val trimmed = raw.replace("\uFEFF", "").trim()
        if (trimmed.isEmpty()) return null
        val synced = LrcLib.parseLrc(trimmed).takeIf { lines -> lines.any { it.text.isNotBlank() } }
            ?.withBackgroundVocals()
        if (synced != null) return synced
        return ProviderLyrics.parse(trimmed)?.withBackgroundVocals()
    }

    private fun readRawWithFormat(context: Context?, uriString: String): Pair<String, String>? {
        val filePath = resolveLocalFilePath(uriString)
        if (filePath != null) {
            sidecarWithFormat(filePath)?.let { return it }
        }
        val bytes = openStream(context, uriString, filePath)?.use { it.readAtMost(MAX_TAG_BYTES) }
            ?: return null
        return fromBytesWithFormat(bytes)
    }

    private fun resolveLocalFilePath(uriString: String): String? {
        if (uriString.startsWith("file:", ignoreCase = true)) {
            runCatching {
                val jUri = URI(uriString)
                val f = File(jUri)
                if (f.exists()) return f.absolutePath
            }
            val stripped = uriString.removePrefix("file://").removePrefix("file:")
            val fallback = File(java.net.URLDecoder.decode(stripped, "UTF-8"))
            if (fallback.exists()) return fallback.absolutePath
            return stripped
        }
        val direct = File(uriString)
        if (direct.exists()) return direct.absolutePath
        return null
    }

    /**
     * Checks for a sidecar `.lrc` file next to [path]:
     * 1. `lyrics.lrc` next to an `.m3u8` offline package playlist.
     * 2. `<filename>.lrc` or `<nameWithoutExtension>.lrc` next to a downloaded/local audio file.
     */
    internal fun sidecar(path: String?): String? = sidecarWithFormat(path)?.first

    internal fun sidecarWithFormat(path: String?): Pair<String, String>? {
        val target = path?.let(::File) ?: return null
        val parent = target.parentFile ?: return null

        val candidates = buildList {
            if (target.name.endsWith(".m3u8", ignoreCase = true)) {
                add(File(parent, "lyrics.lrc") to "Sidecar LRC (HLS/DASH)")
            }
            if (target.name.endsWith(".lrc", ignoreCase = true)) {
                add(target to "Sidecar LRC")
            } else {
                add(File(parent, "${target.nameWithoutExtension}.lrc") to "Sidecar LRC")
                add(File(parent, "${target.name}.lrc") to "Sidecar LRC")
            }
        }

        for ((file, label) in candidates) {
            if (file.isFile && file.length() in 1..MAX_TAG_BYTES.toLong()) {
                val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
                if (text != null) return text to label
            }
        }
        return null
    }

    /**
     * Reads the raw LRC/lyrics text in [head], whichever of the supported containers it is.
     */
    fun fromBytes(head: ByteArray): String? = fromBytesWithFormat(head)?.first

    internal fun fromBytesWithFormat(head: ByteArray): Pair<String, String>? {
        val (found, format) = when {
            head.startsWith(FLAC_MAGIC) -> flac(head) to "FLAC Vorbis Comment"
            head.startsWith(MATROSKA_MAGIC) -> matroska(head) to "WebM/Matroska Tag"
            head.isMp4() -> mp4(head) to "MP4/M4A ©lyr Atom"
            head.startsWith(ID3_MAGIC) -> id3v2(head) to "ID3v2 USLT Tag"
            else -> null to ""
        }
        return found?.takeIf { it.isNotBlank() }?.let { it to format }
    }

    private fun openStream(context: Context?, uriString: String, resolvedPath: String?): InputStream? {
        if (resolvedPath != null) {
            val f = File(resolvedPath)
            if (f.isFile) return f.inputStream()
        }
        if (context != null) {
            return runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)) }.getOrNull()
        }
        return null
    }

    // ---- MP4 / M4A ----------------------------------------------------------

    private fun mp4(bytes: ByteArray): String? {
        val moov = topLevelBox(bytes, "moov") ?: return null
        val end = moov.last + 1
        return ilstText(bytes, moov.first, end, freeform = true)
            ?: ilstText(bytes, moov.first, end, freeform = false)
    }

    private fun topLevelBox(bytes: ByteArray, type: String): IntRange? {
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val declared = readU32(bytes, pos)
            var headerLen = 8
            var size = declared
            if (declared == 1L) {
                if (pos + 16 > bytes.size) return null
                size = readU64(bytes, pos + 8)
                headerLen = 16
            } else if (declared == 0L) {
                size = (bytes.size - pos).toLong()
            }
            if (size < headerLen || size > Int.MAX_VALUE) return null
            val end = (pos + size).toInt().coerceAtMost(bytes.size)
            if (String(bytes, pos + 4, 4, Charsets.ISO_8859_1) == type) return pos until end
            pos += size.toInt()
        }
        return null
    }

    private fun ilstText(bytes: ByteArray, from: Int, endExclusive: Int, freeform: Boolean): String? {
        val marker = if (freeform) WORD_LYRICS_FIELD.toByteArray(Charsets.UTF_8) else LYR_ATOM
        var at = from
        while (true) {
            val found = bytes.indexOf(marker, at, endExclusive) ?: return null
            val data = bytes.indexOf(DATA_ATOM, found, endExclusive) ?: return null
            dataText(bytes, data, endExclusive)?.let { return it }
            at = found + marker.size
        }
    }

    private fun dataText(bytes: ByteArray, dataAt: Int, endExclusive: Int): String? {
        val start = dataAt - 4
        if (start < 0 || dataAt + 12 > endExclusive) return null
        val size = readU32(bytes, start).toInt()
        if (size <= 16 || start + size > endExclusive) return null
        if (readU32(bytes, dataAt + 4).toInt() != 1) return null
        return String(bytes, dataAt + 12, start + size - (dataAt + 12), Charsets.UTF_8)
    }

    // ---- FLAC ---------------------------------------------------------------

    private fun flac(bytes: ByteArray): String? {
        var pos = FLAC_MAGIC.size
        while (pos + 4 <= bytes.size) {
            val flags = bytes[pos].toInt() and 0xFF
            val length = ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                (bytes[pos + 3].toInt() and 0xFF)
            val start = pos + 4
            if (start + length > bytes.size) return null
            if (flags and 0x7F == FLAC_VORBIS_COMMENT) {
                return vorbisComment(bytes, start, start + length)
            }
            if (flags and 0x80 != 0) return null
            pos = start + length
        }
        return null
    }

    private fun vorbisComment(bytes: ByteArray, start: Int, end: Int): String? {
        var pos = start
        fun u32(): Int? {
            if (pos + 4 > end) return null
            val v = (bytes[pos].toInt() and 0xFF) or ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
                ((bytes[pos + 2].toInt() and 0xFF) shl 16) or ((bytes[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }
        val vendor = u32() ?: return null
        pos += vendor
        val count = u32() ?: return null
        var plain: String? = null
        repeat(count.coerceAtMost(4_096)) {
            val length = u32() ?: return plain
            if (length < 0 || pos + length > end) return plain
            val entry = String(bytes, pos, length, Charsets.UTF_8)
            pos += length
            val name = entry.substringBefore('=').uppercase()
            val value = entry.substringAfter('=', "")
            if (name == WORD_LYRICS_FIELD && value.isNotBlank()) return value
            if (name == "LYRICS" && plain == null && value.isNotBlank()) plain = value
        }
        return plain
    }

    // ---- Matroska / WebM ----------------------------------------------------

    private fun matroska(bytes: ByteArray): String? {
        var plain: String? = null
        for (name in listOf(WORD_LYRICS_FIELD, "LYRICS")) {
            val needle = name.toByteArray(Charsets.US_ASCII)
            var from = 0
            while (true) {
                val at = bytes.indexOf(needle, from, bytes.size) ?: break
                from = at + needle.size
                if (at < 3 || bytes[at - 3] != ID_TAGNAME[0] || bytes[at - 2] != ID_TAGNAME[1]) continue
                if ((bytes[at - 1].toInt() and 0x7F) != needle.size) continue
                val string = bytes.indexOf(ID_TAGSTRING, from, bytes.size) ?: continue
                val size = readVint(bytes, string + 2) ?: continue
                val valueAt = string + 2 + size.width
                if (size.value <= 0 || valueAt + size.value > bytes.size) continue
                val value = String(bytes, valueAt, size.value.toInt(), Charsets.UTF_8)
                if (value.isBlank()) continue
                if (name == WORD_LYRICS_FIELD) return value
                if (plain == null) plain = value
            }
        }
        return plain
    }

    // ---- ID3v2 (MP3 USLT) ---------------------------------------------------

    private fun id3v2(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        val version = bytes[3].toInt() and 0xFF
        val tagSize = syncSafeInt(bytes, 6)
        val end = (10 + tagSize).coerceAtMost(bytes.size)
        var pos = 10
        while (pos + 10 <= end) {
            val frameId = String(bytes, pos, 4, Charsets.ISO_8859_1)
            if (frameId.all { it == '\u0000' }) break
            val frameSize = if (version >= 4) syncSafeInt(bytes, pos + 4) else readU32(bytes, pos + 4).toInt()
            if (frameSize <= 0 || pos + 10 + frameSize > end) break
            if (frameId == "USLT") {
                val payloadStart = pos + 10
                val payloadEnd = payloadStart + frameSize
                if (payloadStart + 4 < payloadEnd) {
                    val encoding = bytes[payloadStart].toInt() and 0xFF
                    // Skip encoding(1) + language(3) + content descriptor (null-terminated)
                    var textStart = payloadStart + 4
                    while (textStart < payloadEnd && bytes[textStart] != 0.toByte()) {
                        textStart++
                    }
                    if (textStart < payloadEnd) textStart++ // skip null terminator
                    if (encoding == 1 || encoding == 2) {
                        if (textStart < payloadEnd && bytes[textStart] == 0.toByte()) textStart++
                    }
                    if (textStart < payloadEnd) {
                        val charset = when (encoding) {
                            1 -> Charsets.UTF_16
                            2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            else -> Charsets.ISO_8859_1
                        }
                        val text = String(bytes, textStart, payloadEnd - textStart, charset).trim('\u0000', ' ', '\n', '\r')
                        if (text.isNotBlank()) return text
                    }
                }
            }
            pos += 10 + frameSize
        }
        return null
    }

    private fun syncSafeInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)

    private class Vint(val value: Long, val width: Int)

    private fun readVint(bytes: ByteArray, offset: Int): Vint? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        if (first == 0) return null
        var width = 1
        var mask = 0x80
        while (first and mask == 0) {
            mask = mask shr 1
            width++
        }
        if (offset + width > bytes.size) return null
        var value = (first and mask.inv() and 0xFF).toLong()
        for (i in 1 until width) value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        return Vint(value, width)
    }

    // ---- Bytes --------------------------------------------------------------

    private fun InputStream.readAtMost(max: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(max, 1 shl 16))
        val buffer = ByteArray(1 shl 16)
        var total = 0
        while (total < max) {
            val read = read(buffer, 0, minOf(buffer.size, max - total))
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun ByteArray.isMp4(): Boolean =
        size > 12 && this[4] == 'f'.code.toByte() && this[5] == 't'.code.toByte() &&
            this[6] == 'y'.code.toByte() && this[7] == 'p'.code.toByte()

    private fun ByteArray.indexOf(needle: ByteArray, from: Int, until: Int): Int? {
        if (needle.isEmpty()) return null
        val last = minOf(until, size) - needle.size
        var i = from.coerceAtLeast(0)
        outer@ while (i <= last) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) {
                    i++
                    continue@outer
                }
            }
            return i
        }
        return null
    }

    private fun readU32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or (b[off + 3].toLong() and 0xFF)

    private fun readU64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private const val FLAC_VORBIS_COMMENT = 4
    private val FLAC_MAGIC = "fLaC".toByteArray(Charsets.US_ASCII)
    private val MATROSKA_MAGIC = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
    private val ID3_MAGIC = "ID3".toByteArray(Charsets.US_ASCII)
    private val DATA_ATOM = "data".toByteArray(Charsets.ISO_8859_1)
    private val LYR_ATOM = byteArrayOf(0xA9.toByte()) + "lyr".toByteArray(Charsets.ISO_8859_1)
    private val ID_TAGNAME = byteArrayOf(0x45, 0xA3.toByte())
    private val ID_TAGSTRING = byteArrayOf(0x44, 0x87.toByte())
}

package com.music.bitchord.data

import android.util.Log

/**
 * Diagnostic and telemetry logging for BitChord audio pipeline events.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object TrackLog {
    private const val DEFAULT_TAG = "BitChord"
    private val working = ThreadLocal<String>()

    @Volatile
    var echo: ((level: Char, tag: String, message: String, error: Throwable?) -> Unit)? = null

    @Volatile
    var headerLines: (() -> List<String>)? = null

    fun about(videoId: String) = working.set(videoId)

    fun d(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        val formatted = if (about != null) "[$about] $message" else message
        echo?.invoke('D', tag, formatted, null)
        Log.d(tag, formatted)
    }

    fun i(tag: String = DEFAULT_TAG, message: String, about: String? = working.get()) {
        val formatted = if (about != null) "[$about] $message" else message
        echo?.invoke('I', tag, formatted, null)
        Log.i(tag, formatted)
    }

    fun w(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null, about: String? = null) {
        val contextAbout = about ?: working.get()
        val formatted = if (contextAbout != null) "[$contextAbout] $message" else message
        echo?.invoke('W', tag, formatted, error)
        if (error != null) {
            Log.w(tag, formatted, error)
        } else {
            Log.w(tag, formatted)
        }
    }

    fun e(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null, about: String? = null) {
        val contextAbout = about ?: working.get()
        val formatted = if (contextAbout != null) "[$contextAbout] $message" else message
        echo?.invoke('E', tag, formatted, error)
        if (error != null) {
            Log.e(tag, formatted, error)
        } else {
            Log.e(tag, formatted)
        }
    }
}

package com.music.bitchord.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Audio decoding and streaming statistics telemetry.
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
object NerdStats {
    data class Stats(
        val videoId: String = "",
        val bitrateKbps: Int? = null,
        val mimeType: String? = null,
        val source: String = "Internal",
    )

    private val _currentStats = MutableStateFlow(Stats())
    val currentStats: StateFlow<Stats> = _currentStats.asStateFlow()

    private val sources = ConcurrentHashMap<String, String>()
    private val bitrates = ConcurrentHashMap<String, Int>()

    fun recordSource(videoId: String, sourceName: String) {
        sources[videoId] = sourceName
        _currentStats.value = _currentStats.value.copy(videoId = videoId, source = sourceName)
    }

    fun onStreamPicked(videoId: String, kbps: Int) {
        bitrates[videoId] = kbps
        _currentStats.value = _currentStats.value.copy(videoId = videoId, bitrateKbps = kbps)
    }

    fun forgetLastSession() {
        sources.clear()
        bitrates.clear()
        _currentStats.value = Stats()
    }
}

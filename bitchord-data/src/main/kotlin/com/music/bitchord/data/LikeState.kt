package com.music.bitchord.data

import com.music.bitchord.data.model.LikeStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The rating the listener has seen or set for each track (`videoId` -> [LikeStatus]).
 *
 * Extracted from BitChord 1.8.1 (GPLv3). Synced with [com.music.bitchord.data.spotify.LocalPlaylistStore]
 * for on-device persistence across sessions and with [YtMusicRepository.library] when a remote
 * YouTube Music session is active.
 */
object LikeState {
    private val _overrides = MutableStateFlow<Map<String, LikeStatus>>(emptyMap())
    val overrides: StateFlow<Map<String, LikeStatus>> = _overrides.asStateFlow()

    operator fun get(videoId: String): LikeStatus? = _overrides.value[videoId]

    operator fun set(videoId: String, status: LikeStatus) {
        _overrides.update { it + (videoId to status) }
    }

    /**
     * Records [videoIds] as liked without overwriting any rating already in [overrides].
     */
    fun seedLiked(videoIds: Set<String>) {
        if (videoIds.isEmpty()) return
        _overrides.update { current ->
            val seeded = HashMap<String, LikeStatus>(current.size + videoIds.size)
            videoIds.forEach { seeded[it] = LikeStatus.LIKE }
            seeded.putAll(current)
            seeded
        }
    }

    fun clear() {
        _overrides.value = emptyMap()
    }
}

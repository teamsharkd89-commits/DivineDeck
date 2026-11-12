package com.music.bitchord.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.FileNotFoundException

/**
 * Load error policy that avoids wasting retries on permanent stream failures
 * (e.g. 404 Not Found, unresolvable streams, expired tokens).
 * Extracted from BitChord 1.8.1 (GPLv3).
 */
@OptIn(UnstableApi::class)
class PermanentAwareLoadErrorPolicy(
    defaultMinLoadableRetryCount: Int = DEFAULT_MIN_LOADABLE_RETRY_COUNT
) : DefaultLoadErrorHandlingPolicy(defaultMinLoadableRetryCount) {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        if (isPermanent(loadErrorInfo.exception)) {
            return C.TIME_UNSET
        }
        return super.getRetryDelayMsFor(loadErrorInfo)
    }

    fun isPermanent(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth++ < CAUSE_DEPTH) {
            if (cause is FileNotFoundException) return true
            val msg = cause.message.orEmpty()
            if (msg.contains("404") || msg.contains("unplayable", ignoreCase = true) || msg.contains("Unsupported media URI", ignoreCase = true)) {
                return true
            }
            cause = cause.cause?.takeIf { it !== cause }
        }
        return false
    }

    companion object {
        const val CAUSE_DEPTH = 8
    }
}

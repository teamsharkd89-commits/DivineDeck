package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Shared plumbing for the lyric providers.
 *
 * Extracted from BitChord 1.8.1 (GPLv3), with network guard and test hooks
 * for deterministic offline verification.
 */
private const val LYRICS_TIMEOUT_SECONDS = 6L

internal const val LYRICS_AGENT = "BitChord (https://github.com/bitchord)"

val lyricsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val client by lazy {
    Http.client.newBuilder()
        .callTimeout(LYRICS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()
}

private val authenticatedClient by lazy {
    client.newBuilder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
}

/**
 * Tracks the number of outbound HTTP requests attempted by lyric providers.
 * Used by unit tests to assert that zero network requests occur when offline.
 */
val lyricsNetworkCallCount = AtomicInteger(0)

/**
 * Optional override for network availability in unit tests or offline mode.
 */
@Volatile
var lyricsOfflineModeOverride: Boolean? = null

/**
 * Optional HTTP interceptor hook for deterministic unit tests.
 */
@Volatile
var lyricsHttpOverride: ((url: String) -> String?)? = null

fun resetLyricsNetworkStats() {
    lyricsNetworkCallCount.set(0)
    lyricsHttpOverride = null
}

/** Body of a successful GET, or null for any failure at all. */
internal fun lyricsGet(url: String): String? = runCatching {
    if (lyricsOfflineModeOverride == true) return null
    lyricsNetworkCallCount.incrementAndGet()
    lyricsHttpOverride?.let { return it(url) }
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/** Body of an authenticated provider GET without service-specific browser headers. */
internal fun lyricsGetBearer(url: String, bearer: String): String? = runCatching {
    if (lyricsOfflineModeOverride == true) return null
    if (bearer.isBlank()) return null
    lyricsNetworkCallCount.incrementAndGet()
    lyricsHttpOverride?.let { return it(url) }
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json, text/plain, */*")
        .header("Authorization", "Bearer $bearer")
        .build()
    authenticatedClient.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

/**
 * [lyricsGet], with a bearer token and the headers Apple's own web player
 * sends alongside one.
 */
internal fun lyricsGetAuthorized(url: String, bearer: String): String? = runCatching {
    if (lyricsOfflineModeOverride == true) return null
    lyricsNetworkCallCount.incrementAndGet()
    lyricsHttpOverride?.let { return it(url) }
    val request = Request.Builder().url(url)
        .header("User-Agent", LYRICS_AGENT)
        .header("Accept", "application/json")
        .header("Authorization", "Bearer $bearer")
        .header("Origin", "https://music.apple.com")
        .header("Referer", "https://music.apple.com/")
        .build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) response.body?.string() else null
    }
}.getOrNull()

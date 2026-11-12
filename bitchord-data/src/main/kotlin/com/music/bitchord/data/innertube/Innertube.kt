package com.music.bitchord.data.innertube

import com.music.bitchord.data.Http
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.PlaylistPrivacy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Cliente de red Innertube (YouTube Music).
 * Ejecuta peticiones de búsqueda e interactúa con el catálogo oficial de YouTube Music.
 * Extraído y adaptado de BitChord 1.8.1 (GPLv3).
 */
object Innertube {

    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"
    private const val WEB_REMIX_VERSION = "1.20250101.01.00"
    private const val WEB_REMIX_CLIENT_ID = "67"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    var cookie: String? = null

    @Volatile
    var currentRegion: String = "US"

    @Volatile
    var currentLanguage: String = "en"

    @Volatile
    private var cachedVisitorData: String? = null
    private val VISITOR_DATA_REGEX = Regex("""Cg[A-Za-z0-9_%-]{40,}""")
    private const val FALLBACK_VISITOR_DATA = "Cgt2S1VnN1lVbUtHbyj5yZq0BjIKCgJVUxIEZW4tVVM%3D"

    fun ensureVisitorData(): String {
        val cached = cachedVisitorData
        if (cached != null) return cached

        // Sincronización ligera para arranque o consulta síncrona
        val live = runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                )
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    VISITOR_DATA_REGEX.find(body)?.value
                } else null
            }
        }.getOrNull()

        if (live != null) {
            cachedVisitorData = live
            return live
        }
        return FALLBACK_VISITOR_DATA
    }

    /**
     * Versión asíncrona suspendida para refresco explícito de visitante.
     */
    suspend fun refreshVisitorData(): String = withContext(Dispatchers.IO) {
        val live = runCatching {
            val req = Request.Builder()
                .url("https://www.youtube.com/sw.js_data")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
                )
                .build()
            Http.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    VISITOR_DATA_REGEX.find(body)?.value
                } else null
            }
        }.getOrNull()

        if (live != null) {
            cachedVisitorData = live
            live
        } else {
            cachedVisitorData ?: FALLBACK_VISITOR_DATA
        }
    }

    /**
     * Realiza una búsqueda en YouTube Music.
     * Si [params] se especifica, aplica el filtro correspondiente (Canciones, Videos, Álbumes, etc.).
     */
    suspend fun search(
        query: String,
        params: String? = null,
        continuation: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            if (continuation != null) {
                put("continuation", continuation)
            } else {
                put("query", query)
                if (params != null) {
                    put("params", params)
                }
            }
        }

        val url = if (continuation != null) {
            "$MUSIC_BASE/search?ctoken=$continuation&continuation=$continuation&type=next&prettyPrint=false"
        } else {
            "$MUSIC_BASE/search?prettyPrint=false"
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube search failed with HTTP ${response.code}: ${response.message}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube search")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    /**
     * Sugerencias de autocompletado en tiempo real para el término parcial introducido por el usuario.
     */
    suspend fun searchSuggestions(input: String): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            put("input", input)
        }

        val request = Request.Builder()
            .url("$MUSIC_BASE/music/get_search_suggestions?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = Http.client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube suggestions failed with HTTP ${response.code}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube suggestions")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    /**
     * Consulta un endpoint browse de YouTube Music (Home: FEmusic_home, Explore: FEmusic_moods_and_genres,
     * New Releases: FEmusic_new_releases, FEmusic_explore, álbumes, playlists, artistas y categorías).
     */
    suspend fun browse(
        browseId: String,
        params: String? = null
    ): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            put("browseId", browseId)
            if (params != null) {
                put("params", params)
            }
        }

        val requestBuilder = Request.Builder()
            .url("$MUSIC_BASE/browse?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube browse($browseId) failed with HTTP ${response.code}: ${response.message}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube browse($browseId)")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    /**
     * Obtiene la siguiente página de un feed paginado de browse (Home, playlists, álbumes).
     */
    suspend fun browseContinuation(token: String): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            put("continuation", token)
        }

        val url = "$MUSIC_BASE/browse?ctoken=$token&continuation=$token&type=next&prettyPrint=false"
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube browseContinuation failed with HTTP ${response.code}: ${response.message}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube browseContinuation")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    /**
     * Obtiene la cola de reproducción ("RDAMVM" radio mix) asociada a un [videoId].
     */
    suspend fun next(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
        }

        val requestBuilder = Request.Builder()
            .url("$MUSIC_BASE/next?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube next($videoId) failed with HTTP ${response.code}: ${response.message}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube next($videoId)")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    /**
     * Obtiene la transcripción temporal (captions) de YouTube como fuente de letras.
     */
    suspend fun transcript(videoId: String): JsonObject = withContext(Dispatchers.IO) {
        val idBytes = videoId.toByteArray(Charsets.UTF_8)
        val protoBytes = byteArrayOf(10, idBytes.size.toByte()) + idBytes
        val params = java.util.Base64.getEncoder().encodeToString(protoBytes)

        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            put("params", params)
        }

        val requestBuilder = Request.Builder()
            .url("$MUSIC_BASE/get_transcript?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube transcript($videoId) failed with HTTP ${response.code}: ${response.message}")
        }

        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube transcript($videoId)")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    // ---- Writes (BitChord 1.8.1 original — require session cookie) -----------

    class NotSignedInException : IllegalStateException("Sign in to YouTube Music to do that")

    private fun requireSession() {
        if (cookie == null) throw NotSignedInException()
    }

    suspend fun rate(videoId: String, status: LikeStatus) {
        requireSession()
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val response = postAuthenticated(endpoint) {
            putJsonObject("target") { put("videoId", videoId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the rating: ${message ?: error}")
        }
    }

    suspend fun ratePlaylist(playlistId: String, saved: Boolean) {
        requireSession()
        val endpoint = if (saved) "like/like" else "like/removelike"
        val response = postAuthenticated(endpoint) {
            putJsonObject("target") { put("playlistId", playlistId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the change: ${message ?: error}")
        }
    }

    suspend fun setSubscribed(channelId: String, subscribed: Boolean) {
        requireSession()
        val endpoint = if (subscribed) "subscription/subscribe" else "subscription/unsubscribe"
        val response = postAuthenticated(endpoint) {
            putJsonArray("channelIds") { add(channelId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the change: ${message ?: error}")
        }
    }

    suspend fun sendFeedback(token: String) {
        requireSession()
        postAuthenticated("feedback") {
            putJsonArray("feedbackTokens") { add(token) }
        }
    }

    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        description: String? = null,
        videoIds: List<String> = emptyList(),
    ): String {
        requireSession()
        val response = postAuthenticated("playlist/create") {
            put("title", title)
            put("description", description.orEmpty())
            put("privacyStatus", privacy.apiValue)
            if (videoIds.isNotEmpty()) {
                putJsonArray("videoIds") { videoIds.forEach { add(it) } }
            }
        }
        return response["playlistId"]?.jsonPrimitive?.contentOrNull
            ?: findString(response, "playlistId")
            ?: error("playlist created but no id came back")
    }

    suspend fun deletePlaylist(playlistId: String) {
        requireSession()
        postAuthenticated("playlist/delete") { put("playlistId", playlistId.removePrefix("VL")) }
    }

    private suspend fun editPlaylist(
        playlistId: String,
        actions: JsonArrayBuilder.() -> Unit,
    ): JsonObject {
        requireSession()
        val response = postAuthenticated("browse/edit_playlist") {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions", actions)
        }
        val status = response["status"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "STATUS_SUCCEEDED") {
            error("YouTube Music refused the edit ($status)")
        }
        return response
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>): Map<String, String> {
        val response = editPlaylist(playlistId) {
            videoIds.forEach { videoId ->
                addJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                }
            }
        }
        return (response["playlistEditResults"] as? JsonArray)
            .orEmpty()
            .mapNotNull { result ->
                val added = (result as? JsonObject)
                    ?.get("playlistEditVideoAddedResultData") as? JsonObject
                    ?: return@mapNotNull null
                val videoId = (added["videoId"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                val setVideoId = (added["setVideoId"] as? JsonPrimitive)?.contentOrNull
                    ?: return@mapNotNull null
                videoId to setVideoId
            }
            .toMap()
    }

    suspend fun removeFromPlaylist(playlistId: String, entries: List<Pair<String, String>>) {
        editPlaylist(playlistId) {
            entries.forEach { (setVideoId, videoId) ->
                addJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("setVideoId", setVideoId)
                    put("removedVideoId", videoId)
                }
            }
        }
    }

    suspend fun renamePlaylist(playlistId: String, title: String) {
        editPlaylist(playlistId) {
            addJsonObject {
                put("action", "ACTION_SET_PLAYLIST_NAME")
                put("playlistName", title)
            }
        }
    }

    private suspend fun postAuthenticated(
        endpoint: String,
        bodyExtras: JsonObjectBuilder.() -> Unit,
    ): JsonObject = withContext(Dispatchers.IO) {
        val jsonPayload = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", WEB_REMIX_VERSION)
                    put("hl", currentLanguage)
                    put("gl", currentRegion)
                    put("visitorData", ensureVisitorData())
                }
                putJsonObject("user") {
                    put("lockedSafetyMode", false)
                }
                putJsonObject("request") {
                    put("useSsl", true)
                }
            }
            bodyExtras()
        }

        val requestBuilder = Request.Builder()
            .url("$MUSIC_BASE/$endpoint?prettyPrint=false")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", MUSIC_ORIGIN)
            .addHeader("Referer", "$MUSIC_ORIGIN/")
            .addHeader("X-Origin", MUSIC_ORIGIN)
            .addHeader("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
            .addHeader("X-YouTube-Client-Version", WEB_REMIX_VERSION)
            .addHeader("Accept-Language", "$currentLanguage-$currentRegion,$currentLanguage;q=0.9")
            .addHeader("X-Goog-Visitor-Id", ensureVisitorData())
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            )
            .post(jsonPayload.toString().toRequestBody(JSON_MEDIA_TYPE))

        cookie?.let {
            requestBuilder.addHeader("Cookie", it)
        }

        val response = Http.client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IOException("Innertube $endpoint failed with HTTP ${response.code}: ${response.message}")
        }
        val bodyString = response.body?.string() ?: throw IOException("Empty body from Innertube $endpoint")
        Json.decodeFromString<JsonObject>(bodyString)
    }

    private fun findString(node: JsonElement?, key: String): String? = when (node) {
        is JsonObject -> {
            (node[key] as? JsonPrimitive)?.contentOrNull
                ?: node.values.firstNotNullOfOrNull { findString(it, key) }
        }
        is JsonArray -> node.firstNotNullOfOrNull { findString(it, key) }
        else -> null
    }
}

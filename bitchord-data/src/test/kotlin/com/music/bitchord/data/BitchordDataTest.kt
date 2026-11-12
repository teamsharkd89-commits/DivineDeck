package com.music.bitchord.data

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.innertube.PlayerClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitchordDataTest {

    @Test
    fun `PlayerClient identifies streaming clients correctly`() {
        val iosClient = PlayerClient.forStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=IOS&cver=21.26.4")
        assertEquals("IOS", iosClient.clientName)

        val webClient = PlayerClient.forStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=WEB_REMIX")
        assertEquals("WEB_REMIX", webClient.clientName)

        val androidClient = PlayerClient.forStreamUrl("https://rr1---sn.googlevideo.com/videoplayback?c=ANDROID")
        assertEquals("ANDROID", androidClient.clientName)
    }

    @Test
    fun `InnertubeParser parses empty and malformed json safely`() {
        val emptyObj = Json.decodeFromString<JsonObject>("{}")
        val results = InnertubeParser.parseSearchPage(emptyObj)
        assertTrue(results.rows.isEmpty())
    }
}

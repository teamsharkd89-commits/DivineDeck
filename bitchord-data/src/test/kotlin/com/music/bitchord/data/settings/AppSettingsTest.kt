package com.music.bitchord.data.settings

import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AppSettings] in DIVINE DECK (Phase 06.5E.1).
 * Verifies initialization, persistence, reactiveness, typed clamping, and network rules.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsTest {

    private lateinit var mockPrefs: InMemorySharedPreferences

    @Before
    fun setUp() {
        mockPrefs = InMemorySharedPreferences()
        AppSettings.resetForTest()
    }

    @After
    fun tearDown() {
        AppSettings.resetForTest()
    }

    @Test
    fun test1_initializationWithDefaultValues() {
        AppSettings.initForTest(mockPrefs)

        assertEquals(DownloadQuality.LOSSLESS, AppSettings.downloadQuality.value)
        assertEquals(DownloadQuality.LOSSLESS, AppSettings.currentDownloadQuality)
        assertTrue(AppSettings.wifiOnlyDownloads.value)
        assertTrue(AppSettings.isWifiOnlyDownloads)
        assertFalse(AppSettings.exportDownloads.value)
        assertFalse(AppSettings.isExportDownloads)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.currentAudioCacheLimitBytes)
        assertNull(AppSettings.meteredConnection.value)
        // With no active network (metered = null) and wifiOnly = true, downloadsAllowedNow is true
        assertTrue(AppSettings.downloadsAllowedNow)
    }

    @Test
    fun test2_persistsValuesAcrossReinitialization() {
        AppSettings.initForTest(mockPrefs)

        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)
        AppSettings.setWifiOnlyDownloads(false)
        AppSettings.setExportDownloads(true)
        val customLimit = 2L * 1024 * 1024 * 1024 // 2 GB
        AppSettings.setAudioCacheLimitBytes(customLimit)

        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
        assertTrue(AppSettings.exportDownloads.value)
        assertEquals(customLimit, AppSettings.audioCacheLimitBytes.value)

        // Reset in-memory state and reinitialize using the same SharedPreferences backing
        AppSettings.resetForTest()
        AppSettings.initForTest(mockPrefs)

        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
        assertFalse(AppSettings.wifiOnlyDownloads.value)
        assertTrue(AppSettings.exportDownloads.value)
        assertEquals(customLimit, AppSettings.audioCacheLimitBytes.value)
    }

    @Test
    fun test3_stateFlowUpdatesImmediatelyInMemory() {
        AppSettings.initForTest(mockPrefs)

        assertEquals(DownloadQuality.LOSSLESS, AppSettings.downloadQuality.value)
        AppSettings.setDownloadQuality(DownloadQuality.HIGH)
        assertEquals(DownloadQuality.HIGH, AppSettings.downloadQuality.value)

        assertTrue(AppSettings.wifiOnlyDownloads.value)
        AppSettings.setWifiOnlyDownloads(false)
        assertFalse(AppSettings.wifiOnlyDownloads.value)

        assertFalse(AppSettings.exportDownloads.value)
        AppSettings.setExportDownloads(true)
        assertTrue(AppSettings.exportDownloads.value)
    }

    @Test
    fun test4_handlesTypesAndClampingCorrectly() {
        AppSettings.initForTest(mockPrefs)

        // Floor clamping: values below DEFAULT_CACHE_LIMIT_BYTES (512MB) clamp to 512MB
        AppSettings.setAudioCacheLimitBytes(100L)
        assertEquals(AppSettings.DEFAULT_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)

        // Ceiling clamping: values above MAX_CACHE_LIMIT_BYTES (10GB) clamp to UNLIMITED_CACHE_LIMIT_BYTES
        val aboveMax = 15L * 1024 * 1024 * 1024
        AppSettings.setAudioCacheLimitBytes(aboveMax)
        assertEquals(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)

        // Explicit unlimited
        AppSettings.setAudioCacheLimitBytes(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES)
        assertEquals(AppSettings.UNLIMITED_CACHE_LIMIT_BYTES, AppSettings.audioCacheLimitBytes.value)

        // Normal valid in-between value
        val validMid = 1024L * 1024 * 1024 // 1 GB
        AppSettings.setAudioCacheLimitBytes(validMid)
        assertEquals(validMid, AppSettings.audioCacheLimitBytes.value)
    }

    @Test
    fun test5_preventsDuplicateInitialization() {
        AppSettings.initForTest(mockPrefs)
        AppSettings.setDownloadQuality(DownloadQuality.STANDARD)

        // Second init with another empty prefs should be ignored because initialized is true
        val otherPrefs = InMemorySharedPreferences()
        AppSettings.initForTest(otherPrefs)

        assertEquals(DownloadQuality.STANDARD, AppSettings.downloadQuality.value)
    }

    @Test
    fun test6_downloadsAllowedNowLogic() {
        AppSettings.initForTest(mockPrefs)

        // 1. Wi-Fi Only = true, Mobile Data = true -> Not allowed
        AppSettings.setWifiOnlyDownloads(true)
        AppSettings.setMeteredConnectionForTest(true)
        assertFalse(AppSettings.downloadsAllowedNow)

        // 2. Wi-Fi Only = true, Unmetered Wi-Fi = false -> Allowed
        AppSettings.setMeteredConnectionForTest(false)
        assertTrue(AppSettings.downloadsAllowedNow)

        // 3. Wi-Fi Only = false, Mobile Data = true -> Allowed
        AppSettings.setWifiOnlyDownloads(false)
        AppSettings.setMeteredConnectionForTest(true)
        assertTrue(AppSettings.downloadsAllowedNow)

        // 4. Offline / Unknown network = null -> Allowed through (fails on network socket)
        AppSettings.setWifiOnlyDownloads(true)
        AppSettings.setMeteredConnectionForTest(null)
        assertTrue(AppSettings.downloadsAllowedNow)
    }
}

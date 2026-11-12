package com.divinedeck.launcher.feature.music

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.divinedeck.launcher.feature.music.data.BitChordMediaSessionBridge
import com.divinedeck.launcher.feature.music.data.BitChordSessionConnector
import com.divinedeck.launcher.feature.music.data.MediaControllerRepositoryImpl
import com.divinedeck.launcher.feature.music.ui.LibraryDataSource
import com.divinedeck.launcher.feature.music.ui.MusicCenterDestination
import com.divinedeck.launcher.feature.music.ui.MusicLibraryViewModel
import com.music.bitchord.data.LikeState
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.data.model.ArtistPage
import com.music.bitchord.data.model.LibraryPage
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.UiState
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.download.DownloadProgress
import com.music.bitchord.download.DownloadSession
import com.music.bitchord.download.DownloadState
import com.music.bitchord.download.DownloadStore
import com.music.bitchord.download.Downloader
import com.music.bitchord.download.Downloads
import com.music.bitchord.playback.resolvePlaybackUriString
import com.music.bitchord.playback.resolvedUriString
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Suite de pruebas unitarias y de integración para la Fase 06.5C:
 * Sistema Original de Descargas y Reproducción Offline de BitChord 1.8.1 dentro de DIVINE DECK.
 *
 * Cubre los 13 escenarios obligatorios:
 * 1. Inicio de descarga (`DownloadState.Queued`).
 * 2. Actualización de progreso real en bytes y fracción (`DownloadState.Running`).
 * 3. Transición a descarga completada (`DownloadProgress.Done`, `Downloads.saved`, archivo verificado en disco).
 * 4. Recuperación de descargas tras reinicio simulado (`detachForRestartSimulation` -> `initForTest`).
 * 5. Cancelación de descarga activa (limpieza de `.part` sin dejar archivos corruptos).
 * 6. Eliminación de descarga completada (borrado de archivo físico y registro sin afectar Playlists/Favoritos).
 * 7. Prevención de descargas duplicadas (`videoId` en cola, en progreso o ya descargado).
 * 8. Manejo de error de red y recuperación mediante reintento (`DownloadState.Failed` -> `retry`).
 * 9. Prioridad de resolución hacia archivo local descargado frente a resolución online (`bitchord://watch?v=`).
 * 10. Reproducción local mediante `MediaControllerRepositoryImpl` sin invocar `StreamResolver` online.
 * 11. Integridad de metadatos de canciones descargadas (`SavedSongMetadata` y `Song`).
 * 12. Coexistencia y persistencia de Biblioteca, Playlists y Favoritos (`LocalPlaylistStore` + `Downloads`).
 * 13. Compatibilidad con las 5 pestañas de navegación (`HOME`, `EXPLORE`, `SEARCH`, `LIBRARY`, `PLAYER`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BitChordDownloadsOfflineIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tempBaseDir: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tempBaseDir = Files.createTempDirectory("bitchord_dl_test_").toFile()
        Downloads.resetForTests()
        LocalPlaylistStore.resetForTests()
        LikeState.clear()
    }

    @After
    fun tearDown() {
        Downloads.resetForTests()
        LocalPlaylistStore.resetForTests()
        LikeState.clear()
        runCatching { tempBaseDir.deleteRecursively() }
        Dispatchers.resetMain()
    }

    private class InMemorySharedPreferences(
        val backingMap: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = backingMap.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            backingMap[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (backingMap[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int =
            backingMap[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long =
            backingMap[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float =
            backingMap[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            backingMap[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = backingMap.containsKey(key)
        override fun edit(): SharedPreferences.Editor = EditorImpl(backingMap)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class EditorImpl(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }
            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) map.remove(k) else map[k] = v
                }
            }
        }
    }

    private class TestContextWithStorage(
        private val prefs: SharedPreferences,
        private val baseFilesDir: File
    ) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getFilesDir(): File = baseFilesDir
    }

    private class FakeMediaSessionBridge : BitChordMediaSessionBridge {
        override var isConnected: Boolean = true
        override var isPlaying: Boolean = false
        override var playbackState: Int = Player.STATE_READY
        override var isLoading: Boolean = false
        override var currentPositionMs: Long = 0L
        override var durationMs: Long = 240_000L
        override var currentMediaItem: MediaItem? = null
        override var availableCommands: Player.Commands? = null

        var lastSetMediaItem: MediaItem? = null
        var lastPlayWhenReady: Boolean = false
        private val listeners = mutableListOf<Player.Listener>()

        override fun isCommandAvailable(command: Int): Boolean = true
        override fun play() { isPlaying = true }
        override fun pause() { isPlaying = false }
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun seekTo(positionMs: Long) { currentPositionMs = positionMs }
        override fun setPlayerListener(listener: Player.Listener?) { listener?.let { listeners.add(it) } }
        override fun removePlayerListener(listener: Player.Listener) { listeners.remove(listener) }
        override fun sendCustomCommand(action: String) {}
        override fun sendCustomCommand(action: String, args: android.os.Bundle) {}
        override fun release() {
            isConnected = false
            listeners.clear()
        }

        override fun setMediaItem(mediaItem: MediaItem, playWhenReady: Boolean) {
            lastSetMediaItem = mediaItem
            lastPlayWhenReady = playWhenReady
            currentMediaItem = mediaItem
            isPlaying = playWhenReady
            listeners.forEach {
                it.onMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                it.onIsPlayingChanged(playWhenReady)
            }
        }
    }

    private object OfflineLibraryDataSource : LibraryDataSource {
        override val isSignedIn: Boolean = false
        override suspend fun remoteLibrary(): Result<LibraryPage> =
            Result.success(LibraryPage(emptyList(), emptyList(), emptyList()))
        override suspend fun browseSongs(browseId: String): Result<YtMusicRepository.SongPage> =
            Result.failure(IOException("Offline mode"))
        override suspend fun artistPage(browseId: String): Result<ArtistPage> =
            Result.failure(IOException("Offline mode"))
        override suspend fun localDeviceSongs(context: Context?): List<Song> = emptyList()
    }

    /**
     * Escenarios 1, 2 y 3:
     * Inicio de descarga (`Queued`), actualización de progreso real (`Running`), y transición
     * a descarga completada (`Done` + persistida en `Downloads.saved` con archivo físico verificado).
     */
    @Test
    fun `download lifecycle transitions from Queued to Running with real byte progress to Done`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val gate = CompletableDeferred<Unit>()
        val observedFractions = mutableListOf<Float>()

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream(
                    url = "https://rr1.googlevideo.com/videoplayback?id=$videoId",
                    kbps = 128,
                    mimeType = "audio/mp4; codecs=\"mp4a.40.2\""
                )
                val totalBytes = 4_000L
                val chunk = ByteArray(1_000) { 0x42 }
                openSink(stream).use { out ->
                    out.write(chunk)
                    onProgress(1_000L, totalBytes)
                    observedFractions.add((Downloads.active.value[videoId] as? DownloadState.Running)?.fraction ?: 0f)

                    out.write(chunk)
                    onProgress(2_000L, totalBytes)
                    observedFractions.add((Downloads.active.value[videoId] as? DownloadState.Running)?.fraction ?: 0f)

                    // Pausar en gate para inspeccionar el estado Running al 50%
                    gate.await()

                    out.write(chunk)
                    onProgress(3_000L, totalBytes)
                    out.write(chunk)
                    onProgress(4_000L, totalBytes)
                }
                return stream
            }
        }

        val workerScope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(
            sharedPreferences = prefs,
            baseDir = tempBaseDir,
            fetcher = fakeFetcher,
            testScope = workerScope,
            maxConcurrentWorkers = 1
        )

        val song = Song(
            videoId = "vid_lifecycle_01",
            title = "Midnight Drive",
            artist = "Synthwave Collective",
            albumName = "Night Horizon",
            durationText = "3:45",
            thumbnailUrl = "https://i.ytimg.com/vi/vid_lifecycle_01/hqdefault.jpg"
        )

        // 1. Encolar -> Estado inicial Queued antes de que corra el dispatcher
        Downloads.enqueue(song)
        assertEquals(DownloadState.Queued, Downloads.active.value["vid_lifecycle_01"])
        val batchItemQueued = DownloadSession.state.value.items.firstOrNull { it.videoId == "vid_lifecycle_01" }
        assertNotNull(batchItemQueued)
        assertTrue(batchItemQueued!!.progress is DownloadProgress.Queued)

        // 2. Avanzar hasta el gate -> Estado Running al 50% (2000 / 4000 bytes)
        testScheduler.runCurrent()
        val runningState = Downloads.active.value["vid_lifecycle_01"] as? DownloadState.Running
        assertNotNull("La descarga debe estar en estado Running", runningState)
        assertEquals(0.5f, runningState!!.fraction, 0.01f)
        assertEquals(2_000L, runningState.bytesWritten)
        assertEquals(4_000L, runningState.totalBytes)
        assertFalse("Mientras descarga, aún no debe figurar como completada", Downloads.isDownloaded("vid_lifecycle_01"))

        // 3. Liberar gate y completar descarga
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull("Al completar, ya no debe estar en active", Downloads.active.value["vid_lifecycle_01"])
        assertTrue("Debe estar marcada como descargada y verificada en disco", Downloads.isDownloaded("vid_lifecycle_01"))

        val savedUri = Downloads.verifiedSavedUriString("vid_lifecycle_01")
        assertNotNull(savedUri)
        assertTrue(savedUri!!.startsWith("file:///"))

        val physicalFile = DownloadStore.verifyLocalFile(savedUri)
        assertNotNull("El archivo físico debe existir en disco", physicalFile)
        assertEquals(4_000L, physicalFile!!.length())

        val batchItemDone = DownloadSession.state.value.items.firstOrNull { it.videoId == "vid_lifecycle_01" }
        assertNotNull(batchItemDone)
        assertEquals(DownloadProgress.Done, batchItemDone!!.progress)
    }

    /**
     * Escenarios 4 y 11:
     * Recuperación de descargas tras reinicio simulado e integridad completa de metadatos.
     */
    @Test
    fun `completed downloads and full metadata survive simulated app restart and prune missing files`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream(
                    url = "https://rr1.googlevideo.com/videoplayback?id=$videoId",
                    kbps = 160,
                    mimeType = "audio/webm; codecs=\"opus\""
                )
                val payload = ByteArray(8_192) { (it % 127).toByte() }
                openSink(stream).use { out ->
                    out.write(payload)
                    onProgress(payload.size.toLong(), payload.size.toLong())
                }
                return stream
            }
        }

        val scope1 = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope1)

        val song1 = Song(
            videoId = "vid_persist_01",
            title = "Starlight Highway",
            artist = "Chrome Pulse",
            albumName = "Outrun 2026",
            durationText = "4:12",
            thumbnailUrl = "https://example.com/art1.jpg"
        )
        val song2 = Song(
            videoId = "vid_persist_02",
            title = "Turbo Cruise",
            artist = "Apex Twin",
            albumName = "Telemetry",
            durationText = "3:28",
            thumbnailUrl = "https://example.com/art2.jpg"
        )

        Downloads.enqueue(song1)
        Downloads.enqueue(song2)
        advanceUntilIdle()

        assertTrue(Downloads.isDownloaded("vid_persist_01"))
        assertTrue(Downloads.isDownloaded("vid_persist_02"))
        assertEquals(2, Downloads.getDownloadedSongs().size)

        // Simular cierre de DIVINE DECK (limpia memoria manteniendo SharedPreferences y disco)
        Downloads.detachForRestartSimulation()
        assertEquals(0, Downloads.saved.value.size)

        // Reiniciar motor de descargas con las mismas SharedPreferences y directorio
        val scope2 = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope2)

        assertTrue(Downloads.isDownloaded("vid_persist_01"))
        assertTrue(Downloads.isDownloaded("vid_persist_02"))

        // Verificar integridad de metadatos (Escenario 11)
        val meta1 = Downloads.savedMetadata.value["vid_persist_01"]
        assertNotNull(meta1)
        assertEquals("vid_persist_01", meta1!!.videoId)
        assertEquals("Starlight Highway", meta1.title)
        assertEquals("Chrome Pulse", meta1.artist)
        assertEquals("Outrun 2026", meta1.albumName)
        assertEquals("4:12", meta1.durationText)
        assertEquals("https://example.com/art1.jpg", meta1.thumbnailUrl)
        assertEquals("OPUS 160kbps", meta1.downloadFormat)
        assertEquals(8_192L, meta1.fileSizeBytes)

        // Si el archivo físico de song2 se elimina externamente, verifiedSavedUriString debe detectarlo y limpiar el registro corrupto
        val file2 = DownloadStore.verifyLocalFile(Downloads.saved.value["vid_persist_02"])
        assertNotNull(file2)
        assertTrue(file2!!.delete())

        assertNull("Un archivo eliminado en disco no debe devolverse como URI local válida", Downloads.verifiedSavedUriString("vid_persist_02"))
        assertFalse(Downloads.isDownloaded("vid_persist_02"))
        assertEquals(1, Downloads.getDownloadedSongs().size)
    }

    /**
     * Escenario 5:
     * Cancelación de descarga activa elimina el archivo `.part` temporal y no deja archivos corruptos.
     */
    @Test
    fun `cancelling an active download removes temporary part file and leaves no corrupt file`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val holdGate = CompletableDeferred<Unit>()

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream(
                    url = "https://rr1.googlevideo.com/videoplayback?id=$videoId",
                    kbps = 128,
                    mimeType = "audio/mp4"
                )
                openSink(stream).use { out ->
                    out.write(ByteArray(2048) { 0x11 })
                    onProgress(2048L, 10_000L)
                    holdGate.await()
                }
                return stream
            }
        }

        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope)

        val song = Song(
            videoId = "vid_cancel_01",
            title = "Cancelled Track",
            artist = "Test Artist",
            thumbnailUrl = null
        )

        Downloads.enqueue(song)
        testScheduler.runCurrent()

        // Confirmar que está corriendo y que existe el archivo .part temporal
        assertTrue(Downloads.active.value["vid_cancel_01"] is DownloadState.Running)
        val dlDir = DownloadStore.downloadsDir(tempBaseDir)
        val partFilesDuringRun = dlDir.listFiles()?.filter { it.name.endsWith(".part") }.orEmpty()
        assertEquals("Debe existir 1 archivo .part mientras la descarga está activa", 1, partFilesDuringRun.size)

        // Cancelar descarga activa
        Downloads.cancel("vid_cancel_01")
        advanceUntilIdle()

        assertNull("El estado activo debe limpiarse tras cancelar", Downloads.active.value["vid_cancel_01"])
        assertFalse("No debe figurar como descargada", Downloads.isDownloaded("vid_cancel_01"))

        val remainingFiles = dlDir.listFiles().orEmpty()
        assertTrue("No deben quedar archivos .part ni archivos parciales tras cancelar", remainingFiles.isEmpty())
    }

    /**
     * Escenarios 6 y 12:
     * Eliminación de descarga completada borra el archivo físico y el registro de descargas,
     * pero preserva intactas las Playlists y Canciones Favoritas en `LocalPlaylistStore`.
     */
    @Test
    fun `deleting a completed download removes local file and record without affecting playlists or favorites`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        LocalPlaylistStore.init(TestContextWithStorage(prefs, tempBaseDir))

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream("https://example.com/$videoId.m4a", 128, "audio/mp4")
                openSink(stream).use { out ->
                    out.write(ByteArray(4096) { 0x55 })
                    onProgress(4096L, 4096L)
                }
                return stream
            }
        }

        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope)

        val song = Song(
            videoId = "vid_delete_coexist",
            title = "Roadtrip Anthem",
            artist = "Chevy Sound",
            thumbnailUrl = null,
            durationText = "3:50"
        )

        // Guardar en favoritos y en una playlist personal
        LocalPlaylistStore.toggleLike(song)
        val playlist = LocalPlaylistStore.createPlaylist("Ruta 66")
        LocalPlaylistStore.addSongToPlaylist(playlist.id, song)

        // Descargar la canción para modo offline
        Downloads.enqueue(song)
        advanceUntilIdle()

        val savedUri = Downloads.verifiedSavedUriString(song.videoId)
        assertNotNull(savedUri)
        val fileOnDisk = DownloadStore.verifyLocalFile(savedUri)
        assertNotNull(fileOnDisk)
        assertTrue(fileOnDisk!!.exists())

        // Eliminar la descarga offline
        Downloads.delete(song.videoId)
        advanceUntilIdle()

        assertFalse("El archivo físico debe haberse eliminado del disco", fileOnDisk.exists())
        assertFalse("No debe figurar como descargada", Downloads.isDownloaded(song.videoId))
        assertNull(Downloads.verifiedSavedUriString(song.videoId))

        // Verificar que la canción sigue en Favoritos y en la Playlist "Ruta 66"
        assertTrue("La canción debe seguir en Favoritos", LocalPlaylistStore.isLiked(song.videoId))
        val updatedPlaylist = LocalPlaylistStore.getPlaylist(playlist.id)
        assertNotNull(updatedPlaylist)
        assertEquals(1, updatedPlaylist!!.songs.size)
        assertEquals(song.videoId, updatedPlaylist.songs.first().videoId)
    }

    /**
     * Escenario 7:
     * Prevención de descargas duplicadas (cuando está en cola, ejecutándose o ya descargada).
     */
    @Test
    fun `duplicate download requests for the same videoId are ignored while queued running or completed`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val fetchCount = AtomicInteger(0)
        val gate = CompletableDeferred<Unit>()

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                fetchCount.incrementAndGet()
                val stream = StreamResolver.Stream("https://example.com/$videoId.m4a", 128, "audio/mp4")
                openSink(stream).use { out ->
                    out.write(ByteArray(1024) { 0x33 })
                    onProgress(512L, 1024L)
                    gate.await()
                    out.write(ByteArray(1024) { 0x33 })
                    onProgress(1024L, 1024L)
                }
                return stream
            }
        }

        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope)

        val song = Song(videoId = "vid_dup_01", title = "Unique Track", artist = "Solo Artist", thumbnailUrl = null)

        // Encolar 3 veces seguidas mientras está en Queued
        Downloads.enqueue(song)
        Downloads.enqueue(song)
        Downloads.enqueue(song)

        testScheduler.runCurrent()
        // Intentar encolar de nuevo mientras está en Running
        Downloads.enqueue(song)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("El motor solo debe haber ejecutado 1 descarga para el mismo videoId", 1, fetchCount.get())
        assertTrue(Downloads.isDownloaded("vid_dup_01"))

        // Intentar encolar de nuevo cuando ya está completada en disco
        Downloads.enqueue(song)
        advanceUntilIdle()
        assertEquals("No debe volver a descargar una canción que ya existe en disco", 1, fetchCount.get())
    }

    /**
     * Escenario 8:
     * Manejo de error de red durante la descarga y recuperación exitosa mediante `retry`.
     */
    @Test
    fun `network failure transitions to Failed state cleans up part file and recovers on retry`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        var shouldFail = true

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream("https://example.com/$videoId.m4a", 128, "audio/mp4")
                openSink(stream).use { out ->
                    out.write(ByteArray(512) { 0x22 })
                    onProgress(512L, 4096L)
                    if (shouldFail) {
                        throw IOException("Network connection reset")
                    }
                    out.write(ByteArray(3584) { 0x22 })
                    onProgress(4096L, 4096L)
                }
                return stream
            }
        }

        val scope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, scope)

        val song = Song(videoId = "vid_retry_01", title = "Resilient Song", artist = "Net Artist", thumbnailUrl = null)

        // Primer intento: falla por error de red
        Downloads.enqueue(song)
        advanceUntilIdle()

        val failedState = Downloads.active.value["vid_retry_01"] as? DownloadState.Failed
        assertNotNull("El estado debe ser Failed tras error de red", failedState)
        assertTrue(failedState!!.reason.contains("Network connection reset"))
        assertFalse(Downloads.isDownloaded("vid_retry_01"))

        // Verificar que no quedó ningún archivo .part corrupto
        val dlDir = DownloadStore.downloadsDir(tempBaseDir)
        assertTrue(dlDir.listFiles().orEmpty().isEmpty())

        // Segundo intento: se restablece la red y el usuario pulsa Reintentar
        shouldFail = false
        Downloads.retry("vid_retry_01")
        advanceUntilIdle()

        assertNull("Tras reintentar con éxito, no debe seguir en active", Downloads.active.value["vid_retry_01"])
        assertTrue("La canción debe estar descargada y verificada", Downloads.isDownloaded("vid_retry_01"))
    }

    /**
     * Escenarios 9 y 10:
     * Prioridad de resolución hacia archivo local descargado frente a resolución online (`bitchord://watch?v=`),
     * y reproducción offline en `MediaControllerRepositoryImpl` sin invocar `StreamResolver` online.
     */
    @Test
    fun `offline playback priority resolves local file URI and MediaControllerRepository plays local file without online resolver`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream("https://example.com/$videoId.m4a", 128, "audio/mp4")
                openSink(stream).use { out ->
                    out.write(ByteArray(2048) { 0x7F })
                    onProgress(2048L, 2048L)
                }
                return stream
            }
        }

        val dlScope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, dlScope)

        val onlineSong = Song(
            videoId = "vid_offline_play_01",
            title = "Offline Highway",
            artist = "Night Runner",
            thumbnailUrl = null,
            albumName = "Dark Asphalt",
            durationText = "4:00"
        )

        // Antes de descargar: la resolución debe apuntar al esquema online bitchord://watch?v=
        assertEquals("bitchord://watch?v=vid_offline_play_01", onlineSong.resolvePlaybackUriString())

        // Descargar la canción
        Downloads.enqueue(onlineSong)
        advanceUntilIdle()

        val expectedLocalUri = Downloads.verifiedSavedUriString("vid_offline_play_01")
        assertNotNull(expectedLocalUri)
        assertTrue(expectedLocalUri!!.startsWith("file:///"))

        // Ahora incluso si pasamos la instancia `onlineSong` (sin localUri explícito, p.ej. desde Buscar, Inicio o una Playlist),
        // `resolvePlaybackUriString()` y `toMediaItem()` resuelven prioritariamente el archivo local verificado!
        assertEquals(expectedLocalUri, onlineSong.resolvePlaybackUriString())
        val mediaItem = onlineSong.toMediaItem()
        assertEquals(expectedLocalUri, mediaItem.resolvedUriString)
        assertEquals(expectedLocalUri, mediaItem.toSong().localUri)

        // Probar reproducción a través de MediaControllerRepositoryImpl
        val fakeBridge = FakeMediaSessionBridge()
        val connector = object : BitChordSessionConnector {
            override fun connect(
                context: Context,
                onConnected: (BitChordMediaSessionBridge) -> Unit,
                onDisconnected: () -> Unit,
                onError: (Throwable) -> Unit,
            ): AutoCloseable {
                onConnected(fakeBridge)
                return AutoCloseable {}
            }
        }

        val repoJob = SupervisorJob()
        val repoScope = CoroutineScope(testDispatcher + repoJob)
        val repository = MediaControllerRepositoryImpl(
            context = TestContextWithStorage(prefs, tempBaseDir),
            connector = connector,
            coroutineScope = repoScope
        )

        try {
            repository.connect()
            repository.playSong(onlineSong)

            val playedItem = fakeBridge.lastSetMediaItem
            assertNotNull("El MediaItem debe haberse enviado al reproductor Media3", playedItem)
            assertTrue(fakeBridge.lastPlayWhenReady)
            assertEquals("vid_offline_play_01", playedItem!!.mediaId)
            assertEquals("El MediaItem enviado a ExoPlayer debe usar el archivo local file:/// sin invocar StreamResolver", expectedLocalUri, playedItem.resolvedUriString)
            assertEquals("Offline Highway", repository.currentTrack.value.title)
            assertEquals("Night Runner", repository.currentTrack.value.artist)

            // Si ahora borramos la descarga y volvemos a reproducir la canción, debe hacer fallback limpio a bitchord://watch?v=
            Downloads.delete("vid_offline_play_01")
            repository.playSong(onlineSong)
            val fallbackItem = fakeBridge.lastSetMediaItem
            assertNotNull(fallbackItem)
            assertEquals("bitchord://watch?v=vid_offline_play_01", fallbackItem!!.resolvedUriString)
        } finally {
            repository.disconnect()
            repoJob.cancel()
        }
    }

    /**
     * Escenarios 12 y 13:
     * Integración con `MusicLibraryViewModel` (`BROWSE_ID_DOWNLOADS = "local:downloads"`)
     * y compatibilidad con las 5 pestañas de navegación de Music Center (`HOME`, `EXPLORE`, `SEARCH`, `LIBRARY`, `PLAYER`).
     */
    @Test
    fun `MusicLibraryViewModel exposes downloaded songs collection and preserves 5-tab navigation`() = runTest(testDispatcher) {
        val prefs = InMemorySharedPreferences()
        LocalPlaylistStore.init(TestContextWithStorage(prefs, tempBaseDir))

        val fakeFetcher = object : Downloader.StreamFetcher {
            override suspend fun resolveAndFetch(
                videoId: String,
                maxKbps: Int,
                requireM4a: Boolean,
                openSink: (StreamResolver.Stream) -> OutputStream,
                onProgress: (written: Long, total: Long) -> Unit
            ): StreamResolver.Stream {
                val stream = StreamResolver.Stream("https://example.com/$videoId.m4a", 128, "audio/mp4")
                openSink(stream).use { out ->
                    out.write(ByteArray(3072) { 0x66 })
                    onProgress(3072L, 3072L)
                }
                return stream
            }
        }

        val dlScope = CoroutineScope(SupervisorJob() + testDispatcher)
        Downloads.initForTest(prefs, tempBaseDir, fakeFetcher, dlScope)

        val viewModel = MusicLibraryViewModel(
            dataSource = OfflineLibraryDataSource,
            ioDispatcher = testDispatcher
        )

        val song = Song(
            videoId = "vid_vm_dl_01",
            title = "Autobahn Telemetry",
            artist = "Kraftwerk Tribute",
            thumbnailUrl = null,
            durationText = "5:10"
        )

        viewModel.downloadSong(context = null, song = song)
        advanceUntilIdle()

        assertTrue(viewModel.isSongDownloaded("vid_vm_dl_01"))
        assertEquals(1, viewModel.downloadedSongs.value.size)

        // Verificar que `localFolders` incluye "Canciones descargadas (Offline)" con browseId = "local:downloads"
        val downloadsFolder = viewModel.localFolders.value.firstOrNull {
            it.browseId == MusicLibraryViewModel.BROWSE_ID_DOWNLOADS
        }
        assertNotNull("La carpeta rápida de descargas offline debe existir en Biblioteca", downloadsFolder)
        assertTrue(downloadsFolder!!.subtitle.contains("1 canción disponible offline"))

        // Abrir la colección de descargas en MusicLibraryViewModel
        viewModel.openCollection(
            browseId = MusicLibraryViewModel.BROWSE_ID_DOWNLOADS,
            title = downloadsFolder.title,
            subtitle = downloadsFolder.subtitle,
            thumbnailUrl = null
        )

        val opened = viewModel.selectedCollection.value
        assertNotNull(opened)
        assertEquals(MusicLibraryViewModel.BROWSE_ID_DOWNLOADS, opened!!.browseId)
        val songsInCollection = (opened.songsState as? UiState.Success)?.data.orEmpty()
        assertEquals(1, songsInCollection.size)
        assertEquals("vid_vm_dl_01", songsInCollection.first().videoId)
        assertNotNull(songsInCollection.first().localUri)

        // Verificar las 5 pestañas de navegación de MusicCenterDestination (Escenario 13)
        val destinations = MusicCenterDestination.entries
        assertEquals(
            listOf(
                MusicCenterDestination.HOME,
                MusicCenterDestination.EXPLORE,
                MusicCenterDestination.SEARCH,
                MusicCenterDestination.LIBRARY,
                MusicCenterDestination.PLAYER
            ),
            destinations
        )
    }
}

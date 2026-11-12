package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Duration

/**
 * Descarga del grafo Valhalla de Tepic (valhalla_tiles.tar) desde el release
 * fijo `offline-packs` de GitHub, con verificación SHA-256 contra
 * catalogue.json e instalación atómica (se descarga a .tmp y solo se
 * renombra al final si el hash coincide). También acepta el archivo
 * cargado a mano por Daniel (Drive/USB) en filesDir/offline/.
 *
 * El paquete NO va en git ni en assets: es demasiado grande para el push
 * por API y se actualiza sin reinstalar el APK.
 */
sealed interface RoutingPackState {
    data object NotDownloaded : RoutingPackState
    data class Downloading(val fraction: Float) : RoutingPackState
    data object Verifying : RoutingPackState
    data object Ready : RoutingPackState
    data class Error(val message: String) : RoutingPackState
}

class OfflinePackDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, OfflineNavConstants.OFFLINE_DIR)
    private val targetFile = File(dir, OfflineNavConstants.VALHALLA_TAR_NAME)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<RoutingPackState>(RoutingPackState.NotDownloaded)
    val state: StateFlow<RoutingPackState> = _state.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(Duration.ofMinutes(10))
        .build()

    @Serializable
    private data class CatalogueAsset(val name: String = "", val sha256: String = "", val size: Long = 0)

    @Serializable
    private data class Catalogue(val assets: List<CatalogueAsset> = emptyList())

    fun isInstalled(): Boolean = targetFile.exists() && targetFile.length() > 0

    fun installedFile(): File = targetFile

    fun refreshState() {
        _state.value = if (isInstalled()) {
            RoutingPackState.Ready
        } else {
            RoutingPackState.NotDownloaded
        }
    }

    /** Descarga el catálogo, luego el .tar, verifica el hash e instala. */
    suspend fun downloadPack(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _state.value = RoutingPackState.Downloading(0f)
                val catalogue = fetchCatalogue()
                    ?: throw IllegalStateException("no se pudo leer el catálogo de paquetes")
                val asset = catalogue.assets.firstOrNull {
                    it.name == OfflineNavConstants.VALHALLA_TAR_NAME
                } ?: throw IllegalStateException("el catálogo no trae el paquete de rutas")
                dir.mkdirs()
                val tmp = File(dir, OfflineNavConstants.VALHALLA_TAR_NAME + ".tmp")
                downloadTo(OfflineNavConstants.PACKS_BASE_URL + asset.name, tmp, asset.size)
                _state.value = RoutingPackState.Verifying
                val actualSha = sha256(tmp)
                if (!actualSha.equals(asset.sha256, ignoreCase = true)) {
                    tmp.delete()
                    throw IllegalStateException("el paquete descargado está corrupto (SHA-256 no coincide)")
                }
                if (!tmp.renameTo(targetFile)) {
                    tmp.delete()
                    throw IllegalStateException("no se pudo instalar el paquete")
                }
                _state.value = RoutingPackState.Ready
                true
            } catch (e: Exception) {
                Log.w(TAG, "Descarga del paquete de rutas falló: ${e.message}", e)
                _state.value = RoutingPackState.Error("No se pudo descargar el paquete de rutas")
                false
            }
        }
    }

    private fun fetchCatalogue(): Catalogue? {
        val request = Request.Builder()
            .url(OfflineNavConstants.CATALOGUE_URL)
            .header("User-Agent", "DivineDeck/1.0 (Android car launcher)")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return json.decodeFromString<Catalogue>(body)
        }
    }

    private fun downloadTo(url: String, dest: File, expectedSize: Long) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DivineDeck/1.0 (Android car launcher)")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("descarga HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("respuesta vacía")
            val total = expectedSize.takeIf { it > 0 } ?: body.contentLength()
            var downloaded = 0L
            FileOutputStream(dest).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            _state.value = RoutingPackState.Downloading(
                                (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "OfflinePackDl"
    }
}

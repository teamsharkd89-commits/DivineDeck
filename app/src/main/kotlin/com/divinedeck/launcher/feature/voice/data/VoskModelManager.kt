package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado del modelo de voz sin internet (Vosk, español).
 * Lo observan Ajustes (línea de estado) y [VoiceAssistant] (para esperar
 * a que el modelo esté listo antes de escuchar sin internet).
 */
sealed interface VoskModelState {
    /** Nunca se ha descargado el modelo. */
    data object NotDownloaded : VoskModelState

    /** Descargando. percent = -1 cuando el servidor no informa el tamaño. */
    data class Downloading(val percent: Int) : VoskModelState

    /** Modelo descomprimido y listo para usar sin internet. */
    data object Ready : VoskModelState

    /** La descarga o descompresión falló. message ya está en español. */
    data class Error(val message: String) : VoskModelState
}

/**
 * Descarga (primera vez) y custodia del modelo español de Vosk.
 *
 * - El modelo (~40 MB) NO va en el APK ni en el repo: se descarga una sola
 *   vez desde alphacephei.com (gratis, sin clave ni cuenta) y se guarda en
 *   el almacenamiento interno de la app.
 * - Ruta del modelo: `filesDir/vosk-model-small-es-0.42/` (la carpeta tal
 *   como viene dentro del zip; contiene `am/final.mdl`, `graph/`, `conf/`
 *   e `ivector/`; descomprimido ocupa ~60 MB).
 * - La descarga corre en Dispatchers.IO con su propio scope: si el usuario
 *   cierra Ajustes o suelta el micrófono, la descarga sigue hasta terminar.
 * - Ante cualquier fallo se borra lo parcial, se publica [VoskModelState.Error]
 *   con el motivo en español y NUNCA se tumba la app.
 *
 * No es escucha continua ni palabra de activación: esto solo descarga el
 * modelo que el asistente de botón usa cuando no hay internet.
 */
object VoskModelManager {

    /** Zip del modelo pequeño en español, verificado HTTP 200. */
    const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"

    /** Nombre de la carpeta del modelo dentro del zip y en filesDir. */
    const val MODEL_DIR_NAME = "vosk-model-small-es-0.42"

    private const val ZIP_FILE_NAME = "vosk-model-small-es-0.42.zip"

    /** Archivo cuya presencia certifica que el modelo está completo. */
    private const val MODEL_MARKER = "am/final.mdl"

    private val _state = MutableStateFlow<VoskModelState>(VoskModelState.NotDownloaded)
    val state: StateFlow<VoskModelState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Carpeta del modelo en el almacenamiento interno de la app. */
    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    /** true si el modelo está descomprimido y completo. */
    fun isReady(context: Context): Boolean =
        File(modelDir(context), MODEL_MARKER).exists()

    /**
     * Revalida el estado contra el disco (por si el modelo ya se descargó
     * en una sesión anterior). No toca una descarga en curso.
     */
    fun refreshState(context: Context) {
        if (_state.value is VoskModelState.Downloading) return
        _state.value = if (isReady(context)) VoskModelState.Ready
        else VoskModelState.NotDownloaded
    }

    /**
     * Descarga el modelo si hace falta. Idempotente: si ya está listo o ya
     * se está descargando, no hace nada.
     */
    fun ensureModel(context: Context) {
        val appContext = context.applicationContext
        if (isReady(appContext)) {
            _state.value = VoskModelState.Ready
            return
        }
        if (_state.value is VoskModelState.Downloading) return
        _state.value = VoskModelState.Downloading(-1)
        scope.launch {
            try {
                downloadAndUnzip(appContext)
                if (isReady(appContext)) {
                    _state.value = VoskModelState.Ready
                } else {
                    deletePartial(appContext)
                    _state.value = VoskModelState.Error(
                        "El modelo descargado está incompleto"
                    )
                }
            } catch (e: Exception) {
                deletePartial(appContext)
                _state.value = VoskModelState.Error(friendlyMessage(e))
            }
        }
    }

    /** Reintenta la descarga tras un error. */
    fun retry(context: Context) = ensureModel(context)

    private fun friendlyMessage(e: Exception): String = when (e) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "Sin internet para descargar el modelo de voz"
        is IOException ->
            "No se pudo descargar el modelo de voz (${e.message ?: "error de red"})"
        else ->
            "No se pudo descargar el modelo de voz"
    }

    private fun deletePartial(appContext: Context) {
        try {
            modelDir(appContext).deleteRecursively()
        } catch (_: Exception) {
        }
        try {
            File(appContext.cacheDir, ZIP_FILE_NAME).delete()
        } catch (_: Exception) {
        }
    }

    private fun downloadAndUnzip(appContext: Context) {
        val zipFile = File(appContext.cacheDir, ZIP_FILE_NAME)
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            connect()
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong // -1 si el servidor no lo informa
            var downloaded = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                _state.value = VoskModelState.Downloading(percent)
                            }
                        }
                    }
                }
            }
            // El zip trae la carpeta vosk-model-small-es-0.42/ en la raíz.
            unzip(zipFile, appContext.filesDir)
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
            try {
                zipFile.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Anti zip-slip: ninguna entrada puede salirse de filesDir.
                require(
                    outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator)
                ) { "Entrada no válida en el zip del modelo" }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}

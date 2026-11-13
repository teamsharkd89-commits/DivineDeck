package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado del modelo de voz sin internet (Sherpa-ONNX + Whisper tiny, español).
 * Lo observan Ajustes (línea de estado) y [VoiceAssistant] (para esperar
 * a que el modelo esté listo antes de escuchar sin internet).
 */
sealed interface SherpaModelState {
    /** Nunca se ha descargado el modelo. */
    data object NotDownloaded : SherpaModelState

    /** Descargando. percent = -1 cuando el servidor no informa el tamaño. */
    data class Downloading(val percent: Int) : SherpaModelState

    /** Los 3 archivos del modelo están completos y listos para usar sin internet. */
    data object Ready : SherpaModelState

    /** La descarga falló. message ya está en español. */
    data class Error(val message: String) : SherpaModelState
}

/**
 * Descarga (primera vez) y custodia del modelo español de Sherpa-ONNX.
 *
 * - El modelo NO va en el APK ni en el repo: se descarga una sola vez
 *   desde Hugging Face (gratis, sin clave ni cuenta) y se guarda en el
 *   almacenamiento interno de la app.
 * - Modelo: `csukuangfj/sherpa-onnx-whisper-tiny` (Whisper tiny int8,
 *   multilingüe, incluye español; el repo de Hugging Face no declara
 *   licencia, el Whisper original de OpenAI es MIT). Son 3 archivos,
 *   ~104 MB en total:
 *   - `tiny-encoder.int8.onnx` (~12,3 MB)
 *   - `tiny-decoder.int8.onnx` (~85,7 MB)
 *   - `tiny-tokens.txt` (~0,8 MB)
 * - Ruta del modelo: `filesDir/sherpa-whisper-tiny/`.
 * - La descarga corre en Dispatchers.IO con su propio scope: si el usuario
 *   cierra Ajustes o suelta el micrófono, la descarga sigue hasta terminar.
 * - Ante cualquier fallo se borra lo parcial, se publica
 *   [SherpaModelState.Error] con el motivo en español y NUNCA se tumba la app.
 * - Cada archivo se baja a un `.tmp` y se renombra solo al completarse, así
 *   un archivo a medias nunca pasa por completo.
 *
 * No es escucha continua ni palabra de activación: esto solo descarga el
 * modelo que el asistente de botón usa cuando no hay internet.
 */
object SherpaModelManager {

    /** Base pública de Hugging Face con los archivos del modelo. */
    const val MODEL_BASE_URL =
        "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/resolve/main"

    /** Nombre de la carpeta del modelo en filesDir. */
    const val MODEL_DIR_NAME = "sherpa-whisper-tiny"

    const val ENCODER_FILE_NAME = "tiny-encoder.int8.onnx"
    const val DECODER_FILE_NAME = "tiny-decoder.int8.onnx"
    const val TOKENS_FILE_NAME = "tiny-tokens.txt"

    /** Archivos del modelo con su tamaño esperado en bytes (verificado). */
    val MODEL_FILES: List<Pair<String, Long>> = listOf(
        ENCODER_FILE_NAME to 12_937_772L,
        DECODER_FILE_NAME to 89_855_401L,
        TOKENS_FILE_NAME to 816_730L
    )

    /** Tamaño total esperado de la descarga (~104 MB). */
    val TOTAL_BYTES: Long = MODEL_FILES.sumOf { it.second }

    private val _state = MutableStateFlow<SherpaModelState>(SherpaModelState.NotDownloaded)
    val state: StateFlow<SherpaModelState> = _state.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Carpeta del modelo en el almacenamiento interno de la app. */
    fun modelDir(context: Context): File = File(context.filesDir, MODEL_DIR_NAME)

    /** true si los 3 archivos están presentes con su tamaño esperado. */
    fun isReady(context: Context): Boolean {
        val dir = modelDir(context)
        return MODEL_FILES.all { (name, size) ->
            val file = File(dir, name)
            file.isFile && file.length() == size
        }
    }

    /**
     * Revalida el estado contra el disco (por si el modelo ya se descargó
     * en una sesión anterior). No toca una descarga en curso.
     */
    fun refreshState(context: Context) {
        if (_state.value is SherpaModelState.Downloading) return
        _state.value = if (isReady(context)) SherpaModelState.Ready
        else SherpaModelState.NotDownloaded
    }

    /**
     * Descarga el modelo si hace falta. Idempotente: si ya está listo o ya
     * se está descargando, no hace nada.
     */
    fun ensureModel(context: Context) {
        val appContext = context.applicationContext
        if (isReady(appContext)) {
            _state.value = SherpaModelState.Ready
            return
        }
        if (_state.value is SherpaModelState.Downloading) return
        _state.value = SherpaModelState.Downloading(-1)
        scope.launch {
            try {
                downloadAll(appContext)
                if (isReady(appContext)) {
                    _state.value = SherpaModelState.Ready
                } else {
                    deletePartial(appContext)
                    _state.value = SherpaModelState.Error(
                        "El modelo descargado está incompleto"
                    )
                }
            } catch (e: Exception) {
                deletePartial(appContext)
                _state.value = SherpaModelState.Error(friendlyMessage(e))
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
    }

    /**
     * Descarga los 3 archivos, uno por uno, con progreso global (0-100 %
     * sobre el total de ~104 MB). Un archivo ya completo con su tamaño
     * exacto no se vuelve a bajar.
     */
    private fun downloadAll(appContext: Context) {
        val dir = modelDir(appContext)
        dir.mkdirs()
        var downloadedTotal = 0L
        // Lo ya completo cuenta en el progreso desde el inicio.
        for ((name, size) in MODEL_FILES) {
            val file = File(dir, name)
            if (file.isFile && file.length() == size) downloadedTotal += size
        }
        var lastPercent = -1
        for ((name, size) in MODEL_FILES) {
            val target = File(dir, name)
            if (target.isFile && target.length() == size) continue
            val tmp = File(dir, "$name.tmp")
            downloadOne("$MODEL_BASE_URL/$name", tmp) { downloadedNow ->
                if (TOTAL_BYTES > 0) {
                    val percent =
                        (((downloadedTotal + downloadedNow) * 100) / TOTAL_BYTES)
                            .toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.value = SherpaModelState.Downloading(percent)
                    }
                }
            }
            if (!tmp.isFile || tmp.length() != size) {
                try {
                    tmp.delete()
                } catch (_: Exception) {
                }
                throw IOException("El archivo $name quedó incompleto")
            }
            if (!tmp.renameTo(target)) {
                try {
                    tmp.delete()
                } catch (_: Exception) {
                }
                throw IOException("No se pudo guardar el archivo $name")
            }
            downloadedTotal += size
        }
        _state.value = SherpaModelState.Downloading(100)
    }

    /** Descarga una URL a un archivo temporal, informando los bytes bajados. */
    private fun downloadOne(url: String, tmpFile: File, onProgress: (Long) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
            connect()
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded)
                    }
                }
            }
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}

package com.divinedeck.launcher.feature.voice.data

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Atrapador de crashes de la app.
 *
 * Instala un [Thread.setDefaultUncaughtExceptionHandler] que guarda el
 * último crash (fecha y hora, último [VoiceState] conocido y stack trace
 * completo) en `filesDir/crash-ultimo-error.txt` (solo el último: el
 * archivo se sobrescribe en cada crash) y DESPUÉS deja que el crash
 * siga su curso normal (se encadena el handler anterior; si no había
 * ninguno, se mata el proceso como haría el sistema). No cambia el
 * comportamiento normal de la app: solo deja constancia del error para
 * poder leerlo en Ajustes > DIVERSIÓN > "Último error de la app" y
 * pasárselo a quien arregla la app.
 *
 * Instalar una sola vez y lo antes posible (DivineDeckApp.onCreate,
 * antes que cualquier otro subsistema). Nunca lanza.
 */
object CrashCatcher {

    const val CRASH_FILE_NAME = "crash-ultimo-error.txt"

    /** Último estado del asistente de voz, actualizado por [VoiceAssistant]. */
    @Volatile
    var lastVoiceState: String = "Idle"

    @Volatile
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            installed = true
            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeCrash(appContext, throwable)
                } catch (_: Throwable) {
                }
                try {
                    if (previousHandler != null) {
                        previousHandler.uncaughtException(thread, throwable)
                    } else {
                        Process.killProcess(Process.myPid())
                        exitProcess(10)
                    }
                } catch (_: Throwable) {
                    try {
                        Process.killProcess(Process.myPid())
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    /** Contenido del archivo del último crash, o null si no hay ninguno. */
    fun readLastCrash(context: Context): String? {
        return try {
            val file = File(context.filesDir, CRASH_FILE_NAME)
            if (!file.isFile) return null
            val text = file.readText().trim()
            if (text.isBlank()) null else text
        } catch (_: Throwable) {
            null
        }
    }

    private fun writeCrash(appContext: Context, throwable: Throwable) {
        val timestamp = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es", "MX")).format(Date())
        } catch (_: Throwable) {
            Date().toString()
        }
        val stackTrace = try {
            StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        } catch (_: Throwable) {
            throwable.toString()
        }
        val text = buildString {
            append("Fecha: ").append(timestamp).append('\n')
            append("Último estado de voz: ").append(lastVoiceState).append('\n')
            append("Error: ").append(throwable.toString()).append('\n')
            append(stackTrace)
        }
        File(appContext.filesDir, CRASH_FILE_NAME).writeText(text)
    }
}

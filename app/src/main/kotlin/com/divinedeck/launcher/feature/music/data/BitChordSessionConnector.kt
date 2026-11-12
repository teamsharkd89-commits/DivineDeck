package com.divinedeck.launcher.feature.music.data

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * Conector de sesión para enlazar de manera asíncrona con el servicio multimedia Media3.
 * Prioriza el servicio multimedia interno de DIVINE DECK ([DivineDeckPlaybackService]).
 */
interface BitChordSessionConnector {
    fun connect(
        context: Context,
        onConnected: (BitChordMediaSessionBridge) -> Unit,
        onDisconnected: () -> Unit,
        onError: (Throwable) -> Unit
    ): AutoCloseable
}

/**
 * Implementación de producción para Android que utiliza MediaController.Builder y SessionToken.
 * Por defecto conecta con el servicio multimedia interno de DIVINE DECK en el mismo proceso/APK.
 */
class AndroidBitChordSessionConnector(
    private val targetPackage: String? = null,
    private val targetService: String? = null
) : BitChordSessionConnector {

    companion object {
        const val INTERNAL_PLAYBACK_SERVICE = "com.divinedeck.launcher.feature.music.playback.DivineDeckPlaybackService"
        const val BITCHORD_PACKAGE = "com.music.bitchord"
        const val BITCHORD_PLAYBACK_SERVICE = "com.music.bitchord.playback.PlaybackService"
    }

    override fun connect(
        context: Context,
        onConnected: (BitChordMediaSessionBridge) -> Unit,
        onDisconnected: () -> Unit,
        onError: (Throwable) -> Unit
    ): AutoCloseable {
        var released = false
        val pkg = targetPackage ?: context.packageName
        val srv = targetService ?: INTERNAL_PLAYBACK_SERVICE
        val component = ComponentName(pkg, srv)
        val sessionToken = try {
            SessionToken(context, component)
        } catch (t: Throwable) {
            onError(t)
            return AutoCloseable {}
        }

        val controllerListener = object : MediaController.Listener {
            override fun onDisconnected(controller: MediaController) {
                if (!released) {
                    onDisconnected()
                }
            }
        }

        val controllerFuture = try {
            MediaController.Builder(context, sessionToken)
                .setListener(controllerListener)
                .buildAsync()
        } catch (t: Throwable) {
            onError(t)
            return AutoCloseable {}
        }

        val executor = ContextCompat.getMainExecutor(context)
        controllerFuture.addListener(
            {
                if (released) {
                    MediaController.releaseFuture(controllerFuture)
                    return@addListener
                }
                try {
                    val controller = controllerFuture.get()
                    if (controller != null && controller.isConnected) {
                        onConnected(AndroidMedia3SessionBridge(controller))
                    } else {
                        onError(IllegalStateException("MediaController finalizó sin conexión válida"))
                    }
                } catch (e: Throwable) {
                    onError(e)
                }
            },
            executor
        )

        return AutoCloseable {
            released = true
            try {
                MediaController.releaseFuture(controllerFuture)
            } catch (_: Throwable) {}
        }
    }
}

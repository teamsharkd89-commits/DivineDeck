package com.divinedeck.launcher.feature.navcenter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import com.divinedeck.launcher.MainActivity
import com.divinedeck.launcher.R
import com.divinedeck.launcher.core.designsystem.theme.ThemePreset
import com.divinedeck.launcher.core.designsystem.theme.buildPaletteForTheme
import com.divinedeck.launcher.feature.settings.data.SettingsRepositoryImpl

/**
 * Botón flotante "Volver a DivineDeck" sobre Google Maps.
 *
 * Se inicia al abrir Google Maps desde Navegación (solo si el permiso de
 * superposición está concedido) y se detiene al tocar el botón o cuando
 * MainActivity vuelve a primer plano. No tiene menú ni acciones extra:
 * un toque vuelve a la pantalla principal (Cockpit) y oculta el botón.
 *
 * IMPORTANTE: el overlay es una vista Android clásica (FrameLayout +
 * ImageView), NO un ComposeView. Un ComposeView añadido desde un Service
 * nunca recibe un LifecycleOwner y su composición jamás se ejecuta, por
 * lo que el botón resultaba invisible aunque el servicio estuviera vivo
 * (build 29). La única importación de Compose que queda es la conversión
 * de color `toArgb`, que no necesita runtime de UI.
 *
 * Servicio en primer plano de uso especial (specialUse): Android 14+
 * exige un tipo declarado; no encaja en los tipos estándar (no es
 * multimedia, ubicación ni datos), así que usa specialUse con su permiso
 * FOREGROUND_SERVICE_SPECIAL_USE. La notificación también devuelve al
 * inicio si se toca.
 */
class ReturnHomeFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (Settings.canDrawOverlays(this)) {
            if (!showOverlay()) {
                // No se pudo añadir la vista: no dejar el servicio vivo.
                stopSelf()
            }
        } else {
            // Sin permiso no hay nada que mostrar: no dejar el servicio vivo.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        isRunning = false
        super.onDestroy()
    }

    /** Color de acento del tema guardado como ints ARGB; ámbar #FFB547 si algo falla. */
    private fun resolveAccent(): Pair<Int, Int> {
        return try {
            val prefs = getSharedPreferences(
                SettingsRepositoryImpl.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val preset = ThemePreset.fromId(
                prefs.getString(SettingsRepositoryImpl.KEY_THEME_PRESET, null)
            )
            val customHex = prefs.getLong(
                SettingsRepositoryImpl.KEY_CUSTOM_ACCENT_HEX,
                SettingsRepositoryImpl.DEFAULT_CUSTOM_ACCENT_HEX
            )
            val palette = buildPaletteForTheme(preset, customHex)
            palette.accent.toArgb() to palette.onAccent.toArgb()
        } catch (_: Exception) {
            0xFFFFB547.toInt() to 0xFF080B12.toInt()
        }
    }

    /** Construye el botón flotante con vistas clásicas y lo añade al WindowManager. */
    private fun showOverlay(): Boolean {
        val (accentArgb, onAccentArgb) = resolveAccent()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val density = resources.displayMetrics.density
        val sizePx = (72 * density + 0.5f).toInt()
        val iconPx = (36 * density + 0.5f).toInt()

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentArgb)
            }
            // Sombra redonda: elevation + OutlineProvider ovalado.
            elevation = 8 * density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            clipToOutline = true
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_home_floating)
                    setColorFilter(onAccentArgb)
                    layoutParams = FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER)
                    contentDescription = "Volver a DivineDeck"
                }
            )
            setOnClickListener { returnHome() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }
        overlayView = container
        return try {
            wm.addView(container, params)
            true
        } catch (e: Exception) {
            // El permiso pudo revocarse entre la comprobación y el addView.
            Log.e(TAG, "No se pudo añadir el botón flotante", e)
            overlayView = null
            false
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
                // La vista ya no estaba añadida; nada que quitar.
            }
        }
        overlayView = null
        windowManager = null
    }

    /** Un toque: volver al inicio de DivineDeck y ocultar el botón. */
    private fun returnHome() {
        startActivity(homeIntent())
        stopSelf()
    }

    private fun homeIntent(): Intent {
        return Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NAVIGATE_HOME, true)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DivineDeck",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Botón flotante para volver a DivineDeck desde Google Maps"
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            homeIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DivineDeck")
            .setContentText("Toca para volver a la pantalla principal")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        /** Extra que pide a MainActivity navegar al Cockpit al recibir el intent. */
        const val EXTRA_NAVIGATE_HOME = "navigate_home"

        private const val TAG = "ReturnHomeFloating"
        private const val CHANNEL_ID = "divinedeck_return_home"
        private const val NOTIFICATION_ID = 1001

        /** True mientras el servicio está vivo; MainActivity lo usa en onResume. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * Arranca el servicio del botón flotante.
         * @return true si el servicio se arrancó (o ya estaba vivo); false si
         * Android rechazó el arranque en primer plano (p. ej. la app ya estaba
         * en segundo plano en Android 12+) u ocurrió otro error. Nunca lanza.
         */
        fun start(context: Context): Boolean {
            if (isRunning) return true
            return try {
                context.startForegroundService(
                    Intent(context, ReturnHomeFloatingService::class.java)
                )
                true
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo arrancar el botón flotante", e)
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, ReturnHomeFloatingService::class.java)
            )
        }
    }
}

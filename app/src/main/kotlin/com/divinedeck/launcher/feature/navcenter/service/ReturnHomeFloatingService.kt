package com.divinedeck.launcher.feature.navcenter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.divinedeck.launcher.MainActivity
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
 * Servicio en primer plano de uso especial (specialUse): Android 14+
 * exige un tipo declarado; no encaja en los tipos estándar (no es
 * multimedia, ubicación ni datos), así que usa specialUse con su permiso
 * FOREGROUND_SERVICE_SPECIAL_USE. La notificación también devuelve al
 * inicio si se toca.
 */
class ReturnHomeFloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
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

    /** Color de acento del tema guardado; ámbar #FFB547 si algo falla. */
    private fun resolveAccent(): Pair<Color, Color> {
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
            palette.accent to palette.onAccent
        } catch (_: Exception) {
            Color(0xFFFFB547) to Color(0xFF080B12)
        }
    }

    private fun showOverlay() {
        val (accent, onAccent) = resolveAccent()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val view = ComposeView(this).apply {
            setContent {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(8.dp, CircleShape)
                        .background(accent, CircleShape)
                        .clickable { returnHome() },
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Volver a DivineDeck",
                        tint = onAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
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
        overlayView = view
        wm.addView(view, params)
    }

    private fun hideOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
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

        private const val CHANNEL_ID = "divinedeck_return_home"
        private const val NOTIFICATION_ID = 1001

        /** True mientras el servicio está vivo; MainActivity lo usa en onResume. */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            context.startForegroundService(
                Intent(context, ReturnHomeFloatingService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, ReturnHomeFloatingService::class.java)
            )
        }
    }
}

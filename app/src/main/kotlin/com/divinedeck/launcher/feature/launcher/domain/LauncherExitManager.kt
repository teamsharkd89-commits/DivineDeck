package com.divinedeck.launcher.feature.launcher.domain

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Gestor de salida controlada de DIVINE DECK hacia el escritorio del sistema (Samsung One UI Home).
 * 
 * Regla de negocio crítica:
 * DIVINE DECK mantiene su condición de Launcher HOME predeterminado.
 * La salida hacia One UI es una apertura explícita secundaria; al presionar Inicio (Home)
 * o reiniciar la tablet, el sistema operativo Android regresa automáticamente a DIVINE DECK.
 */
class LauncherExitManager(
    private val context: Context
) {
    companion object {
        const val SAMSUNG_ONE_UI_PACKAGE = "com.sec.android.app.launcher"
        const val SAMSUNG_ONE_UI_ACTIVITY = "com.sec.android.app.launcher.activities.LauncherActivity"
    }

    sealed interface ExitResult {
        data object LaunchedSamsungOneUi : ExitResult
        data class LaunchedAlternativeLauncher(val packageName: String) : ExitResult
        data object FallbackToSystemSettings : ExitResult
        data class Error(val message: String) : ExitResult
    }

    /**
     * Intenta abrir el lanzador del sistema (Samsung One UI) sin alterar el launcher predeterminado.
     */
    fun exitToSystemDesktop(): ExitResult {
        // 1. Intento explícito de apertura para Samsung One UI Home
        try {
            val samsungIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                component = ComponentName(SAMSUNG_ONE_UI_PACKAGE, SAMSUNG_ONE_UI_ACTIVITY)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            if (context.packageManager.resolveActivity(samsungIntent, 0) != null) {
                context.startActivity(samsungIntent)
                return ExitResult.LaunchedSamsungOneUi
            }
        } catch (_: Exception) {
            // Continúa al siguiente método de resolución compatible
        }

        // 2. Búsqueda dinámica de otro lanzador OEM instalado (AOSP Launcher3, Google Launcher, etc.)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolvedList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            val alternative = resolvedList.firstOrNull { it.activityInfo.packageName != context.packageName }
            if (alternative != null) {
                val altIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    component = ComponentName(alternative.activityInfo.packageName, alternative.activityInfo.name)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(altIntent)
                return ExitResult.LaunchedAlternativeLauncher(alternative.activityInfo.packageName)
            }
        } catch (_: Exception) {
            // Continúa a la opción oficial del sistema
        }

        // 3. Mecanismo oficial de Android: Ajustes de Pantalla de Inicio / Apps Predeterminadas
        return try {
            val settingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
            ExitResult.FallbackToSystemSettings
        } catch (e: Exception) {
            ExitResult.Error(e.message ?: "No se pudo invocar el lanzador del sistema")
        }
    }
}

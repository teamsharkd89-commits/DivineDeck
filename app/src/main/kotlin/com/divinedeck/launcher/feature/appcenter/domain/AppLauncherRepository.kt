package com.divinedeck.launcher.feature.appcenter.domain

import android.content.Context
import com.divinedeck.launcher.feature.appcenter.domain.model.InstalledApp
import kotlinx.coroutines.flow.StateFlow

/**
 * Contrato para listar y abrir aplicaciones del sistema instaladas en la tablet.
 */
interface AppLauncherRepository {
    val installedApps: StateFlow<List<InstalledApp>>

    fun refreshApps(context: Context)
    fun launchApp(context: Context, packageName: String): Boolean
}

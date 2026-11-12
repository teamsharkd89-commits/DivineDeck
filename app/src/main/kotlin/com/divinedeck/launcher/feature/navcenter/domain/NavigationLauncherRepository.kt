package com.divinedeck.launcher.feature.navcenter.domain

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Contrato para lanzamiento de navegación vehicular (Google Maps / Waze).
 * Utiliza los estándares de Intent de Android sin costos de API comercial.
 */
interface NavigationLauncherRepository {
    fun launchGoogleMaps(context: Context): Boolean
    fun launchNavigationTo(context: Context, queryOrAddress: String): Boolean
    fun isGoogleMapsInstalled(context: Context): Boolean
}

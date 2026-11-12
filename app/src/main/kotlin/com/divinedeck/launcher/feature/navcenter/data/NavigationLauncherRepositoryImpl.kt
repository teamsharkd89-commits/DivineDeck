package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.divinedeck.launcher.feature.navcenter.domain.NavigationLauncherRepository

class NavigationLauncherRepositoryImpl : NavigationLauncherRepository {

    override fun launchGoogleMaps(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // Fallback a cualquier app de mapas
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun launchNavigationTo(context: Context, queryOrAddress: String): Boolean {
        return try {
            val uri = Uri.parse("google.navigation:q=${Uri.encode(queryOrAddress)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun isGoogleMapsInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.google.android.apps.maps", 0) != null
        } catch (_: Exception) {
            false
        }
    }
}

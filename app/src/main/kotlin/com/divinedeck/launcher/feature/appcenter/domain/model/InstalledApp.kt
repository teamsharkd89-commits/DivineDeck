package com.divinedeck.launcher.feature.appcenter.domain.model

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean = false,
    val icon: Drawable? = null
)

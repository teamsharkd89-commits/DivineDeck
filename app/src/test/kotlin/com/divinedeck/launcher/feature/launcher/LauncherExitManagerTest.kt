package com.divinedeck.launcher.feature.launcher

import com.divinedeck.launcher.feature.launcher.domain.LauncherExitManager
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherExitManagerTest {

    @Test
    fun `samsung one ui constants match official One UI launcher specifications`() {
        assertEquals("com.sec.android.app.launcher", LauncherExitManager.SAMSUNG_ONE_UI_PACKAGE)
        assertEquals("com.sec.android.app.launcher.activities.LauncherActivity", LauncherExitManager.SAMSUNG_ONE_UI_ACTIVITY)
    }
}

package com.divinedeck.launcher.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DivineDeckRouteTest {

    @Test
    fun `all seven core routes are properly configured`() {
        val routes = DivineDeckRoute.entries
        assertEquals(7, routes.size)
        assertTrue(routes.contains(DivineDeckRoute.COCKPIT))
        assertTrue(routes.contains(DivineDeckRoute.MUSIC))
        assertTrue(routes.contains(DivineDeckRoute.NAVIGATION))
        assertTrue(routes.contains(DivineDeckRoute.GARAGE_3D))
        assertTrue(routes.contains(DivineDeckRoute.WIDGETS))
        assertTrue(routes.contains(DivineDeckRoute.APPS))
        assertTrue(routes.contains(DivineDeckRoute.SETTINGS))
    }

    @Test
    fun `routes have non-empty titles and descriptions`() {
        DivineDeckRoute.entries.forEach { route ->
            assertTrue(route.title.isNotBlank())
            assertTrue(route.subtitle.isNotBlank())
        }
    }
}

package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-7.4 — the permission screen's privacy line once read "Your
 * location is only used to fetch weather data and is never stored or
 * shared." Both halves were false: the build saves the most recent
 * GPS fix on-device (PreferencesRepository.saveGPSLocation) and sends
 * coordinates to Open-Meteo (weather, rounded to 1dp) and to
 * Nominatim/OpenStreetMap (place names). The line is pinned verbatim
 * so the false reassurance cannot drift back in.
 */
class PermissionCopyTest {

    @Test
    fun `location privacy line is the RW-7_4 truthful text`() {
        assertEquals(
            "Your location fetches your weather and names your place. Your most " +
                "recent location is saved on this phone so RunWear reopens where " +
                "you left off. Weather lookups send your coordinates, rounded to " +
                "a rough area, to Open-Meteo; place-name lookups send them to " +
                "OpenStreetMap (Nominatim). Your location is never sold or used " +
                "for advertising.",
            PermissionCopy.LOCATION_PRIVACY_LINE
        )
    }

    @Test
    fun `location privacy line makes no never-stored or never-shared claim`() {
        assertFalse(PermissionCopy.LOCATION_PRIVACY_LINE.contains("never stored"))
        assertFalse(PermissionCopy.LOCATION_PRIVACY_LINE.contains("never shared"))
    }

    @Test
    fun `location privacy line names both services that receive coordinates`() {
        assertTrue(PermissionCopy.LOCATION_PRIVACY_LINE.contains("Open-Meteo"))
        assertTrue(PermissionCopy.LOCATION_PRIVACY_LINE.contains("Nominatim"))
    }
}

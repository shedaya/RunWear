package com.runwear.app.ui.screens

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * GEN-3 X-13 (carried W-13): the Set Location sheet's
 * "Current location" row must read from the ACTUAL permission /
 * location state in every sheet mode.
 *
 * The walkthrough evidence: GPS granted at onboarding and in
 * active use; the row read "Current location · CURRENT · GPS
 * location enabled" while the current place was the GPS place
 * (f_094), then flipped to "Requires location permission" once a
 * manual place (New York) was in use (f_105) — the row was keyed
 * on the current place's SOURCE, not the permission. And the
 * "Currently using:" pill in f_105 rendered its label with NO
 * place name (the name was a second line, clipped below the
 * IME-shrunk sheet; f_104, keyboard down, shows it fully named).
 */
class LocationSheetStateTest {

    // ── The row: permission truth in every mode ──────────────────

    @Test
    fun `gps place in use reads as the current gps place`() {
        val status = LocationSheetState.currentLocationRowStatus(
            isUsingGPS = true, hasLocationPermission = true
        )
        assertEquals(LocationSheetState.CurrentLocationRowStatus.USING_GPS, status)
        assertEquals("GPS location enabled", LocationSheetState.currentLocationRowSubtitle(status))
    }

    @Test
    fun `manual place in use with permission granted still reads gps enabled - the f_105 case`() {
        // THE walkthrough defect: manual place on screen, permission
        // granted — the row must NOT claim permission is required.
        val status = LocationSheetState.currentLocationRowStatus(
            isUsingGPS = false, hasLocationPermission = true
        )
        assertEquals(LocationSheetState.CurrentLocationRowStatus.PERMISSION_GRANTED, status)
        assertEquals("GPS location enabled", LocationSheetState.currentLocationRowSubtitle(status))
    }

    @Test
    fun `manual place in use without permission reads requires permission`() {
        val status = LocationSheetState.currentLocationRowStatus(
            isUsingGPS = false, hasLocationPermission = false
        )
        assertEquals(LocationSheetState.CurrentLocationRowStatus.PERMISSION_REQUIRED, status)
        assertEquals(
            "Requires location permission",
            LocationSheetState.currentLocationRowSubtitle(status)
        )
    }

    @Test
    fun `the gps fact wins even if the permission input lags`() {
        // The current place demonstrably came from GPS; the row
        // describes that place, whatever a stale permission read says.
        val status = LocationSheetState.currentLocationRowStatus(
            isUsingGPS = true, hasLocationPermission = false
        )
        assertEquals(LocationSheetState.CurrentLocationRowStatus.USING_GPS, status)
        assertEquals("GPS location enabled", LocationSheetState.currentLocationRowSubtitle(status))
    }

    // ── The pill: always named, or not rendered ──────────────────

    @Test
    fun `the currently-using pill always carries its place name`() {
        assertEquals("New York, NY", LocationSheetState.currentlyUsingName("New York, NY"))
        assertEquals("Overland Park, KS", LocationSheetState.currentlyUsingName("Overland Park, KS"))
    }

    @Test
    fun `a blank name suppresses the pill instead of rendering it empty`() {
        assertNull(LocationSheetState.currentlyUsingName(""))
        assertNull(LocationSheetState.currentlyUsingName("   "))
    }

    @Test
    fun `the pill name is trimmed`() {
        assertEquals("Miami, FL", LocationSheetState.currentlyUsingName("  Miami, FL  "))
    }

    // ── Wiring pins (the sheet cannot be driven under Robolectric) ──

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    @Test
    fun `the sheet resolves the row through the seam and never keys the subtitle on isUsingGPS`() {
        val sheets = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/BottomSheets.kt"
        ).readText()
        assertTrue(sheets.contains("LocationSheetState.currentLocationRowStatus("))
        assertTrue(sheets.contains("hasLocationPermission: Boolean"))
        // The pre-fix mode-keyed ternary must not survive in the sheet:
        // the subtitle string now lives only in the resolver.
        assertFalse(sheets.contains("\"Requires location permission\""))
        assertTrue(sheets.contains("LocationSheetState.currentlyUsingName(currentLocation)"))
    }

    @Test
    fun `callers pass the actual permission, queried from the view model`() {
        val mainActivity = sourceFile(
            "app/src/main/java/com/runwear/app/MainActivity.kt"
        ).readText()
        // Both sheet call sites (home's HeroMainScreen and the
        // permission-screen manual picker) feed the sheet the
        // LocationProvider-backed query — NOT uiState's bypass flag.
        assertEquals(
            2,
            mainActivity.split("hasLocationPermission = viewModel.hasLocationPermission()").size - 1
        )
        val heroMainScreen = sourceFile(
            "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
        ).readText()
        assertTrue(heroMainScreen.contains("hasLocationPermission = hasLocationPermission"))
    }
}

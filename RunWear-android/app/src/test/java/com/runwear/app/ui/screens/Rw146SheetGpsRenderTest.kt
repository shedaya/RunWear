package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-146 replay frame B: after the Current-location tap returns
 * the app to New York on GPS. The GPS row carries the single
 * CURRENT marker, no recent is marked, and the manual-only
 * "Currently using" footer is absent.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw146SheetGpsRenderTest : Rw146SheetRenderTestBase() {

    @Test
    fun `rw146 sheet - new york gps - gps row current, recents and footer agree`() {
        val host = sheetHost(currentLocation = "New York, NY", usingGps = true)
        capture(host, "rw146-b-sheet-newyork-gps.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW146-B texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        val currents = boxes.filter { it.text == "CURRENT" }
        assertEquals("exactly one CURRENT marker in the sheet: $currents", 1, currents.size)
        val gpsRow = boxes.firstOrNull { it.text == "Current location" }
        assertTrue("GPS row must render", gpsRow != null)
        assertTrue(
            "the CURRENT marker must sit on the GPS row",
            kotlin.math.abs(currents[0].top - gpsRow!!.top) / density <= 24f
        )
        assertTrue(
            "no Currently-using footer while GPS is the source",
            boxes.none { it.text.contains("Currently using") }
        )
        host.controller.pause().stop().destroy()
    }
}

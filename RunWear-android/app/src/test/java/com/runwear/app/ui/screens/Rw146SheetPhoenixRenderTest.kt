package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-146 replay frame A: the app is on Phoenix by MANUAL pick.
 * Every sheet surface must agree: Phoenix is CURRENT (recents row
 * + "Currently using" footer), and the GPS "Current location"
 * row is NOT marked current.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw146SheetPhoenixRenderTest : Rw146SheetRenderTestBase() {

    @Test
    fun `rw146 sheet - phoenix manual - all surfaces agree, gps row unmarked`() {
        val host = sheetHost(currentLocation = "Phoenix, AZ", usingGps = false)
        capture(host, "rw146-a-sheet-phoenix-manual.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW146-A texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        val currents = boxes.filter { it.text == "CURRENT" }
        assertEquals("exactly one CURRENT marker in the sheet: $currents", 1, currents.size)
        val phoenix = boxes.firstOrNull { it.text == "Phoenix, AZ" }
        assertTrue("Phoenix recent row must render", phoenix != null)
        assertTrue(
            "the CURRENT marker must sit on the Phoenix row",
            kotlin.math.abs(currents[0].top - phoenix!!.top) / density <= 24f
        )
        assertTrue(
            "footer must agree: Currently using Phoenix",
            boxes.any { it.text.contains("Currently using: Phoenix, AZ") }
        )
        val gpsRow = boxes.firstOrNull { it.text == "Current location" }
        assertTrue("GPS row must render", gpsRow != null)
        assertTrue(
            "GPS row must NOT carry the CURRENT marker",
            currents.none { kotlin.math.abs(it.top - gpsRow!!.top) / density <= 24f }
        )
        host.controller.pause().stop().destroy()
    }
}

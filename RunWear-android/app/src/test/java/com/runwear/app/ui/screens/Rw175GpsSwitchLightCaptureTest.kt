package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-175 capture (light): the first screen AFTER a manual →
 * Current Location switch whose reverse geocode has resolved —
 * the pill, the conditions and the Wear I've Run entry all name
 * the current place (New York), never the previous manual place
 * (Miami Beach). State is the post-resolution committed state
 * the fix produces (resolved NY place + NY cold weather).
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw175GpsSwitchLightCaptureTest : Rw145RenderTestBase() {

    @Test
    fun `rw175 after gps switch - light`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo).copy(
            placeNameResolved = true,
            locationSource = "gps"
        )
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        capture(host, "rw175-after-gps-switch-light.png")
        val boxes = nodeBoxes(host)
        assertTrue("pill must read New York", boxes.any { it.text == "New York, NY" })
        assertTrue(
            "no surface may still say Miami",
            boxes.none { it.text.contains("Miami") }
        )
        host.controller.pause().stop().destroy()
    }
}

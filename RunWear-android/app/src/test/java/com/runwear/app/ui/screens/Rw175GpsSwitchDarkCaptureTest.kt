package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-175 capture (dark): same post-switch resolved state as the
 * light capture — see Rw175GpsSwitchLightCaptureTest.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw175GpsSwitchDarkCaptureTest : Rw145RenderTestBase() {

    @Test
    fun `rw175 after gps switch - dark`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo).copy(
            placeNameResolved = true,
            locationSource = "gps"
        )
        seedExactEntry(state)
        val host = compose(dark = true) { Home(state, exhaustedMim()) }
        capture(host, "rw175-after-gps-switch-dark.png")
        val boxes = nodeBoxes(host)
        assertTrue("pill must read New York", boxes.any { it.text == "New York, NY" })
        assertTrue(
            "no surface may still say Miami",
            boxes.none { it.text.contains("Miami") }
        )
        host.controller.pause().stop().destroy()
    }
}

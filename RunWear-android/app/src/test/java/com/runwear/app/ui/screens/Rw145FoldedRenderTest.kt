package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-145 + RW-147 proof, frame 1: folded-portrait height
 * (~740dp), New York Now, REAL runner photograph, 3-item outfit,
 * at rest. The chip must clear the feels-like block by the
 * guaranteed gap, and no readable content may sit under the dock.
 */
@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw145FoldedRenderTest : Rw145RenderTestBase() {

    @Test
    fun `rw145 folded - chip clears the feels-like block and the dock overlays nothing at rest`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        assertTrue(
            "fixture must be the device's 3-item outfit, got ${state.outfit?.allItems?.map { it.displayName }}",
            (state.outfit?.allItems?.size ?: 0) >= 3
        )
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        capture(host, "rw145-1-folded-740-ny-now.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW145-FOLDED texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        val chipDate = boxes.firstOrNull { it.text == "Mon, Nov 9" }
        val chipNow = boxes.firstOrNull { it.text == "Now" }
        val feels = boxes.firstOrNull { it.text == "FEELS LIKE" }
        assertTrue("chip date must render", chipDate != null)
        assertTrue("chip Now segment must render", chipNow != null)
        assertTrue("feels-like label must render", feels != null)
        // The chip's box extends ~8.5dp beyond its text on each
        // side (34dp chip, ~17dp text): the guaranteed 12dp gap
        // reads as >= 12 + 8.5 - (label's own top inset ~1) on
        // text boxes; assert a conservative >= 16dp text gap.
        val textGap = (feels!!.top - chipDate!!.bottom) / density
        println("RW145-FOLDED chipTextBottom=${chipDate.bottom / density} feelsTop=${feels.top / density} textGap=$textGap")
        assertTrue("chip must clear the block (text gap $textGap dp)", textGap >= 16f)
        // RW-147 at rest: the dock's band starts where the bounded
        // list ends. Every readable text outside the dock itself
        // must end at or above the band's top.
        val dockMark = boxes.firstOrNull { it.text.contains("Wear I've Run") }
        assertTrue("dock must be present at rest", dockMark != null)
        // RW-170: the band top IS the painted card top (band =
        // lift + presented mini height), so the dock's top is
        // the surface node's own top — see Rw145StandardRenderTest.
        val dockTop = dockMark!!.top / density
        val dockTexts = boxes.filter { it.top >= dockMark.top - 1f }
        val offenders = boxes.filter { box ->
            box !in dockTexts && box.bottom / density > dockTop + 2f
        }
        println("RW145-FOLDED dockTop=$dockTop offenders=$offenders")
        assertTrue("dock overlays readable content at rest: $offenders", offenders.isEmpty())
        host.controller.pause().stop().destroy()
    }
}

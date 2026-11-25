package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-145 + RW-147 proof, frame 2: standard phone height (844dp),
 * same content as the folded frame. The approved composition is
 * unchanged where it already worked — chip on its preferred seat,
 * dock over clear space at rest.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw145StandardRenderTest : Rw145RenderTestBase() {

    @Test
    fun `rw145 standard - chip clears the block and the dock overlays nothing at rest`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        capture(host, "rw145-2-standard-844-ny-now.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW145-STANDARD texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        val chipDate = boxes.firstOrNull { it.text == "Mon, Nov 9" }
        val feels = boxes.firstOrNull { it.text == "FEELS LIKE" }
        assertTrue("chip date must render", chipDate != null)
        assertTrue("feels-like label must render", feels != null)
        val textGap = (feels!!.top - chipDate!!.bottom) / density
        println("RW145-STANDARD textGap=$textGap")
        assertTrue("chip must clear the block (text gap $textGap dp)", textGap >= 16f)
        val dockMark = boxes.firstOrNull { it.text.contains("Wear I've Run") }
        assertTrue("dock must be present at rest", dockMark != null)
        val dockTop = (dockMark!!.top / density) - 14f
        val dockTexts = boxes.filter { it.top >= dockMark.top - 1f }
        val offenders = boxes.filter { box ->
            box !in dockTexts && box.bottom / density > dockTop + 2f
        }
        println("RW145-STANDARD dockTop=$dockTop offenders=$offenders")
        assertTrue("dock overlays readable content at rest: $offenders", offenders.isEmpty())
        host.controller.pause().stop().destroy()
    }
}

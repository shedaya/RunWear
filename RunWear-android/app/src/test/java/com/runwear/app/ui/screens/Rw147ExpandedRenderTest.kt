package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-147 proof, frame 3: expanded two-pane layout with the Make
 * It Me upsell card present and the dock at rest. The dock must
 * be confined to the right pane (it may not span onto the hero
 * pane at all) and overlay no readable content.
 */
@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw147ExpandedRenderTest : Rw145RenderTestBase() {

    @Test
    fun `rw147 expanded - dock confined to the right pane, card and hero clear`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo).copy(showMakeItMeUpsell = true)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, mimState(stock = true)) }
        capture(host, "rw147-3-expanded-mim-card.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = nodeBoxes(host)
        println(
            "RW147-EXPANDED texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.left / density).toInt()},${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        val dockMark = boxes.firstOrNull { it.text.contains("Wear I've Run") }
        assertTrue("dock must be present at rest", dockMark != null)
        // EXPANDED: hero pane width = max(400, 0.42 * 1000) = 420dp.
        println("RW147-EXPANDED dockLeft=${dockMark!!.left / density}")
        assertTrue(
            "dock must be confined to the right pane (left=${dockMark.left / density}dp < 420dp)",
            dockMark.left / density >= 420f - 1f
        )
        val actual = boxes.firstOrNull { it.text.startsWith("Actual") }
        assertTrue("hero Actual line must render", actual != null)
        assertTrue(
            "hero Actual line must not run under the dock",
            actual!!.right / density <= 420f + 1f &&
                actual.bottom / density < 800f
        )
        val dockTop = (dockMark.top / density) - 14f
        val dockTexts = boxes.filter { it.top >= dockMark.top - 1f }
        val offenders = boxes.filter { box ->
            box !in dockTexts && box.bottom / density > dockTop + 2f &&
                box.left / density >= 420f
        }
        println("RW147-EXPANDED dockTop=$dockTop offenders=$offenders")
        assertTrue("dock overlays readable content at rest: $offenders", offenders.isEmpty())
        host.controller.pause().stop().destroy()
    }
}

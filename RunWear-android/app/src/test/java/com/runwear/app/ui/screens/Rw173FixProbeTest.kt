package com.runwear.app.ui.screens

import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

/**
 * RW-173 (Nov 13, 2026) — discriminating probes for the two
 * parent-gate findings on the RW-170 surface:
 *
 * F1: at home rest (scroll 0) the expanded panel grows into
 * the Make It Me pill's seat; the pill renders above the
 * surface (RB-09) and clips the panel's script title. The
 * PANEL must yield — its expansion top capped at the pill
 * seat's bottom edge — so the title is fully readable in
 * the resting expanded state, at home rest and on the
 * travel path (expand at the page end, scroll back home).
 *
 * F2: mid-morph, the migrating narration's bounds intersect
 * the wordmark's bounds (text on text). The narration must
 * clear the title at every sampled progress point
 * (~25/50/75% of the height span), the iOS geometry.
 *
 * Both probes assert PAINTED-tree geometry (unmerged text
 * boxes): the wordmark glyph strip vs the pill's text union
 * (F1) and vs the narration box (F2). Written against
 * ef06920, where both fail with the values in FIX2-REPORT.
 */
abstract class Rw173Base : Rw170HomeBase() {

    protected fun glyphWordmark(host: Host): NodeBox =
        wordmarkNodes(host).minByOrNull {
            (it.right - it.left) * (it.bottom - it.top)
        } ?: throw AssertionError("no wordmark glyph node")

    /** The pill's painted extent: the union of its text
     * boxes (script mark + count + action), across the
     * in-list and overlay copies — both sit on the one seat,
     * so the union is the seat's text extent. */
    protected fun pillExtent(host: Host): NodeBox? {
        val parts = unmergedNodeBoxes(host).filter {
            it.text == "Make It Me" ||
                it.text.contains("of 10 used") ||
                it.text == "Get unlimited"
        }
        if (parts.isEmpty()) return null
        return NodeBox(
            "PILL",
            parts.minOf { it.top }, parts.maxOf { it.bottom },
            parts.minOf { it.left }, parts.maxOf { it.right }
        )
    }

    protected fun intersects(a: NodeBox, b: NodeBox): Boolean =
        a.left < b.right - 1f && b.left < a.right - 1f &&
            a.top < b.bottom - 1f && b.top < a.bottom - 1f

    protected fun fmt(b: NodeBox?): String =
        if (b == null) "null" else
            "top=${b.top} bottom=${b.bottom} left=${b.left} right=${b.right}"

    protected val fix2Dir = File(
        "/home/hatch/workspace/release-prep/" +
            "rw135-rebuild-2026-11-12/work/fix2-captures"
    )

    protected fun captureFix2(host: Host, name: String) {
        val bitmap = capture(host, name)
        fix2Dir.mkdirs()
        File(fix2Dir, name).outputStream().use {
            bitmap.compress(
                android.graphics.Bitmap.CompressFormat.PNG, 100, it
            )
        }
    }

    protected fun probe173(lines: List<String>) {
        fix2Dir.mkdirs()
        File(fix2Dir, "rw173-probe.txt").appendText(
            lines.joinToString("\n") + "\n"
        )
        lines.forEach { println("RW173-PROBE $it") }
    }

    protected fun assertTitleClearOfPill(host: Host, where: String) {
        val panel = surfaceBounds(host)
        val wm = glyphWordmark(host)
        val pill = pillExtent(host)
        probe173(
            listOf(
                "[$where] panel=${fmt(NodeBox("p", panel.top, panel.bottom, panel.left, panel.right))}",
                "[$where] wordmark=${fmt(wm)}",
                "[$where] pill=${fmt(pill)}"
            )
        )
        assertTrue("[$where] pill present on Home", pill != null)
        assertTrue(
            "[$where] wordmark fully inside the panel " +
                "(wm ${fmt(wm)}, panel top=${panel.top} bottom=${panel.bottom})",
            wm.top >= panel.top - 1f && wm.bottom <= panel.bottom + 1f
        )
        assertTrue(
            "[$where] panel top at or below the pill's painted bottom " +
                "(panelTop=${panel.top}, pillBottom=${pill!!.bottom}) — " +
                "the panel yields, the pill stays whole above it",
            panel.top >= pill.bottom - 1f
        )
        assertTrue(
            "[$where] title fully readable: wordmark box does not " +
                "intersect the pill (wm ${fmt(wm)}, pill ${fmt(pill)})",
            !intersects(wm, pill)
        )
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw173HomeExpandedLightTest : Rw173Base() {

    @Test
    fun `rw173 F1 - home-rest expanded title clear of the pill, light`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        captureFix2(host, "rw173-home-expanded-light.png")
        assertTitleClearOfPill(host, "home-expanded-light")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw173HomeExpandedDarkTest : Rw173Base() {

    @Test
    fun `rw173 F1 - home-rest expanded title clear of the pill, dark`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = true) { Home(state, exhaustedMim()) }
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        captureFix2(host, "rw173-home-expanded-dark.png")
        assertTitleClearOfPill(host, "home-expanded-dark")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw173HomeExpandedTravelTest : Rw173Base() {

    @Test
    fun `rw173 F1 travel - expand at page end, scroll home, title still clear`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        // Travel: TAP-summon the expanded form first (the
        // R-1 latch keeps a summoned panel up across scroll —
        // a footer-presented one would re-dock on the way
        // back), then travel to the page end and back home:
        // the pill rides the scroll back down into the
        // panel's zone on this path.
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        val (value, max) = driveToEnd(host)
        settleReveal(host)
        assertTrue(
            "scroll reached the page end ($value of $max)",
            max - value <= 1f
        )
        val node = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }!!
        node.config[
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy
        ].action?.invoke(0f, -100000f)
        advanceFrames(host, 3)
        settleReveal(host)
        captureFix2(host, "rw173-home-expanded-travel-light.png")
        assertTitleClearOfPill(host, "home-expanded-travel")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw173MorphClearanceLightTest : Rw173Base() {

    @Test
    fun `rw173 F2 - narration clears the wordmark at 25, 50, 75 percent of the morph`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val miniBounds = surfaceBounds(host)
        val miniH = miniBounds.bottom - miniBounds.top

        // Learn this build's expanded rest height first (the
        // F1 cap may bind at home rest), then re-run the
        // morph and sample by height fraction.
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        val expandedH = surfaceBounds(host).let { it.bottom - it.top }
        clickDescription(host, "Collapse Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)

        clickDescriptionNoSettle(host, "Expand Wear I've Run")
        val looper = org.robolectric.Shadows.shadowOf(
            android.os.Looper.getMainLooper()
        )
        val targets = mutableListOf(0.25f to "25", 0.5f to "50", 0.75f to "75")
        var batches = 0
        while (batches < 900 && targets.isNotEmpty()) {
            repeat(5) { looper.runOneTask() }
            batches++
            val frame = capture(host, "tmp-rw173-morph-frame.png")
            frame.recycle()
            val b = surfaceBounds(host)
            val fraction = (b.bottom - b.top - miniH) / (expandedH - miniH)
            if (fraction >= targets.first().first) {
                val (target, label) = targets.removeAt(0)
                val wm = glyphWordmark(host)
                val narr = narrationNodes(host).firstOrNull()
                captureFix2(host, "rw173-morph-$label-light.png")
                probe173(
                    listOf(
                        "[morph-$label] fraction~$target (measured $fraction) " +
                            "wordmark=${fmt(wm)} narration=${fmt(narr)}"
                    )
                )
                assertTrue(
                    "[morph-$label] narration composed mid-morph",
                    narr != null
                )
                assertTrue(
                    "[morph-$label] narration bounds do not intersect " +
                        "the wordmark bounds (narration ${fmt(narr)}, " +
                        "wordmark ${fmt(wm)}) — no text on text mid-morph",
                    !intersects(wm, narr!!)
                )
            }
        }
        assertTrue("all morph samples taken", targets.isEmpty())
        host.controller.pause().stop().destroy()
    }
}

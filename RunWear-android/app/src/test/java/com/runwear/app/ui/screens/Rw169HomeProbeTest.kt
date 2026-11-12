package com.runwear.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.viewmodel.MainUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.Robolectric

/**
 * RW-169 (Nov 11, 2026): full-screen probes on the real build —
 * the RW-135 §8 checks 4 and 6 closed with harness evidence
 * (diagnosis D4: they may not ride as "unproven" again), the
 * §7G.3 re-settle-while-open probe (§7E), and the §7D pill seat.
 *
 * Harness limitation, stated plainly: these run on Robolectric
 * native graphics (real production composables, scheduler-driven
 * animation frames) — there is no physical device in this lane.
 * Scroll "flicks" are single large ScrollBy actions (the whole
 * range in one input event), the fastest input the harness can
 * deliver; frame sequences are captured in Rw169CaptureTest.
 */
abstract class Rw169HomeBase : Rw169Base() {

    protected fun scrollNodes(host: Host): List<SemanticsNode> =
        allNodes(host).filter {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                it.config.contains(SemanticsActions.ScrollBy)
        }

    protected fun flickToEnd(host: Host) {
        // ONE ScrollBy for the remaining range — a flick, not a
        // stepped scroll.
        repeat(6) {
            val node = scrollNodes(host)
                .maxByOrNull { it.boundsInRoot.height } ?: return
            val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
            val remaining = range.maxValue() - range.value()
            if (remaining <= 1f) return
            node.config[SemanticsActions.ScrollBy].action?.invoke(0f, remaining)
            advanceFrames(host, 2)
        }
        settleReveal(host)
    }

    protected fun entryLineText(host: Host): String? =
        nodeBoxes(host).firstOrNull {
            it.text.contains("feels like") && it.text.contains("·")
        }?.text

    protected fun narrationText(host: Host): String? =
        nodeBoxes(host).firstOrNull {
            it.text.startsWith("Updated to") || it.text.startsWith("Planned for")
        }?.text
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169FlickSpeedProbeTest : Rw169HomeBase() {

    @Test
    fun `rw135 check 4 - flick-speed scroll and flick-speed expansion read as one element`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        assertEquals(1, narrationNodes(host).size)

        // Flick: the whole remaining range in ONE input action,
        // sampled immediately (no settle) and after.
        val node = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }!!
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        node.config[SemanticsActions.ScrollBy].action
            ?.invoke(0f, range.maxValue())
        advanceFrames(host, 1)
        assertEquals(
            "mid-flick: exactly one narration instance",
            1, narrationNodes(host).size
        )
        assertWordmarkWhole(host, "mid-flick")
        settleReveal(host)
        assertEquals(
            "after flick: exactly one narration instance",
            1, narrationNodes(host).size
        )

        // Flick-speed expansion: summon the morph and flick back
        // while it is mid-flight — still one element, one narration.
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 1)
        val node2 = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }!!
        node2.config[SemanticsActions.ScrollBy].action?.invoke(0f, -4000f)
        advanceFrames(host, 1)
        capture(host, "rw169-flick-morph-midflight.png")
        assertEquals(
            "morph mid-flight under scroll: exactly one narration",
            1, narrationNodes(host).size
        )
        assertWordmarkWhole(host, "morph mid-flight under scroll")
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169DeleteAnchorProbeTest : Rw169HomeBase() {

    @Test
    fun `rw135 check 6 - delete at the end of scroll does not move what the user is reading`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        flickToEnd(host)
        // The end-of-scroll reading position: the scroll offset
        // plus the tops of the text nodes composed in the final
        // window (RW-167's stepped drive proves the Pro Tip card
        // renders during this scroll; the §8.6 invariant is that
        // NONE of what is on screen moves when the record is
        // deleted from the surface floating over it).
        fun scrollValue(): Float {
            val node = scrollNodes(host)
                .maxByOrNull { it.boundsInRoot.height }!!
            return node.config[SemanticsProperties.VerticalScrollAxisRange]
                .value()
        }
        val valueBefore = scrollValue()
        val topsBefore = nodeBoxes(host)
            .filter { it.text.isNotBlank() }
            .associate { it.text to it.top }
        assertTrue(
            "reading window composed at end of scroll",
            topsBefore.size >= 4
        )
        // Delete from the visible surface at the end of scroll.
        clickDescription(host, "Remove from Wear I've Run")
        settleReveal(host)
        val valueAfter = scrollValue()
        assertTrue(
            "scroll offset unmoved by the delete " +
                "($valueBefore -> $valueAfter)",
            kotlin.math.abs(valueAfter - valueBefore) <= 2f
        )
        var stillChecked = 0
        for ((text, top) in topsBefore) {
            val now = nodeBoxes(host).firstOrNull { it.text == text }
                ?: continue
            assertTrue(
                "node '$text' unmoved by the delete " +
                    "($top -> ${now.top})",
                kotlin.math.abs(now.top - top) <= 2f
            )
            stillChecked++
        }
        assertTrue(
            "at least 3 reading nodes re-checked after the delete",
            stillChecked >= 3
        )
        assertEquals(
            "the record's narration is gone after the delete",
            0, narrationNodes(host).size
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169ResettleProbeTest : Rw169HomeBase() {

    @Test
    fun `rw169 G3 - footer open plus forced weather re-settle leaves the visible entry byte-identical until the gate commits`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val initial = nyState(photo)
        seedExactEntry(initial)
        var state by mutableStateOf(initial)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        // The initial frame completes its stability window during
        // the compose settle (~4s of frame time).
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 5)
        val entryBefore = entryLineText(host)
            ?: throw AssertionError("expanded entry line not composed")
        val narrationBefore = narrationText(host)
            ?: throw AssertionError("narration not composed")
        println("RW169 G3 before: entry='$entryBefore' narration='$narrationBefore'")

        // Force a weather re-settle: feels-like +5 (a frame-named
        // dimension). Inside the ≈2s window the visible entry
        // must be BYTE-IDENTICAL.
        val w = initial.weather!!
        state = initial.copy(
            weather = w.copy(feelsLike = w.feelsLike + 5.0)
        )
        advanceFrames(host, 10) // ~1s of frame time
        assertEquals(
            "entry line byte-identical inside the stability window",
            entryBefore, entryLineText(host)
        )
        assertEquals(
            "narration byte-identical inside the stability window",
            narrationBefore, narrationText(host)
        )

        // Past the window the flip lands ATOMICALLY: entry line
        // and narration swap together in the same settled frame.
        // The commit's write is a real DataStore edit on IO
        // threads while the harness clock is virtual — landing is
        // awaited with a bounded poll (background scheduler
        // flushed each round) rather than one fixed advance whose
        // real-time budget varies with suite load (the probe
        // flaked twice under full-suite load on exactly this
        // wait, and passed in every targeted run).
        advanceFrames(host, 30)
        var entryAfter = entryLineText(host)
        var rounds = 0
        while (entryAfter == entryBefore && rounds < 40) {
            Robolectric.getBackgroundThreadScheduler().idle()
            settleReveal(host)
            entryAfter = entryLineText(host)
            rounds++
        }
        println("RW169 G3 landing rounds: $rounds")
        println("RW169 G3 after: entry='$entryAfter'")
        assertNotEquals(
            "the settled change does land after the window",
            entryBefore, entryAfter
        )
        assertTrue(
            "the new entry line carries the new feels-like (46, was 41)",
            entryAfter!!.contains("46")
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169PillSeatProbeTest : Rw169HomeBase() {

    @Test
    fun `rw169 D - with the panel expanded the pill renders whole in the overlay host at its measured seat`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val pillText = MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT
        val before = nodeBoxes(host).filter { it.text == pillText }
        assertEquals("pill present at rest", 1, before.size)
        val seat = before.single()

        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 5)
        capture(host, "rw169-pill-over-panel.png")
        val after = nodeBoxes(host).filter { it.text == pillText }
        // The in-list copy yields (alpha 0) and the overlay-host
        // copy renders at the SAME measured bounds — two copies of
        // the one pill value, both at the seat; the visible one is
        // the overlay copy above the panel.
        assertEquals(
            "in-list copy + overlay-host copy at the seat",
            2, after.size
        )
        assertTrue(
            "both copies at the measured seat",
            after.all {
                kotlin.math.abs(it.left - seat.left) <= 4f &&
                    kotlin.math.abs(it.top - seat.top) <= 4f &&
                    kotlin.math.abs(it.right - seat.right) <= 4f
            }
        )
        host.controller.pause().stop().destroy()
    }
}

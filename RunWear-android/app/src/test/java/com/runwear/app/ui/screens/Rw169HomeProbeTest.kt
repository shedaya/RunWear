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

    protected fun overlayPillCopies(host: Host): Int =
        allNodes(host).count {
            it.config.contains(SemanticsProperties.TestTag) &&
                it.config[SemanticsProperties.TestTag] ==
                "rw169PillOverlayCopy"
        }

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
        // RW-135 REBUILD + R-1: the flick lands at the page
        // end, which now PRESENTS the expanded form — the
        // morph may be inside its dissolve dead zone at this
        // sample, where zero narrations are legible. The
        // invariant is at most one legible narration.
        assertTrue(
            "mid-flick: at most one legible narration " +
                "(was ${narrationNodes(host).size})",
            narrationNodes(host).size <= 1
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
        assertTrue(
            "morph mid-flight under scroll: at most one legible narration " +
                "(was ${narrationNodes(host).size})",
            narrationNodes(host).size <= 1
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
        // PLACED nodes only (RW-170 note): the LazyColumn keeps
        // a composed-but-never-placed precomposition copy of
        // off-window items in the tree at zero bounds (the
        // weather-detail row among them). A zero-bounds node is
        // definitionally not "what the user is reading", and
        // keying the map by text alone lets the phantom copy
        // (top 0.0) shadow the placed node depending on walk
        // order — the RW-170 band change (102dp -> 63dp-class)
        // shifted the flick's landing so the detail item is
        // precomposed here, which exposed the collision. The
        // §8.6 invariant is about placed content; it is
        // unchanged (scroll 600 -> 600, placed tops identical).
        val topsBefore = nodeBoxes(host)
            .filter { it.text.isNotBlank() && it.bottom > it.top }
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
            val now = nodeBoxes(host).firstOrNull {
                it.text == text && it.bottom > it.top
            } ?: continue
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
        while (entryAfter == entryBefore && rounds < 60) {
            // DataStore's edit runs on real IO threads; give
            // them real time, then pump the main looper through
            // the harness settle. (Robolectric's scheduler-flush
            // APIs throw in this suite's PAUSED looper mode.)
            Thread.sleep(30)
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

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169PillSeatProbeTest : Rw169HomeBase() {

    @Test
    fun `rw169 D8 loop-back - expanded panel header whole, pill present exactly once in front, in the folded class`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val pillText = MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT
        assertEquals(
            "pill present at rest",
            1, nodeBoxes(host).count { it.text == pillText }
        )
        assertEquals(
            "no overlay pill copy at rest",
            0, overlayPillCopies(host)
        )

        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 12)
        settleReveal(host)
        capture(host, "rw169-pill-over-panel.png")

        // RW-135 REBUILD (RB-09, Nov 12, 2026) — this RW-169
        // expectation is SUPERSEDED. The conformance map §7
        // invariant: the pill is whole and unbisected in MINI,
        // mid-scroll AND EXPANDED — it is never removed while
        // expanded. The pre-rebuild host gated the overlay
        // copy with !wirExpanded while still suppressing the
        // in-list copy on panel overlap, so on an overlapping
        // seat the pill VANISHED while expanded (Sol's
        // recording). The rule now: exactly one pill copy
        // exists in every state — the in-list copy when the
        // panel is clear of its seat, the overlay copy (in
        // front of the panel, never over its header) when the
        // panel covers the seat.
        // RW-173 (Nov 13, 2026) — this RW-170 expectation is
        // SUPERSEDED in its mechanism, not its invariant. The
        // RW-170 head let the expanded panel grow OVER the pill
        // seat, so the host presented the overlay copy in front
        // of the panel — and the pill clipped the panel's script
        // title (parent picture gate, finding F1). RW-173 caps
        // the panel's expansion at the pill seat's bottom edge
        // (WirOverlayPanelPolicy pill cap): the panel now YIELDS
        // and never covers the seat in this class, so no overlay
        // copy is composed and the presented copy is the in-list
        // copy — exactly as at rest. The map §7 invariant is
        // unchanged and still asserted below and above: exactly
        // one pill copy exists and is presented, whole, in every
        // state.
        assertEquals(
            "no overlay pill copy while expanded — RW-173 caps " +
                "the panel below the pill seat (panel yields)",
            0, overlayPillCopies(host)
        )
        assertEquals(
            "exactly one pill copy presented while expanded " +
                "(the in-list copy, whole — map §7)",
            1, nodeBoxes(host).count { it.text == pillText }
        )
        // The panel's header wordmark: exactly one instance,
        // rendered WHOLE inside the panel's bounds — its full
        // glyph box inside the morph surface, clipped by
        // nothing.
        val surface = allNodes(host).firstOrNull {
            it.config.contains(SemanticsProperties.TestTag) &&
                it.config[SemanticsProperties.TestTag] ==
                "rw169MorphSurface"
        }?.boundsInRoot
        assertTrue("morph surface composed", surface != null)
        val wordmarks = wordmarkNodes(host).filter {
            it.bottom - it.top < 200f
        }
        assertEquals(
            "exactly one wordmark glyph strip in the expanded state",
            1, wordmarks.size
        )
        val wm = wordmarks.single()
        assertTrue(
            "panel header wordmark whole inside the panel " +
                "(wm ${wm.top}..${wm.bottom}, panel " +
                "${surface!!.top}..${surface.bottom})",
            wm.top >= surface.top - 1f &&
                wm.bottom <= surface.bottom + 1f &&
                wm.left >= surface.left - 1f &&
                wm.right <= surface.right + 1f
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w1000dp-h800dp-xhdpi")
class Rw169PillSeatWideProbeTest : Rw169HomeBase() {

    @Test
    fun `rw169 D8 loop-back - expanded panel header whole, pill absent-by-design, in the wide class`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 12)
        settleReveal(host)
        assertEquals(
            "no overlay pill copy while the panel is expanded (wide)",
            0, overlayPillCopies(host)
        )
        val wordmarks = wordmarkNodes(host).filter {
            it.bottom - it.top < 200f
        }
        assertEquals(
            "exactly one wordmark glyph strip in the expanded state (wide)",
            1, wordmarks.size
        )
        host.controller.pause().stop().destroy()
    }
}

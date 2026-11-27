package com.runwear.app.weariverun

import com.runwear.app.ui.components.HeroChromePolicy
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-169 (Nov 11, 2026): the structural fix's pure-policy pins —
 * §7B width law (chrome policy), §7C chrome negotiation, §7E
 * PresentedEntry settle discipline (stability gate, atomic
 * snapshot, thumbnail hold, recordable hero), §7A morph policy.
 */
class Rw169PolicyTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 11)

    private fun entry(
        place: String = "Overland Park, KS",
        date: LocalDate = today,
        feels: Double = 41.0,
        items: List<String> = listOf(
            "Running Cap", "Short Sleeve Shirt",
            "Running Shorts (5-7\")", "Sunglasses"
        ),
        heroRef: String? = "https://example.test/hero.jpg",
        momentLabel: String? = null
    ) = WearIveRunDayEntry(
        date = date,
        place = place,
        feelsLikeF = feels,
        conditionLabel = "Clear sky",
        isSnowing = false,
        isRaining = false,
        items = items,
        heroRef = heroRef,
        sessionId = "session-rw169",
        settledAtMillis = 1L,
        momentLabel = momentLabel
    )

    // ---- §7E.1 Stability gate -----------------------------------

    @Test
    fun `stability gate - no commit before the 2s window completes`() {
        val gate = StabilityGate()
        val e = entry()
        val frame = ViewedFrame.of(e)
        assertNull(gate.offer(frame, e, nowMs = 0L))
        assertNull(gate.offer(frame, e, nowMs = 1_000L))
        assertNull(gate.offer(frame, e, nowMs = 1_999L))
        assertEquals(frame, gate.pendingFrame)
    }

    @Test
    fun `stability gate - commits exactly once at the window`() {
        val gate = StabilityGate()
        val e = entry()
        val frame = ViewedFrame.of(e)
        assertNull(gate.offer(frame, e, nowMs = 0L))
        assertEquals(e, gate.offer(frame, e, nowMs = 2_000L))
        // The committed frame never re-commits.
        assertNull(gate.offer(frame, e, nowMs = 5_000L))
        assertEquals(frame, gate.committedFrame)
    }

    @Test
    fun `stability gate - a changed frame restarts the window`() {
        val gate = StabilityGate()
        val e1 = entry(feels = 41.0)
        val e2 = entry(feels = 46.0)
        assertNull(gate.offer(ViewedFrame.of(e1), e1, nowMs = 0L))
        assertNull(gate.offer(ViewedFrame.of(e1), e1, nowMs = 1_500L))
        // The re-settle at 1.5s restarts the window for the NEW frame.
        assertNull(gate.offer(ViewedFrame.of(e2), e2, nowMs = 1_500L))
        assertNull(gate.offer(ViewedFrame.of(e2), e2, nowMs = 3_499L))
        assertEquals(e2, gate.offer(ViewedFrame.of(e2), e2, nowMs = 3_500L))
        // The superseded frame never committed.
        assertEquals(ViewedFrame.of(e2), gate.committedFrame)
    }

    @Test
    fun `stability gate - a re-settle naming nothing in the frame never writes`() {
        // Humidity (or any non-frame dimension) changing produces
        // the SAME ViewedFrame — the gate sees the committed frame
        // and stays silent (diagnosis §5's churn class).
        val gate = StabilityGate()
        val e = entry()
        val frame = ViewedFrame.of(e)
        gate.offer(frame, e, nowMs = 0L)
        gate.offer(frame, e, nowMs = 2_000L)
        val sameFrameNewObject = entry().copy(settledAtMillis = 999L)
        assertNull(
            gate.offer(ViewedFrame.of(sameFrameNewObject), sameFrameNewObject, nowMs = 9_000L)
        )
    }

    @Test
    fun `viewed frame names exactly place day-hour kit and hero`() {
        val a = ViewedFrame.of(entry())
        val b = ViewedFrame.of(entry().copy(conditionLabel = "Rain", settledAtMillis = 42L))
        assertEquals("non-frame fields do not change the frame", a, b)
        assertTrue(ViewedFrame.of(entry(place = "Miami, FL")) != a)
        assertTrue(ViewedFrame.of(entry(date = today.plusDays(1))) != a)
        assertTrue(ViewedFrame.of(entry(momentLabel = "9 p.m.")) != a)
        assertTrue(ViewedFrame.of(entry(feels = 42.0)) != a)
        assertTrue(ViewedFrame.of(entry(items = listOf("Singlet"))) != a)
        assertTrue(ViewedFrame.of(entry(heroRef = null)) != a)
    }

    // ---- §7E.4 recordable hero ----------------------------------

    @Test
    fun `recordable hero - a primary serve is recorded, a fallback serve never is`() {
        assertEquals(
            "https://example.test/personal.jpg",
            recordableHeroRef("https://example.test/personal.jpg", "https://example.test/fallback.jpg")
        )
        assertNull(
            recordableHeroRef(null, "https://example.test/fallback.jpg")
        )
        assertNull(recordableHeroRef(null, null))
    }

    // ---- §7E.2 atomic presented snapshot -------------------------

    @Test
    fun `presented snapshot flips atomically - every field from the same entry`() {
        val e = entry(
            place = "Los Angeles, CA",
            date = today.plusDays(1),
            feels = 66.0
        )
        val snap = PresentedSnapshot.of(e, today, TemperatureUnit.FAHRENHEIT)
        assertEquals(e, snap.entry)
        assertEquals(
            WearIveRunDockNarration.line(e, today).text, snap.narration.text
        )
        assertEquals(
            "Planned for Los Angeles, CA · Tomorrow", snap.narration.text
        )
        assertEquals(
            WearIveRunDockNarration.entryLine(e, today, TemperatureUnit.FAHRENHEIT),
            snap.entryLine
        )
        assertTrue(snap.entryLine.startsWith("Los Angeles, CA · Tomorrow"))
        assertTrue(snap.entryLine.contains("66"))
        assertEquals(4, snap.barEntry.itemCount)
        assertEquals(e.items, snap.barEntry.itemNames)
        assertEquals(e.heroRef, snap.barEntry.thumbnailUrl)
        assertEquals(e, snap.barEntry.sourceEntry)
    }

    // ---- §7E.4 thumbnail hold ------------------------------------

    @Test
    fun `thumbnail hold - a shown thumbnail never returns to an empty slot`() {
        assertEquals("a.jpg", ThumbnailHold.displayed("a.jpg", null))
        assertEquals(
            "a.jpg",
            ThumbnailHold.displayed(null, "a.jpg")
        )
        assertEquals("b.jpg", ThumbnailHold.displayed("b.jpg", "a.jpg"))
        assertNull(ThumbnailHold.displayed(null, null))
    }

    // ---- §7C chrome policy ----------------------------------------

    @Test
    fun `chrome policy - the pair offset from the gear is constant at every width`() {
        for (width in listOf(328f, 358f, 500f, 768f, 1200f)) {
            assertEquals(
                "width $width",
                HeroChromePolicy.PAIR_GAP_DP + HeroChromePolicy.BUTTON_DP,
                HeroChromePolicy.shareOffsetFromGearDp(hasShare = true)
            )
            assertEquals(
                84f, HeroChromePolicy.endGroupWidthDp(hasShare = true), 0.001f
            )
        }
    }

    @Test
    fun `chrome policy - folded portrait with the approval place stacks, wide does not`() {
        // Approval fixture: place "Overland Park, KS". Its measured
        // text width on device is ~80dp -> pill ~127dp.
        val pill = HeroChromePolicy.placePillWidthDp(80f)
        assertEquals(127f, pill, 0.001f)
        // Folded portrait (Fold8 cover class, 390dp - 32dp padding).
        assertTrue(
            "folded portrait must change layout, not truncate the name",
            HeroChromePolicy.useStackedLayout(358f, pill, hasShare = true)
        )
        // Unfolded / wide class: one row, zone reserved, pair clears.
        assertFalse(
            HeroChromePolicy.useStackedLayout(768f, pill, hasShare = true)
        )
        // A short place (the v8 frame's Phoenix, AZ, pill ~97dp)
        // stays one-row even folded.
        assertFalse(
            HeroChromePolicy.useStackedLayout(
                358f, HeroChromePolicy.placePillWidthDp(50f), hasShare = true
            )
        )
    }

    @Test
    fun `chrome policy - the wordmark zone is centred and reserved`() {
        val (start, end) = HeroChromePolicy.wordmarkZone(358f)
        assertEquals((358f - 140f) / 2f, start, 0.001f)
        assertEquals(start + 140f, end, 0.001f)
    }

    // ---- §7A morph policy -----------------------------------------

    @Test
    fun `morph policy - exactly one narration at every progress sample`() {
        // RW-170 (Nov 12, 2026): the narration is ONE
        // composition whose slot migrates — exactly one
        // narration node exists at every progress value, by
        // construction. (The pre-RW-170 two-placement dissolve
        // pinned a legibility count over disjoint alpha
        // windows; that construction and its policy functions
        // are deleted. The full sampled sweep lives in
        // Rw135RebuildPolicyTest.)
        for (p in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(
                "exactly one narration node at p=$p",
                1, WearIveRunMorphPolicy.narrationNodeCount(p)
            )
        }
    }

    @Test
    fun `morph policy - expanded region dissolves over the back half`() {
        assertEquals(0f, WearIveRunMorphPolicy.expandedAlpha(0f), 0.001f)
        assertEquals(0f, WearIveRunMorphPolicy.expandedAlpha(0.45f), 0.001f)
        assertEquals(0.5f, WearIveRunMorphPolicy.expandedAlpha(0.725f), 0.001f)
        assertEquals(1f, WearIveRunMorphPolicy.expandedAlpha(1f), 0.001f)
    }

    @Test
    fun `morph policy - geometry endpoints are the two rest geometries`() {
        assertEquals(17f, WearIveRunMorphPolicy.wordmarkSp(0f), 0.001f)
        assertEquals(31f, WearIveRunMorphPolicy.wordmarkSp(1f), 0.001f)
        // RW-170 Move 4: the ONE narration's font size lerps
        // 12sp -> 15sp as its slot migrates (the pre-RW-170
        // two fixed placements, cross-faded by disjoint
        // dissolve windows, are deleted).
        assertEquals(12f, WearIveRunMorphPolicy.narrationSp(0f), 0.001f)
        assertEquals(15f, WearIveRunMorphPolicy.narrationSp(1f), 0.001f)
        // RB-05: radius is a constant 16dp — it never animates
        // (the pre-rebuild policy lerped 16 -> 24).
        assertEquals(16f, WearIveRunMorphPolicy.radiusDp(0f), 0.001f)
        assertEquals(16f, WearIveRunMorphPolicy.radiusDp(1f), 0.001f)
    }
}

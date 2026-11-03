package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-3 (RC-3): the Wear I've Run element's ONE state machine — the
 * transition table as a unit-test matrix. Every (state × event)
 * pair is asserted, including the illegal ones: an event with no
 * row for a state is a NO-OP there (it cannot occur as a driver),
 * never a fall-through into some other behavior.
 */
class WearIveRunElementMachineTest {

    private val a = WearIveRunElementKind.ABSENT
    private val m = WearIveRunElementKind.MINI
    private val e = WearIveRunElementKind.EXPANDED
    private val c = WearIveRunElementKind.CUE

    /** The complete table of record (§1.2 + the WS-3 driver table). */
    private val expected: Map<Pair<WearIveRunElementKind, WearIveRunElementEvent>, WearIveRunElementKind> =
        mapOf(
            // ABSENT — only a generation landing leaves it.
            (a to WearIveRunElementEvent.GENERATION_LANDED) to c,
            (a to WearIveRunElementEvent.TAP_BODY) to a,
            (a to WearIveRunElementEvent.EXPAND_CONTROL) to a,
            (a to WearIveRunElementEvent.COLLAPSE_CONTROL) to a,
            (a to WearIveRunElementEvent.CUE_TIMEOUT) to a,
            (a to WearIveRunElementEvent.SCROLL_ARRIVED_AT_END) to a,
            (a to WearIveRunElementEvent.SCROLL_DEPARTED_END) to a,
            (a to WearIveRunElementEvent.TRASH) to a,
            (a to WearIveRunElementEvent.DAY_ROLLOVER) to a,
            // MINI
            (m to WearIveRunElementEvent.GENERATION_LANDED) to c,
            (m to WearIveRunElementEvent.TAP_BODY) to e,
            (m to WearIveRunElementEvent.EXPAND_CONTROL) to e,
            (m to WearIveRunElementEvent.COLLAPSE_CONTROL) to m,
            (m to WearIveRunElementEvent.CUE_TIMEOUT) to m,
            (m to WearIveRunElementEvent.SCROLL_ARRIVED_AT_END) to e,
            (m to WearIveRunElementEvent.SCROLL_DEPARTED_END) to m,
            (m to WearIveRunElementEvent.TRASH) to a,
            (m to WearIveRunElementEvent.DAY_ROLLOVER) to a,
            // EXPANDED
            (e to WearIveRunElementEvent.GENERATION_LANDED) to c,
            (e to WearIveRunElementEvent.TAP_BODY) to e,
            (e to WearIveRunElementEvent.EXPAND_CONTROL) to e,
            (e to WearIveRunElementEvent.COLLAPSE_CONTROL) to m,
            (e to WearIveRunElementEvent.CUE_TIMEOUT) to e,
            (e to WearIveRunElementEvent.SCROLL_ARRIVED_AT_END) to e,
            (e to WearIveRunElementEvent.SCROLL_DEPARTED_END) to m,
            (e to WearIveRunElementEvent.TRASH) to a,
            (e to WearIveRunElementEvent.DAY_ROLLOVER) to a,
            // CUE — a timed presentation of EXPANDED.
            (c to WearIveRunElementEvent.GENERATION_LANDED) to c,
            (c to WearIveRunElementEvent.TAP_BODY) to e,
            (c to WearIveRunElementEvent.EXPAND_CONTROL) to e,
            (c to WearIveRunElementEvent.COLLAPSE_CONTROL) to m,
            (c to WearIveRunElementEvent.CUE_TIMEOUT) to m,
            (c to WearIveRunElementEvent.SCROLL_ARRIVED_AT_END) to c,
            (c to WearIveRunElementEvent.SCROLL_DEPARTED_END) to c,
            (c to WearIveRunElementEvent.TRASH) to a,
            (c to WearIveRunElementEvent.DAY_ROLLOVER) to a
        )

    @Test
    fun `the table is total - every state times every event is asserted`() {
        assertEquals(
            WearIveRunElementKind.values().size * WearIveRunElementEvent.values().size,
            expected.size
        )
        for (kind in WearIveRunElementKind.values()) {
            for (event in WearIveRunElementEvent.values()) {
                assertTrue("missing row $kind x $event", expected.containsKey(kind to event))
            }
        }
    }

    @Test
    fun `every transition matches the table of record`() {
        for ((pair, want) in expected) {
            val (kind, event) = pair
            assertEquals(
                "$kind x $event",
                want,
                WearIveRunElementMachine.transition(kind, event)
            )
        }
    }

    @Test
    fun `end of scroll is symmetric - arrival expands, departure collapses`() {
        assertEquals(
            WearIveRunElementKind.EXPANDED,
            WearIveRunElementMachine.transition(m, WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
        )
        assertEquals(
            WearIveRunElementKind.MINI,
            WearIveRunElementMachine.transition(e, WearIveRunElementEvent.SCROLL_DEPARTED_END)
        )
        // Arrival while EXPANDED stays; departure while MINI is a no-op
        // (the D-07 latch — expand with no collapse counterpart — is gone).
        assertEquals(
            WearIveRunElementKind.EXPANDED,
            WearIveRunElementMachine.transition(e, WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
        )
        assertEquals(
            WearIveRunElementKind.MINI,
            WearIveRunElementMachine.transition(m, WearIveRunElementEvent.SCROLL_DEPARTED_END)
        )
    }

    @Test
    fun `the event set is exactly the driver list - no async landing is a driver`() {
        // The abolished drivers (D-07): no event exists for a weather
        // refresh, a hero URL resolution, a location change, a unit
        // re-render, or a foregrounding. If a future change adds one,
        // this test is the tripwire.
        assertEquals(
            setOf(
                "GENERATION_LANDED", "TAP_BODY", "EXPAND_CONTROL", "COLLAPSE_CONTROL",
                "CUE_TIMEOUT", "SCROLL_ARRIVED_AT_END", "SCROLL_DEPARTED_END",
                "TRASH", "DAY_ROLLOVER"
            ),
            WearIveRunElementEvent.values().map { it.name }.toSet()
        )
        assertEquals(
            setOf("ABSENT", "MINI", "EXPANDED", "CUE"),
            WearIveRunElementKind.values().map { it.name }.toSet()
        )
    }

    @Test
    fun `cue timing and scroll thresholds are the spec values`() {
        assertEquals(3_000L, WearIveRunElementMachine.CUE_TIMEOUT_MS)
        assertEquals(900L, WearIveRunElementMachine.CUE_PULSE_MS)
        assertEquals(350L, WearIveRunElementMachine.CUE_RISE_MS)
        assertEquals(12f, WearIveRunElementMachine.CUE_RISE_DP, 0.001f)
        assertEquals(8, WearIveRunElementMachine.END_ARRIVAL_EPSILON_DP)
        assertEquals(48, WearIveRunElementMachine.END_DEPARTURE_THRESHOLD_DP)
        assertEquals(250L, WearIveRunElementMachine.TRASH_COLLAPSE_MS)
    }

    @Test
    fun `dim and reduced-motion policies are the settled values`() {
        assertEquals(0.5f, WearIveRunMiniBarPolicy.SHEET_DIMMED_ALPHA, 0.001f)
        assertEquals(0.5f, WearIveRunMiniBarPolicy.surfaceAlpha(dimmedBySheet = true), 0.001f)
        assertEquals(1.0f, WearIveRunMiniBarPolicy.surfaceAlpha(dimmedBySheet = false), 0.001f)
        assertTrue(WearIveRunMiniBarPolicy.cuePulseEnabled(1.0f))
        assertTrue(!WearIveRunMiniBarPolicy.cuePulseEnabled(0.0f))
    }
}

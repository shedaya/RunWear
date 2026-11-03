package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-25 / WS-3: the "Wear I've Run" narration formatter and
 * translucency policy — pure rules, no composition. The holder tests
 * here pin the machine's basic shape (ABSENT at start, creation
 * lands in CUE, one cue per distinct version); the full transition
 * matrix and interruption suite live in
 * WearIveRunElementMachineTest / WearIveRunElementHolderTest.
 */
class WearIveRunNarrationFormatterTest {

    @Test
    fun `wordmark is the settled name`() {
        assertEquals("Wear I've Run", WearIveRunNarrationFormatter.WORDMARK)
    }

    @Test
    fun `added line uses the comma form for place and time`() {
        val narration = WearIveRunNarrationFormatter.added(place = "New York", timeLabel = "Now")
        assertEquals("Added New York, Now", narration.text)
        assertEquals("New York, Now", narration.payload)
        assertEquals(6, narration.payloadStart)
        assertEquals(narration.text.length, narration.payloadEnd)
    }

    @Test
    fun `added line carries a clock time the same way`() {
        val narration = WearIveRunNarrationFormatter.added(place = "Aspen", timeLabel = "7:00 AM")
        assertEquals("Added Aspen, 7:00 AM", narration.text)
        assertEquals("Aspen, 7:00 AM", narration.payload)
    }

    @Test
    fun `update line names the payload`() {
        val narration = WearIveRunNarrationFormatter.update(payload = "Wind jacket")
        assertEquals("Update to Wind jacket", narration.text)
        assertEquals("Wind jacket", narration.payload)
        assertEquals("Update to ".length, narration.payloadStart)
    }
}

class WearIveRunMiniBarPolicyTest {

    @Test
    fun `mini state is translucent - 82 percent dark, 85 percent light`() {
        assertEquals(0.82f, WearIveRunMiniBarPolicy.backgroundAlpha(isFullExpression = false, darkTheme = true), 0.001f)
        assertEquals(0.85f, WearIveRunMiniBarPolicy.backgroundAlpha(isFullExpression = false, darkTheme = false), 0.001f)
    }

    @Test
    fun `full expression is fully opaque in both themes`() {
        assertEquals(1.0f, WearIveRunMiniBarPolicy.backgroundAlpha(isFullExpression = true, darkTheme = true), 0.001f)
        assertEquals(1.0f, WearIveRunMiniBarPolicy.backgroundAlpha(isFullExpression = true, darkTheme = false), 0.001f)
    }

    @Test
    fun `the expanded and cue states count as full expression - mini and absent do not`() {
        val entry = WearIveRunEntry(place = "New York", timeLabel = "Now")
        val narration = WearIveRunNarrationFormatter.added("New York", "Now")
        assertTrue(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Expanded(narration, entry)))
        assertTrue(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Cue(narration, entry)))
        assertFalse(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Mini(narration, entry)))
        assertFalse(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Absent))
    }
}

class WearIveRunAbsentStateTest {

    @Test
    fun `the state holder starts ABSENT before any entry exists`() {
        assertEquals(WearIveRunBarState.Absent, WearIveRunBarStateHolder().state.value)
    }

    @Test
    fun `creation lands in CUE and cues exactly once per distinct version`() {
        val holder = WearIveRunBarStateHolder()
        val today = java.time.LocalDate.of(2026, 11, 2)
        val entry = com.runwear.app.weariverun.WearIveRunDayEntry(
            date = today, place = "New York", feelsLikeF = 41.0,
            conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
            items = listOf("Tights"), heroRef = null
        )
        holder.onEntriesChanged(listOf(entry), today)
        // WS-3: creation is a GENERATION_LANDED — the element lands
        // in CUE (the timed presentation of EXPANDED), not Mini.
        assertTrue(holder.state.value is WearIveRunBarState.Cue)
        assertEquals(1, holder.cueSequence.value)
        // WS-4 (D-08): creation narration is the design's pattern —
        // simple place + the narration day form "Now".
        val created = (holder.state.value as WearIveRunBarState.Cue).narration
        assertEquals("Added New York, Now", created.text)
        // Same settled version re-delivered: no second cue, no move.
        holder.onEntriesChanged(listOf(entry), today)
        assertEquals(1, holder.cueSequence.value)
        assertTrue(holder.state.value is WearIveRunBarState.Cue)
        // The cue times out to MINI.
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        assertTrue(holder.state.value is WearIveRunBarState.Mini)
        // A later generation the same day with a NEW KIT replaces
        // it: an update cue, exactly once.
        holder.onEntriesChanged(listOf(entry.copy(items = listOf("Jacket"))), today)
        assertEquals(2, holder.cueSequence.value)
        assertTrue(holder.state.value is WearIveRunBarState.Cue)
        val narration = (holder.state.value as WearIveRunBarState.Cue).narration
        // WS-4 (D-08): the update payload is the entry's REAL payload —
        // its first kit item — not "place, Today".
        assertEquals("Update to Jacket", narration.text)
        assertEquals("Jacket", narration.payload)
    }
}

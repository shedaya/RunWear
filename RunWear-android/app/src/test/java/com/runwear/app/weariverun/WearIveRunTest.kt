package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-25: the "Wear I've Run" narration formatter and translucency
 * policy — pure rules, no composition. The bar ships in pre-add state
 * (the collection is not built), so these tests also pin that the
 * state holder can only yield PreAdd in this build.
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
    fun `only the expanded state counts as full expression`() {
        val entry = WearIveRunEntry(place = "New York", timeLabel = "Now")
        val narration = WearIveRunNarrationFormatter.added("New York", "Now")
        assertTrue(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Expanded(narration, entry)))
        assertFalse(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.Mini(narration, entry)))
        assertFalse(WearIveRunMiniBarPolicy.isFullExpression(WearIveRunBarState.PreAdd))
    }
}

class WearIveRunPreAddStateTest {

    @Test
    fun `the state holder starts at PreAdd before any entry exists`() {
        assertEquals(WearIveRunBarState.PreAdd, WearIveRunBarStateHolder().state.value)
    }

    @Test
    fun `the holder leaves PreAdd once an entry exists and cues once per change`() {
        val holder = WearIveRunBarStateHolder()
        val today = java.time.LocalDate.of(2026, 11, 2)
        val entry = com.runwear.app.weariverun.WearIveRunDayEntry(
            date = today, place = "New York", feelsLikeF = 41.0,
            conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
            items = listOf("Tights"), heroRef = null
        )
        holder.onEntriesChanged(listOf(entry), today)
        assertTrue(holder.state.value is WearIveRunBarState.Mini)
        assertEquals(1, holder.cueCount.value)
        // Same settled version re-delivered: no second cue.
        holder.onEntriesChanged(listOf(entry), today)
        assertEquals(1, holder.cueCount.value)
        // A later generation the same day replaces it: an update cue.
        holder.onEntriesChanged(listOf(entry.copy(items = listOf("Jacket"))), today)
        assertEquals(2, holder.cueCount.value)
        val narration = (holder.state.value as WearIveRunBarState.Mini).narration
        assertEquals("Update to New York, Today", narration.text)
    }
}

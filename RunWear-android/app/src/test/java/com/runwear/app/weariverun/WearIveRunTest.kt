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
    fun `mini state is translucent - 78 percent dark, 85 percent light`() {
        assertEquals(0.78f, WearIveRunMiniBarPolicy.backgroundAlpha(isFullExpression = false, darkTheme = true), 0.001f)
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
    fun `the state holder yields PreAdd only in this build`() {
        assertEquals(WearIveRunBarState.PreAdd, WearIveRunBarStateHolder().state.value)
    }
}

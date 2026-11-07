package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-135 (Direction A / A-prime): the dock narration as a pure
 * function of the recorded entry (research section 7.0 / 7.2),
 * the entry-line anatomy, the dock-visibility invariant, and the
 * holder sequences behind section 7.8 checks 2 and 3.
 */
class Rw135DockTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 7)

    private fun entry(
        date: LocalDate = today,
        place: String = "Boston, Massachusetts",
        items: List<String> = listOf("Light Tights", "Wind Shell"),
        feels: Double = 41.0
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
        items = items, heroRef = null
    )

    // -- section 7.2 forms ------------------------------------------

    @Test
    fun `settled today reads Updated to the place`() {
        val line = WearIveRunDockNarration.line(entry(), today)
        assertEquals("Updated to Boston, MA", line.text)
        assertEquals("Boston, MA", line.payload)
    }

    @Test
    fun `a future-day plan reads Planned for - never Updated to`() {
        val line = WearIveRunDockNarration.line(
            entry(date = today.plusDays(1)), today
        )
        assertEquals("Planned for Boston, MA · Tomorrow", line.text)
        assertEquals("Boston, MA", line.payload)
    }

    @Test
    fun `a plan further out names the weekday`() {
        // 2026-11-07 is a Saturday; +3 days is Tuesday, Nov 10.
        val line = WearIveRunDockNarration.line(
            entry(date = today.plusDays(3)), today
        )
        assertEquals("Planned for Boston, MA · Tuesday, Nov 10", line.text)
    }

    @Test
    fun `an older rest entry keeps the settled form`() {
        val line = WearIveRunDockNarration.line(
            entry(date = today.minusDays(2)), today
        )
        assertEquals("Updated to Boston, MA", line.text)
    }

    @Test
    fun `the entry line carries place day feels-like and items from the record`() {
        assertEquals(
            "Boston, MA · Today — 41°F feels like · Light Tights · Wind Shell",
            WearIveRunDockNarration.entryLine(
                entry(), today, TemperatureUnit.FAHRENHEIT
            )
        )
        assertEquals(
            "Boston, MA · Tomorrow — 5°C feels like · Light Tights · Wind Shell",
            WearIveRunDockNarration.entryLine(
                entry(date = today.plusDays(1)), today,
                TemperatureUnit.CELSIUS
            )
        )
    }

    // -- the dock-visibility invariant -------------------------------

    @Test
    fun `the dock shows only while present and its footer is off-screen`() {
        assertTrue(WearIveRunDockPolicy.dockVisible(true, false))
        assertFalse(WearIveRunDockPolicy.dockVisible(true, true))
        assertFalse(WearIveRunDockPolicy.dockVisible(false, false))
        assertFalse(WearIveRunDockPolicy.dockVisible(false, true))
    }

    @Test
    fun `the dock geometry is the ruled section 7_6 form`() {
        assertEquals(12, WearIveRunDockPolicy.DOCK_INSET_DP)
        assertEquals(14, WearIveRunDockPolicy.DOCK_BOTTOM_DP)
        assertEquals(16, WearIveRunDockPolicy.DOCK_RADIUS_DP)
    }

    // -- section 7.8 check 2: the switch sequence --------------------
    // The line is derived at the holder's emit funnel from the
    // recorded entry alone; there is no view-state input anywhere
    // in the chain, so a place the user is merely VIEWING cannot
    // appear. The sequence pins the record-driven behaviour: the
    // line names Boston only once the Boston entry IS the record.

    @Test
    fun `check 2 - the line names a place only once the record holds it`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(
            listOf(entry(place = "New York, NY")), today
        )
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        var state = holder.state.value as WearIveRunBarState.Present
        assertEquals("Updated to New York, NY", state.narration.text)

        // Identical re-delivery (a settle that changed nothing the
        // record holds): the line is unchanged and no cue re-fires.
        val cueBefore = holder.cueSequence.value
        holder.onEntriesChanged(
            listOf(entry(place = "New York, NY")), today
        )
        state = holder.state.value as WearIveRunBarState.Present
        assertEquals("Updated to New York, NY", state.narration.text)
        assertEquals(cueBefore, holder.cueSequence.value)

        // The place switch LANDS in the record: now, and only now,
        // the line names the new place.
        holder.onEntriesChanged(
            listOf(entry(place = "Boston, Massachusetts")), today
        )
        state = holder.state.value as WearIveRunBarState.Present
        assertEquals("Updated to Boston, MA", state.narration.text)
        assertEquals(
            "Boston, Massachusetts",
            holder.currentEntryFlow.value?.place
        )
    }

    // -- section 7.8 check 3: the plan round trip --------------------

    @Test
    fun `check 3 - a future-day plan reads Planned for and settles to Updated to on its day`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(
            listOf(entry(date = today.plusDays(1))), today
        )
        var state = holder.state.value as WearIveRunBarState.Present
        assertEquals(
            "Planned for Boston, MA · Tomorrow", state.narration.text
        )

        // The day arrives: rollover clears the presentation, and
        // the ordinary settle of that day's entry takes over in
        // the settled form — both ends read from the record.
        holder.onDayRollover(today.plusDays(1))
        assertEquals(
            WearIveRunBarState.Absent, holder.state.value
        )
        holder.onEntriesChanged(
            listOf(entry(date = today.plusDays(1))), today.plusDays(1)
        )
        state = holder.state.value as WearIveRunBarState.Present
        assertEquals("Updated to Boston, MA", state.narration.text)
    }

    // -- section 7.8 check 5 (record half): absence ------------------

    @Test
    fun `check 5 - no session entry means no presented line`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(emptyList(), today)
        assertEquals(WearIveRunBarState.Absent, holder.state.value)
        assertEquals(null, holder.currentEntryFlow.value)
    }
}

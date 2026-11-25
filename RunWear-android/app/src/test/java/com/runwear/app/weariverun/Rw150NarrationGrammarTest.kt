package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * RW-150 (Nov 9, 2026): the Wear I've Run narration grammar,
 * per change class, for BOTH displayed surfaces — the dock line
 * and the footer's detail line (both are built by
 * [WearIveRunDockNarration], the one narration source; the
 * footer's header line IS the dock line, the same object).
 *
 * The spec: RW-98 (Sol, Nov 6, 2026) — the line reads "Updated
 * to" and names the new state IN FULL, whichever dimension
 * changed ("Updated to Miami, FL, Now" for a place change at
 * Now; "Updated to November 12 7 p.m." for future moments) —
 * reconciled with RW-135 (the line is a pure function of the
 * recorded entry; future days wear the "Planned for {place} ·
 * {day}" form): RW-98 governs content, RW-135 governs form, so
 * a future-day plan WITH a picked time names the time in the
 * plan form's tail.
 *
 * BEFORE this fix, the displayed builder
 * ([WearIveRunDockNarration.line]) produced the RW-135 section
 * 7.2 short forms — "Updated to {place}" (no ", Now", no
 * picked time) and "Planned for {place} · {day}" (no picked
 * time) — and the RW-98 change narration
 * ([WearIveRunUpdateNarration]) had no production caller at
 * all: its grammar was unit-tested but never displayed.
 */
class Rw150NarrationGrammarTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 9) // Monday

    private fun entry(
        date: LocalDate = today,
        place: String = "New York, New York",
        moment: String? = null,
        items: List<String> = listOf("Long Sleeve", "Tights"),
        feels: Double = 41.0
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = "Cool", isSnowing = false, isRaining = false,
        items = items, heroRef = null, momentLabel = moment
    )

    private fun dock(e: WearIveRunDayEntry): String =
        WearIveRunDockNarration.line(e, today).text

    private fun footer(e: WearIveRunDayEntry): String =
        WearIveRunDockNarration.entryLine(e, today, TemperatureUnit.FAHRENHEIT)

    // ── The class table (dock line / footer detail line) ──────

    @Test
    fun `class 1 - place at Now`() {
        val e = entry(place = "Miami, Florida")
        assertEquals("Updated to Miami, FL, Now", dock(e))
        assertEquals(
            "Miami, FL · Today — 41°F feels like · Long Sleeve · Tights",
            footer(e)
        )
    }

    @Test
    fun `class 2 - place at a picked time`() {
        val e = entry(place = "Phoenix, Arizona", moment = "2 p.m.")
        assertEquals("Updated to Phoenix, AZ, 2 p.m.", dock(e))
        assertEquals(
            "Phoenix, AZ · Today · 2 p.m. — 41°F feels like · Long Sleeve · Tights",
            footer(e)
        )
    }

    @Test
    fun `class 3 - time-only change same day moves the line`() {
        val before = entry()
        val after = entry(moment = "3 p.m.")
        assertEquals("Updated to New York, NY, Now", dock(before))
        assertEquals("Updated to New York, NY, 3 p.m.", dock(after))
        assertNotEquals(dock(before), dock(after))
        // The change narration agrees: a moment payload.
        assertEquals(
            "Updated to 3 p.m.",
            WearIveRunUpdateNarration.narration(before, after, today)?.text
        )
    }

    @Test
    fun `class 4 - date change, day only`() {
        val e = entry(date = today.plusDays(1))
        assertEquals("Planned for New York, NY · Tomorrow", dock(e))
        assertEquals(
            "New York, NY · Tomorrow — 41°F feels like · Long Sleeve · Tights",
            footer(e)
        )
    }

    @Test
    fun `class 5 - date and time change - the plan names the picked time`() {
        val e = entry(date = today.plusDays(3), moment = "7 p.m.")
        assertEquals(
            "Planned for New York, NY · Thursday, Nov 12 · 7 p.m.", dock(e)
        )
        assertEquals(
            "New York, NY · Thursday, Nov 12 · 7 p.m. — " +
                "41°F feels like · Long Sleeve · Tights",
            footer(e)
        )
        // RW-98's own future-moment example, through the change
        // narration for a moment change on that day.
        val dayOnly = entry(date = today.plusDays(3))
        assertEquals(
            "Updated to November 12 7 p.m.",
            WearIveRunUpdateNarration.narration(dayOnly, e, today)?.text
        )
    }

    @Test
    fun `class 6 - return to Now`() {
        val picked = entry(moment = "2 p.m.")
        val now = entry()
        assertEquals("Updated to New York, NY, Now", dock(now))
        assertEquals(
            "Updated to Now",
            WearIveRunUpdateNarration.narration(picked, now, today)?.text
        )
    }

    @Test
    fun `class 7 - a place change at a picked time names the moment in the update narration`() {
        val before = entry(place = "Boston, Massachusetts")
        val after = entry(place = "Phoenix, Arizona", moment = "2 p.m.")
        assertEquals(
            "Updated to Phoenix, AZ, 2 p.m.",
            WearIveRunUpdateNarration.narration(before, after, today)?.text
        )
        // And a place change at Now is RW-98's verbatim example.
        val miami = entry(place = "Miami, Florida")
        assertEquals(
            "Updated to Miami, FL, Now",
            WearIveRunUpdateNarration.narration(before, miami, today)?.text
        )
    }

    @Test
    fun `boundary - an older rest entry keeps the settled short form`() {
        // RW-98's classes cover today's and future states; the
        // past-entry rest form is unchanged (stated boundary,
        // not an oversight).
        val e = entry(date = today.minusDays(2))
        assertEquals("Updated to New York, NY", dock(e))
    }

    @Test
    fun `dock and footer never disagree about the moment`() {
        // For every class, if the entry carries a picked moment,
        // both surfaces name it; if it is at Now today, the dock
        // says ", Now".
        val classes = listOf(
            entry(), entry(moment = "2 p.m."),
            entry(date = today.plusDays(1)),
            entry(date = today.plusDays(3), moment = "7 p.m.")
        )
        for (e in classes) {
            val m = e.momentLabel
            if (m != null) {
                assertTrue2(dock(e).contains(m), "dock drops $m: ${dock(e)}")
                assertTrue2(footer(e).contains(m), "footer drops $m: ${footer(e)}")
            }
        }
    }

    private fun assertTrue2(cond: Boolean, msg: String) {
        if (!cond) throw AssertionError(msg)
    }
}

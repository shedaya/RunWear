package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-3 (RC-3): the state holder as the machine's host — integration
 * of the entry list with the transition table, plus the gate's
 * interruption suite:
 *
 * - trash mid-cue → ABSENT, and the element stays absent (no
 *   fallback resurrection) until a later generation re-creates the
 *   day's entry, which fires the creation cue;
 * - a location/hero/weather landing mid-cue that does not change the
 *   settled kit moves NOTHING (kind and cue sequence untouched),
 *   while its content still lands silently;
 * - the cue fires exactly once per landing (identical re-delivery,
 *   hero-only change, and feels-like drift never re-fire it);
 * - day rollover → ABSENT until the new day's first generation;
 * - scroll arrival/departure are symmetric through the holder.
 */
class WearIveRunElementHolderTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 2)
    private val tomorrow: LocalDate = today.plusDays(1)

    private fun entry(
        date: LocalDate = today,
        place: String = "New York, New York",
        items: List<String> = listOf("Light Tights", "Wind Shell"),
        feels: Double = 41.0,
        heroRef: String? = null
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
        items = items, heroRef = heroRef
    )

    private fun kind(holder: WearIveRunBarStateHolder) = holder.state.value.kind

    private fun present(holder: WearIveRunBarStateHolder) =
        holder.state.value as WearIveRunBarState.Present

    @Test
    fun `an older entry alone presents MINI at rest - no cue for someone else's day`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry(date = today.minusDays(1))), today)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        assertEquals(0, holder.cueSequence.value)
    }

    @Test
    fun `tap body and expand control expand MINI - collapse returns it`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        holder.onEvent(WearIveRunElementEvent.TAP_BODY)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        holder.onEvent(WearIveRunElementEvent.COLLAPSE_CONTROL)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        holder.onEvent(WearIveRunElementEvent.EXPAND_CONTROL)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        holder.onEvent(WearIveRunElementEvent.COLLAPSE_CONTROL)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
    }

    @Test
    fun `a touch during CUE cancels the cue into the touched state`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        holder.onEvent(WearIveRunElementEvent.TAP_BODY)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        // A late CUE_TIMEOUT after the touch is an illegal transition:
        // EXPANDED does not collapse on a stale timer.
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
    }

    @Test
    fun `scroll arrival expands and departure collapses - symmetric through the holder`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEvent(WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        // Arrival again while expanded: stays (no oscillation driver).
        holder.onEvent(WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        holder.onEvent(WearIveRunElementEvent.SCROLL_DEPARTED_END)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        // And back — every return to the end expands again, every
        // departure collapses again. No latch either way.
        holder.onEvent(WearIveRunElementEvent.SCROLL_ARRIVED_AT_END)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder))
        holder.onEvent(WearIveRunElementEvent.SCROLL_DEPARTED_END)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
    }

    @Test
    fun `trash mid-cue goes ABSENT and stays absent until a re-creation lands`() {
        val holder = WearIveRunBarStateHolder()
        val older = entry(date = today.minusDays(1), place = "Aspen, Colorado")
        holder.onEntriesChanged(listOf(entry(), older), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        holder.onEvent(WearIveRunElementEvent.TRASH)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))
        assertNull(holder.currentEntry())
        // The store now reports the list WITHOUT today's entry: the
        // older entry must not resurrect the element behind the
        // user's trash tap.
        holder.onEntriesChanged(listOf(older), today)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))
        assertEquals(1, holder.cueSequence.value)
        // A later generation re-creates today's entry: it presents
        // again and fires the creation cue exactly once.
        holder.onEntriesChanged(listOf(entry(), older), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(2, holder.cueSequence.value)
        assertEquals("Added New York, NY, Now", present(holder).narration.text)
    }

    @Test
    fun `trash in MINI and in EXPANDED both go ABSENT`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        holder.onEvent(WearIveRunElementEvent.TRASH)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))

        val holder2 = WearIveRunBarStateHolder()
        holder2.onEntriesChanged(listOf(entry()), today)
        holder2.onEvent(WearIveRunElementEvent.TAP_BODY)
        assertEquals(WearIveRunElementKind.EXPANDED, kind(holder2))
        holder2.onEvent(WearIveRunElementEvent.TRASH)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder2))
    }

    @Test
    fun `landings that do not change the settled kit never move the element mid-cue`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(1, holder.cueSequence.value)

        // Hero URL landing mid-cue: content lands (the thumbnail),
        // state does not move, the cue does not re-fire.
        holder.onEntriesChanged(listOf(entry(heroRef = "hero://cell-1")), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(1, holder.cueSequence.value)
        assertEquals("hero://cell-1", present(holder).entry.thumbnailUrl)

        // Weather drift mid-cue (feels-like moved, kit identical):
        // silent content update.
        holder.onEntriesChanged(listOf(entry(feels = 39.0, heroRef = "hero://cell-1")), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(1, holder.cueSequence.value)
        assertEquals(39.0, present(holder).entry.feelsLikeF!!, 0.001)

        // Identical re-delivery: nothing at all.
        holder.onEntriesChanged(listOf(entry(feels = 39.0, heroRef = "hero://cell-1")), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(1, holder.cueSequence.value)
        // And the creation narration still stands — no phantom
        // "Update to…" was announced by any of these landings.
        assertEquals("Added New York, NY, Now", present(holder).narration.text)
    }

    @Test
    fun `silent landings in MINI stay MINI and never announce an update`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEntriesChanged(listOf(entry(heroRef = "hero://x", feels = 44.0)), today)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))
        assertEquals(1, holder.cueSequence.value)
        assertEquals("Added New York, NY, Now", present(holder).narration.text)
    }

    @Test
    fun `a kit change is a real update - it cues exactly once with the first kit item`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEntriesChanged(
            listOf(entry(items = listOf("Thermal Tights", "Headband"))), today
        )
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(2, holder.cueSequence.value)
        assertEquals("Update to Thermal Tights", present(holder).narration.text)
        // The same update re-delivered does not cue a second time.
        holder.onEntriesChanged(
            listOf(entry(items = listOf("Thermal Tights", "Headband"))), today
        )
        assertEquals(2, holder.cueSequence.value)
    }

    @Test
    fun `a generation for a new place today is a creation cue`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        // The user changed location; the new place's first generation
        // for today settles — a creation for that place.
        holder.onEntriesChanged(listOf(entry(place = "Kansas City, Missouri")), today)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals(2, holder.cueSequence.value)
        assertEquals("Added Kansas City, MO, Now", present(holder).narration.text)
    }

    @Test
    fun `day rollover goes ABSENT until the new day's first generation`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        assertEquals(WearIveRunElementKind.MINI, kind(holder))

        holder.onDayRollover(tomorrow)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))
        assertNull(holder.currentEntry())
        // Yesterday's entry still exists in the store — it must not
        // present in the new day.
        holder.onEntriesChanged(listOf(entry()), tomorrow)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))
        // The new day's first generation lands: creation cue.
        holder.onEntriesChanged(listOf(entry(date = tomorrow), entry()), tomorrow)
        assertEquals(WearIveRunElementKind.CUE, kind(holder))
        assertEquals("Added New York, NY, Now", present(holder).narration.text)
    }

    @Test
    fun `rollover arriving through the entries seam behaves the same`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry()), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        // No explicit rollover call — the next entries delivery
        // carries the new day.
        holder.onEntriesChanged(listOf(entry()), tomorrow)
        assertEquals(WearIveRunElementKind.ABSENT, kind(holder))
        assertTrue(holder.cueSequence.value == 1)
    }

    @Test
    fun `events on an ABSENT holder are no-ops - illegal transitions cannot occur`() {
        val holder = WearIveRunBarStateHolder()
        for (event in WearIveRunElementEvent.values()) {
            if (event == WearIveRunElementEvent.DAY_ROLLOVER) continue
            holder.onEvent(event)
            assertEquals("ABSENT x $event", WearIveRunElementKind.ABSENT, kind(holder))
        }
        assertEquals(0, holder.cueSequence.value)
    }
}

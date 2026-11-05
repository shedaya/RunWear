package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-4 (RC-4, D-08/D-09): every place-bearing surface of the Wear
 * I've Run element renders the SIMPLE place; the narration carries
 * the design's pattern (wordmark leads, short verb, payload in pine)
 * with the entry's real payload and the narration day form; the
 * expanded subtitle never duplicates the narration.
 *
 * Fixtures deliberately carry RAW geocode strings ("New York,
 * New York"), long place names, and multi-word regions — the holder
 * is fed what the pre-model app would have recorded, and the simple
 * form is still what must render.
 */
class WearIveRunSimplePlaceTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 2)

    private fun entry(
        place: String,
        date: LocalDate = today,
        items: List<String> = listOf("Light Tights", "Wind Shell"),
        feels: Double = 41.0
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
        items = items, heroRef = null
    )

    private fun holderWith(e: WearIveRunDayEntry): WearIveRunBarStateHolder {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(e), today)
        return holder
    }

    /** The presented content, whatever present state the machine is
     * in (WS-3: creation lands in CUE, the timed presentation of
     * EXPANDED — the narration assertions below are state-agnostic). */
    private fun mini(holder: WearIveRunBarStateHolder) =
        holder.state.value as WearIveRunBarState.Present

    @Test
    fun `creation narration is Added simple place comma Now - from a raw geocode input`() {
        val holder = holderWith(entry(place = "New York, New York"))
        val narration = mini(holder).narration
        assertEquals("Added New York, NY, Now", narration.text)
        assertEquals("New York, NY, Now", narration.payload)
        assertFalse(narration.text.contains("New York, New York"))
        assertFalse(narration.text.contains("Today"))
        // The bar entry itself carries the simple place, not the raw string.
        assertEquals("New York, NY", mini(holder).entry.place)
        assertEquals("Now", mini(holder).entry.timeLabel)
    }

    @Test
    fun `creation narration with a long place name and multi-word region stays simple`() {
        val holder = holderWith(entry(place = "Truth or Consequences, New Mexico"))
        assertEquals(
            "Added Truth or Consequences, NM, Now",
            mini(holder).narration.text
        )
        val nc = holderWith(entry(place = "Winston-Salem, North Carolina"))
        assertEquals("Added Winston-Salem, NC, Now", mini(nc).narration.text)
    }

    @Test
    fun `update narration payload names the place - never a garment`() {
        val holder = holderWith(entry(place = "New York, New York"))
        holder.onEntriesChanged(
            listOf(entry(place = "New York, New York", items = listOf("Thermal Tights", "Headband"))),
            today
        )
        val narration = mini(holder).narration
        // GEN-5 (C-7 + Addendum 8 item 2): the update payload is
        // the changed dimension — the simple place in its
        // "{place}, {day}" form. The bar entry no longer carries a
        // kit-fed payload field at all (that field is what the
        // gen-4 device build rendered as a garment narration), so
        // there is nothing garment-shaped left to assert on the
        // entry; the narration assertions below are the contract.
        assertEquals("Update to New York, NY, Now", narration.text)
        assertEquals("New York, NY, Now", narration.payload)
        assertFalse(narration.text.contains("Thermal Tights"))
        assertFalse(narration.text.contains("Today"))
    }

    @Test
    fun `update with no kit items falls back to the simple reading`() {
        val holder = holderWith(entry(place = "Aspen, Colorado", items = listOf("Tights")))
        holder.onEntriesChanged(
            listOf(entry(place = "Aspen, Colorado", items = emptyList(), feels = 30.0)),
            today
        )
        assertEquals("Update to Aspen, CO, Now", mini(holder).narration.text)
    }

    @Test
    fun `narration day form is Now for today and MMM d otherwise - never Today`() {
        assertEquals("Now", WearIveRunCollectionLogic.narrationDayForm(today, today))
        assertEquals(
            "Nov 1",
            WearIveRunCollectionLogic.narrationDayForm(LocalDate.of(2026, 11, 1), today)
        )
        // The collection keeps its own "Today" for its own surfaces.
        assertEquals("Today", WearIveRunCollectionLogic.dayForm(today, today))

        val holder = holderWith(entry(place = "Aspen, Colorado", date = LocalDate.of(2026, 11, 1)))
        assertEquals("Added Aspen, CO, Nov 1", mini(holder).narration.text)
    }

    @Test
    fun `expanded subtitle is the entry detail and duplicates nothing from the narration`() {
        val holder = holderWith(entry(place = "New York, New York"))
        // WS-3: a touch on the cueing element lands it in EXPANDED.
        holder.onEvent(WearIveRunElementEvent.TAP_BODY)
        val expanded = holder.state.value as WearIveRunBarState.Expanded
        val subtitle = WearIveRunSubtitle.detail(expanded.entry)
        // GEN-5 (RW-29 FA-34): the outfit line joins the subtitle
        // (the gen-3 line stopped at the count). Place and day
        // still live in the narration only — not repeated here.
        assertEquals("41° feels like · 2 items · Light Tights · Wind Shell", subtitle)
        assertFalse(subtitle!!.contains("New York"))
        assertFalse(subtitle.contains("Now"))
        assertFalse(subtitle.contains("Today"))
    }

    @Test
    fun `subtitle singularizes one item and is absent without weather`() {
        val one = WearIveRunEntry(
            place = "Aspen, CO", timeLabel = "Now", feelsLikeF = 30.0, itemCount = 1
        )
        assertEquals("30° feels like · 1 item", WearIveRunSubtitle.detail(one))
        val bare = WearIveRunEntry(place = "Aspen, CO", timeLabel = "Now")
        assertEquals(null, WearIveRunSubtitle.detail(bare))
    }

    @Test
    fun `mini narration is short by construction even for the longest fixture`() {
        // D-09: the mini line is one line; its content is bounded by
        // the simple form (city + 2-3 letter region + "Now") or by a
        // single kit-item name — never a raw geocode string that ends
        // mid-phrase at a comma.
        val holder = holderWith(entry(place = "Truth or Consequences, New Mexico"))
        val text = mini(holder).narration.text
        assertTrue(text.length <= "Added ".length + "Truth or Consequences, NM, Now".length)
        assertFalse(text.endsWith(","))
    }

    @Test
    fun `chapter covers carry the simple place`() {
        // Chapter grouping keys on the entry place the holder/store
        // seam normalized; covers render Chapter.place verbatim.
        val entries = listOf(
            entry(place = "New York, NY", date = today),
            entry(place = "New York, NY", date = today.minusDays(1)),
            entry(place = "Kansas City, MO", date = today)
        )
        val chapters = WearIveRunCollectionLogic.chapters(entries)
        assertEquals(setOf("New York, NY", "Kansas City, MO"), chapters.map { it.place }.toSet())
        assertTrue(chapters.none { it.place.contains("New York, New York") })
    }
}

package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WearIveRunCollectionLogicTest {

    private fun entry(
        date: String,
        place: String = "New York",
        feels: Double = 41.0,
        snowing: Boolean = false,
        raining: Boolean = false,
        items: List<String> = listOf("Tights")
    ) = WearIveRunDayEntry(
        date = LocalDate.parse(date),
        place = place,
        feelsLikeF = feels,
        conditionLabel = "Cloudy",
        isSnowing = snowing,
        isRaining = raining,
        items = items,
        heroRef = null
    )

    @Test
    fun `one entry per day per place - last settled version replaces`() {
        val first = entry("2026-11-02", items = listOf("Shorts"))
        val settled = entry("2026-11-02", items = listOf("Tights", "Jacket"))
        val out = WearIveRunCollectionLogic.upsert(
            WearIveRunCollectionLogic.upsert(emptyList(), first), settled
        )
        assertEquals(1, out.size)
        assertEquals(listOf("Tights", "Jacket"), out[0].items)
        // Same day, different place: a separate entry.
        val other = WearIveRunCollectionLogic.upsert(out, entry("2026-11-2".replace("11-2", "11-02"), place = "Aspen"))
        assertEquals(2, other.size)
    }

    @Test
    fun `multi-stay chapter counts stays and names the last stay`() {
        // Mar 2-4, Mar 8-10, Mar 11-13 interleaved with another place.
        val dates = listOf("2027-03-02", "2027-03-03", "2027-03-04",
            "2027-03-08", "2027-03-09", "2027-03-10",
            "2027-03-11", "2027-03-12", "2027-03-13", "2027-03-13")
        var entries = emptyList<WearIveRunDayEntry>()
        dates.dropLast(1).forEach { entries = WearIveRunCollectionLogic.upsert(entries, entry(it, place = "Kansas City", feels = 30.0)) }
        entries = WearIveRunCollectionLogic.upsert(entries, entry("2027-03-05", place = "Denver"))
        val chapter = WearIveRunCollectionLogic.chapters(entries).first { it.place == "Kansas City" }
        assertEquals(9, chapter.dayCount)
        assertEquals(2, chapter.stayCount) // Mar 8-13 is ONE stay (consecutive)
        assertTrue(chapter.summaryLine().startsWith("2 stays · 9 days · Last stay · Mar 11–13"))
    }

    @Test
    fun `stays sum to the chapter total`() {
        val dates = listOf("2027-01-01", "2027-01-02", "2027-01-05", "2027-02-01", "2027-02-02", "2027-02-03")
        val entries = dates.map { entry(it, place = "Aspen") }
        val chapter = WearIveRunCollectionLogic.chapters(entries).single()
        assertEquals(3, chapter.stayCount)
        assertEquals(entries.size, chapter.stays.sumOf { it.dayCount })
        assertEquals("Jan 1", WearIveRunCollectionLogic.spanLabel(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-01-01")))
    }

    @Test
    fun `single-stay chapter keeps its honest span`() {
        val entries = listOf(entry("2026-10-31", feels = 58.0), entry("2026-11-02", feels = 41.0), entry("2026-11-01", feels = 50.0))
        // Oct 31, Nov 1, Nov 2 are consecutive -> one stay, span Oct 31 - Nov 2
        val chapter = WearIveRunCollectionLogic.chapters(entries).single()
        assertEquals(1, chapter.stayCount)
        assertEquals("3 days · Oct 31 – Nov 2 · 41°–58°", chapter.summaryLine())
    }

    @Test
    fun `weather filter recomputes chapter summaries`() {
        val entries = listOf(
            entry("2026-11-01", feels = 30.0, snowing = true),
            entry("2026-11-02", feels = 41.0),
            entry("2026-11-03", feels = 55.0, raining = true),
            entry("2026-11-04", feels = 88.0)
        )
        val all = WearIveRunCollectionLogic.chapters(entries, WearWeatherFilter.ALL).single()
        assertEquals(4, all.dayCount)
        assertEquals("30°–88°", all.feelsRangeLabel())
        val snow = WearIveRunCollectionLogic.chapters(entries, WearWeatherFilter.SNOW).single()
        assertEquals(1, snow.dayCount)
        assertEquals("30°", snow.feelsRangeLabel())
        val cold = WearIveRunCollectionLogic.chapters(entries, WearWeatherFilter.COLD).single()
        assertEquals(1, cold.dayCount) // only the 30° day is < 40
        val heat = WearIveRunCollectionLogic.chapters(entries, WearWeatherFilter.HEAT).single()
        assertEquals(1, heat.dayCount)
    }

    @Test
    fun `month groups sum to the chapter and carry ranges`() {
        var entries = emptyList<WearIveRunDayEntry>()
        // 23 days in Feb 2027 (1st-23rd), feels 20..42
        for (d in 1..23) {
            entries = WearIveRunCollectionLogic.upsert(
                entries,
                entry("2027-02-%02d".format(d), feels = 19.0 + d)
            )
        }
        val groups = WearIveRunCollectionLogic.monthGroups(entries)
        assertEquals(1, groups.size)
        assertEquals(23, groups[0].count)
        assertEquals("20°–42°", groups[0].rangeLabel())
        assertEquals(entries.size, groups.sumOf { it.count })
    }

    @Test
    fun `store encode decode round-trips`() {
        val entries = listOf(entry("2026-11-02", items = listOf("Tights", "Jacket")))
        val decoded = WearIveRunStore.decode(WearIveRunStore.encode(entries))
        assertEquals(entries, decoded)
        assertTrue(WearIveRunStore.decode(null).isEmpty())
        assertTrue(WearIveRunStore.decode("not json").isEmpty())
    }

    @Test
    fun `day form names today`() {
        val today = LocalDate.of(2026, 11, 2)
        assertEquals("Today", WearIveRunCollectionLogic.dayForm(today, today))
        assertEquals("Nov 1", WearIveRunCollectionLogic.dayForm(LocalDate.of(2026, 11, 1), today))
    }
}

class RecentLocationsLogicTest {

    private fun r(name: String) = com.runwear.app.ui.screens.RecentLocation(name, 1.0, 2.0)

    @Test
    fun `recents are most-recent-first, deduped, capped at five`() {
        var recents = emptyList<com.runwear.app.ui.screens.RecentLocation>()
        listOf("A", "B", "C", "D", "E", "F").forEach {
            recents = com.runwear.app.ui.screens.RecentLocationsLogic.record(recents, r(it))
        }
        assertEquals(listOf("F", "E", "D", "C", "B"), recents.map { it.name })
        recents = com.runwear.app.ui.screens.RecentLocationsLogic.record(recents, r("D"))
        assertEquals(listOf("D", "F", "E", "C", "B"), recents.map { it.name })
    }

    @Test
    fun `remove and undo restore at the original index`() {
        val recents = listOf(r("A"), r("B"), r("C"))
        val removed = com.runwear.app.ui.screens.RecentLocationsLogic.remove(recents, "B")
        assertEquals(listOf("A", "C"), removed.map { it.name })
        val restored = com.runwear.app.ui.screens.RecentLocationsLogic.restore(removed, r("B"), 1)
        assertEquals(listOf("A", "B", "C"), restored.map { it.name })
    }
}

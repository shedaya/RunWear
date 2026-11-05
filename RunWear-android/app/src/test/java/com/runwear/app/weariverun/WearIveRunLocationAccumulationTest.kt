package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 ROUND 2 (Sol, Nov 4) — the FULL Wear I've Run model,
 * verified against the merged implementation:
 *
 * - the collection's TOP LEVEL collates BY LOCATION ACROSS ALL
 *   HISTORY (Sol's example: 50 sessions in New York, 10 in Miami,
 *   4 in Overland Park — locations accumulate their sessions over
 *   time; nothing caps a location at one entry, no date window);
 * - inside a location: that place's historical scrapbook — ONE
 *   ENTRY PER SESSION, each updated in place during its own
 *   session, past sessions preserved;
 * - the pinned TODAY entry and the current session's chapter entry
 *   are THE SAME entry, never duplicates (the chapter list the
 *   screen builds excludes the pinned entry BY IDENTITY and the
 *   place's chapter still shows its other entries).
 */
class WearIveRunLocationAccumulationTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)

    private fun entry(
        place: String,
        sessionId: String,
        date: LocalDate,
        settledAt: Long,
        items: List<String> = listOf("Tights", "Long Sleeve")
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = 48.0,
        conditionLabel = "Overcast", isSnowing = false, isRaining = false,
        items = items, heroRef = null,
        sessionId = sessionId, settledAtMillis = settledAt
    )

    /** Sol's shape, scaled down: many sessions per location over
     * many days (one day holding TWO New York sessions), plus one
     * entry from a year ago — history has no window. */
    private fun history(): List<WearIveRunDayEntry> {
        var entries = emptyList<WearIveRunDayEntry>()
        fun record(place: String, session: String, date: LocalDate, settledAt: Long) {
            entries = WearIveRunCollectionLogic.upsert(
                entries, entry(place, session, date, settledAt)
            )
        }
        // New York — 5 sessions across 4 days (two on Nov 2).
        record("New York", "ny-1", LocalDate.of(2026, 10, 30), 10L)
        record("New York", "ny-2", LocalDate.of(2026, 11, 1), 20L)
        record("New York", "ny-3", LocalDate.of(2026, 11, 2), 30L)
        record("New York", "ny-4", LocalDate.of(2026, 11, 2), 40L)
        record("New York", "ny-5", today, 50L)
        // Miami — 3 sessions across 2 days.
        record("Miami", "mi-1", LocalDate.of(2026, 11, 3), 60L)
        record("Miami", "mi-2", LocalDate.of(2026, 11, 3), 70L)
        record("Miami", "mi-3", LocalDate.of(2025, 12, 25), 5L)
        // Overland Park — 1 session.
        record("Overland Park", "op-1", LocalDate.of(2026, 11, 1), 15L)
        return entries
    }

    @Test
    fun `sessions accumulate per location across all history - nothing is capped or windowed`() {
        val entries = history()
        // Every session survives: 5 + 3 + 1 distinct session entries.
        assertEquals(9, entries.size)
        assertEquals(9, entries.map { it.sessionId }.distinct().size)

        val chapters = WearIveRunCollectionLogic.chapters(entries)
        // Top level = locations only, ordered by most recent day.
        assertEquals(
            listOf("New York", "Miami", "Overland Park"),
            chapters.map { it.place }
        )
        val ny = chapters.first { it.place == "New York" }
        val mi = chapters.first { it.place == "Miami" }
        val op = chapters.first { it.place == "Overland Park" }
        // Inside a location, ALL of its sessions — including both
        // Nov 2 New York sessions and the year-old Miami session.
        // GEN-5 (RW-29 FA-13/FA-23/FA-38): chapter days are STAYS —
        // same-day sessions at one place merge into one stay holding
        // every entry (the Miami duplicate-key crash fix), and the
        // day counts count DISTINCT dates. New York: 5 entries over
        // 4 distinct days -> 4 stays; the Nov 2 stay holds both
        // sessions. Miami: 3 entries over 2 distinct days -> 2 stays.
        assertEquals(4, ny.stays.size)
        assertEquals(4, ny.totalDaysUnfiltered)
        assertEquals(
            2,
            ny.stays.single { it.start == LocalDate.of(2026, 11, 2) }.days.size
        )
        assertEquals(5, ny.days.size)
        assertEquals(2, mi.stays.size)
        assertEquals(
            2,
            mi.stays.single { it.start == LocalDate.of(2026, 11, 3) }.days.size
        )
        assertEquals(3, mi.days.size)
        assertTrue(mi.days.any { it.date == LocalDate.of(2025, 12, 25) })
        assertEquals(1, op.days.size)
    }

    @Test
    fun `a session updating in place never adds a second entry to its location`() {
        var entries = history()
        // ny-3 re-settles mid-session with a changed kit.
        entries = WearIveRunCollectionLogic.upsert(
            entries,
            entry("New York", "ny-3", LocalDate.of(2026, 11, 2), 80L,
                items = listOf("Shorts", "Singlet"))
        )
        assertEquals(9, entries.size)
        val ny = WearIveRunCollectionLogic.chapters(entries)
            .first { it.place == "New York" }
        assertEquals(5, ny.days.size)
        val updated = ny.days.first { it.sessionId == "ny-3" }
        assertEquals(listOf("Shorts", "Singlet"), updated.items)
    }

    @Test
    fun `the pinned TODAY entry is the same entry its chapter holds - never a duplicate`() {
        val entries = history()
        val pinned = WearIveRunCollectionLogic.todaysEntry(entries, today)
        // Today's latest settled session is ny-5 (New York, settled 50;
        // Miami's Nov 3 sessions settled later but are not today).
        assertEquals("ny-5", pinned?.sessionId)

        // It is literally an entry of the New York chapter.
        val nyChapter = WearIveRunCollectionLogic.chapters(entries)
            .first { it.place == "New York" }
        assertTrue(nyChapter.days.any {
            WearIveRunCollectionLogic.sameIdentity(it, pinned!!)
        })

        // The screen's chapter list excludes the pinned entry BY
        // IDENTITY (CollectionScreen's own computation): the New
        // York chapter still shows — with its other 4 sessions.
        val rest = entries.filterNot {
            WearIveRunCollectionLogic.sameIdentity(it, pinned!!)
        }
        val listChapters = WearIveRunCollectionLogic.chapters(rest)
        val nyListed = listChapters.first { it.place == "New York" }
        assertEquals(4, nyListed.days.size)
        assertTrue(nyListed.days.none { it.sessionId == "ny-5" })
        // And the store still holds exactly ONE ny-5 entry overall.
        assertEquals(1, entries.count { it.sessionId == "ny-5" })
        assertSame(
            pinned,
            entries.first { it.sessionId == "ny-5" }
        )
    }
}

package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 (WS-C) — the session rule, pinned (Sol's Nov 4 correction,
 * superseding once-per-day / per-place):
 *
 * - one app session produces exactly ONE entry; within the session
 *   the entry updates IN PLACE through every location and weather
 *   change — updates replace, never accumulate — and the scrapbook
 *   keeps the session's last settled version;
 * - after the session's first "Added …", subsequent changes within
 *   the same session narrate as updates (census X-4: the recording
 *   showed three "Added …" and zero "Update to…");
 * - the pinned TODAY card and the session's chapter are THE SAME
 *   entry (census X-5), and the collection does not accumulate one
 *   chapter per lookup;
 * - a new session mints a new entry;
 * - the mini bar follows the latest commit (it never announces a
 *   place the home screen has already moved past);
 * - legacy (pre-session-rule) rows keep their history and their
 *   (date, place) identity; a session entry supersedes only
 *   same-day legacy rows.
 *
 * Session definition (WearIveRunSession.OPERATIONAL_DEFINITION):
 * one continuous period of app use from a fresh-task MainActivity
 * creation (including every cold process start) until the task is
 * finished or the process dies; the id lives in process memory only.
 */
class WearIveRunSessionRuleTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)

    private fun entry(
        place: String = "Overland Park, KS",
        items: List<String> = listOf("Light Long Sleeve", "Light Tights"),
        feels: Double = 48.0,
        sessionId: String = "session-1",
        settledAt: Long = 0L,
        date: LocalDate = today,
        heroRef: String? = null
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = feels,
        conditionLabel = "Overcast", isSnowing = false, isRaining = false,
        items = items, heroRef = heroRef,
        sessionId = sessionId, settledAtMillis = settledAt
    )

    // ---- Store / collection logic: one entry per session ----

    @Test
    fun `multiple place and weather changes in one session produce exactly one entry - the last settled version`() {
        var entries = emptyList<WearIveRunDayEntry>()
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(place = "Overland Park, KS", feels = 48.0, settledAt = 1L)
        )
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(place = "New York, NY", feels = 72.0, settledAt = 2L)
        )
        entries = WearIveRunCollectionLogic.upsert(
            entries,
            entry(
                place = "Miami, FL", feels = 90.0, settledAt = 3L,
                items = listOf("Short Sleeve Shirt", "Running Shorts (5-7\")"),
                heroRef = "hero://miami"
            )
        )
        assertEquals(1, entries.size)
        val only = entries.single()
        assertEquals("Miami, FL", only.place)
        assertEquals(90.0, only.feelsLikeF, 0.001)
        assertEquals(listOf("Short Sleeve Shirt", "Running Shorts (5-7\")"), only.items)
        assertEquals("hero://miami", only.heroRef)
        assertEquals(3L, only.settledAtMillis)
        // And the collection holds ONE chapter for the session —
        // not one chapter per lookup (X-5).
        val chapters = WearIveRunCollectionLogic.chapters(entries)
        assertEquals(1, chapters.size)
        assertEquals("Miami, FL", chapters.single().place)
        assertEquals(1, chapters.single().dayCount)
    }

    @Test
    fun `a session spanning midnight still holds exactly one entry`() {
        var entries = emptyList<WearIveRunDayEntry>()
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(date = today, settledAt = 1L)
        )
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(date = today.plusDays(1), settledAt = 2L)
        )
        assertEquals(1, entries.size)
        assertEquals(today.plusDays(1), entries.single().date)
    }

    @Test
    fun `a new session mints a new entry even on the same day`() {
        var entries = emptyList<WearIveRunDayEntry>()
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(sessionId = "session-1", settledAt = 1L)
        )
        entries = WearIveRunCollectionLogic.upsert(
            entries,
            entry(place = "Miami, FL", sessionId = "session-2", settledAt = 2L)
        )
        assertEquals(2, entries.size)
        assertEquals(
            setOf("session-1", "session-2"),
            entries.map { it.sessionId }.toSet()
        )
    }

    @Test
    fun `a session entry supersedes same-day legacy rows but never wipes other days`() {
        val legacyTodayA = entry(
            place = "Overland Park, KS", sessionId = "", settledAt = 0L
        )
        val legacyTodayB = entry(
            place = "New York, NY", sessionId = "", settledAt = 0L
        )
        val legacyYesterday = entry(
            place = "Aspen, CO", sessionId = "", settledAt = 0L,
            date = today.minusDays(1)
        )
        var entries = listOf(legacyTodayA, legacyTodayB, legacyYesterday)
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(place = "Miami, FL", settledAt = 5L)
        )
        // Today's two legacy chapters collapse into the session
        // entry; yesterday's legacy history is untouched.
        assertEquals(2, entries.size)
        assertTrue(entries.contains(legacyYesterday))
        assertEquals(
            "Miami, FL",
            WearIveRunCollectionLogic.todaysEntry(entries, today)?.place
        )
    }

    @Test
    fun `legacy entries keep per-day-per-place keying and round-trip through the store`() {
        var entries = emptyList<WearIveRunDayEntry>()
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(place = "Aspen, CO", sessionId = "", settledAt = 0L)
        )
        entries = WearIveRunCollectionLogic.upsert(
            entries, entry(place = "Denver, CO", sessionId = "", settledAt = 0L)
        )
        assertEquals(2, entries.size)
        val decoded = WearIveRunStore.decode(WearIveRunStore.encode(entries))
        assertEquals(entries.toSet(), decoded.toSet())
        // A payload written before GEN-3 (no session fields at all)
        // decodes as legacy.
        val oldJson = """[{"date":"2026-11-04","place":"Aspen, CO",""" +
            """"feelsLikeF":41.0,"condition":"Cloudy","snowing":false,""" +
            """"raining":false,"items":["Tights"]}]"""
        val legacy = WearIveRunStore.decode(oldJson).single()
        assertEquals("", legacy.sessionId)
        assertEquals(0L, legacy.settledAtMillis)
    }

    @Test
    fun `todays entry is the latest settled - never an alphabetical place pick`() {
        // The X-5 stale pin picked Overland Park (alphabetically
        // last / first by sort order) while he stood in Miami.
        val entries = listOf(
            entry(place = "Overland Park, KS", sessionId = "s-old", settledAt = 3L),
            entry(place = "Miami, FL", sessionId = "s-new", settledAt = 9L),
            entry(place = "New York, NY", sessionId = "s-mid", settledAt = 6L)
        )
        assertEquals(
            "Miami, FL",
            WearIveRunCollectionLogic.todaysEntry(entries, today)?.place
        )
    }

    // ---- Holder: narration, identity, mini-bar truthfulness ----

    private fun present(holder: WearIveRunBarStateHolder) =
        holder.state.value as WearIveRunBarState.Present

    @Test
    fun `the walkthrough sequence - one Added then only updates, and the bar follows the latest commit`() {
        val holder = WearIveRunBarStateHolder()
        val narrations = mutableListOf<String>()

        holder.onEntriesChanged(
            listOf(entry(place = "Overland Park, KS", settledAt = 1L)), today
        )
        narrations += present(holder).narration.text
        assertEquals("Added Overland Park, KS, Now", narrations.last())
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)

        // Place change, kit unchanged: an UPDATE (X-4), and the
        // presented entry moves with the commit.
        holder.onEntriesChanged(
            listOf(
                entry(place = "New York, NY", feels = 72.0, settledAt = 2L)
            ),
            today
        )
        narrations += present(holder).narration.text
        assertEquals("Update to New York, NY, Now", narrations.last())
        assertEquals("New York, NY", present(holder).entry.place)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)

        // Place + kit change: under C-7 the narration names the
        // PLACE that changed (gen-2's garment payload is the
        // defect); the bar's entry is Miami, the latest commit —
        // never a place home has moved past.
        holder.onEntriesChanged(
            listOf(
                entry(
                    place = "Miami, FL", feels = 90.0, settledAt = 3L,
                    items = listOf("Light Long Sleeve", "Running Shorts (5-7\")")
                )
            ),
            today
        )
        narrations += present(holder).narration.text
        assertEquals("Update to Miami, FL, Now", narrations.last())
        assertEquals("Miami, FL", present(holder).entry.place)
        assertEquals(90.0, present(holder).entry.feelsLikeF!!, 0.001)

        assertEquals(1, narrations.count { it.startsWith("Added ") })
        assertEquals(3, holder.cueSequence.value)
    }

    @Test
    fun `pinned card identity equals the session chapter identity - no duplication`() {
        val holder = WearIveRunBarStateHolder()
        var entries = emptyList<WearIveRunDayEntry>()
        listOf(
            entry(place = "Overland Park, KS", settledAt = 1L),
            entry(place = "New York, NY", feels = 72.0, settledAt = 2L),
            entry(place = "Miami, FL", feels = 90.0, settledAt = 3L)
        ).forEach { settled ->
            entries = WearIveRunCollectionLogic.upsert(entries, settled)
            holder.onEntriesChanged(entries, today)
        }
        val pinned = WearIveRunCollectionLogic.todaysEntry(entries, today)
        // The holder's presented entry and the collection's pinned
        // TODAY entry are the same entry (same identity, same
        // content) — the pinned card is the session's chapter.
        assertEquals(pinned, holder.currentEntry())
        assertEquals(
            WearIveRunCollectionLogic.identityOf(pinned!!),
            WearIveRunCollectionLogic.identityOf(holder.currentEntry()!!)
        )
        // The chapter list beneath the pin excludes that entry, so
        // it cannot appear twice in the collection.
        val beneath = entries.filterNot {
            WearIveRunCollectionLogic.sameIdentity(it, pinned)
        }
        assertTrue(beneath.none { it.date == today })
    }

    @Test
    fun `a new session presents as a fresh Added in a new holder`() {
        val first = WearIveRunBarStateHolder()
        first.onEntriesChanged(
            listOf(entry(sessionId = "session-1", settledAt = 1L)), today
        )
        assertEquals("Added Overland Park, KS, Now", present(first).narration.text)

        // Process death -> new session -> new holder. The store now
        // holds both sessions' entries; the new holder presents the
        // latest settled (the new session's) as its own creation.
        val newHolder = WearIveRunBarStateHolder()
        newHolder.onEntriesChanged(
            listOf(
                entry(sessionId = "session-1", settledAt = 1L),
                entry(place = "Miami, FL", sessionId = "session-2", settledAt = 2L)
            ),
            today
        )
        assertEquals("Added Miami, FL, Now", present(newHolder).narration.text)
        assertEquals("session-2", newHolder.currentEntry()?.sessionId)
    }

    @Test
    fun `trash removes the session entry by identity even after its place moved`() {
        var entries = listOf(
            entry(place = "Overland Park, KS", settledAt = 1L),
            entry(place = "Miami, FL", sessionId = "session-2", settledAt = 2L)
        )
        entries = WearIveRunCollectionLogic.removeByIdentity(
            entries, entry(place = "Anywhere, XX", sessionId = "session-1")
        )
        assertEquals(1, entries.size)
        assertEquals("session-2", entries.single().sessionId)
    }

    @Test
    fun `the session definition is the gate-report wording`() {
        assertTrue(
            WearIveRunSession.OPERATIONAL_DEFINITION.contains("fresh task")
        )
        assertTrue(
            WearIveRunSession.OPERATIONAL_DEFINITION.contains("process dies")
        )
        // Process-memory only: two reads in one process agree.
        assertEquals(WearIveRunSession.currentId(), WearIveRunSession.currentId())
    }
}

package com.runwear.app.weariverun

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 (DA-09 / RW-42): narration discipline — announcements
 * fire ONCE, for live state changes only, and the bar's announced
 * state and its card content are the same solved state.
 *
 * The sequence below scripts Sol's gen-5 recording: a session
 * adds Iredell County, updates to a confirmed 7 p.m. moment,
 * updates to Miami — then the app RELAUNCHES and the same
 * persisted collection restores. The gen-5 build fired a second
 * "Added Miami, FL, Now" on that relaunch (the host's prime was
 * consumed by the collectAsState initial empty list, so the
 * store's real first emission landed as a "first presentation").
 * The holder seam pinned here: prime = rest, live = announce,
 * re-delivery = silence.
 */
class WearIveRunNarrationDisciplineTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    private fun entry(
        place: String,
        feels: Double,
        items: List<String>,
        settledAt: Long,
        momentLabel: String? = null
    ) = WearIveRunDayEntry(
        date = today, place = place, feelsLikeF = feels,
        conditionLabel = "Clear", isSnowing = false, isRaining = false,
        items = items, heroRef = null,
        sessionId = "session-1", settledAtMillis = settledAt,
        momentLabel = momentLabel
    )

    private fun present(holder: WearIveRunBarStateHolder) =
        holder.state.value as WearIveRunBarState.Present

    @Test
    fun `recorded scenario - live announcements fire once each, relaunch restores in silence`() {
        val iredell = entry(
            "Iredell County, NC", 55.0,
            listOf("Long Sleeve", "Light Tights"), settledAt = 1_000L
        )
        val iredellAt7pm = entry(
            "Iredell County, NC", 49.0,
            listOf("Long Sleeve", "Light Tights", "Headlamp"),
            settledAt = 2_000L, momentLabel = "7 p.m."
        )
        val miami = entry(
            "Miami, FL", 82.0,
            listOf("Tee", "Shorts"), settledAt = 3_000L
        )

        // ── The live session (fresh install: prime on empty). ──
        val holder = WearIveRunBarStateHolder()
        holder.primeRestore(emptyList(), today)
        assertEquals(WearIveRunBarState.Absent, holder.state.value)
        assertEquals(0, holder.cueSequence.value)

        holder.onEntriesChanged(listOf(iredell), today)
        assertEquals(1, holder.cueSequence.value)
        assertEquals("Updated to Iredell County, NC", present(holder).narration.text)

        holder.onEntriesChanged(listOf(iredellAt7pm), today)
        assertEquals(2, holder.cueSequence.value)
        assertEquals("Updated to Iredell County, NC", present(holder).narration.text)

        holder.onEntriesChanged(listOf(miami), today)
        assertEquals(3, holder.cueSequence.value)
        assertEquals("Updated to Miami, FL", present(holder).narration.text)

        // Identical re-delivery (a re-subscription emission of the
        // same state) fires nothing.
        holder.onEntriesChanged(listOf(miami), today)
        assertEquals(3, holder.cueSequence.value)
        assertEquals("Updated to Miami, FL", present(holder).narration.text)

        // ── RELAUNCH: a fresh holder restores the same persisted
        // collection. No cue, no new announcement — the element
        // presents at rest. ──
        val relaunched = WearIveRunBarStateHolder()
        relaunched.primeRestore(listOf(miami), today)
        assertEquals(0, relaunched.cueSequence.value)
        assertEquals(WearIveRunElementKind.MINI, present(relaunched).kind)
        assertEquals("Miami, FL", relaunched.currentEntry()?.place)

        // A restore-shaped re-delivery after the prime is silent too.
        relaunched.onEntriesChanged(listOf(miami), today)
        assertEquals(0, relaunched.cueSequence.value)

        // ── A genuine live change after relaunch announces once. ──
        val miamiCooler = entry(
            "Miami, FL", 74.0,
            listOf("Tee", "Shorts", "Light Shell"), settledAt = 4_000L
        )
        relaunched.onEntriesChanged(listOf(miamiCooler), today)
        assertEquals(1, relaunched.cueSequence.value)
        assertTrue(present(relaunched).narration.text.startsWith("Updated to"))
    }

    @Test
    fun `atomicity - the announced update and the card entry are the same solved state`() {
        val outgoing = entry(
            "Iredell County, NC", 85.0,
            listOf("Tee", "Shorts", "Sunglasses"), settledAt = 1_000L
        )
        val incoming = entry(
            "Miami, FL", 82.0,
            listOf("Tee", "Shorts"), settledAt = 2_000L
        )
        val holder = WearIveRunBarStateHolder()
        holder.primeRestore(emptyList(), today)
        holder.onEntriesChanged(listOf(outgoing), today)
        holder.onEntriesChanged(listOf(incoming), today)

        val state = present(holder)
        // The bar names Miami…
        assertEquals("Updated to Miami, FL", state.narration.text)
        // …and the card content in the SAME emitted state is the
        // Miami solve the narration names — never the outgoing
        // entry's 85° / 3-item solve wearing the new name.
        assertEquals("Miami, FL", state.entry.place)
        assertTrue(state.entry.feelsLikeF == 82.0)
        assertEquals(2, state.entry.itemCount)
        assertEquals(listOf("Tee", "Shorts"), state.entry.itemNames)
    }

    @Test
    fun `record policy - a selection records only its own solve`() {
        val selected = LocalDateTime.of(2026, 11, 5, 19, 0)
        // The fetch for 7 p.m. has not committed: the weather in
        // state is still the 6 p.m. solve — recording is refused.
        assertFalse(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = kotlinx.datetime.LocalDateTime(2026, 11, 5, 18, 0),
                selectedMoment = selected
            )
        )
        // The committed solve for the selected hour — records.
        assertTrue(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = kotlinx.datetime.LocalDateTime(2026, 11, 5, 19, 0),
                selectedMoment = selected
            )
        )
        // A solve from another DATE never records under this label.
        assertFalse(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = kotlinx.datetime.LocalDateTime(2026, 11, 6, 19, 0),
                selectedMoment = selected
            )
        )
        // No weather yet — nothing to record.
        assertFalse(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = null,
                selectedMoment = selected
            )
        )
        // At Now, the current conditions are the solve by definition.
        assertTrue(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = true,
                weatherMoment = kotlinx.datetime.LocalDateTime(2026, 11, 5, 18, 0),
                selectedMoment = selected
            )
        )
    }
}

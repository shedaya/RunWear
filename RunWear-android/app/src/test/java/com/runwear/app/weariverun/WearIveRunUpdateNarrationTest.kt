package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-5 (RW-28, ruling C-7 — the ORIGINAL Nov 2 concept governs):
 * the update narration names WHATEVER CHANGED — the place, or the
 * moment's simple reading (a clock reading such as "9 p.m." is
 * in-spec) — NEVER a garment. This file replaces the gen-2 W-11
 * suite, whose garment payloads ("Update to Running Shorts") are
 * the defect the gen-4 walkthrough recorded (B-1/B-10).
 */
class WearIveRunUpdateNarrationTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 5)

    private fun entry(
        items: List<String>,
        place: String = "Kansas City, MO",
        feels: Double = 44.0,
        heroRef: String? = null,
        momentLabel: String? = null
    ) = WearIveRunDayEntry(
        date = today, place = place, feelsLikeF = feels,
        conditionLabel = "Overcast", isSnowing = false, isRaining = false,
        items = items, heroRef = heroRef, momentLabel = momentLabel
    )

    @Test
    fun `a kit substitution at the same place and moment names the place - never the garment`() {
        // The walkthrough case, verbatim in structure: the kit
        // changed Light Tights -> Running Shorts at Kansas City and
        // the gen-4 bar read "Update to Light Long Sleeve". Under
        // C-7 the payload is the place reading — no garment name
        // can appear.
        val previous = entry(listOf("Light Long Sleeve", "Light Tights"))
        val next = entry(listOf("Light Long Sleeve", "Running Shorts (5-7\")"))
        assertEquals(
            "Kansas City, MO",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
        assertEquals(
            "Update to Kansas City, MO, Now",
            WearIveRunUpdateNarration.narration(previous, next)?.text
        )
    }

    @Test
    fun `a place change names the new place`() {
        val previous = entry(listOf("Light Long Sleeve"), place = "Kansas City, MO")
        val next = entry(listOf("Tank Top"), place = "Miami, FL")
        assertEquals(
            "Miami, FL",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
        assertEquals(
            "Update to Miami, FL, Now",
            WearIveRunUpdateNarration.narration(previous, next)?.text
        )
    }

    @Test
    fun `a confirmed hour change names the clock reading`() {
        // Census N-22 / C-7 nuance: "Update to 9 p.m." is in-spec.
        val previous = entry(listOf("Light Long Sleeve"), momentLabel = null)
        val next = entry(listOf("Tank Top"), momentLabel = "9 p.m.")
        assertEquals(
            "9 p.m.",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
        assertEquals(
            "Update to 9 p.m.",
            WearIveRunUpdateNarration.narration(previous, next)?.text
        )
    }

    @Test
    fun `returning to Now from a changed moment names Now`() {
        val previous = entry(listOf("Tank Top"), momentLabel = "9 p.m.")
        val next = entry(listOf("Light Long Sleeve"), momentLabel = null)
        assertEquals(
            "Now",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
        assertEquals(
            "Update to Now",
            WearIveRunUpdateNarration.narration(previous, next)?.text
        )
    }

    @Test
    fun `a kit change at a changed moment names that moment`() {
        val previous = entry(listOf("Light Long Sleeve"), momentLabel = "9 p.m.")
        val next = entry(listOf("Tank Top"), momentLabel = "9 p.m.")
        assertEquals(
            "9 p.m.",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `identical kits announce nothing - weather and hero landings stay silent`() {
        val previous = entry(listOf("Light Long Sleeve", "Light Tights"))
        assertNull(
            WearIveRunUpdateNarration.changedPayload(
                previous, entry(listOf("Light Long Sleeve", "Light Tights"), feels = 41.0)
            )
        )
        assertNull(
            WearIveRunUpdateNarration.narration(
                previous,
                entry(listOf("Light Long Sleeve", "Light Tights"), heroRef = "hero://x")
            )
        )
    }

    @Test
    fun `an emptied kit falls back to the place reading`() {
        val previous = entry(listOf("Light Tights"))
        val next = entry(emptyList())
        assertEquals(
            "Kansas City, MO",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `the holder narrates the dimension diff and cues exactly once`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(
            listOf(entry(listOf("Light Long Sleeve", "Light Tights"))), today
        )
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEntriesChanged(
            listOf(entry(listOf("Light Long Sleeve", "Running Shorts (5-7\")"))), today
        )
        val present = holder.state.value as WearIveRunBarState.Present
        assertEquals(WearIveRunElementKind.CUE, present.kind)
        assertEquals("Update to Kansas City, MO, Now", present.narration.text)
        assertEquals(2, holder.cueSequence.value)
        // A weather-only re-delivery afterwards announces nothing new.
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEntriesChanged(
            listOf(
                entry(
                    listOf("Light Long Sleeve", "Running Shorts (5-7\")"),
                    feels = 43.0
                )
            ),
            today
        )
        val after = holder.state.value as WearIveRunBarState.Present
        assertEquals(WearIveRunElementKind.MINI, after.kind)
        assertEquals("Update to Kansas City, MO, Now", after.narration.text)
        assertEquals(2, holder.cueSequence.value)
    }

    @Test
    fun `the holder cues on a confirmed hour change and names the reading`() {
        val holder = WearIveRunBarStateHolder()
        holder.onEntriesChanged(listOf(entry(listOf("Light Long Sleeve"))), today)
        holder.onEvent(WearIveRunElementEvent.CUE_TIMEOUT)
        holder.onEntriesChanged(
            listOf(entry(listOf("Tank Top"), momentLabel = "9 p.m.")), today
        )
        val present = holder.state.value as WearIveRunBarState.Present
        assertEquals(WearIveRunElementKind.CUE, present.kind)
        assertEquals("Update to 9 p.m.", present.narration.text)
        assertEquals(2, holder.cueSequence.value)
    }

    @Test
    fun `gen-5 - a restored collection primes at rest and never cues on a plain open`() {
        // B-1: the stored collection arriving in a fresh process is
        // a RESTORE — the bar presents at MINI with its rest line
        // and the cue sequence does not move.
        val holder = WearIveRunBarStateHolder()
        holder.primeRestore(listOf(entry(listOf("Light Long Sleeve"))), today)
        val present = holder.state.value as WearIveRunBarState.Present
        assertEquals(WearIveRunElementKind.MINI, present.kind)
        assertEquals("Added Kansas City, MO, Now", present.narration.text)
        assertEquals(0, holder.cueSequence.value)
    }

    @Test
    fun `wear moment label formats clock readings`() {
        assertEquals("12 a.m.", WearMomentLabel.ofHour(0))
        assertEquals("6 a.m.", WearMomentLabel.ofHour(6))
        assertEquals("12 p.m.", WearMomentLabel.ofHour(12))
        assertEquals("9 p.m.", WearMomentLabel.ofHour(21))
        assertEquals("11 p.m.", WearMomentLabel.ofHour(23))
    }
}

package com.runwear.app.weariverun

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-2 (W-11): the update narration is computed from the DIFF
 * between the previous settled entry and the new one — it names the
 * item that ACTUALLY changed, never an unchanged item.
 *
 * The walkthrough case, verbatim in structure: at 44° Kansas City
 * the kit was [Light Long Sleeve, Light Tights]; Sol changed Body
 * Temperature and the kit became [Light Long Sleeve, Running
 * Shorts] — and the bar read "Update to Light Long Sleeve", the one
 * item that did NOT change. The faithful line is
 * "Update to Running Shorts".
 */
class WearIveRunUpdateNarrationTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)

    private fun entry(
        items: List<String>,
        feels: Double = 44.0,
        heroRef: String? = null
    ) = WearIveRunDayEntry(
        date = today, place = "Kansas City, MO", feelsLikeF = feels,
        conditionLabel = "Overcast", isSnowing = false, isRaining = false,
        items = items, heroRef = heroRef
    )

    @Test
    fun `the walkthrough case - a substitution names the item that arrived`() {
        val previous = entry(listOf("Light Long Sleeve", "Light Tights"))
        val next = entry(listOf("Light Long Sleeve", "Running Shorts (5-7\")"))
        assertEquals(
            "Running Shorts (5-7\")",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
        assertEquals(
            "Update to Running Shorts (5-7\")",
            WearIveRunUpdateNarration.narration(previous, next)?.text
        )
    }

    @Test
    fun `a new lead item names itself, not the item it displaced in position`() {
        val previous = entry(listOf("Light Tights", "Wind Shell"))
        val next = entry(listOf("Thermal Tights", "Wind Shell"))
        assertEquals(
            "Thermal Tights",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `an added item names the addition`() {
        val previous = entry(listOf("Short Sleeve Shirt"))
        val next = entry(listOf("Short Sleeve Shirt", "Reflective Gear"))
        assertEquals(
            "Reflective Gear",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `several additions name the first added in new-kit order`() {
        val previous = entry(listOf("Light Long Sleeve"))
        val next = entry(listOf("Light Long Sleeve", "Headband", "Gloves"))
        assertEquals(
            "Headband",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `a pure removal names the new kit's lead item`() {
        val previous = entry(listOf("Light Long Sleeve", "Light Tights", "Gloves"))
        val next = entry(listOf("Light Long Sleeve", "Light Tights"))
        assertEquals(
            "Light Long Sleeve",
            WearIveRunUpdateNarration.changedPayload(previous, next)
        )
    }

    @Test
    fun `a pure reorder names the first item that moved`() {
        val previous = entry(listOf("Wind Shell", "Light Tights"))
        val next = entry(listOf("Light Tights", "Wind Shell"))
        assertEquals(
            "Light Tights",
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
    fun `an emptied kit has no diff payload - the holder's place fallback applies`() {
        val previous = entry(listOf("Light Tights"))
        val next = entry(emptyList())
        assertNull(WearIveRunUpdateNarration.changedPayload(previous, next))
    }

    @Test
    fun `the holder narrates the walkthrough diff and cues exactly once`() {
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
        assertEquals("Update to Running Shorts (5-7\")", present.narration.text)
        assertEquals("Running Shorts (5-7\")", present.narration.payload)
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
        assertEquals("Update to Running Shorts (5-7\")", after.narration.text)
        assertEquals(2, holder.cueSequence.value)
    }
}

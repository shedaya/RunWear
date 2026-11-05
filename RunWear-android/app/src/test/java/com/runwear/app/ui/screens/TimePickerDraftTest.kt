package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-4 Choose-a-Time draft semantics: scrub/step/tap move the DRAFT
 * only; Confirm commits; Cancel restores; the NOW-kept interpretation
 * is pinned exactly as implemented.
 */
class TimePickerDraftTest {

    private val allHours = (0..23).toSet()

    @Test
    fun `default draft is the currently selected hour`() {
        val s = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = false, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        assertEquals(9, s.draftHour)
        assertEquals(9, s.originalHour)
    }

    @Test
    fun `default draft in NOW state on today starts on the NOW hour`() {
        val s = TimePickerDraftPolicy.open(
            selectedHour = 16, isNowSelected = true, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        assertEquals(16, s.draftHour)
    }

    @Test
    fun `NOW state on a future day does NOT start on the now hour`() {
        val s = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = true, isToday = false,
            nowHour = null, selectableHours = allHours
        )
        assertEquals(9, s.draftHour)
    }

    @Test
    fun `scrub step and tap move the draft only - the original is untouched`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = false, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val scrubbed = TimePickerDraftPolicy.moveTo(opened, 14) // scrub / tap target
        assertEquals(14, scrubbed.draftHour)
        assertEquals(9, scrubbed.originalHour)

        val stepped = TimePickerDraftPolicy.step(scrubbed, +1)
        assertEquals(15, stepped.draftHour)
        assertEquals(9, stepped.originalHour)

        val steppedBack = TimePickerDraftPolicy.step(stepped, -1)
        assertEquals(14, steppedBack.draftHour)
    }

    @Test
    fun `confirm commits the drafted hour as an explicit pick`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = false, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val confirm = TimePickerDraftPolicy.confirm(TimePickerDraftPolicy.moveTo(opened, 18))
        assertEquals(18, confirm.hour)
        assertFalse(confirm.keepNow)
    }

    @Test
    fun `cancel restores the pre-open selection exactly`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = false, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val moved = TimePickerDraftPolicy.moveTo(opened, 20)
        assertEquals(9, TimePickerDraftPolicy.cancel(moved))
    }

    @Test
    fun `NOW-kept - confirming the untouched NOW hour keeps NOW`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 16, isNowSelected = true, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val confirm = TimePickerDraftPolicy.confirm(opened)
        assertEquals(16, confirm.hour)
        assertTrue(confirm.keepNow)
    }

    @Test
    fun `NOW-kept fails after moving away and back to the NOW hour`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 16, isNowSelected = true, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val away = TimePickerDraftPolicy.moveTo(opened, 18)
        val back = TimePickerDraftPolicy.moveTo(away, 16)
        val confirm = TimePickerDraftPolicy.confirm(back)
        assertEquals(16, confirm.hour)
        assertFalse("moved off NOW and back is an explicit pick", confirm.keepNow)
    }

    @Test
    fun `NOW-kept fails when a different hour is confirmed from NOW`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 16, isNowSelected = true, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        val confirm = TimePickerDraftPolicy.confirm(TimePickerDraftPolicy.moveTo(opened, 10))
        assertFalse(confirm.keepNow)
    }

    @Test
    fun `hours past the horizon are not selectable - moves snap to the last forecast hour`() {
        val partial = (0..14).toSet()
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 9, isNowSelected = false, isToday = true,
            nowHour = 10, selectableHours = partial
        )
        assertEquals(14, TimePickerDraftPolicy.moveTo(opened, 23).draftHour)
        assertFalse(TimePickerDraftPolicy.canStep(
            TimePickerDraftPolicy.moveTo(opened, 14), +1
        ))
        assertTrue(TimePickerDraftPolicy.canStep(opened, +1))
    }

    @Test
    fun `steppers stop at the day edges`() {
        val opened = TimePickerDraftPolicy.open(
            selectedHour = 0, isNowSelected = false, isToday = true,
            nowHour = 16, selectableHours = allHours
        )
        assertFalse(TimePickerDraftPolicy.canStep(opened, -1))
        assertEquals(0, TimePickerDraftPolicy.step(opened, -1).draftHour)
        val last = TimePickerDraftPolicy.moveTo(opened, 23)
        assertFalse(TimePickerDraftPolicy.canStep(last, +1))
    }
}

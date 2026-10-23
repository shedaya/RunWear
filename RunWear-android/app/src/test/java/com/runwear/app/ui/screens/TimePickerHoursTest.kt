package com.runwear.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker promises the full day in two tiers (option B): the peak grid
 * stays the fast path, the night tier covers the rest. These tests pin the
 * contract — every hour 0–23 selectable exactly once, tiers disjoint, and
 * labels in the grid's "h a" form — because consumption downstream is
 * hour-granular and silently trusts whatever hour the sheet produces.
 */
class TimePickerHoursTest {

    @Test
    fun `peak tier is 5 AM to 9 PM contiguous`() {
        assertEquals((5..21).toList(), TimePickerHours.PEAK_HOURS)
        assertEquals(17, TimePickerHours.PEAK_HOURS.size)
    }

    @Test
    fun `night tier is 10 PM to 4 AM in display order`() {
        assertEquals(listOf(22, 23, 0, 1, 2, 3, 4), TimePickerHours.NIGHT_HOURS)
    }

    @Test
    fun `both tiers together cover all 24 hours exactly once`() {
        val combined = TimePickerHours.PEAK_HOURS + TimePickerHours.NIGHT_HOURS
        assertEquals(24, combined.size)
        assertEquals((0..23).toSet(), combined.toSet())
        assertEquals((0..23).toSet(), TimePickerHours.ALL_HOURS)
    }

    @Test
    fun `tiers are disjoint`() {
        assertTrue(TimePickerHours.PEAK_HOURS.none { it in TimePickerHours.NIGHT_HOURS })
    }

    @Test
    fun `night hour classification matches the tiers`() {
        for (hour in 0..23) {
            assertEquals(
                "hour $hour",
                hour in TimePickerHours.NIGHT_HOURS,
                TimePickerHours.isNightHour(hour)
            )
        }
        assertFalse(TimePickerHours.isNightHour(5))
        assertFalse(TimePickerHours.isNightHour(21))
        assertTrue(TimePickerHours.isNightHour(22))
        assertTrue(TimePickerHours.isNightHour(4))
    }

    @Test
    fun `labels use the grid's h a format across both tiers`() {
        assertEquals("5 AM", TimePickerHours.label(5))
        assertEquals("12 PM", TimePickerHours.label(12))
        assertEquals("9 PM", TimePickerHours.label(21))
        assertEquals("10 PM", TimePickerHours.label(22))
        assertEquals("11 PM", TimePickerHours.label(23))
        assertEquals("12 AM", TimePickerHours.label(0))
        assertEquals("1 AM", TimePickerHours.label(1))
        assertEquals("4 AM", TimePickerHours.label(4))
    }
}

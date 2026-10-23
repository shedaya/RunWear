package com.runwear.app.ui.screens

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The time picker's selectable hours (option B): the peak grid runners
 * actually use, plus a "Night & early morning" tier for the rest of the
 * day. Consumption is hour-granular end to end (the forecast match is by
 * the hour), so the union of both tiers must cover all 24 hours exactly
 * once — pinned by TimePickerHoursTest.
 */
object TimePickerHours {

    /** Primary tier: 5 AM – 9 PM, exactly as the picker has always shown. */
    val PEAK_HOURS: List<Int> = (5..21).toList()

    /** Second tier, in display order: 10 PM, 11 PM, 12 AM – 4 AM. */
    val NIGHT_HOURS: List<Int> = listOf(22, 23, 0, 1, 2, 3, 4)

    /** Every hour the picker can produce. */
    val ALL_HOURS: Set<Int> = (PEAK_HOURS + NIGHT_HOURS).toSet()

    fun isNightHour(hour: Int): Boolean = hour in NIGHT_HOURS

    private val LABEL_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h a", Locale.US)

    /** Grid cell label, e.g. "6 AM", "12 PM", "12 AM". */
    fun label(hour: Int): String = LocalTime.of(hour, 0).format(LABEL_FORMAT)
}

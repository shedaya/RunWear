package com.runwear.app.i18n

import com.runwear.shared.i18n.LocaleFormats

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * LOC-1 WP-R2 gate (DESIGN §7): the pinned US patterns were replaced
 * by platform locale formatters. In en-US every helper must render
 * EXACTLY what the shipped pinned patterns rendered — the fixtures
 * below are the shipped outputs. One non-US spot check proves the
 * formatters actually follow the locale (German names, no crash).
 *
 * Weekday facts verified against the calendar: 2026-11-09 is a
 * Monday; 2026-11-05 is a Thursday.
 */
class LocaleFormatsTest {

    private val monday = LocalDate.of(2026, 11, 9)
    private val thursday = LocalDate.of(2026, 11, 5)
    private val afternoon = LocalDateTime.of(2026, 11, 9, 14, 30)

    @Test
    fun enUsTimesMatchThePinnedPatterns() {
        assertEquals("2:30 PM", LocaleFormats.timeShort(afternoon, Locale.US))
        assertEquals("2 PM", LocaleFormats.timeHour(afternoon, Locale.US))
        assertEquals("9:05 AM", LocaleFormats.timeShort(LocalDateTime.of(2026, 11, 9, 9, 5), Locale.US))
        assertEquals("12:00 PM", LocaleFormats.timeShort(LocalDateTime.of(2026, 11, 9, 12, 0), Locale.US))
        assertEquals("12 AM", LocaleFormats.timeHour(LocalDateTime.of(2026, 11, 9, 0, 0), Locale.US))
    }

    @Test
    fun enUsDatesMatchThePinnedPatterns() {
        assertEquals("Mon, Nov 9", LocaleFormats.dateWeekdayMonthDay(monday, Locale.US))
        assertEquals("Mon, Nov 9", LocaleFormats.dateWeekdayMonthDay(afternoon, Locale.US))
        assertEquals("Nov 9", LocaleFormats.dateMonthDay(monday, Locale.US))
        assertEquals("Mon", LocaleFormats.dateWeekday(monday, Locale.US))
        assertEquals("November", LocaleFormats.monthFull(monday, Locale.US))
        assertEquals("Nov", LocaleFormats.monthShort(monday, Locale.US))
        assertEquals("November 9", LocaleFormats.dateFullMonthDay(monday, Locale.US))
        assertEquals("Monday, Nov 9", LocaleFormats.dateFullWeekdayMonthDay(monday, Locale.US))
        assertEquals("November 2026", LocaleFormats.dateMonthYear(monday, Locale.US))
        assertEquals("Thursday, November 5", LocaleFormats.dateStamped(thursday, Locale.US))
    }

    @Test
    fun nonUsLocaleActuallyLocalizes() {
        assertEquals("Mo.", LocaleFormats.dateWeekday(monday, Locale.GERMANY))
        assertEquals("November", LocaleFormats.monthFull(monday, Locale.GERMANY))
        assertNotEquals(
            LocaleFormats.dateWeekdayMonthDay(monday, Locale.US),
            LocaleFormats.dateWeekdayMonthDay(monday, Locale.GERMANY)
        )
        // German MEDIUM date is numeric on the JDK ("9.11.2026");
        // dateMonthDay drops the year and its separator cleanly.
        val de = LocaleFormats.dateMonthDay(monday, Locale.GERMANY)
        assertEquals("09.11.", de)
    }
}

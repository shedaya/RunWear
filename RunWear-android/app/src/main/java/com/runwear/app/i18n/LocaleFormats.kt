package com.runwear.app.i18n

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * LOC-1 WP-R2 (DESIGN §7) — platform locale formatters for the
 * app's user-visible dates and times, following the studio's
 * JetGuide LocaleFormats precedent (its Android actuals, read
 * 2026-11-09): dates and times are NOT copy and are never
 * translated as strings — they render through the platform's
 * locale formatters, following the device's current locale.
 *
 * Two rendering families, chosen per surface:
 *
 * - 12-HOUR-CYCLE shapes keep the shipped "h:mm a" / "h a"
 *   patterns but take the DEVICE locale instead of the pinned
 *   Locale.US — the meridiem markers, spacing and digits localize
 *   while the 12-hour cycle the design specifies stays (the
 *   JetGuide `localeTime12h` / `localeHourWord` approach,
 *   verbatim). For Locale.US the output is byte-identical to the
 *   old pinned formatters.
 * - DATE shapes compose the locale's own localized fields:
 *   weekday via TextStyle + the year-stripped MEDIUM date (the
 *   JetGuide `localeDateWithDow` / `localeShortDate` construction),
 *   month names via TextStyle. For Locale.US these render exactly
 *   "Sun, Nov 9" / "Nov 9" / "November 9" / "Sunday, Nov 9" /
 *   "November 2026" / "Thursday, November 5" — the shapes the
 *   pinned patterns produced — pinned by LocaleFormatsTest.
 *
 * Scope of this slice: the hero/pill/sheet/chart surfaces, the
 * narration + collection date forms, the share stamped form, and
 * the widget/wear time formats. Internal/diagnostic timestamps
 * (feedback bundles, debug overlays) keep their pinned patterns —
 * machine-facing records, not display copy.
 */
object LocaleFormats {

    /** "2:30 PM" (en-US) — the pill/sheet time segment shape. */
    fun timeShort(value: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        value.format(DateTimeFormatter.ofPattern("h:mm a").withLocale(locale))

    /** "2 PM" (en-US) — the chart axis / hour-row shape. */
    fun timeHour(value: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        value.format(DateTimeFormatter.ofPattern("h a").withLocale(locale))

    /** The MEDIUM date with the year removed ("Nov 9, 2026" → "Nov 9"). */
    private fun shortDate(value: LocalDate, locale: Locale): String {
        val full = value.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        )
        return full
            .replace(Regex("""\d{3,4}"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trimEnd(' ', ',', '-', '/')
            .trim()
    }

    /** "Sun, Nov 9" (en-US) — weekday + short date. */
    fun dateWeekdayMonthDay(value: LocalDate, locale: Locale = Locale.getDefault()): String {
        val dow = value.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        return "$dow, ${shortDate(value, locale)}"
    }

    fun dateWeekdayMonthDay(value: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        dateWeekdayMonthDay(value.toLocalDate(), locale)

    /** "Nov 9" (en-US) — month-day alone (date-sheet sublabels, spans). */
    fun dateMonthDay(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        shortDate(value, locale)

    /** "Sun" (en-US) — the weekday alone (date strip, wear day line). */
    fun dateWeekday(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        value.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /** "November" (en-US) — the full month name alone (span parts). */
    fun monthFull(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        value.month.getDisplayName(TextStyle.FULL, locale)

    /** "Nov" (en-US) — the short month name alone (span parts). */
    fun monthShort(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        value.month.getDisplayName(TextStyle.SHORT, locale)

    /** "November 9" (en-US) — full month + day (narration date form). */
    fun dateFullMonthDay(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${monthFull(value, locale)} ${value.dayOfMonth}"

    /** "Sunday, Nov 9" (en-US) — full weekday + short date (dock day label). */
    fun dateFullWeekdayMonthDay(value: LocalDate, locale: Locale = Locale.getDefault()): String {
        val dow = value.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return "$dow, ${shortDate(value, locale)}"
    }

    /** "November 2026" (en-US) — full month + year (collection groups). */
    fun dateMonthYear(value: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${monthFull(value, locale)} ${value.year}"

    /** "Thursday, November 5" (en-US) — the share caption's stamped form. */
    fun dateStamped(value: LocalDate, locale: Locale = Locale.getDefault()): String {
        val dow = value.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        return "$dow, ${monthFull(value, locale)} ${value.dayOfMonth}"
    }
}

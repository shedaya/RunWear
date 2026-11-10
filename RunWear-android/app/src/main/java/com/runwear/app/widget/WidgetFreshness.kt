package com.runwear.app.widget

import java.time.LocalDateTime

/**
 * Pure freshness/age reckoning for the widget (spec §B.3: data freshness is
 * stated, not implied). Everything here takes `now` explicitly so it is
 * unit-testable and identical across every render path.
 */
object WidgetFreshness {

    /** Beyond the ~60 min target cadence, data is presented as stale (age shown). */
    const val STALE_THRESHOLD_MS = 60L * 60 * 1000

    /** Last-good younger than this is rendered as-is, without paying for a reload. */
    const val FRESH_ENOUGH_MS = 10L * 60 * 1000

    /** Large brand strip switches from "Updated h:mm a" to age form past this (spec §B.3.4). */
    const val LARGE_AGE_FORM_MS = 90L * 60 * 1000

    /** A `refreshing` flag older than this is a corpse (process died mid-refresh) — ignore it. */
    const val REFRESH_FLAG_TTL_MS = 90L * 1000

    fun ageMs(savedAtEpochMs: Long, nowEpochMs: Long): Long =
        (nowEpochMs - savedAtEpochMs).coerceAtLeast(0)

    fun isStale(savedAtEpochMs: Long, nowEpochMs: Long): Boolean =
        ageMs(savedAtEpochMs, nowEpochMs) > STALE_THRESHOLD_MS

    /** Compact age for eyebrow microcopy: "2H AGO", "25M AGO", "3D AGO". */
    fun ageShortUpper(ageMs: Long): String {
        val minutes = ageMs / 60_000
        return when {
            minutes < 1 -> com.runwear.app.i18n.AppEnglish.WIDGET_JUST_NOW
            minutes < 60 -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_MIN_UPPER.format(minutes)
            minutes < 60 * 24 -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_HR_UPPER.format(minutes / 60)
            else -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_DAY_UPPER.format(minutes / (60 * 24))
        }
    }

    /** Sentence-case age for the Large brand strip: "3h ago", "25 min ago", "2d ago". */
    fun ageWords(ageMs: Long): String {
        val minutes = ageMs / 60_000
        return when {
            minutes < 1 -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_JUST_NOW_LOWER
            minutes < 60 -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_MIN_LOWER.format(minutes)
            minutes < 60 * 24 -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_HR_LOWER.format(minutes / 60)
            else -> com.runwear.app.i18n.AppEnglish.WIDGET_AGE_DAY_LOWER.format(minutes / (60 * 24))
        }
    }

    // LOC-1 WP-R2: the freshness clock renders via LocaleFormats.timeShort (device locale).

    /** Large brand-strip freshness: a clock time while recent, an age once it's old. */
    fun updatedLabel(savedAtLocal: LocalDateTime, savedAtEpochMs: Long, nowEpochMs: Long): String {
        val age = ageMs(savedAtEpochMs, nowEpochMs)
        return if (age < LARGE_AGE_FORM_MS) {
            com.runwear.app.i18n.AppEnglish.WIDGET_UPDATED.format(com.runwear.app.i18n.LocaleFormats.timeShort(savedAtLocal))
        } else {
            com.runwear.app.i18n.AppEnglish.WIDGET_UPDATED.format(ageWords(age))
        }
    }

    /** Small/Horizontal eyebrow: clean while fresh, age appended only when stale (spec §B.3.4). */
    fun forNowEyebrow(savedAtEpochMs: Long, nowEpochMs: Long): String {
        return if (isStale(savedAtEpochMs, nowEpochMs)) {
            com.runwear.app.i18n.AppEnglish.WIDGET_FOR_NOW_AGE.format(ageShortUpper(ageMs(savedAtEpochMs, nowEpochMs)))
        } else {
            com.runwear.app.i18n.AppEnglish.WIDGET_FOR_NOW
        }
    }

    /**
     * Small eyebrow (1.1.8): the data's save time is ALWAYS stated — a clock
     * time while fresh, the age once stale — so "FOR NOW" never quietly
     * presents an old record as live (findings §4.3).
     */
    fun smallEyebrow(savedAtLocal: LocalDateTime, savedAtEpochMs: Long, nowEpochMs: Long): String {
        return if (isStale(savedAtEpochMs, nowEpochMs)) {
            com.runwear.app.i18n.AppEnglish.WIDGET_FOR_NOW_AGE.format(ageShortUpper(ageMs(savedAtEpochMs, nowEpochMs)))
        } else {
            com.runwear.app.i18n.AppEnglish.WIDGET_FOR_NOW_AGE.format(com.runwear.app.i18n.LocaleFormats.timeShort(savedAtLocal))
        }
    }

    /** True while a manual refresh is genuinely in flight (flag set recently). */
    fun isRefreshActive(flagSetAtEpochMs: Long?, nowEpochMs: Long): Boolean {
        if (flagSetAtEpochMs == null || flagSetAtEpochMs <= 0L) return false
        return nowEpochMs - flagSetAtEpochMs in 0..REFRESH_FLAG_TTL_MS
    }
}

/**
 * Plain-words failure categories for the never-succeeded error card (spec
 * §B.3.1): the user reads WHY, not an exception message.
 */
enum class WidgetErrorCategory(val headline: String, val reason: String) {
    LOCATION(
        headline = com.runwear.app.i18n.AppEnglish.WIDGET_COULDNT_LOAD,
        reason = com.runwear.app.i18n.AppEnglish.WIDGET_LOCATION_UNAVAILABLE
    ),
    WEATHER(
        headline = com.runwear.app.i18n.AppEnglish.WIDGET_COULDNT_LOAD,
        reason = com.runwear.app.i18n.AppEnglish.WIDGET_COULDNT_REACH
    )
}

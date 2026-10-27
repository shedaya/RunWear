package com.runwear.app.widget

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
            minutes < 1 -> "JUST NOW"
            minutes < 60 -> "${minutes}M AGO"
            minutes < 60 * 24 -> "${minutes / 60}H AGO"
            else -> "${minutes / (60 * 24)}D AGO"
        }
    }

    /** Sentence-case age for the Large brand strip: "3h ago", "25 min ago", "2d ago". */
    fun ageWords(ageMs: Long): String {
        val minutes = ageMs / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60}h ago"
            else -> "${minutes / (60 * 24)}d ago"
        }
    }

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** Large brand-strip freshness: a clock time while recent, an age once it's old. */
    fun updatedLabel(savedAtLocal: LocalDateTime, savedAtEpochMs: Long, nowEpochMs: Long): String {
        val age = ageMs(savedAtEpochMs, nowEpochMs)
        return if (age < LARGE_AGE_FORM_MS) {
            "Updated ${savedAtLocal.format(TIME_FORMAT)}"
        } else {
            "Updated ${ageWords(age)}"
        }
    }

    /** Small/Horizontal eyebrow: clean while fresh, age appended only when stale (spec §B.3.4). */
    fun forNowEyebrow(savedAtEpochMs: Long, nowEpochMs: Long): String {
        return if (isStale(savedAtEpochMs, nowEpochMs)) {
            "FOR NOW · ${ageShortUpper(ageMs(savedAtEpochMs, nowEpochMs))}"
        } else {
            "FOR NOW"
        }
    }

    /**
     * Small eyebrow (1.1.8): the data's save time is ALWAYS stated — a clock
     * time while fresh, the age once stale — so "FOR NOW" never quietly
     * presents an old record as live (findings §4.3).
     */
    fun smallEyebrow(savedAtLocal: LocalDateTime, savedAtEpochMs: Long, nowEpochMs: Long): String {
        return if (isStale(savedAtEpochMs, nowEpochMs)) {
            "FOR NOW · ${ageShortUpper(ageMs(savedAtEpochMs, nowEpochMs))}"
        } else {
            "FOR NOW · ${savedAtLocal.format(TIME_FORMAT)}"
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
        headline = "Couldn't load weather",
        reason = "Location isn't available to the widget"
    ),
    WEATHER(
        headline = "Couldn't load weather",
        reason = "Couldn't reach the weather service"
    )
}

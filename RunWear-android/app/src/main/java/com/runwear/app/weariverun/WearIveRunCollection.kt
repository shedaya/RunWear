package com.runwear.app.weariverun

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RW-26: "Wear I've Run" — the collection domain (checklist §C;
 * design notes §1 + §8-K). Pure logic, no Android types, mirrored
 * rule-for-rule by the iOS lane's Swift implementation:
 *
 * - One entry per day per place. An entry is created automatically at
 *   the day's first generation; the saved entry is the day's LAST
 *   settled version (a later generation the same day replaces it).
 * - NO backfill: entries exist only from the build that records them.
 * - Chapters are BY LOCATION. A chapter holding more than one stay
 *   counts stays + total days and names the LAST stay — never a
 *   single span implying continuity. A single-stay chapter keeps its
 *   honest span.
 * - Hometown-scale chapters group year -> month rows with computed
 *   counts and feels-like ranges; the most recent card is the cover.
 * - The weather filter works at collection level AND inside a
 *   chapter, recomputing every summary it touches.
 * - Days newest-first; stays newest-first; days newest-first within.
 */

/** One day's settled entry for one place. */
data class WearIveRunDayEntry(
    val date: LocalDate,
    val place: String,
    /** Comfort-adjusted feels-like (the ONE frame, RW-23), Fahrenheit. */
    val feelsLikeF: Double,
    /** Condition label from the same frame ("Cloudy", "Snow", ...). */
    val conditionLabel: String,
    val isSnowing: Boolean,
    val isRaining: Boolean,
    /** The day's garment list (display names), last settled version. */
    val items: List<String>,
    /** Hero image reference for the day (URL or row id). */
    val heroRef: String?
)

/** The collection's weather filter (design §1). */
enum class WearWeatherFilter { ALL, COLD, SNOW, RAIN, HEAT }

/** Pure collection arithmetic — every summary is computed, never stored. */
object WearIveRunCollectionLogic {

    const val COLD_MAX_F = 40.0
    const val HEAT_MIN_F = 80.0

    /** Hometown-scale threshold: at/above this many days a chapter
     *  presents year -> month groups instead of one naive scroll. */
    const val HOMETOWN_SCALE_DAYS = 60

    fun matches(entry: WearIveRunDayEntry, filter: WearWeatherFilter): Boolean = when (filter) {
        WearWeatherFilter.ALL -> true
        WearWeatherFilter.COLD -> entry.feelsLikeF < COLD_MAX_F
        WearWeatherFilter.SNOW -> entry.isSnowing
        WearWeatherFilter.RAIN -> entry.isRaining
        WearWeatherFilter.HEAT -> entry.feelsLikeF >= HEAT_MIN_F
    }

    fun filtered(entries: List<WearIveRunDayEntry>, filter: WearWeatherFilter): List<WearIveRunDayEntry> =
        entries.filter { matches(it, filter) }

    /**
     * Insert-or-replace: the saved entry for (date, place) is the
     * day's LAST settled version. Returns the full set sorted
     * newest-first. One entry per day per place is the invariant.
     */
    fun upsert(
        entries: List<WearIveRunDayEntry>,
        entry: WearIveRunDayEntry
    ): List<WearIveRunDayEntry> {
        val rest = entries.filterNot { it.date == entry.date && it.place == entry.place }
        return (rest + entry).sortedWith(
            compareByDescending<WearIveRunDayEntry> { it.date }.thenBy { it.place }
        )
    }

    fun remove(entries: List<WearIveRunDayEntry>, date: LocalDate, place: String): List<WearIveRunDayEntry> =
        entries.filterNot { it.date == date && it.place == place }

    /** A maximal run of consecutive calendar days at one place. */
    data class Stay(val days: List<WearIveRunDayEntry>) {
        val start: LocalDate get() = days.minOf { it.date }
        val end: LocalDate get() = days.maxOf { it.date }
        val dayCount: Int get() = days.size
    }

    /** Stays of one place's days, newest-first; days newest-first within. */
    fun staysOf(placeEntries: List<WearIveRunDayEntry>): List<Stay> {
        val sorted = placeEntries.sortedBy { it.date }
        val stays = mutableListOf<MutableList<WearIveRunDayEntry>>()
        for (entry in sorted) {
            val last = stays.lastOrNull()
            if (last != null && last.last().date.plusDays(1) == entry.date) {
                last.add(entry)
            } else {
                stays.add(mutableListOf(entry))
            }
        }
        return stays.map { Stay(it.sortedByDescending { e -> e.date }) }
            .sortedByDescending { it.end }
    }

    data class Chapter(
        val place: String,
        /** Entries after the active filter, newest-first. */
        val days: List<WearIveRunDayEntry>,
        /** Stays computed from the FILTERED days. */
        val stays: List<Stay>,
        /** Most recent card (of the filtered set) — the cover. */
        val cover: WearIveRunDayEntry?,
        val feelsMinF: Double?,
        val feelsMaxF: Double?,
        /** Unfiltered day count — decides hometown-scale grouping. */
        val totalDaysUnfiltered: Int
    ) {
        val dayCount: Int get() = days.size
        val stayCount: Int get() = stays.size
        val isMultiStay: Boolean get() = stays.size > 1
        val isHometownScale: Boolean get() = totalDaysUnfiltered >= HOMETOWN_SCALE_DAYS

        /**
         * The computed summary line (computed language only, no
         * trophy copy): multi-stay chapters count stays + total days
         * and name the last stay; single-stay chapters keep the span.
         */
        fun summaryLine(): String {
            if (days.isEmpty()) return place
            val range = feelsRangeLabel()
            return if (isMultiStay) {
                val last = stays.first()
                "$stayCount stays · $dayCount days · Last stay · ${spanLabel(last.start, last.end)}" +
                    (range?.let { " · $it" } ?: "")
            } else {
                val stay = stays.first()
                "$dayCount ${if (dayCount == 1) "day" else "days"} · ${spanLabel(stay.start, stay.end)}" +
                    (range?.let { " · $it" } ?: "")
            }
        }

        fun feelsRangeLabel(): String? {
            val min = feelsMinF ?: return null
            val max = feelsMaxF ?: return null
            return if (min == max) "${min.toInt()}°" else "${min.toInt()}°–${max.toInt()}°"
        }
    }

    /** Chapters by location, ordered by most recent day (newest first). */
    fun chapters(
        entries: List<WearIveRunDayEntry>,
        filter: WearWeatherFilter = WearWeatherFilter.ALL
    ): List<Chapter> {
        val byPlace = entries.groupBy { it.place }
        return byPlace.map { (place, placeEntries) ->
            val days = filtered(placeEntries, filter).sortedByDescending { it.date }
            Chapter(
                place = place,
                days = days,
                stays = staysOf(days),
                cover = days.firstOrNull(),
                feelsMinF = days.minOfOrNull { it.feelsLikeF },
                feelsMaxF = days.maxOfOrNull { it.feelsLikeF },
                totalDaysUnfiltered = placeEntries.size
            )
        }.sortedByDescending { it.cover?.date ?: LocalDate.MIN }
    }

    /** One year -> month group inside a hometown-scale chapter. */
    data class MonthGroup(
        val year: Int,
        val month: Int,
        val days: List<WearIveRunDayEntry>
    ) {
        val count: Int get() = days.size
        val feelsMinF: Double? get() = days.minOfOrNull { it.feelsLikeF }
        val feelsMaxF: Double? get() = days.maxOfOrNull { it.feelsLikeF }
        fun label(): String =
            LocalDate.of(year, month, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))

        fun rangeLabel(): String? {
            val min = feelsMinF ?: return null
            val max = feelsMaxF ?: return null
            return if (min == max) "${min.toInt()}°" else "${min.toInt()}°–${max.toInt()}°"
        }
    }

    /** Year -> month groups, newest first, from (already filtered) days. */
    fun monthGroups(days: List<WearIveRunDayEntry>): List<MonthGroup> =
        days.groupBy { it.date.year to it.date.monthValue }
            .map { (ym, groupDays) ->
                MonthGroup(ym.first, ym.second, groupDays.sortedByDescending { it.date })
            }
            .sortedWith(compareByDescending<MonthGroup> { it.year }.thenByDescending { it.month })

    /** Year subtotal line for a hometown chapter ("2027 · 156 days"). */
    fun yearCounts(days: List<WearIveRunDayEntry>): List<Pair<Int, Int>> =
        days.groupBy { it.date.year }
            .map { it.key to it.value.size }
            .sortedByDescending { it.first }

    /** "Mar 11–13" / "Mar 11" / "Mar 30 – Apr 2" span label. */
    fun spanLabel(start: LocalDate, end: LocalDate): String {
        val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        return when {
            start == end -> start.format(monthDay)
            start.year == end.year && start.month == end.month ->
                "${start.format(monthDay)}–${end.dayOfMonth}"
            else -> "${start.format(monthDay)} – ${end.format(monthDay)}"
        }
    }

    /** The mini bar's day form: "Today" for today, else "MMM d". */
    fun dayForm(date: LocalDate, today: LocalDate): String =
        if (date == today) "Today"
        else date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
}

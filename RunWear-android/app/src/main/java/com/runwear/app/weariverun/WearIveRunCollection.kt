package com.runwear.app.weariverun

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RW-26: "Wear I've Run" — the collection domain (checklist §C;
 * design notes §1 + §8-K). Pure logic, no Android types, mirrored
 * rule-for-rule by the iOS lane's Swift implementation:
 *
 * - GEN-3 (WS-C, Sol's Nov 4 correction — supersedes the per-day /
 *   per-place rule below wherever they conflict): ONE entry per
 *   SESSION (see [WearIveRunSession] for the operational
 *   definition). An entry is created automatically at the session's
 *   first settled generation; within the session every later
 *   location/weather change replaces it IN PLACE, and the saved
 *   entry is the session's LAST settled version. Persisted entries
 *   are keyed by session ([WearIveRunDayEntry.sessionId]), not by
 *   (day, place) and not by place alone.
 * - Legacy entries (recorded before the session rule, sessionId
 *   blank) keep the historical key: one entry per day per place,
 *   last settled version replaces. They are never wiped; see
 *   [upsert] for how a session entry supersedes same-day legacy
 *   rows going forward.
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

/**
 * One settled entry. Since GEN-3 its identity is its SESSION
 * ([sessionId]): one session produces exactly one entry, whose
 * [date]/[place]/weather/kit fields carry the session's last
 * settled version. Entries recorded before the session rule carry
 * a blank [sessionId] and keep their historical (date, place)
 * identity.
 */
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
    val heroRef: String?,
    /** GEN-3 (WS-C): the recording session's id (minted in process
     * memory at runtime; persisted ON the entry so the scrapbook
     * can key by session). Blank = legacy, pre-session-rule entry. */
    val sessionId: String = "",
    /** GEN-3 (WS-C): when this version settled (epoch millis;
     * 0 for legacy entries). The recency key that lets every
     * surface agree on the LATEST entry for a day — never an
     * alphabetical place comparison (the X-5 stale-TODAY bug: the
     * pinned card picked the alphabetically-last place and stayed
     * on Overland Park while he stood in Miami). */
    val settledAtMillis: Long = 0L,
    /** GEN-5 (RW-28, census item 9 + C-7): the settled moment's
     * simple clock reading ("9 p.m.") when the entry was settled
     * at a CONFIRMED changed moment; null = the Now moment. The
     * Wear card re-solves with a confirmed hour change, and the
     * narration names this reading when the time is what changed.
     * Entries recorded before GEN-5 carry null (Now). */
    val momentLabel: String? = null
)

/** The collection's weather filter (design §1). */
enum class WearWeatherFilter { ALL, COLD, SNOW, RAIN, HEAT }

/** What system Back does on the collection surface (WS-9 fix, P-3). */
enum class CollectionBackAction { STEP_BACK_TO_CHAPTER_LIST, DISMISS_COLLECTION }

/**
 * WS-9 fix (P-3, §1.6): the collection's internal navigation is a
 * stack — chapters → chapter — and Back walks it one step at a
 * time, exactly as the in-header back arrow does and as iOS's push
 * navigation does. Back inside a chapter steps back to the chapter
 * LIST; only Back at the list dismisses the collection to home.
 * (The pre-fix surface had no Back step: the hosting Dialog's
 * dismiss fired from inside a chapter too, dropping the user
 * straight home.)
 */
object CollectionBackPolicy {
    fun actionFor(openChapter: String?): CollectionBackAction =
        if (openChapter != null) {
            CollectionBackAction.STEP_BACK_TO_CHAPTER_LIST
        } else {
            CollectionBackAction.DISMISS_COLLECTION
        }
}

/** Pure collection arithmetic — every summary is computed, never stored. */
object WearIveRunCollectionLogic {

    const val COLD_MAX_F = 40.0
    const val HEAT_MIN_F = 80.0

    /** Hometown-scale threshold: at/above this many days a chapter
     *  presents year -> month groups instead of one naive scroll. */
    /**
     * GEN-5 (RW-29 FA-38): the hometown grouping threshold counts
     * DISTINCT calendar days and matches the iOS rule (month
     * groups when the chapter's distinct day count exceeds 31) —
     * the gen-4 Android constant counted 60 ENTRIES, so the same
     * collection grouped on one platform and stayed flat on the
     * other. No design source fixes an exact number; parity does.
     */
    const val HOMETOWN_SCALE_DAYS = 32

    fun matches(entry: WearIveRunDayEntry, filter: WearWeatherFilter): Boolean = when (filter) {
        WearWeatherFilter.ALL -> true
        WearWeatherFilter.COLD -> entry.feelsLikeF < COLD_MAX_F
        WearWeatherFilter.SNOW -> entry.isSnowing
        WearWeatherFilter.RAIN -> entry.isRaining
        WearWeatherFilter.HEAT -> entry.feelsLikeF >= HEAT_MIN_F
    }

    fun filtered(entries: List<WearIveRunDayEntry>, filter: WearWeatherFilter): List<WearIveRunDayEntry> =
        entries.filter { matches(it, filter) }

    /** GEN-3: an entry's identity — its session when it has one,
     * else its legacy (date, place) key. */
    fun identityOf(entry: WearIveRunDayEntry): String =
        if (entry.sessionId.isNotBlank()) {
            "session:${entry.sessionId}"
        } else {
            "legacy:${entry.date}|${entry.place}"
        }

    fun sameIdentity(a: WearIveRunDayEntry, b: WearIveRunDayEntry): Boolean =
        identityOf(a) == identityOf(b)

    /**
     * Insert-or-replace, GEN-3 session rule:
     *
     * - A SESSION entry (sessionId non-blank) replaces the entry
     *   with the SAME sessionId wherever it sits — across place and
     *   across date (a session spanning midnight still holds one
     *   entry). It also supersedes LEGACY (blank-session) entries
     *   dated the entry's own date: those rows are today's pre-fix
     *   accumulation, and from the first session recording of the
     *   day forward the day follows the session framing. Legacy
     *   entries for OTHER dates are untouched — history is never
     *   wiped.
     * - A LEGACY entry keeps the historical rule: the saved entry
     *   for (date, place) is the day's LAST settled version.
     *
     * Returns the full set sorted newest-first.
     */
    fun upsert(
        entries: List<WearIveRunDayEntry>,
        entry: WearIveRunDayEntry
    ): List<WearIveRunDayEntry> {
        val rest = if (entry.sessionId.isNotBlank()) {
            entries.filterNot {
                (it.sessionId.isNotBlank() && it.sessionId == entry.sessionId) ||
                    // GEN-5 (RW-29 FA-37): the legacy supersede is
                    // scoped to the SAME PLACE — the gen-4 rule
                    // deleted the day's legacy entries at EVERY
                    // place, silently erasing the second place of
                    // a legacy travel day.
                    (it.sessionId.isBlank() && it.date == entry.date &&
                        it.place == entry.place)
            }
        } else {
            entries.filterNot { it.date == entry.date && it.place == entry.place }
        }
        return (rest + entry).sortedWith(
            compareByDescending<WearIveRunDayEntry> { it.date }.thenBy { it.place }
        )
    }

    fun remove(entries: List<WearIveRunDayEntry>, date: LocalDate, place: String): List<WearIveRunDayEntry> =
        entries.filterNot { it.date == date && it.place == place }

    /** GEN-3: remove by identity (session entries by sessionId,
     * legacy entries by their (date, place) key). */
    fun removeByIdentity(
        entries: List<WearIveRunDayEntry>,
        entry: WearIveRunDayEntry
    ): List<WearIveRunDayEntry> =
        entries.filterNot { sameIdentity(it, entry) }

    /**
     * GEN-3 (X-5): THE entry for [today] — the day's latest settled
     * version, by [WearIveRunDayEntry.settledAtMillis]. Every
     * surface that names "today's entry" (the mini bar's holder,
     * the collection's pinned TODAY card) resolves through this one
     * function, so the pinned card and the bar can never disagree
     * or go stale on an earlier place. Legacy entries all carry
     * settledAtMillis 0; ties break by place name (ascending) so
     * the choice is deterministic and identical on every surface.
     */
    fun todaysEntry(
        entries: List<WearIveRunDayEntry>,
        today: LocalDate
    ): WearIveRunDayEntry? {
        val todays = entries.filter { it.date == today }
        val latestSettled = todays.maxOfOrNull { it.settledAtMillis } ?: return null
        // Among entries tied at the latest settled instant (the
        // legacy all-zero case), the alphabetically first place —
        // deterministic, and the same choice on every surface.
        return todays.filter { it.settledAtMillis == latestSettled }
            .minByOrNull { it.place }
    }

    /** A maximal run of consecutive calendar days at one place. */
    data class Stay(val days: List<WearIveRunDayEntry>) {
        val start: LocalDate get() = days.minOf { it.date }
        val end: LocalDate get() = days.maxOf { it.date }
        /**
         * GEN-5 (RW-29 FA-23): calendar DAYS, not entries — two
         * sessions on one day are one day (iOS counts distinct day
         * starts; the gen-4 count read "2 days" for one Miami day).
         */
        val dayCount: Int get() = days.map { it.date }.distinct().size
    }

    /** Stays of one place's days, newest-first; days newest-first within. */
    fun staysOf(placeEntries: List<WearIveRunDayEntry>): List<Stay> {
        val sorted = placeEntries.sortedBy { it.date }
        val stays = mutableListOf<MutableList<WearIveRunDayEntry>>()
        for (entry in sorted) {
            val last = stays.lastOrNull()
            // GEN-5 (Miami crash fix): two entries for the SAME date
            // at one place (two sessions on one day — e.g. Miami
            // visited in two Nov 5 sessions) belong to the SAME
            // stay. The pre-fix rule split them into two stays with
            // the same start date, and ChapterDetail keyed its lazy
            // items by that start date — duplicate keys, an
            // IllegalArgumentException, and the app died opening
            // the place. Same day = same stay.
            if (last != null &&
                (last.last().date == entry.date ||
                    last.last().date.plusDays(1) == entry.date)
            ) {
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
        /** Unfiltered DISTINCT day count — decides hometown-scale grouping. */
        val totalDaysUnfiltered: Int
    ) {
        /** GEN-5 (FA-23): distinct calendar days, never entry count. */
        val dayCount: Int get() = days.map { it.date }.distinct().size
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
                totalDaysUnfiltered = placeEntries.map { it.date }.distinct().size
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

    /**
     * GEN-5 (RW-28, census item 10 / B-18): the TODAY card's
     * "Last saved …" element, from the entry's settle instant —
     * "Last saved just now" inside a minute, then minutes/hours,
     * then the day form. Legacy entries (settledAtMillis 0) carry
     * no honest save time and render no label.
     */
    fun lastSavedLabel(entry: WearIveRunDayEntry, nowMillis: Long): String? {
        if (entry.settledAtMillis <= 0L) return null
        val ageMs = nowMillis - entry.settledAtMillis
        if (ageMs < 0L) return "Last saved just now"
        val minutes = ageMs / 60_000L
        return when {
            minutes < 1L -> "Last saved just now"
            minutes < 60L -> "Last saved $minutes min ago"
            minutes < 60L * 24L -> {
                val hours = minutes / 60L
                if (hours == 1L) "Last saved 1 hr ago" else "Last saved $hours hr ago"
            }
            else -> "Last saved ${dayForm(entry.date, entry.date)}"
        }
    }

    /** The collection's day form: "Today" for today, else "MMM d". */
    fun dayForm(date: LocalDate, today: LocalDate): String =
        if (date == today) "Today"
        else date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))

    /**
     * WS-4 (D-08): the NARRATION's day form — "Now" for today, else
     * "MMM d". The narration pattern of record reads "Added [Place,
     * Now]"; the collection's "Today" belongs to collection surfaces
     * (day titles, chapter rows), never to the narration line. The
     * holder must use this form, not [dayForm].
     */
    fun narrationDayForm(date: LocalDate, today: LocalDate): String =
        if (date == today) "Now"
        else date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
}

package com.runwear.app.weariverun

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RW-25 / RW-26 / WS-3 (RC-3): "Wear I've Run" — the element's ONE
 * state machine, narration formatter, and translucency policy.
 *
 * WS-3 replaces the three competing expand drivers of the RW-27
 * audit (D-07: a 3-second cue re-fired by a LaunchedEffect keyed on
 * outfit/weather/location/hero URL, an end-of-scroll latch with no
 * collapse counterpart, and a tap that opened the collection instead
 * of expanding) with [WearIveRunElementMachine]: an enumerated event
 * set and a total transition table. The event set is the ONLY way
 * the element's state ever changes — there is deliberately no event
 * for an asynchronous landing (weather refresh, hero URL resolution,
 * unit re-render, foregrounding), so those landings cannot move the
 * element; the holder folds them into the presented entry's CONTENT
 * silently (see [WearIveRunBarStateHolder.onEntriesChanged]).
 */

/** One collection entry, as the expanded bar presents it. */
data class WearIveRunEntry(
    /** The SIMPLE place form ("New York, NY") — never a raw geocode string. */
    val place: String,
    /** The narration day form ("Now" for today) — never the collection's "Today". */
    val timeLabel: String,
    /** The variable payload of an "Update to …" narration, if any. */
    val payload: String? = null,
    val thumbnailUrl: String? = null,
    /** WS-4: the entry's settled feels-like, for the expanded subtitle. */
    val feelsLikeF: Double? = null,
    /** WS-4: the entry's kit size, for the expanded subtitle. */
    val itemCount: Int = 0
)

/**
 * WS-4 (D-08): the expanded card's subtitle. The design's expanded
 * detail line (frame 3) carries the entry's numbers — feels-like and
 * kit count. Place and day live in the narration directly above it;
 * repeating them here was the duplication defect, so this line is
 * built ONLY from the entry's weather/kit facts: it contains neither
 * the place, nor the day form, nor the narration's payload (the
 * first kit item). Pure — unit-tested.
 */
object WearIveRunSubtitle {

    /** "41° feels like · 5 items" — null when the entry has no weather. */
    fun detail(entry: WearIveRunEntry): String? {
        val feels = entry.feelsLikeF ?: return null
        val items = when (entry.itemCount) {
            1 -> "1 item"
            else -> "${entry.itemCount} items"
        }
        return "${feels.toInt()}° feels like · $items"
    }
}

/**
 * A narration line with the character range of its payload — the part
 * the bar renders in pine. Structured (not just a String) so the
 * formatter stays pure and unit-testable with no composition involved.
 */
data class WearIveRunNarration(
    val text: String,
    val payloadStart: Int,
    val payloadEnd: Int
) {
    val payload: String get() = text.substring(payloadStart, payloadEnd)
}

/** The element's display states — exactly the spec's four (§1.2). */
sealed interface WearIveRunBarState {

    /** The machine's kind, for transition-table tests and pins. */
    val kind: WearIveRunElementKind

    /** Nothing for today yet — the element does not render at all. */
    data object Absent : WearIveRunBarState {
        override val kind = WearIveRunElementKind.ABSENT
    }

    /** A state that carries content: narration + the presented entry. */
    sealed interface Present : WearIveRunBarState {
        val narration: WearIveRunNarration
        val entry: WearIveRunEntry
    }

    /** Mini state: wordmark + one narration line + the two icons. */
    data class Mini(
        override val narration: WearIveRunNarration,
        override val entry: WearIveRunEntry
    ) : Present {
        override val kind = WearIveRunElementKind.MINI
    }

    /**
     * Full expression: thumbnail, entry data, and the SAME two icons
     * as Mini (collapse + trash — no "Remove" text button, D-10),
     * fully opaque background.
     */
    data class Expanded(
        override val narration: WearIveRunNarration,
        override val entry: WearIveRunEntry
    ) : Present {
        override val kind = WearIveRunElementKind.EXPANDED
    }

    /**
     * CUE: a timed PRESENTATION of Expanded, not a fifth content
     * state — it carries exactly the expanded content and renders it
     * with the cue motion (rise + one pulse + one soft haptic). Its
     * only exits are the cue timeout, a touch on the element, trash,
     * and day rollover.
     */
    data class Cue(
        override val narration: WearIveRunNarration,
        override val entry: WearIveRunEntry
    ) : Present {
        override val kind = WearIveRunElementKind.CUE
    }
}

/** The four states of §1.2, as a kind the machine transitions over. */
enum class WearIveRunElementKind { ABSENT, MINI, EXPANDED, CUE }

/**
 * Every driver of the element — the COMPLETE list (§1.2's driver
 * table, WS-3 task table). Anything not named here cannot move the
 * element: there is no event for a weather refresh landing, a hero
 * URL resolving, a location string changing, a unit re-render, or
 * the app foregrounding. Those landings update content only.
 */
enum class WearIveRunElementEvent {
    /** A generation/update for today settles into a NEW entry version. */
    GENERATION_LANDED,
    /** The user taps the mini bar's body. */
    TAP_BODY,
    /** The user taps the expand control. */
    EXPAND_CONTROL,
    /** The user taps the collapse control (the flipped glyph). */
    COLLAPSE_CONTROL,
    /** The cue's 3.0 s timer expires with no interaction. */
    CUE_TIMEOUT,
    /** The home scroll arrives at its end (within 8 dp of max). */
    SCROLL_ARRIVED_AT_END,
    /** The home scroll departs its end by more than 48 dp. */
    SCROLL_DEPARTED_END,
    /** The user taps trash, in either present state. */
    TRASH,
    /** The local day at the presented place rolls over. */
    DAY_ROLLOVER
}

/**
 * WS-3: THE state machine — one pure, total transition table.
 *
 * Illegal transitions are unrepresentable as drivers: an event that
 * has no row for a state is a NO-OP in that state (the table returns
 * the state unchanged), never a fall-through into another driver.
 * The table, state by state:
 *
 * - ABSENT: only GENERATION_LANDED leaves it (→ CUE, the creation
 *   cue). Everything else is a no-op — nothing can expand, collapse,
 *   or time out an element that does not exist.
 * - MINI: GENERATION_LANDED → CUE (a user-caused update); TAP_BODY
 *   and EXPAND_CONTROL → EXPANDED (D-07: the body tap expands, it no
 *   longer opens the collection); SCROLL_ARRIVED_AT_END → EXPANDED;
 *   TRASH → ABSENT; DAY_ROLLOVER → ABSENT.
 * - EXPANDED: GENERATION_LANDED → CUE; COLLAPSE_CONTROL → MINI;
 *   SCROLL_DEPARTED_END → MINI (symmetric with arrival — D-07's
 *   latch is retired); SCROLL_ARRIVED_AT_END → stays EXPANDED;
 *   TRASH → ABSENT; DAY_ROLLOVER → ABSENT.
 * - CUE: GENERATION_LANDED → stays CUE (the landing restarts the
 *   timer — the holder bumps the cue sequence); CUE_TIMEOUT → MINI;
 *   TAP_BODY / EXPAND_CONTROL → EXPANDED (a touch cancels the timer
 *   and obeys the touch); COLLAPSE_CONTROL → MINI; scroll events do
 *   NOT move a cueing element; TRASH → ABSENT; DAY_ROLLOVER → ABSENT.
 */
object WearIveRunElementMachine {

    /** The cue's lifetime before it settles to MINI (§1.2: 3.0 s). */
    const val CUE_TIMEOUT_MS = 3_000L

    /** The cue's single pulse: 900 ms ease-in-out, exactly once (§1.2). */
    const val CUE_PULSE_MS = 900L

    /** The cue's rise into place: 350 ms emphasized-decelerate (§1.2). */
    const val CUE_RISE_MS = 350L

    /** Rise distance of the cue entrance, in dp (§1.2: 12 dp). */
    const val CUE_RISE_DP = 12f

    /** End-of-scroll arrival: within this distance of max (dp). */
    const val END_ARRIVAL_EPSILON_DP = 8

    /** End-of-scroll departure: beyond this distance from max (dp). */
    const val END_DEPARTURE_THRESHOLD_DP = 48

    /** Height-collapse duration when trash removes the element (§1.2). */
    const val TRASH_COLLAPSE_MS = 250L

    fun transition(
        kind: WearIveRunElementKind,
        event: WearIveRunElementEvent
    ): WearIveRunElementKind = when (kind) {
        WearIveRunElementKind.ABSENT -> when (event) {
            WearIveRunElementEvent.GENERATION_LANDED -> WearIveRunElementKind.CUE
            else -> WearIveRunElementKind.ABSENT
        }
        WearIveRunElementKind.MINI -> when (event) {
            WearIveRunElementEvent.GENERATION_LANDED -> WearIveRunElementKind.CUE
            WearIveRunElementEvent.TAP_BODY -> WearIveRunElementKind.EXPANDED
            WearIveRunElementEvent.EXPAND_CONTROL -> WearIveRunElementKind.EXPANDED
            WearIveRunElementEvent.SCROLL_ARRIVED_AT_END -> WearIveRunElementKind.EXPANDED
            WearIveRunElementEvent.TRASH -> WearIveRunElementKind.ABSENT
            WearIveRunElementEvent.DAY_ROLLOVER -> WearIveRunElementKind.ABSENT
            else -> WearIveRunElementKind.MINI
        }
        WearIveRunElementKind.EXPANDED -> when (event) {
            WearIveRunElementEvent.GENERATION_LANDED -> WearIveRunElementKind.CUE
            WearIveRunElementEvent.COLLAPSE_CONTROL -> WearIveRunElementKind.MINI
            WearIveRunElementEvent.SCROLL_DEPARTED_END -> WearIveRunElementKind.MINI
            WearIveRunElementEvent.TRASH -> WearIveRunElementKind.ABSENT
            WearIveRunElementEvent.DAY_ROLLOVER -> WearIveRunElementKind.ABSENT
            else -> WearIveRunElementKind.EXPANDED
        }
        WearIveRunElementKind.CUE -> when (event) {
            WearIveRunElementEvent.CUE_TIMEOUT -> WearIveRunElementKind.MINI
            WearIveRunElementEvent.TAP_BODY -> WearIveRunElementKind.EXPANDED
            WearIveRunElementEvent.EXPAND_CONTROL -> WearIveRunElementKind.EXPANDED
            WearIveRunElementEvent.COLLAPSE_CONTROL -> WearIveRunElementKind.MINI
            WearIveRunElementEvent.TRASH -> WearIveRunElementKind.ABSENT
            WearIveRunElementEvent.DAY_ROLLOVER -> WearIveRunElementKind.ABSENT
            else -> WearIveRunElementKind.CUE
        }
    }
}

/**
 * GEN-2 (W-11): the update narration is computed from the DIFF
 * between the previous settled entry and the new one — it names an
 * item that ACTUALLY changed, never an unchanged one. (The walkthrough
 * case: the kit changed Light Tights -> Running Shorts while Light
 * Long Sleeve stayed, and the bar still read "Update to Light Long
 * Sleeve" — the old code named the entry's first item, changed or
 * not.) Pure — unit-tested.
 *
 * The payload, in priority order:
 * - the first ADDED item (an item present in the new kit that was
 *   not in the old, in new-kit order) — a substitution names the
 *   item that arrived, not the one that stayed or the one that left;
 * - else, for a pure reorder, the first item whose position changed;
 * - else, for a pure removal, the new kit's lead item — the settled
 *   kit the update lands on (there is no arrived item to name);
 * - null when the kits are identical (weather/hero/condition-only
 *   landings announce nothing — the abolished-drivers rule), or when
 *   the new kit is empty (the holder falls back to the settled
 *   place reading, as before).
 */
object WearIveRunUpdateNarration {

    /** The changed item an "Update to […]" line should name, if any. */
    fun changedPayload(
        previous: WearIveRunDayEntry,
        next: WearIveRunDayEntry
    ): String? {
        if (previous.items == next.items) return null
        // Multiset difference: items in the new kit beyond the old
        // kit's occurrences of them, in new-kit order.
        val unmatched = previous.items.toMutableList()
        val added = next.items.filter { !unmatched.remove(it) }
        if (added.isNotEmpty()) return added.first()
        // Pure reorder: the first item that moved.
        val moved = next.items.firstOrNull {
            previous.items.indexOf(it) != next.items.indexOf(it)
        }
        if (moved != null) return moved
        // Pure removal: the update lands on the new kit as it stands.
        return next.items.firstOrNull()
    }

    /** The full "Update to […]" narration for an entry update. */
    fun narration(
        previous: WearIveRunDayEntry,
        next: WearIveRunDayEntry
    ): WearIveRunNarration? =
        changedPayload(previous, next)?.let { WearIveRunNarrationFormatter.update(it) }
}

/** Pure narration composition for the bar's one line. */
object WearIveRunNarrationFormatter {

    const val WORDMARK = "Wear I've Run"

    /**
     * "Added {place}, {time}" — e.g. added("New York", "Now") renders
     * "Added New York, Now". The "{place}, {time}" segment is the
     * payload (rendered in pine).
     */
    fun added(place: String, timeLabel: String): WearIveRunNarration {
        val prefix = "Added "
        val payload = "$place, $timeLabel"
        return WearIveRunNarration(
            text = prefix + payload,
            payloadStart = prefix.length,
            payloadEnd = prefix.length + payload.length
        )
    }

    /** "Update to {payload}" — the payload renders in pine. */
    fun update(payload: String): WearIveRunNarration {
        val prefix = "Update to "
        return WearIveRunNarration(
            text = prefix + payload,
            payloadStart = prefix.length,
            payloadEnd = prefix.length + payload.length
        )
    }
}

/**
 * The bar's translucency rule (Sol, RW-25 amendment, binding):
 *
 * - MINI state: the background is TRANSLUCENT — sheet surface at ~82%
 *   alpha in dark (over the card tint), ~85% in light (the stronger light tint keeps the
 *   line holding over light content).
 * - FULL EXPRESSION (expanded / end-of-scroll-expanded / cue moment):
 *   the background is FULLY OPAQUE sheet surface.
 * - Text and icons always render at 100% opacity regardless — only
 *   the background takes the alpha.
 *
 * Pure — unit-tested.
 */
object WearIveRunMiniBarPolicy {

    const val MINI_ALPHA_DARK = 0.82f
    const val MINI_ALPHA_LIGHT = 0.85f
    const val FULL_ALPHA = 1.0f

    fun backgroundAlpha(isFullExpression: Boolean, darkTheme: Boolean): Float = when {
        isFullExpression -> FULL_ALPHA
        darkTheme -> MINI_ALPHA_DARK
        else -> MINI_ALPHA_LIGHT
    }

    /** Whether [state] counts as full expression for the alpha rule.
     * CUE is a presentation of Expanded, so it is full expression too. */
    fun isFullExpression(state: WearIveRunBarState): Boolean =
        state is WearIveRunBarState.Expanded || state is WearIveRunBarState.Cue

    /**
     * WS-3 (D-11): whenever any sheet is up over home, the element —
     * a citizen of home's layer — dims WITH the home backdrop. Its
     * content takes this alpha as a whole (the backdrop scrim's
     * weight, halved light), instead of staying fully bright over
     * the dimmed home the way the root-Box overlay did.
     */
    const val SHEET_DIMMED_ALPHA = 0.5f

    /** The element's whole-surface alpha for a given backdrop state. */
    fun surfaceAlpha(dimmedBySheet: Boolean): Float =
        if (dimmedBySheet) SHEET_DIMMED_ALPHA else 1.0f

    /**
     * §1.0 rule 4 (reduced motion): when the system animator
     * duration scale is 0, the cue's pulse is disabled — the element
     * still cues (state + timeout are information-free motion), it
     * just does not pulse or rise.
     */
    fun cuePulseEnabled(animatorDurationScale: Float): Boolean =
        animatorDurationScale > 0f
}

/**
 * RW-26 / WS-3: the element's ONE state holder, driven by the real
 * collection through [WearIveRunElementMachine] and nothing else.
 *
 * It starts ABSENT and thereafter presents an entry — today's when
 * there is one, else the most recent (the collection doorway's rest
 * state). The holder translates the store's entry list into AT MOST
 * one machine event per distinct settled version:
 *
 * - the FIRST version of today's entry (creation — including its
 *   re-creation after a trash) and a version whose KIT changed (a
 *   real update: the narration's payload is the first kit item) fire
 *   GENERATION_LANDED → CUE and bump [cueSequence] exactly once;
 * - every other landing is CONTENT, never a driver: an identical
 *   re-delivery, a hero-reference-only change (the thumbnail lands),
 *   a feels-like/condition drift, a place-string re-render — these
 *   update the presented entry in place and leave kind, narration,
 *   and cue sequence untouched. This is the abolished-drivers rule:
 *   outfit/weather/location/hero-URL landings as such never move
 *   the element (the D-07 churn mechanism).
 *
 * [cueSequence] is the cue's identity: the UI keys the 3.0 s timeout,
 * the single pulse, and the single soft haptic on it, so a cue fires
 * exactly once per landing and content landings mid-cue cannot
 * restart it.
 */
class WearIveRunBarStateHolder {

    private val _state = MutableStateFlow<WearIveRunBarState>(WearIveRunBarState.Absent)
    val state: StateFlow<WearIveRunBarState> = _state.asStateFlow()

    private val _cueSequence = MutableStateFlow(0)

    /** Monotonic cue identity — increments exactly once per landing that cues. */
    val cueSequence: StateFlow<Int> = _cueSequence.asStateFlow()

    private var kind: WearIveRunElementKind = WearIveRunElementKind.ABSENT
    private var current: WearIveRunDayEntry? = null
    private var narration: WearIveRunNarration? = null

    /** The day the holder believes it is in; null until first contact. */
    private var knownDay: java.time.LocalDate? = null

    /** After a rollover, no older entry may present until today's
     * first generation lands (the rollover rule, §1.2 task table). */
    private var awaitingTodaysGeneration = false

    /** The (date, place) trashed in this session for today: while it
     * stands, an entries list without today's entry must NOT fall
     * back to an older entry and resurrect the element. Its
     * re-appearance in the list IS its re-creation. */
    private var trashedTodayKey: Pair<java.time.LocalDate, String>? = null

    /** The entry the element currently presents (today's, else latest). */
    fun currentEntry(): WearIveRunDayEntry? = current

    /** The machine's current kind. */
    fun currentKind(): WearIveRunElementKind = kind

    private fun emit() {
        val entry = current
        val line = narration
        _state.value = when {
            kind == WearIveRunElementKind.ABSENT || entry == null || line == null ->
                WearIveRunBarState.Absent
            kind == WearIveRunElementKind.MINI ->
                WearIveRunBarState.Mini(line, barEntryOf(entry))
            kind == WearIveRunElementKind.EXPANDED ->
                WearIveRunBarState.Expanded(line, barEntryOf(entry))
            else -> WearIveRunBarState.Cue(line, barEntryOf(entry))
        }
    }

    /** WS-4 content construction: simple place, narration day form,
     * the entry's real payload (its first kit item). */
    private fun barEntryOf(entry: WearIveRunDayEntry): WearIveRunEntry {
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(entry.place).display
        return WearIveRunEntry(
            place = simplePlace,
            timeLabel = WearIveRunCollectionLogic.narrationDayForm(
                entry.date, knownDay ?: entry.date
            ),
            payload = entry.items.firstOrNull()?.takeIf { it.isNotBlank() },
            thumbnailUrl = entry.heroRef,
            feelsLikeF = entry.feelsLikeF,
            itemCount = entry.items.size
        )
    }

    private fun addedNarration(entry: WearIveRunDayEntry): WearIveRunNarration {
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(entry.place).display
        return WearIveRunNarrationFormatter.added(
            simplePlace,
            WearIveRunCollectionLogic.narrationDayForm(entry.date, knownDay ?: entry.date)
        )
    }

    private fun updateNarration(entry: WearIveRunDayEntry): WearIveRunNarration {
        val firstItem = entry.items.firstOrNull()?.takeIf { it.isNotBlank() }
        return if (firstItem != null) {
            WearIveRunNarrationFormatter.update(firstItem)
        } else {
            val simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString(entry.place).display
            WearIveRunNarrationFormatter.update(
                "$simplePlace, ${WearIveRunCollectionLogic.narrationDayForm(entry.date, knownDay ?: entry.date)}"
            )
        }
    }

    /** Dispatch one machine event — the UI's only way to move the element. */
    fun onEvent(event: WearIveRunElementEvent) {
        if (event == WearIveRunElementEvent.DAY_ROLLOVER) {
            // Rollover needs the new day; route through onDayRollover.
            return
        }
        if (event == WearIveRunElementEvent.TRASH) {
            val entry = current
            if (entry != null && entry.date == knownDay) {
                trashedTodayKey = entry.date to entry.place
            }
            kind = WearIveRunElementMachine.transition(kind, event)
            if (kind == WearIveRunElementKind.ABSENT) {
                current = null
                narration = null
            }
            emit()
            return
        }
        kind = WearIveRunElementMachine.transition(kind, event)
        emit()
    }

    /** Day rollover: ABSENT until the new day's first generation. */
    fun onDayRollover(newDay: java.time.LocalDate) {
        if (knownDay == newDay) return
        knownDay = newDay
        kind = WearIveRunElementMachine.transition(kind, WearIveRunElementEvent.DAY_ROLLOVER)
        current = null
        narration = null
        trashedTodayKey = null
        awaitingTodaysGeneration = true
        emit()
    }

    fun onEntriesChanged(entries: List<WearIveRunDayEntry>, today: java.time.LocalDate) {
        // A new day arriving at this seam is a rollover too.
        if (knownDay != null && knownDay != today) {
            onDayRollover(today)
        }
        knownDay = today

        val todays = entries.firstOrNull { it.date == today }

        // Trash suppression: today's entry is gone by the user's own
        // tap — never fall back to an older entry behind their back.
        val trashed = trashedTodayKey
        if (trashed != null && trashed.first == today) {
            if (todays == null) {
                current = null
                narration = null
                kind = WearIveRunElementKind.ABSENT
                emit()
                return
            }
            // It is back in the list: a later generation re-created
            // it — the creation cue fires for the re-creation.
            trashedTodayKey = null
            current = todays
            narration = addedNarration(todays)
            kind = WearIveRunElementMachine.transition(
                WearIveRunElementKind.ABSENT, WearIveRunElementEvent.GENERATION_LANDED
            )
            _cueSequence.value = _cueSequence.value + 1
            emit()
            return
        }

        if (awaitingTodaysGeneration && todays == null) {
            // Rolled over and today has no generation yet: ABSENT,
            // and yesterday's entry does not present.
            current = null
            narration = null
            kind = WearIveRunElementKind.ABSENT
            emit()
            return
        }

        val next = todays ?: entries.maxByOrNull { it.date }
        if (next == null) {
            current = null
            narration = null
            kind = WearIveRunElementKind.ABSENT
            emit()
            return
        }
        if (next.date == today) awaitingTodaysGeneration = false

        val previous = current
        when {
            previous == null -> {
                // First presentation. Today's entry arriving IS the
                // day's generation landing (creation cue); an older
                // entry is just the doorway at rest (MINI, no cue).
                current = next
                narration = addedNarration(next)
                kind = if (next.date == today) {
                    _cueSequence.value = _cueSequence.value + 1
                    WearIveRunElementMachine.transition(
                        kind, WearIveRunElementEvent.GENERATION_LANDED
                    )
                } else {
                    WearIveRunElementKind.MINI
                }
                emit()
            }
            previous == next -> {
                // Identical re-delivery of the settled version: not
                // a landing. Nothing moves, nothing re-fires.
                return
            }
            previous.date != next.date || previous.place != next.place -> {
                // A different day/place is now the presented entry.
                // For today that is a creation; for an older entry it
                // is a content swap at rest.
                current = next
                narration = addedNarration(next)
                if (next.date == today) {
                    kind = WearIveRunElementMachine.transition(
                        kind, WearIveRunElementEvent.GENERATION_LANDED
                    )
                    _cueSequence.value = _cueSequence.value + 1
                }
                emit()
            }
            else -> {
                // Same day + place, a new version of the entry. Only
                // a KIT change is an update the element announces.
                // Everything else (hero reference, feels-like,
                // condition) is an async landing: content updates in
                // place, state does not move, the cue does not re-fire.
                // GEN-2 (W-11): the announced payload is computed
                // from the DIFF between the two versions — it names
                // the item that actually changed (see
                // [WearIveRunUpdateNarration]), never the unchanged
                // first item the old code named.
                val kitChanged = previous.items != next.items
                current = next
                if (kitChanged) {
                    narration = WearIveRunUpdateNarration.narration(previous, next)
                        ?: updateNarration(next)
                    kind = WearIveRunElementMachine.transition(
                        kind, WearIveRunElementEvent.GENERATION_LANDED
                    )
                    _cueSequence.value = _cueSequence.value + 1
                }
                emit()
            }
        }
    }
}

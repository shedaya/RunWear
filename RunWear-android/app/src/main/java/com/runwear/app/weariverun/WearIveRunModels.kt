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

/**
 * GEN-5 (RW-28 Addendum 8, item 2): the ONLY things an "Update
 * to […]" narration may name — a place (with its day form) or a
 * moment reading. The payload is a sealed type, not a string, so
 * a garment payload is UNREPRESENTABLE: the gen-4 device build
 * read "Update to Ear Warmer / Headband" because the payload
 * channel was a free string fed from the kit. There is no
 * constructor here a garment name can flow through.
 */
sealed interface WearIveRunNarrationPayload {
    /** What renders in pine after "Updated to ". */
    fun render(): String

    /** "Updated to {place}, {day}" — the place form. */
    data class Place(val place: String, val dayForm: String) :
        WearIveRunNarrationPayload {
        override fun render(): String = "$place, $dayForm"
    }

    /**
     * "Updated to {reading}" — a clock reading ("9 p.m.") or
     * "Now". RW-98 (Sol's ruling, Nov 6, 2026): when the moment
     * is NOT today's, the payload names the date in full first —
     * "November 12 7 p.m." — so [dateForm] carries the full-date
     * form ("MMMM d") and the render is "{dateForm} {reading}".
     * Today's moments carry no date form: the bare reading.
     */
    data class Moment(val reading: String, val dateForm: String? = null) :
        WearIveRunNarrationPayload {
        override fun render(): String =
            if (dateForm != null) "$dateForm $reading" else reading
    }
}

/** One collection entry, as the expanded bar presents it. */
data class WearIveRunEntry(
    /** The SIMPLE place form ("New York, NY") — never a raw geocode string. */
    val place: String,
    /** The narration day form ("Now" for today) — never the collection's "Today". */
    val timeLabel: String,
    val thumbnailUrl: String? = null,
    /** WS-4: the entry's settled feels-like, for the expanded subtitle. */
    val feelsLikeF: Double? = null,
    /** WS-4: the entry's kit size, for the expanded subtitle. */
    val itemCount: Int = 0,
    /** GEN-5 (RW-29 FA-34): the entry's kit item names, for the
     * expanded subtitle's outfit line. */
    val itemNames: List<String> = emptyList()
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

    /**
     * "41° feels like · 5 items · Light Long Sleeve · Light
     * Tights" — null when the entry has no weather.
     *
     * GEN-5 (RW-29 FA-34): the outfit line (the kit's item
     * names) joins the subtitle — the settled expanded anatomy
     * (design-spec §8.1) carries it, and the gen-4 line stopped
     * at the count. Place and day remain where WS-4 put them —
     * in the narration directly above this line — rather than
     * being repeated here.
     */
    fun detail(entry: WearIveRunEntry): String? {
        val feels = entry.feelsLikeF ?: return null
        val items = when (entry.itemCount) {
            1 -> "1 item"
            else -> "${entry.itemCount} items"
        }
        val outfit = entry.itemNames.filter { it.isNotBlank() }.take(3)
        return buildString {
            append("${feels.toInt()}° feels like · $items")
            if (outfit.isNotEmpty()) {
                append(" · ")
                append(outfit.joinToString(" · "))
            }
        }
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
 * GEN-5 (RW-28, ruling C-7 — the ORIGINAL Nov 2 concept governs):
 * the update narration names WHATEVER CHANGED — never a garment.
 * The gen-2 W-11 rule (name the changed kit item) is SUPERSEDED:
 * the walkthrough's "Update to Tank Top / Singlet" and "Update to
 * Light Long Sleeve" lines are defects against the settled forms.
 *
 * The payload, in priority order (place outranks time — a place
 * change is the bigger move, and its form already carries the day):
 * - the place changed (or the day rolled within the session) ->
 *   the new simple place (the holder renders it in its
 *   "{place}, {day}" form);
 * - else the MOMENT changed -> the new moment's simple reading: a
 *   clock reading ("9 p.m.") for a confirmed changed moment, or
 *   "Now" when the update returns to Now (C-7 nuance, closed
 *   Nov 5: a clock reading is in-spec);
 * - else the kit changed at the same place and moment (e.g. a
 *   body-temperature change re-solved the outfit) -> the entry's
 *   own moment reading when it is a changed moment, else its
 *   place (the holder renders the "{place}, {day}" form);
 * - null when nothing the narration may name changed (weather /
 *   hero-only landings announce nothing — the abolished-drivers
 *   rule), or when the new kit is empty.
 */
object WearIveRunUpdateNarration {

    /** The new entry's moment reading: its clock label, or "Now". */
    fun momentReading(entry: WearIveRunDayEntry): String =
        entry.momentLabel ?: "Now"

    /**
     * RW-98 (Sol's ruling, Nov 6, 2026): the full-date form a
     * moment payload carries when the entry's day is not today —
     * "November 12" (MMMM d) — so the line reads "Updated to
     * November 12 7 p.m.". Null for today's moments, whose bare
     * reading (or "Now") already names them in full.
     */
    fun momentDateForm(
        entry: WearIveRunDayEntry,
        today: java.time.LocalDate
    ): String? =
        if (entry.date == today) {
            null
        } else {
            entry.date.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "MMMM d", java.util.Locale.US
                )
            )
        }

    /**
     * The changed dimension an "Updated to […]" line names, if any
     * — as a [WearIveRunNarrationPayload], so the answer is a
     * place or a moment BY CONSTRUCTION (Addendum 8 item 2: a kit
     * change resolves to the entry's moment when it is a changed
     * moment, else its place; the garment itself has no
     * representation in the result type).
     */
    fun changedPayload(
        previous: WearIveRunDayEntry,
        next: WearIveRunDayEntry,
        today: java.time.LocalDate = next.date
    ): WearIveRunNarrationPayload? {
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(next.place).display
        if (previous.place != next.place || previous.date != next.date) {
            return WearIveRunNarrationPayload.Place(
                simplePlace,
                WearIveRunCollectionLogic.narrationDayForm(next.date, today)
            )
        }
        if (previous.momentLabel != next.momentLabel) {
            return WearIveRunNarrationPayload.Moment(
                momentReading(next), momentDateForm(next, today)
            )
        }
        if (previous.items != next.items) {
            return if (next.momentLabel != null) {
                WearIveRunNarrationPayload.Moment(
                    momentReading(next), momentDateForm(next, today)
                )
            } else {
                WearIveRunNarrationPayload.Place(
                    simplePlace,
                    WearIveRunCollectionLogic.narrationDayForm(next.date, today)
                )
            }
        }
        return null
    }

    /** The full "Updated to […]" narration for an entry update. */
    fun narration(
        previous: WearIveRunDayEntry,
        next: WearIveRunDayEntry,
        today: java.time.LocalDate = next.date
    ): WearIveRunNarration? =
        changedPayload(previous, next, today)?.let { payload ->
            WearIveRunNarrationFormatter.update(payload)
        }
}

/**
 * GEN-5 (RW-28, C-7): a moment's simple clock reading — "9 p.m.",
 * "12 a.m." — the in-spec time payload for update narrations.
 * Lowercase meridiem with periods, matching the ruling's example
 * verbatim ("Updated to 9 p.m.").
 */
object WearMomentLabel {
    fun of(moment: java.time.LocalDateTime): String = ofHour(moment.hour)

    fun ofHour(hour: Int): String {
        val h = ((hour + 11) % 12) + 1
        return if (hour in 0..11) "$h a.m." else "$h p.m."
    }
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

    /**
     * "Updated to {payload}" — the payload renders in pine. The
     * payload is a [WearIveRunNarrationPayload]: there is no
     * overload that takes a free string, so no caller can name a
     * garment (Addendum 8 item 2).
     *
     * RW-98 (Sol's ruling, Nov 6, 2026): the line reads "Updated
     * to," — never "Update to," — on both platforms.
     */
    fun update(payload: WearIveRunNarrationPayload): WearIveRunNarration {
        val prefix = "Updated to "
        val rendered = payload.render()
        return WearIveRunNarration(
            text = prefix + rendered,
            payloadStart = prefix.length,
            payloadEnd = prefix.length + rendered.length
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

    /** GEN-3: the identity (session key, or legacy (date, place)
     * key) trashed for today: while it stands, an entries list
     * without today's entry must NOT fall back to an older entry
     * and resurrect the element. Its re-appearance in the list IS
     * its re-creation. */
    private var trashedTodayIdentity: String? = null

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

    /** WS-4 content construction: simple place, narration day form.
     * GEN-5 (Addendum 8 item 2): the bar entry no longer carries a
     * payload at all — the gen-4 construction stored the entry's
     * first kit item here, and that string is what the device
     * build rendered as "Update to Ear Warmer / Headband". */
    private fun barEntryOf(entry: WearIveRunDayEntry): WearIveRunEntry {
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(entry.place).display
        return WearIveRunEntry(
            place = simplePlace,
            timeLabel = WearIveRunCollectionLogic.narrationDayForm(
                entry.date, knownDay ?: entry.date
            ),
            thumbnailUrl = entry.heroRef,
            feelsLikeF = entry.feelsLikeF,
            itemCount = entry.items.size,
            itemNames = entry.items
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

    private fun updateNarration(entry: WearIveRunDayEntry): WearIveRunNarration =
        // GEN-5 (C-7): the fallback update line is the place form —
        // a garment payload can no longer be produced here either.
        updatePlaceNarration(entry)

    /** Dispatch one machine event — the UI's only way to move the element. */
    fun onEvent(event: WearIveRunElementEvent) {
        if (event == WearIveRunElementEvent.DAY_ROLLOVER) {
            // Rollover needs the new day; route through onDayRollover.
            return
        }
        if (event == WearIveRunElementEvent.TRASH) {
            val entry = current
            if (entry != null && entry.date == knownDay) {
                trashedTodayIdentity = WearIveRunCollectionLogic.identityOf(entry)
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
        trashedTodayIdentity = null
        awaitingTodaysGeneration = true
        emit()
    }

    /** GEN-3 (WS-C): the "Updated to {place}, {day}" narration for a
     * within-session change whose kit did not change — a place (or
     * day) move in the same session is an UPDATE of the session's
     * one entry, never a new "Added …" (census X-4). */
    private fun updatePlaceNarration(entry: WearIveRunDayEntry): WearIveRunNarration {
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(entry.place).display
        return WearIveRunNarrationFormatter.update(
            WearIveRunNarrationPayload.Place(
                simplePlace,
                WearIveRunCollectionLogic.narrationDayForm(entry.date, knownDay ?: entry.date)
            )
        )
    }

    /**
     * GEN-5 (RW-28, census item 7 / B-1): whether the next delivery
     * is the process's FIRST contact with the stored collection.
     * That delivery is a RESTORE, not a landing: the entries were
     * settled by an earlier process, and merely opening the app
     * must never fire the cue or mint an announcement for them.
     * The host primes the holder with the first emission; every
     * later delivery is a live landing and behaves as before.
     */
    private var awaitingRestorePrime = true

    /** Prime with the stored collection's first emission (rest). */
    fun primeRestore(entries: List<WearIveRunDayEntry>, today: java.time.LocalDate) {
        if (!awaitingRestorePrime) {
            onEntriesChanged(entries, today)
            return
        }
        awaitingRestorePrime = false
        knownDay = today
        val todays = WearIveRunCollectionLogic.todaysEntry(entries, today)
        val next = todays ?: entries.maxByOrNull { it.date }
        if (next == null) {
            current = null
            narration = null
            kind = WearIveRunElementKind.ABSENT
            emit()
            return
        }
        if (next.date == today) awaitingTodaysGeneration = false
        current = next
        narration = addedNarration(next)
        kind = WearIveRunElementKind.MINI
        emit()
    }

    fun onEntriesChanged(entries: List<WearIveRunDayEntry>, today: java.time.LocalDate) {
        // Any live delivery also consumes the restore prime: a
        // holder that was never primed treats its first live
        // delivery as before (the unit-test surface), while the
        // app's host always primes first.
        awaitingRestorePrime = false
        // A new day arriving at this seam is a rollover too.
        if (knownDay != null && knownDay != today) {
            onDayRollover(today)
        }
        knownDay = today

        // GEN-3 (X-5): today's entry is THE latest settled version
        // for today (shared logic with the collection's pinned
        // TODAY card) — never the first/alphabetical row, so the
        // mini bar can never announce a place home has moved past.
        val todays = WearIveRunCollectionLogic.todaysEntry(entries, today)

        // Trash suppression: today's entry is gone by the user's own
        // tap — never fall back to an older entry behind their back.
        val trashed = trashedTodayIdentity
        if (trashed != null) {
            val back = entries.firstOrNull {
                WearIveRunCollectionLogic.identityOf(it) == trashed
            }
            if (back != null && back.date == today) {
                // It is back in the list: a later generation
                // re-created it — the creation cue fires for the
                // re-creation.
                trashedTodayIdentity = null
                current = back
                narration = addedNarration(back)
                kind = WearIveRunElementMachine.transition(
                    WearIveRunElementKind.ABSENT, WearIveRunElementEvent.GENERATION_LANDED
                )
                _cueSequence.value = _cueSequence.value + 1
                emit()
                return
            }
            if (todays == null) {
                current = null
                narration = null
                kind = WearIveRunElementKind.ABSENT
                emit()
                return
            }
            // Today's latest is a DIFFERENT identity (a new
            // session's entry): the trash marker has done its job;
            // clear it and present normally below.
            trashedTodayIdentity = null
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
            WearIveRunCollectionLogic.sameIdentity(previous, next) -> {
                // GEN-3 (WS-C): the SAME entry (same session — or
                // the same legacy (date, place)) in a new settled
                // version. The entry updates IN PLACE; it is never a
                // new "Added …". Announced updates:
                // - a KIT change (GEN-2 W-11: the narration payload
                //   is computed from the DIFF — it names the item
                //   that actually changed, never an unchanged one);
                // - a PLACE (or day) change within the session with
                //   an unchanged kit — census X-4: this narrates as
                //   an update too ("Updated to {place}, Now"), and
                //   the presented entry follows the latest commit,
                //   so the mini bar never announces a place home
                //   has already moved past.
                // Everything else (hero reference, feels-like,
                // condition) is an async landing: content updates in
                // place, state does not move, the cue does not
                // re-fire (the abolished-drivers rule).
                // GEN-5 (C-7): an announced update names the
                // dimension that changed — place, else moment, else
                // (kit-only, e.g. a body-temperature re-solve) the
                // entry's moment/place reading — NEVER a garment.
                // A confirmed hour change (moment) now cues and
                // re-solves the card with the rest of home (census
                // item 9); weather/hero-only landings stay silent.
                val placeChanged = previous.place != next.place ||
                    previous.date != next.date
                val momentChanged = previous.momentLabel != next.momentLabel
                val kitChanged = previous.items != next.items
                current = next
                if (placeChanged) {
                    narration = updatePlaceNarration(next)
                    kind = WearIveRunElementMachine.transition(
                        kind, WearIveRunElementEvent.GENERATION_LANDED
                    )
                    _cueSequence.value = _cueSequence.value + 1
                } else if (momentChanged || kitChanged) {
                    narration = WearIveRunUpdateNarration.narration(
                        previous, next, knownDay ?: next.date
                    ) ?: updateNarration(next)
                    kind = WearIveRunElementMachine.transition(
                        kind, WearIveRunElementEvent.GENERATION_LANDED
                    )
                    _cueSequence.value = _cueSequence.value + 1
                }
                emit()
            }
            else -> {
                // A DIFFERENT entry is now the presented one (a new
                // session's entry, a different legacy (date, place),
                // or today arriving over an older doorway entry).
                // For today that is a creation; for an older entry
                // it is a content swap at rest.
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
        }
    }
}

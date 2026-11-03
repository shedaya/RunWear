package com.runwear.app.weariverun

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * RW-25: "Wear I've Run" — the collection mini bar's state model,
 * narration formatter, and translucency policy.
 *
 * The collection feature itself is NOT built anywhere in the app yet:
 * there is no collection store, no add-to-collection action, no
 * entries. This build therefore ships the bar in its PRE-ADD state
 * only — [WearIveRunBarStateHolder] can only ever yield
 * [WearIveRunBarState.PreAdd], and the dock in HeroMainScreen renders
 * nothing for PreAdd. The model, formatter, and fully-styled composable
 * (WearIveRunMiniBar.kt) are in place so the collection feature, when
 * it lands, only has to start feeding real states.
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

/** The bar's display states. Only [PreAdd] is reachable in this build. */
sealed interface WearIveRunBarState {

    /** Nothing added yet — the bar does not render at all. */
    data object PreAdd : WearIveRunBarState

    /** Mini state: wordmark + one narration line + quiet icons. */
    data class Mini(
        val narration: WearIveRunNarration,
        val entry: WearIveRunEntry
    ) : WearIveRunBarState

    /**
     * Full expression: expanded (also the end-of-scroll-expanded and
     * cue-moment presentations) — thumbnail, entry data, quiet Remove,
     * fully opaque background.
     */
    data class Expanded(
        val narration: WearIveRunNarration,
        val entry: WearIveRunEntry
    ) : WearIveRunBarState
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

    /** Whether [state] counts as full expression for the alpha rule. */
    fun isFullExpression(state: WearIveRunBarState): Boolean =
        state is WearIveRunBarState.Expanded
}

/**
 * RW-26: the bar's state holder, now driven by the real collection.
 * It starts at [WearIveRunBarState.PreAdd] (absent before the first
 * entry exists) and thereafter reflects the current entry — today's
 * when there is one, else the most recent. A creation or a same-day
 * replacement (the day's last settled version changing) increments
 * [cueCount] exactly once: the dock answers with one gentle pulse +
 * soft haptic and an expansion, then goes quiet.
 */
class WearIveRunBarStateHolder {

    private val _state = MutableStateFlow<WearIveRunBarState>(WearIveRunBarState.PreAdd)
    val state: StateFlow<WearIveRunBarState> = _state.asStateFlow()

    private val _cueCount = MutableStateFlow(0)
    val cueCount: StateFlow<Int> = _cueCount.asStateFlow()

    private var expanded = false
    private var current: WearIveRunDayEntry? = null

    /** The entry the bar currently presents (today's, else latest). */
    fun currentEntry(): WearIveRunDayEntry? = current

    fun onEntriesChanged(entries: List<WearIveRunDayEntry>, today: java.time.LocalDate) {
        val next = entries.firstOrNull { it.date == today }
            ?: entries.maxByOrNull { it.date }
        if (next == null) {
            current = null
            _state.value = WearIveRunBarState.PreAdd
            return
        }
        val previous = current
        val isCreation = previous == null ||
            previous.date != next.date || previous.place != next.place
        val isUpdate = !isCreation && previous != next
        current = next
        // WS-4 (D-08): narration content is built from the SIMPLE
        // place, the narration day form ("Now", never the collection's
        // "Today"), and — for updates — the entry's REAL payload, its
        // first kit item. The place is normalized at this seam too, so
        // no raw geocode string can reach the line from any source.
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(next.place).display
        val dayForm = WearIveRunCollectionLogic.narrationDayForm(next.date, today)
        val firstItem = next.items.firstOrNull()?.takeIf { it.isNotBlank() }
        val narration = when {
            isUpdate && firstItem != null ->
                WearIveRunNarrationFormatter.update(firstItem)
            isUpdate ->
                WearIveRunNarrationFormatter.update("$simplePlace, $dayForm")
            else -> WearIveRunNarrationFormatter.added(simplePlace, dayForm)
        }
        val barEntry = WearIveRunEntry(
            place = simplePlace,
            timeLabel = dayForm,
            payload = firstItem,
            thumbnailUrl = next.heroRef,
            feelsLikeF = next.feelsLikeF,
            itemCount = next.items.size
        )
        _state.value = if (expanded) {
            WearIveRunBarState.Expanded(narration, barEntry)
        } else {
            WearIveRunBarState.Mini(narration, barEntry)
        }
        if (isCreation || isUpdate) {
            _cueCount.value = _cueCount.value + 1
        }
    }

    fun setExpanded(value: Boolean, today: java.time.LocalDate) {
        expanded = value
        val entry = current ?: return
        // WS-4: same content construction as onEntriesChanged —
        // simple place, narration day form, real payload fields.
        val simplePlace = com.runwear.shared.domain.model.SimplePlace
            .fromRawDisplayString(entry.place).display
        val dayForm = WearIveRunCollectionLogic.narrationDayForm(entry.date, today)
        val barEntry = WearIveRunEntry(
            place = simplePlace,
            timeLabel = dayForm,
            payload = entry.items.firstOrNull(),
            thumbnailUrl = entry.heroRef,
            feelsLikeF = entry.feelsLikeF,
            itemCount = entry.items.size
        )
        val narration = (_state.value as? WearIveRunBarState.Mini)?.narration
            ?: (_state.value as? WearIveRunBarState.Expanded)?.narration
            ?: WearIveRunNarrationFormatter.added(simplePlace, dayForm)
        _state.value = if (value) {
            WearIveRunBarState.Expanded(narration, barEntry)
        } else {
            WearIveRunBarState.Mini(narration, barEntry)
        }
    }
}

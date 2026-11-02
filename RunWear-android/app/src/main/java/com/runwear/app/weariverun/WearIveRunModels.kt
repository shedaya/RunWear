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
    val place: String,
    val timeLabel: String,
    /** The variable payload of an "Update to …" narration, if any. */
    val payload: String? = null,
    val thumbnailUrl: String? = null
)

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
 * - MINI state: the background is TRANSLUCENT — sheet surface at ~78%
 *   alpha in dark, ~85% in light (the stronger light tint keeps the
 *   line holding over light content).
 * - FULL EXPRESSION (expanded / end-of-scroll-expanded / cue moment):
 *   the background is FULLY OPAQUE sheet surface.
 * - Text and icons always render at 100% opacity regardless — only
 *   the background takes the alpha.
 *
 * Pure — unit-tested.
 */
object WearIveRunMiniBarPolicy {

    const val MINI_ALPHA_DARK = 0.78f
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
 * The bar's state holder for this build. The collection is not built,
 * so the holder is created holding [WearIveRunBarState.PreAdd] and
 * exposes no way to leave it — the dock consequently renders nothing.
 * When the collection lands, this holder is where its states get fed.
 */
class WearIveRunBarStateHolder {

    private val _state = MutableStateFlow<WearIveRunBarState>(WearIveRunBarState.PreAdd)
    val state: StateFlow<WearIveRunBarState> = _state.asStateFlow()
}

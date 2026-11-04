package com.runwear.app.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * WS-9 fix (P-2) — the §1.7 haptic map, as code. §1.7's table is the
 * COMPLETE list of haptic events in the app; everything not in it is
 * silent (in particular: ordinary taps on chrome, sheet travel,
 * scrolling, expand/collapse, and theme changes carry no haptic).
 * Every `performHapticFeedback` call in the app names one of these
 * constants, so the map has exactly one place to audit — and
 * `HapticMapPinTest` pins both the values and the call-site census.
 *
 * The Android column of §1.7 names effect CLASSES ("selection-class
 * tick", "light tick", "confirm-class effect", "long-press").
 * Compose UI exposes exactly two pre-built effects —
 * [HapticFeedbackType.TextHandleMove] (the generic tick) and
 * [HapticFeedbackType.LongPress] — so every tick class maps to
 * TextHandleMove (the mapping the pre-rebuild surfaces already used
 * for the cue and onboarding selections) and the one specified
 * long-press maps to LongPress. The classes stay distinct constants
 * so a future Compose version with richer effects can split them
 * without touching a call site.
 */
object HapticSpec {

    /** §1.7: Wear I've Run creation / user-caused update cue —
     * "light confirm-class tick". Fires once, at the cue pulse. */
    val CUE_TICK: HapticFeedbackType = HapticFeedbackType.TextHandleMove

    /** §1.7: element trash / recents removal — "light tick". */
    val LIGHT_TICK: HapticFeedbackType = HapticFeedbackType.TextHandleMove

    /** §1.7: place selected / preference / day / hour selection /
     * a capture check turning passing — "selection-class tick". */
    val SELECTION: HapticFeedbackType = HapticFeedbackType.TextHandleMove

    /** §1.7: first-look reveal — "confirm-class effect". Fires once,
     * as the photo lands. */
    val CONFIRM: HapticFeedbackType = HapticFeedbackType.TextHandleMove

    /** §1.7: the Android recents contextual bar's long-press —
     * "long-press". This is the ONLY long-press haptic in the app:
     * a tap never fires it (the pre-fix GlassButton fired it on
     * every tap of the location pill and Settings button, which
     * §1.7 assigns no haptic at all — WS-9 P-2). */
    val LONG_PRESS: HapticFeedbackType = HapticFeedbackType.LongPress
}

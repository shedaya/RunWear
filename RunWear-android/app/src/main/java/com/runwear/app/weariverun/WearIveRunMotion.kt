package com.runwear.app.weariverun

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.min

/**
 * GEN-2 (motion decision, Nov 4 2026): the Wear I've Run element's
 * collapse <-> expand transition — the Android half of the decision
 * (§4's Android table, §2's choreography, §5's scroll rules, §6's
 * reduced motion, §7's guardrails).
 *
 * ONE [androidx.compose.animation.core.Animatable] progress value
 * (0 = MINI, 1 = EXPANDED) drives four properties together — height,
 * corner radius, background tint alpha, and expanded-content
 * opacity — each a pure function of that one value below. There are
 * no per-property animations to desynchronize (§7.6).
 *
 * Per-driver specs (§3/§4):
 * - CUE entry: spring(0.80, 400) — the ONLY overshoot in the app,
 *   capped at <= 3% of height travel and <= 3 dp, exactly one settle;
 *   the pulse + soft haptic fire AT the settle (in the element).
 * - TAP expand: spring(0.92, 400) — no (visible) overshoot.
 * - End-of-scroll expand: tween(300 ms, EmphasizedDecelerate) —
 *   no overshoot ever.
 * - Collapse (all): tween(280 ms, EmphasizedAccelerate) — never
 *   overshoots.
 *
 * Duration windows are gates (§4): cue settles <= 500 ms, tap
 * <= 450 ms, scroll <= 340 ms, collapse <= 320 ms.
 */
enum class WearTransitionDriver { CUE, TAP, SCROLL, COLLAPSE }

object WearIveRunMotion {

    // -- Duration gates (§4) --
    const val CUE_SETTLE_WINDOW_MS = 500
    const val TAP_SETTLE_WINDOW_MS = 450
    const val SCROLL_SETTLE_WINDOW_MS = 340
    const val COLLAPSE_SETTLE_WINDOW_MS = 320

    /** §6: the reduced-motion replacement is a crossfade <= 150 ms. */
    const val REDUCED_MOTION_MAX_MS = 150

    // -- Geometry tokens --
    const val MINI_RADIUS_DP = 20f
    const val EXPANDED_RADIUS_DP = 20f

    /** §2: expanded-only content fades over this progress window. */
    const val CONTENT_WINDOW_START = 0.45f
    const val CONTENT_WINDOW_END = 1.00f

    /** §2: expanded-only content drifts up at most this far (dp). */
    const val CONTENT_DRIFT_DP = 4f

    /** §7.1: the cue's overshoot caps — 3% of travel, 3 dp absolute. */
    const val OVERSHOOT_FRACTION_CAP = 0.03f
    const val OVERSHOOT_DP_CAP = 3f

    /** M3 emphasized-decelerate: cubic-bezier(0.05, 0.7, 0.1, 1.0). */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** M3 emphasized-accelerate: cubic-bezier(0.3, 0.0, 0.8, 0.15). */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** The one spec a driver animates the progress value with (§4). */
    fun specFor(driver: WearTransitionDriver): AnimationSpec<Float> = when (driver) {
        WearTransitionDriver.CUE -> spring(
            dampingRatio = 0.80f,
            stiffness = Spring.StiffnessMediumLow // 400
        )
        WearTransitionDriver.TAP -> spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow // 400
        )
        WearTransitionDriver.SCROLL -> tween(
            durationMillis = 300,
            easing = EmphasizedDecelerate
        )
        WearTransitionDriver.COLLAPSE -> tween(
            durationMillis = 280,
            easing = EmphasizedAccelerate
        )
    }

    /** §6: reduced motion — one short tween, used as a crossfade. */
    fun reducedSpec(): AnimationSpec<Float> =
        tween(durationMillis = REDUCED_MOTION_MAX_MS)

    /** The gate a driver's settle is measured against (§4). */
    fun durationWindowMs(driver: WearTransitionDriver): Int = when (driver) {
        WearTransitionDriver.CUE -> CUE_SETTLE_WINDOW_MS
        WearTransitionDriver.TAP -> TAP_SETTLE_WINDOW_MS
        WearTransitionDriver.SCROLL -> SCROLL_SETTLE_WINDOW_MS
        WearTransitionDriver.COLLAPSE -> COLLAPSE_SETTLE_WINDOW_MS
    }

    /** §6: Android's reduced-motion signal is animator duration scale 0. */
    fun isReducedMotion(animatorDurationScale: Float): Boolean =
        animatorDurationScale == 0f

    /**
     * Background tint alpha as a function of progress (§2): the
     * settled MINI tint (85% light / 82% dark) at 0, fully opaque
     * at 1, linear between, clamped at 1.00 — so the cue spring's
     * overshoot expresses itself in height alone, never in alpha.
     */
    fun backgroundAlpha(progress: Float, darkTheme: Boolean): Float {
        val mini = WearIveRunMiniBarPolicy.backgroundAlpha(
            isFullExpression = false, darkTheme = darkTheme
        )
        val p = progress.coerceIn(0f, 1f)
        return mini + (WearIveRunMiniBarPolicy.FULL_ALPHA - mini) * p
    }

    /** Corner radius as a function of progress (§2): linear. */
    fun cornerRadiusDp(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return MINI_RADIUS_DP + (EXPANDED_RADIUS_DP - MINI_RADIUS_DP) * p
    }

    /**
     * Expanded-only content opacity (§2): windowed — 0 until
     * progress 0.45, ramping to 1 at 1.00 on expand; on collapse the
     * same function ramps it out over 1.00 -> 0.55 of the reverse
     * travel, so closing text is gone before the card visibly shrinks.
     */
    fun expandedContentAlpha(progress: Float): Float {
        val span = CONTENT_WINDOW_END - CONTENT_WINDOW_START
        return ((progress - CONTENT_WINDOW_START) / span).coerceIn(0f, 1f)
    }

    /** Expanded-only content's upward drift (§2): +4 dp -> 0 as it fades in. */
    fun expandedContentDriftDp(progress: Float): Float =
        CONTENT_DRIFT_DP * (1f - expandedContentAlpha(progress))

    /** §7.1: the absolute overshoot cap for a given height travel (dp). */
    fun overshootCapDp(heightTravelDp: Float): Float =
        min(OVERSHOOT_FRACTION_CAP * heightTravelDp, OVERSHOOT_DP_CAP)
}

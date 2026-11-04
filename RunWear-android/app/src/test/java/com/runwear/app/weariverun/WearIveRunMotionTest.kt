package com.runwear.app.weariverun

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-2 (motion decision, Nov 4 2026) — the Android half's gates,
 * encoded where the harness allows:
 *
 * - the per-driver specs are EXACTLY §4's Android table (cue spring
 *   0.80/400, tap spring 0.92/400, scroll tween 300 ms emphasized-
 *   decelerate, collapse tween 280 ms emphasized-accelerate);
 * - the four properties are pure functions of the one progress value
 *   (alpha endpoints + clamp, content window, drift, radius);
 * - the duration windows are verified by SIMULATING the springs on
 *   a manual frame clock: cue settles <= 500 ms with overshoot
 *   <= 3% and exactly one settle peak; tap settles <= 450 ms with
 *   no visible overshoot; the tweens never overshoot (their easing
 *   output stays inside [0, 1] and monotonic);
 * - reduced motion: scale 0 is the signal, the replacement spec is
 *   a <= 150 ms tween.
 */
class WearIveRunMotionTest {

    // ---- §4 spec table ----

    @Test
    fun `cue and tap specs are the decided springs`() {
        val cue = WearIveRunMotion.specFor(WearTransitionDriver.CUE) as SpringSpec<Float>
        assertEquals(0.80f, cue.dampingRatio, 0.001f)
        assertEquals(400f, cue.stiffness, 0.001f)
        val tap = WearIveRunMotion.specFor(WearTransitionDriver.TAP) as SpringSpec<Float>
        assertEquals(0.92f, tap.dampingRatio, 0.001f)
        assertEquals(400f, tap.stiffness, 0.001f)
    }

    @Test
    fun `scroll and collapse specs are the decided tweens`() {
        val scroll = WearIveRunMotion.specFor(WearTransitionDriver.SCROLL) as TweenSpec<Float>
        assertEquals(300, scroll.durationMillis)
        assertTrue(scroll.easing === WearIveRunMotion.EmphasizedDecelerate)
        val collapse =
            WearIveRunMotion.specFor(WearTransitionDriver.COLLAPSE) as TweenSpec<Float>
        assertEquals(280, collapse.durationMillis)
        assertTrue(collapse.easing === WearIveRunMotion.EmphasizedAccelerate)
        // The emphasized easings are the M3 cubic beziers of record.
        assertEquals(0f, WearIveRunMotion.EmphasizedDecelerate.transform(0f), 0.0001f)
        assertEquals(1f, WearIveRunMotion.EmphasizedDecelerate.transform(1f), 0.0001f)
        assertEquals(0f, WearIveRunMotion.EmphasizedAccelerate.transform(0f), 0.0001f)
        assertEquals(1f, WearIveRunMotion.EmphasizedAccelerate.transform(1f), 0.0001f)
    }

    @Test
    fun `tween easings never overshoot and are monotonic - scroll and collapse`() {
        for (easing in listOf(
            WearIveRunMotion.EmphasizedDecelerate,
            WearIveRunMotion.EmphasizedAccelerate
        )) {
            var previous = 0f
            for (i in 0..100) {
                val v = easing.transform(i / 100f)
                assertTrue("easing output in [0,1] at $i", v in 0f..1f)
                assertTrue("easing monotonic at $i", v >= previous - 0.0001f)
                previous = v
            }
        }
    }

    @Test
    fun `duration windows are the decision's gates`() {
        assertEquals(500, WearIveRunMotion.durationWindowMs(WearTransitionDriver.CUE))
        assertEquals(450, WearIveRunMotion.durationWindowMs(WearTransitionDriver.TAP))
        assertEquals(340, WearIveRunMotion.durationWindowMs(WearTransitionDriver.SCROLL))
        assertEquals(320, WearIveRunMotion.durationWindowMs(WearTransitionDriver.COLLAPSE))
        // The tween drivers fit inside their windows by construction.
        assertTrue(300 <= WearIveRunMotion.durationWindowMs(WearTransitionDriver.SCROLL))
        assertTrue(280 <= WearIveRunMotion.durationWindowMs(WearTransitionDriver.COLLAPSE))
    }

    // ---- §2: the four properties as functions of one progress ----

    @Test
    fun `background alpha runs mini tint to opaque, clamped at one`() {
        assertEquals(0.85f, WearIveRunMotion.backgroundAlpha(0f, darkTheme = false), 0.001f)
        assertEquals(0.82f, WearIveRunMotion.backgroundAlpha(0f, darkTheme = true), 0.001f)
        assertEquals(1.0f, WearIveRunMotion.backgroundAlpha(1f, darkTheme = false), 0.001f)
        assertEquals(1.0f, WearIveRunMotion.backgroundAlpha(1f, darkTheme = true), 0.001f)
        assertEquals(0.925f, WearIveRunMotion.backgroundAlpha(0.5f, darkTheme = false), 0.001f)
        // The cue spring's overshoot must NOT push alpha past 1.00.
        assertEquals(1.0f, WearIveRunMotion.backgroundAlpha(1.03f, darkTheme = false), 0.001f)
    }

    @Test
    fun `expanded content fades on the windowed ramp with at most 4dp drift`() {
        assertEquals(0f, WearIveRunMotion.expandedContentAlpha(0f), 0.001f)
        assertEquals(0f, WearIveRunMotion.expandedContentAlpha(0.45f), 0.001f)
        assertEquals(0.5f, WearIveRunMotion.expandedContentAlpha(0.725f), 0.001f)
        assertEquals(1f, WearIveRunMotion.expandedContentAlpha(1f), 0.001f)
        assertEquals(4f, WearIveRunMotion.expandedContentDriftDp(0f), 0.001f)
        assertEquals(0f, WearIveRunMotion.expandedContentDriftDp(1f), 0.001f)
        assertEquals(
            WearIveRunMotion.MINI_RADIUS_DP,
            WearIveRunMotion.cornerRadiusDp(0.37f),
            0.001f
        )
    }

    @Test
    fun `overshoot cap is 3 percent of travel and 3dp absolute`() {
        assertEquals(3f, WearIveRunMotion.overshootCapDp(200f), 0.001f)
        assertEquals(1.5f, WearIveRunMotion.overshootCapDp(50f), 0.001f)
        assertEquals(3f, WearIveRunMotion.overshootCapDp(100f), 0.001f)
    }

    // ---- §6: reduced motion ----

    @Test
    fun `reduced motion is scale zero with a 150ms replacement`() {
        assertTrue(WearIveRunMotion.isReducedMotion(0f))
        assertFalse(WearIveRunMotion.isReducedMotion(1f))
        assertFalse(WearIveRunMotion.isReducedMotion(0.5f))
        val spec = WearIveRunMotion.reducedSpec() as TweenSpec<Float>
        assertEquals(WearIveRunMotion.REDUCED_MOTION_MAX_MS, spec.durationMillis)
        assertTrue(spec.durationMillis <= 150)
    }

    // ---- Simulated springs: settle windows + overshoot guardrails ----

    private class ManualFrameClock : MonotonicFrameClock {
        var nanos: Long = 0L
            private set

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_666_667L
            return onFrame(nanos)
        }
    }

    /** Animate 0 -> 1 under a manual 60 fps clock; return (values, settle ms). */
    private fun simulate(driver: WearTransitionDriver): Pair<List<Float>, Long> {
        val clock = ManualFrameClock()
        val values = mutableListOf<Float>()
        runBlocking {
            withContext(clock) {
                Animatable(0f).animateTo(
                    1f,
                    WearIveRunMotion.specFor(driver)
                ) { values += value }
            }
        }
        return values to clock.nanos / 1_000_000L
    }

    @Test
    fun `cue spring settles inside 500ms with one capped overshoot settle`() {
        val (values, settleMs) = simulate(WearTransitionDriver.CUE)
        assertTrue("cue must settle (frames recorded)", values.isNotEmpty())
        assertTrue(
            "cue settle ${settleMs}ms must be <= 500ms",
            settleMs <= WearIveRunMotion.durationWindowMs(WearTransitionDriver.CUE)
        )
        val overshoot = (values.maxOrNull() ?: 1f) - 1f
        assertTrue(
            "cue overshoot $overshoot must be <= 3% of travel",
            overshoot <= WearIveRunMotion.OVERSHOOT_FRACTION_CAP + 0.001f
        )
        // Exactly one settle: one local peak above the target.
        var peaks = 0
        for (i in 1 until values.size - 1) {
            if (values[i] > 1.0005f && values[i] >= values[i - 1] && values[i] > values[i + 1]) {
                peaks++
            }
        }
        assertEquals("exactly one settle peak on cue entry", 1, peaks)
    }

    @Test
    fun `tap spring settles inside 450ms with no visible overshoot`() {
        val (values, settleMs) = simulate(WearTransitionDriver.TAP)
        assertTrue(values.isNotEmpty())
        assertTrue(
            "tap settle ${settleMs}ms must be <= 450ms",
            settleMs <= WearIveRunMotion.durationWindowMs(WearTransitionDriver.TAP)
        )
        val overshoot = (values.maxOrNull() ?: 1f) - 1f
        assertTrue(
            "tap overshoot $overshoot must be invisible (<= 0.5%)",
            overshoot <= 0.005f
        )
    }
}

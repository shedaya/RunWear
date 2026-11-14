package com.runwear.app.weariverun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-174 (Nov 14, 2026) — the §4.7 delete-exit split, pinned
 * at policy level (audit rows 39+40). Mirrors the iOS
 * WearIveRunExitPolicy of record. Written against the
 * RW-174 fix: at head 5a9e0b0 this policy does not exist
 * (the host layer and the morph each ran their own exit in
 * BOTH forms — fade+sink 300ms stacked on fade 160ms + fold
 * 400ms), so this suite fails to compile there and passes
 * with the fix; the rendered trajectories are pinned by
 * Rw174FixProbeTest.
 */
class Rw174ExitPolicyTest {

    @Test
    fun `durations - mini 300, expanded 560, reduce motion 200`() {
        assertEquals(
            300,
            WearIveRunExitPolicy.durationMs(
                fromExpanded = false, reduceMotion = false
            )
        )
        assertEquals(
            560,
            WearIveRunExitPolicy.durationMs(
                fromExpanded = true, reduceMotion = false
            )
        )
        assertEquals(
            200,
            WearIveRunExitPolicy.durationMs(
                fromExpanded = false, reduceMotion = true
            )
        )
        assertEquals(
            200,
            WearIveRunExitPolicy.durationMs(
                fromExpanded = true, reduceMotion = true
            )
        )
        assertEquals(160, WearIveRunExitPolicy.EXPANDED_FADE_MS)
        assertEquals(400, WearIveRunExitPolicy.EXPANDED_FOLD_MS)
    }

    @Test
    fun `host layer - sink and fade belong to the mini exit only`() {
        // MINI: full fade + the full 26dp sink at completion.
        assertEquals(
            0f,
            WearIveRunExitPolicy.hostAlpha(1f, false, false),
            0.001f
        )
        assertEquals(
            26f,
            WearIveRunExitPolicy.hostSinkDp(1f, false, false),
            0.001f
        )
        assertEquals(
            13f,
            WearIveRunExitPolicy.hostSinkDp(0.5f, false, false),
            0.001f
        )
        // EXPANDED: NO host sink and NO host container fade
        // at any progress.
        for (q in listOf(0f, 0.25f, 0.5f, 1f)) {
            assertEquals(
                1f,
                WearIveRunExitPolicy.hostAlpha(q, true, false),
                0.001f
            )
            assertEquals(
                0f,
                WearIveRunExitPolicy.hostSinkDp(q, true, false),
                0.001f
            )
        }
        // Reduce Motion: 200ms fade in both forms, never a sink.
        assertEquals(
            0f,
            WearIveRunExitPolicy.hostSinkDp(1f, false, true),
            0.001f
        )
        assertEquals(
            0f,
            WearIveRunExitPolicy.hostAlpha(1f, true, true),
            0.001f
        )
    }

    @Test
    fun `morph internal exit - runs for expanded and RM, never for a plain mini exit`() {
        assertFalse(
            WearIveRunExitPolicy.morphRunsInternalExit(
                fromExpanded = false, reduceMotion = false
            )
        )
        assertTrue(
            WearIveRunExitPolicy.morphRunsInternalExit(
                fromExpanded = true, reduceMotion = false
            )
        )
        assertTrue(
            WearIveRunExitPolicy.morphRunsInternalExit(
                fromExpanded = false, reduceMotion = true
            )
        )
        assertTrue(
            WearIveRunExitPolicy.morphRunsInternalExit(
                fromExpanded = true, reduceMotion = true
            )
        )
    }

    @Test
    fun `expanded curves - content fades inside 160ms, fold starts only after it`() {
        // q indexes the whole 560ms exit. At the fade's end
        // (q = 160/560) the content is gone and the height
        // is still whole; at the midpoint of the fold the
        // height is ~half; at q=1 both are done.
        val fadeEnd = 160f / 560f
        assertEquals(
            0f,
            WearIveRunExitPolicy.contentOpacity(fadeEnd, true, false),
            0.001f
        )
        assertEquals(
            1f,
            WearIveRunExitPolicy.heightScale(fadeEnd, true, false),
            0.001f
        )
        assertEquals(
            0.5f,
            WearIveRunExitPolicy.heightScale(
                (fadeEnd + 1f) / 2f, true, false
            ),
            0.01f
        )
        assertEquals(
            0f,
            WearIveRunExitPolicy.heightScale(1f, true, false),
            0.001f
        )
        // MINI: the height is RELEASED — the scale never
        // leaves 1 (no fold at any progress).
        for (q in listOf(0f, 0.5f, 1f)) {
            assertEquals(
                1f,
                WearIveRunExitPolicy.heightScale(q, false, false),
                0.001f
            )
        }
        // RM: height stays (snaps at removal) in both forms.
        assertEquals(
            1f,
            WearIveRunExitPolicy.heightScale(1f, true, true),
            0.001f
        )
    }

    @Test
    fun `pine ring tokens - blooms and decays across the 900ms cue`() {
        // Spec §4.1 + the ported retired PineRing: alpha
        // 0.45 -> 0, scale 1 -> 1.05, stroke 2dp, radius the
        // surface's constant 16dp.
        assertEquals(
            WearIveRunMorphPolicy.PINE_RING_PEAK_ALPHA,
            WearIveRunMorphPolicy.pineRingAlpha(0f),
            0.001f
        )
        assertEquals(
            0f,
            WearIveRunMorphPolicy.pineRingAlpha(1f),
            0.001f
        )
        assertEquals(
            1f,
            WearIveRunMorphPolicy.pineRingScale(0f),
            0.001f
        )
        assertEquals(
            WearIveRunMorphPolicy.PINE_RING_BLOOM_SCALE,
            WearIveRunMorphPolicy.pineRingScale(1f),
            0.001f
        )
        assertEquals(2f, WearIveRunMorphPolicy.PINE_RING_WIDTH_DP, 0.001f)
        assertEquals(16f, WearIveRunMorphPolicy.RADIUS_DP, 0.001f)
        assertEquals(900, WearIveRunDockPolicy.CUE_PULSE_MS)
    }
}

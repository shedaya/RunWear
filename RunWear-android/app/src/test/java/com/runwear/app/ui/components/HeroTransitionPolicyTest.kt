package com.runwear.app.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-16.27: the hero swap transition rule — a person change cuts, a
 * same-layer refresh may crossfade. The pre-fix hero crossfaded
 * every swap, so the personal photo ghosted over a pool stranger
 * for 500 ms (RW-16.26).
 */
class HeroTransitionPolicyTest {

    @Test
    fun `the first hero in a view may fade in`() {
        assertTrue(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = null, newIsPersonal = false))
        assertTrue(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = null, newIsPersonal = true))
    }

    @Test
    fun `pool to personal cuts`() {
        assertFalse(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = false, newIsPersonal = true))
    }

    @Test
    fun `personal to pool cuts`() {
        assertFalse(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = true, newIsPersonal = false))
    }

    @Test
    fun `same-layer refreshes keep the crossfade`() {
        assertTrue(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = false, newIsPersonal = false))
        assertTrue(HeroTransitionPolicy.shouldCrossfade(previousIsPersonal = true, newIsPersonal = true))
    }
}

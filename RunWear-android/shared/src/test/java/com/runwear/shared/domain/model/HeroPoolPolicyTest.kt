package com.runwear.shared.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hero-pool quality gates (2026-10-27): review gating, the curated
 * rotation floor, and prompt hardening. These are the rules that stop an
 * unreviewed generated image — like the back-view runner with the arm
 * bent across his back — from being served on repeat.
 */
class HeroPoolPolicyTest {

    @Test
    fun `only approved and legacy rows are servable`() {
        assertTrue(HeroPoolPolicy.isServable("approved"))
        assertTrue(HeroPoolPolicy.isServable("APPROVED"))
        // Pre-migration rows have no review_status column value.
        assertTrue(HeroPoolPolicy.isServable(null))
        assertFalse(HeroPoolPolicy.isServable("pending"))
        assertFalse(HeroPoolPolicy.isServable("rejected"))
        assertFalse(HeroPoolPolicy.isServable("anything-else"))
    }

    @Test
    fun `floor covers clear and cloudy at dawn midday dusk for every bracket`() {
        for (bracket in TempBracket.entries) {
            for (weather in listOf(HeroWeatherCondition.CLEAR, HeroWeatherCondition.CLOUDY)) {
                for (time in listOf(TimeOfDay.DAWN, TimeOfDay.MIDDAY, TimeOfDay.DUSK)) {
                    assertTrue("$bracket/$weather/$time", HeroPoolPolicy.isFloorCell(bracket, weather, time))
                }
            }
        }
    }

    @Test
    fun `floor excludes night rain and snow`() {
        for (bracket in TempBracket.entries) {
            assertFalse(HeroPoolPolicy.isFloorCell(bracket, HeroWeatherCondition.CLEAR, TimeOfDay.NIGHT))
            assertFalse(HeroPoolPolicy.isFloorCell(bracket, HeroWeatherCondition.CLOUDY, TimeOfDay.NIGHT))
            assertFalse(HeroPoolPolicy.isFloorCell(bracket, HeroWeatherCondition.RAIN, TimeOfDay.MIDDAY))
            assertFalse(HeroPoolPolicy.isFloorCell(bracket, HeroWeatherCondition.SNOW, TimeOfDay.DAWN))
        }
    }

    @Test
    fun `floor candidate merges once and never duplicates`() {
        val pool = listOf("https://img/a", "https://img/b")
        assertEquals(
            listOf("https://img/a", "https://img/b", "https://img/curated"),
            HeroPoolPolicy.mergeFloorCandidate(pool, "https://img/curated")
        )
        assertEquals(pool, HeroPoolPolicy.mergeFloorCandidate(pool, "https://img/a"))
        assertEquals(pool, HeroPoolPolicy.mergeFloorCandidate(pool, null))
        assertEquals(
            listOf("https://img/curated"),
            HeroPoolPolicy.mergeFloorCandidate(emptyList(), "https://img/curated")
        )
    }

    @Test
    fun `quality suffix is appended once and carries the anatomy constraints`() {
        val prompt = "A male runner mid-stride along a waterfront boardwalk."
        val hardened = HeroPoolPolicy.ensureQualitySuffix(prompt)
        assertTrue(hardened.startsWith(prompt))
        assertTrue(hardened.contains("anatomically correct"))
        assertTrue(hardened.contains("never reaching across the back"))
        assertTrue(hardened.contains("five correct fingers"))
        assertTrue(hardened.contains("from behind"))
        // Idempotent: re-queueing an already-hardened prompt must not stack it.
        assertEquals(hardened, HeroPoolPolicy.ensureQualitySuffix(hardened))
    }
}

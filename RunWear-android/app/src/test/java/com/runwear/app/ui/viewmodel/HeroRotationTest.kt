package com.runwear.app.ui.viewmodel

import com.runwear.shared.domain.model.HeroCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-16.27: the hero refresh rotation. The personal hero leads the
 * rotation when one serves for the cell, so the refresh control
 * lives in the personal state too (pre-RW-16.27 the overlay emptied
 * the candidate list and the control vanished exactly then). With
 * no personal hero the rotation is the pre-existing pool-only one,
 * unchanged.
 */
class HeroRotationTest {

    private val poolA = HeroCandidate(imageUrl = "https://pool/a", imageRowId = "row-a")
    private val poolB = HeroCandidate(imageUrl = "https://pool/b", imageRowId = "row-b")
    private val personalUrl = "https://signed/personal"

    @Test
    fun `pool-only rotation matches the legacy cycle order`() {
        val rotation = heroRotation(listOf(poolA, poolB), personalImageUrl = null)
        assertEquals(2, rotation.size)
        assertEquals(poolB.imageUrl, nextHeroInRotation(rotation, poolA.imageUrl)?.imageUrl)
        // Wraps at the end.
        assertEquals(poolA.imageUrl, nextHeroInRotation(rotation, poolB.imageUrl)?.imageUrl)
    }

    @Test
    fun `the personal hero leads the rotation`() {
        val rotation = heroRotation(listOf(poolA, poolB), personalImageUrl = personalUrl)
        assertEquals(3, rotation.size)
        assertTrue(rotation[0].isPersonal)
        assertEquals(personalUrl, rotation[0].imageUrl)
        assertEquals(listOf(0, 1), rotation.drop(1).map { it.candidateIndex })
    }

    @Test
    fun `cycling from the personal photo steps into the pool and wraps back`() {
        val rotation = heroRotation(listOf(poolA, poolB), personalImageUrl = personalUrl)
        assertEquals(poolA.imageUrl, nextHeroInRotation(rotation, personalUrl)?.imageUrl)
        assertEquals(poolB.imageUrl, nextHeroInRotation(rotation, poolA.imageUrl)?.imageUrl)
        val wrapped = nextHeroInRotation(rotation, poolB.imageUrl)
        assertEquals(personalUrl, wrapped?.imageUrl)
        assertTrue(wrapped?.isPersonal == true)
    }

    @Test
    fun `a personal hero plus one pool photo still cycles`() {
        // The personal state must offer the refresh control even when
        // the cell has a single pool candidate — two photos total.
        val rotation = heroRotation(listOf(poolA), personalImageUrl = personalUrl)
        assertEquals(poolA.imageUrl, nextHeroInRotation(rotation, personalUrl)?.imageUrl)
        assertEquals(personalUrl, nextHeroInRotation(rotation, poolA.imageUrl)?.imageUrl)
    }

    @Test
    fun `a single-entry rotation has nothing to cycle to`() {
        assertNull(
            nextHeroInRotation(heroRotation(listOf(poolA), personalImageUrl = null), poolA.imageUrl)
        )
        assertNull(
            nextHeroInRotation(heroRotation(emptyList(), personalImageUrl = personalUrl), personalUrl)
        )
        assertNull(nextHeroInRotation(heroRotation(emptyList(), personalImageUrl = null), null))
    }

    @Test
    fun `a photo outside the rotation starts at the first entry`() {
        val rotation = heroRotation(listOf(poolA, poolB), personalImageUrl = personalUrl)
        val next = nextHeroInRotation(rotation, "https://pool/stale")
        assertEquals(personalUrl, next?.imageUrl)
    }

    // ── RW-95 (Nov 6, 2026): serve stability — one stock serve
    // per cell per view; the pool's second pick for the same cell
    // never swaps the displayed stock photo mid-view.

    @Test
    fun `a displayed stock serve of the same cell is held - RW-95`() {
        assertTrue(
            HeroServeStabilityPolicy.holdsDisplayedStock(
                displayedUrl = "file:///android_asset/hero_fallback/a.jpg",
                displayedIsPersonal = false,
                displayedCombinationId = "RAIN_WARM_DAY",
                incomingCombinationId = "RAIN_WARM_DAY"
            )
        )
    }

    @Test
    fun `a different cell always replaces - RW-95`() {
        assertEquals(
            false,
            HeroServeStabilityPolicy.holdsDisplayedStock(
                displayedUrl = "file:///android_asset/hero_fallback/a.jpg",
                displayedIsPersonal = false,
                displayedCombinationId = "RAIN_WARM_DAY",
                incomingCombinationId = "CLEAR_MILD_DAY"
            )
        )
    }

    @Test
    fun `a personal serve is never held against - RW-95`() {
        assertEquals(
            false,
            HeroServeStabilityPolicy.holdsDisplayedStock(
                displayedUrl = "https://signed/personal",
                displayedIsPersonal = true,
                displayedCombinationId = "RAIN_WARM_DAY",
                incomingCombinationId = "RAIN_WARM_DAY"
            )
        )
    }

    @Test
    fun `nothing displayed or an unlabelled serve is never held - RW-95`() {
        assertEquals(
            false,
            HeroServeStabilityPolicy.holdsDisplayedStock(
                displayedUrl = null,
                displayedIsPersonal = false,
                displayedCombinationId = "RAIN_WARM_DAY",
                incomingCombinationId = "RAIN_WARM_DAY"
            )
        )
        assertEquals(
            false,
            HeroServeStabilityPolicy.holdsDisplayedStock(
                displayedUrl = "https://pool/a",
                displayedIsPersonal = false,
                displayedCombinationId = null,
                incomingCombinationId = "RAIN_WARM_DAY"
            )
        )
    }
}

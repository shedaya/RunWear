package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-14 client policy tests.
 *
 * - [PersonalHeroCachePolicy]: the versioned cache marker rule
 *   (acceptance 16) — a replaced image MUST replace on screen.
 * - [MakeItMeUpsellRules]: the 6th-cell upsell show rules (spec §5.2).
 * - [PhotoQualityPolicy]: the on-device photo check order (spec §2.3).
 */
class PersonalHeroPoliciesTest {

    // ── Versioned cache (acceptance 16) ─────────────────────────────

    private val now = 1_700_000_000_000L

    @Test
    fun `server not-found serves the pool hero`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(3, "https://signed/old", now - 1_000),
            lookup = HeroLookup(found = false, imageUrl = null, version = 0, heroId = null),
            nowMs = now
        )
        assertEquals(PersonalHeroCachePolicy.Decision.NoPersonalHero, decision)
    }

    @Test
    fun `a version bump always wins over a fresh cached URL`() {
        // The acceptance-16 trap: the cell's hero was REPLACED (photo
        // regeneration, variant added), the cached signed URL is still
        // inside its freshness window — the stale image must NOT win.
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(1, "https://signed/v1", now - 60_000),
            lookup = HeroLookup(found = true, imageUrl = "https://signed/v2", version = 2, heroId = "h2"),
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/v2", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    @Test
    fun `same version and fresh serves the cached URL`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(2, "https://signed/cached", now - 60_000),
            lookup = HeroLookup(found = true, imageUrl = "https://signed/new-signed", version = 2, heroId = "h2"),
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/cached", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    @Test
    fun `same version but stale cached URL re-signs from the server`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(
                2, "https://signed/expired",
                now - PersonalHeroCachePolicy.URL_FRESH_MS - 1_000
            ),
            lookup = HeroLookup(found = true, imageUrl = "https://signed/fresh", version = 2, heroId = "h2"),
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/fresh", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    @Test
    fun `no cache serves the server URL`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = null,
            lookup = HeroLookup(found = true, imageUrl = "https://signed/first", version = 1, heroId = "h1"),
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/first", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    // ── Upsell rules (spec §5.2) ────────────────────────────────────

    private fun upsell(
        personalizationOn: Boolean = true,
        entitled: Boolean = false,
        freeGenerationsUsed: Int = 5,
        cellHasPersonalHero: Boolean = false,
        cellGenerationPending: Boolean = false,
        combinationId: String = "MALE_CLEAR_WARM_DAWN_aaaaaaaa",
        lastDismissedAtMs: Long? = null,
        lastDismissedCell: String? = null,
        nowMs: Long = now
    ) = MakeItMeUpsellRules.shouldShow(
        personalizationOn, entitled, freeGenerationsUsed, cellHasPersonalHero,
        cellGenerationPending, combinationId, lastDismissedAtMs, lastDismissedCell, nowMs
    )

    @Test
    fun `upsell shows on the first exhausted new cell`() {
        assertTrue(upsell())
    }

    @Test
    fun `upsell hidden before opt-in`() {
        assertFalse(upsell(personalizationOn = false))
    }

    @Test
    fun `upsell hidden for subscribers`() {
        assertFalse(upsell(entitled = true))
    }

    @Test
    fun `upsell hidden while allowance remains`() {
        assertFalse(upsell(freeGenerationsUsed = 4))
    }

    @Test
    fun `upsell hidden when the cell already has a personal hero`() {
        assertFalse(upsell(cellHasPersonalHero = true))
    }

    @Test
    fun `upsell hidden while a generation is pending`() {
        assertFalse(upsell(cellGenerationPending = true))
    }

    @Test
    fun `upsell hidden on the dismissed cell`() {
        assertFalse(
            upsell(
                lastDismissedAtMs = now - 30L * 24 * 3600 * 1000,
                lastDismissedCell = "MALE_CLEAR_WARM_DAWN_aaaaaaaa"
            )
        )
    }

    @Test
    fun `upsell hidden within a week of any dismissal`() {
        assertFalse(
            upsell(
                lastDismissedAtMs = now - 3L * 24 * 3600 * 1000,
                lastDismissedCell = "MALE_RAIN_MILD_DAWN_bbbbbbbb"
            )
        )
    }

    @Test
    fun `upsell returns on a different cell after a week`() {
        assertTrue(
            upsell(
                lastDismissedAtMs = now - 8L * 24 * 3600 * 1000,
                lastDismissedCell = "MALE_RAIN_MILD_DAWN_bbbbbbbb"
            )
        )
    }

    // ── Photo quality policy (spec §2.3) ────────────────────────────

    @Test
    fun `dark photo rejected before face checks`() {
        assertEquals(
            PhotoRejection.TOO_DARK,
            PhotoQualityPolicy.verdict(
                PhotoQualityChecker.PhotoFacts(faceCount = 0, largestFaceWidthFraction = 0f, meanLuminance = 0.05f)
            )
        )
    }

    @Test
    fun `no detectable face rejected as unclear`() {
        assertEquals(
            PhotoRejection.FACE_UNCLEAR,
            PhotoQualityPolicy.verdict(
                PhotoQualityChecker.PhotoFacts(faceCount = 0, largestFaceWidthFraction = 0f, meanLuminance = 0.5f)
            )
        )
    }

    @Test
    fun `more than one person rejected`() {
        assertEquals(
            PhotoRejection.MULTIPLE_PEOPLE,
            PhotoQualityPolicy.verdict(
                PhotoQualityChecker.PhotoFacts(faceCount = 2, largestFaceWidthFraction = 0.4f, meanLuminance = 0.5f)
            )
        )
    }

    @Test
    fun `face filling the frame rejected as too close`() {
        assertEquals(
            PhotoRejection.TOO_CLOSE,
            PhotoQualityPolicy.verdict(
                PhotoQualityChecker.PhotoFacts(faceCount = 1, largestFaceWidthFraction = 0.9f, meanLuminance = 0.5f)
            )
        )
    }

    @Test
    fun `a good photo passes`() {
        assertNull(
            PhotoQualityPolicy.verdict(
                PhotoQualityChecker.PhotoFacts(faceCount = 1, largestFaceWidthFraction = 0.35f, meanLuminance = 0.5f)
            )
        )
    }
}

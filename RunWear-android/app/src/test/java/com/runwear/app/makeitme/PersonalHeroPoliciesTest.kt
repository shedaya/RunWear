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
 * - [BillingIdentityPolicy]: the RevenueCat ↔ Supabase identity link
 *   (acceptance 17).
 */
class PersonalHeroPoliciesTest {

    private val now = 1_700_000_000_000L

    // ── Versioned cache (acceptance 16) ─────────────────────────────

    @Test
    fun `server not-found serves the pool hero`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(3, "https://signed/old", now - 1_000),
            found = false,
            serverVersion = 3,
            serverUrl = null,
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
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/v2",
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/v2", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
        assertEquals(2, decision.version)
    }

    @Test
    fun `same version and fresh serves the cached URL`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(2, "https://signed/cached", now - 60_000),
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/new-signed",
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
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/fresh",
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/fresh", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    @Test
    fun `no cache serves the server URL`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = null,
            found = true,
            serverVersion = 1,
            serverUrl = "https://signed/first",
            nowMs = now
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("https://signed/first", (decision as PersonalHeroCachePolicy.Decision.Serve).imageUrl)
    }

    // ── Hero id threading (RW-16.27: the personal flag's address) ───

    @Test
    fun `a fresh serve carries the server hero id`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = null,
            found = true,
            serverVersion = 1,
            serverUrl = "https://signed/first",
            nowMs = now,
            serverHeroId = "hero-1"
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("hero-1", (decision as PersonalHeroCachePolicy.Decision.Serve).heroId)
    }

    @Test
    fun `a cached serve keeps the cached hero id`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(
                2, "https://signed/cached", now - 60_000, heroId = "hero-2"
            ),
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/new-signed",
            nowMs = now,
            serverHeroId = "hero-2"
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("hero-2", (decision as PersonalHeroCachePolicy.Decision.Serve).heroId)
    }

    @Test
    fun `a legacy cached entry adopts the server hero id on a fresh read`() {
        // Entries persisted before RW-16.27 carry no hero id; while
        // the cached URL is still served, the flag's address comes
        // from the same payload's hero_id.
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(2, "https://signed/cached", now - 60_000),
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/new-signed",
            nowMs = now,
            serverHeroId = "hero-2"
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("hero-2", (decision as PersonalHeroCachePolicy.Decision.Serve).heroId)
    }

    @Test
    fun `a version bump serves the new hero's id, never the replaced one's`() {
        val decision = PersonalHeroCachePolicy.decide(
            cached = PersonalHeroCachePolicy.CachedEntry(
                1, "https://signed/v1", now - 60_000, heroId = "hero-old"
            ),
            found = true,
            serverVersion = 2,
            serverUrl = "https://signed/v2",
            nowMs = now,
            serverHeroId = "hero-new"
        )
        assertTrue(decision is PersonalHeroCachePolicy.Decision.Serve)
        assertEquals("hero-new", (decision as PersonalHeroCachePolicy.Decision.Serve).heroId)
    }

    // ── Personal flag reasons (RW-16.27, iOS parity) ────────────────

    @Test
    fun `personal flag reasons mirror the backend vocabulary and iOS labels`() {
        // The codes are the flag_hero action's validated set; the
        // first label is the spec §4 control verbatim. Both must
        // match iOS's PersonalHeroFlagReason exactly.
        assertEquals(
            listOf("doesnt_look_like_me", "looks_broken", "inappropriate", "outfit_mismatch"),
            PersonalHeroFlagReason.entries.map { it.code }
        )
        assertEquals(
            listOf(
                "This doesn't look like me", "Looks broken",
                "Inappropriate", "Doesn't match the outfit"
            ),
            PersonalHeroFlagReason.entries.map { it.label }
        )
    }

    // ── Upsell rules (spec §5.2) ────────────────────────────────────

    private fun upsell(
        personalizationOn: Boolean = true,
        entitled: Boolean = false,
        freeGenerationsUsed: Int = 5,
        cellHasPersonalHero: Boolean = false,
        cellGenerationPending: Boolean = false,
        combinationId: String = "MALE_CLEAR_WARM_DAWN_aaaaaaaa",
        lastDismissedAtMs: Long = 0L,
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
            PhotoQualityPolicy.decide(faceCount = 0, largestFaceWidthFraction = 0f, meanLuminance = 0.05)
        )
    }

    @Test
    fun `no detectable face rejected as unclear`() {
        assertEquals(
            PhotoRejection.FACE_UNCLEAR,
            PhotoQualityPolicy.decide(faceCount = 0, largestFaceWidthFraction = 0f, meanLuminance = 0.5)
        )
    }

    @Test
    fun `more than one person rejected`() {
        assertEquals(
            PhotoRejection.MULTIPLE_PEOPLE,
            PhotoQualityPolicy.decide(faceCount = 2, largestFaceWidthFraction = 0.4f, meanLuminance = 0.5)
        )
    }

    @Test
    fun `face filling the frame rejected as too close`() {
        assertEquals(
            PhotoRejection.TOO_CLOSE,
            PhotoQualityPolicy.decide(faceCount = 1, largestFaceWidthFraction = 0.9f, meanLuminance = 0.5)
        )
    }

    @Test
    fun `a good photo passes`() {
        assertNull(
            PhotoQualityPolicy.decide(faceCount = 1, largestFaceWidthFraction = 0.35f, meanLuminance = 0.5)
        )
    }

    // ── RW-16.30 intake floor + frontality (Route 2 policy) ────────

    @Test
    fun `face under the height floor rejected as too small`() {
        assertEquals(
            PhotoRejection.FACE_TOO_SMALL,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.2f, meanLuminance = 0.5,
                largestFaceHeightFraction = 0.05f, faceYawDegrees = 0f
            )
        )
    }

    @Test
    fun `face at the bracketed pass height passes`() {
        // 59 px in the 768-normalized frame ≈ 0.077 — the full-body
        // record's smallest delivering face.
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.2f, meanLuminance = 0.5,
                largestFaceHeightFraction = 59f / 768f, faceYawDegrees = 5f
            )
        )
    }

    @Test
    fun `turned face rejected as turned away`() {
        assertEquals(
            PhotoRejection.TURNED_AWAY,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.3f, meanLuminance = 0.5,
                largestFaceHeightFraction = 0.3f, faceYawDegrees = -42f
            )
        )
    }

    @Test
    fun `unknown yaw never rejects on frontality`() {
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.3f, meanLuminance = 0.5,
                largestFaceHeightFraction = 0.3f, faceYawDegrees = null
            )
        )
    }

    // ── Billing identity link (acceptance 17) ───────────────────────

    private val supabaseUid = "381d4f7d-0000-4000-8000-000000000000"

    @Test
    fun `anonymous RevenueCat id must be linked to the Supabase uid`() {
        // The pre-link state: the SDK runs under its generated id, and
        // purchases under it are invisible to the backend (RW-16.12).
        assertTrue(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = "\$RCAnonymousID:0123456789abcdef0123456789abcdef",
                supabaseUserId = supabaseUid
            )
        )
    }

    @Test
    fun `already linked to the Supabase uid is a no-op`() {
        assertFalse(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = supabaseUid,
                supabaseUserId = supabaseUid
            )
        )
    }

    @Test
    fun `a different stale uid is re-linked`() {
        assertTrue(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = "381d4f7d-1111-4000-8000-000000000000",
                supabaseUserId = supabaseUid
            )
        )
    }

    @Test
    fun `unknown RevenueCat id links when a uid exists`() {
        assertTrue(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = null,
                supabaseUserId = supabaseUid
            )
        )
    }

    @Test
    fun `no Supabase uid never links`() {
        assertFalse(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = "\$RCAnonymousID:0123456789abcdef0123456789abcdef",
                supabaseUserId = null
            )
        )
        assertFalse(
            BillingIdentityPolicy.shouldLogIn(
                currentRcAppUserId = null,
                supabaseUserId = "  "
            )
        )
    }
}

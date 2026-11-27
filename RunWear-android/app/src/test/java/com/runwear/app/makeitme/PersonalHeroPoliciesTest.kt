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
            listOf("doesnt_look_like_me", "outfit_mismatch", "looks_broken"),
            PersonalHeroFlagReason.entries.map { it.code }
        )
        assertEquals(
            listOf(
                "This doesn't look like me", "Wrong outfit for the weather",
                "Something else"
            ),
            PersonalHeroFlagReason.entries.map { it.label }
        )
    }

    @Test
    fun `personal flag sheet codes equal the server contract in sheet order`() {
        // RW-62 correction: the labels are frame 45's settled design
        // and do not change, but the codes are the server contract
        // and must not change with them. The backend flag_hero
        // action (supabase/functions/request-personal-generation,
        // FLAG_REASONS) and the personal_hero_flags migration pin
        // {doesnt_look_like_me, looks_broken, inappropriate,
        // outfit_mismatch} and return 400 "Unknown flag reason"
        // otherwise. Mirroring iOS's documented mapping: the lead
        // files as doesnt_look_like_me, "Wrong outfit for the
        // weather" files as outfit_mismatch, and the residual
        // "Something else" files under the generic defect code
        // looks_broken. Sheet order is [lead, outfit, residual].
        assertEquals(
            listOf("doesnt_look_like_me", "outfit_mismatch", "looks_broken"),
            PersonalHeroFlagReason.entries.map { it.code }
        )
        assertEquals(
            listOf("doesnt_look_like_me", "outfit_mismatch", "looks_broken"),
            listOf(PersonalFlagSheetContent.headingReason.code) +
                PersonalFlagSheetContent.rowReasons.map { it.code }
        )
        assertEquals(
            "outfit_mismatch",
            PersonalHeroFlagReason.WRONG_OUTFIT.code
        )
        assertEquals(
            "looks_broken",
            PersonalHeroFlagReason.SOMETHING_ELSE.code
        )
    }

    // ── Upsell rules (spec §5.2) ────────────────────────────────────

    private fun upsell(
        personalizationOn: Boolean = true,
        entitled: Boolean = false,
        // RW-128 (Nov 7, 2026): the free allowance is 10 (was 5) —
        // the exhausted default follows the shared allowance.
        freeGenerationsUsed: Int = 10,
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
    fun `face count is judged before darkness (RW-26 F7 harmonized order)`() {
        // A dark photo with NO face reads as face-unclear first on BOTH
        // platforms now; darkness is judged only once exactly one face
        // is present.
        assertEquals(
            PhotoRejection.FACE_UNCLEAR,
            PhotoQualityPolicy.decide(faceCount = 0, largestFaceWidthFraction = 0f, meanLuminance = 0.05)
        )
        assertEquals(
            PhotoRejection.MULTIPLE_PEOPLE,
            PhotoQualityPolicy.decide(faceCount = 2, largestFaceWidthFraction = 0f, meanLuminance = 0.05)
        )
        assertEquals(
            PhotoRejection.TOO_DARK,
            PhotoQualityPolicy.decide(faceCount = 1, largestFaceWidthFraction = 0.35f, meanLuminance = 0.05)
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

    @Test
    fun `face too small is its own rejection class with the settled copy (RW-50)`() {
        // GEN-6 (RW-50): the gen-5 enum had NO face-too-small
        // class, so §10.4's settled copy could never render. A face
        // below the guided checks' own minimum fraction now
        // rejects as FACE_TOO_SMALL. (The fraction itself was
        // raised 0.18 → 0.30 by RW-180; this test pins the class
        // and the boundary via the constant, not the number.)
        assertEquals(
            PhotoRejection.FACE_TOO_SMALL,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.10f, meanLuminance = 0.5
            )
        )
        // At the threshold exactly: acceptable.
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1,
                largestFaceWidthFraction = GuidedSelfieChecks.MIN_FACE_WIDTH_FRACTION,
                meanLuminance = 0.5
            )
        )
        assertEquals(
            "Your face is small in that photo — A closer selfie works best — a mirror selfie is great too. Or let the camera guide you…",
            PhotoQualityPolicy.stringFor(PhotoRejection.FACE_TOO_SMALL)
        )
    }

    // ── RW-180 (Nov 26, 2026): the input-quality lines, grounded
    // in the calibration's four measured references. ─────────────

    @Test
    fun `the calibration's far-away reference now rejects as face too small (RW-180)`() {
        // The reference that produced the bad Make It Me batch:
        // 960x1280, face-width fraction 0.272, frame luminance
        // 0.405, face luminance 0.440 — it PASSED every old line
        // (0.18 face floor included). At the raised 0.30 floor it
        // rejects FACE_TOO_SMALL — and NOT too-low-resolution: its
        // 960 px short edge and 1.23 MP clear the resolution floor,
        // so the framing verdict is the one it earns.
        assertEquals(
            PhotoRejection.FACE_TOO_SMALL,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.272f,
                meanLuminance = 0.405, faceMeanLuminance = 0.440,
                imageShortEdgePx = 960, imagePixelCount = 960L * 1280L
            )
        )
    }

    @Test
    fun `the calibration's accepted references pass every RW-180 line`() {
        // Clara is the tightest accepted reference: fraction
        // 0.324, frame 0.446, face 0.351 (ratio 0.79), 1536x2048.
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.324f,
                meanLuminance = 0.446, faceMeanLuminance = 0.351,
                imageShortEdgePx = 1536, imagePixelCount = 1536L * 2048L
            )
        )
    }

    @Test
    fun `a backlit face rejects as too dark even in a bright frame (RW-180)`() {
        // Face mean 0.20 against a 0.50 frame (ratio 0.40): the
        // frame mean alone would have passed this photo.
        assertEquals(
            PhotoRejection.TOO_DARK,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.5, faceMeanLuminance = 0.20
            )
        )
        // An underexposed face in a dim-but-passing frame.
        assertEquals(
            PhotoRejection.TOO_DARK,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.17, faceMeanLuminance = 0.10
            )
        )
    }

    @Test
    fun `a well-framed but tiny source rejects as too low resolution (RW-180)`() {
        assertEquals(
            PhotoRejection.TOO_LOW_RESOLUTION,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.5,
                imageShortEdgePx = 640, imagePixelCount = 640L * 480L
            )
        )
        // At the floor exactly: acceptable.
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.5,
                imageShortEdgePx = PhotoQualityPolicy.MIN_IMAGE_SHORT_EDGE_PX,
                imagePixelCount = PhotoQualityPolicy.MIN_IMAGE_PIXELS
            )
        )
        assertEquals(
            MakeItMeStrings.C3J_REJECT_LOW_RESOLUTION,
            PhotoQualityPolicy.stringFor(PhotoRejection.TOO_LOW_RESOLUTION)
        )
    }

    @Test
    fun `head turned away is judged on upload with the settled copy (RW-50)`() {
        // GEN-6 (RW-50): §10.4 judges head-turn on upload, against
        // the guided capture's straight-on gate (18 degrees).
        assertEquals(
            PhotoRejection.TURNED_AWAY,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.5, maxAbsHeadTurnDegrees = 30f
            )
        )
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.35f,
                meanLuminance = 0.5,
                maxAbsHeadTurnDegrees = GuidedSelfieChecks.MAX_HEAD_TURN_DEGREES
            )
        )
        assertEquals(
            MakeItMeStrings.C3C_REJECT_TOO_DARK,
            PhotoQualityPolicy.stringFor(PhotoRejection.TURNED_AWAY)
        )
    }

    @Test
    fun `too dark and too close carry the settled section 10-4 wording (RW-50)`() {
        assertEquals(
            "Try facing a window or light, looking at the camera.",
            PhotoQualityPolicy.stringFor(PhotoRejection.TOO_DARK)
        )
        assertEquals(
            "Hold the phone at arm's length, so your whole face fits with space around it.",
            PhotoQualityPolicy.stringFor(PhotoRejection.TOO_CLOSE)
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

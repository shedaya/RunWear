package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RW-16.19 — REPRODUCTION of the pre-fix session fold, pinned as a
 * decision table (RW-16.1 reproduction-first discipline).
 *
 * The pre-fix fold shipped in vc23 (tree head 7a56e32) and no longer
 * exists as code: `PersonalHeroService.session()` fused storage,
 * network, and the decision in one suspend function with no seam, so
 * the deleted code cannot be executed from a unit test. Its DECISION
 * SEMANTICS, however, are fully expressible, and they are transcribed
 * branch-for-branch below from the shipped source:
 *
 *   session() (7a56e32): stored session + token live → reuse; stored
 *   session + token expired → refresh; refresh returned null → the
 *   source comment reads "Refresh failed (revoked/expired): fall
 *   through to a fresh anonymous sign-in, replacing the dead
 *   session." — `clearSession()` then `anonymousSignIn()`, i.e. the
 *   stored uid is DESTROYED and a new one minted.
 *
 *   refreshSession() (7a56e32): `runCatching { … parseSession(…) }
 *   .getOrNull()`, and parseSession returns null for ANY non-success
 *   status and for any unparseable body. So the pre-fix fold saw a
 *   BINARY refresh result — a session, or null — and every failure
 *   class (transport error, 5xx, unparseable body, AND a genuine
 *   4xx rejection) collapsed into the same null and the same
 *   destroy-and-mint.
 *
 * The field signature this reproduces: RW-16.19's missing job (the
 * overlay entered the request branch, yet no job ever landed under
 * the real uid) and RW-16.15(iv)'s bare mid-session signups. The
 * lethal case is rotation: Supabase refresh tokens rotate on use, so
 * a refresh that SUCCEEDED server-side but whose response was lost
 * in transit presents to the client as a transport failure — and
 * under the old fold that alone destroyed the identity.
 *
 * These tests assert BOTH tables on identical inputs: the legacy
 * transcription must destroy the stored session on every unproven
 * failure (the defect, demonstrated), and the shipped
 * [SessionGuardPolicy] — fed the outcome exactly as the new
 * `refreshSession()` classifies it — must keep it.
 */
class SessionGuardReproductionTest {

    /** The raw ways a refresh attempt can end, before classification. */
    private enum class RawRefreshOutcome {
        SUCCESS,
        HTTP_REJECTED_4XX,
        HTTP_SERVER_ERROR_5XX,
        TRANSPORT_ERROR,
        UNPARSEABLE_SUCCESS_BODY
    }

    /** What a session() implementation effectively does with the identity. */
    private enum class SessionEffect {
        REUSE_STORED,
        USE_REFRESHED,
        MINT_FRESH,
        DESTROY_STORED_AND_MINT,
        KEEP_STORED_AND_FAIL
    }

    // ── The pre-fix fold, transcribed from 7a56e32 ──────────────────

    /**
     * Pre-fix refreshSession(): a session comes back ONLY on an HTTP
     * success with a fully parseable body; transport exceptions
     * (runCatching → null), non-success statuses, and unparseable
     * bodies (parseSession → null) ALL collapse to null.
     */
    private fun legacyRefreshYieldsSession(outcome: RawRefreshOutcome): Boolean =
        outcome == RawRefreshOutcome.SUCCESS

    /** Pre-fix session(): the branch structure, verbatim in effect. */
    private fun legacyDecide(
        hasStoredSession: Boolean,
        storedTokenValid: Boolean,
        refreshOutcome: RawRefreshOutcome
    ): SessionEffect = when {
        !hasStoredSession -> SessionEffect.MINT_FRESH
        storedTokenValid -> SessionEffect.REUSE_STORED
        legacyRefreshYieldsSession(refreshOutcome) -> SessionEffect.USE_REFRESHED
        // Refresh returned null → clearSession() + anonymousSignIn().
        else -> SessionEffect.DESTROY_STORED_AND_MINT
    }

    // ── The shipped guard: the new classification + the policy ──────

    /**
     * The new refreshSession()'s classification: REJECTED only on a
     * 4xx answer; SUCCEEDED only on a parseable success; transport
     * errors, 5xx, and unparseable successes are UNKNOWN.
     */
    private fun classify(outcome: RawRefreshOutcome): SessionGuardPolicy.RefreshOutcome =
        when (outcome) {
            RawRefreshOutcome.SUCCESS -> SessionGuardPolicy.RefreshOutcome.SUCCEEDED
            RawRefreshOutcome.HTTP_REJECTED_4XX -> SessionGuardPolicy.RefreshOutcome.REJECTED
            RawRefreshOutcome.HTTP_SERVER_ERROR_5XX,
            RawRefreshOutcome.TRANSPORT_ERROR,
            RawRefreshOutcome.UNPARSEABLE_SUCCESS_BODY ->
                SessionGuardPolicy.RefreshOutcome.UNKNOWN
        }

    private fun guardedDecide(
        hasStoredSession: Boolean,
        storedTokenValid: Boolean,
        refreshOutcome: RawRefreshOutcome
    ): SessionEffect =
        when (
            SessionGuardPolicy.decide(
                hasStoredSession,
                storedTokenValid,
                classify(refreshOutcome)
            )
        ) {
            SessionGuardPolicy.Action.MINT_FRESH -> SessionEffect.MINT_FRESH
            SessionGuardPolicy.Action.REUSE_STORED -> SessionEffect.REUSE_STORED
            SessionGuardPolicy.Action.USE_REFRESHED -> SessionEffect.USE_REFRESHED
            SessionGuardPolicy.Action.CLEAR_AND_MINT -> SessionEffect.DESTROY_STORED_AND_MINT
            SessionGuardPolicy.Action.KEEP_STORED_AND_FAIL -> SessionEffect.KEEP_STORED_AND_FAIL
        }

    /** Every refresh failure that does NOT prove the identity is gone. */
    private val unprovenFailures = listOf(
        RawRefreshOutcome.TRANSPORT_ERROR,
        RawRefreshOutcome.HTTP_SERVER_ERROR_5XX,
        RawRefreshOutcome.UNPARSEABLE_SUCCESS_BODY
    )

    // ── The reproduction ────────────────────────────────────────────

    @Test
    fun `REPRODUCTION - under the pre-fix fold every unproven refresh failure destroyed the stored identity`() {
        for (outcome in unprovenFailures) {
            assertEquals(
                "pre-fix fold, stored session + expired token + $outcome",
                SessionEffect.DESTROY_STORED_AND_MINT,
                legacyDecide(
                    hasStoredSession = true,
                    storedTokenValid = false,
                    refreshOutcome = outcome
                )
            )
        }
    }

    @Test
    fun `REPRODUCTION - the rotation case - a refresh whose response was lost in transit destroyed the identity`() {
        // Server-side the refresh SUCCEEDED and rotated the token;
        // the client saw only a transport failure. The pre-fix fold
        // could not distinguish this from a dead session — and the
        // stored token being dead afterwards made the destruction
        // permanent and silent.
        assertEquals(
            SessionEffect.DESTROY_STORED_AND_MINT,
            legacyDecide(
                hasStoredSession = true,
                storedTokenValid = false,
                refreshOutcome = RawRefreshOutcome.TRANSPORT_ERROR
            )
        )
    }

    // ── The fix, on the identical inputs ────────────────────────────

    @Test
    fun `FIX - the same unproven failures keep the stored session under SessionGuardPolicy`() {
        for (outcome in unprovenFailures) {
            assertEquals(
                "guarded policy, stored session + expired token + $outcome",
                SessionEffect.KEEP_STORED_AND_FAIL,
                guardedDecide(
                    hasStoredSession = true,
                    storedTokenValid = false,
                    refreshOutcome = outcome
                )
            )
        }
    }

    // ── No over-correction: the paths the old fold got right ────────

    @Test
    fun `paths the old fold got right are unchanged by the guard`() {
        // No stored session: both mint.
        assertEquals(
            SessionEffect.MINT_FRESH,
            legacyDecide(false, false, RawRefreshOutcome.TRANSPORT_ERROR)
        )
        assertEquals(
            SessionEffect.MINT_FRESH,
            guardedDecide(false, false, RawRefreshOutcome.TRANSPORT_ERROR)
        )
        // Live token: both reuse without refreshing.
        assertEquals(
            SessionEffect.REUSE_STORED,
            legacyDecide(true, true, RawRefreshOutcome.TRANSPORT_ERROR)
        )
        assertEquals(
            SessionEffect.REUSE_STORED,
            guardedDecide(true, true, RawRefreshOutcome.TRANSPORT_ERROR)
        )
        // Refresh succeeds: both use the refreshed session.
        assertEquals(
            SessionEffect.USE_REFRESHED,
            legacyDecide(true, false, RawRefreshOutcome.SUCCESS)
        )
        assertEquals(
            SessionEffect.USE_REFRESHED,
            guardedDecide(true, false, RawRefreshOutcome.SUCCESS)
        )
        // Definitive 4xx rejection — the identity really is gone:
        // destroy-and-mint remains the recovery under BOTH tables.
        assertEquals(
            SessionEffect.DESTROY_STORED_AND_MINT,
            legacyDecide(true, false, RawRefreshOutcome.HTTP_REJECTED_4XX)
        )
        assertEquals(
            SessionEffect.DESTROY_STORED_AND_MINT,
            guardedDecide(true, false, RawRefreshOutcome.HTTP_REJECTED_4XX)
        )
    }
}

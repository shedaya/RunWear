package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * RW-16.15(iv) — the session-replacement guard.
 *
 * The defect family these tests pin: the anonymous Supabase uid IS
 * the account (photos, profile, entitlement, counter all key off
 * it), and the pre-guard `PersonalHeroService.session()` treated ANY
 * refresh failure as a dead session — it cleared the stored session
 * and minted a fresh uid, silently orphaning everything under the
 * old one. A refresh can fail transiently (transport error, 5xx),
 * and worse, Supabase refresh tokens ROTATE on use: a refresh that
 * succeeded server-side but whose response was lost leaves the
 * stored token dead while the account lives. Under the old fold,
 * either case destroyed the identity.
 *
 * The churn signature observed in the field (two bare anonymous
 * signups, zero footprint, inside one session window) is what a
 * re-mint looks like server-side; these tests pin that the shipping
 * decision function can only produce a mint when (a) no session is
 * stored at all, or (b) the server definitively rejected the stored
 * refresh token — never on an unproven failure.
 */
class SessionGuardPolicyTest {

    @Test
    fun `no stored session mints fresh`() {
        assertEquals(
            SessionGuardPolicy.Action.MINT_FRESH,
            SessionGuardPolicy.decide(
                hasStoredSession = false,
                storedTokenValid = false,
                refreshOutcome = null
            )
        )
    }

    @Test
    fun `a stored session with a live token is reused without any refresh`() {
        assertEquals(
            SessionGuardPolicy.Action.REUSE_STORED,
            SessionGuardPolicy.decide(
                hasStoredSession = true,
                storedTokenValid = true,
                refreshOutcome = null
            )
        )
    }

    @Test
    fun `a successful refresh is used`() {
        assertEquals(
            SessionGuardPolicy.Action.USE_REFRESHED,
            SessionGuardPolicy.decide(
                hasStoredSession = true,
                storedTokenValid = false,
                refreshOutcome = SessionGuardPolicy.RefreshOutcome.SUCCEEDED
            )
        )
    }

    @Test
    fun `a definitive server rejection of the refresh token clears and re-mints`() {
        assertEquals(
            SessionGuardPolicy.Action.CLEAR_AND_MINT,
            SessionGuardPolicy.decide(
                hasStoredSession = true,
                storedTokenValid = false,
                refreshOutcome = SessionGuardPolicy.RefreshOutcome.REJECTED
            )
        )
    }

    @Test
    fun `an unknown refresh outcome keeps the stored session and never mints`() {
        val action = SessionGuardPolicy.decide(
            hasStoredSession = true,
            storedTokenValid = false,
            refreshOutcome = SessionGuardPolicy.RefreshOutcome.UNKNOWN
        )
        assertEquals(SessionGuardPolicy.Action.KEEP_STORED_AND_FAIL, action)
        // The churn signature: under the pre-guard fold this exact
        // input produced a fresh anonymous uid.
        assertNotEquals(SessionGuardPolicy.Action.MINT_FRESH, action)
        assertNotEquals(SessionGuardPolicy.Action.CLEAR_AND_MINT, action)
    }

    @Test
    fun `an expired stored session with no refresh attempted yet also keeps and fails`() {
        assertEquals(
            SessionGuardPolicy.Action.KEEP_STORED_AND_FAIL,
            SessionGuardPolicy.decide(
                hasStoredSession = true,
                storedTokenValid = false,
                refreshOutcome = null
            )
        )
    }
}

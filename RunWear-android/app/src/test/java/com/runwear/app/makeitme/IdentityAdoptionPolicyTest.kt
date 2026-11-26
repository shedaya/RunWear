package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RW-137 (DESIGN §4.1/§4.4) + RW-179c (2026-11-25): the client's
 * identity-adoption trigger — the Android mirror of the iOS
 * IdentityAdoptionPolicy tests. Adoption is automatic (a standing
 * Premium property of a restored subscription); the policy fires
 * on a well-formed hint from the entitlement reconcile and never
 * on a self-adoption. The server re-verifies the RevenueCat proof
 * before re-keying anything, so this policy is a trigger, not a
 * trust decision.
 */
class IdentityAdoptionPolicyTest {

    private val caller = "6a6592d4-1d63-4b30-aefd-4a1b244aa6f0"
    private val prior = "63e82e02-7ef5-4b3a-9bf1-16fa6f3e9f58"

    @Test
    fun `a named prior identity is adopted`() {
        assertEquals(
            prior,
            IdentityAdoptionPolicy.sourceToAdopt(prior, caller)
        )
    }

    @Test
    fun `no hint means no adoption`() {
        assertNull(IdentityAdoptionPolicy.sourceToAdopt(null, caller))
        assertNull(IdentityAdoptionPolicy.sourceToAdopt("", caller))
        assertNull(IdentityAdoptionPolicy.sourceToAdopt("   ", caller))
    }

    @Test
    fun `a self-adoption never fires`() {
        assertNull(IdentityAdoptionPolicy.sourceToAdopt(caller, caller))
    }

    @Test
    fun `the hint is trimmed before use`() {
        assertEquals(
            prior,
            IdentityAdoptionPolicy.sourceToAdopt("  $prior  ", caller)
        )
    }

    @Test
    fun `an unknown caller id does not block a named source`() {
        // The caller id is only the self-adoption guard; when it is
        // unavailable the server's own guard still applies.
        assertEquals(
            prior,
            IdentityAdoptionPolicy.sourceToAdopt(prior, null)
        )
    }
}

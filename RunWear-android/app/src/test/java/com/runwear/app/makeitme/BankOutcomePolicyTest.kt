package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-137 (2026-11-25, subscription-as-identity): the client's
 * reading of the bank outcome the entitlement reconcile resolves
 * server-side. The bank is keyed to the SUBSCRIPTION, not the
 * device uid — resolution replaces the retired client-side
 * re-keying flow, so this policy's only jobs are the Restore
 * Purchases message for each resolved state and the bank
 * reference-photo truth. A not-entitled caller gets no message:
 * the no-subscription flow owns that case.
 */
class BankOutcomePolicyTest {

    // ── restoreMessage: entitlement gate ─────────────────────────

    @Test
    fun `not entitled means no message, whatever the bank says`() {
        assertNull(BankOutcomePolicy.restoreMessage(false, "restored", 12))
        assertNull(BankOutcomePolicy.restoreMessage(false, "own", 3))
        assertNull(BankOutcomePolicy.restoreMessage(false, "new", 0))
        assertNull(BankOutcomePolicy.restoreMessage(false, null, 0))
    }

    // ── restoreMessage: no bank resolved ─────────────────────────

    @Test
    fun `a null bank state says only that the subscription is active`() {
        assertEquals(
            "Your subscription is active.",
            BankOutcomePolicy.restoreMessage(true, null, 0)
        )
        // A look count without a resolved bank proves nothing —
        // the plain active line stands.
        assertEquals(
            "Your subscription is active.",
            BankOutcomePolicy.restoreMessage(true, null, 7)
        )
    }

    @Test
    fun `an unknown bank state falls back to the active line`() {
        assertEquals(
            "Your subscription is active.",
            BankOutcomePolicy.restoreMessage(true, "something_newer", 4)
        )
    }

    // ── restoreMessage: restored ─────────────────────────────────

    @Test
    fun `restored with looks names the count, plural`() {
        assertEquals(
            "Restored — your subscription and your 12 saved looks are back.",
            BankOutcomePolicy.restoreMessage(true, "restored", 12)
        )
        assertEquals(
            "Restored — your subscription and your 2 saved looks are back.",
            BankOutcomePolicy.restoreMessage(true, "restored", 2)
        )
    }

    @Test
    fun `restored with one look is singular`() {
        assertEquals(
            "Restored — your subscription and your 1 saved look is back.",
            BankOutcomePolicy.restoreMessage(true, "restored", 1)
        )
    }

    @Test
    fun `restored with no looks says none were found for it`() {
        assertEquals(
            "Restored — your subscription is back. No saved looks were found for it.",
            BankOutcomePolicy.restoreMessage(true, "restored", 0)
        )
    }

    // ── restoreMessage: own ──────────────────────────────────────

    @Test
    fun `own with looks names the count on this account, plural`() {
        assertEquals(
            "Your subscription is active — 5 saved looks on this account.",
            BankOutcomePolicy.restoreMessage(true, "own", 5)
        )
        assertEquals(
            "Your subscription is active — 2 saved looks on this account.",
            BankOutcomePolicy.restoreMessage(true, "own", 2)
        )
    }

    @Test
    fun `own with one look is singular`() {
        assertEquals(
            "Your subscription is active — 1 saved look on this account.",
            BankOutcomePolicy.restoreMessage(true, "own", 1)
        )
    }

    @Test
    fun `own with no looks is the plain active line`() {
        assertEquals(
            "Your subscription is active.",
            BankOutcomePolicy.restoreMessage(true, "own", 0)
        )
    }

    // ── restoreMessage: new ──────────────────────────────────────

    @Test
    fun `new says active and that no saved looks were found`() {
        assertEquals(
            "Your subscription is active. No saved looks were found for it.",
            BankOutcomePolicy.restoreMessage(true, "new", 0)
        )
    }

    // ── hasBankReferences ────────────────────────────────────────

    @Test
    fun `bank references exist only when the bank holds photos`() {
        assertTrue(BankOutcomePolicy.hasBankReferences(1))
        assertTrue(BankOutcomePolicy.hasBankReferences(3))
        assertFalse(BankOutcomePolicy.hasBankReferences(0))
        assertFalse(BankOutcomePolicy.hasBankReferences(-1))
    }
}

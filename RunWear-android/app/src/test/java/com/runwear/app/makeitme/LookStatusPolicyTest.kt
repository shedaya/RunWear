package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * RW-16.16 — the look-status contracts: the server state → rendered
 * status mapping, and the G5/G6 + amended-G2 copy deck.
 *
 * The mapping is the client half of the state machine the trace
 * demanded: a save registers, generation runs, and whatever the
 * terminal outcome is, the hero must be able to say it. Before this
 * lane the client's only inputs were found/not-found and a persisted
 * requested set whose side effect was to suppress exactly these
 * surfaces (spec G4 silence).
 */
class LookStatusPolicyTest {

    // ── Server state → rendered status ─────────────────────────────

    @Test
    fun `making maps to Making regardless of found`() {
        assertEquals(
            PersonalLookStatus.Making,
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "making", lookReason = null)
        )
        assertEquals(
            PersonalLookStatus.Making,
            PersonalLookStatusPolicy.fromServer(found = true, lookState = "making", lookReason = null)
        )
    }

    @Test
    fun `ready maps to Ready`() {
        assertEquals(
            PersonalLookStatus.Ready,
            PersonalLookStatusPolicy.fromServer(found = true, lookState = "ready", lookReason = null)
        )
    }

    @Test
    fun `none maps to None`() {
        assertEquals(
            PersonalLookStatus.None,
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "none", lookReason = null)
        )
    }

    @Test
    fun `failed maps each reason class`() {
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.QUALITY_REFUSED),
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "failed", lookReason = "quality_refused")
        )
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.SERVICE_REFUSED),
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "failed", lookReason = "service_refused")
        )
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR),
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "failed", lookReason = "error")
        )
    }

    @Test
    fun `failed with a serving prior hero stays Failed (keep-serving failure is the replacement's)`() {
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.QUALITY_REFUSED),
            PersonalLookStatusPolicy.fromServer(found = true, lookState = "failed", lookReason = "quality_refused")
        )
    }

    @Test
    fun `failed with an unknown or missing reason class degrades to ERROR, never to silence`() {
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR),
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "failed", lookReason = "gate_failed: missing_required: bottom (expected tights, saw shorts)")
        )
        assertEquals(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR),
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "failed", lookReason = null)
        )
    }

    @Test
    fun `a pre-v5 payload without state fields falls back to found-based inference`() {
        assertEquals(
            PersonalLookStatus.Ready,
            PersonalLookStatusPolicy.fromServer(found = true, lookState = null, lookReason = null)
        )
        assertEquals(
            PersonalLookStatus.None,
            PersonalLookStatusPolicy.fromServer(found = false, lookState = null, lookReason = null)
        )
    }

    @Test
    fun `an unrecognized state string falls back to found-based inference`() {
        assertEquals(
            PersonalLookStatus.Ready,
            PersonalLookStatusPolicy.fromServer(found = true, lookState = "pending", lookReason = null)
        )
        assertEquals(
            PersonalLookStatus.None,
            PersonalLookStatusPolicy.fromServer(found = false, lookState = "pending", lookReason = null)
        )
    }

    // ── Copy deck (spec §7, amended Nov 1, 2026) ───────────────────

    @Test
    fun `progress sub-line no longer promises ten seconds`() {
        assertFalse(MakeItMeStrings.G2_PROGRESS_SUB.contains("10 seconds"))
        // GEN-6 (RW-47): the settled §11 line, restored verbatim
        // per the owner's Nov 5 conformance ruling.
        assertEquals(
            "This usually takes a few minutes — the standard photo stays up until yours is done.",
            MakeItMeStrings.G2_PROGRESS_SUB
        )
    }

    @Test
    fun `progress sub-line carries the settled photo claim - GEN-6 RW-47 supersedes X-8`() {
        // The gen-3 X-8 audit removed this claim as false in the
        // Overland Park frames (no photo up at all). That state's
        // root cause — the empty-hero path — was closed by the
        // WS-A(1)/GEN-4 fallback floor, and the owner restored the
        // settled line in the Nov 5 conformance sweep (RW-47). The
        // claim is now pinned as PRESENT; if a no-photo state ever
        // recurs it is a serve-path defect, not a copy edit.
        assertTrue(MakeItMeStrings.G2_PROGRESS_SUB.contains("the standard photo stays up"))
    }

    @Test
    fun `failure line and retry action are the deck G5 and G6`() {
        assertEquals("Your look couldn't be made from these photos — tap to try again.", MakeItMeStrings.G5_LOOK_FAILED)
        assertEquals("Try again", MakeItMeStrings.G6_RETRY)
    }

    @Test
    fun `progress line is unchanged G1`() {
        assertEquals("Making this outfit yours…", MakeItMeStrings.G1_PROGRESS)
    }
}

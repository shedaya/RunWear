package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RW-26 §10b: the scaffold/converging render policy (frames 19–21). */
class ScaffoldRenderPolicyTest {

    @Test
    fun `a scaffold serve is badged honestly, an exact serve is silent`() {
        val kind = ScaffoldRenderPolicy.kindFor(
            PersonalLookStatus.Making, serveKindRaw = "scaffold", found = true
        )
        assertEquals(PersonalServeKind.SCAFFOLD, kind)
        assertEquals("Your photo — made for close-by weather", ScaffoldRenderPolicy.homeBadge(kind))
        val exact = ScaffoldRenderPolicy.kindFor(
            PersonalLookStatus.Ready, serveKindRaw = "exact", found = true
        )
        assertEquals(PersonalServeKind.EXACT, exact)
        assertNull(ScaffoldRenderPolicy.homeBadge(exact))
    }

    @Test
    fun `a failed cell serves stock quietly - never badged on home`() {
        val kind = ScaffoldRenderPolicy.kindFor(
            PersonalLookStatus.Failed(PersonalLookStatus.Reason.ERROR),
            serveKindRaw = null, found = false
        )
        assertEquals(PersonalServeKind.STOCK_QUIET, kind)
        assertNull(ScaffoldRenderPolicy.homeBadge(kind))
    }
}

/** RW-26 §10a: the guided capture's live check chips. */
class GuidedSelfieChecksTest {

    private fun m(
        faces: Int = 1, fraction: Float = 0.35f, lum: Double = 0.5, turn: Float = 5f
    ) = PhotoQualityChecker.Measurements(faces, fraction, lum, turn)

    @Test
    fun `a good selfie passes every chip`() {
        assertTrue(GuidedSelfieChecks.allPassed(m()))
        assertEquals("That's it — this photo works.", GuidedSelfieChecks.coachingLine(m()))
    }

    @Test
    fun `turnedPassing names exactly the checks that flip to passing`() {
        // WS-9 fix (P-2, §1.7): the selection tick fires once per
        // check that TURNS passing — the flip set is computed from
        // two production evaluations, never from hand-built states.
        val before = GuidedSelfieChecks.evaluate(m(faces = 0))
        val after = GuidedSelfieChecks.evaluate(m())
        // With no face in frame, the size and brightness chips
        // already pass (they judge the frame, not the face) — only
        // the three face-dependent checks can turn.
        assertEquals(
            listOf("Face in frame", "Just you", "Facing forward"),
            GuidedSelfieChecks.turnedPassing(before, after).map { it.label }
        )
        // Nothing turns when nothing changes, and a check that
        // REGRESSES does not fire.
        assertTrue(GuidedSelfieChecks.turnedPassing(after, after).isEmpty())
        assertTrue(GuidedSelfieChecks.turnedPassing(after, before).isEmpty())
        // A partial improvement names only the checks that flipped
        // (here brightness regresses while the face checks turn).
        val partial = GuidedSelfieChecks.evaluate(m(faces = 1, lum = 0.05))
        assertEquals(
            listOf("Face in frame", "Just you", "Facing forward"),
            GuidedSelfieChecks.turnedPassing(before, partial).map { it.label }
        )
    }

    @Test
    fun `chips fail independently and the coaching line names the first failure`() {
        assertFalse(GuidedSelfieChecks.allPassed(m(faces = 0)))
        assertEquals("Move your face into the oval.", GuidedSelfieChecks.coachingLine(m(faces = 0)))
        assertFalse(GuidedSelfieChecks.allPassed(m(faces = 2)))
        assertFalse(GuidedSelfieChecks.allPassed(m(fraction = 0.05f)))
        assertFalse(GuidedSelfieChecks.allPassed(m(turn = 40f)))
        assertEquals("Face the camera straight on.", GuidedSelfieChecks.coachingLine(m(turn = 40f)))
        assertFalse(GuidedSelfieChecks.allPassed(m(lum = 0.05)))
        assertFalse(GuidedSelfieChecks.allPassed(null))
    }
}

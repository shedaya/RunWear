package com.runwear.app.makeitme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-6 (F-1): the intake orientation rules, exercised with
 * rotated-at-sensor inputs at every candidate seam:
 *
 *  - the EXIF seam: every EXIF orientation value the camera can
 *    write maps to the transform that uprights the pixels;
 *  - the roll seam: the gate's readings across the full circle,
 *    including the diagnosis's own measured pairing (the sideways
 *    derivative read roll = +90.46° and was uprighted by a 90° CCW
 *    rotation — the correction table must reproduce exactly that);
 *  - the policy seam: [PhotoQualityPolicy.decide] rejects ROTATED
 *    beyond the gate and passes upright, with the roll check in its
 *    harmonized position (after face count, before darkness).
 */
class IntakeOrientationTest {

    // ---- EXIF seam ----

    @Test
    fun `every EXIF orientation maps to its uprighting transform`() {
        val expected = mapOf(
            1 to IntakeOrientation.ExifTransform(0, false),
            2 to IntakeOrientation.ExifTransform(0, true),
            3 to IntakeOrientation.ExifTransform(180, false),
            4 to IntakeOrientation.ExifTransform(180, true),
            5 to IntakeOrientation.ExifTransform(270, true),
            6 to IntakeOrientation.ExifTransform(90, false),
            7 to IntakeOrientation.ExifTransform(90, true),
            8 to IntakeOrientation.ExifTransform(270, false)
        )
        for ((exif, transform) in expected) {
            assertEquals("EXIF $exif", transform, IntakeOrientation.transformForExifOrientation(exif))
        }
    }

    @Test
    fun `undefined or out-of-range EXIF values mean as-stored`() {
        for (exif in listOf(0, -1, 9, Int.MAX_VALUE)) {
            assertEquals(
                IntakeOrientation.ExifTransform(0, false),
                IntakeOrientation.transformForExifOrientation(exif)
            )
        }
    }

    // ---- Roll gate seam ----

    @Test
    fun `roll gate passes upright and natural head tilts`() {
        for (roll in listOf(0f, 5f, -12f, 29.9f, -30f, 360f, -360f, 375f)) {
            assertTrue("roll $roll", IntakeOrientation.rollWithinGate(roll))
        }
    }

    @Test
    fun `roll gate fails sideways and steep tilts`() {
        for (roll in listOf(90f, -90f, 180f, 45f, -60f, 90.46f, 269.5f)) {
            assertFalse("roll $roll", IntakeOrientation.rollWithinGate(roll))
        }
    }

    @Test
    fun `roll normalization lands in (-180, 180]`() {
        assertEquals(90f, IntakeOrientation.normalizeRoll(450f), 0.001f)
        assertEquals(-90f, IntakeOrientation.normalizeRoll(270f), 0.001f)
        assertEquals(180f, IntakeOrientation.normalizeRoll(-180f), 0.001f)
        assertEquals(-179f, IntakeOrientation.normalizeRoll(181f), 0.001f)
    }

    // ---- Quarter-turn correction seam ----

    @Test
    fun `the diagnosis pairing - roll +90_46 corrects by 90 CCW`() {
        // The field diagnosis measured roll = +90.46° on Sol's
        // sideways derivative and proved a 90° CCW rotation of the
        // pixels uprights it (identity 0.19 -> 0.73). 90° CCW is
        // 270° clockwise in this table's terms.
        assertEquals(270, IntakeOrientation.quarterTurnCorrection(90.46f))
    }

    @Test
    fun `each quarter-turn roll corrects to upright`() {
        assertEquals(270, IntakeOrientation.quarterTurnCorrection(90f))
        assertEquals(90, IntakeOrientation.quarterTurnCorrection(-90f))
        assertEquals(180, IntakeOrientation.quarterTurnCorrection(180f))
        assertEquals(180, IntakeOrientation.quarterTurnCorrection(-180f))
        // Near-quarter readings within tolerance still correct.
        assertEquals(270, IntakeOrientation.quarterTurnCorrection(102f))
        assertEquals(90, IntakeOrientation.quarterTurnCorrection(-78f))
    }

    @Test
    fun `within-gate rolls need no correction`() {
        for (roll in listOf(0f, 15f, -30f, 30f)) {
            assertNull("roll $roll", IntakeOrientation.quarterTurnCorrection(roll))
        }
    }

    @Test
    fun `mid-angle rolls are not guessed at - no correction`() {
        // Beyond the gate but not a clean quarter-turn: rotation
        // cannot upright these; the policy rejects them ROTATED.
        for (roll in listOf(45f, -45f, 60f, 135f, -120f)) {
            assertNull("roll $roll", IntakeOrientation.quarterTurnCorrection(roll))
        }
    }

    @Test
    fun `a corrected roll re-reads within the gate`() {
        // The checker's correct-and-remeasure loop, at the reading
        // level: rotating the bitmap clockwise by the correction
        // moves the face's measured roll by the same clockwise
        // amount (the +90.46 diagnosis case: 90.46 + 270 -> 0.46).
        // Every correctable roll must land inside the gate.
        for (roll in listOf(90.46f, -89f, 179f, 95f, -100f, -179f)) {
            val correction = IntakeOrientation.quarterTurnCorrection(roll)
            assertTrue("roll $roll must be correctable", correction != null)
            val residual = IntakeOrientation.normalizeRoll(roll + correction!!.toFloat())
            assertTrue(
                "roll $roll corrected by $correction leaves residual $residual",
                IntakeOrientation.rollWithinGate(residual)
            )
        }
    }

    // ---- Policy seam ----

    @Test
    fun `policy rejects a sideways photo as ROTATED`() {
        assertEquals(
            PhotoRejection.ROTATED,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.4f,
                meanLuminance = 0.5, subjectHeadRollDegrees = 90.46f
            )
        )
        assertEquals(
            PhotoRejection.ROTATED,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.4f,
                meanLuminance = 0.5, subjectHeadRollDegrees = -90f
            )
        )
    }

    @Test
    fun `policy passes an upright photo and natural tilts`() {
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.4f,
                meanLuminance = 0.5, subjectHeadRollDegrees = -0.28f
            )
        )
        assertNull(
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.4f,
                meanLuminance = 0.5, subjectHeadRollDegrees = 20f
            )
        )
    }

    @Test
    fun `roll check order - after face count, before darkness`() {
        // No face at all still reads FACE_UNCLEAR, roll or not.
        assertEquals(
            PhotoRejection.FACE_UNCLEAR,
            PhotoQualityPolicy.decide(
                faceCount = 0, largestFaceWidthFraction = 0f,
                meanLuminance = 0.5, subjectHeadRollDegrees = 90f
            )
        )
        // A sideways AND dark photo reports the orientation defect.
        assertEquals(
            PhotoRejection.ROTATED,
            PhotoQualityPolicy.decide(
                faceCount = 1, largestFaceWidthFraction = 0.4f,
                meanLuminance = 0.05, subjectHeadRollDegrees = 90f
            )
        )
    }

    @Test
    fun `ROTATED carries its retake string`() {
        assertEquals(
            MakeItMeStrings.C3G_REJECT_ROTATED,
            PhotoQualityPolicy.stringFor(PhotoRejection.ROTATED)
        )
        assertTrue(MakeItMeStrings.C3G_REJECT_ROTATED.contains("sideways"))
    }

    @Test
    fun `guided checks fail a sideways capture and coach the retake`() {
        val sideways = PhotoQualityChecker.Measurements(
            faceCount = 1,
            largestFaceWidthFraction = 0.4f,
            meanLuminance = 0.5,
            maxAbsHeadTurnDegrees = 2f,
            subjectHeadRollDegrees = 90.46f
        )
        assertFalse(GuidedSelfieChecks.allPassed(sideways))
        assertTrue(GuidedSelfieChecks.coachingLine(sideways).contains("upright"))

        val upright = sideways.copy(subjectHeadRollDegrees = -0.28f)
        assertTrue(GuidedSelfieChecks.allPassed(upright))
    }
}

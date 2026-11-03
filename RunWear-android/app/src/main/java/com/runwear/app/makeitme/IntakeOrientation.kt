package com.runwear.app.makeitme

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * WS-6 (F-1, RC-6): the intake orientation rules — pure, no Android
 * types, so the gate exercises every seam with rotated inputs.
 *
 * F-1's root cause (field diagnosis, Nov 3 2026): the guided-selfie
 * capture's artifact arrived SIDEWAYS. The camera writes pixels in
 * sensor orientation and records the correction in the file's EXIF
 * orientation tag; [PhotoQualityChecker] decoded with
 * `BitmapFactory` — which does NOT apply EXIF — and re-encoded that
 * raw bitmap, manufacturing an upright-looking-in-preview but
 * sideways-on-disk reference whose EXIF was stripped by the
 * re-encode. Every downstream gate waved it through because no stage
 * read in-plane rotation: ML Kit's `headEulerAngleZ` (roll) was
 * never read anywhere in the app.
 *
 * The fix at the source has three parts, all decided here:
 *
 *  1. EXIF-at-decode: [transformForExifOrientation] maps each EXIF
 *     orientation value to the rotation/flip that makes the decoded
 *     bitmap upright. The checker applies it at decode, so the
 *     captured artifact is upright before anything measures or
 *     encodes it.
 *  2. Roll gate: [rollWithinGate] — the subject face's roll
 *     (ML Kit `headEulerAngleZ`) must sit within ±[MAX_ROLL_DEGREES]
 *     of upright (the same 30° line the server's ref-prep uses).
 *  3. Quarter-turn correction: a residual roll that is a near
 *     quarter-turn (an EXIF-less sideways photo — a screenshot, an
 *     edited export) is corrected by [quarterTurnCorrection] and
 *     re-measured; a roll that is neither within the gate nor a
 *     clean quarter-turn is NOT guessed at — the photo is rejected
 *     ROTATED and retaken (the server safety net behaves the same).
 */
object IntakeOrientation {

    /** Roll gate half-width in degrees (mirrors ref-prep's 30° line). */
    const val MAX_ROLL_DEGREES: Float = 30f

    /** How close a residual roll must sit to a quarter-turn to be corrected. */
    const val QUARTER_TURN_TOLERANCE_DEGREES: Float = 15f

    /**
     * The pixel transform an EXIF orientation tag demands:
     * a horizontal flip when [flipHorizontal], followed by
     * [rotationDegrees] clockwise. Mirrors `ExifInterface.ORIENTATION_*`
     * (the constants are duplicated as ints so this object stays
     * Android-free and unit-testable).
     */
    data class ExifTransform(val rotationDegrees: Int, val flipHorizontal: Boolean)

    /**
     * EXIF orientation (1–8; anything else, including 0/undefined,
     * means "as stored") → the transform that uprights the pixels.
     *
     *  1 normal · 2 flip horizontal · 3 rotate 180 ·
     *  4 flip vertical (= 180 + flip horizontal) ·
     *  5 transpose (= 270 CW + flip horizontal) ·
     *  6 rotate 90 CW · 7 transverse (= 90 CW + flip horizontal) ·
     *  8 rotate 270 CW.
     */
    fun transformForExifOrientation(orientation: Int): ExifTransform = when (orientation) {
        2 -> ExifTransform(0, flipHorizontal = true)
        3 -> ExifTransform(180, flipHorizontal = false)
        4 -> ExifTransform(180, flipHorizontal = true)
        5 -> ExifTransform(270, flipHorizontal = true)
        6 -> ExifTransform(90, flipHorizontal = false)
        7 -> ExifTransform(90, flipHorizontal = true)
        8 -> ExifTransform(270, flipHorizontal = false)
        else -> ExifTransform(0, flipHorizontal = false)
    }

    /** Normalize any roll reading into (-180, 180]. */
    fun normalizeRoll(rollDegrees: Float): Float {
        var r = rollDegrees % 360f
        if (r > 180f) r -= 360f
        if (r <= -180f) r += 360f
        return r
    }

    /** The roll gate: the subject face reads upright. */
    fun rollWithinGate(rollDegrees: Float): Boolean =
        abs(normalizeRoll(rollDegrees)) <= MAX_ROLL_DEGREES

    /**
     * The compensating clockwise rotation (90, 180, or 270) that
     * uprights a bitmap whose subject face reads [rollDegrees] —
     * or null when the roll is within the gate (nothing to correct)
     * or is not a clean quarter-turn (not correctable by rotation
     * alone; the gate rejects it instead).
     *
     * Sign: ML Kit's `headEulerAngleZ` is positive when the face
     * tilts counterclockwise in the image as seen... empirically the
     * diagnosis measured roll = +90.46° on pixels rotated 90° CCW,
     * and uprighting those pixels took a 90° CCW rotation — i.e.
     * the correction is the INVERSE of the measured roll, snapped
     * to the nearest quarter-turn.
     */
    fun quarterTurnCorrection(rollDegrees: Float): Int? {
        val roll = normalizeRoll(rollDegrees)
        if (abs(roll) <= MAX_ROLL_DEGREES) return null
        val snapped = (roll / 90f).roundToInt() * 90
        if (abs(roll - snapped) > QUARTER_TURN_TOLERANCE_DEGREES) return null
        // Inverse of the measured roll, expressed clockwise-positive.
        return when (((-snapped) % 360 + 360) % 360) {
            90 -> 90
            180 -> 180
            270 -> 270
            else -> null
        }
    }
}

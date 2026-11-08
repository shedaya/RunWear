package com.runwear.app.ui.components

import android.graphics.Bitmap
import com.runwear.shared.domain.model.TempBracket
import kotlin.math.pow

/**
 * RW-144 (Nov 8, 2026): the contrast guarantee's mechanism.
 *
 * The v8 design's scrims are drawn for one photograph; production
 * serves arbitrary personal photos (snowfields, beaches, noon sky).
 * The mock's scrim peak (α ≈ 0.73, 186/255) is therefore a FLOOR,
 * not a constant (notes-v6 production note + RW-143 UX review item
 * 2): the scrim strength behind the feels-like block AND the
 * place-pill / wordmark / chip zones is driven by the MEASURED
 * LUMINANCE of the actual photo region beneath each zone, sampled
 * at render time from a downscale of the served bitmap.
 *
 * This object is the pure half: sRGB relative luminance, the
 * region sampler, and the luminance → alpha mappings. The Compose
 * half (decoding the served bitmap at thumbnail size through Coil
 * and feeding regions in) lives in HeroSection.
 */
object HeroLuminancePolicy {

    /** The mock's feels-like scrim peak: 186/255 ≈ 0.73. A FLOOR. */
    const val SCRIM_PEAK_FLOOR: Float = 186f / 255f

    /** Strongest the feels-like scrim ever gets (bright glare). */
    const val SCRIM_PEAK_MAX: Float = 0.94f

    /**
     * Luminance at/below which the floor applies unchanged — the
     * healed Phoenix hero's regions sit in this range, so the
     * approved frames' treatment is reproduced exactly there.
     */
    const val FLOOR_LUMINANCE: Float = 0.30f

    /** sRGB relative luminance of one pixel (0 = black, 1 = white). */
    fun relativeLuminance(r: Int, g: Int, b: Int): Float {
        fun linear(c: Int): Float {
            val s = c / 255f
            return if (s <= 0.04045f) s / 12.92f else ((s + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * linear(r) + 0.7152f * linear(g) + 0.0722f * linear(b)
    }

    /**
     * The feels-like scrim's peak alpha for a measured region
     * luminance: the floor up to [FLOOR_LUMINANCE], then a linear
     * ramp to [SCRIM_PEAK_MAX] at luminance 1. Dark photos relax
     * TOWARD the floor, never below it — the floor is what the
     * approved frames carry.
     */
    fun scrimPeakAlpha(measuredLuminance: Float): Float {
        val t = ((measuredLuminance - FLOOR_LUMINANCE) / (1f - FLOOR_LUMINANCE))
            .coerceIn(0f, 1f)
        return SCRIM_PEAK_FLOOR + (SCRIM_PEAK_MAX - SCRIM_PEAK_FLOOR) * t
    }

    /**
     * The top/chip zones' luminance boost: a multiplier on the top
     * scrim's ruled stop alphas (0.62 / 0.31). 1.0 at the floor
     * luminance; up to 1.45 over glare; down to 0.85 over a very
     * dark photo (the scrims relax toward — never past — legibility
     * for the cream chrome).
     */
    fun chromeScrimBoost(measuredLuminance: Float): Float {
        val t = ((measuredLuminance - FLOOR_LUMINANCE) / (1f - FLOOR_LUMINANCE))
            .coerceIn(-0.5f, 1f)
        return if (t >= 0f) 1f + 0.45f * t else 1f + 0.30f * t
    }

    /**
     * Mean relative luminance of a fractional region of [bitmap]:
     * the region is cropped and downscaled to at most 16×16 before
     * averaging, so the cost is bounded regardless of source size.
     * Fractions are of the bitmap's own dimensions; the caller maps
     * display zones to source fractions through the crop geometry
     * (the served slice is the bitmap's top-anchored crop — see
     * HeroImageGeometry).
     */
    fun sampleRegionLuminance(
        bitmap: Bitmap,
        x0: Float, y0: Float, x1: Float, y1: Float
    ): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return FLOOR_LUMINANCE
        val left = (x0.coerceIn(0f, 1f) * w).toInt().coerceIn(0, w - 1)
        val top = (y0.coerceIn(0f, 1f) * h).toInt().coerceIn(0, h - 1)
        val right = (x1.coerceIn(0f, 1f) * w).toInt().coerceIn(left + 1, w)
        val bottom = (y1.coerceIn(0f, 1f) * h).toInt().coerceIn(top + 1, h)
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val small = Bitmap.createScaledBitmap(
            crop, minOf(16, crop.width), minOf(16, crop.height), true
        )
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== crop) small.recycle()
        if (crop !== bitmap) crop.recycle()
        if (pixels.isEmpty()) return FLOOR_LUMINANCE
        var sum = 0.0
        for (p in pixels) {
            sum += relativeLuminance(
                (p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF
            )
        }
        return (sum / pixels.size).toFloat()
    }
}

/**
 * RW-144: the hero band palette — the PWA's dynamic band colours
 * attached to the app's own bracket names (notes-v6, verified in
 * runwearpwa22). The hexes are the Theme temperature tokens; this
 * object owns the band WORD and the hero wash strengths so the
 * hero, the strip proof and the tests read one source.
 */
object HeroBand {

    /** The tracked band word beside the numeral ("MILD"). */
    fun word(bracket: TempBracket): String = bracket.name

    /** Band wash over the photo: α0.15 top → 0.05 mid → 0 at foot. */
    const val WASH_TOP_ALPHA: Float = 0.15f
    const val WASH_MID_ALPHA: Float = 0.05f
}

/**
 * RW-144: chip crop protection (RW-143 UX review item 3). The
 * date/time chip gets the same subject/face-zone protection the
 * wordmark zone has: its vertical seat is chosen so the chip never
 * intersects the runner's head band as displayed by the crop
 * geometry (HeroImageGeometry's audited band, top-anchored slice).
 * Pure dp arithmetic, unit-tested.
 */
object HeroChipPlacement {

    /** Preferred seat: mock geometry (chip top ≈ 21% of the hero's
     * display height below the chrome row) — used when it clears
     * the head band outright. */
    fun preferredTopDp(chromeBottomDp: Float): Float = chromeBottomDp + 58f

    /**
     * The chip's top edge (dp from the hero's top): the preferred
     * seat when [preferredTopDp, +chipHeight] misses the head band;
     * otherwise above the head (head top − chip − 8dp gap) when
     * that still clears the chrome row; otherwise directly below
     * the head band (+ 8dp gap). The chip NEVER lands across the
     * face zone.
     */
    fun topDp(
        chromeBottomDp: Float,
        chipHeightDp: Float,
        headTopDp: Float,
        headBottomDp: Float
    ): Float {
        val preferred = preferredTopDp(chromeBottomDp)
        val chipBottom = preferred + chipHeightDp
        if (chipBottom <= headTopDp || preferred >= headBottomDp) return preferred
        val above = headTopDp - chipHeightDp - 8f
        if (above >= chromeBottomDp + 8f) return above
        return headBottomDp + 8f
    }
}

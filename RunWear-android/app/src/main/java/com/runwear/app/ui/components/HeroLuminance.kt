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
 * RW-145 (Nov 9, 2026): the hero's vertical composition as ONE
 * height-aware system. The RW-144 geometry fixed the hero at
 * 0.44 × screen height and seated the date/time chip by the head
 * band alone: on the shorter folded viewport (≈740dp) the crop's
 * head band sits low, the chip's below-the-head fallback landed
 * at headBottom + 8 — inside the feels-like block, whose footprint
 * is absolute — so the pill sat BEHIND the FEELS LIKE label and
 * the numeral's top on Sol's Fold8 (owner device pass, Nov 9).
 * The harness never saw it: its fixture photo was a flat wash, so
 * the placement rule never moved the pill off its preferred seat.
 *
 * The composition is now solved together, in this one pure place:
 *  - the hero height is 0.44 × screen, but never so short that
 *    chrome + chip + the guaranteed gap + the block cannot all
 *    fit ([heroHeightDp]);
 *  - the block scales gently on short heroes ([blockScale]) so
 *    the standard composition fits more viewports un-grown;
 *  - the chip's seat ([HeroChipPlacement.topDp]) must satisfy
 *    BOTH the face-zone rule AND the block rule: its bottom edge
 *    keeps [MIN_CHIP_BLOCK_GAP_DP] of clear air above the block's
 *    top edge, on every viewport, in every fallback order.
 * The approved v8 design governs throughout (pill high on the
 * hero, clear air above the block) — this is the composition
 * arithmetic that guarantees it, not a redesign.
 */
object HeroComposition {

    /** Bottom edge of the top chrome row (status inset + place
     * pill / gear row), the seat the chip hangs from. Matches the
     * value HeroSection has always passed as chromeBottomDp. */
    const val CHROME_BOTTOM_DP: Float = 96f

    /** The guaranteed minimum clear air between the chip's bottom
     * edge and the feels-like block's top edge — every viewport,
     * every photo, every fallback. */
    const val MIN_CHIP_BLOCK_GAP_DP: Float = 12f

    /** The block's fixed (unscaled) parts: the FEELS LIKE label
     * line, the Actual line and its top padding. */
    const val BLOCK_FIXED_DP: Float = 39f

    /** The block's bottom inset inside the hero (its padding) —
     * the RW-144 seat, retained for callers that compose the
     * block alone. The composed foot stack now rides on
     * [footReserveDp]. */
    const val BLOCK_BOTTOM_INSET_DP: Float = 16f

    /** HERO REDESIGN (Sol's ruling, Nov 9, 2026 — the RW-145
     * amendment): the weather line and the Make It Me straddle
     * state moved ONTO the hero's foot, below the feels-like
     * block. The foot stack, bottom-up: straddle pill (its top
     * half inside the hero) → weather line → gap → block. Its
     * zones: */
    const val WEATHER_ZONE_DP: Float = 56f

    /** Clear air between the weather line's top and the block's
     * bottom edge. */
    const val BLOCK_WEATHER_GAP_DP: Float = 10f

    /** Clear air between the weather line's bottom edge and the
     * straddle pill's top edge. */
    const val WEATHER_STRADDLE_GAP_DP: Float = 8f

    /** The straddle's typical half-height inside the hero, for
     * the height solver (the render path measures the real pill
     * and passes its own reserve). */
    const val STRADDLE_HALF_TYPICAL_DP: Float = 26f

    /** The foot reserve when no straddle pill exists (a stock
     * serve): weather zone + gaps + a plain foot inset. */
    const val DEFAULT_FOOT_RESERVE_DP: Float =
        WEATHER_ZONE_DP + BLOCK_WEATHER_GAP_DP + BLOCK_BOTTOM_INSET_DP

    /** The foot reserve (dp above the hero's foot edge where the
     * feels-like block's bottom sits) for a straddle pill whose
     * top half rises [straddleHalfDp] into the hero. */
    fun footReserveDp(straddleHalfDp: Float): Float =
        straddleHalfDp + WEATHER_STRADDLE_GAP_DP + WEATHER_ZONE_DP +
            BLOCK_WEATHER_GAP_DP

    /** The unscaled hero height the block's scale is normed to
     * (0.44 × 844 — the RW-144 standard phone hero). */
    const val REFERENCE_HERO_DP: Float = 371f

    /** The numeral never shrinks past this fraction of 100sp —
     * below it the hero GROWS instead (see [heroHeightDp]). */
    const val MIN_BLOCK_SCALE: Float = 0.84f

    /** The block's scale at [heroHeightDp]: 1 at and above the
     * reference hero, easing to [MIN_BLOCK_SCALE] on short heroes. */
    fun blockScale(heroHeightDp: Float): Float =
        (heroHeightDp / REFERENCE_HERO_DP).coerceIn(MIN_BLOCK_SCALE, 1f)

    /** The block's footprint (dp) at [scale] with the default
     * foot inset. Retained for the RW-144-era callers/tests. */
    fun blockFootprintDp(scale: Float): Float =
        BLOCK_BOTTOM_INSET_DP + BLOCK_FIXED_DP + 100f * scale

    /** The block's footprint (dp) at [scale] over a foot stack
     * reserving [footReserveDp]: the block's top edge is
     * heroHeight − this. This is the top the chip must clear. */
    fun blockFootprintDp(scale: Float, footReserveDp: Float): Float =
        footReserveDp + BLOCK_FIXED_DP + 100f * scale

    /** HERO REDESIGN fractions (Sol's ruling, Nov 9, 2026): the
     * hero takes most of the first viewport — two-thirds to
     * three-quarters — because every key surface now rides over
     * it. Standard phones get 0.72: at 844dp the outfit header
     * and the first item still land inside the first viewport
     * below the 608dp hero. Short/folded viewports get 0.66: at
     * 740dp the 488dp hero still leaves the header and first
     * item visible above the dock's bounded band. EXPANDED does
     * not use this function — its hero fills the pane (the
     * outfit owns a separate pane). */
    const val STANDARD_FRACTION: Float = 0.72f
    const val SHORT_FRACTION: Float = 0.66f
    const val STANDARD_MIN_HEIGHT_DP: Int = 800

    /** The hero's height for a screen of [screenHeightDp]: the
     * redesign fraction for the viewport's class, raised when
     * the fraction alone cannot hold chrome + chip + gap + the
     * full foot stack + the (maximally scaled-down) block. */
    fun heroHeightDp(screenHeightDp: Int): Float {
        val fraction = screenHeightDp *
            if (screenHeightDp >= STANDARD_MIN_HEIGHT_DP) {
                STANDARD_FRACTION
            } else {
                SHORT_FRACTION
            }
        // The tightest legal stack: chrome, an 8dp breath, the
        // chip, the guaranteed gap, and the block at its minimum
        // scale over the typical foot stack. If the fraction
        // cannot hold it, the hero grows until it can (capped so
        // a tiny window stays a window).
        val needed = CHROME_BOTTOM_DP + 8f + 34f + MIN_CHIP_BLOCK_GAP_DP +
            blockFootprintDp(
                MIN_BLOCK_SCALE, footReserveDp(STRADDLE_HALF_TYPICAL_DP)
            )
        return maxOf(fraction, minOf(needed, screenHeightDp * 0.80f))
    }

    /** The guaranteed gap the solved composition actually delivers
     * at a viewport: chip bottom → block top, given the seat the
     * placement rule chose and the foot stack's reserve. Always
     * ≥ [MIN_CHIP_BLOCK_GAP_DP] when the hero came from
     * [heroHeightDp] (pinned across the viewport × head-band
     * matrix by the RW-145 tests). */
    fun chipBlockGapDp(
        chipTopDp: Float,
        chipHeightDp: Float,
        heroHeightDp: Float,
        footReserveDp: Float = footReserveDp(STRADDLE_HALF_TYPICAL_DP)
    ): Float =
        (heroHeightDp - blockFootprintDp(blockScale(heroHeightDp), footReserveDp)) -
            (chipTopDp + chipHeightDp)
}

/**
 * RW-144: chip crop protection (RW-143 UX review item 3). The
 * date/time chip gets the same subject/face-zone protection the
 * wordmark zone has: its vertical seat is chosen so the chip never
 * intersects the runner's head band as displayed by the crop
 * geometry (HeroImageGeometry's audited band, top-anchored slice).
 * Pure dp arithmetic, unit-tested.
 *
 * RW-145: the seat must ALSO clear the feels-like block —
 * [blockTopDp] is the block's top edge and no candidate seat may
 * put the chip's bottom within [HeroComposition
 * .MIN_CHIP_BLOCK_GAP_DP] of it. The pre-fix below-the-head
 * fallback (headBottom + 8) is exactly the seat that collided on
 * the folded viewport, so the fallback ORDER is now: preferred →
 * above the head → below the head, each accepted only if it
 * satisfies the block rule; when none does, the chip takes the
 * highest seat that satisfies the block rule outright (directly
 * above the block, gap-exact) — the block rule is the guarantee,
 * the face rule yields only in that last resort (and cannot be
 * reached when the hero height came from [HeroComposition
 * .heroHeightDp], which sizes the hero so a face-clear seat
 * always exists).
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
     * face zone — subject to the RW-145 block rule below.
     *
     * RW-145: [blockTopDp] (the feels-like block's top edge) is a
     * hard constraint on every candidate: chipBottom +
     * [HeroComposition.MIN_CHIP_BLOCK_GAP_DP] ≤ blockTopDp.
     */
    fun topDp(
        chromeBottomDp: Float,
        chipHeightDp: Float,
        headTopDp: Float,
        headBottomDp: Float,
        blockTopDp: Float = Float.MAX_VALUE
    ): Float {
        val maxChipBottom = blockTopDp - HeroComposition.MIN_CHIP_BLOCK_GAP_DP
        fun clearsBlock(top: Float): Boolean = top + chipHeightDp <= maxChipBottom
        val preferred = preferredTopDp(chromeBottomDp)
        val chipBottom = preferred + chipHeightDp
        if ((chipBottom <= headTopDp || preferred >= headBottomDp) && clearsBlock(preferred)) {
            return preferred
        }
        val above = headTopDp - chipHeightDp - 8f
        if (above >= chromeBottomDp + 8f && clearsBlock(above)) return above
        val below = headBottomDp + 8f
        if (clearsBlock(below)) return below
        // Last resort: the highest seat that satisfies the block
        // rule outright. Reached only when the caller's hero is
        // shorter than HeroComposition guarantees — the block
        // rule (readable content never collides) outranks the
        // face-zone preference.
        return (maxChipBottom - chipHeightDp).coerceAtLeast(chromeBottomDp + 4f)
    }
}

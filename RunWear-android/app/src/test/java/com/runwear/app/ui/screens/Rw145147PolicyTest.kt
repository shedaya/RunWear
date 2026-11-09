package com.runwear.app.ui.screens

import com.runwear.app.ui.components.HeroChipGeometry
import com.runwear.app.ui.components.HeroChipPlacement
import com.runwear.app.ui.components.HeroComposition
import com.runwear.app.ui.components.HeroImageGeometry
import com.runwear.app.ui.components.heroPhotoHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-145 + RW-147 (Nov 9, 2026): the hero composition guarantee
 * and the dock clearance system, as pure arithmetic.
 *
 * RW-145's device evidence (Fold8 folded): the date/time pill sat
 * BEHIND the FEELS LIKE label and the numeral's top. The seat rule
 * now answers to the block as well as the face, and the hero
 * height is solved so both rules can always be met together.
 * RW-147's device evidence: the dock covered the 3rd outfit row
 * (portrait) and card content (expanded). The reserve is sized
 * from the dock's own maximum band, not from fixture content.
 */
class Rw145147PolicyTest {

    // ── RW-145: hero height ──────────────────────────────────────

    @Test
    fun `rw145 hero height - standard viewports keep the RW-144 fraction exactly`() {
        // The RW-144 pins stand: nothing about the approved
        // composition changes where the fraction already works.
        assertEquals(371.04f, HeroComposition.heroHeightDp(844), 0.01f)
        assertEquals(440f, HeroComposition.heroHeightDp(1000), 0.01f)
        assertEquals(371.04f, heroPhotoHeight(844).value, 0.01f)
    }

    @Test
    fun `rw145 hero height - folded viewport keeps the fraction, tiny windows grow to the needed stack`() {
        // Folded Fold8 class (~740dp): 0.44 holds the stack, so
        // the fraction stands — the fix there is the seat rule.
        assertEquals(325.6f, HeroComposition.heroHeightDp(740), 0.01f)
        // A 600dp window: 0.44 = 264 < the 289dp tightest legal
        // stack, so the hero grows to exactly the stack.
        assertEquals(289f, HeroComposition.heroHeightDp(600), 0.01f)
    }

    @Test
    fun `rw145 block scale - full size at the reference hero, floored on short heroes`() {
        assertEquals(1f, HeroComposition.blockScale(371f), 0.001f)
        assertEquals(1f, HeroComposition.blockScale(440f), 0.001f)
        assertEquals(
            HeroComposition.MIN_BLOCK_SCALE,
            HeroComposition.blockScale(289f), 0.001f
        )
        assertEquals(155f, HeroComposition.blockFootprintDp(1f), 0.01f)
        assertEquals(139f, HeroComposition.blockFootprintDp(0.84f), 0.01f)
    }

    // ── RW-145: the guarantee across the viewport × width matrix ─

    @Test
    fun `rw145 guarantee - chip keeps the minimum gap above the block and misses the face, every viewport`() {
        val chipH = HeroChipGeometry.HEIGHT.value
        for (screenH in listOf(600, 640, 700, 740, 760, 800, 844, 900, 1000)) {
            for (widthDp in listOf(360f, 390f, 411f)) {
                for (density in listOf(2f, 2.625f, 3f)) {
                    val heroH = HeroComposition.heroHeightDp(screenH)
                    val wPx = widthDp * density
                    val hPx = heroH * density
                    val scale = HeroImageGeometry.cropScale(wPx, hPx)
                    val headTop = HeroImageGeometry.headTopDisplayPx(wPx, hPx) / density
                    val headBottom = headTop +
                        (HeroImageGeometry.HEAD_BAND_BOTTOM_FRACTION -
                            HeroImageGeometry.HEAD_BAND_TOP_FRACTION) *
                        HeroImageGeometry.SOURCE_HEIGHT_PX * scale / density
                    val blockTop = heroH -
                        HeroComposition.blockFootprintDp(HeroComposition.blockScale(heroH))
                    val top = HeroChipPlacement.topDp(
                        chromeBottomDp = HeroComposition.CHROME_BOTTOM_DP,
                        chipHeightDp = chipH,
                        headTopDp = headTop,
                        headBottomDp = headBottom,
                        blockTopDp = blockTop
                    )
                    val gap = HeroComposition.chipBlockGapDp(top, chipH, heroH)
                    assertTrue(
                        "screen=$screenH w=$widthDp d=$density: gap $gap < guaranteed " +
                            "${HeroComposition.MIN_CHIP_BLOCK_GAP_DP} (chip $top..${top + chipH}, blockTop $blockTop)",
                        gap >= HeroComposition.MIN_CHIP_BLOCK_GAP_DP - 0.01f
                    )
                    assertTrue(
                        "screen=$screenH w=$widthDp d=$density: chip $top..${top + chipH} " +
                            "crosses head $headTop..$headBottom",
                        top + chipH <= headTop + 0.01f || top >= headBottom - 0.01f
                    )
                }
            }
        }
    }

    @Test
    fun `rw145 folded case - the seat that collided on device now clears the block`() {
        // The device case: ~740dp folded viewport, 390dp wide.
        // Pre-fix the below-the-head fallback put the chip at
        // headBottom + 8 ≈ 256dp, inside the block (top ≈ 183dp).
        val heroH = HeroComposition.heroHeightDp(740)
        val density = 2.625f
        val wPx = 390f * density
        val hPx = heroH * density
        val scale = HeroImageGeometry.cropScale(wPx, hPx)
        val headTop = HeroImageGeometry.headTopDisplayPx(wPx, hPx) / density
        val headBottom = headTop +
            (HeroImageGeometry.HEAD_BAND_BOTTOM_FRACTION -
                HeroImageGeometry.HEAD_BAND_TOP_FRACTION) *
            HeroImageGeometry.SOURCE_HEIGHT_PX * scale / density
        val blockTop = heroH -
            HeroComposition.blockFootprintDp(HeroComposition.blockScale(heroH))
        val top = HeroChipPlacement.topDp(
            chromeBottomDp = HeroComposition.CHROME_BOTTOM_DP,
            chipHeightDp = HeroChipGeometry.HEIGHT.value,
            headTopDp = headTop,
            headBottomDp = headBottom,
            blockTopDp = blockTop
        )
        assertTrue(
            "chip bottom ${top + 34f} must clear block top $blockTop by the guaranteed gap",
            top + 34f <= blockTop - HeroComposition.MIN_CHIP_BLOCK_GAP_DP + 0.01f
        )
        assertTrue(
            "chip $top..${top + 34f} must miss head $headTop..$headBottom",
            top + 34f <= headTop + 0.01f || top >= headBottom - 0.01f
        )
    }

    @Test
    fun `rw145 seat rule - without a block constraint the RW-144 seats are unchanged`() {
        // blockTopDp defaults to +∞: the pre-RW-145 call shape
        // (and its pins in Rw144DesignPolicyTest) behaves exactly
        // as before — the block rule only binds when a real block
        // top is supplied.
        assertEquals(
            154f,
            HeroChipPlacement.topDp(96f, 34f, headTopDp = 260f, headBottomDp = 320f),
            0.01f
        )
        assertEquals(
            177f + 8f,
            HeroChipPlacement.topDp(96f, 34f, headTopDp = 120f, headBottomDp = 177f),
            0.01f
        )
    }

    // ── RW-147: dock clearance ───────────────────────────────────

    @Test
    fun `rw147 reserve - sized from the dock's own maximum band plus clearance`() {
        assertEquals(14f + 88f, DockClearancePolicy.dockBandDp(), 0.01f)
        assertEquals(118f, DockClearancePolicy.scrollEndReserveDp(0f), 0.01f)
        assertEquals(142f, DockClearancePolicy.scrollEndReserveDp(24f), 0.01f)
    }

    @Test
    fun `rw147 at-rest rule - an element is clear iff it ends above the dock band`() {
        // Folded viewport 740dp, no nav inset: the band's top is
        // at 740 − 102 = 638. The device frame's 3rd outfit row
        // (bottom ≈ 700) was NOT clear; the 2nd row (≈ 620) was.
        assertTrue(DockClearancePolicy.isClearAtRest(620f, 740f, 0f))
        assertTrue(!DockClearancePolicy.isClearAtRest(700f, 740f, 0f))
        // With the reserve carried, scroll end lifts the last
        // element by the reserve: a last element whose natural
        // bottom is the viewport's foot rests at 740 − 118 = 622.
        assertTrue(DockClearancePolicy.isClearAtRest(740f - 118f, 740f, 0f))
    }
}

package com.runwear.app.ui.components

import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 A-HERO (WS-A(2)): THE hero pill's state derivation. Census
 * X-14: no pill of any kind appeared in the gen-2 recording — the
 * scaffold badge existed in code and rendered in zero frames. The
 * pill is now ONE component at the bottom of the hero image, and
 * this policy is its whole truth: four states, state-driven, with
 * stock serves and failed looks carrying no pill.
 */
class HeroPillPolicyTest {

    @Test
    fun `generating shows the making pill over whatever photo is serving`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 0
        )
        assertEquals(HeroPillKind.MAKING, pill?.kind)
        assertEquals(MakeItMeStrings.HERO_PILL_MAKING, pill?.text)
        assertTrue(pill?.showSpinner == true)
        assertNull(pill?.actionText)
        // GEN-5 (RW-28): EVERY pill status leads with the script —
        // the generating pill included (A-13/B-14).
        assertTrue(pill?.showScriptMark == true)
        assertTrue(pill?.showLock == false)
        // GEN-5 C-4 AMENDED: the FREE-tier generating pill carries
        // the one small green upsell line, verbatim.
        assertEquals(MakeItMeStrings.HERO_PILL_MAKING_UPSELL, pill?.upsellText)
        assertEquals("Get it instantly next time", pill?.upsellText)
    }

    @Test
    fun `a subscriber's generating pill carries no upsell`() {
        // C-4 AMENDED scope limit: Premium generating states have
        // nothing left to sell — no upsell line.
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = false,
            mode = MakeItMeMode.SUBSCRIBED,
            freeGenerationsUsed = 5
        )
        assertEquals(HeroPillKind.MAKING, pill?.kind)
        assertNull(pill?.upsellText)
        assertTrue(pill?.showScriptMark == true)
    }

    @Test
    fun `gen-5 exhausted - at exactly 5 of 5 the free pill becomes the upsell status`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 5
        )
        assertEquals(HeroPillKind.EXHAUSTED, pill?.kind)
        assertEquals("5 of 5 used", pill?.text)
        assertEquals(MakeItMeStrings.HERO_PILL_GET_UNLIMITED, pill?.actionText)
        assertTrue(pill?.showScriptMark == true)
        assertTrue(pill?.wholePillAction == true)
        assertNull(pill?.upsellText)
    }

    @Test
    fun `gen-5 exhausted - at 4 of 5 the pill is still the ordinary free status`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 4
        )
        assertEquals(HeroPillKind.EXACT_FREE, pill?.kind)
        assertEquals("4 of 5 looks", pill?.text)
    }

    @Test
    fun `generating outranks a scaffold serve already on screen`() {
        // A regeneration for the cell in view is in flight while an
        // older scaffold photo serves: the pill speaks for the look
        // actually requested (X-2), not the older serve.
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.SCAFFOLD,
            heroIsPersonal = true,
            mode = MakeItMeMode.SUBSCRIBED,
            freeGenerationsUsed = 3
        )
        assertEquals(HeroPillKind.MAKING, pill?.kind)
    }

    @Test
    fun `a scaffold serve carries the script mark and Your photo - nothing more`() {
        // GEN-3 DESIGN DELTA (Sol, Nov 4): state (b) is the script
        // mark + "Your photo" — the "made for close-by weather"
        // line is OUT.
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.SCAFFOLD,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 1
        )
        assertEquals(HeroPillKind.SCAFFOLD, pill?.kind)
        assertEquals("Your photo", pill?.text)
        assertEquals(MakeItMeStrings.HERO_PILL_SCAFFOLD, pill?.text)
        assertTrue(pill?.showScriptMark == true)
        assertTrue(pill?.showLock == false)
        assertNull(pill?.actionText)
    }

    @Test
    fun `an exact personal photo on the free tier carries the count and the unlimited door`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 1
        )
        assertEquals(HeroPillKind.EXACT_FREE, pill?.kind)
        // GEN-3 DESIGN DELTA (Sol, Nov 4): "looks" wording per the
        // approved mock, and the script mark leads the state.
        assertEquals("1 of 5 looks", pill?.text)
        assertEquals(MakeItMeStrings.HERO_PILL_GET_UNLIMITED, pill?.actionText)
        assertTrue(pill?.showScriptMark == true)
        assertTrue(pill?.showLock == false)
    }

    @Test
    fun `an exact personal photo for a subscriber carries Premium status`() {
        val pill = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.SUBSCRIBED,
            freeGenerationsUsed = 5
        )
        assertEquals(HeroPillKind.PREMIUM, pill?.kind)
        assertEquals(MakeItMeStrings.HERO_PILL_PREMIUM, pill?.text)
        assertNull(pill?.actionText)
        // GEN-3 DESIGN DELTA (Sol, Nov 4): script mark + lock glyph.
        assertTrue(pill?.showScriptMark == true)
        assertTrue(pill?.showLock == true)
    }

    @Test
    fun `stock serves and quiet stock serves carry no pill`() {
        assertNull(
            HeroPillPolicy.resolve(
                lookStatus = PersonalLookStatus.None,
                serveKind = PersonalServeKind.EXACT,
                heroIsPersonal = false,
                mode = MakeItMeMode.STOCK,
                freeGenerationsUsed = 0
            )
        )
        assertNull(
            HeroPillPolicy.resolve(
                lookStatus = PersonalLookStatus.Ready,
                serveKind = PersonalServeKind.STOCK_QUIET,
                heroIsPersonal = true,
                mode = MakeItMeMode.FREE,
                freeGenerationsUsed = 2
            )
        )
        // An exact serve kind without a personal photo on screen is
        // not a personal state: no pill.
        assertNull(
            HeroPillPolicy.resolve(
                lookStatus = PersonalLookStatus.Ready,
                serveKind = PersonalServeKind.EXACT,
                heroIsPersonal = false,
                mode = MakeItMeMode.FREE,
                freeGenerationsUsed = 2
            )
        )
    }

    @Test
    fun `Y-6 - the pill marks the personal photo in the very frame it lands`() {
        // The iOS defect: Sol's own photo crossfaded in silently,
        // unmarked — a user who blinked missed the only moment his
        // photo arrived. The landing write flips heroIsPersonal and
        // the status together, so the pill derivation must move from
        // MAKING to the marked state with no unmarked frame between:
        // the frame before landing carries the making pill, the
        // landing frame itself carries the count.
        val before = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 0
        )
        assertEquals(HeroPillKind.MAKING, before?.kind)
        val landing = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Ready,
            serveKind = PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 1
        )
        assertEquals(HeroPillKind.EXACT_FREE, landing?.kind)
        assertEquals("1 of 5 looks", landing?.text)
    }

    @Test
    fun `a failed look carries no pill - its durable line lives in the sheet`() {
        assertNull(
            HeroPillPolicy.resolve(
                lookStatus = PersonalLookStatus.Failed(
                    PersonalLookStatus.Reason.ERROR
                ),
                serveKind = PersonalServeKind.EXACT,
                heroIsPersonal = false,
                mode = MakeItMeMode.FREE,
                freeGenerationsUsed = 0
            )
        )
    }
}

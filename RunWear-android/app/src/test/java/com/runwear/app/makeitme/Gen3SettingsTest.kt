package com.runwear.app.makeitme

import com.runwear.app.ui.screens.OnboardingCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gen-3 lane A-SETTINGS (Nov 4, 2026): WS-D (Settings redesign),
 * X-9 (meter truthfulness), X-16 (casing), WS-E (welcome third
 * point). WS-F is pinned in [MakeItMePriceLabelTest].
 */
class Gen3SettingsTest {


    // ── X-9 — the meter can never contradict the flow ──────────

    @Test
    fun `in-flight block shows delivered count plus pending state, never a settled number`() {
        val block = FreeLooksMeter.block(deliveredUsed = 0, looksMaking = 1, subscribed = false)
        assertEquals(2, block.size)
        // The status line carries the in-flight state…
        assertEquals(MakeItMeStrings.S26_MAKING_STATUS, block[0])
        assertEquals("Making your look…", block[0])
        // …and the meter line states the delivered count WITH the
        // pending look in the same line — not a bare settled "0 of 5".
        assertEquals("0 of 5 free looks used · 1 look is being made", block[1])
        assertFalse(block[1] == MakeItMeStrings.s4FreeUsed(0))
    }

    @Test
    fun `settled block is the status line plus the delivered count`() {
        assertEquals(
            listOf("You're in your photos.", "1 of 5 free looks used"),
            FreeLooksMeter.block(deliveredUsed = 1, looksMaking = 0, subscribed = false)
        )
        assertEquals(
            listOf("You're in your photos.", "All 5 free looks used"),
            FreeLooksMeter.block(deliveredUsed = 5, looksMaking = 0, subscribed = false)
        )
    }

    @Test
    fun `in-flight block counts the looks actually being made`() {
        // GEN-5 (RW-29 FA-10): three looks in flight read as
        // three — the gen-4 Boolean seam always printed one.
        val block = FreeLooksMeter.block(deliveredUsed = 2, looksMaking = 3, subscribed = false)
        assertEquals("2 of 5 free looks used · 3 looks are being made", block[1])
    }

    @Test
    fun `subscribed block carries no meter`() {
        val block = FreeLooksMeter.block(deliveredUsed = 3, looksMaking = 0, subscribed = true)
        assertEquals(
            listOf(MakeItMeStrings.S3_STATUS, MakeItMeStrings.S5_STATE_C_SUB),
            block
        )
        assertFalse(block.any { it.contains("free looks used") })
    }

    @Test
    fun `paywall meter is the verbatim settled string in every state`() {
        // GEN-5 (RW-28 item 29): the meter is the verbatim string —
        // no in-flight prose is appended to it any more; the
        // generating state is carried by the pill and the band.
        assertEquals(
            MakeItMeStrings.u2MeterLine(0),
            FreeLooksMeter.paywallLine(0, lookMaking = true)
        )
        assertEquals(
            "2 of 5 used · Free looks never expire — and your first ones take about a minute to make.",
            FreeLooksMeter.paywallLine(2, lookMaking = false)
        )
    }

    // ── WS-D — the Premium offer card ──────────────────────────

    @Test
    fun `offer card carries name, parameterized price with term, benefit and a real CTA`() {
        val card = MakeItMeStrings.premiumOfferCard(annualFormatted = "$9.99", subscribed = false)
        assertEquals("Premium", card.title)
        // GEN-5 (RW-28 item 26): the compact term form "$9.99/yr".
        assertEquals("$9.99/yr", card.priceLine)
        assertEquals(MakeItMeStrings.S18_SETTINGS_PREMIUM_LINE, card.benefitLine)
        assertEquals("Get Premium", card.ctaLabel)
        assertFalse(card.subscribed)
        // Parameterized: another arm's fetched price flows through.
        assertEquals(
            "$29.99/yr",
            MakeItMeStrings.premiumOfferCard("$29.99", subscribed = false).priceLine
        )
        // Unresolved offering: no price line yet, never a hardcoded one.
        assertNull(MakeItMeStrings.premiumOfferCard(null, subscribed = false).priceLine)
        // C-5: the monthly price rides the card when the offering
        // carries it; absent until then, never hardcoded.
        assertEquals(
            "Monthly — $0.99/month",
            MakeItMeStrings.premiumOfferCard(
                "$9.99", subscribed = false, monthlyFormatted = "$0.99"
            ).monthlyLine
        )
        assertNull(card.monthlyLine)
    }

    @Test
    fun `subscribed card shows the subscribed state instead of the offer`() {
        val card = MakeItMeStrings.premiumOfferCard(annualFormatted = "$9.99", subscribed = true)
        assertEquals("Premium", card.title)
        assertEquals(MakeItMeStrings.S28_SUBSCRIBED_STATE, card.benefitLine)
        assertNull(card.priceLine)
        assertNull(card.ctaLabel)
        assertTrue(card.subscribed)
    }

    @Test
    fun `settings structure is status, photos, premium card - destructive quarantined in the danger zone`() {
        val rows = MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.FREE)
        assertEquals(MakeItMeStrings.S6_PHOTOS_ROW, rows.first())
        assertEquals("Premium", rows[1])
        // GEN-5 (RW-28 item 25): no destructive row inside the
        // section; the Danger zone carries both, data last.
        assertFalse(rows.contains(MakeItMeStrings.S10_DELETE_ROW))
        assertEquals(
            MakeItMeStrings.S20_DELETE_DATA_ROW,
            MakeItMeSettingsPresentation.dangerZoneRowTitles(MakeItMeMode.FREE).last()
        )
    }

    // ── X-16 — casing audit over the whole deck ────────────────

    @Test
    fun `no deck string anywhere says Make it me`() {
        val offenders = mutableListOf<String>()
        for (field in MakeItMeStrings::class.java.declaredFields) {
            if (field.type == String::class.java) {
                field.isAccessible = true
                val value = field.get(MakeItMeStrings) as? String ?: continue
                if (value.contains("Make it me")) offenders += "${field.name}=$value"
            }
        }
        for (field in OnboardingCopy::class.java.declaredFields) {
            if (field.type == String::class.java) {
                field.isAccessible = true
                val value = field.get(OnboardingCopy) as? String ?: continue
                if (value.contains("Make it me")) offenders += "${field.name}=$value"
            }
        }
        assertTrue("casing offenders: $offenders", offenders.isEmpty())
        assertEquals("Make It Me", MakeItMeStrings.S1_SECTION_HEADER)
        assertEquals("Make It Me", MakeItMeStrings.F3_OPTION_A_TITLE)
    }

    // ── WS-E → GEN-3 ROUND 2 — welcome third feature point ─────
    // The WS-E headline/line ("Remember every run" / "Wear I've Run
    // turns…") is SUPERSEDED by Sol's name-led composition: the
    // point leads with "Wear I've Run" and the phrase no longer
    // repeats the name.

    @Test
    fun `welcome third point is the name-led composition`() {
        assertEquals("Wear I've Run", OnboardingCopy.PROMISE_3_NAME)
        assertEquals(
            "Turn each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.",
            OnboardingCopy.PROMISE_3_PHRASE
        )
    }
}

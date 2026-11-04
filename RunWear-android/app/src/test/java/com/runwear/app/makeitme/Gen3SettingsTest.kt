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
        val block = FreeLooksMeter.block(deliveredUsed = 0, lookMaking = true, subscribed = false)
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
            FreeLooksMeter.block(deliveredUsed = 1, lookMaking = false, subscribed = false)
        )
        assertEquals(
            listOf("You're in your photos.", "All 5 free looks used"),
            FreeLooksMeter.block(deliveredUsed = 5, lookMaking = false, subscribed = false)
        )
    }

    @Test
    fun `subscribed block carries no meter`() {
        val block = FreeLooksMeter.block(deliveredUsed = 3, lookMaking = false, subscribed = true)
        assertEquals(
            listOf(MakeItMeStrings.S3_STATUS, MakeItMeStrings.S5_STATE_C_SUB),
            block
        )
        assertFalse(block.any { it.contains("free looks used") })
    }

    @Test
    fun `paywall meter shares the seam and the in-flight truth`() {
        assertTrue(
            FreeLooksMeter.paywallLine(0, lookMaking = true)
                .contains("One look is being made right now.")
        )
        assertFalse(
            FreeLooksMeter.paywallLine(0, lookMaking = false)
                .contains("being made")
        )
    }

    // ── WS-D — the Premium offer card ──────────────────────────

    @Test
    fun `offer card carries name, parameterized price with term, benefit and a real CTA`() {
        val card = MakeItMeStrings.premiumOfferCard(annualFormatted = "$9.99", subscribed = false)
        assertEquals("Premium", card.title)
        assertEquals("$9.99 / year", card.priceLine)
        assertEquals(MakeItMeStrings.S18_SETTINGS_PREMIUM_LINE, card.benefitLine)
        assertEquals("Get Premium", card.ctaLabel)
        assertFalse(card.subscribed)
        // Parameterized: another arm's fetched price flows through.
        assertEquals(
            "$29.99 / year",
            MakeItMeStrings.premiumOfferCard("$29.99", subscribed = false).priceLine
        )
        // Unresolved offering: no price line yet, never a hardcoded one.
        assertNull(MakeItMeStrings.premiumOfferCard(null, subscribed = false).priceLine)
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
    fun `settings structure is status, photos, premium card, destructive last`() {
        val rows = MakeItMeSettingsPresentation.rowTitles(true, MakeItMeMode.FREE)
        assertEquals(MakeItMeStrings.S6_PHOTOS_ROW, rows.first())
        assertEquals("Premium", rows[1])
        assertEquals(MakeItMeStrings.S10_DELETE_ROW, rows.last())
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

    // ── WS-E — welcome third feature point ─────────────────────

    @Test
    fun `welcome carries the third feature point verbatim`() {
        assertEquals("Remember every run", OnboardingCopy.PROMISE_3_TITLE)
        assertEquals(
            "Wear I've Run turns each day's outfit into a card you can share — a scrapbook of what you wore and where you ran.",
            OnboardingCopy.PROMISE_3_BODY
        )
    }
}

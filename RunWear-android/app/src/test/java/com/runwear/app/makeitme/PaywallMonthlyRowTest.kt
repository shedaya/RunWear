package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 PAYWALL MONTHLY — RESOLVED (Sol, Nov 4), superseding the
 * earlier "annual-only" confirm-only instruction: the monthly plan
 * IS displayed under the annual card. Its price is parameterized
 * from the fetched RevenueCat product (never a literal); annual
 * remains the default selection; the per-run fine print is
 * unchanged.
 *
 * RW-100 (Sol, Nov 6, 2026) — supersedes the "quiet secondary
 * row" form: monthly is now an explicit, equally selectable plan
 * option (the shared PlanOptionRow: radio + selection tint +
 * pine border), the annual card's highlight follows the
 * selection instead of dressing it permanently, and the one
 * confirm button NAMES the selected plan and its price.
 * Select-then-confirm stands.
 */
class PaywallMonthlyRowTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "RunWear-android/", "app/")) {
                val file = File(dir, prefix + androidRootRelative)
                if (file.exists()) return file
            }
            dir = dir.parentFile
        }
        error("source file not found: $androidRootRelative")
    }

    private fun paywallSheet(): String {
        val src = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
        return src.substringAfter("fun MakeItMePaywallSheet(")
            .substringBefore("\nprivate class PackagePrice(")
    }

    @Test
    fun `the monthly label renders the fetched product price - never a literal`() {
        // GEN-5 (item 29): "Monthly — $0.99/month" form of record.
        assertEquals("Monthly — $0.99/month", MakeItMeStrings.monthlyRowLabel("$0.99"))
        assertEquals("Monthly — $1.49/month", MakeItMeStrings.monthlyRowLabel("$1.49"))
        // The fallback label and the row label builder carry no
        // price of their own.
        assertEquals("Monthly", MakeItMeStrings.U2B_MONTHLY)
        assertFalse(MakeItMeStrings.U2B_MONTHLY.contains("$"))
    }

    @Test
    fun `exactly one monthly option row, under the annual card`() {
        val sheet = paywallSheet()
        // ONE monthly option row in the whole sheet (RW-100: the
        // shared PlanOptionRow form).
        assertEquals(1, sheet.split("PlanOptionRow(").size - 1)
        // The settled order — annual card, monthly option, ONE
        // call to action below both, fine print.
        val annualCardAt = sheet.indexOf("RunWearColors.PaywallTint")
        val monthlyRowAt = sheet.indexOf("PlanOptionRow(")
        val ctaAt = sheet.indexOf("buy(if (annualSelected) annual else monthly)")
        val finePrintAt = sheet.indexOf("MR.strings.mim_u2c_fine_print")
        assertTrue("annual card present", annualCardAt >= 0)
        assertTrue("monthly row under the annual card", monthlyRowAt > annualCardAt)
        assertTrue("the one CTA sits below both plans", ctaAt > monthlyRowAt)
        assertTrue("fine print below the monthly row", finePrintAt > monthlyRowAt)
        // The row is fed from the fetched offering; tapping it
        // SELECTS monthly (FA-9) — it does not purchase.
        assertTrue(sheet.contains("label = monthly?.label ?: stringResource(MR.strings.mim_u2b_monthly)"))
        assertTrue(sheet.contains("enabled = monthly != null && !purchasing"))
        assertTrue(sheet.contains("onClick = { annualSelected = false }"))
        assertFalse(sheet.contains("onClick = { buy(monthly) }"))
    }

    @Test
    fun `annual is the default selection and the highlight follows the selection - RW-100`() {
        val sheet = paywallSheet()
        // Annual selected by default; the sheet's only primary
        // Button sits below both plans and buys the selected one.
        assertTrue(sheet.contains("annualSelected by remember { mutableStateOf(true) }"))
        assertTrue(sheet.contains("text = annual?.label ?: stringResource(MR.strings.mim_u2b_annual)"))
        assertTrue(sheet.contains("buy(if (annualSelected) annual else monthly)"))
        assertTrue(
            sheet.contains(
                "enabled = (if (annualSelected) annual else monthly) != null && !purchasing"
            )
        )
        // RW-100: the annual card's tint is CONDITIONAL on the
        // selection (it no longer reads as chosen while Monthly
        // is selected), and both options carry the radio
        // indicator (the card inline, the row via PlanOptionRow).
        assertTrue(sheet.contains("if (annualSelected)"))
        assertTrue(sheet.contains("PlanRadioIndicator("))
        // The CTA names the selected plan and its price.
        assertTrue(sheet.contains("MakeItMeStrings.planCtaLabel("))
        // The shared option row itself registers selection with
        // the primary pine (border + radio), per RW-100.
        val row = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
            .substringAfter("fun PlanOptionRow")
            .substringBefore("\n}")
        assertTrue(row.contains("RunWearColors.Primary"))
        assertTrue(row.contains("PlanRadioIndicator(selected = selected)"))
    }

    @Test
    fun `the confirm button label names the plan and its price - RW-100`() {
        // Composed from the fetched formatted price, both plans.
        assertEquals(
            "Get Premium — Annual, $9.99/yr",
            MakeItMeStrings.planCtaLabel("Annual", "$9.99/yr")
        )
        assertEquals(
            "Get Premium — Monthly, $0.99/month",
            MakeItMeStrings.planCtaLabel("Monthly", "$0.99/month")
        )
        // No price resolved yet: the plain form, never a guess.
        assertEquals(
            MakeItMeStrings.U2I_CTA,
            MakeItMeStrings.planCtaLabel("Annual", null)
        )
    }

    @Test
    fun `the fine print names the store this build is sold through`() {
        // GEN-5 (item 29): Android's fine print names Google Play
        // only — the gen-4 text named both stores on both platforms.
        // GEN-6 (DA-11): the withdrawn "about 10 seconds" figure is
        // gone — the truthful "about a minute" family stands.
        assertEquals(
            "Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about a minute to make the first time — after that, it's ready instantly. Cancel any time in your Google Play subscriptions.",
            MakeItMeStrings.U2C_FINE_PRINT
        )
        assertFalse(MakeItMeStrings.U2C_FINE_PRINT.contains("App Store"))
        assertFalse(MakeItMeStrings.U2C_FINE_PRINT.contains("10 seconds"))
    }

    @Test
    fun `the per-run line renders directly under the annual price`() {
        // GEN-5 (item 29): the per-run line left the fine print
        // and sits under the price inside the Premium card.
        val sheet = paywallSheet()
        val priceAt = sheet.indexOf("annual?.label ?: stringResource(MR.strings.mim_u2b_annual)")
        val perRunAt = sheet.indexOf("MakeItMeStrings.perRunLine")
        val ctaAt = sheet.indexOf("buy(if (annualSelected) annual else monthly)")
        assertTrue("price present", priceAt >= 0)
        assertTrue("per-run line under the price", perRunAt > priceAt)
        assertTrue("per-run line above the CTA", perRunAt < ctaAt)
    }
}

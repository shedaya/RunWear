package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-3 PAYWALL MONTHLY — RESOLVED (Sol, Nov 4), superseding the
 * earlier "annual-only" confirm-only instruction: the monthly plan
 * IS displayed as ONE quiet secondary row under the annual hero
 * card. Its price is parameterized from the fetched RevenueCat
 * product (never a literal); annual remains the hero and the
 * default selection (the card's primary CTA buys annual); the
 * per-run fine print is unchanged.
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
    fun `exactly one monthly row, secondary under the annual hero card`() {
        val sheet = paywallSheet()
        // ONE monthly row in the whole sheet.
        assertEquals(1, sheet.split("PaywallPriceRow(").size - 1)
        // GEN-5 (RW-29 FA-9): the settled order — annual card,
        // monthly row, ONE call to action below both, fine print.
        val annualCardAt = sheet.indexOf("RunWearColors.PaywallTint")
        val monthlyRowAt = sheet.indexOf("PaywallPriceRow(")
        val ctaAt = sheet.indexOf("buy(if (annualSelected) annual else monthly)")
        val finePrintAt = sheet.indexOf("MakeItMeStrings.U2C_FINE_PRINT")
        assertTrue("annual card present", annualCardAt >= 0)
        assertTrue("monthly row under the annual card", monthlyRowAt > annualCardAt)
        assertTrue("the one CTA sits below both plans", ctaAt > monthlyRowAt)
        assertTrue("fine print below the monthly row", finePrintAt > monthlyRowAt)
        // The row is fed from the fetched offering; tapping it
        // SELECTS monthly (FA-9) — it no longer purchases.
        assertTrue(sheet.contains("label = monthly?.label ?: MakeItMeStrings.U2B_MONTHLY"))
        assertTrue(sheet.contains("enabled = monthly != null && !purchasing"))
        assertTrue(sheet.contains("onClick = { annualSelected = false }"))
        assertFalse(sheet.contains("onClick = { buy(monthly) }"))
    }

    @Test
    fun `annual remains the hero and the default selection`() {
        val sheet = paywallSheet()
        // The ONE highlighted card: the design's pale pine tint
        // (cream map) inside a pine border, annual selected by
        // default, and the sheet's only primary Button — below
        // both plans, buying the selected one (FA-9).
        assertTrue(sheet.contains("color = RunWearColors.PaywallTint"))
        assertTrue(sheet.contains("annualSelected by remember { mutableStateOf(true) }"))
        assertTrue(sheet.contains("text = annual?.label ?: MakeItMeStrings.U2B_ANNUAL"))
        assertTrue(sheet.contains("buy(if (annualSelected) annual else monthly)"))
        assertTrue(
            sheet.contains(
                "enabled = (if (annualSelected) annual else monthly) != null && !purchasing"
            )
        )
        // The monthly row is a plain (non-primary) row composable,
        // not a second hero: it renders as a TextButton-style row.
        val row = sourceFile(
            "app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt"
        ).readText()
            .substringAfter("private fun PaywallPriceRow")
            .substringBefore("\n}")
        assertFalse(row.contains("RunWearColors.Primary"))
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
        val priceAt = sheet.indexOf("annual?.label ?: MakeItMeStrings.U2B_ANNUAL")
        val perRunAt = sheet.indexOf("MakeItMeStrings.perRunLine")
        val ctaAt = sheet.indexOf("buy(if (annualSelected) annual else monthly)")
        assertTrue("price present", priceAt >= 0)
        assertTrue("per-run line under the price", perRunAt > priceAt)
        assertTrue("per-run line above the CTA", perRunAt < ctaAt)
    }
}

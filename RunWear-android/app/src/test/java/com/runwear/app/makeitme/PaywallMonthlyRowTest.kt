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
        assertEquals("$0.99 / month", MakeItMeStrings.monthlyRowLabel("$0.99"))
        assertEquals("$1.49 / month", MakeItMeStrings.monthlyRowLabel("$1.49"))
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
        // Position: the annual hero card (with its buy(annual) CTA)
        // comes first; the monthly row follows it; the fine print
        // follows the row.
        val annualCtaAt = sheet.indexOf("buy(annual)")
        val monthlyRowAt = sheet.indexOf("PaywallPriceRow(")
        val finePrintAt = sheet.indexOf("MakeItMeStrings.finePrint")
        assertTrue("annual CTA present", annualCtaAt >= 0)
        assertTrue("monthly row under the annual card", monthlyRowAt > annualCtaAt)
        assertTrue("fine print below the monthly row", finePrintAt > monthlyRowAt)
        // The row is fed from the fetched offering and buys monthly
        // only on its own tap; it is inert until the product loads.
        assertTrue(sheet.contains("label = monthly?.label ?: MakeItMeStrings.U2B_MONTHLY"))
        assertTrue(sheet.contains("enabled = monthly != null && !purchasing"))
        assertTrue(sheet.contains("onClick = { buy(monthly) }"))
    }

    @Test
    fun `annual remains the hero and the default selection`() {
        val sheet = paywallSheet()
        // The ONE highlighted card: Primary border, annual price
        // slot, and the sheet's only primary Button — buying annual.
        assertTrue(sheet.contains("BorderStroke(1.5.dp, RunWearColors.Primary)"))
        assertTrue(sheet.contains("text = annual?.label ?: MakeItMeStrings.U2B_ANNUAL"))
        assertTrue(sheet.contains("onClick = { buy(annual) }"))
        assertTrue(sheet.contains("enabled = annual != null && !purchasing"))
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
    fun `the per-run fine print is unchanged`() {
        assertEquals(
            "Your outfits for your local weather are made when you subscribe. A brand-new outfit or a new place takes about 10 seconds to make the first time — after that, it's ready instantly. Cancel any time in your App Store or Google Play subscriptions.",
            MakeItMeStrings.U2C_FINE_PRINT
        )
    }
}

package com.runwear.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-5 (RW-28): the composed home order, pinned at the source
 * seam for all three layouts.
 *
 * C-3 (Sol's ruling): hero/headline → outfit items → hourly
 * chart (glance band) → Pro Tip → Wear I've Run. The gen-4 build
 * had the Pro Tip (and the feedback row) INSIDE the outfit list,
 * above the chart.
 *
 * Addendum 4 (Sol, Nov 5, 2026 ~06:35 CST): while a Premium
 * user's bank is generating, the "Making your looks" band
 * renders DIRECTLY BELOW THE OUTFIT AREA — above the chart,
 * above the Pro Tip, above the feedback row. When not
 * generating, the band is absent and the C-3 order stands.
 *
 * RW-101 (Sol's direction, Nov 6, 2026 — SUPERSEDES the
 * Addendum-4 seat): the band now renders DIRECTLY UNDER THE
 * HERO, ahead of the outfit area, in every layout, so the
 * subscriber sees what is happening the moment the screen
 * opens. Order: hero → band → outfit → chart → trailing.
 */
class Gen5HomeOrderTest {

    private fun heroMainScreen(): String {
        val candidates = listOf(
            File("src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"),
            File("app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"),
            File("../app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("HeroMainScreen.kt not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private fun section(source: String, from: String, to: String): String {
        val start = source.indexOf(from)
        assertTrue("section start missing: $from", start >= 0)
        val end = source.indexOf(to, start)
        assertTrue("section end missing: $to", end > start)
        return source.substring(start, end)
    }

    @Test
    fun `the outfit list no longer carries the pro tip or the feedback row`() {
        val source = heroMainScreen()
        val outfit = section(
            source,
            "private fun LazyListScope.outfitListItems",
            "private fun LazyListScope.homeTrailingListItems"
        )
        assertTrue(
            "Pro Tip must not render inside the outfit list",
            !outfit.contains("ProTipCard(")
        )
        assertTrue(
            "feedback row must not render inside the outfit list",
            !outfit.contains("OutfitFeedbackRow(")
        )
        val outfitGrid = section(
            source,
            "private fun LazyGridScope.outfitGridItems",
            "private fun LazyGridScope.homeTrailingGridItems"
        )
        assertTrue(
            "Pro Tip must not render inside the outfit grid",
            !outfitGrid.contains("ProTipCard(")
        )
    }

    @Test
    fun `trailing blocks carry pro tip then feedback`() {
        val source = heroMainScreen()
        val trailing = section(
            source,
            "private fun LazyListScope.homeTrailingListItems",
            "private fun LazyGridScope.outfitGridItems"
        )
        val tipAt = trailing.indexOf("TipsSection(")
        val feedbackAt = trailing.indexOf("Not what you'd wear? Tell us")
        assertTrue("Pro Tip in the list trailing block", tipAt >= 0)
        assertTrue("feedback row follows the Pro Tip", feedbackAt > tipAt)

        val gridTrailing = section(
            source,
            "private fun LazyGridScope.homeTrailingGridItems",
            "private fun HeroFlagSheet("
        )
        assertTrue(
            "Pro Tip in the grid trailing block",
            gridTrailing.contains("TipsSection(")
        )
        assertTrue(
            "feedback row in the grid trailing block",
            gridTrailing.contains("Not what you'd wear? Tell us")
        )
    }

    @Test
    fun `every layout composes band then outfit then chart then trailing`() {
        val source = heroMainScreen()
        // RW-101 order, per layout: the band call sits BEFORE the
        // outfit call (directly under the hero), and the chart
        // and trailing blocks follow the outfit.
        val outfitCalls = listOf("outfitListItems(", "outfitGridItems(")
        var searchFrom = 0
        var sites = 0
        while (true) {
            val next = outfitCalls
                .map { source.indexOf(it, searchFrom) }
                .filter { it >= 0 }
                .minOrNull() ?: break
            // Skip the two function DEFINITIONS (they are not call
            // sites and contain no band/chart/trailing sequence).
            val isDefinition = source.substring(
                maxOf(0, next - 40), next
            ).contains("fun Lazy")
            if (isDefinition) {
                searchFrom = next + 1
                continue
            }
            val bandAt = source.lastIndexOf("ConvergenceBand(", next)
            val chartAt = source.indexOf("dayChartBandContent()", next)
            val trailingAt = source.indexOf("homeTrailing", next)
            assertTrue("band sits under the hero, ahead of the outfit block", bandAt in 0 until next)
            assertTrue("chart follows the outfit block", chartAt > next)
            assertTrue("trailing follows the chart", trailingAt > chartAt)
            sites++
            searchFrom = trailingAt + 1
        }
        assertTrue("expected the three layout call sites, found $sites", sites == 3)
    }
}

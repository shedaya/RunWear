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
        val tipAt = trailing.indexOf("ProTipCard(")
        val feedbackAt = trailing.indexOf("OutfitFeedbackRow(")
        assertTrue("Pro Tip in the list trailing block", tipAt >= 0)
        assertTrue("feedback row follows the Pro Tip", feedbackAt > tipAt)

        val gridTrailing = section(
            source,
            "private fun LazyGridScope.homeTrailingGridItems",
            "private fun HeroFlagSheet("
        )
        assertTrue(
            "Pro Tip in the grid trailing block",
            gridTrailing.contains("ProTipCard(")
        )
        assertTrue(
            "feedback row in the grid trailing block",
            gridTrailing.contains("OutfitFeedbackRow(")
        )
    }

    @Test
    fun `every layout composes outfit then band then chart then trailing`() {
        val source = heroMainScreen()
        // Slice each layout from its outfit call to just past its
        // trailing call; inside the slice the order must be
        // outfit → band → chart → trailing.
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
            val bandAt = source.indexOf("ConvergenceBand(", next)
            val chartAt = source.indexOf("dayChartBandContent()", next)
            val trailingAt = source.indexOf("homeTrailing", next)
            assertTrue("band follows the outfit block", bandAt > next)
            assertTrue("chart follows the band", chartAt > bandAt)
            assertTrue("trailing follows the chart", trailingAt > chartAt)
            sites++
            searchFrom = trailingAt + 1
        }
        assertTrue("expected the three layout call sites, found $sites", sites == 3)
    }
}

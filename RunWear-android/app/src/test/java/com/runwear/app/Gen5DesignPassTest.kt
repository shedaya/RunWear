package com.runwear.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-5 design pass (RW-28 Addendum 5, Sol, Nov 5, 2026): the
 * approved cream + Warm Charcoal + pine design system (design of
 * record, Nov 2, 2026) applied to EVERY Android surface. The
 * phone palettes already carried the tokens; the disease lived
 * in the surfaces that never joined the system — the Wear theme
 * (retired teal primary, cool-grey surfaces), the home-screen
 * widget and the Wear tile (retired cool-grey palette), and
 * pure-white ink where the design's warm ink #F5F1EA belongs.
 * These pins keep the retired values out.
 */
class Gen5DesignPassTest {

    private fun read(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("$relative not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun `wear theme carries the charcoal pair and the pine family`() {
        val theme = read("wear/src/main/java/com/runwear/wear/presentation/theme/Theme.kt")
        assertTrue(theme.contains("0xFF16130F")) // charcoal ground
        assertTrue(theme.contains("0xFF211D17")) // charcoal sheet
        assertTrue(theme.contains("0xFF2A251E")) // charcoal card
        assertTrue(theme.contains("0xFFF5F1EA")) // warm ink
        assertTrue(theme.contains("0xFFB8AEA0")) // muted
        assertTrue(theme.contains("0xFF5CC79F")) // pine accent on dark
        assertTrue(theme.contains("0xFF175E4C")) // pine seed
        assertFalse("retired teal must not return", theme.contains("0xFF00796B"))
    }

    @Test
    fun `widget palette is the warm charcoal pair, not the retired cool greys`() {
        val widget = read("src/main/java/com/runwear/app/widget/OutfitWidget.kt")
        assertTrue(widget.contains("0xFF16130F"))
        assertTrue(widget.contains("0xFF2A251E"))
        assertTrue(widget.contains("0xFFF5F1EA"))
        assertTrue(widget.contains("0xFF5CC79F"))
        assertFalse(widget.contains("0xFF0A0A0A"))
        assertFalse(widget.contains("0xFF00796B"))
    }

    @Test
    fun `wear tile palette matches the design pair`() {
        val tile = read("wear/src/main/java/com/runwear/wear/tiles/OutfitTileService.kt")
        assertTrue(tile.contains("0xFFF5F1EA"))
        assertTrue(tile.contains("0xFF5CC79F"))
        assertFalse(tile.contains("0xFF00796B"))
        assertFalse(tile.contains("0xFFB3B3B3"))
    }

    @Test
    fun `phone palettes still carry the exact design tokens`() {
        val theme = read("src/main/java/com/runwear/app/ui/theme/Theme.kt")
        // Light pair.
        assertTrue(theme.contains("0xFFF5F1E8")) // cream ground
        assertTrue(theme.contains("0xFFFDFBF6")) // sheet / cards
        assertTrue(theme.contains("0xFF33291F")) // RW-144 warm ink (was 0xFF17191C pre-RW-144)
        assertTrue(theme.contains("0xFF685D4F")) // RW-144 secondary ink
        // Dark pair.
        assertTrue(theme.contains("0xFF16130F"))
        assertTrue(theme.contains("0xFF211D17"))
        assertTrue(theme.contains("0xFF2A251E"))
        assertTrue(theme.contains("0xFFF5F1EA"))
        assertTrue(theme.contains("0xFFB8AEA0"))
    }
}

package com.runwear.app.weariverun

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-99 source pin (Android fix wave, Nov 6, 2026).
 *
 * The collection cover's scaled render cannot be pixel-proved
 * in the Robolectric harness (its manual draw does not apply
 * graphicsLayer scale/clip — probe-verified), so the fix's
 * structure is pinned at source level, the same pattern as the
 * repo's other source pins: PosterCover MUST compose the
 * poster canvas at its natural 360×450 via required-size
 * modifiers (which ignore the slot's coercive constraints —
 * the original defect) and scale it into the slot through the
 * graphics layer from the slot's own width.
 */
class Rw99PosterCoverSourcePinTest {

    private fun source(): String {
        val path =
            "src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
        val file = File(path)
        assertTrue("source file readable at $path", file.exists())
        return file.readText()
    }

    @Test
    fun `poster cover holds the canvas at natural size and scales it into the slot`() {
        val src = source()
        val coverStart = src.indexOf("private fun PosterCover")
        assertTrue("PosterCover exists", coverStart >= 0)
        val cover = src.substring(coverStart, src.indexOf("\n@Composable", coverStart + 10).takeIf { it > 0 } ?: src.length)
        assertTrue(
            "canvas held at natural width against the slot",
            cover.contains("requiredWidth(360.dp)")
        )
        assertTrue(
            "canvas held at natural height against the slot",
            cover.contains("requiredHeight(450.dp)")
        )
        assertTrue(
            "scale derives from the slot width over the canvas width",
            cover.contains("maxWidth.value / 360f")
        )
        assertTrue(
            "the layer scales from the top-left origin",
            cover.contains("TransformOrigin(0f, 0f)")
        )
        assertTrue(
            "the slot clips the scaled render",
            cover.contains("clipToBounds()")
        )
        assertTrue(
            "the hero loads through the display loader",
            cover.contains("rememberHeroBitmap(entry.heroRef)")
        )
    }
}

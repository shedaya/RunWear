package com.runwear.app.makeitme

import androidx.compose.ui.Alignment
import com.runwear.app.ui.components.HeroImageGeometry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * W-3 (Nov 4 walkthrough, GEN-2 lane A4) — the Make It Me fork's
 * header photo band cropped the runner at the chin: the band's
 * AsyncImage carried NO alignment, so Crop defaulted to CENTER and
 * discarded the top of the frame, head included. Home's hero — the
 * same serve — anchors TOP (HeroSection's BiasAlignment −1 at
 * rest) and shows the full head.
 *
 * Pins:
 *  - the fork band's alignment IS Alignment.TopCenter;
 *  - across the standard portrait phone matrix, the band's
 *    top-anchored slice reaches the bottom of the head band D-23
 *    audited on the home serve (source y 392–509 of 1200) — the
 *    full head is in frame;
 *  - the CENTER anchor the band used before discards a real top
 *    region of the frame at these sizes (the defect mechanism);
 *  - source-level: the rendered band actually passes the pinned
 *    alignment, and the fork root scrolls (the whole fork — the
 *    "Use standard photos" button included — stays reachable
 *    however tall the content runs).
 */
class ForkBandGeometryTest {

    private fun sourceFile(androidRootRelative: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        val candidates = listOf(
            androidRootRelative,
            "RunWear-android/$androidRootRelative",
            "../$androidRootRelative"
        )
        while (dir != null) {
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
            dir = dir.parentFile
        }
        fail("source file not found: $androidRootRelative (user.dir=${System.getProperty("user.dir")})")
        throw AssertionError("unreachable")
    }

    private fun makeItMeScreens(): String =
        sourceFile("app/src/main/java/com/runwear/app/makeitme/MakeItMeScreens.kt")
            .readText()

    @Test
    fun `the fork band is top-anchored, as home`() {
        assertEquals(Alignment.TopCenter, ForkBandGeometry.IMAGE_ALIGNMENT)
    }

    @Test
    fun `the top-anchored slice covers the audited head band across the portrait phone matrix`() {
        // Portrait phone widths (dp) × densities; the band is
        // ForkBandGeometry.BAND_HEIGHT_DP tall in every case.
        val widthsDp = listOf(320, 360, 390, 411, 430, 450)
        val densities = listOf(2.0f, 2.75f, 3.0f)
        for (widthDp in widthsDp) {
            for (density in densities) {
                val widthPx = widthDp * density
                val heightPx = ForkBandGeometry.BAND_HEIGHT_DP * density
                assertTrue(
                    "full head must be in the fork band at ${widthDp}dp ×$density",
                    ForkBandGeometry.headBandFullyVisible(widthPx, heightPx)
                )
            }
        }
    }

    @Test
    fun `the old center anchor discarded the top of the frame at band sizes`() {
        // The defect mechanism, in home's own geometry: a CENTER
        // anchor's slice starts at (sourceHeight − sliceHeight)/2,
        // strictly below the frame's top — whatever the serve
        // places up there (on the fork's serve: the head) is cut
        // before the slice begins. The top anchor starts at 0.
        val widthPx = 411f * 2.0f
        val heightPx = ForkBandGeometry.BAND_HEIGHT_DP * 2.0f
        val slice = HeroImageGeometry.sliceHeightSourcePx(widthPx, heightPx)
        val centerSliceTop =
            (HeroImageGeometry.SOURCE_HEIGHT_PX - slice) / 2f
        assertTrue(
            "center anchor must discard a real top region (was $centerSliceTop source px)",
            centerSliceTop > 100f
        )
    }

    @Test
    fun `the rendered fork band passes the pinned alignment and the fork scrolls`() {
        val source = makeItMeScreens()
        assertTrue(
            "the fork band's AsyncImage must pass ForkBandGeometry.IMAGE_ALIGNMENT",
            source.contains("alignment = ForkBandGeometry.IMAGE_ALIGNMENT")
        )
        assertTrue(
            "the band height must come from ForkBandGeometry.BAND_HEIGHT_DP",
            source.contains(".height(ForkBandGeometry.BAND_HEIGHT_DP.dp)")
        )
        // Reachability (W-3's second half): the fork root is a
        // vertical-scrolling column, so the full content — both
        // option cards, the "Use standard photos" button, and the
        // footer — is reachable at any viewport height.
        val forkStart = source.indexOf("fun MakeItMeForkScreen")
        val forkEnd = source.indexOf("private fun ForkOptionCard")
        assertTrue(forkStart in 0 until forkEnd)
        val forkBody = source.substring(forkStart, forkEnd)
        assertTrue(
            "the fork root must scroll",
            forkBody.contains(".verticalScroll(rememberScrollState())")
        )
        assertTrue(
            "the fork must render the standard-photos option card",
            forkBody.contains("MakeItMeStrings.F8_OPTION_B_BUTTON")
        )
    }
}

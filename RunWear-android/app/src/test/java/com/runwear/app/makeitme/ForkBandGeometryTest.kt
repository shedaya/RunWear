package com.runwear.app.makeitme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * GEN-5 (RW-28, ruling C-2): the fork band's geometry for the
 * purpose-designed hard-coded image (mim-fork-hero.webp,
 * 2048 × 1152) and its code-drawn dashed face box.
 *
 * The asset lane's anchoring rule is binding (plain centre-crop is
 * ruled OUT — the head has only 3.2% headroom in the image):
 *  - band aspect ≤ image aspect (1.778): the FULL image height is
 *    shown and the horizontal window is centred on the face
 *    centre x = 0.507, clamped to the image edges;
 *  - band aspect > image aspect: the FULL image width is shown
 *    and the vertical window is anchored to the TOP (y = 0).
 *
 * Pins: the rule's two branches, the face box's registration to
 * the face under the SAME transform as the image, the head's full
 * visibility across the asset lane's crop-test matrix, and the
 * source-level facts (bundled asset, cream placeholder, dashed
 * overlay, scrolling root).
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
    fun `narrow bands show the full image height with the window centred on the face`() {
        // 1080 x 900 (1.2:1) — full height shown, so the displayed
        // height equals the band height and the top is zero; the
        // face centre lands at the band's horizontal centre.
        val rect = ForkBandGeometry.displayedImageRect(1080f, 900f)
        assertEquals(900f, rect.height, 0.5f)
        assertEquals(0f, rect.top, 0.001f)
        val faceCentreX = rect.left + ForkBandGeometry.FACE_CENTRE_X_FRACTION * rect.width
        assertEquals(540f, faceCentreX, 1.0f)
    }

    @Test
    fun `the shipped-like band (1080x620) centres the face and keeps the full head`() {
        val rect = ForkBandGeometry.displayedImageRect(1080f, 620f)
        assertEquals(620f, rect.height, 0.5f)
        val faceCentreX = rect.left + ForkBandGeometry.FACE_CENTRE_X_FRACTION * rect.width
        assertEquals(540f, faceCentreX, 1.0f)
        assertTrue(ForkBandGeometry.headFullyVisible(1080f, 620f))
    }

    @Test
    fun `wide bands show the full image width anchored to the top`() {
        // 2560 x 1000 (2.56:1) — full width shown, window at y = 0,
        // so the head (top 3.2% of the image) is always in frame.
        val rect = ForkBandGeometry.displayedImageRect(2560f, 1000f)
        assertEquals(2560f, rect.width, 0.5f)
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertTrue(ForkBandGeometry.headFullyVisible(2560f, 1000f))
    }

    @Test
    fun `the head is fully visible across the asset lane's crop-test matrix`() {
        // Aspects from the README's crop tests, at a fixed height.
        for (aspect in listOf(0.86f, 1.0f, 1.2f, 1.5f, 1.74f, 2.32f, 2.56f)) {
            val height = 1000f
            val width = aspect * height
            assertTrue(
                "full head must be visible at aspect $aspect",
                ForkBandGeometry.headFullyVisible(width, height)
            )
        }
    }

    @Test
    fun `the face box is the normalised box under the same transform as the image`() {
        for ((w, h) in listOf(1080f to 900f, 1080f to 620f, 2560f to 1000f, 800f to 930f)) {
            val img = ForkBandGeometry.displayedImageRect(w, h)
            val box = ForkBandGeometry.faceBoxRect(w, h)
            assertEquals(img.left + 0.400f * img.width, box.left, 0.5f)
            assertEquals(img.top + 0.018f * img.height, box.top, 0.5f)
            assertEquals(0.214f * img.width, box.width, 0.5f)
            assertEquals(0.478f * img.height, box.height, 0.5f)
            // And the box actually contains the measured head box.
            val headLeft = img.left + 0.414f * img.width
            val headRight = img.left + 0.600f * img.width
            val headTop = img.top + 0.032f * img.height
            val headBottom = img.top + 0.482f * img.height
            assertTrue(box.left <= headLeft && box.left + box.width >= headRight)
            assertTrue(box.top <= headTop && box.top + box.height >= headBottom)
        }
    }

    @Test
    fun `source - bundled asset, cream placeholder, dashed overlay, scrolling root`() {
        val source = makeItMeScreens()
        val fork = source.substringAfter("fun MakeItMeForkScreen(")
            .substringBefore("private fun ForkOptionCard")
        assertTrue(fork.contains("R.drawable.mim_fork_hero"))
        // The designed loading ground is the cream Background
        // token, never white (A-5).
        assertTrue(fork.contains(".background(RunWearColors.Background)"))
        // The face box is a code overlay with the dashed language.
        assertTrue(fork.contains("dashPathEffect"))
        assertTrue(fork.contains("ForkBandGeometry.faceBoxRect"))
        assertTrue(fork.contains("ForkBandGeometry.displayedImageRect"))
        // The band's height comes from the geometry object.
        assertTrue(fork.contains(".height(ForkBandGeometry.BAND_HEIGHT_DP.dp)"))
        // The whole fork stays reachable: the root scrolls.
        assertTrue(fork.contains("verticalScroll(rememberScrollState())"))
    }
}

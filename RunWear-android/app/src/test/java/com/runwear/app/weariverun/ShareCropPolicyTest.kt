package com.runwear.app.weariverun

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-5 (RW-28 sharing fix): the headless-poster regression.
 * Sol's evidence (Nov 5, 2026): the Morning Poster rendered the
 * runner from the neck down — a portrait source centre-cropped
 * into the poster box. These tests pin the top-anchored crop
 * policy against that exact shape, and prove the centre crop
 * (the gen-4 behaviour) fails the same shape — so the test
 * would have caught the shipped defect.
 */
class ShareCropPolicyTest {

    /** The centre-crop window (gen-4 behaviour), for contrast. */
    private fun centerWindow(srcW: Float, srcH: Float, dstW: Float, dstH: Float):
        ShareCropPolicy.Window {
        val scale = maxOf(dstW / srcW, dstH / srcH)
        val visibleW = dstW / scale
        val visibleH = dstH / scale
        return ShareCropPolicy.Window(
            x = (srcW - visibleW) / 2f,
            y = (srcH - visibleH) / 2f,
            width = visibleW,
            height = visibleH
        )
    }

    @Test
    fun `evidence shape - portrait source into the poster box keeps the head`() {
        // Sol's evidence: a portrait runner photo (the TODAY
        // thumbnail shows the full runner) rendered into a wide
        // poster box — headless under the centre crop.
        val srcW = 1000f
        val srcH = 1400f
        val dstW = 1546f
        val dstH = 1150f

        val topAnchored = ShareCropPolicy.visibleSourceWindow(srcW, srcH, dstW, dstH)
        assertEquals(0f, topAnchored.y, 0.01f)
        assertTrue(
            "top-anchored window must contain the whole head zone",
            ShareCropPolicy.headZoneFullyVisible(srcH, topAnchored)
        )

        val centered = centerWindow(srcW, srcH, dstW, dstH)
        assertFalse(
            "the centre crop is the headless render — it must fail this shape",
            ShareCropPolicy.headZoneFullyVisible(srcH, centered)
        )
    }

    @Test
    fun `phone poster box keeps the head for portrait and landscape sources`() {
        // Android poster: fillMaxWidth x 420dp at a 360dp phone
        // (1080 x 1260 px at 3x density).
        val phonePoster = ShareCropPolicy.visibleSourceWindow(1000f, 1400f, 1080f, 1260f)
        assertTrue(ShareCropPolicy.headZoneFullyVisible(1400f, phonePoster))

        val landscape = ShareCropPolicy.visibleSourceWindow(1400f, 1000f, 1080f, 1260f)
        assertEquals(0f, landscape.y, 0.01f)
        assertTrue(ShareCropPolicy.headZoneFullyVisible(1000f, landscape))
    }

    @Test
    fun `window is horizontally centred and never exceeds the source`() {
        val window = ShareCropPolicy.visibleSourceWindow(1200f, 900f, 600f, 600f)
        assertTrue(window.x >= 0f)
        assertTrue(window.x + window.width <= 1200f + 0.01f)
        assertEquals(150f, window.x, 0.5f)
    }

    @Test
    fun `poster and thumbnails render the top-anchored crop`() {
        // GEN-6 Wave B reconciliation: the gen-5 mechanism (Coil
        // Alignment.TopCenter on the poster and thumbnails) is
        // superseded by the anchor crop — the poster and the
        // collection covers now draw a bitmap pre-cropped by
        // ShareAnchorCrop.cropBitmap from the face anchor (whose
        // default IS the upper-middle composition point, and whose
        // placement rule keeps the face in the upper third with
        // headroom — the headless-poster fix, strengthened). The
        // pin follows the mechanism to its new homes.
        val screen = sourceFile("WearIveRunCollectionScreen.kt")
        // RE-PINNED (RW-152 §3, Nov 10, 2026 — supersedes the
        // poster-composition crop for COLLECTION surfaces):
        // covers and stay cards no longer compose the poster, so
        // they no longer crop through the face anchor — they load
        // the photo directly with the settled top-centre crop.
        // The face-anchor crop remains the SHARE/EXPORT rule
        // (asserted below, unchanged).
        assertTrue(
            "collection covers no longer crop through the face anchor (RW-152 §3)",
            !screen.contains("ShareAnchorCrop.cropBitmap")
        )
        assertTrue(
            "collection thumbnails use the settled top-centre crop",
            screen.contains("Alignment.TopCenter")
        )
        val renderer = sourceFile("ShareExportRenderer.kt")
        assertTrue(
            "the export render crops through the face anchor",
            renderer.contains("ShareAnchorCrop.cropBitmap")
        )
    }

    private fun sourceFile(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/runwear/app/weariverun/$name"),
            File("app/src/main/java/com/runwear/app/weariverun/$name"),
            File("../app/src/main/java/com/runwear/app/weariverun/$name")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("$name not found from ${File(".").absolutePath}")
        return file.readText()
    }
}

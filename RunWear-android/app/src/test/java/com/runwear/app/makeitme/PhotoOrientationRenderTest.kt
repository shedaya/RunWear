package com.runwear.app.makeitme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WS-6 (F-1): the EXIF-at-decode seam exercised at the pixel level.
 * For each camera EXIF orientation, a stored bitmap is manufactured
 * the way the sensor pipeline stores it (the upright scene run
 * through the INVERSE of the tag's correction) and
 * [PhotoQualityChecker.applyExifOrientation] must restore the
 * upright scene pixel-for-pixel — the stored artifact the checker
 * goes on to measure and encode is upright at the source.
 *
 * The scene is a 3x2 grid of unique colors: any rotation or flip
 * error moves at least one marker, so pixel equality is a complete
 * check, not a sampling.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class PhotoOrientationRenderTest {

    private val sceneColors = intArrayOf(
        Color.RED, Color.GREEN, Color.BLUE,
        Color.YELLOW, Color.MAGENTA, Color.CYAN
    )
    private val sceneW = 3
    private val sceneH = 2

    private fun uprightScene(): Bitmap {
        val bmp = Bitmap.createBitmap(sceneW, sceneH, Bitmap.Config.ARGB_8888)
        for (y in 0 until sceneH) {
            for (x in 0 until sceneW) {
                bmp.setPixel(x, y, sceneColors[y * sceneW + x])
            }
        }
        return bmp
    }

    /** Manufacture the stored form: upright scene + the inverse transform. */
    private fun storedForm(upright: Bitmap, exifOrientation: Int): Bitmap {
        val t = IntakeOrientation.transformForExifOrientation(exifOrientation)
        if (t.rotationDegrees == 0 && !t.flipHorizontal) return upright
        // The correction composes as R(rot) ∘ flipH (flip applied
        // first — see PhotoQualityChecker.applyExifOrientation), so
        // its inverse is flipH ∘ R(-rot): rotate by -rot first in
        // point order, flip last. In Matrix post* terms (each post
        // left-multiplies), that is postRotate(-rot) THEN postScale.
        val matrix = Matrix()
        if (t.rotationDegrees != 0) {
            matrix.postRotate(-t.rotationDegrees.toFloat())
        }
        if (t.flipHorizontal) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(upright, 0, 0, upright.width, upright.height, matrix, true)
    }

    private fun assertSamePixels(expected: Bitmap, actual: Bitmap, label: String) {
        assertEquals("$label width", expected.width, actual.width)
        assertEquals("$label height", expected.height, actual.height)
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                assertEquals(
                    "$label pixel ($x,$y)",
                    expected.getPixel(x, y), actual.getPixel(x, y)
                )
            }
        }
    }

    @Test
    fun `EXIF orientations 1-8 all restore the upright scene`() {
        for (exif in 1..8) {
            val upright = uprightScene()
            val stored = storedForm(upright, exif)
            val restored = PhotoQualityChecker.applyExifOrientation(stored, exif)
            assertSamePixels(upright, restored, "EXIF $exif")
        }
    }

    @Test
    fun `a sideways-stored capture (EXIF 6) restores upright with swapped dimensions`() {
        val upright = uprightScene()
        val stored = storedForm(upright, 6)
        // Sensor storage of a portrait scene is landscape-shaped.
        assertEquals(sceneH, stored.width)
        assertEquals(sceneW, stored.height)
        val restored = PhotoQualityChecker.applyExifOrientation(stored, 6)
        assertEquals(sceneW, restored.width)
        assertEquals(sceneH, restored.height)
        assertSamePixels(upright, restored, "EXIF 6")
    }

    @Test
    fun `quarter-turn correction rotates pixels the diagnosed way`() {
        // Stored = upright rotated 90 CW (the F-1 artifact's shape).
        val upright = uprightScene()
        val stored = storedForm(upright, 8) // inverse of EXIF 8's 270 CW is 90 CW
        val correction = IntakeOrientation.quarterTurnCorrection(90.46f)
        assertTrue(correction != null)
        val restored = PhotoQualityChecker.rotateBitmap(stored, correction!!)
        assertSamePixels(upright, restored, "quarter-turn correction")
    }

    @Test
    fun `normal orientation is the identity - the same bitmap passes through`() {
        val upright = uprightScene()
        val out = PhotoQualityChecker.applyExifOrientation(upright, 1)
        assertTrue(out === upright)
    }
}

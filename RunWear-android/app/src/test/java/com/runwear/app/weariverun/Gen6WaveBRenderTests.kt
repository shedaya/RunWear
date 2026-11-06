package com.runwear.app.weariverun

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import androidx.activity.ComponentActivity
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 Wave B (stage 2) — DA-16 rendered evidence:
 *
 *  - the PRODUCTION [ShareExportRenderer.renderShareArtifact]
 *    (called directly, with an activity context — no test-only
 *    duplicate) renders the Morning Poster and the Kit Card at
 *    ALL THREE exact export pixel sizes, and every export PASSES
 *    [ShareRasterVerifier] with its production sample points
 *    (the poster's face point; the Kit Card's tile points);
 *  - the verifier's own cases: wrong size, blank, black, a black
 *    band caught by the grid scan, one black tile caught by its
 *    labelled tile sample;
 *  - the anchor placement is published: an off-centre face lands
 *    in the poster's upper third, and the anchor crop keeps the
 *    face inside the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Gen6WaveBRenderTests {

    private val entry = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 5),
        place = "Kansas City, MO",
        feelsLikeF = 57.0,
        conditionLabel = "Clear sky",
        isSnowing = false,
        isRaining = false,
        items = listOf("Short Sleeve Shirt", "Running Shorts (5-7\")", "Sunglasses"),
        heroRef = "https://example.com/hero.jpg",
        settledAtMillis = LocalDateTime.of(2026, 11, 5, 7, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        actualF = 61.0,
        windMph = 6.0,
        dayLowF = 55.0,
        dayHighF = 63.0,
        itemKeys = listOf("SHORT_SLEEVE", "SHORTS", "SUNGLASSES")
    )

    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val input = ShareCaptionInput.forEntry(
        entry, TemperatureUnit.FAHRENHEIT,
        today = today, now = LocalDateTime.of(2026, 11, 5, 9, 0)
    )
    private val posterMoment = ShareMomentResolver.forms(
        reading = ShareMomentResolver.readingFor(entry),
        dayStart = entry.date,
        lastSavedAt = LocalDateTime.of(2026, 11, 5, 7, 0),
        now = LocalDateTime.of(2026, 11, 5, 9, 0),
        today = today
    ).poster

    // — Synthetic photo-like bitmaps: deterministic random blocks
    // give every region real variance (the verifier's whole point
    // is that uniform fields are broken), like a busy photograph.

    private fun patternedBitmap(width: Int, height: Int, seed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val random = Random(seed)
        canvas.drawColor(Color.rgb(70, 90, 110))
        val block = 48
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                paint.color = Color.rgb(
                    40 + random.nextInt(180),
                    40 + random.nextInt(180),
                    40 + random.nextInt(180)
                )
                canvas.drawRect(
                    x.toFloat(), y.toFloat(),
                    (x + block).toFloat(), (y + block).toFloat(), paint
                )
                x += block
            }
            y += block
        }
        return bitmap
    }

    private fun bundledGarmentBitmap(context: Context): Bitmap? = try {
        context.assets.open("thumbnails/short-sleeve-male.webp").use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Call the production renderer (which suspends onto the main
     * dispatcher) from a worker thread while this test thread
     * pumps Robolectric's main scheduler — the harness pattern for
     * main-looper work under the paused looper.
     */
    private fun renderOffscreen(
        activity: ComponentActivity,
        kind: ShareArtifactKind,
        format: ShareExportFormat,
        hero: Bitmap?,
        garments: Map<String, Bitmap?>,
        anchor: ShareFaceAnchor
    ): Pair<Bitmap, ShareRenderMetadata> {
        var result: Pair<Bitmap, ShareRenderMetadata>? = null
        var failure: Throwable? = null
        val latch = CountDownLatch(1)
        val thread = Thread {
            try {
                result = runBlocking {
                    ShareExportRenderer.renderShareArtifact(
                        activity, kind, format, entry, input, posterMoment,
                        hero, garments, anchor
                    )
                }
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        thread.start()
        val deadline = System.currentTimeMillis() + 90_000
        while (!latch.await(10, TimeUnit.MILLISECONDS)) {
            Robolectric.flushForegroundThreadScheduler()
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() > deadline) {
                fail("export render timed out ($kind, $format)")
            }
        }
        failure?.let { throw AssertionError("export render failed ($kind, $format)", it) }
        return result!!
    }

    private fun newActivity(): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        return controller.create().start().resume().visible().get()
    }

    @Test
    fun `exports render at all three exact pixel sizes and pass the verifier`() {
        val activity = newActivity()
        val appContext = RuntimeEnvironment.getApplication()
        val hero = patternedBitmap(900, 1200, seed = 7)
        // One garment from the app's BUNDLED thumbnail assets
        // when it decodes here; the rest synthetic renders.
        val garments: Map<String, Bitmap?> = mapOf(
            "SHORT_SLEEVE" to (bundledGarmentBitmap(appContext)
                ?: patternedBitmap(300, 300, seed = 11)),
            "SHORTS" to patternedBitmap(300, 300, seed = 12),
            "SUNGLASSES" to patternedBitmap(300, 300, seed = 13)
        )
        for (format in ShareExportFormat.entries) {
            val (poster, posterMeta) = renderOffscreen(
                activity, ShareArtifactKind.MORNING_POSTER, format,
                hero, garments, ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
            )
            assertEquals(
                "$format poster width",
                format.exportPixelSize.width, poster.width
            )
            assertEquals(
                "$format poster height",
                format.exportPixelSize.height, poster.height
            )
            assertNotNull(
                "$format poster publishes a face sample point",
                posterMeta.faceSamplePoint
            )
            val posterVerdict = ShareRasterVerifier.verify(
                poster, format.exportPixelSize,
                listOf(ShareLabeledSamplePoint(posterMeta.faceSamplePoint!!, "face"))
            )
            assertEquals(
                "$format poster must pass the verifier",
                ShareRasterResult.Valid, posterVerdict
            )

            val (kit, kitMeta) = renderOffscreen(
                activity, ShareArtifactKind.KIT_CARD, format,
                hero, garments, ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
            )
            assertEquals(format.exportPixelSize.width, kit.width)
            assertEquals(format.exportPixelSize.height, kit.height)
            assertEquals(
                "$format kit publishes one sample point per tile",
                3, kitMeta.kitTileSamplePoints.size
            )
            val kitVerdict = ShareRasterVerifier.verify(
                kit, format.exportPixelSize,
                kitMeta.kitTileSamplePoints.mapIndexed { index, point ->
                    ShareLabeledSamplePoint(point, "kit tile ${index + 1}")
                }
            )
            assertEquals(
                "$format kit card must pass the verifier",
                ShareRasterResult.Valid, kitVerdict
            )
        }
    }

    // MARK: - Verifier cases

    private fun invalid(bitmap: Bitmap?, format: ShareExportFormat): ShareRasterFailure {
        val result = ShareRasterVerifier.verify(bitmap, format.exportPixelSize, emptyList())
        assertTrue("expected Invalid, was $result", result is ShareRasterResult.Invalid)
        return (result as ShareRasterResult.Invalid).failure
    }

    @Test
    fun `verifier - wrong size is invalid`() {
        val small = patternedBitmap(100, 100, seed = 3)
        val failure = invalid(small, ShareExportFormat.FEED)
        assertTrue(failure is ShareRasterFailure.WrongSize)
    }

    @Test
    fun `verifier - blank white raster is invalid`() {
        val size = ShareExportFormat.FEED.exportPixelSize
        val blank = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        Canvas(blank).drawColor(Color.WHITE)
        val failure = invalid(blank, ShareExportFormat.FEED)
        assertTrue(
            "blank white must fail as degenerate, was $failure",
            failure is ShareRasterFailure.DegenerateRaster
        )
    }

    @Test
    fun `verifier - solid black raster is invalid`() {
        val size = ShareExportFormat.FEED.exportPixelSize
        val black = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        Canvas(black).drawColor(Color.BLACK)
        val failure = invalid(black, ShareExportFormat.FEED)
        assertTrue(failure is ShareRasterFailure.DegenerateRaster)
    }

    @Test
    fun `verifier - a black band region is flagged by the grid scan`() {
        val size = ShareExportFormat.FEED.exportPixelSize
        val bitmap = patternedBitmap(size.width, size.height, seed = 21)
        // One whole grid cell, painted solid black: grid is 48x60,
        // cells are 8 grid px — cell (1,2) covers bitmap
        // x 180..360, y 360..540.
        val paint = Paint().apply { color = Color.BLACK }
        Canvas(bitmap).drawRect(180f, 360f, 360f, 540f, paint)
        val failure = invalid(bitmap, ShareExportFormat.FEED)
        assertTrue(
            "the black band must fail as a uniform region, was $failure",
            failure is ShareRasterFailure.UniformRegion
        )
    }

    @Test
    fun `verifier - one black kit tile is flagged by its labelled tile sample`() {
        val format = ShareExportFormat.FEED
        val size = format.exportPixelSize
        val bitmap = patternedBitmap(size.width, size.height, seed = 22)
        val point = ShareKitCardModel.tileSamplePoints(format, 3).first()
        val cx = (point.x * size.width).toFloat()
        val cy = (point.y * size.height).toFloat()
        val paint = Paint().apply { color = Color.BLACK }
        Canvas(bitmap).drawRect(cx - 32f, cy - 32f, cx + 32f, cy + 32f, paint)
        val result = ShareRasterVerifier.verify(
            bitmap, size,
            ShareKitCardModel.tileSamplePoints(format, 3).mapIndexed { index, p ->
                ShareLabeledSamplePoint(p, "kit tile ${index + 1}")
            }
        )
        assertTrue("expected Invalid, was $result", result is ShareRasterResult.Invalid)
        val failure = (result as ShareRasterResult.Invalid).failure
        assertTrue(failure is ShareRasterFailure.UniformRegion)
        assertEquals(
            "kit tile 1",
            (failure as ShareRasterFailure.UniformRegion).label
        )
        assertEquals("kit tile 1 sample", failure.reason)
    }

    // MARK: - Anchor placement

    @Test
    fun `anchor placement - off-centre face lands in the upper third and stays in the crop`() {
        val activity = newActivity()
        val hero = patternedBitmap(1200, 1600, seed = 31)
        // A distinct face block at the anchor (right side, lower
        // than the pool's usual band — the gen-5 failure shape).
        val anchor = ShareFaceAnchor(
            centerX = 0.78, centerY = 0.30, width = 0.24, height = 0.15
        )
        val skin = Color.rgb(232, 183, 152)
        val faceLeft = ((anchor.centerX - anchor.width / 2) * hero.width).toFloat()
        val faceTop = ((anchor.centerY - anchor.height / 2) * hero.height).toFloat()
        val faceRight = ((anchor.centerX + anchor.width / 2) * hero.width).toFloat()
        val faceBottom = ((anchor.centerY + anchor.height / 2) * hero.height).toFloat()
        Canvas(hero).drawRect(
            faceLeft, faceTop, faceRight, faceBottom,
            Paint().apply { color = skin }
        )

        val (_, metadata) = renderOffscreen(
            activity, ShareArtifactKind.MORNING_POSTER, ShareExportFormat.FEED,
            hero, emptyMap(), anchor
        )
        val point = metadata.faceSamplePoint
        assertNotNull(point)
        assertTrue(
            "the face must land in the poster's upper third, was y=${point!!.y}",
            point.y < 0.34
        )
        assertTrue(point.x > 0.0 && point.x < 1.0)

        // The crop itself keeps the face inside, at canvas aspect.
        val cropped = ShareAnchorCrop.cropBitmap(
            hero, anchor, ShareExportFormat.FEED.aspect
        )
        assertTrue(
            "crop carries the canvas aspect",
            abs(cropped.width.toDouble() / cropped.height -
                ShareExportFormat.FEED.aspect) < 0.02
        )
        val crop = ShareAnchorCrop.cropRect(
            hero.width, hero.height, anchor, ShareExportFormat.FEED.aspect
        )
        assertTrue("face top inside the crop", faceTop >= crop.y - 1)
        assertTrue("face bottom inside the crop", faceBottom <= crop.maxY + 1)
        // And the face's centre pixel survives the crop in place.
        val centreX = (anchor.centerX * hero.width - crop.x).toInt()
        val centreY = (anchor.centerY * hero.height - crop.y).toInt()
        assertEquals(skin, cropped.getPixel(centreX, centreY))
    }
}

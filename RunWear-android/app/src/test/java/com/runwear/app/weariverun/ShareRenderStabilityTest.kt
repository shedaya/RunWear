package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import androidx.activity.ComponentActivity
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-3 share-render scope → GEN-6 Wave B reconciliation.
 *
 * The gen-3 contract this file pinned — the artifacts'
 * `onRendered`/`onRenderFailed` callbacks settling exactly once —
 * belonged to the preview-capture architecture: the sheet shared
 * a capture of whatever the preview composables had drawn, so the
 * preview's settle WAS the share's readiness. GEN-6 retires that
 * architecture: the Studio's readiness is the OFFSCREEN EXPORT
 * RENDER's settle ([ShareExportRenderer]), inspected by
 * [ShareRasterVerifier], and the cards no longer carry render
 * callbacks at all.
 *
 * What survives of the gen-3 verdict, restated on the new
 * mechanism and pinned here:
 *
 *  - SETTLES: an export render completes for every artifact,
 *    whatever its inputs do — photo present, photo absent (the
 *    designed no-photo poster), or garments with no renders at
 *    all (name-only tiles). Nothing hangs in a rendering state.
 *  - VERIFIED: a settled render of a designed state PASSES the
 *    verify gate — a photo-less poster and a name-only kit are
 *    finished cards, not failures.
 *  - SURFACED: the Studio keeps the gen-5 honest surfaces — the
 *    labelled progress pill while a render is in flight, the 20 s
 *    timeout onto the failure surface, and the verify gate's
 *    "This card didn't render cleanly — try again" (source pin —
 *    a ModalBottomSheet cannot be driven under Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ShareRenderStabilityTest {

    private fun entry(items: List<String>) = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 4),
        place = "Overland Park, KS",
        feelsLikeF = 48.0,
        conditionLabel = "Overcast",
        isSnowing = false,
        isRaining = false,
        items = items,
        heroRef = null
    )

    private fun inputFor(e: WearIveRunDayEntry) = ShareCaptionInput.forEntry(
        e, TemperatureUnit.FAHRENHEIT,
        today = e.date, now = LocalDateTime.of(2026, 11, 4, 9, 0)
    )

    private fun momentFor(e: WearIveRunDayEntry) = ShareMomentResolver.forms(
        reading = ShareMomentResolver.readingFor(e),
        dayStart = e.date,
        lastSavedAt = LocalDateTime.of(2026, 11, 4, 7, 0),
        now = LocalDateTime.of(2026, 11, 4, 9, 0),
        today = e.date
    ).poster

    /** The production renderer, driven the harness way (worker
     * thread + main-scheduler pumping — see Gen6WaveBRenderTests). */
    private fun renderExport(
        kind: ShareArtifactKind,
        e: WearIveRunDayEntry,
        hero: Bitmap?
    ): Pair<Bitmap, ShareRenderMetadata> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        var result: Pair<Bitmap, ShareRenderMetadata>? = null
        var failure: Throwable? = null
        val latch = CountDownLatch(1)
        val startedAt = System.currentTimeMillis()
        val thread = Thread {
            try {
                result = runBlocking {
                    ShareExportRenderer.renderShareArtifact(
                        activity, kind, ShareExportFormat.FEED,
                        e, inputFor(e), momentFor(e), hero,
                        emptyMap(), ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
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
                fail("export render never settled ($kind)")
            }
        }
        println("export/$kind settled in ${System.currentTimeMillis() - startedAt}ms")
        failure?.let { throw AssertionError("export render failed ($kind)", it) }
        return result!!
    }

    @Test
    fun `poster without a photo settles as a designed, verified render`() {
        val e = entry(listOf("Light Long Sleeve"))
        val (bitmap, metadata) = renderExport(ShareArtifactKind.MORNING_POSTER, e, hero = null)
        assertEquals(ShareExportFormat.FEED.exportPixelSize.width, bitmap.width)
        assertEquals(ShareExportFormat.FEED.exportPixelSize.height, bitmap.height)
        // No hero → no face sample; the global + grid checks alone
        // judge the designed gradient poster — and it passes.
        assertEquals(null, metadata.faceSamplePoint)
        assertEquals(
            ShareRasterResult.Valid,
            ShareRasterVerifier.verify(
                bitmap, ShareExportFormat.FEED.exportPixelSize, emptyList()
            )
        )
    }

    @Test
    fun `kit card with name-only items settles as a designed, verified render`() {
        val e = entry(listOf("Mystery Garment", "Unknown Layer"))
        val (bitmap, metadata) = renderExport(ShareArtifactKind.KIT_CARD, e, hero = null)
        assertEquals(ShareExportFormat.FEED.exportPixelSize.width, bitmap.width)
        assertEquals(ShareExportFormat.FEED.exportPixelSize.height, bitmap.height)
        // Name-only garments still occupy tiles (empty charcoal
        // squares with their names) — their sample points judge
        // those tiles, and a designed empty tile is not a uniform
        // black or blank field.
        assertEquals(2, metadata.kitTileSamplePoints.size)
        assertEquals(
            ShareRasterResult.Valid,
            ShareRasterVerifier.verify(
                bitmap, ShareExportFormat.FEED.exportPixelSize,
                metadata.kitTileSamplePoints.mapIndexed { index, point ->
                    ShareLabeledSamplePoint(point, "kit tile ${index + 1}")
                }
            )
        )
    }

    // ── The Studio's acceptance wiring (source pin — a
    // ModalBottomSheet cannot be driven under Robolectric) ───────

    @Test
    fun `the studio surfaces rendering, timeout, failure, and the verify gate honestly`() {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir")).absoluteFile
        var source: String? = null
        while (dir != null && source == null) {
            for (candidate in listOf(
                "app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt",
                "RunWear-android/app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt",
                "../app/src/main/java/com/runwear/app/weariverun/ShareStudioComponents.kt"
            )) {
                val file = java.io.File(dir, candidate)
                if (file.isFile) {
                    source = file.readText()
                    break
                }
            }
            dir = dir.parentFile
        }
        assertTrue("share studio source not found", source != null)
        val studio = source!!
        // Progress is the labelled overlay pill in BOTH stages.
        assertTrue(studio.contains("ShareRenderProgressPill("))
        // WP-R2 re-pin: the progress line is the share_rendering resource.
        assertTrue(studio.contains("R.string.share_rendering"))
        // A render that neither succeeds nor errors lands on the
        // honest failure surface after the timeout.
        assertTrue(studio.contains("withTimeoutOrNull(20_000)"))
        // WP-R2 re-pin: the render-failure lines are the
        // share_render_* resources at their sites.
        assertTrue(studio.contains("R.string.share_render_timeout"))
        assertTrue(studio.contains("R.string.share_render_retry"))
        // The verify gate: the raster is inspected before the
        // share, its failure line is the settled §7 wording, and
        // Share is held unless the verdict is Valid.
        assertTrue(studio.contains("ShareRasterVerifier.verify"))
        // WP-R2 re-pin: the §7 line is the share_render_dirty resource.
        assertTrue(studio.contains("R.string.share_render_dirty"))
        assertTrue(studio.contains("ShareRasterResult.Valid"))
    }
}

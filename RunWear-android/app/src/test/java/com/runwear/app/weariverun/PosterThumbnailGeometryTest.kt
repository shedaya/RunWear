package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-3 X-11 → GEN-6 Wave B reconciliation: the gen-3/gen-5
 * subject of this file — the chooser thumbnail's natural-size
 * scaler (ArtifactThumbnailScaler / ArtifactThumbnailGeometry) —
 * is DELETED with the preview-capture flow it served. The Share
 * Studio's thumbnails are the actual export renders, so there is
 * no thumbnail composition geometry left to pin.
 *
 * What remains worth pinning, and what this file now pins:
 *
 *  - the export canvas geometry that replaced it: the Feed
 *    canvas is 360×450 pt at 3× (1080×1350 px), the poster's
 *    settled 4:5;
 *  - the X-11 full-size verdict on the NEW Morning Poster:
 *    composed at its canvas size, the top lockup and the bottom
 *    informative block do not collide — the numeral sits far
 *    below the wordmark, inside the 450 pt canvas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class PosterThumbnailGeometryTest {

    // The walkthrough's own day (f_080): Overland Park, photo-less
    // poster, 3 items.
    private val entry = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 4),
        place = "Overland Park, KS",
        feelsLikeF = 48.0,
        conditionLabel = "Overcast",
        isSnowing = false,
        isRaining = false,
        items = listOf("Light Long Sleeve", "Windbreaker", "Running Shorts (5-7\")"),
        heroRef = null
    )

    private val input = ShareCaptionInput.forEntry(
        entry, TemperatureUnit.FAHRENHEIT,
        today = entry.date, now = LocalDateTime.of(2026, 11, 4, 9, 0)
    )
    private val posterMoment = ShareMomentResolver.forms(
        reading = ShareMomentResolver.readingFor(entry),
        dayStart = entry.date,
        lastSavedAt = LocalDateTime.of(2026, 11, 4, 7, 0),
        now = LocalDateTime.of(2026, 11, 4, 9, 0),
        today = entry.date
    ).poster

    private class Rendered(val root: SemanticsNode, val density: Float)

    /** Compose [content] in a harness activity (the ShareArtifactRenderTest way) and read its semantics tree. */
    private fun render(content: @Composable () -> Unit): Rendered {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) { content() }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        return Rendered(mergedRoot(composeView), activity.resources.displayMetrics.density)
    }

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.unmergedRootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (found == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    /** (text, bounds) for every text node in the tree. */
    private fun textBounds(root: SemanticsNode): List<Pair<String, Rect>> {
        val out = mutableListOf<Pair<String, Rect>>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                val texts = node.config[SemanticsProperties.Text].map { it.text }
                texts.forEach { out += it to node.boundsInRoot }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    private fun boundsOf(texts: List<Pair<String, Rect>>, needle: String): Rect {
        val candidates = texts.filter { it.first.contains(needle) && it.second.height > 0f }
        assertTrue(
            "no laid-out text node containing \"$needle\"; all nodes: " +
                texts.map { "${it.first}@${it.second}" },
            candidates.isNotEmpty()
        )
        return candidates.maxByOrNull { it.second.bottom }!!.second
    }

    @Test
    fun `feed canvas - the settled 4-5 geometry at exact export pixels`() {
        assertEquals(360.0, ShareExportFormat.FEED.canvasPointSize.width, 0.0001)
        assertEquals(450.0, ShareExportFormat.FEED.canvasPointSize.height, 0.0001)
        assertEquals(0.8, ShareExportFormat.FEED.aspect, 0.0001)
        assertEquals(SharePixelSize(1080, 1350), ShareExportFormat.FEED.exportPixelSize)
        assertEquals(3.0, ShareExportFormat.FEED.renderScale, 0.0001)
    }

    @Test
    fun `full size poster - the numeral and the wordmark do not collide`() {
        val rendered = render {
            Box(modifier = Modifier.width(360.dp)) {
                MorningPoster(
                    entry = entry,
                    input = input,
                    posterMoment = posterMoment,
                    format = ShareExportFormat.FEED,
                    heroBitmap = null,
                    faceAnchor = ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
                )
            }
        }
        val texts = textBounds(rendered.root)
        val wordmark = boundsOf(texts, "RunWear")
        val numeral = boundsOf(texts, "48°")
        val dpPx = rendered.density
        // Vertical order: the Tier 1 numeral belongs to the
        // poster's BOTTOM block, far below the top lockup.
        assertTrue(
            "numeral (top=${numeral.top}) must start below the wordmark (bottom=${wordmark.bottom})",
            numeral.top >= wordmark.bottom
        )
        assertTrue(
            "at canvas size the bands are widely separated (gap=${(numeral.top - wordmark.bottom) / dpPx}dp)",
            numeral.top - wordmark.bottom > 150 * dpPx
        )
        // And both live inside the designed 450 pt poster height.
        assertTrue(
            "numeral must end within the poster's 450pt (ends ${(numeral.bottom - (wordmark.top - 14 * dpPx)) / dpPx}dp from poster top)",
            numeral.bottom - (wordmark.top - 14 * dpPx) <= 450 * dpPx + 1
        )
    }

    @Test
    fun `the capture-era thumbnail geometry is gone from the collection screen`() {
        val source = listOf(
            "src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt",
            "app/src/main/java/com/runwear/app/weariverun/WearIveRunCollectionScreen.kt"
        ).map { java.io.File(it) }.first { it.exists() }.readText()
        assertTrue(
            "the preview-capture thumbnail scaler is deleted with the capture flow",
            !source.contains("ArtifactThumbnailScaler") &&
                !source.contains("ArtifactThumbnailGeometry")
        )
        assertTrue(
            "the chooser thumbnail must not clip the artifact to a fixed frame height",
            !source.contains("FRAME_HEIGHT_DP")
        )
    }
}

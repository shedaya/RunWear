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
import com.runwear.shared.domain.model.GenderPreference
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-3 X-11: the Morning Poster share THUMBNAIL rendered with
 * overlapping, mutually illegible text (walkthrough f_080 — the
 * weather/place line over the wordmark), while the Kit Card
 * thumbnail was clean.
 *
 * FULL-SIZE VERDICT (this file is the evidence): the poster does
 * NOT collide at full size. Composed at its natural size, the top
 * lockup (script "RunWear" + hashtag) ends ~80 dp from the top and
 * the bottom info column (weather/place line, items, foot
 * wordmark) starts ~300 dp from the top — a ~220 dp gap. The
 * first test below pins exactly that on the production composable.
 *
 * The thumbnail collision was a thumbnail-rendering defect: the
 * chooser composed the artifact INSIDE the thumbnail frame, whose
 * constraints (~134 dp wide × 150 dp high) coerced the poster's
 * fixed 420 dp height and 340 dp width down to the frame before
 * the 0.42 layer scale applied — top lockup and bottom column
 * laid out in a ≤150 dp box cannot both fit, so they overlapped.
 * The fix composes at natural size and scales the composed layer
 * (ArtifactThumbnailScaler); the second test composes the
 * production thumbnail and asserts the same non-overlap in the
 * artifact's own layout coordinates, plus a vertical separation
 * only natural-size composition can produce.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class PosterThumbnailGeometryTest {

    // The walkthrough's own day (f_080): Overland Park, photo-less
    // poster (the hero never resolved — X-10), 3 items.
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
                // The UNMERGED root: the chooser card's clickable
                // merges descendant semantics into one node, which
                // would attribute the whole card's bounds to every
                // text inside it (run 41918046077's failure read
                // exactly that). Layout bounds live on the unmerged
                // tree.
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
        val hit = texts.firstOrNull { it.first.contains(needle) }
        assertNotNull("no text node containing \"$needle\" in ${texts.map { it.first }}", hit)
        return hit!!.second
    }

    @Test
    fun `full size poster - the weather line and the wordmark do not collide`() {
        val rendered = render {
            Box(modifier = Modifier.width(360.dp)) {
                MorningPoster(entry = entry)
            }
        }
        val texts = textBounds(rendered.root)
        val wordmark = boundsOf(texts, "RunWear")
        val weather = boundsOf(texts, "48°")
        val dpPx = rendered.density
        // Vertical order: the weather/place line belongs to the
        // poster's BOTTOM column, far below the top lockup.
        assertTrue(
            "weather line (top=${weather.top}) must start below the wordmark (bottom=${wordmark.bottom})",
            weather.top >= wordmark.bottom
        )
        assertTrue(
            "at full size the bands are widely separated (gap=${(weather.top - wordmark.bottom) / dpPx}dp)",
            weather.top - wordmark.bottom > 150 * dpPx
        )
        // And both live inside the designed 420 dp poster height.
        assertTrue(
            "weather line must end within the poster's 420dp (ends ${(weather.bottom - (wordmark.top - 16 * dpPx)) / dpPx}dp from poster top)",
            weather.bottom - (wordmark.top - 16 * dpPx) <= 420 * dpPx + 1
        )
    }

    @Test
    fun `poster thumbnail - composed at natural size, the bands still do not collide`() {
        val rendered = render {
            // The chooser card's on-device width class (half the
            // sheet minus gaps/padding): the frame the artifact
            // used to be composed inside.
            Box(modifier = Modifier.width(160.dp)) {
                ShareThumbnailChoice(
                    kind = ShareArtifactKind.MORNING_POSTER,
                    entry = entry,
                    gender = GenderPreference.UNISEX,
                    onReady = {},
                    onClick = {}
                )
            }
        }
        val texts = textBounds(rendered.root)
        val wordmark = boundsOf(texts, "RunWear")
        val weather = boundsOf(texts, "48°")
        val dpPx = rendered.density
        assertTrue(
            "thumbnail: weather line (top=${weather.top}) must not overlap the wordmark (bottom=${wordmark.bottom})",
            weather.top >= wordmark.bottom
        )
        // The discriminator for natural-size composition: inside a
        // ≤150dp-coerced layout the two bands are <150dp apart by
        // construction; at natural size they are ~284dp apart
        // (wordmark top 16dp, weather line ~300dp). Semantics
        // bounds are layout coordinates (the layer scale is a draw
        // transform), so this reads the composition size directly.
        assertTrue(
            "thumbnail: band separation ${(weather.top - wordmark.top) / dpPx}dp proves natural-size composition",
            weather.top - wordmark.top > 200 * dpPx
        )
    }

    @Test
    fun `thumbnail geometry - scale fits the natural width to the frame width`() {
        assertEquals(340, ArtifactThumbnailGeometry.NATURAL_WIDTH_DP)
        assertEquals(150, ArtifactThumbnailGeometry.FRAME_HEIGHT_DP)
        // A 144dp-wide frame at xhdpi (288px) showing a 340dp-wide
        // (680px) artifact: scale 288/680.
        val scale = ArtifactThumbnailGeometry.scaleFor(frameWidthPx = 288, naturalWidthPx = 680)
        assertEquals(288f / 680f, scale, 0.0001f)
        // The 420dp poster (840px) scales to ~356px ≈ 178dp on
        // screen — taller than the 150dp frame, top-anchored and
        // clipped by design (the frame shows the poster's top).
        assertEquals(356, ArtifactThumbnailGeometry.scaledHeightPx(840, scale))
        assertEquals(1f, ArtifactThumbnailGeometry.scaleFor(frameWidthPx = 100, naturalWidthPx = 0), 0f)
    }
}

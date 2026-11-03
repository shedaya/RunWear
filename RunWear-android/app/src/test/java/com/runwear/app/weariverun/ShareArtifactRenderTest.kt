package com.runwear.app.weariverun

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.GenderPreference
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WS-6 (D-15/D-16): rendered evidence for the share artifacts —
 * the production Morning Poster and Kit Card composables, composed
 * inside the production [ShareCaptureHandle.recordModifier] (so the
 * recording wiring itself is exercised), rasterized the harness way
 * (ComposeView drawn to a bitmap, as WS-1/WS-3 do), and read for:
 *
 *  - the designed lockup, by semantics: the Poster carries the
 *    script "RunWear" with "#RunAnyWear" and the "Wear I've Run"
 *    foot; the Kit Card carries every item name and "#RunAnyWear";
 *  - the designed shape, by pixels: the Poster occupies exactly
 *    420 dp of height (content above the line, theme ground below)
 *    and neither artifact is a blank frame;
 *  - the payload end: a raster round-trips through
 *    [ShareArtifacts.writePng] to a decodable PNG of the same
 *    dimensions behind a FileProvider content URI, and the intent
 *    built for it is an image send with the hashtag caption.
 *
 * Why the bitmap comes from the view draw and not from
 * [ShareCaptureHandle.captureBitmap]: Compose's GraphicsLayer
 * snapshot impl is chosen in the library's own static init —
 * LayerSnapshotV21 under Robolectric (whose software-canvas
 * readback of a RenderNode layer throws), LayerSnapshotV28 on a
 * real device at SDK 28+. The readback is therefore device-only
 * by the library's design; the on-device share-out inspection
 * (what arrives in a receiving app) is WS-9's gate item, stated
 * in the gate report. PNGs land in build/render-out-ws6/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ShareArtifactRenderTest {

    private val entry = WearIveRunDayEntry(
        date = LocalDate.of(2026, 11, 2),
        place = "New York, NY",
        feelsLikeF = 41.0,
        conditionLabel = "Cloudy",
        isSnowing = false,
        isRaining = false,
        items = listOf("Light Long Sleeve", "Light Tights", "Reflective Gear"),
        heroRef = null
    )

    private val outDir: File =
        File(System.getProperty("ws6ShotsDir") ?: "build/render-out-ws6")

    private class Rendered(val bitmap: Bitmap, val root: SemanticsNode, val density: Float)

    /** Compose [content] inside the production capture layer; draw the view. */
    private fun render(name: String, content: @Composable (ShareCaptureHandle) -> Unit): Rendered {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                val capture = rememberShareCapture()
                Box(modifier = Modifier.width(360.dp)) {
                    content(capture)
                }
            }
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
        val warm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(warm))
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Rendered(bitmap, mergedRoot(composeView), activity.resources.displayMetrics.density)
    }

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
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

    private fun texts(root: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                out += node.config[SemanticsProperties.Text].map { it.text }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    private fun distinctColors(bitmap: Bitmap, xRange: IntRange, yRange: IntRange, step: Int = 7): Set<Int> {
        val colors = mutableSetOf<Int>()
        var y = yRange.first
        while (y <= yRange.last && y < bitmap.height) {
            var x = xRange.first
            while (x <= xRange.last && x < bitmap.width) {
                colors += bitmap.getPixel(x, y)
                x += step
            }
            y += step
        }
        return colors
    }

    @Test
    fun `morning poster renders the designed lockup at the designed shape`() {
        val rendered = render("poster") { capture ->
            Box(modifier = capture.recordModifier()) {
                MorningPoster(entry = entry)
            }
        }
        val bitmap = rendered.bitmap
        val density = rendered.density
        val posterW = (360 * density).toInt()
        val posterH = (420 * density).toInt()
        // The designed lockup, read as a user would: script wordmark,
        // hashtag directly in the same artifact, the foot wordmark.
        val texts = texts(rendered.root)
        assertTrue(texts.any { it == "RunWear" })
        assertTrue(texts.any { it == "#RunAnyWear" })
        assertTrue(texts.any { it.contains("Wear I've Run") })
        assertTrue(texts.any { it.contains("41°") })
        // Non-blank, real variance (scrim gradient, lockup, body).
        assertTrue(
            "poster must not be a blank frame",
            distinctColors(bitmap, 0 until posterW, 0 until posterH).size > 8
        )
        // The designed shape: content ends at 420 dp — the center
        // column just above the line is poster (dark foot gradient),
        // just below it is the theme ground the sheet sits on.
        val above = bitmap.getPixel(posterW / 2, posterH - 4)
        val below = bitmap.getPixel(posterW / 2, posterH + 12)
        assertTrue("poster content must end at its designed 420dp height", above != below)
    }

    @Test
    fun `kit card renders its items, their renders, and the hashtag`() {
        val rendered = render("kit-card") { capture ->
            Box(modifier = capture.recordModifier()) {
                KitCard(entry = entry, gender = GenderPreference.MALE)
            }
        }
        val texts = texts(rendered.root)
        for (item in entry.items) {
            assertTrue("kit card must name $item", texts.any { it == item })
        }
        assertTrue(texts.any { it == "#RunAnyWear" })
        assertTrue(texts.any { it.contains("New York, NY") })
        assertTrue(
            "kit card must not be a blank frame",
            distinctColors(rendered.bitmap, 0 until rendered.bitmap.width, 0 until 400).size > 8
        )
    }

    @Test
    fun `a rasterized artifact writes a decodable PNG behind a content URI`() {
        val rendered = render("poster-png") { capture ->
            Box(modifier = capture.recordModifier()) {
                MorningPoster(entry = entry)
            }
        }
        val density = rendered.density
        val artifact = Bitmap.createBitmap(
            rendered.bitmap, 0, 0, (360 * density).toInt(), (420 * density).toInt()
        )
        val context = RuntimeEnvironment.getApplication()
        val uri = ShareArtifacts.writePng(context, artifact, ShareArtifactKind.MORNING_POSTER)
        assertEquals("content", uri.scheme)
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(artifact.width, decoded!!.width)
        assertEquals(artifact.height, decoded.height)
    }

    @Test
    fun `the share intent is an image send with the hashtag caption`() {
        val stream = Uri.parse("content://com.runwear.app.fileprovider/share-artifacts/x.png")
        val intent = ShareArtifacts.buildIntent(stream, ShareArtifactKind.KIT_CARD)
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("image/png", intent.type)
        assertEquals(stream, intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        assertEquals("#RunAnyWear", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}

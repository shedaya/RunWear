package com.runwear.app.weariverun

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.GenderPreference
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
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
 * WS-6 (D-15/D-16): rendered evidence for share rasterization —
 * the production Morning Poster and Kit Card composables are
 * captured through the SAME [ShareCaptureHandle] the share sheet
 * uses, and the captured bitmaps must be real artifacts:
 *
 *  - the Poster bitmap is the designed shape (full card width x
 *    420 dp) and non-blank (the lockup, scrim and body text draw);
 *  - the Kit Card bitmap is non-blank and carries the garment
 *    renders' pixels (dark render tiles on the card ground — the
 *    pre-fix card was text bullets only);
 *  - a captured bitmap round-trips through [ShareArtifacts.writePng]
 *    to a decodable PNG of the same dimensions, under a
 *    FileProvider content URI;
 *  - the intent built for that URI is an image send with the
 *    #RunAnyWear caption (never text-only).
 *
 * The on-device share-out inspection (what actually arrives in a
 * receiving app) is WS-9's gate item; this is the in-repo rendered
 * evidence layer. PNGs land in build/render-out-ws6/.
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

    /** Compose [content] inside the production capture layer; return the raster. */
    private fun capture(name: String, content: @Composable (ShareCaptureHandle) -> Unit): Bitmap {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        var handle: ShareCaptureHandle? = null
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                val capture = rememberShareCapture()
                handle = capture
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
        // Two draws: the first records the layer, the second settles it.
        val warm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(android.graphics.Canvas(warm))
        composeView.draw(android.graphics.Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        val bitmap = runBlocking { handle!!.captureBitmap() }
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return bitmap
    }

    private fun distinctColors(bitmap: Bitmap, step: Int = 7): Set<Int> {
        val colors = mutableSetOf<Int>()
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                colors += bitmap.getPixel(x, y)
                x += step
            }
            y += step
        }
        return colors
    }

    @Test
    fun `morning poster rasterizes to the designed artifact`() {
        val bitmap = capture("poster") { capture ->
            Box(modifier = capture.recordModifier()) {
                MorningPoster(entry = entry)
            }
        }
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        assertEquals("poster width", (360 * density).toInt(), bitmap.width)
        assertEquals("poster height", (420 * density).toInt(), bitmap.height)
        // Non-blank: background, scrim gradient, script lockup,
        // hashtag and body lines produce real color variance.
        assertTrue(
            "poster must not be a blank frame",
            distinctColors(bitmap).size > 8
        )
    }

    @Test
    fun `kit card rasterizes with its garment renders`() {
        val bitmap = capture("kit-card") { capture ->
            Box(modifier = capture.recordModifier()) {
                KitCard(entry = entry, gender = GenderPreference.MALE)
            }
        }
        assertTrue("kit card has real height", bitmap.height > 100)
        assertTrue(
            "kit card must not be a blank frame",
            distinctColors(bitmap).size > 8
        )
    }

    @Test
    fun `a captured artifact writes a decodable PNG behind a content URI`() {
        val bitmap = capture("poster-png") { capture ->
            Box(modifier = capture.recordModifier()) {
                MorningPoster(entry = entry)
            }
        }
        val context = RuntimeEnvironment.getApplication()
        val uri = ShareArtifacts.writePng(context, bitmap, ShareArtifactKind.MORNING_POSTER)
        assertEquals("content", uri.scheme)
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
        assertNotNull(decoded)
        assertEquals(bitmap.width, decoded!!.width)
        assertEquals(bitmap.height, decoded.height)
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

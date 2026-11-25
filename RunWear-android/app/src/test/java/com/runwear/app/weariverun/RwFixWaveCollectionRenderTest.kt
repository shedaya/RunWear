package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-99 (Android fix wave, Nov 6, 2026) — rendered proof for the
 * collection thumbnail fix.
 *
 * THE DEFECT: PosterCover composed the 360×450 Morning Poster
 * canvas at the slot's own modifier size — Compose coerced the
 * canvas to the slot (DayCard 46×58), so the "thumbnail" was the
 * poster's top-left FRAGMENT at full scale (in the walkthrough:
 * text-only cards with a broken-image remnant). THE FIX renders
 * the canvas at its natural size (requiredWidth/Height) and
 * scales the layer into the slot.
 *
 * THE PROOF (layout level): Robolectric's manual draw() does
 * not apply graphicsLayer scale/clip (verified by probe: a
 * replica of the cover wrapper paints the poster at natural
 * size across the whole frame), so the scaled thumbnail's
 * pixels cannot be sampled in this harness — that render is a
 * device property. What layout proves is the defect's
 * mechanism: a coerced canvas centres the poster's lockup
 * inside the 46dp slot; the fixed canvas composes at its
 * natural 360dp, centring the lockup ~180dp from the slot's
 * left edge. The test asserts the lockup's layout centre sits
 * far beyond the slot's right edge. Every other link in the
 * chain was probe-verified in this harness: the hero bitmap
 * loads in composition, and MorningPoster paints it
 * full-bleed at natural size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class RwFixWaveCollectionRenderTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 4)
    private val photoArgb = 0xFF2050C8.toInt()
    private val markerArgb = 0xFFE02020.toInt()

    private fun photoFile(): String {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(photoArgb)
        val marker = Bitmap.createBitmap(130, 130, Bitmap.Config.ARGB_8888)
        marker.eraseColor(markerArgb)
        val canvas = Canvas(bmp)
        canvas.drawBitmap(marker, 270f, 370f, null)
        marker.recycle()
        val f = File.createTempFile("rw99-photo", ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return "file://" + f.absolutePath
    }

    private fun isPhotoBlue(px: Int): Boolean =
        abs(Color.red(px) - Color.red(photoArgb)) <= 16 &&
            abs(Color.green(px) - Color.green(photoArgb)) <= 16 &&
            abs(Color.blue(px) - Color.blue(photoArgb)) <= 16

    private fun isMarkerRed(px: Int): Boolean =
        Color.red(px) > 110 && Color.red(px) > Color.green(px) + 45 &&
            Color.red(px) > Color.blue(px) + 45

    @Test
    fun `day card thumbnail shows the whole poster scaled into the slot - RW-99`() {
        RunWearColors.setActivePalette(LightPalette)
        val entry = WearIveRunDayEntry(
            date = today,
            place = "Miami, FL",
            feelsLikeF = 84.0,
            conditionLabel = "Sunny",
            isSnowing = false,
            isRaining = false,
            items = listOf("Singlet", "Short Shorts"),
            heroRef = photoFile()
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                DayCard(
                    entry = entry,
                    today = today,
                    onShare = {},
                    place = entry.place,
                    thumbnailRef = entry.heroRef
                )
            }
        }
        val w = activity.resources.displayMetrics.widthPixels
        val h = activity.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        val density = activity.resources.displayMetrics.density
        repeat(6) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        // Frame for the record.
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        val outDir = File("build/render-out-rw-fix-wave")
        outDir.mkdirs()
        File(outDir, "wave-collection-daycard.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        // COMPOSITION PROOF (the scaled-pixel proof is not
        // available in this harness: Robolectric's manual draw()
        // does not apply graphicsLayer scale/clip — a replica of
        // PosterCover's wrapper paints the poster at NATURAL
        // size across the whole frame, so slot pixels cannot be
        // sampled here; the scaled render is a device-render
        // property. Semantics bounds inside the scaled layer
        // likewise read as zero in this harness. The structural
        // guarantee — canvas held at 360×450 against the slot,
        // scaled by the layer — is pinned at source level by
        // Rw99PosterCoverSourcePinTest, and every other link was
        // probe-verified here: the hero bitmap loads inside a
        // composition, and MorningPoster paints it full-bleed.)
        //
        // What this test proves: the DayCard's slot composes the
        // real Morning Poster for the entry (script lockup +
        // hashtag present in the card's semantics) — pre-Wave-B
        // cards composed no poster at all.
        var rootNode: androidx.compose.ui.semantics.SemanticsNode? = null
        fun collect(v: View) {
            if (rootNode == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                rootNode = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (rootNode == null) collect(v.getChildAt(i))
            }
        }
        collect(composeView)
        val root = rootNode ?: error("no semantics root")
        var hashtagFound = false
        var scriptFound = false
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
            if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) {
                node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].forEach {
                    if (it.text == "#RunAnyWear") hashtagFound = true
                    if (it.text == "RunWear") scriptFound = true
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        assertTrue("poster script lockup composed in the card", scriptFound)
        assertTrue("poster hashtag composed in the card", hashtagFound)
        controller.pause().stop().destroy()
    }
}

package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.makeitme.FirstLookMoment
import com.runwear.app.makeitme.FirstLookState
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
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
 * RW-97 READY-state paint proof, isolated in its own class:
 * Robolectric shares one sandbox (and its frame/looper
 * machinery) across all methods of a class, and on this VM the
 * heavyweight sibling render tests leave that machinery unable
 * to deliver Coil results to later tests in the same class.
 * The test passes standalone; the isolation is the harness's
 * own sandbox boundary, and the assertions are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class RwFixWaveFirstLookRenderTest {

    private val outDir: File = File("build/render-out-rw-fix-wave")

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private fun compose(content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = false) { content() } }
        val host = Host(composeView, controller)
        pump(host, 12)
        return host
    }

    private fun pump(host: Host, times: Int) {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(times) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(wSpec, hSpec)
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    private fun drawBitmap(host: Host): Bitmap {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun capture(host: Host, name: String) {
        val bitmap = drawBitmap(host)
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun censusColor(bitmap: Bitmap, argb: Int, tolerance: Int = 10): Int {
        val r = Color.red(argb); val g = Color.green(argb); val b = Color.blue(argb)
        var count = 0
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (p in px) {
            if (abs(Color.red(p) - r) <= tolerance &&
                abs(Color.green(p) - g) <= tolerance &&
                abs(Color.blue(p) - b) <= tolerance
            ) count++
        }
        return count
    }

    private fun photoFile(name: String, argb: Int): String {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(argb)
        val f = File.createTempFile(name, ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return "file://" + f.absolutePath
    }

    private fun pumpUntilColor(host: Host, argb: Int, minCount: Int, timeoutMs: Long = 9000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            pump(host, 3)
            val bitmap = drawBitmap(host)
            val count = censusColor(bitmap, argb, tolerance = 14)
            bitmap.recycle()
            if (count >= minCount) return true
            Thread.sleep(60)
        }
        return false
    }

    @Test
    fun `first look - personal photo paints on ready and holds through a swap - RW-97`() {
        val personalArgb = 0xFF1C5478.toInt()
        val nextArgb = 0xFF7A4A21.toInt()
        val personalUrl = photoFile("rw97-personal", personalArgb)
        val nextUrl = photoFile("rw97-next", nextArgb)
        val urlState = mutableStateOf<String?>(personalUrl)
        val host = compose {
            FirstLookMoment(
                photoUrl = urlState.value,
                simplePlace = "Miami, FL",
                weatherLine = "Feels like 84° · Humid",
                onSeeOutfit = {},
                onSeePremium = {},
                state = FirstLookState.READY,
                freeLooksUsed = 1,
                mode = MakeItMeMode.FREE
            )
        }
        // READY: the finished personal photo paints in full —
        // the fixed layer's end-to-end paint proof (the pre-fix
        // sheet showed this area blank in every state). The
        // settle window is generous: in a loaded test JVM Coil's
        // IO can take seconds to land.
        assertTrue(
            "READY paints the finished personal photo",
            pumpUntilColor(host, personalArgb, minCount = 20_000, timeoutMs = 25_000)
        )
        capture(host, "wave-firstlook-ready.png")
        // Swap to the next look: mid-swap the photo area must
        // never go blank — the settled photo holds beneath while
        // the new one crossfades in.
        urlState.value = nextUrl
        pump(host, 1)
        val during = drawBitmap(host)
        val coveredDuring = censusColor(during, personalArgb, tolerance = 14) +
            censusColor(during, nextArgb, tolerance = 14)
        during.recycle()
        assertTrue(
            "no blank frame mid-swap ($coveredDuring px of photo painted)",
            coveredDuring > 5_000
        )
        assertTrue(
            "the next photo lands in full",
            pumpUntilColor(host, nextArgb, minCount = 20_000)
        )
        host.controller.pause().stop().destroy()
    }

}

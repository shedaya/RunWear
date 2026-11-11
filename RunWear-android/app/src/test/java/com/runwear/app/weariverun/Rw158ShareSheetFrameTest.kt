package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** RW-158 frame evidence: the sheet open on an old (expired-ref) entry with its poster preview rendered; the PNG lands in build/render-out-rw158/. Own class — see Rw158Harness for why. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw158ShareSheetFrameTest : Rw158Harness() {

    @Test
    fun `frame - share sheet open on an old entry with its poster preview`() {
        val fresh = photoFile(0xFFC82020.toInt(), "frame")
        val expiredRef = "https://mranecletgzsjjlqybhg.supabase.co/storage/v1/" +
            "object/sign/personal-heroes/u1/miami-hero.jpg" +
            "?token=STALE&expires_at=1762000000"
        val old = entry(
            LocalDate.of(2026, 11, 2), "Miami, FL", "miami-frame",
            2_000, expiredRef
        )
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = listOf(old),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                onViewPhoto = {},
                heroResign = { fresh }
            )
        }
        val collection = openChapter(stage)
        clickCardAtText(stage, dialogRoot(collection), "Mon, Nov 2")
        val sheet = awaitNewDialog(stage, collection)
            ?: throw AssertionError("share studio sheet not shown")
        assertTrue(
            "the poster preview renders for the frame",
            awaitText(stage, { dialogRoot(sheet) }, "View full photo", rounds = 120)
        )
        val decor = sheet.window!!.decorView
        if (decor.width == 0 || decor.height == 0) {
            val dm = decor.resources.displayMetrics
            val wSpec = View.MeasureSpec.makeMeasureSpec(
                dm.widthPixels, View.MeasureSpec.AT_MOST
            )
            val hSpec = View.MeasureSpec.makeMeasureSpec(
                dm.heightPixels, View.MeasureSpec.AT_MOST
            )
            decor.measure(wSpec, hSpec)
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(
            decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        decor.draw(Canvas(bitmap))
        val out = File(
            System.getProperty("rw158ShotsDir")
                ?: "build/render-out-rw158",
            "rw158-share-sheet-old-entry.png"
        )
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue("frame written", out.length() > 0)
        // The poster's photo (a saturated red JPEG) is in the
        // sheet: some pixel in the sheet carries it.
        var photoSeen = false
        var y = 0
        while (y < bitmap.height && !photoSeen) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                if (r > 140 && g < 120 && b < 120) {
                    photoSeen = true
                    break
                }
                x += 6
            }
            y += 6
        }
        assertTrue("the old entry's photo renders in the sheet", photoSeen)
        stage.controller.pause().stop().destroy()
    }
}

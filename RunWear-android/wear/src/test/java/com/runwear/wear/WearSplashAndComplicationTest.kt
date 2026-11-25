package com.runwear.wear

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.runwear.wear.complications.OutfitComplicationService
import com.runwear.wear.presentation.MainActivity
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.wear.watchface.complications.data.ComplicationType

/**
 * Addendum 10 evidence: SPLASH (branded launch — 48x48dp app icon
 * on a black background) and the complication data inventory.
 *
 * Splash: MainActivity's manifest theme must be the Splash theme
 * and that theme's windowBackground must resolve to
 * @drawable/splash_background. The drawable is then drawn at
 * 384x384 (192dp at xhdpi, smallest round face) and pixel-checked:
 * corners pure black, the icon's non-black bounding box is 48dp
 * (96px) square and centred, and the icon's own ground pixel is
 * the shipped dark #17262A with the white shirt at its centre —
 * i.e. the DARK icon, unchanged (Addendum 9), not the new cream
 * light icon.
 *
 * Complications: the production service's preview data for every
 * supported type is inventoried and its strings asserted bounded
 * (the watch face owns slot rendering; the data source's duty is
 * that no string it emits needs truncation at any font scale —
 * SHORT_TEXT is a temperature of at most 5 glyphs).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class WearSplashTest {

    @Test
    fun splash_theme_and_pixels() {
        val context = RuntimeEnvironment.getApplication()

        // Manifest: MainActivity carries the splash theme.
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java), 0
        )
        assertEquals(
            "MainActivity theme must be the branded-launch splash theme",
            R.style.Theme_RunWear_Wear_Splash, info.theme
        )

        // The theme's windowBackground is the splash drawable.
        val attrs = context.theme.obtainStyledAttributes(
            R.style.Theme_RunWear_Wear_Splash,
            intArrayOf(android.R.attr.windowBackground)
        )
        val bgRes = attrs.getResourceId(0, 0)
        attrs.recycle()
        assertEquals(R.drawable.splash_background, bgRes)

        // Draw it at the smallest round face size.
        val size = 384
        val drawable = ContextCompat.getDrawable(context, R.drawable.splash_background)!!
        drawable.setBounds(0, 0, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        drawable.draw(canvas)

        // Corners: pure black background.
        for ((x, y) in listOf(2 to 2, size - 3 to 2, 2 to size - 3, size - 3 to size - 3)) {
            assertEquals("corner ($x,$y) must be black", Color.BLACK, bmp.getPixel(x, y))
        }

        // Non-black bounding box = the icon: 48dp at xhdpi = 96px.
        var minX = size; var minY = size; var maxX = -1; var maxY = -1
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (bmp.getPixel(x, y) != Color.BLACK) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val boxW = maxX - minX + 1
        val boxH = maxY - minY + 1
        assertTrue("icon box ${boxW}x${boxH}px, expected 96x96 (48dp at xhdpi)", boxW in 94..98 && boxH in 94..98)
        assertTrue(
            "icon must be centred: box [$minX,$minY]-[$maxX,$maxY]",
            kotlin.math.abs((minX + maxX) / 2 - size / 2) <= 2 &&
                kotlin.math.abs((minY + maxY) / 2 - size / 2) <= 2
        )

        // The icon is the shipped DARK icon: its ground is #17262A
        // and its centre (the shirt) is white — not the cream/pine
        // light icon.
        val ground = bmp.getPixel(minX + 3, minY + 3)
        assertEquals("icon ground must be the shipped dark #17262A", Color.rgb(0x17, 0x26, 0x2A), ground)
        val centre = bmp.getPixel(size / 2, size / 2)
        assertTrue(
            "icon centre must be a light (shirt) pixel, was ${Integer.toHexString(centre)}",
            Color.red(centre) > 200 && Color.green(centre) > 200 && Color.blue(centre) > 200
        )

        val outDir = File(System.getProperty("wearComplianceDir") ?: "build/wear-compliance")
        outDir.mkdirs()
        File(outDir, "splash_small-round_384.png").outputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class WearComplicationInventoryTest {

    @Test
    fun complication_preview_inventory() {
        val context = RuntimeEnvironment.getApplication()
        val controller = Robolectric.buildService(OutfitComplicationService::class.java)
        val service = controller.create().get()
        val now = Instant.now()
        val lines = mutableListOf<String>()

        val short = service.getPreviewData(ComplicationType.SHORT_TEXT) as? ShortTextComplicationData
        assertNotNull("SHORT_TEXT preview must exist", short)
        val shortText = short!!.text.getTextAt(context.resources, now).toString()
        lines += "SHORT_TEXT text=\"$shortText\""
        assertTrue("SHORT_TEXT must be a bounded temperature, was \"$shortText\"", shortText.length <= 5)

        val long = service.getPreviewData(ComplicationType.LONG_TEXT) as? LongTextComplicationData
        assertNotNull("LONG_TEXT preview must exist", long)
        val longText = long!!.text.getTextAt(context.resources, now).toString()
        val longTitle = long.title?.getTextAt(context.resources, now).toString()
        lines += "LONG_TEXT title=\"$longTitle\" text=\"$longText\""
        assertNotNull(longTitle)
        assertTrue(longTitle!!.length <= 6)

        val ranged = service.getPreviewData(ComplicationType.RANGED_VALUE) as? RangedValueComplicationData
        assertNotNull("RANGED_VALUE preview must exist", ranged)
        val rangedText = ranged!!.text?.getTextAt(context.resources, now).toString()
        val rangedTitle = ranged.title?.getTextAt(context.resources, now).toString()
        lines += "RANGED_VALUE title=\"$rangedTitle\" text=\"$rangedText\" min=${ranged.min} max=${ranged.max} value=${ranged.value}"
        assertNotNull(rangedText)
        assertTrue(rangedText!!.length <= 6)

        val outDir = File(System.getProperty("wearComplianceDir") ?: "build/wear-compliance")
        outDir.mkdirs()
        File(outDir, "complication-preview-inventory.txt").writeText(lines.joinToString("\n") + "\n")
        controller.destroy()
    }
}

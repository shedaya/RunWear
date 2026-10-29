package com.runwear.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.glance.appwidget.AppWidgetId
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlinx.datetime.LocalDateTime
import java.time.LocalDateTime as JavaLocalDateTime

/**
 * Genuine renders of the production widget (Robolectric native
 * graphics — this VM has no emulator), following the harness
 * established on the 2026-10-25 redesign branch:
 *
 *  - A plain android.app.Application (no Hilt app): provideGlance
 *    reads the seeded last-good store FIRST and only resolves the
 *    Hilt entry point when a reload is needed, so a fresh fixture
 *    renders the production fresh path with no DI graph, no network.
 *  - The widget is registered with the shadow AppWidgetManager by
 *    hand (putWidgetInfo + addBoundWidget), and the session is driven
 *    by a direct update() launched on Dispatchers.Default — running it
 *    under runBlocking on the test thread deadlocks the session
 *    against the paused main looper.
 *  - The production receiver's SizeMode is Exact, so the session
 *    composes the single reported size and the shadow materializes
 *    exactly that variant.
 *
 * The capture is drawn over a flat launcher-wallpaper color so
 * truncation, overlap, and dead space judge against a realistic
 * backdrop, and a blank-capture assertion guards the harness itself.
 *
 * PNGs land in build/render-out/ under the module dir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [33], qualifiers = "xhdpi")
class WidgetRenderTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun weather(
        at: LocalDateTime,
        temp: Double,
        feels: Double,
        wind: Double,
        code: WeatherCode
    ) = WeatherConditions(
        dateTime = at,
        temperature = temp,
        feelsLike = feels,
        humidity = 55,
        windSpeed = wind,
        windGusts = wind + 4,
        precipitationProbability = 10,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = code == WeatherCode.SNOW_SLIGHT || code == WeatherCode.SNOW_MODERATE,
        cloudCover = 90,
        uvIndex = 2.0,
        weatherCode = code,
        isCelsius = false
    )

    private fun fixture(
        weather: WeatherConditions,
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        savedAt: JavaLocalDateTime
    ) = WidgetLastGood(
        weather = weather,
        outfit = outfit,
        locationName = "New York, New York",
        latitude = 40.78,
        longitude = -73.95,
        laterTodayLine = laterTodayLine,
        savedAtLocal = savedAt,
        savedAtEpochMs = System.currentTimeMillis()
    )

    /** Sol's Oct 28 screenshot scenario: cold overcast morning, two pieces, daypart change. */
    @Test
    fun renderMildDayTile() {
        val at = LocalDateTime(2026, 10, 28, 10, 15)
        val w = weather(at, temp = 49.0, feels = 42.0, wind = 6.0, code = WeatherCode.OVERCAST)
        val lastGood = fixture(
            weather = w,
            outfit = OutfitRecommendation(
                weather = w,
                topBase = ClothingItem.LONG_SLEEVE_LIGHT,
                bottom = ClothingItem.LIGHT_TIGHTS
            ),
            laterTodayLine = "By 1 PM: drop the Light Long Sleeve \u00B7 add the Light Jacket",
            savedAt = JavaLocalDateTime.of(2026, 10, 28, 10, 25)
        )
        render(lastGood, "runwear-widget-121-mild.png")
    }

    /** Deep winter: seven pieces — garment rows, one chip, and the computed "+2 more". */
    @Test
    fun renderDeepWinterTile() {
        val at = LocalDateTime(2026, 1, 15, 7, 30)
        val w = weather(at, temp = 14.0, feels = 8.0, wind = 14.0, code = WeatherCode.SNOW_SLIGHT)
        val lastGood = fixture(
            weather = w,
            outfit = OutfitRecommendation(
                weather = w,
                topBase = ClothingItem.LONG_SLEEVE_THERMAL,
                topMid = ClothingItem.QUARTER_ZIP,
                topOuter = ClothingItem.INSULATED_JACKET,
                bottom = ClothingItem.THERMAL_TIGHTS,
                head = ClothingItem.THERMAL_BEANIE,
                hands = ClothingItem.THERMAL_GLOVES,
                accessories = listOf(ClothingItem.NECK_GAITER)
            ),
            laterTodayLine = "By 2 PM: drop the Insulated Jacket \u00B7 snow 40%",
            savedAt = JavaLocalDateTime.of(2026, 1, 15, 7, 32)
        )
        render(lastGood, "runwear-widget-121-winter.png")
    }

    private var nextWidgetId = 7100

    private fun render(lastGood: WidgetLastGood, outName: String) {
        runBlocking { WidgetLastGoodStore(context).write(lastGood) }

        val manager = AppWidgetManager.getInstance(context)
        val shadow = Shadows.shadowOf(manager)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, OutfitWidgetReceiver::class.java)
        }
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, TILE_W_DP)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, TILE_W_DP)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, TILE_H_DP)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, TILE_H_DP)
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(TILE_W_DP.toFloat(), TILE_H_DP.toFloat()))
            )
        }

        var view: View? = null
        var attempt = 0
        while (view == null && attempt < 3) {
            attempt++
            val attemptId = nextWidgetId++
            shadow.putWidgetInfo(attemptId, info)
            shadow.addBoundWidget(attemptId, info)
            manager.updateAppWidgetOptions(attemptId, options)
            val job = CoroutineScope(Dispatchers.Default).launch {
                // The production widget's sizeMode is already Exact.
                OutfitWidget().update(context, AppWidgetId(attemptId))
            }
            repeat(300) {
                Robolectric.flushForegroundThreadScheduler()
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                view = runCatching { shadow.getViewFor(attemptId) }.getOrNull()
                if (view != null) return@repeat
                Thread.sleep(50)
            }
            job.cancel()
        }
        assertNotNull("Glance session never posted a view for $outName", view)
        val rendered: View = view!!

        val density = context.resources.displayMetrics.density
        val wPx = (TILE_W_DP * density).toInt()
        val hPx = (TILE_H_DP * density).toInt()
        val parent = FrameLayout(context)
        parent.addView(rendered)
        rendered.measure(
            View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
        )
        rendered.layout(0, 0, wPx, hPx)
        val bitmap = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x3D, 0x6E, 0x8C))
        rendered.draw(canvas)

        val outDir = File("build/render-out").apply { mkdirs() }
        FileOutputStream(File(outDir, outName)).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // Blank-capture guard (the redesign harness's check): a real
        // tile lights well over 1% of sampled pixels.
        var lit = 0
        var sampled = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val lum = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
                if (lum > 40) lit++
                sampled++
                x += 4
            }
            y += 4
        }
        assertTrue(
            "widget capture $outName looks blank ($lit/$sampled)",
            sampled > 0 && lit > sampled / 100
        )
    }

    companion object {
        // The size class of Sol's Oct 28, 2026 screenshot tile.
        const val TILE_W_DP = 232
        const val TILE_H_DP = 240
    }
}

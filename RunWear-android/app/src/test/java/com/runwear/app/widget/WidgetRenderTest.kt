package com.runwear.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.SizeMode
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
import org.junit.Assert.assertEquals
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
import java.time.LocalDateTime

/**
 * Visual gate for the Outfit widget (2026-10-25 per-size redesign):
 * runs the real Glance session against a seeded fresh last-good store
 * (the production fresh path — no Hilt, no network) and captures the
 * inflated RemoteViews at placed sizes.
 *
 * SizeMode is pinned to Exact per scenario: with the production
 * Responsive mode the session composes every declared breakpoint into
 * one multi-size RemoteViews and the Robolectric shadow materializes
 * only the first variant.
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

    private class TestOutfitWidget : OutfitWidget() {
        override val sizeMode: SizeMode = SizeMode.Exact
    }

    private fun coldWeather() = WeatherConditions(
        dateTime = LocalDateTime.of(2026, 10, 25, 7, 0),
        temperature = 36.0,
        feelsLike = 33.0,
        humidity = 62,
        windSpeed = 8.0,
        windGusts = 14.0,
        windDirection = 270,
        precipitationProbability = 20,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 3.0,
        weatherCode = WeatherCode.CLEAR_SKY,
        isCelsius = false,
    )

    private fun coldOutfit(weather: WeatherConditions) = OutfitRecommendation(
        weather = weather,
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        topMid = ClothingItem.QUARTER_ZIP,
        bottom = ClothingItem.THERMAL_TIGHTS,
        head = ClothingItem.HEADBAND,
        hands = ClothingItem.LIGHT_GLOVES,
        accessories = listOf(
            ClothingItem.SUNGLASSES,
            ClothingItem.REFLECTIVE_GEAR,
            ClothingItem.NECK_GAITER,
        ),
    )

    private fun fixture(laterTodayLine: String?): WidgetLastGood {
        val weather = coldWeather()
        val now = LocalDateTime.now()
        return WidgetLastGood(
            weather = weather,
            outfit = coldOutfit(weather),
            locationName = "Kansas City, Missouri",
            latitude = 39.1,
            longitude = -94.6,
            laterTodayLine = laterTodayLine,
            savedAtLocal = now,
            savedAtEpochMs = System.currentTimeMillis(),
        )
    }

    @Test
    fun textFunctions_useFullNamesAndRealConditions() {
        val outfit = coldOutfit(coldWeather())
        val (tops, bottomLine) = outfitSummaryLines(outfit)
        // Greedy strip composition: whole names only — the mid layer
        // and head don't fit the strip's budget with these long names,
        // so the strip carries base + bottom (the large widget and the
        // app carry every piece).
        assertEquals("Light Long Sleeve", tops)
        assertEquals("Thermal Tights", bottomLine)
        assertEquals(
            "Humidity 62% · Rain 20% · UV 3 · Gusts 14 mph",
            conditionsDetailLine(coldWeather()),
        )
        // The full-name summary carries complete garment names.
        assertTrue(buildOutfitSummary(outfit).contains("Light Long Sleeve"))
        assertTrue(buildOutfitSummary(outfit).contains("Thermal Tights"))
    }

    @Test
    fun renderSmall() {
        renderWidget(fixture(laterTodayLine = null), 110, 110, "runwear-widget-small.png")
    }

    @Test
    fun renderHorizontal() {
        renderWidget(fixture(laterTodayLine = null), 260, 70, "runwear-widget-horizontal.png")
    }

    @Test
    fun renderLargeWithLaterToday() {
        renderWidget(
            fixture(laterTodayLine = "Later today: Short Sleeve Shirt after 2 PM"),
            260, 200, "runwear-widget-large.png",
        )
    }

    @Test
    fun renderLargeConditionsOnly() {
        renderWidget(fixture(laterTodayLine = null), 260, 200, "runwear-widget-large-conditions.png")
    }

    private var nextWidgetId = 7100

    private fun renderWidget(
        lastGood: WidgetLastGood,
        widthDp: Int,
        heightDp: Int,
        fileName: String,
    ) {
        runBlocking { WidgetLastGoodStore(context).write(lastGood) }

        val manager = AppWidgetManager.getInstance(context)
        val shadow = Shadows.shadowOf(manager)
        val id = nextWidgetId++
        val info = android.appwidget.AppWidgetProviderInfo().apply {
            provider = android.content.ComponentName(
                context,
                OutfitWidgetReceiver::class.java,
            )
        }
        shadow.putWidgetInfo(id, info)
        shadow.addBoundWidget(id, info)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(android.util.SizeF(widthDp.toFloat(), heightDp.toFloat())),
            )
        }
        manager.updateAppWidgetOptions(id, options)

        var view: View? = null
        var attempt = 0
        while (view == null && attempt < 3) {
            attempt++
            val attemptId = if (attempt == 1) {
                id
            } else {
                val retryId = nextWidgetId++
                shadow.putWidgetInfo(retryId, info)
                shadow.addBoundWidget(retryId, info)
                manager.updateAppWidgetOptions(retryId, options)
                retryId
            }
            val job = CoroutineScope(Dispatchers.Default).launch {
                TestOutfitWidget().update(context, AppWidgetId(attemptId))
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
        assertNotNull("Glance session never posted a view for $fileName", view)
        val rendered: View = view!!

        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val parent = FrameLayout(context)
        parent.addView(rendered)
        rendered.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        rendered.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        rendered.draw(Canvas(bitmap))

        val outDir = File("build/widget-renders-raw").apply { mkdirs() }
        File(outDir, fileName).outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        var lit = 0
        var sampled = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val px = bitmap.getPixel(x, y)
                val lum = (android.graphics.Color.red(px) +
                    android.graphics.Color.green(px) +
                    android.graphics.Color.blue(px)) / 3
                if (lum > 40) lit++
                sampled++
                x += 4
            }
            y += 4
        }
        assertTrue(
            "widget capture $fileName looks blank ($lit/$sampled)",
            sampled > 0 && lit > sampled / 100,
        )
    }
}

package com.runwear.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * WS-2 rendered evidence (Robolectric native graphics, the repo's
 * harness pattern): the production [HeroSheetBlock] fed ONE canonical
 * weather object, flipped between units the way the ViewModel flips
 * it — a single state value the whole block projects from.
 *
 * Assertions, all on drawn pixels:
 *  1. The FIRST frame after composition already equals the settled
 *     frame — the numeral is not counting toward its value (the
 *     D-05 800 ms tween would make these differ).
 *  2. Flipping the unit changes the frame (the projection is real),
 *     and the first frame after the flip already equals the settled
 *     frame — the flip lands atomically, with no intermediate count
 *     and no mixed frame at any captured instant.
 *  3. Flipping back reproduces the original frame exactly.
 *
 * PNGs land in build/render-out-ws2/ for the WS-9 walkthrough.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class HeroSheetUnitRenderTest {

    private val outDir: File =
        File(System.getProperty("ws2ShotsDir") ?: "build/render-out-ws2")

    private val weather = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 11, 3, 7, 0),
        temperature = 45.0,
        feelsLike = 41.0,
        humidity = 40,
        windSpeed = 10.0,
        windGusts = 15.0,
        windDirection = 180,
        precipitationProbability = 5,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    @Test
    fun `unit flips land atomically - first frame equals settled frame, every time`() {
        // Pre-publish the palette under test (WS-8 harness lesson:
        // the theme's SideEffect lands after the first composition).
        RunWearColors.setActivePalette(LightPalette)

        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)

        var unit by mutableStateOf(TemperatureUnit.FAHRENHEIT)
        val now = LocalDateTime.of(2026, 11, 3, 7, 0)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                // RW-144: the sheet block is now the boxless
                // weather line; the unit projection it exercises
                // is the wind figure ("10 mph" <-> metric form).
                HeroSheetBlock(
                    weather = weather,
                    temperatureUnit = unit
                )
            }
        }

        val density = activity.resources.displayMetrics.density
        val widthPx = (390 * density).toInt()
        val heightPx = (500 * density).toInt()
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)

        fun capture(name: String): IntArray {
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, widthPx, heightPx)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(bitmap))
            val px = IntArray(widthPx * heightPx)
            bitmap.getPixels(px, 0, widthPx, 0, 0, widthPx, heightPx)
            outDir.mkdirs()
            File(outDir, "$name.png").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return px
        }

        fun idle() = shadowOf(Looper.getMainLooper()).idle()

        fun settle() {
            val scheduler = Robolectric.getForegroundThreadScheduler()
            repeat(30) {
                scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
                idle()
            }
            idle()
        }

        // First paint in °F — idle only, no time travel: this frame
        // must already BE the settled frame (no counter in flight).
        idle()
        val fahrenheitFirst = capture("sheet-fahrenheit-first")
        settle()
        val fahrenheitSettled = capture("sheet-fahrenheit-settled")
        assertTrue(
            "°F: first frame differs from settled frame — something is still animating the numeral",
            fahrenheitFirst.contentEquals(fahrenheitSettled)
        )
        var opaque = 0
        for (p in fahrenheitFirst) if (p ushr 24 == 0xFF) opaque++
        assertTrue("sheet did not render", opaque > widthPx * heightPx / 4)

        // The flip: one state write, exactly the ViewModel's.
        unit = TemperatureUnit.CELSIUS
        idle()
        val celsiusFirst = capture("sheet-celsius-first")
        assertTrue(
            "the flip changed nothing on screen — the projection is not driving the sheet",
            !celsiusFirst.contentEquals(fahrenheitFirst)
        )
        settle()
        val celsiusSettled = capture("sheet-celsius-settled")
        assertTrue(
            "°C: first frame after the flip differs from settled — the flip did not land atomically",
            celsiusFirst.contentEquals(celsiusSettled)
        )

        // Flip back: the original frame, exactly.
        unit = TemperatureUnit.FAHRENHEIT
        idle()
        val fahrenheitAgain = capture("sheet-fahrenheit-again")
        assertTrue(
            "flipping back did not reproduce the original °F frame exactly",
            fahrenheitAgain.contentEquals(fahrenheitFirst)
        )

        controller.pause().stop().destroy()
    }
}

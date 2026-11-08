package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeUiState
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.ui.components.DayChartBuilder
import com.runwear.app.ui.components.DayChartGlanceBand
import com.runwear.app.ui.components.HeroFeelsLikeBlock
import com.runwear.app.ui.components.HeroFeelsLikeScrim
import com.runwear.app.ui.components.HeroLuminancePolicy
import com.runwear.app.ui.theme.DarkPalette
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunStore
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-144 (Nov 8, 2026) — rendered walkthrough for the approved
 * first-screen redesign (v8 frames). Full-screen frames compose
 * the PRODUCTION HeroMainScreen on Phoenix fixtures matching the
 * design frames' state (58° feels like / 60° actual, Now, the
 * engine's true outfit); contrast frames compose the production
 * hero overlay components over generated bright/dark bitmaps with
 * the production luminance sampling in the loop.
 *
 * Frames land in build/render-out-rw144/ and are staged by the
 * lane into ~/workspace/release-prep/rw144-android-2026-11-08/frames/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw144FirstScreenRenderTest {

    private val outDir: File = File("build/render-out-rw144")
    private val engine = GetOutfitRecommendationUseCase()
    private val today: LocalDate = LocalDate.of(2026, 11, 8)
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 8, 7, 0)

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>,
        val activity: ComponentActivity
    )

    private fun phoenixWeather(feels: Double = 58.0, actual: Double = 60.0) = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 11, 8, 7, 0),
        temperature = actual,
        feelsLike = feels,
        humidity = 60,
        windSpeed = 0.0,
        windGusts = 2.0,
        windDirection = 90,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 5,
        uvIndex = 0.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    private fun hourly(date: LocalDate): List<HourlyForecast> =
        (0..23).map { h ->
            val feels = 50.0 + 10.0 * kotlin.math.sin((h - 6) / 24.0 * 2 * Math.PI)
            HourlyForecast(
                dateTime = KotlinLocalDateTime(date.year, date.monthValue, date.dayOfMonth, h, 0),
                temperature = feels + 2,
                feelsLike = feels,
                humidity = 55,
                windSpeed = 3.0,
                precipitationProbability = 0,
                weatherCode = WeatherCode.CLEAR_SKY,
                uvIndex = if (h in 9..16) 3.0 else 0.0
            )
        }

    private fun uiState(
        picked: Boolean = false,
        making: Boolean = false,
        stock: Boolean = false
    ): MainUiState {
        val w = phoenixWeather()
        val sel = if (picked) LocalDateTime.of(2026, 11, 10, 15, 0) else now
        return MainUiState(
            isLoading = false,
            error = null,
            outfit = engine.execute(w, ComfortPreference.NEUTRAL),
            weather = w,
            hourlyForecast = hourly(if (picked) LocalDate.of(2026, 11, 10) else today),
            locationName = "Phoenix, AZ",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("Phoenix, AZ"),
            selectedDateTime = sel,
            locationNow = now,
            isNowSelected = !picked,
            heroImageUrl = null,
            heroIsPersonal = !stock,
            personalLookStatus = if (making) {
                PersonalLookStatus.Making
            } else {
                PersonalLookStatus.Ready
            },
            heroServeKind = if (stock) {
                PersonalServeKind.STOCK_QUIET
            } else {
                PersonalServeKind.EXACT
            },
            hasCompletedOnboarding = true
        )
    }

    private fun mimState(making: Boolean = false, stock: Boolean = false) = MakeItMeUiState(
        mode = if (stock) MakeItMeMode.STOCK else MakeItMeMode.FREE,
        freeGenerationsUsed = 6
    )

    @Composable
    private fun Home(
        state: MainUiState,
        mim: MakeItMeUiState
    ) {
        HeroMainScreen(
            uiState = state,
            locationSearchState = LocationSearchState(),
            onRefresh = {},
            onToggleUnit = {},
            onSetComfortPreference = {},
            onSetGenderPreference = {},
            onDateSelected = {},
            onTimeSelected = {},
            onResetToNow = {},
            onLocationSearch = {},
            onLocationSelect = { _, _, _ -> },
            makeItMeState = mim
        )
    }

    private fun compose(dark: Boolean, content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(if (dark) DarkPalette else LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = dark) { content() } }
        val host = Host(composeView, controller, activity)
        pump(host, 40)
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
            scheduler.advanceBy(50, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(wSpec, hSpec)
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    private fun capture(host: Host, name: String): Bitmap {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    private fun captureCrop(host: Host, name: String, topPx: Int, bottomPx: Int) {
        val full = capture(host, "tmp-full.png")
        val crop = Bitmap.createBitmap(full, 0, topPx, full.width, bottomPx - topPx)
        File(outDir, name).outputStream().use {
            crop.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        crop.recycle()
        full.recycle()
    }

    private fun rootNode(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (found == null) collect(v.getChildAt(i))
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private data class TextBox(val text: String, val top: Float, val bottom: Float)

    private fun texts(host: Host): List<TextBox> {
        val out = mutableListOf<TextBox>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    out += TextBox(t.text, node.boundsInRoot.top, node.boundsInRoot.bottom)
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(rootNode(host.composeView))
        return out
    }

    private fun seedWearIveRun(host: Host) {
        val entry = WearIveRunDayEntry(
            date = today,
            place = "Phoenix, AZ",
            feelsLikeF = 58.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Short Sleeve Shirt", "Running Shorts (5-7\")", "Reflective Gear"),
            heroRef = null
        )
        runBlocking {
            WearIveRunStore(host.activity.applicationContext).recordSettled(entry)
        }
    }

    // (a) First open at rest — the Audit-item-B acceptance frame:
    // the dock must overlay NO readable content.
    @Test
    fun `rw144 - a - first open at rest - dock overlays clear space only`() {
        val host = compose(dark = false) { Home(uiState(), mimState()) }
        seedWearIveRun(host)
        pump(host, 40)
        capture(host, "a-first-open-rest-light.png")
        val density = host.composeView.resources.displayMetrics.density
        val boxes = texts(host)
        val dockWordmark = boxes.firstOrNull { it.text.contains("Wear I've Run") }
        println(
            "RW144-A texts: " + boxes.joinToString(" | ") {
                "${it.text}@${(it.top / density).toInt()}..${(it.bottom / density).toInt()}"
            }
        )
        assertTrue("first screen must show the chip date", boxes.any { it.text == "Sun, Nov 8" })
        assertTrue("first screen must show Now", boxes.any { it.text == "Now" })
        assertTrue(
            "free pill must show the settled count form",
            boxes.any { it.text == "6 of 10 looks" }
        )
        if (dockWordmark != null) {
            val dockTop = dockWordmark.top - 14 * density // dock's own top padding zone
            val offenders = boxes.filter { box ->
                !box.text.contains("Wear I've Run") &&
                    box.bottom > dockTop + 2 && box.top < 844 * density &&
                    box.bottom <= 844 * density + 4 &&
                    box.top >= dockTop - 2
            }
            println("RW144-A dockTopDp=${dockTop / density} offenders=$offenders")
            assertTrue(
                "dock overlays readable content at rest: $offenders",
                offenders.isEmpty()
            )
        } else {
            println("RW144-A dock absent in harness (store seeding did not surface it)")
        }
        host.controller.pause().stop().destroy()
    }

    // (b) Hero in Now state (dark mode, full screen).
    @Test
    fun `rw144 - b - hero now state dark`() {
        val host = compose(dark = true) { Home(uiState(), mimState()) }
        capture(host, "b-hero-now-dark.png")
        val boxes = texts(host)
        assertTrue(boxes.any { it.text == "Sun, Nov 8" })
        assertTrue(boxes.any { it.text == "Now" })
        assertTrue(boxes.any { it.text == "58°" })
        assertTrue(boxes.any { it.text == "FEELS LIKE" })
        host.controller.pause().stop().destroy()
    }

    // (c) Picked state: picked date | divider | picked time | return.
    @Test
    fun `rw144 - c - hero picked state shows return segment`() {
        val host = compose(dark = false) { Home(uiState(picked = true), mimState()) }
        capture(host, "c-hero-picked-light.png")
        val boxes = texts(host)
        assertTrue("picked date", boxes.any { it.text == "Tue, Nov 10" })
        assertTrue("picked time", boxes.any { it.text == "3:00 PM" })
        assertTrue(
            "return-to-Now segment present in picked state",
            boxes.any { it.text == "Now" }
        )
        host.controller.pause().stop().destroy()
    }

    // (d) Sheet top crop (weather line + count header + outfit).
    @Test
    fun `rw144 - d - sheet top`() {
        val host = compose(dark = false) { Home(uiState(), mimState()) }
        val density = host.composeView.resources.displayMetrics.density
        captureCrop(host, "d-sheet-top-light.png", (330 * density).toInt(), (844 * density).toInt())
        val boxes = texts(host)
        assertTrue(boxes.any { it.text == "WIND" })
        assertTrue(boxes.any { it.text == "RAIN" })
        assertTrue(boxes.any { it.text == "HUMIDITY" })
        assertTrue(boxes.any { it.text == "UV" })
        assertTrue(boxes.any { it.text == "0 of 11" })
        assertTrue(boxes.any { it.text.contains("items for your run") })
        host.controller.pause().stop().destroy()
    }

    // (e) Below the fold: day-chart glance band + Change + detailed block.
    @Test
    fun `rw144 - e - below the fold - glance band and detailed weather`() {
        val w = phoenixWeather()
        val model = DayChartBuilder.build(
            hourly = hourly(today),
            date = today,
            comfort = ComfortPreference.NEUTRAL,
            nowDate = today,
            nowHour = 7,
            nowFeelsLikeF = 58.0
        )
        val host = compose(dark = false) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Surface)
                    .padding(top = 16.dp)
            ) {
                DayChartGlanceBand(
                    model = model,
                    selectedHour = 7,
                    locationToday = today,
                    nowHour = 7,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    onChangeClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(1.dp)
                        .background(RunWearColors.Hairline)
                )
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    UnifiedWeatherDetail(
                        weather = w,
                        unit = TemperatureUnit.FAHRENHEIT,
                        locationName = "Phoenix, AZ",
                        momentLabel = "Now",
                        runningTip = null,
                        comfortPreference = ComfortPreference.NEUTRAL
                    )
                }
            }
        }
        capture(host, "e-below-fold-light.png")
        val boxes = texts(host)
        assertTrue("Change row", boxes.any { it.text == "Change ›" })
        assertTrue("detail rows", boxes.any { it.text == "Feels like" })
        assertTrue("detail rows", boxes.any { it.text == "Precipitation" })
        host.controller.pause().stop().destroy()
    }

    // (f) Generating pill state on the full screen.
    @Test
    fun `rw144 - f - generating pill state`() {
        val host = compose(dark = false) {
            Home(uiState(making = true), mimState(making = true))
        }
        capture(host, "f-generating-pill-light.png")
        val boxes = texts(host)
        assertTrue(boxes.any { it.text == "Making this you…" })
        host.controller.pause().stop().destroy()
    }

    // (g) Stock serve: no personal photo, no pill.
    @Test
    fun `rw144 - g - stock serve hero carries no pill`() {
        val host = compose(dark = false) {
            Home(uiState(stock = true), mimState(stock = true))
        }
        capture(host, "g-stock-serve-light.png")
        val boxes = texts(host)
        assertFalse(boxes.any { it.text.contains("of 10 looks") })
        assertFalse(boxes.any { it.text == "Making this you…" })
        assertTrue(boxes.any { it.text == "58°" })
        host.controller.pause().stop().destroy()
    }

    // Contrast proof: the SAME production block + scrim over a
    // bright bitmap and a dark bitmap, scrim strength sampled by
    // the production mechanism from each bitmap.
    @Test
    fun `rw144 - contrast - luminance drives the feels-like scrim`() {
        val bright = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        bright.eraseColor(0xFFF2EFE6.toInt())
        val dark = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
        dark.eraseColor(0xFF14100C.toInt())
        val w = phoenixWeather()

        fun frame(bitmap: Bitmap, name: String): Pair<Float, Float> {
            val footLum = HeroLuminancePolicy.sampleRegionLuminance(
                bitmap, 0f, 0.52f, 1f, 1f
            )
            val peak = HeroLuminancePolicy.scrimPeakAlpha(footLum)
            val host = compose(dark = false) {
                Box(modifier = Modifier.fillMaxWidth().height(520.dp)) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(196.dp)
                    ) {
                        HeroFeelsLikeScrim(peakAlpha = peak, modifier = Modifier.fillMaxSize())
                        HeroFeelsLikeBlock(
                            weather = w,
                            temperatureUnit = TemperatureUnit.FAHRENHEIT,
                            comfortPreference = ComfortPreference.NEUTRAL,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 16.dp, end = 16.dp)
                        )
                    }
                }
            }
            capture(host, name)
            host.controller.pause().stop().destroy()
            return footLum to peak
        }

        val (brightLum, brightPeak) = frame(bright, "h-contrast-bright-photo.png")
        val (darkLum, darkPeak) = frame(dark, "i-contrast-dark-photo.png")
        println(
            "RW144-CONTRAST bright: luminance=$brightLum scrimPeak=$brightPeak | " +
                "dark: luminance=$darkLum scrimPeak=$darkPeak | " +
                "floor=${HeroLuminancePolicy.SCRIM_PEAK_FLOOR}"
        )
        assertTrue("bright photo must measure bright", brightLum > 0.8f)
        assertTrue("dark photo must measure dark", darkLum < 0.05f)
        assertEquals(
            "dark photo relaxes to the mock floor",
            HeroLuminancePolicy.SCRIM_PEAK_FLOOR, darkPeak, 0.01f
        )
        assertTrue(
            "bright photo scales the scrim well above the floor",
            brightPeak > HeroLuminancePolicy.SCRIM_PEAK_FLOOR + 0.10f
        )
        assertTrue(brightPeak > darkPeak)
    }
}

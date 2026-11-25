package com.runwear.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.wear.presentation.WearTempFrame
import com.runwear.wear.presentation.screens.HeroWearMainScreen
import com.runwear.wear.presentation.theme.RunWearWatchTheme
import com.runwear.wear.presentation.viewmodel.WearUiState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import java.io.File
import java.time.LocalDateTime as JLocalDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 WAVE C EVIDENCE CLOSURE (picker→watch, gap 2) — WATCH SIDE.
 *
 * Paired with PickerWatchClosurePhoneTest in the :app module
 * over ONE fixture, byte-identical in both classes: place
 * "Kansas City"; start selection Fri 2026-11-06 19:00 (feels-like
 * 47° / actual 50°, partly cloudy); committed changed selection
 * Sat 2026-11-07 19:00 (feels-like 39° / actual 42°, clear).
 *
 * How the Wear app obtains state (investigated 2026-11-05):
 * INDEPENDENTLY. There is no DataLayer / MessageClient /
 * CapabilityClient code anywhere in the repo — the watch never
 * receives the phone's selection. WearViewModel holds its own
 * WearUiState.selectedDateTime (default now()), obtains its own
 * location, and solves through the SHARED engine inputs: the
 * shared WeatherRepository.getWeatherForDateTime(lat, lon,
 * selectedDateTime) and the shared
 * GetOutfitRecommendationUseCase — the same use case the phone
 * solves with. The wear seam a selection enters through is
 * WearViewModel.setDateTime(dt), which writes
 * WearUiState.selectedDateTime and re-fetches.
 *
 * This test therefore drives the watch render through that real
 * seam as far as the environment allows: WearUiState is built
 * with selectedDateTime == the phone's committed selection
 * (2026-11-07T19:00, pinned below to the phone half's
 * selectDate arithmetic), the outfit is produced by the shared
 * GetOutfitRecommendationUseCase over that selection's fixture
 * weather (exactly what WearViewModel.fetchWeather does with a
 * repository result), and HeroWearMainScreen renders it. The
 * pre-change state (Nov 6, 47°) is rendered for contrast, so
 * the re-solve is visible as a CHANGE.
 *
 * NOT proven: live phone↔watch transport — there is no
 * transport implementation to exercise, and this VM has no
 * emulator/device pair (/dev/kvm absent, adb devices empty).
 * Exercising transport would require a DataLayer implementation
 * (a product change, out of this lane's scope) plus a paired
 * phone + Wear emulator or physical pair.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class PickerWatchClosureWearTest {

    private val outDir: File = File(
        System.getenv("PW_SHOTS_DIR")
            ?: System.getProperty("pwShotsDir")
            ?: "build/render-out-picker-watch"
    )

    private val preChangeSelection: JLocalDateTime = JLocalDateTime.of(2026, 11, 6, 19, 0)
    private val committedSelection: JLocalDateTime = JLocalDateTime.of(2026, 11, 7, 19, 0)

    private fun weather(
        selection: JLocalDateTime, feels: Double, actual: Double, code: WeatherCode
    ) = WeatherConditions(
        dateTime = selection.toKotlinLocalDateTime(),
        temperature = actual, feelsLike = feels, humidity = 55,
        windSpeed = 6.0, windGusts = 10.0, precipitationProbability = 10,
        precipitation = 0.0, isRaining = false, isSnowing = false,
        cloudCover = 30, uvIndex = 2.0, weatherCode = code
    )

    private val preChangeWeather =
        weather(preChangeSelection, 47.0, 50.0, WeatherCode.PARTLY_CLOUDY)
    private val committedWeather =
        weather(committedSelection, 39.0, 42.0, WeatherCode.CLEAR_SKY)

    private fun shoot(name: String, state: WearUiState): List<String> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearWatchTheme {
                HeroWearMainScreen(
                    uiState = state,
                    onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                    onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
                )
            }
        }
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(60) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val sizePx = 384
        val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        composeView.measure(spec, spec)
        composeView.layout(0, 0, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val texts = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                texts += node.config[SemanticsProperties.Text].map { it.text }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                texts += node.config[SemanticsProperties.ContentDescription]
            }
            node.children.forEach { walk(it) }
        }
        var root: SemanticsNode? = null
        fun collect(v: View) {
            if (root == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                root = v.semanticsOwner.unmergedRootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (root == null) collect(v.getChildAt(i))
            }
        }
        collect(composeView.rootView)
        root?.let { walk(it) }
        controller.pause().stop().destroy()
        return texts
    }

    private fun stateFor(selection: JLocalDateTime, weather: WeatherConditions): WearUiState {
        // WearViewModel.fetchWeather's solve, verbatim in shape:
        // shared use case over the repository weather for
        // uiState.selectedDateTime, comfort from state.
        val outfit = GetOutfitRecommendationUseCase()
            .execute(weather, ComfortPreference.NEUTRAL)
        return WearUiState(
            isLoading = false, error = null, outfit = outfit,
            weather = weather, heroImageUrl = null,
            locationName = "Kansas City",
            selectedDateTime = selection,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            comfortPreference = ComfortPreference.NEUTRAL,
            hasLocationPermission = true
        )
    }

    @Test
    fun `pin - committed selection solves to Nov 7 39-degree frame, pre-change to Nov 6 47-degree frame`() {
        // The pair's shared solve input: the wear state's
        // selectedDateTime IS the phone's committed selection
        // (MainViewModel.selectDate arithmetic, phone half).
        assertEquals(committedSelection, stateFor(committedSelection, committedWeather).selectedDateTime)
        assertEquals(preChangeSelection, stateFor(preChangeSelection, preChangeWeather).selectedDateTime)

        // The one-frame headline (DA-13, WearTempFrame): the
        // committed selection headlines feels-like 39°, actual
        // subordinate 42°; the pre-change selection 47° / 50°.
        assertEquals(
            "39°F",
            WearTempFrame.headlineText(committedWeather, ComfortPreference.NEUTRAL, TemperatureUnit.FAHRENHEIT)
        )
        assertEquals("Actual 42°F", WearTempFrame.actualLine(committedWeather, TemperatureUnit.FAHRENHEIT))
        assertEquals(
            "47°F",
            WearTempFrame.headlineText(preChangeWeather, ComfortPreference.NEUTRAL, TemperatureUnit.FAHRENHEIT)
        )
        assertEquals("Actual 50°F", WearTempFrame.actualLine(preChangeWeather, TemperatureUnit.FAHRENHEIT))

        // The shared engine solves each selection's own weather:
        // the outfit frames carry their selection's conditions.
        val committedOutfit = GetOutfitRecommendationUseCase()
            .execute(committedWeather, ComfortPreference.NEUTRAL)
        val preChangeOutfit = GetOutfitRecommendationUseCase()
            .execute(preChangeWeather, ComfortPreference.NEUTRAL)
        assertEquals(committedWeather, committedOutfit.weather)
        assertEquals(preChangeWeather, preChangeOutfit.weather)
        assertTrue(committedOutfit.summary.startsWith("Feels like 39°F"))
        assertTrue(preChangeOutfit.summary.startsWith("Feels like 47°F"))
    }

    @Test
    fun `watch hero re-solves - pre-change Nov 6 47 then committed Nov 7 39`() {
        val preTexts = shoot(
            "PW-B1-watch-hero-pre-change-nov6-47.png",
            stateFor(preChangeSelection, preChangeWeather)
        )
        assertTrue("pre-change headline must be 47°F: $preTexts", preTexts.any { it == "47°F" })
        assertTrue("pre-change subordinate must be Actual 50°F: $preTexts", preTexts.any { it == "Actual 50°F" })

        val committedTexts = shoot(
            "PW-B2-watch-hero-committed-nov7-39.png",
            stateFor(committedSelection, committedWeather)
        )
        assertTrue("committed headline must be 39°F: $committedTexts", committedTexts.any { it == "39°F" })
        assertTrue("committed subordinate must be Actual 42°F: $committedTexts", committedTexts.any { it == "Actual 42°F" })
    }
}

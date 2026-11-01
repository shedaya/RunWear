package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
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
 * Genuine renders of the production [HeroMainScreen] across the
 * foldable/tablet width matrix (Robolectric native graphics — this VM
 * has no emulator), following the harness pattern established by the
 * widget render tests and the wear screenshot tests: host the
 * composable in a real [ComposeView] inside a Robolectric activity,
 * settle composition/effects/animations by walking the foreground
 * scheduler, then measure/layout/draw onto a bitmap.
 *
 * The outfit in every shot is the real engine's output
 * ([GetOutfitRecommendationUseCase]) for the shot's weather — the
 * shipped UI fed its real state, nothing mocked up. The hero photo is
 * null (the designed fallback gradient) so captures are deterministic
 * and judge layout, not photo decode timing.
 *
 * One class per window size (the @Config qualifiers fix the device
 * configuration HeroSection reads for its 75%-of-screen height).
 * PNGs land in build/render-out-fluid/.
 */
abstract class HeroFluidShotHarness {

    protected val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("fluidShotsDir") ?: "build/render-out-fluid")

    protected fun weather(tempF: Double, feelsF: Double, code: WeatherCode) =
        WeatherConditions(
            dateTime = KotlinLocalDateTime(2026, 10, 30, 7, 35),
            temperature = tempF,
            feelsLike = feelsF,
            humidity = 31,
            windSpeed = 5.0,
            windGusts = 7.0,
            precipitationProbability = 1,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 1.0,
            weatherCode = code,
            isCelsius = false
        )

    /** Sol's Aspen scenario class: 42°F actual / 33°F feels-like — layered outfit, 6+ items. */
    protected val coldWeather = weather(42.0, 33.0, WeatherCode.CLEAR_SKY)

    /** Mild morning: 60°F / 58°F feels-like — two-item outfit. */
    protected val mildWeather = weather(60.0, 58.0, WeatherCode.CLEAR_SKY)

    protected fun shoot(name: String, widthDp: Int, heightDp: Int, w: WeatherConditions) {
        val outfit = engine.execute(w, ComfortPreference.NEUTRAL)
        val now = LocalDateTime.of(2026, 10, 30, 7, 35)
        val uiState = MainUiState(
            isLoading = false,
            error = null,
            outfit = outfit,
            weather = w,
            locationName = "Aspen, Colorado",
            selectedDateTime = now,
            locationNow = now,
            isNowSelected = true,
            heroImageUrl = null,
            hasCompletedOnboarding = true
        )

        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                HeroMainScreen(
                    uiState = uiState,
                    locationSearchState = LocationSearchState(),
                    onRefresh = {},
                    onToggleUnit = {},
                    onSetComfortPreference = {},
                    onSetGenderPreference = {},
                    onDateSelected = {},
                    onTimeSelected = {},
                    onResetToNow = {},
                    onLocationSearch = {},
                    onLocationSelect = { _, _, _ -> }
                )
            }
        }

        // Settle generously (same contract as the wear harness): the
        // temperature count-up alone runs ~1s of tween after a delay,
        // and the outfit cards stagger in behind it.
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(80) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        composeView.dispatchWindowFocusChanged(true)
        composeView.requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))

        val px = IntArray(widthPx * heightPx)
        bitmap.getPixels(px, 0, widthPx, 0, 0, widthPx, heightPx)
        var opaque = 0
        for (p in px) if (p ushr 24 == 0xFF) opaque++
        assertTrue(
            "only $opaque/${widthPx * heightPx} opaque pixels — nothing rendered?",
            opaque > widthPx * heightPx / 4
        )

        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.pause().stop().destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class FluidShotCompact390Test : HeroFluidShotHarness() {
    @Test
    fun compact390Cold() = shoot("compact-390x844-cold", 390, 844, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class FluidShotCompactMild390Test : HeroFluidShotHarness() {
    @Test
    fun compact390Mild() = shoot("compact-390x844-mild", 390, 844, mildWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w600dp-h960dp-xhdpi")
class FluidShotMedium600Test : HeroFluidShotHarness() {
    @Test
    fun medium600Cold() = shoot("medium-600x960-cold", 600, 960, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w690dp-h829dp-xhdpi")
class FluidShotMedium690Test : HeroFluidShotHarness() {
    @Test
    fun medium690Cold() = shoot("medium-690x829-cold", 690, 829, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w829dp-h690dp-xhdpi")
class FluidShotMedium829LandscapeTest : HeroFluidShotHarness() {
    @Test
    fun medium829Cold() = shoot("medium-829x690-cold", 829, 690, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w1024dp-h768dp-xhdpi")
class FluidShotExpanded1024Test : HeroFluidShotHarness() {
    @Test
    fun expanded1024Cold() = shoot("expanded-1024x768-cold", 1024, 768, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w1280dp-h800dp-xhdpi")
class FluidShotExpanded1280Test : HeroFluidShotHarness() {
    @Test
    fun expanded1280Cold() = shoot("expanded-1280x800-cold", 1280, 800, coldWeather)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w1920dp-h1080dp-mdpi")
class FluidShotExpanded1920Test : HeroFluidShotHarness() {
    @Test
    fun expanded1920Cold() = shoot("expanded-1920x1080-cold", 1920, 1080, coldWeather)
}

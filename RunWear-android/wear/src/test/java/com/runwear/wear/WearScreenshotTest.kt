package com.runwear.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.wear.presentation.screens.HeroWeatherPage
import com.runwear.wear.presentation.screens.OutfitPage
import com.runwear.wear.presentation.screens.TipsPage
import com.runwear.wear.presentation.theme.RunWearWatchTheme
import java.io.File
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
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
 * Play Wear OS listing screenshots, rendered from the PRODUCTION wear
 * composables (the playbook's no-emulator route: this VM has no /dev/kvm).
 *
 * The outfit in every shot is the real recommendation engine's output
 * ([GetOutfitRecommendationUseCase], no-arg) for the shot's weather — the
 * shipped UI fed its real state, nothing mocked up.
 *
 * Capture mechanics: the compose-ui-test `captureToImage` path times out
 * under Robolectric (its forceRedraw wait never satisfies on the paused
 * looper), so the harness instead hosts the composable in a real
 * [ComposeView] inside a Robolectric activity, settles composition /
 * effects / animation by advancing the foreground scheduler, then
 * measures, lays out and draws the view onto a bitmap — the same manual
 * draw pattern as this studio's widget render tests. Frame: 456 × 456 px
 * (a Pixel Watch's resolution; 1:1, inside Play's 384–3840 px band).
 * Captures land in build/wear-screenshots/; flattening onto solid black
 * (Play rejects transparency) happens afterwards per the playbook recipe.
 *
 * A plain [Application] stands in for the Hilt app class: the harness
 * renders composables directly and never touches the DI graph.
 */
class ShotApplication : Application()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class WearScreenshotTest {

    private val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("wearShotsDir") ?: "build/wear-screenshots")

    private fun weather(
        tempF: Double,
        feelsF: Double,
        code: WeatherCode,
        humidity: Int,
        windMph: Double,
        uv: Double,
        cloudCover: Int
    ) = WeatherConditions(
        dateTime = LocalDateTime.now().toKotlinLocalDateTime(),
        temperature = tempF,
        feelsLike = feelsF,
        humidity = humidity,
        windSpeed = windMph,
        windGusts = windMph * 1.4,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = cloudCover,
        uvIndex = uv,
        weatherCode = code,
        isCelsius = false
    )

    private val mildWeather = weather(60.0, 58.0, WeatherCode.CLEAR_SKY, 45, 6.0, 5.5, 10)
    private val coldWeather = weather(30.0, 24.0, WeatherCode.OVERCAST, 55, 14.0, 0.5, 90)
    private val hotWeather = weather(91.0, 96.0, WeatherCode.CLEAR_SKY, 62, 5.0, 9.0, 5)

    private fun shoot(name: String, settleSteps: Int = 60, content: @Composable () -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        // visible() attaches the window — without it the ComposeView never
        // attaches and has no windowRecomposer to compose into
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearWatchTheme { content() }
        }

        // Settle: flush composition, LaunchedEffects and the temperature
        // count-up animation (100 ms delay + 600 ms tween) by walking the
        // foreground scheduler forward in small steps, then drain anything
        // still scheduled. The walk must be generous: under Robolectric the
        // frame clock advances slower than the scheduler's virtual time,
        // and a short settle captures the count-up mid-flight (an early
        // run froze a 24°F feels-like at 16°F).
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(settleSteps) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        val size = 456
        val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
        composeView.measure(spec, spec)
        composeView.layout(0, 0, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))

        assertEquals("capture must be square", bitmap.width, bitmap.height)
        assertTrue(
            "capture side ${bitmap.width}px outside Play's 384..3840 band",
            bitmap.width in 384..3840
        )
        // Something must actually be drawn: the hero page paints its full
        // frame, but the outfit/tips pages paint the round viewport only —
        // their square corners stay transparent by design. So instead of a
        // single corner pixel, require a solid share of opaque pixels.
        var opaque = 0
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        for (p in pixels) if (p ushr 24 == 0xFF) opaque++
        assertTrue(
            "only $opaque/${size * size} opaque pixels — nothing rendered?",
            opaque > size * size / 4
        )

        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun shot01_heroMild() {
        val outfit = engine.execute(mildWeather, ComfortPreference.NEUTRAL)
        shoot("01-hero-mild") {
            HeroWeatherPage(
                weather = mildWeather,
                heroImageUrl = null,
                locationName = "New York",
                outfitItemCount = outfit.allItems.size,
                onToggleUnit = {}
            )
        }
    }

    @Test
    fun shot02_outfitMild() {
        val outfit = engine.execute(mildWeather, ComfortPreference.NEUTRAL)
        shoot("02-outfit-mild") {
            OutfitPage(outfit = outfit, onRefresh = {}, isVisible = false)
        }
    }

    @Test
    fun shot03_tipsMild() {
        val outfit = engine.execute(mildWeather, ComfortPreference.NEUTRAL)
        shoot("03-tips-mild", settleSteps = 150) {
            TipsPage(tips = outfit.tips, locationName = "New York", isVisible = false)
        }
    }

    @Test
    fun shot04_heroCold() {
        val outfit = engine.execute(coldWeather, ComfortPreference.NEUTRAL)
        shoot("04-hero-cold") {
            HeroWeatherPage(
                weather = coldWeather,
                heroImageUrl = null,
                locationName = "New York",
                outfitItemCount = outfit.allItems.size,
                onToggleUnit = {}
            )
        }
    }

    @Test
    fun shot05_outfitCold() {
        val outfit = engine.execute(coldWeather, ComfortPreference.NEUTRAL)
        shoot("05-outfit-cold") {
            OutfitPage(outfit = outfit, onRefresh = {}, isVisible = false)
        }
    }

    @Test
    fun shot06_heroHot() {
        val outfit = engine.execute(hotWeather, ComfortPreference.NEUTRAL)
        shoot("06-hero-hot") {
            HeroWeatherPage(
                weather = hotWeather,
                heroImageUrl = null,
                locationName = "New York",
                outfitItemCount = outfit.allItems.size,
                onToggleUnit = {}
            )
        }
    }
}

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
 *
 * One test class per shot (below), and wear/build.gradle.kts forks the
 * test JVM per class: inside one shared fork the shots leak window /
 * focus state into each other and the ScalingLazyColumn pages degrade
 * to fully transparent captures from the second activity on (bisected
 * 2026-10-29 — every shot renders solo). Process isolation is the
 * harness's contract, not a workaround for the app: on-device the
 * pages render in sequence all day.
 */
class ShotApplication : Application()

abstract class WearShotHarness {

    protected val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("wearShotsDir") ?: "build/wear-screenshots")

    protected fun weather(
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

    protected val mildWeather = weather(60.0, 58.0, WeatherCode.CLEAR_SKY, 45, 6.0, 5.5, 10)
    protected val coldWeather = weather(30.0, 24.0, WeatherCode.OVERCAST, 55, 14.0, 0.5, 90)
    protected val hotWeather = weather(91.0, 96.0, WeatherCode.CLEAR_SKY, 62, 5.0, 9.0, 5)

    protected fun shoot(
        name: String,
        settleSteps: Int = 60,
        sizePx: Int = 456,
        requireOpaqueShare: Float = 0.25f,
        content: @Composable () -> Unit
    ) {
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

        // Place focus before capturing: the outfit/tips pages carry a
        // focus chain on their ScalingLazyColumn (focusRequester +
        // rotaryScrollable + focusable), and under Robolectric a
        // focusable ScalingLazyColumn draws nothing until focus lands in
        // the tree (bisected 2026-10-29: a bare ScalingLazyColumn renders
        // at 39,601 opaque px and rotaryScrollable alone renders, but
        // adding .focusable() blanks it). On-device a real window places
        // focus through normal traversal; here, dispatch window focus and
        // request focus on the host view, then drain the traversal.
        composeView.dispatchWindowFocusChanged(true)
        composeView.requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        val size = sizePx
        fun capture(): Pair<Bitmap, Int> {
            val spec = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)
            composeView.measure(spec, spec)
            composeView.layout(0, 0, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(bmp))
            val px = IntArray(size * size)
            bmp.getPixels(px, 0, size, 0, 0, size, size)
            var op = 0
            for (p in px) if (p ushr 24 == 0xFF) op++
            return bmp to op
        }

        var (bitmap, opaque) = capture()
        val firstPassOpaque = opaque
        if (opaque <= size * size * requireOpaqueShare) {
            // Slow hosts can lag ScalingLazyColumn's first layout behind
            // even the long settle above. One more settle + capture round
            // before concluding nothing rendered.
            repeat(50) {
                scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
                shadowOf(Looper.getMainLooper()).idle()
            }
            shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            val second = capture()
            bitmap = second.first
            opaque = second.second
        }

        assertEquals("capture must be square", bitmap.width, bitmap.height)
        assertTrue(
            "capture side ${bitmap.width}px outside Play's 384..3840 band",
            bitmap.width in 384..3840
        )
        // Something must actually be drawn: the hero page paints its full
        // frame, but the outfit/tips pages paint the round viewport only —
        // their square corners stay transparent by design. So instead of a
        // single corner pixel, require a solid share of opaque pixels.
        assertTrue(
            "only $opaque/${size * size} opaque pixels (first pass $firstPassOpaque) — nothing rendered?",
            opaque > size * size * requireOpaqueShare
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
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot01HeroMildTest : WearShotHarness() {
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
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot02OutfitMildTest : WearShotHarness() {
    @Test
    fun shot02_outfitMild() {
        val outfit = engine.execute(mildWeather, ComfortPreference.NEUTRAL)
        shoot("02-outfit-mild") {
            OutfitPage(outfit = outfit, onRefresh = {}, isVisible = false)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot03TipsHotTest : WearShotHarness() {
    @Test
    fun shot03_tipsHot() {
        // Hot weather on purpose: the mild scenario produces ZERO tips
        // (every generateTips rule correctly stays silent at 58°F, calm,
        // dry, UV 5.5), so a "tips" shot of it depicts the empty state —
        // and failed this harness's opaque-pixel check in the Oct 2026
        // Wear release session for exactly that reason (465 px = the
        // title alone; the release shipped without a tips screenshot).
        // The hot scenario yields three real tips (heat, hydration, UV).
        val outfit = engine.execute(hotWeather, ComfortPreference.NEUTRAL)
        shoot("03-tips-hot", settleSteps = 150) {
            TipsPage(tips = outfit.tips, locationName = "New York", isVisible = false)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot04HeroColdTest : WearShotHarness() {
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
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot05OutfitColdTest : WearShotHarness() {
    @Test
    fun shot05_outfitCold() {
        val outfit = engine.execute(coldWeather, ComfortPreference.NEUTRAL)
        shoot("05-outfit-cold") {
            OutfitPage(outfit = outfit, onRefresh = {}, isVisible = false)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class Shot06HeroHotTest : WearShotHarness() {
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

// ============================================================================
// Field-verification renders (Oct 30, 2026 polish pass) — NOT listing shots.
// Rendered at 454 × 454 (Pixel Watch-class round frame) from the same
// production composables, to prove two on-watch field defects fixed:
// the empty-tips sentence clipped at the round face's edges, and the hero
// page's new subtle digital clock. The tips page paints glyphs only (no
// full-frame background), so its opaque-share floor is 1% — still above
// the 465 px "title alone" failure this harness caught in Oct 2026.
// ============================================================================

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w227dp-h227dp-xhdpi")
class VerifyTipsEmptyRoundTest : WearShotHarness() {
    @Test
    fun verify_tipsEmptyRound() {
        shoot("verify-tips-empty-454", sizePx = 454, requireOpaqueShare = 0.01f) {
            TipsPage(tips = emptyList(), locationName = "New York, New York", isVisible = false)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w227dp-h227dp-xhdpi")
class VerifyHeroClockRoundTest : WearShotHarness() {
    @Test
    fun verify_heroClockRound() {
        val outfit = engine.execute(mildWeather, ComfortPreference.NEUTRAL)
        shoot("verify-hero-clock-454", sizePx = 454) {
            HeroWeatherPage(
                weather = mildWeather,
                heroImageUrl = null,
                locationName = "New York",
                outfitItemCount = outfit.allItems.size,
                onToggleUnit = {}
            )
        }
    }
}

package com.runwear.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import com.runwear.wear.presentation.screens.HeroWearMainScreen
import com.runwear.wear.presentation.theme.RunWearWatchTheme
import com.runwear.wear.presentation.viewmodel.WearUiState
import java.io.File
import kotlinx.datetime.toKotlinLocalDateTime
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Addendum 10 (Wear Play compliance) evidence harness.
 *
 * Google rejected Wear production vc10015 on WATCH SHAPES (content
 * must fit the physical display; nothing cut off by the screen
 * edges) and LARGE FONT ("Texts are cut off when a large font size
 * is selected"). This harness renders the PRODUCTION wear surfaces
 * (via the public [HeroWearMainScreen] state machine and the real
 * recommendation engine) on the SMALLEST ROUND FACE in the matrix
 * — 384 x 384 px (192dp at xhdpi, the small-round Wear OS class) —
 * at the default font scale AND at fontScale 1.30, the largest
 * Wear OS font-size setting, applied through LocalDensity exactly
 * as the system scales sp text.
 *
 * Captures are round-masked and flattened onto black (the physical
 * display shape) and land in build/wear-compliance/. The reviewer
 * screenshots from the Play Console were not reachable from this
 * environment; the surface set walked here is the whole Wear app:
 * hero, outfit, tips, loading, error and permission states.
 *
 * One shot per class: wear/build.gradle.kts forks per class (the
 * screenshot harness's contract — shared forks degrade captures).
 */
abstract class WearComplianceHarness {

    protected val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("wearComplianceDir") ?: "build/wear-compliance")

    protected fun weather(
        tempF: Double,
        feelsF: Double,
        code: WeatherCode
    ) = WeatherConditions(
        dateTime = LocalDateTime.now().toKotlinLocalDateTime(),
        temperature = tempF,
        feelsLike = feelsF,
        humidity = 55,
        windSpeed = 12.0,
        windGusts = 18.0,
        precipitationProbability = 20,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 40,
        uvIndex = 3.0,
        weatherCode = code,
    )

    protected val mildWeather = weather(60.0, 58.0, WeatherCode.CLEAR_SKY)
    protected val coldWeather = weather(30.0, 24.0, WeatherCode.OVERCAST)

    /**
     * @param sizePx square canvas side in px (384 = smallest round).
     * @param fontScale LocalDensity font scale (1.30 = largest Wear
     * font-size setting).
     */
    protected fun shootCompliance(
        name: String,
        sizePx: Int,
        fontScale: Float,
        settleSteps: Int = 80,
        content: @Composable () -> Unit
    ) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearWatchTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale)
                ) { content() }
            }
        }

        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(settleSteps) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        composeView.dispatchWindowFocusChanged(true)
        composeView.requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        val spec = View.MeasureSpec.makeMeasureSpec(sizePx, View.MeasureSpec.EXACTLY)
        composeView.measure(spec, spec)
        composeView.layout(0, 0, sizePx, sizePx)
        val raw = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(raw))

        // Something must be drawn inside the round area.
        val px = IntArray(sizePx * sizePx)
        raw.getPixels(px, 0, sizePx, 0, 0, sizePx, sizePx)
        val cx = sizePx / 2
        val cy = sizePx / 2
        var opaqueInCircle = 0
        var r2 = (sizePx / 2) * (sizePx / 2)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy <= r2 && (px[y * sizePx + x] ushr 24) == 0xFF) {
                    opaqueInCircle++
                }
            }
        }
        assertTrue(
            "only $opaqueInCircle opaque px inside the round face — nothing rendered?",
            opaqueInCircle > sizePx * sizePx / 5
        )

        // Round-mask + flatten onto black: what the physical round
        // display actually shows.
        val masked = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(masked)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val circle = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val cc = Canvas(circle)
        cc.drawColor(Color.WHITE)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        val clipped = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val clipCanvas = Canvas(clipped)
        clipCanvas.drawBitmap(raw, 0f, 0f, null)
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val tmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val tmpCanvas = Canvas(tmp)
        tmpCanvas.drawBitmap(raw, 0f, 0f, null)
        tmpCanvas.drawCircle(cx.toFloat(), cy.toFloat(), sizePx / 2f, maskPaint.apply {
            color = Color.WHITE
        })
        canvas.drawBitmap(tmp, 0f, 0f, paint)

        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            masked.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        controller.pause().stop().destroy()
    }

    protected fun stateFor(
        weather: WeatherConditions,
        locationName: String
    ): WearUiState {
        val outfit = engine.execute(weather, ComfortPreference.NEUTRAL)
        return WearUiState(
            isLoading = false,
            error = null,
            outfit = outfit,
            weather = weather,
            heroImageUrl = null,
            locationName = locationName,
            hasLocationPermission = true
        )
    }
}

// ── Smallest round face, default font scale (WATCH SHAPES) ──────

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceSmallHeroTest : WearComplianceHarness() {
    @Test fun small_hero() {
        shootCompliance("small-round_font1.0_hero", 384, 1.0f) {
            HeroWearMainScreen(
                uiState = stateFor(mildWeather, "Overland Park, Kansas"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceSmallErrorTest : WearComplianceHarness() {
    @Test fun small_error() {
        val state = WearUiState(
            isLoading = false,
            error = "Could not load weather for your location. Check the connection on your phone and try again.",
            hasLocationPermission = true
        )
        shootCompliance("small-round_font1.0_error", 384, 1.0f) {
            HeroWearMainScreen(
                uiState = state,
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceSmallPermissionTest : WearComplianceHarness() {
    @Test fun small_permission() {
        val state = WearUiState(isLoading = false, hasLocationPermission = false)
        shootCompliance("small-round_font1.0_permission", 384, 1.0f) {
            HeroWearMainScreen(
                uiState = state,
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

// ── Smallest round face, LARGEST font scale 1.30 (LARGE FONT) ───

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontHeroTest : WearComplianceHarness() {
    @Test fun maxfont_hero() {
        shootCompliance("small-round_font1.3_hero", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = stateFor(mildWeather, "Overland Park, Kansas"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontHeroColdTest : WearComplianceHarness() {
    @Test fun maxfont_hero_cold() {
        shootCompliance("small-round_font1.3_hero-cold", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = stateFor(coldWeather, "Kansas City, Missouri"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontErrorTest : WearComplianceHarness() {
    @Test fun maxfont_error() {
        val state = WearUiState(
            isLoading = false,
            error = "Could not load weather for your location. Check the connection on your phone and try again.",
            hasLocationPermission = true
        )
        shootCompliance("small-round_font1.3_error", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = state,
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontPermissionTest : WearComplianceHarness() {
    @Test fun maxfont_permission() {
        val state = WearUiState(isLoading = false, hasLocationPermission = false)
        shootCompliance("small-round_font1.3_permission", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = state,
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontLoadingTest : WearComplianceHarness() {
    @Test fun maxfont_loading() {
        val state = WearUiState(isLoading = true, hasLocationPermission = true)
        shootCompliance("small-round_font1.3_loading", 384, 1.30f) {
            HeroWearMainScreen(
                uiState = state,
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

// Outfit + Tips pages sit behind the pager (pages 2 and 3); render
// them directly, as the listing screenshot harness does.

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceSmallOutfitTest : WearComplianceHarness() {
    @Test fun small_outfit() {
        val outfit = engine.execute(coldWeather, ComfortPreference.NEUTRAL)
        shootCompliance("small-round_font1.0_outfit", 384, 1.0f, settleSteps = 150) {
            com.runwear.wear.presentation.screens.OutfitPage(
                outfit = outfit, onRefresh = {}, isVisible = false
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontOutfitTest : WearComplianceHarness() {
    @Test fun maxfont_outfit() {
        val outfit = engine.execute(coldWeather, ComfortPreference.NEUTRAL)
        shootCompliance("small-round_font1.3_outfit", 384, 1.30f, settleSteps = 150) {
            com.runwear.wear.presentation.screens.OutfitPage(
                outfit = outfit, onRefresh = {}, isVisible = false
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceSmallTipsTest : WearComplianceHarness() {
    @Test fun small_tips() {
        val outfit = engine.execute(
            weather(91.0, 96.0, WeatherCode.CLEAR_SKY), ComfortPreference.NEUTRAL
        )
        shootCompliance("small-round_font1.0_tips", 384, 1.0f, settleSteps = 150) {
            com.runwear.wear.presentation.screens.TipsPage(
                tips = outfit.tips, locationName = "Overland Park, Kansas", isVisible = false
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w192dp-h192dp-xhdpi")
class ComplianceMaxFontTipsTest : WearComplianceHarness() {
    @Test fun maxfont_tips() {
        val outfit = engine.execute(
            weather(91.0, 96.0, WeatherCode.CLEAR_SKY), ComfortPreference.NEUTRAL
        )
        shootCompliance("small-round_font1.3_tips", 384, 1.30f, settleSteps = 150) {
            com.runwear.wear.presentation.screens.TipsPage(
                tips = outfit.tips, locationName = "Overland Park, Kansas", isVisible = false
            )
        }
    }
}

// ── Large round face (456px), largest font scale: the hero card
//    and the advice card at the other end of the size matrix ────

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class ComplianceLargeMaxFontHeroTest : WearComplianceHarness() {
    @Test fun large_maxfont_hero() {
        shootCompliance("large-round_font1.3_hero", 456, 1.30f) {
            HeroWearMainScreen(
                uiState = stateFor(mildWeather, "Overland Park, Kansas"),
                onRefresh = {}, onToggleUnit = {}, onNextDay = {},
                onPreviousDay = {}, onResetToNow = {}, onRequestPermission = {}
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ShotApplication::class, sdk = [35], qualifiers = "w228dp-h228dp-xhdpi")
class ComplianceLargeMaxFontTipsTest : WearComplianceHarness() {
    @Test fun large_maxfont_tips() {
        val outfit = engine.execute(
            weather(91.0, 96.0, WeatherCode.CLEAR_SKY), ComfortPreference.NEUTRAL
        )
        shootCompliance("large-round_font1.3_tips", 456, 1.30f, settleSteps = 150) {
            com.runwear.wear.presentation.screens.TipsPage(
                tips = outfit.tips, locationName = "Overland Park, Kansas", isVisible = false
            )
        }
    }
}

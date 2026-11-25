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
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-144 (Nov 8, 2026) — shared harness for the rendered
 * walkthrough of the approved first-screen redesign (v8
 * frames). Each frame lives in its own subclass (fresh
 * Robolectric sandbox per class — see settleReveal's note).
 * Frames land in build/render-out-rw144/ and are staged by the
 * lane into ~/workspace/release-prep/rw144-android-2026-11-08/frames/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
abstract class Rw144RenderTestBase {

    protected val outDir: File = File("build/render-out-rw144")
    protected val engine = GetOutfitRecommendationUseCase()
    protected val today: LocalDate = LocalDate.of(2026, 11, 8)
    protected val now: LocalDateTime = LocalDateTime.of(2026, 11, 8, 7, 0)

    protected class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>,
        val activity: ComponentActivity
    )

    protected fun phoenixWeather(feels: Double = 58.0, actual: Double = 60.0) = WeatherConditions(
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

    protected fun hourly(date: LocalDate): List<HourlyForecast> =
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

    protected fun uiState(
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

    protected fun mimState(making: Boolean = false, stock: Boolean = false) = MakeItMeUiState(
        mode = if (stock) MakeItMeMode.STOCK else MakeItMeMode.FREE,
        freeGenerationsUsed = 6
    )

    @Composable
    protected fun Home(
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

    protected fun compose(
        dark: Boolean,
        pumpSettle: Boolean = false,
        content: @Composable () -> Unit
    ): Host {
        RunWearColors.setActivePalette(if (dark) DarkPalette else LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = dark) { content() } }
        val host = Host(composeView, controller, activity)
        // One real traversal before the settle pump, so the
        // composition and its first layout exist before any
        // looper tasks run (mirrors a window's first frame).
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, w, h)
        val prime = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(prime))
        prime.recycle()
        if (pumpSettle) settlePump(host) else settleReveal(host)
        return host
    }

    /**
     * Reveal settle: advance the paused scheduler in small
     * steps (frame timestamps must progress for the reveal
     * gate's recomposition to run), idle the looper after each
     * step, and draw between steps. Safe for compositions with
     * no infinite transition.
     *
     * Harness history (RW-144 probe series, Nov 8, 2026): the
     * reveal this drives — HeroMainScreen's LaunchedEffect
     * flipping contentVisible into an AnimatedVisibility — only
     * ever composes for the FIRST full-screen composition in a
     * Robolectric sandbox; in later test methods of the same
     * class the gated unit never composes (the repo's older
     * full-screen suites never asserted gated content, so the
     * limitation went unnoticed). Each walkthrough frame
     * therefore lives in its OWN test class (fresh sandbox),
     * where this settle reveals exactly as it does on device.
     * Separately, idle() must never run against an infinite
     * transition — see settlePump.
     */
    protected fun settleReveal(host: Host) {
        val scheduler = Robolectric.getForegroundThreadScheduler()
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    /**
     * Pump settle for compositions containing an infinite
     * transition (the generating pill's CircularProgressIndicator):
     * idle()/advanceBy() run "until quiet", and the spinner's
     * frame-clock trampoline self-posts at a frozen timestamp —
     * the queue never quiets (measured 282-449 wall-seconds for
     * a single settle, thread-dumped to InfiniteTransition
     * withFrameNanos under Robolectric's paused scheduler). A
     * BOUNDED runOneTask pump drives the composition without
     * chasing the animation. On device, vsync paces these
     * frames in real time; the storm is a harness artefact,
     * not production behaviour.
     */
    protected fun settlePump(host: Host) {
        val looper = shadowOf(Looper.getMainLooper())
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        repeat(40) {
            repeat(25) { looper.runOneTask() }
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    protected fun capture(host: Host, name: String): Bitmap {
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

    protected fun captureCrop(host: Host, name: String, topPx: Int, bottomPx: Int) {
        val full = capture(host, "tmp-full.png")
        val crop = Bitmap.createBitmap(full, 0, topPx, full.width, bottomPx - topPx)
        File(outDir, name).outputStream().use {
            crop.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        crop.recycle()
        full.recycle()
    }

    protected fun rootNode(view: View): SemanticsNode {
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

    protected data class TextBox(val text: String, val top: Float, val bottom: Float)

    protected fun texts(host: Host): List<TextBox> {
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

    /**
     * Seed the Wear I've Run store with the entry the screen's own
     * record effect will write for [state] — same day, place, kit.
     * The holder primes Present from it (no cue), and the screen's
     * re-record with the same kit updates silently (the record
     * effect's own rule), so the composition stays at rest: no
     * cue, no glide. This is the production first-open state for a
     * returning user whose today is already recorded.
     */
    protected fun seedExactEntry(state: MainUiState) {
        val outfit = state.outfit ?: return
        val weather = state.weather ?: return
        val entry = WearIveRunDayEntry(
            date = state.selectedDateTime.toLocalDate(),
            place = state.simplePlace?.display ?: "Phoenix, AZ",
            feelsLikeF = weather.feelsLike + state.comfortPreference.tempAdjustment,
            conditionLabel = weather.weatherCode.description,
            isSnowing = weather.isSnowing,
            isRaining = weather.isRaining,
            items = outfit.allItems.map { it.displayName },
            heroRef = state.effectiveHeroUrl
        )
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        runBlocking { WearIveRunStore(app).recordSettled(entry) }
    }
}

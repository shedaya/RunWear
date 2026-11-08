package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.ui.components.HeroPill
import com.runwear.app.ui.components.HeroPillContent
import com.runwear.app.ui.components.HeroPillPolicy
import com.runwear.app.ui.components.HeroFeelsLikeBlock
import com.runwear.app.ui.components.HeroSheetBlock
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 VQ harness (Android): renders PRODUCTION composables for the
 * visual-QA fixtures the pre-existing render tests do not cover —
 * the hero sheet at the numeral width extremes (feels-like 108 and
 * -4, TEST-PLAN F-HOT-108 / F-COLD-NEG) and with a long place name
 * (F-LONGPLACE), each at font scale 1.0 and 1.3 (the plan's `max`),
 * one full-home attempt at the hot fixture, plus the hero pill
 * states in the DARK theme (VQ-1.10; the existing HeroPillRenderTest
 * renders light only). The hero sheet is rendered through the
 * production [HeroSheetBlock] — the same composable the
 * PickerWatchClosurePhoneTest harness renders — and settling uses
 * that harness's pump loop (measure/layout/draw inside the loop;
 * a settle without in-loop draws captures a blank frame for these
 * surfaces, found in VQ run 232 and fixed here). Font scale is
 * applied through LocalDensity exactly as the system scales sp
 * text (the WearComplianceTest method). PNGs + semantics text
 * dumps land in build/render-out-gen6vq/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Gen6VqRenderTest {

    private val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("gen6VqShotsDir") ?: "build/render-out-gen6vq")

    private fun weather(tempF: Double, feelsF: Double) = WeatherConditions(
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
        weatherCode = WeatherCode.CLEAR_SKY,
    )

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (found == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private fun dumpTexts(view: ComposeView, name: String) {
        val lines = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val b = node.boundsInRoot
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    lines += "TEXT [${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}] ${t.text}"
                }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                for (t in node.config[SemanticsProperties.ContentDescription]) {
                    lines += "DESC [${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}] $t"
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(mergedRoot(view))
        outDir.mkdirs()
        File(outDir, "$name.txt").writeText(lines.joinToString("\n"))
    }

    private fun hostAndShoot(
        name: String,
        fontScale: Float,
        dark: Boolean = false,
        content: @Composable () -> Unit
    ) {
        RunWearColors.setActivePalette(
            if (dark) com.runwear.app.ui.theme.DarkPalette
            else com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = dark) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale)
                ) { content() }
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
            composeView.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        dumpTexts(composeView, name)
        controller.pause().stop().destroy()
    }

    private fun sheet(name: String, w: WeatherConditions, place: String, fontScale: Float) {
        val now = LocalDateTime.of(2026, 10, 30, 7, 35)
        hostAndShoot(name, fontScale) {
            androidx.compose.foundation.layout.Column {
                // RW-144: the first-screen pair — feels-like block (hero) + weather line (sheet).
                com.runwear.app.ui.components.HeroFeelsLikeBlock(
                    weather = w,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    comfortPreference = ComfortPreference.NEUTRAL
                )
                HeroSheetBlock(
                    weather = w,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT
                )
            }
        }
    }

    @Test fun heroHot108Default() =
        sheet("hero-F-HOT108-default", weather(104.0, 108.0), "Miami, Florida", 1.0f)

    @Test fun heroHot108Max() =
        sheet("hero-F-HOT108-max", weather(104.0, 108.0), "Miami, Florida", 1.3f)

    @Test fun heroColdNegDefault() =
        sheet("hero-F-COLDNEG-default", weather(2.0, -4.0), "Aspen, Colorado", 1.0f)

    @Test fun heroColdNegMax() =
        sheet("hero-F-COLDNEG-max", weather(2.0, -4.0), "Aspen, Colorado", 1.3f)

    @Test fun heroLongPlaceMax() =
        sheet("hero-F-LONGPLACE-max", weather(60.0, 58.0), "Truth or Consequences, New Mexico", 1.3f)

    @Test fun heroLongPlaceDefault() =
        sheet("hero-F-LONGPLACE-default", weather(60.0, 58.0), "Truth or Consequences, New Mexico", 1.0f)

    private fun sheetChanged(name: String, w: WeatherConditions, place: String, fontScale: Float, dark: Boolean = false) {
        // Changed state: selection is tomorrow 19:00, location-now is today 07:35.
        val selection = LocalDateTime.of(2026, 10, 31, 19, 0)
        val now = LocalDateTime.of(2026, 10, 30, 7, 35)
        hostAndShoot(name, fontScale, dark = dark) {
            androidx.compose.foundation.layout.Column {
                // RW-144: the first-screen pair — feels-like block (hero) + weather line (sheet).
                com.runwear.app.ui.components.HeroFeelsLikeBlock(
                    weather = w,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    comfortPreference = ComfortPreference.NEUTRAL
                )
                HeroSheetBlock(
                    weather = w,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT
                )
            }
        }
    }

    @Test fun heroLongPlaceChangedMax() =
        sheetChanged("hero-F-LONGPLACE-changed-max", weather(60.0, 58.0), "Truth or Consequences, New Mexico", 1.3f)

    @Test fun heroLongPlaceChangedDefault() =
        sheetChanged("hero-F-LONGPLACE-changed-default", weather(60.0, 58.0), "Truth or Consequences, New Mexico", 1.0f)

    @Test fun heroHot108DarkDefault() {
        val now = LocalDateTime.of(2026, 10, 30, 7, 35)
        hostAndShoot("hero-F-HOT108-dark-default", 1.0f, dark = true) {
            androidx.compose.foundation.layout.Column {
                com.runwear.app.ui.components.HeroFeelsLikeBlock(
                    weather = weather(104.0, 108.0),
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    comfortPreference = ComfortPreference.NEUTRAL
                )
                HeroSheetBlock(
                    weather = weather(104.0, 108.0),
                    temperatureUnit = TemperatureUnit.FAHRENHEIT
                )
            }
        }
    }

    @Test fun homeFullHot108Default() {
        val w = weather(104.0, 108.0)
        val outfit = engine.execute(w, ComfortPreference.NEUTRAL)
        val now = LocalDateTime.of(2026, 10, 30, 7, 35)
        val uiState = MainUiState(
            isLoading = false,
            error = null,
            outfit = outfit,
            weather = w,
            locationName = "Miami, Florida",
            selectedDateTime = now,
            locationNow = now,
            isNowSelected = true,
            heroImageUrl = null,
            hasCompletedOnboarding = true
        )
        hostAndShoot("home-full-F-HOT108-default", 1.0f) {
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

    private fun pill(name: String, content: HeroPillContent, dark: Boolean) {
        hostAndShoot(name, 1.0f, dark = dark) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF33413A))
                    .padding(16.dp)
            ) {
                HeroPill(content = content, onAction = {})
            }
        }
    }

    private fun resolve(
        lookStatus: PersonalLookStatus = PersonalLookStatus.Ready,
        serveKind: PersonalServeKind = PersonalServeKind.EXACT,
        heroIsPersonal: Boolean = true,
        mode: MakeItMeMode = MakeItMeMode.FREE,
        used: Int = 0
    ): HeroPillContent = HeroPillPolicy.resolve(
        lookStatus = lookStatus,
        serveKind = serveKind,
        heroIsPersonal = heroIsPersonal,
        mode = mode,
        freeGenerationsUsed = used
    )!!

    @Test fun pillDarkMakingFree() = pill(
        "pill-dark-making-free",
        resolve(lookStatus = PersonalLookStatus.Making, heroIsPersonal = false),
        dark = true
    )

    @Test fun pillDarkExactFree() = pill(
        "pill-dark-exact-free-3-of-5",
        resolve(used = 3),
        dark = true
    )

    @Test fun pillDarkExhausted() = pill(
        "pill-dark-exhausted",
        resolve(used = 5),
        dark = true
    )

    @Test fun pillDarkPremium() = pill(
        "pill-dark-premium",
        resolve(mode = MakeItMeMode.SUBSCRIBED),
        dark = true
    )
}

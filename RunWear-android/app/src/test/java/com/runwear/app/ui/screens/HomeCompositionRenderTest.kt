package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.components.HeroSection
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
 * WS-1 (RC-1) rendered evidence: the production home composition,
 * hosted exactly like the fluid-shot harness (Robolectric native
 * graphics, real engine outfit), asserted at the SEMANTICS level —
 * the composed picture, not the source:
 *
 *  - at rest, the location pill (A3) is present in the photo zone's
 *    top area, showing the SimplePlace form ("Aspen, CO", never the
 *    raw "Aspen, Colorado" the state carries), and the Settings
 *    control is present and enabled;
 *  - the sheet's "FEELS LIKE" label sits at the handoff — its top
 *    is within a few dp of heroHeight − 28dp, NOT at the top of the
 *    screen (D-01's regression puts it at y≈0, clipped under the
 *    status bar, D-03);
 *  - the same assertions hold at the unfolded-Fold width and in
 *    dark theme;
 *  - the photo-zone chrome at the END of the handoff travel is
 *    non-interactive (HeroSection rendered directly at full
 *    scroll offset): the Settings control carries the disabled
 *    semantics property, while at rest it does not.
 *
 * PNGs land in build/render-out-ws1/ (CI evidence is the passing
 * assertions, per the harness limitation WS-2/WS-8 recorded).
 */
abstract class HomeCompositionHarness {

    protected val engine = GetOutfitRecommendationUseCase()

    private val outDir: File =
        File(System.getProperty("ws1ShotsDir") ?: "build/render-out-ws1")

    protected fun weather() = WeatherConditions(
        dateTime = KotlinLocalDateTime(2026, 10, 30, 7, 35),
        temperature = 42.0,
        feelsLike = 33.0,
        humidity = 31,
        windSpeed = 5.0,
        windGusts = 7.0,
        precipitationProbability = 1,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.CLEAR_SKY
    )

    protected fun settle(composeView: ComposeView) {
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
    }

    protected fun drawAndSave(
        activity: ComponentActivity,
        composeView: ComposeView,
        name: String,
        widthDp: Int,
        heightDp: Int
    ) {
        val density = activity.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()
        val wSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** All semantics nodes in the tree, flattened. */
    protected fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    protected fun texts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        if (node.config.contains(SemanticsProperties.Text)) {
            out += node.config[SemanticsProperties.Text].map { it.text }
        }
        if (node.config.contains(SemanticsProperties.ContentDescription)) {
            out += node.config[SemanticsProperties.ContentDescription]
        }
        return out
    }

    protected fun findByText(root: SemanticsNode, text: String): SemanticsNode? =
        nodes(root).firstOrNull { texts(it).any { t -> t == text } }

    protected fun findByContentDescription(root: SemanticsNode, desc: String): SemanticsNode? =
        nodes(root).firstOrNull {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription].any { d -> d == desc }
        }

    protected fun assertHomeComposition(
        composeView: ComposeView,
        density: Float,
        screenHeightDp: Int,
        label: String
    ) {
        val root = (composeView as androidx.compose.ui.platform.ViewRootForTest).semanticsOwner.rootSemanticsNode

        // A3: the location pill is on screen at rest, in the photo
        // zone's top area, in the SimplePlace form.
        val pill = findByText(root, "Aspen, CO")
        assertTrue("[$label] location pill 'Aspen, CO' must be present at rest", pill != null)
        val rawPill = findByText(root, "Aspen, Colorado")
        assertTrue(
            "[$label] the pill must not carry the raw geocode string as its own node text",
            rawPill == null || rawPill == pill
        )
        val pillTopDp = pill!!.boundsInRoot.top / density
        assertTrue(
            "[$label] pill must sit in the photo zone's top area (top=${pillTopDp}dp)",
            pillTopDp < 120f
        )

        // D-02: Settings is present and enabled at rest.
        val settings = findByContentDescription(root, "Settings")
        assertTrue("[$label] Settings control must be present at rest", settings != null)
        assertFalse(
            "[$label] Settings must be interactive at rest",
            settings!!.config.contains(SemanticsProperties.Disabled)
        )

        // D-01/D-03: the sheet's label sits at the handoff —
        // heroHeight − 28dp (plus the sheet's own 20dp top padding),
        // never at the top of the screen.
        val heroHeightDp = screenHeightDp * 0.75f
        val feels = findByText(root, "FEELS LIKE")
        assertTrue("[$label] FEELS LIKE label must be present", feels != null)
        val feelsTopDp = feels!!.boundsInRoot.top / density
        val expected = heroHeightDp - 28f + 20f
        assertTrue(
            "[$label] sheet label top must be at the handoff ≈${expected}dp " +
                "(was ${feelsTopDp}dp; D-01 puts it near 0)",
            feelsTopDp > expected - 24f && feelsTopDp < expected + 24f
        )
    }

    protected fun renderHome(
        name: String,
        widthDp: Int,
        heightDp: Int,
        dark: Boolean
    ) {
        val w = weather()
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
            RunWearTheme(darkTheme = dark) {
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
        settle(composeView)
        drawAndSave(activity, composeView, name, widthDp, heightDp)
        val density = activity.resources.displayMetrics.density
        assertHomeComposition(composeView, density, heightDp, name)
        controller.pause().stop().destroy()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class HomeCompositionPhoneLightTest : HomeCompositionHarness() {
    @Test fun phoneLightAtRest() = renderHome("ws1-phone-light-rest", 390, 844, dark = false)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class HomeCompositionPhoneDarkTest : HomeCompositionHarness() {
    @Test fun phoneDarkAtRest() = renderHome("ws1-phone-dark-rest", 390, 844, dark = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w690dp-h829dp-xhdpi")
class HomeCompositionFoldLightTest : HomeCompositionHarness() {
    @Test fun foldLightAtRest() = renderHome("ws1-fold-light-rest", 690, 829, dark = false)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w690dp-h829dp-xhdpi")
class HomeCompositionFoldDarkTest : HomeCompositionHarness() {
    @Test fun foldDarkAtRest() = renderHome("ws1-fold-dark-rest", 690, 829, dark = true)
}

/**
 * The chrome at the handoff's extremes, rendered directly against
 * HeroSection: at rest the Settings control is interactive; at full
 * travel (scroll offset beyond the handoff range) it is disabled —
 * the fade's end state, exercised in the composed surface.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class HomeChromeHandoffTest : HomeCompositionHarness() {

    private fun renderHero(scrollOffset: Float, name: String): SemanticsNode {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                HeroSection(
                    weather = weather(),
                    outfit = null,
                    locationName = "New York, New York",
                    heroImageUrl = null,
                    scrollOffset = scrollOffset,
                    onLocationClick = {},
                    onSettingsClick = {},
                    onShareClick = {}
                )
            }
        }
        settle(composeView)
        drawAndSave(activity, composeView, name, 390, 844)
        // The semantics tree is read by the caller after this
        // returns, so the activity is deliberately not destroyed
        // here (the harness process is per-test-class).
        return (composeView as androidx.compose.ui.platform.ViewRootForTest).semanticsOwner.rootSemanticsNode
    }

    @Test
    fun chromeInteractiveAtRestAndDisabledAtEndOfHandoff() {
        val rest = renderHero(0f, "ws1-hero-rest")
        val restSettings = findByContentDescription(rest, "Settings")
        assertTrue("Settings must exist at rest", restSettings != null)
        assertFalse(
            "Settings must be interactive at rest",
            restSettings!!.config.contains(SemanticsProperties.Disabled)
        )
        // The pill shows the simple form of the raw geocode string.
        assertTrue(
            "pill must show 'New York, NY'",
            findByText(rest, "New York, NY") != null
        )

        val end = renderHero(100000f, "ws1-hero-handoff-end")
        val endSettings = findByContentDescription(end, "Settings")
        assertTrue("Settings node must still exist at handoff end", endSettings != null)
        assertTrue(
            "Settings must be NON-interactive at the end of the chrome fade",
            endSettings!!.config.contains(SemanticsProperties.Disabled)
        )
    }
}

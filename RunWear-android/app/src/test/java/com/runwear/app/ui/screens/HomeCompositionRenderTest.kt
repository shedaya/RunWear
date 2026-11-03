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
import androidx.compose.ui.graphics.toArgb
import com.runwear.app.ui.theme.RunWearColors
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
 * graphics, real engine outfit). Two assertion layers:
 *
 *  - PIXEL probes on the drawn full-screen frames (phone + Fold,
 *    light + dark): the top chrome band and the band just above
 *    the handoff are photo (gradient), the band just below the
 *    handoff is the sheet's flat Surface reaching the page edge —
 *    the sheet top IS heroHeight − 28dp, never at y≈0 over the
 *    chrome (D-01/D-02) and never a floating card. Lazy-item
 *    semantics are unreachable from this harness's semantics/a11y
 *    trees (the LazyColumn node exposes scroll properties only),
 *    so the full-screen composition is judged on its pixels.
 *  - SEMANTICS on HeroSection rendered directly: at rest the
 *    location pill (A3) is present showing the SimplePlace form
 *    ("New York, NY") and Settings is enabled; at the END of the
 *    handoff travel the Settings control carries the disabled
 *    semantics property — chrome fade completes and disables.
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
    ): Bitmap {
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
        return bitmap
    }

    /** The semantics root: ComposeView hosts its composition in a
     * child AndroidComposeView, which is the ViewRootForTest. The
     * MERGED tree collapses this screen to a handful of nodes, so
     * the UNMERGED tree (reflectively, with the merged tree as
     * fallback) is what gets read; every candidate across the whole
     * window hierarchy is collected and the LARGEST tree wins. */
    protected fun rootNode(view: View): SemanticsNode {
        val candidates = mutableListOf<SemanticsNode>()
        fun addTrees(owner: androidx.compose.ui.semantics.SemanticsOwner) {
            candidates += owner.rootSemanticsNode
            try {
                val m = owner.javaClass.getMethod("getUnmergedRootSemanticsNode")
                @Suppress("UNCHECKED_CAST")
                candidates += m.invoke(owner) as SemanticsNode
            } catch (e: Exception) {
                // merged tree already collected
            }
        }
        fun collect(v: View) {
            if (v is androidx.compose.ui.platform.ViewRootForTest) {
                addTrees(v.semanticsOwner)
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) collect(v.getChildAt(i))
            }
        }
        collect(view.rootView)
        return candidates.maxByOrNull { nodes(it).size }
            ?: throw IllegalStateException("no ViewRootForTest under $view")
    }

    /** The MERGED semantics root (clickable surfaces merge their
     * descendants — and their Disabled state — into one node). */
    protected fun mergedRootNode(view: View): SemanticsNode {
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
        return found ?: throw IllegalStateException("no ViewRootForTest under $view")
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
        bitmap: Bitmap,
        density: Float,
        screenHeightDp: Int,
        label: String
    ) {
        // The content branch composed (not skeleton / error): the
        // semantics tree carries the LazyColumn collection node.
        val root = rootNode(composeView)
        val hasCollection = nodes(root).any {
            it.config.contains(
                androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo
            )
        }
        assertTrue("[$label] home content branch must be composed", hasCollection)

        // Pixel probes on the actual drawn frame. The sheet is a
        // flat RunWearColors.Surface; the photo zone behind it is
        // the temperature gradient. (Lazy-item semantics are not
        // reachable from the semantics/a11y trees in this harness —
        // the LazyColumn node exposes scroll properties only — so
        // the composition is asserted on the rendered pixels, the
        // same evidence the fluid-shot harness judges.)
        val surface = RunWearColors.Surface.toArgb()
        fun px(xDp: Float, yDp: Float): Int =
            bitmap.getPixel(
                (xDp * density).toInt().coerceIn(0, bitmap.width - 1),
                (yDp * density).toInt().coerceIn(0, bitmap.height - 1)
            )
        fun channelDiff(a: Int, b: Int): Int {
            var d = 0
            for (shift in listOf(16, 8, 0)) {
                d = maxOf(d, kotlin.math.abs((a shr shift and 0xFF) - (b shr shift and 0xFF)))
            }
            return d
        }
        val centerX = bitmap.width / 2 / density
        val heroHeightDp = screenHeightDp * 0.75f
        val sheetTopDp = heroHeightDp - 28f

        // D-02/A3: the top chrome band shows the PHOTO, not the
        // sheet — D-01's mis-placement covers it with Surface.
        assertTrue(
            "[$label] top chrome band must be photo, not sheet surface " +
                "(px=${Integer.toHexString(px(centerX, 60f))} surface=${Integer.toHexString(surface)} " +
                "above=${Integer.toHexString(px(centerX, sheetTopDp - 40f))} " +
                "below=${Integer.toHexString(px(centerX, sheetTopDp + 30f))} " +
                "edge=${Integer.toHexString(px(3f, sheetTopDp + 40f))})",
            channelDiff(px(centerX, 60f), surface) > 8
        )
        // Just above the handoff: still photo.
        assertTrue(
            "[$label] just above the handoff must be photo",
            channelDiff(px(centerX, sheetTopDp - 40f), surface) > 8
        )
        // Just below the handoff: the sheet's flat surface — the
        // sheet top IS the handoff (heroHeight − 28dp), in every
        // scroll position, never floating and never at y≈0.
        assertTrue(
            "[$label] just below the handoff must be the sheet surface " +
                "(sheetTop≈${sheetTopDp}dp)",
            channelDiff(px(centerX, sheetTopDp + 30f), surface) <= 2
        )
        // Near the sheet's left edge below its rounded corner: the
        // same flat surface (full-bleed sheet, not a floating card
        // inset from the page edges).
        assertTrue(
            "[$label] sheet must reach the left page edge (no floating card)",
            channelDiff(px(3f, sheetTopDp + 40f), surface) <= 2
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
        val bitmap = drawAndSave(activity, composeView, name, widthDp, heightDp)
        val density = activity.resources.displayMetrics.density
        assertHomeComposition(composeView, bitmap, density, heightDp, name)
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
        // here (the harness process is per-test-class). The chrome
        // assertions read the MERGED tree: a clickable Surface
        // merges its icon's content description and its own
        // Disabled state into one node there.
        return mergedRootNode(composeView)
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

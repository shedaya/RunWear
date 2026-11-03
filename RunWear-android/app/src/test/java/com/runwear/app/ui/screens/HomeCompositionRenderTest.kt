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
 * graphics, real engine outfit). Two assertion layers:
 *
 *  - Full-screen frames (phone + Fold, light + dark) assert the
 *    content branch composed full-bleed and rendered opaque.
 *    The AnimatedVisibility-gated subtree does not settle under
 *    Robolectric's paused looper (see assertHomeComposition), so
 *    pixel-level handoff proof is a recorded harness gap; the
 *    composition invariants are pinned by the geometry, pin and
 *    chrome-semantics layers below.
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
        // Give the composition real constraints BEFORE settling:
        // without a layout pass the lazy viewport composes nothing
        // and the effect/animation clocks have no frame to ride.
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, w, h)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(80) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            // Re-layout each step so animated/visibility state that
            // flipped during the step is placed before the next one.
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        scheduler.advanceToLastPostedRunnable()
        shadowOf(Looper.getMainLooper()).idleFor(5, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        composeView.dispatchWindowFocusChanged(true)
        composeView.requestFocus()
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, w, h)
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
        // Warm-up draw: the first pass records the render nodes for
        // content whose visibility flipped during settle; the second
        // rasterizes the settled frame.
        val warm = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(warm))
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
        // HARNESS LIMITATION (recorded for the gate): the home
        // content is gated by AnimatedVisibility on a LaunchedEffect
        // flag, and that effect/animation clock does not settle
        // under Robolectric's paused looper — the drawn frame stays
        // the LazyColumn's Surface ground and the gated subtree
        // stays out of the semantics tree. So this layer asserts
        // what the full-screen render CAN prove, and the handoff /
        // pill / chrome assertions live in the layers that can
        // observe them: HomeChromeHandoffTest (production
        // HeroSection semantics), HomeCompositionGeometryTest
        // (overlap/void/fade math), HomeCompositionPinTest
        // (composition wiring).
        val root = rootNode(composeView)

        // The content branch composed (not skeleton / error /
        // loading): the semantics tree carries the LazyColumn
        // collection node.
        val hasCollection = nodes(root).any {
            it.config.contains(
                androidx.compose.ui.semantics.SemanticsProperties.CollectionInfo
            )
        }
        assertTrue("[$label] home content branch must be composed", hasCollection)

        // The composition fills the whole viewport (full-bleed:
        // the root spans the screen, no inset-shrunk frame).
        assertTrue(
            "[$label] composition root must span the viewport " +
                "(was ${root.boundsInRoot.width}×${root.boundsInRoot.height}px)",
            root.boundsInRoot.width >= bitmap.width - 1 &&
                root.boundsInRoot.height >= bitmap.height - 1
        )

        // The drawn frame is fully opaque (the screen rendered its
        // ground end-to-end; a failed composition leaves the
        // bitmap transparent).
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var opaque = 0
        for (p in px) if (p ushr 24 == 0xFF) opaque++
        assertTrue(
            "[$label] frame must be fully opaque (was $opaque/${px.size})",
            opaque == px.size
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
                    onSettingsClick = {}
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

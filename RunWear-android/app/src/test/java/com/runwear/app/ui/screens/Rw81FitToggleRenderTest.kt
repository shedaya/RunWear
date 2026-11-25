package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
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
 * RW-81 (Sol's ruling, Nov 6, 2026) rendered evidence: the fit
 * preference control on BOTH seats (onboarding's combined
 * preferences step and Settings) in all three states — Men's
 * selected, Women's selected, and neutral (nothing selected) —
 * at default font, light palette.
 *
 * Selection is asserted from PIXELS: the fill sampled just left
 * of each option's label must be the Primary token when that
 * option is selected and the control's track colour when it is
 * not. Both seats are composed with a stored preference and
 * rendered in all three states (Men's / Women's / neutral);
 * the RW-81 toggle behaviour behind them is pinned by the
 * unit/source suites (see the onboarding class doc for why
 * activation gestures are not driven in this harness). PNGs
 * land in build/render-out-rw81/.
 */
abstract class Rw81FitShotHarness {

    protected val outDir: File =
        File(System.getProperty("rw81ShotsDir") ?: "build/render-out-rw81")

    protected class Host(
        val activity: ComponentActivity,
        val composeView: ComposeView,
        val widthPx: Int,
        val heightPx: Int
    )

    protected fun host(content: @androidx.compose.runtime.Composable () -> Unit): Host {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) { content() }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        settle(composeView, w, h)
        return Host(activity, composeView, w, h)
    }

    protected fun settle(composeView: ComposeView, w: Int, h: Int) {
        // The proven pump loop (PickerWatchClosurePhoneTest — the
        // pattern the GEN-6 VQ harness fix 9ccea52 standardized
        // on): flush, advance, idle, measure, layout AND DRAW on
        // every iteration. The draw is load-bearing: pointer-input
        // processing and the AnimatedContent step transition only
        // advance on drawn frames, and a settle without draws can
        // leave a tapped step change unprocessed in a loaded test
        // JVM (CI run 42235360057: the Get Started tap never took
        // effect; the same tests passed in an idle local JVM).
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(15) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
            composeView.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected fun draw(host: Host): Bitmap {
        val bitmap = Bitmap.createBitmap(host.widthPx, host.heightPx, Bitmap.Config.ARGB_8888)
        host.composeView.draw(Canvas(bitmap))
        return bitmap
    }

    protected fun save(bitmap: Bitmap, name: String) {
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /** Crop the band around the fit control: from [topPx] for [heightPx]. */
    protected fun saveCrop(bitmap: Bitmap, name: String, topPx: Int, heightPx: Int) {
        val top = topPx.coerceIn(0, bitmap.height - 1)
        val height = heightPx.coerceAtMost(bitmap.height - top)
        val crop = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, height)
        save(crop, name)
    }

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                // The UNMERGED root: the proven tap-registration
                // pattern (LocationPickerTapRegistrationTest) — its
                // label nodes carry tight, dispatch-accurate bounds,
                // where merged nodes can describe a whole ancestor.
                found = v.semanticsOwner.unmergedRootSemanticsNode
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

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    protected fun textNode(host: Host, text: String): SemanticsNode {
        val all = nodes(mergedRoot(host.composeView))
        return all.firstOrNull { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text == text }
        } ?: throw AssertionError("text node not found: $text")
    }

    /**
     * The fill colour of the segment carrying [label]: the labels
     * are centred in their segments, so the point 55dp LEFT of the
     * label's centre sits inside the segment box, clear of the
     * label's own glyphs (segments are ~165dp wide, labels ~45dp).
     */
    protected fun segmentFill(host: Host, bitmap: Bitmap, label: String): Int {
        val bounds = textNode(host, label).boundsInRoot
        val density = host.activity.resources.displayMetrics.density
        val x = (bounds.center.x - 55 * density).toInt().coerceIn(0, bitmap.width - 1)
        val y = bounds.center.y.toInt().coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    /**
     * Assert the rendered fit state. The selected segment's fill
     * must be exactly the Primary token (an opaque paint). The
     * unselected fill is compared exactly when [trackArgb] is an
     * opaque token the control paints itself (onboarding's
     * Background track); for Settings the track token is
     * translucent over the sheet surface, so [trackArgb] is null
     * and the unselected fill is asserted only to differ from
     * Primary (the neutral test additionally asserts both
     * unselected fills are identical to each other).
     */
    protected fun assertFitState(
        host: Host,
        bitmap: Bitmap,
        selectedLabel: String?,
        trackArgb: Int?,
        context: String
    ) {
        val primary = LightPalette.Primary.toArgb()
        for (label in listOf("Men's", "Women's")) {
            val fill = segmentFill(host, bitmap, label)
            if (label == selectedLabel) {
                assertEquals(
                    "$context: '$label' must render SELECTED (Primary fill)",
                    primary, fill
                )
            } else if (trackArgb != null) {
                assertEquals(
                    "$context: '$label' must render UNSELECTED (track fill)",
                    trackArgb, fill
                )
            } else {
                assertTrue(
                    "$context: '$label' must render UNSELECTED (not Primary)",
                    fill != primary
                )
            }
        }
        if (selectedLabel == null && trackArgb == null) {
            assertEquals(
                "$context: in neutral both segments must carry the same track fill",
                segmentFill(host, bitmap, "Men's"),
                segmentFill(host, bitmap, "Women's")
            )
        }
        // Exactly two options exist: no third label anywhere.
        val texts = nodes(mergedRoot(host.composeView)).flatMap { node ->
            if (node.config.contains(SemanticsProperties.Text)) {
                node.config[SemanticsProperties.Text].map { it.text }
            } else {
                emptyList()
            }
        }
        assertTrue(
            "$context: a 'No preference' label must not exist",
            texts.none { it.contains("No preference") }
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h900dp-xhdpi")
class Rw81OnboardingFitRenderTest : Rw81FitShotHarness() {

    /**
     * The production [PreferencesStep] composed directly with a
     * stored preference passed in — the same seat the Settings
     * class renders, and the same call [OnboardingScreen] makes
     * with its own state. Activation gestures are deliberately
     * NOT driven in this harness: neither raw MotionEvent
     * dispatch nor semantics OnClick actions took effect inside
     * the CI Robolectric JVM across runs 42235360057,
     * 42241604548, 42245713753 and 42251061429 (the saved CI
     * renders show the state unchanged after activation was
     * invoked), while the identical harness fired locally. The
     * full screen's AnimatedContent step transition likewise
     * does not complete there. The RW-81 toggle behaviour
     * itself — tap selected -> UNISEX, the onSelect wiring, the
     * two-option list — is pinned behaviourally by
     * OnboardingCombinedScreenTest and Gen5FurtherAuditTest
     * (fitPreferenceAfterTap truth table + source pins of the
     * production wiring); this class's job is the RENDERED
     * states of the production step at default font.
     */
    private fun onboardingHost(gender: GenderPreference): Host = host {
        // The production screen paints the Background surface
        // behind the step; mirror it here (the fit control's own
        // fills are unaffected).
        androidx.compose.material3.Surface(color = LightPalette.Background) {
            PreferencesStep(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                genderPreference = gender,
                comfortPreference = ComfortPreference.NEUTRAL,
                onTemperatureUnitChange = {},
                onGenderPreferenceChange = {},
                onComfortPreferenceChange = {},
                onBack = {},
                onContinue = {}
            )
        }
    }

    private fun shootFit(host: Host, name: String): Bitmap {
        val bitmap = draw(host)
        save(bitmap, "$name-full")
        val density = host.activity.resources.displayMetrics.density
        val titleTop = textNode(host, "Fit preference").boundsInRoot.top
        saveCrop(bitmap, name, (titleTop - 10 * density).toInt(), (150 * density).toInt())
        return bitmap
    }

    @Test
    fun `fresh install starts neutral - nothing selected`() {
        val host = onboardingHost(GenderPreference.UNISEX)
        val bitmap = shootFit(host, "onboarding-neutral")
        // The onboarding segmented track paints the Background token.
        assertFitState(host, bitmap, null, LightPalette.Background.toArgb(), "onboarding neutral")
    }

    @Test
    fun `mens selected`() {
        val host = onboardingHost(GenderPreference.MALE)
        val bitmap = shootFit(host, "onboarding-mens")
        assertFitState(host, bitmap, "Men's", LightPalette.Background.toArgb(), "onboarding men's")
    }

    @Test
    fun `womens selected`() {
        val host = onboardingHost(GenderPreference.FEMALE)
        val bitmap = shootFit(host, "onboarding-womens")
        assertFitState(host, bitmap, "Women's", LightPalette.Background.toArgb(), "onboarding women's")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h2200dp-xhdpi")
class Rw81SettingsFitRenderTest : Rw81FitShotHarness() {

    private fun settingsHost(gender: GenderPreference): Host = host {
        // The production sheet container paints the surface the
        // content sits on; the harness composes the content
        // standalone, so mirror that container here (the fit
        // control's own fills are unaffected).
        androidx.compose.material3.Surface(color = LightPalette.Background) {
            SettingsSheetContent(
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                comfortPreference = ComfortPreference.NEUTRAL,
                genderPreference = gender,
                onToggleUnit = {},
                onSetComfortPreference = {},
                onSetGenderPreference = {},
                analyticsOptIn = false,
                onAnalyticsOptInChange = {},
                onSendFeedback = {}
            )
        }
    }

    private fun shootFit(host: Host, name: String): Bitmap {
        val bitmap = draw(host)
        save(bitmap, "$name-full")
        val density = host.activity.resources.displayMetrics.density
        val titleTop = textNode(host, "Fit Preference").boundsInRoot.top
        saveCrop(bitmap, name, (titleTop - 10 * density).toInt(), (140 * density).toInt())
        return bitmap
    }

    @Test
    fun `settings - mens selected`() {
        val host = settingsHost(GenderPreference.MALE)
        val bitmap = shootFit(host, "settings-mens")
        assertFitState(host, bitmap, "Men's", null, "settings men's")
    }

    @Test
    fun `settings - womens selected`() {
        val host = settingsHost(GenderPreference.FEMALE)
        val bitmap = shootFit(host, "settings-womens")
        assertFitState(host, bitmap, "Women's", null, "settings women's")
    }

    @Test
    fun `settings - stored UNISEX renders nothing selected`() {
        val host = settingsHost(GenderPreference.UNISEX)
        val bitmap = shootFit(host, "settings-neutral")
        assertFitState(host, bitmap, null, null, "settings neutral")
    }
}

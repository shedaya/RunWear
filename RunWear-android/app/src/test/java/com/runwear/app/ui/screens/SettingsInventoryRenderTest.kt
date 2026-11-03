package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.BuildConfig
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
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
 * WS-5 (D-14) — the Settings inventory, WALKED: the production
 * [SettingsSheetContent] rendered end to end (Robolectric native
 * graphics, tall viewport so the LazyColumn composes every item)
 * in the shipped configuration — Make It Me flag OFF, no view
 * model passed by the harness beyond what the host passes — in
 * both modes. Every registered section's rows must be present,
 * in registry order. (Flag-ON mode presentations are covered by
 * SettingsInventoryTest + MakeItMeSettingsPresentation: the flag
 * is a build constant, OFF in this build, so the ON presentations
 * cannot be rendered by this build's composables.)
 *
 * PNGs land in build/render-out-ws5/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h2200dp-xhdpi")
class SettingsInventoryRenderTest {

    private val outDir: File =
        File(System.getProperty("ws5ShotsDir") ?: "build/render-out-ws5")

    private fun render(name: String, dark: Boolean): List<String> {
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
                SettingsSheetContent(
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    comfortPreference = ComfortPreference.NEUTRAL,
                    genderPreference = GenderPreference.UNISEX,
                    onToggleUnit = {},
                    onSetComfortPreference = {},
                    onSetGenderPreference = {},
                    analyticsOptIn = false,
                    onAnalyticsOptInChange = {},
                    onSendFeedback = {}
                )
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return texts(mergedRoot(composeView))
    }

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

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun texts(root: SemanticsNode): List<String> =
        nodes(root).flatMap { node ->
            val out = mutableListOf<String>()
            if (node.config.contains(SemanticsProperties.Text)) {
                out += node.config[SemanticsProperties.Text].map { it.text }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                out += node.config[SemanticsProperties.ContentDescription]
            }
            out
        }

    /** The inventory checklist, in registry order (frames 35–39). */
    private val expectedInOrder = listOf(
        "SETTINGS",
        "Appearance",
        "Temperature Unit",
        "Body Temperature",
        "Fit Preference",
        MakeItMeStrings.S1_SECTION_HEADER,
        MakeItMeStrings.S2_STATE_A_ROW,
        MakeItMeStrings.S20_DELETE_DATA_ROW,
        "Send feedback",
        "Help improve the app",
        "About",
        "Version",
        SettingsInventory.versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    )

    private fun assertInventory(texts: List<String>, label: String) {
        var cursor = -1
        for (expected in expectedInOrder) {
            val at = texts.indexOfFirst { it == expected || it.contains(expected) }
            assertTrue(
                "[$label] missing inventory row '$expected' — rendered: $texts",
                at >= 0
            )
            assertTrue(
                "[$label] inventory row '$expected' out of order (at $at, after $cursor) — rendered: $texts",
                at > cursor
            )
            cursor = at
        }
    }

    @Test
    fun `settings renders the full inventory in order - flag OFF, light`() {
        assertInventory(render("settings-inventory-light", dark = false), "light")
    }

    @Test
    fun `settings renders the full inventory in order - flag OFF, dark`() {
        assertInventory(render("settings-inventory-dark", dark = true), "dark")
    }
}

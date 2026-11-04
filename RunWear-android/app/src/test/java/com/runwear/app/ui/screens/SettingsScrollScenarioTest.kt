package com.runwear.app.ui.screens

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ScrollAxisRange
import com.runwear.app.BuildConfig
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.TemperatureUnit
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

/**
 * GEN-2 A1 (W-8) — Settings' last row must be reachable.
 *
 * The field defect (Sol's Nov 4 recording): the final "About"
 * section renders as a half-height sliver at the Settings sheet's
 * bottom edge, and no amount of scrolling carries it into full
 * view. Mechanism: the sheet's column stacks the drag handle
 * above the content, but the content's LazyColumn was measured
 * against the column's full height constraint (a Column does not
 * subtract siblings for unweighted children), so the list's
 * viewport extended past the sheet's bottom edge — at max scroll
 * the list's end aligned with an off-screen viewport bottom and
 * About stayed below the visible edge forever. The fix
 * (Stage-1 sheet discipline: one scroll consumer, proper bottom
 * anchor) weight-bounds the content inside the sheet's column so
 * the viewport's bottom IS the sheet's bottom.
 *
 * This test hosts the PRODUCTION [SettingsSheet] (the real
 * ModalBottomSheet, in its Dialog window), drives its LazyColumn
 * to max scroll through the list's own semantics ScrollBy action,
 * and asserts the About header AND its Version row are fully
 * inside the window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class SettingsScrollScenarioTest {

    @Test
    fun `settings scrolls to the bottom and About is fully in view`() {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                SettingsSheet(
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    comfortPreference = ComfortPreference.NEUTRAL,
                    genderPreference = GenderPreference.UNISEX,
                    onToggleUnit = {},
                    onSetComfortPreference = {},
                    onSetGenderPreference = {},
                    analyticsOptIn = false,
                    onAnalyticsOptInChange = {},
                    onSendFeedback = {},
                    onDismiss = {}
                )
            }
        }
        settle(composeView)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Settings must be hosted in a sheet Dialog", dialog)
        assertTrue("the Settings sheet Dialog must be showing", dialog!!.isShowing)

        // Drive the sheet's one scroll consumer to its max through
        // its own ScrollBy semantics action, re-reading the range
        // each round (recomposition republishes the tree).
        var range: ScrollAxisRange? = null
        var rounds = 0
        while (rounds < 40) {
            val root = dialogRoot(dialog, unmerged = true)
            val scrollNode = nodes(root).firstOrNull {
                it.config.contains(SemanticsProperties.ScrollAxisRange) &&
                    it.config.contains(SemanticsActions.ScrollBy)
            }
            assertNotNull("the Settings sheet must carry a scrollable list", scrollNode)
            range = scrollNode!!.config[SemanticsProperties.ScrollAxisRange]
            if (range.maxValue() > 0f && range.value() >= range.maxValue() - 1f) break
            val advanced = scrollNode.config[SemanticsActions.ScrollBy]
                .action?.invoke(0f, 8000f) == true
            settle(composeView)
            if (!advanced) break
            rounds++
        }
        assertNotNull("the list must publish a scroll range", range)
        val finalRange = range!!
        assertTrue(
            "the list must actually scroll (max ${finalRange.maxValue()})",
            finalRange.maxValue() > 0f
        )
        assertTrue(
            "the list must reach max scroll (value ${finalRange.value()} of ${finalRange.maxValue()})",
            finalRange.value() >= finalRange.maxValue() - 1f
        )

        // At max scroll, About — header and Version row alike —
        // must be FULLY inside the window (pre-fix, its bottom sat
        // below the window's bottom edge at every scroll position).
        val root = dialogRoot(dialog, unmerged = true)
        val limit = root.boundsInRoot.bottom
        val about = textNode(root, "About")
        val versionLabel = textNode(
            root,
            SettingsInventory.versionLabel(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        )
        assertNotNull("About must be composed at max scroll", about)
        assertNotNull("the Version row must be composed at max scroll", versionLabel)
        assertTrue(
            "About must be fully in view: bottom ${about!!.boundsInRoot.bottom} within $limit",
            about.boundsInRoot.bottom <= limit + 1f && about.boundsInRoot.top >= -1f
        )
        assertTrue(
            "the Version row must be fully in view: bottom " +
                "${versionLabel!!.boundsInRoot.bottom} within $limit",
            versionLabel.boundsInRoot.bottom <= limit + 1f && versionLabel.boundsInRoot.top >= -1f
        )
        controller.pause().stop().destroy()
    }

    private fun settle(composeView: ComposeView) {
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, w, h)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun dialogRoot(dialog: android.app.Dialog, unmerged: Boolean): SemanticsNode {
        val decor = dialog.window!!.decorView
        val candidates = mutableListOf<SemanticsNode>()
        fun collect(v: View) {
            if (v is ViewRootForTest) {
                candidates += if (unmerged) {
                    v.semanticsOwner.unmergedRootSemanticsNode
                } else {
                    v.semanticsOwner.rootSemanticsNode
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) collect(v.getChildAt(i))
            }
        }
        collect(decor)
        return candidates.maxByOrNull { nodes(it).size }
            ?: throw IllegalStateException("no ViewRootForTest in the Dialog window")
    }

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun textNode(root: SemanticsNode, text: String): SemanticsNode? =
        nodes(root).firstOrNull {
            it.config.contains(SemanticsProperties.Text) &&
                it.config[SemanticsProperties.Text].any { t -> t.text == text }
        }
}

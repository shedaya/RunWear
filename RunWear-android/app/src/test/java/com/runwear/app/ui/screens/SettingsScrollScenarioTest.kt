package com.runwear.app.ui.screens

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.unit.dp
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

/**
 * GEN-2 A1 (W-8) — Settings' last row must be reachable.
 *
 * The field defect (Sol's Nov 4 recording): the final "About"
 * section renders as a half-height sliver at the Settings sheet's
 * bottom edge, and no amount of scrolling carries it into full
 * view. Mechanism: inside the sheet, the chrome stacks ABOVE the
 * content (the status-bar inset the sheet applies, then the drag
 * handle), but the content's LazyColumn was measured against the
 * sheet column's FULL height constraint (a Column measures
 * unweighted children at the full incoming max height — it does
 * not subtract its siblings). The list's viewport therefore
 * extended past the sheet's bottom edge by the stacked chrome
 * (~52 dp: 24 inset + 28 handle), more than the list's 32 dp
 * bottom padding can absorb: at max scroll the list aligns its
 * end with that off-screen viewport bottom, so the last rows
 * stay below the visible edge at every scroll position. The fix
 * (Stage-1 sheet discipline: one scroll consumer, proper bottom
 * anchor) is the weight bound [SettingsSheet] passes into
 * [SettingsSheetContent] — the viewport becomes exactly the
 * space remaining in the sheet's bounded column.
 *
 * Harness note, with evidence: the production SettingsSheet
 * itself (a ModalBottomSheet in a Dialog window) cannot be
 * driven to its Expanded rest state under Robolectric — in run
 * 41860330133 (LEGACY looper) the list scrolled to max inside a
 * sheet still translated ~1560 px down the window, and in run
 * 41861598667 (PAUSED looper) the sheet never left its hidden
 * offset at all (SETTINGS title top 1792 in a 1688 window); the
 * AnchoredDraggable spring does not progress in a Robolectric
 * Dialog window in either mode (the WS-9 P-3 family). So this
 * test composes the PRODUCTION content with the PRODUCTION
 * weight wiring inside a column that replicates the sheet
 * column's geometry exactly (52 dp of stacked chrome above the
 * content, bounded at the window height), and SheetDiscipline-
 * PinTest pins that SettingsSheet passes this same weight
 * modifier into this same content in the real sheet. In this
 * harness the pre-fix wiring (no weight) leaves About's Version
 * row ~40 px below the window's bottom edge at max scroll; the
 * fixed wiring lands it fully inside.
 */
// LEGACY looper, deliberately: the LazyList scroll animation's
// frames must execute as they are posted (in the PAUSED mode of
// run 41864221822 the semantics ScrollBy was accepted but the
// list's scroll value never moved — 0.0 of 100.0 after 25
// rounds — while the same drive scrolled the real sheet's list
// to max under LEGACY in run 41860330133).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.LEGACY)
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
                // The sheet column's geometry: bounded at the
                // window height, chrome stacked above the content
                // (status inset + drag handle = 52 dp), content
                // weight-bounded exactly as SettingsSheet wires it
                // inside the ModalBottomSheet.
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(52.dp))
                    SettingsSheetContent(
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        comfortPreference = ComfortPreference.NEUTRAL,
                        genderPreference = GenderPreference.UNISEX,
                        onToggleUnit = {},
                        onSetComfortPreference = {},
                        onSetGenderPreference = {},
                        analyticsOptIn = false,
                        onAnalyticsOptInChange = {},
                        onSendFeedback = {},
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        settle(composeView)

        // Drive the content's one scroll consumer to its max
        // through its own ScrollBy semantics action, re-reading
        // the range each round (recomposition republishes it).
        var range: ScrollAxisRange? = null
        var rounds = 0
        while (rounds < 25) {
            val root = rootOf(composeView)
            // The list's viewport is the tallest scroll-range node.
            val scrollNode = nodes(root).filter {
                it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                    it.config.contains(SemanticsActions.ScrollBy)
            }.maxByOrNull { it.boundsInRoot.height }
            assertNotNull("the Settings content must carry a scrollable list", scrollNode)
            range = scrollNode!!.config[SemanticsProperties.VerticalScrollAxisRange]
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
            "the list must reach max scroll (value ${finalRange.value()} of ${finalRange.maxValue()})" +
                " — scroll candidates: " + nodes(rootOf(composeView)).filter {
                    it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
                }.joinToString { n ->
                    val r = n.config[SemanticsProperties.VerticalScrollAxisRange]
                    "[top=${n.boundsInRoot.top} h=${n.boundsInRoot.height} " +
                        "range=${r.value()}/${r.maxValue()}]"
                },
            finalRange.value() >= finalRange.maxValue() - 1f
        )

        // At max scroll, About — header and Version row alike —
        // must be FULLY inside the window.
        val root = rootOf(composeView)
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
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun rootOf(view: View): SemanticsNode {
        var found: ViewRootForTest? = null
        fun collect(v: View) {
            if (found == null && v is ViewRootForTest) found = v
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (found == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(view.rootView)
        return (found ?: throw IllegalStateException("no ViewRootForTest"))
            .semanticsOwner.unmergedRootSemanticsNode
    }

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun textNode(root: SemanticsNode, text: String): SemanticsNode? =
        nodes(root).firstOrNull {
            it.config.contains(SemanticsProperties.Text) &&
                it.config[SemanticsProperties.Text].any { t -> t.text == text }
        }
}

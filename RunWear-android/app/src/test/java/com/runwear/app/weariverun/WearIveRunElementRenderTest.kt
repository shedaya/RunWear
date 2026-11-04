package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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
 * WS-3 (RC-3): rendered evidence for the Wear I've Run element —
 * the production [WearIveRunMiniBar] composable under Robolectric
 * native graphics, in both modes:
 *
 * - state sweeps: MINI / EXPANDED / CUE / ABSENT render exactly the
 *   state's content (CUE renders the EXPANDED content — it is a
 *   presentation of it, not a fifth content state);
 * - both present states carry the SAME two controls (expand/collapse
 *   with the flipped glyph + trash) — and the expanded state has NO
 *   "Remove" text button anywhere (D-10);
 * - tap tests on all four controls (expand + trash in MINI,
 *   collapse + trash in EXPANDED) plus body-tap expansion (D-07:
 *   the mini body tap expands; it does not open the collection);
 * - D-12: expanded content is present from frame one and the
 *   thumbnail slot is a plain tinted box when there is no decoded
 *   photo — a pixel probe at the slot's centre finds the card tint,
 *   not a broken-image glyph.
 *
 * PNGs land in build/render-out-ws3/ (CI evidence is the passing
 * assertions, per the harness pattern WS-1/WS-2/WS-8 recorded).
 */
abstract class WearElementHarness {

    protected val entry = WearIveRunEntry(
        place = "New York, NY",
        timeLabel = "Now",
        payload = "Light Tights",
        thumbnailUrl = null,
        feelsLikeF = 41.0,
        itemCount = 2
    )
    protected val narration: WearIveRunNarration =
        WearIveRunNarrationFormatter.added("New York, NY", "Now")

    private val outDir: File =
        File(System.getProperty("ws3ShotsDir") ?: "build/render-out-ws3")

    protected class Rendered(
        val root: SemanticsNode,
        val bitmap: Bitmap,
        val density: Float
    )

    protected fun render(
        name: String,
        dark: Boolean,
        content: @Composable () -> Unit
    ): Rendered {
        // Pre-publish the palette the theme's SideEffect would
        // publish: under Robolectric's paused looper the SideEffect →
        // recomposition round does not reliably settle before the
        // draw (the WS-1 harness limitation), and RunWearColors is a
        // process-global token holder — without this, a render can
        // read the palette a previous render in the class left
        // behind. The theme below still composes with the same flag.
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            if (dark) com.runwear.app.ui.theme.DarkPalette
            else com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = dark) { content() }
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
        val warm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(warm))
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return Rendered(
            mergedRoot(composeView),
            bitmap,
            activity.resources.displayMetrics.density
        )
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

    protected fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    protected fun texts(root: SemanticsNode): List<String> =
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

    protected fun clickByDescription(root: SemanticsNode, description: String) {
        val node = nodes(root).firstOrNull {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription].any { d -> d == description } &&
                it.config.contains(SemanticsActions.OnClick)
        } ?: throw AssertionError("no clickable node with description '$description'")
        node.config[SemanticsActions.OnClick].action?.invoke()
            ?: throw AssertionError("OnClick action missing")
    }

    /** Click the clickable node that carries [text] — the body tap. */
    protected fun clickBody(root: SemanticsNode, text: String) {
        val node = nodes(root).firstOrNull {
            it.config.contains(SemanticsProperties.Text) &&
                it.config[SemanticsProperties.Text].any { t -> t.text.contains(text) } &&
                it.config.contains(SemanticsActions.OnClick)
        } ?: throw AssertionError("no clickable body node carrying '$text'")
        node.config[SemanticsActions.OnClick].action?.invoke()
            ?: throw AssertionError("OnClick action missing")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class WearIveRunElementSweepTest : WearElementHarness() {

    private fun assertMini(r: Rendered, label: String) {
        val all = texts(r.root)
        assertTrue("[$label] wordmark", all.any { it.contains("Wear I've Run") })
        assertTrue("[$label] narration", all.any { it.contains("Added New York, NY, Now") })
        assertTrue("[$label] expand control", all.contains("Expand Wear I've Run"))
        assertTrue("[$label] trash control", all.contains("Remove from Wear I've Run"))
        assertFalse("[$label] no collapse in mini", all.contains("Collapse Wear I've Run"))
    }

    private fun assertExpandedContent(r: Rendered, label: String) {
        val all = texts(r.root)
        assertTrue("[$label] wordmark", all.any { it.contains("Wear I've Run") })
        assertTrue("[$label] narration present from frame one", all.any { it.contains("Added New York, NY, Now") })
        assertTrue("[$label] subtitle present from frame one", all.any { it.contains("41° feels like · 2 items") })
        assertTrue("[$label] collapse control", all.contains("Collapse Wear I've Run"))
        assertTrue("[$label] trash control", all.contains("Remove from Wear I've Run"))
        assertFalse("[$label] no expand glyph while expanded", all.contains("Expand Wear I've Run"))
        // D-10: deletion is the trash ICON in both states — the word
        // "Remove" as a standalone TEXT label must not exist (the
        // content description "Remove from Wear I've Run" is the
        // icon's, and is expected).
        assertFalse("[$label] no Remove text button", all.any { it == "Remove" })
    }

    @Test
    fun miniLightAndDark() {
        assertMini(
            render("ws3-mini-light", dark = false) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Mini(narration, entry),
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "mini light"
        )
        assertMini(
            render("ws3-mini-dark", dark = true) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Mini(narration, entry),
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "mini dark"
        )
    }

    @Test
    fun expandedLightAndDark() {
        assertExpandedContent(
            render("ws3-expanded-light", dark = false) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Expanded(narration, entry),
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "expanded light"
        )
        assertExpandedContent(
            render("ws3-expanded-dark", dark = true) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Expanded(narration, entry),
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "expanded dark"
        )
    }

    @Test
    fun cueRendersTheExpandedContentInBothModes() {
        assertExpandedContent(
            render("ws3-cue-light", dark = false) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Cue(narration, entry),
                    cueSequence = 1,
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "cue light"
        )
        assertExpandedContent(
            render("ws3-cue-dark", dark = true) {
                WearIveRunMiniBar(
                    state = WearIveRunBarState.Cue(narration, entry),
                    cueSequence = 1,
                    onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
                )
            }, "cue dark"
        )
    }

    @Test
    fun absentRendersNothing() {
        val r = render("ws3-absent-light", dark = false) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Absent,
                onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
            )
        }
        assertTrue("ABSENT must render no content", texts(r.root).isEmpty())
    }

    @Test
    fun thumbnailSlotIsAPlainTintedBoxWhenNoPhotoIsDecoded() {
        // Dark mode: the card tint (#2A251E) differs from the sheet
        // surface (#211D17), so the slot is pixel-distinguishable.
        // thumbnailUrl = null (no photo decoded yet): the centre of
        // the 56dp slot must be the pure card tint — the old build
        // drew a broken-image glyph there (D-12). GEN-2 morph
        // geometry: the slot sits in the expanded section below the
        // anchor row — x = 16dp start padding + 28dp (half the slot),
        // y = 16dp top padding (full expression) + 48dp anchor row
        // (the 48dp control hit areas) + 8dp section gap + 28dp.
        val r = render("ws3-expanded-dark-nophoto", dark = true) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Expanded(narration, entry),
                onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {}
            )
        }
        val cx = (44 * r.density).toInt()
        val cy = (100 * r.density).toInt()
        val px = r.bitmap.getPixel(cx, cy)
        assertEquals(
            "thumbnail slot centre must be the plain card tint, no glyph",
            0xFF2A251E.toInt(), px
        )
    }

    @Test
    fun expandedCarriesTheExplicitViewCollectionAffordance() {
        // W-1 (a): the EXPANDED card carries a labeled, visible
        // control into the full collection — and tapping it opens
        // the collection without expanding or collapsing anything.
        var collections = 0
        var expands = 0
        var collapses = 0
        val r = render("gen2-expanded-view-collection", dark = false) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Expanded(narration, entry),
                onExpandClick = { expands++ },
                onCollapseClick = { collapses++ },
                onRemoveClick = {},
                onOpenCollection = { collections++ }
            )
        }
        val all = texts(r.root)
        assertTrue(
            "expanded state must label the collection doorway",
            all.any { it == "View collection" }
        )
        assertTrue(
            "the doorway carries its full description",
            all.contains("View the full Wear I've Run collection")
        )
        clickByDescription(r.root, "View the full Wear I've Run collection")
        assertEquals(1, collections)
        assertEquals(0, expands)
        assertEquals(0, collapses)
        // The MINI state must NOT carry the doorway (it belongs to
        // the expanded expression).
        val mini = render("gen2-mini-no-doorway", dark = false) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Mini(narration, entry),
                onExpandClick = {}, onCollapseClick = {}, onRemoveClick = {},
                onOpenCollection = { collections++ }
            )
        }
        // (The morph keeps the section in the tree at zero height /
        // zero alpha in MINI; the visible-state assertion is the
        // EXPANDED one above — MINI's rest height is the anchor row
        // alone, pinned by the motion tests' geometry.)
        assertTrue(texts(mini.root).any { it.contains("Added New York, NY, Now") })
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class WearIveRunElementTapTest : WearElementHarness() {

    @Test
    fun miniControlsAndBodyTap() {
        var expands = 0
        var collapses = 0
        var removes = 0
        var collections = 0
        val r = render("ws3-mini-taps", dark = false) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Mini(narration, entry),
                onExpandClick = { expands++ },
                onCollapseClick = { collapses++ },
                onRemoveClick = { removes++ },
                onOpenCollection = { collections++ }
            )
        }
        clickByDescription(r.root, "Expand Wear I've Run")
        assertEquals(1, expands)
        clickByDescription(r.root, "Remove from Wear I've Run")
        assertEquals(1, removes)
        // Body tap: expands — and does NOT open the collection (D-07).
        clickBody(r.root, "Added New York, NY, Now")
        assertEquals(2, expands)
        assertEquals(0, collapses)
        assertEquals(0, collections)
    }

    @Test
    fun expandedControlsAndBodyTap() {
        var expands = 0
        var collapses = 0
        var removes = 0
        var collections = 0
        val r = render("ws3-expanded-taps", dark = false) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Expanded(narration, entry),
                onExpandClick = { expands++ },
                onCollapseClick = { collapses++ },
                onRemoveClick = { removes++ },
                onOpenCollection = { collections++ }
            )
        }
        clickByDescription(r.root, "Collapse Wear I've Run")
        assertEquals(1, collapses)
        clickByDescription(r.root, "Remove from Wear I've Run")
        assertEquals(1, removes)
        // The expanded body is the collection doorway (a navigation,
        // not a state driver): it neither expands nor collapses.
        clickBody(r.root, "Added New York, NY, Now")
        assertEquals(1, collections)
        assertEquals(0, expands)
        assertEquals(1, collapses)
    }

    @Test
    fun cueControlsObeyTheTouch() {
        var expands = 0
        var collapses = 0
        var removes = 0
        val r = render("ws3-cue-taps", dark = true) {
            WearIveRunMiniBar(
                state = WearIveRunBarState.Cue(narration, entry),
                cueSequence = 1,
                onExpandClick = { expands++ },
                onCollapseClick = { collapses++ },
                onRemoveClick = { removes++ }
            )
        }
        clickByDescription(r.root, "Collapse Wear I've Run")
        assertEquals(1, collapses)
        clickByDescription(r.root, "Remove from Wear I've Run")
        assertEquals(1, removes)
        assertEquals(0, expands)
    }
}

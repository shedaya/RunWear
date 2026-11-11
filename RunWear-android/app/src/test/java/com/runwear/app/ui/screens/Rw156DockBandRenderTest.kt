package com.runwear.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-156 REOPENED (Nov 11, 2026) — the rectangular cream block
 * around the Wear I've Run dock/panel, reproduced and pinned at
 * pixel level.
 *
 * Diagnosis of record (from the 1.2.32 tree + Sol's device
 * screenshots, pixel-sampled): in the MINI form the painted
 * surface is the root ground itself — HeroMainScreen's
 * BoxWithConstraints paints RunWearColors.Background (#F5F1E8),
 * the scroll hosts paint RunWearColors.Surface (#FDFBF6), and
 * RW-147's layout boundary (the PullToRefreshBox bottom padding
 * of dockBandDp + inset) truncates the Surface-painted scroll
 * area ~102dp above the screen's bottom edge. The exposed
 * Background strip spans the full width with a hard straight top
 * edge, and the translucent mini dock floats inset inside it —
 * the strip reads as a rectangular mat around the pill. In the
 * EXPANDED form the panel (WearIveRunFooterSection) is
 * full-bleed edge-to-edge FooterGround with square sides, and
 * its only shadow is an un-clipped rectangular gradient strip
 * above its rounded top corners — a rectangle, not a boundary.
 *
 * These tests pin the cured geometry: no Background-coloured
 * band at the screen's side edges in the dock zone, and the
 * expanded panel inset from both side edges (the RW-152
 * design's dock-residence extent) instead of full-bleed.
 */
abstract class Rw156Base : Rw145RenderTestBase() {

    protected fun nearColor(pixel: Int, r: Int, g: Int, b: Int, tol: Int = 6): Boolean =
        kotlin.math.abs(Color.red(pixel) - r) <= tol &&
            kotlin.math.abs(Color.green(pixel) - g) <= tol &&
            kotlin.math.abs(Color.blue(pixel) - b) <= tol

    protected fun isBackgroundGround(pixel: Int) = nearColor(pixel, 0xF5, 0xF1, 0xE8)
    protected fun isFooterGround(pixel: Int) = nearColor(pixel, 0xF5, 0xF1, 0xE8)

    /** Contiguous run of ground-coloured rows at [x] ending at
     * the bitmap's bottom edge. */
    protected fun bottomGroundRunPx(bitmap: Bitmap, x: Int): Int {
        var run = 0
        var y = bitmap.height - 1
        while (y >= 0 && isBackgroundGround(bitmap.getPixel(x, y))) {
            run++
            y--
        }
        return run
    }

    protected fun clickDescription(host: Host, description: String) {
        var target: SemanticsNode? = null
        fun walk(node: SemanticsNode) {
            if (target == null &&
                node.config.contains(SemanticsProperties.ContentDescription) &&
                node.config[SemanticsProperties.ContentDescription]
                    .any { it == description } &&
                node.config.contains(SemanticsActions.OnClick)
            ) {
                target = node
            }
            node.children.forEach { walk(it) }
        }
        walk(rootNode(host.composeView))
        val node = target
            ?: throw AssertionError("no clickable node '$description'")
        node.config[SemanticsActions.OnClick].action?.invoke()
            ?: throw AssertionError("OnClick missing for '$description'")
        settleReveal(host)
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw156MiniBandRenderTest : Rw156Base() {

    @Test
    fun `rw156 mini dock - no ground band at the side edges`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val bitmap = capture(host, "rw156-mini-rest.png")
        val density = host.composeView.resources.displayMetrics.density
        val leftRun = bottomGroundRunPx(bitmap, 3)
        val rightRun = bottomGroundRunPx(bitmap, bitmap.width - 4)
        println(
            "RW156-MINI ground run at bottom edges: left=${leftRun}px " +
                "right=${rightRun}px (density=$density, bitmap=${bitmap.width}x${bitmap.height})"
        )
        assertTrue(
            "mini dock present",
            texts(host).any { it.text.contains("Wear I've Run") }
        )
        assertTrue(
            "no Background ground band at the left edge " +
                "(run ${leftRun}px >= 8dp)",
            leftRun < 8 * density
        )
        assertTrue(
            "no Background ground band at the right edge " +
                "(run ${rightRun}px >= 8dp)",
            rightRun < 8 * density
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw156ExpandedPanelRenderTest : Rw156Base() {

    @Test
    fun `rw156 expanded panel - inset from both side edges`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        clickDescription(host, "Expand Wear I've Run")
        val bitmap = capture(host, "rw156-expanded.png")
        val density = host.composeView.resources.displayMetrics.density

        // The panel's vertical span: FooterGround rows on the
        // centre column.
        val cx = bitmap.width / 2
        var panelTop = -1
        var panelBottom = -1
        for (y in 0 until bitmap.height) {
            if (isFooterGround(bitmap.getPixel(cx, y))) {
                if (panelTop < 0) panelTop = y
                panelBottom = y
            }
        }
        assertTrue("expanded panel composed (FooterGround found)", panelTop >= 0)
        val midY = (panelTop + panelBottom) / 2
        // Panel's horizontal extent at its middle row.
        var panelLeft = -1
        var panelRight = -1
        for (x in 0 until bitmap.width) {
            if (isFooterGround(bitmap.getPixel(x, midY))) {
                if (panelLeft < 0) panelLeft = x
                panelRight = x
            }
        }
        println(
            "RW156-EXPANDED panelTop=$panelTop panelBottom=$panelBottom " +
                "panelLeft=$panelLeft panelRight=$panelRight " +
                "bitmap=${bitmap.width}x${bitmap.height} density=$density"
        )
        assertTrue(
            "panel inset from the left edge (left=${panelLeft}px, want >= 10dp)",
            panelLeft >= 10 * density
        )
        assertTrue(
            "panel inset from the right edge (right=${panelRight}px of " +
                "${bitmap.width}, want >= 10dp of air)",
            bitmap.width - 1 - panelRight >= 10 * density
        )
        host.controller.pause().stop().destroy()
    }
}

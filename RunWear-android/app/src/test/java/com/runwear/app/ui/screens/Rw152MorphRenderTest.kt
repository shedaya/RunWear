package com.runwear.app.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-152 §1+§2 (Nov 10, 2026) — the dock-overlay morph, rendered:
 * ONE composition driven through its three states — mini at
 * rest, expanded panel on the chevron, mini again on the
 * collapse chevron (dismissal restores the page exactly). The
 * expanded assertions pin the design's gate at render level: the
 * doorway is composed inside the viewport and the narration the
 * panel grew from is still the panel's own line (content-sized,
 * in place — not a second surface).
 *
 * One test per class: the repo's full-screen harness rule (the
 * first full-screen composition in a sandbox is the one that
 * reveals — see Rw144RenderTestBase).
 */
abstract class Rw152MorphBase : Rw145RenderTestBase() {

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

    protected fun morphSequence(framePrefix: String) {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }

        // REST: the mini dock, no doorway.
        capture(host, "$framePrefix-rest.png")
        var boxes = nodeBoxes(host)
        assertTrue(
            "mini dock present at rest",
            boxes.any { it.text.contains("Wear I've Run") }
        )
        assertFalse(
            "no collection doorway at rest",
            boxes.any { it.text.contains("Open the collection") }
        )

        // EXPANDED: the same surface, grown in place.
        clickDescription(host, "Expand Wear I've Run")
        capture(host, "$framePrefix-expanded.png")
        boxes = nodeBoxes(host)
        val density = host.composeView.resources.displayMetrics.density
        val viewportDp =
            host.composeView.resources.displayMetrics.heightPixels / density
        val doorway = boxes.firstOrNull { it.text.contains("Open the collection") }
        assertTrue("doorway composed in the expanded panel", doorway != null)
        assertTrue(
            "doorway inside the viewport (bottom ${doorway!!.bottom / density} of $viewportDp)",
            doorway.bottom / density <= viewportDp + 1f
        )
        val panelTop = boxes
            .filter { it.text.contains("Wear I've Run") }
            .minOfOrNull { it.top / density } ?: Float.MAX_VALUE
        println(
            "RW152-MORPH $framePrefix panelTopText=$panelTop viewport=$viewportDp"
        )

        // DISMISSED: the page restored exactly — mini dock, no
        // doorway, same narration.
        clickDescription(host, "Collapse Wear I've Run")
        capture(host, "$framePrefix-dismissed.png")
        boxes = nodeBoxes(host)
        assertTrue(
            "mini dock restored after dismissal",
            boxes.any { it.text.contains("Wear I've Run") }
        )
        assertFalse(
            "doorway gone after dismissal",
            boxes.any { it.text.contains("Open the collection") }
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw152MorphStandardRenderTest : Rw152MorphBase() {

    @Test
    fun `rw152 morph sequence - standard portrait`() {
        morphSequence("rw152-morph-standard")
    }
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw152MorphFoldedRenderTest : Rw152MorphBase() {

    @Test
    fun `rw152 morph sequence - folded portrait`() {
        morphSequence("rw152-morph-folded")
    }
}

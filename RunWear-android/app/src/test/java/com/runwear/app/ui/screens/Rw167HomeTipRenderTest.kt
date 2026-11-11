package com.runwear.app.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ScrollAxisRange
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-167 (Nov 11, 2026) — the home scroll showed the same tip
 * twice: the inlined detailed-weather block's "Running tip" row
 * and the "Pro Tip" card both render outfit.tips.first. This
 * test drives the home list through its scroll positions and
 * pins the cured surface: at no composed position does the
 * first tip's text appear more than once, the "Running tip"
 * header never appears on the home surface, and the Pro Tip
 * card does render the tip (the suppression must not take the
 * settled card with it).
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw167HomeTipRenderTest : Rw145RenderTestBase() {

    private fun allNodes(root: SemanticsNode): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            out += n
            n.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    @Test
    fun `rw167 home scroll renders the first tip exactly once`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = nyState(photo)
        val outfit = state.outfit!!
        assertTrue("fixture outfit carries tips", outfit.tips.isNotEmpty())
        val tipProbe = outfit.tips.first()
            .substringAfter(" ", outfit.tips.first())
            .take(24)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }

        var maxSimultaneous = 0
        var proTipSeen = false
        var runningTipHeaderSeen = false

        fun sample() {
            val all = texts(host).map { it.text }
            val count = all.count { it.contains(tipProbe) }
            if (count > maxSimultaneous) maxSimultaneous = count
            if (all.any { it.contains("Pro Tip") }) proTipSeen = true
            if (all.any { it.trim() == "Running tip" }) {
                runningTipHeaderSeen = true
            }
            println(
                "RW167 sample: tipOccurrences=$count proTip=$proTipSeen " +
                    "runningTipHeader=$runningTipHeaderSeen"
            )
        }

        sample()
        // Drive the home list through its range in steps,
        // sampling each composed position.
        repeat(14) {
            val root = rootNode(host.composeView)
            val scrollNode = allNodes(root).filter {
                it.config.contains(SemanticsProperties.VerticalScrollAxisRange) &&
                    it.config.contains(SemanticsActions.ScrollBy)
            }.maxByOrNull { it.boundsInRoot.height } ?: return@repeat
            val range: ScrollAxisRange =
                scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]
            if (range.value() >= range.maxValue() - 1f) return@repeat
            scrollNode.config[SemanticsActions.ScrollBy]
                .action?.invoke(0f, 900f)
            settleReveal(host)
            sample()
        }

        println(
            "RW167 final: maxSimultaneous=$maxSimultaneous " +
                "proTipSeen=$proTipSeen runningTipHeaderSeen=$runningTipHeaderSeen"
        )
        assertTrue(
            "the Pro Tip card renders on the home scroll",
            proTipSeen
        )
        assertTrue(
            "first tip never appears twice at one composed position " +
                "(max simultaneous $maxSimultaneous)",
            maxSimultaneous <= 1
        )
        assertTrue(
            "no 'Running tip' header on the home surface",
            !runningTipHeaderSeen
        )
        host.controller.pause().stop().destroy()
    }
}

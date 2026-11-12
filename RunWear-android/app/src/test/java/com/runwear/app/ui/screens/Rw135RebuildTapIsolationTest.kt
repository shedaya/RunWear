package com.runwear.app.ui.screens

import android.os.Looper
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunMorphSurface
import com.runwear.shared.domain.model.TemperatureUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RB-02 isolation (Phase 2 rebuild, Nov 12, 2026): the expand
 * icon does nothing on device, while every semantics-driven
 * harness test passed — because those tests invoke the node's
 * OnClick action directly, bypassing pointer hit-testing
 * entirely. This test separates the two paths on the SAME
 * surface:
 *
 *  1. semantics path — invoke the "Expand Wear I've Run"
 *     node's OnClick action (what Rw152MorphBase.clickDescription
 *     does). Recorded as [semanticsClickFires].
 *  2. pointer path — dispatch a REAL MotionEvent down/up at
 *     the centre of that node's boundsInRoot to the hosting
 *     view, so the event travels the actual pointer-input hit
 *     chain (container clickable, stacked control slot,
 *     siblings). Recorded as [touchFires].
 *
 * The assertion of record is the pointer path: a real tap at
 * the expand glyph's centre must fire onExpand exactly once
 * and flip the surface to its expanded state. On the pre-fix
 * construction (two IconButtons stacked in one 48dp Box, the
 * topmost carrying graphicsLayer alpha = p = 0 at mini rest)
 * this test FAILS — see the lane report for the mechanism.
 */
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw135RebuildTapIsolationTest : Rw169Base() {

    @Test
    fun `rb02 expand icon - semantics click vs real touch at centre`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val day = fixtureDayEntry(photo)
        var expanded by mutableStateOf(false)
        var expandCalls = 0
        var semanticsClickFires = -1
        var touchFires = -1
        val host = compose(dark = false) {
            WearIveRunMorphSurface(
                narration = WearIveRunDockNarration.line(day, nov11),
                entry = fixtureBarEntry(photo),
                entryLine = WearIveRunDockNarration.entryLine(
                    day, nov11, TemperatureUnit.FAHRENHEIT
                ),
                expanded = expanded,
                onExpand = { expandCalls++; expanded = true },
                onCollapse = { expanded = false },
                onDelete = {},
                onOpenCollection = {},
                maxExpandedHeightDp = 2000f
            )
        }

        fun findNode(desc: String): SemanticsNode? {
            var found: SemanticsNode? = null
            fun walk(n: SemanticsNode) {
                if (found == null &&
                    n.config.contains(SemanticsProperties.ContentDescription) &&
                    n.config[SemanticsProperties.ContentDescription].any { it == desc }
                ) {
                    found = n
                }
                n.children.forEach { walk(it) }
            }
            walk(rootNode(host.composeView))
            return found
        }

        // ---- Path 1: semantics OnClick (the harness's old way) ----
        val expandNode = findNode("Expand Wear I've Run")
            ?: throw AssertionError("no 'Expand Wear I've Run' node")
        val beforeSemantics = expandCalls
        expandNode.config[SemanticsActions.OnClick].action?.invoke()
        semanticsClickFires = expandCalls - beforeSemantics
        println(
            "RB02-ISOLATION semanticsClickFires=$semanticsClickFires " +
                "expandedAfterSemantics=$expanded"
        )
        // Reset to mini for the pointer path.
        expanded = false
        expandCalls = 0
        advanceFrames(host, 4)

        // ---- Path 2: a real touch at the node's centre ----
        val node2 = findNode("Expand Wear I've Run")
            ?: throw AssertionError("no 'Expand Wear I've Run' node (2nd)")
        val b = node2.boundsInRoot
        val cx = (b.left + b.right) / 2f
        val cy = (b.top + b.bottom) / 2f
        println(
            "RB02-ISOLATION touch centre=($cx,$cy) bounds=$b " +
                "view=${host.composeView.width}x${host.composeView.height}"
        )
        val view = host.composeView
        val downTime = android.os.SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, cx, cy, 0)
        view.dispatchTouchEvent(down)
        shadowOf(Looper.getMainLooper()).idle()
        val up = MotionEvent.obtain(
            downTime, downTime + 50, MotionEvent.ACTION_UP, cx, cy, 0
        )
        view.dispatchTouchEvent(up)
        shadowOf(Looper.getMainLooper()).idle()
        // Let any click + the morph's first frames run.
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(4) {
            scheduler.advanceBy(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        touchFires = expandCalls
        println(
            "RB02-ISOLATION touchFires=$touchFires expandedAfterTouch=$expanded"
        )
        down.recycle(); up.recycle()

        // ---- Path 3: a real touch on the bar BODY (wordmark
        // area, away from both control slots) — isolates whether
        // the container's own clickable is alive while the icon
        // slot is dead.
        expanded = false
        expandCalls = 0
        advanceFrames(host, 4)
        val bodyDown = MotionEvent.obtain(
            downTime, downTime, MotionEvent.ACTION_DOWN, 100f, 48f, 0
        )
        view.dispatchTouchEvent(bodyDown)
        shadowOf(Looper.getMainLooper()).idle()
        val bodyUp = MotionEvent.obtain(
            downTime, downTime + 50, MotionEvent.ACTION_UP, 100f, 48f, 0
        )
        view.dispatchTouchEvent(bodyUp)
        shadowOf(Looper.getMainLooper()).idle()
        repeat(4) {
            scheduler.advanceBy(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val bodyTouchFires = expandCalls
        println(
            "RB02-ISOLATION bodyTouchFires=$bodyTouchFires " +
                "expandedAfterBodyTouch=$expanded"
        )
        bodyDown.recycle(); bodyUp.recycle()

        assertTrue(
            "semantics path recorded (fires=$semanticsClickFires)",
            semanticsClickFires >= 0
        )
        assertEquals(
            "a REAL touch on the bar body must fire onExpand exactly once",
            1, bodyTouchFires
        )
        assertEquals(
            "a REAL touch at the expand glyph's centre must fire onExpand " +
                "exactly once (semantics path fired $semanticsClickFires)",
            1, touchFires
        )
        assertTrue("the surface state flips to expanded", expanded)
        host.controller.pause().stop().destroy()
    }
}

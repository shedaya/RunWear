package com.runwear.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunMorphSurface
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-135 REBUILD Phase 3 evidence (Nov 12, 2026): the morph
 * driven through its REAL animation progress at the folded
 * width, with the Kansas City fixture entry from Sol's
 * recording — place "Kansas City, MO", narration "Updated to
 * Kansas City, MO, Now", the 5-item entry line (Ear Warmer /
 * Headband · Light Long Sleeve · Light Tights · Light Gloves
 * · Reflective Gear, 37°F feels like).
 *
 * Samples land at p = 0, ~0.25, ~0.5, ~0.75, 1 through the
 * production 380ms morph tween, walked frame by frame with
 * the bounded one-task looper pump (idle() completes the
 * animation in one call in this harness) and sampled when
 * the measured panel-height fraction crosses each target —
 * never by hand-placing two rest states. The lane stages the
 * PNGs and verifies the mid-flight files are pairwise
 * byte-distinct and distinct from both endpoints.
 */
@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw135RebuildCaptureTest : Rw169Base() {

    private val kcDay: LocalDate = LocalDate.of(2026, 11, 12)

    /** The panel's rendered height in px: the surface spans
     * the full width at the top of the frame, so the last
     * opaque row on the centre column is its bottom edge
     * (the harness background is transparent black). */
    private fun panelHeightPx(
        bitmap: android.graphics.Bitmap
    ): Int {
        val cx = bitmap.width / 2
        var last = -1
        for (y in 0 until bitmap.height) {
            if (android.graphics.Color.alpha(
                    bitmap.getPixel(cx, y)
                ) > 0
            ) {
                last = y
            }
        }
        return last + 1
    }

    private fun kcEntry(): WearIveRunDayEntry = WearIveRunDayEntry(
        date = kcDay,
        place = "Kansas City, MO",
        feelsLikeF = 37.0,
        conditionLabel = "Clear sky",
        isSnowing = false,
        isRaining = false,
        items = listOf(
            "Ear Warmer / Headband", "Light Long Sleeve",
            "Light Tights", "Light Gloves", "Reflective Gear"
        ),
        heroRef = null,
        sessionId = "rw135-rebuild-kc"
    )

    @Composable
    private fun KcMorph(expanded: Boolean) {
        val day = kcEntry()
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, kcDay),
            entry = WearIveRunEntry(
                place = "Kansas City, MO",
                timeLabel = "Now",
                thumbnailUrl = null,
                feelsLikeF = 37.0,
                itemCount = day.items.size,
                itemNames = day.items,
                sourceEntry = day
            ),
            entryLine = WearIveRunDockNarration.entryLine(
                day, kcDay, TemperatureUnit.FAHRENHEIT
            ),
            expanded = expanded,
            onExpand = {},
            onCollapse = {},
            onDelete = {},
            onOpenCollection = {},
            maxExpandedHeightDp = 2000f
        )
    }

    @Test
    fun `rw135 rebuild - kansas city morph sequence at folded width`() {
        // The fixture's own strings, pinned before any frame.
        val day = kcEntry()
        assertEquals(
            "Updated to Kansas City, MO, Now",
            WearIveRunDockNarration.line(day, kcDay).text
        )
        val line = WearIveRunDockNarration.entryLine(
            day, kcDay, TemperatureUnit.FAHRENHEIT
        )
        assertTrue(line.startsWith("Kansas City, MO · Today"))
        assertTrue(line.contains("37°F feels like"))
        assertTrue(line.contains("Ear Warmer / Headband"))
        assertTrue(line.contains("Reflective Gear"))

        var expanded by mutableStateOf(false)
        val host = compose(dark = false) { KcMorph(expanded) }
        capture(host, "rw135-kc-morph-p000.png")

        expanded = true
        // Sampling through the REAL animation, walked frame by
        // frame with the bounded one-task pump (the settlePump
        // idiom): in this harness idle() runs the animation's
        // frame trampoline to completion in a single call, so
        // scheduler-stepped samples all land at rest — probed
        // Nov 12, 2026. The pump advances the production
        // 380ms tween task by task; the panel's rendered
        // height is a monotonic linear-in-p function (header,
        // paddings and the expanded region all scale with p),
        // so samples are taken when the MEASURED panel-height
        // fraction crosses 0.25 / 0.50 / 0.75 of the
        // mini->expanded height span.
        // Learn both rest heights on THIS host first: settle
        // to EXPANDED (idle() completes the animation), measure,
        // settle back to MINI, then re-run the expansion under
        // the bounded pump with the true span known.
        val miniPx = panelHeightPx(capture(host, "tmp-mini.png"))
        expanded = true
        advanceFrames(host, 4)
        val restPx = panelHeightPx(capture(host, "tmp-rest.png"))
        assertTrue("panel grew to expanded rest", restPx > miniPx)
        expanded = false
        advanceFrames(host, 4)
        val backPx = panelHeightPx(capture(host, "tmp-back.png"))
        assertTrue(
            "collapse returned to mini height (was ${backPx}px vs ${miniPx}px)",
            kotlin.math.abs(backPx - miniPx) <= 2
        )

        expanded = true
        val targets = listOf(
            0.25f to "rw135-kc-morph-p025.png",
            0.50f to "rw135-kc-morph-p050.png",
            0.75f to "rw135-kc-morph-p075.png"
        )
        val looper = org.robolectric.Shadows.shadowOf(
            android.os.Looper.getMainLooper()
        )
        var targetIdx = 0
        var batches = 0
        var lastHeight = miniPx
        while (targetIdx < targets.size && batches < 600) {
            repeat(5) { looper.runOneTask() }
            batches++
            val w = host.composeView.width
            val h = host.composeView.height
            host.composeView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    w, android.view.View.MeasureSpec.EXACTLY
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    h, android.view.View.MeasureSpec.EXACTLY
                )
            )
            host.composeView.layout(0, 0, w, h)
            val frame = capture(host, "tmp-frame.png")
            lastHeight = panelHeightPx(frame)
            frame.recycle()
            val fraction = (lastHeight - miniPx).toFloat() /
                (restPx - miniPx).toFloat()
            if (fraction >= targets[targetIdx].first) {
                capture(host, targets[targetIdx].second)
                println(
                    "RW135 sample ${targets[targetIdx].second} " +
                        "at batch $batches, panel=${lastHeight}px, " +
                        "fraction=${"%.2f".format(fraction)}"
                )
                targetIdx++
            }
        }
        assertEquals("all mid-morph samples captured", targets.size, targetIdx)
        advanceFrames(host, 4)
        val restFrame = capture(host, "rw135-kc-morph-p100.png")
        val restPx2 = panelHeightPx(restFrame)
        println(
            "RW135 expanded rest panel=${restPx2}px " +
                "(mini=${miniPx}px, first-pass rest=${restPx}px)"
        )
        assertTrue(
            "re-run settles at the same expanded rest height",
            kotlin.math.abs(restPx2 - restPx) <= 2
        )

        // Expanded rest: the doorway is present and the entry
        // line renders whole.
        assertTrue(
            "doorway present at expanded rest",
            texts(host).any { it.text.contains("Open the collection") }
        )
        assertTrue(
            "entry line present at expanded rest",
            texts(host).any { it.text.startsWith("Kansas City, MO · Today") }
        )
        host.controller.pause().stop().destroy()
    }
}

/**
 * RB-04 rendered heights: the MINI rest height for a one-line
 * narration is the prototype's ≈49dp, and in the EXPANDED
 * form the doorway sits exactly the 12dp bottom pad above
 * the panel's bottom edge — no unaccounted empty band below
 * the doorway (the retired construction lerped the bottom
 * pad to 32dp).
 */
@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw135RebuildHeightRenderTest : Rw169Base() {

    @Composable
    private fun ShortMorph(expanded: Boolean, photo: String) {
        val day = WearIveRunDayEntry(
            date = nov11,
            place = "Miami, FL",
            feelsLikeF = 78.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Singlet", "Running Shorts (5-7\")"),
            heroRef = photo,
            sessionId = "rw135-rebuild-height"
        )
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, nov11),
            entry = WearIveRunEntry(
                place = "Miami, FL",
                timeLabel = "Now",
                thumbnailUrl = photo,
                feelsLikeF = 78.0,
                itemCount = day.items.size,
                itemNames = day.items,
                sourceEntry = day
            ),
            entryLine = WearIveRunDockNarration.entryLine(
                day, nov11, TemperatureUnit.FAHRENHEIT
            ),
            expanded = expanded,
            onExpand = {},
            onCollapse = {},
            onDelete = {},
            onOpenCollection = {},
            maxExpandedHeightDp = 2000f
        )
    }

    private fun surfaceBounds(host: Host): androidx.compose.ui.geometry.Rect {
        // The UNMERGED tree: the merged tree's testTag node is
        // an ancestor at viewport size, while the unmerged
        // node carrying the tag is the surface's own Box.
        var root: androidx.compose.ui.semantics.SemanticsNode? = null
        fun collect(v: android.view.View) {
            if (root == null &&
                v is androidx.compose.ui.platform.ViewRootForTest
            ) {
                root = v.semanticsOwner.unmergedRootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (root == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(host.composeView.rootView)
        val r = root ?: throw IllegalStateException("no ViewRootForTest")
        var found: androidx.compose.ui.geometry.Rect? = null
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            if (n.config.contains(
                    androidx.compose.ui.semantics.SemanticsProperties.TestTag
                ) &&
                n.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag] ==
                "rw169MorphSurface"
            ) {
                found = n.boundsInRoot
            }
            n.children.forEach { walk(it) }
        }
        walk(r)
        return found ?: throw AssertionError("no morph surface node")
    }

    @Test
    fun `rb04 rendered heights - mini 49dp class, doorway ends the panel`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        var expanded by mutableStateOf(false)
        // Wrapped in a loose parent: composed as the ComposeView
        // root, the surface would inherit exact viewport
        // constraints; in the app it hangs in the overlay with
        // wrap-content height, which is what is measured here.
        val host = compose(dark = false) {
            androidx.compose.foundation.layout.Box {
                ShortMorph(expanded, photo)
            }
        }
        val density = host.composeView.resources.displayMetrics.density

        val mini = surfaceBounds(host)
        val miniDp = (mini.bottom - mini.top) / density
        println("RB04 mini rest height = ${miniDp}dp")
        assertTrue(
            "mini rest height ≈49dp for a one-line narration (was ${miniDp}dp)",
            miniDp in 47f..52f
        )

        expanded = true
        advanceFrames(host, 5)
        val panel = surfaceBounds(host)
        val doorway = unmergedNodeBoxes(host)
            .firstOrNull { it.text.contains("Open the collection") }
            ?: throw AssertionError("no doorway at expanded rest")
        val belowDoorwayDp = (panel.bottom - doorway.bottom) / density
        println(
            "RB04 expanded height = ${(panel.bottom - panel.top) / density}dp, " +
                "below-doorway = ${belowDoorwayDp}dp"
        )
        assertTrue(
            "only the 12dp bottom pad below the doorway (was ${belowDoorwayDp}dp)",
            belowDoorwayDp in 9f..15f
        )
        host.controller.pause().stop().destroy()
    }
}

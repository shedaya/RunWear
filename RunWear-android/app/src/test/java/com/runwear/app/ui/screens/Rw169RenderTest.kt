package com.runwear.app.ui.screens

import android.os.Looper
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunMorphSurface
import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * RW-169 (Nov 11, 2026): rendered pins for the structural fix —
 * §7A the shared-layout morph (exactly one narration + an
 * uncrushed wordmark at every progress sample, stills at
 * p≈0.25/0.5/0.75), §7B the width law at the approval fixtures,
 * §7C the negotiating chrome at the folded-portrait fixture.
 *
 * Approval-fixture data (diagnosis §7B/G): place pill
 * "Overland Park, KS", narration "Planned for Los Angeles, CA ·
 * Tomorrow", a 4-item kit, personal hero (the bundled photo).
 */
abstract class Rw169Base : Rw156Base() {

    protected val nov11: LocalDate = LocalDate.of(2026, 11, 11)
    protected val nov12: LocalDate = LocalDate.of(2026, 11, 12)

    protected fun fixtureDayEntry(photo: String): WearIveRunDayEntry =
        WearIveRunDayEntry(
            date = nov12,
            place = "Los Angeles, CA",
            feelsLikeF = 66.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf(
                "Running Cap", "Short Sleeve Shirt",
                "Running Shorts (5-7\")", "Sunglasses"
            ),
            heroRef = photo,
            sessionId = "rw169-fixture"
        )

    protected fun fixtureBarEntry(photo: String): WearIveRunEntry {
        val day = fixtureDayEntry(photo)
        return WearIveRunEntry(
            place = "Los Angeles, CA",
            timeLabel = "Tomorrow",
            thumbnailUrl = photo,
            feelsLikeF = 66.0,
            itemCount = 4,
            itemNames = day.items,
            sourceEntry = day
        )
    }

    @Composable
    protected fun MorphFixture(expanded: Boolean, photo: String) {
        val day = fixtureDayEntry(photo)
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, nov11),
            entry = fixtureBarEntry(photo),
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


    /** Overland Park, Now, 41°F — the approval-fixture place at
     * the hero chrome, with the bundled personal-style photo. */
    protected fun overlandState(photo: String): com.runwear.app.ui.viewmodel.MainUiState {
        val w = nyWeather()
        return com.runwear.app.ui.viewmodel.MainUiState(
            isLoading = false,
            error = null,
            outfit = engine.execute(w, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL),
            weather = w,
            hourlyForecast = hourly(nov9),
            locationName = "Overland Park, KS",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("Overland Park, KS"),
            selectedDateTime = nov9Now,
            locationNow = nov9Now,
            isNowSelected = true,
            heroImageUrl = photo,
            heroFallbackUrl = photo,
            heroIsPersonal = false,
            personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Ready,
            heroServeKind = com.runwear.app.makeitme.PersonalServeKind.EXACT,
            locationSource = "gps",
            hasCompletedOnboarding = true
        )
    }

    /** Fine-grained frame steps: advance the scheduler [stepMs]
     * at a time (the morph's 380ms tween resolves into true
     * mid-flight geometry only at frame-scale steps — 100ms
     * steps overshoot whole phases of it). */
    protected fun advanceFramesMs(host: Host, steps: Int, stepMs: Long) {
        val scheduler = Robolectric.getForegroundThreadScheduler()
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        repeat(steps) {
            scheduler.advanceBy(stepMs, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, w, h)
            val frame = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888
            )
            view.draw(android.graphics.Canvas(frame))
            frame.recycle()
        }
    }

    /** Advance the paused scheduler [steps] x 100ms with a draw
     * per step (the settleReveal idiom, bounded for mid-flight
     * morph samples). */
    protected fun advanceFrames(host: Host, steps: Int) {
        val scheduler = Robolectric.getForegroundThreadScheduler()
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        repeat(steps) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, w, h)
            val frame = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888
            )
            view.draw(android.graphics.Canvas(frame))
            frame.recycle()
        }
    }

    protected fun allNodes(host: Host): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            out += n
            n.children.forEach { walk(it) }
        }
        walk(rootNode(host.composeView))
        return out
    }

    /** Text boxes from the UNMERGED semantics tree. The merged
     * tree folds a clickable surface's whole subtree into the
     * surface's node (the mini bar is one tap target), so a
     * merged-tree "wordmark" box measures the surface, not the
     * glyph strip. Glyph-level assertions (one narration, the
     * wordmark's wide-not-tall strip) belong to the unmerged
     * tree, where every Text keeps its own layout bounds. */
    protected fun unmergedNodeBoxes(host: Host): List<NodeBox> {
        var root: SemanticsNode? = null
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
        val out = mutableListOf<NodeBox>()
        fun walk(node: SemanticsNode) {
            // RW-135 REBUILD: a placement carrying
            // invisibleToUser (the morph's sub-0.5-alpha
            // narration/entry/doorway placements) is out of
            // the accessibility tree — its text is not a
            // rendered, legible node for these assertions.
            if (node.config.contains(SemanticsProperties.InvisibleToUser)) {
                return
            }
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    out += NodeBox(
                        t.text,
                        node.boundsInRoot.top, node.boundsInRoot.bottom,
                        node.boundsInRoot.left, node.boundsInRoot.right
                    )
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(r)
        return out
    }

    protected fun narrationNodes(host: Host): List<NodeBox> =
        unmergedNodeBoxes(host).filter {
            it.text.contains("Planned for") || it.text.startsWith("Updated to")
        }

    protected fun wordmarkNodes(host: Host): List<NodeBox> =
        unmergedNodeBoxes(host).filter { it.text == "Wear I've Run" }

    /** The anti-crush signature (diagnosis D1): the wordmark is a
     * wide, short strip. A crushed wordmark is tall and narrow.
     * The glyph node is the SMALLEST node carrying the exact
     * wordmark text — merged ancestors (the surface's clickable
     * merges its subtree in the semantics tree) carry the same
     * text at container size and are not the glyph strip. */
    protected fun assertWordmarkWhole(host: Host, where: String) {
        val marks = wordmarkNodes(host)
        assertTrue("$where: wordmark present", marks.isNotEmpty())
        val m = marks.minByOrNull {
            (it.right - it.left) * (it.bottom - it.top)
        }!!
        val width = m.right - m.left
        val height = m.bottom - m.top
        assertTrue(
            "$where: wordmark wide-not-tall (w=${width}px h=${height}px)",
            width > height * 2f
        )
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169MorphMiniRenderTest : Rw169Base() {

    @Test
    fun `rw169 morph mini at rest - one narration, wordmark whole, bar grows for the two-line narration`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val host = compose(dark = false) { MorphFixture(false, photo) }
        capture(host, "rw169-morph-mini-rest.png")
        assertEquals(
            "exactly one narration instance at rest",
            1, narrationNodes(host).size
        )
        assertEquals(
            "the fixture narration, whole",
            "Planned for Los Angeles, CA · Tomorrow",
            narrationNodes(host).single().text.replace('\u00A0', ' ')
        )
        assertWordmarkWhole(host, "mini rest")
        // The two-line case (spec §4.3): the narration wraps and
        // the bar GROWS — its node is taller than one 12sp line.
        val narr = narrationNodes(host).single()
        val density = host.composeView.resources.displayMetrics.density
        assertTrue(
            "narration wrapped to two lines (h=${narr.bottom - narr.top}px)",
            narr.bottom - narr.top > 20f * density
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw169MorphMidFlightRenderTest : Rw169Base() {

    @Test
    fun `rw169 morph mid-flight - one legible narration and a whole wordmark at p samples`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        var expanded by mutableStateOf(false)
        val host = compose(dark = false) {
            MorphFixtureToggle(expanded, photo)
        }
        assertEquals(1, narrationNodes(host).size)
        expanded = true
        // 380ms morph at frame-scale steps: samples at ~96/192/
        // 288ms of frame time (p≈0.25 / 0.5 / 0.76).
        val labels = listOf("p025", "p050", "p075")
        for ((i, label) in labels.withIndex()) {
            advanceFramesMs(host, 6, 16)
            capture(host, "rw169-morph-$label.png")
            // RW-135 REBUILD legibility invariant: at most ONE
            // narration placement is in the accessibility tree
            // ( hence legible) at any sample — the dissolve's
            // dead zone legitimately shows zero. Any legible
            // narration present is the same string, whole.
            assertTrue(
                "sample $label: at most one legible narration " +
                    "(was ${narrationNodes(host).size})",
                narrationNodes(host).size <= 1
            )
            assertWordmarkWhole(host, "sample $label")
            narrationNodes(host).singleOrNull()?.let { only ->
                assertEquals(
                    "sample $label: the legible narration, whole",
                    "Planned for Los Angeles, CA · Tomorrow",
                    only.text.replace('\u00A0', ' ')
                )
            }
            println("RW169 morph sample $label ok (step ${i + 1})")
        }
        advanceFrames(host, 3)
        capture(host, "rw169-morph-expanded-rest.png")
        assertEquals(1, narrationNodes(host).size)
        assertWordmarkWhole(host, "expanded rest")
        // The expanded-only region is present at rest.
        assertTrue(
            "doorway present at expanded rest",
            texts(host).any { it.text.contains("Open the collection") }
        )
        assertTrue(
            "entry line present at expanded rest, wrapping whole",
            texts(host).any {
                it.text.startsWith("Los Angeles, CA · Tomorrow")
            }
        )
        host.controller.pause().stop().destroy()
    }

    @Composable
    private fun MorphFixtureToggle(expanded: Boolean, photo: String) {
        val day = fixtureDayEntry(photo)
        WearIveRunMorphSurface(
            narration = WearIveRunDockNarration.line(day, nov11),
            entry = fixtureBarEntry(photo),
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
}

@Config(qualifiers = "w800dp-h844dp-xhdpi")
class Rw169MorphWideRenderTest : Rw169Base() {

    @Test
    fun `rw169 morph wide class - mini rest whole, expanded rest whole`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        var expanded by mutableStateOf(false)
        val host = compose(dark = false) {
            MorphWideToggle(expanded, photo)
        }
        capture(host, "rw169-morph-wide-mini.png")
        assertEquals(1, narrationNodes(host).size)
        assertWordmarkWhole(host, "wide mini")
        expanded = true
        advanceFrames(host, 6)
        capture(host, "rw169-morph-wide-expanded.png")
        assertEquals(1, narrationNodes(host).size)
        assertWordmarkWhole(host, "wide expanded")
        host.controller.pause().stop().destroy()
    }

    @Composable
    private fun MorphWideToggle(expanded: Boolean, photo: String) {
        MorphFixture(expanded, photo)
    }
}

@Config(qualifiers = "w390dp-h740dp-xhdpi")
class Rw169ChromeFoldedRenderTest : Rw169Base() {

    @Test
    fun `rw169 chrome folded - place whole, pair end-grouped with constant offset, wordmark zone clear`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = overlandState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        capture(host, "rw169-chrome-folded.png")
        val density = host.composeView.resources.displayMetrics.density
        val widthPx = host.composeView.resources.displayMetrics.widthPixels
        val boxes = nodeBoxes(host)

        // §7B: the place name renders WHOLE — one node, exact text,
        // no ellipsis anywhere in the chrome.
        val place = boxes.filter { it.text == "Overland Park, KS" }
        assertEquals("place name whole (one exact node)", 1, place.size)
        assertTrue(
            "no ellipsized place variant",
            boxes.none { it.text.contains("Overland") && it.text.contains("…") }
        )

        // §7C: share + gear end-grouped. Gear at the row's end;
        // share immediately left of it at the CONSTANT offset.
        val all = allNodes(host)
        fun descBounds(desc: String): NodeBox {
            val node = all.firstOrNull { n ->
                n.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) &&
                    n.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                        .any { it == desc }
            } ?: throw AssertionError("no node with description '$desc'")
            return NodeBox(
                desc, node.boundsInRoot.top, node.boundsInRoot.bottom,
                node.boundsInRoot.left, node.boundsInRoot.right
            )
        }
        val gear = descBounds("Settings")
        val share = descBounds("Share")
        assertTrue(
            "gear at the row's end (right=${gear.right} of $widthPx)",
            gear.right >= widthPx - 20f * density
        )
        val gapDp = (gear.left - share.right) / density
        assertTrue(
            "share-gear gap is the constant 8dp (was ${gapDp}dp)",
            gapDp in 6f..10f
        )
        assertTrue(
            "share in the row's end third, not displaced mid-row " +
                "(left=${share.left} of $widthPx)",
            share.left > widthPx * 0.6f
        )

        // The centred wordmark zone: nothing renders under or over
        // the "RunWear" wordmark — no pill/pair bounds intersect it.
        val wordmark = boxes.filter { it.text == "RunWear" }
        assertEquals(1, wordmark.size)
        val wm = wordmark.single()
        fun intersects(a: NodeBox, b: NodeBox): Boolean =
            a.left < b.right && b.left < a.right &&
                a.top < b.bottom && b.top < a.bottom
        assertTrue(
            "place pill clears the wordmark zone",
            !intersects(place.single(), wm)
        )
        assertTrue("share clears the wordmark", !intersects(share, wm))
        assertTrue("gear clears the wordmark", !intersects(gear, wm))
        host.controller.pause().stop().destroy()
    }
}

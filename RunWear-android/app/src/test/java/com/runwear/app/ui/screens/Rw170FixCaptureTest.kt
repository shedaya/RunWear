package com.runwear.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.viewmodel.MainUiState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * RW-170 FIX LANE (Nov 12, 2026) — composited evidence for the
 * structural fix, written against the DIAGNOSIS's
 * rendered-evidence standard: the REAL HeroMainScreen
 * composition over the real page (never surface-only renders
 * on a dark backdrop), light AND dark, at MINI rest,
 * mid-scroll, scroll end, mid-morph (p≈.5) and EXPANDED —
 * Kansas City fixture (place "Kansas City, MO", 37°F feels
 * like, narration "Updated to Kansas City, MO, Now").
 *
 * Measurement probes, each reported numerically to its own
 * probe file in the render-out dir:
 * (a) container painted bounds vs reservation at rest —
 *     the band's top edge vs the card's painted extent;
 * (b) cross-state surface colour identity — the surface
 *     interior RGB at matched points at p=0 and p=1 (the
 *     same FooterGround token; alpha the only difference —
 *     the mini sample is checked against the alpha
 *     compositing equation over the known sheet ground);
 * (c) shadow spill on all four sides vs the spec shadow
 *     (offset (0,+6dp), blur 24dp, black 14%): a pool below,
 *     ≤ hairline-class spill elsewhere;
 * (d) the morph sequence composited over the page: ONE
 *     surface node, ONE narration node (raw unmerged count —
 *     no invisibleToUser filtering), and landmark stability
 *     at a fixed scroll position between p=0 and p=1.
 */
abstract class Rw170HomeBase : Rw169HomeBase() {

    protected fun kcState(photoUrl: String): MainUiState {
        val w = nyWeather().copy(temperature = 44.0, feelsLike = 37.0)
        return MainUiState(
            isLoading = false,
            error = null,
            outfit = engine.execute(
                w, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
            ),
            weather = w,
            hourlyForecast = hourly(nov9),
            locationName = "Kansas City, MO",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("Kansas City, MO"),
            selectedDateTime = nov9Now,
            locationNow = nov9Now,
            isNowSelected = true,
            heroImageUrl = photoUrl,
            heroFallbackUrl = photoUrl,
            heroIsPersonal = false,
            personalLookStatus =
                com.runwear.app.makeitme.PersonalLookStatus.Ready,
            heroServeKind = com.runwear.app.makeitme.PersonalServeKind.EXACT,
            locationSource = "gps",
            hasCompletedOnboarding = true
        )
    }

    /** Los Angeles TOMORROW plan — the two-line narration
     * case (the wrap grows the presented height; the band
     * must follow by exactly the wrapped line). */
    protected fun laPlanState(photoUrl: String): MainUiState {
        val w = nyWeather().copy(temperature = 72.0, feelsLike = 66.0)
        val tomorrow = nov9Now.plusDays(1)
        return MainUiState(
            isLoading = false,
            error = null,
            outfit = engine.execute(
                w, com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
            ),
            weather = w,
            hourlyForecast = hourly(nov9.plusDays(1)),
            locationName = "Los Angeles, CA",
            simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString("Los Angeles, CA"),
            selectedDateTime = tomorrow,
            locationNow = nov9Now,
            isNowSelected = false,
            heroImageUrl = photoUrl,
            heroFallbackUrl = photoUrl,
            heroIsPersonal = false,
            personalLookStatus =
                com.runwear.app.makeitme.PersonalLookStatus.Ready,
            heroServeKind = com.runwear.app.makeitme.PersonalServeKind.EXACT,
            locationSource = "gps",
            hasCompletedOnboarding = true
        )
    }

    /** The morph surface's own bounds from the UNMERGED tree
     * (the merged tree's testTag node is a viewport-sized
     * ancestor — the Rw135RebuildHeightRenderTest lesson). */
    protected fun surfaceBounds(
        host: Host
    ): androidx.compose.ui.geometry.Rect {
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
        var found: androidx.compose.ui.geometry.Rect? = null
        var count = 0
        fun walk(n: SemanticsNode) {
            if (n.config.contains(SemanticsProperties.TestTag) &&
                n.config[SemanticsProperties.TestTag] ==
                "rw169MorphSurface"
            ) {
                count++
                found = n.boundsInRoot
            }
            n.children.forEach { walk(it) }
        }
        walk(r)
        assertEquals("exactly one morph surface node", 1, count)
        return found ?: throw AssertionError("no morph surface node")
    }

    /** RAW narration-node count in the unmerged tree — no
     * invisibleToUser filtering: a second composed narration
     * counts even if a policy hides it from accessibility.
     * This is the RW-170 Move 4 discriminator. */
    protected fun rawNarrationCount(host: Host): Int {
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
        var count = 0
        fun walk(n: SemanticsNode) {
            if (n.config.contains(SemanticsProperties.Text)) {
                for (t in n.config[SemanticsProperties.Text]) {
                    if (t.text.startsWith("Updated to") ||
                        t.text.startsWith("Planned for")
                    ) {
                        count++
                    }
                }
            }
            n.children.forEach { walk(it) }
        }
        walk(r)
        return count
    }

    /** The scroll viewport's bottom edge (px, root coords):
     * the tallest scroll node's bounds bottom — the point
     * the RW-147/170 truncation cuts the list at. */
    protected fun scrollViewportBottomPx(host: Host): Float =
        scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }
            ?.boundsInRoot?.bottom ?: -1f

    /** Click a semantics-described control WITHOUT the
     * settle pump (clickDescription settles, which completes
     * any animation it triggers — useless for mid-flight
     * samples). */
    protected fun clickDescriptionNoSettle(host: Host, description: String) {
        var target: SemanticsNode? = null
        fun walk(node: SemanticsNode) {
            if (target == null &&
                node.config.contains(SemanticsProperties.ContentDescription) &&
                node.config[SemanticsProperties.ContentDescription]
                    .any { it == description } &&
                node.config.contains(
                    androidx.compose.ui.semantics.SemanticsActions.OnClick
                )
            ) {
                target = node
            }
            node.children.forEach { walk(it) }
        }
        walk(rootNode(host.composeView))
        val node = target
            ?: throw AssertionError("no clickable node '$description'")
        node.config[androidx.compose.ui.semantics.SemanticsActions.OnClick]
            .action?.invoke()
            ?: throw AssertionError("OnClick missing for '$description'")
    }

    /** Drive the page scroll to its end with bounded ScrollBy
     * steps, reporting the final (value, max). The shared
     * flickToEnd under-scrolls on this taller KC page (its
     * single remaining-range invoke does not land), so the
     * RW-170 captures drive explicitly and assert arrival. */
    protected fun driveToEnd(host: Host): Pair<Float, Float> {
        var value = 0f
        var max = 0f
        repeat(12) {
            val node = scrollNodes(host)
                .maxByOrNull { it.boundsInRoot.height } ?: return value to max
            val range =
                node.config[SemanticsProperties.VerticalScrollAxisRange]
            value = range.value()
            max = range.maxValue()
            if (max - value <= 1f) return value to max
            node.config[
                androidx.compose.ui.semantics.SemanticsActions.ScrollBy
            ].action?.invoke(0f, 600f)
            advanceFrames(host, 2)
        }
        val node = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }
        if (node != null) {
            val range =
                node.config[SemanticsProperties.VerticalScrollAxisRange]
            value = range.value()
            max = range.maxValue()
        }
        return value to max
    }

    /** PAGE landmarks: text -> (top, bottom, left, right)
     * in root px, from the UNMERGED tree, EXCLUDING the morph
     * surface's own texts (wordmark, narration, entry line,
     * doorway — those legitimately move/grow with the morph;
     * everything else on the page must not). */
    protected fun landmarks(host: Host): Map<String, List<Float>> =
        unmergedNodeBoxes(host).filterNot {
            it.text == "Wear I've Run" ||
                it.text.startsWith("Updated to") ||
                it.text.startsWith("Planned for") ||
                it.text.contains("feels like") ||
                it.text.contains("Open the collection")
        }.associate {
            it.text to listOf(it.top, it.bottom, it.left, it.right)
        }

    protected fun probe(name: String, lines: List<String>) {
        outDir.mkdirs()
        File(outDir, name).writeText(lines.joinToString("\n") + "\n")
        lines.forEach { println("RW170-PROBE $it") }
    }

    protected fun rgb(pixel: Int): Triple<Int, Int, Int> =
        Triple(Color.red(pixel), Color.green(pixel), Color.blue(pixel))

    protected fun darkening(pixel: Int, ground: Triple<Int, Int, Int>): Int {
        val (r, g, b) = rgb(pixel)
        return (ground.first - r) + (ground.second - g) + (ground.third - b)
    }

    /** Interior sample: centre-x, [fromBottomDp] above the
     * card's bottom edge — inside the bottom padding zone in
     * both forms, clear of text and controls. */
    protected fun interiorSample(
        bitmap: Bitmap,
        bounds: androidx.compose.ui.geometry.Rect,
        fromBottomDp: Float,
        density: Float
    ): Triple<Int, Int, Int> {
        val x = ((bounds.left + bounds.right) / 2f).toInt()
        val y = (bounds.bottom - fromBottomDp * density).toInt()
        return rgb(bitmap.getPixel(x, y))
    }

    /** Composite of [token] at [alpha] over [ground]. */
    protected fun composite(
        token: Triple<Int, Int, Int>, alpha: Float,
        ground: Triple<Int, Int, Int>
    ): Triple<Float, Float, Float> = Triple(
        token.first * alpha + ground.first * (1f - alpha),
        token.second * alpha + ground.second * (1f - alpha),
        token.third * alpha + ground.third * (1f - alpha)
    )

    protected fun near(
        actual: Triple<Int, Int, Int>,
        expected: Triple<Float, Float, Float>, tol: Float
    ): Boolean =
        kotlin.math.abs(actual.first - expected.first) <= tol &&
            kotlin.math.abs(actual.second - expected.second) <= tol &&
            kotlin.math.abs(actual.third - expected.third) <= tol

    protected fun tokenOf(c: androidx.compose.ui.graphics.Color) =
        Triple(
            (c.red * 255).toInt(), (c.green * 255).toInt(),
            (c.blue * 255).toInt()
        )
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeMiniRestLightTest : Rw170HomeBase() {

    @Test
    fun `rw170 mini rest light - band, colour, shadow probes`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val bitmap = capture(host, "rw170-home-mini-rest-light.png")
        val density = host.composeView.resources.displayMetrics.density
        val lines = mutableListOf<String>()
        lines += "state=MINI-REST appearance=LIGHT"

        // ---- (a) painted vs reserved -------------------------
        val bounds = surfaceBounds(host)
        val presentedDp = (bounds.bottom - bounds.top) / density
        val bandDp = DockClearancePolicy.dockBandDp(presentedDp)
        val bandTopPx = bitmap.height - bandDp * density
        val viewportBottom = scrollViewportBottomPx(host)
        lines += "presentedMiniHeightDp=$presentedDp"
        lines += "bandDp=$bandDp (lift 14 + presented)"
        lines += "cardTopPx=${bounds.top} cardBottomPx=${bounds.bottom}"
        lines += "bandTopPx=$bandTopPx viewportBottomPx=$viewportBottom"
        lines += "screenHeightPx=${bitmap.height}"
        // In the HOME seat the host's 12dp inset narrows the
        // surface to 366dp and the Kansas City narration wraps
        // to two lines — the presented form is the WRAPPED
        // form, and the band must price exactly it. (The
        // one-line 49dp / 63dp-band case is pinned at policy
        // level in Rw145147PolicyTest and at surface level in
        // Rw135RebuildHeightRenderTest.)
        val narrNode = unmergedNodeBoxes(host).firstOrNull {
            it.text.startsWith("Updated to")
        }
        lines += "narrationNodeHeightPx=" +
            "${narrNode?.let { it.bottom - it.top }}"
        assertTrue(
            "KC narration wrapped to two lines in the home seat",
            narrNode != null && narrNode.bottom - narrNode.top > 40f
        )
        assertTrue(
            "presented height is the grown wrapped height (was $presentedDp)",
            presentedDp in 64f..76f
        )
        assertTrue(
            "band == 14 + presented (was $bandDp)",
            kotlin.math.abs(bandDp - (14f + presentedDp)) <= 0.6f
        )
        assertTrue(
            "card top edge == band top edge ±2px " +
                "(card ${bounds.top}, band $bandTopPx)",
            kotlin.math.abs(bounds.top - bandTopPx) <= 2f
        )
        assertTrue(
            "card bottom + 14dp lift == screen bottom ±2px",
            kotlin.math.abs(
                bounds.bottom + 14f * density - bitmap.height
            ) <= 2f
        )
        assertTrue(
            "scroll viewport truncated exactly at the band top ±2px " +
                "(viewport $viewportBottom, band $bandTopPx)",
            kotlin.math.abs(viewportBottom - bandTopPx) <= 2f
        )
        // Painted card span on the centre column: rows matching
        // the interior composite (within tolerance) — the
        // painted body must equal the reserved card height.
        val token = tokenOf(RunWearColors.FooterGround)
        val sheet = tokenOf(RunWearColors.Surface)
        val expectedMini = composite(token, 0.85f, sheet)
        val cx = bitmap.width / 2
        var firstPainted = -1
        var lastPainted = -1
        val scanTop = (bounds.top - 4).toInt().coerceAtLeast(0)
        val scanBottom = (bounds.bottom + 4).toInt()
            .coerceAtMost(bitmap.height)
        for (y in scanTop until scanBottom) {
            if (near(rgb(bitmap.getPixel(cx, y)), expectedMini, 4f)) {
                if (firstPainted < 0) firstPainted = y
                lastPainted = y
            }
        }
        // SPAN, not a row count: narration glyphs cross the
        // centre column and break a count, but the painted
        // body's extent is first-to-last interior row.
        val paintedSpan = if (firstPainted >= 0) {
            lastPainted - firstPainted + 1
        } else {
            0
        }
        lines += "paintedInteriorSpanCentreColPx=$paintedSpan " +
            "cardHeightPx=${bounds.bottom - bounds.top}"
        assertTrue(
            "painted interior span ≈ card height ±6px " +
                "(painted $paintedSpan, card ${bounds.bottom - bounds.top})",
            kotlin.math.abs(
                paintedSpan - (bounds.bottom - bounds.top)
            ) <= 6f
        )

        // ---- (b) colour identity, mini side ------------------
        val sample = interiorSample(bitmap, bounds, 6f, density)
        lines += "miniInteriorSample=$sample expectedComposite=$expectedMini"
        assertTrue(
            "mini interior == FooterGround@0.85 over sheet ±4 " +
                "(sample $sample, expected $expectedMini)",
            near(sample, expectedMini, 4f)
        )
        // The OLD defect's mini sample (MiniBarContainer
        // #FDFBF6 @0.85 over sheet = the sheet itself) must NOT
        // match: the sample is a full tone step away.
        val oldMini = composite(
            tokenOf(RunWearColors.MiniBarContainer), 0.85f, sheet
        )
        assertTrue(
            "mini interior is NOT the retired MiniBarContainer composite",
            !near(sample, oldMini, 4f)
        )

        // ---- (c) shadow spill --------------------------------
        // Ground at the sample columns: the sheet above the
        // band. Measure darkening runs from the card's edges.
        // NOTE on geometry: only the 14dp lift strip exists
        // below the card, so the pool is measured by DEPTH
        // (max darkening) there, and by run length at a
        // material threshold (≥24 summed darkening ≈ 3%
        // shadow alpha) on the other three sides.
        var above = 0
        var y = bounds.top.toInt() - 1
        while (y >= 0 && darkening(bitmap.getPixel(cx, y), sheet) >= 24) {
            above++; y--
        }
        // Depth just above the top edge (the 2dp immediately
        // above it): only shadow + hairline live there — any
        // higher window samples page content (the fading
        // outfit item), not shadow.
        var aboveMax = 0
        for (yy in (bounds.top - 2 * density).toInt() until
            bounds.top.toInt()) {
            if (yy >= 0) {
                aboveMax = maxOf(
                    aboveMax, darkening(bitmap.getPixel(cx, yy), sheet)
                )
            }
        }
        var below = 0
        y = bounds.bottom.toInt() + 1
        while (y < bitmap.height &&
            darkening(bitmap.getPixel(cx, y), sheet) >= 24
        ) {
            below++; y++
        }
        var belowMax = 0
        for (yy in bounds.bottom.toInt() + 1 until bitmap.height) {
            belowMax = maxOf(
                belowMax, darkening(bitmap.getPixel(cx, yy), sheet)
            )
        }
        val midY = ((bounds.top + bounds.bottom) / 2f).toInt()
        var left = 0
        var x = bounds.left.toInt() - 1
        while (x >= 0 && darkening(bitmap.getPixel(x, midY), sheet) >= 24) {
            left++; x--
        }
        var right = 0
        x = bounds.right.toInt() + 1
        while (x < bitmap.width &&
            darkening(bitmap.getPixel(x, midY), sheet) >= 24
        ) {
            right++; x++
        }
        lines += "shadowSpillPx@dark24 above=$above below=$below " +
            "left=$left right=$right (density=$density)"
        lines += "shadowDepth nearEdgeAbove=$aboveMax maxBelow=$belowMax " +
            "(summed-channel darkening vs sheet)"
        lines.forEach { println("RW170-PROBE $it") }
        assertTrue(
            "shadow pools below the card (depth $belowMax, run ${below}px)",
            belowMax >= 40 && below >= 20
        )
        assertTrue(
            "no dense halo hugging the top edge " +
                "(near-edge above depth $aboveMax vs pool $belowMax)",
            aboveMax <= 60 && belowMax > aboveMax
        )
        assertTrue(
            "material spill above is a short blur tail, not a halo " +
                "(above run ${above}px)",
            above <= 16
        )
        assertTrue(
            "side spill is a short blur tail, not a halo " +
                "(left ${left}px, right ${right}px)",
            left <= 26 && right <= 26
        )

        // ---- (d) one surface, one narration ------------------
        val raw = rawNarrationCount(host)
        lines += "rawNarrationNodes=$raw"
        assertEquals("exactly one narration node at mini rest", 1, raw)
        probe("rw170-mini-rest-light-probe.txt", lines)
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeMiniRestDarkTest : Rw170HomeBase() {

    @Test
    fun `rw170 mini rest dark - band and colour probes`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = true) { Home(state, exhaustedMim()) }
        val bitmap = capture(host, "rw170-home-mini-rest-dark.png")
        val density = host.composeView.resources.displayMetrics.density
        val lines = mutableListOf<String>()
        lines += "state=MINI-REST appearance=DARK"
        val bounds = surfaceBounds(host)
        val presentedDp = (bounds.bottom - bounds.top) / density
        val bandDp = DockClearancePolicy.dockBandDp(presentedDp)
        val bandTopPx = bitmap.height - bandDp * density
        lines += "presentedMiniHeightDp=$presentedDp bandDp=$bandDp"
        lines += "cardTopPx=${bounds.top} bandTopPx=$bandTopPx"
        assertTrue(
            "dark presented height is the wrapped height (was $presentedDp)",
            presentedDp in 64f..76f
        )
        assertTrue(
            "dark band == 14 + presented (was $bandDp)",
            kotlin.math.abs(bandDp - (14f + presentedDp)) <= 0.6f
        )
        assertTrue(
            "dark card top == band top ±2px",
            kotlin.math.abs(bounds.top - bandTopPx) <= 2f
        )
        val token = tokenOf(RunWearColors.FooterGround)
        val sheet = tokenOf(RunWearColors.Surface)
        val expectedMini = composite(token, 0.82f, sheet)
        val sample = interiorSample(bitmap, bounds, 6f, density)
        lines += "darkToken=$token darkSheet=$sheet"
        lines += "miniInteriorSample=$sample expectedComposite=$expectedMini"
        assertTrue(
            "dark mini interior == FooterGround@0.82 over sheet ±4 " +
                "(sample $sample, expected $expectedMini)",
            near(sample, expectedMini, 4f)
        )
        assertEquals(
            "exactly one narration node at dark mini rest",
            1, rawNarrationCount(host)
        )
        probe("rw170-mini-rest-dark-probe.txt", lines)
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeMidScrollLightTest : Rw170HomeBase() {

    @Test
    fun `rw170 mid-scroll light - truncation holds at the presented band`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val density = host.composeView.resources.displayMetrics.density
        val bounds0 = surfaceBounds(host)
        val presentedDp = (bounds0.bottom - bounds0.top) / density
        val bandTopPx = host.composeView.height -
            DockClearancePolicy.dockBandDp(presentedDp) * density

        val node = scrollNodes(host)
            .maxByOrNull { it.boundsInRoot.height }!!
        node.config[androidx.compose.ui.semantics.SemanticsActions.ScrollBy]
            .action?.invoke(0f, 500f * density)
        advanceFrames(host, 3)
        settleReveal(host)
        val bitmap = capture(host, "rw170-home-midscroll-light.png")
        val lines = mutableListOf<String>()
        lines += "state=MID-SCROLL appearance=LIGHT scrollByPx=${500f * density}"
        val viewportBottom = scrollViewportBottomPx(host)
        lines += "viewportBottomPx=$viewportBottom bandTopPx=$bandTopPx"
        assertTrue(
            "mid-scroll viewport still truncated at the band top ±2px",
            kotlin.math.abs(viewportBottom - bandTopPx) <= 2f
        )
        // No composed list text may rest below the band top
        // (RW-147's guarantee, priced by RW-170's band).
        val sheetTexts = nodeBoxes(host).filter {
            it.bottom > bandTopPx + 2f
        }
        lines += "textNodesBelowBandTop=${sheetTexts.size} " +
            sheetTexts.take(4).map { it.text.take(24) }.toString()
        val bounds = surfaceBounds(host)
        assertTrue(
            "mini card still seated at the screen bottom mid-scroll",
            kotlin.math.abs(
                bounds.bottom + 14f * density - bitmap.height
            ) <= 2f
        )
        assertEquals(
            "exactly one narration node mid-scroll",
            1, rawNarrationCount(host)
        )
        probe("rw170-midscroll-light-probe.txt", lines)
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeExpandedLightTest : Rw170HomeBase() {

    @Test
    fun `rw170 expanded light - landmarks stable, colour identity, band unchanged`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val density = host.composeView.resources.displayMetrics.density
        val lines = mutableListOf<String>()
        lines += "state=EXPANDED appearance=LIGHT (summon at fixed scroll 0)"

        val miniBounds = surfaceBounds(host)
        val presentedDp = (miniBounds.bottom - miniBounds.top) / density
        val bandDp = DockClearancePolicy.dockBandDp(presentedDp)
        val bandTopPx = host.composeView.height - bandDp * density
        val before = landmarks(host)
        val miniSampleBmp = capture(host, "tmp-rw170-mini-for-landmarks.png")
        val miniSample = interiorSample(miniSampleBmp, miniBounds, 6f, density)
        miniSampleBmp.recycle()

        // Summon the expanded form at a FIXED scroll position.
        clickDescription(host, "Expand Wear I've Run")
        advanceFrames(host, 6)
        settleReveal(host)
        val bitmap = capture(host, "rw170-home-expanded-light.png")
        val panelBounds = surfaceBounds(host)
        lines += "presentedMiniHeightDp=$presentedDp bandDp=$bandDp"
        lines += "panelHeightDp=${(panelBounds.bottom - panelBounds.top) / density}"

        // (d) landmark stability: every text node composed in
        // both states sits at the IDENTICAL position.
        val after = landmarks(host)
        val common = before.keys intersect after.keys
        var moved = 0
        var worst = 0f
        for (k in common) {
            val d = before[k]!!.zip(after[k]!!).maxOf { (a, b) ->
                kotlin.math.abs(a - b)
            }
            if (d > worst) worst = d
            if (d > 1f) moved++
        }
        lines += "landmarksCommon=${common.size} moved=$moved worstDeltaPx=$worst"
        assertTrue("landmarks compared (was ${common.size})", common.size >= 10)
        assertEquals(
            "no page landmark moves when the surface expands " +
                "(moved $moved, worst ${worst}px)", 0, moved
        )
        // The list's item set never changes at presentation.
        lines += "itemSetBefore=${before.keys.size} itemSetAfter=${after.keys.size}"
        assertEquals(
            "composed item set identical across expansion",
            before.keys, after.keys
        )

        // (a) the band does not move when expanded.
        val viewportBottom = scrollViewportBottomPx(host)
        lines += "viewportBottomPx=$viewportBottom bandTopPx=$bandTopPx"
        assertTrue(
            "band top unchanged while expanded ±2px",
            kotlin.math.abs(viewportBottom - bandTopPx) <= 2f
        )

        // (b) colour identity across states: the expanded
        // interior is the pure token; the mini sample must be
        // that same token at 0.85 over the sheet.
        val token = tokenOf(RunWearColors.FooterGround)
        val sheet = tokenOf(RunWearColors.Surface)
        val expSample = interiorSample(bitmap, panelBounds, 6f, density)
        val expectedMini = composite(token, 0.85f, sheet)
        lines += "tokenFooterGround=$token"
        lines += "miniSample=$miniSample expandedSample=$expSample"
        lines += "expectedMiniComposite=$expectedMini"
        assertTrue(
            "expanded interior == FooterGround token ±3 " +
                "(sample $expSample, token $token)",
            near(
                expSample,
                Triple(token.first.toFloat(), token.second.toFloat(),
                    token.third.toFloat()),
                3f
            )
        )
        assertTrue(
            "mini interior == same token @0.85 over sheet ±4",
            near(miniSample, expectedMini, 4f)
        )
        assertEquals(
            "exactly one narration node at expanded rest",
            1, rawNarrationCount(host)
        )
        probe("rw170-expanded-light-probe.txt", lines)

        // Scroll end in the expanded state: the R-1 seat.
        val (scrollValue, scrollMax) = driveToEnd(host)
        settleReveal(host)
        capture(host, "rw170-home-scrollend-light.png")
        println(
            "RW170-PROBE scrollEnd value=$scrollValue max=$scrollMax"
        )
        assertTrue(
            "scroll reached the page end (value $scrollValue of $scrollMax)",
            scrollMax - scrollValue <= 1f
        )
        assertTrue(
            "the page's last line is composed at scroll end",
            texts(host).any { it.text.contains("Not what you'd wear") }
        )
        assertEquals(
            "exactly one narration node at scroll end",
            1, rawNarrationCount(host)
        )
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeExpandedDarkTest : Rw170HomeBase() {

    @Test
    fun `rw170 expanded dark - colour identity`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = true) { Home(state, exhaustedMim()) }
        val (scrollValue, scrollMax) = driveToEnd(host)
        settleReveal(host)
        val bitmap = capture(host, "rw170-home-expanded-dark.png")
        val density = host.composeView.resources.displayMetrics.density
        val bounds = surfaceBounds(host)
        val token = tokenOf(RunWearColors.FooterGround)
        val sample = interiorSample(bitmap, bounds, 6f, density)
        val panelDp = (bounds.bottom - bounds.top) / density
        val lines = listOf(
            "state=EXPANDED appearance=DARK (scroll to end, R-1)",
            "scrollValue=$scrollValue scrollMax=$scrollMax",
            "darkToken=$token expandedSample=$sample",
            "panelHeightDp=$panelDp"
        )
        lines.forEach { println("RW170-PROBE $it") }
        assertTrue(
            "scroll reached the page end (value $scrollValue of $scrollMax)",
            scrollMax - scrollValue <= 1f
        )
        assertTrue(
            "R-1 presented the EXPANDED form at the end (panel ${panelDp}dp)",
            panelDp > 150f
        )
        assertTrue(
            "dark expanded interior == FooterGround token ±3 " +
                "(sample $sample, token $token)",
            near(
                sample,
                Triple(token.first.toFloat(), token.second.toFloat(),
                    token.third.toFloat()),
                3f
            )
        )
        assertEquals(
            "exactly one narration node at dark expanded rest",
            1, rawNarrationCount(host)
        )
        probe("rw170-expanded-dark-probe.txt", lines)
        host.controller.pause().stop().destroy()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeMidMorphLightTest : Rw170HomeBase() {

    @Test
    fun `rw170 mid-morph light - one surface, one narration, landmarks stable`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = kcState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val density = host.composeView.resources.displayMetrics.density
        val miniBounds = surfaceBounds(host)
        val miniH = miniBounds.bottom - miniBounds.top
        val before = landmarks(host)

        // No-settle click + the bounded one-task pump (the
        // Rw135RebuildCaptureTest idiom): idle() completes the
        // 380ms tween in a single call in this harness, so the
        // mid-flight sample is taken when the MEASURED panel
        // height crosses ~half the mini->expanded span, driven
        // task by task through the real animation.
        clickDescriptionNoSettle(host, "Expand Wear I've Run")
        val expandedH = 494f
        val looper = org.robolectric.Shadows.shadowOf(
            android.os.Looper.getMainLooper()
        )
        var midH = miniH
        var batches = 0
        while (batches < 600) {
            repeat(5) { looper.runOneTask() }
            batches++
            // A DRAW each batch drives the frame clock (the
            // semantics tree only reflects the animation after
            // a drawn frame — measuring without drawing reads
            // the stale rest tree forever).
            val frame = capture(host, "tmp-rw170-midmorph-frame.png")
            frame.recycle()
            val b = surfaceBounds(host)
            midH = b.bottom - b.top
            val fraction = (midH - miniH) / (expandedH - miniH)
            if (fraction >= 0.4f) break
        }
        val midFraction = (midH - miniH) / (expandedH - miniH)
        val bitmap = capture(host, "rw170-home-midmorph-light.png")
        val midBounds = surfaceBounds(host)
        midH = midBounds.bottom - midBounds.top
        val lines = mutableListOf<String>()
        lines += "state=MID-MORPH appearance=LIGHT " +
            "(height fraction ${"%.2f".format(midFraction)} of span)"
        lines += "miniHeightPx=$miniH midHeightPx=$midH"
        assertTrue(
            "container height strictly between the rest heights " +
                "(mini $miniH, mid $midH, expanded $expandedH, " +
                "fraction $midFraction)",
            midH > miniH + 4f && midH < expandedH - 4f
        )
        val raw = rawNarrationCount(host)
        lines += "rawNarrationNodesMidMorph=$raw"
        assertEquals(
            "exactly ONE narration node mid-morph (no ghost copy)",
            1, raw
        )
        val after = landmarks(host)
        val common = before.keys intersect after.keys
        var moved = 0
        for (k in common) {
            val d = before[k]!!.zip(after[k]!!).maxOf { (a, b) ->
                kotlin.math.abs(a - b)
            }
            if (d > 1f) moved++
        }
        lines += "landmarksCommon=${common.size} movedMidMorph=$moved"
        assertEquals(
            "no page landmark moves mid-morph (moved $moved)", 0, moved
        )
        probe("rw170-midmorph-light-probe.txt", lines)
        host.controller.pause().stop().destroy()
        bitmap.recycle()
    }
}

@Config(qualifiers = "w390dp-h844dp-xhdpi")
class Rw170HomeWrappedBandLightTest : Rw170HomeBase() {

    @Test
    fun `rw170 wrapped narration - presented height grows, band follows`() {
        val photo = heroPhotoUrl("cool_clear_male.webp")
        val state = laPlanState(photo)
        seedExactEntry(state)
        val host = compose(dark = false) { Home(state, exhaustedMim()) }
        val bitmap = capture(host, "rw170-home-wrapped-mini-light.png")
        val density = host.composeView.resources.displayMetrics.density
        val bounds = surfaceBounds(host)
        val presentedDp = (bounds.bottom - bounds.top) / density
        val bandDp = DockClearancePolicy.dockBandDp(presentedDp)
        val bandTopPx = bitmap.height - bandDp * density
        val viewportBottom = scrollViewportBottomPx(host)
        val lines = listOf(
            "state=MINI-REST-WRAPPED appearance=LIGHT (LA plan, 2-line)",
            "presentedMiniHeightDp=$presentedDp bandDp=$bandDp",
            "cardTopPx=${bounds.top} bandTopPx=$bandTopPx " +
                "viewportBottomPx=$viewportBottom"
        )
        lines.forEach { println("RW170-PROBE $it") }
        assertTrue(
            "wrapped presented height grew past the one-line 49dp " +
                "(was $presentedDp)",
            presentedDp > 55f
        )
        assertTrue(
            "band == 14 + the GROWN presented height (was $bandDp)",
            kotlin.math.abs(bandDp - (14f + presentedDp)) <= 0.6f &&
                bandDp > 69f
        )
        assertTrue(
            "wrapped card top == band top ±2px",
            kotlin.math.abs(bounds.top - bandTopPx) <= 2f
        )
        assertTrue(
            "wrapped viewport truncated at the grown band top ±2px",
            kotlin.math.abs(viewportBottom - bandTopPx) <= 2f
        )
        assertEquals(
            "exactly one narration node in the wrapped form",
            1, rawNarrationCount(host)
        )
        probe("rw170-wrapped-light-probe.txt", lines)
        host.controller.pause().stop().destroy()
    }
}

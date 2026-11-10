package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunDock
import com.runwear.app.weariverun.WearIveRunDockNarration
import com.runwear.app.weariverun.WearIveRunDockPolicy
import com.runwear.app.weariverun.WearIveRunEntry
import com.runwear.app.weariverun.WearIveRunFooterSection
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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
 * RW-140 (Nov 8, 2026) — rendered proof for the Android dock/footer
 * restoration, after Sol's device finding on vc41: (1) the handoff
 * dissolve read as two distinct elements (its intermediate values
 * never reached the display), and (2) the footer was cropped at the
 * left edge (the compact host passed the GRID footer variant, whose
 * breakout layout shifted the section left by pagePadding in a
 * LazyColumn that has no horizontal padding to break out of).
 *
 * The harness composes the PRODUCTION dock and footer in the fixed
 * COMPACT host arrangement (LazyColumn, bottom-only content padding,
 * footer as the last item with the plain — non-breakout — content,
 * sheet gap 34dp), derives the fade through the same
 * [WearIveRunDockPolicy.fadeFromLayoutPx] the host uses, and applies
 * the dock transform in the graphicsLayer draw phase exactly as the
 * host now does. Frames are captured at measured scroll positions:
 * (a) dock at rest mid-scroll, (b) Phase 1 arrival peek (fade 0),
 * (c) mid-dissolve (fade 0.5), (d) handed off at the end (fade 1,
 * footer whole within the viewport).
 *
 * Captures land in build/render-out-rw140/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw140DockRestoreRenderTest {

    private val outDir: File = File("build/render-out-rw140")

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private val today: LocalDate = LocalDate.of(2026, 11, 8)

    private fun phoenixEntry() = WearIveRunDayEntry(
        date = today,
        place = "Phoenix, AZ",
        feelsLikeF = 58.0,
        conditionLabel = "Clear",
        isSnowing = false,
        isRaining = false,
        items = listOf(
            "Short Sleeve Shirt",
            "Running Shorts (5-7\")",
            "Reflective Gear"
        ),
        heroRef = null
    )

    private fun phoenixBarEntry() = WearIveRunEntry(
        place = "Phoenix, AZ",
        timeLabel = "Today",
        thumbnailUrl = null,
        feelsLikeF = 58.0,
        itemCount = 3,
        itemNames = listOf(
            "Short Sleeve Shirt",
            "Running Shorts (5-7\")",
            "Reflective Gear"
        )
    )

    // Test-visible handles on the harness's scroll state, assigned
    // during composition.
    private var harnessListState: LazyListState? = null
    private var harnessFadeState: State<Float>? = null

    @Composable
    private fun HandoffHarness() {
        val listState = rememberLazyListState()
        val density = LocalDensity.current
        val fadeState = remember(listState) {
            derivedStateOf {
                val info = listState.layoutInfo
                val footer = info.visibleItemsInfo.firstOrNull {
                    info.totalItemsCount > 0 &&
                        it.index == info.totalItemsCount - 1
                }
                WearIveRunDockPolicy.fadeFromLayoutPx(
                    info.viewportEndOffset,
                    footer?.offset,
                    density.density
                )
            }
        }
        harnessListState = listState
        harnessFadeState = fadeState
        val dayEntry = phoenixEntry()
        val line = WearIveRunDockNarration.line(dayEntry, today)
        val entryLine = WearIveRunDockNarration.entryLine(
            dayEntry, today,
            com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(RunWearColors.Background)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Surface),
                contentPadding = androidx.compose.foundation.layout
                    .PaddingValues(bottom = 0.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(430.dp)
                                .background(Color(0xFF9DB89A))
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "58°",
                            fontSize = 44.sp
                        )
                        Text(text = "Phoenix, AZ · Now")
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = "3 items for your run",
                            fontSize = 20.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(text = "Short Sleeve Shirt")
                        Spacer(Modifier.height(24.dp))
                        Text(text = "Running Shorts (5-7\")")
                        Spacer(Modifier.height(24.dp))
                        Text(text = "Reflective Gear")
                        Spacer(Modifier.height(28.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color(0xFFDCE8DC))
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(text = "54° low")
                        Spacer(Modifier.height(420.dp))
                    }
                }
                item {
                    Text(
                        text = "Not what you'd wear? Tell us",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
                // The footer: the page's last item, plain content —
                // the fixed COMPACT arrangement (RW-140).
                item(key = "wir-footer") {
                    WearIveRunFooterSection(
                        narration = line,
                        entry = phoenixBarEntry(),
                        entryLine = entryLine,
                        onCollapse = {},
                        onDelete = {},
                        onOpenCollection = {},
                        sheetGapDp = WearIveRunDockPolicy.FOOTER_SHEET_GAP_DP
                    )
                }
            }
            // The dock overlay, mirroring the host: the handoff
            // transform is read in the graphicsLayer draw phase.
            val fade = fadeState.value
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (fade >= 1f) {
                            Modifier.clearAndSetSemantics {}
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        val drawFade = fadeState.value
                        alpha = WearIveRunDockPolicy.dockOpacity(drawFade)
                        translationY = WearIveRunDockPolicy
                            .dockSinkDp(drawFade) * density.density
                        val s = WearIveRunDockPolicy.dockScale(drawFade)
                        scaleX = s
                        scaleY = s
                    }
            ) {
                WearIveRunDock(
                    narration = line,
                    cueKey = 0,
                    cueArmed = false,
                    onExpand = {},
                    onDelete = {},
                    hitTestable = WearIveRunDockPolicy
                        .dockHitTestable(fadeState.value),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal =
                                WearIveRunDockPolicy.DOCK_INSET_DP.dp
                        )
                        .padding(
                            bottom =
                                WearIveRunDockPolicy.DOCK_BOTTOM_DP.dp
                        )
                )
            }
        }
    }

    private fun compose(content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = false) { content() } }
        val host = Host(composeView, controller)
        pump(host, 30)
        return host
    }

    private fun pump(host: Host, times: Int) {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(times) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(50, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(wSpec, hSpec)
            view.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(frame))
            frame.recycle()
        }
    }

    private fun capture(host: Host, name: String) {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    private fun unmergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (found == null) collect(v.getChildAt(i))
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private fun texts(host: Host): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                node.config[SemanticsProperties.Text].forEach {
                    out.add(it.text.replace('\u00A0', ' '))
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return out
    }

    private fun boundsOf(host: Host, text: String): Rect? {
        var bounds: Rect? = null
        fun walk(node: SemanticsNode) {
            if (bounds == null && node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any {
                    it.text.replace('\u00A0', ' ').contains(text)
                }
            ) {
                bounds = node.boundsInRoot
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return bounds
    }

    private fun scrollNodeOf(host: Host): SemanticsNode {
        var found: SemanticsNode? = null
        fun walk(node: SemanticsNode) {
            if (found == null && node.config.contains(SemanticsActions.ScrollBy)) {
                found = node
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return found ?: throw IllegalStateException("no ScrollBy node")
    }

    /** Current a (dp): how far the footer item's top edge is above
     * the viewport's bottom edge, from the harness's layout info. */
    private fun currentADp(host: Host): Float {
        val state = harnessListState ?: error("no list state")
        val density = host.composeView.resources.displayMetrics.density
        val info = state.layoutInfo
        val footer = info.visibleItemsInfo.firstOrNull {
            info.totalItemsCount > 0 && it.index == info.totalItemsCount - 1
        } ?: return 0f
        return (info.viewportEndOffset - footer.offset) / density
    }

    private fun scrollToA(host: Host, targetADp: Float) {
        val density = host.composeView.resources.displayMetrics.density
        repeat(12) {
            val deltaDp = targetADp - currentADp(host)
            if (kotlin.math.abs(deltaDp) < 1.5f) return
            val action = scrollNodeOf(host)
                .config[SemanticsActions.ScrollBy].action
            assertNotNull("ScrollBy action", action)
            action!!.invoke(0f, deltaDp * density)
            pump(host, 8)
        }
    }

    @Test
    fun `rw140 - fade from layout px pins the spec section 3 window`() {
        // density 2.0 (xhdpi): viewportEnd 1688px; a in px = 2 x dp.
        assertEquals(0f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, null, 2f), 0.0001f)
        assertEquals(0f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, 1688, 2f), 0.0001f)
        assertEquals(0f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, 1688 - 166, 2f), 0.0001f)
        assertEquals(0.5f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, 1688 - 203, 2f), 0.0001f)
        assertEquals(1f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, 1688 - 240, 2f), 0.0001f)
        assertEquals(1f, WearIveRunDockPolicy.fadeFromLayoutPx(1688, 900, 2f), 0.0001f)
    }

    @Test
    fun `rw140 - plan narration - dock wrap point and footer wholeness`() {
        // The third-recording audit item: the Nov 10 plan entry
        // from Sol's dark-mode recording, at default type.
        val planEntry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 10),
            place = "Phoenix, AZ",
            feelsLikeF = 76.0,
            conditionLabel = "Clear",
            isSnowing = false,
            isRaining = false,
            items = listOf(
                "Visor", "Short Sleeve Shirt",
                "Running Shorts (5-7\")", "Sunglasses"
            ),
            heroRef = null
        )
        val planLine = WearIveRunDockNarration.line(planEntry, today)
        assertEquals(
            "Planned for Phoenix, AZ · Tuesday, Nov 10",
            planLine.text
        )
        val planEntryLine = WearIveRunDockNarration.entryLine(
            planEntry, today,
            com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        )
        val planBarEntry = WearIveRunEntry(
            place = "Phoenix, AZ",
            timeLabel = "Tuesday, Nov 10",
            thumbnailUrl = null,
            feelsLikeF = 76.0,
            itemCount = 4,
            itemNames = listOf(
                "Visor", "Short Sleeve Shirt",
                "Running Shorts (5-7\")", "Sunglasses"
            )
        )

        // (f) The dock with the plan narration: it wraps (the
        // two-line case, spec section 4.3) — the eyeball check on
        // the full-resolution capture is that the ONLY break is
        // before "· Tuesday, Nov 10": neither the place nor the
        // day is ever split.
        val dockHost = compose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Surface),
                contentAlignment = Alignment.BottomCenter
            ) {
                WearIveRunDock(
                    narration = planLine,
                    cueKey = 0,
                    cueArmed = false,
                    onExpand = {},
                    onDelete = {},
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 14.dp)
                )
            }
        }
        capture(dockHost, "f-dock-plan-narration.png")
        dockHost.controller.pause().stop().destroy()

        // (g) The footer with the same record: the narration sits
        // on ONE line in its own clear space, and the entry line
        // renders whole within the section.
        val footerHost = compose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Surface),
                contentAlignment = Alignment.BottomCenter
            ) {
                WearIveRunFooterSection(
                    narration = planLine,
                    entry = planBarEntry,
                    entryLine = planEntryLine,
                    onCollapse = {},
                    onDelete = {},
                    onOpenCollection = {},
                    sheetGapDp = 0
                )
            }
        }
        capture(footerHost, "g-footer-plan-whole.png")
        val widthPx = footerHost.composeView.resources
            .displayMetrics.widthPixels
        val narration = boundsOf(footerHost, "Planned for Phoenix, AZ")
        assertNotNull("footer plan narration bounds", narration)
        assertTrue(
            "plan narration whole: $narration (width $widthPx)",
            narration!!.left >= 30f &&
                narration.right <= widthPx.toFloat()
        )
        assertTrue(
            "plan narration on one line in the footer: $narration",
            narration.height < 55f
        )
        val entryBounds = boundsOf(footerHost, "Tuesday, Nov 10 — 76°F")
        assertNotNull("footer entry line bounds", entryBounds)
        assertTrue(
            "entry line whole: $entryBounds",
            entryBounds!!.left >= 30f &&
                entryBounds.right <= widthPx.toFloat()
        )
        footerHost.controller.pause().stop().destroy()
    }

    @Test
    fun `rw140 - handoff frames - dock rest, phase 1 peek, mid dissolve, handed off`() {
        val host = compose { HandoffHarness() }
        val fadeState = harnessFadeState ?: error("no fade state")

        // (a) Dock at rest, mid-scroll: scroll partway; the footer
        // is still off-screen (a <= 0), the dock fully opaque.
        scrollToA(host, -260f)
        assertEquals("fade at rest", 0f, fadeState.value, 0.0001f)
        capture(host, "a-dock-at-rest.png")

        // (b) Phase 1 arrival: the section's own top edge (item
        // top + the 34dp sheet gap) is ~7dp into the viewport; the
        // dock is still fully opaque (fade 0) — only the footer's
        // rounded top and shadow peek below it.
        scrollToA(host, 41f)
        assertEquals("fade in phase 1", 0f, fadeState.value, 0.0001f)
        capture(host, "b-phase1-peek.png")

        // (c) Mid-dissolve: a = 101.5dp is the window's midpoint —
        // smoothstep(0.5) = 0.5, dock at half opacity, sinking,
        // the footer's header and narration legible above it.
        scrollToA(host, 101.5f)
        assertTrue(
            "fade mid-dissolve: ${fadeState.value}",
            fadeState.value in 0.35f..0.65f
        )
        capture(host, "c-mid-dissolve.png")

        // (d) Handed off: scrolled to the end; fade 1, the dock
        // contributes no semantics, and the footer is WHOLE within
        // the viewport — the RW-140 crop regression check: every
        // footer element's bounds start at the section's own 16dp
        // internal inset (32px at xhdpi), never negative.
        scrollToA(host, 400f)
        assertEquals("fade handed off", 1f, fadeState.value, 0.0001f)
        capture(host, "d-handed-off-footer-whole.png")
        val widthPx = host.composeView.resources.displayMetrics.widthPixels
        val wordmark = boundsOf(host, "Wear I've Run")
        assertNotNull("footer wordmark bounds", wordmark)
        assertTrue(
            "wordmark whole: $wordmark (width $widthPx)",
            wordmark!!.left >= 30f && wordmark.right <= widthPx.toFloat()
        )
        val narration = boundsOf(host, "Updated to Phoenix, AZ")
        assertNotNull("footer narration bounds", narration)
        assertTrue(
            "narration whole: $narration",
            narration!!.left >= 30f && narration.right <= widthPx.toFloat()
        )
        val doorway = boundsOf(host, "Open the collection")
        assertNotNull("doorway bounds", doorway)
        assertTrue(
            "doorway whole: $doorway",
            doorway!!.left >= 30f && doorway.right <= widthPx.toFloat()
        )
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `rw140 - first open rest frame - dock residence measured against the weather block`() {
        // Spec section 2.1 residence audit (Sol's Nov 8, 2026
        // 8:48 AM EST device evidence): on first open, at rest, the
        // dock floats over the weather card — the HUMIDITY and UV
        // values half-legible behind it and the foot of the giant
        // numeral clipped. This frame reproduces the production
        // first-screen geometry faithfully: the REAL HeroSheetBlock
        // inside the production stacked unit (photo =
        // heroPhotoHeight(screenHeight) = 0.75 x 844 = 633dp;
        // sheet bottom-anchored, overlapping the photo's foot by
        // HeroSheetOverlap 28dp), the production dock overlay at
        // its section 2.1 rest position (BottomCenter, inset 12,
        // 14 above the bottom edge), scroll at 0.
        val weather = com.runwear.shared.domain.model.WeatherConditions(
            dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 8, 7, 0),
            temperature = 62.0,
            feelsLike = 58.0,
            humidity = 23,
            windSpeed = 3.0,
            windGusts = 6.0,
            windDirection = 180,
            precipitationProbability = 0,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 10,
            uvIndex = 3.0,
            weatherCode = com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
        )
        val now = java.time.LocalDateTime.of(2026, 11, 8, 7, 0)
        val dayEntry = phoenixEntry()
        val line = WearIveRunDockNarration.line(dayEntry, today)
        val host = compose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RunWearColors.Background)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RunWearColors.Surface)
                ) {
                    item {
                        val heroH = com.runwear.app.ui.components
                            .heroPhotoHeight(844)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroH + 248.dp - 28.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(heroH)
                                    .background(Color(0xFF9DB89A))
                            )
                            // RW-144: the sheet block is the boxless
                            // weather line now; it still carries the
                            // "3 of 11" UV and "23%" humidity values
                            // this measurement reads.
                            com.runwear.app.ui.components.HeroSheetBlock(
                                weather = weather,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                            )
                        }
                    }
                    item { Spacer(Modifier.height(900.dp)) }
                }
                // The production dock overlay at rest (fade 0).
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    WearIveRunDock(
                        narration = line,
                        cueKey = 0,
                        cueArmed = false,
                        onExpand = {},
                        onDelete = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal =
                                    WearIveRunDockPolicy.DOCK_INSET_DP.dp
                            )
                            .padding(
                                bottom =
                                    WearIveRunDockPolicy.DOCK_BOTTOM_DP.dp
                            )
                    )
                }
            }
        }
        capture(host, "h-first-open-rest.png")
        val density = host.composeView.resources
            .displayMetrics.density
        fun bottomDpOf(text: String): Float? =
            boundsOf(host, text)?.let { it.bottom / density }
        val dock = boundsOf(host, "Updated to Phoenix, AZ")
        assertNotNull("dock narration at rest", dock)
        val dockTopDp = dock!!.top / density
        // The residence measurements (printed for the lane
        // report): how far the weather block's lowest elements
        // extend BELOW the dock's top edge — i.e. how much of
        // them sits behind the translucent dock at rest.
        val uvBottom = bottomDpOf("3 of 11")
        val humidityBottom = bottomDpOf("23%")
        println(
            "RW140-REST dockTopDp=$dockTopDp " +
                "uvBottomDp=$uvBottom humidityBottomDp=$humidityBottom " +
                "viewportHDp=844"
        )
        assertNotNull("UV value bounds", uvBottom)
        assertNotNull("humidity value bounds", humidityBottom)
        host.controller.pause().stop().destroy()
    }
}

/**
 * RW-140 frame (e): the footer at the largest system font scale
 * (1.3) — every element whole within the viewport.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    application = Application::class, sdk = [35],
    qualifiers = "w390dp-h844dp-xhdpi", fontScale = 1.3f
)
class Rw140FooterFontScaleRenderTest {

    private val outDir: File = File("build/render-out-rw140")

    @Test
    fun `rw140 - footer whole at the largest font scale`() {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        val today = LocalDate.of(2026, 11, 8)
        val dayEntry = WearIveRunDayEntry(
            date = today,
            place = "Phoenix, AZ",
            feelsLikeF = 58.0,
            conditionLabel = "Clear",
            isSnowing = false,
            isRaining = false,
            items = listOf(
                "Short Sleeve Shirt",
                "Running Shorts (5-7\")",
                "Reflective Gear"
            ),
            heroRef = null
        )
        val line = WearIveRunDockNarration.line(dayEntry, today)
        val entryLine = WearIveRunDockNarration.entryLine(
            dayEntry, today,
            com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
        )
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RunWearColors.Surface),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    WearIveRunFooterSection(
                        narration = line,
                        entry = WearIveRunEntry(
                            place = "Phoenix, AZ",
                            timeLabel = "Today",
                            thumbnailUrl = null,
                            feelsLikeF = 58.0,
                            itemCount = 3,
                            itemNames = listOf(
                                "Short Sleeve Shirt",
                                "Running Shorts (5-7\")",
                                "Reflective Gear"
                            )
                        ),
                        entryLine = entryLine,
                        onCollapse = {},
                        onDelete = {},
                        onOpenCollection = {},
                        sheetGapDp = 0
                    )
                }
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(50, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "e-footer-largest-font-scale.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // Every text node of the footer sits whole within the
        // viewport at font scale 1.3.
        var root: SemanticsNode? = null
        fun collect(v: View) {
            if (root == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                root = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (root == null) collect(v.getChildAt(i))
            }
        }
        collect(composeView.rootView)
        val texts = mutableListOf<Pair<String, Rect>>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                node.config[SemanticsProperties.Text].forEach {
                    texts.add(it.text.replace('\u00A0', ' ') to node.boundsInRoot)
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(root!!)
        assertTrue("footer texts present: $texts", texts.size >= 4)
        for ((text, bounds) in texts) {
            assertTrue(
                "'$text' whole within viewport: $bounds (width $w)",
                bounds.left >= -1f && bounds.right <= w + 1f
            )
        }
        controller.pause().stop().destroy()
    }
}

/**
 * RW-140 narration display pins (third-recording audit item):
 * the plan form's day segment is one semantic unit — in the
 * display layer its spaces are non-breaking, so "Tuesday, Nov 10"
 * can never split across lines (Sol's dark-mode recording wrapped
 * it as "…Tuesday, Nov" / "10"). The only mid-line break in the
 * plan narration is before "· {day}" (spec section 4.3).
 */
class Rw140NarrationDisplayTest {

    @Test
    fun `rw140 - day segment is non-breaking in the display tail`() {
        assertEquals(
            " · Tuesday,\u00A0Nov\u00A010",
            com.runwear.app.weariverun.wirNarrationTailForDisplay(
                " · Tuesday, Nov 10"
            )
        )
        // "Tomorrow" has no internal space; the tail is unchanged
        // apart from staying whole.
        assertEquals(
            " · Tomorrow",
            com.runwear.app.weariverun.wirNarrationTailForDisplay(" · Tomorrow")
        )
        // Non-plan tails pass through untouched.
        assertEquals(
            "",
            com.runwear.app.weariverun.wirNarrationTailForDisplay("")
        )
    }

    @Test
    fun `rw140 - plan narration display text never splits place or day`() {
        RunWearColors.setActivePalette(LightPalette)
        val entry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 10),
            place = "Phoenix, AZ",
            feelsLikeF = 76.0,
            conditionLabel = "Clear",
            isSnowing = false,
            isRaining = false,
            items = listOf("Visor"),
            heroRef = null
        )
        val line = WearIveRunDockNarration.line(
            entry, LocalDate.of(2026, 11, 8)
        )
        assertEquals(
            "Planned for Phoenix, AZ · Tuesday, Nov 10",
            line.text
        )
        val display = com.runwear.app.weariverun
            .buildWirNarrationText(line).text
        // Place unit non-breaking (RW-138) and day unit
        // non-breaking (RW-140); the break before "·" stays a
        // regular space — the one ruled break point.
        assertTrue(display.contains("Phoenix,\u00A0AZ"))
        assertTrue(display.contains("Tuesday,\u00A0Nov\u00A010"))
        assertTrue(display.contains(" · Tuesday"))
        assertTrue(!display.contains("Tuesday, Nov"))
    }
}

/**
 * RW-140 source pins: the compact host takes the plain footer
 * content (never the grid breakout variant), the grid hosts keep
 * the breakout variant, and the dock's handoff transform is read
 * in the graphicsLayer draw phase from the fade state.
 */
class Rw140SourcePinTest {

    private fun heroMainSource(): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(
                dir,
                "app/src/main/java/com/runwear/app/ui/screens/HeroMainScreen.kt"
            )
            if (candidate.exists()) return candidate.readText()
            dir = dir.parentFile
        }
        throw IllegalStateException("HeroMainScreen.kt not found")
    }

    // RE-PINNED (RW-152 §1, Nov 10, 2026 — supersedes both
    // RW-140 pins below in full): the in-flow footer and the
    // layout-position handoff are RETIRED. The expanded panel is
    // a state of the dock overlay (one composable, in-place
    // morph); there is no wirFooterContent for any host, no
    // grid breakout variant, and no fade derivation in the host
    // (the pure policy functions remain only for this file's own
    // RW-140 harness host above).
    @Test
    fun `rw152 - no in-flow footer content remains in any host`() {
        val source = heroMainSource()
        assertEquals(
            "no footer content lambda is passed to any scroll host",
            0,
            Regex("wirFooterContent").findAll(source).count()
        )
        assertTrue(
            "the expanded state lives in the overlay morph",
            source.contains("\"wirDockSurfaceMorph\"")
        )
    }

    @Test
    fun `rw152 - the host derives no layout fade for the dock element`() {
        val source = heroMainSource()
        assertFalse(
            "the fadeFromLayoutPx handoff is retired in the host (RW-152 §1)",
            source.contains("WearIveRunDockPolicy.fadeFromLayoutPx")
        )
        assertTrue(
            "the dock and panel are one AnimatedContent surface",
            source.contains("AnimatedContent(")
        )
    }
}

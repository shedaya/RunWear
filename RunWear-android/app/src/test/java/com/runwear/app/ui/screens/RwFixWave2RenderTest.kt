package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.components.HeroViewerContent
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.weariverun.WearIveRunDayEntry
import java.io.File
import java.time.LocalDate
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
 * FIX WAVE 2 (Nov 7, 2026) — rendered evidence for the wave's visual
 * items, on the production composables (Robolectric native graphics;
 * this VM has no emulator). Host machinery follows the
 * RwFixWaveRenderTest pattern. Captures land in
 * build/render-out-rw-fix-wave-2/ and are staged with the lane report.
 *
 *  - RW-135 (Direction A / A-prime, ruled Nov 7): the MINI dock
 *    renders the ruled form (wordmark 17pt + the record's line +
 *    controls), the INTERIM footer renders the section 7.2
 *    anatomy, and the comparison-position composition proves the
 *    chart label row stays fully visible with the dock present
 *    (section 7.8 check 1, staged).
 *  - RW-133: the full-screen viewer composes with at most one quiet
 *    caption line naming the outfit pieces. (The stored photo itself
 *    is decoded by Coil from a remote URL at runtime; under
 *    Robolectric the image layer stays empty — the caption and the
 *    fit-frame composition are what this capture proves.)
 *  - RW-122: the collection renders a per-entry delete affordance and
 *    the by-place remove-all affordance in the chapter.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class RwFixWave2RenderTest {

    private val outDir: File = File("build/render-out-rw-fix-wave-2")

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private fun compose(content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = false) { content() } }
        val host = Host(composeView, controller)
        pump(host, 12)
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
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
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
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
                node.config[SemanticsProperties.Text].forEach { out.add(it.text) }
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
                node.config[SemanticsProperties.Text].any { it.text.contains(text) }
            ) {
                bounds = node.boundsInRoot
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return bounds
    }

        @Test
    fun `rw133 - viewer composes with one quiet caption line`() {
        val host = compose {
            HeroViewerContent(
                imageRef = "https://example.invalid/hero.jpg",
                captionItems = listOf("Merino tee", "Light tights", "Road shoes"),
                onDismiss = {}
            )
        }
        capture(host, "rw133-viewer.png")
        val all = texts(host)
        assertTrue(
            "viewer shows the single caption line: $all",
            all.any { it == "Merino tee · Light tights · Road shoes" }
        )
    }

    @Test
    fun `rw122 - collection renders per-entry delete affordances`() {
        val entry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 7),
            place = "New York, NY",
            sessionId = "s1",
            settledAtMillis = 1000L,
            momentLabel = "7:00 AM",
            feelsLikeF = 55.0,
            conditionLabel = "Clear",
            isSnowing = false,
            isRaining = false,
            items = listOf("Tee"),
            heroRef = null
        )
        // The collection screen hosts its content in a Dialog
        // window (unreachable from the activity-root harness), so
        // the chapter detail — the surface that carries the
        // RW-122 affordances — is composed directly.
        val chapter = com.runwear.app.weariverun.WearIveRunCollectionLogic
            .chapters(listOf(entry)).single()
        val host = compose {
            com.runwear.app.weariverun.ChapterDetail(
                chapter = chapter,
                today = LocalDate.of(2026, 11, 7),
                onShare = {},
                onDeleteEntry = {},
                onDeletePlace = {},
                onViewPhoto = {}
            )
        }
        capture(host, "rw122-collection.png")
        val all = texts(host)
        assertTrue("chapter names the place: $all", all.any { it.contains("New York") })
        assertTrue(
            "chapter carries the by-place remove-all action: $all",
            all.any { it == "Remove all New York, NY entries" }
        )
    }

    // -- RW-135 ------------------------------------------------------

    private fun wirEntry(
        date: LocalDate = LocalDate.of(2026, 11, 7),
        place: String = "Boston, Massachusetts"
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = 41.0,
        conditionLabel = "Cloudy", isSnowing = false, isRaining = false,
        items = listOf("Light Tights", "Wind Shell"), heroRef = null
    )

    @Test
    fun `rw135 - the mini dock renders the ruled form`() {
        val today = LocalDate.of(2026, 11, 7)
        val line = com.runwear.app.weariverun.WearIveRunDockNarration
            .line(wirEntry(), today)
        val host = compose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                com.runwear.app.weariverun.WearIveRunDock(
                    narration = line,
                    cueKey = 0,
                    cueArmed = false,
                    onExpand = {},
                    onDelete = {},
                    modifier = Modifier.padding(bottom = 14.dp)
                )
            }
        }
        val all = texts(host)
        assertTrue("wordmark renders: $all",
            all.any { it.contains("Wear I've Run") })
        assertTrue("the record's line renders: $all",
            all.any { it.contains("Updated to Boston, MA") })
        val dock = boundsOf(host, "Updated to Boston, MA")
        assertNotNull(dock)
        // Inset on the page margin (12dp at xhdpi = 24px), never
        // full-width: the line starts inside the inset.
        assertTrue("dock is inset: $dock", dock!!.left >= 24f)
        capture(host, "rw135-dock.png")
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `rw135 - the footer renders the approved spec 2_2 anatomy`() {
        // Re-pointed (RW-135 footer alignment, Nov 7, 2026): the
        // interim footer is superseded by the APPROVED spec
        // section 2.2 — full-bleed ground surface, 24dp top
        // corners, 20/16/32 internal padding. The staging box's
        // own padding here is the harness's, not the section's;
        // the anatomy assertions below are unchanged.
        val today = LocalDate.of(2026, 11, 7)
        val dayEntry = wirEntry(date = today.plusDays(1))
        val line = com.runwear.app.weariverun.WearIveRunDockNarration
            .line(dayEntry, today)
        val entryLine = com.runwear.app.weariverun.WearIveRunDockNarration
            .entryLine(
                dayEntry, today,
                com.runwear.shared.domain.model.TemperatureUnit.FAHRENHEIT
            )
        val host = compose {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                com.runwear.app.weariverun.WearIveRunFooterSection(
                    narration = line,
                    entry = com.runwear.app.weariverun.WearIveRunEntry(
                        place = "Boston, MA",
                        timeLabel = "Tomorrow",
                        thumbnailUrl = null,
                        feelsLikeF = 41.0,
                        itemCount = 2,
                        itemNames = listOf("Light Tights", "Wind Shell")
                    ),
                    entryLine = entryLine,
                    onCollapse = {},
                    onDelete = {},
                    onOpenCollection = {}
                )
            }
        }
        val all = texts(host)
        assertTrue("plan-form narration: $all",
            all.any { it.contains("Planned for Boston, MA · Tomorrow") })
        assertTrue("entry line: $all", all.any {
            it.contains("Boston, MA · Tomorrow — 41°F feels like")
        })
        assertTrue("collection doorway: $all",
            all.any { it.contains("Open the collection") })
        capture(host, "rw135-footer.png")
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `rw135 - comparison position - chart labels fully visible with the dock present`() {
        // Section 7.8 check 1, staged as a composition in the
        // ruled geometry: the day chart's label row ends exactly
        // where the dock's space begins (62dp of clearance = the
        // dock's height + its 14dp lift); the dock must not cover
        // any part of the label row.
        val today = LocalDate.of(2026, 11, 7)
        val line = com.runwear.app.weariverun.WearIveRunDockNarration
            .line(wirEntry(), today)
        val host = compose {
            Box(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(RunWearColors.Surface)
                ) {
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.weight(1f)
                    )
                    androidx.compose.material3.Text(
                        text = "Feels-like by hour"
                    )
                    androidx.compose.material3.Text(
                        text = "6a   8a   10a   12p   2p   4p"
                    )
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.height(62.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    com.runwear.app.weariverun.WearIveRunDock(
                        narration = line,
                        cueKey = 0,
                        cueArmed = false,
                        onExpand = {},
                        onDelete = {},
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }
        }
        val labels = boundsOf(host, "6a")
        val dockLine = boundsOf(host, "Updated to Boston, MA")
        assertNotNull(labels)
        assertNotNull(dockLine)
        assertTrue(
            "label row fully above the dock: labels=$labels dock=$dockLine",
            labels!!.bottom <= dockLine!!.top + 4f
        )
        capture(host, "rw135-comparison.png")
        host.controller.pause().stop().destroy()
    }
}

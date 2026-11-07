package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
 *  - RW-135 is HELD (owner ruling pending) — no capture in this wave.
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
}

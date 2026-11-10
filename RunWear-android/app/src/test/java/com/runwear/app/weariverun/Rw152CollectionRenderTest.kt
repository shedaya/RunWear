package com.runwear.app.weariverun

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWindowManagerImpl

/**
 * RW-152 §3+§4 (Nov 10, 2026) — rendered proofs for the
 * collection: thumbnails on the shipped cards (chapter covers,
 * TODAY pin), the cover trash bound to the representative entry
 * (J2), and the J1 past-entry confirmation naming place + date.
 *
 * The J1 confirmation scenario moved to its own class
 * (Rw152DeleteConfirmationRenderTest) at gate closure: as this
 * class's second activity instance in a reused sandbox its
 * composition frame work intermittently never landed (gate
 * runs 307/317/318) — one scenario per class is the repo's
 * harness rule. This class keeps the thumbnails scenario.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LEGACY looper mode, per the repo's established Dialog-window
// pattern (CollectionBackScenarioTest): under PAUSED mode the
// Dialog window's posted frame callbacks — the ones that
// recompose after a click inside the collection Dialog — do
// not reliably run in the full suite (the scenario passed
// standalone in run 305 and raced in-suite in runs 307-312);
// LEGACY executes posted work as it is posted, as a device does.
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.LEGACY
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw152CollectionRenderTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 10)

    private fun photoFile(argb: Int, tag: String): String {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(argb)
        val f = File.createTempFile("rw152-$tag", ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return "file://" + f.absolutePath
    }

    private fun entry(
        date: LocalDate,
        place: String,
        session: String,
        settledAt: Long,
        heroRef: String?
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = 62.0,
        conditionLabel = "Clear sky", isSnowing = false, isRaining = false,
        items = listOf("Long Sleeve", "Light Tights"),
        heroRef = heroRef, sessionId = session, settledAtMillis = settledAt
    )

    private fun fixtureEntries(): List<WearIveRunDayEntry> = listOf(
        entry(
            today, "New York, NY", "ny-today", 5_000,
            photoFile(0xFF2050C8.toInt(), "ny")
        ),
        entry(
            LocalDate.of(2026, 11, 8), "New York, NY", "ny-past",
            2_000, photoFile(0xFF20C860.toInt(), "nypast")
        ),
        entry(
            LocalDate.of(2026, 11, 2), "Miami, FL", "miami-new-nophoto",
            4_000, null
        ),
        entry(
            LocalDate.of(2026, 11, 2), "Miami, FL", "miami-old-photo",
            3_000, photoFile(0xFFC82020.toInt(), "miami")
        )
    )

    private class Stage(
        val activity: ComponentActivity,
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller
            .ActivityController<ComponentActivity>
    ) {
        fun settle(draws: Int = 30) {
            val w = composeView.resources.displayMetrics.widthPixels
            val h = composeView.resources.displayMetrics.heightPixels
            val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            val scheduler = Robolectric.getForegroundThreadScheduler()
            repeat(draws) {
                scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
                shadowOf(Looper.getMainLooper()).idle()
                composeView.measure(wSpec, hSpec)
                composeView.layout(0, 0, w, h)
            }
        }

        fun capture(name: String): Bitmap {
            // Draw the collection's Dialog window view (the
            // screen's real surface), falling back to the
            // activity's view.
            val target = windowViews()
                .filter { it !== activity.window.decorView }
                .maxByOrNull { it.width * it.height }
                ?: composeView
            val w = target.width.coerceAtLeast(1)
            val h = target.height.coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            target.draw(Canvas(bitmap))
            val outDir = File("build/render-out-rw152")
            outDir.mkdirs()
            File(outDir, name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            return bitmap
        }

        fun root(): SemanticsNode = mergedRoot(composeView)

        /** All window roots (the collection screen hosts its own
         * Dialog window; its content does not live in the
         * activity's ComposeView). */
        /** Measure/layout every window view (Dialog windows
         * included) — a freshly attached dialog window gets no
         * traversal from the activity's settle loop, and an
         * unlaid-out ComposeView never composes its content. */
        fun settleWindows() {
            val w = composeView.resources.displayMetrics.widthPixels
            val h = composeView.resources.displayMetrics.heightPixels
            val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
            val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            windowViews().forEach { v ->
                runCatching {
                    v.measure(wSpec, hSpec)
                    v.layout(0, 0, w, h)
                }
            }
        }

        fun windowViews(): List<View> {
            val wm = shadowOf(activity.windowManager)
                as ShadowWindowManagerImpl
            return wm.views.toList()
        }

        fun containsComposeRoot(view: View): Boolean {
            if (view is androidx.compose.ui.platform.ViewRootForTest) {
                return true
            }
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    if (containsComposeRoot(view.getChildAt(i))) return true
                }
            }
            return false
        }

        fun composeRoots(): List<SemanticsNode> =
            windowViews().mapNotNull { v ->
                if (containsComposeRoot(v)) mergedRoot(v) else null
            }

        fun allTexts(): List<String> =
            composeRoots().flatMap { texts(it) }

        fun mergedRoot(view: View): SemanticsNode {
            var found: SemanticsNode? = null
            fun collect(v: View) {
                if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                    found = v.semanticsOwner.rootSemanticsNode
                }
                if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) {
                        if (found == null) collect(v.getChildAt(i))
                    }
                }
            }
            collect(view.rootView)
            return found ?: throw IllegalStateException("no ViewRootForTest")
        }

        fun nodes(root: SemanticsNode): List<SemanticsNode> =
            listOf(root) + root.children.flatMap { nodes(it) }

        fun texts(root: SemanticsNode): List<String> =
            nodes(root).flatMap { node ->
                val out = mutableListOf<String>()
                if (node.config.contains(SemanticsProperties.Text)) {
                    out += node.config[SemanticsProperties.Text].map { it.text }
                }
                if (node.config.contains(SemanticsProperties.ContentDescription)) {
                    out += node.config[SemanticsProperties.ContentDescription]
                }
                out
            }

        fun clickDescription(root: SemanticsNode, description: String) {
            val node = nodes(root).firstOrNull {
                it.config.contains(SemanticsProperties.ContentDescription) &&
                    it.config[SemanticsProperties.ContentDescription]
                        .any { d -> d == description } &&
                    it.config.contains(SemanticsActions.OnClick)
            } ?: throw AssertionError("no clickable node '$description'")
            node.config[SemanticsActions.OnClick].action?.invoke()
                ?: throw AssertionError("OnClick missing for '$description'")
        }
    }

    private fun stage(content: @androidx.compose.runtime.Composable () -> Unit): Stage {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) { content() }
        }
        val stage = Stage(activity, composeView, controller)
        stage.settle()
        return stage
    }

    @Test
    fun `collection list renders thumbnails and a cover trash per chapter`() {
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = fixtureEntries(),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {}
            )
        }
        stage.capture("rw152-collection-thumbnails.png")
        val roots = stage.composeRoots()
        assertTrue("the collection dialog window composed", roots.isNotEmpty())
        val all = roots.flatMap { stage.texts(it) }
        val photoSlots = roots.sumOf { r -> stage.nodes(r).count {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription]
                    .any { d -> d == "Entry photo" }
        } }
        assertTrue(
            "TODAY pin + chapter covers carry photo thumbnails (found $photoSlots)",
            photoSlots >= 3
        )
        assertTrue(
            "the Miami cover carries its own trash (J2)",
            all.contains("Delete the Miami, FL cover entry")
        )
        assertTrue(
            "the New York cover carries its own trash (J2)",
            all.contains("Delete the New York, NY cover entry")
        )
        stage.controller.pause().stop().destroy()
    }

    private fun containsComposeRoot(view: View): Boolean {
        if (view is androidx.compose.ui.platform.ViewRootForTest) return true
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsComposeRoot(view.getChildAt(i))) return true
            }
        }
        return false
    }
}

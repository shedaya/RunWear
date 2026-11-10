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

    @Test
    fun `tapping a past chapter cover's trash asks first - naming place and date`() {
        val stage = stage {
            WearIveRunCollectionScreen(
                entries = fixtureEntries(),
                today = today,
                onDismiss = {},
                onDeleteEntry = {},
                onDeletePlace = {}
            )
        }
        stage.settle()
        // The repo's Dialog-window scenario pattern
        // (CollectionBackScenarioTest): drive the collection
        // through its own Dialog handle, and treat semantics
        // publishing as a bounded frame-timing wait.
        val collectionDialog = org.robolectric.shadows.ShadowDialog
            .getLatestDialog()
            ?: throw AssertionError("collection dialog not shown")
        assertTrue(collectionDialog.isShowing)

        fun dialogRoot(dialog: android.app.Dialog): SemanticsNode {
            val decor = dialog.window!!.decorView
            val candidates = mutableListOf<SemanticsNode>()
            fun collect(v: android.view.View) {
                if (v is androidx.compose.ui.platform.ViewRootForTest) {
                    candidates += v.semanticsOwner.rootSemanticsNode
                }
                if (v is android.view.ViewGroup) {
                    for (i in 0 until v.childCount) collect(v.getChildAt(i))
                }
            }
            collect(decor)
            return candidates.lastOrNull()
                ?: throw AssertionError("no compose root in dialog window")
        }

        fun findTrashNode(): SemanticsNode? =
            stage.nodes(dialogRoot(collectionDialog)).firstOrNull {
                it.config.contains(SemanticsProperties.ContentDescription) &&
                    it.config[SemanticsProperties.ContentDescription]
                        .any { d -> d == "Delete the Miami, FL cover entry" }
            }

        var trashNode: SemanticsNode? = null
        repeat(30) {
            if (trashNode == null) {
                trashNode = runCatching { findTrashNode() }.getOrNull()
                if (trashNode == null) stage.settle(5)
            }
        }
        val descNode = trashNode
            ?: throw AssertionError("cover trash description node not found")
        // The description sits on the button's icon node in the
        // unmerged tree; the OnClick lives on the button — climb
        // to the nearest clickable ancestor.
        var climber: SemanticsNode? = descNode
        var clickAction: (() -> Boolean)? = null
        while (climber != null && clickAction == null) {
            if (climber.config.contains(SemanticsActions.OnClick)) {
                clickAction = climber.config[SemanticsActions.OnClick].action
            }
            climber = climber.parent
        }
        val action = clickAction
            ?: throw AssertionError("no clickable ancestor for the cover trash")
        assertTrue("cover trash click must be accepted", action.invoke())

        // Await the confirmation as a NEW latest dialog (bounded
        // frame-timing wait, same pattern).
        var alertDialog: android.app.Dialog? = null
        repeat(30) {
            if (alertDialog == null) {
                stage.settle(5)
                val latest = org.robolectric.shadows.ShadowDialog
                    .getLatestDialog()
                if (latest != null && latest !== collectionDialog) {
                    alertDialog = latest
                }
            }
        }
        val alert = alertDialog
            ?: throw AssertionError("confirmation dialog not shown")
        val dRoot = dialogRoot(alert)
        val dTexts = stage.texts(dRoot)
        assertTrue("dialog title", dTexts.any { it.contains("Delete this entry?") })
        assertTrue(
            "dialog names place and date",
            dTexts.any {
                it.contains("Miami, FL") && it.contains("Mon, Nov 2, 2026")
            }
        )
        assertTrue(
            "dialog states on-device-only + irreversible plainly",
            dTexts.any {
                it.contains("stored only on this device") &&
                    it.contains("cannot be undone")
            }
        )
        assertTrue("Keep action", dTexts.any { it == "Keep" })
        assertTrue("Delete action", dTexts.any { it == "Delete" })

        val decor = alert.window!!.decorView
        if (decor.width == 0 || decor.height == 0) {
            val dm = decor.resources.displayMetrics
            val wSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                dm.widthPixels, android.view.View.MeasureSpec.AT_MOST
            )
            val hSpec = android.view.View.MeasureSpec.makeMeasureSpec(
                dm.heightPixels, android.view.View.MeasureSpec.AT_MOST
            )
            decor.measure(wSpec, hSpec)
            decor.layout(0, 0, decor.measuredWidth, decor.measuredHeight)
        }
        val bitmap = Bitmap.createBitmap(
            decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        decor.draw(android.graphics.Canvas(bitmap))
        val out = java.io.File(
            "build/render-out-rw152/rw152-delete-confirmation.png"
        )
        out.parentFile?.mkdirs()
        java.io.FileOutputStream(out).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
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

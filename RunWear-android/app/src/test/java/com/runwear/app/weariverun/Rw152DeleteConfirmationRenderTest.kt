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
 * RW-152 §4 / J1 (Nov 10, 2026) — rendered proof for the
 * past-entry delete confirmation: tapping a past chapter cover's
 * trash asks first, naming place and date.
 *
 * This scenario lives in its OWN class — the repo's harness rule
 * (one scenario per class; Rw144RenderTestBase: "fresh
 * Robolectric sandbox per class", and the build forks per test
 * class) — and runs in PAUSED looper mode. Both choices are the
 * gate-closure fix for its long flake (runs 305–321), diagnosed
 * locally end-to-end:
 *
 * The scenario originally shared Rw152CollectionRenderTest
 * with the thumbnails scenario under LEGACY looper mode, and
 * intermittently failed at "collection dialog not shown"
 * (gate runs 307/317/318/320) or, one step later, at
 * "confirmation dialog not shown" (run 321), while the product
 * behaviour is frame-proven
 * (frames-android/rw152-delete-confirmation.png). The failure
 * is the harness's frame delivery, not the product:
 *  - At the failure point no dialog window is attached, the
 *    foreground scheduler and the main looper are both EMPTY
 *    and idle, and neither flushForegroundThreadScheduler nor
 *    idleFor(10s) revives the pending work — under LEGACY the
 *    Compose frame callback (posted via the legacy
 *    choreographer's real-time Handler path) is LOST, not
 *    queued, so longer bounded waits cannot fix it.
 *  - The click itself is sound: a snapshot apply-observer
 *    recorded the click's state write applying (1 object), a
 *    fresh-node re-click changed nothing, and a test-owned
 *    probe state flipped after the click never recomposed
 *    either — after its first frame, the composition simply
 *    receives no further frame ticks under LEGACY.
 * Under PAUSED, frames are driven by the virtual scheduler
 * clock (ShadowPausedChoreographer): this harness's settle
 * advances that clock in bulk every round, so the frame ticks
 * that apply recompositions — the collection Dialog's show()
 * effect and the post-click AlertDialog alike — land
 * deterministically. (CollectionBackScenarioTest's documented
 * PAUSED-mode race was in its own in-suite context; this class
 * runs alone in its fork, and every read here is a bounded
 * wait over the bulk-advance settle.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw152DeleteConfirmationRenderTest {

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
                settleWindows()
                // Draw every window each round: a draw forces a
                // ComposeView traversal (pending composition +
                // effects apply) in a way a bare measure/layout
                // of the activity view does not — the same
                // per-round draw Rw144RenderTestBase.settleReveal
                // uses. The Dialog's show() and the dialog
                // window's post-click recomposition (the J1
                // AlertDialog) are exactly those effects.
                (windowViews() + composeView).distinct().forEach { v ->
                    runCatching {
                        if (v.width > 0 && v.height > 0) {
                            val frame = Bitmap.createBitmap(
                                v.width, v.height, Bitmap.Config.ARGB_8888
                            )
                            v.draw(Canvas(frame))
                            frame.recycle()
                        }
                    }
                }
            }
        }

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
        // One real traversal before the settle pump, so the
        // composition and its first layout exist before any
        // looper tasks run (mirrors a window's first frame;
        // Rw144RenderTestBase.compose's prime).
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        composeView.layout(0, 0, w, h)
        val prime = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(prime))
        prime.recycle()
        stage.settle()
        return stage
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
        var collectionDialog: android.app.Dialog? = null
        repeat(30) {
            if (collectionDialog == null) {
                stage.settle(5)
                collectionDialog = org.robolectric.shadows.ShadowDialog
                    .getLatestDialog()
            }
        }
        val collection = collectionDialog
            ?: throw AssertionError("collection dialog not shown")
        assertTrue(collection.isShowing)

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
            stage.nodes(dialogRoot(collection)).firstOrNull {
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
                if (latest != null && latest !== collection) {
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
}

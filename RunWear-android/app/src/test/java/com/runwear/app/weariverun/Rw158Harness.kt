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
import com.runwear.app.ui.theme.RunWearTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowWindowManagerImpl
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * RW-158 shared harness (user ruling, Nov 10, 2026 — a past
 * entry's tap opens the Share Studio directly).
 *
 * WHY ONE SCENARIO PER CLASS: under Robolectric's PAUSED
 * looper, a bare snapshot state write schedules its
 * recomposition through the snapshot-apply channel registered
 * during the sandbox's FIRST composition. Robolectric's
 * between-method reset invalidates that channel: from the
 * second test method in a shared class sandbox on, initial
 * compositions still render and plain-lambda assertions still
 * pass, but post-write recompositions (open a chapter, open
 * the sheet) never land. Proven on this branch: run 324 (the
 * five state-write scenarios failed in-suite after four
 * lambda-only tests passed; every scenario passes as its
 * sandbox's first method) and the RW-152 gate closure's own
 * history (the J1 scenario went green in-suite only as its
 * own class, run 323). So each RW-158 scenario that drives a
 * state write lives in its own class below, extending this
 * harness; the scenarios whose assertions need no
 * recomposition share Rw158StaticRoutingTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.LooperMode(
    org.robolectric.annotation.LooperMode.Mode.PAUSED
)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
abstract class Rw158Harness {


    protected val today: LocalDate = LocalDate.of(2026, 11, 10)

    protected fun photoFile(argb: Int, tag: String): String {
        val bmp = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(argb)
        val f = File.createTempFile("rw158-$tag", ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bmp.recycle()
        return "file://" + f.absolutePath
    }

    protected fun entry(
        date: LocalDate,
        place: String,
        session: String,
        settledAt: Long,
        heroRef: String?
    ) = WearIveRunDayEntry(
        date = date, place = place, feelsLikeF = 62.0,
        conditionLabel = "Clear sky", isSnowing = false, isRaining = false,
        items = listOf("Long Sleeve", "Light Tights"),
        heroRef = heroRef, sessionId = session, settledAtMillis = settledAt,
        faceAnchor = ShareFaceAnchor.DEFAULT_UPPER_MIDDLE
    )

    /** A Miami chapter of two PAST days (newest first on screen). */
    protected fun miamiPastEntries(): List<WearIveRunDayEntry> = listOf(
        entry(
            LocalDate.of(2026, 11, 8), "Miami, FL", "miami-nov8",
            3_000, photoFile(0xFF20C860.toInt(), "nov8")
        ),
        entry(
            LocalDate.of(2026, 11, 2), "Miami, FL", "miami-nov2",
            2_000, photoFile(0xFFC82020.toInt(), "nov2")
        )
    )

    protected class Stage(
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

    protected fun stage(content: @androidx.compose.runtime.Composable () -> Unit): Stage {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) { content() }
        }
        val stage = Stage(activity, composeView, controller)
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

    /** The activity content's semantics root (via the public
     * ViewRootForTest interface — AndroidComposeView is internal). */
    protected fun activityRoot(stage: Stage): SemanticsNode {
        val candidates = mutableListOf<SemanticsNode>()
        fun collect(v: android.view.View) {
            if (v is androidx.compose.ui.platform.ViewRootForTest) {
                candidates += v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) collect(v.getChildAt(i))
            }
        }
        collect(stage.composeView)
        return candidates.lastOrNull()
            ?: throw AssertionError("no compose root in activity window")
    }

    protected fun dialogRoot(dialog: android.app.Dialog): SemanticsNode {
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

    /** Bounded wait for a dialog that is not [known] (frame-timing
     * waits are the repo's Dialog-window scenario pattern). */
    protected fun awaitNewDialog(
        stage: Stage,
        known: android.app.Dialog
    ): android.app.Dialog? {
        var found: android.app.Dialog? = null
        repeat(40) {
            if (found == null) {
                stage.settle(5)
                val latest = org.robolectric.shadows.ShadowDialog.getLatestDialog()
                if (latest != null && latest !== known && latest.isShowing) {
                    found = latest
                }
            }
        }
        return found
    }

    protected fun awaitCollectionDialog(stage: Stage): android.app.Dialog {
        var found: android.app.Dialog? = null
        repeat(40) {
            if (found == null) {
                stage.settle(5)
                found = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            }
        }
        return found ?: throw AssertionError("collection dialog not shown")
    }

    /** Invoke the OnClick of the node carrying [description]
     * (climbing to the nearest clickable ancestor — the
     * description sits on the icon/image node, the action on its
     * button/card). */
    protected fun clickByDescription(
        stage: Stage,
        root: SemanticsNode,
        description: String
    ) {
        var node: SemanticsNode? = null
        repeat(30) {
            if (node == null) {
                node = runCatching {
                    stage.nodes(root).firstOrNull {
                        it.config.contains(SemanticsProperties.ContentDescription) &&
                            it.config[SemanticsProperties.ContentDescription]
                                .any { d -> d == description }
                    }
                }.getOrNull()
                if (node == null) stage.settle(5)
            }
        }
        val descNode = node
            ?: throw AssertionError("node not found: $description")
        var climber: SemanticsNode? = descNode
        var action: (() -> Boolean)? = null
        while (climber != null && action == null) {
            if (climber.config.contains(SemanticsActions.OnClick)) {
                action = climber.config[SemanticsActions.OnClick].action
            }
            climber = climber.parent
        }
        assertTrue(
            "click must be accepted: $description",
            (action ?: throw AssertionError("no clickable ancestor: $description"))
                .invoke()
        )
    }

    /** Invoke the click of the widest clickable node whose bounds
     * contain the centre of the node rendering [text] — a card's
     * tap target, resolved geometrically (the card merges its
     * children; its own bounds are the card's). */
    protected fun clickCardAtText(
        stage: Stage,
        root: SemanticsNode,
        text: String
    ) {
        var acted = false
        repeat(30) {
            if (!acted) {
                val all = stage.nodes(root)
                val anchor = all.firstOrNull {
                    it.config.contains(SemanticsProperties.Text) &&
                        it.config[SemanticsProperties.Text]
                            .any { t -> t.text.contains(text) }
                }
                if (anchor == null) {
                    stage.settle(5)
                } else {
                    val b = anchor.boundsInRoot
                    val cx = (b.left + b.right) / 2f
                    val cy = (b.top + b.bottom) / 2f
                    val card = all.filter {
                        it.config.contains(SemanticsActions.OnClick) &&
                            cx >= it.boundsInRoot.left && cx <= it.boundsInRoot.right &&
                            cy >= it.boundsInRoot.top && cy <= it.boundsInRoot.bottom
                    }.maxByOrNull {
                        (it.boundsInRoot.right - it.boundsInRoot.left) *
                            (it.boundsInRoot.bottom - it.boundsInRoot.top)
                    }
                    if (card == null) {
                        stage.settle(5)
                    } else {
                        assertTrue(
                            "card click must be accepted at: $text",
                            card.config[SemanticsActions.OnClick].action!!.invoke()
                        )
                        acted = true
                    }
                }
            }
        }
        if (!acted) throw AssertionError("no card click target at: $text")
    }

    /** Poll (bounded) until [needle] appears in [root]'s texts. */
    protected fun awaitText(
        stage: Stage,
        root: () -> SemanticsNode,
        needle: String,
        rounds: Int = 60
    ): Boolean {
        var seen = false
        repeat(rounds) {
            if (!seen) {
                seen = runCatching {
                    stage.texts(root()).any { it.contains(needle) }
                }.getOrDefault(false)
                if (!seen) stage.settle(5)
            }
        }
        return seen
    }

    protected fun openChapter(stage: Stage): android.app.Dialog {
        val collection = awaitCollectionDialog(stage)
        // The chapter cover is a clickable row Surface: tap it at
        // its place title (the widest clickable containing it).
        clickCardAtText(stage, dialogRoot(collection), "Miami, FL")
        stage.settle()
        return collection
    }

    // ── The ruling, surface (a): chapter day rows ─────────────

    protected fun footerStage(
        onEntryTap: (() -> Unit)?,
        counters: IntArray
    ): Stage = stage {
        WearIveRunFooterSection(
            narration = WearIveRunNarrationFormatter.added("Miami, FL", "Mon"),
            entry = WearIveRunEntry(
                place = "Miami, FL",
                timeLabel = "Mon",
                thumbnailUrl = null,
                feelsLikeF = 78.0,
                itemCount = 2
            ),
            entryLine = "Mon, Nov 2 · 78° feels like · Clear sky",
            onCollapse = {},
            onDelete = {},
            onOpenCollection = { counters[0]++ },
            onEntryTap = onEntryTap?.let { tap -> { counters[1]++; tap() } }
        )
    }
}

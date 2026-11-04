package com.runwear.app.weariverun

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * WS-9 fix (P-3, §1.6) — system Back on the Wear I've Run
 * collection, exercised at scenario level on the PRODUCTION screen.
 *
 * The collection is a full-screen Dialog whose internal navigation
 * is a stack (chapter list → chapter). Before this fix, system Back
 * from inside a chapter fired the Dialog's dismiss and dropped the
 * user straight home; §1.6 (and iOS's push navigation) step back to
 * the chapter list first. The screen now carries a BackHandler,
 * enabled only while a chapter is open, driven by
 * [CollectionBackPolicy]; at the list, Back falls through to the
 * Dialog's own dismiss.
 *
 * The presses are fired on the DIALOG's own back dispatcher — the
 * window that actually receives the key on device — and the chapter
 * is opened through the cover's real semantics click action.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class CollectionBackScenarioTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 3)

    private fun entries() = listOf(
        WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 2),
            place = "Kansas City",
            feelsLikeF = 41.0,
            conditionLabel = "Cloudy",
            isSnowing = false,
            isRaining = false,
            items = listOf("Thermal Tights", "Merino Top"),
            heroRef = null
        ),
        WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 1),
            place = "Kansas City",
            feelsLikeF = 38.0,
            conditionLabel = "Clear",
            isSnowing = false,
            isRaining = false,
            items = listOf("Thermal Tights"),
            heroRef = null
        )
    )

    @Test
    fun `the collection back policy steps back inside a chapter and dismisses only at the list`() {
        assertEquals(
            CollectionBackAction.STEP_BACK_TO_CHAPTER_LIST,
            CollectionBackPolicy.actionFor("Kansas City")
        )
        assertEquals(
            CollectionBackAction.DISMISS_COLLECTION,
            CollectionBackPolicy.actionFor(null)
        )
    }

    @Test
    fun `system Back inside a chapter returns to the chapter list and Back at the list dismisses`() {
        val dismissed = AtomicBoolean(false)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                WearIveRunCollectionScreen(
                    entries = entries(),
                    today = today,
                    onDismiss = { dismissed.set(true) }
                )
            }
        }
        settle(composeView)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("the collection must be hosted in a Dialog", dialog)
        assertTrue("the collection Dialog must be showing", dialog!!.isShowing)
        val backDispatcher = (dialog as OnBackPressedDispatcherOwner)
            .onBackPressedDispatcher

        // At the chapter list there is no in-header Back arrow.
        assertFalse(
            "at the list, no chapter Back arrow is composed",
            hasContentDescription(dialogRoot(dialog), "Back")
        )

        // Open the chapter through the cover's real click action.
        val cover = findClickableWithText(dialogRoot(dialog), "Kansas City")
        assertNotNull("the Kansas City chapter cover must be clickable", cover)
        assertTrue(
            "the cover click must be accepted",
            cover!!.config[SemanticsActions.OnClick].action?.invoke() == true
        )
        settle(composeView)

        assertTrue(
            "inside the chapter, the header Back arrow is composed",
            hasContentDescription(dialogRoot(dialog), "Back")
        )
        assertFalse("opening a chapter dismisses nothing", dismissed.get())

        // System Back inside the chapter: step back to the list —
        // the collection stays open and nothing is dismissed.
        backDispatcher.onBackPressed()
        settle(composeView)

        assertFalse(
            "Back inside a chapter must NOT dismiss the collection",
            dismissed.get()
        )
        assertTrue(
            "the collection Dialog must still be showing after the step back",
            dialog.isShowing
        )
        assertFalse(
            "after the step back, the chapter list is showing (no Back arrow)",
            hasContentDescription(dialogRoot(dialog), "Back")
        )

        // System Back at the list: the Dialog's own dismiss lands
        // home — and the activity is never finished either way.
        backDispatcher.onBackPressed()
        settle(composeView)

        assertTrue(
            "Back at the chapter list must dismiss the collection to home",
            dismissed.get()
        )
        assertFalse(
            "Back must NOT finish the activity",
            activity.isFinishing
        )
        controller.pause().stop().destroy()
    }

    private fun settle(composeView: ComposeView) {
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        composeView.measure(wSpec, hSpec)
        composeView.layout(0, 0, w, h)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(40) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    /**
     * The Dialog window's MERGED semantics root — the largest
     * merged tree among the compose views under the decor (the
     * merged tree is the one where a clickable surface and its
     * text live on the same node; which view publishes the live
     * tree can shift across recompositions, so largest wins over
     * first-found).
     */
    private fun dialogRoot(dialog: android.app.Dialog): SemanticsNode {
        val decor = dialog.window!!.decorView
        val candidates = mutableListOf<SemanticsNode>()
        fun collect(v: View) {
            if (v is ViewRootForTest) {
                candidates += v.semanticsOwner.rootSemanticsNode
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) collect(v.getChildAt(i))
            }
        }
        collect(decor)
        return candidates.maxByOrNull { nodes(it).size }
            ?: throw IllegalStateException("no ViewRootForTest in the Dialog window")
    }

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun texts(node: SemanticsNode): List<String> {
        val out = mutableListOf<String>()
        if (node.config.contains(SemanticsProperties.Text)) {
            out += node.config[SemanticsProperties.Text].map { it.text }
        }
        return out
    }

    private fun hasContentDescription(root: SemanticsNode, desc: String): Boolean =
        nodes(root).any {
            it.config.contains(SemanticsProperties.ContentDescription) &&
                it.config[SemanticsProperties.ContentDescription].any { d -> d == desc }
        }

    private fun findClickableWithText(root: SemanticsNode, text: String): SemanticsNode? =
        nodes(root).firstOrNull {
            it.config.contains(SemanticsActions.OnClick) &&
                texts(it).any { t -> t == text }
        }
}

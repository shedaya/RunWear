package com.runwear.app.makeitme

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runwear.app.ui.theme.RunWearTheme
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

/**
 * GEN-2 A1 (F-2 coverage) — system Back in Your Photos.
 *
 * The rebuild's navigation design (WS-5): Your Photos is a real
 * presentation structure — a full-screen Dialog hosted from
 * HeroMainScreen (`onDismissRequest = { showMakeItMePhotos =
 * false }`, `usePlatformDefaultWidth = false`; that host wiring
 * is pinned in SheetDisciplinePinTest) — so system Back lands on
 * the Dialog's dismiss and returns to the surface that opened it
 * (Settings/home), never finishing the activity. W-2 blocked
 * this path in the field, so the fix was never exercised; this
 * test hosts the PRODUCTION screen (the seam-carrying core
 * overload of [MakeItMePhotosScreen], with a loader that answers
 * immediately so no network is touched) in exactly that Dialog
 * pattern and fires Back on the DIALOG's own back dispatcher —
 * the window that actually receives the key on device, per the
 * CollectionBackScenarioTest precedent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.LEGACY)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class PhotosBackScenarioTest {

    @Test
    fun `system Back in Your Photos dismisses to the opening surface and does not finish the activity`() {
        val dismissed = AtomicBoolean(false)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                var showPhotos by remember { mutableStateOf(true) }
                if (showPhotos) {
                    // The production host pattern (HeroMainScreen),
                    // verbatim in shape: a full-screen Dialog whose
                    // dismiss returns to the opening surface.
                    Dialog(
                        onDismissRequest = {
                            dismissed.set(true)
                            showPhotos = false
                        },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        MakeItMePhotosScreen(
                            loadReferenceUrls = { onResult -> onResult(emptyList()) },
                            onReplaceClick = {},
                            onDismiss = {
                                dismissed.set(true)
                                showPhotos = false
                            }
                        )
                    }
                }
            }
        }
        settle(composeView)

        val dialog = ShadowDialog.getLatestDialog()
        assertNotNull("Your Photos must be hosted in a Dialog", dialog)
        assertTrue("the Your Photos Dialog must be showing", dialog!!.isShowing)
        assertTrue(
            "the Your Photos screen must be composed inside the Dialog",
            hasText(dialogRoot(dialog), MakeItMeStrings.S6_PHOTOS_ROW)
        )
        assertFalse("nothing dismissed before Back", dismissed.get())

        val backDispatcher = (dialog as OnBackPressedDispatcherOwner)
            .onBackPressedDispatcher
        backDispatcher.onBackPressed()
        repeat(30) {
            if (dismissed.get()) return@repeat
            settle(composeView)
        }

        assertTrue(
            "Back in Your Photos must dismiss it back to the opening surface",
            dismissed.get()
        )
        assertFalse(
            "Back in Your Photos must NOT finish the activity (the F-2 trap)",
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

    private fun hasText(root: SemanticsNode, text: String): Boolean =
        nodes(root).any {
            it.config.contains(SemanticsProperties.Text) &&
                it.config[SemanticsProperties.Text].any { t -> t.text == text }
        }
}

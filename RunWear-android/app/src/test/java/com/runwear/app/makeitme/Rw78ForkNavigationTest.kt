package com.runwear.app.makeitme

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-78 (owner device evidence, Nov 6, 2026) — the Make It Me
 * fork's doors navigate DIRECTLY.
 *
 * On the gen-6 candidate, Wave C's RW-52 rewire had made each door
 * only SELECT its card (a 2dp border) while the committing
 * "Continue" sat below the fold: the owner tapped the full-width
 * green "Use my photos" button and nothing happened. The RW-52
 * select-then-commit reading is superseded by that evidence; both
 * doors carry their navigation at the tap again (the gen-5 form).
 *
 * Method (the repo's proven tap-registration pattern,
 * LocationPickerTapRegistrationTest): the production
 * [MakeItMeForkScreen] is composed in a harness activity and REAL
 * MotionEvent down/up pairs are dispatched at each door button's
 * centre — the full pointer-input path, not a semantics-action
 * shortcut. Each door must fire its own navigation exactly once
 * and never the other door's. The rendered frame lands in
 * build/render-out-rw78/ as the lane's visual evidence.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h844dp-xhdpi")
class Rw78ForkNavigationTest {

    private class Host(
        val activity: ComponentActivity,
        val composeView: ComposeView,
        val widthPx: Int,
        val heightPx: Int
    )

    private fun host(
        onUseMyPhotos: () -> Unit,
        onUseStandardPhotos: () -> Unit
    ): Host {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme {
                MakeItMeForkScreen(
                    heroImageUrl = null,
                    onUseMyPhotos = onUseMyPhotos,
                    onUseStandardPhotos = onUseStandardPhotos,
                    onBack = {}
                )
            }
        }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        settle(composeView, w, h)
        return Host(activity, composeView, w, h)
    }

    /** The proven pump loop (RW-81 harness): flush, advance, idle,
     * measure, layout AND DRAW on every iteration. */
    private fun settle(composeView: ComposeView, w: Int, h: Int) {
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(15) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
            composeView.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun mergedRoot(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.unmergedRootSemanticsNode
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

    private fun nodes(root: SemanticsNode): List<SemanticsNode> =
        listOf(root) + root.children.flatMap { nodes(it) }

    private fun textNode(host: Host, text: String): SemanticsNode {
        val all = nodes(mergedRoot(host.composeView))
        return all.firstOrNull { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text == text }
        } ?: throw AssertionError("text node not found: $text")
    }

    private fun allTexts(host: Host): List<String> =
        nodes(mergedRoot(host.composeView))
            .filter { it.config.contains(SemanticsProperties.Text) }
            .flatMap { it.config[SemanticsProperties.Text].map { a -> a.text } }

    /** A real down/up pair at the centre of [text]'s node. */
    private fun tapText(host: Host, text: String) {
        val bounds = textNode(host, text).boundsInRoot
        val x = bounds.center.x
        val y = bounds.center.y
        val now = System.currentTimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 12, MotionEvent.ACTION_UP, x, y, 0)
        host.composeView.dispatchTouchEvent(down)
        host.composeView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        settle(host.composeView, host.widthPx, host.heightPx)
    }

    @Test
    fun `tapping Use my photos navigates into the photo path at the tap`() {
        val photos = AtomicInteger(0)
        val standard = AtomicInteger(0)
        val host = host({ photos.incrementAndGet() }, { standard.incrementAndGet() })

        tapText(host, "Use my photos")

        assertEquals(
            "the photos door must navigate at the tap (RW-78)",
            1, photos.get()
        )
        assertEquals("the photos door must not settle the stock choice", 0, standard.get())
    }

    @Test
    fun `tapping Use standard photos settles the stock choice at the tap`() {
        val photos = AtomicInteger(0)
        val standard = AtomicInteger(0)
        val host = host({ photos.incrementAndGet() }, { standard.incrementAndGet() })

        tapText(host, "Use standard photos")

        assertEquals(
            "the standard door must settle at the tap (RW-78)",
            1, standard.get()
        )
        assertEquals("the standard door must not open the photo path", 0, photos.get())
    }

    @Test
    fun `the fork carries no Continue commit control and renders both doors`() {
        val host = host({}, {})
        val texts = allTexts(host)
        assertTrue("photos door renders", texts.contains("Use my photos"))
        assertTrue("standard door renders", texts.contains("Use standard photos"))
        assertFalse(
            "no select-then-commit Continue may exist on the fork (RW-78)",
            texts.contains("Continue")
        )

        // Lane evidence: the fork as the user meets it.
        val outDir = File(
            System.getProperty("rw78ShotsDir") ?: "build/render-out-rw78"
        )
        outDir.mkdirs()
        val bitmap = Bitmap.createBitmap(host.widthPx, host.heightPx, Bitmap.Config.ARGB_8888)
        host.composeView.draw(Canvas(bitmap))
        File(outDir, "fork-direct-navigation.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("render written", File(outDir, "fork-direct-navigation.png").length() > 0)
    }
}

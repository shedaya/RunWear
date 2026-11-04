package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.data.repository.LocationResult
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
 * GEN-3 Y-15 PARITY CHECK (verification, not an assumed fix): on
 * iOS build 21 the location picker intermittently failed to
 * register taps on search-result rows (Juneau's row tapped
 * repeatedly across four searches, never selected). No such
 * defect was observed on Android; this test runs the same
 * repeated-tap check against the ANDROID picker's rows.
 *
 * Method: the production row composable ([LocationSearchResultRows],
 * extracted from the picker sheet unchanged — a ModalBottomSheet
 * cannot be driven to Expanded under Robolectric) is composed in
 * a harness activity, and REAL MotionEvent down/up pairs are
 * dispatched to the ComposeView at each row's center — the full
 * pointer-input path, not a semantics-action shortcut. Scenarios:
 * every row; consecutive taps on one row; a tap immediately after
 * the result set changes (the just-typed moment); two taps in
 * quick succession with no settle between them. Every tap must
 * register its selection, with the tapped row's identity.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class LocationPickerTapRegistrationTest {

    private val newYork = LocationResult(40.7127281, -74.0060152, "New York", "New York, NY", "New York, United States", isUSA = true)
    private val boston = LocationResult(42.3600825, -71.0588801, "Boston", "Boston, MA", "Massachusetts, United States", isUSA = true)
    private val chicago = LocationResult(41.8781136, -87.6297982, "Chicago", "Chicago, IL", "Illinois, United States", isUSA = true)
    private val miami = LocationResult(25.7616798, -80.1917902, "Miami", "Miami, FL", "Florida, United States", isUSA = true)

    private class Host(
        val composeView: ComposeView,
        val results: androidx.compose.runtime.MutableState<List<LocationResult>>,
        val selections: MutableList<LocationResult>
    )

    private fun composePicker(initial: List<LocationResult>): Host {
        com.runwear.app.ui.theme.RunWearColors.setActivePalette(
            com.runwear.app.ui.theme.LightPalette
        )
        val results = mutableStateOf(initial)
        val selections = mutableListOf<LocationResult>()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                Box(modifier = Modifier.width(360.dp)) {
                    LocationSearchResultRows(
                        results = results.value,
                        onSelectResult = { selections += it }
                    )
                }
            }
        }
        val host = Host(composeView, results, selections)
        pump(host, 10)
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
            scheduler.advanceBy(50, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(wSpec, hSpec)
            view.layout(0, 0, w, h)
            view.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
    }

    private fun unmergedRoot(view: View): SemanticsNode {
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

    /** Center (in view coordinates) of the row whose name text is exactly [name], or null if not composed yet. */
    private fun findRowCenter(host: Host, name: String): Offset? {
        var center: Offset? = null
        fun walk(node: SemanticsNode) {
            if (center == null && node.config.contains(SemanticsProperties.Text)) {
                if (node.config[SemanticsProperties.Text].any { it.text == name }) {
                    center = node.boundsInRoot.center
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return center
    }

    private fun rowCenter(host: Host, name: String): Offset {
        val center = findRowCenter(host, name)
        assertNotNull("row for \"$name\" must be composed", center)
        return center!!
    }

    /** A real down/up pair at (x, y), then the frames that process it. */
    private fun tap(host: Host, x: Float, y: Float, settle: Boolean = true) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 12, MotionEvent.ACTION_UP, x, y, 0)
        host.composeView.dispatchTouchEvent(down)
        host.composeView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
        if (settle) pump(host, 4)
    }

    private fun tapRow(host: Host, name: String, settle: Boolean = true) {
        val c = rowCenter(host, name)
        tap(host, c.x, c.y, settle)
    }

    @Test
    fun `every result row registers its tap`() {
        val host = composePicker(listOf(newYork, boston, chicago))
        tapRow(host, "New York")
        tapRow(host, "Boston")
        tapRow(host, "Chicago")
        assertEquals(
            listOf(newYork, boston, chicago),
            host.selections
        )
    }

    @Test
    fun `consecutive taps on the same row all register`() {
        val host = composePicker(listOf(newYork, boston))
        repeat(3) { tapRow(host, "New York") }
        assertEquals(
            "three taps on the row must be three selections (the iOS failure was zero)",
            listOf(newYork, newYork, newYork),
            host.selections
        )
    }

    @Test
    fun `a tap immediately after the results change registers the new row`() {
        val host = composePicker(listOf(boston))
        // The just-typed moment: a new search lands a new result
        // set. The tap goes in at the FIRST moment the new row is
        // composed (the minimum settle physics allows — a tap
        // cannot land on a row that has not composed yet, on any
        // platform; the iOS defect was rows that were composed and
        // visible yet never registered). The new row must compose
        // within a few frames, and the tap must then hit the NEW
        // row — not a stale one, and not nothing.
        host.results.value = listOf(miami, newYork)
        var pumps = 0
        while (findRowCenter(host, "New York") == null && pumps < 5) {
            pump(host, 1)
            pumps++
        }
        assertNotNull(
            "the new row must compose within a few frames of the results landing (took $pumps)",
            findRowCenter(host, "New York")
        )
        tapRow(host, "New York")
        assertEquals(listOf(newYork), host.selections)
    }

    @Test
    fun `taps in quick succession both register, in order`() {
        val host = composePicker(listOf(newYork, boston))
        val ny = rowCenter(host, "New York")
        val bo = rowCenter(host, "Boston")
        // Both taps dispatched back-to-back before any frame
        // processing — neither may be swallowed.
        tap(host, ny.x, ny.y, settle = false)
        tap(host, bo.x, bo.y, settle = false)
        pump(host, 6)
        assertEquals(listOf(newYork, boston), host.selections)
        assertTrue(host.selections.size == 2)
    }
}

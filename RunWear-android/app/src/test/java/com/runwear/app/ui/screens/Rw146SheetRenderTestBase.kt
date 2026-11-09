package com.runwear.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import java.io.File
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * RW-146 proof frames: the Set Location sheet under the two
 * location states the recording exercised. The sheet is a
 * ModalBottomSheet — it composes into a Dialog window, not the
 * activity's view tree — so the harness resolves the dialog's
 * decor view (Robolectric's ShadowDialog) and reads/draws THAT
 * window: the frame is the sheet as the user sees it, and the
 * semantics are the sheet's own. One class per frame, per the
 * harness rule.
 */
abstract class Rw146SheetRenderTestBase : Rw145RenderTestBase() {

    protected val recents = listOf(
        RecentLocation(name = "Phoenix, AZ", latitude = 33.45, longitude = -112.07),
        RecentLocation(name = "New York, NY", latitude = 40.71, longitude = -74.0),
        RecentLocation(name = "Seattle, WA", latitude = 47.6, longitude = -122.33),
        RecentLocation(name = "Miami, FL", latitude = 25.76, longitude = -80.19)
    )

    protected fun sheetHost(currentLocation: String, usingGps: Boolean): Host {
        return compose(dark = false) {
            LocationPickerSheet(
                currentLocation = currentLocation,
                onSearch = {},
                onSelectLocation = { _, _, _ -> },
                onDismiss = {},
                isUsingGPS = usingGps,
                hasLocationPermission = true,
                recents = recents
            )
        }
    }

    /** The sheet dialog's decor view, settled open. */
    protected fun sheetDecor(host: Host): View {
        val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            ?: throw AssertionError("the sheet dialog never appeared")
        val decor = dialog.window?.decorView
            ?: throw AssertionError("the sheet dialog has no decor view")
        val w = host.composeView.resources.displayMetrics.widthPixels
        val h = host.composeView.resources.displayMetrics.heightPixels
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, w, h)
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(frame))
            frame.recycle()
        }
        return decor
    }

    protected fun captureSheet(decor: View, name: String) {
        val w = decor.width.coerceAtLeast(1)
        val h = decor.height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    protected fun sheetBoxes(decor: View): List<NodeBox> {
        var root: SemanticsNode? = null
        fun collect(v: View) {
            if (root == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                root = v.semanticsOwner.rootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (root == null) collect(v.getChildAt(i))
                }
            }
        }
        collect(decor)
        val r = root ?: throw AssertionError("no semantics root in the sheet dialog")
        val out = mutableListOf<NodeBox>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    out += NodeBox(
                        t.text,
                        node.boundsInRoot.top, node.boundsInRoot.bottom,
                        node.boundsInRoot.left, node.boundsInRoot.right
                    )
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(r)
        return out
    }
}

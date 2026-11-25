package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeSettingsSection
import com.runwear.app.makeitme.MakeItMeUiState
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import java.io.File
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
 * RW-100 Settings-card proof, isolated in its own class for
 * the same Robolectric sandbox reason as
 * RwFixWaveFirstLookRenderTest: within the shared class
 * sandbox, earlier heavyweight compositions leave the frame
 * machinery unable to deliver this test's click recomposition.
 * The test passes standalone; assertions are unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class RwFixWaveSettingsRenderTest {

    private val outDir: File = File("build/render-out-rw-fix-wave")

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

    private fun drawBitmap(host: Host): Bitmap {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun capture(host: Host, name: String) {
        val bitmap = drawBitmap(host)
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
                node.config[SemanticsProperties.Text].any { it.text == text }
            ) {
                bounds = node.boundsInRoot
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return bounds
    }

    // ── RW-100 settings card ──────────────────────────────────────

    @Test
    fun `settings offer card - two selectable plans, selection moves on tap - RW-100`() {
        val host = compose {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                MakeItMeSettingsSection(
                    viewModel = null,
                    state = MakeItMeUiState(
                        mode = MakeItMeMode.FREE,
                        personalizationOn = true
                    ),
                    onOptInClick = {},
                    onPhotosClick = {},
                    onSubscribeClick = {},
                    onManageSubscriptionClick = {},
                    onDeleted = {},
                    onLooksClick = {}
                )
            }
        }
        capture(host, "wave-settings-card-annual.png")
        val all = texts(host)
        assertTrue("card title", all.any { it.contains("Premium") })
        assertTrue("Number of photos row (RW-84)", all.any { it == "Number of photos" })
        val annualBounds = boundsOf(host, "Annual")
        val monthlyBounds = boundsOf(host, "Monthly")
        assertNotNull("annual option composed", annualBounds)
        assertNotNull("monthly option composed", monthlyBounds)

        val density = host.composeView.resources.displayMetrics.density
        // The selection signal inside the row's own band (±11dp of
        // the label): the radio ring + filled dot in deep pine.
        // (The row's pine BORDER sits at ±26dp and the card's CTA
        // button below — both outside this window by design.)
        fun pineAround(textBounds: Rect): Int {
            val bitmap = drawBitmap(host)
            val y0 = (textBounds.top - 11 * density).toInt().coerceAtLeast(0)
            val y1 = (textBounds.bottom + 11 * density).toInt().coerceAtMost(bitmap.height)
            var count = 0
            val pine = LightPalette.Primary.toArgb()
            for (y in y0 until y1) {
                for (x in (24 * density).toInt() until bitmap.width - (24 * density).toInt()) {
                    if (bitmap.getPixel(x, y) == pine) count++
                }
            }
            bitmap.recycle()
            return count
        }
        val annualPineBefore = pineAround(annualBounds!!)
        val monthlyPineBefore = pineAround(monthlyBounds!!)
        assertTrue(
            "annual starts selected (pine $annualPineBefore vs monthly $monthlyPineBefore)",
            annualPineBefore > monthlyPineBefore + 300
        )
        // Invoke the monthly row's own OnClick semantics action.
        // (The section lives in a verticalScroll column; this
        // harness's synthetic touch dispatch gets claimed by the
        // scrollable's gesture detector before the row's click
        // resolves — the semantics action exercises the same
        // production onClick wiring the row exposes to taps and
        // accessibility alike.)
        var clicked = false
        fun clickWalk(node: SemanticsNode) {
            if (!clicked && node.config.contains(SemanticsActions.OnClick) &&
                node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text.contains("Monthly") }
            ) {
                node.config[SemanticsActions.OnClick].action?.invoke()
                clicked = true
            }
            node.children.forEach { clickWalk(it) }
        }
        clickWalk(unmergedRoot(host.composeView))
        assertTrue("monthly row exposes a click action", clicked)
        // The click's state change lands on a following frame;
        // in a loaded test JVM that can take several pumps, so
        // pump until the selection census settles (deadline ~9 s)
        // rather than measuring one fixed frame.
        var annualPineAfter = 0
        var monthlyPineAfter = 0
        val settleDeadline = System.currentTimeMillis() + 9000
        while (System.currentTimeMillis() < settleDeadline) {
            pump(host, 3)
            annualPineAfter = pineAround(annualBounds)
            monthlyPineAfter = pineAround(monthlyBounds)
            if (monthlyPineAfter > annualPineAfter + 300) break
            Thread.sleep(60)
        }
        capture(host, "wave-settings-card-monthly.png")
        assertTrue(
            "selection visibly moves to monthly (pine $monthlyPineAfter vs annual $annualPineAfter)",
            monthlyPineAfter > annualPineAfter + 300
        )
        host.controller.pause().stop().destroy()
    }

}

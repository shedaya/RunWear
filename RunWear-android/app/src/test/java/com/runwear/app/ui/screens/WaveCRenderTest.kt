package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.LooksLibraryPolicy
import com.runwear.app.makeitme.LooksLibraryScreen
import com.runwear.app.ui.components.DayChartGlanceBand
import com.runwear.app.ui.components.DayChartHour
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
import java.time.LocalDate
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
 * GEN-6 WAVE C render proofs:
 *  - DA-15(c): the day-chart glance band at the 7:00 PM selection —
 *    the axis label and "Change ›" must not overlap, and "Change ›"
 *    must sit fully inside the band (bounds asserted from the
 *    semantics tree, not eyeballed).
 *  - RW-61: the "Your looks" library with a gate-failed cell
 *    present — failed label, explanation band and the
 *    "Why am I seeing a stock photo?" door all rendered.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class WaveCRenderTest {

    private val outDir: File =
        File(System.getProperty("waveCShotsDir") ?: "build/render-out-wavec")

    private fun compose(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView {
        RunWearColors.setActivePalette(com.runwear.app.ui.theme.LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent { RunWearTheme(darkTheme = false) { content() } }
        val w = composeView.resources.displayMetrics.widthPixels
        val h = composeView.resources.displayMetrics.heightPixels
        val wSpec = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        val scheduler = Robolectric.getForegroundThreadScheduler()
        repeat(30) {
            Robolectric.flushForegroundThreadScheduler()
            scheduler.advanceBy(100, TimeUnit.MILLISECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            composeView.measure(wSpec, hSpec)
            composeView.layout(0, 0, w, h)
            composeView.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
        return composeView
    }

    private fun capture(view: ComposeView, name: String) {
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun root(view: View): SemanticsNode {
        var found: SemanticsNode? = null
        fun collect(v: View) {
            if (found == null && v is androidx.compose.ui.platform.ViewRootForTest) {
                found = v.semanticsOwner.unmergedRootSemanticsNode
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) if (found == null) collect(v.getChildAt(i))
            }
        }
        collect(view.rootView)
        return found ?: throw IllegalStateException("no ViewRootForTest")
    }

    private fun nodesWithText(view: ComposeView, text: String): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text == text }
            ) out += node
            node.children.forEach { walk(it) }
        }
        walk(root(view))
        return out
    }

    private fun allTexts(view: ComposeView): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                out += node.config[SemanticsProperties.Text].map { it.text }
            }
            node.children.forEach { walk(it) }
        }
        walk(root(view))
        return out
    }

    @Test
    fun `day chart at 7 PM - axis label and Change never collide, Change inside the band`() {
        val hours = (0..23).map { h ->
            DayChartHour(
                hour = h,
                feelsLikeF = 40.0 + h,
                rawFeelsLikeF = 40.0 + h,
                bracket = TempBracket.COOL,
                outfitItems = emptyList(),
                weatherCode = com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
            )
        }
        val model = DayChartModel(
            date = LocalDate.of(2026, 11, 5),
            hours = hours,
            low = hours.first(), high = hours.last(),
            changeHours = emptyList(), horizonLastHour = 23
        )
        val view = compose {
            Box(modifier = Modifier.fillMaxWidth()) {
                DayChartGlanceBand(
                    model = model,
                    selectedHour = 19,
                    locationToday = LocalDate.of(2026, 11, 5),
                    nowHour = 6,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    onChangeClick = {}
                )
            }
        }
        capture(view, "DA-15c-daychart-7pm-change-clear.png")
        val label = nodesWithText(view, "7:00 PM").firstOrNull()
        val change = nodesWithText(view, "Change ›").firstOrNull()
        assertNotNull("axis label composed", label)
        assertNotNull("Change composed", change)
        val labelBounds = label!!.boundsInRoot
        val changeBounds = change!!.boundsInRoot
        assertTrue(
            "label right edge ${labelBounds.right} must clear Change left edge ${changeBounds.left}",
            labelBounds.right <= changeBounds.left
        )
        val bandWidth = view.resources.displayMetrics.widthPixels.toFloat()
        assertTrue(
            "Change right edge ${changeBounds.right} must be inside the band width $bandWidth",
            changeBounds.right <= bandWidth
        )
        assertTrue("Change must not be clipped at the left either", changeBounds.left >= 0f)
    }

    @Test
    fun `hero pill stays fully on screen at phone width - DA-15b`() {
        // The widest pill state: script mark + free count + the
        // "Get unlimited" action, in a 360dp-wide hero.
        val content = com.runwear.app.ui.components.HeroPillPolicy.resolve(
            lookStatus = com.runwear.app.makeitme.PersonalLookStatus.Ready,
            serveKind = com.runwear.app.makeitme.PersonalServeKind.EXACT,
            heroIsPersonal = true,
            mode = com.runwear.app.makeitme.MakeItMeMode.FREE,
            freeGenerationsUsed = 3
        )
        assertNotNull(content)
        val view = compose {
            Box(modifier = Modifier.width(360.dp)) {
                com.runwear.app.ui.components.HeroPill(
                    content = content!!,
                    onAction = {}
                )
            }
        }
        capture(view, "DA-15b-hero-pill-on-screen.png")
        val pillTexts = allTexts(view)
        assertTrue(pillTexts.any { it.contains("Get unlimited") })
        // Every rendered text node of the pill lies within the
        // 360dp hero width (xhdpi: 720px) — nothing runs off either
        // edge.
        val widthPx = 720f
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                val b = node.boundsInRoot
                assertTrue(
                    "pill text '${node.config[SemanticsProperties.Text].firstOrNull()?.text}' " +
                        "bounds $b must be inside 0..$widthPx",
                    b.left >= 0f && b.right <= widthPx
                )
            }
            node.children.forEach { walk(it) }
        }
        walk(root(view))
    }

    @Test
    fun `looks library renders a gate-failed cell with its explanation and door`() {
        val cells = LooksLibraryPolicy.build(
            heroCells = setOf("UNISEX_CLEAR_WARM_MIDDAY_11111111"),
            jobStatuses = mapOf(
                "UNISEX_CLEAR_WARM_MIDDAY_11111111" to "COMPLETED",
                "UNISEX_SNOW_FREEZING_NIGHT_33333333" to "FAILED",
                "UNISEX_RAIN_COLD_DAWN_22222222" to "QUEUED"
            )
        )
        val view = compose {
            Box(modifier = Modifier.width(390.dp)) {
                LooksLibraryScreen(cells = cells, onDismiss = {})
            }
        }
        capture(view, "RW-61-looks-library-with-failed-cell.png")
        val texts = allTexts(view)
        assertTrue(texts.any { it == "Your looks" })
        assertTrue(texts.any { it == "Snow · Freezing" })
        assertTrue(texts.any { it == "Night · stock photo for now" })
        assertTrue(texts.any { it == "Why am I seeing a stock photo?" })
        assertTrue(
            texts.any { it.contains("didn't pass the quality gate") }
        )
    }
}

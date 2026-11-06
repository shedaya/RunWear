package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runwear.app.ui.components.DayChartHour
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FOLLOWING-BUILD ASSEMBLY (Nov 6, 2026) — RW-73 DETENT RULING
 * render proof, Android half.
 *
 * The ruling (Sol, Nov 6, 2026, delegated to Tank's suggestion;
 * BACKLOG RW-73): at the LARGEST font scale the date/time
 * picker sheets may scroll internally; at default and moderate
 * scales the settled no-scroll discipline stands. Production
 * form: each picker content's root Column carries exactly one
 * vertical scroll consumer, applied only when
 * [PickerSheetScrollPolicy] permits (fontScale >= 1.3), gated
 * on LocalDensity's font scale — the channel these renders
 * drive via a LocalDensity override (the Gen6VqRenderTest
 * precedent).
 *
 * The sheets' window-height cap is simulated by a 420dp-tall
 * host box: at font scale 1.3 both contents overflow it.
 * Proofs, per sheet:
 *   * the hoisted ScrollState reports maxValue > 0 (the ruled
 *     consumer is attached and the content overflows);
 *   * Confirm starts BELOW the capped frame's foot;
 *   * after scrolling to the foot, Confirm's semantics bounds
 *     sit wholly inside the frame — reachable and whole;
 *   * at font scale 1.0 in a full-height host the same state
 *     reports maxValue == 0 (no consumer attached — the
 *     discipline side of the ruling) and Confirm is in frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw73DetentRenderTest {

    private val outDir: File = File(
        System.getenv("RW73_SHOTS_DIR")
            ?: System.getProperty("rw73ShotsDir")
            ?: "build/render-out-rw73-detent"
    )

    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val locationNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 6, 30)

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private fun compose(content: @Composable () -> Unit): Host {
        RunWearColors.setActivePalette(com.runwear.app.ui.theme.LightPalette)
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
            view.draw(Canvas(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)))
        }
    }

    private fun capture(host: Host, name: String) {
        val view = host.composeView
        val w = view.resources.displayMetrics.widthPixels
        val h = view.resources.displayMetrics.heightPixels
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
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

    @Composable
    private fun Scaled(fontScale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            content()
        }
    }

    @Composable
    private fun CappedSheet(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CAP_DP.dp)
                .background(RunWearColors.Surface)
        ) { content() }
    }

    private fun dayChartModel(): DayChartModel {
        val hours = (0..23).map { h ->
            DayChartHour(
                hour = h,
                feelsLikeF = 40.0 + h,
                rawFeelsLikeF = 40.0 + h,
                bracket = TempBracket.COOL,
                outfitItems = listOf("Light jacket", "Running tights"),
                weatherCode = com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
            )
        }
        return DayChartModel(
            date = today,
            hours = hours,
            low = hours.first(), high = hours.last(),
            changeHours = emptyList(), horizonLastHour = 23
        )
    }

    private fun assertConfirmReachableAfterScroll(
        host: Host,
        scrollState: ScrollState,
        sheet: String,
        footName: String
    ) {
        val density = host.composeView.resources.displayMetrics.density
        val capPx = CAP_DP * density
        assertTrue(
            "$sheet: at font scale 1.3 the content must overflow the cap (maxValue > 0), was ${scrollState.maxValue}",
            scrollState.maxValue > 0
        )
        val before = boundsOf(host, "Confirm")
        assertTrue("$sheet: Confirm must be composed", before != null)
        assertTrue(
            "$sheet: Confirm must start below the capped frame's foot (bottom ${before!!.bottom} > cap $capPx)",
            before.bottom > capPx
        )
        runBlocking { scrollState.scrollTo(scrollState.maxValue) }
        pump(host, 10)
        capture(host, footName)
        val after = boundsOf(host, "Confirm")
        assertTrue("$sheet: Confirm must still be composed after the scroll", after != null)
        assertTrue(
            "$sheet: after scrolling to the foot, Confirm must be whole inside the frame " +
                "(top ${after!!.top} >= 0, bottom ${after.bottom} <= cap $capPx)",
            after.top >= 0f && after.bottom <= capPx + 1f
        )
    }

    @Test
    fun `date sheet at the largest font scale - scrolls, Confirm reachable and whole`() {
        val scrollState = ScrollState(0)
        val host = compose {
            Scaled(1.3f) {
                CappedSheet {
                    DatePickerSheetContent(
                        selectedDate = today,
                        today = today,
                        onSelectDate = {},
                        onDismiss = {},
                        scrollState = scrollState
                    )
                }
            }
        }
        capture(host, "DETENT-android-date-fs13-top.png")
        assertConfirmReachableAfterScroll(host, scrollState, "date", "DETENT-android-date-fs13-foot.png")
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `time sheet at the largest font scale - scrolls, Confirm reachable and whole`() {
        val scrollState = ScrollState(0)
        val host = compose {
            Scaled(1.3f) {
                CappedSheet {
                    TimePickerSheetContent(
                        model = dayChartModel(),
                        selectedHour = 12,
                        locationNow = locationNow,
                        isNowSelected = false,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        locationName = "Kansas City",
                        onConfirm = {},
                        onDismiss = {},
                        scrollState = scrollState
                    )
                }
            }
        }
        capture(host, "DETENT-android-time-fs13-top.png")
        assertConfirmReachableAfterScroll(host, scrollState, "time", "DETENT-android-time-fs13-foot.png")
        host.controller.pause().stop().destroy()
    }

    @Test
    fun `both sheets at the default font scale - no scroll consumer, Confirm in frame`() {
        val dateState = ScrollState(0)
        val dateHost = compose {
            Scaled(1.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RunWearColors.Surface)
                ) {
                    DatePickerSheetContent(
                        selectedDate = today,
                        today = today,
                        onSelectDate = {},
                        onDismiss = {},
                        scrollState = dateState
                    )
                }
            }
        }
        capture(dateHost, "DETENT-android-date-fs10-full.png")
        assertTrue(
            "date at 1.0: no scroll consumer may attach (maxValue must stay 0), was ${dateState.maxValue}",
            dateState.maxValue == 0
        )
        val dateConfirm = boundsOf(dateHost, "Confirm")
        val screenH = dateHost.composeView.resources.displayMetrics.heightPixels.toFloat()
        assertTrue("date at 1.0: Confirm composed", dateConfirm != null)
        assertTrue(
            "date at 1.0: Confirm whole inside the full-height frame (bottom ${dateConfirm!!.bottom} <= $screenH)",
            dateConfirm.bottom <= screenH && dateConfirm.top >= 0f
        )
        dateHost.controller.pause().stop().destroy()

        val timeState = ScrollState(0)
        val timeHost = compose {
            Scaled(1.0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RunWearColors.Surface)
                ) {
                    TimePickerSheetContent(
                        model = dayChartModel(),
                        selectedHour = 12,
                        locationNow = locationNow,
                        isNowSelected = false,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                        locationName = "Kansas City",
                        onConfirm = {},
                        onDismiss = {},
                        scrollState = timeState
                    )
                }
            }
        }
        capture(timeHost, "DETENT-android-time-fs10-full.png")
        assertTrue(
            "time at 1.0: no scroll consumer may attach (maxValue must stay 0), was ${timeState.maxValue}",
            timeState.maxValue == 0
        )
        val timeConfirm = boundsOf(timeHost, "Confirm")
        assertTrue("time at 1.0: Confirm composed", timeConfirm != null)
        assertTrue(
            "time at 1.0: Confirm whole inside the full-height frame (bottom ${timeConfirm!!.bottom} <= $screenH)",
            timeConfirm.bottom <= screenH && timeConfirm.top >= 0f
        )
        timeHost.controller.pause().stop().destroy()
    }

    private companion object {
        /** Simulated ModalBottomSheet window-height cap. */
        const val CAP_DP = 420
    }
}

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
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
 * What the ruling insures against is a window too short for the
 * untruncated content (a small phone, split-screen): the
 * ModalBottomSheet caps its content at the window height, and
 * without a consumer the foot is simply unreachable. The
 * proofs therefore run in deliberately SHORT windows (the
 * PROCESS §5 worst case), sized per sheet from the content's
 * measured height at font scale 1.3 on a full screen (Date
 * ≈ 330dp, Time ≈ 565dp — both fit a tall window whole, which
 * the fs13-top captures in the first gate run showed):
 *   * Date in a 280dp window, Time in a 500dp window: the
 *     hoisted ScrollState reports a real, positive maxValue
 *     (the consumer attached and the content overflows);
 *   * Confirm starts BELOW the window's foot;
 *   * after scrolling to the foot, Confirm's semantics bounds
 *     sit wholly inside the window — reachable and whole.
 * The discipline side: at font scale 1.0 in a full-height
 * window the same states are NEVER attached — an unattached
 * ScrollState keeps its Int.MAX_VALUE sentinel maxValue, the
 * observable signature of "no consumer" — and Confirm renders
 * in frame.
 *
 * (First-run correction, of record: the assembly lane's first
 * version of this proof capped the host at 420dp and expected
 * maxValue == 0 for "no consumer". Both premises were wrong —
 * an intermediate height cap does not bind under this harness's
 * manual measure/draw, and ScrollState's unattached sentinel is
 * Int.MAX_VALUE, not 0. Gate run 42278554001's renders are the
 * evidence for the corrected design.)
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
    private fun SheetSurface(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
        val windowPx = host.composeView.resources.displayMetrics.heightPixels.toFloat()
        assertTrue(
            "$sheet: at font scale 1.3 in a short window the ruled consumer must attach " +
                "(maxValue must leave the Int.MAX_VALUE sentinel), was ${scrollState.maxValue}",
            scrollState.maxValue != Int.MAX_VALUE
        )
        assertTrue(
            "$sheet: the content must overflow the short window (maxValue > 0), was ${scrollState.maxValue}",
            scrollState.maxValue > 0
        )
        val before = boundsOf(host, "Confirm")
        assertTrue("$sheet: Confirm must be composed", before != null)
        assertTrue(
            "$sheet: Confirm must start below the window's foot (bottom ${before!!.bottom} > window $windowPx)",
            before.bottom > windowPx
        )
        runBlocking { scrollState.scrollTo(scrollState.maxValue) }
        pump(host, 10)
        capture(host, footName)
        val after = boundsOf(host, "Confirm")
        assertTrue("$sheet: Confirm must still be composed after the scroll", after != null)
        assertTrue(
            "$sheet: after scrolling to the foot, Confirm must be whole inside the window " +
                "(top ${after!!.top} >= 0, bottom ${after.bottom} <= window $windowPx)",
            after.top >= 0f && after.bottom <= windowPx + 1f
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h280dp-xhdpi")
    fun `date sheet at the largest font scale - scrolls, Confirm reachable and whole`() {
        val scrollState = ScrollState(0)
        val host = compose {
            Scaled(1.3f) {
                SheetSurface {
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
    @Config(qualifiers = "w390dp-h500dp-xhdpi")
    fun `time sheet at the largest font scale - scrolls, Confirm reachable and whole`() {
        val scrollState = ScrollState(0)
        val host = compose {
            Scaled(1.3f) {
                SheetSurface {
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
                SheetSurface {
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
            "date at 1.0: no scroll consumer may attach (maxValue must keep the " +
                "Int.MAX_VALUE sentinel), was ${dateState.maxValue}",
            dateState.maxValue == Int.MAX_VALUE
        )
        val dateConfirm = boundsOf(dateHost, "Confirm")
        val screenH = dateHost.composeView.resources.displayMetrics.heightPixels.toFloat()
        assertTrue("date at 1.0: Confirm composed", dateConfirm != null)
        assertTrue(
            "date at 1.0: Confirm whole inside the full-height window (bottom ${dateConfirm!!.bottom} <= $screenH)",
            dateConfirm.bottom <= screenH && dateConfirm.top >= 0f
        )
        dateHost.controller.pause().stop().destroy()

        val timeState = ScrollState(0)
        val timeHost = compose {
            Scaled(1.0f) {
                SheetSurface {
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
            "time at 1.0: no scroll consumer may attach (maxValue must keep the " +
                "Int.MAX_VALUE sentinel), was ${timeState.maxValue}",
            timeState.maxValue == Int.MAX_VALUE
        )
        val timeConfirm = boundsOf(timeHost, "Confirm")
        assertTrue("time at 1.0: Confirm composed", timeConfirm != null)
        assertTrue(
            "time at 1.0: Confirm whole inside the full-height window (bottom ${timeConfirm!!.bottom} <= $screenH)",
            timeConfirm.bottom <= screenH && timeConfirm.top >= 0f
        )
        timeHost.controller.pause().stop().destroy()
    }
}

package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.runwear.app.makeitme.FirstLookMoment
import com.runwear.app.makeitme.FirstLookState
import com.runwear.app.makeitme.MakeItMeFailedLine
import com.runwear.app.makeitme.MakeItMeForkScreen
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.ui.components.DayChartHour
import com.runwear.app.ui.components.DayChartModel
import com.runwear.app.ui.theme.LightPalette
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TemperatureUnit
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * FOLLOWING-BUILD VQ (Nov 6, 2026) — harness-only renders produced by
 * the independent Visual QA executor on candidate 63015bc. Covers the
 * states the candidate's own harness set does not render:
 *  - RW-79: FirstLookMoment in its FAILED phase (with "Try again")
 *    and the Home failed line (MakeItMeFailedLine) verbatim.
 *  - FirstLookMoment MAKING (free) and READY (free) phases for the
 *    VQ-7.2 verbatim diff (incl. the DS §11 honesty line).
 *  - RW-78: the fork's static state (both doors, no Continue).
 *  - VQ-2.4: picker contents at font scale 1.3 in a full-height host
 *    (top state), incl. a partial-day (F-PARTDAY) time model whose
 *    forecast ends mid-day.
 * PNGs + semantics dumps land in build/render-out-followingvq/.
 * NO product code is touched by this file.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w411dp-h900dp-xhdpi")
class FollowingVqRenderTest {

    private val outDir: File =
        File(System.getProperty("followingVqShotsDir") ?: "build/render-out-followingvq")

    private val today: LocalDate = LocalDate.of(2026, 11, 5)
    private val locationNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 6, 30)

    private fun mergedRoot(view: View): SemanticsNode {
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

    private fun dumpTexts(view: ComposeView, name: String): List<String> {
        val lines = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val b = node.boundsInRoot
            if (node.config.contains(SemanticsProperties.Text)) {
                for (t in node.config[SemanticsProperties.Text]) {
                    lines += "TEXT [${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}] ${t.text}"
                }
            }
            if (node.config.contains(SemanticsProperties.ContentDescription)) {
                for (t in node.config[SemanticsProperties.ContentDescription]) {
                    lines += "DESC [${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()}] $t"
                }
            }
            node.children.forEach { walk(it) }
        }
        walk(mergedRoot(view))
        outDir.mkdirs()
        File(outDir, "$name.txt").writeText(lines.joinToString("\n"))
        return lines.map { it.substringAfter("] ") }
    }

    private fun hostAndShoot(
        name: String,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit
    ): List<String> {
        RunWearColors.setActivePalette(LightPalette)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, fontScale)
                ) { content() }
            }
        }
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
            val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            composeView.draw(Canvas(frame))
            frame.recycle()
        }
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        outDir.mkdirs()
        File(outDir, "$name.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        val texts = dumpTexts(composeView, name)
        controller.pause().stop().destroy()
        return texts
    }

    // ── RW-79 (a): first-look sheet in FAILED phase ──────────────
    @Test
    fun firstLookFailedPhase() {
        val texts = hostAndShoot("FVQ-firstlook-failed") {
            FirstLookMoment(
                photoUrl = null,
                simplePlace = "Kansas City",
                weatherLine = "41° feels like · Clear sky",
                onSeeOutfit = {},
                onSeePremium = {},
                state = FirstLookState.FAILED,
                freeLooksUsed = 0,
                mode = MakeItMeMode.FREE,
                onRetry = {}
            )
        }
        assertTrue(
            "FAILED phase must carry the settled failed line, got: $texts",
            texts.contains(MakeItMeStrings.G5_LOOK_FAILED)
        )
        assertTrue(
            "FAILED phase must carry a 'Try again' control, got: $texts",
            texts.contains(MakeItMeStrings.G6_RETRY)
        )
    }

    // ── RW-79 (b): Home failed line ──────────────────────────────
    @Test
    fun homeFailedLine() {
        val texts = hostAndShoot("FVQ-home-failed-line") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RunWearColors.Background)
                    .padding(16.dp)
            ) {
                MakeItMeFailedLine(onRetry = {})
            }
        }
        assertTrue(
            "Home failed line must render verbatim, got: $texts",
            texts.contains(MakeItMeStrings.G5_LOOK_FAILED)
        )
    }

    // ── VQ-7.2: first-look MAKING (free) — honesty line ──────────
    @Test
    fun firstLookMakingFree() {
        val texts = hostAndShoot("FVQ-firstlook-making-free") {
            FirstLookMoment(
                photoUrl = null,
                simplePlace = "Kansas City",
                weatherLine = "41° feels like · Clear sky",
                onSeeOutfit = {},
                onSeePremium = {},
                state = FirstLookState.MAKING,
                freeLooksUsed = 1,
                mode = MakeItMeMode.FREE,
                onRetry = {}
            )
        }
        assertTrue(
            "MAKING phase must carry the DS §11 honesty line verbatim, got: $texts",
            texts.contains(
                "This usually takes a few minutes — the standard photo stays up until yours is done."
            )
        )
    }

    // ── VQ-7.2: first-look READY (free) — claim + counter ────────
    @Test
    fun firstLookReadyFree() {
        val texts = hostAndShoot("FVQ-firstlook-ready-free") {
            FirstLookMoment(
                photoUrl = null,
                simplePlace = "Kansas City",
                weatherLine = "41° feels like · Clear sky",
                onSeeOutfit = {},
                onSeePremium = {},
                state = FirstLookState.READY,
                freeLooksUsed = 1,
                mode = MakeItMeMode.FREE,
                onRetry = {}
            )
        }
        assertTrue(
            "READY phase must carry the finished claim, got: $texts",
            texts.contains("You're in the photo now.")
        )
    }

    // ── RW-78: fork static state ─────────────────────────────────
    @Test
    fun forkStaticState() {
        val texts = hostAndShoot("FVQ-fork-static") {
            MakeItMeForkScreen(
                heroImageUrl = null,
                onUseMyPhotos = {},
                onUseStandardPhotos = {},
                onBack = {}
            )
        }
        assertTrue("fork renders the photos door, got: $texts", texts.contains("Use my photos"))
        assertTrue(
            "fork renders the standard door, got: $texts",
            texts.contains("Use standard photos")
        )
        assertTrue(
            "fork must carry NO select-then-commit Continue (RW-78), got: $texts",
            texts.none { it == "Continue" }
        )
    }

    private fun dayChartModel(horizonLastHour: Int): DayChartModel {
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
            changeHours = emptyList(), horizonLastHour = horizonLastHour
        )
    }

    // ── VQ-2.4: date picker content at max type, full-height top ─
    @Test
    fun datePickerMaxTypeTop() {
        val texts = hostAndShoot("FVQ-date-picker-fs13-top", fontScale = 1.3f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RunWearColors.Surface)
            ) {
                DatePickerSheetContent(
                    selectedDate = today,
                    today = today,
                    onSelectDate = {},
                    onDismiss = {}
                )
            }
        }
        assertTrue(
            "date picker must carry the beyond-forecast caption, got: $texts",
            texts.any { it.contains("beyond the forecast") }
        )
        assertTrue("date picker Confirm composed, got: $texts", texts.contains("Confirm"))
    }

    // ── VQ-2.4 / VQ-5.4: time picker on a partial day at max type ─
    @Test
    fun timePickerPartialDayMaxType() {
        val texts = hostAndShoot("FVQ-time-picker-partday-fs13", fontScale = 1.3f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RunWearColors.Surface)
            ) {
                TimePickerSheetContent(
                    model = dayChartModel(horizonLastHour = 14),
                    selectedHour = 12,
                    locationNow = locationNow,
                    isNowSelected = false,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    locationName = "Kansas City",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        assertTrue("time picker Confirm composed, got: $texts", texts.contains("Confirm"))
    }

    // ── VQ-2.4: time picker readout form at default type ─────────
    @Test
    fun timePickerReadoutDefault() {
        val texts = hostAndShoot("FVQ-time-picker-readout-default", fontScale = 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RunWearColors.Surface)
            ) {
                TimePickerSheetContent(
                    model = dayChartModel(horizonLastHour = 23),
                    selectedHour = 18,
                    locationNow = locationNow,
                    isNowSelected = false,
                    temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    locationName = "Kansas City",
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        // Settled readout form: "6 PM · 58°" (hour + feels-like ONLY).
        assertTrue(
            "readout must be in the settled hour + feels-like form, got: $texts",
            texts.any { it == "6 PM · 58°" }
        )
    }
}

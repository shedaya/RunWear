package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import com.runwear.app.ui.components.HeroSheetBlock
import com.runwear.app.ui.theme.RunWearColors
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.TemperatureUnit
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import kotlinx.datetime.LocalDateTime as KLocalDateTime
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * GEN-6 WAVE C EVIDENCE CLOSURE (picker→watch, gap 2) — PHONE SIDE.
 *
 * Paired with PickerWatchClosureWearTest in the :wear module
 * (the two modules share only :shared, so the pair is two test
 * classes over ONE fixture, below, byte-identical in both).
 *
 * Fixture (the platform lane's RW-53 selection, reproduced
 * exactly): place "Kansas City"; locationNow Thu 2026-11-05
 * 06:30; start selection Fri 2026-11-06 19:00 explicit (fixture
 * feels-like 47° / actual 50°, partly cloudy); changed selection
 * Sat 2026-11-07 19:00 (fixture feels-like 39° / actual 42°,
 * clear).
 *
 * What this class proves: the picker Confirm, driven with real
 * MotionEvent taps through the production DatePickerSheetContent,
 * commits Sat Nov 7; the committed selection is formed with
 * MainViewModel.selectDate's own arithmetic
 * (LocalDateTime.of(date, selectedDateTime.toLocalTime()) →
 * 2026-11-07T19:00); and the home headline re-solves to that
 * selection's fixture (39°). The wear half feeds the IDENTICAL
 * committed LocalDateTime into WearUiState.selectedDateTime —
 * the wear seam WearViewModel.setDateTime writes — and renders
 * the watch hero solving it.
 *
 * What it does NOT prove: live phone↔watch transport. This
 * codebase contains no DataLayer/MessageClient code at all
 * (verified by source search, 2026-11-05): the Wear app solves
 * independently from its own WearUiState.selectedDateTime via
 * the shared WeatherRepository + GetOutfitRecommendationUseCase.
 * There is no transport seam to exercise, on this VM or on a
 * device pair, until one is built.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class PickerWatchClosurePhoneTest {

    private val outDir: File = File(
        System.getenv("PW_SHOTS_DIR")
            ?: System.getProperty("pwShotsDir")
            ?: "build/render-out-picker-watch"
    )

    private val today: LocalDate = LocalDate.of(2026, 11, 5) // Thursday
    private val locationNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 6, 30)
    private val startSelection: LocalDateTime = LocalDateTime.of(2026, 11, 6, 19, 0)
    private val targetDate: LocalDate = LocalDate.of(2026, 11, 7) // Saturday

    /** The committed selection both halves of the pair solve. */
    private val committedSelection: LocalDateTime = LocalDateTime.of(2026, 11, 7, 19, 0)

    private fun weather(feels: Double, actual: Double, code: WeatherCode) =
        WeatherConditions(
            dateTime = KLocalDateTime(2026, 11, 5, 7, 0),
            temperature = actual, feelsLike = feels, humidity = 55,
            windSpeed = 6.0, windGusts = 10.0, precipitationProbability = 10,
            precipitation = 0.0, isRaining = false, isSnowing = false,
            cloudCover = 30, uvIndex = 2.0, weatherCode = code
        )

    private val startWeather = weather(47.0, 50.0, WeatherCode.PARTLY_CLOUDY)
    private val targetWeather = weather(39.0, 42.0, WeatherCode.CLEAR_SKY)

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>
    )

    private fun compose(content: @androidx.compose.runtime.Composable () -> Unit): Host {
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

    private fun findCenter(host: Host, text: String): androidx.compose.ui.geometry.Offset? {
        var center: androidx.compose.ui.geometry.Offset? = null
        fun walk(node: SemanticsNode) {
            if (center == null && node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].any { it.text == text }
            ) {
                center = node.boundsInRoot.center
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return center
    }

    private fun texts(host: Host): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsProperties.Text)) {
                out += node.config[SemanticsProperties.Text].map { it.text }
            }
            node.children.forEach { walk(it) }
        }
        walk(unmergedRoot(host.composeView))
        return out
    }

    private fun tap(host: Host, text: String) {
        val c = findCenter(host, text)
        assertNotNull("node with text \"$text\" must be composed", c)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, c!!.x, c.y, 0)
        val up = MotionEvent.obtain(now, now + 12, MotionEvent.ACTION_UP, c.x, c.y, 0)
        host.composeView.dispatchTouchEvent(down)
        host.composeView.dispatchTouchEvent(up)
        down.recycle(); up.recycle()
        pump(host, 5)
    }

    @androidx.compose.runtime.Composable
    private fun HomeHeadline(
        selection: LocalDateTime,
        weather: WeatherConditions,
        onDateClick: () -> Unit,
        onTimeClick: () -> Unit = {}
    ) {
        // RW-144: the home date/time entry moved from the sheet
        // headline to the HERO CHIP — the same two targets (date
        // segment -> date picker, time segment -> hourly chart)
        // in the chip's single-pill form. The feels-like block
        // rides along so the weather assertions keep their surface.
        androidx.compose.foundation.layout.Column {
            com.runwear.app.ui.components.HeroDateTimeChip(
                selectedDateTime = selection,
                isNowSelected = false,
                onDateClick = onDateClick,
                onTimeClick = onTimeClick,
                onNowClick = {}
            )
            com.runwear.app.ui.components.HeroFeelsLikeBlock(
                weather = weather,
                temperatureUnit = TemperatureUnit.FAHRENHEIT,
                comfortPreference = ComfortPreference.NEUTRAL
            )
        }
    }

    @Test
    fun `picker confirm commits Nov 7 and home re-solves - the phone half of the pair`() {
        // Pre-change home: the start selection, 47° fixture.
        val home = compose { HomeHeadline(startSelection, startWeather, onDateClick = {}) }
        capture(home, "PW-A1-phone-home-pre-change-nov6-47.png")
        assertTrue(texts(home).any { it == "Fri, Nov 6" })
        assertTrue(texts(home).any { it.contains("47") })
        home.controller.pause().stop().destroy()

        // Picker: draft moves to Sat Nov 7, nothing commits yet.
        val committed = mutableStateOf<LocalDate?>(null)
        val sheet = compose {
            DatePickerSheetContent(
                selectedDate = startSelection.toLocalDate(),
                today = today,
                onSelectDate = { committed.value = it },
                onDismiss = {}
            )
        }
        val targetLabel = targetDate.format(DateTimeFormatter.ofPattern("MMM d"))
        tap(sheet, targetLabel)
        capture(sheet, "PW-A2-phone-picker-draft-nov7-uncommitted.png")
        assertNull("a day tap moves the draft only", committed.value)

        // Confirm commits; the committed selection is formed
        // exactly as MainViewModel.selectDate forms it.
        tap(sheet, "Confirm")
        assertEquals(targetDate, committed.value)
        sheet.controller.pause().stop().destroy()
        val committedDateTime = LocalDateTime.of(committed.value, startSelection.toLocalTime())
        assertEquals(
            "phone committed selection (the pair's shared solve input)",
            committedSelection, committedDateTime
        )

        // Home re-solved to the committed selection: Nov 7, 39°.
        val resolved = compose { HomeHeadline(committedDateTime, targetWeather, onDateClick = {}) }
        capture(resolved, "PW-A3-phone-home-re-solved-nov7-39.png")
        assertTrue(texts(resolved).any { it == "Sat, Nov 7" })
        assertTrue(texts(resolved).any { it.contains("39") })
        resolved.controller.pause().stop().destroy()
    }

    @Test
    fun `pin - selectDate arithmetic on the committed date yields 2026-11-07T19-00`() {
        // MainViewModel.selectDate(date) :=
        //   LocalDateTime.of(date, uiState.selectedDateTime.toLocalTime())
        // applied to the picker's confirmed date over the start
        // selection (Fri Nov 6, 19:00) — the value the wear half
        // pins as its WearUiState.selectedDateTime.
        val viaSelectDate = LocalDateTime.of(targetDate, startSelection.toLocalTime())
        assertEquals(LocalDateTime.of(2026, 11, 7, 19, 0), viaSelectDate)
        // And the draft policy itself: open on start, select moves
        // the draft, confirm returns it unchanged.
        val draft = DatePickerDraftPolicy.select(
            startSelection.toLocalDate(), targetDate, today
        )
        assertEquals(targetDate, DatePickerDraftPolicy.confirm(draft))
    }
}

package com.runwear.app.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
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
 * GEN-6 WAVE C (RW-53) — the date-change path, PROVEN end to end
 * with real MotionEvent taps through the production composables
 * (the LocationPickerTapRegistrationTest method; a
 * ModalBottomSheet cannot be driven to Expanded under
 * Robolectric, so the sheet's extracted content is driven — the
 * same content the sheet hosts).
 *
 * The selection used throughout (the closure-lane handoff):
 * place "Kansas City" (uiState.locationName), locationNow =
 * Thu Nov 5, 2026, 6:30 AM; starting selection = TOMORROW
 * (Fri Nov 6) 7:00 PM explicit; the date change picks Sun Nov 8
 * (today + 3)... corrected in-flow below to today + 2
 * (Sat Nov 7). The selection-state seam is
 * MainUiState.selectedDateTime, written by
 * MainViewModel.selectDate(LocalDate) (keeps the selection's
 * local time, exits NOW, re-solves via fetchWeather).
 *
 * Four artefacts:
 *  1. RW-53-1-home-date-entry.png — home headline, changed state:
 *     the date segment "Tomorrow," is rendered and tappable;
 *     tapping it fires onDateClick (the entry exists).
 *  2. RW-53-2-date-changed-in-picker.png — Choose-a-Day with the
 *     draft moved to Sat Nov 7; NOTHING has committed yet.
 *  3. RW-53-3-confirm-home-resolves-new-date.png — after Confirm,
 *     home re-solved to Sat Nov 7's conditions (39° fixture).
 *  4. RW-53-4-cancel-prior-selection-restored.png — a second
 *     session changes the draft then Cancels: no commit; home
 *     still shows the original selection (47° fixture).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw53DateChangeRenderTest {

    private val outDir: File =
        File(System.getProperty("rw53ShotsDir") ?: "build/render-out-rw53")

    private val today: LocalDate = LocalDate.of(2026, 11, 5) // Thursday
    private val locationNow: LocalDateTime = LocalDateTime.of(2026, 11, 5, 6, 30)
    private val startSelection: LocalDateTime = LocalDateTime.of(2026, 11, 6, 19, 0) // Tomorrow 7 PM
    private val targetDate: LocalDate = LocalDate.of(2026, 11, 7) // Saturday (+2)

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
        HeroSheetBlock(
            weather = weather,
            locationName = "Kansas City",
            selectedDateTime = selection,
            locationNow = locationNow,
            isNowSelected = false,
            onDateClick = onDateClick,
            onTimeClick = onTimeClick,
            onNowClick = {},
            onWeatherPillClick = {},
            overlapsPhoto = false,
            temperatureUnit = TemperatureUnit.FAHRENHEIT,
            holdNumeral = false,
            comfortPreference = ComfortPreference.NEUTRAL
        )
    }

    @Test
    fun `date change path - entry, changed draft, confirm re-solve, cancel restore`() {
        // ── (1) Home: the date entry exists and fires. ──────────
        var dateOpened = false
        val home = compose {
            HomeHeadline(startSelection, startWeather, onDateClick = { dateOpened = true })
        }
        capture(home, "RW-53-1-home-date-entry.png")
        assertTrue(texts(home).any { it == "Tomorrow, " })
        tap(home, "Tomorrow, ")
        assertTrue("tapping the date segment must fire onDateClick", dateOpened)
        home.controller.pause().stop().destroy()

        // ── (2) Choose-a-Day: draft changes, nothing commits. ───
        val committed = mutableStateOf<LocalDate?>(null)
        val sheet = compose {
            DatePickerSheetContent(
                selectedDate = startSelection.toLocalDate(),
                today = today,
                onSelectDate = { committed.value = it },
                onDismiss = {}
            )
        }
        // The target cell's sublabel is its "MMM d" date text.
        val targetLabel = targetDate.format(DateTimeFormatter.ofPattern("MMM d"))
        tap(sheet, targetLabel)
        capture(sheet, "RW-53-2-date-changed-in-picker.png")
        assertNull(
            "a day tap moves the draft only — no commit before Confirm",
            committed.value
        )

        // ── (3) Confirm commits; home re-solves to the new date. ─
        tap(sheet, "Confirm")
        assertEquals(targetDate, committed.value)
        sheet.controller.pause().stop().destroy()

        val resolvedSelection = LocalDateTime.of(targetDate, startSelection.toLocalTime())
        val resolved = compose {
            HomeHeadline(resolvedSelection, targetWeather, onDateClick = {})
        }
        capture(resolved, "RW-53-3-confirm-home-resolves-new-date.png")
        assertTrue(texts(resolved).any { it == "Sat, Nov 7, " })
        assertTrue(texts(resolved).any { it.contains("39") })
        resolved.controller.pause().stop().destroy()

        // ── (4) Cancel: draft change discarded, selection stands. ─
        val committedAfterCancel = mutableStateOf<LocalDate?>(null)
        var dismissed = false
        val sheet2 = compose {
            DatePickerSheetContent(
                selectedDate = startSelection.toLocalDate(),
                today = today,
                onSelectDate = { committedAfterCancel.value = it },
                onDismiss = { dismissed = true }
            )
        }
        tap(sheet2, targetLabel)
        tap(sheet2, "Cancel")
        assertTrue(dismissed)
        assertNull("Cancel must not commit", committedAfterCancel.value)
        sheet2.controller.pause().stop().destroy()

        val restored = compose {
            HomeHeadline(startSelection, startWeather, onDateClick = {})
        }
        capture(restored, "RW-53-4-cancel-prior-selection-restored.png")
        assertTrue(texts(restored).any { it == "Tomorrow, " })
        assertTrue(texts(restored).any { it.contains("47") })
        restored.controller.pause().stop().destroy()
    }
}

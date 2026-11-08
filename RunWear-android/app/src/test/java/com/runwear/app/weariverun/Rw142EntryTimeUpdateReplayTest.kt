package com.runwear.app.weariverun

import android.app.Application
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.runwear.app.ui.screens.HeroMainScreen
import com.runwear.app.ui.theme.RunWearTheme
import com.runwear.app.ui.viewmodel.LocationSearchState
import com.runwear.app.ui.viewmodel.MainUiState
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime as KotlinLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RW-142 (Nov 8, 2026) — the owner's device sequence, replayed
 * against the production [HeroMainScreen] and the REAL
 * [WearIveRunStore] (Robolectric; this VM has no emulator):
 *
 * Phoenix, at Now (58° feels-like, MILD kit) the session's single
 * Wear I've Run entry records the Now solve. A confirmed 3 p.m.
 * pick for TODAY (77° feels-like, WARM kit) must update that same
 * single entry IN PLACE — its recorded conditions and outfit become
 * the picked hour's, under the "3 p.m." moment label. A confirmed
 * pick on a future day (Tue Nov 10, 3 p.m., 76°) moves the same
 * entry to that day as its plan. Between a confirm and its fetch
 * landing, the in-flight state (selection written, previous solve
 * still on screen) must record NOTHING — the DA-09 same-solve
 * discipline the recording gate exists for. A further pick whose
 * write is immediately churned into the next pick's refused
 * in-flight window must still land (Phase D), and a cancelled
 * picker excursion — which commits nothing — must leave the
 * recorded entry byte-identical (Phase F; the draft-level cancel
 * itself is pinned by TimePickerDraftTest).
 *
 * ONE composition drives the whole sequence on purpose: the
 * preferences DataStore is process-wide, and in this harness a
 * second activity composition in the same process does not
 * record (a Robolectric artefact — the Application is swapped
 * under a process-wide store delegate — not product behaviour;
 * see the RW-142 lane report).
 *
 * The store is read through a second [WearIveRunStore] over the
 * same application context: the preferences DataStore delegate is
 * process-wide, so both wrappers read the one file the composable
 * writes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class Rw142EntryTimeUpdateReplayTest {

    private val engine = GetOutfitRecommendationUseCase()

    private val today: LocalDate = LocalDate.of(2026, 11, 8)
    private val futureDay: LocalDate = LocalDate.of(2026, 11, 10)
    private val nowMoment: LocalDateTime = LocalDateTime.of(2026, 11, 8, 7, 29)
    private val pickToday: LocalDateTime = LocalDateTime.of(2026, 11, 8, 15, 0)
    private val pickFuture: LocalDateTime = LocalDateTime.of(2026, 11, 10, 15, 0)

    private fun weather(moment: LocalDateTime, tempF: Double, feelsF: Double, uv: Double) =
        WeatherConditions(
            dateTime = KotlinLocalDateTime(
                moment.year, moment.monthValue, moment.dayOfMonth,
                moment.hour, moment.minute
            ),
            temperature = tempF,
            feelsLike = feelsF,
            humidity = 30,
            windSpeed = 2.0,
            windGusts = 4.0,
            precipitationProbability = 0,
            precipitation = 0.0,
            isRaining = false,
            isSnowing = false,
            cloudCover = 5,
            uvIndex = uv,
            weatherCode = WeatherCode.CLEAR_SKY
        )

    // UV carries the accessory rules (visor / sunglasses at the
    // picked hour — the owner's recording shows exactly that kit
    // change), so the morning Now solve carries morning UV and the
    // 3 p.m. solves carry full Phoenix afternoon sun.
    private val nowWeather = weather(nowMoment, 59.0, 58.0, uv = 2.0)
    private val pickWeather = weather(pickToday, 80.0, 77.0, uv = 8.0)
    private val futureWeather = weather(pickFuture, 80.0, 76.0, uv = 8.0)

    private fun state(
        selected: LocalDateTime,
        isNow: Boolean,
        w: WeatherConditions
    ) = MainUiState(
        isLoading = false,
        error = null,
        outfit = engine.execute(w, ComfortPreference.NEUTRAL),
        weather = w,
        locationName = "Phoenix, AZ",
        selectedDateTime = selected,
        locationNow = nowMoment,
        isNowSelected = isNow,
        heroImageUrl = null,
        hasCompletedOnboarding = true
    )

    private class Host(
        val composeView: ComposeView,
        val controller: org.robolectric.android.controller.ActivityController<ComponentActivity>,
        val store: WearIveRunStore
    )

    private fun compose(initial: MainUiState): Pair<Host, (MainUiState) -> Unit> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        val activity = controller.create().start().resume().visible().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        // The preferences DataStore is process-wide and Robolectric
        // reuses the sandbox across test methods: empty the shared
        // collection first so each method starts from a clean store.
        val cleaningStore = WearIveRunStore(activity.applicationContext)
        runBlocking {
            cleaningStore.entriesFlow.first()
                .forEach { cleaningStore.removeByIdentity(it) }
            check(cleaningStore.entriesFlow.first().isEmpty()) {
                "store cleanup failed"
            }
        }
        var current by mutableStateOf(initial)
        composeView.setContent {
            RunWearTheme(darkTheme = false) {
                HeroMainScreen(
                    uiState = current,
                    locationSearchState = LocationSearchState(),
                    onRefresh = {},
                    onToggleUnit = {},
                    onSetComfortPreference = {},
                    onSetGenderPreference = {},
                    onDateSelected = {},
                    onTimeSelected = {},
                    onPickerCommit = { _, _, _ -> },
                    onResetToNow = {},
                    onLocationSearch = {},
                    onLocationSelect = { _, _, _ -> }
                )
            }
        }
        val host = Host(
            composeView, controller,
            WearIveRunStore(activity.applicationContext)
        )
        pump(host, 12)
        return host to { next -> current = next }
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
        }
    }

    private fun entries(host: Host): List<WearIveRunDayEntry> =
        runBlocking { host.store.entriesFlow.first() }

    /** Pump + poll until [predicate] holds for the stored entries. */
    private fun awaitEntries(
        host: Host,
        description: String,
        timeoutMs: Long = 30_000,
        predicate: (List<WearIveRunDayEntry>) -> Boolean
    ): List<WearIveRunDayEntry> {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last: List<WearIveRunDayEntry> = emptyList()
        while (System.currentTimeMillis() < deadline) {
            pump(host, 4)
            last = entries(host)
            if (predicate(last)) return last
            Thread.sleep(50)
        }
        fail("Timed out waiting for: $description; last entries: $last")
        return last // unreachable — fail() always throws
    }

    @Test
    fun `confirmed hour pick updates the session entry in place`() {
        val nowKit = engine.execute(nowWeather, ComfortPreference.NEUTRAL)
            .allItems.map { it.displayName }
        val pickKit = engine.execute(pickWeather, ComfortPreference.NEUTRAL)
            .allItems.map { it.displayName }
        val futureKit = engine.execute(futureWeather, ComfortPreference.NEUTRAL)
            .allItems.map { it.displayName }
        assertNotEquals(
            "the replay needs the Now and 3 p.m. kits to differ",
            nowKit, pickKit
        )

        val (host, setState) = compose(state(nowMoment, isNow = true, w = nowWeather))
        val sessionId = WearIveRunSession.currentId()

        // Phase A — at Now, the session's entry records the Now solve.
        val atNow = awaitEntries(host, "the Now entry to be recorded") {
            it.size == 1 && it[0].momentLabel == null
        }
        with(atNow[0]) {
            assertEquals(sessionId, this.sessionId)
            assertEquals(today, date)
            assertEquals("Phoenix, AZ", place)
            assertEquals(58.0, feelsLikeF, 0.001)
            assertEquals(nowKit, items)
        }

        // Phase B1 — the confirm's in-flight window: the selection
        // is written (3 p.m. today) while the Now solve is still on
        // screen. The same-solve gate must refuse: the entry keeps
        // the Now record, never the old solve under the new label.
        setState(state(pickToday, isNow = false, w = nowWeather))
        pump(host, 20)
        Thread.sleep(300)
        val inFlight = entries(host)
        assertEquals(1, inFlight.size)
        assertEquals(null, inFlight[0].momentLabel)
        assertEquals(nowKit, inFlight[0].items)

        // Phase B2 — the 3 p.m. solve commits. THE RW-142
        // ASSERTION: the same single entry now records the picked
        // hour's conditions and outfit, under its moment label.
        setState(state(pickToday, isNow = false, w = pickWeather))
        val atPick = awaitEntries(host, "the entry to take the 3 p.m. solve") {
            it.size == 1 && it[0].momentLabel == "3 p.m."
        }
        with(atPick[0]) {
            assertEquals(sessionId, this.sessionId)
            assertEquals(today, date)
            assertEquals("Phoenix, AZ", place)
            assertEquals(77.0, feelsLikeF, 0.001)
            assertEquals(pickKit, items)
        }

        // Phase C1 — the future-day confirm's in-flight window:
        // selection on Nov 10 while today's 3 p.m. solve is still
        // on screen. Nothing may move.
        setState(state(pickFuture, isNow = false, w = pickWeather))
        pump(host, 20)
        Thread.sleep(300)
        val futureInFlight = entries(host)
        assertEquals(1, futureInFlight.size)
        assertEquals(today, futureInFlight[0].date)
        assertEquals("3 p.m.", futureInFlight[0].momentLabel)
        assertEquals(pickKit, futureInFlight[0].items)

        // Phase C2 — the Nov 10 solve commits: the same single
        // entry is now that day's plan, carrying the picked hour's
        // outfit for that day.
        setState(state(pickFuture, isNow = false, w = futureWeather))
        val atFuture = awaitEntries(host, "the entry to become the Nov 10 plan") {
            it.size == 1 && it[0].date == futureDay
        }
        with(atFuture[0]) {
            assertEquals(sessionId, this.sessionId)
            assertEquals("Phoenix, AZ", place)
            assertEquals("3 p.m.", momentLabel)
            assertEquals(76.0, feelsLikeF, 0.001)
            assertEquals(futureKit, items)
        }

        // Phase D — the churn leg: a further pick (Wed Nov 11,
        // 9 a.m.) commits and the selection churns IMMEDIATELY into
        // the next pick's refused in-flight window (Thu Nov 12
        // selected, Nov 11's solve still on screen). The admitted
        // Nov 11 write must still land: on the owner's device the
        // window between a confirm and its write is filled with
        // exactly this kind of churn, and a pick — unlike Now,
        // which re-solves continuously — is never re-offered by a
        // later solve of the same moment.
        val churnDay: LocalDate = LocalDate.of(2026, 11, 11)
        val churnMoment: LocalDateTime = LocalDateTime.of(2026, 11, 11, 9, 0)
        val churnWeather = weather(churnMoment, 66.0, 64.0, uv = 5.0)
        val churnKit = engine.execute(churnWeather, ComfortPreference.NEUTRAL)
            .allItems.map { it.displayName }
        val nextDay: LocalDate = LocalDate.of(2026, 11, 12)
        val nextMoment: LocalDateTime = LocalDateTime.of(2026, 11, 12, 9, 0)
        val nextWeather = weather(nextMoment, 65.0, 63.0, uv = 5.0)
        val nextKit = engine.execute(nextWeather, ComfortPreference.NEUTRAL)
            .allItems.map { it.displayName }

        setState(state(churnMoment, isNow = false, w = churnWeather))
        pump(host, 1)
        setState(state(nextMoment, isNow = false, w = churnWeather))
        val atChurn = awaitEntries(host, "the Nov 11 write to survive the churn") {
            it.size == 1 && it[0].date == churnDay
        }
        with(atChurn[0]) {
            assertEquals(sessionId, this.sessionId)
            assertEquals("9 a.m.", momentLabel)
            assertEquals(64.0, feelsLikeF, 0.001)
            assertEquals(churnKit, items)
        }

        // Phase E — the Nov 12 solve commits; the same single
        // entry becomes that day's plan.
        setState(state(nextMoment, isNow = false, w = nextWeather))
        val atNext = awaitEntries(host, "the entry to become the Nov 12 plan") {
            it.size == 1 && it[0].date == nextDay
        }
        with(atNext[0]) {
            assertEquals(sessionId, this.sessionId)
            assertEquals("9 a.m.", momentLabel)
            assertEquals(63.0, feelsLikeF, 0.001)
            assertEquals(nextKit, items)
        }

        // Phase F — the cancel shape: a picker excursion that is
        // cancelled commits NOTHING (the draft never reaches
        // uiState — TimePickerDraftPolicy.cancel, pinned at policy
        // level by TimePickerDraftTest). Holding the committed
        // state across further frames must leave the entry exactly
        // as Phase E recorded it.
        pump(host, 30)
        Thread.sleep(300)
        val afterCancelShape = entries(host)
        assertEquals(1, afterCancelShape.size)
        assertEquals(atNext[0], afterCancelShape[0])

        host.controller.pause().stop().destroy()
    }

}

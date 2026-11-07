package com.runwear.app

import com.runwear.app.makeitme.ConvergenceBandPolicy
import com.runwear.app.makeitme.MakeItMeMode
import com.runwear.app.makeitme.MakeItMeStrings
import com.runwear.app.makeitme.MakeItMeUpsellRules
import com.runwear.app.makeitme.PersonalHeroService
import com.runwear.app.makeitme.PersonalLookStatus
import com.runwear.app.makeitme.PersonalServeKind
import com.runwear.app.makeitme.ScaffoldRenderPolicy
import com.runwear.app.makeitme.deriveBankCoverage
import com.runwear.app.ui.components.DayChartBuilder
import com.runwear.app.ui.components.HeroPillKind
import com.runwear.app.ui.components.HeroPillPolicy
import com.runwear.app.ui.components.HeroViewerPolicy
import com.runwear.app.ui.screens.OnboardingCopy
import com.runwear.app.ui.screens.TimePickerDraftPolicy
import com.runwear.app.ui.viewmodel.PersonalLookRecheckPolicy
import com.runwear.app.weariverun.WearIveRunCollectionLogic
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunRecordPolicy
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.HourlyForecast
import com.runwear.shared.domain.model.WeatherCode
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.datetime.LocalDate as KLocalDate
import kotlinx.datetime.LocalDateTime as KLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-FIXWAVE (Nov 7, 2026) — the Android lane's regression pins,
 * mapped to the architecture review's 30-row matrix (Section 6)
 * and the filed items RW-122/123/125/126/128/130/131/132/133/
 * 134/135. Rows that are rendered states are additionally covered
 * by RwFixWave2RenderTest; rows belonging to the server or iOS
 * lanes are not graded here.
 */
class RwFixWave2PoliciesTest {

    // ── RW-128: the aligned free allowance is 10 everywhere ─────

    @Test
    fun `rw128 - allowance constant and every counting string read 10`() {
        assertEquals(10, MakeItMeUpsellRules.FREE_ALLOWANCE)
        assertEquals("2 of 10 looks", MakeItMeStrings.heroPillLookCount(2))
        assertEquals("10 of 10 used", MakeItMeStrings.HERO_PILL_EXHAUSTED_COUNT)
        assertEquals("All 10 free looks used", MakeItMeStrings.s4FreeUsed(10))
        assertEquals("9 of 10 free looks used", MakeItMeStrings.s4FreeUsed(9))
        assertEquals(
            "Your first 10 looks are free.",
            OnboardingCopy.FREE_LOOKS_PROMISE
        )
        assertEquals(
            "That was look 1 of your 10 free looks — 9 remain, whenever you want them.",
            MakeItMeStrings.FIRST_LOOK_COUNT_LINE
        )
        assertEquals(
            "3 of 10 used · Free looks never expire — and your first ones take about a minute to make.",
            MakeItMeStrings.u2MeterLine(3)
        )
    }

    @Test
    fun `rw128 - exhausted pill flips at 10 not 5 (matrix S6-20 shape)`() {
        val atNine = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.None,
            serveKind = PersonalServeKind.STOCK_QUIET,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 9
        )
        assertNull(atNine)
        val atTen = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.None,
            serveKind = PersonalServeKind.STOCK_QUIET,
            heroIsPersonal = false,
            mode = MakeItMeMode.FREE,
            freeGenerationsUsed = 10
        )
        assertEquals(HeroPillKind.EXHAUSTED, atTen?.kind)
        assertEquals("10 of 10 used", atTen?.text)
    }

    // ── RW-130: the ladder's client half ────────────────────────

    @Test
    fun `rw130 - similar and scaffold serves classify as non-exact and stay silent`() {
        assertEquals(
            PersonalServeKind.SIMILAR,
            ScaffoldRenderPolicy.kindFor(PersonalLookStatus.Ready, "similar", found = true)
        )
        assertEquals(
            PersonalServeKind.SCAFFOLD,
            ScaffoldRenderPolicy.kindFor(PersonalLookStatus.Ready, "scaffold", found = true)
        )
        assertTrue(ScaffoldRenderPolicy.isNonExactServe(PersonalServeKind.SIMILAR))
        assertTrue(ScaffoldRenderPolicy.isNonExactServe(PersonalServeKind.SCAFFOLD))
        assertFalse(ScaffoldRenderPolicy.isNonExactServe(PersonalServeKind.EXACT))
        // Silence: no pill on a similar serve, even for Premium.
        assertNull(
            HeroPillPolicy.resolve(
                lookStatus = PersonalLookStatus.Ready,
                serveKind = PersonalServeKind.SIMILAR,
                heroIsPersonal = true,
                mode = MakeItMeMode.SUBSCRIBED,
                freeGenerationsUsed = 0
            )
        )
        // The generating pill still outranks silence while the
        // exact cell is making (settled FA-1 precedence).
        val making = HeroPillPolicy.resolve(
            lookStatus = PersonalLookStatus.Making,
            serveKind = PersonalServeKind.SIMILAR,
            heroIsPersonal = true,
            mode = MakeItMeMode.SUBSCRIBED,
            freeGenerationsUsed = 0
        )
        assertEquals(HeroPillKind.MAKING, making?.kind)
    }

    // ── RW-134 / review row 18: the widened recheck set ─────────

    @Test
    fun `rw134 - recheck is owed for Making AND None, not for Ready or Failed`() {
        fun owed(status: PersonalLookStatus) = PersonalLookRecheckPolicy.isRecheckOwed(
            personalLayerEngages = true, hasCellInView = true, status = status
        )
        assertTrue(owed(PersonalLookStatus.Making))
        // The RW-134 shape: a stock-served (None) cell that banks
        // in the background must be re-asked on landing/foreground.
        assertTrue(owed(PersonalLookStatus.None))
        assertFalse(owed(PersonalLookStatus.Ready))
        assertFalse(
            owed(PersonalLookStatus.Failed(reason = PersonalLookStatus.Reason.ERROR))
        )
        // Never owed without the layer engaged or a cell in view.
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                personalLayerEngages = false, hasCellInView = true,
                status = PersonalLookStatus.None
            )
        )
        assertFalse(
            PersonalLookRecheckPolicy.isRecheckOwed(
                personalLayerEngages = true, hasCellInView = false,
                status = PersonalLookStatus.None
            )
        )
    }

    // ── RW-126 + review row 19: band truth ──────────────────────

    @Test
    fun `rw126 - band retires when every missing target cell terminally failed`() {
        // RW-126's shape: 44 delivered of a 60-cell named set in
        // spirit — here 6 of 10 ready, the other 4 recorded FAILED.
        val cov = deriveBankCoverage(
            heroCells = (1..6).map { "cell-$it" }.toSet(),
            jobCells = (1..10).map { "cell-$it" }.toSet(),
            targetCells = (1..10).map { "cell-$it" }.toSet(),
            failedCells = (7..10).map { "cell-$it" }.toSet()
        )
        assertEquals(6, cov.ready)
        assertEquals(4, cov.failed)
        assertEquals(0, cov.outstanding)
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, cov, inTrial = false)
        )
        // One genuinely outstanding cell keeps the band honest.
        val stillWorking = deriveBankCoverage(
            heroCells = (1..6).map { "cell-$it" }.toSet(),
            jobCells = (1..10).map { "cell-$it" }.toSet(),
            targetCells = (1..10).map { "cell-$it" }.toSet(),
            failedCells = (7..9).map { "cell-$it" }.toSet()
        )
        assertEquals(1, stillWorking.outstanding)
        assertNotNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, stillWorking, inTrial = false)
        )
    }

    @Test
    fun `review row 19 - no engaged-union denominator before the target is derived`() {
        val cov = deriveBankCoverage(
            heroCells = setOf("a", "b", "c"),
            jobCells = setOf("c", "d", "e", "f")
        )
        // Unknown denominator, reported as such — the band policy
        // renders no band rather than an invented fraction.
        assertEquals(0, cov.total)
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, cov, inTrial = false)
        )
    }

    // ── Day-chart Now point (Sol's ruling; matrix S6-27) ───────

    @Test
    fun `day chart - the Now hour takes the live current value, band included`() {
        val date = LocalDate.of(2026, 11, 7)
        val hourly = (0..23).map { h ->
            HourlyForecast(
                dateTime = KLocalDateTime(2026, 11, 7, h, 0),
                temperature = 50.0,
                feelsLike = 50.0,
                humidity = 40,
                windSpeed = 5.0,
                precipitationProbability = 0,
                weatherCode = WeatherCode.CLEAR_SKY,
                uvIndex = 0.0
            )
        }
        val model = DayChartBuilder.build(
            hourly = hourly,
            date = date,
            comfort = ComfortPreference.NEUTRAL,
            nowDate = date,
            nowHour = 10,
            nowFeelsLikeF = 71.0
        )
        val nowPoint = model.point(10)!!
        assertEquals(71.0, nowPoint.feelsLikeF, 0.001)
        // The band shading for the Now hour derives from the same
        // value: 71F is a different bracket than the forecast 50F.
        val forecastModel = DayChartBuilder.build(
            hourly = hourly, date = date, comfort = ComfortPreference.NEUTRAL
        )
        assertEquals(50.0, forecastModel.point(10)!!.feelsLikeF, 0.001)
        assertFalse(nowPoint.bracket == forecastModel.point(10)!!.bracket)
        // Neighbouring hours stay forecast.
        assertEquals(50.0, model.point(11)!!.feelsLikeF, 0.001)
        // No substitution for a picked (non-Now) view.
        val picked = DayChartBuilder.build(
            hourly = hourly, date = date, comfort = ComfortPreference.NEUTRAL,
            nowDate = null, nowHour = null, nowFeelsLikeF = null
        )
        assertEquals(50.0, picked.point(10)!!.feelsLikeF, 0.001)
    }

    // ── RW-131: the picker session ──────────────────────────────

    @Test
    fun `rw131 - changing the date mutates the session in place and sticks`() {
        val today = LocalDate.of(2026, 11, 7)
        val tomorrow = today.plusDays(1)
        var session = TimePickerDraftPolicy.open(
            selectedHour = 7,
            isNowSelected = true,
            isToday = true,
            nowHour = 7,
            selectableHours = (0..23).toSet(),
            workingDate = today
        )
        session = TimePickerDraftPolicy.changeDay(
            session = session,
            newDate = tomorrow,
            newSelectableHours = (0..23).toSet(),
            newIsToday = false,
            newNowHour = null
        )
        assertEquals(tomorrow, session.workingDate)
        assertTrue(session.dayChanged)
        // The working hour survives the day change when selectable.
        assertEquals(7, session.draftHour)
        // Confirm commits (tomorrow, 7) as a pick — one commit.
        val confirm = TimePickerDraftPolicy.confirm(session)
        assertFalse(confirm.keepNow)
        assertEquals(tomorrow, confirm.date)
        assertEquals(7, confirm.hour)
    }

    @Test
    fun `rw131 - day changed away and back is still an explicit pick`() {
        val today = LocalDate.of(2026, 11, 7)
        var session = TimePickerDraftPolicy.open(
            selectedHour = 7,
            isNowSelected = true,
            isToday = true,
            nowHour = 7,
            selectableHours = (0..23).toSet(),
            workingDate = today
        )
        session = TimePickerDraftPolicy.changeDay(
            session, today.plusDays(1), (0..23).toSet(),
            newIsToday = false, newNowHour = null
        )
        session = TimePickerDraftPolicy.changeDay(
            session, today, (0..23).toSet(),
            newIsToday = true, newNowHour = 7
        )
        val confirm = TimePickerDraftPolicy.confirm(session)
        // Sticky: the touched session does NOT re-enter Now.
        assertFalse(confirm.keepNow)
        assertEquals(today, confirm.date)
    }

    @Test
    fun `rw131 - untouched Now session still keeps Now, unselectable hour snaps nearest`() {
        val today = LocalDate.of(2026, 11, 7)
        val session = TimePickerDraftPolicy.open(
            selectedHour = 7,
            isNowSelected = true,
            isToday = true,
            nowHour = 7,
            selectableHours = (0..23).toSet(),
            workingDate = today
        )
        assertTrue(TimePickerDraftPolicy.confirm(session).keepNow)
        val changed = TimePickerDraftPolicy.changeDay(
            session, today.plusDays(1), setOf(6, 9, 12),
            newIsToday = false, newNowHour = null
        )
        // 7 is not selectable on the new day — nearest is 6.
        assertEquals(6, changed.draftHour)
    }

    // ── RW-110/132 future days: the recording gate ─────────────

    @Test
    fun `rw132 - a committed future-day pick records under its own moment`() {
        // Android's same-solve gate admits a picked moment once
        // its own fetch has landed (weather timestamp == pick) —
        // including future days. The entry's DATE now follows the
        // viewed day (HeroMainScreen records selectedDateTime's
        // date), so hero, entry, and line agree in one frame.
        val pick = LocalDateTime.of(2026, 11, 8, 7, 0)
        assertTrue(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = KLocalDateTime(2026, 11, 8, 7, 0),
                selectedMoment = pick
            )
        )
        // A stale solve (yesterday's weather under tomorrow's
        // label) still refuses — DA-09 stands.
        assertFalse(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = false,
                weatherMoment = KLocalDateTime(2026, 11, 7, 7, 0),
                selectedMoment = pick
            )
        )
        // Both switch sequences (matrix S6-04/S6-06): Now admits
        // by definition; a pick admits on its own solve.
        assertTrue(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = true,
                weatherMoment = null,
                selectedMoment = LocalDateTime.of(2026, 11, 7, 7, 0)
            )
        )
    }

    // ── RW-122: collection delete ───────────────────────────────

    @Test
    fun `rw122 - removePlace deletes the whole location group only`() {
        fun entry(place: String, date: LocalDate, session: String) =
            WearIveRunDayEntry(
                date = date, place = place, sessionId = session,
                settledAtMillis = 1L, momentLabel = null,
                feelsLikeF = 50.0, conditionLabel = "Clear",
                isSnowing = false, isRaining = false,
                items = listOf("Tee"), heroRef = null
            )
        val entries = listOf(
            entry("New York, NY", LocalDate.of(2026, 11, 6), "s1"),
            entry("New York, NY", LocalDate.of(2026, 11, 7), "s2"),
            entry("Seattle, WA", LocalDate.of(2026, 11, 7), "s3")
        )
        val after = WearIveRunCollectionLogic.removePlace(entries, "New York, NY")
        assertEquals(1, after.size)
        assertEquals("Seattle, WA", after.single().place)
        // Individual delete still works by identity.
        val one = WearIveRunCollectionLogic.removeByIdentity(entries, entries[2])
        assertEquals(2, one.size)
    }

    // ── RW-133: viewer policy ───────────────────────────────────

    @Test
    fun `rw133 - one quiet caption line, zoom clamped, swipe-down threshold`() {
        assertEquals(
            "Short Sleeve Shirt · Running Shorts",
            HeroViewerPolicy.captionLine(
                listOf("Short Sleeve Shirt", "Running Shorts")
            )
        )
        assertNull(HeroViewerPolicy.captionLine(emptyList()))
        assertEquals(4f, HeroViewerPolicy.clampZoom(9f))
        assertEquals(1f, HeroViewerPolicy.clampZoom(0.5f))
        assertTrue(HeroViewerPolicy.shouldDismissOnSwipeDown(130f))
        assertFalse(HeroViewerPolicy.shouldDismissOnSwipeDown(40f))
    }


}

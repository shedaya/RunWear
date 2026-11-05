package com.runwear.app.makeitme

import com.runwear.shared.domain.model.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * GEN-5 (RW-28 Addendum 8, Defects 1 + 2): the convergence
 * sequence the coordinator's gate checks — grant → the fill is
 * requested for the forecast cells → the bank converges against
 * the SAME set — exercised at the policy level.
 */
class ForecastFillTest {

    private val day0: LocalDate = LocalDate.of(2026, 11, 5)

    /** A 2-day forecast's frames, in forecast order. */
    private fun frames(): List<FillFrame> {
        val out = mutableListOf<FillFrame>()
        fun add(day: Long, daypart: TimeOfDay, cell: String) {
            out += FillFrame(day0.plusDays(day), daypart, cell)
        }
        // Day 0: early-night cluster, dawn, midday, dusk, late-night.
        add(0, TimeOfDay.NIGHT, "COOL_NIGHT")
        add(0, TimeOfDay.DAWN, "COOL_DAWN")
        add(0, TimeOfDay.MIDDAY, "MILD_MIDDAY")
        add(0, TimeOfDay.DUSK, "COOL_DUSK")
        add(0, TimeOfDay.NIGHT, "COLD_NIGHT")
        // Day 1: dawn prescribes day 0's dusk cell (dedupe target).
        add(1, TimeOfDay.DAWN, "COOL_DUSK")
        add(1, TimeOfDay.MIDDAY, "WARM_MIDDAY")
        add(1, TimeOfDay.DUSK, "WARM_DUSK")
        add(1, TimeOfDay.NIGHT, "WARM_NIGHT")
        return out
    }

    @Test
    fun `select names one cell per day-daypart cluster in forecast order`() {
        assertEquals(
            listOf(
                "COOL_NIGHT", "COOL_DAWN", "MILD_MIDDAY", "COOL_DUSK",
                "COLD_NIGHT", "WARM_MIDDAY", "WARM_DUSK", "WARM_NIGHT"
            ),
            ForecastFillCells.select(frames())
        )
    }

    @Test
    fun `grant to fill to converged - the bank fills without opening cells one by one`() {
        // 1. The grant's fill request names the forecast cells.
        val named = ForecastFillCells.select(frames())
        val body = fillRequestBody(named)
        assertEquals(
            """{"priority_cells":["COOL_NIGHT","COOL_DAWN","MILD_MIDDAY","COOL_DUSK","COLD_NIGHT","WARM_MIDDAY","WARM_DUSK","WARM_NIGHT"]}""",
            body
        )
        // 2. Coverage is measured against the named set from the
        // start: nothing banked, band up at an honest zero.
        val target = named.toSet()
        var coverage = deriveBankCoverage(emptySet(), emptySet(), target)
        assertEquals(0, coverage.ready)
        assertEquals(8, coverage.total)
        assertNotNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
        )
        // 3. As the worker banks the named cells (none of them
        // opened by hand), the fraction climbs against the SAME
        // denominator — no engaged-set inflation at any step.
        for (banked in 1..7) {
            coverage = deriveBankCoverage(named.take(banked).toSet(), emptySet(), target)
            assertEquals(banked, coverage.ready)
            assertEquals(8, coverage.total)
            assertNotNull(
                ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
            )
        }
        // 4. The last named cell lands: the bank has converged and
        // the band retires.
        coverage = deriveBankCoverage(target, emptySet(), target)
        assertEquals(8, coverage.ready)
        assertNull(
            ConvergenceBandPolicy.content(MakeItMeMode.SUBSCRIBED, coverage, inTrial = false)
        )
    }

    // ── Second follow-up: the corrected fill set ────────────────

    private val engine = com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase()

    @Test
    fun `the core matrix derives all 20 canonical cells through the serve path`() {
        val core = CoreMatrixCells.ids(
            engine, com.runwear.shared.domain.model.GenderPreference.FEMALE
        )
        assertEquals(20, core.size)
        assertEquals(20, core.distinct().size)
        // The drift-guard anchor (FillCanonicalOutfitsTest): the
        // CLEAR COOL DAWN cell hashes to the combination the live
        // client requested on 2026-11-01 — the matrix ids here are
        // the server's canonical ids by construction.
        assertTrue(core.contains("FEMALE_CLEAR_COOL_DAWN_e216ba73"))
    }

    @Test
    fun `gate check 1 - a winter subscriber's matrix banks the hot clear midday cell`() {
        // Winter profile: the forecast names only cold cells, but
        // the composed fill still banks HOT x CLEAR x MIDDAY, so
        // when that prescription is opened the serve is EXACT.
        val core = CoreMatrixCells.ids(
            engine, com.runwear.shared.domain.model.GenderPreference.MALE
        )
        val hotMidday = core.filter {
            it.contains("_CLEAR_HOT_MIDDAY_")
        }
        assertEquals(1, hotMidday.size)
        val winterForecast = listOf("MALE_CLEAR_FREEZING_DAWN_x", "MALE_CLEAR_COLD_DUSK_y")
        val owed = ForecastFillCells.compose(core, winterForecast)
        assertTrue(owed.contains(hotMidday.single()))
        // Forecast cells keep the higher priority (they lead).
        assertEquals(winterForecast[0], owed[0])
        assertEquals(winterForecast[1], owed[1])
    }

    @Test
    fun `gate check 2 - an off-matrix cell is not in the fill; the substrate is`() {
        // HOT x RAIN x DUSK is off-matrix and (in winter) off-
        // forecast: no fill job banks it, so its serve must come
        // from the own-photo scaffold path (get-personal-hero,
        // engaged by the Item-3 guarantee) — and the substrate it
        // scaffolds FROM is the same prescription family's matrix
        // cells, which ARE in the fill.
        val core = CoreMatrixCells.ids(
            engine, com.runwear.shared.domain.model.GenderPreference.MALE
        )
        val owed = ForecastFillCells.compose(core, listOf("MALE_CLEAR_COLD_DAWN_z"))
        assertTrue(owed.none { it.contains("_RAIN_HOT_DUSK_") })
        assertTrue(owed.any { it.contains("_CLEAR_HOT_DAWN_") })
        assertTrue(owed.any { it.contains("_RAIN_HOT_DAWN_") })
    }

    @Test
    fun `gate check 3 - a fresh subscription names matrix plus forecast, opening nothing`() {
        val core = CoreMatrixCells.ids(
            engine, com.runwear.shared.domain.model.GenderPreference.UNISEX
        )
        val forecast = ForecastFillCells.select(frames())
        val body = fillRequestBody(ForecastFillCells.compose(core, forecast))
        // Every forecast cell and every matrix cell is named.
        for (cell in forecast) assertTrue(body.contains("\"$cell\""))
        for (cell in core) assertTrue(body.contains("\"$cell\""))
        // And the forecast cells are named ahead of the matrix.
        val firstForecastAt = body.indexOf(forecast.first())
        val firstMatrixOnly = core.first { it !in forecast }
        assertTrue(firstForecastAt in 0 until body.indexOf(firstMatrixOnly))
    }
}

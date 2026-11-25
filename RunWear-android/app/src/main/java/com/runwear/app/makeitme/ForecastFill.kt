package com.runwear.app.makeitme

import com.runwear.shared.domain.model.TimeOfDay
import java.time.LocalDate

/**
 * GEN-5 (RW-28 Addendum 8, Defect 1 — convergence diagnosis,
 * Nov 5, 2026): the forecast fill's cell selection.
 *
 * The deployed start-personal-fill (v13, RW-16.30 convergence
 * model) enqueues ONLY the cells the caller names in
 * `priority_cells`. The client names the cells of its own 7-day
 * forecast: one representative frame per (day, daypart) cluster —
 * the first forecast hour of each cluster, in forecast order —
 * each frame's combination id derived by exactly the serve path's
 * derivation (the outfit engine at that frame, hashed the same
 * way), so a named cell is a cell the serve path can actually
 * request when that frame arrives. The night daypart forms two
 * clusters per calendar day (00:00–04:59 and 20:00–23:59); both
 * are named.
 */
data class FillFrame(
    val date: LocalDate,
    val daypart: TimeOfDay,
    val combinationId: String
)

object ForecastFillCells {

    /**
     * The priority cell list for a forecast: the first frame of
     * each consecutive (date, daypart) cluster, in forecast order,
     * with duplicate combination ids removed (adjacent dayparts
     * frequently prescribe the same cell).
     */
    fun select(frames: List<FillFrame>): List<String> {
        val out = mutableListOf<String>()
        var lastKey: Pair<LocalDate, TimeOfDay>? = null
        for (frame in frames) {
            val key = frame.date to frame.daypart
            if (key != lastKey) {
                out += frame.combinationId
                lastKey = key
            }
        }
        return out.distinct()
    }

    /**
     * GEN-5 (RW-28 Addendum 8, second follow-up — the corrected
     * fill set): the full owed set the grant/epoch fill names, in
     * priority order — the FORECAST cells first (imminent
     * conditions land first), the CORE MATRIX behind them (the
     * scaffold substrate fills behind). Duplicates collapse to
     * their first, highest-priority position.
     */
    fun compose(coreMatrix: List<String>, forecastCells: List<String>): List<String> =
        (forecastCells + coreMatrix).distinct()
}

/**
 * GEN-5 (RW-28 Addendum 8, second follow-up): the 20-cell
 * canonical CORE MATRIX — the scaffold substrate. Every
 * temperature bracket (FREEZING, COLD, COOL, MILD, WARM, HOT) at
 * CLEAR × DAWN, CLEAR × DUSK and RAIN × DAWN, plus WARM and HOT
 * at CLEAR × MIDDAY, each at the bracket's canonical outfit.
 * This is the RW-16.30 production fill's original guarantee
 * (start-personal-fill v9): the bank ALWAYS contains a photo of
 * the user in every canonical outfit, so any prescription can be
 * served exactly (a matrix cell) or as the user's own-photo
 * scaffold (an off-matrix cell) — never a stock photo. A
 * forecast-only fill breaks that promise seasonally (a winter
 * forecast banks no hot-weather photo to scaffold from).
 *
 * The ids are DERIVED, not mirrored: each cell's representative
 * frame (the exact frames FillCanonicalOutfitsTest pins against
 * the server function's canonical table — comfort NEUTRAL, CLEAR
 * calm / RAIN at 1.0mm, UV 2 at DAWN/DUSK, 4 at MIDDAY, 1 in
 * RAIN, hours 8/18/13, bracket temps 12/27/42/57/72/85 °F) is run
 * through the production outfit engine and the serve path's own
 * combination derivation, so a matrix id here is byte-identical
 * to the id the server enqueues and the client later requests.
 */
object CoreMatrixCells {

    private val BRACKET_TEMPS: List<Double> =
        listOf(12.0, 27.0, 42.0, 57.0, 72.0, 85.0)

    fun ids(
        getOutfit: com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase,
        gender: com.runwear.shared.domain.model.GenderPreference
    ): List<String> {
        val out = mutableListOf<String>()
        for (temp in BRACKET_TEMPS) {
            out += cellId(getOutfit, gender, temp, raining = false, hour = 8, uv = 2.0)
            out += cellId(getOutfit, gender, temp, raining = false, hour = 18, uv = 2.0)
            out += cellId(getOutfit, gender, temp, raining = true, hour = 8, uv = 1.0)
        }
        out += cellId(getOutfit, gender, 72.0, raining = false, hour = 13, uv = 4.0)
        out += cellId(getOutfit, gender, 85.0, raining = false, hour = 13, uv = 4.0)
        return out.distinct()
    }

    private fun cellId(
        getOutfit: com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase,
        gender: com.runwear.shared.domain.model.GenderPreference,
        tempF: Double,
        raining: Boolean,
        hour: Int,
        uv: Double
    ): String {
        val conditions = com.runwear.shared.domain.model.WeatherConditions(
            dateTime = kotlinx.datetime.LocalDateTime(2026, 1, 1, hour, 0),
            temperature = tempF,
            feelsLike = tempF,
            humidity = 50,
            windSpeed = 0.0,
            windGusts = 0.0,
            precipitationProbability = if (raining) 100 else 0,
            precipitation = if (raining) 1.0 else 0.0,
            isRaining = raining,
            isSnowing = false,
            cloudCover = if (raining) 100 else 0,
            uvIndex = uv,
            weatherCode = if (raining) {
                com.runwear.shared.domain.model.WeatherCode.RAIN_MODERATE
            } else {
                com.runwear.shared.domain.model.WeatherCode.CLEAR_SKY
            }
        )
        val outfit = getOutfit.execute(
            conditions,
            com.runwear.shared.domain.model.ComfortPreference.NEUTRAL
        )
        return com.runwear.shared.domain.model.OutfitCombination.fromConditions(
            conditions, outfit, gender, frameFeelsLikeF = tempF
        ).id
    }
}

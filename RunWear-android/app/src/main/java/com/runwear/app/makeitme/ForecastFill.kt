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
    val combinationId: String,
    /**
     * RW-151 client (Nov 10, 2026; DESIGN.md §5): the outfit item
     * keys the engine prescribed at this frame — the items the
     * client already computed via personalCombinationFor and used
     * to discard. A fill job is fulfilled entirely from its item
     * list (the server cannot recover items from the id's one-way
     * signature hash), so a forecast cell is only FILLABLE when it
     * travels accompanied by these items. Empty on legacy call
     * sites that derive no outfit (never fillable as accompanied).
     */
    val outfitItems: List<String> = emptyList()
)

/**
 * RW-151 client: one named fill cell — its combination id plus,
 * for forecast-derived cells, the outfit items that accompany it
 * on the wire. Core-matrix cells carry no items: they are
 * canonical by construction and travel BARE (the server resolves
 * them through its canonical table, preserving isCoreCell
 * promotion).
 */
data class FillCell(
    val combinationId: String,
    val outfitItems: List<String> = emptyList()
) {
    /**
     * The wire form (start-personal-fill v15 contract): forecast
     * cells accompanied — "id|ITEM,ITEM,..." — core cells bare.
     */
    fun wireForm(): String =
        if (outfitItems.isEmpty()) {
            combinationId
        } else {
            combinationId + "|" + outfitItems.joinToString(",")
        }
}

object ForecastFillCells {

    /**
     * The priority cell list for a forecast: the first frame of
     * each consecutive (date, daypart) cluster, in forecast order,
     * with duplicate combination ids removed (adjacent dayparts
     * frequently prescribe the same cell).
     */
    fun select(frames: List<FillFrame>): List<String> =
        selectCells(frames).map { it.combinationId }

    /**
     * RW-151 client: the same cluster-representative selection and
     * order as [select], carrying each representative's (id, items)
     * pair through instead of discarding the items.
     */
    fun selectCells(frames: List<FillFrame>): List<FillCell> {
        val out = mutableListOf<FillCell>()
        var lastKey: Pair<LocalDate, TimeOfDay>? = null
        for (frame in frames) {
            val key = frame.date to frame.daypart
            if (key != lastKey) {
                out += FillCell(frame.combinationId, frame.outfitItems)
                lastKey = key
            }
        }
        return out.distinctBy { it.combinationId }
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

    /**
     * RW-151 client: the composed WIRE set — forecast cells first
     * in their accompanied form, the core matrix behind them BARE,
     * deduplicated by combination id (a forecast cell that is also
     * a core cell keeps its first, accompanied, forecast position).
     * The wire type is unchanged: priority_cells stays [String].
     */
    fun composeWire(coreMatrix: List<String>, forecastCells: List<FillCell>): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (cell in forecastCells) {
            if (seen.add(cell.combinationId)) out += cell.wireForm()
        }
        for (id in coreMatrix) {
            if (seen.add(id)) out += id
        }
        return out
    }

    /** The combination id a wire entry names (the part before any
     * accompaniment separator) — the coverage target's identity. */
    fun cellIdOf(wireEntry: String): String = wireEntry.substringBefore('|')

    /**
     * RW-151 client (DESIGN.md §5): the FILLABLE subset of a target
     * set, given the composed wire entries and the canonical core
     * ids. A target cell is fillable iff it travels accompanied
     * (forecast form, items present) or it is a bare core cell the
     * canonical table resolves. A bare, non-core, unaccompanied
     * cell is NOT fillable — the contract cannot enqueue it — and
     * must never hold the convergence denominator open.
     */
    fun fillableIds(wireEntries: List<String>, coreMatrix: List<String>): Set<String> {
        val core = coreMatrix.toSet()
        val out = mutableSetOf<String>()
        for (entry in wireEntries) {
            val id = cellIdOf(entry)
            val accompanied = entry.contains('|') &&
                entry.substringAfter('|').isNotBlank()
            if (accompanied || id in core) out += id
        }
        return out
    }
}

/**
 * RW-151 client (DESIGN.md §5, reconcile rule): the band must
 * never present "making" with nothing in flight. After any fill
 * response or coverage read, re-issue the fill ONCE for the
 * fillable remainder iff outstanding work remains over the
 * fillable set AND no non-terminal job exists for the account AND
 * no fill request is outstanding. The caller owns the
 * once-per-derivation guard (its cell-set dedupe key, extended to
 * the accompanied wire form); this policy is the pure predicate.
 */
object FillReconcilePolicy {
    fun shouldReissue(
        outstanding: Int,
        hasNonTerminalJob: Boolean,
        fillOutstanding: Boolean
    ): Boolean = outstanding > 0 && !hasNonTerminalJob && !fillOutstanding
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

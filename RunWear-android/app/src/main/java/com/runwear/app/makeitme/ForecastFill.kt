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
}

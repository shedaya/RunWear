package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate

/**
 * RW-169 §7E (Nov 11, 2026 — diagnosis §5 / spec §2.7): the
 * PresentedEntry settle discipline, implemented once.
 *
 * THE DEFECT (diagnosis §5, proved on Android): the recording path
 * was a LaunchedEffect keyed on the LIVE uiState whose only gate
 * (WearIveRunRecordPolicy.shouldRecord) tested moment-consistency —
 * no stability window existed anywhere in the path, so every
 * re-settle of the view (weather fetch, NOW tick, fallback-hero
 * cycle, personal-look poll) was a candidate write, and surfaces
 * built their lines piecemeal from live state (the footer latched
 * only its narration while building its entry line live). At 13.5 s
 * of the owner's recording the open footer rewrote its entry under
 * his eyes: items 4 -> 2 in one frame, then the thumbnail washing
 * out and vanishing ~0.75 s later.
 *
 * THE DISCIPLINE (spec §2.7 / §7E):
 * 1. Stability-gated commit — a change reaches the record only
 *    after the viewed frame (place, day/hour, kit, hero reference)
 *    is unchanged for the ≈2 s settle window ([StabilityGate]).
 * 2. Atomic flip — on commit the entry flips as ONE immutable
 *    presented snapshot ([PresentedSnapshot]: place, day,
 *    feelsLike, items, narration, hero reference together).
 * 3. All three surfaces render ONLY the presented snapshot (mini,
 *    expanded footer, collection TODAY card); narration and entry
 *    line are the same object, never latched separately.
 * 4. Thumbnail binds at promotion — a surface that has shown a
 *    thumbnail never returns to an empty slot for the same entry
 *    ([ThumbnailHold]); fallback serves are never recorded as the
 *    entry's hero reference ([recordableHeroRef]).
 * 5. Flips render per spec §4.3: opacity-only narration cross-fade
 *    + ≈1.5% update bounce, deferred to scroll rest (the host's
 *    presented-snapshot swap happens only at scroll rest).
 */

/** The viewed frame a settle is judged on: exactly the dimensions
 * spec §2.7 names — place, day/hour, kit, hero reference. Two
 * frames are the same proposal iff every field agrees. */
data class ViewedFrame(
    val place: String,
    val date: LocalDate,
    val momentLabel: String?,
    val feelsLikeF: Double,
    val items: List<String>,
    /** The RECORDABLE hero reference (see [recordableHeroRef]) —
     * a fallback serve contributes null here, never its URL. */
    val heroRef: String?
) {
    companion object {
        fun of(entry: WearIveRunDayEntry): ViewedFrame = ViewedFrame(
            place = entry.place,
            date = entry.date,
            momentLabel = entry.momentLabel,
            feelsLikeF = entry.feelsLikeF,
            items = entry.items,
            heroRef = entry.heroRef
        )
    }
}

/**
 * The hero reference a settle may RECORD for a frame. The served
 * hero is `heroImageUrl ?: heroFallbackUrl`; when only the
 * fallback serves (no primary hero for the cell), the serve is a
 * stand-in, and a stand-in is never the entry's hero reference
 * (§7E.4) — the entry records null and the RW-166 bank recovery
 * heals a real reference onto it later. When a primary hero
 * serves, it IS the effective URL and is recorded as today.
 */
fun recordableHeroRef(heroImageUrl: String?, heroFallbackUrl: String?): String? {
    // Primary serve only: effective == heroImageUrl when it exists.
    return heroImageUrl
}

/**
 * The ≈2 s stability gate (spec §1.2 / §2.7: "≈2 s of stability";
 * a proposal arriving during the window restarts it — stability
 * is the criterion). Pure and clock-injected so the gate is
 * unit-testable without composition.
 *
 * Usage: [offer] every candidate frame with the current time.
 * It returns the frame's entry exactly once — at the first offer
 * at or after the window during which the frame never changed.
 * A frame identical to the last COMMITTED frame never re-commits
 * (a weather re-settle that changes nothing the frame names is
 * not a write at all).
 */
class StabilityGate(
    val windowMs: Long = STABILITY_WINDOW_MS
) {
    private var candidate: ViewedFrame? = null
    private var candidateSinceMs: Long = 0L
    private var committed: ViewedFrame? = null

    /** The frame currently inside its stability window, if any. */
    val pendingFrame: ViewedFrame? get() = candidate

    /** The last frame that completed its window, if any. */
    val committedFrame: ViewedFrame? get() = committed

    /**
     * Offer [entry] (the candidate record for [frame]) observed at
     * [nowMs]. Returns the entry to write when — and only when —
     * this offer completes the frame's stability window.
     */
    fun offer(frame: ViewedFrame, entry: WearIveRunDayEntry, nowMs: Long): WearIveRunDayEntry? {
        if (frame == committed) {
            // Already the record: no window, no write.
            candidate = null
            return null
        }
        if (frame != candidate) {
            candidate = frame
            candidateSinceMs = nowMs
            return null
        }
        if (nowMs - candidateSinceMs >= windowMs) {
            committed = frame
            candidate = null
            return entry
        }
        return null
    }

    /** Forget all state (session end / day rollover). */
    fun reset() {
        candidate = null
        committed = null
    }

    companion object {
        /** Spec §1.2 / §2.7: the ≈2 s settle window. */
        const val STABILITY_WINDOW_MS: Long = 2_000L
    }
}

/**
 * ONE immutable presented snapshot (§7E.2): the recorded entry,
 * its narration and its entry line, built together from the same
 * entry in the same instant. Surfaces render this and nothing
 * else — the narration latch and the entry line can never again
 * be different objects sampled at different times.
 */
data class PresentedSnapshot(
    val entry: WearIveRunDayEntry,
    val narration: WearIveRunNarration,
    val entryLine: String,
    /** The expanded form's entry projection, built from the SAME
     * entry in the SAME instant (the holder's barEntryOf shape). */
    val barEntry: WearIveRunEntry
) {
    companion object {
        fun of(
            entry: WearIveRunDayEntry,
            today: LocalDate,
            temperatureUnit: TemperatureUnit
        ): PresentedSnapshot = PresentedSnapshot(
            entry = entry,
            narration = WearIveRunDockNarration.line(entry, today),
            entryLine = WearIveRunDockNarration.entryLine(entry, today, temperatureUnit),
            barEntry = barEntryOf(entry, today)
        )

        /** The holder's projection, reproduced as a pure function
         * of the snapshot's entry so every surface projects the
         * presented entry identically. */
        fun barEntryOf(entry: WearIveRunDayEntry, today: LocalDate): WearIveRunEntry {
            val simplePlace = com.runwear.shared.domain.model.SimplePlace
                .fromRawDisplayString(entry.place).display
            return WearIveRunEntry(
                place = simplePlace,
                timeLabel = WearIveRunCollectionLogic.narrationDayForm(entry.date, today),
                thumbnailUrl = entry.heroRef,
                sourceEntry = entry,
                feelsLikeF = entry.feelsLikeF,
                itemCount = entry.items.size,
                itemNames = entry.items
            )
        }
    }
}

/**
 * Thumbnail-never-unloads (§7E.4): the URL a surface displays for
 * an entry is the freshly resolved URL when one exists, and the
 * last URL the surface actually showed while a successor
 * reference resolves. A surface that has shown a thumbnail for an
 * entry never returns to an empty slot for that same entry.
 * Pure — the composable holder in HeroRefResolver is a thin
 * state wrapper over this rule.
 */
object ThumbnailHold {
    fun displayed(resolved: String?, held: String?): String? =
        resolved ?: held
}

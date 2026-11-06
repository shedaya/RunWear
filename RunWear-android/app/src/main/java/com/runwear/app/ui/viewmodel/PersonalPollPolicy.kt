package com.runwear.app.ui.viewmodel

import com.runwear.app.makeitme.PersonalLookStatus

/**
 * GEN-6 WAVE A, ITEM 1 (DA-02 client amplifier) — the personal-hero
 * MAKING poll schedule.
 *
 * Gen-5 defect: the poll ran 18 × 15 s ≈ 4.5 minutes and then simply
 * stopped, stranding the MAKING pill on screen for as long as the
 * user stayed on Home — the job was still in flight server-side
 * (measured: 17–40 s of generation behind a queue that could hold a
 * cell for minutes), but nothing on the client ever looked again.
 *
 * The schedule here is aligned to the guaranteed first-cell
 * delivery window with a wide margin: short polls while the job is
 * fresh (15 s × 4 = 1 minute), then 30 s × 4 (2 minutes), then one
 * poll a minute until the total horizon of 20 minutes — comfortably
 * past the server lane's processing-bound guarantee. When the
 * horizon expires the poll RETURNS its outcome instead of dying
 * silently: the caller re-reads the server truth one final time and
 * leaves whatever state the server reports (a still-Making job
 * keeps its honest MAKING pill over the fallback photo — the screen
 * stays usable, and every re-entry point — foreground, refresh,
 * cell revisit — restarts the poll through the overlay path).
 */
object PersonalPollPolicy {

    /** Total poll horizon: 20 minutes — ≥ the 15-minute floor. */
    const val HORIZON_MS: Long = 20 * 60 * 1000L

    /** The poll always stops at a terminal look state. */
    fun isTerminal(status: PersonalLookStatus): Boolean =
        status != PersonalLookStatus.Making

    /**
     * Delay BEFORE poll attempt [attempt] (0-based). Backs off:
     * 15 s for the first four attempts, 30 s for the next four,
     * then 60 s for the rest of the horizon.
     */
    fun delayBeforeAttemptMs(attempt: Int): Long = when {
        attempt < 0 -> 15_000L
        attempt < 4 -> 15_000L
        attempt < 8 -> 30_000L
        else -> 60_000L
    }

    /** Cumulative elapsed time once [attempts] attempts have run. */
    fun elapsedAfterAttempts(attempts: Int): Long {
        var total = 0L
        for (i in 0 until attempts) total += delayBeforeAttemptMs(i)
        return total
    }

    /** How many attempts fit inside the horizon. */
    fun maxAttempts(): Int {
        var attempts = 0
        var elapsed = 0L
        while (elapsed + delayBeforeAttemptMs(attempts) <= HORIZON_MS) {
            elapsed += delayBeforeAttemptMs(attempts)
            attempts++
        }
        return attempts
    }

    /**
     * Whether another attempt is due: the look is still Making AND
     * the next attempt's delay still fits inside the horizon.
     */
    fun shouldPollAgain(status: PersonalLookStatus, attemptsSoFar: Int): Boolean {
        if (isTerminal(status)) return false
        return elapsedAfterAttempts(attemptsSoFar) +
            delayBeforeAttemptMs(attemptsSoFar) <= HORIZON_MS
    }
}

/**
 * RW-79: when a lifecycle event owes the in-view cell an immediate
 * look-state re-read.
 *
 * The gen-6 defect: the poll was the ONLY periodic reader of the
 * look state, and it ran inside the cancellable hero-resolution
 * job — a superseding resolution killed it, and no event
 * (foregrounding, paywall dismissal, leaving the first-look sheet)
 * ever re-read the state or re-armed the poll. A job that failed
 * terminally at +82 s left Home claiming MAKING for minutes.
 *
 * A re-read is owed exactly while Home claims the look is still
 * MAKING for a cell in view with the personal layer engaged: that
 * claim is the one a dead or horizon-expired poll strands.
 * Terminal or absent states are already the truth on screen —
 * re-reading them would only churn. When the re-read lands, a
 * terminal state resolves Home within seconds; a still-Making
 * result (re)arms the poll.
 */
object PersonalLookRecheckPolicy {

    fun isRecheckOwed(
        personalLayerEngages: Boolean,
        hasCellInView: Boolean,
        status: PersonalLookStatus
    ): Boolean =
        personalLayerEngages && hasCellInView && status == PersonalLookStatus.Making
}

/** What a MAKING poll concluded — never a silent strand. */
enum class PersonalPollOutcome {
    /** The cell reached READY inside the horizon. */
    Delivered,

    /** The cell reached FAILED inside the horizon. */
    Failed,

    /**
     * The horizon expired with the job still Making server-side.
     * The caller keeps the honest MAKING state (usable screen,
     * fallback photo + pill) and the poll restarts at the next
     * re-entry point.
     */
    StillMakingHorizonExpired,

    /** The watched cell changed identity mid-poll; the new cell owns its own poll. */
    CellChanged
}

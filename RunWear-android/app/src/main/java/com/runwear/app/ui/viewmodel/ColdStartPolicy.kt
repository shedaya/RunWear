package com.runwear.app.ui.viewmodel

/**
 * GEN-6 WAVE A, ITEM 4 (DA-07) — cold-start serve order and place
 * persistence, as pure decisions.
 *
 * The owner's relaunch recording showed: blank gradient ~1 s →
 * STOCK stranger ~2 s → his own banked photo — for a Premium user
 * whose banked photo already existed. And the place he had picked
 * (Miami) was discarded for the GPS fix (Iredell), contradicting
 * the app's own "RunWear reopens where you left off" copy.
 *
 * Two mechanisms, both fixed at the seams this object names:
 *
 *  1. SERVE ORDER. The last-good seed always painted a bundled
 *     STOCK fallback as the first hero state, and the weather
 *     resolution wrote the pool (stock) hero first and only then
 *     overlaid the personal photo. Now: a banked own photo found
 *     for the seeded cell IS the first hero state ([firstHeroUrl]
 *     prefers the personal hit), and the resolution path composes
 *     personal-before-stock in a single write.
 *
 *  2. PLACE PERSISTENCE. The preferences store has ONE
 *     saved-location slot shared by manual picks and GPS saves, so
 *     any GPS save overwrote the picked place, and the launch /
 *     foreground GPS paths then "restored" the GPS place. Now a
 *     persisted MANUAL place is authoritative: GPS adoption is
 *     refused while one exists ([gpsAdoptionAllowed]), and the
 *     launch sequence restores the saved place before any GPS work
 *     is allowed to run.
 */
object ColdStartPolicy {

    /** Where a launch gets its place from. */
    enum class LaunchPlace {
        /** A persisted place (manual pick or last GPS) reopens. */
        RESTORE_SAVED,

        /** Nothing persisted: fall back to a fresh GPS fix. */
        FETCH_GPS
    }

    /** Saved-location sources (PreferencesRepository values). */
    const val SOURCE_MANUAL: String = "manual"
    const val SOURCE_GPS: String = "gps"

    /**
     * The launch decision: a persisted place always wins — the app
     * reopens where the user left off. GPS is only the cold
     * fallback when nothing was ever saved.
     */
    fun launchPlace(hasSavedLocation: Boolean): LaunchPlace =
        if (hasSavedLocation) LaunchPlace.RESTORE_SAVED else LaunchPlace.FETCH_GPS

    /**
     * Whether a fresh GPS fix may replace the current place. NEVER
     * when the persisted place is a manual pick, and never when
     * the place currently in view is manual — a picked place
     * persists across relaunch and across foreground revalidation.
     *
     * RW-141: [explicitRequest] is the one exception, and it is
     * not a loosening of this rule — the guard exists to stop
     * UNSOLICITED adoption (launch, foreground revalidation)
     * from discarding the user's pick. When the user themselves
     * taps "Current location" in the Set Location sheet (or
     * grants permission in answer to that tap), their own
     * instruction outranks the guard. The gen-6 defect was that
     * the sheet's tap flowed through this same seam with no
     * explicit marker, so a successful fix was refused and the
     * saved manual place silently re-applied — the button did
     * nothing. Ordinary launches and foreground revalidation
     * never pass `explicitRequest = true`.
     */
    fun gpsAdoptionAllowed(
        savedSource: String?,
        currentSource: String?,
        explicitRequest: Boolean = false
    ): Boolean =
        explicitRequest || (savedSource != SOURCE_MANUAL && currentSource != SOURCE_MANUAL)

    /**
     * The first hero of a cold start: the banked own photo when
     * one exists for the seeded cell; the stock fallback only when
     * it does not. Never stock-first-when-personal-exists.
     */
    fun firstHeroUrl(personalHitUrl: String?, stockFallbackUrl: String?): String? =
        personalHitUrl ?: stockFallbackUrl

    /** Whether the first hero state is the user's own photo. */
    fun firstHeroIsPersonal(personalHitUrl: String?): Boolean = personalHitUrl != null
}

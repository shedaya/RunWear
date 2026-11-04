package com.runwear.app.ui.screens

/**
 * GEN-3 X-13 (carried W-13): the Set Location sheet's
 * permission-bearing rows, resolved from the ACTUAL location
 * permission state — never from the sheet's mode.
 *
 * The defect: the "Current location" row's subtitle was keyed on
 * `isUsingGPS`, which callers derive from
 * `uiState.locationSource == "gps"` — the source of the place
 * currently on screen, not the permission. The moment the user
 * picked a place manually, the row flipped to "Requires location
 * permission" in the same session where permission had been
 * granted at onboarding and GPS was in active use (walkthrough
 * f_105; minutes earlier, on GPS, the same row read "GPS location
 * enabled", f_094). The row now takes the permission as its own
 * input: callers pass the real grant state (MainActivity reads it
 * from the LocationProvider at composition time — NOT
 * `uiState.hasLocationPermission`, which manual selection also
 * sets as a permission-screen bypass and so does not mean
 * "permission granted").
 */
object LocationSheetState {

    /** What the "Current location" row should say about itself. */
    enum class CurrentLocationRowStatus {
        /** The place on screen IS the GPS place: CURRENT badge, check, "GPS location enabled". */
        USING_GPS,

        /** Permission is granted but a manual place is on screen: no badge, still "GPS location enabled". */
        PERMISSION_GRANTED,

        /** Permission is not granted: "Requires location permission". */
        PERMISSION_REQUIRED
    }

    /**
     * Resolve the row from the two independent facts: whether the
     * current place came from GPS, and whether location permission
     * is actually granted. Every sheet mode (search, recents,
     * manual place in use) reads the same answer for the same
     * facts.
     */
    fun currentLocationRowStatus(
        isUsingGPS: Boolean,
        hasLocationPermission: Boolean
    ): CurrentLocationRowStatus = when {
        isUsingGPS -> CurrentLocationRowStatus.USING_GPS
        hasLocationPermission -> CurrentLocationRowStatus.PERMISSION_GRANTED
        else -> CurrentLocationRowStatus.PERMISSION_REQUIRED
    }

    /** The row's subtitle line for a resolved [status]. */
    fun currentLocationRowSubtitle(status: CurrentLocationRowStatus): String = when (status) {
        CurrentLocationRowStatus.USING_GPS,
        CurrentLocationRowStatus.PERMISSION_GRANTED -> "GPS location enabled"
        CurrentLocationRowStatus.PERMISSION_REQUIRED -> "Requires location permission"
    }

    /**
     * The place name the "Currently using:" pill carries, or null
     * when the pill must not render at all. The pill's pre-fix
     * layout stacked the label and the name on separate lines at
     * the bottom of a non-scrollable sheet column: with the IME
     * open the sheet's usable height shrinks, the name line fell
     * below the sheet's clip, and the pill rendered with its label
     * and NO name (f_105; with the keyboard down the same pill
     * rendered fully, f_104). The sheet now composes label + name
     * as ONE line from this seam, so a visible pill always carries
     * its name — and a blank name suppresses the pill instead of
     * rendering it empty.
     */
    fun currentlyUsingName(currentLocation: String): String? =
        currentLocation.trim().takeIf { it.isNotEmpty() }
}

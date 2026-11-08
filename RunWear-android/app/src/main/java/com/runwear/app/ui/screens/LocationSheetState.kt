package com.runwear.app.ui.screens

import com.runwear.shared.util.LocationErrorReason

/**
 * RW-141: the lifecycle of an EXPLICIT "Current location" request
 * (the user tapped the row in the Set Location sheet), carried in
 * the ViewModel's UI state so it survives the sheet itself — the
 * sheet may close or be dismissed mid-flight without cancelling
 * or losing the request, and a failure stays visible in the row
 * instead of spinning and closing silently.
 */
sealed interface CurrentLocationRequestState {
    /** No explicit request in flight and no outcome to show. */
    data object Idle : CurrentLocationRequestState

    /** The user tapped and a fix is being obtained. */
    data object Locating : CurrentLocationRequestState

    /** A fix landed and the GPS place was applied. */
    data object Succeeded : CurrentLocationRequestState

    /** No fix could be obtained; [message] is the row's plain-language account. */
    data class Failed(val message: String) : CurrentLocationRequestState
}

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
     * RW-141: the plain-language account of why an explicit
     * Current location request produced no fix. This is what the
     * row tells the user — a silent no-op is the defect being
     * fixed, so every failure reason has a sentence.
     */
    fun currentLocationFailureMessage(reason: LocationErrorReason): String = when (reason) {
        LocationErrorReason.PERMISSION_DENIED ->
            "Location permission is off. Turn it on to use your current location."
        LocationErrorReason.TIMEOUT ->
            "We couldn't get a fix on your location. Try again in a moment."
        LocationErrorReason.UNAVAILABLE ->
            "Your current location isn't available right now. Try again in a moment."
    }

    /**
     * RW-141: the row's subtitle while an explicit request has
     * something to say — locating, or the failure message — or
     * null when the request is quiet and the row falls back to
     * its permission-status subtitle.
     */
    fun currentLocationRequestSubtitle(request: CurrentLocationRequestState): String? =
        when (request) {
            CurrentLocationRequestState.Locating -> "Finding your location…"
            is CurrentLocationRequestState.Failed -> request.message
            CurrentLocationRequestState.Idle,
            CurrentLocationRequestState.Succeeded -> null
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

package com.runwear.app.ui.viewmodel

import com.runwear.app.ui.screens.LocationSheetState
import com.runwear.shared.util.LocationFetchResult
import com.runwear.shared.util.UserLocation

/**
 * RW-141: an explicit "Current location" request, resolved as a
 * pure flow over injected ports.
 *
 * The owner's recording (Nov 8, 2026) showed the tap's spinner
 * and a closing sheet, but the place never changed: the GPS fix
 * was obtained and then REFUSED at adoption by the cold-start
 * persistence guard (see [ColdStartPolicy] and the RW-141 lane
 * report). The fix makes the explicit path deliberate end to
 * end:
 *
 *  - The request lives in the ViewModel (viewModelScope + UI
 *    state), never in the sheet's composition — dismissing the
 *    sheet cannot cancel it, and a fix that resolves afterwards
 *    still applies.
 *  - A fix is an [Outcome.ApplyFix]: the caller adopts it as an
 *    EXPLICIT adoption ([ColdStartPolicy.gpsAdoptionAllowed]
 *    with `explicitRequest = true`), which outranks a saved
 *    manual pick — the user just asked for their GPS place.
 *  - No fix is an [Outcome.Failed] carrying the row's
 *    plain-language message. The caller applies NOTHING: the
 *    place on screen and the persisted record stay exactly as
 *    they were, instead of the pre-fix silent fallback that
 *    re-applied the saved place and said nothing.
 */
object CurrentLocationFlow {

    sealed interface Outcome {
        /** A fix was obtained; adopt these coordinates as the GPS place. */
        data class ApplyFix(val location: UserLocation) : Outcome

        /** No fix could be obtained; [message] is what the row tells the user. */
        data class Failed(val message: String) : Outcome
    }

    /**
     * Resolve one explicit request: the last-known fix first (it
     * resolves instantly when one exists), then a fresh fix.
     * Nothing in here knows about UI or sheets — the resolution
     * completes whenever the fix completes, regardless of what
     * any surface did in the meantime.
     */
    suspend fun resolve(
        lastKnown: suspend () -> UserLocation?,
        freshFix: suspend () -> LocationFetchResult
    ): Outcome {
        lastKnown()?.let { return Outcome.ApplyFix(it) }
        return when (val result = freshFix()) {
            is LocationFetchResult.Success -> Outcome.ApplyFix(result.location)
            is LocationFetchResult.Error ->
                Outcome.Failed(LocationSheetState.currentLocationFailureMessage(result.reason))
        }
    }
}

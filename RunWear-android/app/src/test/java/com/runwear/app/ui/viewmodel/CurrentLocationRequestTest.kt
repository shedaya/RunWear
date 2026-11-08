package com.runwear.app.ui.viewmodel

import com.runwear.app.ui.screens.CurrentLocationRequestState
import com.runwear.app.ui.screens.LocationSheetState
import com.runwear.shared.util.LocationErrorReason
import com.runwear.shared.util.LocationFetchResult
import com.runwear.shared.util.UserLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-141 — the explicit "Current location" request.
 *
 * The owner's recording (Nov 8, 2026, Android vc41): viewing a
 * manually picked place (Seattle), three taps on the sheet's
 * Current location row produced a spinner and a closing sheet,
 * and the place never changed — the device was in New York.
 * Diagnosis (lane report): the fix was obtained and then REFUSED
 * at adoption — the DA-07 persistence guard
 * ([ColdStartPolicy.gpsAdoptionAllowed]) reads the persisted
 * record, the tap path never retired or outranked it, and the
 * saved manual place was silently re-applied.
 *
 * These tests pin the repaired behaviour at its production
 * seams:
 *  (1) an explicit request outranks the guard; an unsolicited
 *      one still does not (the launch rule is untouched);
 *  (2) a fix that resolves after the sheet is gone still comes
 *      back as an ApplyFix — the resolution is not owned by any
 *      UI lifecycle;
 *  (3) a failed fix yields the honest row message and carries
 *      no location to apply.
 */
class CurrentLocationRequestTest {

    private val seattle = UserLocation(47.6062, -122.3321)
    private val newYork = UserLocation(40.7128, -74.0060)

    // ── (1) The adoption guard: explicit vs unsolicited ────────

    @Test
    fun `an explicit request adopts over a saved manual pick`() {
        // The recording's exact state: the persisted record and
        // the place in view are both the manual pick.
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_MANUAL, ColdStartPolicy.SOURCE_MANUAL,
                explicitRequest = true
            )
        )
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_MANUAL, ColdStartPolicy.SOURCE_GPS,
                explicitRequest = true
            )
        )
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_GPS, ColdStartPolicy.SOURCE_MANUAL,
                explicitRequest = true
            )
        )
    }

    @Test
    fun `unsolicited adoption is still refused over a manual pick`() {
        // The Oct 22 / DA-07 launch rule, unchanged: a saved
        // manual place wins over live GPS on ordinary launches
        // and foreground revalidation.
        assertFalse(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_MANUAL, ColdStartPolicy.SOURCE_MANUAL
            )
        )
        assertFalse(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_MANUAL, ColdStartPolicy.SOURCE_GPS
            )
        )
        assertFalse(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_GPS, ColdStartPolicy.SOURCE_MANUAL
            )
        )
        // …while GPS places remain revalidatable, as before.
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_GPS, ColdStartPolicy.SOURCE_GPS
            )
        )
        assertTrue(ColdStartPolicy.gpsAdoptionAllowed(null, null))
        assertEquals(
            ColdStartPolicy.LaunchPlace.RESTORE_SAVED,
            ColdStartPolicy.launchPlace(hasSavedLocation = true)
        )
    }

    // ── (2) Resolution survives the sheet ──────────────────────

    @Test
    fun `a fix that resolves after the sheet is dismissed still applies`() = runBlocking {
        // The fix is still in flight when the sheet goes away.
        // The resolution runs in a scope that owes nothing to any
        // composition (in production: viewModelScope), so nothing
        // about the dismissal can cancel or drop it.
        val pendingFix = CompletableDeferred<LocationFetchResult>()
        val outcome = async {
            CurrentLocationFlow.resolve(
                lastKnown = { null },
                freshFix = { pendingFix.await() }
            )
        }
        // The sheet is dismissed here — there is no UI-side handle
        // left to complete or cancel anything. The fix then lands.
        pendingFix.complete(LocationFetchResult.Success(newYork))

        val resolved = outcome.await()
        assertTrue(resolved is CurrentLocationFlow.Outcome.ApplyFix)
        assertEquals(
            newYork,
            (resolved as CurrentLocationFlow.Outcome.ApplyFix).location
        )
    }

    @Test
    fun `a last known fix applies without waiting for a fresh one`() = runBlocking {
        var freshFixCalled = false
        val outcome = CurrentLocationFlow.resolve(
            lastKnown = { newYork },
            freshFix = {
                freshFixCalled = true
                LocationFetchResult.Success(seattle)
            }
        )
        assertTrue(outcome is CurrentLocationFlow.Outcome.ApplyFix)
        assertEquals(
            newYork,
            (outcome as CurrentLocationFlow.Outcome.ApplyFix).location
        )
        assertFalse("the fast path must not wait on the radio", freshFixCalled)
    }

    // ── (3) Honest failure ─────────────────────────────────────

    @Test
    fun `a failed fix produces the honest message and applies nothing`() = runBlocking {
        val outcome = CurrentLocationFlow.resolve(
            lastKnown = { null },
            freshFix = { LocationFetchResult.Error(LocationErrorReason.TIMEOUT) }
        )
        assertTrue(outcome is CurrentLocationFlow.Outcome.Failed)
        assertEquals(
            "We couldn't get a fix on your location. Try again in a moment.",
            (outcome as CurrentLocationFlow.Outcome.Failed).message
        )
        // A Failed carries no coordinates — there is nothing for
        // the caller to apply, by construction.
    }

    @Test
    fun `every failure reason has its own plain language message`() {
        assertEquals(
            "Location permission is off. Turn it on to use your current location.",
            LocationSheetState.currentLocationFailureMessage(
                LocationErrorReason.PERMISSION_DENIED
            )
        )
        assertEquals(
            "We couldn't get a fix on your location. Try again in a moment.",
            LocationSheetState.currentLocationFailureMessage(LocationErrorReason.TIMEOUT)
        )
        assertEquals(
            "Your current location isn't available right now. Try again in a moment.",
            LocationSheetState.currentLocationFailureMessage(
                LocationErrorReason.UNAVAILABLE
            )
        )
    }

    @Test
    fun `the row subtitle speaks only while the request has something to say`() {
        assertEquals(
            "Finding your location…",
            LocationSheetState.currentLocationRequestSubtitle(
                CurrentLocationRequestState.Locating
            )
        )
        assertEquals(
            "boom",
            LocationSheetState.currentLocationRequestSubtitle(
                CurrentLocationRequestState.Failed("boom")
            )
        )
        // Quiet states fall back to the permission-status subtitle.
        assertNull(
            LocationSheetState.currentLocationRequestSubtitle(
                CurrentLocationRequestState.Idle
            )
        )
        assertNull(
            LocationSheetState.currentLocationRequestSubtitle(
                CurrentLocationRequestState.Succeeded
            )
        )
    }
}

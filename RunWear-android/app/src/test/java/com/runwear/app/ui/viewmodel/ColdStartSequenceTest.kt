package com.runwear.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 WAVE A, ITEM 4 (DA-07) — cold-start state sequences.
 *
 * (1) SERVE ORDER: with a banked own photo, the FIRST hero state
 * of a relaunch is that photo — never the recorded blank gradient
 * → stock stranger → own photo sequence.
 *
 * (2) PLACE PERSISTENCE: a manually picked place survives a
 * simulated relaunch — the launch restores it, and neither the
 * cold-start GPS fast path nor foreground revalidation may adopt
 * (or persist over) it. The recording dropped Miami for the GPS
 * fix (Iredell), contradicting "RunWear reopens where you left
 * off".
 */
class ColdStartSequenceTest {

    /** Mirrors the persisted saved-location slot. */
    private data class SavedPlace(val name: String, val source: String)

    @Test
    fun `cold start with a banked photo renders it as the FIRST hero state`() {
        val bankedUrl = "https://signed.example/own-photo.jpg"
        val stockUrl = "bundled-fallback"
        // The seed's decision, through the production seam.
        val firstHero = ColdStartPolicy.firstHeroUrl(bankedUrl, stockUrl)
        assertEquals(bankedUrl, firstHero)
        assertTrue(ColdStartPolicy.firstHeroIsPersonal(firstHero))
        // The recorded sequence's middle state (stock first) is
        // unreachable when a banked photo exists.
        assertFalse(firstHero == stockUrl)
    }

    @Test
    fun `cold start without a banked photo seeds the stock fallback`() {
        val stockUrl = "bundled-fallback"
        assertEquals(stockUrl, ColdStartPolicy.firstHeroUrl(null, stockUrl))
        assertFalse(ColdStartPolicy.firstHeroIsPersonal(null))
        assertNull(ColdStartPolicy.firstHeroUrl(null, null))
    }

    @Test
    fun `a picked place survives a simulated relaunch`() {
        // Launch 1: the user picks Miami manually; the pick is the
        // persisted record (source "manual").
        var persisted = SavedPlace("Miami", ColdStartPolicy.SOURCE_MANUAL)
        var inViewSource: String? = ColdStartPolicy.SOURCE_MANUAL

        // Relaunch: a persisted place exists → restore it; GPS is
        // not consulted for the launch place at all.
        assertEquals(
            ColdStartPolicy.LaunchPlace.RESTORE_SAVED,
            ColdStartPolicy.launchPlace(hasSavedLocation = persisted != null)
        )
        var launchPlace = persisted

        // The cold-start GPS fast path produces a fix anyway
        // (Iredell, the recording): adoption must be REFUSED and
        // the persisted record must survive untouched.
        val adoptionAllowed = ColdStartPolicy.gpsAdoptionAllowed(
            persisted.source, inViewSource
        )
        assertFalse("GPS adoption over a manual pick is refused", adoptionAllowed)
        if (adoptionAllowed) {
            persisted = SavedPlace("Iredell", ColdStartPolicy.SOURCE_GPS)
            launchPlace = persisted
        }
        assertEquals("Miami", launchPlace.name)
        assertEquals("Miami", persisted.name)
        assertEquals(ColdStartPolicy.SOURCE_MANUAL, persisted.source)

        // Foreground revalidation with a far-away fix: same rule.
        assertFalse(
            ColdStartPolicy.gpsAdoptionAllowed(persisted.source, inViewSource)
        )
        assertEquals("Miami", persisted.name)
    }

    @Test
    fun `a GPS-saved place may still be revalidated by GPS`() {
        // The F1 stale-fix behaviour is preserved for GPS places:
        // only MANUAL picks are adoption-proof.
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                ColdStartPolicy.SOURCE_GPS, ColdStartPolicy.SOURCE_GPS
            )
        )
        assertTrue(ColdStartPolicy.gpsAdoptionAllowed(null, null))
        assertEquals(
            ColdStartPolicy.LaunchPlace.FETCH_GPS,
            ColdStartPolicy.launchPlace(hasSavedLocation = false)
        )
    }

    @Test
    fun `an explicit switch to GPS clears the pick first, so adoption opens`() {
        // switchToGPS() clears the manual record and sets the
        // in-view source to GPS before forcing a re-poll — the
        // guard must not trap the user's own switch.
        val persistedAfterClear: SavedPlace? = null
        assertTrue(
            ColdStartPolicy.gpsAdoptionAllowed(
                persistedAfterClear?.source, ColdStartPolicy.SOURCE_GPS
            )
        )
    }
}

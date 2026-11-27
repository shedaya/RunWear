package com.runwear.app.ui.viewmodel

import com.runwear.app.weariverun.WearIveRunRecordPolicy
import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.SimplePlace
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * RW-175 — manual place → Current Location, with the reverse
 * geocode landing AFTER the forecast (Sol's 2026-11-15 screenshot:
 * New York cold weather and kit under a "Miami Beach, FL" pill,
 * and a Wear I've Run entry recorded for Miami Beach).
 *
 * The sequence is modelled at the production seams the ViewModel
 * itself uses, in the order it uses them:
 *  1. Miami Beach is the committed manual selection.
 *  2. The GPS tap installs a pending selection at tap time
 *     ([gpsUnresolvedPendingPlace] — the existing "Your Location"
 *     fallback form) under a NEW selection generation; the old
 *     place's name is not inheritable by the new weather.
 *  3. The forecast wins the race: the fetch commit runs through
 *     [withFetchedConditions] with that unresolved pending place.
 *  4. The geocode lands later and resolves THE SAME selection,
 *     guarded by generation ([SelectionGeneration] /
 *     [gpsPlaceCommitDecision] / [withResolvedGpsPlace]) — not by
 *     exact Double equality against mutable coordinates.
 *  5. Wear I've Run recording ([WearIveRunRecordPolicy]) is
 *     gated on the selection's name being resolved.
 */
class Rw175LocationLabelTest {

    private fun weather(hour: Int, feelsLike: Double): WeatherConditions = WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 15, hour, 0),
        temperature = feelsLike + 6.0,
        feelsLike = feelsLike,
        humidity = 84,
        windSpeed = 6.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 0.0,
        weatherCode = WeatherCode.MAINLY_CLEAR
    )

    private fun outfit(w: WeatherConditions): OutfitRecommendation =
        OutfitRecommendation(
            weather = w,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.LIGHT_TIGHTS
        )

    private val miamiWeather = weather(6, 82.0)
    private val nyWeather = weather(6, 31.0)
    private val nyPlace = SimplePlace("New York", "NY")
    private val nowMoment: LocalDateTime = LocalDateTime.of(2026, 11, 15, 6, 0)

    private val miamiState = MainUiState(
        isLoading = false,
        weather = miamiWeather,
        outfit = outfit(miamiWeather),
        locationName = "Miami Beach, FL",
        simplePlace = SimplePlace("Miami Beach", "FL"),
        locationSource = "manual",
        placeNameResolved = true
    )

    /** The fetch commit for the GPS selection whose geocode has
     * not landed yet — exactly what fetchWeather performs when the
     * forecast wins the race against the reverse geocode. */
    private fun gpsFetchCommitBeforeGeocode(gpsGeneration: Long): MainUiState =
        miamiState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = gpsUnresolvedPendingPlace(),
            pendingSource = "gps",
            pendingPlaceNameResolved = false,
            pendingSelectionGeneration = gpsGeneration,
            locationNowAnchor = nowMoment,
            nowSelected = true
        )

    @Test
    fun `a - forecast before geocode never pairs the new weather with the old place name`() {
        val generations = SelectionGeneration()
        generations.next() // the Miami manual selection
        val gpsGeneration = generations.next() // the GPS tap

        val committed = gpsFetchCommitBeforeGeocode(gpsGeneration)

        // The weather IS New York's…
        assertSame(nyWeather, committed.weather)
        // …and the pill-bound fields name only the unresolved
        // GPS placeholder — never the previous place.
        assertNotEquals("Miami Beach, FL", committed.locationName)
        assertEquals("Your Location", committed.locationName)
        assertEquals(SimplePlace("Your Location"), committed.simplePlace)
        assertEquals("gps", committed.locationSource)
        assertFalse(committed.placeNameResolved)
        assertEquals(gpsGeneration, committed.selectionGeneration)
    }

    @Test
    fun `b - geocode landing after the forecast resolves the same selection`() {
        val generations = SelectionGeneration()
        generations.next()
        val gpsGeneration = generations.next()
        val committed = gpsFetchCommitBeforeGeocode(gpsGeneration)

        // The weather on screen already describes this selection's
        // fix, and the geocode belongs to the live generation:
        // it applies directly, as commitGpsPlace's direct branch.
        assertEquals(
            GpsPlaceCommit.DIRECT,
            gpsPlaceCommitDecision(
                generationIsCurrent = generations.isCurrent(gpsGeneration),
                weatherOnScreenDescribesFix = true
            )
        )
        val resolved = committed.withResolvedGpsPlace(nyPlace, gpsGeneration)

        assertEquals("New York, NY", resolved.locationName)
        assertEquals(nyPlace, resolved.simplePlace)
        assertEquals("gps", resolved.locationSource)
        assertTrue(resolved.placeNameResolved)
        // The name moved; the weather it labels did not.
        assertSame(nyWeather, resolved.weather)
    }

    @Test
    fun `b2 - geocode landing before the forecast commits atomically with its weather`() {
        val generations = SelectionGeneration()
        generations.next()
        val gpsGeneration = generations.next()

        // Weather does not yet describe the fix: the geocoded
        // place becomes the pending pair instead of a direct write.
        assertEquals(
            GpsPlaceCommit.PENDING,
            gpsPlaceCommitDecision(
                generationIsCurrent = generations.isCurrent(gpsGeneration),
                weatherOnScreenDescribesFix = false
            )
        )
        val committed = miamiState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = nyPlace,
            pendingSource = "gps",
            pendingPlaceNameResolved = true,
            pendingSelectionGeneration = gpsGeneration,
            locationNowAnchor = nowMoment,
            nowSelected = true
        )
        assertEquals("New York, NY", committed.locationName)
        assertEquals(nyPlace, committed.simplePlace)
        assertTrue(committed.placeNameResolved)
        assertSame(nyWeather, committed.weather)
    }

    @Test
    fun `b3 - a superseded geocode is dropped by generation even at identical coordinates`() {
        val generations = SelectionGeneration()
        val firstGps = generations.next()
        generations.next() // a manual pick supersedes it
        val secondGps = generations.next() // GPS re-adopted — SAME lat/lon doubles

        // The pre-fix guard compared exact doubles against
        // currentLat/currentLon: for the first geocode those
        // doubles match again after the re-adoption, so it would
        // have applied a stale name. Generation drops it whole.
        assertFalse(generations.isCurrent(firstGps))
        assertEquals(
            GpsPlaceCommit.DROP,
            gpsPlaceCommitDecision(
                generationIsCurrent = generations.isCurrent(firstGps),
                weatherOnScreenDescribesFix = true
            )
        )
        assertEquals(
            GpsPlaceCommit.DROP,
            gpsPlaceCommitDecision(
                generationIsCurrent = generations.isCurrent(firstGps),
                weatherOnScreenDescribesFix = false
            )
        )
        assertTrue(generations.isCurrent(secondGps))
    }

    @Test
    fun `c - wear ive run recording is suppressed while unresolved and records the resolved place`() {
        val generations = SelectionGeneration()
        generations.next()
        val gpsGeneration = generations.next()
        val committed = gpsFetchCommitBeforeGeocode(gpsGeneration)

        // While the name is unresolved the record gate refuses —
        // even at Now, where the pre-fix policy returned true
        // unconditionally. A mispaired entry is impossible.
        assertFalse(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = true,
                weatherMoment = nyWeather.dateTime,
                selectedMoment = nowMoment,
                placeNameResolved = committed.placeNameResolved
            )
        )

        // Once the geocode resolves the same selection, recording
        // is admitted and the entry's place — derived from the
        // same simplePlace the pill reads — is the current place.
        val resolved = committed.withResolvedGpsPlace(nyPlace, gpsGeneration)
        assertTrue(
            WearIveRunRecordPolicy.shouldRecord(
                isNowSelected = true,
                weatherMoment = nyWeather.dateTime,
                selectedMoment = nowMoment,
                placeNameResolved = resolved.placeNameResolved
            )
        )
        assertEquals("New York, NY", resolved.simplePlace?.display)
    }

    @Test
    fun `same-place refresh carry-over stays legitimate and keeps the resolution state`() {
        val generations = SelectionGeneration()
        val gpsGeneration = generations.next()
        val resolved = gpsFetchCommitBeforeGeocode(gpsGeneration)
            .withResolvedGpsPlace(nyPlace, gpsGeneration)

        // A refresh of the SAME place (no pending place) still
        // carries name/source over — and stays resolved.
        val refreshed = resolved.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = null,
            locationNowAnchor = nowMoment,
            nowSelected = true
        )
        assertEquals("New York, NY", refreshed.locationName)
        assertEquals("gps", refreshed.locationSource)
        assertTrue(refreshed.placeNameResolved)
        assertEquals(gpsGeneration, refreshed.selectionGeneration)

        // A refresh landing while a GPS name is still unresolved
        // must not silently resolve it either.
        val unresolved = gpsFetchCommitBeforeGeocode(gpsGeneration)
        val refreshedUnresolved = unresolved.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = null,
            locationNowAnchor = nowMoment,
            nowSelected = true
        )
        assertEquals("Your Location", refreshedUnresolved.locationName)
        assertFalse(refreshedUnresolved.placeNameResolved)
    }
}

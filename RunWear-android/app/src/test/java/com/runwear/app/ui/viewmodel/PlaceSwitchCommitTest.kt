package com.runwear.app.ui.viewmodel

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.SimplePlace
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * GEN-2 A2 (W-6): the atomic place-switch commit. The walkthrough
 * caught a frame pairing the "New York, NY" label with Kansas City's
 * "44° COOL" — selectLocation wrote the new name into state at tap
 * time while the weather still described the old place.
 * fetchWeather's success write is now the ONLY commit a switch
 * produces, and it is this pure function: the returned state carries
 * the new place AND the new weather together, or (no switch pending)
 * the old place with the new weather. There is no intermediate state
 * with the new name over old conditions.
 */
class PlaceSwitchCommitTest {

    private fun weather(
        hour: Int,
        feelsLike: Double,
        code: WeatherCode
    ): WeatherConditions = WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 4, hour, 0),
        temperature = feelsLike + 5.0,
        feelsLike = feelsLike,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = code
    )

    private fun outfit(weather: WeatherConditions): OutfitRecommendation =
        OutfitRecommendation(
            weather = weather,
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.LIGHT_TIGHTS
        )

    private val kcWeather = weather(8, 44.0, WeatherCode.MAINLY_CLEAR)
    private val nyWeather = weather(9, 58.0, WeatherCode.PARTLY_CLOUDY)

    private val kcState = MainUiState(
        isLoading = true,
        weather = kcWeather,
        outfit = outfit(kcWeather),
        locationName = "Kansas City, MO",
        simplePlace = SimplePlace("Kansas City", "MO")
    )

    @Test
    fun `a switch commits place and weather in the same state`() {
        val committed = kcState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        // The pairing the walkthrough frame violated: wherever the
        // New York name appears, the New York weather is already there.
        assertEquals("New York, NY", committed.locationName)
        assertEquals(SimplePlace("New York", "NY"), committed.simplePlace)
        assertSame(nyWeather, committed.weather)
        assertEquals(false, committed.isLoading)
        // NOW re-anchors on the response's own location-local time.
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.selectedDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.locationNow)
    }

    @Test
    fun `without a pending place the place fields carry over while conditions update`() {
        val refreshed = kcState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = null,
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        assertEquals("Kansas City, MO", refreshed.locationName)
        assertEquals(SimplePlace("Kansas City", "MO"), refreshed.simplePlace)
        assertSame(nyWeather, refreshed.weather)
    }

    // ── GEN-3 A-HERO (X-7 + WS-A(1)): the photo commits WITH the place ──
    // GEN-2 W-6 made label + weather + outfit atomic; the PHOTO still
    // lagged: the state's heroImageUrl kept the old place's photo
    // (pool or personal) until the async resolution wrote, so Miami's
    // label sat over New York's photo for ~1.3 s and the Wear I've
    // Run / collection / poster surfaces recorded the old photo under
    // the new place. The commit now swaps the hero in the same write:
    // the new place's own bundled fallback becomes the hero, and the
    // old place's personal layer is reset to the new cell's truth.

    private val kcHeroState = kcState.copy(
        heroImageUrl = "https://example.test/kc-personal.webp",
        heroFallbackUrl = "file:///android_asset/hero_fallback/cool_clear_female.webp",
        heroImageRowId = "kc-row",
        heroCombinationId = "kc-cell",
        heroRequestedCombinationId = "kc-cell",
        heroIsPersonal = true,
        personalHeroId = "kc-hero",
        personalHeroImageUrl = "https://example.test/kc-personal.webp",
        personalLookStatus = com.runwear.app.makeitme.PersonalLookStatus.Making,
        heroServeKind = com.runwear.app.makeitme.PersonalServeKind.SCAFFOLD
    )

    @Test
    fun `a switch swaps the hero to the new place's fallback in the same write`() {
        val nyFallback =
            "file:///android_asset/hero_fallback/mild_clear_female.webp"
        val committed = kcHeroState.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true,
            heroFallbackUrl = nyFallback
        )
        // Label + photo + fallback are the new place's, together.
        assertEquals("New York, NY", committed.locationName)
        assertEquals(nyFallback, committed.heroImageUrl)
        assertEquals(nyFallback, committed.heroFallbackUrl)
        assertEquals(nyFallback, committed.effectiveHeroUrl)
        // The old place's personal layer does not survive the switch.
        assertEquals(false, committed.heroIsPersonal)
        assertNull(committed.personalHeroId)
        assertNull(committed.personalHeroImageUrl)
        assertNull(committed.heroImageRowId)
        assertNull(committed.heroCombinationId)
        assertNull(committed.heroRequestedCombinationId)
        assertTrue(committed.heroCandidates.isEmpty())
        assertEquals(
            com.runwear.app.makeitme.PersonalLookStatus.None,
            committed.personalLookStatus
        )
        assertEquals(
            com.runwear.app.makeitme.PersonalServeKind.EXACT,
            committed.heroServeKind
        )
    }

    @Test
    fun `a same-place refresh keeps the hero and only re-derives the fallback`() {
        val newFallback =
            "file:///android_asset/hero_fallback/cool_cloudy_female.webp"
        val refreshed = kcHeroState.withFetchedConditions(
            weather = kcWeather,
            outfit = outfit(kcWeather),
            forecast = emptyList(),
            pendingPlace = null,
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 8, 0),
            nowSelected = true,
            heroFallbackUrl = newFallback
        )
        assertEquals("https://example.test/kc-personal.webp", refreshed.heroImageUrl)
        assertEquals(true, refreshed.heroIsPersonal)
        assertEquals(newFallback, refreshed.heroFallbackUrl)
        assertEquals(
            com.runwear.app.makeitme.PersonalLookStatus.Making,
            refreshed.personalLookStatus
        )
    }

    @Test
    fun `effectiveHeroUrl prefers the resolved hero and falls back to the bundled fallback`() {
        val unresolved = kcState.copy(
            heroImageUrl = null,
            heroFallbackUrl = "file:///android_asset/hero_fallback/cool_clear_female.webp"
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp",
            unresolved.effectiveHeroUrl
        )
        val resolved = unresolved.copy(heroImageUrl = "https://example.test/pool.webp")
        assertEquals("https://example.test/pool.webp", resolved.effectiveHeroUrl)
        assertNull(kcState.copy(heroImageUrl = null, heroFallbackUrl = null).effectiveHeroUrl)
    }

    // ── RW-146: the source commits WITH the place and weather ────
    // Pre-fix the source was written on its own timeline (at tap
    // time for manual picks, at fix time for GPS adoption, the
    // geocoded name later still), so the sheet's surfaces — GPS
    // row from the source, recents/footer from the name — could
    // disagree, and the pill read the old name over new weather.

    @Test
    fun `rw146 a switch commits its source in the same state as place and weather`() {
        val committed = kcState.copy(locationSource = "gps").withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            pendingSource = "manual",
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        assertEquals("New York, NY", committed.locationName)
        assertEquals("manual", committed.locationSource)
        assertSame(nyWeather, committed.weather)
    }

    @Test
    fun `rw146 a gps adoption commits the gps source with its place`() {
        val committed = kcState.copy(locationSource = "manual").withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            pendingSource = "gps",
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        assertEquals("New York, NY", committed.locationName)
        assertEquals("gps", committed.locationSource)
    }

    @Test
    fun `rw146 without a pending place the source carries over untouched`() {
        val refreshed = kcState.copy(locationSource = "manual").withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = null,
            pendingSource = "gps",
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = true
        )
        // A stray pendingSource with no pending place moves
        // nothing: name and source both stay with the old place.
        assertEquals("Kansas City, MO", refreshed.locationName)
        assertEquals("manual", refreshed.locationSource)
    }

    @Test
    fun `an explicit selection keeps its moment and only re-anchors locationNow`() {
        val picked = LocalDateTime.of(2026, 11, 5, 7, 30)
        val state = kcState.copy(selectedDateTime = picked, isNowSelected = false)
        val committed = state.withFetchedConditions(
            weather = nyWeather,
            outfit = outfit(nyWeather),
            forecast = emptyList(),
            pendingPlace = SimplePlace("New York", "NY"),
            locationNowAnchor = LocalDateTime.of(2026, 11, 4, 9, 0),
            nowSelected = false
        )
        assertEquals(picked, committed.selectedDateTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 9, 0), committed.locationNow)
        assertEquals("New York, NY", committed.locationName)
    }
}

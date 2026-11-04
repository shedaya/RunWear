package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.OutfitCombination
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TimeOfDay
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * GEN-2 A2 (W-4): the rotation-floor candidate obeys the garment
 * gate. Pre-fix, the bundled floor photo joined a floor cell's
 * rotation unchecked — the COOL floor asset wears the bracket
 * cold-end headband and gloves, so the rotation could deal it under
 * a 44°F prescription carrying neither, even when point-grain pool
 * rows for the prescription existed.
 */
class HeroFloorCandidateGateTest {

    private val repository = HeroImageRepository()

    private fun weatherFixture(): WeatherConditions = WeatherConditions(
        dateTime = kotlinx.datetime.LocalDateTime(2026, 11, 4, 12, 0),
        temperature = 45.0,
        feelsLike = 44.0,
        humidity = 50,
        windSpeed = 5.0,
        windGusts = 9.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 1.0,
        weatherCode = WeatherCode.MAINLY_CLEAR
    )

    private fun outfit(
        head: ClothingItem? = null,
        hands: ClothingItem? = null
    ): OutfitRecommendation = OutfitRecommendation(
        weather = weatherFixture(),
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.LIGHT_TIGHTS,
        head = head,
        hands = hands
    )

    private fun combination(timeOfDay: TimeOfDay): OutfitCombination = OutfitCombination(
        id = "FEMALE_CLEAR_COOL_${timeOfDay.name}_e216ba73",
        genderPreference = GenderPreference.FEMALE,
        weatherCode = WeatherCode.MAINLY_CLEAR,
        tempBracket = TempBracket.COOL,
        timeOfDay = timeOfDay,
        outfitHash = "e216ba73"
    )

    @Test
    fun `the floor candidate is refused when its garments contradict the prescription`() {
        // KC 44°F: 2-item prescription, COOL x CLEAR x MIDDAY floor
        // cell — the floor asset wears headband + gloves.
        assertNull(
            repository.gatedFloorCandidate(combination(TimeOfDay.MIDDAY), outfit())
        )
    }

    @Test
    fun `the floor candidate joins when its garments match the prescription`() {
        val candidate = repository.gatedFloorCandidate(
            combination(TimeOfDay.MIDDAY),
            outfit(head = ClothingItem.HEADBAND, hands = ClothingItem.LIGHT_GLOVES)
        )
        assertEquals(
            "file:///android_asset/hero_fallback/cool_clear_female.webp",
            candidate?.imageUrl
        )
        assertNull(candidate?.imageRowId)
    }

    @Test
    fun `no floor candidate outside floor cells, however well the garments match`() {
        assertNull(
            repository.gatedFloorCandidate(
                combination(TimeOfDay.NIGHT),
                outfit(head = ClothingItem.HEADBAND, hands = ClothingItem.LIGHT_GLOVES)
            )
        )
    }

    @Test
    fun `the floor cell classification the gate rides on is the established one`() {
        // COOL x CLEAR x MIDDAY is a floor cell; the gate adds the
        // garment condition ON TOP of HeroPoolPolicy, not instead of it.
        assertEquals(
            true,
            com.runwear.shared.domain.model.HeroPoolPolicy.isFloorCell(
                TempBracket.COOL, HeroWeatherCondition.CLEAR, TimeOfDay.MIDDAY
            )
        )
    }
}

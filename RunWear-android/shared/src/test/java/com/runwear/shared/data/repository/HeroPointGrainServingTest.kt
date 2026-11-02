package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import com.runwear.shared.domain.usecase.GetOutfitRecommendationUseCase
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RW-20 serving seam (2026-11-02): the wire decode of the backfilled
 * adjudication columns (person_count, garment_classes) and the
 * [pointGrainImages] filter every serving path draws from. A silent
 * decode miss would null the classes, and the filter fails closed on
 * unreadable garments — so a decode regression shows up here as a
 * red test, not as an empty app.
 */
class HeroPointGrainServingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun weather(feelsLikeF: Double): WeatherConditions = WeatherConditions(
        dateTime = LocalDateTime(2026, 11, 2, 6, 0),
        temperature = feelsLikeF,
        feelsLike = feelsLikeF,
        humidity = 77,
        windSpeed = 2.0,
        windGusts = 4.0,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 0.0,
        weatherCode = WeatherCode.CLEAR_SKY,
        isCelsius = false
    )

    /** Sol's 2026-11-02 field prescription: 39F, Light Long Sleeve + Light Tights. */
    private val cool39Outfit = OutfitRecommendation(
        weather = weather(39.0),
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.LIGHT_TIGHTS,
        head = ClothingItem.HEADBAND,
        hands = ClothingItem.LIGHT_GLOVES
    )

    private fun row(
        id: String,
        status: String? = "approved",
        personCount: Int? = 1,
        classes: GarmentClasses? = GarmentClasses(
            topBase = "long", outer = false, bottom = "tights"
        )
    ) = GeneratedImage(
        id = id,
        combinationId = "MALE_CLEAR_COOL_DAWN_$id",
        imageUrl = "https://example.test/$id.webp",
        reviewStatus = status,
        personCount = personCount,
        garmentClasses = classes
    )

    @Test
    fun `person count and garment classes decode from the wire`() {
        val decoded = json.decodeFromString<GeneratedImage>(
            """{"id":"72d6f96c","combination_id":"MALE_CLEAR_COOL_DAWN_v10",""" +
                """"image_url":"https://example.test/v10.webp","review_status":"approved",""" +
                """"person_count":1,"garment_classes":{"topBase":"long","outer":false,""" +
                """"mid":false,"bottom":"shorts","head":null,"hands":null,""" +
                """"sunglasses":false,"gaiter":false},"garment_source":"adjudicated"}"""
        )
        assertEquals(1, decoded.personCount)
        assertEquals("shorts", decoded.garmentClasses?.bottom)
        assertEquals("adjudicated", decoded.garmentSource)

        // A row from before the backfill decodes nulls.
        val legacy = json.decodeFromString<GeneratedImage>(
            """{"id":"a2","combination_id":"MALE_CLEAR_COOL_DUSK_v2",""" +
                """"image_url":"https://example.test/dusk_v2.webp"}"""
        )
        assertNull(legacy.personCount)
        assertNull(legacy.garmentClasses)
    }

    @Test
    fun `the field failure cell serves only the correct photo`() {
        // The five heroes Sol cycled through at 39F (diagnosis §2):
        val jacket = row("jacket", classes = GarmentClasses(topBase = "long", outer = true, bottom = "tights"))
        val forestShorts = row("forest", classes = GarmentClasses(topBase = "long", outer = false, bottom = "shorts"))
        val bridgeShorts = row("bridge", classes = GarmentClasses(topBase = "long", outer = false, bottom = "shorts"))
        val twoRunners = row("pair", personCount = 2)
        val duskShorts = row("dusk", classes = GarmentClasses(topBase = "long", outer = false, bottom = "shorts"))
        val correct = row("correct")

        val served = pointGrainImages(
            listOf(jacket, forestShorts, bridgeShorts, twoRunners, duskShorts, correct),
            cool39Outfit
        )
        assertEquals(listOf("correct"), served.map { it.id })
    }

    @Test
    fun `review gate still applies inside the point-grain filter`() {
        val pending = row("pending", status = "pending")
        val rejected = row("rejected", status = "rejected")
        assertTrue(pointGrainImages(listOf(pending, rejected), cool39Outfit).isEmpty())
    }

    @Test
    fun `Juneau rain serving - bare-handed rows must not serve when gloves are prescribed`() {
        val juneauWeather = WeatherConditions(
            dateTime = LocalDateTime(2026, 11, 5, 7, 0),
            temperature = 40.0,
            feelsLike = 32.0,
            humidity = 80,
            windSpeed = 11.0,
            windGusts = 15.0,
            precipitationProbability = 80,
            precipitation = 0.5,
            isRaining = true,
            isSnowing = false,
            cloudCover = 90,
            uvIndex = 0.0,
            weatherCode = WeatherCode.RAIN_MODERATE,
            isCelsius = false
        )
        val outfit = GetOutfitRecommendationUseCase().execute(juneauWeather, ComfortPreference.RUNS_WARM)
        assertEquals(ClothingItem.BASEBALL_CAP, outfit.head)
        assertEquals(ClothingItem.LIGHT_GLOVES, outfit.hands)
        assertEquals(ClothingItem.LIGHT_TIGHTS, outfit.bottom)
        assertEquals(ClothingItem.RAIN_JACKET, outfit.topOuter)
        assertEquals(ClothingItem.LONG_SLEEVE_LIGHT, outfit.topBase)

        fun juneauRow(id: String, combinationId: String, hands: String?) = GeneratedImage(
            id = id,
            combinationId = combinationId,
            imageUrl = "https://example.test/$id.webp",
            reviewStatus = "approved",
            personCount = 1,
            garmentClasses = GarmentClasses(topBase = "long", outer = true, bottom = "tights", head = "cap", hands = hands)
        )

        val served1 = juneauRow("served1", "MALE_RAIN_COOL_MIDDAY_v6", null)
        val served2 = juneauRow("served2", "MALE_RAIN_COOL_DUSK_v21", null)
        val served3 = juneauRow("served3", "MALE_RAIN_COOL_NIGHT_v13", null)
        val served4 = juneauRow("served4", "MALE_RAIN_COOL_DUSK_v14", null)
        val correct = juneauRow("correct", "MALE_RAIN_COLD_DAWN_vX", "gloves")

        val served = pointGrainImages(listOf(served1, served2, served3, served4, correct), outfit)
        assertEquals(listOf("correct"), served.map { it.id })
    }

    @Test
    fun `at 47F the same shorts rows pass and the tights row does not`() {
        val outfit47 = OutfitRecommendation(
            weather = weather(47.0),
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            bottom = ClothingItem.SHORTS
        )
        val shorts = row("shorts", classes = GarmentClasses(topBase = "long", outer = false, bottom = "shorts"))
        val tights = row("tights")
        val served = pointGrainImages(listOf(shorts, tights), outfit47)
        assertEquals(listOf("shorts"), served.map { it.id })
    }
}

package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroImageResult
import com.runwear.shared.domain.model.HeroImageSelector
import com.runwear.shared.domain.model.HeroServeRecord
import com.runwear.shared.domain.model.HeroServeSource
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.OutfitCombination
import com.runwear.shared.domain.model.TempBracket
import com.runwear.shared.domain.model.TimeOfDay
import com.runwear.shared.domain.model.WeatherCode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WS-7 / RW-26(c) — serve-source telemetry (D-18's attribution fix).
 *
 * Every hero serve emits exactly one record naming its source — pool /
 * bundled-fallback / personal / scaffold — with the cell, gender, and
 * served combination where applicable, and (for a bundled serve) the
 * asset file + manifest provenance. These tests pin that each serve
 * kind's record is classified and emitted through [HeroServeTelemetry],
 * the channel the repository resolves through and the view model
 * paints through.
 *
 * WS-9 fix (test authenticity — what these tests do and do NOT
 * prove): the pool/bundled cases drive [HeroServeTelemetry.emitForResult],
 * the production classification HeroImageRepository calls at serve
 * time (its getHeroImage emission sites); the personal/scaffold case
 * drives [HeroServeTelemetry.personalServeRecord] + [emit], the
 * production construction both of MainViewModel's personal serve
 * sites (resolve-time overlay, poll delivery) build and emit through.
 * The serve PATHS themselves are not driven: the repository performs
 * live Supabase network I/O by design, and the view model's personal
 * layer is compile-time gated OFF in this build
 * (MakeItMeAvailability) behind concrete network repositories — so
 * the claim of this suite is the classification, construction, and
 * emission of the records, verified against the seams production
 * calls, not an end-to-end serve.
 */
class HeroServeTelemetryTest {

    private val captured = CopyOnWriteArrayList<HeroServeRecord>()

    @Before
    fun setUp() {
        HeroServeTelemetry.clear()
        captured.clear()
        HeroServeTelemetry.installSink { captured.add(it) }
    }

    @After
    fun tearDown() {
        HeroServeTelemetry.installSink(HeroServeTelemetry.DEFAULT_SINK)
        HeroServeTelemetry.clear()
    }

    private val combination = OutfitCombination(
        id = "MALE_CLEAR_COOL_MIDDAY_abcd1234",
        genderPreference = GenderPreference.MALE,
        weatherCode = WeatherCode.MAINLY_CLEAR,
        tempBracket = TempBracket.COOL,
        timeOfDay = TimeOfDay.MIDDAY,
        outfitHash = "abcd1234"
    )

    @Test
    fun `a pool serve emits a pool record with its row identity`() {
        val result = HeroImageResult(
            imageUrl = "https://cdn.example/pool/row-1.webp",
            thumbnailUrl = "https://cdn.example/pool/row-1.webp",
            combinationId = combination.id,
            imageRowId = "row-1",
            servedCombinationId = "MALE_CLEAR_COOL_MIDDAY_ffff0000",
            serveSource = HeroServeSource.POOL
        )
        HeroServeTelemetry.emitForResult(result, combination, trigger = "pool-cascade")
        assertEquals(1, captured.size)
        val record = captured.single()
        assertEquals(HeroServeSource.POOL, record.source)
        assertEquals("pool", record.source.wire)
        assertEquals("row-1", record.imageRowId)
        assertEquals(TempBracket.COOL, record.tempBracket)
        assertEquals(HeroWeatherCondition.CLEAR, record.weatherCondition)
        assertEquals(GenderPreference.MALE, record.gender)
        assertEquals("pool-cascade", record.trigger)
        assertTrue(record.summary().contains("source=pool"))
    }

    @Test
    fun `a bundled-fallback serve emits a bundled record with asset and provenance`() {
        val url = HeroImageSelector.pairedFallbackAsset(
            TempBracket.COOL, HeroWeatherCondition.CLEAR, GenderPreference.MALE
        )!!
        val result = HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id,
            serveSource = HeroServeSource.BUNDLED_FALLBACK,
            fallbackProvenance = HeroImageSelector.fallbackProvenance(url),
            fallbackAssetFile = HeroImageSelector.fallbackAssetFile(url)
        )
        HeroServeTelemetry.emitForResult(result, combination, trigger = "bundled-fallback")
        assertEquals(1, captured.size)
        val record = captured.single()
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, record.source)
        assertEquals("bundled-fallback", record.source.wire)
        assertEquals("cool_clear_male", record.fallbackAssetFile)
        assertEquals(
            "f4b95431-0943-435b-95c7-304b95762e51",
            record.fallbackProvenance?.sourceRowId
        )
        assertEquals("MALE_CLEAR_COOL_MIDDAY_84c10525", record.fallbackProvenance?.combinationId)
        val summary = record.summary()
        assertTrue(summary.contains("source=bundled-fallback"))
        assertTrue(summary.contains("asset=cool_clear_male"))
        assertTrue(summary.contains("provenance_row=f4b95431-0943-435b-95c7-304b95762e51"))
        assertTrue(summary.contains("rung=T1"))
    }

    @Test
    fun `a bundled URL is recorded as bundled even when the result is unlabelled`() {
        // The URL is the ground truth: a rotation-floor pick of the
        // bundled candidate arrives with no serveSource label and no
        // pool row id — it must still attribute as bundled-fallback.
        val url = "file:///android_asset/hero_fallback/cool_snow_male.webp"
        val result = HeroImageResult(
            imageUrl = url,
            thumbnailUrl = url,
            combinationId = combination.id
        )
        HeroServeTelemetry.emitForResult(result, combination, trigger = "pool-cascade")
        val record = captured.single()
        assertEquals(HeroServeSource.BUNDLED_FALLBACK, record.source)
        assertEquals("cool_snow_male", record.fallbackAssetFile)
        assertEquals(
            "f8e3791a-6fe2-472c-ad80-e058a00ed792",
            record.fallbackProvenance?.sourceRowId
        )
    }

    @Test
    fun `personal and scaffold serves emit the records the production seam builds`() {
        // WS-9 fix: these records are BUILT by the production seam
        // (HeroServeTelemetry.personalServeRecord) — the same factory
        // MainViewModel's overlay and poll-delivery sites call — not
        // hand-built here. The assertions consume what production
        // produced: classification (exact → PERSONAL, scaffold →
        // SCAFFOLD), identity, gender, and trigger.
        HeroServeTelemetry.emit(
            HeroServeTelemetry.personalServeRecord(
                isScaffold = false,
                requestedCombinationId = combination.id,
                servedCombinationId = "MALE_CLEAR_COOL_MIDDAY_ffff0000",
                gender = GenderPreference.MALE,
                trigger = "personal-overlay"
            )
        )
        HeroServeTelemetry.emit(
            HeroServeTelemetry.personalServeRecord(
                isScaffold = true,
                requestedCombinationId = combination.id,
                servedCombinationId = "MALE_CLEAR_COOL_MIDDAY_ffff0000",
                gender = GenderPreference.MALE,
                trigger = "personal-overlay"
            )
        )
        // The poll-delivery site builds its record through the same
        // seam, keyed on the cell (no served combination).
        HeroServeTelemetry.emit(
            HeroServeTelemetry.personalServeRecord(
                isScaffold = true,
                requestedCombinationId = combination.id,
                gender = GenderPreference.MALE,
                trigger = "personal-poll-delivery"
            )
        )
        assertEquals(3, captured.size)
        assertEquals(HeroServeSource.PERSONAL, captured[0].source)
        assertEquals("personal", captured[0].source.wire)
        assertEquals(combination.id, captured[0].requestedCombinationId)
        assertEquals(
            "MALE_CLEAR_COOL_MIDDAY_ffff0000",
            captured[0].servedCombinationId
        )
        assertEquals(GenderPreference.MALE, captured[0].gender)
        assertEquals("personal-overlay", captured[0].trigger)
        assertTrue(captured[0].summary().contains("source=personal"))
        assertEquals(HeroServeSource.SCAFFOLD, captured[1].source)
        assertEquals("scaffold", captured[1].source.wire)
        assertEquals("personal-overlay", captured[1].trigger)
        assertTrue(captured[1].summary().contains("source=scaffold"))
        assertEquals(HeroServeSource.SCAFFOLD, captured[2].source)
        assertEquals("personal-poll-delivery", captured[2].trigger)
        assertEquals(null, captured[2].servedCombinationId)
        // History retains all three, in order — the after-the-fact channel.
        assertEquals(captured.toList(), HeroServeTelemetry.recent())
    }

    @Test
    fun `a result with no image emits nothing`() {
        HeroServeTelemetry.emitForResult(
            HeroImageResult(imageUrl = null, thumbnailUrl = null, combinationId = "unknown"),
            combination,
            trigger = "pool-cascade"
        )
        assertTrue(captured.isEmpty())
    }
}

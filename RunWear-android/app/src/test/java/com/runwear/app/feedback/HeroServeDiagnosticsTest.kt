package com.runwear.app.feedback

import com.runwear.shared.domain.model.FallbackAssetProvenance
import com.runwear.shared.domain.model.GenderPreference
import com.runwear.shared.domain.model.HeroServeRecord
import com.runwear.shared.domain.model.HeroServeSource
import com.runwear.shared.domain.model.HeroWeatherCondition
import com.runwear.shared.domain.model.TempBracket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS-7 / RW-26(c): the remote half of serve-source telemetry — a
 * bundled-fallback serve files under the automated `diagnostics`
 * category (RW-18's routing: automated filings never land in a human
 * category), carrying the serve record's summary as its text and the
 * asset + provenance in its bundle, so the serve is attributable
 * after the fact (D-18).
 */
class HeroServeDiagnosticsTest {

    private val record = HeroServeRecord(
        source = HeroServeSource.BUNDLED_FALLBACK,
        requestedCombinationId = "MALE_CLEAR_COOL_MIDDAY_abcd1234",
        tempBracket = TempBracket.COOL,
        weatherCondition = HeroWeatherCondition.CLEAR,
        gender = GenderPreference.MALE,
        fallbackAssetFile = "cool_clear_male",
        fallbackProvenance = FallbackAssetProvenance(
            combinationId = "MALE_CLEAR_COOL_MIDDAY_84c10525",
            sourceRowId = "f4b95431-0943-435b-95c7-304b95762e51",
            rung = "T1"
        ),
        trigger = "resolve-timeout"
    )

    @Test
    fun `bundled serve payload files under diagnostics with the record as its text`() {
        val payload = HeroServeDiagnostics.payload(record, appVersion = "1.2.15", installId = "install-1")
        assertEquals("diagnostics", payload.getString("category"))
        assertEquals("hero_serve", payload.getString("entryPoint"))
        assertEquals(record.summary(), payload.getString("text"))
        assertTrue(payload.getString("text").contains("source=bundled-fallback"))
        assertTrue(payload.getString("text").contains("asset=cool_clear_male"))
        assertTrue(payload.getString("text").contains("trigger=resolve-timeout"))
        // Automated filing: never a human category, no reply email.
        assertFalse(payload.has("replyEmail"))
        assertFalse(
            FeedbackCategory.values().any { it.id == payload.getString("category") }
        )
    }

    @Test
    fun `payload bundle stays inside the intake allowlist - all serve facts ride in the text`() {
        val payload = HeroServeDiagnostics.payload(record, appVersion = "1.2.15", installId = "install-1")
        val bundle = payload.getJSONObject("bundle")
        // runwear-feedback/1 rejects unknown bundle keys with 422, so
        // the bundle is exactly the allowlisted engineVersion (the
        // same shape RawCrashBootstrap's diagnostics filings use).
        assertEquals(setOf("engineVersion"), bundle.keys().asSequence().toSet())
        // The text carries every serve fact: source, cell, gender,
        // combinations, asset, provenance combination + row, rung,
        // and trigger.
        val text = payload.getString("text")
        assertTrue(text.contains("bracket=COOL"))
        assertTrue(text.contains("weather=CLEAR"))
        assertTrue(text.contains("gender=MALE"))
        assertTrue(text.contains("requested=MALE_CLEAR_COOL_MIDDAY_abcd1234"))
        assertTrue(text.contains("provenance_combination=MALE_CLEAR_COOL_MIDDAY_84c10525"))
        assertTrue(text.contains("provenance_row=f4b95431-0943-435b-95c7-304b95762e51"))
        assertTrue(text.contains("rung=T1"))
    }

    @Test
    fun `payload carries the standard telemetry envelope`() {
        val payload = HeroServeDiagnostics.payload(record, appVersion = "1.2.15", installId = "install-1")
        assertEquals(TelemetryConfig.FEEDBACK_SCHEMA, payload.getString("schema"))
        assertEquals(TelemetryConfig.APP_ID, payload.getString("app"))
        assertEquals("android", payload.getString("platform"))
        assertEquals("1.2.15", payload.getString("appVersion"))
        assertEquals("install-1", payload.getString("installId"))
    }
}

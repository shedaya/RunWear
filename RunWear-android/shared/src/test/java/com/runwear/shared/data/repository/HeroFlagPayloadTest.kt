package com.runwear.shared.data.repository

import com.runwear.shared.domain.model.HeroFlagReason
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The hero-flag contract: a flag must identify the EXACT photo on
 * screen (pool row id for pool photos, URL + 'fallback' marker for
 * curated ones), carry the reason codes the hero_flags CHECK
 * constraint accepts, and submit fire-and-forget — one silent retry,
 * never an exception to the caller.
 */
class HeroFlagPayloadTest {

    private val poolUrl =
        "https://ebicqznlcjbqcukjfzcf.supabase.co/storage/v1/object/public/hero-images/MALE_CLEAR_MILD_DUSK_v5/11c5bac0-b83a-4ee1-b018-799dd6ec9fc2"
    private val fallbackUrl =
        "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=800&h=1200&fit=crop"
    private val rowId = "7367eebd-47ff-4338-a7a0-0a5a64e3d9b2"
    private val ts = "2026-10-29T22:30:00Z"

    @Test
    fun `pool photo carries its row id and the served row's own combination`() {
        val payload = HeroFlagPayload.build(
            imageUrl = poolUrl,
            imageRowId = rowId,
            servedCombinationId = "MALE_CLEAR_MILD_DUSK_v5",
            requestedCombinationId = "MALE_CLEAR_MILD_DUSK_a1b2c3d4",
            reason = HeroFlagReason.LOOKS_BROKEN,
            appVersion = "1.2.2",
            createdAtIso = ts
        )!!
        assertEquals(rowId, payload.imageRowId)
        assertEquals(poolUrl, payload.imageUrl)
        // The served cell wins over the requested combination: the
        // cascade may have served a neighbouring cell's photo.
        assertEquals("MALE_CLEAR_MILD_DUSK_v5", payload.combinationId)
        assertEquals(HeroFlagPayload.SOURCE_POOL, payload.imageSource)
        assertEquals("looks_broken", payload.reason)
        assertEquals("Android", payload.platform)
        assertEquals("1.2.2", payload.appVersion)
        assertEquals(ts, payload.createdAt)
    }

    @Test
    fun `fallback photo has no row id and is marked non-pool`() {
        val payload = HeroFlagPayload.build(
            imageUrl = fallbackUrl,
            imageRowId = null,
            servedCombinationId = null,
            requestedCombinationId = "FEMALE_CLEAR_MILD_MIDDAY_0badc0de",
            reason = HeroFlagReason.OUTFIT_MISMATCH,
            appVersion = "1.2.2",
            createdAtIso = ts
        )!!
        assertNull(payload.imageRowId)
        assertEquals(fallbackUrl, payload.imageUrl)
        assertEquals("FEMALE_CLEAR_MILD_MIDDAY_0badc0de", payload.combinationId)
        assertEquals(HeroFlagPayload.SOURCE_FALLBACK, payload.imageSource)
        assertEquals("outfit_mismatch", payload.reason)
    }

    @Test
    fun `blank row id is treated as non-pool`() {
        val payload = HeroFlagPayload.build(
            imageUrl = fallbackUrl,
            imageRowId = "  ",
            servedCombinationId = null,
            requestedCombinationId = null,
            reason = HeroFlagReason.INAPPROPRIATE,
            appVersion = null,
            createdAtIso = ts
        )!!
        assertNull(payload.imageRowId)
        assertEquals(HeroFlagPayload.SOURCE_FALLBACK, payload.imageSource)
        assertNull(payload.combinationId)
        assertNull(payload.appVersion)
    }

    @Test
    fun `no photo on screen means no flag`() {
        assertNull(
            HeroFlagPayload.build(
                imageUrl = null,
                imageRowId = rowId,
                servedCombinationId = null,
                requestedCombinationId = null,
                reason = HeroFlagReason.LOOKS_BROKEN,
                appVersion = "1.2.2",
                createdAtIso = ts
            )
        )
        assertNull(
            HeroFlagPayload.build(
                imageUrl = "   ",
                imageRowId = null,
                servedCombinationId = null,
                requestedCombinationId = null,
                reason = HeroFlagReason.LOOKS_BROKEN,
                appVersion = "1.2.2",
                createdAtIso = ts
            )
        )
    }

    @Test
    fun `reason codes match the hero_flags check constraint exactly`() {
        assertEquals("looks_broken", HeroFlagReason.LOOKS_BROKEN.code)
        assertEquals("inappropriate", HeroFlagReason.INAPPROPRIATE.code)
        assertEquals("outfit_mismatch", HeroFlagReason.OUTFIT_MISMATCH.code)
    }

    @Test
    fun `insert serializes to the table's snake_case columns`() {
        val payload = HeroFlagPayload.build(
            imageUrl = poolUrl,
            imageRowId = rowId,
            servedCombinationId = "MALE_CLEAR_MILD_DUSK_v5",
            requestedCombinationId = null,
            reason = HeroFlagReason.INAPPROPRIATE,
            appVersion = "1.2.2",
            createdAtIso = ts
        )!!
        val json = Json.encodeToString(HeroFlagInsert.serializer(), payload).let { Json.parseToJsonElement(it).jsonObject }
        assertEquals(rowId, json["image_row_id"]?.jsonPrimitive?.content)
        assertEquals(poolUrl, json["image_url"]?.jsonPrimitive?.content)
        assertEquals("MALE_CLEAR_MILD_DUSK_v5", json["combination_id"]?.jsonPrimitive?.content)
        assertEquals("pool", json["image_source"]?.jsonPrimitive?.content)
        assertEquals("inappropriate", json["reason"]?.jsonPrimitive?.content)
        assertEquals("Android", json["platform"]?.jsonPrimitive?.content)
        assertEquals("1.2.2", json["app_version"]?.jsonPrimitive?.content)
        assertEquals(ts, json["created_at"]?.jsonPrimitive?.content)
    }

    @Test
    fun `submit sends once when the first attempt succeeds`() = runBlocking {
        val calls = AtomicInteger(0)
        HeroFlagPayload.submit(samplePayload()) { calls.incrementAndGet() }
        assertEquals(1, calls.get())
    }

    @Test
    fun `submit retries once after a failure and then succeeds`() = runBlocking {
        val calls = AtomicInteger(0)
        HeroFlagPayload.submit(samplePayload()) {
            if (calls.incrementAndGet() == 1) throw RuntimeException("network down")
        }
        assertEquals(2, calls.get())
    }

    @Test
    fun `submit swallows persistent failure after the retry budget`() = runBlocking {
        val calls = AtomicInteger(0)
        // Must return normally — a flag can never surface an error.
        HeroFlagPayload.submit(samplePayload()) {
            calls.incrementAndGet()
            throw RuntimeException("network down")
        }
        assertEquals(HeroFlagPayload.MAX_SEND_ATTEMPTS, calls.get())
        assertTrue(calls.get() >= 1)
    }

    private fun samplePayload(): HeroFlagInsert = HeroFlagPayload.build(
        imageUrl = poolUrl,
        imageRowId = rowId,
        servedCombinationId = "MALE_CLEAR_MILD_DUSK_v5",
        requestedCombinationId = null,
        reason = HeroFlagReason.LOOKS_BROKEN,
        appVersion = "1.2.2",
        createdAtIso = ts
    )!!
}

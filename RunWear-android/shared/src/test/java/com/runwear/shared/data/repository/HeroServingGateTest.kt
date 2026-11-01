package com.runwear.shared.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rejected-row exclusion gate (Oct 30, 2026 vest incident).
 *
 * Sol's phone was served a hi-vis-vest hero from his MALE × CLEAR ×
 * MILD × DAWN slot twice in one morning. Pool-side the slot was
 * already clean: all five of its rows were review_status='rejected'
 * and it held no approved rows at all. The serving lineages on his
 * phone (1.2.1 production, 1.2.3 internal) branched before the
 * 2026-10-27 review gate landed and fetched generated_images with no
 * status filter at all, so an unfiltered random pick in that slot
 * landed on a rejected row with certainty. A client honoring the gate
 * finds the slot EMPTY after filtering and cascades to the cell's
 * approved DUSK/MIDDAY rows instead.
 *
 * These tests pin the two seams that gate depends on: the wire decode
 * (review_status must actually arrive on the DTO — a silent decode
 * miss would null it, and null counts as approved) and the selection
 * filter every serving path draws from ([servableImages]).
 */
class HeroServingGateTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun row(id: String, status: String?) = GeneratedImage(
        id = id,
        combinationId = "MALE_CLEAR_MILD_DAWN_$id",
        imageUrl = "https://example.test/$id.png",
        reviewStatus = status
    )

    @Test
    fun `review status decodes from the wire`() {
        val rejected = json.decodeFromString<GeneratedImage>(
            """{"id":"d98e7b91","combination_id":"MALE_CLEAR_MILD_DAWN_v2",""" +
                """"image_url":"https://example.test/dawn_v2.png","review_status":"rejected"}"""
        )
        assertEquals("rejected", rejected.reviewStatus)

        val approved = json.decodeFromString<GeneratedImage>(
            """{"id":"a1","combination_id":"MALE_CLEAR_MILD_DUSK_v1",""" +
                """"image_url":"https://example.test/dusk_v1.png","review_status":"approved"}"""
        )
        assertEquals("approved", approved.reviewStatus)

        // A row from before the column existed decodes null (legacy).
        val legacy = json.decodeFromString<GeneratedImage>(
            """{"id":"a2","combination_id":"MALE_CLEAR_MILD_DUSK_v2",""" +
                """"image_url":"https://example.test/dusk_v2.png"}"""
        )
        assertNull(legacy.reviewStatus)
    }

    @Test
    fun `a slot whose only rows are rejected serves nothing`() {
        // Sol's DAWN slot on the morning of Oct 30: five rows, all
        // rejected, zero approved. The gate must yield an empty list
        // so the cascade moves on — never a vest.
        val dawnSlot = (1..5).map { row("dawn_v$it", "rejected") }
        assertTrue(servableImages(dawnSlot).isEmpty())
    }

    @Test
    fun `pending rows are excluded alongside rejected`() {
        val rows = listOf(
            row("p1", "pending"),
            row("r1", "rejected"),
            row("p2", "pending")
        )
        assertTrue(servableImages(rows).isEmpty())
    }

    @Test
    fun `mixed rows keep only approved and legacy, in pool order`() {
        val rows = listOf(
            row("newest", "rejected"),
            row("ok1", "approved"),
            row("pend", "pending"),
            row("legacy", null),
            row("ok2", "APPROVED")
        )
        assertEquals(
            listOf("ok1", "legacy", "ok2"),
            servableImages(rows).map { it.id }
        )
    }
}

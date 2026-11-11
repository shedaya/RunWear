package com.runwear.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.runwear.app.weariverun.HeroRecovery
import com.runwear.app.weariverun.HeroRefResolver
import com.runwear.app.weariverun.WearIveRunCollectionLogic
import com.runwear.app.weariverun.WearIveRunDayEntry
import com.runwear.app.weariverun.WearIveRunStore
import com.runwear.shared.domain.model.ClothingItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/**
 * RW-166 (Nov 10, 2026): the Android entry→bank re-link recovery —
 * pure candidate construction, the never-fabricate rules, the
 * cover-after-heal model behaviour, and the store heal's
 * persistence. The fixture is Sol's device shape exactly: a
 * New York entry settled with NO hero reference whose outfit
 * cell IS banked, and a Philadelphia entry settled WITH one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class Rw166UnitTest {

    private val nyZone: ZoneId = ZoneId.of("America/New_York")

    /** Real engine item KEYS — the enum case names the settle
     * persists (`outfit.allItems.map { it.key }`, key == name).
     * Display names would hash a different signature than the
     * one the bank cell was keyed from. */
    private val kitKeys = listOf(
        "LONG_SLEEVE_LIGHT",
        "SHORTS",
        "LIGHT_BEANIE",
        "SUNGLASSES"
    )

    private fun emptyRefEntry(
        date: LocalDate = LocalDate.of(2026, 11, 9),
        place: String = "New York, NY",
        feelsLikeF: Double = 58.0,
        conditionLabel: String = "Clear sky",
        momentLabel: String? = "9 a.m.",
        itemKeys: List<String> = kitKeys,
        settledAtMillis: Long = 1_762_696_800_000L // 2025-11-09T14:00Z
    ) = WearIveRunDayEntry(
        date = date,
        place = place,
        feelsLikeF = feelsLikeF,
        conditionLabel = conditionLabel,
        isSnowing = false,
        isRaining = false,
        items = itemKeys,
        itemKeys = itemKeys,
        heroRef = null,
        heroDurableRef = null,
        momentLabel = momentLabel,
        settledAtMillis = settledAtMillis,
        sessionId = "rw166-$date"
    )

    private fun bankFor(entry: WearIveRunDayEntry): Pair<String, String> {
        val id = HeroRecovery.candidateCombinationIds(
            entry, "MALE", nyZone
        ).first()
        return id to "user-1/heroes/$id/v1.png"
    }

    @Test
    fun fixtureKeysAreRealEngineItemKeys() {
        val keys = ClothingItem.entries.map { it.key }.toSet()
        kitKeys.forEach { assertTrue("$it is not an engine item key", it in keys) }
        // And the hash over the keys equals the engine's own
        // cell hash over the same items (fromConditions hashes
        // `allItems.map { it.name }` — the same strings).
        val engineHash = kitKeys.map { key ->
            ClothingItem.entries.first { it.key == key }
        }.map { it.name }.sorted().joinToString("-").hashCode()
            .toUInt().toString(16).takeLast(8).padStart(8, '0')
        assertEquals(engineHash, HeroRecovery.outfitHash(kitKeys))
    }

    @Test
    fun exactReconstructionLeadsTheCandidates() {
        val entry = emptyRefEntry()
        val ids = HeroRecovery.candidateCombinationIds(entry, "MALE", nyZone)
        // 58°F → MILD (exact bracket); "Clear sky" → CLEAR first;
        // "9 a.m." → hour 9 → DAWN. The engine's own id shape.
        val hash = HeroRecovery.outfitHash(kitKeys)
        assertEquals(
            "MALE_CLEAR_MILD_DAWN_$hash",
            ids.first()
        )
        // Outfit/bracket never vary across candidates.
        assertTrue(ids.all { it.startsWith("MALE_") && it.contains("_MILD_") })
        assertTrue(ids.all { it.endsWith("_$hash") })
    }

    @Test
    fun recoveredReferenceFindsTheBankedCell() {
        val entry = emptyRefEntry()
        val (id, path) = bankFor(entry)
        assertEquals(
            path,
            HeroRecovery.recoveredDurableReference(
                entry, "MALE", mapOf(id to path), nyZone
            )
        )
    }

    @Test
    fun neverFabricatesAcrossBracketsOrOutfits() {
        val entry = emptyRefEntry()
        val hash = HeroRecovery.outfitHash(kitKeys)
        // Same outfit, WRONG bracket (HOT instead of MILD): a
        // different look — must not be served.
        val wrongBracket = mapOf(
            "MALE_CLEAR_HOT_DAWN_$hash" to "user-1/heroes/x/v1.png"
        )
        assertNull(
            HeroRecovery.recoveredDurableReference(
                entry, "MALE", wrongBracket, nyZone
            )
        )
        // Same bracket, DIFFERENT outfit: not this entry's photo.
        val otherOutfit = mapOf(
            "MALE_CLEAR_MILD_DAWN_deadbeef" to "user-1/heroes/y/v1.png"
        )
        assertNull(
            HeroRecovery.recoveredDurableReference(
                entry, "MALE", otherOutfit, nyZone
            )
        )
        // Empty bank → honestly empty (the unrecoverable class).
        assertNull(
            HeroRecovery.recoveredDurableReference(
                entry, "MALE", emptyMap(), nyZone
            )
        )
    }

    @Test
    fun anEntryWithALoadableReferenceIsNeverRelinked() {
        val (id, path) = bankFor(emptyRefEntry())
        val signed = emptyRefEntry().copy(
            heroRef = "https://x.supabase.co/storage/v1/object/sign/" +
                "personal-heroes/user-1/heroes/other/v3.png?token=t",
            heroDurableRef = "user-1/heroes/other/v3.png"
        )
        assertNull(
            HeroRecovery.recoveredDurableReference(
                signed, "MALE", mapOf(id to path), nyZone
            )
        )
    }

    @Test
    fun keylessEntryHasNoCandidates() {
        val entry = emptyRefEntry(itemKeys = emptyList())
        assertTrue(
            HeroRecovery.candidateCombinationIds(entry, "MALE", nyZone)
                .isEmpty()
        )
        assertNull(
            HeroRecovery.recoveredDurableReference(
                entry, "MALE", mapOf("any" to "p"), nyZone
            )
        )
    }

    @Test
    fun weatherAndTimeCandidateSetsFollowTheLabels() {
        // Rain label → RAIN leads; boundary neighbour only for
        // CLEAR/CLOUDY.
        assertEquals(
            "RAIN",
            HeroRecovery.weatherTokens("Rain").first()
        )
        assertEquals(
            listOf("CLEAR", "CLOUDY"),
            HeroRecovery.weatherTokens("Clear sky").take(2)
        )
        assertEquals(
            listOf("CLOUDY", "CLEAR"),
            HeroRecovery.weatherTokens("Partly cloudy").take(2)
        )
        // Clock labels parse to their hour's band.
        assertEquals(21, HeroRecovery.parseClockHour("9 p.m."))
        assertEquals(0, HeroRecovery.parseClockHour("12 a.m."))
        assertEquals(12, HeroRecovery.parseClockHour("12 p.m."))
        assertNull(HeroRecovery.parseClockHour("this morning"))
        // A no-label entry falls back to its settled instant:
        // 14:00Z in New York (EST) = 09:00 → DAWN.
        val noLabel = emptyRefEntry(momentLabel = null)
        assertEquals(
            "DAWN",
            HeroRecovery.timeTokens(noLabel, nyZone).first()
        )
        // Legacy shape (no label, settledAtMillis 0): full set,
        // no primary claimed.
        val legacy = emptyRefEntry(momentLabel = null, settledAtMillis = 0L)
        assertEquals(
            listOf("DAWN", "MIDDAY", "DUSK", "NIGHT"),
            HeroRecovery.timeTokens(legacy, nyZone)
        )
    }

    @Test
    fun coverPicksTheHealedEntryAfterRecovery() {
        val newest = emptyRefEntry() // Nov 9, no ref
        val older = emptyRefEntry(
            date = LocalDate.of(2026, 11, 7),
            momentLabel = "7 a.m.",
            settledAtMillis = 1_762_527_600_000L
        )
        // Pre-heal: neither entry has a loadable reference, so
        // the cover is the NEWEST entry (the honest placeholder
        // shape Sol saw).
        val before = WearIveRunCollectionLogic.chapters(
            listOf(newest, older)
        ).single()
        assertEquals(newest.date, before.cover?.date)
        assertFalse(HeroRefResolver.hasLoadableReference(before.cover!!))
        // The older entry's cell is banked; after its heal the
        // cover rule (newest WITH a loadable reference) selects
        // the healed older entry — a real photo on the cover.
        val (_, olderPath) = bankFor(older)
        val healedOlder = older.copy(
            heroRef = olderPath, heroDurableRef = olderPath
        )
        val after = WearIveRunCollectionLogic.chapters(
            listOf(newest, healedOlder)
        ).single()
        assertEquals(older.date, after.cover?.date)
        assertTrue(HeroRefResolver.hasLoadableReference(after.cover!!))
    }

    @Test
    fun storeHealPersistsAndRefusesALoadableEntry() = runBlocking {
        val context = ApplicationProvider
            .getApplicationContext<Application>()
        val store = WearIveRunStore(context)
        val entry = emptyRefEntry()
        val (id, path) = bankFor(entry)
        store.recordSettled(entry)

        store.healHeroReference(entry, path)
        val healed = store.entriesFlow.first()
            .single { it.date == entry.date && it.place == entry.place }
        assertEquals(path, healed.heroDurableRef)
        assertEquals(path, healed.heroRef)
        assertTrue(HeroRefResolver.hasLoadableReference(healed))
        assertNotNull(id)

        // A second heal attempt with a different path is refused:
        // the entry's own (now healed) identity wins.
        store.healHeroReference(healed, "user-1/heroes/other/v9.png")
        val after = store.entriesFlow.first()
            .single { it.date == entry.date && it.place == entry.place }
        assertEquals(path, after.heroDurableRef)
    }

    @Test
    fun storeHealOfADeletedEntryResurrectsNothing() = runBlocking {
        val context = ApplicationProvider
            .getApplicationContext<Application>()
        val store = WearIveRunStore(context)
        val entry = emptyRefEntry(
            date = LocalDate.of(2026, 11, 8),
            place = "Kansas City, MO"
        )
        store.recordSettled(entry)
        store.removeByIdentity(entry)
        store.healHeroReference(entry, "user-1/heroes/x/v1.png")
        val entries = store.entriesFlow.first()
        assertTrue(entries.none {
            it.date == entry.date && it.place == entry.place
        })
    }
}

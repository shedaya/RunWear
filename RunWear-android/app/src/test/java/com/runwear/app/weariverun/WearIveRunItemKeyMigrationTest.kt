package com.runwear.app.weariverun

import com.runwear.shared.domain.model.ClothingItem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LOC-1 WP-R2 gate (DESIGN — the sweep's ONE behavioural change):
 * Wear I've Run entries persist display names today; the migration
 * derives the stable item KEYS at decode, so the first write after
 * the upgrade persists keys going forward. Covers:
 *  - a SHIPPED-FORMAT fixture: entries exactly as the shipped build
 *    writes them (items, NO itemKeys; pre-share-field shape too);
 *  - the name → key map covering every catalog item;
 *  - unknown names mapping to themselves (the share layer's standing
 *    treatment of off-catalog names);
 *  - entries whose stored keys already align decoding UNCHANGED;
 *  - display names NEVER being rewritten (only keys are added);
 *  - the round trip: encode after decode persists the derived keys.
 */
class WearIveRunItemKeyMigrationTest {

    /** One entry byte-for-byte in the shape the shipped build's
     *  encoder writes it (date/place/sessionId/settledAtMillis/
     *  feelsLikeF/condition/snowing/raining/items/heroRef — no
     *  itemKeys, no share fields). */
    private val shippedFormatJson = """
        [{"date":"2026-11-05","place":"New York, NY","sessionId":"sess-1","settledAtMillis":1762387200000,"feelsLikeF":41.0,"condition":"Cloudy","snowing":false,"raining":false,"items":["Thermal Long Sleeve","Thermal Tights","Light Gloves"],"heroRef":"https://example.com/hero.jpg"}]
    """.trimIndent()

    @Test
    fun shippedFormatEntryDecodesWithDerivedKeys() {
        val entries = WearIveRunStore.decode(shippedFormatJson)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(
            listOf("Thermal Long Sleeve", "Thermal Tights", "Light Gloves"),
            entry.items
        )
        assertEquals(
            listOf("LONG_SLEEVE_THERMAL", "THERMAL_TIGHTS", "LIGHT_GLOVES"),
            entry.itemKeys
        )
        // The rest of the shipped record decodes exactly as before.
        assertEquals(LocalDate.of(2026, 11, 5), entry.date)
        assertEquals("New York, NY", entry.place)
        assertEquals(41.0, entry.feelsLikeF, 0.0)
        assertEquals("Cloudy", entry.conditionLabel)
        assertEquals("https://example.com/hero.jpg", entry.heroRef)
    }

    @Test
    fun legacyPreSessionEntryAlsoMigrates() {
        // Even older shape: no sessionId / settledAtMillis either.
        val legacy = """
            [{"date":"2025-12-20","place":"Kansas City, MO","feelsLikeF":28.0,"condition":"Snow","snowing":true,"raining":false,"items":["Balaclava / Face Mask","Mittens"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(legacy).single()
        assertEquals(listOf("BALACLAVA", "MITTENS"), entry.itemKeys)
        assertEquals("", entry.sessionId)
        assertEquals(0L, entry.settledAtMillis)
    }

    @Test
    fun mapCoversEveryCatalogItem() {
        assertEquals(ClothingItem.entries.size, WearIveRunItemKeyMigration.keyByDisplayName.size)
        for (item in ClothingItem.entries) {
            assertEquals(item.key, WearIveRunItemKeyMigration.keyFor(item.displayName))
        }
    }

    @Test
    fun unknownNameMapsToItself() {
        assertEquals("Split Shorts", WearIveRunItemKeyMigration.keyFor("Split Shorts"))
        assertEquals("Some Future Garment", WearIveRunItemKeyMigration.keyFor("Some Future Garment"))
    }

    @Test
    fun alignedEntryDecodesUnchanged() {
        val json = """
            [{"date":"2026-11-06","place":"Miami, FL","sessionId":"sess-2","settledAtMillis":1762473600000,"feelsLikeF":78.0,"condition":"Clear","snowing":false,"raining":false,"items":["Tank Top / Singlet","Running Shorts (5-7\")"],"itemKeys":["TANK_TOP","SHORTS"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(json).single()
        assertEquals(listOf("TANK_TOP", "SHORTS"), entry.itemKeys)
        assertEquals(listOf("Tank Top / Singlet", "Running Shorts (5-7\")"), entry.items)
    }

    @Test
    fun misalignedStoredKeysAreReplacedByDerivation() {
        // Defensive: a stale/short key list never survives decode
        // when every name is a catalog name — consumers require
        // positional alignment with items.
        val json = """
            [{"date":"2026-11-06","place":"Miami, FL","feelsLikeF":78.0,"condition":"Clear","snowing":false,"raining":false,"items":["Visor","Sunglasses"],"itemKeys":["VISOR"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(json).single()
        assertEquals(listOf("VISOR", "SUNGLASSES"), entry.itemKeys)
        assertTrue(WearIveRunItemKeyMigration.keysAlign(entry.items, entry.itemKeys))
    }

    @Test
    fun entryWithOffCatalogNamesIsPreservedExactly() {
        // The refined contract (run-293 adjudication): derivation
        // happens only when it is TOTAL. An entry whose names are
        // not all catalog display names keeps its stored bytes —
        // including a generic key list written by another writer —
        // because a partial derivation would break the positional
        // agreement between items and keys.
        val genericKeys = """
            [{"date":"2026-11-05","place":"Kansas City, MO","feelsLikeF":57.0,"condition":"Clear sky","snowing":false,"raining":false,"items":["Short-sleeve shirt","Running shorts"],"itemKeys":["SHORT_SLEEVE_SHIRT","RUNNING_SHORTS"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(genericKeys).single()
        assertEquals(listOf("SHORT_SLEEVE_SHIRT", "RUNNING_SHORTS"), entry.itemKeys)
        assertEquals(listOf("Short-sleeve shirt", "Running shorts"), entry.items)

        val noKeys = """
            [{"date":"2026-11-05","place":"Kansas City, MO","feelsLikeF":57.0,"condition":"Clear sky","snowing":false,"raining":false,"items":["Shorts"]}]
        """.trimIndent()
        val legacy = WearIveRunStore.decode(noKeys).single()
        assertTrue(legacy.itemKeys.isEmpty())
        assertFalse(WearIveRunItemKeyMigration.canDeriveCompletely(listOf("Shorts")))
        assertTrue(
            WearIveRunItemKeyMigration.canDeriveCompletely(
                listOf("Thermal Long Sleeve", "Visor")
            )
        )
    }

    @Test
    fun namesAreNeverRewrittenAndEncodePersistsDerivedKeys() {
        val decoded = WearIveRunStore.decode(shippedFormatJson)
        val reencoded = WearIveRunStore.encode(decoded)
        // The names survive verbatim; the keys are now persisted.
        assertTrue(reencoded.contains("\"Thermal Long Sleeve\""))
        assertTrue(reencoded.contains("\"itemKeys\""))
        assertTrue(reencoded.contains("\"LONG_SLEEVE_THERMAL\""))
        assertTrue(reencoded.contains("\"THERMAL_TIGHTS\""))
        assertTrue(reencoded.contains("\"LIGHT_GLOVES\""))
        // And the re-encoded form decodes to the identical entry.
        val roundTripped = WearIveRunStore.decode(reencoded).single()
        assertEquals(decoded.single(), roundTripped)
    }

    @Test
    fun emptyAndBlankStoresStillDecodeEmpty() {
        assertTrue(WearIveRunStore.decode(null).isEmpty())
        assertTrue(WearIveRunStore.decode("").isEmpty())
        assertFalse(WearIveRunStore.decode(shippedFormatJson).isEmpty())
    }
}

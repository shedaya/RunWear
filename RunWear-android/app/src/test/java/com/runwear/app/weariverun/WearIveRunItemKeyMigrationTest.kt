/**
 * LOC-1 WP-R2 gate (WP-3 DESIGN — "dual-read legacy entries"): Wear
 * I've Run entries persist display names; stable keys ride along
 * only when the writer had them. The adjudicated contract (CI runs
 * 293/294, arbitrated by the shipped suites Gen6WaveBShareTests and
 * WearIveRunSessionRuleTest): DECODE IS LOSSLESS — the store never
 * rewrites stored names or stored keys; the name → key mapping is a
 * READ treatment at the consumer (ShareExportRenderer.buildKitTiles
 * derives positionally when stored keys are incomplete). Covers:
 *  - a SHIPPED-FORMAT fixture decoding byte-faithfully (names AND
 *    the absent key list exactly as stored);
 *  - the name → key map covering every catalog item, exposed for
 *    the read side (deriveKeys over a shipped fixture's names);
 *  - unknown names mapping to themselves;
 *  - entries whose stored keys exist decoding UNCHANGED, aligned
 *    or not — keys are data;
 *  - the round trip: decode → encode is the identity on entries.
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
    fun shippedFormatEntryDecodesLosslessly() {
        val entries = WearIveRunStore.decode(shippedFormatJson)
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals(
            listOf("Thermal Long Sleeve", "Thermal Tights", "Light Gloves"),
            entry.items
        )
        // Lossless: the shipped record carries no keys, and decode
        // invents none — keys are a READ-side derivation.
        assertTrue(entry.itemKeys.isEmpty())
        // The read side derives the stable keys positionally from
        // the very names the shipped build persisted.
        assertEquals(
            listOf("LONG_SLEEVE_THERMAL", "THERMAL_TIGHTS", "LIGHT_GLOVES"),
            WearIveRunItemKeyMigration.deriveKeys(entry.items)
        )
        assertTrue(
            WearIveRunItemKeyMigration.canDeriveCompletely(entry.items)
        )
        // The rest of the shipped record decodes exactly as before.
        assertEquals(LocalDate.of(2026, 11, 5), entry.date)
        assertEquals("New York, NY", entry.place)
        assertEquals(41.0, entry.feelsLikeF, 0.0)
        assertEquals("Cloudy", entry.conditionLabel)
        assertEquals("https://example.com/hero.jpg", entry.heroRef)
    }

    @Test
    fun legacyPreSessionEntryDecodesLosslessly() {
        // Even older shape: no sessionId / settledAtMillis either.
        val legacy = """
            [{"date":"2025-12-20","place":"Kansas City, MO","feelsLikeF":28.0,"condition":"Snow","snowing":true,"raining":false,"items":["Balaclava / Face Mask","Mittens"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(legacy).single()
        assertTrue(entry.itemKeys.isEmpty())
        assertEquals(
            listOf("BALACLAVA", "MITTENS"),
            WearIveRunItemKeyMigration.deriveKeys(entry.items)
        )
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
    fun misalignedStoredKeysArePreservedVerbatim() {
        // Keys are data: a stale/short stored list survives decode
        // exactly as written. Completeness is judged at READ time —
        // buildKitTiles uses stored keys only when they are
        // complete, and derives from the names otherwise.
        val json = """
            [{"date":"2026-11-06","place":"Miami, FL","feelsLikeF":78.0,"condition":"Clear","snowing":false,"raining":false,"items":["Visor","Sunglasses"],"itemKeys":["VISOR"]}]
        """.trimIndent()
        val entry = WearIveRunStore.decode(json).single()
        assertEquals(listOf("VISOR"), entry.itemKeys)
        assertFalse(WearIveRunItemKeyMigration.keysAlign(entry.items, entry.itemKeys))
        assertEquals(
            listOf("VISOR", "SUNGLASSES"),
            WearIveRunItemKeyMigration.deriveKeys(entry.items)
        )
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
    fun decodeEncodeIsTheIdentityOnShippedShapes() {
        val decoded = WearIveRunStore.decode(shippedFormatJson)
        val reencoded = WearIveRunStore.encode(decoded)
        // Nothing is added, dropped, or renamed in the round trip:
        // the keyless shipped shape stays keyless until a writer
        // that constructs from the catalog records anew.
        assertFalse(reencoded.contains("\"itemKeys\""))
        assertTrue(reencoded.contains("\"Thermal Long Sleeve\""))
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

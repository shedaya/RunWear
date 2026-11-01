package com.runwear.shared.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RW-16.23 drift guard: start-personal-fill's canonical fill cells
 * must BE engine v4.3 outputs, not a hand-mirror of them.
 *
 * Background: the fill function's original CANONICAL_OUTFITS table
 * mirrored the legacy generic-pool prompts (scripts/queue-images.js),
 * not the engine. 10 of its 12 outfits were outfits
 * [GetOutfitRecommendationUseCase] cannot produce at any frame —
 * CLEAR COOL demanded LIGHT_JACKET, which selectTopOuter never
 * returns — so subscriber fill jobs were gated on garments the app
 * never recommends, and their combination hashes could never equal a
 * client combination, making even a gate-passing fill hero
 * unservable (RW-16.22 §1).
 *
 * This test replays each fill cell's REPRESENTATIVE FRAME through the
 * production engine and asserts the derived allItems set equals the
 * item set written in the generated block of
 * supabase/functions/start-personal-fill/index.ts (parsed out of the
 * file, located by walking up from the test working dir — the same
 * seam EngineFixtureTest uses). If the engine's selection tables
 * change, or the TS block is edited by hand, this test fails until
 * the two are re-derived together.
 *
 * Representative frames (must match the header comment in the TS
 * file): comfort NEUTRAL (adjusted == raw); CLEAR calm (wind gate
 * off); RAIN measured rain with precipitation 1.0mm; UV 2 at
 * DAWN/DUSK, 4 at MIDDAY, 1 in RAIN; hour 8 DAWN / 18 DUSK / 13
 * MIDDAY; adjusted temps FREEZING 12F, COLD 27F, COOL 42F, MILD 57F,
 * WARM 72F, HOT 85F.
 */
class FillCanonicalOutfitsTest {

    private val engine = GetOutfitRecommendationUseCase()

    data class Cell(
        val weather: String,
        val bracket: String,
        val time: String,
        val tempF: Double,
        val uv: Double,
        val hour: Int
    )

    companion object {
        const val FILL_RELATIVE_PATH = "supabase/functions/start-personal-fill/index.ts"

        private val TEMP = mapOf(
            "FREEZING" to 12.0, "COLD" to 27.0, "COOL" to 42.0,
            "MILD" to 57.0, "WARM" to 72.0, "HOT" to 85.0
        )
        private val BRACKETS = listOf("FREEZING", "COLD", "COOL", "MILD", "WARM", "HOT")

        /** The 20 fill cells with their representative frames. */
        fun expectedCells(): List<Cell> {
            val cells = mutableListOf<Cell>()
            for (b in BRACKETS) {
                cells += Cell("CLEAR", b, "DAWN", TEMP.getValue(b), 2.0, 8)
                cells += Cell("CLEAR", b, "DUSK", TEMP.getValue(b), 2.0, 18)
                cells += Cell("RAIN", b, "DAWN", TEMP.getValue(b), 1.0, 8)
            }
            cells += Cell("CLEAR", "WARM", "MIDDAY", TEMP.getValue("WARM"), 4.0, 13)
            cells += Cell("CLEAR", "HOT", "MIDDAY", TEMP.getValue("HOT"), 4.0, 13)
            return cells
        }

        fun locateFillFunction(): File? {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            var hops = 0
            while (dir != null && hops < 10) {
                val candidate = File(dir, FILL_RELATIVE_PATH)
                if (candidate.isFile) return candidate
                dir = dir.parentFile
                hops++
            }
            return null
        }

        private val CELL_LINE = Regex(
            """\{\s*weather: '([A-Z]+)', bracket: '([A-Z]+)', time: '([A-Z]+)', items: \[([^\]]*)\]\s*\}"""
        )
        private val ITEM = Regex("""'([A-Z_]+)'""")

        /** Parse the generated canonical-cells block out of index.ts. */
        fun parseCanonicalCells(ts: String): Map<String, Set<String>> {
            val begin = ts.indexOf("BEGIN GENERATED CANONICAL CELLS")
            val end = ts.indexOf("END GENERATED CANONICAL CELLS")
            require(begin >= 0 && end > begin) { "generated canonical cells block not found in index.ts" }
            val block = ts.substring(begin, end)
            val out = LinkedHashMap<String, Set<String>>()
            for (m in CELL_LINE.findAll(block)) {
                val key = "${m.groupValues[1]}|${m.groupValues[2]}|${m.groupValues[3]}"
                val items = ITEM.findAll(m.groupValues[4]).map { it.groupValues[1] }.toSet()
                out[key] = items
            }
            return out
        }

        /** The engine/TS shared outfit signature (String.hashCode over sorted names). */
        fun outfitHash(items: Collection<String>): String =
            items.sorted().joinToString("-").hashCode()
                .toUInt().toString(16).takeLast(8).padStart(8, '0')
    }

    @Test
    fun canonicalFillCellsEqualEngineOutputs() {
        val file = locateFillFunction()
        assertNotNull("start-personal-fill index.ts not found above ${System.getProperty("user.dir")}", file)
        val table = parseCanonicalCells(file!!.readText())
        val expected = expectedCells()
        assertEquals(
            "fill cell count drift: TS block vs representative-frame list",
            expected.size, table.size
        )
        for (cell in expected) {
            val key = "${cell.weather}|${cell.bracket}|${cell.time}"
            val tsItems = table[key]
            assertNotNull("cell $key missing from the TS canonical block", tsItems)
            val raining = cell.weather == "RAIN"
            val selection = engine.recommendFrames(
                adjustedFeelsLikeF = cell.tempF,
                rawFeelsLikeF = cell.tempF,
                isRaining = raining,
                precipitation = if (raining) 1.0 else 0.0,
                isWindy = false,
                isHumid = false,
                uvIndex = cell.uv,
                hourOfDay = cell.hour
            )
            val engineItems = selection.allItems.map { it.name }.toSet()
            assertEquals(
                "cell $key: TS canonical items drifted from engine v4.3 output at the representative frame",
                engineItems, tsItems
            )
        }
    }

    @Test
    fun coolClearDawnSignatureMatchesLiveClientCombination() {
        // Anchor: the aligned CLEAR COOL DAWN cell must hash to the
        // combination Sol's client actually requested on 2026-11-01
        // (FEMALE_CLEAR_COOL_DAWN_e216ba73) — the whole point of the
        // alignment is that fill combinations equal client ones.
        val file = locateFillFunction()
        assertNotNull(file)
        val table = parseCanonicalCells(file!!.readText())
        val cool = table["CLEAR|COOL|DAWN"]
        assertNotNull(cool)
        assertEquals("e216ba73", outfitHash(cool!!))
        assertTrue(cool.none { it == "LIGHT_JACKET" })
    }
}

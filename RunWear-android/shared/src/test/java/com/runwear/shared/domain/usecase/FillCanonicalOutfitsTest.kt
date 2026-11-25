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
 * file): comfort NEUTRAL (adjusted == raw); calm (wind gate off);
 * RAIN measured rain with precipitation 1.0mm; CLOUDY and SNOW
 * take the dry frame (the engine reads measured rain only);
 * UV 2 at DAWN/DUSK, 4 at MIDDAY, 1 in RAIN, 0 at NIGHT;
 * hour 8 DAWN / 13 MIDDAY / 18 DUSK / 22 NIGHT; adjusted temps
 * FREEZING 12F, COLD 27F, COOL 42F, MILD 57F, WARM 72F, HOT 85F.
 *
 * RW-69 reconciliation (Nov 5, 2026): this test originally pinned
 * the pre-RW-16.30 20-cell core matrix. RW-16.30 extended the
 * launch set to 88 cells (weather x bracket x daypart minus the
 * unreachable WARM/HOT x SNOW cells) and the deployed
 * start-personal-fill (v14 — the production function, live-proven
 * by Gen-6 Wave A) carries the 88-cell block. When the repo copy
 * was synced to the deployed source (commit 2a593b8), this test
 * went red on the count alone (expected 20, TS block 88). The
 * stale side was THIS representative-frame list, not the TS
 * block: the list below is now generated from the same launch-set
 * definition the TS header documents. The deployed function is
 * the reference truth — this test reconciles to it, never the
 * reverse.
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

        /**
         * The 88 launch-set fill cells (RW-16.30) with their
         * representative frames, generated from the set definition
         * the TS header documents: CLEAR / CLOUDY / RAIN x all six
         * brackets x DAWN / MIDDAY / DUSK / NIGHT (72), plus SNOW x
         * the four reachable brackets x the same dayparts (16) —
         * WARM/HOT x SNOW are unreachable and omitted. UV: 0 at
         * NIGHT, 1 in RAIN, 4 at MIDDAY, else 2. Hour: 8 DAWN,
         * 13 MIDDAY, 18 DUSK, 22 NIGHT. (The pre-RW-16.30 list
         * pinned only the 20-cell core matrix — see the RW-69 note
         * in the class doc.)
         */
        fun expectedCells(): List<Cell> {
            val cells = mutableListOf<Cell>()
            val dayparts = listOf("DAWN", "MIDDAY", "DUSK", "NIGHT")
            fun add(weather: String, bracket: String, time: String) {
                val uv = when {
                    time == "NIGHT" -> 0.0
                    weather == "RAIN" -> 1.0
                    time == "MIDDAY" -> 4.0
                    else -> 2.0
                }
                val hour = when (time) {
                    "DAWN" -> 8
                    "MIDDAY" -> 13
                    "DUSK" -> 18
                    else -> 22 // NIGHT
                }
                cells += Cell(weather, bracket, time, TEMP.getValue(bracket), uv, hour)
            }
            for (w in listOf("CLEAR", "CLOUDY", "RAIN")) {
                for (b in BRACKETS) for (t in dayparts) add(w, b, t)
            }
            for (b in listOf("FREEZING", "COLD", "COOL", "MILD")) {
                for (t in dayparts) add("SNOW", b, t)
            }
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

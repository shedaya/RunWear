package com.runwear.shared.domain.usecase

import com.runwear.shared.domain.model.OutfitSelection
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-platform engine fixtures (Stage 2 of the iOS parity plan, 2026-10-29).
 *
 * One committed JSON file — RunWear-iOS/RunWearTests/engine_fixtures.json —
 * records the shared `:engine` module's decision for ~3,200 frame inputs
 * (the Stage 1 boundary frames: comfort-adjusted + raw feels-like °F,
 * measured rain + precipitation mm, mph wind, percent humidity, UV,
 * location-local hour). This test replays every case through
 * [GetOutfitRecommendationUseCase.recommendFrames] on the JVM; the Swift
 * EngineFixtureTests replay the SAME file through the production
 * OutfitRecommendationService (which delegates to the same Kotlin code via
 * the RunWearEngine framework). Identical inputs → identical decisions is
 * therefore asserted from durable data on both platforms, not by two
 * independently-written expectation sets.
 *
 * Regeneration: run with RUNWEAR_FIXTURE_WRITE=1 in the environment (the
 * android-tests workflow_dispatch input does this) — [generateFixtures]
 * rewrites the file from the current engine. Regenerate ONLY when an
 * engine table change is intentional, and review the fixture diff like a
 * table diff: it IS the table, in data form.
 */
class EngineFixtureTest {

    private val engine = GetOutfitRecommendationUseCase()

    companion object {
        const val FIXTURE_RELATIVE_PATH = "RunWear-iOS/RunWearTests/engine_fixtures.json"

        /** Walk up from the test working dir (the :shared module dir) to the repo root. */
        fun locateFixture(): File? {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            var hops = 0
            while (dir != null && hops < 8) {
                val candidate = File(dir, FIXTURE_RELATIVE_PATH)
                if (candidate.isFile) return candidate
                dir = dir.parentFile
                hops++
            }
            return null
        }
    }

    /** One fixture case: frame inputs exactly as the boundary defines them. */
    data class FrameInput(
        val adjusted: Double,
        val raw: Double,
        val isRaining: Boolean,
        val precipitation: Double,
        val windSpeedMph: Double,
        val humidity: Int,
        val uvIndex: Double,
        val hour: Int
    ) {
        // The gate derivations, single-sourced semantics: WeatherConditions
        // derives isWindy = windSpeed > 10 / isHumid = humidity > 65 on
        // Android; the Swift service derives the same booleans from its mph
        // wind and percent humidity at the same thresholds.
        fun run(engine: GetOutfitRecommendationUseCase): OutfitSelection =
            engine.recommendFrames(
                adjustedFeelsLikeF = adjusted,
                rawFeelsLikeF = raw,
                isRaining = isRaining,
                precipitation = precipitation,
                isWindy = windSpeedMph > 10,
                isHumid = humidity > 65,
                uvIndex = uvIndex,
                hourOfDay = hour
            )
    }

    /** The fixture grid. Deterministic; shared by the generator and documented here. */
    fun grid(): List<FrameInput> {
        val cases = LinkedHashSet<FrameInput>()

        fun add(
            adjusted: Double, raw: Double, isRaining: Boolean, precipitation: Double,
            wind: Double, humidity: Int, uv: Double, hour: Int
        ) {
            cases += FrameInput(adjusted, raw, isRaining, precipitation, wind, humidity, uv, hour)
        }

        val rainCases = listOf(
            false to 0.0,   // dry
            true to 0.0,    // rain code, zero measured rain (Stage 1 boundary case)
            true to 0.05,   // drizzle: cap + tip, NO rain jacket (≤ 0.1 mm gate)
            true to 0.5     // real rain: jacket gate fires
        )

        // Grid A — the main sweep: raw == adjusted (neutral comfort), noon.
        val tempsA = sortedSetOf<Double>().apply {
            for (t in -20..110 step 5) add(t.toDouble())
            addAll(
                listOf(
                    -19.0, -10.0, -5.0, 0.0, 4.0, 5.0, 9.0, 10.0, 14.0, 15.0, 19.0, 20.0,
                    24.0, 25.0, 29.0, 30.0, 31.0, 32.0, 33.0, 35.0, 39.0, 39.9, 40.0,
                    44.0, 45.0, 49.0, 49.5, 50.0, 60.0, 64.0, 65.0, 69.0, 70.0, 79.0,
                    80.0, 90.0, 100.0
                )
            )
        }
        for (t in tempsA) for ((rain, precip) in rainCases)
            for (wind in listOf(0.0, 12.0)) for (hum in listOf(30, 85))
                for (uv in listOf(0.0, 8.0)) {
                    add(t, t, rain, precip, wind, hum, uv, 12)
                }

        // Grid B — environment variation: hours (reflective gate), UV bands
        // (sun gates), wind values straddling the gate, humid air.
        for (t in listOf(-10.0, 0.0, 20.0, 35.0, 45.0, 55.0, 70.0, 85.0, 100.0))
            for ((rain, precip) in listOf(false to 0.0, true to 0.5))
                for (wind in listOf(0.0, 10.5, 25.0)) for (hum in listOf(50, 66))
                    for (uv in listOf(0.0, 4.0, 7.0)) for (hour in listOf(3, 6, 12, 19)) {
                        add(t, t, rain, precip, wind, hum, uv, hour)
                    }

        // Grid C — comfort frames split: raw ≠ adjusted (the v4.3 sun gates
        // must follow RAW while warmth follows ADJUSTED).
        for (adj in listOf(15.0, 30.0, 45.0, 55.0, 60.0, 70.0, 75.0))
            for (delta in listOf(-10.0, -5.0, 5.0, 10.0))
                for (uv in listOf(0.0, 8.0)) {
                    add(adj, adj + delta, false, 0.0, 0.0, 50, uv, 12)
                }

        // Grid D — gate boundaries: wind 9.9 / 10.0 / 10.1 mph, humidity
        // 65 / 66 %, precipitation exactly at the 0.1 mm jacket gate.
        for (t in listOf(30.0, 45.0, 55.0, 65.0, 75.0))
            for (wind in listOf(9.9, 10.0, 10.1)) for (hum in listOf(65, 66))
                for (uv in listOf(0.0, 7.0)) {
                    add(t, t, false, 0.0, wind, hum, uv, 12)
                }
        for (precip in listOf(0.09, 0.1, 0.11)) {
            add(45.0, 45.0, true, precip, 0.0, 50, 0.0, 12)
            add(60.0, 60.0, true, precip, 0.0, 50, 0.0, 12)
        }

        return cases.toList()
    }

    private fun JSONObject.putInput(input: FrameInput) {
        put("adj", input.adjusted)
        put("raw", input.raw)
        put("rain", input.isRaining)
        put("precip", input.precipitation)
        put("wind", input.windSpeedMph)
        put("hum", input.humidity)
        put("uv", input.uvIndex)
        put("hour", input.hour)
    }

    private fun JSONObject.putSelection(sel: OutfitSelection) {
        put("topBase", sel.topBase.key)
        put("topMid", sel.topMid?.key ?: JSONObject.NULL)
        put("topOuter", sel.topOuter?.key ?: JSONObject.NULL)
        put("bottom", sel.bottom.key)
        put("head", sel.head?.key ?: JSONObject.NULL)
        put("hands", sel.hands?.key ?: JSONObject.NULL)
        put("accessories", JSONArray(sel.accessories.map { it.key }))
        put("tips", JSONArray(sel.tips))
    }

    @Test
    fun generateFixtures() {
        if (System.getenv("RUNWEAR_FIXTURE_WRITE") != "1") {
            // Normal runs: generation is opt-in only; the assertion test
            // below is the gate. (Printed so the skip is visible in logs.)
            println("EngineFixtureTest.generateFixtures: RUNWEAR_FIXTURE_WRITE != 1, skipping")
            return
        }
        val target = locateFixture()
            ?: File(File(System.getProperty("user.dir")).absoluteFile, "../../$FIXTURE_RELATIVE_PATH")
        val root = JSONObject()
        root.put("engineVersion", GetOutfitRecommendationUseCase.ENGINE_VERSION)
        root.put(
            "inputs",
            "adj/raw = comfort-adjusted / raw feels-like °F; rain = measured rain > 0; " +
                "precip = mm; wind = mph; hum = %; uv = UV index; hour = location-local 0-23"
        )
        val casesJson = JSONArray()
        for (input in grid()) {
            val caseJson = JSONObject()
            val inJson = JSONObject()
            inJson.putInput(input)
            caseJson.put("in", inJson)
            val outJson = JSONObject()
            outJson.putSelection(input.run(engine))
            caseJson.put("out", outJson)
            casesJson.put(caseJson)
        }
        root.put("cases", casesJson)
        target.parentFile?.mkdirs()
        target.writeText(root.toString(1))
        println("EngineFixtureTest: wrote ${casesJson.length()} fixture cases to ${target.absolutePath}")
    }

    @Test
    fun fixturesMatchEngine() {
        val file = locateFixture()
        if (file == null) {
            // Bootstrap allowance ONLY: while RUNWEAR_FIXTURE_WRITE=1, the
            // generator above is (re)creating the file in this same run, so
            // there is nothing to assert against yet. In every normal run a
            // missing fixture is a hard failure — the file is committed
            // permanently and the gate must never pass without it.
            if (System.getenv("RUNWEAR_FIXTURE_WRITE") == "1") {
                println("EngineFixtureTest.fixturesMatchEngine: fixture absent, generation mode — skipping")
                return
            }
            throw AssertionError("Fixture file not found (looked up from user.dir=${System.getProperty("user.dir")} " +
                "for $FIXTURE_RELATIVE_PATH). It is committed permanently; do not delete it.")
        }
        val root = JSONObject(file.readText())
        assertEquals(
            "Fixture engineVersion must equal the engine's — regenerate the fixtures " +
                "if a table change was intentional",
            GetOutfitRecommendationUseCase.ENGINE_VERSION,
            root.getString("engineVersion")
        )
        val cases = root.getJSONArray("cases")
        assertTrue("Fixture grid suspiciously small: ${cases.length()}", cases.length() > 2500)

        var mismatches = 0
        val report = StringBuilder()
        for (i in 0 until cases.length()) {
            val caseJson = cases.getJSONObject(i)
            val inJson = caseJson.getJSONObject("in")
            val input = FrameInput(
                adjusted = inJson.getDouble("adj"),
                raw = inJson.getDouble("raw"),
                isRaining = inJson.getBoolean("rain"),
                precipitation = inJson.getDouble("precip"),
                windSpeedMph = inJson.getDouble("wind"),
                humidity = inJson.getInt("hum"),
                uvIndex = inJson.getDouble("uv"),
                hour = inJson.getInt("hour")
            )
            val expected = JSONObject()
            expected.putSelection(input.run(engine))
            val actual = caseJson.getJSONObject("out")
            if (!jsonEquals(expected, actual)) {
                mismatches++
                if (mismatches <= 10) {
                    report.appendLine("case $i input=$inJson expected(engine)=$expected fixture=$actual")
                }
            }
        }
        assertEquals("Fixture mismatches (first 10):\n$report", 0, mismatches)
        println("EngineFixtureTest: ${cases.length()} fixture cases match the shared engine")
    }

    /** Structural equality for the flat selection objects (org.json has no deep equals). */
    private fun jsonEquals(a: JSONObject, b: JSONObject): Boolean {
        val keysA = a.keys().asSequence().toSet()
        val keysB = b.keys().asSequence().toSet()
        if (keysA != keysB) return false
        for (key in keysA) {
            val va = a.get(key)
            val vb = b.get(key)
            when {
                va is JSONArray && vb is JSONArray -> {
                    if (va.length() != vb.length()) return false
                    for (i in 0 until va.length()) if (va.get(i) != vb.get(i)) return false
                }
                va == JSONObject.NULL || vb == JSONObject.NULL -> if (va != vb) return false
                else -> if (va.toString() != vb.toString()) return false
            }
        }
        return true
    }
}

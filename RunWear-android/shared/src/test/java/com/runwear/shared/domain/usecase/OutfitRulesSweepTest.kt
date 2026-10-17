package com.runwear.shared.domain.usecase

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.ComfortPreference
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Outfit engine v4.2 — full scenario sweep (QA harness, 2026-10-17).
 *
 * Sweeps the REAL [GetOutfitRecommendationUseCase] over its full input space
 * (209,600 main-grid scenarios plus divergence and Celsius slices) and checks
 * the invariants the v4.2 recalibration is supposed to guarantee. Every
 * section writes a report file under build/outfit-sweep/ so results survive
 * even when an assertion fails the build.
 *
 * Grid axes (the engine's true inputs, traced from source):
 *  - feelsLike: -20..110 °F in 1° steps (actual temperature == feels-like on
 *    the main grid; the engine never reads `temperature` — proven by inv10)
 *  - windSpeed: 0, 3, 6, 9, 12, 16, 20, 25, 30, 40 mph (engine reads it only
 *    via isWindy = windSpeed > 10)
 *  - condition: DRY / DRIZZLE (isRaining, precip 0.05 ≤ 0.1) / RAIN
 *    (isRaining, precip 0.5) / SNOW (isSnowing, precip 0.4)
 *  - comfort: all 5 ComfortPreference values (tempAdjustment -10..+10)
 *  - humidity: 30 / 85 (engine reads it only via isHumid → tips; swept anyway)
 *  - env (hour × UV, the two remaining item-relevant inputs):
 *    DAY_SUN (12:00, UV 8), DAY_GREY (12:00, UV 0), DAWN_UV (06:00, UV 8 —
 *    reflective gear and high-UV gear co-occur; structural-max probe),
 *    NIGHT (21:00, UV 0)
 *
 * weatherCode, windGusts, windDirection, cloudCover, precipitationProbability
 * and isSnowing are never read by the engine's selection logic (verified by
 * source read; SNOW≡DRY equivalence is measured in inv9).
 */
class OutfitRulesSweepTest {

    enum class Cond { DRY, DRIZZLE, RAIN, SNOW }
    enum class Env { DAY_SUN, DAY_GREY, DAWN_UV, NIGHT }

    data class Scenario(
        val feels: Int,
        val wind: Double,
        val cond: Cond,
        val comfort: ComfortPreference,
        val humidity: Int,
        val env: Env
    ) {
        /** The temperature the engine's brackets actually see. */
        val adjusted: Double get() = feels.toDouble() + comfort.tempAdjustment
        fun describe(): String =
            "feels=${feels}F actual=${feels}F wind=${wind}mph cond=$cond comfort=$comfort(adj=${comfort.tempAdjustment}) humidity=$humidity env=$env adjusted=${adjusted}F"
    }

    /** Compact per-scenario record: item bitmask over ClothingItem ordinals + counts. */
    data class Rec(val mask: Int, val total: Int, val torso: Int)

    companion object {
        private val engine = GetOutfitRecommendationUseCase()

        val FEELS: List<Int> = (-20..110).toList()
        val WINDS: List<Double> = listOf(0.0, 3.0, 6.0, 9.0, 12.0, 16.0, 20.0, 25.0, 30.0, 40.0)
        val CONDS: List<Cond> = Cond.entries
        val COMFORTS: List<ComfortPreference> = ComfortPreference.entries // coldest → warmest
        val HUMIDITIES: List<Int> = listOf(30, 85)
        val ENVS: List<Env> = Env.entries

        val SECTIONS = ConcurrentHashMap<String, String>()
        val SECTION_ORDER = listOf(
            "inv0", "inv1", "inv2", "inv3", "inv4", "inv5",
            "inv6", "inv7", "inv8", "inv9", "inv10", "inv11", "spot"
        )

        val GRID: Map<Scenario, Rec> by lazy {
            val map = HashMap<Scenario, Rec>(210_000)
            for (feels in FEELS) for (wind in WINDS) for (cond in CONDS)
                for (comfort in COMFORTS) for (humidity in HUMIDITIES) for (env in ENVS) {
                    val s = Scenario(feels, wind, cond, comfort, humidity, env)
                    map[s] = recOf(engine.execute(weatherFor(s), s.comfort))
                }
            map
        }

        @JvmStatic
        @AfterClass
        fun writeCombinedReport() {
            val dir = File("build/outfit-sweep").apply { mkdirs() }
            val sb = StringBuilder()
            sb.appendLine("RunWear outfit engine sweep — ENGINE_VERSION=${GetOutfitRecommendationUseCase.ENGINE_VERSION}")
            sb.appendLine("Main grid scenarios: ${GRID.size}")
            sb.appendLine()
            for (key in SECTION_ORDER) {
                SECTIONS[key]?.let { sb.appendLine(it); sb.appendLine() }
            }
            File(dir, "report.txt").writeText(sb.toString())
            println("Wrote ${dir}/report.txt (${sb.length} chars)")
        }

        fun weatherFor(s: Scenario, actualF: Double = s.feels.toDouble()): WeatherConditions {
            val (hour, uv, cloud) = when (s.env) {
                Env.DAY_SUN -> Triple(12, 8.0, 10)
                Env.DAY_GREY -> Triple(12, 0.0, 90)
                Env.DAWN_UV -> Triple(6, 8.0, 10)
                Env.NIGHT -> Triple(21, 0.0, 50)
            }
            val (raining, snowing, precip, prob, code) = when (s.cond) {
                Cond.DRY -> CondSpec(false, false, 0.0, 0, WeatherCode.PARTLY_CLOUDY)
                Cond.DRIZZLE -> CondSpec(true, false, 0.05, 60, WeatherCode.DRIZZLE_LIGHT)
                Cond.RAIN -> CondSpec(true, false, 0.5, 90, WeatherCode.RAIN_MODERATE)
                Cond.SNOW -> CondSpec(false, true, 0.4, 80, WeatherCode.SNOW_MODERATE)
            }
            return WeatherConditions(
                dateTime = LocalDateTime.of(2026, 6, 15, hour, 0),
                temperature = actualF,
                feelsLike = s.feels.toDouble(),
                humidity = s.humidity,
                windSpeed = s.wind,
                windGusts = s.wind,
                precipitationProbability = prob,
                precipitation = precip,
                isRaining = raining,
                isSnowing = snowing,
                cloudCover = cloud,
                uvIndex = uv,
                weatherCode = code,
                isCelsius = false
            )
        }

        private data class CondSpec(
            val raining: Boolean, val snowing: Boolean,
            val precip: Double, val prob: Int, val code: WeatherCode
        )

        fun recOf(outfit: OutfitRecommendation): Rec {
            val mask = outfit.allItems.fold(0) { m, item -> m or (1 shl item.ordinal) }
            val torso = 1 +
                (if (outfit.topMid != null) 1 else 0) +
                (if (outfit.topOuter != null) 1 else 0)
            return Rec(mask, outfit.allItems.size, torso)
        }

        fun has(mask: Int, item: ClothingItem): Boolean = mask and (1 shl item.ordinal) != 0

        fun itemsOf(mask: Int): List<ClothingItem> =
            ClothingItem.entries.filter { has(mask, it) }

        fun describe(mask: Int, total: Int, torso: Int): String =
            itemsOf(mask).joinToString(", ") { it.displayName } + " | total=$total torso=$torso"

        fun outfitString(outfit: OutfitRecommendation): String {
            val r = recOf(outfit)
            return describe(r.mask, r.total, r.torso)
        }

        val MID_ITEMS = listOf(
            ClothingItem.QUARTER_ZIP, ClothingItem.FLEECE_PULLOVER, ClothingItem.HALF_ZIP_THERMAL
        )
        val OUTER_ITEMS = listOf(
            ClothingItem.LIGHT_VEST, ClothingItem.WINDBREAKER, ClothingItem.LIGHT_JACKET,
            ClothingItem.RAIN_JACKET, ClothingItem.INSULATED_JACKET
        )

        fun outerOf(mask: Int): ClothingItem? = OUTER_ITEMS.firstOrNull { has(mask, it) }
        fun midOf(mask: Int): ClothingItem? = MID_ITEMS.firstOrNull { has(mask, it) }

        fun section(name: String, body: String) {
            SECTIONS[name] = body
            File("build/outfit-sweep").apply { mkdirs() }
            File("build/outfit-sweep/$name.txt").writeText(body)
            println(body.lineSequence().take(3).joinToString("\n"))
        }
    }

    // ── inv0: engine version pin ──────────────────────────────────────────

    @Test
    fun inv0_engineVersionPin() {
        assertEquals(
            "Harness written against engine 4.2; re-review invariants before bumping this pin.",
            "4.2", GetOutfitRecommendationUseCase.ENGINE_VERSION
        )
        section(
            "inv0",
            "## inv0 — engine version pin\nENGINE_VERSION = ${GetOutfitRecommendationUseCase.ENGINE_VERSION} (asserted == \"4.2\")"
        )
    }

    // ── inv1: 25–40°F band — wind shell and mid layer never co-occur ─────

    @Test
    fun inv1_shellMidExclusionInCoolBand() {
        var checks = 0
        val violations = mutableListOf<String>()
        // Observations (reported, not asserted — see notes in section text):
        var rainStackCount = 0
        val rainStackExamples = mutableListOf<String>()
        var rawBandStackAdjustedOutside = 0
        val rawBandExamples = mutableListOf<String>()
        var substitutions = 0

        for ((s, r) in GRID) {
            val mid = midOf(r.mask)
            val outer = outerOf(r.mask)
            if (s.adjusted >= 25.0 && s.adjusted < 40.0) {
                checks++
                if (mid != null && outer == ClothingItem.WINDBREAKER) {
                    violations += "VIOLATION ${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
                }
                if (mid != null && outer == ClothingItem.RAIN_JACKET) {
                    rainStackCount++
                    if (rainStackExamples.size < 10) {
                        rainStackExamples += "${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
                    }
                }
                // Documented substitution fired: adjusted 25–32 would select a mid,
                // wind called the shell, mid was dropped.
                if (s.adjusted >= 25.0 && s.adjusted < 32.0 && s.wind > 10.0 &&
                    outer == ClothingItem.WINDBREAKER && mid == null
                ) substitutions++
            }
            // Raw feels-like framing (what a user reading the thermometer sees):
            if (s.feels in 25..40 && mid != null && outer == ClothingItem.WINDBREAKER &&
                !(s.adjusted >= 25.0 && s.adjusted < 40.0)
            ) {
                rawBandStackAdjustedOutside++
                if (rawBandExamples.size < 10) {
                    rawBandExamples += "${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
                }
            }
        }

        section(
            "inv1",
            buildString {
                appendLine("## inv1 — 25–40°F band: wind shell + mid layer co-occurrence")
                appendLine("Checks (scenarios with adjusted temp in [25,40)): $checks")
                appendLine("WINDBREAKER+mid violations: ${violations.size}")
                violations.forEach { appendLine(it) }
                appendLine("Documented substitutions observed (mid dropped for shell in band): $substitutions")
                appendLine("OBSERVATION — RAIN_JACKET+mid stacks inside the adjusted band: $rainStackCount")
                appendLine("  The v4.2 exclusion in execute() names only WINDBREAKER; the rain branch")
                appendLine("  returns before it. A rain shell over a mid layer in cold rain may be")
                appendLine("  intended design, but it is outside the letter of the v4.2 rule.")
                rainStackExamples.forEach { appendLine("  e.g. $it") }
                appendLine("OBSERVATION — raw feels-like in 25..40 with WINDBREAKER+mid but adjusted")
                appendLine("  temp OUTSIDE the band (comfort shift moved the bracket): $rawBandStackAdjustedOutside")
                rawBandExamples.forEach { appendLine("  e.g. $it") }
            }
        )
        assertTrue(
            "inv1: ${violations.size} WINDBREAKER+mid stacks in the 25–40°F adjusted band",
            violations.isEmpty()
        )
    }

    // ── inv2 + inv3: temperature chains (monotonic warmth, no cliffs) ────

    private fun chains(): Sequence<List<Pair<Scenario, Rec>>> = sequence {
        for (wind in WINDS) for (cond in CONDS) for (comfort in COMFORTS)
            for (humidity in HUMIDITIES) for (env in ENVS) {
                yield(FEELS.map { f ->
                    val s = Scenario(f, wind, cond, comfort, humidity, env)
                    s to GRID.getValue(s)
                })
            }
    }

    @Test
    fun inv2_monotonicWarmth() {
        var steps = 0
        val violations = mutableListOf<String>()
        for (chain in chains()) {
            for (i in 0 until chain.size - 1) {
                steps++
                val (s0, r0) = chain[i]
                val (s1, r1) = chain[i + 1]
                if (r1.torso > r0.torso) {
                    violations += "VIOLATION torso ${r0.torso} -> ${r1.torso} as feels rises: " +
                        "${s0.describe()} -> ${describe(r0.mask, r0.total, r0.torso)} || " +
                        "${s1.describe()} -> ${describe(r1.mask, r1.total, r1.torso)}"
                }
            }
        }
        section(
            "inv2",
            buildString {
                appendLine("## inv2 — monotonic warmth (torso layers never increase as feels-like rises 1°F)")
                appendLine("Adjacent-step comparisons: $steps")
                appendLine("Violations: ${violations.size}")
                violations.forEach { appendLine(it) }
            }
        )
        assertTrue("inv2: ${violations.size} monotonic-warmth violations", violations.isEmpty())
    }

    @Test
    fun inv3_noCliffs() {
        var steps = 0
        val violations = mutableListOf<String>()
        val cliffsOf2 = mutableListOf<String>()
        for (chain in chains()) {
            for (i in 0 until chain.size - 1) {
                steps++
                val (s0, r0) = chain[i]
                val (s1, r1) = chain[i + 1]
                val delta = r1.total - r0.total
                if (kotlin.math.abs(delta) > 2) {
                    violations += "VIOLATION total ${r0.total} -> ${r1.total} (Δ$delta): " +
                        "${s0.describe()} -> ${describe(r0.mask, r0.total, r0.torso)} || " +
                        "${s1.describe()} -> ${describe(r1.mask, r1.total, r1.torso)}"
                } else if (kotlin.math.abs(delta) == 2) {
                    val removed = itemsOf(r0.mask).filter { !has(r1.mask, it) }
                    val added = itemsOf(r1.mask).filter { !has(r0.mask, it) }
                    cliffsOf2 += "CLIFF Δ$delta at feels ${s0.feels}->${s1.feels}F " +
                        "[wind=${s0.wind} cond=${s0.cond} comfort=${s0.comfort} humidity=${s0.humidity} env=${s0.env}] " +
                        "removed=${removed.map { it.displayName }} added=${added.map { it.displayName }} " +
                        "total ${r0.total} -> ${r1.total}"
                }
            }
        }
        section(
            "inv3",
            buildString {
                appendLine("## inv3 — no cliffs (adjacent 1°F steps change total item count by at most 2)")
                appendLine("Adjacent-step comparisons: $steps")
                appendLine("Violations (|Δ| > 2): ${violations.size}")
                violations.forEach { appendLine(it) }
                appendLine("Jumps of exactly 2 (logged, allowed): ${cliffsOf2.size}")
                cliffsOf2.forEach { appendLine(it) }
            }
        )
        assertTrue("inv3: ${violations.size} cliff violations (|Δ|>2)", violations.isEmpty())
    }

    // ── inv4: structural sanity ───────────────────────────────────────────

    @Test
    fun inv4_structuralSanity() {
        val violations = mutableListOf<String>()
        var minTotal = Int.MAX_VALUE
        var maxTotal = Int.MIN_VALUE
        val maxExamples = mutableListOf<String>()
        val bottomItems = ClothingItem.entries.filter { it.category.name == "BOTTOM" }
        val torsoCats = setOf("TOP_BASE", "TOP_MID", "TOP_OUTER")
        for ((s, r) in GRID) {
            val items = itemsOf(r.mask)
            val bottoms = items.count { it in bottomItems }
            val torsoItems = items.count { it.category.name in torsoCats }
            if (items.isEmpty()) violations += "VIOLATION empty outfit: ${s.describe()}"
            if (bottoms != 1) violations += "VIOLATION bottoms=$bottoms: ${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
            if (torsoItems < 1) violations += "VIOLATION no torso item: ${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
            if (r.total !in 2..10) violations += "VIOLATION total=${r.total} outside 2..10: ${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
            if (r.total < minTotal) minTotal = r.total
            if (r.total > maxTotal) {
                maxTotal = r.total
                maxExamples.clear()
            }
            if (r.total == maxTotal && maxExamples.size < 5) {
                maxExamples += "${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
            }
        }
        section(
            "inv4",
            buildString {
                appendLine("## inv4 — structural sanity (never empty, exactly one bottom, ≥1 torso item, total 2–10)")
                appendLine("Scenarios checked: ${GRID.size}; observed total range: $minTotal..$maxTotal")
                appendLine("Max-total examples:")
                maxExamples.forEach { appendLine("  $it") }
                appendLine("Violations: ${violations.size}")
                violations.take(200).forEach { appendLine(it) }
                if (violations.size > 200) appendLine("... and ${violations.size - 200} more (see test rerun)")
            }
        )
        assertTrue("inv4: ${violations.size} structural violations", violations.isEmpty())
    }

    // ── inv5: no contradictory pairs ──────────────────────────────────────

    @Test
    fun inv5_noContradictions() {
        val shorts = setOf(ClothingItem.SHORTS, ClothingItem.SHORT_SHORTS)
        val thermalWear = setOf(
            ClothingItem.LONG_SLEEVE_THERMAL, ClothingItem.HALF_ZIP_THERMAL,
            ClothingItem.INSULATED_JACKET, ClothingItem.THERMAL_TIGHTS
        )
        val tank = setOf(ClothingItem.TANK_TOP)
        val coldWear = setOf(
            ClothingItem.QUARTER_ZIP, ClothingItem.FLEECE_PULLOVER, ClothingItem.HALF_ZIP_THERMAL,
            ClothingItem.WINDBREAKER, ClothingItem.INSULATED_JACKET,
            ClothingItem.LIGHT_TIGHTS, ClothingItem.THERMAL_TIGHTS,
            ClothingItem.LIGHT_GLOVES, ClothingItem.THERMAL_GLOVES, ClothingItem.MITTENS,
            ClothingItem.LIGHT_BEANIE, ClothingItem.THERMAL_BEANIE, ClothingItem.BALACLAVA,
            ClothingItem.NECK_GAITER
        )
        val shortSleeve = setOf(ClothingItem.SHORT_SLEEVE)
        val heavyCold = setOf(
            ClothingItem.HALF_ZIP_THERMAL, ClothingItem.INSULATED_JACKET, ClothingItem.THERMAL_TIGHTS
        )
        // (groupA, groupB, label) — an outfit containing any A with any B is a contradiction.
        // RAIN_JACKET is deliberately excluded from the tank pairs: a waterproof shell over
        // a singlet in a hot downpour is legitimate gear, not a contradiction.
        val rules = listOf(
            Triple(shorts, thermalWear, "shorts × thermal/insulated wear"),
            Triple(tank, coldWear, "tank/singlet × cold-weather wear"),
            Triple(shortSleeve, heavyCold, "short sleeve × heavy thermal wear")
        )
        val pairList = mutableListOf<String>()
        for ((a, b, label) in rules) for (x in a) for (y in b) pairList += "$label: ${x.displayName} + ${y.displayName}"

        val violations = mutableListOf<String>()
        for ((s, r) in GRID) {
            for ((a, b, label) in rules) {
                val hitA = a.firstOrNull { has(r.mask, it) }
                val hitB = b.firstOrNull { has(r.mask, it) }
                if (hitA != null && hitB != null) {
                    violations += "VIOLATION [$label] ${hitA.displayName} + ${hitB.displayName}: " +
                        "${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
                }
            }
        }
        section(
            "inv5",
            buildString {
                appendLine("## inv5 — no contradictory pairs (pairs derived from the ClothingItem taxonomy)")
                appendLine("Pairs checked per scenario (${pairList.size}):")
                pairList.forEach { appendLine("  $it") }
                appendLine("Scenarios checked: ${GRID.size}")
                appendLine("Violations: ${violations.size}")
                violations.take(200).forEach { appendLine(it) }
                if (violations.size > 200) appendLine("... and ${violations.size - 200} more")
            }
        )
        assertTrue("inv5: ${violations.size} contradiction violations", violations.isEmpty())
    }

    // ── inv6: wind sanity ─────────────────────────────────────────────────

    @Test
    fun inv6_windSanity() {
        var rows = 0
        var steps = 0
        val violations = mutableListOf<String>()
        var calmTo20Decreases = 0
        var substitutionsCalmTo20 = 0
        for (feels in FEELS) for (cond in CONDS) for (comfort in COMFORTS)
            for (humidity in HUMIDITIES) for (env in ENVS) {
                rows++
                val ladder = WINDS.map { w ->
                    val s = Scenario(feels, w, cond, comfort, humidity, env)
                    s to GRID.getValue(s)
                }
                for (i in 0 until ladder.size - 1) {
                    steps++
                    val (s0, r0) = ladder[i]
                    val (s1, r1) = ladder[i + 1]
                    if (r1.total < r0.total) {
                        violations += "VIOLATION wind ${s0.wind} -> ${s1.wind} mph, total ${r0.total} -> ${r1.total}: " +
                            "${s0.describe()} -> ${describe(r0.mask, r0.total, r0.torso)} || " +
                            "${s1.describe()} -> ${describe(r1.mask, r1.total, r1.torso)}"
                    }
                }
                val calm = ladder.first { it.first.wind == 0.0 }
                val w20 = ladder.first { it.first.wind == 20.0 }
                if (w20.second.total < calm.second.total) calmTo20Decreases++
                if (w20.second.total == calm.second.total &&
                    midOf(calm.second.mask) != null && midOf(w20.second.mask) == null &&
                    outerOf(w20.second.mask) == ClothingItem.WINDBREAKER
                ) substitutionsCalmTo20++
            }
        section(
            "inv6",
            buildString {
                appendLine("## inv6 — wind sanity (more wind never yields fewer items; shell-for-mid substitution is equal-count)")
                appendLine("Rows (feels × cond × comfort × humidity × env): $rows; adjacent wind-step comparisons: $steps")
                appendLine("Calm(0) -> 20 mph decreases: $calmTo20Decreases")
                appendLine("Documented shell-for-mid substitutions at 0 -> 20 mph (equal total): $substitutionsCalmTo20")
                appendLine("Violations (any wind step decreases total): ${violations.size}")
                violations.take(200).forEach { appendLine(it) }
                if (violations.size > 200) appendLine("... and ${violations.size - 200} more")
            }
        )
        assertTrue("inv6: ${violations.size} wind-sanity violations", violations.isEmpty())
    }

    // ── inv7: profile ordering ────────────────────────────────────────────

    private fun sunGearCount(mask: Int, cond: Cond): Int {
        var n = 0
        if (has(mask, ClothingItem.SUNGLASSES)) n++
        if (has(mask, ClothingItem.SUNSCREEN)) n++
        // Visor/cap count as sun gear only when they are not rain gear.
        val head = ClothingItem.entries.firstOrNull {
            has(mask, it) && it.category.name == "HEAD"
        }
        if (cond != Cond.RAIN && cond != Cond.DRIZZLE &&
            (head == ClothingItem.VISOR || head == ClothingItem.BASEBALL_CAP)
        ) n++
        return n
    }

    @Test
    fun inv7_profileOrdering() {
        var rows = 0
        var comparisons = 0
        val violations = mutableListOf<String>()
        var explainedBySunGear = 0
        for (feels in FEELS) for (wind in WINDS) for (cond in CONDS)
            for (humidity in HUMIDITIES) for (env in ENVS) {
                rows++
                val chain = COMFORTS.map { c ->
                    val s = Scenario(feels, wind, cond, c, humidity, env)
                    s to GRID.getValue(s)
                }
                for (i in 0 until chain.size - 1) {
                    comparisons++
                    val (sCold, rCold) = chain[i]
                    val (sWarm, rWarm) = chain[i + 1]
                    if (rWarm.total > rCold.total) {
                        val exCold = rCold.total - sunGearCount(rCold.mask, cond)
                        val exWarm = rWarm.total - sunGearCount(rWarm.mask, cond)
                        val sunNote = if (exWarm <= exCold) {
                            explainedBySunGear++
                            " [ordering holds once sun gear is excluded: $exCold vs $exWarm]"
                        } else " [NOT explained by sun gear]"
                        violations += "VIOLATION ${sCold.comfort} total=${rCold.total} < ${sWarm.comfort} total=${rWarm.total}$sunNote: " +
                            "${sCold.describe()} -> ${describe(rCold.mask, rCold.total, rCold.torso)} || " +
                            "${sWarm.describe()} -> ${describe(rWarm.mask, rWarm.total, rWarm.torso)}"
                    }
                }
            }
        section(
            "inv7",
            buildString {
                appendLine("## inv7 — profile ordering (runs-cold count >= neutral >= runs-warm at identical weather)")
                appendLine("Rows: $rows; adjacent comfort comparisons (coldest->warmest chain of 5): $comparisons")
                appendLine("Strict inversions: ${violations.size} (of which fully explained by sun-gear thresholds: $explainedBySunGear)")
                appendLine("Mechanism note: selectHead()/accessories key sun protection (visor/cap/sunglasses/")
                appendLine("sunscreen) off the COMFORT-ADJUSTED temperature, so shifting comfort moves sun")
                appendLine("gear across its 50/65°F thresholds — an inversion of total count can be caused")
                appendLine("purely by sun gear appearing/disappearing, with warmth layering unchanged.")
                violations.forEach { appendLine(it) }
            }
        )
        assertTrue("inv7: ${violations.size} profile-ordering inversions", violations.isEmpty())
    }

    // ── inv8: field-case regressions (Sol's real cases behind v4.2) ──────

    @Test
    fun inv8_fieldCaseRegressions() {
        val sb = StringBuilder()
        sb.appendLine("## inv8 — field-case regressions")

        // (a) 47°F actual / 40°F feels / 11 mph / NEUTRAL — the case that produced
        // three torso layers pre-v4.2.
        val caseA = Scenario(40, 11.0, Cond.DRY, ComfortPreference.NEUTRAL, 30, Env.DAY_GREY)
        val outfitA = engine.execute(weatherFor(caseA, actualF = 47.0), caseA.comfort)
        val recA = recOf(outfitA)
        sb.appendLine("Case (a) feels=40F actual=47F wind=11mph NEUTRAL dry day-grey:")
        sb.appendLine("  ${outfitString(outfitA)}")
        assertTrue("case (a) torso layers ${recA.torso} > 2", recA.torso <= 2)
        assertTrue(
            "case (a) shell+mid stack present",
            outfitA.topMid == null || outfitA.topOuter == null
        )

        // (b) 39°F feels / calm / NEUTRAL — the shipped v4.2 result: 4 items, 1 torso layer.
        val caseB = Scenario(39, 0.0, Cond.DRY, ComfortPreference.NEUTRAL, 30, Env.DAY_GREY)
        val outfitB = engine.execute(weatherFor(caseB), caseB.comfort)
        val recB = recOf(outfitB)
        sb.appendLine("Case (b) feels=39F wind=0mph NEUTRAL dry day-grey:")
        sb.appendLine("  ${outfitString(outfitB)}")
        assertEquals("case (b) total items", 4, recB.total)
        assertEquals("case (b) torso layers", 1, recB.torso)

        section("inv8", sb.toString())
    }

    // ── inv9: rain coverage ───────────────────────────────────────────────

    @Test
    fun inv9_rainCoverage() {
        var rainChecks = 0
        val violations = mutableListOf<String>()
        val missingTemps = sortedSetOf<Double>()
        var drizzleChecks = 0
        var drizzleWithRainJacket = 0
        var snowDryDiffs = 0
        var snowDryPairs = 0
        for ((s, r) in GRID) {
            when (s.cond) {
                Cond.RAIN -> {
                    rainChecks++
                    if (outerOf(r.mask) != ClothingItem.RAIN_JACKET) {
                        violations += "VIOLATION rain without RAIN_JACKET: ${s.describe()} -> ${describe(r.mask, r.total, r.torso)}"
                        missingTemps += s.adjusted
                    }
                }
                Cond.DRIZZLE -> {
                    drizzleChecks++
                    if (outerOf(r.mask) == ClothingItem.RAIN_JACKET) drizzleWithRainJacket++
                }
                else -> Unit
            }
            if (s.cond == Cond.SNOW) {
                val dryTwin = GRID.getValue(s.copy(cond = Cond.DRY))
                snowDryPairs++
                if (dryTwin.mask != r.mask) snowDryDiffs++
            }
        }
        section(
            "inv9",
            buildString {
                appendLine("## inv9 — rain coverage (condition=rain ⇒ the engine's rain/waterproof item)")
                appendLine("Rain scenarios checked (isRaining, precip=0.5 > 0.1): $rainChecks")
                appendLine("Violations (rain item missing): ${violations.size}")
                if (missingTemps.isNotEmpty()) {
                    appendLine("Adjusted temps missing the rain jacket: ${missingTemps.joinToString()}")
                }
                violations.take(100).forEach { appendLine(it) }
                appendLine("OBSERVATION — drizzle (isRaining, precip=0.05 ≤ 0.1 threshold): $drizzleChecks scenarios,")
                appendLine("  rain jacket in $drizzleWithRainJacket of them. selectTopOuter() requires precip > 0.1,")
                appendLine("  so drizzle gets NO rain-specific outer at any temperature (head still gets a cap).")
                appendLine("OBSERVATION — snow: $snowDryPairs snow scenarios compared with their dry twins;")
                appendLine("  outfits differing: $snowDryDiffs. The engine has no snow-specific branch:")
                appendLine("  isSnowing is never read by the selection logic.")
            }
        )
        assertTrue("inv9: ${violations.size} rain scenarios missing RAIN_JACKET", violations.isEmpty())
    }

    // ── inv10 (extra): actual-temperature independence ───────────────────

    @Test
    fun inv10_actualTemperatureIgnored() {
        var checks = 0
        val mismatches = mutableListOf<String>()
        for (feels in FEELS) for (wind in WINDS) {
            val base = Scenario(feels, wind, Cond.DRY, ComfortPreference.NEUTRAL, 30, Env.DAY_GREY)
            val baseMask = GRID.getValue(base).mask
            for (delta in listOf(-10.0, 10.0)) {
                checks++
                val outfit = engine.execute(weatherFor(base, actualF = feels + delta), base.comfort)
                if (recOf(outfit).mask != baseMask) {
                    mismatches += "MISMATCH actual=${feels + delta}F vs ${feels}F at ${base.describe()}"
                }
            }
        }
        section(
            "inv10",
            buildString {
                appendLine("## inv10 (extra) — actual-temperature independence")
                appendLine("Divergence slice checks (actual = feels ±10°F, NEUTRAL/dry/day-grey): $checks")
                appendLine("Mismatches: ${mismatches.size} — the engine's selection reads feelsLike only;")
                appendLine("the `temperature` field never reaches a selector (source-verified, now measured).")
                mismatches.forEach { appendLine(it) }
            }
        )
        assertTrue("inv10: ${mismatches.size} actual-temp mismatches", mismatches.isEmpty())
    }

    // ── inv11 (extra, observational): Celsius round-trip equivalence ─────

    @Test
    fun inv11_celsiusRoundTrip() {
        var checks = 0
        val mismatches = mutableListOf<String>()
        for (feelsF in -20..110 step 5) for (wind in listOf(0.0, 12.0, 25.0))
            for (comfort in COMFORTS) for (cond in listOf(Cond.DRY, Cond.RAIN)) {
                val s = Scenario(feelsF, wind, cond, comfort, 30, Env.DAY_GREY)
                val celsiusWeather = weatherFor(s).copy(
                    temperature = (feelsF - 32.0) * 5.0 / 9.0,
                    feelsLike = (feelsF - 32.0) * 5.0 / 9.0,
                    isCelsius = true
                )
                checks++
                val outfit = engine.execute(celsiusWeather, comfort)
                val expected = GRID.getValue(s)
                if (recOf(outfit).mask != expected.mask) {
                    val roundTripped = (feelsF - 32.0) * 5.0 / 9.0 * 9.0 / 5.0 + 32.0
                    mismatches += "MISMATCH feels=${feelsF}F via Celsius (round-trips to $roundTripped): " +
                        "${s.describe()} -> F-run ${describe(expected.mask, expected.total, expected.torso)} " +
                        "vs C-run ${outfitString(outfit)}"
                }
            }
        section(
            "inv11",
            buildString {
                appendLine("## inv11 (extra, observational) — Celsius round-trip equivalence")
                appendLine("Checks: $checks (feels step 5°F, winds {0,12,25}, all comforts, dry+rain, day-grey)")
                appendLine("Mismatches: ${mismatches.size}")
                appendLine("The engine converts °C feels-like back to °F in floating point; values that")
                appendLine("land a hair below a bracket boundary (e.g. 39.999… instead of 40.0) select")
                appendLine("the colder bracket. Counted and listed here as a finding, not asserted:")
                appendLine("it is not one of the nine v4.2 invariants.")
                mismatches.forEach { appendLine(it) }
            }
        )
        println("inv11: ${mismatches.size} Celsius round-trip mismatches out of $checks checks")
    }

    // ── spot table ────────────────────────────────────────────────────────

    @Test
    fun spotTable() {
        val sb = StringBuilder()
        sb.appendLine("## spot table — NEUTRAL, dry, humidity 30, day-grey (UV 0, 12:00)")
        sb.appendLine("feels | wind | outfit")
        for (feels in listOf(25, 30, 35, 40, 45, 50, 55, 60)) {
            for (wind in listOf(0.0, 15.0)) {
                val s = Scenario(feels, wind, Cond.DRY, ComfortPreference.NEUTRAL, 30, Env.DAY_GREY)
                val outfit = engine.execute(weatherFor(s))
                sb.appendLine("${feels}F | ${wind}mph | ${outfitString(outfit)}")
            }
        }
        section("spot", sb.toString())
    }
}

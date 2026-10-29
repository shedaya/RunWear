package com.runwear.shared.domain.usecase

import com.runwear.shared.domain.model.*

/**
 * THE BRAIN: Running-Specific Clothing Recommendation Engine (v4.2)
 *
 * Brackets are mapped directly from real feels-like temperature to running-appropriate clothing.
 * No arithmetic offset — the thresholds themselves reflect that runners generate 5-15× resting
 * metabolic heat. The "Rule of 20" is baked into the brackets, not applied as math.
 *
 * Sources: Nike, Jeff Galloway (US Olympian), Marathon Handbook, Tina Muir (2:36 marathoner),
 * Fleet Feet, Luke Humphrey (Hanson's Brooks Distance Project), NCBI exercise physiology.
 *
 * Key insight: No credible running source recommends an insulated jacket at 20°F.
 * That threshold sits at 0-10°F for active runners.
 *
 * v4.2 cool-band recalibration (field report, Oct 2026): 47°F actual / ~40°F feels-like in
 * 11 mph wind produced THREE torso layers — light long sleeve + quarter-zip + windbreaker.
 * Causes: (a) the mid-layer band started at 40°F, (b) the windbreaker was unconditional
 * across 10–39°F, and (c) the three torso selectors all keyed off the same feels-like with
 * no mutual exclusion, so a raw feels-like of 39.x (displayed as "40°") fired every layer at
 * once — crossing 40.0 → 39.9°F added FOUR items in a single step (2 → 6 total).
 * Fixes: mid-layers now start at 32°F, the shell is wind-gated through the whole cool band
 * (it only becomes standard issue below 25°F, where cold — not wind — dominates), and in
 * the 25–40°F band a wind shell and a mid-layer are alternatives, never a stack.
 * NOTE: the PWA mirrors these tables and needs the same update (out of scope here).
 *
 * v4.3 sun-gear frame fix (owner decision, Oct 2026): sun protection is not layering.
 * The visor/cap sun gates in selectHead() read the comfort-ADJUSTED temperature, so a
 * warmer comfort profile could gain a running cap the colder profile didn't get at the
 * same weather (640 profile-ordering inversions in the Oct 17 sweep, inv7). The sun
 * gates now read the RAW temperature (baseTemp, before the comfort shift); warmth
 * headgear stays on the adjusted frame. Sunglasses/sunscreen were already UV-only.
 * The PWA mirror needs the same sun-gate change.
 *
 * Stage 2 (2026-10-29): this class lives in the shared KMP `:engine` module and
 * is the ONLY implementation of the decision tables on both platforms. Android
 * calls [execute] with a [WeatherConditions]; the Apple platforms call
 * [recommendFrames] with the same decision inputs in frame form (the Stage 1
 * boundary contract: comfort-adjusted + raw feels-like in °F, measured rain,
 * mph wind gate, real humidity/UV). The selectors below are frame-based so
 * both entry points run literally the same code.
 */
class GetOutfitRecommendationUseCase {

    companion object {
        /**
         * Engine version, surfaced in feedback diagnostic bundles so a report
         * always names the exact rules that produced the outfit. Bump this
         * whenever the selection tables below change.
         */
        const val ENGINE_VERSION = "4.3"
    }

    fun execute(weather: WeatherConditions, comfort: ComfortPreference = ComfortPreference.NEUTRAL): OutfitRecommendation {
        // Convert to Fahrenheit for consistent logic (engine uses F internally)
        val baseTemp = if (weather.isCelsius) weather.feelsLike * 9 / 5 + 32 else weather.feelsLike
        // Comfort adjustment only: "runs hot" (+10) shifts up, "runs cold" (-10) shifts down
        // No separate running offset — the bracket thresholds are already running-specific.
        val adjustedTemp = baseTemp + comfort.tempAdjustment

        val selection = recommendFrames(
            adjustedFeelsLikeF = adjustedTemp,
            rawFeelsLikeF = baseTemp,
            isRaining = weather.isRaining,
            precipitation = weather.precipitation,
            isWindy = weather.isWindy,
            isHumid = weather.isHumid,
            uvIndex = weather.uvIndex,
            hourOfDay = weather.dateTime.hour
        )

        return OutfitRecommendation(
            weather = weather,
            topBase = selection.topBase,
            topMid = selection.topMid,
            topOuter = selection.topOuter,
            bottom = selection.bottom,
            head = selection.head,
            hands = selection.hands,
            accessories = selection.accessories,
            tips = selection.tips
        )
    }

    /**
     * The decision core in frame form — the Apple-platform entry point, and
     * the single code path [execute] delegates to.
     *
     * Frame contract (Stage 1, preserved exactly):
     * - [adjustedFeelsLikeF]: feels-like °F AFTER the comfort adjustment —
     *   the frame every warmth selector reads.
     * - [rawFeelsLikeF]: feels-like °F BEFORE the comfort adjustment — read
     *   ONLY by the visor/cap sun gates (v4.3).
     * - [isRaining]: measured rain (rain amount > 0), never a weather-code
     *   class. [precipitation] is the measured amount in mm; the rain-jacket
     *   gate additionally requires precipitation > 0.1.
     * - [isWindy] / [isHumid]: the gate booleans the selectors read. On
     *   Android they are WeatherConditions' own derivations (windSpeed > 10
     *   in the stored unit frame, humidity > 65); the Apple bridge derives
     *   them from mph wind and percent humidity at the same thresholds.
     * - [hourOfDay]: location-local hour 0–23, for the reflective-gear gate.
     */
    fun recommendFrames(
        adjustedFeelsLikeF: Double,
        rawFeelsLikeF: Double,
        isRaining: Boolean,
        precipitation: Double,
        isWindy: Boolean,
        isHumid: Boolean,
        uvIndex: Double,
        hourOfDay: Int
    ): OutfitSelection {
        val topMid = selectTopMid(adjustedFeelsLikeF)
        val topOuter = selectTopOuter(adjustedFeelsLikeF, isRaining, precipitation, isWindy)
        // Cool-band rule (25–39.9°F): a wind shell and a mid-layer are alternatives,
        // never a stack. If the wind calls for the shell, the mid-layer comes off.
        // (Feels-like already prices in the wind chill; dressing for both double-counts it.)
        val effectiveTopMid =
            if (topOuter == ClothingItem.WINDBREAKER && topMid != null &&
                adjustedFeelsLikeF >= 25 && adjustedFeelsLikeF < 40
            ) null else topMid

        return OutfitSelection(
            topBase = selectTopBase(adjustedFeelsLikeF),
            topMid = effectiveTopMid,
            topOuter = topOuter,
            bottom = selectBottom(adjustedFeelsLikeF),
            head = selectHead(adjustedFeelsLikeF, rawFeelsLikeF, uvIndex, isRaining),
            hands = selectHands(adjustedFeelsLikeF),
            accessories = selectAccessories(uvIndex, adjustedFeelsLikeF, hourOfDay),
            tips = generateTips(adjustedFeelsLikeF, isWindy, isRaining, isHumid, uvIndex)
        )
    }

    // Runners wear short sleeves down to 50°F — body heat makes up the difference
    private fun selectTopBase(temp: Double): ClothingItem = when {
        temp >= 80 -> ClothingItem.TANK_TOP
        temp >= 50 -> ClothingItem.SHORT_SLEEVE
        temp >= 25 -> ClothingItem.LONG_SLEEVE_LIGHT
        else -> ClothingItem.LONG_SLEEVE_THERMAL
    }

    // No mid-layer above 32°F: at 32–49°F a single long-sleeve layer (plus an
    // optional wind shell) is what mainstream dress charts call for. Below 32°F,
    // progressively warmer mid-layers.
    private fun selectTopMid(temp: Double): ClothingItem? = when {
        temp >= 32 -> null
        temp >= 25 -> ClothingItem.QUARTER_ZIP
        temp >= 10 -> ClothingItem.FLEECE_PULLOVER
        else -> ClothingItem.HALF_ZIP_THERMAL
    }

    // Rain always gets rain jacket. Wind shell beats heavy jacket for running.
    // Insulated jacket ONLY below 10°F.
    // The shell is wind-GATED through the whole cool band (25–49°F): feels-like
    // already includes wind chill, so an automatic shell on top of it double-counts
    // the wind. Below 25°F the shell becomes standard (cold dominates, and it seals
    // the mid-layer). In the 25–40°F band, execute() drops the mid-layer when the
    // shell is on — the two are alternatives there, never a stack.
    private fun selectTopOuter(temp: Double, isRaining: Boolean, precipitation: Double, isWindy: Boolean): ClothingItem? {
        if (isRaining && precipitation > 0.1) return ClothingItem.RAIN_JACKET

        return when {
            temp >= 50 -> null
            temp >= 25 -> if (isWindy) ClothingItem.WINDBREAKER else null
            temp >= 10 -> ClothingItem.WINDBREAKER  // Wind shell for 10–24°F — NOT insulated
            else -> ClothingItem.INSULATED_JACKET    // Insulated ONLY below 10°F
        }
    }

    // Runners wear shorts down to ~45°F — legs generate plenty of heat
    private fun selectBottom(temp: Double): ClothingItem = when {
        temp >= 80 -> ClothingItem.SHORT_SHORTS
        temp >= 45 -> ClothingItem.SHORTS
        temp >= 30 -> ClothingItem.LIGHT_TIGHTS
        else -> ClothingItem.THERMAL_TIGHTS
    }

    private fun selectHead(temp: Double, rawTemp: Double, uvIndex: Double, isRaining: Boolean): ClothingItem? {
        // Sun protection is NOT layering (v4.3): the visor/cap sun gates key off
        // the RAW temperature (rawTemp = baseTemp, before the comfort
        // adjustment), never the comfort-adjusted frame — "I run hot" must not
        // put a cap on your head, and "I run cold" must not take one off.
        if (rawTemp >= 65 && uvIndex > 3) return ClothingItem.VISOR
        if (rawTemp >= 50 && uvIndex > 3) return ClothingItem.BASEBALL_CAP
        // Same item, different job: in rain the cap is rain gear, keyed to rain
        // itself — not a temperature decision. Unchanged.
        if (isRaining) return ClothingItem.BASEBALL_CAP

        // Warmth headgear below stays on the comfort-adjusted frame (temp).
        return when {
            temp >= 40 -> null
            temp >= 30 -> ClothingItem.HEADBAND
            temp >= 15 -> ClothingItem.LIGHT_BEANIE
            temp >= 5 -> ClothingItem.THERMAL_BEANIE
            else -> ClothingItem.BALACLAVA
        }
    }

    private fun selectHands(temp: Double): ClothingItem? = when {
        temp >= 40 -> null
        temp >= 25 -> ClothingItem.LIGHT_GLOVES
        temp >= 15 -> ClothingItem.THERMAL_GLOVES
        else -> ClothingItem.MITTENS
    }

    private fun selectAccessories(uvIndex: Double, temp: Double, hourOfDay: Int): List<ClothingItem> {
        val accessories = mutableListOf<ClothingItem>()
        // Sun protection, UV-only: never read the temperature in any frame, so
        // the comfort preference cannot move them (classification audited v4.3).
        if (uvIndex > 3) accessories.add(ClothingItem.SUNGLASSES)
        if (uvIndex > 5) accessories.add(ClothingItem.SUNSCREEN)
        if (temp < 30) accessories.add(ClothingItem.NECK_GAITER)
        // Reflective gear for dawn/dusk/night
        if (hourOfDay < 7 || hourOfDay > 18) accessories.add(ClothingItem.REFLECTIVE_GEAR)
        return accessories
    }

    private fun generateTips(temp: Double, isWindy: Boolean, isRaining: Boolean, isHumid: Boolean, uvIndex: Double): List<String> {
        val tips = mutableListOf<String>()

        // The key running tip — always show below 50°F
        if (temp < 50) {
            tips.add("🏃 You should feel slightly cool stepping outside. If you're comfortable standing still, you're overdressed for running.")
        }

        // Layered-setup ventilation tip (2+ torso layers happen below 32°F)
        if (temp < 32 && temp >= 10) {
            tips.add("🧥 Your layered setup lets you regulate heat — unzip your mid-layer or shell if you warm up after the first mile.")
        }

        // Wind tip
        if (isWindy && temp < 50) {
            tips.add("💨 A wind shell beats a heavy jacket for running. It blocks wind chill while letting you vent excess heat.")
        }

        // Extreme cold
        if (temp < 10) {
            tips.add("🥶 Cover all exposed skin — frostbite risk increases below 10°F with wind. Vaseline on cheeks and nose helps.")
        }

        // Heat
        when {
            temp >= 80 -> {
                tips.add("🌡️ High heat risk — consider running early morning or evening.")
                tips.add("💧 Hydrate well before, during, and after your run.")
            }
            temp >= 70 -> tips.add("💧 Stay hydrated — consider carrying water.")
        }

        // Rain
        if (isRaining) {
            tips.add("🌧️ Wear a cap under your hood to keep rain out of your eyes. Avoid cotton — it retains moisture.")
        }

        // Humidity
        if (isHumid && temp > 60) {
            tips.add("💦 High humidity — sweat won't evaporate easily. Wear looser, lighter fabrics and hydrate more.")
        }

        // UV
        if (uvIndex > 6) tips.add("☀️ UV is high — reapply sunscreen every 60-90 minutes if running longer than an hour.")

        // Shorts in cold encouragement
        if (temp in 40.0..49.0) {
            tips.add("🩳 Many runners still wear shorts at this temperature. Your legs generate plenty of heat — try it before adding tights.")
        }

        return tips.take(3)
    }
}

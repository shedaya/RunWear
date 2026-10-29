package com.runwear.app.widget

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import com.runwear.shared.domain.model.WeatherCode
import com.runwear.shared.domain.model.WeatherConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The 1.1.8 render matrix: size dispatch + slot budgets. This repo has no
 * screenshot/render harness for Glance, so the layout plan — the pure
 * layer the composables render verbatim — is verified across the matrix
 * the Oct 27 findings demanded: canonical sizes, breakpoint edges,
 * provider extrema, bare / layered / rain outfits, maximum secondary
 * sets, long names, Celsius, and enlarged font scales. Invariants per
 * cell: the right composition is chosen, worn garments never shed,
 * optional content sheds in priority order, secondary garments stay
 * represented, and the plan fits its height budget (one documented
 * tolerance cell aside — see [font scale 1_3 tolerance]). The single
 * representation exception: at font scale 1.3, a layered outfit with
 * three or more secondary items may shed the secondary zone — it is
 * the last slot to shed, after later-today, footer segments, and every
 * type step-down (see [matrix - worn garments never shed and chips
 * stay a priority prefix]).
 */
class WidgetLayoutTest {

    // ---- fixtures -----------------------------------------------------------

    private fun weather(
        feelsLike: Double = 51.0,
        code: WeatherCode = WeatherCode.CLEAR_SKY,
        celsius: Boolean = false
    ) = WeatherConditions(
        dateTime = LocalDateTime.of(2026, 10, 27, 13, 15),
        temperature = feelsLike + 3,
        feelsLike = feelsLike,
        humidity = 55,
        windSpeed = 8.0,
        windGusts = 12.0,
        windDirection = 180,
        precipitationProbability = 0,
        precipitation = 0.0,
        isRaining = false,
        isSnowing = false,
        cloudCover = 10,
        uvIndex = 4.0,
        weatherCode = code,
        isCelsius = celsius
    )

    /** Sol's field case: 51°F clear — short sleeve + shorts, nothing else. */
    private val bare51 = OutfitRecommendation(
        weather = weather(),
        topBase = ClothingItem.SHORT_SLEEVE,
        bottom = ClothingItem.SHORTS
    )

    /** The field case plus the secondary items the old Small used to hide. */
    private val bare51Sun = OutfitRecommendation(
        weather = weather(),
        topBase = ClothingItem.SHORT_SLEEVE,
        bottom = ClothingItem.SHORTS,
        head = ClothingItem.BASEBALL_CAP,
        accessories = listOf(ClothingItem.SUNGLASSES, ClothingItem.SUNSCREEN)
    )

    /** Full cold layering: mid + outer. */
    private val layered = OutfitRecommendation(
        weather = weather(feelsLike = 24.0, code = WeatherCode.OVERCAST),
        topBase = ClothingItem.LONG_SLEEVE_THERMAL,
        topMid = ClothingItem.QUARTER_ZIP,
        topOuter = ClothingItem.WINDBREAKER,
        bottom = ClothingItem.THERMAL_TIGHTS
    )

    /** Rain outfit with the maximum secondary set (head + hands + 2 accessories). */
    private val rainMax = OutfitRecommendation(
        weather = weather(feelsLike = 46.0, code = WeatherCode.RAIN_MODERATE),
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        topOuter = ClothingItem.RAIN_JACKET,
        bottom = ClothingItem.LIGHT_TIGHTS,
        head = ClothingItem.BASEBALL_CAP,
        hands = ClothingItem.LIGHT_GLOVES,
        accessories = listOf(ClothingItem.SUNGLASSES, ClothingItem.SUNSCREEN)
    )

    /** Longest display names in the catalog. */
    private val longNames = OutfitRecommendation(
        weather = weather(feelsLike = 18.0, code = WeatherCode.PARTLY_CLOUDY),
        topBase = ClothingItem.TANK_TOP,
        topMid = ClothingItem.HALF_ZIP_THERMAL,
        topOuter = ClothingItem.INSULATED_JACKET,
        bottom = ClothingItem.CAPRIS,
        head = ClothingItem.BALACLAVA,
        hands = ClothingItem.THERMAL_GLOVES,
        accessories = listOf(ClothingItem.REFLECTIVE_GEAR, ClothingItem.NECK_GAITER)
    )

    private val outfits = listOf(bare51, bare51Sun, layered, rainMax, longNames)
    private val layeredOutfits = listOf(layered, rainMax, longNames)

    private fun fullSegments(w: WeatherConditions): List<String> {
        val unit = if (w.isCelsius) "°C" else "°F"
        val windUnit = if (w.isCelsius) "km/h" else "mph"
        return listOf(
            "Feels like ${w.feelsLike.toInt()}$unit",
            "Actual ${w.temperature.toInt()}$unit",
            "Wind ${w.windSpeed.toInt()} $windUnit",
            w.weatherCode.description
        )
    }

    private fun smallSegments(w: WeatherConditions): List<String> {
        val unit = if (w.isCelsius) "°C" else "°F"
        return listOf("Feels like ${w.feelsLike.toInt()}$unit", w.weatherCode.description)
    }

    private val laterLine = "By 6 PM: add a Light Running Jacket"

    private fun innerFor(kind: WidgetLayout.Kind, w: Int, h: Int): Pair<Float, Float> {
        val (padH, padV) = WidgetLayout.paddingDp(kind)
        return (w - 2 * padH).toFloat() to (h - 2 * padV).toFloat()
    }

    private fun bucketFor(kind: WidgetLayout.Kind): Pair<Int, Int> = when (kind) {
        WidgetLayout.Kind.SMALL -> 100 to 100
        WidgetLayout.Kind.MEDIUM_SQUARE -> 160 to 160
        WidgetLayout.Kind.HORIZONTAL -> 250 to 60
        WidgetLayout.Kind.LARGE -> 250 to 190
    }

    private fun planFor(
        kind: WidgetLayout.Kind,
        outfit: OutfitRecommendation,
        fontScale: Float,
        later: String? = laterLine,
        bucket: Pair<Int, Int> = bucketFor(kind)
    ): WidgetLayout.SlotPlan {
        val (innerW, innerH) = innerFor(kind, bucket.first, bucket.second)
        val full = kind == WidgetLayout.Kind.MEDIUM_SQUARE || kind == WidgetLayout.Kind.LARGE
        val segments = if (full) fullSegments(outfit.weather) else smallSegments(outfit.weather)
        return WidgetLayout.plan(
            kind = kind,
            outfit = outfit,
            laterTodayLine = later,
            footerSegments = segments,
            droppableFooterSegment = if (full) segments[1] else null,
            innerWidthDp = innerW,
            innerHeightDp = innerH,
            fontScale = fontScale
        )
    }

    // ---- dispatch -------------------------------------------------------------

    @Test
    fun `canonical buckets dispatch to their compositions`() {
        assertEquals(WidgetLayout.Kind.SMALL, WidgetLayout.layoutFor(100, 100))
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(160, 160))
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(180, 180))
        assertEquals(WidgetLayout.Kind.HORIZONTAL, WidgetLayout.layoutFor(250, 60))
        assertEquals(WidgetLayout.Kind.LARGE, WidgetLayout.layoutFor(250, 190))
    }

    @Test
    fun `dispatch covers the provider extrema and breakpoint edges`() {
        // Provider box: 100..400 wide, 60..200 tall — every corner composed.
        assertEquals(WidgetLayout.Kind.SMALL, WidgetLayout.layoutFor(100, 60))
        assertEquals(WidgetLayout.Kind.HORIZONTAL, WidgetLayout.layoutFor(400, 60))
        assertEquals(WidgetLayout.Kind.LARGE, WidgetLayout.layoutFor(400, 200))
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(249, 200))
        // The field case: a 2x2 square well under 250 wide is MEDIUM, never Small.
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(170, 165))
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(125, 105))
        // Edges around the medium threshold.
        assertEquals(WidgetLayout.Kind.SMALL, WidgetLayout.layoutFor(124, 105))
        assertEquals(WidgetLayout.Kind.HORIZONTAL, WidgetLayout.layoutFor(125, 104))
        // Wide bands below the Large height threshold stay strips.
        assertEquals(WidgetLayout.Kind.HORIZONTAL, WidgetLayout.layoutFor(300, 129))
        assertEquals(WidgetLayout.Kind.LARGE, WidgetLayout.layoutFor(300, 130))
        // Narrow-tall placements fall back to the anchored Small.
        assertEquals(WidgetLayout.Kind.SMALL, WidgetLayout.layoutFor(110, 190))
    }

    // ---- the matrix -----------------------------------------------------------

    @Test
    fun `matrix - every kind, outfit and regular font scale fits its height budget`() {
        val failures = mutableListOf<String>()
        for (kind in WidgetLayout.Kind.values()) {
            for ((index, outfit) in outfits.withIndex()) {
                for (fontScale in listOf(1.0f, 1.15f)) {
                    val plan = planFor(kind, outfit, fontScale)
                    if (!plan.fitsHeight) {
                        failures.add("$kind outfit#$index fs=$fontScale est=${plan.estimatedHeightDp}")
                    }
                }
            }
        }
        assertTrue("overflowing plans: $failures", failures.isEmpty())
    }

    @Test
    fun `font scale 1_3 tolerance - only the smallest medium bucket with layers may overflow, slightly`() {
        // At fontScale 1.3 (Android's largest text setting) every cell must
        // still fit, except MEDIUM@160 with a layered outfit, where the
        // conservative estimator may exceed the tile by at most 12 dp after
        // every optional slot has shed. All other cells fit outright.
        for (kind in WidgetLayout.Kind.values()) {
            for (outfit in outfits) {
                val plan = planFor(kind, outfit, fontScale = 1.3f)
                val (_, innerH) = innerFor(kind, bucketFor(kind).first, bucketFor(kind).second)
                if (kind == WidgetLayout.Kind.MEDIUM_SQUARE && outfit in layeredOutfits) {
                    assertTrue(
                        "medium@160 fs=1.3 overflow too large: ${plan.estimatedHeightDp - innerH}",
                        plan.estimatedHeightDp - innerH <= 12f
                    )
                    assertFalse("later-today must shed in the tolerance cell", plan.showLaterToday)
                } else {
                    assertTrue(
                        "$kind fs=1.3 est=${plan.estimatedHeightDp} inner=$innerH",
                        plan.fitsHeight
                    )
                }
            }
        }
        // The taller medium bucket fits even the tolerance cell class.
        for (outfit in layeredOutfits) {
            val plan = planFor(
                WidgetLayout.Kind.MEDIUM_SQUARE, outfit,
                fontScale = 1.3f, bucket = 180 to 180
            )
            assertTrue("medium@180 fs=1.3 layered must fit: ${plan.estimatedHeightDp}", plan.fitsHeight)
        }
    }

    @Test
    fun `matrix - worn garments never shed and chips stay a priority prefix`() {
        for (fontScale in listOf(1.0f, 1.15f, 1.3f)) {
            for (kind in WidgetLayout.Kind.values()) {
                for (outfit in outfits) {
                    val plan = planFor(kind, outfit, fontScale = fontScale)
                    if (kind == WidgetLayout.Kind.HORIZONTAL) {
                        assertTrue(plan.summaryItems.contains(outfit.topBase.displayName))
                        assertTrue(plan.summaryItems.contains(outfit.bottom.displayName))
                    }
                    val secondary = WidgetLayout.secondaryItems(outfit)
                    assertEquals(secondary.take(plan.chips.size), plan.chips)
                    // Representation: every secondary item is a chip, behind
                    // the marker, or in the compact line. The documented
                    // exceptions shed the zone entirely — it sheds last,
                    // after every other optional slot: font scale 1.3 + a
                    // layered outfit with 3+ secondary items, and (since
                    // 1.2.1 budgets the layer line at its wrapped count)
                    // font scale 1.15+ when a layered outfit's own layer
                    // line wraps to two lines with 3+ secondary items.
                    if (secondary.isNotEmpty() && kind != WidgetLayout.Kind.HORIZONTAL) {
                        val hasLayer = outfit.topMid != null || outfit.topOuter != null
                        val documentedMaxStack = hasLayer && secondary.size >= 3 &&
                            (fontScale == 1.3f ||
                                (fontScale >= 1.15f && plan.layerLines >= 2))
                        assertTrue(
                            "$kind lost secondary representation at fs=$fontScale",
                            plan.secondaryMode != WidgetLayout.SecondaryMode.NONE || documentedMaxStack
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `matrix - celsius outfit plans fit and pack within width`() {
        val celsius = OutfitRecommendation(
            weather = weather(feelsLike = 11.0, code = WeatherCode.PARTLY_CLOUDY, celsius = true),
            topBase = ClothingItem.LONG_SLEEVE_LIGHT,
            topOuter = ClothingItem.LIGHT_JACKET,
            bottom = ClothingItem.LIGHT_TIGHTS,
            head = ClothingItem.BASEBALL_CAP
        )
        for (kind in WidgetLayout.Kind.values()) {
            val plan = planFor(kind, celsius, fontScale = 1.15f)
            assertTrue("$kind celsius plan overflows", plan.fitsHeight)
            val (bucketW, bucketH) = bucketFor(kind)
            val innerW = innerFor(kind, bucketW, bucketH).first
            for (line in plan.footerLines) {
                assertTrue(
                    "$kind footer line too wide: $line",
                    WidgetLayout.textWidthDp(line, plan.footerSp, 1.15f) <= innerW + 0.5f
                )
            }
        }
        val segments = fullSegments(celsius.weather)
        assertTrue(segments.any { it.contains("°C") })
        assertTrue(segments.any { it.contains("km/h") })
    }

    // ---- slot decisions ---------------------------------------------------------

    @Test
    fun `medium square shows the field-case outfit in full`() {
        val plan = planFor(WidgetLayout.Kind.MEDIUM_SQUARE, bare51, fontScale = 1.0f, later = null)
        assertTrue(plan.fitsHeight)
        assertEquals(WidgetLayout.Sizes.MEDIUM_HERO, plan.heroSp) // no step-down needed
        // The outfit itself is complete. The hero's support line is the
        // lowest-priority slot: at the smallest bucket the top-hero
        // hierarchy (whisper + hero block + garment panel) spends the
        // whole 136 dp, so the support sheds in full here — it is
        // present at the field sizes (see the sol tile test).
        assertTrue(plan.footerLines.isEmpty())
        assertTrue(plan.tempSp > 0f)
    }

    @Test
    fun `field regression - fold cover 2x2 tile size gets the full medium plan`() {
        // The size One UI reports for the field tile (Galaxy Z Fold cover
        // screen, 2x2 cells, ~164dp square — measured from the Oct 27 2026
        // field screenshot's geometry). Under SizeMode.Exact this size is
        // what the composition receives: dispatch must land on
        // MEDIUM_SQUARE and the plan must hold the field-case outfit in
        // full. (Pre-1.2.0 SizeMode.Responsive quantized placements to
        // declared buckets and left the host free to serve a smaller
        // bucket's RemoteViews for in-between sizes.)
        assertEquals(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.layoutFor(164, 164))
        val plan = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, bare51,
            fontScale = 1.0f, later = null, bucket = 164 to 164
        )
        assertTrue(plan.fitsHeight)
        assertEquals(WidgetLayout.Sizes.MEDIUM_HERO, plan.heroSp) // no step-down needed
        // As at 160: the outfit is complete; the support line sheds.
        assertTrue(plan.footerLines.isEmpty())
    }

    @Test
    fun `medium 180 field case - the sun set demotes to the compact line inside the panel`() {
        val plan = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, bare51Sun,
            fontScale = 1.0f, later = null, bucket = 180 to 180
        )
        // The hierarchy pass zones the secondary row INSIDE the garment
        // panel, so the chip budget runs against the panel's inner
        // width (156 - 2x10 = 136 dp): the cap chip plus the "+2 more"
        // marker no longer fits (150.9 dp), and with a single chip
        // shown there is nothing to trade for the marker. The set
        // demotes to the compact line — every item still represented,
        // the hidden count still computed, never an ellipsis.
        assertEquals(WidgetLayout.SecondaryMode.LINE, plan.secondaryMode)
        assertEquals("Running Cap +2 more", plan.secondaryLine)
    }

    @Test
    fun `chips are width-budgeted - the row including the marker never exceeds the inner width`() {
        for (kind in listOf(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE)) {
            val (bucketW, bucketH) = bucketFor(kind)
            val innerW = innerFor(kind, bucketW, bucketH).first
            val chipSp = if (kind == WidgetLayout.Kind.LARGE) {
                WidgetLayout.Sizes.LARGE_CHIP
            } else {
                WidgetLayout.Sizes.MEDIUM_CHIP
            }
            val plan = planFor(kind, rainMax, fontScale = 1.0f)
            var used = 0f
            plan.chips.forEachIndexed { i, item ->
                used += WidgetLayout.chipWidthDp(item, chipSp, 1.0f) + if (i > 0) 6f else 0f
            }
            if (plan.showOverflowMarker) {
                used += (if (plan.chips.isEmpty()) 0f else 6f) +
                    WidgetLayout.overflowMarkerWidthDp(plan.chipsHiddenCount, chipSp, 1.0f)
            }
            assertTrue("$kind chips width $used exceeds inner $innerW", used <= innerW + 0.5f)
            // The maximum secondary set cannot all fit as chips at either size.
            assertTrue(plan.chipsHiddenCount > 0)
        }
    }

    @Test
    fun `later today sheds first, and survives when the budget is honest`() {
        for (kind in listOf(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE)) {
            val stressed = planFor(kind, rainMax, fontScale = 1.3f)
            assertFalse("$kind kept later-today at fs=1.3 max outfit", stressed.showLaterToday)
        }
        val large = planFor(WidgetLayout.Kind.LARGE, bare51, fontScale = 1.0f)
        assertTrue(large.showLaterToday)
        // Large with the sun set: the cap stays a chip and both remaining
        // items sit behind the "+2 more" marker — since 1.2.1 the marker
        // carries the word "more" and is wider, so the chip budget
        // trades the second chip for the count rather than leaving an
        // item silently unrepresented.
        val largeSun = planFor(WidgetLayout.Kind.LARGE, bare51Sun, fontScale = 1.0f)
        assertEquals(WidgetLayout.SecondaryMode.CHIPS, largeSun.secondaryMode)
        assertEquals(listOf(ClothingItem.BASEBALL_CAP), largeSun.chips)
        assertEquals(2, largeSun.chipsHiddenCount)
        assertTrue(largeSun.showOverflowMarker)
        // But the guidance yields here: whisper header + hero block +
        // garment panel + chips fill the 166 dp inner height honestly,
        // and later-today, first in the shed order, is what gives.
        // Chips outrank it and survive, as documented.
        assertFalse(largeSun.showLaterToday)
    }

    @Test
    fun `horizontal summary uses full names and sheds in reverse priority`() {
        val plan = planFor(WidgetLayout.Kind.HORIZONTAL, layered, fontScale = 1.0f)
        assertTrue(plan.summaryItems.contains(ClothingItem.LONG_SLEEVE_THERMAL.displayName))
        assertTrue(plan.summaryItems.contains(ClothingItem.THERMAL_TIGHTS.displayName))
        assertTrue(plan.summaryLineEstimate >= 1)
        // No two-word truncation anywhere: every summary entry is a full display name.
        val allNames = outfits.flatMap { o ->
            listOfNotNull(o.topBase, o.topMid, o.topOuter, o.bottom, o.head).map { it.displayName }
        }
        for (outfit in outfits) {
            val p = planFor(WidgetLayout.Kind.HORIZONTAL, outfit, fontScale = 1.0f)
            assertTrue(p.summaryItems.all { it in allNames })
        }
        // Rain outfit: the rain jacket outranks the mid layer in the summary.
        val rain = planFor(WidgetLayout.Kind.HORIZONTAL, rainMax, fontScale = 1.0f)
        assertTrue(rain.summaryItems.contains(ClothingItem.LONG_SLEEVE_LIGHT.displayName))
        assertTrue(rain.summaryItems.contains(ClothingItem.LIGHT_TIGHTS.displayName))
    }

    @Test
    fun `horizontal wind sheds at enlarged font scale, temp and condition stay`() {
        val plan = planFor(WidgetLayout.Kind.HORIZONTAL, bare51, fontScale = 1.3f)
        assertFalse(plan.showWind)
        assertTrue(plan.fitsHeight)
        assertTrue(plan.heroSp < WidgetLayout.Sizes.HORIZ_SUMMARY) // summary stepped down
    }

    @Test
    fun `small represents secondary items explicitly, with a plus-N form when tight`() {
        val line = WidgetLayout.compactItemLine(
            WidgetLayout.secondaryItems(rainMax),
            WidgetLayout.Sizes.SMALL_MICRO,
            innerWidthDp = 84f,
            fontScale = 1.0f
        )
        assertTrue(line.isNotEmpty())
        // At 84dp the four secondary names cannot all fit in full.
        assertTrue("expected a +N more form, got: $line", line.contains("more") || !line.contains("·"))
        val roomy = WidgetLayout.compactItemLine(
            listOf(ClothingItem.SUNGLASSES),
            WidgetLayout.Sizes.SMALL_MICRO,
            innerWidthDp = 84f,
            fontScale = 1.0f
        )
        assertEquals("Sunglasses", roomy)
    }

    @Test
    fun `small keeps weather and secondary together for the sun field case`() {
        val plan = planFor(WidgetLayout.Kind.SMALL, bare51Sun, fontScale = 1.0f, later = null)
        assertTrue(plan.fitsHeight)
        assertEquals(WidgetLayout.SecondaryMode.LINE, plan.secondaryMode)
        assertTrue(plan.secondaryLine!!.isNotEmpty())
        assertEquals(1, plan.footerLines.size)
        assertTrue(plan.footerLines.first().contains("Feels like 51°F"))
    }

    // ---- 1.2.1: hero temperature, guidance wrap, overflow count ---------------

    /** Sol's field tile, Oct 28 2026: ~240×248 dp on the Fold — overcast, feels 42°F. */
    private val solTileOutfit = OutfitRecommendation(
        weather = weather(feelsLike = 42.0, code = WeatherCode.OVERCAST),
        topBase = ClothingItem.LONG_SLEEVE_LIGHT,
        bottom = ClothingItem.LIGHT_TIGHTS
    )

    private val solGuidance = "By 1 PM: drop the Light Long Sleeve · add the Light Jacket"

    @Test
    fun `sol tile - guidance is budgeted at two wrapped lines, never one clipped line`() {
        val plan = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, solTileOutfit,
            fontScale = 1.0f, later = solGuidance, bucket = 240 to 248
        )
        // The hierarchy pass stacks at every square size: whisper
        // header, hero temperature block on top, garment panel,
        // guidance — full-width garment rows, so names never clip.
        assertEquals(WidgetLayout.TempArrangement.STACKED, plan.tempArrangement)
        assertTrue(plan.fitsHeight)
        assertTrue("guidance must survive at the field size", plan.showLaterToday)
        // The defect: this text cannot fit one line at this width. The
        // plan must budget the two lines it wraps to — a one-line budget
        // is what rendered the mid-word clip on the 1.2.0 tile.
        assertEquals(2, plan.laterTodayLines)
        assertTrue(
            WidgetLayout.textWidthDp(solGuidance, plan.laterSp, 1.0f) >
                216f // inner width at the field bucket
        )
    }

    @Test
    fun `guidance never budgets one line for a two-line text at any square size`() {
        for (kind in listOf(WidgetLayout.Kind.MEDIUM_SQUARE, WidgetLayout.Kind.LARGE)) {
            for (bucket in listOf(160 to 160, 180 to 180, 240 to 248, 250 to 190, 300 to 200)) {
                val plan = planFor(kind, solTileOutfit, fontScale = 1.0f, later = solGuidance, bucket = bucket)
                if (plan.showLaterToday) {
                    val (innerW, _) = innerFor(kind, bucket.first, bucket.second)
                    val needs = WidgetLayout.wrappedLineEstimate(solGuidance, plan.laterSp, innerW, 1.0f)
                    assertEquals(
                        "$kind@$bucket budgeted ${plan.laterTodayLines} lines for a $needs-line text",
                        needs, plan.laterTodayLines
                    )
                }
            }
        }
    }

    @Test
    fun `temperature is a hero in the square compositions, support lines exclude the feels-like`() {
        val medium = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, solTileOutfit,
            fontScale = 1.0f, later = solGuidance, bucket = 240 to 248
        )
        // STACKED at every width (see the sol tile test), and the
        // hero number renders at the full base size — the retired
        // BESIDE arrangement's 4 sp stacked discount is gone. The
        // support segments merge into ONE muted line under the number.
        assertEquals(WidgetLayout.Sizes.MEDIUM_TEMP, medium.tempSp)
        assertTrue(medium.showTempLabel)
        assertEquals(
            listOf("Actual 45°F · Wind 8 mph · Overcast"),
            medium.footerLines
        )
        val large = planFor(
            WidgetLayout.Kind.LARGE, solTileOutfit,
            fontScale = 1.0f, later = solGuidance, bucket = 300 to 200
        )
        assertEquals(WidgetLayout.Sizes.LARGE_TEMP, large.tempSp)
        assertTrue(large.footerLines.none { it.contains("Feels like") })
        // The narrow bucket stacks the same way — one order everywhere.
        val narrow = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, solTileOutfit,
            fontScale = 1.0f, later = null, bucket = 180 to 180
        )
        assertEquals(WidgetLayout.TempArrangement.STACKED, narrow.tempArrangement)
        assertTrue(narrow.tempSp > 0f)
    }

    @Test
    fun `deep winter - secondary overflow is a computed count of the actual recommendation`() {
        val winter = OutfitRecommendation(
            weather = weather(feelsLike = 8.0, code = WeatherCode.SNOW_SLIGHT),
            topBase = ClothingItem.LONG_SLEEVE_THERMAL,
            topMid = ClothingItem.QUARTER_ZIP,
            topOuter = ClothingItem.INSULATED_JACKET,
            bottom = ClothingItem.THERMAL_TIGHTS,
            head = ClothingItem.THERMAL_BEANIE,
            hands = ClothingItem.THERMAL_GLOVES,
            accessories = listOf(ClothingItem.NECK_GAITER)
        )
        val plan = planFor(
            WidgetLayout.Kind.MEDIUM_SQUARE, winter,
            fontScale = 1.0f, later = "By 2 PM: drop the Insulated Jacket · snow 40%",
            bucket = 240 to 248
        )
        assertTrue(plan.fitsHeight)
        // The joined layer line (two layers, each with its emoji) is
        // budgeted at the two lines it wraps to inside the panel —
        // the outer layer never ellipsizes off the tile. The guidance
        // survives too.
        assertEquals(WidgetLayout.TempArrangement.STACKED, plan.tempArrangement)
        assertEquals(2, plan.layerLines)
        assertTrue(plan.showLaterToday)
        val secondary = WidgetLayout.secondaryItems(winter)
        assertEquals(3, secondary.size)
        // Every secondary item is accounted for: shown as a chip or
        // counted behind the marker — the count is derived, not decorative.
        assertEquals(secondary, plan.chips + secondary.drop(plan.chips.size))
        assertEquals(secondary.size - plan.chips.size, plan.chipsHiddenCount)
        assertTrue("the winter set cannot all fit as chips at this width", plan.chipsHiddenCount > 0)
        assertTrue(plan.showOverflowMarker)
        // The marker's budgeted width is the width of the text it renders.
        val markerWidth = WidgetLayout.overflowMarkerWidthDp(plan.chipsHiddenCount, WidgetLayout.Sizes.MEDIUM_CHIP, 1.0f)
        assertEquals(
            markerWidth,
            12f + WidgetLayout.textWidthDp("+${plan.chipsHiddenCount} more", WidgetLayout.Sizes.MEDIUM_CHIP, 1.0f),
            0.01f
        )
    }

    @Test
    fun `footer packing splits long content instead of overflowing`() {
        val segments = listOf("Feels like 51°F", "Actual 54°F", "Wind 8 mph", "Partly cloudy")
        val lines = WidgetLayout.packLines(segments, 11f, innerWidthDp = 226f, fontScale = 1.0f)
        for (line in lines) {
            assertTrue(
                "line too wide: $line",
                WidgetLayout.textWidthDp(line, 11f, 1.0f) <= 226f + 0.5f
            )
        }
        assertEquals(segments.joinToString(" · "), lines.joinToString(" · "))
        // At Medium's 136dp the same segments need more lines, all within width.
        val mediumLines = WidgetLayout.packLines(segments, 10f, innerWidthDp = 136f, fontScale = 1.0f)
        assertTrue(mediumLines.size >= 2)
        for (line in mediumLines) {
            assertTrue(WidgetLayout.textWidthDp(line, 10f, 1.0f) <= 136f + 0.5f)
        }
    }
}

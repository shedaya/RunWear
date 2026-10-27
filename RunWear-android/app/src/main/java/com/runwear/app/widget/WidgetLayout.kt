package com.runwear.app.widget

import com.runwear.shared.domain.model.ClothingItem
import com.runwear.shared.domain.model.OutfitRecommendation
import kotlin.math.ceil

/**
 * The widget's size system (1.1.8): which composition a placement gets, and
 * which content slots that composition may show at that size.
 *
 * Before 1.1.8 the dispatch was width-only — every width under 250 dp fell
 * through to the 100×100 Small composition, so a 2×2 square tile rendered
 * four centered lines floating in a void (field findings, Oct 27 2026).
 * This object is the single source of truth for:
 *
 *  - [layoutFor] — size dispatch by width + height;
 *  - the type scale each composition renders at ([Sizes]);
 *  - slot budgeting — secondary chips are width-budgeted with a "+N"
 *    overflow marker, and optional slots shed in a fixed priority order
 *    (later-today → footer step-down → lowest-value footer segments →
 *    type step-downs → chips) instead of clipping or floating. Worn
 *    garment slots (base, layers, bottom) NEVER shed, and secondary
 *    garments are represented somehow (chips, a "+N" marker, or the
 *    compact line) at every font scale up to 1.15; only at font scale
 *    1.3 with a layered outfit carrying the maximum secondary set does
 *    the secondary zone shed — last, after every other slot.
 *
 * Everything here is pure Kotlin on dp/sp numbers so the full render
 * matrix (sizes × outfit shapes × font scales) is unit-testable — see
 * WidgetLayoutTest. The estimates use a conservative text model
 * ([LINE_FACTOR], per-character width factors); they exist to make budget
 * decisions, not to predict pixels.
 */
object WidgetLayout {

    enum class Kind { SMALL, MEDIUM_SQUARE, HORIZONTAL, LARGE }

    /** Type scale per composition — the composables read these too. */
    object Sizes {
        const val SMALL_EYEBROW = 9f
        const val SMALL_HERO = 15f
        const val SMALL_LAYER = 11f
        const val SMALL_BOTTOM = 13f
        const val SMALL_MICRO = 9f
        const val SMALL_WEATHER = 10f

        const val MEDIUM_EYEBROW = 10f
        const val MEDIUM_LOCATION = 10f
        const val MEDIUM_META = 9f
        const val MEDIUM_HERO = 17f
        const val MEDIUM_LAYER = 13f
        const val MEDIUM_CHIP = 11f
        const val MEDIUM_LATER = 11f
        const val MEDIUM_FOOTER = 10f

        const val LARGE_BRAND = 13f
        const val LARGE_LOCATION = 11f
        const val LARGE_UPDATED = 10f
        const val LARGE_EYEBROW = 10f
        const val LARGE_HERO = 18f
        const val LARGE_LAYER = 13f
        const val LARGE_CHIP = 11f
        const val LARGE_LATER = 11f
        const val LARGE_FOOTER = 11f

        const val HORIZ_SUMMARY = 15f
        const val HORIZ_TEMP = 14f
        const val HORIZ_SUB = 10f
        const val HORIZ_WIND = 9f

        /** Compact one-line representation of secondary garments. */
        const val SECONDARY_LINE = 9f
    }

    /** Shell padding per kind (horizontal, vertical) — strips and Small cannot afford 12 dp vertically. */
    fun paddingDp(kind: Kind): Pair<Int, Int> = when (kind) {
        Kind.SMALL -> 8 to 6
        Kind.MEDIUM_SQUARE -> 12 to 12
        Kind.HORIZONTAL -> 12 to 6
        Kind.LARGE -> 12 to 12
    }

    /**
     * Size dispatch. Ordered thresholds over width and height. The
     * canonical buckets must map: 100×100 → SMALL, 160×160 and 180×180 →
     * MEDIUM_SQUARE, 250×60 → HORIZONTAL, 250×190 → LARGE.
     */
    fun layoutFor(widthDp: Int, heightDp: Int): Kind = when {
        widthDp >= 250 && heightDp >= 130 -> Kind.LARGE
        heightDp <= 100 && widthDp >= 140 -> Kind.HORIZONTAL
        widthDp >= 250 -> Kind.HORIZONTAL // wide band 101–129 dp tall
        widthDp >= 125 && heightDp >= 105 -> Kind.MEDIUM_SQUARE
        widthDp >= 125 -> Kind.HORIZONTAL // squat rectangle 125–249 dp wide
        else -> Kind.SMALL
    }

    // ---- conservative text model -------------------------------------------

    private const val LINE_FACTOR = 1.35f
    private const val CHAR_WIDTH_BOLD = 0.56f
    private const val CHAR_WIDTH_REGULAR = 0.52f

    fun lineDp(fontSizeSp: Float, fontScale: Float): Float =
        fontSizeSp * LINE_FACTOR * fontScale

    fun textWidthDp(text: String, fontSizeSp: Float, fontScale: Float, bold: Boolean = false): Float =
        text.length * fontSizeSp * (if (bold) CHAR_WIDTH_BOLD else CHAR_WIDTH_REGULAR) * fontScale

    // ---- shared slot pieces -------------------------------------------------

    /** Secondary garments in render priority order: head, hands, accessories. */
    fun secondaryItems(outfit: OutfitRecommendation): List<ClothingItem> =
        listOfNotNull(outfit.head, outfit.hands) + outfit.accessories

    fun chipWidthDp(item: ClothingItem, chipSp: Float, fontScale: Float): Float =
        12f + 14f + 4f + textWidthDp(item.displayName, chipSp, fontScale) // h-pad + icon + gap + text

    fun chipHeightDp(chipSp: Float, fontScale: Float): Float =
        maxOf(14f, lineDp(chipSp, fontScale)) + 8f // icon vs text, + 4+4 vertical padding (OutfitItemChip)

    /** Width of the "+N" overflow marker chip (text-only). */
    fun overflowMarkerWidthDp(hiddenCount: Int, chipSp: Float, fontScale: Float): Float =
        12f + textWidthDp("+$hiddenCount", chipSp, fontScale)

    data class ChipBudget(
        val shown: List<ClothingItem>,
        /** Items that did not fit the row — represented by the "+N" marker when [markerShown]. */
        val hiddenCount: Int,
        val markerShown: Boolean
    )

    /**
     * Greedy width budget for a chip row: items are taken in priority
     * order while they fit; the first that doesn't fit stops the row (no
     * partial chips, no overflow). Leftover items are counted in
     * [ChipBudget.hiddenCount] and represented by a "+N" marker chip when
     * that marker itself fits the remaining width.
     */
    fun budgetedChips(
        items: List<ClothingItem>,
        chipSp: Float,
        innerWidthDp: Float,
        fontScale: Float
    ): ChipBudget {
        var used = 0f
        val shown = mutableListOf<ClothingItem>()
        for (item in items) {
            val w = chipWidthDp(item, chipSp, fontScale) + if (shown.isEmpty()) 0f else 6f
            if (used + w > innerWidthDp) break
            used += w
            shown.add(item)
        }
        val hidden = items.size - shown.size
        val markerShown = hidden > 0 &&
            used + (if (shown.isEmpty()) 0f else 6f) +
            overflowMarkerWidthDp(hidden, chipSp, fontScale) <= innerWidthDp
        return ChipBudget(shown, hidden, markerShown)
    }

    /**
     * Pack footer segments ("Feels like 51°F", "Actual 54°F", …) into lines
     * joined by " · ", starting a new line when the next segment would
     * exceed [innerWidthDp]. Never splits a segment.
     */
    fun packLines(
        segments: List<String>,
        fontSizeSp: Float,
        innerWidthDp: Float,
        fontScale: Float
    ): List<String> {
        val lines = mutableListOf<String>()
        var current: String? = null
        for (segment in segments) {
            val candidate = if (current == null) segment else "$current · $segment"
            if (current != null && textWidthDp(candidate, fontSizeSp, fontScale) > innerWidthDp) {
                lines.add(current)
                current = segment
            } else {
                current = candidate
            }
        }
        current?.let { lines.add(it) }
        return lines
    }

    /**
     * One-line representation of secondary garments: full names while they
     * fit, then the first fitting names plus "+N more" — items are
     * represented explicitly, never silently dropped.
     */
    fun compactItemLine(
        items: List<ClothingItem>,
        fontSizeSp: Float,
        innerWidthDp: Float,
        fontScale: Float
    ): String {
        if (items.isEmpty()) return ""
        val names = items.map { it.displayName }
        val full = names.joinToString(" · ")
        if (textWidthDp(full, fontSizeSp, fontScale) <= innerWidthDp) return full
        for (take in (names.size - 1) downTo 1) {
            val candidate = names.take(take).joinToString(" · ") + " +${names.size - take} more"
            if (textWidthDp(candidate, fontSizeSp, fontScale) <= innerWidthDp) return candidate
        }
        return "+${names.size} more"
    }

    // ---- the slot plan --------------------------------------------------------

    /**
     * What a Success composition renders. Garment slots are structural (not
     * flags): if the outfit has layers, every kind renders a layer line at
     * [layerSp]. Optional slots are decided here, by budget, in priority
     * order. [footerSegments] must be the kind's full segment list, with
     * the actual-temperature segment identified so it can shed last-resort.
     */
    /** How secondary garments are represented in the secondary zone. */
    enum class SecondaryMode { NONE, CHIPS, LINE }

    data class SlotPlan(
        val kind: Kind,
        /** Secondary representation mode — the composable renders exactly this. */
        val secondaryMode: SecondaryMode,
        /** Secondary chips actually shown (width-budgeted prefix; Medium/Large, CHIPS mode). */
        val chips: List<ClothingItem>,
        /** Secondary items not shown as chips (see [showOverflowMarker]). */
        val chipsHiddenCount: Int,
        /** Whether the "+N" marker chip renders after [chips] (CHIPS mode only). */
        val showOverflowMarker: Boolean,
        /** Compact secondary line text (LINE mode). */
        val secondaryLine: String?,
        /** Whether the "Later today" line renders. */
        val showLaterToday: Boolean,
        /** Footer segments packed into lines at [footerSp]. */
        val footerLines: List<String>,
        /** Footer font size after any step-down. */
        val footerSp: Float,
        /** Freshness-label font size after any step-down (Medium meta / Large updated). */
        val metaSp: Float,
        /** Horizontal only: garment names in the summary, priority-kept. */
        val summaryItems: List<String>,
        /** Horizontal only: estimated summary line count before the 2-line render cap. */
        val summaryLineEstimate: Int,
        /** Horizontal only: whether the wind line renders under temp/condition. */
        val showWind: Boolean,
        /** Hero (or Horizontal summary) font size after any step-down. */
        val heroSp: Float,
        /** Layer-line font size after any step-down. */
        val layerSp: Float,
        /** Small only: bottom-row font size after any step-down. */
        val bottomSp: Float,
        /** Eyebrow font size after any step-down (Small steps it; other kinds return their constant). */
        val eyebrowSp: Float,
        /** Small only: secondary micro-line font size after any step-down. */
        val microSp: Float,
        /** Estimated total content height of the plan, dp. */
        val estimatedHeightDp: Float,
        /** True when the plan fits the inner height after all shedding. */
        val fitsHeight: Boolean
    )

    fun plan(
        kind: Kind,
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan = when (kind) {
        Kind.SMALL -> planSmall(outfit, footerSegments, innerWidthDp, innerHeightDp, fontScale)
        Kind.MEDIUM_SQUARE -> planMedium(
            outfit, laterTodayLine, footerSegments, droppableFooterSegment, innerWidthDp, innerHeightDp, fontScale
        )
        Kind.LARGE -> planLarge(
            outfit, laterTodayLine, footerSegments, droppableFooterSegment, innerWidthDp, innerHeightDp, fontScale
        )
        Kind.HORIZONTAL -> planHorizontal(outfit, innerWidthDp, innerHeightDp, fontScale)
    }

    // SMALL — canonical 100×100. Slots: eyebrow, top row, layer line,
    // bottom row, secondary micro-line, weather line. Shed order (lowest
    // priority first): weather line, then two rounds of type step-downs
    // (hero/layer/bottom, then the micro and eyebrow sizes), and the
    // secondary line only as the last resort — it outranks type size.
    // Layers never shed; caller passes [feels, condition].
    private fun planSmall(
        outfit: OutfitRecommendation,
        footerSegments: List<String>,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan {
        val hasLayer = outfit.topMid != null || outfit.topOuter != null
        val secondary = secondaryItems(outfit)
        var showWeather = true
        var showSecondary = secondary.isNotEmpty()
        var heroSp = Sizes.SMALL_HERO
        var layerSp = Sizes.SMALL_LAYER
        var bottomSp = Sizes.SMALL_BOTTOM
        var microSp = Sizes.SMALL_MICRO
        var eyebrowSp = Sizes.SMALL_EYEBROW

        fun height(): Float {
            var h = lineDp(eyebrowSp, fontScale) + 3f // eyebrow + gap
            h += maxOf(16f, lineDp(heroSp, fontScale)) + 2f // top row + gap
            if (hasLayer) h += maxOf(14f, lineDp(layerSp, fontScale)) + 2f
            h += maxOf(16f, lineDp(bottomSp, fontScale)) // bottom row
            if (showSecondary) h += 3f + lineDp(microSp, fontScale)
            if (showWeather) h += 3f + lineDp(Sizes.SMALL_WEATHER, fontScale)
            return h
        }

        if (height() > innerHeightDp) showWeather = false
        if (height() > innerHeightDp) heroSp -= 1f
        if (height() > innerHeightDp) layerSp -= 1f
        if (height() > innerHeightDp) bottomSp -= 1f
        if (height() > innerHeightDp) heroSp -= 1f
        if (height() > innerHeightDp) layerSp -= 1f
        if (height() > innerHeightDp) bottomSp -= 1f
        if (height() > innerHeightDp) microSp -= 1f
        if (height() > innerHeightDp) eyebrowSp -= 1f
        if (height() > innerHeightDp) showSecondary = false

        return SlotPlan(
            kind = Kind.SMALL,
            secondaryMode = if (showSecondary) SecondaryMode.LINE else SecondaryMode.NONE,
            chips = emptyList(),
            chipsHiddenCount = 0,
            showOverflowMarker = false,
            secondaryLine = if (showSecondary) {
                compactItemLine(secondary, microSp, innerWidthDp, fontScale)
            } else null,
            showLaterToday = false,
            footerLines = if (showWeather) {
                packLines(footerSegments, Sizes.SMALL_WEATHER, innerWidthDp, fontScale).take(1)
            } else emptyList(),
            footerSp = Sizes.SMALL_WEATHER,
            metaSp = 0f,
            summaryItems = emptyList(),
            summaryLineEstimate = 0,
            showWind = false,
            heroSp = heroSp,
            layerSp = layerSp,
            bottomSp = bottomSp,
            eyebrowSp = eyebrowSp,
            microSp = microSp,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }

    // MEDIUM SQUARE — canonical 160×160 / 180×180. Header = eyebrow left,
    // location + updated stacked right. Then top row, layer line, bottom
    // row, chips (or the compact secondary line), later-today, footer.
    // Shed order: later-today → footer −1 sp → drop actual segment →
    // drop trailing footer segments (never feels-like) → footer to 8 sp →
    // hero −2 → layer −2 → meta −1 → hero −2 → chips (demoted to the
    // compact line, then dropped) → hero −2 again.
    private fun planMedium(
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan {
        val hasLayer = outfit.topMid != null || outfit.topOuter != null
        val secondary = secondaryItems(outfit)
        val chipBudget = budgetedChips(secondary, Sizes.MEDIUM_CHIP, innerWidthDp, fontScale)
        var chips: List<ClothingItem> = chipBudget.shown
        var secondaryMode = when {
            secondary.isEmpty() -> SecondaryMode.NONE
            chips.isNotEmpty() || chipBudget.markerShown -> SecondaryMode.CHIPS
            else -> SecondaryMode.LINE
        }
        var showLater = laterTodayLine != null
        var heroSp = Sizes.MEDIUM_HERO
        var layerSp = Sizes.MEDIUM_LAYER
        var metaSp = Sizes.MEDIUM_META
        var footerSp = Sizes.MEDIUM_FOOTER
        var segments = footerSegments
        var footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)

        fun height(): Float {
            var h = maxOf(
                lineDp(Sizes.MEDIUM_EYEBROW, fontScale),
                lineDp(Sizes.MEDIUM_LOCATION, fontScale) + lineDp(metaSp, fontScale)
            ) + 4f // header + gap
            h += maxOf(20f, lineDp(heroSp, fontScale)) + 3f // top row
            if (hasLayer) h += maxOf(16f, lineDp(layerSp, fontScale)) + 3f
            h += maxOf(20f, lineDp(heroSp, fontScale)) // bottom row
            when (secondaryMode) {
                SecondaryMode.CHIPS -> h += 6f + chipHeightDp(Sizes.MEDIUM_CHIP, fontScale)
                SecondaryMode.LINE -> h += 6f + lineDp(Sizes.SECONDARY_LINE, fontScale)
                SecondaryMode.NONE -> {}
            }
            if (showLater) h += 6f + lineDp(Sizes.MEDIUM_LATER, fontScale)
            h += 6f + footerLines.size * lineDp(footerSp, fontScale)
            return h
        }

        if (height() > innerHeightDp) showLater = false
        if (height() > innerHeightDp && footerSp > 9f) {
            footerSp -= 1f
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp && droppableFooterSegment != null && segments.contains(droppableFooterSegment)) {
            segments = segments - droppableFooterSegment
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        // Trailing footer segments (condition, then wind) shed before any
        // type size does — the feels-like segment always stays.
        while (height() > innerHeightDp && segments.size > 1) {
            segments = segments.dropLast(1)
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp && footerSp > 8f) {
            footerSp -= 1f
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp) layerSp -= 2f
        if (height() > innerHeightDp) metaSp -= 1f
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp && secondaryMode == SecondaryMode.CHIPS) {
            // Demote chips to the compact line before dropping them outright.
            secondaryMode = SecondaryMode.LINE
            chips = emptyList()
        }
        if (height() > innerHeightDp && secondaryMode == SecondaryMode.LINE) {
            secondaryMode = SecondaryMode.NONE
        }
        if (height() > innerHeightDp) heroSp -= 2f

        return SlotPlan(
            kind = Kind.MEDIUM_SQUARE,
            secondaryMode = secondaryMode,
            chips = chips,
            chipsHiddenCount = chipBudget.hiddenCount,
            showOverflowMarker = secondaryMode == SecondaryMode.CHIPS && chipBudget.markerShown,
            secondaryLine = if (secondaryMode == SecondaryMode.LINE) {
                compactItemLine(secondary, Sizes.SECONDARY_LINE, innerWidthDp, fontScale)
            } else null,
            showLaterToday = showLater,
            footerLines = footerLines,
            footerSp = footerSp,
            metaSp = metaSp,
            summaryItems = emptyList(),
            summaryLineEstimate = 0,
            showWind = false,
            heroSp = heroSp,
            layerSp = layerSp,
            bottomSp = heroSp,
            eyebrowSp = Sizes.MEDIUM_EYEBROW,
            microSp = Sizes.SECONDARY_LINE,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }

    // LARGE — canonical 250×190. Brand row (location + updated stacked at
    // the right), eyebrow, top row, layer line, bottom row, chips (or the
    // compact line), later-today, footer. Shed order: later-today → footer
    // −1 sp → drop actual segment → drop trailing footer segments (never
    // feels-like) → footer −1 sp again → hero −2 → layer −2 → updated −1 →
    // hero −2 → chips (demote, then drop) → hero −2 again.
    private fun planLarge(
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan {
        val hasLayer = outfit.topMid != null || outfit.topOuter != null
        val secondary = secondaryItems(outfit)
        val chipBudget = budgetedChips(secondary, Sizes.LARGE_CHIP, innerWidthDp, fontScale)
        var chips: List<ClothingItem> = chipBudget.shown
        var secondaryMode = when {
            secondary.isEmpty() -> SecondaryMode.NONE
            chips.isNotEmpty() || chipBudget.markerShown -> SecondaryMode.CHIPS
            else -> SecondaryMode.LINE
        }
        var showLater = laterTodayLine != null
        var heroSp = Sizes.LARGE_HERO
        var layerSp = Sizes.LARGE_LAYER
        var updatedSp = Sizes.LARGE_UPDATED
        var footerSp = Sizes.LARGE_FOOTER
        var segments = footerSegments
        var footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)

        fun height(): Float {
            var h = lineDp(Sizes.LARGE_LOCATION, fontScale) +
                lineDp(updatedSp, fontScale) + 4f // brand row + gap
            h += lineDp(Sizes.LARGE_EYEBROW, fontScale) + 2f // FOR NOW · time + gap
            h += maxOf(20f, lineDp(heroSp, fontScale)) // top row
            if (hasLayer) h += lineDp(layerSp, fontScale)
            h += maxOf(20f, lineDp(heroSp, fontScale)) // bottom row
            when (secondaryMode) {
                SecondaryMode.CHIPS -> h += 6f + chipHeightDp(Sizes.LARGE_CHIP, fontScale)
                SecondaryMode.LINE -> h += 6f + lineDp(Sizes.SECONDARY_LINE, fontScale)
                SecondaryMode.NONE -> {}
            }
            if (showLater) h += 6f + lineDp(Sizes.LARGE_LATER, fontScale)
            h += 6f + footerLines.size * lineDp(footerSp, fontScale)
            return h
        }

        if (height() > innerHeightDp) showLater = false
        if (height() > innerHeightDp && footerSp > 9f) {
            footerSp -= 1f
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp && droppableFooterSegment != null && segments.contains(droppableFooterSegment)) {
            segments = segments - droppableFooterSegment
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        // Trailing footer segments (condition, then wind) shed before any
        // type size does — the feels-like segment always stays.
        while (height() > innerHeightDp && segments.size > 1) {
            segments = segments.dropLast(1)
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp && footerSp > 8f) {
            footerSp -= 1f
            footerLines = packLines(segments, footerSp, innerWidthDp, fontScale)
        }
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp) layerSp -= 2f
        if (height() > innerHeightDp) updatedSp -= 1f
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp && secondaryMode == SecondaryMode.CHIPS) {
            secondaryMode = SecondaryMode.LINE
            chips = emptyList()
        }
        if (height() > innerHeightDp && secondaryMode == SecondaryMode.LINE) {
            secondaryMode = SecondaryMode.NONE
        }
        if (height() > innerHeightDp) heroSp -= 2f

        return SlotPlan(
            kind = Kind.LARGE,
            secondaryMode = secondaryMode,
            chips = chips,
            chipsHiddenCount = chipBudget.hiddenCount,
            showOverflowMarker = secondaryMode == SecondaryMode.CHIPS && chipBudget.markerShown,
            secondaryLine = if (secondaryMode == SecondaryMode.LINE) {
                compactItemLine(secondary, Sizes.SECONDARY_LINE, innerWidthDp, fontScale)
            } else null,
            showLaterToday = showLater,
            footerLines = footerLines,
            footerSp = footerSp,
            metaSp = updatedSp,
            summaryItems = emptyList(),
            summaryLineEstimate = 0,
            showWind = false,
            heroSp = heroSp,
            layerSp = layerSp,
            bottomSp = heroSp,
            eyebrowSp = Sizes.LARGE_EYEBROW,
            microSp = Sizes.SECONDARY_LINE,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }

    // HORIZONTAL — canonical 250×60 strip. Left: the outfit summary (full
    // names, rendered at most two lines); right: temp / condition / wind
    // stack. Summary items shed by the width model in reverse priority
    // (head, then mid, then outer) — base and bottom never shed. Wind
    // sheds by height, then the summary steps down a size.
    private fun planHorizontal(
        outfit: OutfitRecommendation,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan {
        val rightStackWidth = textWidthDp("-12°F", Sizes.HORIZ_TEMP, fontScale, bold = true) + 4f
        val summaryWidth = (innerWidthDp - rightStackWidth - 25f).coerceAtLeast(40f)

        var kept: List<ClothingItem> = listOfNotNull(
            outfit.topBase, outfit.topMid, outfit.topOuter, outfit.bottom, outfit.head
        )
        val sheddable = listOfNotNull(outfit.head, outfit.topMid, outfit.topOuter) // reverse priority
        var summarySp = Sizes.HORIZ_SUMMARY
        fun summaryLines(items: List<ClothingItem>): Int {
            val text = items.joinToString(" • ") { it.displayName }
            if (text.isEmpty()) return 0
            val w = textWidthDp(text, summarySp, fontScale, bold = true)
            return ceil(w / summaryWidth).toInt().coerceAtLeast(1)
        }
        for (item in sheddable) {
            if (summaryLines(kept) <= 2) break
            kept = kept - item
        }

        var showWind = true
        fun height(): Float {
            var right = lineDp(Sizes.HORIZ_TEMP, fontScale) + lineDp(Sizes.HORIZ_SUB, fontScale)
            if (showWind) right += lineDp(Sizes.HORIZ_WIND, fontScale)
            val summaryH = minOf(summaryLines(kept), 2) * lineDp(summarySp, fontScale)
            return maxOf(right, summaryH)
        }
        if (height() > innerHeightDp) showWind = false
        if (height() > innerHeightDp) summarySp = Sizes.HORIZ_SUMMARY - 2f

        return SlotPlan(
            kind = Kind.HORIZONTAL,
            secondaryMode = SecondaryMode.NONE,
            chips = emptyList(),
            chipsHiddenCount = 0,
            showOverflowMarker = false,
            secondaryLine = null,
            showLaterToday = false,
            footerLines = emptyList(),
            footerSp = Sizes.HORIZ_SUB,
            metaSp = 0f,
            summaryItems = kept.map { it.displayName },
            summaryLineEstimate = summaryLines(kept),
            showWind = showWind,
            heroSp = summarySp,
            layerSp = 0f,
            bottomSp = 0f,
            eyebrowSp = 0f,
            microSp = 0f,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }
}

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
 *  - slot budgeting — secondary chips are width-budgeted with a "+N more"
 *    overflow marker, and optional slots shed in a fixed priority order
 *    instead of clipping or floating. Worn garment slots (base, layers,
 *    bottom) NEVER shed, and secondary garments are represented somehow
 *    (chips, a "+N more" marker, or the compact line) at every font scale
 *    up to 1.15; only at font scale 1.3 with a layered outfit carrying
 *    the maximum secondary set does the secondary zone shed — last,
 *    after every other slot.
 *
 * 1.2.1 (field verdict on the 1.2.0 tile, Oct 28 2026) adds, for the
 * square compositions (Medium/Large):
 *  - the feels-like temperature is a HERO, not a footer segment: it
 *    renders at display size ([Sizes.MEDIUM_TEMP] / [Sizes.LARGE_TEMP])
 *    with the remaining weather segments (actual, wind, condition) as
 *    its support. Shed order for the square compositions: support
 *    segments (condition, then wind) → actual segment → temperature
 *    step-down → guidance step-down, then guidance → garment
 *    step-downs → temperature label → temperature compression
 *    interleaved with the secondary demote/drop (secondary sheds
 *    last). The feels-like number itself never sheds.
 *  - the later-today guidance is budgeted at its WRAPPED line count
 *    (capped at two lines, stepping its type down until two lines hold
 *    it) — it is never budgeted at one line and clipped mid-word.
 *  - the header reserves the refresh affordance's corner: the location
 *    column is inset from the top-right so no text runs under the ↻.
 *
 * The hierarchy pass (later on Oct 28 2026 — Sol called the first
 * 1.2.1 renders "super busy" and approved a calmer system from
 * mockups, temperature on top by his own suggestion) then fixed ONE
 * stacked order for every square size:
 *  - a whisper header: location + freshness merged into a single
 *    muted line — no FOR NOW eyebrow, no two-column header, no
 *    brand row;
 *  - the hero block ON TOP: "FEELS LIKE" eyebrow, the number, and
 *    the support segments merged into ONE muted line (packed at
 *    segment boundaries, never split mid-segment);
 *  - the garment panel: the outfit — worn rows, the layer line, and
 *    the secondary zone — zoned on one raised surface;
 *  - the guidance as one unboxed line under the panel.
 * The BESIDE arrangement is retired: the plan for a square kind is
 * always [TempArrangement.STACKED]. Type discipline: three sizes —
 * the hero number, the garment names, and one secondary scale for
 * everything else (the "FEELS LIKE" eyebrow is that scale's
 * small-caps treatment).
 *
 * Everything here is pure Kotlin on dp/sp numbers so the full render
 * matrix (sizes × outfit shapes × font scales) is unit-testable — see
 * WidgetLayoutTest. The estimates use a conservative text model
 * ([LINE_FACTOR], per-character width factors); they exist to make budget
 * decisions, not to predict pixels.
 */
object WidgetLayout {

    enum class Kind { SMALL, MEDIUM_SQUARE, HORIZONTAL, LARGE }

    /**
     * How the hero temperature sits in the square compositions. The
     * hierarchy pass retired BESIDE: the hero block always sits ON
     * TOP of the garment panel (STACKED), at every square size.
     */
    enum class TempArrangement { BESIDE, STACKED }

    /** Garment-panel horizontal padding (square compositions) — chips and lines budget inside it. */
    const val PANEL_PAD_H_DP = 10

    /** Garment-panel vertical padding (square compositions), top and bottom. */
    const val PANEL_PAD_V_DP = 4

    /** Gap between the garment rows and the secondary zone inside the panel. */
    const val PANEL_SEC_GAP_DP = 6f

    /** Fixed gaps the square plan budgets (the composables add weighted spacers on top). */
    const val HEADER_GAP_DP = 3f
    const val HERO_PANEL_GAP_DP = 5f
    const val PANEL_GUIDANCE_GAP_DP = 4f

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
        const val MEDIUM_TEMP = 30f

        const val LARGE_BRAND = 13f
        const val LARGE_LOCATION = 11f
        const val LARGE_UPDATED = 10f
        const val LARGE_EYEBROW = 10f
        const val LARGE_HERO = 18f
        const val LARGE_LAYER = 13f
        const val LARGE_CHIP = 11f
        const val LARGE_LATER = 11f
        const val LARGE_FOOTER = 11f
        const val LARGE_TEMP = 30f

        /** The "FEELS LIKE" eyebrow over the hero temperature (Medium/Large). */
        const val TEMP_LABEL = 9f

        /** The hero temperature's merged support line: actual, wind, condition. */
        const val TEMP_SUPPORT = 10f

        /** The square compositions' whisper header: location + freshness, one muted line. */
        const val SQUARE_WHISPER = 10f

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

    /**
     * Wrapped line estimate for a text rendered at [fontSizeSp] in
     * [innerWidthDp], capped at [maxLines]. The guidance line is budgeted
     * at this count — never at one line by assumption (the 1.2.0 defect:
     * a two-line guidance rendered at maxLines = 1 and clipped mid-word).
     */
    fun wrappedLineEstimate(
        text: String,
        fontSizeSp: Float,
        innerWidthDp: Float,
        fontScale: Float,
        maxLines: Int = 2
    ): Int {
        if (text.isEmpty() || innerWidthDp <= 0f) return 0
        return ceil(textWidthDp(text, fontSizeSp, fontScale) / innerWidthDp)
            .toInt().coerceIn(1, maxLines)
    }

    // ---- shared slot pieces -------------------------------------------------

    /** Secondary garments in render priority order: head, hands, accessories. */
    fun secondaryItems(outfit: OutfitRecommendation): List<ClothingItem> =
        listOfNotNull(outfit.head, outfit.hands) + outfit.accessories

    fun chipWidthDp(item: ClothingItem, chipSp: Float, fontScale: Float): Float =
        12f + 14f + 4f + textWidthDp(item.displayName, chipSp, fontScale) // h-pad + icon + gap + text

    fun chipHeightDp(chipSp: Float, fontScale: Float): Float =
        maxOf(14f, lineDp(chipSp, fontScale)) + 8f // icon vs text, + 4+4 vertical padding (OutfitItemChip)

    /** Width of the "+N more" overflow marker chip (text-only). */
    fun overflowMarkerWidthDp(hiddenCount: Int, chipSp: Float, fontScale: Float): Float =
        12f + textWidthDp("+$hiddenCount more", chipSp, fontScale)

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
        var hidden = items.size - shown.size
        fun markerFits(): Boolean = hidden > 0 &&
            used + (if (shown.isEmpty()) 0f else 6f) +
            overflowMarkerWidthDp(hidden, chipSp, fontScale) <= innerWidthDp
        // The count is part of the representation: when items remain
        // hidden but the "+N more" marker cannot fit behind the greedy
        // prefix, trade the last chip for the marker — a counted
        // remainder beats one more visible chip with the rest silently
        // dropped. (Since 1.2.1 the marker carries the word "more" and
        // is wider; this trade is what keeps it on the row.)
        while (hidden > 0 && !markerFits() && shown.size > 1) {
            val removed = shown.removeAt(shown.lastIndex)
            used -= chipWidthDp(removed, chipSp, fontScale) + 6f
            hidden += 1
        }
        return ChipBudget(shown.toList(), hidden, markerFits())
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
        /** Compact secondary line font size after any step-down (Medium/Large). */
        val secondaryLineSp: Float,
        /** Whether the "Later today" line renders. */
        val showLaterToday: Boolean,
        /**
         * Footer lines at [footerSp]. Small: the packed weather lines.
         * Medium/Large: the hero temperature's SUPPORT — the weather
         * segments after the feels-like (actual, wind, condition)
         * merged into one muted line, packed at segment boundaries
         * (so: usually a single line, two only when narrow); the
         * feels-like itself is [tempSp], never a line.
         */
        val footerLines: List<String>,
        /** Footer/support font size after any step-down. */
        val footerSp: Float,
        /** How the hero temperature sits against the outfit (Medium/Large). */
        val tempArrangement: TempArrangement,
        /** Hero feels-like font size (Medium/Large); 0 for Small/Horizontal. */
        val tempSp: Float,
        /** Whether the "FEELS LIKE" eyebrow renders over the hero temperature. */
        val showTempLabel: Boolean,
        /** Later-today font size after any width step-down (Medium/Large). */
        val laterSp: Float,
        /** Lines the later-today text is budgeted at (0 when not shown; capped at 2). */
        val laterTodayLines: Int,
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
        /** Lines the layer line is budgeted at (Medium/Large; 1 elsewhere). */
        val layerLines: Int,
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
            secondaryLineSp = Sizes.SECONDARY_LINE,
            showLaterToday = false,
            footerLines = if (showWeather) {
                packLines(footerSegments, Sizes.SMALL_WEATHER, innerWidthDp, fontScale).take(1)
            } else emptyList(),
            footerSp = Sizes.SMALL_WEATHER,
            tempArrangement = TempArrangement.STACKED,
            tempSp = 0f,
            showTempLabel = false,
            laterSp = 0f,
            laterTodayLines = 0,
            metaSp = 0f,
            summaryItems = emptyList(),
            summaryLineEstimate = 0,
            showWind = false,
            heroSp = heroSp,
            layerSp = layerSp,
            layerLines = 1,
            bottomSp = bottomSp,
            eyebrowSp = eyebrowSp,
            microSp = microSp,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }

    // MEDIUM SQUARE / LARGE — the square compositions share one planner.
    // Hierarchy pass (Oct 28 2026, Sol's verdict on the first 1.2.1
    // renders — "super busy"; system approved from mockups, temperature
    // on top by his own suggestion): ONE stacked order at every
    // square size:
    //
    //   whisper header — a single muted line, location + freshness
    //     ("New York, New York · Updated 10:25 AM"); no FOR NOW
    //     eyebrow, no two-column header, no brand row.
    //   hero block — the feels-like temperature ON TOP: "FEELS LIKE"
    //     eyebrow, the number at display size, and its support
    //     segments merged into ONE muted line (packed at segment
    //     boundaries — usually one line; two only when narrow).
    //   garment panel — the outfit zoned on one raised surface: worn
    //     rows, the layer line, and the secondary zone (chips or the
    //     compact line, "+N more" included) inside the panel.
    //   guidance — one unboxed line under the panel; the daypart
    //     time stays inline in its sentence.
    //
    // Type discipline: three sizes — the hero number, the garment
    // names, and one secondary scale for everything else (whisper,
    // support, guidance, chips, the compact line); the "FEELS LIKE"
    // eyebrow is that scale's small-caps treatment. Shed order under
    // height pressure: support segments (condition, then wind) →
    // actual segment → temperature step-down → guidance step-down,
    // then guidance → garment step-down → layer-line step-down →
    // whisper step-down → garment step-down → whisper step-down →
    // temperature label → temperature compression interleaved with
    // the secondary demote/drop (secondary sheds last).
    private fun planSquare(
        kind: Kind,
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float,
        chipSp: Float,
        heroBase: Float,
        layerBase: Float,
        tempBase: Float,
        laterBase: Float
    ): SlotPlan {
        val hasLayer = outfit.topMid != null || outfit.topOuter != null
        val secondary = secondaryItems(outfit)
        // The garment panel insets its content: chips, the layer line
        // and the compact line all budget against the panel's inner
        // width, never the tile's.
        val panelInnerWidth = innerWidthDp - 2 * PANEL_PAD_H_DP
        val chipBudget = budgetedChips(secondary, chipSp, panelInnerWidth, fontScale)
        val chips: List<ClothingItem> = chipBudget.shown
        var secondaryMode = when {
            secondary.isEmpty() -> SecondaryMode.NONE
            chipBudget.markerShown || (chips.isNotEmpty() && chipBudget.hiddenCount == 0) ->
                SecondaryMode.CHIPS
            else -> SecondaryMode.LINE
        }
        var secondaryLineSp = Sizes.SECONDARY_LINE
        // Temperature: segment 0 is the feels-like — the hero number,
        // never a line. The rest are its support, merged into one
        // muted line, shed segment by segment under pressure.
        var supportSegments = footerSegments.drop(1)

        var heroSp = heroBase
        var tempSp = tempBase
        var showTempLabel = true

        // The layer line carries each layer's emoji + name; budgeted
        // at its wrapped line count (cap 2) inside the panel, stepping
        // its type down toward one line first.
        val layers = listOfNotNull(outfit.topMid, outfit.topOuter)
        val layerText = layers.joinToString(" + ") { "${it.icon} ${it.displayName}" }
        var layerSp = layerBase
        fun layerLineCount(): Int =
            if (layers.isEmpty()) 0
            else wrappedLineEstimate(layerText, layerSp, panelInnerWidth, fontScale)
        if (layers.isNotEmpty()) {
            while (layerSp > 11f && layerLineCount() > 1) layerSp -= 1f
        }

        var metaSp = Sizes.SQUARE_WHISPER
        var showLater = laterTodayLine != null
        var laterSp = laterBase
        if (laterTodayLine != null) {
            while (laterSp > 9f &&
                ceil(textWidthDp(laterTodayLine, laterSp, fontScale) / innerWidthDp) > 2
            ) laterSp -= 1f
        }

        fun laterLines(): Int =
            if (showLater && laterTodayLine != null) {
                wrappedLineEstimate(laterTodayLine, laterSp, innerWidthDp, fontScale)
            } else 0

        fun supportLines(): List<String> =
            packLines(supportSegments, Sizes.TEMP_SUPPORT, innerWidthDp, fontScale)

        fun heroBlockHeight(): Float {
            var h = 0f
            if (showTempLabel) h += lineDp(Sizes.TEMP_LABEL, fontScale) + 2f
            h += lineDp(tempSp, fontScale)
            val lines = supportLines()
            if (lines.isNotEmpty()) {
                h += 3f + lines.size * lineDp(Sizes.TEMP_SUPPORT, fontScale)
            }
            return h
        }

        fun outfitRowsHeight(): Float {
            var h = maxOf(20f, lineDp(heroSp, fontScale)) + 2f
            if (hasLayer) {
                h += maxOf(16f, layerLineCount() * lineDp(layerSp, fontScale)) + 2f
            }
            h += maxOf(20f, lineDp(heroSp, fontScale))
            return h
        }

        fun height(): Float {
            var h = lineDp(metaSp, fontScale) + HEADER_GAP_DP
            h += heroBlockHeight()
            h += HERO_PANEL_GAP_DP
            h += PANEL_PAD_V_DP + outfitRowsHeight()
            when (secondaryMode) {
                SecondaryMode.CHIPS ->
                    h += PANEL_SEC_GAP_DP + chipHeightDp(chipSp, fontScale)
                SecondaryMode.LINE ->
                    h += PANEL_SEC_GAP_DP + lineDp(secondaryLineSp, fontScale)
                SecondaryMode.NONE -> {}
            }
            h += PANEL_PAD_V_DP
            if (showLater) {
                h += PANEL_GUIDANCE_GAP_DP + laterLines() * lineDp(laterSp, fontScale)
            }
            return h
        }

        // Secondary representation outranks the non-essential support
        // segments, the label, and type size — shed in that order.
        // Support sheds first (condition, then wind), then the
        // droppable (actual) segment.
        while (height() > innerHeightDp && supportSegments.size > 1) {
            supportSegments = supportSegments.dropLast(1)
        }
        if (height() > innerHeightDp && droppableFooterSegment != null &&
            supportSegments.contains(droppableFooterSegment)
        ) {
            supportSegments = supportSegments - droppableFooterSegment
        }
        if (height() > innerHeightDp && tempSp > 22f) tempSp -= 4f
        // Guidance steps its type down (floor 9sp) before it sheds.
        while (height() > innerHeightDp && showLater && laterSp > 9f) laterSp -= 1f
        if (height() > innerHeightDp) showLater = false
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp) layerSp -= 2f
        if (height() > innerHeightDp) metaSp -= 1f
        if (height() > innerHeightDp) heroSp -= 2f
        if (height() > innerHeightDp) metaSp -= 1f
        if (height() > innerHeightDp) showTempLabel = false
        // Temperature compression and the secondary demote/drop
        // interleave so the secondary sheds last.
        if (height() > innerHeightDp && tempSp > 15f) tempSp -= 2f
        if (height() > innerHeightDp && secondaryMode == SecondaryMode.CHIPS) {
            secondaryMode = SecondaryMode.LINE
        }
        if (height() > innerHeightDp && secondaryLineSp > 8f) secondaryLineSp -= 1f
        if (height() > innerHeightDp && tempSp > 15f) tempSp -= 2f
        if (height() > innerHeightDp && tempSp > 15f) tempSp -= 2f
        if (height() > innerHeightDp && tempSp > 15f) tempSp -= 2f
        if (height() > innerHeightDp && tempSp > 15f) tempSp -= 2f
        if (height() > innerHeightDp) secondaryMode = SecondaryMode.NONE
        if (height() > innerHeightDp) heroSp -= 2f

        return SlotPlan(
            kind = kind,
            secondaryMode = secondaryMode,
            chips = chips,
            chipsHiddenCount = chipBudget.hiddenCount,
            showOverflowMarker = secondaryMode == SecondaryMode.CHIPS && chipBudget.markerShown,
            secondaryLine = if (secondaryMode == SecondaryMode.LINE) {
                compactItemLine(secondary, secondaryLineSp, panelInnerWidth, fontScale)
            } else null,
            secondaryLineSp = secondaryLineSp,
            showLaterToday = showLater,
            footerLines = supportLines(),
            footerSp = Sizes.TEMP_SUPPORT,
            tempArrangement = TempArrangement.STACKED,
            tempSp = tempSp,
            showTempLabel = showTempLabel,
            laterSp = laterSp,
            laterTodayLines = laterLines(),
            metaSp = metaSp,
            summaryItems = emptyList(),
            summaryLineEstimate = 0,
            showWind = false,
            heroSp = heroSp,
            layerSp = layerSp,
            layerLines = layerLineCount(),
            bottomSp = heroSp,
            eyebrowSp = if (kind == Kind.LARGE) Sizes.LARGE_EYEBROW else Sizes.MEDIUM_EYEBROW,
            microSp = Sizes.SECONDARY_LINE,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }

    private fun planMedium(
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan = planSquare(
        kind = Kind.MEDIUM_SQUARE,
        outfit = outfit,
        laterTodayLine = laterTodayLine,
        footerSegments = footerSegments,
        droppableFooterSegment = droppableFooterSegment,
        innerWidthDp = innerWidthDp,
        innerHeightDp = innerHeightDp,
        fontScale = fontScale,
        chipSp = Sizes.MEDIUM_CHIP,
        heroBase = Sizes.MEDIUM_HERO,
        layerBase = Sizes.MEDIUM_LAYER,
        tempBase = Sizes.MEDIUM_TEMP,
        laterBase = Sizes.MEDIUM_LATER
    )

    private fun planLarge(
        outfit: OutfitRecommendation,
        laterTodayLine: String?,
        footerSegments: List<String>,
        droppableFooterSegment: String?,
        innerWidthDp: Float,
        innerHeightDp: Float,
        fontScale: Float
    ): SlotPlan = planSquare(
        kind = Kind.LARGE,
        outfit = outfit,
        laterTodayLine = laterTodayLine,
        footerSegments = footerSegments,
        droppableFooterSegment = droppableFooterSegment,
        innerWidthDp = innerWidthDp,
        innerHeightDp = innerHeightDp,
        fontScale = fontScale,
        chipSp = Sizes.LARGE_CHIP,
        heroBase = Sizes.LARGE_HERO,
        layerBase = Sizes.LARGE_LAYER,
        tempBase = Sizes.LARGE_TEMP,
        laterBase = Sizes.LARGE_LATER
    )

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
            secondaryLineSp = Sizes.SECONDARY_LINE,
            showLaterToday = false,
            footerLines = emptyList(),
            footerSp = Sizes.HORIZ_SUB,
            tempArrangement = TempArrangement.STACKED,
            tempSp = 0f,
            showTempLabel = false,
            laterSp = 0f,
            laterTodayLines = 0,
            metaSp = 0f,
            summaryItems = kept.map { it.displayName },
            summaryLineEstimate = summaryLines(kept),
            showWind = showWind,
            heroSp = summarySp,
            layerSp = 0f,
            layerLines = 1,
            bottomSp = 0f,
            eyebrowSp = 0f,
            microSp = 0f,
            estimatedHeightDp = height(),
            fitsHeight = height() <= innerHeightDp
        )
    }
}

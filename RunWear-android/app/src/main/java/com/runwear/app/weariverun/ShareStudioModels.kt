package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TemperatureUnit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * RW-31 unified share — pure models (GEN-6 Wave B, Android stage 1).
 *
 * Pure Kotlin (java.time only, plus the engine's [TemperatureUnit]
 * projection for the caption-input builder), ported behaviour-for-
 * behaviour from the iOS lane's ShareStudioModels.swift — same
 * strings, same arithmetic — so the two platforms can never drift
 * into two caption systems again. Everything the Share Studio,
 * the Morning Poster, and the Kit Card render is decided HERE,
 * from the day record:
 *
 *   * the three export canvases (design §6) and their exact
 *     pixel sizes;
 *   * the four-template caption system (§5) with the why-line
 *     library and the picker ordering;
 *   * the moment forms (§5.1): a lower-case mid-sentence form
 *     for captions and a sentence form for the poster's Tier 2;
 *   * the face-anchor model and crop math (§4.3);
 *   * the poster tier lines (§4.2) and the Kit Card grid rules
 *     (§4.5).
 *
 * Captions NEVER describe or claim the photograph (§4.4): every
 * template speaks about the call and the kit, so the same set
 * serves personal and stock days unchanged. [ShareCaptionInput]
 * carries NO photograph input anywhere — that is the honesty
 * mechanism, by construction.
 */

// MARK: - Export formats (§6)

/** An export size in whole pixels. */
data class SharePixelSize(val width: Int, val height: Int)

/** A composition canvas size in points. */
data class SharePointSize(val width: Double, val height: Double)

enum class ShareExportFormat {
    FEED, STORIES, SQUARE;

    val label: String
        get() = when (this) {
            FEED -> com.runwear.app.i18n.AppEnglish.SHARE_FORMAT_FEED
            STORIES -> com.runwear.app.i18n.AppEnglish.SHARE_FORMAT_STORIES
            SQUARE -> com.runwear.app.i18n.AppEnglish.SHARE_FORMAT_SQUARE
        }

    /** Exact export pixels — the verify gate requires the
     * finished raster to match these dimensions exactly. */
    val exportPixelSize: SharePixelSize
        get() = when (this) {
            FEED -> SharePixelSize(1080, 1350)
            STORIES -> SharePixelSize(1080, 1920)
            SQUARE -> SharePixelSize(1080, 1080)
        }

    /** The composition canvas in points. Every canvas is 360 pt
     * wide, so one render scale (3×) lands every format on its
     * exact export pixels. */
    val canvasPointSize: SharePointSize
        get() = when (this) {
            FEED -> SharePointSize(360.0, 450.0)
            STORIES -> SharePointSize(360.0, 640.0)
            SQUARE -> SharePointSize(360.0, 360.0)
        }

    val renderScale: Double
        get() = exportPixelSize.width / canvasPointSize.width

    val aspect: Double
        get() = canvasPointSize.width / canvasPointSize.height
}

// MARK: - Moment readings + forms (§5.1)

/**
 * A day's moment, typed — the Android mirror of iOS's
 * MomentReading. [Daypart] is part of the parity contract (iOS
 * reads its own MomentReading from its day surfaces); today's
 * Android entry data can only produce [Now] and [Clock] — see
 * [ShareMomentResolver.readingFor].
 */
sealed class MomentReading {

    object Now : MomentReading()

    data class Daypart(val dayWord: String, val part: DayPart) : MomentReading()

    data class Clock(val hour12: Int, val isPM: Boolean) : MomentReading()

    enum class DayPart(val rawValue: String) {
        MORNING("morning"),
        AFTERNOON("afternoon"),
        EVENING("evening"),
        NIGHT("night");

        companion object {
            /** The part of day for a 24-hour clock hour — the
             * production site's settled bands, identical to
             * iOS's MomentReading.DayPart.forHour. */
            fun forHour(hour: Int): DayPart = when (hour) {
                in 5..11 -> MORNING
                in 12..16 -> AFTERNOON
                in 17..21 -> EVENING
                else -> NIGHT
            }
        }
    }
}

object ShareMomentResolver {

    /** The day's moment in its two grammatical seats:
     * [caption] is the lower-case mid-sentence form ("this
     * morning", "tonight", "today, 3:00 PM", or the stamped
     * "Thursday, November 5" for a past chapter); [poster] is
     * the sentence form for the poster's Tier 2 ("This
     * morning", "Tonight", "Today, 3:00 PM", stamped as-is). */
    data class Forms(
        val caption: String,
        val poster: String,
        val isPastChapter: Boolean
    )

    fun forms(
        reading: MomentReading,
        dayStart: LocalDate,
        lastSavedAt: LocalDateTime,
        now: LocalDateTime,
        today: LocalDate
    ): Forms {
        // `today` is the caller-resolved local date of `now`
        // (kept as its own parameter so tests and the entry
        // bridge pin the day boundary explicitly, as iOS pins
        // its calendar).
        if (dayStart.isBefore(today)) {
            val stamped = stampedForm(dayStart)
            return Forms(stamped, stamped, true)
        }
        return when (reading) {
            is MomentReading.Clock -> {
                val time = com.runwear.app.i18n.AppEnglish.SHARE_CLOCK_TIME.format(reading.hour12, if (reading.isPM) com.runwear.app.i18n.AppEnglish.SHARE_MERIDIEM_PM else com.runwear.app.i18n.AppEnglish.SHARE_MERIDIEM_AM)
                Forms(com.runwear.app.i18n.AppEnglish.SHARE_CLOCK_CAPTION.format(time), com.runwear.app.i18n.AppEnglish.SHARE_CLOCK_POSTER.format(time), false)
            }
            is MomentReading.Daypart -> {
                if (reading.dayWord == "Tomorrow") {
                    Forms(
                        com.runwear.app.i18n.AppEnglish.SHARE_TOMORROW_CAPTION.format(reading.part.rawValue),
                        com.runwear.app.i18n.AppEnglish.SHARE_TOMORROW_POSTER.format(reading.part.rawValue),
                        false
                    )
                } else {
                    Forms(
                        com.runwear.app.i18n.AppEnglish.SHARE_DAYPART_CAPTION.format(reading.dayWord.lowercase(Locale.US), reading.part.rawValue),
                        com.runwear.app.i18n.AppEnglish.SHARE_DAYPART_POSTER.format(reading.dayWord, reading.part.rawValue),
                        false
                    )
                }
            }
            MomentReading.Now -> {
                when (MomentReading.DayPart.forHour(lastSavedAt.hour)) {
                    MomentReading.DayPart.MORNING ->
                        Forms(com.runwear.app.i18n.AppEnglish.SHARE_MORNING_CAPTION, com.runwear.app.i18n.AppEnglish.SHARE_MORNING_POSTER, false)
                    MomentReading.DayPart.AFTERNOON ->
                        Forms(com.runwear.app.i18n.AppEnglish.SHARE_AFTERNOON_CAPTION, com.runwear.app.i18n.AppEnglish.SHARE_AFTERNOON_POSTER, false)
                    MomentReading.DayPart.EVENING, MomentReading.DayPart.NIGHT ->
                        Forms(com.runwear.app.i18n.AppEnglish.SHARE_TONIGHT_CAPTION, com.runwear.app.i18n.AppEnglish.SHARE_TONIGHT_POSTER, false)
                }
            }
        }
    }

    /** "Thursday, November 5" — the stamped form for past
     * chapters (full weekday, full month, day number). */
    fun stampedForm(dayStart: LocalDate): String =
        dayStart.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))

    /**
     * The bridge FROM the Android day entry: the entry's recorded
     * moment label ("9 p.m." style, see [WearMomentLabel]) parses
     * to a [MomentReading.Clock]; a null label (or one no clock
     * reading can have produced) is the [MomentReading.Now]
     * moment. Daypart is not producible from today's entry data —
     * the parity note of record: iOS reads its own MomentReading;
     * Android's recorded label is a clock reading.
     */
    fun readingFor(entry: WearIveRunDayEntry): MomentReading {
        val label = entry.momentLabel?.trim() ?: return MomentReading.Now
        val parts = label.split(" ")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull()
            if (hour != null && hour in 1..12 &&
                (parts[1] == "p.m." || parts[1] == "a.m.")
            ) {
                return MomentReading.Clock(hour, parts[1] == "p.m.")
            }
        }
        return MomentReading.Now
    }
}

// MARK: - Caption system (§5)

/**
 * Everything a caption is filled from — the day record's own
 * numbers, in the user's display unit as whole degrees, plus
 * the canonical Fahrenheit values the why-line thresholds are
 * defined in (swing ≥ 15°, feels-like ≤ 40°). Temperatures are
 * DISPLAY-unit whole degrees except [swingF] / [feelsF], which
 * are canonical °F.
 */
data class ShareCaptionInput(
    val place: String,
    val captionMoment: String,
    val isPastChapter: Boolean,
    val feels: Int,
    val actual: Int,
    val low: Int,
    val high: Int,
    /** Canonical Fahrenheit swing (high − low) for the ≥ 15 rule. */
    val swingF: Double,
    /** Canonical Fahrenheit feels-like for the ≤ 40 rule. */
    val feelsF: Double,
    val conditionLower: String,
    /** Headline garments, up to four, in the day's order. */
    val kitNames: List<String>
) {
    val swingDisplay: Int get() = high - low

    companion object {
        /**
         * The caption input for a recorded day, filled from the
         * entry's own record — the same record that composes the
         * card, so the words and the image can never contradict
         * each other. Display values go through
         * [TemperatureUnit.displayValue] (truncation), mirroring
         * iOS's number-from-formatted. Where a legacy record
         * lacks the day's low/high, they derive as min/max of
         * the day's own actual and feels-like — derived from
         * the day's own numbers, never fabricated.
         */
        fun forEntry(
            entry: WearIveRunDayEntry,
            unit: TemperatureUnit,
            today: LocalDate,
            now: LocalDateTime
        ): ShareCaptionInput {
            val settledAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(entry.settledAtMillis),
                ZoneId.systemDefault()
            )
            val forms = ShareMomentResolver.forms(
                reading = ShareMomentResolver.readingFor(entry),
                dayStart = entry.date,
                lastSavedAt = settledAt,
                now = now,
                today = today
            )
            val feelsCanon = entry.feelsLikeF
            val actualCanon = entry.actualF ?: entry.feelsLikeF
            val lowCanon: Double
            val highCanon: Double
            if (entry.dayLowF != null && entry.dayHighF != null) {
                lowCanon = entry.dayLowF
                highCanon = entry.dayHighF
            } else {
                lowCanon = min(actualCanon, feelsCanon)
                highCanon = max(actualCanon, feelsCanon)
            }
            return ShareCaptionInput(
                place = entry.place,
                captionMoment = forms.caption,
                isPastChapter = forms.isPastChapter,
                feels = unit.displayValue(feelsCanon),
                actual = unit.displayValue(actualCanon),
                low = unit.displayValue(lowCanon),
                high = unit.displayValue(highCanon),
                swingF = highCanon - lowCanon,
                feelsF = feelsCanon,
                conditionLower = entry.conditionLabel.lowercase(Locale.US),
                kitNames = entry.items
            )
        }
    }
}

enum class ShareCaptionTemplate {
    FEELS_LIKE_STORY, SWING, SCRAPBOOK, SHORT_CALL;

    val title: String
        get() = when (this) {
            FEELS_LIKE_STORY -> com.runwear.app.i18n.AppEnglish.SHARE_TITLE_FEELS_STORY
            SWING -> com.runwear.app.i18n.AppEnglish.SHARE_TITLE_SWING
            SCRAPBOOK -> com.runwear.app.i18n.AppEnglish.SHARE_TITLE_SCRAPBOOK
            SHORT_CALL -> com.runwear.app.i18n.AppEnglish.SHARE_TITLE_SHORT_CALL
        }
}

object ShareCaptionComposer {

    /** The kit as bullet lines — one garment per line, up to
     * four headline garments (§5.3: bullets, always). */
    fun kitBlock(kitNames: List<String>): String =
        kitNames.take(4).joinToString("\n") { com.runwear.app.i18n.AppEnglish.SHARE_KIT_BULLET.format(it) }

    /** The why-line library (T1's anchor element). Deterministic
     * selection, identical on both platforms: a big swing wins,
     * then cold, then the anchor line. */
    fun whyLine(input: ShareCaptionInput): String {
        if (input.swingF >= 15) {
            return com.runwear.app.i18n.AppEnglish.SHARE_WHY_SWING.format(input.swingDisplay)
        }
        if (input.feelsF <= 40) {
            return com.runwear.app.i18n.AppEnglish.SHARE_WHY_COLD.format(input.feels)
        }
        return com.runwear.app.i18n.AppEnglish.SHARE_WHY_ANCHOR
    }

    fun caption(template: ShareCaptionTemplate, input: ShareCaptionInput): String {
        val kit = kitBlock(input.kitNames)
        return when (template) {
            ShareCaptionTemplate.FEELS_LIKE_STORY ->
                com.runwear.app.i18n.AppEnglish.SHARE_CAPTION_FEELS_STORY.format(input.place, input.captionMoment, input.actual, input.feels, kit, whyLine(input))
            ShareCaptionTemplate.SWING ->
                com.runwear.app.i18n.AppEnglish.SHARE_CAPTION_SWING.format(input.place, input.low, input.high, input.captionMoment, input.feels, kit)
            ShareCaptionTemplate.SCRAPBOOK ->
                com.runwear.app.i18n.AppEnglish.SHARE_CAPTION_SCRAPBOOK.format(input.place, input.captionMoment, input.feels, input.conditionLower, kit)
            ShareCaptionTemplate.SHORT_CALL ->
                com.runwear.app.i18n.AppEnglish.SHARE_CAPTION_SHORT_CALL.format(input.place, input.captionMoment, input.feels, input.conditionLower, kit)
        }
    }

    /** Picker order (§5.2): a past chapter leads with the
     * Scrapbook; else a swing day (≥ 15°) leads with the Swing;
     * else the Feels-Like Story leads. The Short Call is always
     * present, always last. The first template is the
     * pre-selected caption. */
    fun pickerOrder(input: ShareCaptionInput): List<ShareCaptionTemplate> {
        val lead = when {
            input.isPastChapter -> ShareCaptionTemplate.SCRAPBOOK
            input.swingF >= 15 -> ShareCaptionTemplate.SWING
            else -> ShareCaptionTemplate.FEELS_LIKE_STORY
        }
        val rest = listOf(
            ShareCaptionTemplate.FEELS_LIKE_STORY,
            ShareCaptionTemplate.SWING,
            ShareCaptionTemplate.SCRAPBOOK
        ).filter { it != lead }
        return listOf(lead) + rest + ShareCaptionTemplate.SHORT_CALL
    }

    fun orderedCaptions(
        input: ShareCaptionInput
    ): List<Pair<ShareCaptionTemplate, String>> =
        pickerOrder(input).map { it to caption(it, input) }

    /** The activity sheet's subject (Mail) — §8. */
    fun subject(input: ShareCaptionInput): String =
        com.runwear.app.i18n.AppEnglish.SHARE_SUBJECT.format(input.place, input.feels)

    /** The rich link metadata title — §8. */
    fun linkTitle(input: ShareCaptionInput): String =
        com.runwear.app.i18n.AppEnglish.SHARE_LINK_TITLE.format(input.place, input.feels)
}

// MARK: - Face anchor (§4.3)

/**
 * A photograph's face anchor: the runner's head position in the
 * source image, in top-left-normalised coordinates (y down).
 * Stored null-safely on the day record; resolved at render time
 * as stored → detection → default.
 */
data class ShareFaceAnchor(
    val centerX: Double,
    val centerY: Double,
    val width: Double,
    val height: Double
) {
    val topNormalized: Double get() = centerY - height / 2

    companion object {
        /** The default when no face is found: the pool's known
         * composition point — the upper-middle of the frame,
         * where full-body running photographs place the runner.
         * NEVER the geometric centre of the picture. */
        val DEFAULT_UPPER_MIDDLE = ShareFaceAnchor(
            centerX = 0.5, centerY = 0.16, width = 0.22, height = 0.14
        )

        /** Build an anchor from a detected face rect in
         * top-left-normalised coordinates (x, y = the rect's
         * top-left corner). */
        fun fromFaceRect(x: Double, y: Double, width: Double, height: Double): ShareFaceAnchor =
            ShareFaceAnchor(
                centerX = x + width / 2, centerY = y + height / 2,
                width = width, height = height
            )
    }
}

object ShareAnchorCrop {

    /** Where the face should sit in the composed canvas: centre
     * in the upper third, with at least this much of the canvas
     * height as headroom above the head. */
    const val TARGET_FACE_CENTER_Y = 0.24
    const val MIN_HEADROOM = 0.08

    /** A crop window in image coordinates. */
    data class CropRect(
        val x: Double,
        val y: Double,
        val width: Double,
        val height: Double
    ) {
        val maxX: Double get() = x + width
        val maxY: Double get() = y + height
    }

    /** The face's placement inside the composed canvas, as
     * fractions of the canvas height. */
    data class Placement(
        val faceCenterYFraction: Double,
        val headroomFraction: Double
    )

    /**
     * The crop rect (image coordinates) of aspect [canvasAspect]
     * computed FROM the anchor: the largest window of that
     * aspect that fits the image, positioned so the face lands
     * in the upper third with its headroom intact. Widening or
     * narrowing between canvases moves the window sideways and
     * downward — extra Stories height goes to the scene below
     * the runner, never to sky above the head. Ported line-for-
     * line from iOS's ShareAnchorCrop.cropRect.
     */
    fun cropRect(
        imageW: Double,
        imageH: Double,
        anchor: ShareFaceAnchor,
        canvasAspect: Double
    ): CropRect {
        if (imageW <= 0 || imageH <= 0 || canvasAspect <= 0) {
            return CropRect(0.0, 0.0, 0.0, 0.0)
        }
        var windowWidth = imageW
        var windowHeight = windowWidth / canvasAspect
        if (windowHeight > imageH) {
            windowHeight = imageH
            windowWidth = windowHeight * canvasAspect
        }
        val faceCenterPxX = anchor.centerX * imageW
        val faceCenterPxY = anchor.centerY * imageH
        val faceTopPx = anchor.topNormalized * imageH
        // Horizontal: centre the window on the face.
        val originX = min(max(faceCenterPxX - windowWidth / 2, 0.0), imageW - windowWidth)
        // Vertical: the centre rule and the headroom rule both
        // bound the origin from above; the tighter (smaller)
        // origin wins, then clamp into the image.
        val centerOriginY = faceCenterPxY - TARGET_FACE_CENTER_Y * windowHeight
        val headroomOriginY = faceTopPx - MIN_HEADROOM * windowHeight
        val originY = min(max(min(centerOriginY, headroomOriginY), 0.0), imageH - windowHeight)
        return CropRect(originX, originY, windowWidth, windowHeight)
    }

    fun cropRect(
        imageW: Int,
        imageH: Int,
        anchor: ShareFaceAnchor,
        canvasAspect: Double
    ): CropRect = cropRect(imageW.toDouble(), imageH.toDouble(), anchor, canvasAspect)

    /** The face's placement inside the composed canvas, as
     * fractions of the canvas height — the render-and-verify
     * step checks these before rasterising (§4.3). */
    fun placement(
        crop: CropRect,
        anchor: ShareFaceAnchor,
        imageW: Double,
        imageH: Double
    ): Placement {
        if (crop.height <= 0 || imageH <= 0) return Placement(0.0, 0.0)
        val faceCenterPx = anchor.centerY * imageH
        val faceTopPx = anchor.topNormalized * imageH
        return Placement(
            faceCenterYFraction = (faceCenterPx - crop.y) / crop.height,
            headroomFraction = (faceTopPx - crop.y) / crop.height
        )
    }

    /** True iff the placement honours the rule: face in the
     * upper third, headroom at least the minimum. */
    fun placementIsValid(
        crop: CropRect,
        anchor: ShareFaceAnchor,
        imageW: Double,
        imageH: Double
    ): Boolean {
        val placed = placement(crop, anchor, imageW, imageH)
        return placed.faceCenterYFraction <= (1.0 / 3.0) + 0.001 &&
            placed.headroomFraction >= MIN_HEADROOM - 0.001
    }
}

// MARK: - Poster composition (§4.2)

object SharePosterModel {

    /** Tier 2 — "{place} · {Moment}." (sentence moment form). */
    fun tier2Line(place: String, posterMoment: String): String =
        "$place · $posterMoment."

    /** Tier 3 — the outfit in words: headline garments (up to
     * four) joined by middots. Promoted: the clear second read
     * after the numeral. */
    fun tier3Line(kitNames: List<String>): String =
        kitNames.take(4).joinToString(" · ")

    /** Tier 4 — the conditions micro-line. */
    fun tier4Line(condition: String, actualDegrees: Int, windMph: Int): String =
        "$condition · Actual $actualDegrees° · Wind $windMph mph"

    /** The yield rule: under pressure the micro-line yields —
     * never the outfit line. The Square canvas with a long
     * outfit line is the pressure case; Feed and Stories always
     * have room. */
    fun showsMicroLine(format: ShareExportFormat, tier3Text: String): Boolean =
        !(format == ShareExportFormat.SQUARE && tier3Text.length > 64)

    /** Stories safe zones (§6): the lockup begins below the top
     * 250 px at export scale; the block (with the footer folded
     * in as its last line) ends above the bottom 340 px. */
    fun lockupTopInsetPoints(format: ShareExportFormat): Double =
        if (format == ShareExportFormat.STORIES) 250 / format.renderScale else 14.0

    fun blockBottomInsetPoints(format: ShareExportFormat): Double =
        if (format == ShareExportFormat.STORIES) 340 / format.renderScale else 16.0

    /** On Stories the footer folds into the informative block as
     * its last line; on Feed and Square it is its own row. */
    fun footerFoldsIntoBlock(format: ShareExportFormat): Boolean =
        format == ShareExportFormat.STORIES
}

// MARK: - Kit Card composition (§4.5)

/** A tile centre as fractions of the canvas (0..1 on both axes). */
data class ShareSamplePoint(val x: Double, val y: Double)

object ShareKitCardModel {

    /** The day's garments in WEARING ORDER — the existing outfit
     * ordering the entry already stores. The grid preserves it;
     * nothing re-sorts. */
    fun orderedItems(names: List<String>, keys: List<String>): List<Pair<String, String>> =
        names.zip(keys)

    /** Notes ride on Feed and Square; on Stories they yield
     * first under height pressure — a tall kit (more than four
     * garments) drops its notes there, names stand alone. */
    fun showsNotes(format: ShareExportFormat, itemCount: Int): Boolean = when (format) {
        ShareExportFormat.FEED, ShareExportFormat.SQUARE -> true
        ShareExportFormat.STORIES -> itemCount <= 4
    }

    /** The most garments a canvas can carry with name + Shop ›
     * labels and the foot intact — canvas geometry, not a
     * design preference: Square fits two rows, Feed three,
     * Stories four. */
    fun maxItems(format: ShareExportFormat): Int = when (format) {
        ShareExportFormat.FEED -> 6
        ShareExportFormat.SQUARE -> 4
        ShareExportFormat.STORIES -> 8
    }

    /** The header weather line: "{feels}° feels like ·
     * {condition} · {place} · {Moment}." */
    fun weatherLine(
        feelsDegrees: Int,
        condition: String,
        place: String,
        posterMoment: String
    ): String = com.runwear.app.i18n.AppEnglish.SHARE_WEATHER_LINE.format(feelsDegrees, condition, place, posterMoment)

    const val DISCLOSURE: String = com.runwear.app.i18n.AppEnglish.SHARE_DISCLOSURE

    // Grid layout — one geometry for view and verifier.
    //
    // Measured against the Kit Card's real stack: header block
    // ≈ 84 pt below the top padding; the label block under a
    // tile is name (25) + note (16) + Shop › (19) = 60 with
    // notes, 44 without.

    const val KIT_HEADER_HEIGHT = 84.0
    const val KIT_FOOTER_HEIGHT = 44.0
    const val KIT_VERTICAL_PADDING = 28.0
    const val KIT_ROW_SPACING = 10.0
    const val KIT_MAX_TILE_SIDE = 148.0
    const val KIT_MIN_TILE_SIDE = 44.0

    /** Below this side the notes yield and the side recomputes. */
    const val KIT_NOTES_YIELD_BELOW = 64.0

    data class KitGridLayout(
        val tileSide: Double,
        val showsNotes: Boolean,
        val labelHeight: Double,
        val rowPitch: Double
    )

    fun gridLayout(format: ShareExportFormat, itemCount: Int): KitGridLayout {
        val rows = max(1, (max(1, itemCount) + 1) / 2)
        val canvasHeight = format.canvasPointSize.height
        fun side(notesShown: Boolean): Double {
            val label = if (notesShown) 60.0 else 44.0
            val available = canvasHeight - KIT_VERTICAL_PADDING - KIT_HEADER_HEIGHT -
                KIT_FOOTER_HEIGHT - (rows - 1) * KIT_ROW_SPACING - rows * label
            return available / rows
        }
        var notes = showsNotes(format, itemCount)
        var tile = side(notes)
        if (tile < KIT_NOTES_YIELD_BELOW && notes) {
            notes = false
            tile = side(false)
        }
        val label = if (notes) 60.0 else 44.0
        val clamped = tile.coerceIn(KIT_MIN_TILE_SIDE, KIT_MAX_TILE_SIDE)
        return KitGridLayout(
            tileSide = clamped, showsNotes = notes,
            labelHeight = label, rowPitch = clamped + label + KIT_ROW_SPACING
        )
    }

    /** One grid column's width: the canvas minus the card's
     * horizontal padding (20 each side) and the inter-column
     * spacing (12), halved. The grid is two FIXED columns —
     * each tile is centred within its column, and a lone
     * last-row tile stays in the first column (the iOS view's
     * two flexible LazyVGrid columns render exactly this).
     * Shared by the Kit Card view and [tileSamplePoints] so
     * the two cannot drift apart. */
    fun columnWidth(canvasWidth: Double): Double =
        (canvasWidth - 40 - 12) / 2

    /** Canvas-fraction centres of the grid's tiles, for the
     * raster verifier's per-tile samples — the same geometry
     * the Kit Card renders. */
    fun tileSamplePoints(
        format: ShareExportFormat,
        itemCount: Int
    ): List<ShareSamplePoint> {
        if (itemCount <= 0) return emptyList()
        val canvas = format.canvasPointSize
        val layout = gridLayout(format, itemCount)
        val columnWidth = columnWidth(canvas.width)
        val top = KIT_VERTICAL_PADDING / 2 + KIT_HEADER_HEIGHT
        return (0 until itemCount).map { index ->
            val column = index % 2
            val row = index / 2
            val x = 20 + columnWidth / 2 + column * (columnWidth + 12)
            val y = top + layout.tileSide / 2 + row * layout.rowPitch
            ShareSamplePoint(x / canvas.width, y / canvas.height)
        }
    }
}

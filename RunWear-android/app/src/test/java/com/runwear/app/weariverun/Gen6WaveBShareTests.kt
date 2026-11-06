package com.runwear.app.weariverun

import com.runwear.shared.domain.model.TemperatureUnit
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GEN-6 Wave B (stage 1) — RW-31 unified share pins, the Android
 * mirror of the iOS lane's Gen6WaveBShareTests:
 *
 *   * the caption composer: exact template strings, the why-line
 *     library's deterministic selection, the picker ordering,
 *     and the honesty rule — personal-day and stock-day fixtures
 *     produce IDENTICAL caption sets, because no template
 *     describes or claims the photograph;
 *   * the moment forms (§5.1): same-day lower-case, stamped past
 *     chapters, planned-hour;
 *   * the face-anchor crop math (§4.3): face in the upper third,
 *     ≥ 8% headroom, per canvas, including an off-centre face;
 *   * the poster tier lines (§4.2) and the Kit Card structure
 *     (§4.5): wearing order, notes yield, grid geometry;
 *   * the day record's null-safe share fields (legacy decode +
 *     round-trip through [WearIveRunStore]).
 */
class Gen6WaveBShareTests {

    // MARK: - Caption fixtures
    //
    // The Kansas City day of the design mockups. The composer
    // input carries NO photograph information at all — that is
    // the §4.4 honesty mechanism — so the "personal" and "stock"
    // fixtures below are the same day record, and their caption
    // sets must be identical.

    private val kansasCityInput = ShareCaptionInput(
        place = "Kansas City, MO",
        captionMoment = "this morning",
        isPastChapter = false,
        feels = 57, actual = 61, low = 55, high = 63,
        swingF = 8.0, feelsF = 57.0,
        conditionLower = "clear sky",
        kitNames = listOf("Short-sleeve shirt", "Running shorts", "Sunglasses")
    )

    private val kansasCityKit = "• Short-sleeve shirt\n• Running shorts\n• Sunglasses"

    @Test
    fun `captions - Kansas City fixture - all four templates exact`() {
        val input = kansasCityInput
        assertEquals(
            "Kansas City, MO, this morning: 61° on the thermometer, 57° on the run. RunWear's call:\n$kansasCityKit\nDressed for the feels-like, not the thermometer. #RunAnyWear · runwear.ai",
            ShareCaptionComposer.caption(ShareCaptionTemplate.FEELS_LIKE_STORY, input)
        )
        assertEquals(
            "Kansas City, MO today: 55° to 63°. Dressed for this morning's 57°:\n$kansasCityKit\nOne outfit, no mid-run regret. #RunAnyWear · runwear.ai",
            ShareCaptionComposer.caption(ShareCaptionTemplate.SWING, input)
        )
        assertEquals(
            "One for the scrapbook: Kansas City, MO, this morning, 57° feels like, clear sky. The kit:\n$kansasCityKit\nEvery run, remembered — run, weather or not. #RunAnyWear · @runwear.ai",
            ShareCaptionComposer.caption(ShareCaptionTemplate.SCRAPBOOK, input)
        )
        assertEquals(
            "Kansas City, MO, this morning: 57° feels like, clear sky. The call:\n$kansasCityKit\n#RunAnyWear · @runwear.ai",
            ShareCaptionComposer.caption(ShareCaptionTemplate.SHORT_CALL, input)
        )
    }

    @Test
    fun `captions - personal and stock days produce identical sets`() {
        val personalDay = kansasCityInput
        val stockDay = kansasCityInput
        for (template in ShareCaptionTemplate.entries) {
            assertEquals(
                "$template must not change between personal and stock days",
                ShareCaptionComposer.caption(template, personalDay),
                ShareCaptionComposer.caption(template, stockDay)
            )
        }
        assertEquals(
            ShareCaptionComposer.pickerOrder(personalDay),
            ShareCaptionComposer.pickerOrder(stockDay)
        )
        assertEquals(
            ShareCaptionComposer.orderedCaptions(personalDay),
            ShareCaptionComposer.orderedCaptions(stockDay)
        )
    }

    @Test
    fun `captions - never claim the photograph and obey voice rules`() {
        for (template in ShareCaptionTemplate.entries) {
            val text = ShareCaptionComposer.caption(template, kansasCityInput)
            val lowered = text.lowercase()
            for (claim in listOf("my photo", "your photo", "photo of me", "i wore", "picture of")) {
                assertFalse(
                    "$template must never describe or claim the photograph: $text",
                    lowered.contains(claim)
                )
            }
            assertFalse("$template: no interrogative hooks (§5.3)", text.contains("?"))
            assertFalse("$template: no exclamation marks (§5.3)", text.contains("!"))
            assertEquals(
                "$template: the hashtag travels exactly once, as text",
                1, text.split("#RunAnyWear").size - 1
            )
        }
    }

    @Test
    fun `captions - kit is bullets and capped at four`() {
        val input = kansasCityInput.copy(
            kitNames = listOf("A", "B", "C", "D", "E", "F")
        )
        val text = ShareCaptionComposer.caption(ShareCaptionTemplate.SHORT_CALL, input)
        assertTrue(text.contains("• A\n• B\n• C\n• D"))
        assertFalse("the headline kit caps at four garments", text.contains("• E"))
    }

    // MARK: - Why-line library (deterministic, both platforms)

    @Test
    fun `why line - swing wins at fifteen or more`() {
        val input = ShareCaptionInput(
            place = "Kansas City, MO", captionMoment = "this morning",
            isPastChapter = false, feels = 60, actual = 66, low = 50, high = 70,
            swingF = 20.0, feelsF = 60.0, conditionLower = "cloudy",
            kitNames = kansasCityInput.kitNames
        )
        assertEquals(
            "One outfit for a 20-degree swing.",
            ShareCaptionComposer.whyLine(input)
        )
    }

    @Test
    fun `why line - swing beats cold`() {
        val input = ShareCaptionInput(
            place = "Chicago, IL", captionMoment = "this morning",
            isPastChapter = false, feels = 30, actual = 36, low = 28, high = 48,
            swingF = 20.0, feelsF = 30.0, conditionLower = "snow",
            kitNames = listOf("Thermal tights")
        )
        assertEquals(
            "One outfit for a 20-degree swing.",
            ShareCaptionComposer.whyLine(input)
        )
    }

    @Test
    fun `why line - cold at forty or below`() {
        val input = ShareCaptionInput(
            place = "Chicago, IL", captionMoment = "this morning",
            isPastChapter = false, feels = 34, actual = 38, low = 32, high = 40,
            swingF = 8.0, feelsF = 34.0, conditionLower = "snow",
            kitNames = listOf("Thermal tights")
        )
        assertEquals("No guesswork at 34°.", ShareCaptionComposer.whyLine(input))
    }

    @Test
    fun `why line - default is the anchor line`() {
        assertEquals(
            "Dressed for the feels-like, not the thermometer.",
            ShareCaptionComposer.whyLine(kansasCityInput)
        )
    }

    // MARK: - Picker ordering

    @Test
    fun `picker order - default leads feels-like story, short call last`() {
        assertEquals(
            listOf(
                ShareCaptionTemplate.FEELS_LIKE_STORY,
                ShareCaptionTemplate.SWING,
                ShareCaptionTemplate.SCRAPBOOK,
                ShareCaptionTemplate.SHORT_CALL
            ),
            ShareCaptionComposer.pickerOrder(kansasCityInput)
        )
    }

    @Test
    fun `picker order - swing day leads swing`() {
        val input = ShareCaptionInput(
            place = "Denver, CO", captionMoment = "this morning",
            isPastChapter = false, feels = 60, actual = 66, low = 50, high = 70,
            swingF = 20.0, feelsF = 60.0, conditionLower = "sunny",
            kitNames = listOf("Shorts")
        )
        assertEquals(
            listOf(
                ShareCaptionTemplate.SWING,
                ShareCaptionTemplate.FEELS_LIKE_STORY,
                ShareCaptionTemplate.SCRAPBOOK,
                ShareCaptionTemplate.SHORT_CALL
            ),
            ShareCaptionComposer.pickerOrder(input)
        )
    }

    @Test
    fun `picker order - past chapter leads scrapbook even on a swing day`() {
        val input = ShareCaptionInput(
            place = "Miami, FL", captionMoment = "Thursday, November 5",
            isPastChapter = true, feels = 88, actual = 91, low = 70, high = 92,
            swingF = 22.0, feelsF = 88.0, conditionLower = "sunny",
            kitNames = listOf("Tank top")
        )
        assertEquals(
            listOf(
                ShareCaptionTemplate.SCRAPBOOK,
                ShareCaptionTemplate.FEELS_LIKE_STORY,
                ShareCaptionTemplate.SWING,
                ShareCaptionTemplate.SHORT_CALL
            ),
            ShareCaptionComposer.pickerOrder(input)
        )
    }

    @Test
    fun `subject and link title - exact forms`() {
        assertEquals(
            "RunWear — Kansas City, MO, 57° feels like",
            ShareCaptionComposer.subject(kansasCityInput)
        )
        assertEquals(
            "RunWear — Kansas City, MO · 57° feels like",
            ShareCaptionComposer.linkTitle(kansasCityInput)
        )
    }

    @Test
    fun `template titles - exact`() {
        assertEquals("Feels-Like Story", ShareCaptionTemplate.FEELS_LIKE_STORY.title)
        assertEquals("Swing", ShareCaptionTemplate.SWING.title)
        assertEquals("Scrapbook", ShareCaptionTemplate.SCRAPBOOK.title)
        assertEquals("Short Call", ShareCaptionTemplate.SHORT_CALL.title)
    }

    @Test
    fun `export formats - labels, exact pixels, one render scale`() {
        assertEquals("Feed 4:5", ShareExportFormat.FEED.label)
        assertEquals("Stories 9:16", ShareExportFormat.STORIES.label)
        assertEquals("Square", ShareExportFormat.SQUARE.label)
        assertEquals(SharePixelSize(1080, 1350), ShareExportFormat.FEED.exportPixelSize)
        assertEquals(SharePixelSize(1080, 1920), ShareExportFormat.STORIES.exportPixelSize)
        assertEquals(SharePixelSize(1080, 1080), ShareExportFormat.SQUARE.exportPixelSize)
        for (format in ShareExportFormat.entries) {
            assertEquals(3.0, format.renderScale, 0.0001)
            assertEquals(360.0, format.canvasPointSize.width, 0.0001)
        }
    }

    // MARK: - Moment forms (§5.1)

    @Test
    fun `moment - same-day now - morning and tonight`() {
        val day = LocalDate.of(2026, 11, 5)
        val morning = ShareMomentResolver.forms(
            reading = MomentReading.Now,
            dayStart = day,
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 7, 0),
            now = LocalDateTime.of(2026, 11, 5, 9, 0),
            today = day
        )
        assertEquals("this morning", morning.caption)
        assertEquals("This morning", morning.poster)
        assertFalse(morning.isPastChapter)

        val evening = ShareMomentResolver.forms(
            reading = MomentReading.Now,
            dayStart = day,
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 19, 0),
            now = LocalDateTime.of(2026, 11, 5, 20, 0),
            today = day
        )
        assertEquals("tonight", evening.caption)
        assertEquals("Tonight", evening.poster)
    }

    @Test
    fun `moment - planned hour today uses today clock form`() {
        val day = LocalDate.of(2026, 11, 5)
        val forms = ShareMomentResolver.forms(
            reading = MomentReading.Clock(hour12 = 3, isPM = true),
            dayStart = day,
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 9, 0),
            now = LocalDateTime.of(2026, 11, 5, 9, 0),
            today = day
        )
        assertEquals("today, 3:00 PM", forms.caption)
        assertEquals("Today, 3:00 PM", forms.poster)
        assertFalse(forms.isPastChapter)
    }

    @Test
    fun `moment - past chapter uses the stamped form`() {
        // Thursday, November 5, 2026, read back on November 7.
        val forms = ShareMomentResolver.forms(
            reading = MomentReading.Now,
            dayStart = LocalDate.of(2026, 11, 5),
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 7, 0),
            now = LocalDateTime.of(2026, 11, 7, 9, 0),
            today = LocalDate.of(2026, 11, 7)
        )
        assertEquals("Thursday, November 5", forms.caption)
        assertEquals("Thursday, November 5", forms.poster)
        assertTrue(forms.isPastChapter)
    }

    @Test
    fun `moment - daypart forms for tomorrow and a weekday`() {
        val day = LocalDate.of(2026, 11, 5)
        val tomorrow = ShareMomentResolver.forms(
            reading = MomentReading.Daypart("Tomorrow", MomentReading.DayPart.EVENING),
            dayStart = day,
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 9, 0),
            now = LocalDateTime.of(2026, 11, 5, 9, 0),
            today = day
        )
        assertEquals("tomorrow evening", tomorrow.caption)
        assertEquals("Tomorrow evening", tomorrow.poster)

        val friday = ShareMomentResolver.forms(
            reading = MomentReading.Daypart("Friday", MomentReading.DayPart.MORNING),
            dayStart = day,
            lastSavedAt = LocalDateTime.of(2026, 11, 5, 9, 0),
            now = LocalDateTime.of(2026, 11, 5, 9, 0),
            today = day
        )
        assertEquals("friday morning", friday.caption)
        assertEquals("Friday morning", friday.poster)
    }

    @Test
    fun `moment - day part hour bands match the settled bands`() {
        assertEquals(MomentReading.DayPart.MORNING, MomentReading.DayPart.forHour(5))
        assertEquals(MomentReading.DayPart.MORNING, MomentReading.DayPart.forHour(11))
        assertEquals(MomentReading.DayPart.AFTERNOON, MomentReading.DayPart.forHour(12))
        assertEquals(MomentReading.DayPart.AFTERNOON, MomentReading.DayPart.forHour(16))
        assertEquals(MomentReading.DayPart.EVENING, MomentReading.DayPart.forHour(17))
        assertEquals(MomentReading.DayPart.EVENING, MomentReading.DayPart.forHour(21))
        assertEquals(MomentReading.DayPart.NIGHT, MomentReading.DayPart.forHour(22))
        assertEquals(MomentReading.DayPart.NIGHT, MomentReading.DayPart.forHour(4))
    }

    @Test
    fun `moment - reading for entry parses the recorded clock label`() {
        val base = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 5),
            place = "Kansas City, MO",
            feelsLikeF = 57.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Shorts"),
            heroRef = null
        )
        assertEquals(
            MomentReading.Clock(hour12 = 9, isPM = true),
            ShareMomentResolver.readingFor(base.copy(momentLabel = "9 p.m."))
        )
        assertEquals(
            MomentReading.Clock(hour12 = 12, isPM = false),
            ShareMomentResolver.readingFor(base.copy(momentLabel = "12 a.m."))
        )
        assertEquals(
            MomentReading.Now,
            ShareMomentResolver.readingFor(base.copy(momentLabel = null))
        )
    }

    // MARK: - Caption input builder

    @Test
    fun `caption input for entry - recorded day carries its own numbers`() {
        val entry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 5),
            place = "Kansas City, MO",
            feelsLikeF = 57.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Short-sleeve shirt", "Running shorts", "Sunglasses"),
            heroRef = null,
            settledAtMillis = 1_762_300_000_000L,
            momentLabel = "3 p.m.",
            actualF = 61.0,
            windMph = 6.0,
            dayLowF = 55.0,
            dayHighF = 63.0,
            itemKeys = listOf("SHORT_SLEEVE_SHIRT", "RUNNING_SHORTS", "SUNGLASSES")
        )
        val input = ShareCaptionInput.forEntry(
            entry, TemperatureUnit.FAHRENHEIT,
            today = LocalDate.of(2026, 11, 5),
            now = LocalDateTime.of(2026, 11, 5, 9, 0)
        )
        assertEquals("Kansas City, MO", input.place)
        assertEquals("today, 3:00 PM", input.captionMoment)
        assertFalse(input.isPastChapter)
        assertEquals(57, input.feels)
        assertEquals(61, input.actual)
        assertEquals(55, input.low)
        assertEquals(63, input.high)
        assertEquals(8.0, input.swingF, 0.0001)
        assertEquals(57.0, input.feelsF, 0.0001)
        assertEquals("clear sky", input.conditionLower)
        assertEquals(entry.items, input.kitNames)
    }

    @Test
    fun `caption input for entry - legacy record derives range from its own numbers`() {
        // A pre-Wave-B record: no actualF, no day range. The
        // actual falls back to the feels-like and the range
        // derives as min/max of the day's own numbers.
        val entry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 5),
            place = "Kansas City, MO",
            feelsLikeF = 57.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Shorts"),
            heroRef = null,
            settledAtMillis = 1_762_300_000_000L
        )
        val input = ShareCaptionInput.forEntry(
            entry, TemperatureUnit.FAHRENHEIT,
            today = LocalDate.of(2026, 11, 7),
            now = LocalDateTime.of(2026, 11, 7, 9, 0)
        )
        assertEquals("Thursday, November 5", input.captionMoment)
        assertTrue(input.isPastChapter)
        assertEquals(57, input.actual)
        assertEquals(57, input.low)
        assertEquals(57, input.high)
        assertEquals(0.0, input.swingF, 0.0001)
    }

    // MARK: - Face anchor + crop math (§4.3)

    @Test
    fun `anchor - default is upper middle, never the centre`() {
        assertEquals(0.5, ShareFaceAnchor.DEFAULT_UPPER_MIDDLE.centerX, 0.0001)
        assertEquals(0.16, ShareFaceAnchor.DEFAULT_UPPER_MIDDLE.centerY, 0.0001)
        assertTrue(
            "the default anchor is the upper-middle composition point",
            ShareFaceAnchor.DEFAULT_UPPER_MIDDLE.centerY < 0.25
        )
        assertTrue(ShareFaceAnchor.DEFAULT_UPPER_MIDDLE.centerY != 0.5)
    }

    @Test
    fun `anchor - from face rect centres on the rect`() {
        val anchor = ShareFaceAnchor.fromFaceRect(x = 0.30, y = 0.10, width = 0.20, height = 0.10)
        assertEquals(0.40, anchor.centerX, 0.0001)
        assertEquals(0.15, anchor.centerY, 0.0001)
        assertEquals(0.10, anchor.topNormalized, 0.0001)
    }

    @Test
    fun `anchor crop - centred face - upper third with headroom on every canvas`() {
        val imageW = 1000.0
        val imageH = 1500.0
        val anchor = ShareFaceAnchor(centerX = 0.5, centerY = 0.16, width = 0.30, height = 0.12)
        for (format in ShareExportFormat.entries) {
            val rect = ShareAnchorCrop.cropRect(imageW, imageH, anchor, format.aspect)
            assertEquals(
                "$format: the window carries the canvas aspect",
                format.aspect, rect.width / rect.height, 0.01
            )
            val placed = ShareAnchorCrop.placement(rect, anchor, imageW, imageH)
            assertTrue(
                "$format: the face sits in the upper third",
                placed.faceCenterYFraction <= 1.0 / 3.0 + 0.001
            )
            assertTrue(
                "$format: at least 8% headroom above the head",
                placed.headroomFraction >= 0.08 - 0.001
            )
            assertTrue(
                "$format: the placement honours the rule",
                ShareAnchorCrop.placementIsValid(rect, anchor, imageW, imageH)
            )
        }
    }

    @Test
    fun `anchor crop - off-centre face - still upper third with headroom`() {
        // The gen-5 failure shape: a face well off centre —
        // right side, lower than the pool's usual band.
        val imageW = 1200.0
        val imageH = 1600.0
        val anchor = ShareFaceAnchor(centerX = 0.78, centerY = 0.30, width = 0.24, height = 0.15)
        for (format in ShareExportFormat.entries) {
            val rect = ShareAnchorCrop.cropRect(imageW, imageH, anchor, format.aspect)
            assertEquals(format.aspect, rect.width / rect.height, 0.01)
            // The face is fully inside the window — never clipped.
            val faceTop = anchor.topNormalized * imageH
            val faceBottom = faceTop + anchor.height * imageH
            assertTrue("$format: the head is not clipped", faceTop >= rect.y - 1)
            assertTrue("$format: the chin is not clipped", faceBottom <= rect.maxY + 1)
            val placed = ShareAnchorCrop.placement(rect, anchor, imageW, imageH)
            assertTrue(
                "$format: off-centre face still lands in the upper third",
                placed.faceCenterYFraction <= 1.0 / 3.0 + 0.001
            )
            assertTrue(
                "$format: off-centre face keeps its headroom",
                placed.headroomFraction >= 0.08 - 0.001
            )
            // The window follows the face sideways, too.
            val faceCenterCanvasX = (anchor.centerX * imageW - rect.x) / rect.width
            assertTrue(
                "$format: the window tracks the face horizontally",
                faceCenterCanvasX > 0.5
            )
        }
    }

    @Test
    fun `anchor crop - stories extra height goes below the runner`() {
        // Same image, same anchor: the Stories window must not
        // sit HIGHER than the Feed window — extra height goes
        // to the scene below, never to sky above the head.
        val imageW = 1000.0
        val imageH = 2000.0
        val anchor = ShareFaceAnchor(centerX = 0.5, centerY = 0.14, width = 0.26, height = 0.12)
        val feed = ShareAnchorCrop.cropRect(
            imageW, imageH, anchor, ShareExportFormat.FEED.aspect
        )
        val stories = ShareAnchorCrop.cropRect(
            imageW, imageH, anchor, ShareExportFormat.STORIES.aspect
        )
        assertTrue(stories.y <= feed.y + 1)
        assertTrue(
            ShareAnchorCrop.placement(stories, anchor, imageW, imageH)
                .headroomFraction >= 0.08 - 0.001
        )
    }

    // MARK: - Poster composition (§4.2)

    @Test
    fun `poster tier lines - exact forms`() {
        assertEquals(
            "Miami, FL · This morning.",
            SharePosterModel.tier2Line(place = "Miami, FL", posterMoment = "This morning")
        )
        assertEquals(
            "Tank top · Short shorts · Visor · Reflective gear",
            SharePosterModel.tier3Line(
                kitNames = listOf("Tank top", "Short shorts", "Visor", "Reflective gear", "Extra")
            )
        )
        assertEquals(
            "Clear sky · Actual 61° · Wind 5 mph",
            SharePosterModel.tier4Line(condition = "Clear sky", actualDegrees = 61, windMph = 5)
        )
    }

    @Test
    fun `poster - micro line yields first, never the outfit`() {
        val longOutfit = SharePosterModel.tier3Line(
            kitNames = listOf(
                "Merino wool long sleeve base layer",
                "Insulated waterproof windbreaker jacket",
                "Thermal tights", "Trail shoes"
            )
        )
        assertFalse(
            "on the Square canvas under pressure the micro-line yields",
            SharePosterModel.showsMicroLine(ShareExportFormat.SQUARE, longOutfit)
        )
        assertTrue(
            "the Feed canvas holds all four tiers",
            SharePosterModel.showsMicroLine(ShareExportFormat.FEED, longOutfit)
        )
        assertTrue(
            "a short outfit keeps the micro-line even on Square",
            SharePosterModel.showsMicroLine(ShareExportFormat.SQUARE, "Tank · Shorts")
        )
    }

    @Test
    fun `poster - stories safe zones and folded footer`() {
        assertEquals(
            250.0,
            SharePosterModel.lockupTopInsetPoints(ShareExportFormat.STORIES) *
                ShareExportFormat.STORIES.renderScale,
            0.5
        )
        assertEquals(
            340.0,
            SharePosterModel.blockBottomInsetPoints(ShareExportFormat.STORIES) *
                ShareExportFormat.STORIES.renderScale,
            0.5
        )
        assertTrue(SharePosterModel.footerFoldsIntoBlock(ShareExportFormat.STORIES))
        assertFalse(SharePosterModel.footerFoldsIntoBlock(ShareExportFormat.FEED))
        assertFalse(SharePosterModel.footerFoldsIntoBlock(ShareExportFormat.SQUARE))
    }

    // MARK: - Kit Card structure (§4.5)

    @Test
    fun `kit card - wearing order is the entry's order`() {
        val items = ShareKitCardModel.orderedItems(
            names = listOf("Visor", "Tank top", "Short shorts", "Sunglasses"),
            keys = listOf("VISOR", "TANK_TOP", "SHORT_SHORTS", "SUNGLASSES")
        )
        assertEquals(
            "the grid preserves the existing outfit ordering verbatim — nothing re-sorts",
            listOf("Visor", "Tank top", "Short shorts", "Sunglasses"),
            items.map { it.first }
        )
        assertEquals(
            listOf("VISOR", "TANK_TOP", "SHORT_SHORTS", "SUNGLASSES"),
            items.map { it.second }
        )
    }

    @Test
    fun `kit card - notes ride feed and square, yield on stories under pressure`() {
        assertTrue(ShareKitCardModel.showsNotes(ShareExportFormat.FEED, itemCount = 8))
        assertTrue(ShareKitCardModel.showsNotes(ShareExportFormat.SQUARE, itemCount = 8))
        assertTrue(ShareKitCardModel.showsNotes(ShareExportFormat.STORIES, itemCount = 4))
        assertFalse(
            "on Stories a tall kit drops its notes first — names stand alone",
            ShareKitCardModel.showsNotes(ShareExportFormat.STORIES, itemCount = 5)
        )
    }

    @Test
    fun `kit card - grid layout fits every canvas and yields notes under pressure`() {
        assertTrue(ShareKitCardModel.gridLayout(ShareExportFormat.FEED, itemCount = 3).showsNotes)
        assertTrue(ShareKitCardModel.gridLayout(ShareExportFormat.SQUARE, itemCount = 2).showsNotes)
        assertFalse(
            "Square yields notes at 3 items — the design's yield rule under real geometry",
            ShareKitCardModel.gridLayout(ShareExportFormat.SQUARE, itemCount = 3).showsNotes
        )
        for (format in ShareExportFormat.entries) {
            for (count in 1..ShareKitCardModel.maxItems(format)) {
                val layout = ShareKitCardModel.gridLayout(format, count)
                val rows = (count + 1) / 2
                val totalHeight = ShareKitCardModel.KIT_VERTICAL_PADDING +
                    ShareKitCardModel.KIT_HEADER_HEIGHT +
                    ShareKitCardModel.KIT_FOOTER_HEIGHT +
                    rows * layout.tileSide + rows * layout.labelHeight +
                    (rows - 1) * ShareKitCardModel.KIT_ROW_SPACING
                assertTrue(
                    "$format with $count items must fit its canvas",
                    totalHeight <= format.canvasPointSize.height + 6
                )
                assertTrue(layout.tileSide >= 44)
            }
        }
    }

    @Test
    fun `kit card - weather line and disclosure exact`() {
        assertEquals(
            "88° feels like · Sunny · Miami, FL · This morning.",
            ShareKitCardModel.weatherLine(
                feelsDegrees = 88, condition = "Sunny",
                place = "Miami, FL", posterMoment = "This morning"
            )
        )
        assertEquals(
            "As an Amazon Associate, RunWear earns from qualifying purchases.",
            ShareKitCardModel.DISCLOSURE
        )
    }

    @Test
    fun `kit card - tile sample points cover every tile inside the canvas`() {
        for (format in ShareExportFormat.entries) {
            val count = minOf(5, ShareKitCardModel.maxItems(format))
            val points = ShareKitCardModel.tileSamplePoints(format, count)
            assertEquals("$format", count, points.size)
            for (point in points) {
                assertTrue(
                    "$format: sample $point lies inside the canvas",
                    point.x > 0 && point.x < 1 && point.y > 0 && point.y < 1
                )
            }
        }
    }

    // MARK: - Day record share fields (null-safe)

    @Test
    fun `entry - legacy payload without share fields decodes with nulls`() {
        // A hand-written legacy record: none of the Wave B keys.
        val legacyJson = """
            [{"date":"2026-11-05","place":"Kansas City, MO","sessionId":"s1",
              "settledAtMillis":1762300000000,"feelsLikeF":57.0,
              "condition":"Clear sky","snowing":false,"raining":false,
              "items":["Shorts"],"heroRef":"https://example.com/hero.jpg"}]
        """.trimIndent()
        val legacy = WearIveRunStore.decode(legacyJson).single()
        assertNull(legacy.actualF)
        assertNull(legacy.windMph)
        assertNull(legacy.dayLowF)
        assertNull(legacy.dayHighF)
        assertNull(legacy.faceAnchor)
        assertTrue(legacy.itemKeys.isEmpty())
        assertEquals(
            "Kansas City, MO", legacy.place
        )
        assertEquals(57.0, legacy.feelsLikeF, 0.0001)
    }

    @Test
    fun `entry - share fields round-trip through the store`() {
        val entry = WearIveRunDayEntry(
            date = LocalDate.of(2026, 11, 5),
            place = "Kansas City, MO",
            feelsLikeF = 57.0,
            conditionLabel = "Clear sky",
            isSnowing = false,
            isRaining = false,
            items = listOf("Short-sleeve shirt", "Running shorts"),
            heroRef = "https://example.com/hero.jpg",
            sessionId = "s1",
            settledAtMillis = 1_762_300_000_000L,
            actualF = 61.0,
            windMph = 6.0,
            dayLowF = 55.0,
            dayHighF = 63.0,
            itemKeys = listOf("SHORT_SLEEVE_SHIRT", "RUNNING_SHORTS"),
            faceAnchor = ShareFaceAnchor(centerX = 0.5, centerY = 0.15, width = 0.24, height = 0.14)
        )
        val roundTripped = WearIveRunStore.decode(
            WearIveRunStore.encode(listOf(entry))
        ).single()
        assertEquals(61.0, roundTripped.actualF!!, 0.0001)
        assertEquals(6.0, roundTripped.windMph!!, 0.0001)
        assertEquals(55.0, roundTripped.dayLowF!!, 0.0001)
        assertEquals(63.0, roundTripped.dayHighF!!, 0.0001)
        assertEquals(
            listOf("SHORT_SLEEVE_SHIRT", "RUNNING_SHORTS"), roundTripped.itemKeys
        )
        assertEquals(0.5, roundTripped.faceAnchor!!.centerX, 0.0001)
        assertEquals(0.15, roundTripped.faceAnchor!!.centerY, 0.0001)
        assertEquals(0.24, roundTripped.faceAnchor!!.width, 0.0001)
        assertEquals(0.14, roundTripped.faceAnchor!!.height, 0.0001)
    }
}

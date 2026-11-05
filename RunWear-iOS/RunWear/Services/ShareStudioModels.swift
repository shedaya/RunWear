import Foundation

// MARK: - RW-31 unified share — pure models (GEN-6 Wave B, iOS)
//
// Foundation-only and dual-membered (app + RunWearTests), like
// RW26CollectionModels, so the test target pins the arithmetic
// itself. Everything the Share Studio, the Morning Poster, and
// the Kit Card render is decided HERE, from the day record:
//
//   * the three export canvases (§6) and their exact pixel sizes;
//   * the four-template caption system (§5) with the why-line
//     library and the picker ordering — IDENTICAL selection logic
//     to the Android lane's composer, by construction of the
//     shared spec (RW-31 §5 + the Wave B brief);
//   * the moment forms (§5.1): a lower-case mid-sentence form for
//     captions and a sentence form for the poster's Tier 2;
//   * the face-anchor model and crop math (§4.3);
//   * the poster tier lines (§4.2) and the Kit Card grid rules
//     (§4.5).
//
// Captions NEVER describe or claim the photograph (§4.4): every
// template speaks about the call and the kit, so the same set
// serves personal and stock days unchanged.

// MARK: - Export formats (§6)

enum ShareExportFormat: String, CaseIterable, Identifiable {
    case feed
    case stories
    case square

    var id: String { rawValue }

    var label: String {
        switch self {
        case .feed: return "Feed 4:5"
        case .stories: return "Stories 9:16"
        case .square: return "Square"
        }
    }

    /// Exact export pixels — the verify gate (DA-16) requires the
    /// finished raster to match these dimensions exactly.
    var exportPixelSize: CGSize {
        switch self {
        case .feed: return CGSize(width: 1080, height: 1350)
        case .stories: return CGSize(width: 1080, height: 1920)
        case .square: return CGSize(width: 1080, height: 1080)
        }
    }

    /// The composition canvas in points. Every canvas is 360 pt
    /// wide, so one render scale (3×) lands every format on its
    /// exact export pixels.
    var canvasPointSize: CGSize {
        switch self {
        case .feed: return CGSize(width: 360, height: 450)
        case .stories: return CGSize(width: 360, height: 640)
        case .square: return CGSize(width: 360, height: 360)
        }
    }

    var renderScale: CGFloat {
        exportPixelSize.width / canvasPointSize.width
    }

    var aspect: CGFloat {
        canvasPointSize.width / canvasPointSize.height
    }
}

// MARK: - Display-degree helpers
//
// The day surfaces hand the share layer the app's formatted
// temperatures ("57°F" / "14°C"). Captions and card lines print
// the bare degree form ("57°"), and the composer works in whole
// display degrees.

enum ShareDisplayDegrees {
    /// "57°F" → 57; "−4°C" → -4 (the unit's minus variants too).
    static func number(fromFormatted formatted: String) -> Int {
        let cleaned = formatted
            .replacingOccurrences(of: "−", with: "-")
            .filter { $0.isNumber || $0 == "-" }
        return Int(cleaned) ?? 0
    }

    /// "57°F" → "57°".
    static func text(fromFormatted formatted: String) -> String {
        "\(number(fromFormatted: formatted))°"
    }
}

// MARK: - Moment forms (§5.1)

enum ShareMomentResolver {
    /// The day's moment in its two grammatical seats:
    /// `caption` is the lower-case mid-sentence form ("this
    /// morning", "tonight", "today, 3:00 PM", or the stamped
    /// "Thursday, November 5" for a past chapter); `poster` is
    /// the sentence form for the poster's Tier 2 ("This
    /// morning", "Tonight", "Today, 3:00 PM", stamped as-is).
    static func forms(
        momentReading: MomentReading,
        dayStart: Date,
        lastSavedAt: Date,
        now: Date,
        calendar: Calendar = .current
    ) -> (caption: String, poster: String, isPastChapter: Bool) {
        let isPast = calendar.startOfDay(for: dayStart) < calendar.startOfDay(for: now)
        if isPast {
            let stamped = stampedForm(dayStart, calendar: calendar)
            return (stamped, stamped, true)
        }
        switch momentReading {
        case .clock(let hour12, let isPM):
            let time = "\(hour12):00 \(isPM ? "PM" : "AM")"
            return ("today, \(time)", "Today, \(time)", false)
        case .daypart(let dayWord, let part):
            if dayWord == "Tomorrow" {
                return ("tomorrow \(part.rawValue)", "Tomorrow \(part.rawValue)", false)
            }
            return ("\(dayWord.lowercased()) \(part.rawValue)", "\(dayWord) \(part.rawValue)", false)
        case .now:
            let hour = calendar.component(.hour, from: lastSavedAt)
            switch MomentReading.DayPart.forHour(hour) {
            case .morning: return ("this morning", "This morning", false)
            case .afternoon: return ("this afternoon", "This afternoon", false)
            case .evening, .night: return ("tonight", "Tonight", false)
            }
        }
    }

    /// "Thursday, November 5" — the stamped form for past
    /// chapters (full weekday, full month, day number).
    static func stampedForm(_ date: Date, calendar: Calendar = .current) -> String {
        let formatter = DateFormatter()
        formatter.calendar = calendar
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = calendar.timeZone
        formatter.dateFormat = "EEEE, MMMM d"
        return formatter.string(from: date)
    }
}

// MARK: - Caption system (§5)

/// Everything a caption is filled from — the day record's own
/// numbers, in the user's display unit as whole degrees, plus
/// the canonical Fahrenheit values the why-line thresholds are
/// defined in (swing ≥ 15°, feels-like ≤ 40°).
struct ShareCaptionInput: Equatable {
    let place: String
    let captionMoment: String
    let isPastChapter: Bool
    let feels: Int
    let actual: Int
    let low: Int
    let high: Int
    /// Canonical Fahrenheit swing (high − low) for the ≥ 15 rule.
    let swingF: Double
    /// Canonical Fahrenheit feels-like for the ≤ 40 rule.
    let feelsF: Double
    let conditionLower: String
    /// Headline garments, up to four, in the day's order.
    let kitNames: [String]

    var swingDisplay: Int { high - low }
}

enum ShareCaptionTemplate: String, CaseIterable, Identifiable {
    case feelsLikeStory
    case swing
    case scrapbook
    case shortCall

    var id: String { rawValue }

    var title: String {
        switch self {
        case .feelsLikeStory: return "Feels-Like Story"
        case .swing: return "Swing"
        case .scrapbook: return "Scrapbook"
        case .shortCall: return "Short Call"
        }
    }
}

enum ShareCaptionComposer {
    /// The kit as bullet lines — one garment per line, up to
    /// four headline garments (§5.3: bullets, always).
    static func kitBlock(_ kitNames: [String]) -> String {
        kitNames.prefix(4).map { "• \($0)" }.joined(separator: "\n")
    }

    /// The why-line library (T1's anchor element). Deterministic
    /// selection, identical on both platforms: a big swing wins,
    /// then cold, then the anchor line.
    static func whyLine(for input: ShareCaptionInput) -> String {
        if input.swingF >= 15 {
            return "One outfit for a \(input.swingDisplay)-degree swing."
        }
        if input.feelsF <= 40 {
            return "No guesswork at \(input.feels)°."
        }
        return "Dressed for the feels-like, not the thermometer."
    }

    static func caption(for template: ShareCaptionTemplate, input: ShareCaptionInput) -> String {
        let kit = kitBlock(input.kitNames)
        switch template {
        case .feelsLikeStory:
            return "\(input.place), \(input.captionMoment): \(input.actual)° on the thermometer, \(input.feels)° on the run. RunWear's call:\n\(kit)\n\(whyLine(for: input)) #RunAnyWear · runwear.ai"
        case .swing:
            return "\(input.place) today: \(input.low)° to \(input.high)°. Dressed for \(input.captionMoment)'s \(input.feels)°:\n\(kit)\nOne outfit, no mid-run regret. #RunAnyWear · runwear.ai"
        case .scrapbook:
            return "One for the scrapbook: \(input.place), \(input.captionMoment), \(input.feels)° feels like, \(input.conditionLower). The kit:\n\(kit)\nEvery run, remembered — run, weather or not. #RunAnyWear · @runwear.ai"
        case .shortCall:
            return "\(input.place), \(input.captionMoment): \(input.feels)° feels like, \(input.conditionLower). The call:\n\(kit)\n#RunAnyWear · @runwear.ai"
        }
    }

    /// Picker order (§5.2 + Wave B brief): a past chapter leads
    /// with the Scrapbook; else a swing day (≥ 15°) leads with
    /// the Swing; else the Feels-Like Story leads. The Short
    /// Call is always present, always last. The first template
    /// is the pre-selected caption.
    static func pickerOrder(for input: ShareCaptionInput) -> [ShareCaptionTemplate] {
        let lead: ShareCaptionTemplate
        if input.isPastChapter {
            lead = .scrapbook
        } else if input.swingF >= 15 {
            lead = .swing
        } else {
            lead = .feelsLikeStory
        }
        let rest: [ShareCaptionTemplate] = [.feelsLikeStory, .swing, .scrapbook].filter { $0 != lead }
        return [lead] + rest + [.shortCall]
    }

    static func orderedCaptions(for input: ShareCaptionInput) -> [(template: ShareCaptionTemplate, text: String)] {
        pickerOrder(for: input).map { ($0, caption(for: $0, input: input)) }
    }

    /// The activity sheet's subject (Mail) — §8.
    static func subject(for input: ShareCaptionInput) -> String {
        "RunWear — \(input.place), \(input.feels)° feels like"
    }

    /// The rich link metadata title — §8.
    static func linkTitle(for input: ShareCaptionInput) -> String {
        "RunWear — \(input.place) · \(input.feels)° feels like"
    }
}

// MARK: - Face anchor (§4.3)

/// A photograph's face anchor: the runner's head position in the
/// source image, in top-left-normalised coordinates (y down —
/// the callers convert from Vision's y-up space at detection
/// time). Stored null-safely on the day record; resolved at
/// render time as stored → Vision detection → default.
struct ShareFaceAnchor: Codable, Equatable, Hashable {
    var centerX: Double
    var centerY: Double
    var width: Double
    var height: Double

    /// The default when no face is found: the pool's known
    /// composition point — the upper-middle of the frame, where
    /// full-body running photographs place the runner. NEVER the
    /// geometric centre of the picture.
    static let defaultUpperMiddle = ShareFaceAnchor(
        centerX: 0.5, centerY: 0.16, width: 0.22, height: 0.14
    )

    /// Build an anchor from a detected face rect in
    /// top-left-normalised coordinates.
    static func from(faceRectTopLeftNormalized rect: CGRect) -> ShareFaceAnchor {
        ShareFaceAnchor(
            centerX: Double(rect.midX), centerY: Double(rect.midY),
            width: Double(rect.width), height: Double(rect.height)
        )
    }

    var topNormalized: Double { centerY - height / 2 }
}

enum ShareAnchorCrop {
    /// Where the face should sit in the composed canvas: centre
    /// in the upper third, with at least this much of the canvas
    /// height as headroom above the head.
    static let targetFaceCenterYFraction: CGFloat = 0.24
    static let minimumHeadroomFraction: CGFloat = 0.08

    /// The crop rect (image coordinates) of aspect `canvasAspect`
    /// computed FROM the anchor: the largest window of that
    /// aspect that fits the image, positioned so the face lands
    /// in the upper third with its headroom intact. Widening or
    /// narrowing between canvases moves the window sideways and
    /// downward — extra Stories height goes to the scene below
    /// the runner, never to sky above the head.
    static func cropRect(imageSize: CGSize, anchor: ShareFaceAnchor, canvasAspect: CGFloat) -> CGRect {
        guard imageSize.width > 0, imageSize.height > 0, canvasAspect > 0 else { return .zero }
        var windowWidth = imageSize.width
        var windowHeight = windowWidth / canvasAspect
        if windowHeight > imageSize.height {
            windowHeight = imageSize.height
            windowWidth = windowHeight * canvasAspect
        }
        let faceCenterPx = CGPoint(x: CGFloat(anchor.centerX) * imageSize.width,
                                   y: CGFloat(anchor.centerY) * imageSize.height)
        let faceTopPx = CGFloat(anchor.topNormalized) * imageSize.height
        // Horizontal: centre the window on the face.
        let originX = min(max(faceCenterPx.x - windowWidth / 2, 0), imageSize.width - windowWidth)
        // Vertical: the centre rule and the headroom rule both
        // bound the origin from above; the tighter (smaller)
        // origin wins, then clamp into the image.
        let centerOriginY = faceCenterPx.y - targetFaceCenterYFraction * windowHeight
        let headroomOriginY = faceTopPx - minimumHeadroomFraction * windowHeight
        let originY = min(max(min(centerOriginY, headroomOriginY), 0), imageSize.height - windowHeight)
        return CGRect(x: originX, y: originY, width: windowWidth, height: windowHeight)
    }

    /// The face's placement inside the composed canvas, as
    /// fractions of the canvas height — the render-and-verify
    /// step checks these before rasterising (§4.3.3).
    static func placement(cropRect: CGRect, anchor: ShareFaceAnchor, imageSize: CGSize)
        -> (faceCenterYFraction: CGFloat, headroomFraction: CGFloat) {
        guard cropRect.height > 0, imageSize.height > 0 else { return (0, 0) }
        let faceCenterPx = CGFloat(anchor.centerY) * imageSize.height
        let faceTopPx = CGFloat(anchor.topNormalized) * imageSize.height
        return (
            faceCenterYFraction: (faceCenterPx - cropRect.origin.y) / cropRect.height,
            headroomFraction: (faceTopPx - cropRect.origin.y) / cropRect.height
        )
    }

    /// True iff the placement honours the rule: face in the
    /// upper third, headroom at least the minimum.
    static func placementIsValid(cropRect: CGRect, anchor: ShareFaceAnchor, imageSize: CGSize) -> Bool {
        let placed = placement(cropRect: cropRect, anchor: anchor, imageSize: imageSize)
        return placed.faceCenterYFraction <= (1.0 / 3.0) + 0.001
            && placed.headroomFraction >= minimumHeadroomFraction - 0.001
    }
}

// MARK: - Poster composition (§4.2)

enum SharePosterModel {
    /// Tier 2 — "{place} · {Moment}." (sentence moment form).
    static func tier2Line(place: String, posterMoment: String) -> String {
        "\(place) · \(posterMoment)."
    }

    /// Tier 3 — the outfit in words: headline garments (up to
    /// four) joined by middots. Promoted: the clear second read
    /// after the numeral.
    static func tier3Line(kitNames: [String]) -> String {
        kitNames.prefix(4).joined(separator: " · ")
    }

    /// Tier 4 — the conditions micro-line.
    static func tier4Line(condition: String, actualDegrees: Int, windMph: Int) -> String {
        "\(condition) · Actual \(actualDegrees)° · Wind \(windMph) mph"
    }

    /// The yield rule: under pressure the micro-line yields —
    /// never the outfit line. The Square canvas with a long
    /// outfit line is the pressure case; Feed and Stories always
    /// have room.
    static func showsMicroLine(format: ShareExportFormat, tier3Text: String) -> Bool {
        !(format == .square && tier3Text.count > 64)
    }

    /// Stories safe zones (§6): the lockup begins below the top
    /// 250 px at export scale; the block (with the footer folded
    /// in as its last line) ends above the bottom 340 px.
    static func lockupTopInset(format: ShareExportFormat) -> CGFloat {
        format == .stories ? (250 / format.renderScale) : 14
    }

    static func blockBottomInset(format: ShareExportFormat) -> CGFloat {
        format == .stories ? (340 / format.renderScale) : 16
    }

    /// On Stories the footer folds into the informative block as
    /// its last line; on Feed and Square it is its own row.
    static func footerFoldsIntoBlock(format: ShareExportFormat) -> Bool {
        format == .stories
    }
}

// MARK: - Kit Card composition (§4.5)

enum ShareKitCardModel {
    /// The day's garments in WEARING ORDER — the existing outfit
    /// ordering the entry already stores (OutfitRecommendation
    /// .allItems order, recorded verbatim into itemNames /
    /// itemKeys). The grid preserves it; nothing re-sorts.
    static func orderedItems(names: [String], keys: [String]) -> [(name: String, key: String)] {
        Array(zip(names, keys))
    }

    /// Notes ride on Feed and Square; on Stories they yield
    /// first under height pressure — a tall kit (more than four
    /// garments) drops its notes there, names stand alone.
    static func showsNotes(format: ShareExportFormat, itemCount: Int) -> Bool {
        switch format {
        case .feed, .square: return true
        case .stories: return itemCount <= 4
        }
    }

    /// The header weather line: "{feels}° feels like ·
    /// {condition} · {place} · {Moment}."
    static func weatherLine(feelsDegrees: Int, condition: String, place: String, posterMoment: String) -> String {
        "\(feelsDegrees)° feels like · \(condition) · \(place) · \(posterMoment)."
    }

    static let disclosure = "As an Amazon Associate, RunWear earns from qualifying purchases."

    /// Canvas-fraction centres of the grid's tiles, for the
    /// raster verifier's per-tile samples. The grid geometry
    /// mirrors KitCardView's layout: 20 pt side padding, two
    /// columns, tiles starting below the header block.
    static func tileSamplePoints(format: ShareExportFormat, itemCount: Int) -> [CGPoint] {
        guard itemCount > 0 else { return [] }
        let canvas = format.canvasPointSize
        let headerHeight: CGFloat = format == .stories ? 150 : 118
        let tileSide: CGFloat = (canvas.width - 40 - 12) / 2
        let rowPitch: CGFloat = tileSide + 52
        return (0..<itemCount).map { index in
            let column = index % 2
            let row = index / 2
            let x = 20 + tileSide / 2 + CGFloat(column) * (tileSide + 12)
            let y = headerHeight + tileSide / 2 + CGFloat(row) * rowPitch
            return CGPoint(x: x / canvas.width, y: y / canvas.height)
        }
    }
}

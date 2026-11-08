import SwiftUI
import UIKit

// MARK: - RW-144 first-screen hero (design of record: v8 frames)
//
// The approved first-screen redesign (Sol, Nov 8, 2026;
// release-prep/rw-first-screen-mock-2026-11-08, frames v8 +
// notes through v8). The hero is ≈45% of the screen and carries,
// over the full-bleed photograph: the top chrome row (place pill,
// RunWear wordmark, settings gear in a circle), the ONE-pill
// two-target date/time chip, and the feels-like block on a dark
// scrim zone at the hero's foot. The Make It Me pill straddles
// the hero/sheet boundary (its seat lives in WeatherOutfitView's
// sheet container); the sheet opens with the boxless weather
// line (FirstScreenSheet.swift).
//
// Palette discipline (notes-v8 audit table): chrome fill is the
// warm black (24,20,16); every chrome stroke and glyph is the
// cream #F5F1EA — never #FFFFFF. The scrim ink is (14,11,8); the
// top scrim ink is (26,20,14).

// MARK: Band palette (PWA brackets, notes-v6)

/// The dynamic band colour of the hero feels-like block. The
/// palette attaches to the app's own bracket names; the °F
/// brackets are the PWA's (freezing <20, cold 20–34, cool 35–49,
/// mild 50–64, warm 65–79, hot ≥80) — the same thresholds
/// `HeroTempBracket.from(feelsLikeTemperature:)` already encodes,
/// and the colours are AppTheme's pinned temperature hexes
/// (freezing #8B5CF6, cold #6366F1, cool #3B82F6, mild #10B981,
/// warm #F59E0B, hot #F97316).
enum HeroBandPalette {
    static func bracket(feelsLikeF: Double) -> HeroTempBracket {
        HeroTempBracket.from(feelsLikeTemperature: feelsLikeF)
    }

    static func color(feelsLikeF: Double) -> Color {
        AppTheme.temperatureColor(for: bracket(feelsLikeF: feelsLikeF))
    }

    /// The tracked band word beside the numeral ("MILD").
    static func name(feelsLikeF: Double) -> String {
        bracket(feelsLikeF: feelsLikeF).rawValue
    }
}

// MARK: Luminance-driven scrim (the contrast guarantee, notes-v6)

/// The mock's scrim peak (α ≈ 0.73) is a FLOOR, not a constant:
/// in production the scrim strength behind the feels-like block
/// and the chrome zones is driven by the MEASURED LUMINANCE of
/// the actual photo region beneath — scaling up for bright
/// photographs (snow, beach, noon sky) and relaxing toward the
/// floor for dark ones — so every band colour and cream element
/// holds contrast over arbitrary personal photos.
enum HeroScrimMath {
    /// The v8 mock's peak scrim alpha — the floor.
    static let floorPeakAlpha: Double = 186.0 / 255.0
    /// The strongest peak the guarantee will choose.
    static let maxPeakAlpha: Double = 0.92
    /// Luminance at/below which the floor suffices.
    static let floorLuminance: Double = 0.35
    /// Luminance at/above which the peak saturates.
    static let saturatedLuminance: Double = 0.85

    /// Peak scrim alpha for a measured region luminance (0…1).
    static func peakAlpha(forLuminance luminance: Double) -> Double {
        let l = min(1, max(0, luminance))
        guard l > floorLuminance else { return floorPeakAlpha }
        let t = (l - floorLuminance) / (saturatedLuminance - floorLuminance)
        return floorPeakAlpha + min(1, t) * (maxPeakAlpha - floorPeakAlpha)
    }

    /// How much stronger than the floor the chosen peak is —
    /// the multiplier the chrome fills (place pill, chip, gear
    /// circle) apply to their mock alphas, capped so chrome
    /// never goes opaque.
    static func chromeBoost(forLuminance luminance: Double) -> Double {
        peakAlpha(forLuminance: luminance) / floorPeakAlpha
    }

    static func chromeAlpha(base: Double, luminance: Double) -> Double {
        min(0.9, base * chromeBoost(forLuminance: luminance))
    }

    /// The feels-like scrim zone's alpha profile (notes-v6):
    /// α0 at the zone's top edge, rising to the peak by 28% of
    /// the zone height, holding, then easing to 65% of peak at
    /// the bottom edge. `fraction` is 0 at the zone top, 1 at
    /// its bottom. The zone's edges are additionally feathered
    /// by the gradient stops the view builds from this profile.
    static func scrimAlpha(peak: Double, fraction: Double) -> Double {
        let f = min(1, max(0, fraction))
        if f < 0.28 {
            // Smooth rise 0 → peak across the first 28%.
            let t = f / 0.28
            return peak * (t * t * (3 - 2 * t))
        }
        if f > 0.72 {
            // Ease peak → 65% of peak across the last 28%.
            let t = (f - 0.72) / 0.28
            let s = t * t * (3 - 2 * t)
            return peak * (1 - 0.35 * s)
        }
        return peak
    }

    /// Gradient stops sampling `scrimAlpha` for the zone view.
    static func scrimStops(peak: Double, ink: Color) -> [Gradient.Stop] {
        [0.0, 0.14, 0.28, 0.5, 0.72, 0.86, 1.0].map { f in
            .init(color: ink.opacity(scrimAlpha(peak: peak, fraction: f)), location: f)
        }
    }
}

/// Measures the mean luminance of a region of the served hero
/// image: the image is downsampled into a small bitmap and the
/// region's pixels are averaged (relative luminance,
/// 0.2126R + 0.7152G + 0.0722B, on sRGB values). This is the
/// render-time measurement the scrim guarantee runs on — not a
/// fixed constant.
enum HeroLuminanceSampler {
    /// The hero's three measured zones, as fractions of the
    /// hero frame: the top chrome band, the chip band, and the
    /// feels-like foot zone (the scrim patch spans the foot's
    /// left ~73% in the frames; the sampler measures that part).
    static let topRegion = CGRect(x: 0, y: 0, width: 1, height: 0.18)
    static let chipRegion = CGRect(x: 0, y: 0.36, width: 0.62, height: 0.18)
    static let footRegion = CGRect(x: 0, y: 0.58, width: 0.73, height: 0.42)

    static func meanLuminance(of image: UIImage, region: CGRect) -> Double {
        guard let cg = image.cgImage, cg.width > 0, cg.height > 0 else { return 0.5 }
        let side = 32
        var bytes = [UInt8](repeating: 0, count: side * side * 4)
        guard let ctx = CGContext(
            data: &bytes, width: side, height: side, bitsPerComponent: 8,
            bytesPerRow: side * 4, space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return 0.5 }
        ctx.interpolationQuality = .low
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: side, height: side))
        // Bitmap rows run bottom-up relative to the image; the
        // regions are symmetric enough vertically (bands span
        // the full width of their zone) that row order does not
        // change the mean materially — x/y are taken as drawn.
        let x0 = Int((region.minX * Double(side)).rounded())
        let x1 = max(x0 + 1, Int((region.maxX * Double(side)).rounded()))
        let y0 = Int((region.minY * Double(side)).rounded())
        let y1 = max(y0 + 1, Int((region.maxY * Double(side)).rounded()))
        var sum = 0.0
        var count = 0.0
        for y in y0..<min(side, y1) {
            for x in x0..<min(side, x1) {
                let i = (y * side + x) * 4
                let r = Double(bytes[i]) / 255
                let g = Double(bytes[i + 1]) / 255
                let b = Double(bytes[i + 2]) / 255
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                count += 1
            }
        }
        return count > 0 ? sum / count : 0.5
    }
}

/// Loads the served hero image (bundled file URL or remote
/// signed URL) and publishes the three zone luminances. Loads
/// are keyed to the photo's IDENTITY (the RW-106 rule: the URL
/// with its signature stripped), so a re-signed URL for the
/// same photo never re-measures.
@MainActor
final class HeroLuminanceModel: ObservableObject {
    @Published private(set) var topLuminance: Double = 0.5
    @Published private(set) var chipLuminance: Double = 0.5
    @Published private(set) var footLuminance: Double = 0.5

    private var measuredIdentity: String?
    private var loadTask: Task<Void, Never>?

    func update(imageURLString: String?) {
        guard let imageURLString, let url = URL(string: imageURLString) else { return }
        let identity = HeroRemoteImageLoader.identity(for: imageURLString)
        guard identity != measuredIdentity else { return }
        measuredIdentity = identity
        loadTask?.cancel()
        loadTask = Task { [weak self] in
            let data: Data?
            if url.isFileURL {
                data = try? Data(contentsOf: url)
            } else {
                data = try? await URLSession.shared.data(from: url).0
            }
            guard let data, let image = UIImage(data: data), !Task.isCancelled else { return }
            let top = HeroLuminanceSampler.meanLuminance(of: image, region: HeroLuminanceSampler.topRegion)
            let chip = HeroLuminanceSampler.meanLuminance(of: image, region: HeroLuminanceSampler.chipRegion)
            let foot = HeroLuminanceSampler.meanLuminance(of: image, region: HeroLuminanceSampler.footRegion)
            self?.topLuminance = top
            self?.chipLuminance = chip
            self?.footLuminance = foot
        }
    }
}

// MARK: Chip placement (face-zone protection)

/// The chip zone carries the same subject protection the
/// wordmark zone has: the hero crop is top-anchored (HeroPhoto),
/// so a full-body portrait's head sits in the frame's top-center
/// band. The chip's seat is BELOW that band by construction, and
/// this placement function is the guarantee: if a proposed chip
/// rect would intersect the face zone, the chip moves below the
/// zone (clamped so it still clears the feels-like block).
enum HeroChipPlacement {
    /// The protected face zone, as fractions of the hero frame:
    /// top-center, where the top-anchored crop keeps the head.
    static func faceZone(frameSize: CGSize) -> CGRect {
        CGRect(
            x: frameSize.width * 0.32,
            y: 0,
            width: frameSize.width * 0.36,
            height: frameSize.height * 0.30
        )
    }

    /// The chip's default top edge (fraction of hero height) —
    /// the v8 seat (chip top ≈ 530/1150 of the hero in the
    /// frames ≈ 0.46).
    static let defaultTopFraction: CGFloat = 0.46

    /// The chip's top edge for a hero of `frameSize`, shifted
    /// clear of the face zone if the default seat would cross it.
    static func chipTop(frameSize: CGSize, chipHeight: CGFloat) -> CGFloat {
        let proposed = CGRect(
            x: 16, y: frameSize.height * defaultTopFraction,
            width: frameSize.width * 0.62, height: chipHeight
        )
        let zone = faceZone(frameSize: frameSize)
        guard proposed.intersects(zone) else { return proposed.minY }
        // Move below the face zone, but never into the
        // feels-like block (which opens at ≈0.58 of the hero).
        let belowZone = zone.maxY + 8
        let latestTop = frameSize.height * 0.58 - chipHeight - 4
        return min(belowZone, max(proposed.minY, latestTop))
    }
}

// MARK: Chrome tokens

/// The hero chrome's shared tokens (notes-v8): warm-black fills,
/// cream strokes/glyphs, mock alphas carried as fractions.
enum HeroChrome {
    static let fillInk = Color(hex: "181410") // (24,20,16)
    static let cream = Color(hex: "F5F1EA")
    static let placeFillAlpha: Double = 84.0 / 255.0
    static let chipFillAlpha: Double = 72.0 / 255.0
    static let placeOutlineAlpha: Double = 30.0 / 255.0
    static let chipOutlineAlpha: Double = 26.0 / 255.0
    static let dividerAlpha: Double = 96.0 / 255.0
    static let topScrimInk = Color(hex: "1A140E") // (26,20,14)
    static let footScrimInk = Color(hex: "0E0B08") // (14,11,8)
}

// MARK: - Top chrome row

/// Place pill (top-left), RunWear wordmark (centered), settings
/// gear in a circle (top-right) — the v8 hero's top row. The
/// place pill is the location entry (one tap straight to the
/// picker, the Y-16 rule the nav-bar principal item carried);
/// the gear opens Settings.
struct HeroTopChromeView: View {
    let placeName: String
    let luminance: Double
    let onPlaceTapped: () -> Void
    let onSettingsTapped: () -> Void

    var body: some View {
        ZStack {
            // Wordmark, centered on the hero (not on the row's
            // remaining space — the frames center it on screen).
            Text("RunWear")
                .font(.greatVibes(size: 25))
                .lineLimit(1)
                .foregroundColor(HeroChrome.cream)
                .shadow(color: Color.black.opacity(0.35), radius: 6, y: 2)
                .accessibilityHidden(true)

            HStack(alignment: .top, spacing: 8) {
                Button(action: onPlaceTapped) {
                    HStack(spacing: 6) {
                        Image(systemName: "mappin")
                            .font(.system(size: 13, weight: .semibold))
                        Text(placeName)
                            .font(.system(size: 13, weight: .semibold))
                            .lineLimit(1)
                            .truncationMode(.tail)
                            // The pill must never reach the
                            // centered wordmark: long place names
                            // truncate inside the pill (the mock's
                            // pill is ~118 pt wide).
                            .frame(maxWidth: 110, alignment: .leading)
                    }
                    .foregroundColor(HeroChrome.cream)
                    .padding(.horizontal, 12)
                    .frame(height: 32)
                    .background(
                        Capsule().fill(HeroChrome.fillInk.opacity(
                            HeroScrimMath.chromeAlpha(base: HeroChrome.placeFillAlpha, luminance: luminance)))
                    )
                    .overlay(
                        Capsule().stroke(HeroChrome.cream.opacity(HeroChrome.placeOutlineAlpha), lineWidth: 1)
                    )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Location: \(placeName). Change location")

                Spacer(minLength: 0)

                Button(action: onSettingsTapped) {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(HeroChrome.cream)
                        .frame(width: 32, height: 32)
                        .background(
                            Circle().fill(HeroChrome.fillInk.opacity(
                                HeroScrimMath.chromeAlpha(base: HeroChrome.placeFillAlpha, luminance: luminance)))
                        )
                        .overlay(
                            Circle().stroke(HeroChrome.cream.opacity(HeroChrome.placeOutlineAlpha), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }
        }
    }
}

// MARK: - Date/time chip (ONE pill, TWO targets)

/// The v8 chip geometry, translated from the mock's proportions
/// (1179 px-wide frames = 393 pt at 3×, so mock-pt ÷ 3): height
/// ≈76 mock-pt ≈ 25 pt (rendered 28 for text legibility, with a
/// 44 pt hit area per segment), and — the v7 rule — ≈48 mock-pt
/// (16 pt) of clear space on EACH side of the divider, so the
/// two targets read as two inside one pill.
enum HeroChipGeometry {
    static let height: CGFloat = 28
    static let hitHeight: CGFloat = 44
    static let dividerClearSpace: CGFloat = 16
    static let dividerVerticalInset: CGFloat = 6 // 17 mock-pt ÷ 3 ≈ 5.7
    static let leftPadding: CGFloat = 10
    static let rightPadding: CGFloat = 9
}

/// One translucent pill, two targets (notes-v5/v7): the date
/// segment (clock glyph + date + chevron) opens the DATE
/// PICKER; the time segment opens the HOURLY CHART (which itself
/// links to the same date picker). In the Now state the time
/// segment reads "Now". In a picked state the pill carries
/// picked date | divider | picked time | "↻ Now" — the return
/// segment exists ONLY while the selection differs from Now.
struct HeroDateTimeChipView: View {
    let dateText: String
    /// "Now" in the Now state; the picked time otherwise.
    let timeText: String
    let isNow: Bool
    let luminance: Double
    let onDateTapped: () -> Void
    let onTimeTapped: () -> Void
    let onNowTapped: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onDateTapped) {
                HStack(spacing: 5) {
                    Image(systemName: "clock")
                        .font(.system(size: 12, weight: .semibold))
                    Text(dateText)
                        .font(.system(size: 13, weight: .medium))
                        .lineLimit(1)
                    Text("›")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(HeroChrome.cream)
                .padding(.leading, HeroChipGeometry.leftPadding)
                .frame(minHeight: HeroChipGeometry.hitHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Choose a date: \(dateText)")

            // Divider with the v7 clear space on each side.
            HStack(spacing: 0) {
                Spacer().frame(width: HeroChipGeometry.dividerClearSpace)
                Rectangle()
                    .fill(HeroChrome.cream.opacity(HeroChrome.dividerAlpha))
                    .frame(width: 1, height: HeroChipGeometry.height - HeroChipGeometry.dividerVerticalInset * 2)
                Spacer().frame(width: HeroChipGeometry.dividerClearSpace)
            }

            Button(action: onTimeTapped) {
                HStack(spacing: 5) {
                    Text(timeText)
                        .font(.system(size: 13, weight: .medium))
                        .lineLimit(1)
                    Text("›")
                        .font(.system(size: 13, weight: .semibold))
                }
                .foregroundColor(HeroChrome.cream)
                .frame(minHeight: HeroChipGeometry.hitHeight)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(isNow ? "Choose a time: now" : "Choose a time: \(timeText)")

            if !isNow {
                Button(action: onNowTapped) {
                    HStack(spacing: 3) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.system(size: 11, weight: .bold))
                        Text("Now")
                            .font(.system(size: 13, weight: .semibold))
                            .lineLimit(1)
                    }
                    .foregroundColor(HeroChrome.cream)
                    .padding(.leading, 10)
                    .frame(minHeight: HeroChipGeometry.hitHeight)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to now")
            }

            Spacer().frame(width: HeroChipGeometry.rightPadding)
        }
        .frame(height: HeroChipGeometry.height)
        .background(
            Capsule().fill(HeroChrome.fillInk.opacity(
                HeroScrimMath.chromeAlpha(base: HeroChrome.chipFillAlpha, luminance: luminance)))
        )
        .overlay(
            Capsule().stroke(HeroChrome.cream.opacity(HeroChrome.chipOutlineAlpha), lineWidth: 1)
        )
        // The visual pill is 28 pt; the hit area is the full
        // 44 pt band the segments carry.
        .frame(height: HeroChipGeometry.hitHeight)
    }
}

// MARK: - Feels-like block

/// The feels-like block on its dark scrim zone at the hero's
/// foot (notes-v6): "FEELS LIKE" label, the giant numeral in the
/// DYNAMIC BAND COLOUR with the PWA's soft shadow, the band dot
/// + tracked band word beside it, and the "Actual NN°" line
/// (value bold) — label and Actual in cream over the scrim.
struct HeroFeelsLikeBlockView: View {
    let formattedFeelsLike: String
    let formattedActual: String
    let feelsLikeF: Double

    @ScaledMetric(relativeTo: .largeTitle) private var numeralSize: CGFloat = 72

    private var bandColor: Color { HeroBandPalette.color(feelsLikeF: feelsLikeF) }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("FEELS LIKE")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2.2)
                .foregroundColor(HeroChrome.cream)

            HStack(alignment: .bottom, spacing: 10) {
                Text(formattedFeelsLike)
                    .font(.system(size: numeralSize, weight: .semibold))
                    .monospacedDigit()
                    .foregroundColor(bandColor)
                    // The PWA's soft shadow: CSS 0 4px 20px
                    // rgba(0,0,0,.3) ≈ (0, +5, blur 9, α80/255).
                    .shadow(color: Color.black.opacity(80.0 / 255.0), radius: 9, y: 5)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)

                HStack(spacing: 5) {
                    Circle()
                        .fill(bandColor)
                        .frame(width: 7, height: 7)
                    Text(HeroBandPalette.name(feelsLikeF: feelsLikeF))
                        .font(.system(size: 12, weight: .semibold))
                        .tracking(1.4)
                        .foregroundColor(bandColor)
                }
                // The band word sits ON the numeral's baseline
                // (the frames' seat): bottom-aligned to the
                // numeral's view box, lifted by the system
                // font's descender depth so the baselines meet.
                .padding(.bottom, 13)
            }

            HStack(spacing: 0) {
                Text("Actual ")
                    .font(.system(size: 15))
                Text(formattedActual)
                    .font(.system(size: 15, weight: .bold))
            }
            .foregroundColor(HeroChrome.cream)
        }
        .dynamicTypeSize(...DynamicTypeSize.accessibility1)
    }
}

// MARK: - The composed hero chrome overlay

/// Everything the v8 hero carries over the photograph, in the
/// frames' z-order: band-tinted wash (under scrims/chrome) →
/// top scrim → feels-like scrim zone → chrome (top row, chip,
/// feels-like block). The photograph and its tap-to-viewer
/// gesture stay in HeroImageView/WeatherOutfitView; this
/// overlay's non-control areas are hit-transparent so the hero
/// tap still lands.
struct HeroChromeOverlayView: View {
    let placeName: String
    let dateText: String
    let timeText: String
    let isNow: Bool
    let formattedFeelsLike: String
    let formattedActual: String
    let feelsLikeF: Double
    let topLuminance: Double
    let chipLuminance: Double
    let footLuminance: Double
    let onPlaceTapped: () -> Void
    let onSettingsTapped: () -> Void
    let onDateTapped: () -> Void
    let onTimeTapped: () -> Void
    let onNowTapped: () -> Void

    var body: some View {
        GeometryReader { geo in
            let size = geo.size
            let band = HeroBandPalette.color(feelsLikeF: feelsLikeF)
            let footPeak = HeroScrimMath.peakAlpha(forLuminance: footLuminance)
            ZStack(alignment: .topLeading) {
                // Band-tinted wash over the photo (notes-v6):
                // band colour α0.15 top → 0.05 mid → 0 at the
                // hero's foot, under the scrims and chrome.
                LinearGradient(
                    stops: [
                        .init(color: band.opacity(0.15), location: 0),
                        .init(color: band.opacity(0.05), location: 0.5),
                        .init(color: band.opacity(0), location: 1.0)
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .allowsHitTesting(false)

                // Top scrim (26,20,14), boosted by the measured
                // luminance of the top zone.
                LinearGradient(
                    stops: [
                        .init(color: HeroChrome.topScrimInk.opacity(
                            min(0.92, 0.62 * HeroScrimMath.chromeBoost(forLuminance: topLuminance))), location: 0),
                        .init(color: HeroChrome.topScrimInk.opacity(
                            min(0.6, 0.31 * HeroScrimMath.chromeBoost(forLuminance: topLuminance))), location: 0.175),
                        .init(color: .clear, location: 0.35)
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .allowsHitTesting(false)

                // Feels-like scrim zone at the hero's foot
                // (notes-v6 profile; peak is the luminance-driven
                // value, the mock's 0.73 its floor).
                VStack(spacing: 0) {
                    Spacer()
                    HStack(spacing: 0) {
                        LinearGradient(
                            stops: HeroScrimMath.scrimStops(peak: footPeak, ink: HeroChrome.footScrimInk),
                            startPoint: .top, endPoint: .bottom
                        )
                        .frame(width: size.width * 0.85, height: size.height * 0.46)
                        Spacer(minLength: 0)
                    }
                    .blur(radius: 13) // the feathered edge (26 mock-pt Gaussian ≈ 13 pt)
                }
                .allowsHitTesting(false)

                // Top chrome row.
                HeroTopChromeView(
                    placeName: placeName,
                    luminance: topLuminance,
                    onPlaceTapped: onPlaceTapped,
                    onSettingsTapped: onSettingsTapped
                )
                .padding(.horizontal, 16)
                .padding(.top, 10)

                // Date/time chip — its seat is face-zone
                // protected (HeroChipPlacement).
                HeroDateTimeChipView(
                    dateText: dateText,
                    timeText: timeText,
                    isNow: isNow,
                    luminance: chipLuminance,
                    onDateTapped: onDateTapped,
                    onTimeTapped: onTimeTapped,
                    onNowTapped: onNowTapped
                )
                .padding(.leading, 16)
                .offset(y: HeroChipPlacement.chipTop(frameSize: size, chipHeight: HeroChipGeometry.hitHeight))

                // Feels-like block, seated in the scrim zone at
                // the hero's foot. Its bottom edge clears the
                // straddle pill by construction: the pill is
                // centred on the sheet boundary (the hero's foot
                // minus the 28 pt sheet overlap) and stands
                // 44 pt tall, so its top edge sits 50 pt above
                // the hero's foot — the block's last line
                // (Actual) ends 34 pt above the foot (the mock's
                // own seat is ≈25 pt), a 16 pt clearance that
                // holds whatever the pill's width.
                VStack(spacing: 0) {
                    Spacer()
                    HeroFeelsLikeBlockView(
                        formattedFeelsLike: formattedFeelsLike,
                        formattedActual: formattedActual,
                        feelsLikeF: feelsLikeF
                    )
                    .padding(.leading, 16)
                    .padding(.bottom, 34)
                }
                .frame(width: size.width, height: size.height, alignment: .bottomLeading)
                .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height)
        }
    }
}

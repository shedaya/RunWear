import SwiftUI
import UIKit
import XCTest

/// Field-findings fixtures (Sol's screenshots, 2026-10-31, build (7),
/// New York, 5:24 AM, hour set to 5:00 PM). Five defects, one screen:
///
/// 1. The hero printed "FEELS LIKE 12° / Actual: 16°" with NO unit
///    while the band below read "Cool Weather Running 40-49°F" —
///    the hero numbers were the hour's Celsius values (12°C =
///    53.6°F) under a hard-coded °F band. Fix: hero and Actual carry
///    the unit symbol; the badge range renders in the display unit.
/// 2. Over that 40-49°F outfit (Light Long Sleeve + Windbreaker +
///    Light Tights), the hero photo was a runner in a T-SHIRT AND
///    SHORTS: the photo was keyed on the RAW feels-like (53.6°F →
///    MILD cell) while the outfit and band were keyed on the
///    comfort-ADJUSTED frame (43.6°F → COOL). Fix: the photo is
///    keyed on the recommendation frame, as the watch already did.
/// 3. The glass toolbar capsule overlapped the location row and
///    the weather pills ghosted through it — the navigation bar
///    had no background over a safe-area-ignoring hero. Fix: an
///    opaque toolbar background (source-level; see
///    WeatherOutfitView — the visual half is the device walk).
/// 4. Outfit rows carried raw enum identifiers as subtitles
///    ("LONG_SLEEVE_LIGHT"): across the Kotlin/Native seam,
///    `description` resolves to NSObject.description (the enum case
///    name), so the human string never arrived. Fix: the engine
///    exposes descriptionText and the bridge reads it.
/// 5. The hero rendered near-square with the subject's head
///    cropped: heroHeightRatio was applied TWICE (0.75 × 0.75 of
///    the container) and the photo was center-cropped. Fix: one
///    ratio application (Android's 0.75), and HeroPhoto's
///    top-anchored fill — heads are never the crop's cost.
///
/// Every test here drives the production construction (the view
/// model's own assembly, the real engine bridge, the real crop
/// view rendered to pixels) — no reimplementations.
final class FieldFindingsFixTests: XCTestCase {

    // MARK: - Fixture plumbing (mirrors EngineBoundaryTests' idiom)

    private struct HourSpec {
        var time: String
        var tempC: Double
        var feelsC: Double
        var humidity: Int = 50
        var precipProb: Int = 0
        var precip: Double = 0
        var rain: Double = 0
        var uv: Double = 0
        var windMph: Double = 0
        var code: Int = 0
    }

    private func makeHourly(_ specs: [HourSpec]) -> HourlyWeather {
        HourlyWeather(
            time: specs.map(\.time),
            temperature2m: specs.map(\.tempC),
            apparentTemperature: specs.map(\.feelsC),
            relativeHumidity2m: specs.map(\.humidity),
            precipitationProbability: specs.map(\.precipProb),
            precipitation: specs.map(\.precip),
            rain: specs.map(\.rain),
            uvIndex: specs.map(\.uv),
            windSpeed10m: specs.map(\.windMph),
            windDirection10m: specs.map { _ in 0 },
            windGusts10m: specs.map(\.windMph),
            weatherCode: specs.map(\.code)
        )
    }

    @MainActor
    private func makeViewModel(hourly: HourlyWeather?) -> WeatherViewModel {
        let vm = WeatherViewModel()
        vm.weatherData = WeatherData(
            latitude: 40.7128,
            longitude: -74.0060,
            currentWeather: CurrentWeather(temperature: 10, windspeed: 8, weathercode: 0, isDay: 1),
            hourly: hourly,
            timezone: "America/New_York",
            currentDetail: nil
        )
        return vm
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day))!
    }

    // MARK: - Finding 1: one unit story

    func testHeroNumbersCarryTheirUnit() {
        // The photographed incoherence in miniature: 12.0 in the
        // user's Celsius must render "12°C", never a bare "12°"
        // beside a "40-49°F" band.
        XCTAssertEqual(TemperatureUnit.celsius.format(12.0), "12°C")
        XCTAssertEqual(TemperatureUnit.celsius.format(16.0), "16°C")
        XCTAssertEqual(TemperatureUnit.fahrenheit.format(53.6), "54°F")
    }

    func testBadgeRangeRendersInTheDisplayUnit() {
        // Fahrenheit is the historical string, byte-for-byte.
        for bracket in TemperatureBracket.allCases {
            XCTAssertEqual(bracket.rangeText(for: .fahrenheit), bracket.rawValue)
        }
        // Celsius converts the same bounds — the band the photographed
        // screen showed as "40-49°F" reads "4-9°C" for the user whose
        // hero says 12°C.
        XCTAssertEqual(TemperatureBracket.cool.rangeText(for: .celsius), "4-9°C")
        XCTAssertEqual(TemperatureBracket.mild.rangeText(for: .celsius), "10-18°C")
        XCTAssertEqual(TemperatureBracket.warm.rangeText(for: .celsius), "18-26°C")
        XCTAssertEqual(TemperatureBracket.hot.rangeText(for: .celsius), "27°C+")
        XCTAssertEqual(TemperatureBracket.cold.rangeText(for: .celsius), "-1-4°C")
        XCTAssertEqual(TemperatureBracket.veryCold.rangeText(for: .celsius), "-7--2°C")
        XCTAssertEqual(TemperatureBracket.frigid.rangeText(for: .celsius), "-12--7°C")
        XCTAssertEqual(TemperatureBracket.extreme.rangeText(for: .celsius), "<-12°C")
        for bracket in TemperatureBracket.allCases {
            XCTAssertFalse(bracket.rangeText(for: .celsius).contains("°F"))
        }
    }

    // MARK: - Finding 2: the photo depicts the recommended outfit

    @MainActor
    func testHeroPhotoIsKeyedOnTheRecommendationFrame() throws {
        // The photographed state: feels-like 12°C (53.6°F raw) at
        // 5 PM with 15 mph wind, and a runs-very-cold comfort
        // profile (-10°F) — the outfit is recommended in the
        // adjusted 43.6°F frame.
        let vm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-31T17:00", tempC: 16, feelsC: 12, windMph: 15)
        ]))
        vm.comfortLevel = .veryCold
        defer { vm.comfortLevel = .neutral }
        vm.selectedDate = date(2026, 10, 31)
        vm.selectedHour = 17
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()

        let rec = try XCTUnwrap(vm.recommendation)
        XCTAssertEqual(rec.temperatureBracket, .cool)
        XCTAssertEqual(rec.topBase.key, "LONG_SLEEVE_LIGHT")
        XCTAssertEqual(rec.topOuter?.key, "WINDBREAKER")
        XCTAssertEqual(rec.bottom.key, "LIGHT_TIGHTS")

        // The hero frame is the SAME adjusted frame — 43.6°F, the
        // COOL hero cell (the cool-weather outfit's own photo).
        let frame = try XCTUnwrap(vm.prepareHeroFrame())
        XCTAssertEqual(frame.feelsLikeF, 43.6, accuracy: 0.01)
        XCTAssertEqual(HeroTempBracket.from(feelsLikeTemperature: frame.feelsLikeF), .COOL)

        // For the record, the pre-fix keying: the raw frame is a
        // different cell entirely — MILD, the t-shirt-and-shorts
        // photo the field screenshot showed.
        XCTAssertEqual(HeroTempBracket.from(feelsLikeTemperature: 53.6), .MILD)
    }

    // MARK: - Finding 4: human subtitles, not enum identifiers

    func testEngineItemDescriptionsAreHumanCopy() {
        let rec = OutfitRecommendationService.shared.getRecommendation(
            for: 43.6,
            rawTemperature: 53.6,
            isRaining: false,
            windSpeed: 15,
            humidity: 50,
            uvIndex: 0,
            precipitation: 0,
            hour: 17
        )
        XCTAssertEqual(rec.topBase.key, "LONG_SLEEVE_LIGHT")
        XCTAssertEqual(rec.topBase.description, "Thin, breathable long sleeve")
        XCTAssertEqual(rec.topOuter?.key, "WINDBREAKER")
        XCTAssertEqual(rec.topOuter?.description, "Lightweight wind and light rain protection")
        XCTAssertEqual(rec.bottom.key, "LIGHT_TIGHTS")
        XCTAssertEqual(rec.bottom.description, "Full leg coverage, breathable")
        // The general contract, every item the engine emits: the
        // subtitle is copy, never the identifier.
        for item in rec.allItems {
            XCTAssertNotEqual(item.description, item.key, "\(item.key) subtitle is its own identifier")
            XCTAssertFalse(item.description.contains("_"), "\(item.key) subtitle is not human copy: \(item.description)")
        }
    }

    // MARK: - Finding 5: full frame, head intact

    func testHeroCropContractStartsAtTheHeadAndCoversTheFrame() {
        // Pool portrait (800×1200) in the phone hero after the
        // single-ratio fix (393×585): nearly the whole portrait
        // shows, starting at its top edge.
        let phone = HeroPhoto.visibleRect(
            imageSize: CGSize(width: 800, height: 1200),
            frameSize: CGSize(width: 393, height: 585)
        )
        XCTAssertEqual(phone.minY, 0)
        XCTAssertEqual(phone.minX, 0, accuracy: 1)
        XCTAssertGreaterThan(phone.height, 1180)

        // Wide pane (the Duo landscape hero): a center crop would
        // show y ≈ 395-805 of the portrait — the head (top 15%,
        // 180px) gone. Top-anchored, the visible window is
        // y = 0..~411: head and torso, legs are the crop's cost.
        let wide = HeroPhoto.visibleRect(
            imageSize: CGSize(width: 800, height: 1200),
            frameSize: CGSize(width: 700, height: 360)
        )
        XCTAssertEqual(wide.minY, 0)
        XCTAssertEqual(wide.height, 411, accuracy: 1)
        XCTAssertGreaterThan(wide.height, 180)

        // Square pool asset in the phone hero: full height shown,
        // sides centered — no letterbox in either direction.
        let square = HeroPhoto.visibleRect(
            imageSize: CGSize(width: 1024, height: 1024),
            frameSize: CGSize(width: 393, height: 585)
        )
        XCTAssertEqual(square.minY, 0)
        XCTAssertEqual(square.height, 1024, accuracy: 1)
        XCTAssertEqual(square.minX, (1024 - square.width) / 2, accuracy: 0.5)

        // Coverage invariant across shapes: the visible rect's
        // aspect IS the frame's aspect (the scaled image covers).
        let shapes: [(CGSize, CGSize)] = [
            (CGSize(width: 800, height: 1200), CGSize(width: 393, height: 585)),
            (CGSize(width: 800, height: 1200), CGSize(width: 700, height: 360)),
            (CGSize(width: 1024, height: 1024), CGSize(width: 393, height: 585)),
            (CGSize(width: 1200, height: 800), CGSize(width: 393, height: 585)),
        ]
        for (image, frame) in shapes {
            let rect = HeroPhoto.visibleRect(imageSize: image, frameSize: frame)
            XCTAssertEqual(rect.width / rect.height, frame.width / frame.height, accuracy: 0.01)
            XCTAssertEqual(rect.minY, 0)
        }
    }

    @MainActor
    func testHeroPhotoRenderKeepsTheHeadBand() throws {
        // A synthetic portrait: red head band (top 15%), blue
        // torso, green legs — rendered through the REAL HeroPhoto
        // into a wide frame, the shape that cut heads under the
        // old center crop.
        let size = CGSize(width: 200, height: 300)
        let uiImage = UIGraphicsImageRenderer(size: size).image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 200, height: 45))
            UIColor.blue.setFill()
            ctx.fill(CGRect(x: 0, y: 45, width: 200, height: 135))
            UIColor.green.setFill()
            ctx.fill(CGRect(x: 0, y: 180, width: 200, height: 120))
        }

        let renderer = ImageRenderer(content: HeroPhoto(image: Image(uiImage: uiImage), width: 200, height: 100))
        renderer.scale = 1
        let cgImage = try XCTUnwrap(renderer.cgImage)

        // Top-center is the head band. Under center-crop this
        // pixel was torso blue; under a letterbox it was black.
        let top = pixel(cgImage, x: 100, y: 4)
        XCTAssertTrue(isRed(top), "top-center should be the head band, got \(top)")
        // Bottom-center is torso — the crop took the legs.
        let bottom = pixel(cgImage, x: 100, y: 95)
        XCTAssertTrue(isBlue(bottom), "bottom-center should be torso, got \(bottom)")
        // Corners are photo, never letterbox bars.
        for (x, y) in [(2, 2), (197, 2), (2, 97), (197, 97)] {
            let corner = pixel(cgImage, x: x, y: y)
            XCTAssertFalse(isBlack(corner), "corner (\(x),\(y)) is a letterbox bar: \(corner)")
        }
    }

    // MARK: - Pixel sampling

    private func pixel(_ image: CGImage, x: Int, y: Int) -> (r: Int, g: Int, b: Int) {
        let width = image.width
        let height = image.height
        var data = [UInt8](repeating: 0, count: width * height * 4)
        let context = CGContext(
            data: &data,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )!
        // Normalize to top-down rows (UIKit orientation).
        context.translateBy(x: 0, y: CGFloat(height))
        context.scaleBy(x: 1, y: -1)
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        let index = (y * width + x) * 4
        return (Int(data[index]), Int(data[index + 1]), Int(data[index + 2]))
    }

    private func isRed(_ pixel: (r: Int, g: Int, b: Int)) -> Bool {
        pixel.r > 150 && pixel.g < 100 && pixel.b < 100
    }

    private func isBlue(_ pixel: (r: Int, g: Int, b: Int)) -> Bool {
        pixel.b > 150 && pixel.r < 100 && pixel.g < 100
    }

    private func isBlack(_ pixel: (r: Int, g: Int, b: Int)) -> Bool {
        pixel.r < 40 && pixel.g < 40 && pixel.b < 40
    }
}

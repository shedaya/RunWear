import XCTest

/// Engine-boundary fixtures (Stage 1, 2026-10-29).
///
/// The v4.3 engine tables were never the problem — the October 2026
/// parity audit found the iOS app diverging from Android at the
/// CALL BOUNDARY: the engine ran on actual temperature instead of
/// feels-like, humidity was fabricated at 50, UV was always 0, wind
/// arrived in km/h against an mph gate, rain was inferred from the
/// weather-code class instead of measured rain, and a time-change
/// re-run passed only temperature + condition so every other input
/// silently defaulted. The outfit sweep pins the tables; these
/// fixtures pin the boundary by driving the PRODUCTION construction
/// — WeatherViewModel.updateSelectedWeatherSnapshot +
/// makeRecommendation — never a reimplementation of it.
final class EngineBoundaryTests: XCTestCase {

    // MARK: - Fixture plumbing

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
        // Pin the comfort profile per test: the view model persists
        // changes to UserDefaults, so never inherit another test's.
        vm.comfortLevel = .neutral
        return vm
    }

    private func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day))!
    }

    @MainActor
    private func recommend(_ vm: WeatherViewModel, day: Date, hour: Int) -> OutfitRecommendation {
        vm.selectedDate = day
        vm.selectedHour = hour
        vm.updateSelectedWeatherSnapshot()
        vm.makeRecommendation()
        return vm.recommendation!
    }

    // MARK: - Feels-like, not actual temperature

    @MainActor
    func testFeelsLikeDrivesTheEngine_notActualTemperature() {
        // 55°F actual / 41°F feels-like: dressed for the ACTUAL
        // temperature this is shorts + short sleeves (mild bracket);
        // dressed for feels-like (Android's frame) it is light
        // tights + a light long sleeve (cool bracket). Before Stage 1
        // the engine got the actual.
        let vm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 13, feelsC: 5)
        ]))
        let rec = recommend(vm, day: date(2026, 10, 29), hour: 7)
        XCTAssertEqual(rec.bottom.key, "LIGHT_TIGHTS")
        XCTAssertEqual(rec.topBase.key, "LONG_SLEEVE_LIGHT")
        XCTAssertEqual(rec.temperatureBracket, .cool)
    }

    // MARK: - Real humidity and UV reach the engine

    @MainActor
    func testHumidityAndUvReachTheEngine() {
        // 70°F feels-like, 85% humidity, UV 8. Before Stage 1 the
        // engine saw humidity 50 (fabricated) and UV 0 (never
        // fetched): no humidity tip, no sun gear at all.
        let vm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T12:00", tempC: 21, feelsC: 21, humidity: 85, uv: 8)
        ]))
        let rec = recommend(vm, day: date(2026, 10, 29), hour: 12)
        let accessoryKeys = Set(rec.accessories.map(\.key))
        XCTAssertTrue(accessoryKeys.contains("SUNGLASSES"))
        XCTAssertTrue(accessoryKeys.contains("SUNSCREEN"))
        XCTAssertEqual(rec.head?.key, "VISOR") // raw 70°F + UV > 3 sun gate
        XCTAssertTrue(rec.tips.contains { $0.contains("High humidity") })
    }

    // MARK: - Wind is mph at the gate

    @MainActor
    func testWindGateReadsMph() {
        // 36°F feels-like sits in the wind-gated band (25–50°F):
        // 15 mph calls the windbreaker, 9 mph does not. The values
        // pass through selection unconverted — the request asks
        // Open-Meteo for mph (before Stage 1 it asked for km/h and
        // the same numbers silently under-read the gate by 1.6x).
        let windy = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 2, feelsC: 2, windMph: 15)
        ]))
        let recWindy = recommend(windy, day: date(2026, 10, 29), hour: 7)
        XCTAssertEqual(windy.selectedWeatherSnapshot?.windSpeed, 15)
        XCTAssertEqual(recWindy.topOuter?.key, "WINDBREAKER")

        let calm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 2, feelsC: 2, windMph: 9)
        ]))
        let recCalm = recommend(calm, day: date(2026, 10, 29), hour: 7)
        XCTAssertNil(recCalm.topOuter)
    }

    // MARK: - Measured rain, not the weather-code class

    @MainActor
    func testMeasuredRainDrivesRainGear() {
        // (a) Real rain (0.5 mm): rain jacket + rain cap.
        let rain = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 16, feelsC: 16, precipProb: 90, precip: 0.5, rain: 0.5, code: 61)
        ]))
        let recRain = recommend(rain, day: date(2026, 10, 29), hour: 7)
        XCTAssertEqual(recRain.topOuter?.key, "RAIN_JACKET")
        XCTAssertEqual(recRain.head?.key, "BASEBALL_CAP")

        // (b) Drizzle (0.05 mm, at/below the 0.1 gate): NO rain
        // jacket — it falls through to the temperature bands, as in
        // the Kotlin engine — but the rain cap and rain tip still
        // fire, because they key off isRaining alone.
        let drizzle = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 16, feelsC: 16, precipProb: 40, precip: 0.05, rain: 0.05, code: 51)
        ]))
        let recDrizzle = recommend(drizzle, day: date(2026, 10, 29), hour: 7)
        XCTAssertNil(recDrizzle.topOuter)
        XCTAssertEqual(recDrizzle.head?.key, "BASEBALL_CAP")
        XCTAssertTrue(recDrizzle.tips.contains { $0.contains("cap under your hood") })

        // (c) Rain CODE but zero measured rain: Android's isRaining
        // (rain > 0) is false, so no rain gear at all. The pre-Stage-1
        // class-based derivation produced a rain jacket + cap here.
        let codedDry = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 16, feelsC: 16, precipProb: 20, precip: 0, rain: 0, code: 61)
        ]))
        let recCodedDry = recommend(codedDry, day: date(2026, 10, 29), hour: 7)
        XCTAssertNil(recCodedDry.topOuter)
        XCTAssertNil(recCodedDry.head)
    }

    // MARK: - A time-change re-run passes the full input set (R-04)

    @MainActor
    func testTimeChangeRerunKeepsFullInputs() {
        let vm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 16, feelsC: 16),
            HourSpec(time: "2026-10-29T18:00", tempC: 2, feelsC: 2, uv: 8, windMph: 15)
        ]))
        let morning = recommend(vm, day: date(2026, 10, 29), hour: 7)
        XCTAssertNil(morning.topOuter)
        XCTAssertTrue(morning.accessories.isEmpty)

        // The production re-run entry point. Before Stage 1 it
        // passed only temperature + condition, so the evening's
        // 15 mph wind and UV 8 silently defaulted to 0: no
        // windbreaker, no sunglasses.
        vm.updateSelectedTime(date: date(2026, 10, 29), hour: 18)
        let evening = vm.recommendation!
        XCTAssertEqual(evening.topOuter?.key, "WINDBREAKER")
        XCTAssertTrue(evening.accessories.contains { $0.key == "SUNGLASSES" })
    }

    // MARK: - Snapshot selection matches the location frame textually

    @MainActor
    func testSnapshotSelectionMatchesLocationFrameHour() {
        let vm = makeViewModel(hourly: makeHourly([
            HourSpec(time: "2026-10-29T07:00", tempC: 10, feelsC: 10),
            HourSpec(time: "2026-10-29T08:00", tempC: 20, feelsC: 20)
        ]))
        vm.selectedDate = date(2026, 10, 29)
        vm.selectedHour = 7
        vm.updateSelectedWeatherSnapshot()
        XCTAssertEqual(vm.selectedWeatherSnapshot?.hour, 7)
        XCTAssertEqual(vm.selectedWeatherSnapshot?.feelsLike, 10)

        // An hour outside the forecast window falls back to the
        // current-conditions snapshot rather than fabricating one.
        vm.selectedHour = 3
        vm.updateSelectedWeatherSnapshot()
        XCTAssertEqual(vm.selectedWeatherSnapshot?.hour, 3)
        XCTAssertEqual(vm.selectedWeatherSnapshot?.temperature, 10) // the fixture's current block
        XCTAssertEqual(vm.selectedWeatherSnapshot?.weatherCode, 0)
    }

    // MARK: - Every engine-emittable item resolves a thumbnail (R-07)

    @MainActor
    func testEveryEmittableItemResolvesAThumbnail() {
        let vm = makeViewModel(hourly: nil)
        let grid: [(rain: Double, precip: Double, uv: Double, hour: Int, windMph: Double)] = [
            (0, 0, 0, 12, 0), (0.5, 0.5, 0, 12, 0), (0.05, 0.05, 0, 12, 0),
            (0, 0, 8, 12, 0), (0, 0, 8, 21, 0), (0, 0, 0, 21, 0), (0, 0, 0, 12, 15)
        ]
        var seen = Set<String>()
        for feelsF in stride(from: -20.0, through: 110.0, by: 2.5) {
            for cell in grid {
                vm.selectedWeatherSnapshot = HourlyWeatherSnapshot(
                    hour: cell.hour,
                    temperature: (feelsF - 32) * 5 / 9,
                    feelsLike: (feelsF - 32) * 5 / 9,
                    humidity: 50,
                    precipitationProbability: 0,
                    precipitation: cell.precip,
                    rain: cell.rain,
                    uvIndex: cell.uv,
                    windSpeed: cell.windMph,
                    windDirection: 0,
                    windGusts: 0,
                    weatherCode: 0
                )
                vm.makeRecommendation()
                for item in vm.recommendation!.allItems {
                    seen.insert(item.key)
                    XCTAssertNotNil(
                        ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: .male),
                        "No male thumbnail for \(item.key)"
                    )
                    XCTAssertNotNil(
                        ClothingThumbnailService.shared.getThumbnailURL(for: item, gender: .female),
                        "No female thumbnail for \(item.key)"
                    )
                }
            }
        }
        // The full emittable set (Android's ClothingItem minus the
        // three the engine never selects: LIGHT_VEST, LIGHT_JACKET,
        // CAPRIS). If a selector silently stops emitting an item,
        // this fails too.
        XCTAssertEqual(seen, [
            "TANK_TOP", "SHORT_SLEEVE", "LONG_SLEEVE_LIGHT", "LONG_SLEEVE_THERMAL",
            "QUARTER_ZIP", "FLEECE_PULLOVER", "HALF_ZIP_THERMAL",
            "WINDBREAKER", "RAIN_JACKET", "INSULATED_JACKET",
            "SHORT_SHORTS", "SHORTS", "LIGHT_TIGHTS", "THERMAL_TIGHTS",
            "VISOR", "BASEBALL_CAP", "HEADBAND", "LIGHT_BEANIE", "THERMAL_BEANIE", "BALACLAVA",
            "LIGHT_GLOVES", "THERMAL_GLOVES", "MITTENS",
            "SUNGLASSES", "SUNSCREEN", "REFLECTIVE_GEAR", "NECK_GAITER"
        ])
    }

    // MARK: - WMO freezing precipitation codes (R-10)

    func testFreezingPrecipitationCodesMapToPrecipConditions() {
        XCTAssertEqual(WeatherCondition.from(code: 56), .drizzle)
        XCTAssertEqual(WeatherCondition.from(code: 57), .drizzle)
        XCTAssertEqual(WeatherCondition.from(code: 66), .rain)
        XCTAssertEqual(WeatherCondition.from(code: 67), .rain)
    }

    // MARK: - Comfort migration (R-13)

    func testComfortMigrationFromSevenLevelModel() {
        XCTAssertEqual(ComfortLevel.fromStoredRawValue(-3), .cold)
        XCTAssertEqual(ComfortLevel.fromStoredRawValue(3), .warm)
        XCTAssertEqual(ComfortLevel.fromStoredRawValue(-10), .veryCold)
        XCTAssertEqual(ComfortLevel.fromStoredRawValue(0), .neutral)
        XCTAssertEqual(ComfortLevel.fromStoredRawValue(10), .veryWarm)
        XCTAssertNil(ComfortLevel.fromStoredRawValue(7))
        XCTAssertEqual(ComfortLevel.allCases.map(\.rawValue), [-10, -5, 0, 5, 10])
    }
}

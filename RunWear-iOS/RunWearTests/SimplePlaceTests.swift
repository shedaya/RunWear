import XCTest

/// Pins for WS-4 (RC-4) — the simple-place model, iOS alignment.
///
/// The register judged iOS narration CLEAN at code level (D-08 /
/// D-09), because CLGeocoder usually hands iOS an abbreviated
/// region for US places. That was luck, not a model: iOS had no
/// simple-place value, the header and the picker concatenated
/// placemark fields verbatim, and the mini bar's "Update to"
/// payload was a caller-built "{n} items · {temp}" summary rather
/// than the entry's real payload (its first kit item). These pins
/// cover the model itself and EVERY place-bearing surface's
/// formatter, with fixtures carrying long place names and
/// multi-word regions, proving the simple form renders and no
/// raw geocode string can reach a rendered line.
///
/// Dual-membered pure types are compiled into the test target
/// directly, so no @testable import.
final class SimplePlaceTests: XCTestCase {

    // MARK: - Fixtures

    private var utcCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func entry(
        place: String,
        items: [String] = ["Insulated Jacket", "Tights", "Gloves"],
        feels: Double = 41,
        condition: String = "Cloudy"
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: utcCalendar.startOfDay(for: Date(timeIntervalSince1970: 1_762_560_000)),
            placeName: place,
            feelsLikeF: feels,
            actualF: feels - 3,
            condition: condition,
            itemNames: items,
            itemKeys: items.map { $0.lowercased() },
            heroImageReference: "hero"
        )
    }

    /// Raw geocode strings as a geocoder / legacy store can carry
    /// them: full region names, the city duplicated in the region
    /// slot, and a country third component.
    private let rawFixtures: [(raw: String, simple: String)] = [
        ("New York, New York", "New York, NY"),
        ("New York, New York, United States", "New York, NY"),
        ("Kansas City, Missouri", "Kansas City, MO"),
        ("Fort Lauderdale, Florida", "Fort Lauderdale, FL"),
        ("Rancho Santa Margarita, California", "Rancho Santa Margarita, CA"),
        ("Washington, District of Columbia", "Washington, DC"),
        ("Sydney, New South Wales", "Sydney, NSW"),
        ("Toronto, Ontario", "Toronto, ON"),
        ("San Francisco, CA", "San Francisco, CA"),
        ("Singapore, Singapore", "Singapore"),
        ("Null Island", "Null Island"),
    ]

    // MARK: - The model: derivation at geocode time

    func testDerivationFromGeocodeComponents() {
        XCTAssertEqual(
            SimplePlace(city: "New York", region: "New York")?.displayName,
            "New York, NY", "the D-08 string itself: city duplicated in the region slot")
        XCTAssertEqual(
            SimplePlace(city: "Kansas City", region: "Missouri")?.displayName,
            "Kansas City, MO")
        XCTAssertEqual(
            SimplePlace(city: "Washington", region: "District of Columbia")?.displayName,
            "Washington, DC", "multi-word region takes its standard abbreviation")
        XCTAssertEqual(
            SimplePlace(city: "Sydney", region: "New South Wales")?.displayName,
            "Sydney, NSW", "multi-word region, Australian table")
        XCTAssertEqual(
            SimplePlace(city: "San Francisco", region: "CA")?.displayName,
            "San Francisco, CA", "an already-abbreviated region is kept")
        XCTAssertEqual(
            SimplePlace(city: "Singapore", region: "Singapore")?.displayName,
            "Singapore", "an unabbreviable region that repeats the city is dropped")
        XCTAssertEqual(
            SimplePlace(city: "  Rancho   Santa Margarita ", region: " California ")?.displayName,
            "Rancho Santa Margarita, CA", "long city names are whitespace-normalized, not truncated")
        XCTAssertEqual(
            SimplePlace(city: nil, region: "New York")?.displayName,
            "New York", "a region-only geocode still names a place")
        XCTAssertNil(SimplePlace(city: nil, region: nil))
        XCTAssertNil(SimplePlace(city: " ", region: ""))
    }

    func testDerivationFromRawDisplayStrings() {
        for fixture in rawFixtures {
            XCTAssertEqual(
                SimplePlace(rawDisplayName: fixture.raw)?.displayName,
                fixture.simple, fixture.raw)
            XCTAssertEqual(
                SimplePlace.displayName(forRawDisplayName: fixture.raw),
                fixture.simple, fixture.raw)
        }
        XCTAssertNil(SimplePlace(rawDisplayName: "   "))
        XCTAssertEqual(SimplePlace.displayName(forRawDisplayName: "   "), "")
    }

    func testRawStringNeverSurvivesDerivation() {
        // For every fixture whose raw form differs from the simple
        // form, the raw form must not be a substring-equal render:
        // specifically the full region name and any third
        // component must be GONE from the rendered line.
        for fixture in rawFixtures where fixture.raw != fixture.simple {
            let rendered = SimplePlace.displayName(forRawDisplayName: fixture.raw)
            XCTAssertNotEqual(rendered, fixture.raw, fixture.raw)
            XCTAssertFalse(rendered.contains("United States"), fixture.raw)
            XCTAssertFalse(rendered.contains("Missouri"), fixture.raw)
            XCTAssertFalse(rendered.contains("California"), fixture.raw)
            XCTAssertFalse(rendered.contains("District of Columbia"), fixture.raw)
            XCTAssertFalse(rendered.contains("New South Wales"), fixture.raw)
        }
    }

    // MARK: - Home header location line (§7 header stack)

    func testHeaderLocationLineRendersSimpleForm() {
        for fixture in rawFixtures {
            XCTAssertEqual(
                HomeHeaderCopy.locationLine(place: fixture.raw, moment: "Now"),
                "\(fixture.simple) · Now", fixture.raw)
        }
        // The design's own example, via the SimplePlace overload.
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(
                place: SimplePlace(city: "New York", region: "New York")!,
                moment: "Now"),
            "New York, NY · Now")
        // A non-now moment keeps the same simple place.
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(place: "Kansas City, Missouri", moment: "Today 3:00 PM"),
            "Kansas City, MO · Today 3:00 PM")
    }

    // MARK: - Narration, both states (notes §1 pattern)

    func testAddedNarrationUsesSimplePlaceAndNow() {
        for fixture in rawFixtures {
            let state = WearIveRunMiniBarModel.state(
                todaysEntry: entry(place: fixture.raw),
                moment: "Now",
                wasUpdatedThisSession: false)
            XCTAssertEqual(
                state, .added(place: fixture.simple, moment: "Now"), fixture.raw)
            XCTAssertEqual(
                WearIveRunNarration.line(for: state),
                "Added \(fixture.simple), Now", fixture.raw)
        }
    }

    func testAddedNarrationDayFormIsNowNotToday() {
        // The narration's moment is the header's "Now" form; the
        // collection's "Today" day label belongs to the expanded
        // subtitle only (frame 3). A "Today" in the mini line is
        // the D-08 defect.
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: entry(place: "New York, New York"),
            moment: "Now",
            wasUpdatedThisSession: false)
        let line = WearIveRunNarration.line(for: state) ?? ""
        XCTAssertTrue(line.hasSuffix(", Now"), line)
        XCTAssertFalse(line.contains("Today"), line)
    }

    func testUpdatedNarrationIsFedTheEntrysRealPayload() {
        // The entry's real payload is its FIRST KIT ITEM (the
        // design's pattern; Android's holder computed it and
        // never used it — D-08). Not the place, not the day form,
        // not a count/temperature summary.
        let e = entry(place: "New York, New York",
                      items: ["Insulated Jacket", "Tights", "Gloves"])
        XCTAssertEqual(e.narrationPayload, "Insulated Jacket")
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: e, moment: "Now", wasUpdatedThisSession: true)
        XCTAssertEqual(state, .updated(payload: "Insulated Jacket"))
        XCTAssertEqual(
            WearIveRunNarration.line(for: state),
            "Update to Insulated Jacket")
        // The superseded iOS summary form must not reappear.
        let line = WearIveRunNarration.line(for: state) ?? ""
        XCTAssertFalse(line.contains("items"), line)
        XCTAssertFalse(line.contains("°"), line)
        XCTAssertFalse(line.contains("New York"), line)
    }

    func testMiniNarrationIsShortByConstruction() {
        // D-09: the mini line is ONE line; it survives because its
        // payload is short by construction. With the longest
        // fixture place, the Added line is wordmark-independent
        // narration text of bounded length — city + abbreviated
        // region + "Now" and nothing else.
        let state = WearIveRunMiniBarModel.state(
            todaysEntry: entry(place: "Rancho Santa Margarita, California"),
            moment: "Now",
            wasUpdatedThisSession: false)
        let line = WearIveRunNarration.line(for: state) ?? ""
        XCTAssertEqual(line, "Added Rancho Santa Margarita, CA, Now")
        XCTAssertEqual(line.components(separatedBy: ",").count, 3,
                       "verb + city, region, moment — no raw third component: \(line)")
    }

    // MARK: - Expanded entry subtitle

    func testExpandedLineUsesSimplePlaceAndDoesNotDuplicateNarration() {
        let e = entry(place: "New York, New York, United States",
                      items: ["Insulated Jacket", "Tights", "Gloves", "Hat", "Socks"])
        let expanded = WearIveRunNarration.expandedLine(for: e, formattedFeelsLike: "41°")
        XCTAssertEqual(expanded, "New York, NY · Today — 41° feels like · 5 items")
        // The expanded subtitle must not duplicate either
        // narration payload: not the Added payload ("{place},
        // Now") and not the Update payload (first kit item).
        XCTAssertFalse(expanded.contains("Now"), expanded)
        XCTAssertFalse(expanded.contains("Insulated Jacket"), expanded)
        XCTAssertFalse(expanded.contains("United States"), expanded)
    }

    func testExpandedLineWithLongPlaceName() {
        let e = entry(place: "Rancho Santa Margarita, California", items: ["Tee"])
        XCTAssertEqual(
            WearIveRunNarration.expandedLine(for: e, formattedFeelsLike: "72°"),
            "Rancho Santa Margarita, CA · Today — 72° feels like · 1 item")
    }

    // MARK: - Collection chapter covers + entry identity

    func testChapterCoverNamesTheSimplePlace() {
        let entries = [
            entry(place: "New York, New York", feels: 40),
            entry(place: "Kansas City, Missouri", feels: 55),
        ]
        let chapters = WearIveRunCollection.chapters(
            from: entries, filter: .all, calendar: utcCalendar)
        XCTAssertEqual(Set(chapters.map(\.placeName)), ["New York, NY", "Kansas City, MO"])
    }

    func testLegacyRawAndSimpleEntriesAreTheSamePlace() {
        // An entry stored under the raw string and one stored
        // under the simple form share ONE identity: one chapter,
        // one upsert slot, one trash target.
        let raw = entry(place: "New York, New York", feels: 40)
        let simple = entry(place: "New York, NY", feels: 44)
        XCTAssertEqual(raw.placeKey, simple.placeKey)
        XCTAssertEqual(raw.id, simple.id)
        let upserted = WearIveRunCollection.upserting([raw], simple)
        XCTAssertEqual(upserted.count, 1)
        XCTAssertEqual(upserted.first?.feelsLikeF, 44)
        let chapters = WearIveRunCollection.chapters(
            from: [raw, simple], filter: .all, calendar: utcCalendar)
        XCTAssertEqual(chapters.count, 1)
        XCTAssertEqual(chapters.first?.placeName, "New York, NY")
    }

    func testStoreLookupAndTrashCrossRawAndSimpleForms() {
        let defaults = UserDefaults(suiteName: "ws4-tests-\(UUID().uuidString)")!
        let store = WearIveRunStore(defaults: defaults)
        let e = entry(place: "New York, New York", feels: 41)
        store.record(e)
        // Lookup by the simple name finds the raw-stored entry,
        // and trash by the simple name removes it.
        XCTAssertEqual(
            store.todaysEntry(placeName: "New York, NY", today: e.dayStart, calendar: utcCalendar)?.feelsLikeF,
            41)
        store.remove(dayStart: e.dayStart, placeName: "New York, NY")
        XCTAssertTrue(store.entries.isEmpty)
    }

    func testEntryPlaceDisplayNameForEveryFixture() {
        for fixture in rawFixtures {
            XCTAssertEqual(
                entry(place: fixture.raw).placeDisplayName,
                fixture.simple, fixture.raw)
        }
    }

    // MARK: - Recents (rendered place lines in the picker)

    func testRecentsStoreTheSimpleForm() {
        let recorded = RecentLocations.recording(
            [],
            selection: RecentLocation(name: "New York, New York, United States", latitude: 40.7, longitude: -74.0))
        XCTAssertEqual(recorded.first?.name, "New York, NY")
        // A raw and a simple selection of the same place dedupe
        // to one row.
        let again = RecentLocations.recording(
            recorded,
            selection: RecentLocation(name: "New York, NY", latitude: 40.7, longitude: -74.0))
        XCTAssertEqual(again.count, 1)
        XCTAssertEqual(again.first?.name, "New York, NY")
    }

    // MARK: - View model rendered form

    @MainActor
    func testViewModelPlaceDisplayNameSanitizesDirectAssignment() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }
        let vm = WeatherViewModel()
        // Even a direct raw assignment (the seam tests use) can
        // only render the simple form.
        vm.locationName = "New York, New York, United States"
        XCTAssertEqual(vm.placeDisplayName, "New York, NY")
        vm.locationName = "Rancho Santa Margarita, California"
        XCTAssertEqual(vm.placeDisplayName, "Rancho Santa Margarita, CA")
        // Non-place labels pass through untouched.
        vm.locationName = "Current Location"
        XCTAssertEqual(vm.placeDisplayName, "Current Location")
    }

    @MainActor
    func testManualLocationSaveStoresTheSimpleForm() {
        let defaults = UserDefaults(suiteName: "ws4-manual-\(UUID().uuidString)")!
        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 40.7, longitude: -74.0, name: "New York, New York"),
            to: defaults)
        XCTAssertEqual(ManualLocationStore.load(from: defaults)?.name, "New York, NY")
    }
}

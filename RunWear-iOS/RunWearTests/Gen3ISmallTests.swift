import XCTest

/// GEN-3 lane I-SMALL — the iOS state of census X-11 / X-12 / X-13
/// (observed on Android in the gen-2 walkthrough), diagnosed
/// surface by surface and pinned here.
///
/// X-12 — Location search duplicates. iOS searches ONE source
/// (CLGeocoder in LocationPickerSheet); Android's dual-source
/// merge does not exist. But construction applied no dedupe at
/// all — every placemark became a row under a fresh UUID — so a
/// place returned twice rendered as two identical rows. The fix
/// dedupes at construction on place identity (SimplePlace key +
/// coordinate), via the pure LocationSearchCandidate /
/// LocationSearchResults in RW26CollectionModels (dual-membered,
/// so these pins compile the production logic directly).
///
/// X-13 — The "Current location" row and the current-place name.
/// The Android row flipped to "Requires location permission"
/// with the sheet's mode while GPS was granted and in use. iOS
/// has NO permission text in the row at all (pinned below) — the
/// row cannot make that false claim. Its one piece of state, the
/// CURRENT badge, WAS mode-sourced: two of the three presentation
/// sites hardcoded `isUsingGPS: false` (ContentView's gate sheet,
/// Onboarding's sheet — whose preview view model is in GPS mode
/// by default), so the badge followed the sheet host instead of
/// the actual selection. All sites now pass the view model's
/// actual state (source-pinned below). The "Currently using:"
/// pill is an Android sheet element with no iOS counterpart; the
/// iOS surfaces that name the place in use render
/// `WeatherViewModel.placeDisplayName`, which now guarantees a
/// non-empty name instead of passing an empty string through.
///
/// X-11 — Morning Poster thumbnail text overlap. iOS composes
/// the poster at ONE size (390×520, MorningPosterMetrics) and
/// renders it once, full-size, through ImageRenderer; the share
/// preview is a raster scale-down. There is no thumbnail-size
/// re-layout anywhere on iOS, so Android's collision mechanism
/// does not exist. Full-size verdict: NO collision — the lockup
/// band and the body+foot band are sequential VStack bands
/// across a Spacer, and their bounded heights (the metrics the
/// view itself consumes) leave clear headroom even with
/// worst-case wrapped content, at full size and — the geometry
/// is scale-linear — at thumbnail scale.
final class Gen3ISmallTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    private func occurrences(_ text: String, _ needle: String) -> Int {
        text.components(separatedBy: needle).count - 1
    }

    // MARK: - X-12: dedupe by place identity

    private func candidate(
        displayName: String,
        latitude: Double,
        longitude: Double,
        name: String? = nil,
        subtitle: String = "New York, United States"
    ) -> LocationSearchCandidate {
        LocationSearchCandidate(
            name: name ?? displayName,
            subtitle: subtitle,
            displayName: displayName,
            latitude: latitude,
            longitude: longitude
        )
    }

    func testSearchDedupe_samePlaceReturnedTwice_rendersOnce() {
        let newYork = candidate(displayName: "New York, NY", latitude: 40.7128, longitude: -74.0060)
        let results = LocationSearchResults.deduplicated([newYork, newYork])
        XCTAssertEqual(results, [newYork], "the identical place returned twice is ONE row")
    }

    func testSearchDedupe_rawAndSimpleForms_atSameCoordinate_collapse() {
        // Identity canonicalizes through SimplePlace: a raw-form
        // duplicate and the simple form are the same place.
        let simple = candidate(displayName: "New York, NY", latitude: 40.7128, longitude: -74.0060)
        let raw = candidate(displayName: "New York, New York", latitude: 40.7128, longitude: -74.0060)
        let results = LocationSearchResults.deduplicated([simple, raw])
        XCTAssertEqual(results, [simple], "first occurrence wins; the canonical duplicate collapses")
    }

    func testSearchDedupe_distinctSameNamePlaces_survive() {
        // Same name, different regions (and coordinates): BOTH
        // are real results — a name-string dedupe would eat one.
        let illinois = candidate(
            displayName: "Springfield, IL", latitude: 39.7817, longitude: -89.6501,
            subtitle: "Illinois, United States")
        let missouri = candidate(
            displayName: "Springfield, MO", latitude: 37.2089, longitude: -93.2923,
            subtitle: "Missouri, United States")
        let results = LocationSearchResults.deduplicated([illinois, missouri])
        XCTAssertEqual(results, [illinois, missouri])
    }

    func testSearchDedupe_sameNameSameRegion_differentCoordinates_survive() {
        // Identity is name-key PLUS coordinate: two distinct
        // same-name places in one region are not one place.
        let first = candidate(displayName: "Salem, OR", latitude: 44.9429, longitude: -123.0351,
                              subtitle: "Oregon, United States")
        let second = candidate(displayName: "Salem, OR", latitude: 45.4167, longitude: -122.7833,
                               subtitle: "Oregon, United States")
        let results = LocationSearchResults.deduplicated([first, second])
        XCTAssertEqual(results, [first, second])
    }

    func testSearchDedupe_nearDuplicateCoordinates_collapse_orderPreserved() {
        // A duplicate whose coordinate wobbles by well under
        // ~100 m (the identity's rounding) still collapses, and
        // the surviving order is the geocoder's.
        let miami = candidate(displayName: "Miami, FL", latitude: 25.7617, longitude: -80.1918,
                              subtitle: "Florida, United States")
        let miamiAgain = candidate(displayName: "Miami, FL", latitude: 25.76172, longitude: -80.19181,
                                   subtitle: "Florida, United States")
        let tampa = candidate(displayName: "Tampa, FL", latitude: 27.9506, longitude: -82.4572,
                              subtitle: "Florida, United States")
        let results = LocationSearchResults.deduplicated([miami, miamiAgain, tampa])
        XCTAssertEqual(results, [miami, tampa])
    }

    func testPickerConstruction_appliesTheIdentityDedupe_sourcePin() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(sheet.contains("LocationSearchResults.deduplicated(candidates)"),
                      "result construction routes through the identity dedupe")
        XCTAssertTrue(sheet.contains("LocationSearchCandidate("),
                      "placemarks are mapped to the pure candidate before dedupe")
    }

    // MARK: - X-13: the Current location row reads actual state

    func testCurrentLocationRow_everyCallSitePassesTheActualState_sourcePin() throws {
        let content = try sourceFile("../RunWear/Views/ContentView.swift")
        XCTAssertTrue(content.contains("isUsingGPS: viewModel.isUsingGPS"),
                      "the gate sheet passes the view model's actual state")
        XCTAssertFalse(content.contains("isUsingGPS: false"),
                       "no hardcoded mode constant at the gate call site")

        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("isUsingGPS: previewVM.isUsingGPS"),
                      "the onboarding sheet passes the preview view model's actual state")
        XCTAssertFalse(onboarding.contains("isUsingGPS: false"),
                       "no hardcoded mode constant at the onboarding call site")

        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("isUsingGPS: viewModel.isUsingGPS"),
                      "the home sheet passes the view model's actual state")
    }

    func testCurrentLocationRow_carriesNoPermissionClaim_sourcePin() throws {
        let sheet = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertFalse(sheet.contains("Requires location permission"),
                       "the iOS row never claims permission is required — the Android X-13 text has no counterpart")
        XCTAssertFalse(sheet.lowercased().contains("permission"),
                       "the row makes no permission claim of any kind")
        XCTAssertTrue(sheet.contains("if isUsingGPS"),
                      "the row's only state is the CURRENT badge, driven by the passed actual state")
    }

    // MARK: - X-13: the place in use is always named

    @MainActor
    func testPlaceDisplayName_neverRendersEmpty() {
        ManualLocationStore.clear()
        defer { ManualLocationStore.clear() }
        let vm = WeatherViewModel()
        vm.locationName = ""
        XCTAssertEqual(vm.placeDisplayName, "Current Location",
                       "an empty place name falls back to the non-place label — never an empty pill/line")
        vm.locationName = "   "
        XCTAssertEqual(vm.placeDisplayName, "Current Location")
        // Non-empty states are untouched by the guarantee.
        vm.locationName = "Loading..."
        XCTAssertEqual(vm.placeDisplayName, "Loading...")
        vm.locationName = "New York, New York, United States"
        XCTAssertEqual(vm.placeDisplayName, "New York, NY")
    }

    func testLocationLine_neverRendersAnEmptyPlace() {
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(place: "", moment: "Now"),
            "Current Location · Now")
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(place: "   ", moment: "Now"),
            "Current Location · Now")
        XCTAssertEqual(
            HomeHeaderCopy.locationLine(place: "New York, New York", moment: "Now"),
            "New York, NY · Now",
            "named places render exactly as before")
    }

    // MARK: - X-11: poster geometry, full size and thumbnail

    /// Realistic worst case: the longest simple place in the
    /// fixtures, a long day label, four long kit item names, and
    /// a long condition — everything wrapped pessimistically by
    /// the metrics' over-counting line estimator.
    private var worstCasePoster: (feels: String, place: String, items: String) {
        (
            feels: "48° · Thunderstorms with heavy rain",
            place: "Rancho Santa Margarita, CA · Wednesday, November 4",
            items: "Merino Wool Long Sleeve Base Layer · Insulated Waterproof Windbreaker Jacket · Thermal Compression Running Tights · Waterproof Trail Running Shoes"
        )
    }

    func testPosterGeometry_fullSize_noCollisionWithWorstCaseContent() {
        let poster = worstCasePoster
        XCTAssertFalse(
            MorningPosterMetrics.collides(feelsText: poster.feels, placeText: poster.place, itemsText: poster.items, scale: 1),
            "full size (390×520): lockup band + body/foot band leave headroom — NO collision")
        let occupied = MorningPosterMetrics.lockupBandHeight
            + MorningPosterMetrics.bodyBandHeight(feelsText: poster.feels, placeText: poster.place, itemsText: poster.items)
        XCTAssertLessThan(occupied, MorningPosterMetrics.canvasHeight * 0.85,
                          "the bands occupy well under the canvas even in the worst case")
    }

    func testPosterGeometry_thumbnailScale_noCollision() {
        let poster = worstCasePoster
        // The share preview shows the poster at roughly quarter
        // scale. Every metric scales linearly, so the full-size
        // verdict carries — pinned explicitly so a future
        // non-linear change (a fixed-height element, a minimum
        // font) breaks THIS test instead of shipping silently.
        XCTAssertFalse(
            MorningPosterMetrics.collides(feelsText: poster.feels, placeText: poster.place, itemsText: poster.items, scale: 0.25),
            "thumbnail scale: NO collision")
        XCTAssertEqual(
            MorningPosterMetrics.collides(feelsText: poster.feels, placeText: poster.place, itemsText: poster.items, scale: 0.25),
            MorningPosterMetrics.collides(feelsText: poster.feels, placeText: poster.place, itemsText: poster.items, scale: 1),
            "the collision verdict is scale-invariant, as a raster scale-down requires")
    }

    func testPosterGeometry_collisionDetectorCanSayYes() {
        // The pin is not a tautology: absurd content DOES collide.
        let absurd = String(repeating: "Extraordinarily Long Item Name · ", count: 60)
        XCTAssertTrue(
            MorningPosterMetrics.collides(feelsText: "48° · Overcast", placeText: "Miami, FL · Wed, Nov 4", itemsText: absurd, scale: 1))
    }

    func testPoster_singleFullSizeRenderPath_sourcePin() throws {
        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertEqual(occurrences(collection, "MorningPosterView("), 1,
                       "the poster view is instantiated exactly once — the full-size render")
        XCTAssertTrue(collection.contains("ImageRenderer(content: view)"),
                      "the one instantiation renders through ImageRenderer")
        XCTAssertTrue(collection.contains("renderer.scale = 3"),
                      "rendered once at full size, 3x raster")
        XCTAssertTrue(collection.contains("MorningPosterMetrics.canvasWidth")
                      && collection.contains("MorningPosterMetrics.canvasHeight"),
                      "the view consumes the pinned metrics for its canvas")
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains("MorningPosterView"),
                       "no second (thumbnail) composition of the poster exists on the home surface")
    }
}

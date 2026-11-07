import XCTest

/// WS-5 (RC-5: D-13 + F-2) — source pins for the iOS sheet
/// discipline (Part 1 §1.3) and the F-2 dismissal contract.
///
/// §1.3 on iOS, as pinned here:
///   * Every sheet rests at NAMED detents only — an explicit
///     `.presentationDetents` on every `.sheet` in the app.
///   * Sheets whose content scrolls (Form / List / ScrollView)
///     rest at `.large` only — never at a partial detent over
///     independently scrolling content. The two exceptions are
///     named in the spec itself: Location ([.medium, .large],
///     §1.3's Location paragraph), and the Date/Time pickers
///     ([.medium, .large]) whose grids are FIXED content with no
///     scroll consumer at default and moderate sizes.
///     AMENDMENT (RW-73 detent ruling, Sol, Nov 6, 2026 —
///     delegated to Tank's suggestion): at the largest
///     accessibility text sizes, where the pickers' untruncated
///     content is taller than a phone screen, each picker
///     carries exactly ONE ScrollView gated on the
///     accessibility size class, so Confirm stays reachable;
///     the detents do not change. Pinned below in the ruled form.
///   * One scroll consumer per sheet: the sheet's own container
///     (Form, List, or a single ScrollView) owns scrolling.
///   * Settings opens at full height (§1.3's Settings paragraph:
///     "No detents, no partial rest") — [.large] only.
///
/// F-2 on iOS is verification: Your Photos and the Make It Me
/// flow present through the settings surface's single native
/// sheet slot, and every dismissal path (swipe-down, Done/close,
/// the flow's own finish/cancel) returns to the opening surface.
/// There is no boolean full-screen overlay anywhere in the app —
/// the only full-screen covers are §1.4's two sanctioned ones
/// (the onboarding opt-in flow and the guided camera capture).
final class SheetDisciplineTests: XCTestCase {

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

    // MARK: - Named detents, per host file

    func testHomeSheetsRestAtNamedDetents_sourcePin() throws {
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // 10 sheets: Settings, Date, Time, Shop, WeatherDetail,
        // Location, Collection, Feedback, (GEN-3 WS-A(2)) the
        // hero pill's Premium paywall, and (GEN-6 RW-61) the
        // looks library — the failed cell's "See all your looks"
        // door, resting at .large like the other scrolling
        // surfaces.
        XCTAssertEqual(occurrences(view, ".presentationDetents([.large])"), 7,
                       "Settings, Shop, WeatherDetail, Collection, Feedback, the pill paywall and the looks library rest at .large only")
        XCTAssertEqual(occurrences(view, ".presentationDetents([.medium, .large])"), 3,
                       "only Date, Time and Location keep a medium detent")
        XCTAssertEqual(occurrences(view, ".presentationDragIndicator(.visible)"), 10,
                       "every home sheet names its detents and shows the drag indicator")
    }

    func testSettingsOpensAtFullHeight_sourcePin() throws {
        // The Settings presentation block in WeatherOutfitView is
        // the first sheet in the file; its detent is the first
        // detent in the file, and §1.3 gives it .large only.
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        let settingsSheet = view.range(of: "SettingsModal(")!
        let firstDetent = view.range(of: ".presentationDetents(")!
        XCTAssertLessThan(settingsSheet.lowerBound, firstDetent.lowerBound)
        let detentText = view[firstDetent.lowerBound..<view.index(firstDetent.lowerBound, offsetBy: 40)]
        XCTAssertTrue(detentText.contains("[.large]"),
                      "Settings rests at .large only — no partial rest (got: \(detentText))")
        XCTAssertFalse(detentText.contains(".medium"),
                       "Settings must not rest at .medium")
    }

    func testSettingsSurfaceSheetsAreLargeOnly_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertEqual(occurrences(settings, ".presentationDetents([.large])"), 2,
                       "the Feedback sheet and the single Make It Me sheet slot are .large only")
        XCTAssertEqual(occurrences(settings, ".presentationDetents([.medium"), 0,
                       "no sheet inside Settings rests at a partial detent")
    }

    func testEveryRemainingSheetNamesItsDetent_sourcePin() throws {
        let content = try sourceFile("../RunWear/Views/ContentView.swift")
        XCTAssertEqual(occurrences(content, ".presentationDetents([.medium, .large])"), 1,
                       "the manual Location picker keeps §1.3's named Location detents")

        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertEqual(occurrences(onboarding, ".presentationDetents([.medium, .large])"), 1,
                       "onboarding's Location picker keeps the named Location detents")
        XCTAssertEqual(occurrences(onboarding, ".presentationDetents([.large])"), 1,
                       "the first-look Premium sheet (scrolling paywall) is .large only")

        let mim = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertEqual(occurrences(mim, ".presentationDetents([.large])"), 1,
                       "the upsell card's subscription sheet is .large only")

        let collection = try sourceFile("../RunWear/Views/WearIveRunCollectionView.swift")
        XCTAssertEqual(occurrences(collection, ".presentationDetents([.large])"), 1,
                       "the collection's share-choice sheet names its detent")
    }

    // MARK: - One scroll consumer per sheet

    func testSettingsSheetHasOneScrollConsumer_sourcePin() throws {
        // The Settings sheet's content is a Form — the Form IS
        // the scroll consumer; no ScrollView nests inside the
        // SettingsModal file (the D-13 two-consumer mechanism).
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("Form {"))
        XCTAssertEqual(occurrences(settings, "ScrollView {"), 0,
                       "no nested ScrollView may compete with the Settings Form")
        XCTAssertEqual(occurrences(settings, "ScrollView("), 0)
    }

    func testShopSheetHasExactlyOneScrollConsumer_sourcePin() throws {
        let shop = try sourceFile("../RunWear/Views/Modals/ShopModal.swift")
        XCTAssertEqual(occurrences(shop, "ScrollView {"), 1,
                       "the Shop sheet's item list is its one scroll consumer")
    }

    func testLocationSheetsScrollThroughTheirList_sourcePin() throws {
        let location = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(location.contains("List {"),
                      "the Location sheet's List is its scroll consumer")
        XCTAssertEqual(occurrences(location, "ScrollView"), 0,
                       "no second consumer nests inside the Location List")
    }

    func testMediumDetentPickersScrollOnlyAtAccessibilitySizes_sourcePin() throws {
        // The Date/Time pickers are the only non-Location sheets
        // resting at .medium. Pre-ruling form (through Nov 5,
        // 2026): fixed grids with no scroll consumer at all.
        // RW-73 DETENT RULING (Sol, Nov 6, 2026 — delegated to
        // Tank's suggestion): at the largest accessibility text
        // sizes the untruncated content is taller than a phone
        // screen, so each sheet may scroll internally THERE AND
        // ONLY THERE — exactly one ScrollView, directly inside
        // the accessibility-size gate; at default and moderate
        // sizes the fixed, scroll-free form stands. The detents
        // stay [.medium, .large] (pinned above).
        // (Counting note, Nov 6, 2026: the count below is of
        // the CODE form "ScrollView {" — the sheetContent seam
        // comment added the bare word "ScrollView" twice per
        // file, and a word count cannot tell a comment from a
        // consumer. The gated-position assertion beneath is
        // unchanged and is the load-bearing half.)
        for file in ["DatePickerSheet.swift", "TimePickerSheet.swift"] {
            let sheet = try sourceFile("../RunWear/Views/Modals/\(file)")
            XCTAssertEqual(occurrences(sheet, "ScrollView {"), 1,
                           "\(file) carries exactly one ScrollView (the accessibility-size form)")
            XCTAssertTrue(sheet.contains("if dynamicTypeSize.isAccessibilitySize {\n                ScrollView {"),
                          "\(file)'s ScrollView must sit directly inside the accessibility-size gate")
        }
    }

    func testMakeItMeSheetContentsEachCarryOneScrollView_sourcePin() throws {
        // Photos and Subscription are two of the single slot's
        // contents; each carries exactly one ScrollView. GEN-6
        // (RW-61): the looks library at the file's end carries
        // the third — it is hosted from home in its own sheet,
        // not through the Settings slot.
        let mim = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertEqual(occurrences(mim, "ScrollView {"), 3,
                       "Your Photos, the Subscription sheet and the looks library each own exactly one ScrollView")
    }

    // MARK: - The WS-3 dim seam stays intact

    func testHomeDimSeamStillEnumeratesEveryHomeSheet_sourcePin() throws {
        // WS-3's element dims from this seam; WS-5 changes detents
        // only and must not narrow it.
        let view = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        for state in ["showSettings", "showDatePicker", "showTimePicker", "showShop",
                      "showLocationPicker", "selectedWeatherDetail != nil",
                      "showCollection", "showFeedbackSheet"] {
            XCTAssertTrue(view.contains(state), "the dim seam must keep covering \(state)")
        }
        XCTAssertTrue(view.contains("isDimmedBySheet: anyHomeSheetPresented"),
                      "the element must keep consuming the seam")
    }

    // MARK: - F-2: hosting (functional dismissal tests live in
    // SheetDismissalTests below, on the main actor like the host)

    func testYourPhotosIsHostedOnlyByTheSettingsSheetSlot_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains(".sheet(item: $makeItMePresentation.sheet)"),
                      "the Make It Me surface presents through ONE native sheet slot")
        XCTAssertTrue(settings.contains("case .photos:"),
                      "Your Photos is one of the slot's contents")
        // No home/onboarding surface hosts Photos (or the flow)
        // as an overlay — the F-2 exposure cannot exist on iOS.
        for file in ["../RunWear/Views/WeatherOutfitView.swift",
                     "../RunWear/Views/ContentView.swift",
                     "../RunWear/Views/Onboarding/OnboardingView.swift"] {
            let source = try sourceFile(file)
            XCTAssertFalse(source.contains("MakeItMePhotosView("),
                           "\(file) must not host Your Photos outside the sheet slot")
        }
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains("MakeItMeOptInFlow("),
                       "home must not host the Make It Me flow as an overlay")
    }

    func testFullScreenCoversAreOnlyTheTwoSection14Ones_sourcePin() throws {
        // §1.4: capture is full-screen; consent's flow in
        // onboarding rides a full-screen cover. Nothing else in
        // the app presents full-screen — in particular no
        // Make It Me surface is a boolean overlay or cover on
        // the Settings side.
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertEqual(occurrences(onboarding, ".fullScreenCover("), 1,
                       "onboarding's opt-in flow is the one cover there")
        let flow = try sourceFile("../RunWear/Views/MakeItMeFlowViews.swift")
        XCTAssertEqual(occurrences(flow, ".fullScreenCover("), 1,
                       "the guided camera capture is the one cover in the flow")
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertEqual(occurrences(settings, ".fullScreenCover("), 0)
        let mim = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        // RE-PINNED for RW-133 (Sol's Nov 7, 2026 ruling): the
        // Your Looks library presents the settled full-screen
        // hero viewer as a cover — the one sanctioned addition
        // since §1.4. It is a photo viewer, not a Make It Me
        // flow surface; no other cover may stand here.
        XCTAssertEqual(occurrences(mim, ".fullScreenCover("), 1,
                       "the RW-133 hero viewer is the one cover in the Make It Me settings surfaces")
        XCTAssertTrue(mim.contains("HeroPhotoViewer("),
                      "the one cover presents the RW-133 full-screen viewer")
    }

    func testDismissalPathsReturnToTheOpeningSurface_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("Button(\"Done\") { isPresented = false }"),
                      "Settings' Done returns to home")
        XCTAssertEqual(occurrences(settings, "makeItMePresentation.dismissSheet()"), 4,
                       "both Make It Me flows exit through the host's dismissal (finish + cancel, opt-in + replace)")
        let location = try sourceFile("../RunWear/Views/Modals/LocationPickerSheet.swift")
        XCTAssertTrue(location.contains("Button(\"Done\") { close() }"),
                      "Location's Done returns to its opening surface")
        XCTAssertTrue(location.contains("isPresented = false"),
                      "Location dismissal is state-driven — it cannot exit the app")
        // Your Photos declares no dismissal of its own: the
        // native sheet's swipe-down (and the slot's swap to
        // Replace) are its only exits, both owned by the slot.
        // (Sliced to the next struct: the Subscription sheet
        // further down the same file legitimately uses dismiss.)
        let mim = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        let photosStart = mim.range(of: "struct MakeItMePhotosView")!
        let photosEnd = mim.range(of: "struct MakeItMeSubscriptionSheet")!
        let photosBody = mim[photosStart.lowerBound..<photosEnd.lowerBound]
        XCTAssertFalse(photosBody.contains("@Environment(\\.dismiss)"),
                       "Your Photos must not carry a hand-rolled dismissal — the sheet owns it")
    }
}

/// WS-5 / F-2 — the dismissal contract, exercised on the
/// presentation host itself: every Make It Me sheet — Your Photos
/// first — presents through the Settings surface's single native
/// sheet slot, and dismissing it lands back on that surface. The
/// host IS the state the sheet renders, so "dismissal returns to
/// the opening surface" is a state fact here, not a UI hope; no
/// state in this machine corresponds to leaving the app.
@MainActor
final class SheetDismissalTests: XCTestCase {

    func testYourPhotosPresentsThroughTheSingleSlotAndDismissesToSettings() {
        let host = MakeItMePresentationHost()
        host.present(.photos)
        XCTAssertEqual(host.sheet, .photos, "Your Photos presents as the slot's sheet")
        host.dismissSheet()
        XCTAssertNil(host.sheet,
                     "dismissal returns to the Settings surface that owns the host — there is no exit path")
    }

    func testPhotosToReplaceIsAContentSwapInsideOnePresentation() {
        let host = MakeItMePresentationHost()
        host.present(.photos)
        host.present(.replace)
        XCTAssertEqual(host.sheet, .replace,
                       "Photos → Replace swaps content inside the single presentation, never a dismiss-and-represent chain")
        host.dismissSheet()
        XCTAssertNil(host.sheet)
    }

    func testEveryMakeItMeSheetDismissesBackToTheOpeningSurface() {
        let host = MakeItMePresentationHost()
        for sheet in [MakeItMeSheet.optIn, .replace, .photos, .subscription] {
            host.present(sheet)
            XCTAssertEqual(host.sheet, sheet)
            host.dismissSheet()
            XCTAssertNil(host.sheet, "\(sheet) must dismiss to its opening surface")
        }
    }
}

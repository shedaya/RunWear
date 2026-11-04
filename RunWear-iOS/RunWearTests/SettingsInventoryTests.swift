import XCTest

/// WS-5 (D-14) — Settings completeness as a TEST, not a review
/// finding. The designed inventory (frames 35–39; notes §8H) must
/// render in EVERY shipped configuration: both Make It Me flag
/// states × all three coded modes. If a registered section ever
/// emits nothing in a configuration, one of these tests fails.
///
/// The iOS half of the invariant the Android lane pins against
/// the same registry concept (SettingsInventory.kt): same nine
/// section families, each platform's own Form order.
final class SettingsInventoryTests: XCTestCase {

    func testTheRegistryIsTheDesignedInventoryInDesignedOrder() {
        XCTAssertEqual(
            SettingsInventory.sections,
            [
                .appearance,
                .temperatureUnit,
                .fitPreference,
                .bodyTemperature,
                .helpImprove,
                .makeItMe,
                .deleteMyData,
                .support,
                .about,
            ]
        )
    }

    func testEveryConfigurationRendersTheFullInventory() {
        for enabled in [false, true] {
            for mode in SettingsMakeItMeMode.allCases {
                XCTAssertEqual(
                    SettingsInventory.sectionsFor(makeItMeEnabled: enabled, makeItMeMode: mode),
                    SettingsInventory.sections,
                    "flag=\(enabled) mode=\(mode) must render the full inventory — no section emits nothing"
                )
            }
        }
    }

    func testAppearanceIsFirstAndAboutIsLast() {
        XCTAssertEqual(SettingsInventory.sections.first, .appearance)
        XCTAssertEqual(SettingsInventory.sections.last, .about)
    }

    func testDeleteMyDataIsItsOwnSectionAfterMakeItMeAndBeforeSupport() {
        let sections = SettingsInventory.sections
        let delete = sections.firstIndex(of: .deleteMyData)!
        XCTAssertGreaterThan(delete, sections.firstIndex(of: .makeItMe)!)
        XCTAssertLessThan(delete, sections.firstIndex(of: .support)!)
    }

    // MARK: - The Make It Me section's rows (frames 35–37)

    func testMakeItMeSectionPresentsRowsInEveryConfiguration() {
        for enabled in [false, true] {
            for mode in SettingsMakeItMeMode.allCases {
                XCTAssertFalse(
                    MakeItMeSettingsPresentation.rows(makeItMeEnabled: enabled, mode: mode).isEmpty,
                    "flag=\(enabled) mode=\(mode) must present at least one row"
                )
            }
        }
    }

    func testFlagOffPresentsExactlyThePlainNotStartedRow() {
        for mode in SettingsMakeItMeMode.allCases {
            XCTAssertEqual(
                MakeItMeSettingsPresentation.rows(makeItMeEnabled: false, mode: mode),
                [.notStarted],
                "with the flag OFF every mode shows frame 36's plain not-started row"
            )
        }
    }

    func testEachEnabledModePresentsItsDesignedRows() {
        XCTAssertEqual(
            MakeItMeSettingsPresentation.rows(makeItMeEnabled: true, mode: .stock),
            [.notStarted]
        )
        XCTAssertEqual(
            MakeItMeSettingsPresentation.rows(makeItMeEnabled: true, mode: .free),
            [.yourPhotos, .premium, .deletePhotos]
        )
        XCTAssertEqual(
            MakeItMeSettingsPresentation.rows(makeItMeEnabled: true, mode: .subscribed),
            [.yourPhotos, .manageSubscription, .deletePhotos]
        )
    }

    func testRowTitlesAreTheShippedStrings() {
        XCTAssertEqual(MakeItMeSettingsRow.notStarted.title, "See yourself in every outfit")
        XCTAssertEqual(MakeItMeSettingsRow.yourPhotos.title, "Your photos")
        XCTAssertEqual(MakeItMeSettingsRow.premium.title, "Premium")
        XCTAssertEqual(MakeItMeSettingsRow.manageSubscription.title, "Manage subscription")
        XCTAssertEqual(MakeItMeSettingsRow.deletePhotos.title, "Delete my photos")
    }

    // MARK: - Source pins (the Form IS the registry)

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw XCTSkip("source of record unreachable at \(relativePath)")
        }
        return text
    }

    func testSettingsFormComposesTheRegistryInOrder_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("SettingsInventory.sectionsFor("),
                      "the Form's sections must come from the registry")
        XCTAssertTrue(settings.contains("ForEach(inventorySections)"),
                      "the Form must iterate the registry, one section per entry")
        // The section switch renders the registry in registry
        // order — a reordered or dropped case is a dropped
        // section on screen.
        let cases = [
            "case .appearance:", "case .temperatureUnit:", "case .fitPreference:",
            "case .bodyTemperature:", "case .helpImprove:", "case .makeItMe:",
            "case .deleteMyData:", "case .support:", "case .about:",
        ]
        var lastIndex = -1
        for marker in cases {
            let index = settings.range(of: marker)?.lowerBound.utf16Offset(in: settings)
            XCTAssertNotNil(index, "SettingsModal must render the \(marker) section")
            if let index {
                XCTAssertGreaterThan(index, lastIndex, "\(marker) is out of registry order")
                lastIndex = index
            }
        }
    }

    func testNoSectionIsGatedOnTheAvailabilityFlag_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertFalse(settings.contains("if MakeItMeAvailability.isEnabled"),
                       "the flag may gate function, never a section's existence (D-14)")
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(section.contains("if featureEnabled"),
                      "inside the section, the flag gates only the not-started row's door")
    }

    func testDeleteMyDataLivesInSettingsNotInTheMakeItMeSection_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("Text(\"Delete my data\")"),
                      "Delete my data must be Settings' own section row")
        XCTAssertTrue(settings.contains("makeItMePresentation.alert = .confirmDeleteData"),
                      "its confirmation rides the surface's single alert slot")
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertFalse(section.contains("Text(\"Delete my data\")"),
                       "the Make It Me section must not host Delete my data (the D-14 coupling)")
        XCTAssertFalse(section.contains("confirmDeleteData"),
                       "the Make It Me section must not own the data-delete alert")
    }

    func testAboutCarriesTheRealStoreVersion_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("LabeledContent(\"Version\", value: appVersion)"))
        XCTAssertTrue(settings.contains("return \"\\(short) (\\(build))\""),
                      "the About value is the store version in \"name (build)\" form")
    }

    func testSectionViewRendersItsRowsUnderTheModelTitles_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        for row in ["notStarted", "yourPhotos", "premium", "manageSubscription", "deletePhotos"] {
            XCTAssertTrue(section.contains("MakeItMeSettingsRow.\(row).title"),
                          "the section must render the \(row) row under the model's title")
        }
    }

    // MARK: - GEN-3 (WS-D) · the redesigned Make It Me groups

    /// The redesign's binding order: heading/status group → Your
    /// photos group → Premium offer group → destructive group
    /// last. The composition's body must list the groups in that
    /// order (study §Redesign; gen-2's flat stack is the defect).
    func testGen3MakeItMeGroupsRenderInSpecOrder_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        let bodyStart = try XCTUnwrap(section.range(of: "var body: some View"))
        let bodyEnd = try XCTUnwrap(section.range(of: "private var statusSection"))
        let body = String(section[bodyStart.lowerBound..<bodyEnd.lowerBound])
        let groups = ["statusSection", "photosSection", "offerSection", "destructiveSection"]
        var lastIndex = -1
        for group in groups {
            let index = body.range(of: group)?.lowerBound.utf16Offset(in: body)
            XCTAssertNotNil(index, "the composition must render the \(group) group")
            if let index {
                XCTAssertGreaterThan(index, lastIndex, "\(group) is out of WS-D order")
                lastIndex = index
            }
        }
    }

    /// GEN-3 (Y-13): Settings' Fit Preference presents the three
    /// choices directly — the shared inline rows onboarding uses
    /// (W-22) — not a collapsed menu row. The menu-style picker is
    /// gone from the controls entirely, so no surface can drift
    /// back to it.
    func testGen3SettingsFitPreferencePresentsDirectChoices_sourcePin() throws {
        let settings = try sourceFile("../RunWear/Views/Modals/SettingsModal.swift")
        XCTAssertTrue(settings.contains("FitPreferenceInlinePicker(preference: $genderPreference)"),
                      "Y-13: the Settings Fit Preference section renders the direct-choice rows")
        let controls = try sourceFile("../RunWear/Views/Components/PreferenceControls.swift")
        XCTAssertFalse(controls.contains("struct FitPreferencePicker: View"),
                       "Y-13: the collapsed menu picker is removed — the inline rows are the one fit control")
        XCTAssertTrue(controls.contains("struct FitPreferenceInlinePicker: View"),
                      "the shared inline control stands")
    }

    /// The offer card must read as an offer (study P1): the name
    /// "Premium" (the model's row title), the price with term from
    /// the store product through the parameterized seam, and a
    /// real "Get Premium" button — plus the state-aware subscribed
    /// state for a subscriber.
    func testGen3OfferCardIsARealOffer_sourcePin() throws {
        let section = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(section.contains("MakeItMePremiumOfferCard"),
                      "the free state renders the dedicated offer card")
        XCTAssertTrue(section.contains("Text(\"Get Premium\")"),
                      "the card carries a real button that opens the paywall")
        XCTAssertTrue(section.contains("MakeItMePriceLabels.settingsOfferPriceLine(annualPriceString: billing.annualPriceString)"),
                      "the card's price is the fetched product's price — parameterized, never hardcoded")
        XCTAssertTrue(section.contains("Made ahead, without limit — every outfit is ready when you open the app."),
                      "the settled two-benefit copy stands on the card")
        XCTAssertTrue(section.contains("Text(\"You're subscribed.\")"),
                      "a subscriber sees the subscribed state instead of the offer")
        XCTAssertFalse(section.contains("less than paying monthly"),
                       "WS-F: the monthly comparison renders nowhere in Settings or the paywall")
        XCTAssertTrue(section.contains("store.billing.perRunFinePrint"),
                      "WS-F: the paywall's fine print derives from the displayed product's annual price")
    }
}

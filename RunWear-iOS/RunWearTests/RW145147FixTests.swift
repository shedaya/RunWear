import XCTest
import SwiftUI
import UIKit

/// RW-145 / RW-146 / RW-147 + RW-148 + RW-150 (fix pass,
/// Nov 9, 2026) — the iOS lane for Sol's device-pass findings,
/// as amended by the coordinator's two rulings:
///
/// - RW-145 + RULING 2 (hero proportion redesign): the hero
///   takes most of the first viewport (HeroHeightPolicy: 0.70
///   standard / 0.66 short) and its internal vertical stack —
///   chrome → date/time chip → feels-like block → boxless
///   weather line → Make It Me straddle pill — is composed by
///   ONE layout (HeroVerticalLayout) with guaranteed clear
///   gaps, so the build-33 collision (the chip seated ON the
///   FEELS LIKE label; the two-line pill covering the weather
///   line) is impossible by construction.
/// - RW-146: the displayed location truth — sheet marking and
///   the place pill derive from the COMMITTED place/source,
///   and the commit flips place + conditions together.
/// - RW-147: dock residence — no readable content under the
///   dock at rest, proven against the NEW geometry in the
///   portrait layouts and, as its own named case, in Duo.
/// - RW-148 (Ruling 1): ONE Make It Me counter — free looks
///   used out of FreeLooksAllowance.value (10) — read by every
///   surface; pins fail on any other allowance or source.
/// - RW-150: Wear I've Run narration grammar (RW-98 content,
///   RW-135 form) — the line names whichever condition changed,
///   in full, in every change class, from the one source both
///   the dock and the footer read.
///
/// Render half: production components composed as production
/// composes them, over a REAL runner photograph (the bundled
/// hero pool's mild_clear_male — a local image file, not the
/// RW-144 synthetic wash), written into `render-artefacts/`.
final class RW145147FixTests: XCTestCase {

    private func sourceFile(_ relativePath: String) throws -> String {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent(relativePath)
            .standardizedFileURL
        return try String(contentsOf: url, encoding: .utf8)
    }

    // MARK: - RW-145 + Ruling 2: the hero stack as one system

    func testRW145GapGuaranteeAcrossHeroGeometries() {
        // Every production hero geometry: short portrait
        // (667 × 0.66), mid (736 × 0.66), standard portrait
        // (852 × 0.70), a tall phone (876 × 0.70), and the Duo
        // pane at full height (834) — crossed with every pill
        // seat: no pill, the 44 pt single-line states, the
        // 62 pt two-line generating form (the build-33 defect's
        // tall pill), at the stacked offset (+straddleDip) and
        // the Duo pane offset (−duoPillBottomInset).
        for heroHeight in [440.22, 485.76, 596.4, 613.2, 834.0] {
            for pillHeight in [0.0, 44.0, 62.0] {
                for offset in [HeroVerticalLayout.straddleDip, -HeroVerticalLayout.duoPillBottomInset] {
                    let layout = HeroVerticalLayout.layout(
                        heroHeight: heroHeight, requestedNumeralSize: 72,
                        pillHeight: pillHeight, pillBottomOffset: offset)
                    XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5,
                        "hero \(heroHeight) pill \(pillHeight) offset \(offset): chip↔block gap \(layout.gap)")
                    // Block ↔ weather line: the composed chain
                    // holds blockWeatherGap exactly.
                    XCTAssertGreaterThanOrEqual(layout.weatherTop - layout.blockBottom,
                        HeroVerticalLayout.blockWeatherGap - 0.5,
                        "hero \(heroHeight) pill \(pillHeight): block bottom \(layout.blockBottom) vs weather top \(layout.weatherTop)")
                    // Weather line ↔ pill: the two-line pill can
                    // never cover the weather line (the build-33
                    // iOS defect Android prevents by its seat).
                    if let pillTop = layout.pillTop {
                        XCTAssertGreaterThanOrEqual(pillTop - layout.weatherBottom,
                            HeroVerticalLayout.weatherPillGap - 0.5,
                            "hero \(heroHeight) pill \(pillHeight): weather bottom \(layout.weatherBottom) vs pill top \(pillTop)")
                    } else {
                        XCTAssertEqual(pillHeight, 0)
                    }
                    // Face zone: the chip's VISUAL top clears the
                    // zone's foot; the hit top never goes above
                    // the 50 pt chrome floor.
                    XCTAssertGreaterThanOrEqual(layout.chipHitTop,
                        max(HeroVerticalLayout.minimumChipHitTop,
                            heroHeight * 0.30 + 2 - HeroVerticalLayout.chipVisualTopInset) - 0.5,
                        "hero \(heroHeight): chip hit top \(layout.chipHitTop) enters the face zone")
                    XCTAssertLessThanOrEqual(layout.numeralSize, 72)
                    XCTAssertGreaterThanOrEqual(layout.numeralSize, HeroVerticalLayout.minimumNumeralSize)
                }
            }
        }
    }

    func testRW145ShortHeroWorstStackHoldsTheFullNumeralAndTheGap() {
        // The hostile composition: the SHORTEST production
        // hero (iPhone SE class, 440 pt) with the TALLEST pill
        // (the 62 pt two-line generating form). The chain seats
        // the pill at 392, the weather line directly above it,
        // and the layout still affords the full 72 pt numeral
        // with the guaranteed 12 pt chip↔block gap.
        let layout = HeroVerticalLayout.layout(
            heroHeight: 440.22, requestedNumeralSize: 72,
            pillHeight: HeroPillMetrics.twoLineHeight,
            pillBottomOffset: HeroVerticalLayout.straddleDip)
        XCTAssertEqual(layout.pillTop ?? -1, 392.22, accuracy: 0.5)
        XCTAssertEqual(layout.numeralSize, 72, accuracy: 0.5)
        XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5)
    }

    func testRW145StandardHeroKeepsTheDefaultChipSeat() {
        // At the standard hero the v8 seat (chip at the default
        // 0.46 fraction, clear air above the block) is feasible
        // again — the layout takes it rather than pushing the
        // chip to its latest feasible seat.
        let layout = HeroVerticalLayout.layout(
            heroHeight: 596.4, requestedNumeralSize: 72,
            pillHeight: HeroPillMetrics.singleLineHeight,
            pillBottomOffset: HeroVerticalLayout.straddleDip)
        XCTAssertEqual(layout.chipHitTop, 596.4 * HeroChipPlacement.defaultTopFraction, accuracy: 0.5)
        XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap)
    }

    func testRW145BlockHeightIsMonotonicInNumeralSize() {
        var last = -1.0
        for size in stride(from: 36.0, through: 96.0, by: 12) {
            let h = HeroVerticalLayout.blockHeight(numeralSize: size)
            XCTAssertGreaterThan(h, last)
            last = h
        }
    }

    func testRuling2PillPlanningHeights() {
        // The layout's pill seats are planned from these
        // heights; the two-line generating/exhausted-class
        // forms are the tall ones the guarantee must cover.
        XCTAssertEqual(HeroPillMetrics.height(for: .hidden), 0)
        XCTAssertEqual(HeroPillMetrics.height(for: .making), HeroPillMetrics.singleLineHeight)
        XCTAssertEqual(HeroPillMetrics.height(for: .makingWithUpsell), HeroPillMetrics.twoLineHeight)
        XCTAssertEqual(HeroPillMetrics.height(for: .freeLook(used: 3, allowance: 10)),
                       HeroPillMetrics.singleLineHeight)
        XCTAssertEqual(HeroPillMetrics.height(for: .exhausted(used: 10, allowance: 10)),
                       HeroPillMetrics.singleLineHeight)
        // The straddle seat is bottom-anchored: a taller pill
        // grows UP into the hero's reserved space, never down
        // over the sheet's first line (the build-33 mechanism).
        XCTAssertEqual(HeroVerticalLayout.pillOffsetFromSheetTop(pillHeight: 62),
                       HeroVerticalLayout.straddleDip + HomeHandoffGeometry.sheetOverlap - 62,
                       accuracy: 0.5)
        XCTAssertLessThan(HeroVerticalLayout.pillOffsetFromSheetTop(pillHeight: 62),
                          HeroVerticalLayout.pillOffsetFromSheetTop(pillHeight: 44))
    }

    // MARK: - RW-147 (portrait): policy heights + at-rest proof

    func testRW147PolicyHeightsAndAtRestClearances() {
        // Ruling 2 proportions, by viewport class.
        XCTAssertEqual(HeroHeightPolicy.stackedHeight(viewportHeight: 852), 596.4, accuracy: 0.5)
        XCTAssertEqual(HeroHeightPolicy.stackedHeight(viewportHeight: 667), 440.22, accuracy: 0.5)
        XCTAssertEqual(HeroHeightPolicy.stackedHeight(viewportHeight: 736), 485.76, accuracy: 0.5)

        // Standard phone, pill present, no band: the outfit
        // header AND the first row rest fully clear of the
        // dock (header 646–668, first row bottom 740, dock top
        // 782 — 42 pt of clear air under the row).
        let dockTop852 = DockResidence.dockTop(
            viewportHeight: 852, dockHeight: DockResidence.measuredDockHeight)
        XCTAssertEqual(HeroHeightPolicy.firstRowBottom(
            viewportHeight: 852, pillVisible: true, bandPresent: false), 740.4, accuracy: 0.6)
        XCTAssertLessThanOrEqual(HeroHeightPolicy.firstRowBottom(
            viewportHeight: 852, pillVisible: true, bandPresent: false),
            dockTop852 - 12)

        // Short phone, pill present, no band: header + first
        // row still in the first viewport, clear of the dock
        // (first row bottom 584 vs dock top 597).
        let dockTop667 = DockResidence.dockTop(
            viewportHeight: 667, dockHeight: DockResidence.measuredDockHeight)
        XCTAssertLessThanOrEqual(HeroHeightPolicy.firstRowBottom(
            viewportHeight: 667, pillVisible: true, bandPresent: false),
            dockTop667)

        // Standard phone WITH the convergence band: the header
        // rides to the viewport's immediate edge — its bottom
        // (780) still holds the dock's top edge (782).
        let headerTopBand = HeroHeightPolicy.outfitHeaderTop(
            viewportHeight: 852, pillVisible: true, bandPresent: true)
        XCTAssertLessThanOrEqual(headerTopBand + HeroHeightPolicy.outfitHeaderHeight,
                                 dockTop852 + 0.5)

        // No pill: the sheet's reserve shrinks to the no-pill
        // form and everything rides higher.
        XCTAssertLessThan(HeroHeightPolicy.outfitHeaderTop(
            viewportHeight: 852, pillVisible: false, bandPresent: false),
            HeroHeightPolicy.outfitHeaderTop(
                viewportHeight: 852, pillVisible: true, bandPresent: false))

        // Wrapped-dock sensitivity (RW-138: the narration wraps
        // whole and the dock GROWS — the round-1 frame tests
        // failed on exactly this, asserting against a measured
        // wrapped dock with single-line arithmetic). At the
        // clearance-sized wrapped height (80 pt — the height
        // DockResidence.scrollEndClearance is sized for): the
        // standard phone's first row still rests fully clear
        // (740 vs dock top 758); on the short class the first
        // row's bottom grazes the dock band by at most 12 pt —
        // the quantified tension the lane report flags for
        // Sol's device pass, not a silent pass.
        let wrappedDockTop852 = DockResidence.dockTop(viewportHeight: 852, dockHeight: 80)
        XCTAssertLessThanOrEqual(HeroHeightPolicy.firstRowBottom(
            viewportHeight: 852, pillVisible: true, bandPresent: false), wrappedDockTop852)
        let wrappedDockTop667 = DockResidence.dockTop(viewportHeight: 667, dockHeight: 80)
        let shortOverhang = HeroHeightPolicy.firstRowBottom(
            viewportHeight: 667, pillVisible: true, bandPresent: false) - wrappedDockTop667
        XCTAssertLessThanOrEqual(shortOverhang, 12,
            "short-class wrapped-dock graze is \(shortOverhang) pt — beyond 12 pt this stops being a graze and the short ratio must be revisited")
    }

    func testRW147DockResidenceArithmetic() {
        XCTAssertEqual(DockResidence.dockTop(viewportHeight: 852, dockHeight: 56), 782, accuracy: 0.5)
        XCTAssertEqual(DockResidence.dockTop(viewportHeight: 834, dockHeight: 56), 764, accuracy: 0.5)
        // RE-PINNED for RW-152 §1 (Nov 9, 2026): the
        // footerTopContentY assertion that stood here pinned
        // the host's glide bookkeeping for the in-flow footer —
        // the footer and its bookkeeping are removed, and the
        // expanded panel is the overlay dock's own state, so
        // there is no footer-top arithmetic left to pin. The
        // RW-147 invariant below is untouched.
        // The scroll-end clearance is UNCHANGED by this pass:
        // it is what carries the glance band (and every other
        // sheet element) fully clear of the dock at scroll end,
        // in the stacked layout and the Duo column alike.
        XCTAssertEqual(DockResidence.scrollEndClearance, 80, accuracy: 0.5)
    }

    // MARK: - RW-147 (Duo): the expanded-layout dock residence,
    // its own named case (coordinator's report-structure note)

    @MainActor
    func testRW147DuoDockResidence() throws {
        // The Duo hero pane stands at the FULL viewport height
        // (834) with the pill overlaid at the pane's foot; the
        // internal stack holds the same guarantees there.
        let layout = HeroVerticalLayout.layout(
            heroHeight: 834, requestedNumeralSize: 72,
            pillHeight: HeroPillMetrics.singleLineHeight,
            pillBottomOffset: -HeroVerticalLayout.duoPillBottomInset)
        XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5)
        XCTAssertEqual(layout.pillTop ?? -1, 834 - 12 - 44, accuracy: 0.5)

        // The secondary column's readable content at rest —
        // outfit header + the 3-item outfit, the column's whole
        // initial content now that the weather line and the
        // in-flow pill live on the hero — ends far above the
        // full-width dock's top edge (764). What scrolls in the
        // column is carried clear at scroll end by the same
        // 80 pt clearance the portrait case pins.
        let columnContent = VStack(spacing: 0) {
            HStack {
                Text("3 items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            VStack(spacing: 16) {
                ForEach(fixtureItems()) { item in
                    FirstScreenOutfitRow(item: item, gender: .male)
                }
            }
            .padding(.top, 16)
        }
        let contentHeight = fittingHeight(columnContent, width: 500)
        let contentBottom = 20 + contentHeight // the column's top padding
        let dockTop = DockResidence.dockTop(
            viewportHeight: 834, dockHeight: DockResidence.measuredDockHeight)
        XCTAssertLessThanOrEqual(contentBottom, dockTop - 100,
            "Duo: secondary content bottom \(contentBottom) vs dock top \(dockTop)")
        print("RW147 Duo: content bottom=\(contentBottom) dockTop=\(dockTop) clearance=\(dockTop - contentBottom)")
    }

    // MARK: - RW-146: displayed truth (carried from round 1)

    override func setUp() {
        super.setUp()
        ManualLocationStore.clear()
    }

    override func tearDown() {
        ManualLocationStore.clear()
        super.tearDown()
    }

    @MainActor
    func testRW146DisplayedTruthStartsAsTheLaunchState() {
        let gpsVM = WeatherViewModel()
        XCTAssertTrue(gpsVM.displayedSourceIsGPS)
        XCTAssertEqual(gpsVM.displayedPlaceName, gpsVM.placeDisplayName)

        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let manualVM = WeatherViewModel()
        XCTAssertFalse(manualVM.displayedSourceIsGPS)
        XCTAssertEqual(manualVM.displayedPlaceName, "Phoenix, AZ")
    }

    @MainActor
    func testRW146PendingSwitchDoesNotMoveTheDisplayedTruth() {
        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let vm = WeatherViewModel()
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")

        // The tap: the SELECTION flips immediately…
        vm.switchToGPS()
        XCTAssertTrue(vm.isUsingGPS)
        // …but the DISPLAYED truth — what the sheet marks and
        // the pill names — still states the place on screen
        // until a GPS fetch commits. (The Android RW-146 (a)
        // defect was the marking following the tap-time flag.)
        XCTAssertFalse(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")

        // The commit flips place + source together.
        vm.commitDisplayedPlace(
            sourceIsGPS: true,
            geocodedPlace: SimplePlace(city: "New York", region: "New York"),
            pendingManualPlace: nil)
        XCTAssertTrue(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "New York, NY")
        XCTAssertEqual(vm.displayedPlaceName, vm.placeDisplayName,
                       "after the commit the displayed and pending names coincide")
    }

    @MainActor
    func testRW146ManualCommitKeepsThePickedPlaceWhenTheGeocodeFails() {
        let vm = WeatherViewModel()
        vm.commitDisplayedPlace(
            sourceIsGPS: false,
            geocodedPlace: nil,
            pendingManualPlace: SimplePlace(city: "Phoenix", region: "Arizona"))
        XCTAssertFalse(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")
        // A GPS commit with no geocode leaves the non-place label.
        vm.commitDisplayedPlace(sourceIsGPS: true, geocodedPlace: nil, pendingManualPlace: nil)
        XCTAssertTrue(vm.displayedSourceIsGPS)
        XCTAssertEqual(vm.displayedPlaceName, "Current Location")
    }

    func testRW146SheetMarkingHasExactlyOneTruth() {
        // GPS displayed: the GPS row is marked, no recent is.
        XCTAssertTrue(LocationSheetMarking.gpsRowIsCurrent(displayedSourceIsGPS: true))
        XCTAssertFalse(LocationSheetMarking.recentIsCurrent(
            recentName: "Phoenix, AZ", displayedSourceIsGPS: true, displayedPlaceName: nil))
        // Manual Phoenix displayed: the GPS row is unmarked and
        // exactly the Phoenix recent is marked.
        XCTAssertFalse(LocationSheetMarking.gpsRowIsCurrent(displayedSourceIsGPS: false))
        XCTAssertTrue(LocationSheetMarking.recentIsCurrent(
            recentName: "Phoenix, AZ", displayedSourceIsGPS: false, displayedPlaceName: "Phoenix, AZ"))
        XCTAssertFalse(LocationSheetMarking.recentIsCurrent(
            recentName: "Seattle, WA", displayedSourceIsGPS: false, displayedPlaceName: "Phoenix, AZ"))
    }

    // MARK: - RW-148: one counter, every surface

    func testRW148TheOneAllowanceIsTen() {
        XCTAssertEqual(FreeLooksAllowance.value, 10,
                       "RW-128/RW-148: the shared free allowance is 10 — any other value here fails every surface pin below by construction")
    }

    func testRW148EverySurfaceReportsTheSameCounter() throws {
        // Every user-visible surface, composed the production
        // way (allowance = the one shared constant), reports
        // the same used count against the same allowance of 10.
        for used in [0, 1, 3, 7, 10] {
            XCTAssertEqual(FreeLooksMeter.settingsLine(used: used, allowance: FreeLooksAllowance.value),
                           used >= 10 ? "All 10 free looks used" : "\(used) of 10 free looks used")
            XCTAssertTrue(FreeLooksMeter.subscriptionLine(used: used, allowance: FreeLooksAllowance.value)
                            .hasPrefix("\(used) of 10 used"))
            XCTAssertEqual(HeroPill.freeLookCountLabel(used: used, allowance: FreeLooksAllowance.value),
                           "\(used) of 10 looks")
            XCTAssertEqual(HeroPill.exhaustedCountLabel(used: used, allowance: FreeLooksAllowance.value),
                           "\(used) of 10 looks used")
            // The card's sentence carries the SAME used count
            // the pill beside it reports.
            XCTAssertTrue(FreeLooksMeter.upsellBodyLine(used: used)
                            .contains("in \(used) outfit"),
                          "the card and the pill must read the same counter (used \(used))")
        }
        // Singular/plural honesty in the card's sentence.
        XCTAssertTrue(FreeLooksMeter.upsellBodyLine(used: 1).contains("in 1 outfit."))
        XCTAssertTrue(FreeLooksMeter.upsellBodyLine(used: 7).contains("in 7 outfits."))
        // The first-look meter (onboarding's frame 34) reads
        // the shared counter too.
        let presentation = FirstLookPresentation.resolve(
            lookState: .ready, personalPhotoDisplayed: true, personalImageLoaded: true,
            freeGenerationsUsed: 7, freeAllowance: FreeLooksAllowance.value,
            isSubscribed: false)
        XCTAssertEqual(presentation.meterLine,
                       "That was look 7 of your 10 free looks — 3 remain, whenever you want them.")
    }

    func testRW148ExhaustedErrorWithoutACountFallsBackToTheOneAllowance() {
        // A server refusal that omits the count maps to the ONE
        // allowance — the pre-fix mapper invented 5 and the
        // store's catch wrote it into the shared counter.
        let noCount = PersonalHeroAPIError.mapped(
            data: Data(#"{"error":"allowance_exhausted"}"#.utf8), status: 403)
        XCTAssertEqual(noCount, .allowanceExhausted(freeGenerationsUsed: 10))
        // A server-sent count passes through untouched — the
        // shared counter IS the server's count.
        let withCount = PersonalHeroAPIError.mapped(
            data: Data(#"{"error":"allowance_exhausted","free_generations_used":7}"#.utf8), status: 403)
        XCTAssertEqual(withCount, .allowanceExhausted(freeGenerationsUsed: 7))
    }

    func testRW148NoSurfaceCarriesTheOldAllowance_sourcePin() throws {
        let models = try sourceFile("../RunWear/Models/PersonalHeroModels.swift")
        XCTAssertFalse(models.contains("?? 5"),
                       "the error mapper's invented-5 fallback is the RW-148 defect")
        XCTAssertFalse(models.contains("in 5 outfits"))
        let card = try sourceFile("../RunWear/Views/MakeItMeSettingsViews.swift")
        XCTAssertTrue(card.contains("FreeLooksMeter.upsellBodyLine(used: store.freeGenerationsUsed)"),
                      "the card's body composes from the store's one counter through the seam")
        XCTAssertFalse(card.contains("in 5 outfits"),
                       "the card's hardcoded 5 was the stale RW-148 surface")
        let store = try sourceFile("../RunWear/Services/PersonalHeroStore.swift")
        XCTAssertTrue(store.contains("static let freeAllowance = FreeLooksAllowance.value"),
                      "the store forwards the one shared constant — no second literal")
        // Both pill hosts pass the store's allowance constant.
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertTrue(home.contains("freeAllowance: PersonalHeroStore.freeAllowance"))
        let onboarding = try sourceFile("../RunWear/Views/Onboarding/OnboardingView.swift")
        XCTAssertTrue(onboarding.contains("freeAllowance: PersonalHeroStore.freeAllowance"))
    }

    // MARK: - RW-150: narration grammar per change class

    private func rw150Calendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }

    private func rw150Date(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0) -> Date {
        var components = DateComponents()
        components.year = year; components.month = month; components.day = day; components.hour = hour
        return rw150Calendar().date(from: components)!
    }

    private func rw150Entry(
        dayStart: Date,
        moment: MomentReading,
        payload: NarrationPayload?,
        wasUpdated: Bool
    ) -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: dayStart, placeName: "New York, NY",
            feelsLikeF: 49, actualF: 52, condition: "Clear",
            itemNames: ["Tee"], itemKeys: ["tee"],
            heroImageReference: "", sessionID: "s-rw150",
            wasUpdatedInSession: wasUpdated,
            momentReading: moment, updatePayload: payload)
    }

    /// The one narration source both the dock and the footer
    /// read (WearIveRunMiniBarModel.state → WearIveRunNarration
    /// .line) — the class pins below therefore pin both
    /// surfaces at once; the source pin at the end proves no
    /// surface composes its own line.
    private func rw150State(_ entry: WearIveRunEntry, now: Date) -> WearIveRunState {
        WearIveRunMiniBarModel.state(
            todaysEntry: entry, moment: entry.momentReading,
            wasUpdatedThisSession: entry.wasUpdatedInSession,
            now: now, calendar: rw150Calendar())
    }

    func testRW150SettledTodayClasses() {
        let now = rw150Date(2026, 11, 9, 12)
        let today = rw150Date(2026, 11, 9)

        // Class A — creation at Now (the recording's first
        // defect: build 33 read "Updated to New York, NY").
        let creation = rw150Entry(dayStart: today, moment: .now, payload: nil, wasUpdated: false)
        XCTAssertEqual(rw150State(creation, now: now),
                       .updated(payload: .placeAtMoment("New York, NY", .now)))
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(creation, now: now)),
                       "Updated to New York, NY, Now")

        // Class B — place change at a picked time today: the
        // line names the new state IN FULL (place + time).
        let placeAtTime = rw150Entry(
            dayStart: today, moment: .clock(hour12: 7, isPM: true),
            payload: .place("New York, NY"), wasUpdated: true)
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(placeAtTime, now: now)),
                       "Updated to New York, NY, 7 p.m.")

        // Class C — time-only change, same day: the line names
        // the moment that changed (RW-98's "Updated to
        // November 12 7 p.m." form).
        let timeOnly = rw150Entry(
            dayStart: today, moment: .clock(hour12: 7, isPM: true),
            payload: .moment(.clock(hour12: 7, isPM: true)), wasUpdated: true)
        XCTAssertEqual(rw150State(timeOnly, now: now),
                       .updated(payload: .moment(.clock(hour12: 7, isPM: true))))
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(timeOnly, now: now)),
                       "Updated to 7 p.m.")

        // Class D — return to Now: the full place + Now form
        // returns with the state it names.
        let backToNow = rw150Entry(
            dayStart: today, moment: .now,
            payload: .moment(.now), wasUpdated: true)
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(backToNow, now: now)),
                       "Updated to New York, NY, Now")
    }

    func testRW150PlannedClasses() {
        let now = rw150Date(2026, 11, 9, 12)
        let tomorrow = rw150Date(2026, 11, 10)

        // Class E — date change, future day, no picked time:
        // the RW-135 plan form stands as-is.
        let dateOnly = rw150Entry(dayStart: tomorrow, moment: .now, payload: nil, wasUpdated: false)
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(dateOnly, now: now)),
                       "Planned for New York, NY · Tomorrow")

        // Class F — date + time change (the recording's second
        // defect: build 33 read "Planned for New York, NY ·
        // Tomorrow" for a Tue Nov 10, 2:00 PM pick — the time
        // appeared nowhere). RW-98 governs content: the picked
        // time is named, in the RW-135 form.
        let dateAndTime = rw150Entry(
            dayStart: tomorrow, moment: .clock(hour12: 2, isPM: true),
            payload: nil, wasUpdated: false)
        XCTAssertEqual(rw150State(dateAndTime, now: now),
                       .planned(place: "New York, NY", dayLabel: "Tomorrow",
                                moment: .clock(hour12: 2, isPM: true)))
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(dateAndTime, now: now)),
                       "Planned for New York, NY · Tomorrow · 2 p.m.")

        // Class G — a legacy daypart entry (recorded before
        // RW-150 stamped clock readings) still narrates its
        // recorded reading whole.
        let legacy = rw150Entry(
            dayStart: tomorrow, moment: .daypart(dayWord: "Tomorrow", part: .afternoon),
            payload: nil, wasUpdated: false)
        XCTAssertEqual(WearIveRunNarration.line(for: rw150State(legacy, now: now)),
                       "Planned for New York, NY · Tomorrow afternoon")
    }

    func testRW150ShareMomentNamesTheEntrysOwnDay() {
        let calendar = rw150Calendar()
        let now = rw150Date(2026, 11, 9, 12)
        let clock = MomentReading.clock(hour12: 2, isPM: true)
        // Today: unchanged from the pre-fix forms.
        var forms = ShareMomentResolver.forms(
            momentReading: clock, dayStart: rw150Date(2026, 11, 9),
            lastSavedAt: now, now: now, calendar: calendar)
        XCTAssertEqual(forms.caption, "today, 2:00 PM")
        XCTAssertEqual(forms.poster, "Today, 2:00 PM")
        // Tomorrow: the pre-fix seat said "today" for ANY day —
        // a falsehood the moment future-day entries carry
        // clock readings.
        forms = ShareMomentResolver.forms(
            momentReading: clock, dayStart: rw150Date(2026, 11, 10),
            lastSavedAt: now, now: now, calendar: calendar)
        XCTAssertEqual(forms.caption, "tomorrow, 2:00 PM")
        XCTAssertEqual(forms.poster, "Tomorrow, 2:00 PM")
        // Further out (Thu Nov 12, 2026): the weekday names it.
        forms = ShareMomentResolver.forms(
            momentReading: clock, dayStart: rw150Date(2026, 11, 12),
            lastSavedAt: now, now: now, calendar: calendar)
        XCTAssertEqual(forms.caption, "thursday, 2:00 PM")
        XCTAssertEqual(forms.poster, "Thursday, 2:00 PM")
    }

    func testRW150NarrationLivesAtTheOneSource_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        // A picked hour records the CLOCK reading on any day —
        // the exact time reaches the entry, and §7.0's pure
        // function of the entry can therefore name it. (The
        // pre-fix daypart stamp is what made the picked time
        // unrecoverable downstream.)
        XCTAssertTrue(home.contains("return .clock(hour12: display, isPM: hour >= 12)"))
        XCTAssertFalse(home.contains(".daypart(dayWord:"),
                       "no daypart construction may stamp an entry moment again")
        // No surface composes its own narration line.
        XCTAssertFalse(home.contains("\"Updated to"))
        XCTAssertFalse(home.contains("\"Planned for"))
    }

    // MARK: - Shop removal + sheet order (Amendment 1, item 4)

    func testShopButtonRemovedAndWeatherLineLivesOnTheHero_sourcePin() throws {
        let home = try sourceFile("../RunWear/Views/WeatherOutfitView.swift")
        XCTAssertFalse(home.contains("shopEntryButton"),
                       "the v8 header is the count line alone — the Shop button was removed on Android in RW-144 and no later ruling restores it")
        XCTAssertFalse(home.contains("showShop"))
        XCTAssertFalse(home.contains("BoxlessWeatherLineView("),
                       "the weather line no longer opens the sheet — it rides the hero (Ruling 2)")
        XCTAssertTrue(home.contains("sheetTopReserveWithPill"),
                      "the sheet's first seat is the straddle reserve the pill's bottom-anchored seat grows into")
        let hero = try sourceFile("../RunWear/Views/FirstScreenHero.swift")
        XCTAssertTrue(hero.contains("BoxlessWeatherLineView("))
        XCTAssertTrue(hero.contains("style: .hero"),
                      "the hero's weather line renders in the hero (cream) style over the photograph")
    }

    // MARK: - Render harness

    private func artefactsDirectory() -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../render-artefacts", isDirectory: true)
            .standardizedFileURL
    }

    @MainActor
    private func writeRender<Content: View>(_ content: Content, size: CGSize, name: String) throws {
        let renderer = ImageRenderer(content: content.frame(width: size.width, height: size.height))
        renderer.scale = 3
        let image = try XCTUnwrap(renderer.uiImage, "render failed for \(name)")
        let dir = artefactsDirectory()
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let data = try XCTUnwrap(image.pngData(), "PNG encoding failed for \(name)")
        try data.write(to: dir.appendingPathComponent(name))
        print("RW145147 wrote \(name)")
    }

    @MainActor
    private func fittingHeight<Content: View>(_ content: Content, width: CGFloat) -> CGFloat {
        let renderer = ImageRenderer(content: content)
        renderer.scale = 3
        renderer.proposedSize = ProposedViewSize(width: width, height: nil)
        return renderer.uiImage?.size.height ?? 0
    }

    /// The REAL runner photograph: the bundled hero pool's
    /// mild_clear_male (800×1200 WebP), loaded from its local
    /// file — the workaround for the harness's remote-thumbnail
    /// limitation, and a visible runner subject for the chip's
    /// face-zone seat (the RW-144 synthetic wash had neither).
    private func realRunnerPhoto() throws -> UIImage {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("../RunWear/hero_fallback/mild_clear_male.webp")
            .standardizedFileURL
        let image = try XCTUnwrap(UIImage(contentsOfFile: url.path),
                                  "the bundled real runner photo must decode in the harness")
        XCTAssertGreaterThan(image.size.width, 100)
        return image
    }

    private func fixtureItems() -> [ClothingItem] {
        [
            ClothingItem(key: "SHORT_SLEEVE", name: "Short Sleeve Shirt",
                         description: "Moisture-wicking technical fabric",
                         icon: "tshirt", amazonSearchTerm: "short sleeve running shirt"),
            ClothingItem(key: "SHORTS", name: "Running Shorts (5-7\u{2033})",
                         description: "Standard running shorts",
                         icon: "figure.run", amazonSearchTerm: "running shorts"),
            ClothingItem(key: "REFLECTIVE_GEAR", name: "Reflective Gear",
                         description: "Visibility for low-light",
                         icon: "exclamationmark.triangle", amazonSearchTerm: "reflective running gear"),
        ]
    }

    private func fixtureEntry() -> WearIveRunEntry {
        WearIveRunEntry(
            dayStart: Date(timeIntervalSince1970: 1_798_000_000),
            placeName: "Phoenix, AZ", feelsLikeF: 58, actualF: 60, condition: "Clear sky",
            itemNames: ["Short Sleeve Shirt", "Running Shorts (5-7\u{2033})", "Reflective Gear"],
            itemKeys: ["SHORT_SLEEVE", "SHORTS", "REFLECTIVE_GEAR"],
            heroImageReference: "", windMph: 0, dayLowF: 55, dayHighF: 63,
            faceAnchor: nil)
    }

    /// The hero as production composes it under Ruling 2: the
    /// real photograph + the chrome overlay carrying the
    /// weather line and the pill's planning seat.
    private func heroWithRealPhoto(
        photo: UIImage, width: CGFloat, height: CGFloat,
        pillState: HeroPillState, pillBottomOffset: CGFloat
    ) -> some View {
        let top = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.topRegion)
        let chip = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.chipRegion)
        let foot = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.footRegion)
        let weather = HeroLuminanceSampler.meanLuminance(of: photo, region: HeroLuminanceSampler.weatherRegion)
        print("RW145147 real-photo luminance top=\(String(format: "%.3f", top)) chip=\(String(format: "%.3f", chip)) foot=\(String(format: "%.3f", foot)) weather=\(String(format: "%.3f", weather))")
        return ZStack {
            Image(uiImage: photo)
                .resizable()
                .scaledToFill()
                .frame(width: width, height: height)
                .clipped()
            HeroChromeOverlayView(
                placeName: "Phoenix, AZ",
                dateText: "Sun, Nov 8",
                timeText: "Now",
                isNow: true,
                formattedFeelsLike: "58°",
                formattedActual: "60°",
                feelsLikeF: 58,
                topLuminance: top, chipLuminance: chip, footLuminance: foot,
                onPlaceTapped: {}, onSettingsTapped: {},
                onDateTapped: {}, onTimeTapped: {}, onNowTapped: {},
                windMph: 0, rainPercent: 0, humidityPercent: 60, uvIndex: 0,
                weatherLuminance: weather,
                pillHeight: HeroPillMetrics.height(for: pillState),
                pillBottomOffset: pillBottomOffset
            )
        }
        .frame(width: width, height: height)
    }

    /// The sheet's head as production composes it now: the
    /// straddle reserve (the pill's bottom-anchored seat grows
    /// up into it), then the outfit header — the count line
    /// ALONE (no Shop button) — then the rows.
    private func sheetStack(pillState: HeroPillState) -> some View {
        VStack(spacing: 0) {
            Color.clear
                .frame(height: pillState == .hidden
                    ? HeroVerticalLayout.sheetTopReserveNoPill
                    : HeroVerticalLayout.sheetTopReserveWithPill)
            HStack {
                Text("3 items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            VStack(spacing: 16) {
                ForEach(fixtureItems()) { item in
                    FirstScreenOutfitRow(item: item, gender: .male)
                }
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }
        .background {
            UnevenRoundedRectangle(cornerRadii: RectangleCornerRadii(topLeading: 28, topTrailing: 28))
                .fill(AppTheme.sheet)
        }
        .overlay(alignment: .top) {
            if pillState != .hidden {
                HeroPillView(pillState: pillState, seat: .straddle, onPillGetUnlimited: {})
                    .offset(y: HeroVerticalLayout.pillOffsetFromSheetTop(
                        pillHeight: HeroPillMetrics.height(for: pillState)))
            }
        }
    }

    private func dock() -> WearIveRunMiniBarView {
        WearIveRunMiniBarView(
            state: .updated(payload: .placeAtMoment("Phoenix, AZ", .now)),
            entry: fixtureEntry(),
            formattedFeelsLike: "58°F", cueCount: 0,
            onOpenCollection: {}, onTrash: {})
    }

    // MARK: - Frames

    /// (1) Short portrait (iPhone SE class, 375×667), Now,
    /// 3-item outfit at rest, real photo, in the build-33
    /// defect's own state — the two-line generating pill. The
    /// pill now rides the hero's foot over its reserved space;
    /// the weather line sits above it on the hero, whole.
    @MainActor
    func testRW145147ShortPortraitFrame() throws {
        let viewport = CGSize(width: 375, height: 667)
        let heroHeight = HeroHeightPolicy.stackedHeight(viewportHeight: viewport.height)
        let pillState = HeroPillState.makingWithUpsell
        let layout = HeroVerticalLayout.layout(
            heroHeight: heroHeight, requestedNumeralSize: 72,
            pillHeight: HeroPillMetrics.height(for: pillState),
            pillBottomOffset: HeroVerticalLayout.straddleDip)
        XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5,
            "SE hero \(heroHeight): chip↔block gap \(layout.gap) (numeral \(layout.numeralSize))")

        let photo = try realRunnerPhoto()
        let screen = ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                heroWithRealPhoto(photo: photo, width: viewport.width, height: heroHeight,
                                  pillState: pillState,
                                  pillBottomOffset: HeroVerticalLayout.straddleDip)
                sheetStack(pillState: pillState)
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
                    .frame(maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-1-short-portrait-rest-light.png")
        print("RW147 short portrait: hero=\(heroHeight) numeral=\(layout.numeralSize) gap=\(layout.gap)")
    }

    /// (2) Standard phone height (393×852), Now, 3-item outfit
    /// at rest, the free-look pill (3 of 10 — the RW-148
    /// counter in its production form). (The glance band is
    /// not composed in this frame: under the new proportions
    /// its at-rest seat is below the fold — its clearance is
    /// the arithmetic pinned in testRW147PolicyHeightsAndAtRest
    /// Clearances plus the unchanged scroll-end clearance.)
    @MainActor
    func testRW145147StandardPortraitFrame() throws {
        let viewport = CGSize(width: 393, height: 852)
        let heroHeight = HeroHeightPolicy.stackedHeight(viewportHeight: viewport.height)
        let pillState = HeroPillState.freeLook(used: 3, allowance: FreeLooksAllowance.value)
        let photo = try realRunnerPhoto()
        let screen = ZStack(alignment: .bottom) {
            VStack(spacing: 0) {
                heroWithRealPhoto(photo: photo, width: viewport.width, height: heroHeight,
                                  pillState: pillState,
                                  pillBottomOffset: HeroVerticalLayout.straddleDip)
                sheetStack(pillState: pillState)
                    .padding(.top, -HomeHandoffGeometry.sheetOverlap)
                    .frame(maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-2-standard-portrait-rest-light.png")
        print("RW147 standard: hero=\(heroHeight)")
    }

    /// (3) Duo / expanded layout (1194×834) — RW-147's own
    /// named case: the hero pane stands at full height with
    /// the exhausted pill overlaid at its foot, the secondary
    /// column opens with the outfit (the weather line and the
    /// pill live on the hero now), and the full-width dock
    /// rests over clear space in both panes.
    @MainActor
    func testRW145147DuoFrame() throws {
        let viewport = CGSize(width: 1194, height: 834)
        let pillState = HeroPillState.exhausted(used: 10, allowance: FreeLooksAllowance.value)
        let layout = HeroVerticalLayout.layout(
            heroHeight: viewport.height, requestedNumeralSize: 72,
            pillHeight: HeroPillMetrics.height(for: pillState),
            pillBottomOffset: -HeroVerticalLayout.duoPillBottomInset)
        XCTAssertGreaterThanOrEqual(layout.gap, HeroVerticalLayout.minimumGap - 0.5)

        let photo = try realRunnerPhoto()
        let secondary = VStack(spacing: 0) {
            HStack {
                Text("3 items for your run")
                    .font(.appSectionTitle)
                    .foregroundColor(AppTheme.inkPrimary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 20)
            VStack(spacing: 16) {
                ForEach(fixtureItems()) { item in
                    FirstScreenOutfitRow(item: item, gender: .male)
                }
            }
            .padding(.top, 16)
            Spacer(minLength: 0)
        }

        let screen = ZStack(alignment: .bottom) {
            HStack(alignment: .top, spacing: 24) {
                heroWithRealPhoto(photo: photo, width: 550, height: viewport.height,
                                  pillState: pillState,
                                  pillBottomOffset: -HeroVerticalLayout.duoPillBottomInset)
                    .overlay(alignment: .bottom) {
                        HeroPillView(pillState: pillState, seat: .straddle, onPillGetUnlimited: {})
                            .padding(.bottom, HeroVerticalLayout.duoPillBottomInset)
                    }
                secondary
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            dock()
                .padding(.horizontal, DockResidence.horizontalInset)
                .padding(.bottom, DockResidence.bottomInset)
        }
        .background(AppTheme.background)
        .environment(\.colorScheme, .light)
        try writeRender(screen, size: viewport, name: "RW147-3-duo-rest-light.png")
        print("RW147 Duo: hero=\(viewport.height) gap=\(layout.gap)")
    }

    /// (4) RW-146 sheet sequence: the location sheet in the
    /// three states of a GPS switch — GPS displayed; manual
    /// Phoenix displayed; GPS requested but not yet committed
    /// (the displayed truth, and so the sheet, still states
    /// Phoenix). Frames (b) and (c) carry identical inputs by
    /// design: that identity IS the fix.
    @MainActor
    func testRW146SheetSequenceFrames() throws {
        // Seed the recents the sheet lists (Phoenix + Seattle).
        let store = RecentLocationsStore()
        store.record(RecentLocation(name: "Seattle, WA", latitude: 47.6062, longitude: -122.3321))
        store.record(RecentLocation(name: "Phoenix, AZ", latitude: 33.4484, longitude: -112.0740))
        defer {
            UserDefaults.standard.removeObject(forKey: "location.recents.v1")
        }

        // (a) GPS displayed.
        let gpsSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: true,
            currentPlaceName: nil,
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(gpsSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-a-sheet-gps-displayed.png")

        // (b) Manual Phoenix displayed.
        let manualSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: false,
            currentPlaceName: "Phoenix, AZ",
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(manualSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-b-sheet-manual-displayed.png")

        // (c) The switch tapped, not yet committed: drive the
        // sheet from a real view model's DISPLAYED truth after
        // switchToGPS() — the inputs are (b)'s, proven in
        // testRW146PendingSwitchDoesNotMoveTheDisplayedTruth.
        ManualLocationStore.save(
            ManualLocationStore.Saved(latitude: 33.4484, longitude: -112.0740, name: "Phoenix, AZ"))
        let vm = WeatherViewModel()
        vm.switchToGPS()
        XCTAssertEqual(vm.displayedPlaceName, "Phoenix, AZ")
        let pendingSheet = LocationPickerSheet(
            isPresented: .constant(true),
            isUsingGPS: vm.displayedSourceIsGPS,
            currentPlaceName: vm.displayedSourceIsGPS ? nil : vm.displayedPlaceName,
            onUseCurrentLocation: {}, onSelectLocation: { _, _ in })
        try writeRender(pendingSheet, size: CGSize(width: 393, height: 560),
                        name: "RW146-c-sheet-switch-pending.png")
    }
}

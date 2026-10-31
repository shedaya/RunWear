import XCTest

// App Store screenshot capture — drives the REAL app on the
// simulator through the network-free-of-GPS manual-city path
// (onboarding "Set a City Instead" -> CLGeocoder -> live Open-Meteo
// weather -> live hero pool), and keeps a PNG attachment at each
// listing frame. The drive mirrors what a first-run user does; no
// debug hooks, no seeded fake data — the weather, hero photo, and
// outfit on screen are the app's real output for the chosen city.
//
// Attachments are exported from the .xcresult by the
// ios-screenshots workflow. Nothing here uploads anywhere; the
// parent reviews the frames before they go to App Store Connect.
final class ScreenshotTests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = true
        app = XCUIApplication()
        app.launch()
    }

    override func tearDownWithError() throws {
        app = nil
    }

    // ── Helpers ──────────────────────────────────────────────────

    private func dump(_ stage: String) {
        print("UITEST-DUMP \(stage)")
        for line in app.debugDescription.split(separator: "\n") {
            print("UITEST | \(line)")
        }
    }

    @discardableResult
    private func waitFor(_ element: XCUIElement, _ what: String, timeout: TimeInterval = 20) -> Bool {
        if !element.waitForExistence(timeout: timeout) {
            dump("missing: \(what)")
            print("UITEST-MISSING \(what)")
            return false
        }
        return true
    }

    private func tapIfExists(_ element: XCUIElement, timeout: TimeInterval = 4) -> Bool {
        guard element.waitForExistence(timeout: timeout), element.isHittable else {
            return false
        }
        element.tap()
        return true
    }

    private func scrollTo(_ element: XCUIElement, _ what: String, maxSwipes: Int = 6) -> Bool {
        for _ in 0..<maxSwipes {
            if element.exists && element.isHittable { return true }
            app.swipeUp()
            Thread.sleep(forTimeInterval: 0.6)
        }
        if element.exists && element.isHittable { return true }
        dump("cannot scroll to: \(what)")
        return false
    }

    private func shot(_ name: String) {
        // Let the screen settle (hero image, sheet dismissal,
        // staggered card animations).
        Thread.sleep(forTimeInterval: 1.2)
        let shot = XCUIScreen.main.screenshot()
        let attachment = XCTAttachment(screenshot: shot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        print("UITEST-SHOT \(name)")
    }

    // ── The capture run ──────────────────────────────────────────

    func testCaptureListingSet() throws {
        // Onboarding: single scroll screen. Choose the manual-city
        // path so no location-permission alert is involved.
        waitFor(app.staticTexts["Welcome to RunWear"], "onboarding welcome")
        let setCity = app.buttons["Set a City Instead"]
        XCTAssertTrue(scrollTo(setCity, "Set a City Instead"),
                      "onboarding location choice not reachable")
        setCity.tap()

        let getStarted = app.buttons["Get Started"]
        waitFor(getStarted, "Get Started")
        getStarted.tap()

        // ContentView opens the city picker directly (the onboarding
        // choice is honored on first launch).
        let field = app.textFields["Enter city or ZIP code"]
        waitFor(field, "city search field", timeout: 30)
        field.tap()
        field.typeText("New York")

        // CLGeocoder results are buttons labelled with the place
        // name + region. Tap the first New York result.
        let result = app.buttons.matching(
            NSPredicate(format: "label CONTAINS %@", "New York")).firstMatch
        waitFor(result, "geocode result for New York", timeout: 45)
        result.tap()

        // Live weather + recommendation. Generous timeout: the
        // fetch chain is Open-Meteo + hero pool over the network.
        let outfitHeader = app.staticTexts["Recommended Outfit"]
        let loaded = waitFor(outfitHeader, "recommended outfit", timeout: 90)
        XCTAssertTrue(loaded, "recommendation never loaded")
        // Hero photo + thumbnails settle.
        Thread.sleep(forTimeInterval: 5)

        // 01 — Main recommendation: hero + outfit for a run, now.
        shot("01-main-recommendation")

        // 02 — A different day: the hero date pill ("Today") opens
        // the 7-day picker; choosing Tomorrow re-plans the outfit
        // against tomorrow's forecast.
        let datePill = app.buttons["Today"]
        if tapIfExists(datePill, timeout: 6) {
            if waitFor(app.staticTexts["Select Date"], "date picker sheet", timeout: 10) {
                let tomorrow = app.buttons.matching(
                    NSPredicate(format: "label CONTAINS %@", "Tomorrow")).firstMatch
                if tapIfExists(tomorrow, timeout: 10) {
                    // The pill relabels to "Tomorrow" once the new
                    // day's plan is on screen.
                    _ = app.buttons["Tomorrow"].waitForExistence(timeout: 30)
                    Thread.sleep(forTimeInterval: 4)
                    shot("02-tomorrow-forecast")
                } else {
                    dump("no Tomorrow cell")
                }
            }
        } else {
            dump("no date pill")
        }

        // 03 — The outfit itself: scroll the recommendation list so
        // the item cards (and tips, when present) fill the frame.
        app.swipeUp()
        Thread.sleep(forTimeInterval: 0.8)
        app.swipeUp()
        Thread.sleep(forTimeInterval: 2)
        shot("03-outfit-items")
        app.swipeDown()
        Thread.sleep(forTimeInterval: 0.8)
        app.swipeDown()
        // A pull-to-refresh may have fired; let it settle.
        Thread.sleep(forTimeInterval: 3)

        // 04 — Shop sheet: the outfit as a shopping list.
        let shopButton = app.buttons["Shop"]
        if tapIfExists(shopButton, timeout: 6) {
            if waitFor(app.staticTexts["Shop Gear"], "shop sheet", timeout: 15) {
                Thread.sleep(forTimeInterval: 2)
                shot("04-shop-gear")
                _ = tapIfExists(app.buttons["Close"], timeout: 4)
                Thread.sleep(forTimeInterval: 1)
            }
        } else {
            dump("no Shop toolbar button")
        }

        // 05 — Settings: the preference surface (units, fit,
        // comfort) that personalizes the recommendation.
        let settingsButton = app.buttons["Settings"]
        if tapIfExists(settingsButton, timeout: 6) {
            if app.staticTexts["Temperature Unit"].waitForExistence(timeout: 10) {
                Thread.sleep(forTimeInterval: 1.5)
                shot("05-settings")
                _ = tapIfExists(app.buttons["Done"], timeout: 4)
            }
        } else {
            dump("no Settings toolbar button")
        }
    }
}

import XCTest

/// App Store screenshot capture for RunWear iOS (build 1.0 (8)).
///
/// Drives the real app the way a first-run user goes: onboarding with
/// the manual-city path (no location-permission prompt), pick
/// "New York" from the geocoder results, let live Open-Meteo weather
/// load, then walk the main recommendation, the tomorrow forecast,
/// the outfit detail, the shop sheet, and settings.
///
/// Frame 1 is a hard gate: if the recommendation never renders, the
/// test fails so no bad set can ship. Later frames are best-effort
/// (JetGuide harness pattern) but every checkpoint leaves a stage
/// screenshot AND a hierarchy text attachment either way — prints do
/// not reach the CI log on this toolchain, attachments do.
final class ScreenshotTests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = true
    }

    private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
        print("UITEST-SHOT \(name)")
    }

    /// Stage evidence that survives export: a PNG named `stage-*` plus
    /// the full accessibility hierarchy as a text attachment.
    private func stage(_ app: XCUIApplication, _ name: String) {
        shot(app, name)
        let text = XCTAttachment(string: app.debugDescription)
        text.name = name + "-hierarchy"
        text.lifetime = .keepAlways
        add(text)
    }

    @discardableResult
    private func waitFor(_ element: XCUIElement, _ timeout: TimeInterval, _ label: String) -> Bool {
        let ok = element.waitForExistence(timeout: timeout)
        print("UITEST-WAIT \(label): \(ok ? "found" : "MISSING")")
        return ok
    }

    /// Type the city, wait for the geocoder results, and tap the first
    /// "New York" result. CLGeocoder cancels the in-flight request on
    /// every keystroke, so a typed burst can come back empty on a slow
    /// network — in that case do what a user would: clear the field
    /// and type it again. Returns true once a result was tapped.
    private func pickNewYork(_ app: XCUIApplication, attempt: Int) -> Bool {
        let field = app.textFields["Enter city or ZIP code"]
        guard waitFor(field, 30, "city field (attempt \(attempt))") else { return false }
        field.tap()
        if let value = field.value as? String, !value.isEmpty, value != "Enter city or ZIP code" {
            // Clear any partial text from a previous attempt.
            field.doubleTap()
            app.keys["Select All"].tap()
            app.menuItems["Cut"].tap()
        }
        field.typeText("New York")
        let result = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@", "New York")
        ).firstMatch
        if waitFor(result, 60, "geocode result (attempt \(attempt))") {
            result.tap()
            return true
        }
        stage(app, "stage-picker-no-results-\(attempt)")
        return false
    }

    func testCaptureListingSet() throws {
        let app = XCUIApplication()
        app.launch()

        // --- Onboarding: single scroll screen. The manual-city button
        // sits in the Location section near the bottom.
        let setCity = app.buttons["Set a City Instead"]
        var hops = 0
        while !setCity.exists || !setCity.isHittable {
            app.swipeUp()
            hops += 1
            if hops > 8 { break }
        }
        stage(app, "stage-01-onboarding")
        XCTAssertTrue(setCity.waitForExistence(timeout: 10), "Set a City Instead never appeared")
        setCity.tap()

        let getStarted = app.buttons["Get Started"]
        XCTAssertTrue(getStarted.waitForExistence(timeout: 10), "Get Started never appeared")
        getStarted.tap()

        // --- Location picker auto-opens. Pick New York (one retry).
        var picked = pickNewYork(app, attempt: 1)
        if !picked { picked = pickNewYork(app, attempt: 2) }
        XCTAssertTrue(picked, "Could not pick a geocoded city")
        stage(app, "stage-02-after-pick")

        // --- The recommendation itself. ContentView shows it once
        // weather + recommendation exist; in the notDetermined state
        // with nothing loaded it shows the permission view instead, so
        // wait in slices and capture the mid-state for diagnosis.
        let outfit = app.staticTexts["Recommended Outfit"]
        var loaded = outfit.waitForExistence(timeout: 60)
        if !loaded {
            stage(app, "stage-03-mid-load")
            loaded = outfit.waitForExistence(timeout: 60)
        }
        if !loaded {
            // One recovery pass exactly as a user would do it: the
            // permission view offers "Set Location Manually".
            let manual = app.buttons["Set Location Manually"]
            if manual.exists {
                manual.tap()
                if pickNewYork(app, attempt: 3) {
                    loaded = outfit.waitForExistence(timeout: 90)
                }
            }
        }
        stage(app, "stage-04-main-state")
        XCTAssertTrue(loaded, "Recommended Outfit never loaded")
        // Let the hero image finish so frame 1 is not a loading state.
        _ = app.staticTexts["Feels Like"].waitForExistence(timeout: 20)
        Thread.sleep(forTimeInterval: 2)
        shot(app, "01-main-recommendation")

        // --- Frame 2: tomorrow's forecast. The hero date pill reads
        // "Today" and opens the 7-day picker.
        let pill = app.buttons["Today"]
        if waitFor(pill, 10, "date pill") {
            pill.tap()
            let tomorrow = app.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] %@", "Tomorrow")
            ).firstMatch
            if waitFor(tomorrow, 10, "tomorrow cell") {
                tomorrow.tap()
                _ = app.buttons.matching(
                    NSPredicate(format: "label CONTAINS[c] %@", "Tomorrow")
                ).firstMatch.waitForExistence(timeout: 25)
                Thread.sleep(forTimeInterval: 2)
                shot(app, "02-tomorrow-forecast")
            } else {
                stage(app, "stage-05-date-picker")
            }
        }

        // --- Back to Today for the detail frame (the picker selection
        // sticks; reopen and choose the Today cell if present).
        let tomorrowPill = app.buttons.matching(
            NSPredicate(format: "label CONTAINS[c] %@", "Tomorrow")
        ).firstMatch
        if tomorrowPill.exists {
            tomorrowPill.tap()
            let todayCell = app.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] %@", "Today")
            ).firstMatch
            if waitFor(todayCell, 10, "today cell") {
                todayCell.tap()
                _ = app.buttons["Today"].waitForExistence(timeout: 20)
            }
        }

        // --- Frame 3: outfit items. Scroll the recommendation list
        // until the top item rows are on screen.
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1)
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1)
        shot(app, "03-outfit-items")

        // --- Frame 4: shop sheet.
        app.swipeDown()
        app.swipeDown()
        let shop = app.buttons["Shop"]
        if waitFor(shop, 10, "shop button") {
            shop.tap()
            _ = app.staticTexts["Shop Gear"].waitForExistence(timeout: 20)
            Thread.sleep(forTimeInterval: 2)
            shot(app, "04-shop-gear")
            let close = app.buttons["Close"]
            if close.exists { close.tap() }
        } else {
            stage(app, "stage-06-no-shop")
        }

        // --- Frame 5: settings.
        let settings = app.buttons["Settings"]
        if waitFor(settings, 10, "settings button") {
            settings.tap()
            _ = app.staticTexts["Temperature Unit"].waitForExistence(timeout: 15)
            Thread.sleep(forTimeInterval: 1)
            shot(app, "05-settings")
            let done = app.buttons["Done"]
            if done.exists { done.tap() }
        } else {
            stage(app, "stage-07-no-settings")
        }
    }
}

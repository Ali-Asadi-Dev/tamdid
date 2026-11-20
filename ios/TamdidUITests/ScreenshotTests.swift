import XCTest

/// Walks the main screens with seeded data and attaches screenshots (kept by CI as artifacts).
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    private func launch(language: String, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-sample-data", language, "-AppleLanguages", "(\(language))", "-AppleLocale", language == "fa" ? "fa_IR" : "en_US"] + extra
        app.launch()
        return app
    }

    private func snap(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testEnglishTour() {
        let app = launch(language: "en")
        XCTAssertTrue(app.staticTexts["monthlyTotal"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["monthlyTotal"].label, "$94.76")
        snap("en-1-home")
        let tabs = app.tabBars.buttons
        tabs.element(boundBy: 1).tap(); snap("en-2-subscriptions")
        app.cells.firstMatch.tap(); snap("en-3-detail")
        tabs.element(boundBy: 2).tap(); snap("en-4-calendar")
        tabs.element(boundBy: 3).tap(); snap("en-5-insights")
        tabs.element(boundBy: 4).tap(); snap("en-6-settings")
    }

    func testPersianTour() {
        let app = launch(language: "fa")
        XCTAssertTrue(app.staticTexts["monthlyTotal"].waitForExistence(timeout: 5))
        snap("fa-1-home")
        let tabs = app.tabBars.buttons
        tabs.element(boundBy: 2).tap(); snap("fa-4-calendar")
        tabs.element(boundBy: 3).tap(); snap("fa-5-insights")
    }

    func testAddSubscription() {
        let app = launch(language: "en", extra: ["-empty"])
        app.buttons["add"].firstMatch.tap()
        app.staticTexts["YouTube Premium"].tap()
        let price = app.textFields["price"]
        XCTAssertTrue(price.waitForExistence(timeout: 3))
        price.tap()
        price.typeText("13.99")
        snap("en-7-edit")
        app.buttons["save"].tap()
        XCTAssertTrue(app.staticTexts["monthlyTotal"].waitForExistence(timeout: 3))
        XCTAssertEqual(app.staticTexts["monthlyTotal"].label, "$13.99")
    }
}

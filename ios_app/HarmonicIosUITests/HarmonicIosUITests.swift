import XCTest
import UIKit

final class HarmonicIosUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
        ]
        app.launchEnvironment["HARMONIC_UI_TESTING"] = "1"
        app.launch()
        completeFirstRunIfNeeded()
    }

    private var storyListHeader: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Top Stories")).firstMatch
    }

    private var firstCommentsButton: XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+$")
        ).firstMatch
    }

    private var articleHeader: XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Open article")
        ).firstMatch
    }

    private func completeFirstRunIfNeeded() {
        let getStarted = app.buttons["Get started"]
        if getStarted.waitForExistence(timeout: 5) {
            getStarted.tap()
        }
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
    }

    private func edgeSwipeBack(
        endX: CGFloat = 0.72,
        velocity: XCUIGestureVelocity = .default
    ) {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.002, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: endX, dy: 0.5))
        start.press(
            forDuration: 0.05,
            thenDragTo: end,
            withVelocity: velocity,
            thenHoldForDuration: 0.05
        )
    }

    private func openFirstComments() {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 5))
        firstCommentsButton.tap()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 15))
    }

    private func openSettings() {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        app.buttons["More options"].tap()
        let settings = app.buttons["Settings"]
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tap()
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
    }

    private func keepScreenshot(_ name: String) {
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testLaunchAndCompleteFirstRun() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Search"].exists)
        XCTAssertTrue(app.buttons["More options"].exists)
        XCTAssertTrue(
            app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^[0-9]+$")).firstMatch.exists,
            "A loaded story should expose its comments button"
        )
        keepScreenshot("Top Stories")

        app.swipeUp()
        keepScreenshot("Scrolled Top Stories Status Protection")
    }

    func testOpenCommentsAndEdgeSwipeBack() throws {
        openFirstComments()
        XCTAssertFalse(app.staticTexts["Comments"].exists)
        XCTAssertFalse(app.buttons["Navigate up"].exists)
        keepScreenshot("Comments")

        app.swipeUp()
        keepScreenshot("Scrolled Comments Status Protection")
        app.swipeDown()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 5))

        edgeSwipeBack(endX: 0.04, velocity: 50)
        XCTAssertTrue(
            articleHeader.waitForExistence(timeout: 3),
            "A short, slow edge swipe must cancel and leave comments visible"
        )

        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertFalse(articleHeader.waitForExistence(timeout: 3))
    }

    func testOpenArticleInEmbeddedWebView() throws {
        openFirstComments()
        articleHeader.tap()

        let showComments = app.buttons["Show comments"]
        XCTAssertTrue(showComments.waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Refresh website"].exists)
        XCTAssertTrue(app.buttons["Open in browser"].exists)
        XCTAssertFalse(app.buttons["Close article"].exists)
        keepScreenshot("Embedded Article with Comments Sheet")

        showComments.tap()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        XCTAssertFalse(showComments.exists)

        articleHeader.tap()
        XCTAssertTrue(showComments.waitForExistence(timeout: 10))
        edgeSwipeBack()
        XCTAssertTrue(
            articleHeader.waitForExistence(timeout: 10),
            "Back from the website should reveal comments before leaving the story"
        )
        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertFalse(articleHeader.waitForExistence(timeout: 3))
    }

    func testShareDoesNotWriteToClipboard() throws {
        openFirstComments()
        let sentinel = "harmonic-share-sentinel-\(UUID().uuidString)"
        UIPasteboard.general.string = sentinel

        app.buttons["Share"].tap()
        let articleLink = app.buttons["Article link"]
        XCTAssertTrue(articleLink.waitForExistence(timeout: 5))
        articleLink.tap()

        XCTAssertEqual(
            UIPasteboard.general.string,
            sentinel,
            "Opening the system share sheet must not copy the share text"
        )
        keepScreenshot("System Share Sheet")
        app.swipeDown()
    }

    func testSubmissionsUsesOpaqueSafeArea() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 5))
        firstCommentsButton.tap()

        XCTAssertTrue(app.buttons["User"].waitForExistence(timeout: 15))
        app.buttons["User"].tap()
        let submissions = app.buttons["Submissions"]
        if !submissions.waitForExistence(timeout: 5) {
            for _ in 0..<8 where !submissions.exists {
                app.swipeUp()
                if submissions.waitForExistence(timeout: 1) {
                    break
                }
            }
        }
        XCTAssertTrue(submissions.waitForExistence(timeout: 5))
        submissions.tap()

        let navigateUp = app.buttons["Navigate up"]
        let title = app.staticTexts["Submissions"]
        XCTAssertTrue(navigateUp.waitForExistence(timeout: 15))
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        keepScreenshot("Submissions Safe Area")
        XCTAssertGreaterThanOrEqual(navigateUp.frame.minY, 24)
        XCTAssertGreaterThanOrEqual(title.frame.minY, 24)
        XCTAssertEqual(navigateUp.frame.midY, title.frame.midY, accuracy: 12)

        edgeSwipeBack()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
    }

    func testSettingsAndNavigateUp() throws {
        openSettings()
        XCTAssertTrue(app.buttons["Appearance"].exists)
        XCTAssertTrue(app.buttons["Stories"].exists)
        XCTAssertTrue(app.buttons["Navigate up"].waitForExistence(timeout: 10))
        keepScreenshot("Settings List")

        app.buttons["Stories"].tap()
        XCTAssertTrue(app.staticTexts["Stories"].waitForExistence(timeout: 10))
        let usesSplitSettingsLayout = app.buttons["Appearance"].isHittable
        if !usesSplitSettingsLayout {
            app.buttons["Navigate up"].tap()
            XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
            XCTAssertTrue(app.buttons["Appearance"].exists)
        }

        app.buttons["Data"].tap()
        XCTAssertTrue(app.staticTexts["Data"].waitForExistence(timeout: 10))
        XCTAssertFalse(
            app.descendants(matching: .any)["Open Hacker News links in Harmonic"].exists,
            "The Android app-link preference should not be shown on iOS"
        )
        if !usesSplitSettingsLayout {
            app.buttons["Navigate up"].tap()
            XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
        }

        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testSearchAndEdgeSwipeBack() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        app.buttons["Search"].tap()

        XCTAssertTrue(app.buttons["Close search"].waitForExistence(timeout: 5))
        keepScreenshot("Story Search")

        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testLandscapeLayoutAndRestorePortrait() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        defer { XCUIDevice.shared.orientation = .portrait }

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(
            app.frame.width,
            app.frame.height,
            "The application window must relayout to landscape bounds"
        )
        XCTAssertTrue(app.buttons["More options"].isHittable)
        keepScreenshot("Landscape Top Stories")

        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testDarkThemeUpdatesStatusBarAndRestoresTheme() throws {
        openSettings()
        app.buttons["Appearance"].tap()

        let themeRow = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Theme")
        ).firstMatch
        XCTAssertTrue(themeRow.waitForExistence(timeout: 10))
        themeRow.tap()

        let darkTheme = app.staticTexts["Dark"]
        XCTAssertTrue(darkTheme.waitForExistence(timeout: 5))
        darkTheme.tap()
        XCTAssertTrue(themeRow.waitForExistence(timeout: 10))
        keepScreenshot("Dark Theme Status Bar")

        themeRow.tap()
        let automaticTheme = app.staticTexts["Material You (auto)"]
        XCTAssertTrue(automaticTheme.waitForExistence(timeout: 5))
        automaticTheme.tap()
        XCTAssertTrue(themeRow.waitForExistence(timeout: 10))
    }

    func testHomeScreenIconExists() throws {
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        XCUIDevice.shared.press(.home)
        XCTAssertTrue(springboard.icons["Harmonic"].firstMatch.waitForExistence(timeout: 5))

        app.activate()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }
}

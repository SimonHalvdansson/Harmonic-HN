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

    func testSubmissionsKeepsBackButtonBelowStatusBar() throws {
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

        let back = app.buttons["Back"]
        XCTAssertTrue(back.waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Stories"].waitForExistence(timeout: 5))
        keepScreenshot("Submissions status bar protection")
        XCTAssertGreaterThanOrEqual(back.frame.minY, 24)

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

/// Presentation checks use the installed app's settings and do not reset its data.
final class HarmonicIosPresentationTests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        let getStarted = app.buttons["Get started"]
        if getStarted.waitForExistence(timeout: 3) { getStarted.tap() }
        XCTAssertTrue(app.buttons["More options"].waitForExistence(timeout: 15))
    }

    private func keepScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testWelcomeAndAboutBranding() throws {
        app.buttons["More options"].tap()
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 5))
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["About"].waitForExistence(timeout: 10))
        app.buttons["About"].tap()
        XCTAssertTrue(app.images["Harmonic app icon"].waitForExistence(timeout: 10))
        keepScreenshot("About Harmonic icon")

        app.buttons["Navigate up"].tap()
        XCTAssertTrue(app.buttons["Debug"].waitForExistence(timeout: 10))
        app.buttons["Debug"].tap()
        let welcome = app.buttons["Welcome dialog"]
        for _ in 0..<4 where !welcome.isHittable { app.swipeUp() }
        XCTAssertTrue(welcome.waitForExistence(timeout: 5))
        let getStarted = app.buttons["Get started"]
        // A tap during Compose's remaining scroll momentum can only stop the fling.
        // Re-observe the dialog before tapping the row again.
        for _ in 0..<3 {
            welcome.tap()
            if getStarted.waitForExistence(timeout: 2) { break }
        }
        XCTAssertTrue(getStarted.exists)
        keepScreenshot("Welcome Harmonic icon")
        // Relaunching dismisses the preview without applying a display preset.
        app.terminate()
        app.launch()
    }

    func testSubmissionsScrollingHeader() throws {
        checkSubmissionsScrolling(storyIndex: 0)
    }

    func testSubmissionsScrollingHeaderOnAnotherProfile() throws {
        checkSubmissionsScrolling(storyIndex: 1)
    }

    private func checkSubmissionsScrolling(storyIndex: Int) {
        let comments = app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^[0-9]+$")
        ).element(boundBy: storyIndex)
        XCTAssertTrue(comments.waitForExistence(timeout: 15))
        comments.tap()
        XCTAssertTrue(app.buttons["User"].waitForExistence(timeout: 15))
        app.buttons["User"].tap()
        let submissions = app.buttons["Submissions"]
        if !submissions.waitForExistence(timeout: 5) {
            for _ in 0..<8 where !submissions.exists {
                app.swipeUp()
                if submissions.waitForExistence(timeout: 1) { break }
            }
        }
        XCTAssertTrue(submissions.waitForExistence(timeout: 5))
        submissions.tap()
        XCTAssertTrue(app.buttons["Back"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Stories"].waitForExistence(timeout: 15))
        let backFrame = app.buttons["Back"].frame
        let header = app.descendants(matching: .any).matching(
            NSPredicate(format: "label BEGINSWITH %@", "Submissions by ")
        ).firstMatch
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        func checkSafeArea() {
            XCTAssertEqual(app.buttons["Back"].frame.minY, backFrame.minY, accuracy: 1)
            XCTAssertGreaterThanOrEqual(backFrame.minY, 24)
        }
        // The status bar is deliberately translucent; scrolling content may change its pixels.
        // Capture each state for visual inspection and check the floating button stays in place.
        keepScreenshot("Submissions initial")
        app.swipeUp()
        checkSafeArea()
        keepScreenshot("Submissions scrolled up")
        app.swipeDown()
        checkSafeArea()
        keepScreenshot("Submissions reverse scroll")
        app.swipeDown()
        checkSafeArea()
        keepScreenshot("Submissions overscroll")
        // Gestures that start on the retained header must scroll the same surface.
        header.swipeUp()
        checkSafeArea()
        keepScreenshot("Submissions header drag")
        app.buttons["Back"].tap()
        XCTAssertTrue(app.buttons["User"].waitForExistence(timeout: 10))
    }
}

/// Native settings checks preserve the installed app's preferences and bookmarks.
final class HarmonicIosSettingsTests: XCTestCase {
    private var app: XCUIApplication!
    private var restoreFixture = false
    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication()
        app.launch()
        if app.buttons["Get started"].waitForExistence(timeout: 2) { app.buttons["Get started"].tap() }
        XCTAssertTrue(app.buttons["More options"].waitForExistence(timeout: 15))
    }
    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        if restoreFixture {
            debugLink()
            if app.buttons["Remove bookmark"].waitForExistence(timeout: 5) { tap("Remove bookmark") }
            XCTAssertTrue(app.buttons["Bookmark"].waitForExistence(timeout: 5))
            record("Fixture bookmark restored")
        }
    }
    private func dismissFiles() {
        let top = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.09))
        let bottom = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.9))
        top.press(forDuration: 0.1, thenDragTo: bottom)
        Thread.sleep(forTimeInterval: 1)
    }
    private func record(_ name: String) {
        Thread.sleep(forTimeInterval: 1)
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name + " hierarchy"; tree.lifetime = .keepAlways; add(tree)
    }
    private func tap(_ label: String) {
        let button = app.buttons[label].firstMatch
        XCTAssertTrue(button.waitForExistence(timeout: 15), label)
        button.tap()
    }
    private func settings(_ section: String) {
        app.terminate(); app.launch()
        tap("More options"); tap("Settings"); tap(section)
    }
    private func dataRow(_ prefix: String) -> XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", prefix)).firstMatch
    }
    private func debugLink() {
        settings("Debug")
        let row = app.buttons["Link post"]
        for _ in 0..<4 where !row.isHittable { app.swipeUp() }
        Thread.sleep(forTimeInterval: 1)
        row.tap()
        XCTAssertTrue(app.buttons["User"].waitForExistence(timeout: 15))
    }
    func testWebSettingsAndLandscape() {
        settings("Web and links")
        record("Web settings supported features")
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Block WebView ads")).firstMatch.exists)
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Enable reader mode")).firstMatch.exists)
        app.swipeUp()
        record("Web settings links")
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Block WebView ads")).firstMatch.exists)
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Enable reader mode")).firstMatch.exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 4)
        record("Landscape settings")
        if UIDevice.current.userInterfaceIdiom == .phone {
            XCTAssertFalse(app.buttons["Appearance"].exists && app.buttons["Appearance"].isHittable)
        }
        XCUIDevice.shared.orientation = .portrait
        debugLink()
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 4)
        record("Landscape comments")
        if UIDevice.current.userInterfaceIdiom == .phone {
            let feed = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Top Stories")).firstMatch
            XCTAssertFalse(feed.exists && feed.isHittable)
        }
    }
    func testBookmarkFilesCancellationPreservesBookmarks() throws {
        try XCTSkipIf(UIDevice.current.userInterfaceIdiom != .phone, "Exercises the iPhone Files sheet")
        debugLink()
        try XCTSkipIf(app.buttons["Remove bookmark"].exists, "Preserve an existing fixture bookmark")
        restoreFixture = true
        tap("Bookmark")
        XCTAssertTrue(app.buttons["Remove bookmark"].waitForExistence(timeout: 5))
        settings("Data")
        let export = dataRow("Export bookmarks")
        XCTAssertTrue(export.waitForExistence(timeout: 10)); export.tap()
        Thread.sleep(forTimeInterval: 3)
        record("Native bookmark export picker")
        let filename = app.textFields["DOCPicker.filenameTextField"]
        XCTAssertTrue(filename.waitForExistence(timeout: 10))
        XCTAssertTrue((filename.value as? String ?? "").hasPrefix("HarmonicBookmarks"))
        dismissFiles()
        let importRow = dataRow("Import bookmarks")
        XCTAssertTrue(importRow.waitForExistence(timeout: 10)); importRow.tap()
        let add = dataRow("Add to bookmarks")
        XCTAssertTrue(add.waitForExistence(timeout: 5)); add.tap()
        Thread.sleep(forTimeInterval: 3)
        record("Native bookmark import picker")
        XCTAssertTrue(app.collectionViews["File View"].waitForExistence(timeout: 10))
        tap("Cancel")

        debugLink()
        XCTAssertTrue(app.buttons["Remove bookmark"].waitForExistence(timeout: 10), "Cancelling must preserve bookmarks")
    }
}

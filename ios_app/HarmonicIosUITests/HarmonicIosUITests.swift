import XCTest
import UIKit
import WebKit
import HarmonicKit

private extension XCUIApplication {
    // Compose combines a row's title and supporting text in its accessibility label.
    func labeledButton(_ label: String) -> XCUIElement {
        buttons.matching(NSPredicate(format: "label == %@ OR label BEGINSWITH %@", label, label + ",")).firstMatch
    }

    func tapVisibleButton(_ label: String) {
        let target = labeledButton(label)
        for _ in 0..<7 where !target.isHittable { swipeUp(velocity: .slow) }
        XCTAssertTrue(target.waitForExistence(timeout: 10), label)
        XCTAssertTrue(target.isHittable, label)
        target.tapCenter()
    }

    func completeWelcomeIfNeeded() {
        guard staticTexts["Welcome to Harmonic"].waitForExistence(timeout: 5) else { return }
        let getStarted = buttons["Get started"]
        // Short windows, including Duo's outer display, scroll the welcome content.
        for _ in 0..<4 where !getStarted.isHittable {
            swipeUp(velocity: .slow)
        }
        XCTAssertTrue(getStarted.waitForExistence(timeout: 5))
        getStarted.tapCenter()
    }
}

private extension XCUIElement {
    func tapCenter() {
        // Wait for scrolling and presentation to finish before choosing the visible center.
        var previousFrame = CGRect.null
        var stableSince = Date()
        let stationary = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            guard self.exists && self.isHittable else { return false }
            let current = self.frame
            if current != previousFrame {
                previousFrame = current
                stableSince = Date()
                return false
            }
            return Date().timeIntervalSince(stableSince) >= 0.25
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [stationary], timeout: 5), .completed)
        // XCTest's default hit point can land outside a circular Compose button.
        coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
    }
}

final class IosLiteRtSettingsUITests: XCTestCase {
    func testLocalPickerOffersGemmaWithoutGgufModels() {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        app.completeWelcomeIfNeeded()
        let more = app.labeledButton("More options")
        XCTAssertTrue(more.waitForExistence(timeout: 20))
        more.tapCenter()
        app.labeledButton("Settings").tapCenter()
        let ai = app.labeledButton("AI features")
        for _ in 0..<4 where !ai.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(ai.waitForExistence(timeout: 10))
        ai.tapCenter()
        let local = app.labeledButton("Local")
        XCTAssertTrue(local.waitForExistence(timeout: 10))
        local.tapCenter()
        func labelContaining(_ text: String) -> XCUIElement {
            app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", text)).firstMatch
        }
        XCTAssertTrue(labelContaining("Apple Intelligence").waitForExistence(timeout: 10))
        app.tapVisibleButton("Downloadable models")
        XCTAssertTrue(labelContaining("Gemma 4 E2B").waitForExistence(timeout: 10))
        XCTAssertTrue(labelContaining("Gemma 4 E4B").exists)
        XCTAssertFalse(labelContaining("Bonsai 1.7B").exists)
        XCTAssertFalse(labelContaining("llama.cpp").exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "iOS LiteRT model picker"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}

final class HarmonicIosWelcomeLayoutTests: XCTestCase {
    func testWelcomeFillsDuoAndKeepsContentClearOfSystemIndicators() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchEnvironment["HARMONIC_UI_TESTING"] = "1"
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let title = app.staticTexts["Welcome to Harmonic"]
        XCTAssertTrue(title.waitForExistence(timeout: 15))
        let surface = app.otherElements["welcome-surface"]
        XCTAssertTrue(surface.waitForExistence(timeout: 5))
        XCTAssertEqual(surface.frame.minX, app.frame.minX, accuracy: 1)
        XCTAssertEqual(surface.frame.minY, app.frame.minY, accuracy: 1)
        XCTAssertEqual(surface.frame.width, app.frame.width, accuracy: 1)
        XCTAssertEqual(surface.frame.height, app.frame.height, accuracy: 1)
        XCTAssertLessThan(title.frame.maxX, app.frame.maxX - 56)
        let action = app.labeledButton("Get started")
        XCTAssertTrue(action.isHittable)
        let actionFrame = action.frame
        XCTAssertLessThan(actionFrame.midX, app.frame.midX, "Center the action in the usable content area")
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Duo full-screen Welcome with safe content"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.swipeUp(velocity: .slow)
        app.tapVisibleButton("Simple")
        XCTAssertEqual(action.frame, actionFrame, "Scrolling and changing presets must keep the action fixed")
        XCTAssertTrue(action.isHittable)
        action.tapCenter()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Top Stories")).firstMatch.waitForExistence(timeout: 15))
    }
}

final class HarmonicIosUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        if ProcessInfo.processInfo.environment["HARMONIC_PRESERVE_TEST_ORIENTATION"] != "1" {
            XCUIDevice.shared.orientation = .portrait
        }
        app = XCUIApplication()
        app.launchArguments += [
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US",
        ]
        app.launchEnvironment["HARMONIC_UI_TESTING"] = "1"
        app.launch()
        completeFirstRunIfNeeded()
    }

    private func openStoryFromDebugSettings() {
        openDebugSettings()
        let field = app.textViews["HN ID"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tapCenter()
        field.typeText("49643982")
        let openItem = app.labeledButton("Open HN ID")
        if UIDevice.current.userInterfaceIdiom == .pad && !openItem.isHittable {
            openItem.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        openItem.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 20))
    }

    func testEdgeBackFromStoryReturnsToRetainedDebugSettings() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone)
        openStoryFromDebugSettings()
        edgeSwipeBack()
        XCTAssertTrue(app.textViews["HN ID"].waitForExistence(timeout: 5))
        XCTAssertFalse(articleHeader.isHittable)
        keepScreenshot("Story Back returns to Debug settings")
    }

    func testRetainedStoriesRemainInteractiveAndBackReturnsToSubmissions() throws {
        openStoryFromDebugSettings()
        app.labeledButton("User").tapCenter()
        let submissions = app.labeledButton("Submissions")
        XCTAssertTrue(submissions.waitForExistence(timeout: 15))
        submissions.tapCenter()
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 20))
        firstCommentsButton.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        print("Retained story idle start")
        Thread.sleep(forTimeInterval: 10)
        print("Retained story idle end")
        app.labeledButton("User").tapCenter()
        XCTAssertTrue(submissions.waitForExistence(timeout: 5), "Retained stories must remain responsive")
        // Dismiss the profile without invoking any account action.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.2)).tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: submissions
        )], timeout: 5), .completed)
        if UIDevice.current.userInterfaceIdiom == .phone {
            edgeSwipeBack()
            XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 5))
        }
        keepScreenshot("Retained story navigation remains interactive")
    }

    private var storyListHeader: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Top Stories")).firstMatch
    }

    private var firstCommentsButton: XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label MATCHES %@", "^(Open comments, )?([0-9]+ points, )?[0-9]+ comments?$")
        ).firstMatch
    }

    private var articleHeader: XCUIElement {
        app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Open article")
        ).firstMatch
    }

    private var tapToUpdate: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Tap to update")).firstMatch
    }

    private func completeFirstRunIfNeeded() {
        app.completeWelcomeIfNeeded()
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
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 5))
        firstCommentsButton.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 15))
    }

    private func openSettings() {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        app.labeledButton("More options").tapCenter()
        let settings = app.labeledButton("Settings")
        XCTAssertTrue(settings.waitForExistence(timeout: 5))
        settings.tapCenter()
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
    }

    private func closeSettings() {
        for _ in 0..<3 {
            if storyListHeader.waitForExistence(timeout: 1) { return }
            app.labeledButton("Navigate up").firstMatch.tapCenter()
        }
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    private func keepScreenshot(_ name: String) {
        // Compose accessibility can settle one frame before the destination is drawn.
        Thread.sleep(forTimeInterval: 0.5)
        let screenshot = XCTAttachment(screenshot: app.windows.firstMatch.screenshot())
        screenshot.name = name
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testSettingsEdgeBackReturnsToParentBeforeClosingSettings() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone)
        openSettings()
        app.labeledButton("Appearance").tapCenter()
        let theme = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Color scheme")).firstMatch
        XCTAssertTrue(theme.waitForExistence(timeout: 5))
        theme.tapCenter()
        XCTAssertTrue(app.staticTexts["Color scheme"].firstMatch.waitForExistence(timeout: 5))
        edgeSwipeBack()
        XCTAssertTrue(theme.waitForExistence(timeout: 5), "Back must return to Appearance")
        edgeSwipeBack()
        XCTAssertTrue(app.labeledButton("Appearance").waitForExistence(timeout: 5), "Back must return to Settings")
        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 5))
    }

    func testProfileCommentsOpening() throws {
        for mode in ["immediate", "after-draw", "after-draw", "immediate"] {
            app.terminate()
            app.launchEnvironment["HARMONIC_PROFILE_COMMENTS"] = mode
            app.launch()
            completeFirstRunIfNeeded()
            openSettings()
            let debug = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Debug")).firstMatch
            for _ in 0..<4 where !debug.isHittable { app.swipeUp() }
            XCTAssertTrue(debug.waitForExistence(timeout: 5))
            debug.tapCenter()
            let sample = app.labeledButton("Link post")
            for _ in 0..<4 where !sample.isHittable { app.swipeUp() }
            for _ in 0..<8 {
                XCTAssertTrue(sample.waitForExistence(timeout: 5))
                sample.tapCenter()
                XCTAssertTrue(app.labeledButton("Back").firstMatch.waitForExistence(timeout: 15))
                // Let the one-second in-app frame sampling finish before navigating away.
                Thread.sleep(forTimeInterval: 1.2)
                app.labeledButton("Back").firstMatch.tapCenter()
                XCTAssertTrue(sample.waitForExistence(timeout: 10))
            }
        }
    }

    func testBrowserBackReturnsToStoriesOrCommentsPane() throws {
        openFirstComments()
        let usesTwoPanes = storyListHeader.isHittable
        articleHeader.tapCenter()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 15))
        let refresh = app.labeledButton("Refresh website")
        let browserBack = app.buttons.matching(identifier: "Back").allElementsBoundByIndex.max { $0.frame.minY < $1.frame.minY }!
        if refresh.frame.minX > app.frame.width * 0.8 {
            XCTAssertLessThan(browserBack.frame.midY, refresh.frame.midY)
        } else {
            XCTAssertLessThan(browserBack.frame.midX, refresh.frame.midX)
        }
        browserBack.tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        if usesTwoPanes {
            XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        } else {
            XCTAssertFalse(articleHeader.waitForExistence(timeout: 2))
        }
        keepScreenshot("Browser back result")
    }

    func testIosWebSettingsHideUnsupportedBackPreference() throws {
        openSettings()
        app.labeledButton("Web and links").tapCenter()
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Preload websites")).firstMatch.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Hide browser on back")).firstMatch.exists)
        keepScreenshot("iOS web settings")
    }

    func testOfflineCachingDoesNotOfferWebsiteSnapshots() throws {
        app.labeledButton("More options").tapCenter()
        app.labeledButton("Cache stories").tapCenter()
        XCTAssertTrue(app.staticTexts["Cache stories"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["Download website content"].exists)
        keepScreenshot("Comments-only offline caching")
        app.labeledButton("Cancel").tapCenter()
    }

    private func openDebugSettings() {
        openSettings()
        let debug = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Debug")).firstMatch
        let listAnchor = app.labeledButton("Appearance").coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        for _ in 0..<6 where !debug.isHittable {
            listAnchor.withOffset(CGVector(dx: 0, dy: 220)).press(
                forDuration: 0.1,
                thenDragTo: listAnchor.withOffset(CGVector(dx: 0, dy: -40)),
                withVelocity: .slow, thenHoldForDuration: 0.2
            )
        }
        XCTAssertTrue(debug.waitForExistence(timeout: 5))
        debug.tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 5))
    }

    private func openLinkPostFromDebugSettings() {
        openDebugSettings()
        let link = app.labeledButton("Link post")
        let detailX = app.staticTexts.matching(identifier: "Debug").allElementsBoundByIndex.map { $0.frame.minX }.max()! + 8
        let bounds = app.windows.firstMatch.frame
        let origin = app.windows.firstMatch.coordinate(withNormalizedOffset: .zero)
        for _ in 0..<8 where !link.isHittable {
            origin.withOffset(CGVector(dx: detailX, dy: bounds.height * 0.75)).press(
                forDuration: 0.1,
                thenDragTo: origin.withOffset(CGVector(dx: detailX, dy: bounds.height * 0.30)),
                withVelocity: .slow, thenHoldForDuration: 0.2
            )
        }
        XCTAssertTrue(link.waitForExistence(timeout: 5))
        link.tapCenter()
    }

    func testDebugWelcomeSurvivesDuoFolding() throws {
        // Opt in with TEST_RUNNER_HARMONIC_DUO_FOLD_TEST=1 and use Device Hub's
        // Open/Closed controls when the DUO_FOLD_READY markers appear in the test log.
        try XCTSkipUnless(ProcessInfo.processInfo.environment["HARMONIC_DUO_FOLD_TEST"] == "1",
                         "Requires an operator to fold the Duo in Device Hub")
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openDebugSettings()
        app.tapVisibleButton("Welcome screen")
        XCTAssertTrue(app.staticTexts["Welcome to Harmonic"].waitForExistence(timeout: 10))
        app.swipeUp(velocity: .slow)
        app.tapVisibleButton("Simple")
        print("DUO_FOLD_READY_OPEN")
        let opened = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in self.app.frame.width > 600 }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [opened], timeout: 60), .completed)
        XCTAssertTrue(app.buttons["Get started"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.labeledButton("Simple").isSelected, "Keep the pending preset when unfolding")
        keepScreenshot("Welcome retained on open Duo")
        print("DUO_FOLD_READY_CLOSE")
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in self.app.frame.width < 600 }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 60), .completed)
        XCTAssertTrue(app.buttons["Get started"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.labeledButton("Simple").isSelected, "Keep the pending preset when folding")
        keepScreenshot("Welcome retained on closed Duo")
        app.buttons["Get started"].tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 10))
    }

    func testSettingsScrollSurvivesDuoFolding() throws {
        // Device Hub supplies the fold actions, as in the Welcome fold test above.
        try XCTSkipUnless(ProcessInfo.processInfo.environment["HARMONIC_DUO_FOLD_TEST"] == "1",
                         "Requires an operator to fold the Duo in Device Hub")
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openSettings()
        let debug = app.labeledButton("Debug")
        for _ in 0..<4 where !debug.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(debug.isHittable)
        app.tapVisibleButton("Data")
        // Exercise a long settings page without opening dialogs or changing preferences.
        let lastSetting = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Show update changelogs")).firstMatch
        for _ in 0..<7 where !lastSetting.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(lastSetting.isHittable)
        keepScreenshot("Settings scrolled before folding")
        print("DUO_SETTINGS_READY_OPEN")
        let opened = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            min(self.app.frame.width, self.app.frame.height) > 600
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [opened], timeout: 60), .completed)
        // Wait for the restored content after the display migration, rather than
        // using the app frame's briefly stale orientation as a layout assertion.
        let restoredOpen = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in lastSetting.isHittable }, object: nil)
        let detailRetainedOnOpen = XCTWaiter.wait(for: [restoredOpen], timeout: 5) == .completed
        let restoredList = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in debug.isHittable }, object: nil)
        let listRetainedOnOpen = XCTWaiter.wait(for: [restoredList], timeout: 5) == .completed
        keepScreenshot("Settings scroll retained on open Duo")
        print("DUO_SETTINGS_READY_CLOSE")
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            min(self.app.frame.width, self.app.frame.height) < 600
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 60), .completed)
        XCUIDevice.shared.orientation = .portrait
        let restoredClosed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in lastSetting.isHittable }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [restoredClosed], timeout: 5), .completed,
                       "Folding must retain the settings detail's reading position")
        keepScreenshot("Settings scroll retained on closed Duo")
        XCTAssertTrue(detailRetainedOnOpen, "Unfolding must retain the settings detail's reading position")
        XCTAssertTrue(listRetainedOnOpen, "The settings list keeps its own scroll position")
        app.labeledButton("Navigate up").tapCenter()
        XCTAssertTrue(app.labeledButton("Debug").isHittable, "Returning to the settings list keeps its independent position")
    }

    func testStoriesHeaderSettingsPreviewKeepsBoundsAndReturnsToAppearance() throws {
        defer { XCUIDevice.shared.orientation = .portrait }
        if UIDevice.current.userInterfaceIdiom == .pad { XCUIDevice.shared.orientation = .landscapeLeft }
        openSettings()
        app.labeledButton("Appearance").tapCenter()
        app.tapVisibleButton("Stories header")
        XCTAssertTrue(app.staticTexts["Stories header"].waitForExistence(timeout: 5))
        let preview = app.otherElements["stories-header-preview"]
        XCTAssertTrue(preview.waitForExistence(timeout: 5))
        let compact = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Compact header")).firstMatch
        XCTAssertTrue(compact.waitForExistence(timeout: 5))
        if compact.value as? String == "1" { compact.tapCenter() }
        app.labeledButton("Dropdown").tapCenter()
        keepScreenshot("Stories header - dropdown, standard header")
        let frame = preview.frame
        let content = app.otherElements["stories-header-preview-content"]
        XCTAssertTrue(content.waitForExistence(timeout: 5))
        func assertFixedCenteredPreview() {
            XCTAssertEqual(preview.frame.height, frame.height, accuracy: 1)
            XCTAssertEqual(preview.frame.minY, frame.minY, accuracy: 1)
            XCTAssertEqual(content.frame.midY, preview.frame.midY, accuracy: 1)
        }
        let standardHeight = content.frame.height
        compact.tapCenter()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in content.frame.height < standardHeight - 8 }, object: nil
        )], timeout: 5), .completed)
        keepScreenshot("Stories header - dropdown, compact header")
        assertFixedCenteredPreview()
        let compactHeight = content.frame.height
        app.labeledButton("Chips").tapCenter()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in content.frame.height > compactHeight + 24 }, object: nil
        )], timeout: 5), .completed)
        keepScreenshot("Stories header - chips, compact header")
        assertFixedCenteredPreview()
        let chips = app.otherElements["stories-header-preview-chips"]
        XCTAssertTrue(chips.waitForExistence(timeout: 5))
        XCTAssertEqual(chips.frame.minX, preview.frame.minX, accuracy: 1)
        XCTAssertEqual(chips.frame.maxX, preview.frame.maxX, accuracy: 1,
                       "The shared chip row must extend to the preview's safe edge")
        XCTAssertTrue(app.staticTexts["A faster way to solve equations"].exists)
        let chipHeight = content.frame.height
        compact.tapCenter()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in content.frame.height > chipHeight + 8 }, object: nil
        )], timeout: 5), .completed)
        keepScreenshot("Stories header - chips, standard header")
        assertFixedCenteredPreview()
        XCTAssertTrue(compact.isHittable, "Controls must remain clear of the preview")
        let back = app.buttons.matching(NSPredicate(format: "label == %@", "Navigate up")).allElementsBoundByIndex.last!
        back.tapCenter()
        XCTAssertTrue(app.labeledButton("Stories header").waitForExistence(timeout: 5))
        XCTAssertFalse(compact.isHittable, "Stories header controls belong in the subpage")
    }

    func testStatusBarTapScrollsStoriesAndCommentsToTop() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone)
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 20))
        let firstLabel = firstCommentsButton.label
        let originalFirst = app.buttons.matching(NSPredicate(format: "label == %@", firstLabel)).firstMatch
        for _ in 0..<2 {
            app.swipeUp(velocity: .slow)
            app.swipeUp(velocity: .slow)
            XCTAssertFalse(originalFirst.isHittable)
            tapStatusBar()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in originalFirst.isHittable }, object: nil
            )], timeout: 10), .completed)
        }
        openFirstComments()
        waitForCommentsBeforeDraggingSheet()
        app.swipeUp(velocity: .slow)
        app.swipeUp(velocity: .slow)
        XCTAssertFalse(articleHeader.isHittable)
        tapStatusBar()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in self.articleHeader.isHittable }, object: nil
        )], timeout: 10), .completed)
        keepScreenshot("Status bar tap restored comments header")
    }

    func testStatusBarTapScrollsSettingsToTop() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone)
        openDebugSettings()
        let mock = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Mock AI answers")).firstMatch
        XCTAssertTrue(mock.waitForExistence(timeout: 5))
        app.swipeUp(velocity: .slow)
        app.swipeUp(velocity: .slow)
        XCTAssertFalse(mock.isHittable)
        tapStatusBar()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in mock.isHittable }, object: nil
        )], timeout: 10), .completed)
    }

    func testIpadStatusBarTapScrollsOnlyTheTappedPane() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openFirstComments()
        waitForCommentsBeforeDraggingSheet()
        let firstLabel = firstCommentsButton.label
        let originalFirst = app.buttons.matching(NSPredicate(format: "label == %@", firstLabel)).firstMatch
        let window = app.windows.firstMatch
        let origin = window.coordinate(withNormalizedOffset: .zero)
        func scrollPane(x: CGFloat) {
            for _ in 0..<2 {
                origin.withOffset(CGVector(dx: x, dy: window.frame.height * 0.8)).press(
                    forDuration: 0.05,
                    thenDragTo: origin.withOffset(CGVector(dx: x, dy: window.frame.height * 0.3)),
                    withVelocity: .slow, thenHoldForDuration: 0.2
                )
            }
        }
        let commentsX = articleHeader.frame.midX
        scrollPane(x: originalFirst.frame.midX)
        scrollPane(x: commentsX)
        XCTAssertFalse(originalFirst.isHittable)
        XCTAssertFalse(articleHeader.isHittable)
        tapStatusBar(relativeX: commentsX / window.frame.width)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in self.articleHeader.isHittable }, object: nil
        )], timeout: 10), .completed)
        XCTAssertFalse(originalFirst.isHittable, "The other pane must retain its reading position")
        tapStatusBar(relativeX: 0.2)
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in originalFirst.isHittable }, object: nil
        )], timeout: 10), .completed)
    }

    private func tapStatusBar(relativeX: CGFloat = 0.5) {
        let window = app.windows.firstMatch
        window.coordinate(withNormalizedOffset: .zero)
            .withOffset(CGVector(dx: window.frame.width * relativeX, dy: 2)).tap()
    }

    func testIpadAskCommentBackButtonDoesNotDragSheet() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openDebugSettings()
        let mock = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Mock AI answers")).firstMatch
        XCTAssertTrue(mock.waitForExistence(timeout: 5))
        mock.tapCenter()
        closeSettings()
        openFirstComments()
        waitForCommentsBeforeDraggingSheet()
        let comment = app.otherElements["comment-row"].firstMatch
        XCTAssertTrue(comment.isHittable)
        // Hold the card's upper trailing space so embedded links cannot open a preview.
        comment.coordinate(withNormalizedOffset: CGVector(dx: 0.9, dy: 0))
            .withOffset(CGVector(dx: 0, dy: 16)).press(forDuration: 1)
        let ask = app.labeledButton("Ask")
        XCTAssertTrue(ask.waitForExistence(timeout: 10))
        ask.tapCenter()
        let back = app.labeledButton("Back to comment")
        let title = app.staticTexts["Ask about this comment"]
        XCTAssertTrue(back.waitForExistence(timeout: 10))
        XCTAssertTrue(title.waitForExistence(timeout: 5))
        let originalFrame = title.frame
        keepScreenshot("iPad Ask before back-button drag")
        let start = back.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.05,
                    thenDragTo: start.withOffset(CGVector(dx: 0, dy: app.windows.firstMatch.frame.height * 0.8)),
                    withVelocity: .slow, thenHoldForDuration: 0.5)
        keepScreenshot("iPad Ask after back-button drag")
        XCTAssertEqual(title.frame.minY, originalFrame.minY, accuracy: 1,
                       "Dragging the Ask back button must not lower the comments sheet")
        XCTAssertTrue(back.isHittable)
        back.tapCenter()
        XCTAssertTrue(ask.waitForExistence(timeout: 5), "Tapping Back must still return to the comment actions")
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.1)).tap()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: ask
        )], timeout: 5), .completed)
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 5))
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 5))
        let expandedHandleY = handle.frame.minY
        let windowHeight = app.windows.firstMatch.frame.height
        let handleStart = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        handleStart.press(forDuration: 0.05,
                          thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.96)))
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                handle.exists && handle.frame.minY > expandedHandleY + windowHeight * 0.5
            }, object: nil
        )], timeout: 10), .completed, "Dismissing the modal must restore normal comments sheet dragging")
    }

    func testAskQuestionAndAnswerRespectCommentTextSize() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .phone)
        let question = "Explain the main idea in simpler terms"
        let answerPrefix = "A useful way to understand technical language"
        var heights: [(question: CGFloat, answer: CGFloat)] = []
        for largeText in [false, true] {
            if largeText {
                app.terminate()
                app.launch()
                completeFirstRunIfNeeded()
                openSettings()
                app.tapVisibleButton("Comments")
                // Compose exposes this adjustable range as Other, not a native UISlider.
                let slider = app.otherElements.matching(NSPredicate(format: "value == %@", "0.0")).firstMatch
                XCTAssertTrue(slider.waitForExistence(timeout: 5))
                // The accessibility bounds extend beyond the actual track's horizontal hit area.
                slider.coordinate(withNormalizedOffset: CGVector(dx: 0.94, dy: 0.5)).tap()
                XCTAssertTrue(app.staticTexts["+6"].waitForExistence(timeout: 5))
                closeSettings()
            }
            openDebugSettings()
            let mock = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Mock AI answers")).firstMatch
            XCTAssertTrue(mock.waitForExistence(timeout: 5))
            mock.tapCenter()
            let field = app.textViews["HN ID"]
            field.tapCenter()
            field.typeText("47938184")
            app.labeledButton("Open HN ID").tapCenter()
            let summarize = app.labeledButton("Summarize")
            XCTAssertTrue(summarize.waitForExistence(timeout: 20))
            summarize.tapCenter()
            let ask = app.labeledButton("Ask")
            XCTAssertTrue(ask.waitForExistence(timeout: 20))
            ask.tapCenter()
            app.labeledButton(question).tapCenter()
            let answer = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", answerPrefix)).firstMatch
            XCTAssertTrue(answer.waitForExistence(timeout: 15))
            let stop = app.labeledButton("Stop response")
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "exists == false"), object: stop
            )], timeout: 15), .completed)
            // Auto-follow leaves the answer clipped to the same viewport height at either size.
            // Return to the top before comparing text layout rather than the clipped viewport.
            app.swipeDown(velocity: .slow)
            app.swipeDown(velocity: .slow)
            let sentQuestion = app.staticTexts[question]
            XCTAssertTrue(sentQuestion.isHittable)
            heights.append((sentQuestion.frame.height, answer.frame.height))
            keepScreenshot(largeText ? "Ask maximum comment size" : "Ask default comment size")
        }
        XCTAssertGreaterThan(heights[1].question, heights[0].question)
        XCTAssertGreaterThan(heights[1].answer, heights[0].answer)
    }

    func testForegroundResumePreservesTapToUpdateAndNavigation() throws {
        openDebugSettings()
        let always = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Always show tap to refresh")).firstMatch
        always.tapCenter()
        keepScreenshot("Tap-to-refresh debug preference enabled")
        closeSettings()
        XCTAssertTrue(tapToUpdate.waitForExistence(timeout: 10))
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertTrue(tapToUpdate.waitForExistence(timeout: 10))
        tapToUpdate.tapCenter()
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 15))
        keepScreenshot("Resumed stories")
    }

    func testGithubPreviewLoadsInTheCommentsHeader() throws {
        openDebugSettings()
        let previews = app.labeledButton("Link previews")
        for _ in 0..<5 where !previews.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(previews.waitForExistence(timeout: 5))
        Thread.sleep(forTimeInterval: 1) // Let the list finish decelerating before tapping.
        previews.tapCenter()
        let sample = app.buttons.matching(NSPredicate(format: "label CONTAINS %@", "Show HN: Ctxdiff")).firstMatch
        XCTAssertTrue(sample.waitForExistence(timeout: 15))
        sample.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 15))
        // This header exists only after StoryLinkPreviewSession has populated gitHubRepoInfo.
        let repo = app.staticTexts.matching(NSPredicate(format: "label ==[c] %@", "salmanzafar949 / ctxdiff")).firstMatch
        for _ in 0..<5 where !repo.isHittable {
            app.swipeUp()
            if repo.waitForExistence(timeout: 3) { break }
        }
        XCTAssertTrue(repo.waitForExistence(timeout: 15))
        keepScreenshot("GitHub preview in comments")
        for _ in 0..<5 where !articleHeader.isHittable { app.swipeDown() }
        articleHeader.tapCenter()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
    }

    func testReadingPositionSurvivesClosingAndReopeningAStory() throws {
        openDebugSettings()
        let sample = app.labeledButton("Link post")
        app.tapVisibleButton("Link post")
        XCTAssertTrue(app.labeledButton("Back").firstMatch.waitForExistence(timeout: 15))
        let loaded = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.staticTexts.allElementsBoundByIndex.contains { $0.label.count > 60 }
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [loaded], timeout: 15), .completed)
        app.swipeUp()
        Thread.sleep(forTimeInterval: 1.5) // Let inertial scrolling settle before sampling the anchor.
        keepScreenshot("Scrolled comments before restoration check")
        let text = app.buttons.allElementsBoundByIndex.first {
            $0.label.count > 20 && $0.isHittable && $0.frame.minY > 140 &&
            $0.frame.minY < app.frame.height - 100
        }
        let anchor = try XCTUnwrap(text, "A comment should be visible after scrolling")
        let label = anchor.label
        let y = anchor.frame.minY
        keepScreenshot("Reading position before closing")
        app.labeledButton("Back").firstMatch.tapCenter()
        XCTAssertTrue(sample.waitForExistence(timeout: 10))
        sample.tapCenter()
        let restored = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", String(label.prefix(80)))).firstMatch
        let position = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in
                restored.exists && restored.isHittable && abs(restored.frame.minY - y) < 30
            }, object: nil
        )
        let restorationResult = XCTWaiter.wait(for: [position], timeout: 15)
        keepScreenshot("Restored reading position")
        XCTAssertEqual(restorationResult, .completed, "Expected y=\(y), actual y=\(restored.exists ? restored.frame.minY : -1)")
    }

    func testLaunchAndCompleteFirstRun() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(app.labeledButton("Search").exists)
        XCTAssertTrue(app.labeledButton("More options").exists)
        XCTAssertTrue(
            app.buttons.matching(NSPredicate(format: "label MATCHES %@", "^(Open comments, )?([0-9]+ points, )?[0-9]+ comments?$")).firstMatch.exists,
            "A loaded story should expose its comments button"
        )
        keepScreenshot("Top Stories")

        app.swipeUp()
        keepScreenshot("Scrolled Top Stories Status Protection")
    }

    func testOpenCommentsAndEdgeSwipeBack() throws {
        openFirstComments()
        XCTAssertFalse(app.staticTexts["Comments"].exists)
        XCTAssertFalse(app.labeledButton("Navigate up").exists)
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
        XCTAssertEqual(app.webViews.count, 0, "The article must remain hidden until requested")
        articleHeader.tapCenter()

        let showComments = app.labeledButton("Show comments")
        XCTAssertTrue(showComments.waitForExistence(timeout: 15))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(app.labeledButton("Refresh website").exists)
        XCTAssertTrue(app.labeledButton("Open in browser").exists)
        XCTAssertFalse(app.labeledButton("Close article").exists)
        keepScreenshot("Embedded Article with Comments Sheet")

        showComments.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        XCTAssertFalse(showComments.isHittable)

        articleHeader.tapCenter()
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
        let originalClipboard = UIPasteboard.general.items
        defer { UIPasteboard.general.items = originalClipboard }
        let sentinel = "harmonic-share-sentinel-\(UUID().uuidString)"
        UIPasteboard.general.string = sentinel

        app.labeledButton("Share").tapCenter()
        let articleLink = app.labeledButton("Article link")
        XCTAssertTrue(articleLink.waitForExistence(timeout: 5))
        articleLink.tapCenter()
        // Presentation is asynchronous; wait for the native sheet before inspecting it.
        let nativeCopy = app.descendants(matching: .any)
            .matching(NSPredicate(format: "label == %@", "Copy")).firstMatch
        XCTAssertTrue(nativeCopy.waitForExistence(timeout: 10))

        XCTAssertEqual(
            UIPasteboard.general.string,
            sentinel,
            "Opening the system share sheet must not copy the share text"
        )
        keepScreenshot("System Share Sheet")
        app.swipeDown()
    }

    func testDragCommentsRevealsDeferredBrowser() throws {
        openFirstComments()
        XCTAssertEqual(app.webViews.count, 0)
        waitForCommentsBeforeDraggingSheet()
        // The header can contain an image button that consumes its own gestures.
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 10))
        let start = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        // Duo's compact peek leaves more sheet travel than the horizontal toolbar.
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.96))
        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
        app.labeledButton("Show comments").tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
    }

    func testStoriesMenuWelcomeHintPlacement() {
        let title = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Make it yours")).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 10))
        XCTAssertTrue(app.labeledButton("Got it").waitForExistence(timeout: 5))
        let hint = app.otherElements["stories-welcome-hint"]
        XCTAssertTrue(hint.waitForExistence(timeout: 5))
        func checkPlacement() {
            let menu = app.labeledButton("More options").frame
            let card = hint.frame
            let outerDuo = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true &&
                min(app.frame.width, app.frame.height) < 600
            XCTAssertGreaterThanOrEqual(card.minX, 15)
            XCTAssertGreaterThanOrEqual(card.minY, 15)
            XCTAssertLessThanOrEqual(card.maxX, app.frame.maxX - 15)
            XCTAssertLessThanOrEqual(card.maxY, app.frame.maxY - 15)
            XCTAssertTrue(app.labeledButton("Got it").isHittable)
            if outerDuo {
                XCTAssertLessThan(card.maxX, menu.minX, "The hint belongs beside the rail")
                XCTAssertGreaterThanOrEqual(menu.midY, card.minY + 20)
                XCTAssertLessThanOrEqual(menu.midY, card.maxY - 20, "The sideways caret must be able to point at the menu")
            } else {
                XCTAssertGreaterThanOrEqual(card.minY, menu.maxY, "Header menus keep the existing hint below them")
            }
        }
        checkPlacement()
        keepScreenshot("Stories menu welcome hint placement")
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "Welcome hint accessibility geometry"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
        app.labeledButton("Got it").tapCenter()
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: title
        )], timeout: 5), .completed)
        app.labeledButton("More options").tapCenter()
        XCTAssertTrue(app.labeledButton("Settings").waitForExistence(timeout: 5))
    }

    func testStoriesSearchIsAtBottomRightAndOpensSearch() {
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        let search = app.labeledButton("Search")
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        let usesRail = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true &&
            min(app.frame.width, app.frame.height) < 600
        if usesRail {
            XCTAssertGreaterThan(search.frame.midX, app.frame.width * 0.8)
            XCTAssertGreaterThan(search.frame.minY, app.frame.height * 0.75)
        } else {
            XCTAssertEqual(search.frame.midY, app.labeledButton("More options").frame.midY, accuracy: 2)
        }
        keepScreenshot("Stories bottom right search")
        search.tapCenter()
        XCTAssertTrue(app.textViews["Search posts"].waitForExistence(timeout: 10))
        app.labeledButton("Back").tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        if usesRail {
            let searchAxis = search.frame.midX
            openSettings()
            XCTAssertEqual(app.labeledButton("Navigate up").frame.midX, searchAxis, accuracy: 1,
                           "Search and Settings Back must share the rail's axis")
        }
    }

    func testStoriesMoreMenuUsesTopRailAndStaysFixed() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        let more = app.labeledButton("More options")
        let search = app.labeledButton("Search")
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label == %@", "More options")).count, 1)
        let originalFrame = more.frame
        XCTAssertEqual(originalFrame.midX, search.frame.midX, accuracy: 1)
        XCTAssertGreaterThan(originalFrame.minY, 140, "Leave room for Duo's system indicators")
        XCTAssertLessThan(originalFrame.maxY, app.frame.height * 0.4)
        for _ in 0..<2 { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(more.isHittable)
        XCTAssertEqual(more.frame.minX, originalFrame.minX, accuracy: 1)
        XCTAssertEqual(more.frame.minY, originalFrame.minY, accuracy: 1)
        keepScreenshot("Stories fixed top rail menu")
        more.tapCenter()
        XCTAssertTrue(app.labeledButton("Settings").waitForExistence(timeout: 5))
        keepScreenshot("Stories rail menu expanded")
        app.labeledButton("Settings").tapCenter()
        let up = app.labeledButton("Navigate up")
        XCTAssertTrue(up.waitForExistence(timeout: 10))
        XCTAssertEqual(up.frame.midX, originalFrame.midX, accuracy: 1)
        XCTAssertEqual(up.frame.midY, originalFrame.midY, accuracy: 1)
        up.tapCenter()
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        more.tapCenter()
        XCTAssertTrue(app.labeledButton("Settings").waitForExistence(timeout: 5))
    }

    func testSettingsTitlesRespectRailLayout() {
        openSettings()
        let up = app.labeledButton("Navigate up")
        let appearance = app.labeledButton("Appearance")
        XCTAssertTrue(appearance.waitForExistence(timeout: 5))
        let rail = up.frame.minX > app.frame.width * 0.8
        // With rail navigation, titles align with the cards' outer leading edge.
        let rootContentStart = rail ? appearance.frame.minX : up.frame.maxX + 12
        XCTAssertEqual(app.staticTexts["Settings"].frame.minX, rootContentStart, accuracy: 1)
        keepScreenshot("Settings title and rail alignment")
        appearance.tapCenter()
        let colorScheme = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Color scheme")).firstMatch
        XCTAssertTrue(colorScheme.waitForExistence(timeout: 5))
        let detailContentStart = rail ? colorScheme.frame.minX : up.frame.maxX + 12
        XCTAssertEqual(app.staticTexts["Appearance"].frame.minX, detailContentStart, accuracy: 1)
        keepScreenshot("Settings detail title and rail alignment")
        up.tapCenter()
        XCTAssertTrue(appearance.waitForExistence(timeout: 5))
    }

    func testPostHeaderBackStaysFixedAboveScrollingContent() throws {
        openSettings()
        app.tapVisibleButton("Post header")
        let preview = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "Header preview")
        ).firstMatch
        XCTAssertTrue(preview.waitForExistence(timeout: 10))
        let back = app.labeledButton("Back").exists
            ? app.labeledButton("Back") : app.labeledButton("Navigate up")
        XCTAssertTrue(back.waitForExistence(timeout: 5))
        func checkScrollingBack() {
            // Window bounds can update just before Compose applies the new safe-area insets.
            Thread.sleep(forTimeInterval: 0.5)
            let originalFrame = back.frame
            for _ in 0..<4 {
                app.swipeUp(velocity: .slow)
                XCTAssertTrue(back.isHittable, "Back must stay above the scrolling preview and settings")
                XCTAssertEqual(back.frame.minX, originalFrame.minX, accuracy: 1)
                XCTAssertEqual(back.frame.minY, originalFrame.minY, accuracy: 1)
            }
        }
        checkScrollingBack()
        keepScreenshot("Post header fixed Back after scrolling")
        if ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") != true {
            defer { XCUIDevice.shared.orientation = .portrait }
            XCUIDevice.shared.orientation = .landscapeLeft
            let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                self.app.frame.width > self.app.frame.height
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 10), .completed)
            checkScrollingBack()
            keepScreenshot("Landscape Post header fixed Back after scrolling")
        }
        back.tapCenter()
        XCTAssertTrue(app.labeledButton("Post header").waitForExistence(timeout: 10))
    }

    func testPostHeaderSampleChangesWhileScrolledKeepPreviewOffscreen() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openSettings()
        app.tapVisibleButton("Post header")
        for _ in 0..<3 { app.labeledButton("Next preview post").tapCenter() }
        let user = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "User")).firstMatch
        func userIsVisible() -> Bool {
            guard user.exists else { return false }
            let frame = user.frame
            return frame.height > 0 && frame.minY > app.frame.minY + 100 && frame.maxY < app.frame.maxY - 120
        }
        for _ in 0..<10 where !userIsVisible() { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(userIsVisible())
        // Clear the last part of the tall header without flinging the User row offscreen.
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.65)).press(
            forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.40)),
            withVelocity: .slow, thenHoldForDuration: 0.3
        )
        let original = user.frame
        let preview = app.descendants(matching: .any).matching(
            NSPredicate(format: "label == %@", "Header preview")
        ).firstMatch
        let next = app.labeledButton("Next preview post")
        if preview.exists { XCTAssertEqual(preview.frame.intersection(app.frame).height, 0, accuracy: 1) }
        keepScreenshot("Scrolled before switching preview samples")
        for index in 0..<8 {
            next.tapCenter()
            XCTAssertEqual(user.frame.minY, original.minY, accuracy: 1)
            if preview.exists {
                XCTAssertEqual(preview.frame.intersection(app.frame).height, 0, accuracy: 1,
                    "A scrolled-away preview must not reappear when selecting another sample")
            }
            keepScreenshot("Scrolled sample change \(index + 1)")
        }
    }

    func testShortPostHeaderSettingsEditsKeepRowsStable() throws {
        let duo = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true
        defer { if !duo { XCUIDevice.shared.orientation = .portrait } }
        if !duo {
            XCUIDevice.shared.orientation = .landscapeLeft
            let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                self.app.frame.width > self.app.frame.height
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: 10), .completed)
        }
        openSettings()
        app.tapVisibleButton("Post header")
        // The repository-card sample stays taller than the pinning limit in short windows.
        for _ in 0..<3 { app.labeledButton("Next preview post").tapCenter() }
        // Let the sample's height animation finish, then bring the layout rows into view
        // before querying them. XCTest may retain an offscreen node after Compose removes it.
        Thread.sleep(forTimeInterval: 0.5)
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.75)).press(
            forDuration: 0.1,
            thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.30)),
            withVelocity: .slow, thenHoldForDuration: 0.3
        )
        let domain = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Domain")).firstMatch
        func domainIsInViewport() -> Bool {
            guard domain.exists else { return false }
            let frame = domain.frame
            return frame.height > 0 && frame.minY > app.frame.minY + 80 &&
                frame.maxY < app.frame.maxY - 100
        }
        // On Duo, XCTest can throw when asked for hittability of an offscreen Compose node.
        // Scroll by its visible bounds before asking the native hit tester.
        var sawDomain = false
        for _ in 0..<24 where !domainIsInViewport() {
            let frame = domain.exists ? domain.frame : .zero
            let aboveViewport = (frame.height > 0 && frame.maxY < app.frame.minY + 100) ||
                (sawDomain && frame.height == 0)
            if frame.height > 0 { sawDomain = true }
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.35, dy: 0.55)).press(
                forDuration: 0.1,
                thenDragTo: app.coordinate(withNormalizedOffset:
                    CGVector(dx: 0.35, dy: aboveViewport ? 0.70 : 0.40)),
                withVelocity: .slow, thenHoldForDuration: 0.3
            )
        }
        XCTAssertTrue(domain.isHittable)
        let rowFrame = domain.frame
        let back = duo ? app.labeledButton("Navigate up") : app.labeledButton("Back")
        XCTAssertTrue(back.isHittable)
        let backFrame = back.frame
        for _ in 0..<4 {
            domain.tapCenter()
            XCTAssertTrue(domain.isHittable)
            XCTAssertEqual(domain.frame.minY, rowFrame.minY, accuracy: 1,
                "Editing a scrolled preview must not repin it over the settings")
            XCTAssertEqual(back.frame, backFrame)
        }
        keepScreenshot("Short Post header settings after repeated edits")
        back.tapCenter()
        XCTAssertTrue(app.labeledButton("Post header").waitForExistence(timeout: 10))
    }

    func testDuoSettingsRailBackAndRequiredHeaderBack() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openSettings()
        let up = app.labeledButton("Navigate up")
        XCTAssertTrue(up.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(up.frame.minX, app.frame.width * 0.8)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "label == %@", "Navigate up")).count, 1)
        let railFrame = up.frame
        keepScreenshot("Settings Back in side rail")
        app.tapVisibleButton("Appearance")
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Color scheme")).firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(up.frame.midX, railFrame.midX, accuracy: 1)
        up.tapCenter()
        XCTAssertTrue(app.labeledButton("Post header").waitForExistence(timeout: 5))
        app.tapVisibleButton("Post header")
        let thumbnail = app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Thumbnail")).firstMatch
        for _ in 0..<4 where !thumbnail.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(thumbnail.waitForExistence(timeout: 5))
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Back")).firstMatch.exists)
        XCTAssertEqual(up.frame.midX, railFrame.midX, accuracy: 1)
        XCTAssertEqual(up.frame.midY, railFrame.midY, accuracy: 1)
        XCTAssertFalse(app.labeledButton("Back").exists, "The header preview must use the host rail")
        keepScreenshot("Header display without optional Back switch")
        up.tapCenter()
        XCTAssertTrue(app.labeledButton("Post header").waitForExistence(timeout: 5))
        up.tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testDuoBrowserRailAndCompactSheet() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openDebugSettings()
        app.tapVisibleButton("Link post")
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        let up = app.labeledButton("Navigate up")
        XCTAssertTrue(up.waitForExistence(timeout: 5))
        XCTAssertGreaterThan(up.frame.minX, app.frame.width * 0.8)
        let upFrame = up.frame
        keepScreenshot("Comments Back in side rail")
        waitForCommentsBeforeDraggingSheet()
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 5))
        // Keep the rail alignment check independent of a swipe at the screen's top edge.
        articleHeader.tapCenter()
        let refresh = app.labeledButton("Refresh website")
        XCTAssertTrue(refresh.waitForExistence(timeout: 15))
        XCTAssertGreaterThan(refresh.frame.minX, app.frame.width * 0.8, "Controls belong in Duo's right status strip")
        let showComments = app.labeledButton("Show comments")
        let browser = app.webViews.firstMatch
        XCTAssertTrue(browser.waitForExistence(timeout: 10))
        XCTAssertTrue(refresh.isHittable)
        XCTAssertTrue(showComments.isHittable)
        XCTAssertEqual(refresh.frame.midX, showComments.frame.midX, accuracy: 1)
        for label in ["Back", "Refresh website", "Show comments", "Open in browser"] {
            XCTAssertEqual(app.labeledButton(label).frame.midX, up.frame.midX, accuracy: 1,
                           "Every browser control must share app Back's rail axis")
        }
        XCTAssertLessThan(refresh.frame.maxY, showComments.frame.minY + 1)
        XCTAssertGreaterThan(refresh.frame.minX, browser.frame.maxX - 1)
        XCTAssertGreaterThan(refresh.frame.minY, app.frame.height * 0.5, "Browser actions build up from the bottom")
        XCTAssertGreaterThan(refresh.frame.minY, up.frame.maxY + 56, "App Back is separate from browser controls")
        XCTAssertEqual(up.frame.midX, upFrame.midX, accuracy: 1)
        XCTAssertEqual(up.frame.midY, upFrame.midY, accuracy: 1)
        XCTAssertLessThan(app.frame.maxY - handle.frame.minY, 60, "Only the handle and system inset remain below the page")
        keepScreenshot("Duo fixed browser rail and compact sheet")
        refresh.tapCenter()
        XCTAssertTrue(showComments.isHittable)
        showComments.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        XCTAssertFalse(showComments.isHittable, "Expanded comments cover the rail")
        articleHeader.tapCenter()
        XCTAssertTrue(showComments.waitForExistence(timeout: 10))
        showComments.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        keepScreenshot("Duo comments restored after browser controls")
        up.tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 10), "App Back returns to the retained parent")
    }

    private func waitForCommentsBeforeDraggingSheet() {
        // The story header arrives before the comments and saved-position restoration.
        // The sheet intentionally rejects downward drags until that initial load finishes.
        XCTAssertTrue(app.otherElements["comment-row"].firstMatch.waitForExistence(timeout: 20))
        let loading = app.otherElements["comments-loading-indicator"]
        XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
            predicate: NSPredicate(format: "exists == false"), object: loading
        )], timeout: 10), .completed)
    }

    func testDuoReaderModeAndAvailabilityAfterRefresh() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openDebugSettings()
        app.tapVisibleButton("Link post")
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        articleHeader.tapCenter()
        let reader = app.labeledButton("Reader mode")
        XCTAssertTrue(reader.waitForExistence(timeout: 30))
        XCTAssertEqual(reader.frame.midX, app.labeledButton("Navigate up").frame.midX, accuracy: 1)
        reader.tapCenter()
        let enabledReader = app.labeledButton("Reader mode on")
        XCTAssertTrue(enabledReader.waitForExistence(timeout: 15))
        enabledReader.tapCenter()
        XCTAssertTrue(reader.waitForExistence(timeout: 15))
        app.labeledButton("Refresh website").tapCenter()
        // Navigation removes reader availability, then detection restores its animated slot.
        XCTAssertTrue(reader.waitForExistence(timeout: 30))
        reader.tapCenter()
        XCTAssertTrue(enabledReader.waitForExistence(timeout: 15))
        keepScreenshot("Duo reader mode after website refresh")
        enabledReader.tapCenter()
        XCTAssertTrue(reader.waitForExistence(timeout: 15))
    }

    func testPreloadPreferenceSurvivesRelaunchAndArticleOpening() throws {
        openSettings()
        app.labeledButton("Web and links").tapCenter()
        let preload = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Preload websites")
        ).firstMatch
        XCTAssertTrue(preload.waitForExistence(timeout: 5))
        preload.tapCenter()
        app.staticTexts["Always"].tapCenter()
        app.labeledButton("OK").tapCenter()
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Preload websites, Always")
        ).firstMatch.waitForExistence(timeout: 5))
        closeSettings()
        // Keep the isolated test files for this launch to exercise a fresh DataStore reader.
        app.launchEnvironment["HARMONIC_UI_TEST_PRESERVE_DATA"] = "1"
        defer { app.launchEnvironment.removeValue(forKey: "HARMONIC_UI_TEST_PRESERVE_DATA") }
        app.terminate()
        app.launch()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15), "First-run preferences must survive too")
        openSettings()
        app.labeledButton("Web and links").tapCenter()
        XCTAssertTrue(app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Preload websites, Always")
        ).firstMatch.waitForExistence(timeout: 5))
        closeSettings()
        openFirstComments()
        XCTAssertTrue(articleHeader.exists)
        articleHeader.tapCenter()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
    }

    func testSubmissionsBackUsesPlatformPlacementAndStaysFixed() throws {
        defer { XCUIDevice.shared.orientation = .portrait }
        if min(app.frame.width, app.frame.height) >= 600 {
            XCUIDevice.shared.orientation = .landscapeLeft
        }

        let usesRail = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true &&
            min(app.frame.width, app.frame.height) < 600
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        let more = app.labeledButton("More options")
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        let railControlFrame = more.frame
        openLinkPostFromDebugSettings()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 15))
        app.labeledButton("User").tapCenter()
        let submissions = app.labeledButton("Submissions")
        if !submissions.waitForExistence(timeout: 5) {
            for _ in 0..<8 where !submissions.exists {
                app.swipeUp()
                if submissions.waitForExistence(timeout: 1) {
                    break
                }
            }
        }
        XCTAssertTrue(submissions.waitForExistence(timeout: 5))
        submissions.tapCenter()

        let back = app.labeledButton("Back")
        XCTAssertTrue(back.waitForExistence(timeout: 15))
        XCTAssertTrue(app.labeledButton("Stories").waitForExistence(timeout: 5))
        let backFrame = back.frame
        XCTAssertGreaterThanOrEqual(backFrame.minY, usesRail ? 24 : 0)
        XCTAssertLessThan(backFrame.maxY, app.windows.firstMatch.frame.height * 0.35)
        if usesRail {
            XCTAssertGreaterThan(backFrame.minX, app.frame.width * 0.8)
            XCTAssertEqual(backFrame.midX, railControlFrame.midX, accuracy: 1)
            XCTAssertEqual(backFrame.midY, railControlFrame.midY, accuracy: 1)
        } else {
            XCTAssertLessThan(backFrame.maxX, app.frame.width * 0.5)
        }
        keepScreenshot("Submissions platform Back placement")
        for _ in 0..<3 {
            app.swipeUp(velocity: .slow)
            XCTAssertTrue(back.isHittable)
            XCTAssertEqual(back.frame.minX, backFrame.minX, accuracy: 1)
            XCTAssertEqual(back.frame.minY, backFrame.minY, accuracy: 1)
        }
        keepScreenshot("Submissions fixed Back after scrolling")
        back.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
    }

    func testExpandedStoriesControlsStayInHeader() throws {
        try XCTSkipUnless(min(app.windows.firstMatch.frame.width, app.windows.firstMatch.frame.height) >= 600)
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        let more = app.labeledButton("More options")
        let search = app.labeledButton("Search")
        XCTAssertTrue(more.waitForExistence(timeout: 10))
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        XCTAssertEqual(search.frame.midY, more.frame.midY, accuracy: 2)
        XCTAssertLessThan(more.frame.maxY, app.windows.firstMatch.frame.height * 0.35)
        keepScreenshot("Expanded Stories standard header controls")
        search.tapCenter()
        XCTAssertTrue(app.textViews["Search posts"].waitForExistence(timeout: 10))
        app.labeledButton("Back").tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testSideBySideSurvivesDuoFolding() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["HARMONIC_DUO_FOLD_TEST"] == "1")
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openLinkPostFromDebugSettings()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        waitForCommentsBeforeDraggingSheet()
        let split = app.labeledButton("Read side by side")
        if !split.exists { app.labeledButton("More options").tapCenter() }
        XCTAssertTrue(split.waitForExistence(timeout: 5))
        split.tapCenter()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 15))
        let exit = app.labeledButton("Exit side by side")
        XCTAssertTrue(exit.waitForExistence(timeout: 10))
        let window = app.windows.firstMatch
        func appBack() -> XCUIElement {
            let candidates = app.buttons.matching(NSPredicate(format: "label == %@ OR label == %@", "Navigate up", "Back"))
            return candidates.allElementsBoundByIndex.first {
                $0.isHittable && $0.frame.maxY < window.frame.height * 0.35
            } ?? app.labeledButton("Back")
        }
        window.coordinate(withNormalizedOffset: CGVector(dx: 0.22, dy: 0.8)).press(
            forDuration: 0.1,
            thenDragTo: window.coordinate(withNormalizedOffset: CGVector(dx: 0.22, dy: 0.35)),
            withVelocity: .slow, thenHoldForDuration: 0.3
        )
        XCTAssertTrue(exit.exists, "Scrolling comments must keep side by side active")
        print("DUO_SPLIT_READY_CLOSE")
        let closed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            min(window.frame.width, window.frame.height) < 600
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [closed], timeout: 60), .completed)
        XCTAssertTrue(app.otherElements["comment-row"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(appBack().waitForExistence(timeout: 10))
        keepScreenshot("Scrolled side by side comments retained on Duo cover")
        print("DUO_SPLIT_READY_OPEN")
        let opened = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            min(window.frame.width, window.frame.height) >= 600
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [opened], timeout: 60), .completed)
        XCTAssertTrue(app.otherElements["comment-row"].firstMatch.waitForExistence(timeout: 10))
        XCTAssertTrue(appBack().waitForExistence(timeout: 10))
        keepScreenshot("Scrolled comments retained after unfolding Duo")
        appBack().tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 10))
    }

    func testExpandedSettingsSurviveRotationAndFolding() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["HARMONIC_DUO_FOLD_TEST"] == "1")
        try XCTSkipUnless(ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true)
        openSettings()
        app.labeledButton("Post header").tapCenter()
        let preview = app.descendants(matching: .any)["Header preview"]
        XCTAssertTrue(preview.waitForExistence(timeout: 10))
        func waitForLayout(_ marker: String, _ matches: @escaping (CGRect) -> Bool) {
            print(marker)
            let layout = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                matches(self.app.windows.firstMatch.frame)
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [layout], timeout: 60), .completed)
            XCTAssertTrue(preview.waitForExistence(timeout: 10))
        }
        waitForLayout("DUO_QA_READY_PORTRAIT") { $0.width < $0.height && min($0.width, $0.height) >= 600 }
        keepScreenshot("Settings retained after rotating expanded Duo")
        waitForLayout("DUO_QA_READY_CLOSE") { min($0.width, $0.height) < 600 }
        keepScreenshot("Settings retained on Duo cover")
        waitForLayout("DUO_QA_READY_OPEN") { min($0.width, $0.height) >= 600 }
        waitForLayout("DUO_QA_READY_LANDSCAPE") { $0.width > $0.height && min($0.width, $0.height) >= 600 }
        keepScreenshot("Settings retained after unfolding and rotating Duo")
        app.labeledButton("Navigate up").tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testExpandedSettingsCategorySwitching() throws {
        try XCTSkipUnless(min(app.windows.firstMatch.frame.width, app.windows.firstMatch.frame.height) >= 600)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        // Duo's app-level AX frame can retain portrait bounds while its active display is landscape.
        openSettings()
        for _ in 0..<3 {
            for category in ["Stories", "Comments", "Post header", "Appearance", "Web and links"] {
                let row = app.labeledButton(category)
                XCTAssertTrue(row.waitForExistence(timeout: 5))
                row.tapCenter()
                if category == "Post header" {
                    XCTAssertTrue(app.descendants(matching: .any)["Header preview"].waitForExistence(timeout: 5))
                } else {
                    XCTAssertTrue(app.staticTexts[category].waitForExistence(timeout: 5))
                }
                XCTAssertTrue(app.labeledButton("Appearance").exists)
            }
        }
        keepScreenshot("Expanded Settings after repeated detail transitions")
    }

    func testExpandedSideBySideFromStories() throws {
        try XCTSkipUnless(min(app.windows.firstMatch.frame.width, app.windows.firstMatch.frame.height) >= 600)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        // Duo's app-level AX frame can retain portrait bounds while its active display is landscape.
        let hint = app.labeledButton("Got it")
        if hint.waitForExistence(timeout: 2) { hint.tapCenter() }
        openFirstComments()
        let split = app.labeledButton("Read side by side")
        if !split.exists { app.buttons.matching(NSPredicate(format: "label == %@", "More options")).allElementsBoundByIndex.last?.tapCenter() }
        XCTAssertTrue(split.waitForExistence(timeout: 5))
        split.tapCenter()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 15))
        let exit = app.labeledButton("Exit side by side")
        XCTAssertTrue(exit.waitForExistence(timeout: 10))
        XCTAssertLessThan(articleHeader.frame.maxX, app.windows.firstMatch.frame.midX + app.windows.firstMatch.frame.width * 0.1)
        keepScreenshot("Stories replaced by side by side comments")
        exit.tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        keepScreenshot("Stories restored after leaving side by side")
    }

    func testIpadInteractiveBackFromSubmissions() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openFirstComments()
        app.labeledButton("User").tapCenter()
        let submissions = app.labeledButton("Submissions")
        XCTAssertTrue(submissions.waitForExistence(timeout: 15))
        submissions.tapCenter()
        XCTAssertTrue(app.labeledButton("Stories").waitForExistence(timeout: 15))
        keepScreenshot("iPad submissions before interactive back")
        // Slow drags leave enough frames in the simulator recording to inspect the retained feed.
        edgeSwipeBack(endX: 0.85, velocity: .slow)
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        keepScreenshot("iPad feed after interactive back")
    }

    func testIpadPullCommentsFromAdjustedSplit() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openFirstComments()
        let split = app.labeledButton("Read side by side")
        if !split.exists { app.buttons.matching(NSPredicate(format: "label == %@", "More options")).allElementsBoundByIndex.last?.tapCenter() }
        XCTAssertTrue(split.waitForExistence(timeout: 5))
        split.tapCenter()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 20))
        let divider = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "Adjust split ratio")).firstMatch
        XCTAssertTrue(divider.waitForExistence(timeout: 5))
        let start = divider.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        start.press(forDuration: 0.1, thenDragTo: start.withOffset(CGVector(dx: 140, dy: 0)), withVelocity: .slow, thenHoldForDuration: 0.1)
        keepScreenshot("iPad adjusted split before pulling comments")
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 5))
        let handleStart = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        handleStart.press(forDuration: 0.1, thenDragTo: handleStart.withOffset(CGVector(dx: 0, dy: -220)), withVelocity: .slow, thenHoldForDuration: 1)
        keepScreenshot("iPad comments after partial pull")
        // Complete the transfer using the current, observed handle position.
        if handle.exists && handle.isHittable {
            let current = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            current.press(forDuration: 0.1, thenDragTo: app.coordinate(withNormalizedOffset: CGVector(dx: 0.8, dy: 0.04)), withVelocity: .slow, thenHoldForDuration: 0.1)
        }
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        keepScreenshot("iPad comments after completing pull")
    }

    func testIpadTextDialogsKeepWidthWhenKeyboardChanges() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openSettings()
        app.tapVisibleButton("AI features")
        for label in ["System prompt", "Base URL", "API Key"] {
            app.tapVisibleButton(label)
            let field = app.textViews.firstMatch
            XCTAssertTrue(field.waitForExistence(timeout: 5), label)
            field.tapCenter()
            let keyboard = app.keyboards.firstMatch
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in keyboard.exists && keyboard.frame.height > 1 }, object: nil
            )], timeout: 5), .completed, label)
            let focusedWidth = field.frame.width
            keepScreenshot("iPad \(label) with keyboard")
            let hideKeyboard = app.keyboards.buttons.matching(
                NSPredicate(format: "label CONTAINS[c] %@", "Hide keyboard")
            ).firstMatch
            XCTAssertTrue(hideKeyboard.exists, app.keyboards.debugDescription)
            hideKeyboard.tapCenter()
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in !keyboard.exists || keyboard.frame.height < 1 }, object: nil
            )], timeout: 5), .completed)
            keepScreenshot("iPad \(label) without keyboard")
            XCTAssertEqual(field.frame.width, focusedWidth, accuracy: 1,
                           "\(label) must keep its width when focus changes")
            field.tapCenter()
            XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
            XCTAssertEqual(field.frame.width, focusedWidth, accuracy: 1)
            app.labeledButton("Cancel").tapCenter()
            if label == "System prompt" { XCUIDevice.shared.orientation = .portrait }
        }
    }

    func testIpadCancelledSheetPullKeepsHeaderActionsInPlace() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        openLinkPostFromDebugSettings()
        waitForCommentsBeforeDraggingSheet()
        let split = app.labeledButton("Read side by side")
        if !split.exists {
            app.buttons.matching(NSPredicate(format: "label == %@", "More options"))
                .allElementsBoundByIndex.last?.tapCenter()
        }
        XCTAssertTrue(split.waitForExistence(timeout: 5))
        split.tapCenter()
        XCTAssertTrue(app.labeledButton("Exit side by side").waitForExistence(timeout: 10))
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 5))
        let user = app.labeledButton("User")
        XCTAssertTrue(user.waitForExistence(timeout: 5))
        let initialFrame = user.frame
        keepScreenshot("iPad header before cancelled sheet pull")
        for _ in 0..<3 {
            let start = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
            start.press(forDuration: 0.1,
                        thenDragTo: start.withOffset(CGVector(dx: 0, dy: -60)),
                        withVelocity: .slow, thenHoldForDuration: 0.5)
            // Every cancelled pull must return to the same actions and positions.
            XCTAssertEqual(XCTWaiter.wait(for: [XCTNSPredicateExpectation(
                predicate: NSPredicate { _, _ in user.isHittable }, object: nil
            )], timeout: 5), .completed)
            XCTAssertEqual(user.frame.minX, initialFrame.minX, accuracy: 1)
            XCTAssertEqual(user.frame.width, initialFrame.width, accuracy: 1)
        }
        keepScreenshot("iPad header after cancelled sheet pulls")
    }

    func testReportedHttpArticleLoadsInBrowser() throws {
        openDebugSettings()
        let field = app.textViews["HN ID"]
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tapCenter()
        field.typeText("50227400")
        app.labeledButton("Open HN ID").tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        articleHeader.tapCenter()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 20))
        let title = app.webViews.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "MacOS Extensions")).firstMatch
        XCTAssertTrue(title.waitForExistence(timeout: 30))
        XCTAssertFalse(app.staticTexts["Couldn't load this page"].exists)
        keepScreenshot("Reported HTTP article loaded on iPad")
    }

    func testExpandedSideBySideBrowserAndComments() throws {
        try XCTSkipUnless(min(app.windows.firstMatch.frame.width, app.windows.firstMatch.frame.height) >= 600)
        defer { XCUIDevice.shared.orientation = .portrait }
        XCUIDevice.shared.orientation = .landscapeLeft
        // Duo's app-level AX frame can retain portrait bounds while its active display is landscape.
        openLinkPostFromDebugSettings()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        let split = app.labeledButton("Read side by side")
        if !split.exists {
            app.labeledButton("More options").tapCenter()
        }
        XCTAssertTrue(split.waitForExistence(timeout: 5))
        split.tapCenter()
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 15))
        let exit = app.labeledButton("Exit side by side")
        XCTAssertTrue(exit.waitForExistence(timeout: 10))
        XCTAssertLessThan(articleHeader.frame.maxX, app.windows.firstMatch.frame.midX + app.windows.firstMatch.frame.width * 0.1)
        XCTAssertGreaterThan(app.webViews.firstMatch.frame.minX, app.windows.firstMatch.frame.width * 0.25)
        keepScreenshot("Expanded side by side browser and comments")
        exit.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        keepScreenshot("Expanded comments after leaving side by side")
    }

    func testSettingsAndNavigateUp() throws {
        openSettings()
        XCTAssertTrue(app.labeledButton("Appearance").exists)
        XCTAssertTrue(app.labeledButton("Stories").exists)
        XCTAssertTrue(app.labeledButton("Navigate up").waitForExistence(timeout: 10))
        keepScreenshot("Settings List")

        app.labeledButton("Stories").tapCenter()
        XCTAssertTrue(app.staticTexts["Stories"].waitForExistence(timeout: 10))
        let usesSplitSettingsLayout = app.labeledButton("Appearance").isHittable
        if !usesSplitSettingsLayout {
            app.labeledButton("Navigate up").tapCenter()
            XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
            XCTAssertTrue(app.labeledButton("Appearance").exists)
        }

        app.labeledButton("Data").tapCenter()
        XCTAssertTrue(app.staticTexts["Data"].waitForExistence(timeout: 10))
        XCTAssertFalse(
            app.descendants(matching: .any)["Open Hacker News links in Harmonic"].exists,
            "The Android app-link preference should not be shown on iOS"
        )
        if !usesSplitSettingsLayout {
            app.labeledButton("Navigate up").tapCenter()
            XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
        }

        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testSearchAndEdgeSwipeBack() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        app.labeledButton("Search").tapCenter()

        XCTAssertTrue(app.textViews["Search posts"].waitForExistence(timeout: 5))
        keepScreenshot("Story Search")

        edgeSwipeBack()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testLandscapeLayoutAndRestorePortrait() throws {
        let duo = ProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]?.contains("Duo") == true
        // XCUIDevice does not rotate Duo's active cover display.
        // Device Hub must rotate the visible display, as it supplies folding above.
        try XCTSkipUnless(!duo || ProcessInfo.processInfo.environment["HARMONIC_DUO_ROTATION_TEST"] == "1",
                         "Requires an operator to rotate the Duo in Device Hub")
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        defer { XCUIDevice.shared.orientation = .portrait }

        if duo { print("DUO_ROTATE_READY_LANDSCAPE") }
        else { XCUIDevice.shared.orientation = .landscapeLeft }
        let landscape = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.frame.width > self.app.frame.height
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [landscape], timeout: duo ? 60 : 10), .completed,
                       "The application window must relayout to landscape bounds")
        XCTAssertTrue(app.labeledButton("More options").isHittable)
        keepScreenshot("Landscape Top Stories")

        if duo { print("DUO_ROTATE_READY_PORTRAIT") }
        else { XCUIDevice.shared.orientation = .portrait }
        let portrait = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            self.app.frame.width < self.app.frame.height
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [portrait], timeout: duo ? 60 : 10), .completed)
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testDarkThemeUpdatesStatusBarAndRestoresTheme() throws {
        openSettings()
        app.labeledButton("Appearance").tapCenter()

        let themeRow = app.buttons.matching(
            NSPredicate(format: "label BEGINSWITH %@", "Color scheme")
        ).firstMatch
        XCTAssertTrue(themeRow.waitForExistence(timeout: 10))
        themeRow.tapCenter()

        let darkTheme = app.staticTexts["Dark"].firstMatch
        XCTAssertTrue(darkTheme.waitForExistence(timeout: 5))
        darkTheme.tapCenter()
        keepScreenshot("Dark Theme Status Bar")

        // Appearance mode is now a nested screen with Light/System/Dark segments.
        let automaticTheme = app.staticTexts["System"].firstMatch
        XCTAssertTrue(automaticTheme.waitForExistence(timeout: 5))
        automaticTheme.tapCenter()
        app.labeledButton("Navigate up").firstMatch.tapCenter()
        XCTAssertTrue(themeRow.waitForExistence(timeout: 10))
    }

    func testSearchQueryOpensResultsAndKeepsQueryOnBack() throws {
        app.labeledButton("Search").tapCenter()
        let search = app.textViews["Search posts"]
        XCTAssertTrue(search.waitForExistence(timeout: 5))
        search.tapCenter()
        search.typeText("Kotlin\n")
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 30))
        keepScreenshot("Search results for Kotlin")
        firstCommentsButton.tapCenter()
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 15))
        let appBack = app.labeledButton("Navigate up").exists
            ? app.labeledButton("Navigate up") : app.labeledButton("Back")
        appBack.tapCenter()
        // The placeholder stops being the accessibility label once the field has a value.
        let restoredSearch = app.textViews.matching(NSPredicate(format: "value == %@", "Kotlin")).firstMatch
        XCTAssertTrue(restoredSearch.waitForExistence(timeout: 10))
        app.labeledButton("Back").firstMatch.tapCenter()
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
    }

    func testSampleContentNavigationWithoutAccountActions() throws {
        openDebugSettings()
        for label in ["Link post", "Reference links post", "YouTube comment",
                      "Very long comment", "Poll", "Internal HN link"] {
            let sample = app.labeledButton(label)
            for _ in 0..<5 where !sample.isHittable { app.swipeUp(velocity: .slow) }
            XCTAssertTrue(sample.waitForExistence(timeout: 5), label)
            Thread.sleep(forTimeInterval: 1) // A tap during a fling only stops the scroll.
            sample.tapCenter()
            XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 20), label)
            keepScreenshot("Sample content - " + label)
            app.swipeUp()
            keepScreenshot("Scrolled sample - " + label)
            app.labeledButton("Back").firstMatch.tapCenter()
            XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 10))
        }
    }

    func testOpenHistoricalStoryByIdKeepsNavigation() throws {
        openDebugSettings()
        let itemId = app.textViews["HN ID"]
        for _ in 0..<4 where !itemId.isHittable { app.swipeDown() }
        XCTAssertTrue(itemId.waitForExistence(timeout: 5))
        itemId.tapCenter()
        itemId.typeText("1")
        let openItem = app.labeledButton("Open HN ID")
        if UIDevice.current.userInterfaceIdiom == .pad && !openItem.isHittable {
            // The numeric keypad is a popover on iPad. An outside tap dismisses it.
            openItem.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        openItem.tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 20))
        XCTAssertTrue(articleHeader.label.contains("Y Combinator"))
        articleHeader.tapCenter()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 15))
        // Historical HN links also exercise HTTP URLs and native browser error handling.
        Thread.sleep(forTimeInterval: 3)
        keepScreenshot("Historical HTTP article")
        app.labeledButton("Show comments").tapCenter()
        app.labeledButton("Back").firstMatch.tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 10))
    }

    func testArticleSurvivesRotationAndForegroundResume() throws {
        defer { XCUIDevice.shared.orientation = .portrait }
        openFirstComments()
        let title = articleHeader.label
        articleHeader.tapCenter()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 15))
        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        Thread.sleep(forTimeInterval: 4) // Allow simulator rotation and the native browser to settle.
        keepScreenshot("Article after expanding landscape")
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        app.labeledButton("Show comments").tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
        XCTAssertEqual(articleHeader.label, title)
        keepScreenshot("Same comments after resize and resume")
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
        app.completeWelcomeIfNeeded()
        XCTAssertTrue(app.labeledButton("More options").waitForExistence(timeout: 15))
    }

    private func keepScreenshot(_ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testWelcomeAndAboutBranding() throws {
        app.labeledButton("More options").tapCenter()
        XCTAssertTrue(app.labeledButton("Settings").waitForExistence(timeout: 5))
        app.labeledButton("Settings").tapCenter()
        XCTAssertTrue(app.labeledButton("About").waitForExistence(timeout: 10))
        app.tapVisibleButton("About")
        XCTAssertTrue(app.images["Harmonic app icon"].waitForExistence(timeout: 10))
        keepScreenshot("About Harmonic icon")

        app.labeledButton("Navigate up").tapCenter()
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
        app.tapVisibleButton("Debug")
        let welcome = app.labeledButton("Welcome dialog")
        for _ in 0..<4 where !welcome.isHittable { app.swipeUp() }
        XCTAssertTrue(welcome.waitForExistence(timeout: 5))
        let getStarted = app.labeledButton("Get started")
        // A tap during Compose's remaining scroll momentum can only stop the fling.
        // Re-observe the dialog before tapping the row again.
        for _ in 0..<3 {
            welcome.tapCenter()
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
            NSPredicate(format: "label MATCHES %@", "^(Open comments, )?([0-9]+ points, )?[0-9]+ comments?$")
        ).element(boundBy: storyIndex)
        XCTAssertTrue(comments.waitForExistence(timeout: 15))
        comments.tapCenter()
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 15))
        app.labeledButton("User").tapCenter()
        let submissions = app.labeledButton("Submissions")
        if !submissions.waitForExistence(timeout: 5) {
            for _ in 0..<8 where !submissions.exists {
                app.swipeUp()
                if submissions.waitForExistence(timeout: 1) { break }
            }
        }
        XCTAssertTrue(submissions.waitForExistence(timeout: 5))
        submissions.tapCenter()
        XCTAssertTrue(app.labeledButton("Back").waitForExistence(timeout: 15))
        XCTAssertTrue(app.labeledButton("Stories").waitForExistence(timeout: 15))
        let backFrame = app.labeledButton("Back").frame
        let header = app.descendants(matching: .any).matching(
            NSPredicate(format: "label BEGINSWITH %@", "Submissions by ")
        ).firstMatch
        XCTAssertTrue(header.waitForExistence(timeout: 5))
        func checkSafeArea() {
            XCTAssertEqual(app.labeledButton("Back").frame.minY, backFrame.minY, accuracy: 1)
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
        app.labeledButton("Back").tapCenter()
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 10))
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
        app.completeWelcomeIfNeeded()
        XCTAssertTrue(app.labeledButton("More options").waitForExistence(timeout: 15))
    }
    override func tearDownWithError() throws {
        XCUIDevice.shared.orientation = .portrait
        if restoreFixture {
            debugLink()
            if app.labeledButton("Remove bookmark").waitForExistence(timeout: 5) { tap("Remove bookmark") }
            XCTAssertTrue(app.labeledButton("Bookmark").waitForExistence(timeout: 5))
            record("Fixture bookmark restored")
        }
    }
    private func dismissFiles() {
        let cancel = app.labeledButton("Cancel").firstMatch
        if cancel.waitForExistence(timeout: 2) {
            cancel.tapCenter()
            return
        }
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
        app.tapVisibleButton(label)
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
        app.tapVisibleButton("Link post")
        XCTAssertTrue(app.labeledButton("User").waitForExistence(timeout: 15))
    }
    func testWebSettingsAndLandscape() {
        settings("Web and links")
        record("Web settings supported features")
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Block in-app browser ads")).firstMatch.exists)
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Enable reader mode")).firstMatch.exists)
        app.swipeUp()
        record("Web settings links")
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Block in-app browser ads")).firstMatch.exists)
        XCTAssertFalse(app.switches.matching(NSPredicate(format: "label BEGINSWITH %@", "Enable reader mode")).firstMatch.exists)
        XCUIDevice.shared.orientation = .landscapeLeft
        Thread.sleep(forTimeInterval: 4)
        record("Landscape settings")
        if UIDevice.current.userInterfaceIdiom == .phone {
            XCTAssertFalse(app.labeledButton("Appearance").exists && app.labeledButton("Appearance").isHittable)
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
        debugLink()
        try XCTSkipIf(app.labeledButton("Remove bookmark").exists, "Preserve an existing fixture bookmark")
        restoreFixture = true
        tap("Bookmark")
        XCTAssertTrue(app.labeledButton("Remove bookmark").waitForExistence(timeout: 5))
        settings("Data")
        let export = dataRow("Export bookmarks")
        XCTAssertTrue(export.waitForExistence(timeout: 10)); export.tapCenter()
        Thread.sleep(forTimeInterval: 3)
        record("Native bookmark export picker")
        let filename = app.textFields["DOCPicker.filenameTextField"]
        XCTAssertTrue(filename.waitForExistence(timeout: 10))
        XCTAssertTrue((filename.value as? String ?? "").hasPrefix("HarmonicBookmarks"))
        dismissFiles()
        let importRow = dataRow("Import bookmarks")
        XCTAssertTrue(importRow.waitForExistence(timeout: 10)); importRow.tapCenter()
        let add = dataRow("Add to bookmarks")
        XCTAssertTrue(add.waitForExistence(timeout: 5)); add.tapCenter()
        Thread.sleep(forTimeInterval: 3)
        record("Native bookmark import picker")
        XCTAssertTrue(app.collectionViews["File View"].waitForExistence(timeout: 10))
        tap("Cancel")

        debugLink()
        XCTAssertTrue(app.labeledButton("Remove bookmark").waitForExistence(timeout: 10), "Cancelling must preserve bookmarks")
    }
}

// WebKit needs a UIApplication host to launch its renderer. These checks run in the XCTest
// runner application rather than the standalone Kotlin/Native command-line test executable.
@MainActor
final class HarmonicBrowserIntegrationTests: XCTestCase {
    private func browserWithPage() async throws -> IosCommentsWebView {
        let browser = IosCommentsWebView(
            initialUrl: "https://example.com/first",
            archiveDomains: { [] },
            openExternal: { _ in }
        )
        browser.ensureLoaded()
        let view = try XCTUnwrap(browser.view)
        view.stopLoading()
        view.loadHTMLString("<html><body><article>Original text</article></body></html>",
                            baseURL: URL(string: "https://example.com/first"))
        try await waitUntil { !browser.loading && browser.failure == nil }
        return browser
    }

    private func waitUntil(_ condition: () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(15)
        while !condition() && Date() < deadline {
            try await Task.sleep(for: .milliseconds(20))
        }
        XCTAssertTrue(condition(), "Timed out waiting for WebKit")
        if !condition() { throw NSError(domain: "BrowserTestTimeout", code: 1) }
    }

    func testRenderedPageTextUsesLiveDomAndPreservesQuotes() async throws {
        let browser = try await browserWithPage()
        defer { browser.dispose() }
        let view = try XCTUnwrap(browser.view)
        _ = try await view.evaluateJavaScript("document.body.innerText = 'Rendered article: \"hello\" ☃'; true")
        let text: String? = try await withCheckedThrowingContinuation { continuation in
            browser.readPageText(loadIfNeeded: false) { text, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: text) }
            }
        }
        XCTAssertEqual(text, "Rendered article: \"hello\" ☃")
        _ = try await view.evaluateJavaScript("document.body.innerText = 'a'.repeat(300000); true")
        let bounded: String? = try await withCheckedThrowingContinuation { continuation in
            browser.readPageText(loadIfNeeded: false) { text, error in
                if let error { continuation.resume(throwing: error) }
                else { continuation.resume(returning: text) }
            }
        }
        XCTAssertEqual(bounded?.count, 256 * 1024)
    }

    func testBrowserHistoryIsTraversedWithinTheSameView() async throws {
        let browser = IosCommentsWebView(
            initialUrl: "https://example.com/?history=first",
            archiveDomains: { [] }, openExternal: { _ in }
        )
        defer { browser.dispose() }
        browser.ensureLoaded()
        try await waitUntil { !browser.loading }
        XCTAssertNil(browser.failure)
        let view = try XCTUnwrap(browser.view)
        let firstUrl = browser.currentUrl()
        let initialBackCount = view.backForwardList.backList.count
        // loadHTMLString replaces the current document; use two real navigations for history.
        XCTAssertTrue(browser.load(url: "https://example.com/?history=second"))
        try await waitUntil { browser.canGoBack() && !browser.loading }
        XCTAssertNil(browser.failure)
        XCTAssertTrue(browser.currentUrl()?.hasSuffix("history=second") == true)
        browser.goBack()
        try await waitUntil { browser.currentUrl() == firstUrl && !browser.loading }
        XCTAssertTrue(view === browser.view)
        XCTAssertEqual(view.backForwardList.backList.count, initialBackCount)
    }

    func testJavascriptDialogsReturnTheUsersResponse() async throws {
        let browser = try await browserWithPage()
        defer { browser.dispose() }
        let view = try XCTUnwrap(browser.view)
        let confirmation = Task { try await view.evaluateJavaScript("String(confirm('Continue?'))") as? String }
        try await waitUntil { browser.dialog != nil }
        XCTAssertEqual(browser.dialog?.message, "Continue?")
        XCTAssertEqual(browser.dialog?.origin, "example.com")
        browser.finishDialog(result: "")
        let confirmed = try await confirmation.value
        XCTAssertEqual(confirmed, "true")

        let prompt = Task { try await view.evaluateJavaScript("String(prompt('Name?', 'original'))") as? String }
        try await waitUntil { browser.dialog != nil }
        XCTAssertEqual(browser.dialog?.defaultText, "original")
        browser.finishDialog(result: "Harmonic")
        let typed = try await prompt.value
        XCTAssertEqual(typed, "Harmonic")
    }

    func testTargetBlankLoadsInTheExistingBrowser() async throws {
        let browser = try await browserWithPage()
        defer { browser.dispose() }
        let view = try XCTUnwrap(browser.view)
        _ = try await view.evaluateJavaScript("var a=document.createElement('a');a.href='https://example.invalid/popup';a.target='_blank';document.body.appendChild(a);a.click();true")
        try await waitUntil { browser.currentUrl() == "https://example.invalid/popup" }
        XCTAssertTrue(view === browser.view)
    }

    func testWebKitFailureRecoveryAndDelegateCleanup() async throws {
        let browser = try await browserWithPage()
        let view = try XCTUnwrap(browser.view)
        defer { browser.dispose() }
        view.navigationDelegate?.webView?(view, didFailProvisionalNavigation: nil,
                                          withError: NSError(domain: NSURLErrorDomain, code: NSURLErrorCannotFindHost))
        XCTAssertNotNil(browser.failure)
        XCTAssertFalse(browser.loading)
        XCTAssertEqual(browser.currentUrl(), "https://example.com/first")
        browser.reload()
        XCTAssertNil(browser.failure)
        view.navigationDelegate?.webViewWebContentProcessDidTerminate?(view)
        XCTAssertTrue(browser.loading)
        view.navigationDelegate?.webViewWebContentProcessDidTerminate?(view)
        XCTAssertEqual(browser.failure, "This website stopped responding")
        browser.dispose()
        XCTAssertNil(browser.view)
        XCTAssertNil(view.navigationDelegate)
        XCTAssertNil(view.uiDelegate)
    }
}

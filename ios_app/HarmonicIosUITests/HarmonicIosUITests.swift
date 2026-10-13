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
        let screenshot = XCTAttachment(screenshot: app.screenshot())
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
        // The browser back button is in the bottom bar, to the left of refresh.
        let refresh = app.labeledButton("Refresh website")
        let browserBack = app.buttons.matching(identifier: "Back").allElementsBoundByIndex.max { $0.frame.minY < $1.frame.minY }!
        XCTAssertLessThan(browserBack.frame.midX, refresh.frame.midX)
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
        for _ in 0..<4 where !debug.isHittable { app.swipeUp(velocity: .slow) }
        XCTAssertTrue(debug.waitForExistence(timeout: 5))
        debug.tapCenter()
        XCTAssertTrue(app.staticTexts["Debug"].waitForExistence(timeout: 5))
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
        // The header can contain an image button that consumes its own gestures.
        let handle = app.otherElements["comments-sheet-handle"]
        XCTAssertTrue(handle.waitForExistence(timeout: 10))
        let start = handle.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.05, thenDragTo: end)
        XCTAssertTrue(app.labeledButton("Show comments").waitForExistence(timeout: 10))
        XCTAssertTrue(app.webViews.firstMatch.waitForExistence(timeout: 10))
        app.labeledButton("Show comments").tapCenter()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
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

    func testSubmissionsKeepsBackButtonBelowStatusBar() throws {
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        XCTAssertTrue(firstCommentsButton.waitForExistence(timeout: 5))
        firstCommentsButton.tapCenter()

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
        keepScreenshot("Submissions status bar protection")
        XCTAssertGreaterThanOrEqual(back.frame.minY, 24)

        edgeSwipeBack()
        XCTAssertTrue(articleHeader.waitForExistence(timeout: 10))
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
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 15))
        defer { XCUIDevice.shared.orientation = .portrait }

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(storyListHeader.waitForExistence(timeout: 10))
        XCTAssertGreaterThan(
            app.frame.width,
            app.frame.height,
            "The application window must relayout to landscape bounds"
        )
        XCTAssertTrue(app.labeledButton("More options").isHittable)
        keepScreenshot("Landscape Top Stories")

        XCUIDevice.shared.orientation = .portrait
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
        app.labeledButton("Back").firstMatch.tapCenter()
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

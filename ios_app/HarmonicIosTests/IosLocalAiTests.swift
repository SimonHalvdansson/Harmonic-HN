import XCTest
import HarmonicKit
@testable import HarmonicIos

/// Real inference is opt-in: seed litert-test-fixture.litertlm in the app's Application Support.
final class IosLocalAiTests: XCTestCase {
    private var environment: IosLocalAiEnvironment!
    private var directory: URL!
    private var defaults: UserDefaults!
    private var suite: String!

    override func setUpWithError() throws {
        continueAfterFailure = false
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        suite = "harmonic-litert-tests-\(UUID().uuidString)"
        defaults = UserDefaults(suiteName: suite)!
    }

    override func tearDownWithError() throws {
        environment?.close()
        defaults.removePersistentDomain(forName: suite)
        try? FileManager.default.removeItem(at: directory)
    }

    private func createEnvironment(bridge: IosLiteRtSummaryBridge) {
        environment = IosLocalAiEnvironment.companion.create(
            preferences: IosKeyValueStore(defaults: defaults),
            modelsDirectory: directory.appendingPathComponent("models").path,
            cacheDirectory: directory.appendingPathComponent("cache").path,
            userAgent: "Harmonic-iOS-LiteRT-Tests",
            apple: IosNativeLocalSummaryEngine(bridge: UnavailableAppleModel()),
            liteRt: bridge
        )
    }

    private var gemma: LocalModelDefinition {
        environment.models.catalog.first { $0.runtime == .litertLm }!
    }

    private func prepareModel(real: Bool) throws {
        let destination = URL(fileURLWithPath: environment.models.installedPath(model: gemma))
        try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
        if real {
            let fixture = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("litert-test-fixture.litertlm")
            try XCTSkipUnless(FileManager.default.fileExists(atPath: fixture.path), "Seed Gemma E2B in Application Support/litert-test-fixture.litertlm to run inference.")
            try FileManager.default.linkItem(at: fixture, to: destination)
        } else {
            // A sparse file exercises the production size/selection policy without model weights.
            FileManager.default.createFile(atPath: destination.path, contents: nil)
            let file = try FileHandle(forWritingTo: destination)
            try file.truncate(atOffset: UInt64(gemma.sizeBytes))
            try file.close()
        }
        let ready = expectation(description: "Model cache ready")
        environment.models.preload { error in
            XCTAssertNil(error)
            ready.fulfill()
        }
        wait(for: [ready], timeout: 10)
        XCTAssertTrue(environment.models.isDownloaded(model: gemma))
        let selected = expectation(description: "Downloaded model selected")
        environment.models.select(modelId: gemma.id) { result, error in
            XCTAssertNil(error)
            XCTAssertEqual(result?.boolValue, true)
            selected.fulfill()
        }
        wait(for: [selected], timeout: 10)
        XCTAssertTrue(environment.summary.isReady())
    }

    private func summarize(_ article: String) throws -> SummaryResult {
        let completed = expectation(description: "Summary completed")
        var output: SummaryResult?
        var failure: Error?
        environment.summary.summarize(request: SummaryRequest(
            text: article, prompt: nil, model: nil,
            streamResponses: true, useGeminiNanoSummarizationLora: false
        )) { result, error in
            output = result
            failure = error
            completed.fulfill()
        }
        wait(for: [completed], timeout: 600)
        if let failure { throw failure }
        return try XCTUnwrap(output)
    }

    func testCatalogAndDownloadableFallbackWithoutAppleIntelligence() throws {
        createEnvironment(bridge: StubLiteRtBridge())
        XCTAssertEqual(environment.models.catalog.count, 3)
        XCTAssertFalse(environment.models.catalog.contains { $0.runtime == .llamaCpp })
        XCTAssertTrue(environment.models.isRuntimeInstalled(runtime: .litertLm))
        XCTAssertFalse(environment.models.isRuntimeInstalled(runtime: .llamaCpp))
        XCTAssertFalse(environment.summary.isReady())
        let resolved = expectation(description: "Availability resolved")
        environment.summary.availability { result, error in
            XCTAssertNil(error)
            XCTAssertEqual(result?.available, true)
            XCTAssertEqual(result?.downloadableFallbackRequired, true)
            resolved.fulfill()
        }
        wait(for: [resolved], timeout: 10)
        let selected = expectation(description: "Missing model rejected")
        environment.models.select(modelId: gemma.id) { result, error in
            XCTAssertNil(error)
            XCTAssertEqual(result?.boolValue, false)
            selected.fulfill()
        }
        wait(for: [selected], timeout: 10)
    }

    func testSharedSummaryUsesSelectedLiteRtModelAndPreparedInput() throws {
        let bridge = StubLiteRtBridge()
        createEnvironment(bridge: bridge)
        try prepareModel(real: false)
        let result = try summarize(String(repeating: "Article sentence about solar power. ", count: 1_000))
        XCTAssertEqual(result.text, "- Solar power generation increased.")
        XCTAssertTrue(result.debugInfo?.contains("Gemma 4 E2B") == true)
        XCTAssertEqual(bridge.modelPath, environment.models.installedPath(model: gemma))
        XCTAssertLessThan(bridge.text.count, 20_000)
        XCTAssertTrue(bridge.instruction.contains("Summarize"))
    }

    func testNativeRuntimeReportsInvalidModel() throws {
        let callback = NativeCallback(expectation: expectation(description: "Invalid model failed"))
        let service = IosLiteRtSummaryService()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let invalid = directory.appendingPathComponent("invalid.litertlm")
        try Data("invalid model".utf8).write(to: invalid)
        let request = service.start(
            modelPath: invalid.path,
            cacheDirectory: directory.path, contextTokens: 2_048,
            text: "Summarize this article.", instruction: "Summarize.", callback: callback
        )
        wait(for: [callback.finished], timeout: 30)
        XCTAssertNil(callback.summary)
        XCTAssertNotNil(callback.error)
        withExtendedLifetime(request) {}
    }

    func testGemmaProducesRealSummariesOnSimulator() throws {
        createEnvironment(bridge: IosLiteRtSummaryService())
        try prepareModel(real: true)
        let article = """
        The city opened a new public library on Monday after two years of construction.
        The building includes a children's reading room, free computer access, quiet study spaces,
        and a collection of more than fifty thousand books. Solar panels on the roof provide most
        of the library's electricity. Residents can borrow books for three weeks without paying
        membership fees. The library will host weekly reading groups and free classes for adults.
        It is open six days a week and employs twelve librarians.
        """
        // A second request proves the conversation/engine was released and can be recreated.
        for _ in 0..<2 {
            let result = try summarize(article)
            XCTAssertFalse(result.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            XCTAssertTrue(result.text.lowercased().contains("library"), result.text)
            XCTAssertLessThan(result.text.count, 1_000, "The model must follow the concise summary instruction")
            XCTAssertFalse(result.text.contains("What would you like"), result.text)
            XCTAssertTrue(result.debugInfo?.contains("Gemma 4 E2B") == true)
            let attachment = XCTAttachment(string: result.text)
            attachment.name = "Gemma E2B simulator summary"
            attachment.lifetime = .keepAlways
            add(attachment)
        }
    }

    func testCancellationStopsNativeStreamAndAllowsAnotherRequest() throws {
        let service = IosLiteRtSummaryService()
        createEnvironment(bridge: service)
        try prepareModel(real: true)
        let callback = NativeCallback(expectation: expectation(description: "Native stream cancelled"))
        var request: IosLiteRtSummaryTask?
        callback.onProgress = { request?.cancel() }
        request = service.start(
            modelPath: environment.models.installedPath(model: gemma),
            cacheDirectory: directory.appendingPathComponent("cache").path,
            contextTokens: 2_048,
            text: "Explain how solar panels generate electricity.", instruction: "Explain in detail.",
            callback: callback
        )
        wait(for: [callback.finished], timeout: 600)
        XCTAssertGreaterThan(callback.progressCount, 0)
        XCTAssertNotNil(callback.error)
        XCTAssertNil(callback.summary)
        callback.onProgress = nil
        request = nil
        // The same service must drain and release the cancelled conversation before starting again.
        let result = try summarize(String(repeating: "Solar panels turn sunlight into electricity. ", count: 12))
        XCTAssertFalse(result.text.isEmpty)
    }
}

private final class UnavailableAppleModel: IosNativeSummaryBridge {
    func canAttempt() -> Bool { true }
    func isAvailable() -> Bool { false }
    func availabilityMessage() -> String? { "Apple Intelligence unavailable in test" }
    func summarize(text: String, instruction: String, callback: IosNativeSummaryCallback) {
        callback.complete(summary: nil, errorMessage: availabilityMessage())
    }
}

private final class StubLiteRtBridge: IosLiteRtSummaryBridge, IosLiteRtSummaryTask {
    var modelPath = ""
    var text = ""
    var instruction = ""
    func start(modelPath: String, cacheDirectory: String, contextTokens: Int32, text: String, instruction: String, callback: IosLiteRtSummaryCallback) -> IosLiteRtSummaryTask {
        self.modelPath = modelPath
        self.text = text
        self.instruction = instruction
        callback.loaded(milliseconds: 42)
        callback.progress(summary: "- Solar power")
        callback.complete(summary: "- Solar power generation increased.", errorMessage: nil)
        return self
    }
    func cancel() {}
}

private final class NativeCallback: IosLiteRtSummaryCallback {
    let finished: XCTestExpectation
    var summary: String?
    var error: String?
    var progressCount = 0
    var onProgress: (() -> Void)?
    init(expectation: XCTestExpectation) { finished = expectation }
    func loaded(milliseconds: Int64) {}
    func progress(summary: String) {
        progressCount += 1
        onProgress?()
    }
    func complete(summary: String?, errorMessage: String?) {
        self.summary = summary
        error = errorMessage
        finished.fulfill()
    }
}

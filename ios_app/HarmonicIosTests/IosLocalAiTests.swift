import XCTest
import HarmonicKit
@testable import HarmonicIos

final class IosReleasePackagingTests: XCTestCase {
    func testRequiredReasonManifestsAreInTheAppAndEmbeddedRuntime() throws {
        func reasons(at url: URL) throws -> [String: [String]] {
            let plist = try XCTUnwrap(PropertyListSerialization.propertyList(
                from: Data(contentsOf: url), format: nil
            ) as? [String: Any])
            let entries = try XCTUnwrap(plist["NSPrivacyAccessedAPITypes"] as? [[String: Any]])
            return try Dictionary(uniqueKeysWithValues: entries.map {
                (try XCTUnwrap($0["NSPrivacyAccessedAPIType"] as? String),
                 try XCTUnwrap($0["NSPrivacyAccessedAPITypeReasons"] as? [String]))
            })
        }
        let appManifest = try XCTUnwrap(Bundle.main.url(forResource: "PrivacyInfo", withExtension: "xcprivacy"))
        let appReasons = try reasons(at: appManifest)
        XCTAssertEqual(appReasons["NSPrivacyAccessedAPICategoryDiskSpace"], ["E174.1"])
        XCTAssertEqual(appReasons["NSPrivacyAccessedAPICategoryFileTimestamp"], ["C617.1"])
        XCTAssertEqual(appReasons["NSPrivacyAccessedAPICategorySystemBootTime"], ["35F9.1"])

        let frameworks = try XCTUnwrap(Bundle.main.privateFrameworksURL)
        let runtimeReasons = try reasons(at: frameworks.appendingPathComponent("CLiteRTLM.framework/PrivacyInfo.xcprivacy"))
        XCTAssertEqual(runtimeReasons["NSPrivacyAccessedAPICategoryFileTimestamp"], ["C617.1"])
        XCTAssertEqual(runtimeReasons["NSPrivacyAccessedAPICategorySystemBootTime"], ["35F9.1"])
    }
}

final class IosArticleTransportTests: XCTestCase {
    func testReportedHttpArticleCanBeFetchedForExtraction() async throws {
        let url = try XCTUnwrap(URL(string: "http://oldvcr.blogspot.com/2026/10/macos-extensions-pack-for-apple-network.html"))
        let (data, response) = try await URLSession.shared.data(from: url)
        XCTAssertEqual((response as? HTTPURLResponse)?.statusCode, 200)
        XCTAssertTrue(String(decoding: data, as: UTF8.self).contains("MacOS Extensions"))
    }
}

/// Real inference is opt-in: seed litert-test-fixture.litertlm in the app's Application Support.
final class IosLocalAiTests: XCTestCase {
    private var environment: IosLocalAiEnvironment!
    private var directory: URL!

    override func setUpWithError() throws {
        continueAfterFailure = false
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDownWithError() throws {
        environment?.close()
        try? FileManager.default.removeItem(at: directory)
    }

    private func createEnvironment(bridge: IosLiteRtSummaryBridge) {
        environment = IosLocalAiEnvironment.companion.create(
            preferences: InMemoryKeyValueStore(),
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

    func testNewModelDirectoryIsExcludedFromBackupWithoutExcludingUserData() throws {
        createEnvironment(bridge: StubLiteRtBridge())
        let models = directory.appendingPathComponent("models")
        XCTAssertEqual(try models.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        XCTAssertNotEqual(try directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }

    func testExistingDownloadsArePreservedAndExcludedWhenReopeningStorage() throws {
        var models = directory.appendingPathComponent("models")
        try FileManager.default.createDirectory(at: models, withIntermediateDirectories: true)
        let partial = models.appendingPathComponent("existing.download")
        let contents = Data("partial model download".utf8)
        try contents.write(to: partial)
        var values = URLResourceValues()
        values.isExcludedFromBackup = false
        try models.setResourceValues(values)

        createEnvironment(bridge: StubLiteRtBridge())
        XCTAssertEqual(try models.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        XCTAssertEqual(try Data(contentsOf: partial), contents)
        XCTAssertNotEqual(try directory.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
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
            streamResponses: true, useGeminiNanoSummarizationLora: false, preserveInput: false,
            reserveDiscussionSpace: false, enableThinking: false
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
        XCTAssertEqual(environment.models.catalog.count, 8)
        XCTAssertTrue(environment.models.catalog.contains { $0.id == LocalModelCatalog.shared.embeddingModel.id && !$0.supportsSummarization })
        XCTAssertTrue(environment.models.catalog.filter { $0.downloadable }.allSatisfy { $0.runtime == .litertLm })
        XCTAssertTrue(environment.models.isRuntimeInstalled(runtime: .litertLm))
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

final class IosArticleEmbeddingTests: XCTestCase {
    private var service: IosArticleEmbeddingService!
    private var directory: URL!

    override func setUpWithError() throws {
        service = IosArticleEmbeddingService()
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        let closed = NativeEmbeddingCallback(expectation: expectation(description: "Embedding engine closed"))
        service.close(callback: closed)
        wait(for: [closed.finished], timeout: 30)
        XCTAssertNil(closed.error)
        service = nil
        try FileManager.default.removeItem(at: directory)
    }

    private func load(_ path: String) throws {
        let loaded = NativeEmbeddingCallback(expectation: expectation(description: "Embedding model loaded"))
        service.load(modelPath: path, cacheDirectory: directory.path, maxInputTokens: 256, callback: loaded)
        wait(for: [loaded.finished], timeout: 120)
        if let error = loaded.error { throw NSError(domain: "EmbeddingTest", code: 1, userInfo: [NSLocalizedDescriptionKey: error]) }
    }

    private func embed(_ text: String) throws -> [Float] {
        let completed = NativeEmbeddingCallback(expectation: expectation(description: "Text embedded"))
        service.embed(text: text, callback: completed)
        wait(for: [completed.finished], timeout: 120)
        XCTAssertNil(completed.error)
        let vector = try XCTUnwrap(completed.embedding)
        return (0..<vector.size).map { vector.get(index: $0) }
    }

    func testMissingAndInvalidModelReturnErrorsWithoutCrashing() throws {
        XCTAssertThrowsError(try load(directory.appendingPathComponent("missing.litertlm").path))
        let invalid = directory.appendingPathComponent("invalid.litertlm")
        try Data("invalid model".utf8).write(to: invalid)
        XCTAssertThrowsError(try load(invalid.path))
        let completed = NativeEmbeddingCallback(expectation: expectation(description: "Unloaded engine rejected"))
        service.embed(text: "Text", callback: completed)
        wait(for: [completed.finished], timeout: 30)
        XCTAssertNotNil(completed.error)
        XCTAssertNil(completed.embedding)
    }

    func testEmbeddingGemmaProducesNormalizedSemanticVectorsAndReloads() throws {
        let fixture = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("embedding-test-fixture.litertlm")
        try XCTSkipUnless(FileManager.default.fileExists(atPath: fixture.path), "Seed EmbeddingGemma 2 in Application Support/embedding-test-fixture.litertlm to run native inference.")
        try load(fixture.path)
        let title = "The Rust compiler improves memory safety"
        let text = ArticleCategories.shared.input(title: title, description: "A new release of the Rust programming language.")
        let first = try embed(text)
        let unrelated = try embed(ArticleCategories.shared.input(title: "Astronomers discover a new planet", description: "A distant world orbits a nearby star."))
        XCTAssertEqual(first.count, 768)
        XCTAssertTrue(first.allSatisfy(\.isFinite))
        XCTAssertEqual(first.reduce(0) { $0 + $1 * $1 }, 1, accuracy: 0.001)
        try load(fixture.path) // Replacing a loaded model releases the previous native engine.
        let repeated = try embed(text)
        let sameSimilarity = zip(first, repeated).reduce(Float(0)) { $0 + $1.0 * $1.1 }
        let unrelatedSimilarity = zip(first, unrelated).reduce(Float(0)) { $0 + $1.0 * $1.1 }
        XCTAssertEqual(sameSimilarity, 1, accuracy: 0.001)
        XCTAssertLessThan(unrelatedSimilarity, sameSimilarity - 0.05)
        let attachment = XCTAttachment(string: "EmbeddingGemma 2: 768 dimensions; same-input similarity \(sameSimilarity); unrelated-topic similarity \(unrelatedSimilarity)")
        attachment.name = "Real iOS category embeddings"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

private final class NativeEmbeddingCallback: IosArticleEmbeddingCallback {
    let finished: XCTestExpectation
    var embedding: KotlinFloatArray?
    var error: String?
    init(expectation: XCTestExpectation) { finished = expectation }
    func complete(embedding: KotlinFloatArray?, errorMessage: String?) {
        self.embedding = embedding
        error = errorMessage
        finished.fulfill()
    }
}

final class IosFoundationModelsCancellationTests: XCTestCase {
    func testCancellationStopsGenerationAndAllowsAnotherRequest() async {
        let started = expectation(description: "Native generation started")
        let cancelled = expectation(description: "Native generation cancelled")
        let bridge = IosFoundationModelsSummaryBridge { text, _ in
            if text == "first" {
                started.fulfill()
                do {
                    try await Task.sleep(for: .seconds(60))
                    XCTFail("Cancelled generation continued running")
                } catch {
                    XCTAssertTrue(error is CancellationError)
                    cancelled.fulfill()
                    throw error
                }
            }
            return "New summary"
        }
        let first = AppleSummaryCallback(finished: expectation(description: "Cancelled request completed"))
        let task = bridge.summarize(text: "first", instruction: "Summarize", callback: first)
        await fulfillment(of: [started], timeout: 5)
        task.cancel()
        await fulfillment(of: [cancelled, first.finished], timeout: 5)
        XCTAssertNil(first.summary)
        XCTAssertNotNil(first.error)

        let next = AppleSummaryCallback(finished: expectation(description: "Next request completed"))
        let nextTask = bridge.summarize(text: "next", instruction: "Summarize", callback: next)
        await fulfillment(of: [next.finished], timeout: 5)
        XCTAssertEqual(next.summary, "New summary")
        XCTAssertNil(next.error)
        withExtendedLifetime(nextTask) {}
    }
}

private final class AppleSummaryCallback: IosNativeSummaryCallback {
    let finished: XCTestExpectation
    var summary: String?
    var error: String?

    init(finished: XCTestExpectation) { self.finished = finished }

    func complete(summary: String?, errorMessage: String?) {
        self.summary = summary
        error = errorMessage
        finished.fulfill()
    }
}

private final class UnavailableAppleModel: IosNativeSummaryBridge, IosNativeSummaryTask {
    func canAttempt() -> Bool { true }
    func isAvailable() -> Bool { false }
    func availabilityMessage() -> String? { "Apple Intelligence unavailable in test" }
    func summarize(text: String, instruction: String, callback: IosNativeSummaryCallback) -> IosNativeSummaryTask {
        callback.complete(summary: nil, errorMessage: availabilityMessage())
        return self
    }
    func cancel() {}
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

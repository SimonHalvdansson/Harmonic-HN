import XCTest
import Foundation
import Network
import HarmonicKit
@testable import HarmonicIos

final class IosBackgroundModelTransfersTests: XCTestCase {
    private var directory: URL!
    private var transfers: IosBackgroundModelTransfers?
    private var server: ModelDownloadTestServer?

    override func setUpWithError() throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let transfers {
            let stopped = expectation(description: "Fixture transfers stopped")
            transfers.cancel(modelId: "test-model") { stopped.fulfill() }
            wait(for: [stopped], timeout: 5)
            transfers.invalidate()
        }
        server?.stop()
        try? FileManager.default.removeItem(at: directory)
    }

    func testFullDownloadInstallsAndRemovesResumeState() throws {
        let model = try startServer { _ in (200, "", Foundation.Data([1, 2, 3, 4])) }
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .finished && !self.transfers!.isActive(modelId: model.id) }
        XCTAssertEqual(try Foundation.Data(contentsOf: destination(model)), Foundation.Data([1, 2, 3, 4]))
        XCTAssertFalse(FileManager.default.fileExists(atPath: resumePath(model)))
    }

    func testExistingForegroundPartialUsesRangeAndAppends() throws {
        let model = try startServer { request in
            XCTAssertTrue(request.lowercased().contains("range: bytes=2-"))
            return (206, "Content-Range: bytes 2-3/4\r\n", Foundation.Data([3, 4]))
        }
        try seedPartial(model)
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .finished }
        XCTAssertEqual(try Foundation.Data(contentsOf: destination(model)), Foundation.Data([1, 2, 3, 4]))
    }

    func testServerIgnoringRangeReplacesThePartial() throws {
        let model = try startServer { _ in (200, "", Foundation.Data([4, 3, 2, 1])) }
        try seedPartial(model)
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .finished }
        XCTAssertEqual(try Foundation.Data(contentsOf: destination(model)), Foundation.Data([4, 3, 2, 1]))
    }

    func testHttpErrorCannotInstallAnErrorPageOfTheExpectedSize() throws {
        let model = try startServer { _ in (403, "", Foundation.Data([1, 2, 3, 4])) }
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .failed }
        XCTAssertFalse(FileManager.default.fileExists(atPath: destination(model).path))
    }

    func testRepeatedEnqueueCreatesOnlyOneTransfer() throws {
        let requested = expectation(description: "Only one request")
        requested.assertForOverFulfill = true
        let model = try startServer(delay: 0.1) { _ in
            requested.fulfill()
            return (200, "", Foundation.Data([1, 2, 3, 4]))
        }
        transfers!.enqueue(model: model)
        transfers!.enqueue(model: model)
        wait(for: [requested], timeout: 5)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .finished }
    }

    func testInterruptedTransferStateIsRecoveredAndRetryInstalls() throws {
        let requested = expectation(description: "First request")
        var first = true
        let model = try startServer(delay: 0.5) { _ in
            if first { first = false; requested.fulfill() }
            return (200, "", Foundation.Data([1, 2, 3, 4]))
        }
        transfers!.enqueue(model: model)
        wait(for: [requested], timeout: 5)
        // Preserve the state that would remain if the process disappeared during this task.
        let savedURL = URL(fileURLWithPath: resumePath(model))
        let saved = try Foundation.Data(contentsOf: savedURL)
        let cancelled = expectation(description: "Old test session stopped")
        transfers!.cancel(modelId: model.id) { cancelled.fulfill() }
        wait(for: [cancelled], timeout: 5)
        transfers!.invalidate()
        try saved.write(to: savedURL)
        transfers = IosBackgroundModelTransfers(directory: directory, identifier: "test-relaunch",
            configuration: .ephemeral, catalog: [model])
        waitUntil { !self.transfers!.isActive(modelId: model.id) }
        XCTAssertEqual(transfers!.work(modelId: model.id)?.state, .failed)
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .finished }
        XCTAssertEqual(try Foundation.Data(contentsOf: destination(model)), Foundation.Data([1, 2, 3, 4]))
    }

    func testWrongSizeCannotInstall() throws {
        let model = try startServer { _ in (200, "", Foundation.Data([1, 2])) }
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .failed }
        XCTAssertFalse(FileManager.default.fileExists(atPath: destination(model).path))
    }

    func testSpaceCheckCreditsSavedOsBytesOnlyWithMatchingResumeData() throws {
        let model = try startServer { _ in XCTFail("Preflight must not start a transfer"); return (200, "", Foundation.Data()) }
        try saveResume(model, offset: 0, received: 3, data: Foundation.Data([1]))
        XCTAssertEqual(transfers!.requiredSpaceBytes(model: model, partialFileBytes: 0), 1)
        try saveResume(model, offset: 0, received: 3, data: nil)
        XCTAssertEqual(transfers!.requiredSpaceBytes(model: model, partialFileBytes: 0), 4)
        try saveResume(model, offset: 0, received: 3, data: Foundation.Data([1]), url: "https://old.example/model")
        XCTAssertEqual(transfers!.requiredSpaceBytes(model: model, partialFileBytes: 0), 4)
    }

    func testResumingLegacyRemainderIncludesInstallationCopyAndRejectsChangedPartial() throws {
        let model = try startServer { _ in (200, "", Foundation.Data([1, 2, 3, 4])) }
        try saveResume(model, offset: 2, received: 3, data: Foundation.Data([1]))
        XCTAssertEqual(transfers!.requiredSpaceBytes(model: model, partialFileBytes: 2), 3)
        XCTAssertEqual(transfers!.requiredSpaceBytes(model: model, partialFileBytes: 0), 4)
    }

    func testNativeStartChecksPeakSpaceBeforeCreatingARequest() throws {
        let model = try startServer(availableBytes: {
            LocalModelDownloadSpacePolicy.shared.withSafetyBuffer(requiredBytes: 3)
        }) { _ in
            XCTFail("Insufficient installation space must prevent the request")
            return (206, "Content-Range: bytes 2-3/4\r\n", Foundation.Data([3, 4]))
        }
        try seedPartial(model)
        transfers!.enqueue(model: model)
        waitUntil { self.transfers!.work(modelId: model.id)?.state == .failed }
        XCTAssertFalse(transfers!.isActive(modelId: model.id))
        XCTAssertTrue(transfers!.work(modelId: model.id)!.error.contains("free space"))
        XCTAssertEqual(try Foundation.Data(contentsOf: destination(model).appendingPathExtension("download")), Foundation.Data([1, 2]))
    }

    private func saveResume(_ model: LocalModelDefinition, offset: Int64, received: Int64,
                            data: Foundation.Data?, url: String? = nil) throws {
        let record = IosBackgroundModelTransfers.Record(modelId: model.id, url: url ?? model.url,
            fileName: model.fileName, size: model.sizeBytes, offset: offset, resumed: false)
        let saved = IosBackgroundModelTransfers.SavedTransfer(record: record, resumeData: data,
            receivedBytes: received, error: "Interrupted")
        let target = URL(fileURLWithPath: resumePath(model))
        try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(saved).write(to: target)
    }

    func testCancelWaitsForTaskAndDiscardsResumeState() throws {
        let requested = expectation(description: "Request reached server")
        let model = try startServer(delay: 0.5) { _ in
            requested.fulfill()
            return (200, "", Foundation.Data([1, 2, 3, 4]))
        }
        transfers!.enqueue(model: model)
        wait(for: [requested], timeout: 5)
        let cancelled = expectation(description: "Native cancellation completed")
        transfers!.cancel(modelId: model.id) {
            cancelled.fulfill()
        }
        wait(for: [cancelled], timeout: 5)
        XCTAssertFalse(transfers!.isActive(modelId: model.id))
        XCTAssertNil(transfers!.work(modelId: model.id))
        XCTAssertFalse(FileManager.default.fileExists(atPath: resumePath(model)))
        XCTAssertFalse(FileManager.default.fileExists(atPath: destination(model).path))
    }

    func testBackgroundCompletionWaitsForFinishedEventsAndRunsOnMain() throws {
        _ = try startServer { _ in (200, "", Foundation.Data([1, 2, 3, 4])) }
        let completed = expectation(description: "Background events acknowledged")
        transfers!.handleBackgroundEvents(identifier: "test-download") {
            XCTAssertTrue(Thread.isMainThread)
            completed.fulfill()
        }
        // The production delegate receives this only after file and task callbacks have finished.
        transfers!.urlSessionDidFinishEvents(forBackgroundURLSession: URLSession.shared)
        wait(for: [completed], timeout: 5)
    }

    private func startServer(delay: TimeInterval = 0, availableBytes: (() -> Int64)? = nil,
                             response: @escaping (String) -> (Int, String, Foundation.Data)) throws -> LocalModelDefinition {
        server = try ModelDownloadTestServer(delay: delay, response: response)
        let model = LocalModelDefinition(id: "test-model", displayName: "Test", parameterSize: "",
            quantization: "", brand: .google, fileName: "model.litertlm", url: server!.url,
            sizeBytes: 4, downloadable: true, runtime: .litertLm, contextTokens: 16,
            supportsSummarization: true)
        transfers = IosBackgroundModelTransfers(directory: directory, identifier: "test-download",
            configuration: .ephemeral, catalog: [model], availableBytes: availableBytes)
        waitUntil { !self.transfers!.isActive(modelId: model.id) }
        return model
    }

    private func resumePath(_ model: LocalModelDefinition) -> String {
        IosModelDownloadFiles(rootDirectory: directory.path).resumeDataPath(model: model)
    }

    private func destination(_ model: LocalModelDefinition) -> URL {
        URL(fileURLWithPath: resumePath(model)).deletingLastPathComponent().appendingPathComponent(model.fileName)
    }

    private func seedPartial(_ model: LocalModelDefinition) throws {
        let final = destination(model)
        try FileManager.default.createDirectory(at: final.deletingLastPathComponent(), withIntermediateDirectories: true)
        try Foundation.Data([1, 2]).write(to: final.appendingPathExtension("download"))
    }

    private func waitUntil(_ condition: @escaping () -> Bool) {
        let ready = expectation(for: NSPredicate { _, _ in condition() }, evaluatedWith: nil)
        wait(for: [ready], timeout: 10)
    }
}

/// A loopback HTTP fixture exercises real URLSession file delivery without external downloads.
private final class ModelDownloadTestServer {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "harmonic.download-test-server")
    private var connections: [NWConnection] = []
    let url: String

    init(delay: TimeInterval, response: @escaping (String) -> (Int, String, Foundation.Data)) throws {
        listener = try NWListener(using: .tcp, on: .any)
        let ready = DispatchSemaphore(value: 0)
        listener.stateUpdateHandler = { state in
            if case .ready = state { ready.signal() }
            if case .failed = state { ready.signal() }
        }
        listener.start(queue: queue)
        guard ready.wait(timeout: .now() + 5) == .success, let port = listener.port else {
            listener.cancel()
            throw NSError(domain: "DownloadTestServer", code: 1)
        }
        url = "http://127.0.0.1:\(port.rawValue)/model"
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else { connection.cancel(); return }
            self.connections.append(connection)
            connection.start(queue: self.queue)
            self.receive(connection, accumulated: Foundation.Data(), delay: delay, response: response)
        }
    }

    private func receive(_ connection: NWConnection, accumulated: Foundation.Data, delay: TimeInterval,
                         response: @escaping (String) -> (Int, String, Foundation.Data)) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { [weak self] data, _, done, error in
            guard let self, error == nil else { connection.cancel(); return }
            var request = accumulated
            if let data { request.append(data) }
            let text = String(decoding: request, as: UTF8.self)
            guard text.contains("\r\n\r\n") else {
                if done { connection.cancel() }
                else { self.receive(connection, accumulated: request, delay: delay, response: response) }
                return
            }
            let (status, extra, body) = response(text)
            let headers = "HTTP/1.1 \(status) Test\r\nContent-Length: \(body.count)\r\n\(extra)Connection: close\r\n\r\n"
            self.queue.asyncAfter(deadline: .now() + delay) {
                connection.send(content: Foundation.Data(headers.utf8) + body, completion: .contentProcessed { _ in connection.cancel() })
            }
        }
    }

    func stop() {
        listener.cancel()
        queue.sync { connections.forEach { $0.cancel() } }
    }
}

import Foundation
import HarmonicKit

/// One app-wide session, including launches where UIKit never creates a scene.
final class IosBackgroundModelTransfers: NSObject, LocalModelTransferScheduler, URLSessionDownloadDelegate {
    static let sessionIdentifier = "com.simon.harmonichackernews.model-downloads"
    static let shared = IosBackgroundModelTransfers(
        directory: harmonicFilesDirectory.appendingPathComponent("local_ai_models"),
        identifier: sessionIdentifier + (harmonicIsUiTesting ? ".ui-tests" : "")
    )

    struct Record: Codable {
        let modelId: String
        let url: String
        let fileName: String
        let size: Int64
        let offset: Int64
        let resumed: Bool

        func matches(_ model: LocalModelDefinition) -> Bool {
            modelId == model.id && url == model.url && fileName == model.fileName && size == model.sizeBytes
        }
    }

    struct SavedTransfer: Codable {
        let record: Record
        let resumeData: Data?
        let receivedBytes: Int64
        let error: String
    }

    private struct DownloadPlan {
        let offset: Int64
        let receivedBytes: Int64
        let resumeData: Data?
        let requiredBytes: Int64
    }

    private let worker = DispatchQueue(label: "com.simon.harmonic.model-downloads", qos: .utility)
    private let stateLock = NSLock()
    private var snapshots: [String: LocalModelWorkSnapshot] = [:]
    private var active: Set<String> = []
    private var cancelRequested: Set<String> = []
    private var restoring = true
    private var observer: (() -> KotlinUnit)?
    private let files: IosModelDownloadFiles
    private let models: [String: LocalModelDefinition]
    private let identifier: String
    private let availableBytes: () -> Int64
    private var session: URLSession!
    // Everything below is confined to the serial delegate worker.
    private var restored = false
    private var pending: [() -> Void] = []
    private var tasks: [String: URLSessionDownloadTask] = [:]
    private var lastProgress: [String: TimeInterval] = [:]
    private var completedTasks: Set<Int> = []
    private var cancellations: [String: [() -> KotlinUnit]] = [:]
    private var installed: Set<String> = []
    private var backgroundCompletion: (() -> Void)?
    private var eventsFinished = false

    init(directory: URL, identifier: String, configuration: URLSessionConfiguration? = nil,
         catalog: [LocalModelDefinition] = LocalModelCatalog.shared.models,
         availableBytes: (() -> Int64)? = nil) {
        self.identifier = identifier
        self.availableBytes = availableBytes ?? {
            guard let attributes = try? FileManager.default.attributesOfFileSystem(forPath: directory.path),
                  let bytes = attributes[.systemFreeSize] as? NSNumber else { return 0 }
            return bytes.int64Value
        }
        files = IosModelDownloadFiles(rootDirectory: directory.path)
        models = Dictionary(uniqueKeysWithValues: catalog
            .filter { $0.downloadable && $0.runtime == .litertLm }.map { ($0.id, $0) })
        super.init()
        // The daemon may finish a download while the phone is locked after its first unlock.
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true,
            attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        var root = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? root.setResourceValues(values)
        let config = configuration ?? URLSessionConfiguration.background(withIdentifier: identifier)
        if configuration == nil {
            config.sessionSendsLaunchEvents = true
            config.isDiscretionary = false
            config.waitsForConnectivity = true
        }
        config.httpMaximumConnectionsPerHost = 2
        let queue = OperationQueue()
        queue.maxConcurrentOperationCount = 1
        queue.underlyingQueue = worker
        session = URLSession(configuration: config, delegate: self, delegateQueue: queue)
        session.getAllTasks { [weak self] tasks in
            guard let self else { return }
            self.worker.async { self.restore(tasks) }
        }
    }

    func work(modelId: String) -> LocalModelWorkSnapshot? {
        stateLock.lock()
        defer { stateLock.unlock() }
        return snapshots[modelId]
    }

    func isActive(modelId: String) -> Bool {
        stateLock.lock()
        defer { stateLock.unlock() }
        // Prevent shared storage preparation/removal from racing restored OS tasks.
        return restoring || active.contains(modelId)
    }

    func setObserver(observer: @escaping () -> KotlinUnit) {
        stateLock.lock()
        self.observer = observer
        stateLock.unlock()
        notify()
    }

    func enqueue(model: LocalModelDefinition) {
        stateLock.lock()
        let inserted = active.insert(model.id).inserted
        stateLock.unlock()
        guard inserted else { return }
        publish(model.id, .waiting)
        worker.async {
            self.afterRestoration { self.start(model, allowResume: true) }
        }
    }

    func requiredSpaceBytes(model: LocalModelDefinition, partialFileBytes: Int64) -> Int64 {
        // The shared lifecycle calls this off Main, before applying its usual safety buffer.
        worker.sync { downloadPlan(model, partialBytes: partialFileBytes, allowResume: true).requiredBytes }
    }

    private func downloadPlan(_ model: LocalModelDefinition, partialBytes: Int64, allowResume: Bool) -> DownloadPlan {
        let saved = allowResume ? readSaved(model) : nil
        let partial = partialBytes >= 0 && partialBytes < model.sizeBytes ? partialBytes : 0
        let canResume = saved.map {
            $0.resumeData?.isEmpty == false &&
            ($0.record.offset == 0 || $0.record.offset == partial) &&
            $0.record.offset >= 0 && $0.record.offset < model.sizeBytes &&
            $0.receivedBytes >= $0.record.offset && $0.receivedBytes <= model.sizeBytes
        } ?? false
        let offset = canResume ? saved!.record.offset : partial
        let received = canResume ? saved!.receivedBytes : offset
        return DownloadPlan(offset: offset, receivedBytes: received,
            resumeData: canResume ? saved!.resumeData : nil,
            requiredBytes: LocalModelDownloadSpacePolicy.shared.backgroundFileBytes(
                modelBytes: model.sizeBytes, appendOffset: offset, receivedBytes: received))
    }

    func cancel(modelId: String, onCancelled: @escaping () -> KotlinUnit) {
        stateLock.lock()
        cancelRequested.insert(modelId)
        stateLock.unlock()
        worker.async {
            self.afterRestoration {
                self.cancellations[modelId, default: []].append(onCancelled)
                if let task = self.tasks[modelId] {
                    // Wait for didComplete before allowing shared storage to delete files.
                    task.cancel()
                } else {
                    self.finishCancellation(modelId)
                }
            }
        }
    }

    func reset() {
        stateLock.lock()
        if active.isEmpty && !restoring { snapshots.removeAll() }
        stateLock.unlock()
        notify()
    }

    func handleBackgroundEvents(identifier: String, completion: @escaping () -> Void) {
        guard identifier == self.identifier else { completion(); return }
        worker.async {
            self.backgroundCompletion = completion
            self.finishBackgroundEventsIfReady()
        }
    }

    private func afterRestoration(_ action: @escaping () -> Void) {
        if restored { action() } else { pending.append(action) }
    }

    private func restore(_ restoredTasks: [URLSessionTask]) {
        for model in models.values {
            if let saved = readSaved(model), snapshotsForWorker(model.id) == nil {
                publish(model.id, .failed, bytes: saved.receivedBytes,
                    error: saved.error.isEmpty ? "Download interrupted. Retry to resume." : saved.error)
            }
        }
        for task in restoredTasks {
            guard !completedTasks.contains(task.taskIdentifier), task.state != .completed,
                  let download = task as? URLSessionDownloadTask,
                  let record = record(for: task), let model = models[record.modelId],
                  record.matches(model) else {
                task.cancel()
                continue
            }
            if let existing = tasks[model.id], existing.taskIdentifier != task.taskIdentifier {
                task.cancel()
                continue
            }
            tasks[model.id] = download
            setActive(model.id, true)
            publish(model.id, .running, bytes: record.offset + max(0, task.countOfBytesReceived))
            if task.state == .suspended { task.resume() }
        }
        restored = true
        stateLock.lock()
        restoring = false
        stateLock.unlock()
        let actions = pending
        pending.removeAll()
        actions.forEach { $0() }
        notify()
    }

    private func start(_ model: LocalModelDefinition, allowResume: Bool) {
        guard let current = models[model.id], current.url == model.url,
              current.fileName == model.fileName, current.sizeBytes == model.sizeBytes else {
            setActive(model.id, false)
            publish(model.id, .failed, error: "This model is no longer in the download catalog.")
            return
        }
        if tasks[model.id] != nil { return }
        let partial = files.partialBytes(model: model)
        let plan = downloadPlan(model, partialBytes: partial, allowResume: allowResume)
        let required = LocalModelDownloadSpacePolicy.shared.withSafetyBuffer(requiredBytes: plan.requiredBytes)
        // Recheck here too: an expired resume token can restart without going through the
        // shared lifecycle, and disk availability may change between preflight and enqueue.
        guard availableBytes() >= required else {
            setActive(model.id, false)
            publish(model.id, .failed, bytes: plan.receivedBytes,
                error: "Not enough free space. Free space and retry the model download.")
            return
        }
        let resume = plan.resumeData
        let offset = plan.offset
        let record = Record(modelId: model.id, url: model.url, fileName: model.fileName,
            size: model.sizeBytes, offset: offset, resumed: resume != nil)
        let task: URLSessionDownloadTask
        if let resume {
            task = session.downloadTask(withResumeData: resume)
        } else {
            guard let url = URL(string: model.url), url.scheme == "https" || url.scheme == "http" else {
                setActive(model.id, false)
                publish(model.id, .failed, error: "Invalid model download URL.")
                return
            }
            var request = URLRequest(url: url)
            request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
            if offset > 0 { request.setValue("bytes=\(offset)-", forHTTPHeaderField: "Range") }
            task = session.downloadTask(with: request)
        }
        do {
            task.taskDescription = String(decoding: try JSONEncoder().encode(record), as: UTF8.self)
            try save(SavedTransfer(record: record, resumeData: resume, receivedBytes: plan.receivedBytes, error: ""), model)
            tasks[model.id] = task
            setActive(model.id, true)
            task.resume()
        } catch {
            task.cancel()
            setActive(model.id, false)
            publish(model.id, .failed, error: error.localizedDescription)
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        guard let record = record(for: downloadTask), !isCancellationRequested(record.modelId),
              tasks[record.modelId] == nil || tasks[record.modelId]?.taskIdentifier == downloadTask.taskIdentifier else { return }
        let offset = (downloadTask.response as? HTTPURLResponse)?.statusCode == 200 ? 0 : record.offset
        let bytes = min(record.size, offset + totalBytesWritten)
        let previous = snapshotsForWorker(record.modelId)
        let now = ProcessInfo.processInfo.systemUptime
        // Avoid refreshing shared model storage for every network packet.
        if previous?.state != .running || bytes == record.size ||
            now - (lastProgress[record.modelId] ?? 0) >= 0.5 {
            lastProgress[record.modelId] = now
            publish(record.modelId, .running, bytes: bytes)
        }
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {
        guard let record = record(for: downloadTask), let model = models[record.modelId],
              record.matches(model), !isCancellationRequested(model.id),
              tasks[model.id] == nil || tasks[model.id]?.taskIdentifier == downloadTask.taskIdentifier else { return }
        guard let response = downloadTask.response as? HTTPURLResponse,
              response.statusCode == 200 || response.statusCode == 206 else {
            publish(model.id, .failed, error: "Model server returned HTTP \((downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0).")
            return
        }
        // Must consume the OS temporary file before returning from this delegate call.
        // Shared storage validates size and appends legacy .download files only for HTTP 206.
        let error = files.install(model: model, temporaryPath: location.path,
            appendOffset: response.statusCode == 206 ? record.offset : 0)
        if let error {
            publish(model.id, .failed, error: error)
        } else {
            installed.insert(model.id)
            removeSaved(model)
            publish(model.id, .finished, bytes: model.sizeBytes)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        if !restored { completedTasks.insert(task.taskIdentifier) }
        guard let record = record(for: task), let model = models[record.modelId], record.matches(model) else { return }
        if let current = tasks[model.id], current.taskIdentifier != task.taskIdentifier { return }
        tasks.removeValue(forKey: model.id)
        lastProgress.removeValue(forKey: model.id)
        if isCancellationRequested(model.id) {
            finishCancellation(model.id)
            return
        }
        if let error {
            let resume = (error as NSError).userInfo[NSURLSessionDownloadTaskResumeData] as? Data
            if record.resumed && resume == nil {
                // Resume data can expire or refer to a removed OS temporary file. Retry once fresh.
                removeSaved(model)
                start(model, allowResume: false)
                return
            }
            // Retain the effective offset, including servers that ignored a legacy Range.
            let offset = (task.response as? HTTPURLResponse)?.statusCode == 200 ? 0 : record.offset
            let failedRecord = Record(modelId: record.modelId, url: record.url, fileName: record.fileName,
                size: record.size, offset: offset, resumed: record.resumed)
            let bytes = min(record.size, max(offset, offset + max(0, task.countOfBytesReceived)))
            do {
                try save(SavedTransfer(record: failedRecord, resumeData: resume,
                    receivedBytes: bytes, error: error.localizedDescription), model)
            } catch {
                removeSaved(model)
            }
            publish(model.id, .failed, bytes: bytes, error: error.localizedDescription)
        } else if snapshotsForWorker(model.id)?.state != .finished {
            // A rejected HTTP response or invalid file must not reuse stale resume data.
            removeSaved(model)
        }
        installed.remove(model.id)
        setActive(model.id, false)
        notify()
    }

    func urlSessionDidFinishEvents(forBackgroundURLSession session: URLSession) {
        worker.async {
            self.eventsFinished = true
            self.finishBackgroundEventsIfReady()
        }
    }

    private func finishBackgroundEventsIfReady() {
        guard eventsFinished, let completion = backgroundCompletion else { return }
        eventsFinished = false
        backgroundCompletion = nil
        DispatchQueue.main.async(execute: completion)
    }

    private func finishCancellation(_ id: String) {
        if let model = models[id] {
            removeSaved(model)
            if installed.remove(id) != nil { files.discardCompleted(model: model) }
        }
        setActive(id, false)
        stateLock.lock()
        snapshots.removeValue(forKey: id)
        cancelRequested.remove(id)
        stateLock.unlock()
        let callbacks = cancellations.removeValue(forKey: id) ?? []
        DispatchQueue.main.async { callbacks.forEach { _ = $0() } }
        notify()
    }

    private func record(for task: URLSessionTask) -> Record? {
        guard let value = task.taskDescription?.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(Record.self, from: value)
    }

    private func readSaved(_ model: LocalModelDefinition) -> SavedTransfer? {
        guard let data = try? Data(contentsOf: URL(fileURLWithPath: files.resumeDataPath(model: model))),
              let saved = try? JSONDecoder().decode(SavedTransfer.self, from: data),
              saved.record.matches(model) else { return nil }
        return saved
    }

    private func save(_ saved: SavedTransfer, _ model: LocalModelDefinition) throws {
        let url = URL(fileURLWithPath: files.resumeDataPath(model: model))
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(saved).write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    private func removeSaved(_ model: LocalModelDefinition) {
        try? FileManager.default.removeItem(atPath: files.resumeDataPath(model: model))
    }

    private func snapshotsForWorker(_ id: String) -> LocalModelWorkSnapshot? { work(modelId: id) }

    private func isCancellationRequested(_ id: String) -> Bool {
        stateLock.lock()
        defer { stateLock.unlock() }
        return cancelRequested.contains(id)
    }

    /// Tests use a separate ephemeral session and must break URLSession's delegate retention.
    func invalidate() { session.invalidateAndCancel() }

    private func setActive(_ id: String, _ value: Bool) {
        stateLock.lock()
        if value { active.insert(id) } else { active.remove(id) }
        stateLock.unlock()
    }

    private func publish(_ id: String, _ state: LocalModelWorkState, bytes: Int64 = 0, error: String = "") {
        stateLock.lock()
        snapshots[id] = LocalModelWorkSnapshot(state: state, receivedBytes: bytes, error: error)
        stateLock.unlock()
        notify()
    }

    private func notify() {
        stateLock.lock()
        let callback = observer
        stateLock.unlock()
        DispatchQueue.main.async { _ = callback?() }
    }
}

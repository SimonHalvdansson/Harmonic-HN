import Foundation
import HarmonicKit
import CLiteRTLM

/// The serial worker retains each engine until its stream ends, including on cancellation.
final class IosLiteRtSummaryService: IosLiteRtSummaryBridge {
    private let worker = DispatchQueue(label: "com.simon.harmonic.litert", qos: .userInitiated)

    func start(
        modelPath: String,
        cacheDirectory: String,
        contextTokens: Int32,
        text: String,
        instruction: String,
        callback: IosLiteRtSummaryCallback
    ) -> IosLiteRtSummaryTask {
        let request = LiteRtSummaryRequest(callback: callback)
        worker.async {
            defer { request.releaseCallback() }
            do {
                let summary = try request.generate(
                    modelPath: modelPath, cacheDirectory: cacheDirectory,
                    contextTokens: contextTokens, text: text, instruction: instruction
                )
                callback.complete(summary: summary, errorMessage: nil)
            } catch {
                callback.complete(summary: nil, errorMessage: error.localizedDescription)
            }
        }
        return request
    }
}

private struct LiteRtSummaryError: LocalizedError {
    let errorDescription: String?
    init(_ message: String) { errorDescription = message }
}

private final class LiteRtSummaryRequest: IosLiteRtSummaryTask {
    private let lock = NSLock()
    private var conversation: OpaquePointer?
    private var cancelled = false
    private var callback: IosLiteRtSummaryCallback?
    // Native callbacks write these; the worker reads after wait_until_done returns.
    private var summary = ""
    private var streamError: String?
    private var nativeCancelled = false

    init(callback: IosLiteRtSummaryCallback) { self.callback = callback }

    func generate(modelPath: String, cacheDirectory: String, contextTokens: Int32, text: String, instruction: String) throws -> String {
        try checkCancellation()
        guard FileManager.default.fileExists(atPath: modelPath) else {
            throw LiteRtSummaryError("The downloaded local model file is missing.")
        }
        let settings = try createHandle("configure LiteRT-LM") {
            litert_lm_engine_settings_create(modelPath, "cpu", nil, nil, $0)
        }
        defer { litert_lm_engine_settings_delete(settings) }
        try checkStatus(litert_lm_engine_settings_set_max_num_tokens(settings, contextTokens))
        try checkStatus(litert_lm_engine_settings_set_cache_dir(settings, cacheDirectory))
        let started = ContinuousClock.now
        let engine = try createHandle("load the local model") {
            litert_lm_engine_create(settings, $0)
        }
        defer { litert_lm_engine_delete(engine) }
        try checkCancellation()
        let elapsed = started.duration(to: .now).components
        callback?.loaded(milliseconds: elapsed.seconds * 1_000 + elapsed.attoseconds / 1_000_000_000_000_000)

        let session = try createHandle("configure the local summary session") { litert_lm_session_config_create($0) }
        defer { litert_lm_session_config_delete(session) }
        let config = try createHandle("configure the local summary conversation") { litert_lm_conversation_config_create($0) }
        defer { litert_lm_conversation_config_delete(config) }
        let sampler = try createHandle("configure the local summary sampler") {
            litert_lm_sampler_params_create(kLiteRtLmSamplerTypeTopP, $0)
        }
        defer { litert_lm_sampler_params_delete(sampler) }
        let thinking = try createHandle("configure the local summary output") { litert_lm_thinking_config_create($0) }
        defer { litert_lm_thinking_config_delete(thinking) }
        try checkStatus(litert_lm_sampler_params_set_top_k(sampler, 64))
        try checkStatus(litert_lm_sampler_params_set_top_p(sampler, 0.95))
        try checkStatus(litert_lm_sampler_params_set_temperature(sampler, 0.3))
        try checkStatus(litert_lm_session_config_set_sampler_params(session, sampler))
        try checkStatus(litert_lm_session_config_set_max_output_tokens(session, 512))
        try checkStatus(litert_lm_conversation_config_set_session_config(config, session))
        // This setter takes Contents JSON (an array), while send_message takes a Message object.
        let systemContent = try JSONSerialization.data(withJSONObject: [["type": "text", "text": instruction]])
        try checkStatus(litert_lm_conversation_config_set_system_message(config, String(decoding: systemContent, as: UTF8.self)))
        try checkStatus(litert_lm_thinking_config_set_enable_thinking(thinking, false))
        try checkStatus(litert_lm_conversation_config_set_thinking_config(config, thinking))
        let handle = try createHandle("create the local summary conversation") {
            litert_lm_conversation_create(engine, config, $0)
        }
        conversation = handle
        defer {
            // wait_until_done has joined every callback that could access this handle.
            conversation = nil
            litert_lm_conversation_delete(handle)
        }
        try checkCancellation()
        let status = litert_lm_conversation_send_message_stream(
            handle, try message(text, role: "user"), nil, nil,
            { context, chunk in
                guard let context else { return }
                Unmanaged<LiteRtSummaryRequest>.fromOpaque(context).takeUnretainedValue().receive(chunk)
            },
            Unmanaged.passUnretained(self).toOpaque()
        )
        try checkStatus(status)
        // Join native work, including its callbacks, before releasing any handle on cancellation.
        let completionStatus = litert_lm_conversation_wait_until_done(handle)
        try checkCancellation()
        if let streamError { throw LiteRtSummaryError(streamError) }
        try checkStatus(completionStatus)
        return summary
    }

    private func message(_ text: String, role: String) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: [
            "role": role, "content": [["type": "text", "text": text]],
        ])
        return String(decoding: data, as: UTF8.self)
    }

    private func receive(_ chunk: OpaquePointer?) {
        var isFinal = true
        do {
            try checkStatus(litert_lm_stream_chunk_is_final(chunk, &isFinal))
            lock.lock()
            let shouldCancel = cancelled
            lock.unlock()
            if shouldCancel || streamError != nil {
                cancelNativeStream(isFinal: isFinal)
                return
            }
            var error: UnsafePointer<CChar>?
            try checkStatus(litert_lm_stream_chunk_get_error(chunk, &error))
            if let error {
                streamError = String(cString: error)
                cancelNativeStream(isFinal: isFinal)
                return
            }
            var json: UnsafePointer<CChar>?
            try checkStatus(litert_lm_stream_chunk_get_text(chunk, &json))
            if let json {
                let data = Data(String(cString: json).utf8)
                let message = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                for content in message?["content"] as? [[String: Any]] ?? [] {
                    if content["type"] as? String == "text", let text = content["text"] as? String {
                        summary += text
                    }
                }
                callback?.progress(summary: summary.trimmingCharacters(in: .whitespacesAndNewlines))
            }
        } catch {
            streamError = "Could not read LiteRT-LM's response: \(error.localizedDescription)"
            cancelNativeStream(isFinal: isFinal)
        }
    }

    private func cancelNativeStream(isFinal: Bool) {
        // The C API permits cancellation from a non-final callback. Keep other threads
        // away from the native handle while the worker is joining its pending work.
        guard !isFinal, !nativeCancelled, let conversation else { return }
        nativeCancelled = true
        _ = litert_lm_conversation_cancel_process(conversation)
    }

    private func createHandle(
        _ operation: String,
        _ create: (UnsafeMutablePointer<OpaquePointer?>) -> LiteRtLmStatusCode
    ) throws -> OpaquePointer {
        var handle: OpaquePointer?
        try checkStatus(create(&handle))
        guard let handle else { throw LiteRtSummaryError("Could not \(operation).") }
        return handle
    }

    private func checkStatus(_ status: LiteRtLmStatusCode) throws {
        guard status == kLiteRtLmStatusOk else {
            let detail = litert_lm_get_last_error_message().map { String(cString: $0) }
            throw LiteRtSummaryError(detail ?? "LiteRT-LM failed (\(status.rawValue)).")
        }
    }

    private func checkCancellation() throws {
        lock.lock()
        let shouldCancel = cancelled
        lock.unlock()
        if shouldCancel { throw CancellationError() }
    }

    func releaseCallback() { callback = nil }

    func cancel() {
        lock.lock()
        cancelled = true
        lock.unlock()
    }
}

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
    private let finished = DispatchSemaphore(value: 0)
    private var callback: IosLiteRtSummaryCallback?
    // Native callbacks write these; the worker reads after finished signals.
    private var summary = ""
    private var streamError: String?

    init(callback: IosLiteRtSummaryCallback) { self.callback = callback }

    func generate(modelPath: String, cacheDirectory: String, contextTokens: Int32, text: String, instruction: String) throws -> String {
        try checkCancellation()
        guard FileManager.default.fileExists(atPath: modelPath) else {
            throw LiteRtSummaryError("The downloaded local model file is missing.")
        }
        guard let settings = litert_lm_engine_settings_create(modelPath, "cpu", nil, nil) else {
            throw LiteRtSummaryError("Could not configure LiteRT-LM.")
        }
        defer { litert_lm_engine_settings_delete(settings) }
        litert_lm_engine_settings_set_max_num_tokens(settings, contextTokens)
        litert_lm_engine_settings_set_cache_dir(settings, cacheDirectory)
        let started = ContinuousClock.now
        guard let engine = litert_lm_engine_create(settings) else {
            throw LiteRtSummaryError("Could not load the local model with LiteRT-LM.")
        }
        defer { litert_lm_engine_delete(engine) }
        try checkCancellation()
        let elapsed = started.duration(to: .now).components
        callback?.loaded(milliseconds: elapsed.seconds * 1_000 + elapsed.attoseconds / 1_000_000_000_000_000)

        guard let session = litert_lm_session_config_create() else {
            throw LiteRtSummaryError("Could not configure the local summary session.")
        }
        defer { litert_lm_session_config_delete(session) }
        guard let config = litert_lm_conversation_config_create() else {
            throw LiteRtSummaryError("Could not configure the local summary conversation.")
        }
        defer { litert_lm_conversation_config_delete(config) }
        guard let sampler = litert_lm_sampler_params_create(kLiteRtLmSamplerTypeTopP) else {
            throw LiteRtSummaryError("Could not configure the local summary sampler.")
        }
        defer { litert_lm_sampler_params_delete(sampler) }
        guard let thinking = litert_lm_thinking_config_create() else {
            throw LiteRtSummaryError("Could not configure the local summary output.")
        }
        defer { litert_lm_thinking_config_delete(thinking) }
        litert_lm_sampler_params_set_top_k(sampler, 64)
        litert_lm_sampler_params_set_top_p(sampler, 0.95)
        litert_lm_sampler_params_set_temperature(sampler, 0.3)
        litert_lm_session_config_set_sampler_params(session, sampler)
        litert_lm_session_config_set_max_output_tokens(session, 512)
        litert_lm_conversation_config_set_session_config(config, session)
        // This setter takes Contents JSON (an array), while send_message takes a Message object.
        let systemContent = try JSONSerialization.data(withJSONObject: [["type": "text", "text": instruction]])
        litert_lm_conversation_config_set_system_message(config, String(decoding: systemContent, as: UTF8.self))
        litert_lm_thinking_config_set_enable_thinking(thinking, false)
        litert_lm_conversation_config_set_thinking_config(config, thinking)
        guard let handle = litert_lm_conversation_create(engine, config) else {
            throw LiteRtSummaryError("Could not create the local summary conversation.")
        }
        lock.lock()
        conversation = handle
        lock.unlock()
        defer {
            // cancel() must not access a deleted native handle.
            lock.lock()
            conversation = nil
            lock.unlock()
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
        guard status == 0 else {
            throw LiteRtSummaryError("LiteRT-LM could not start the summary (\(status)).")
        }
        // Keep handles alive until the terminal callback, even when Kotlin cancels its waiter.
        finished.wait()
        try checkCancellation()
        if let streamError { throw LiteRtSummaryError(streamError) }
        return summary
    }

    private func message(_ text: String, role: String) throws -> String {
        let data = try JSONSerialization.data(withJSONObject: [
            "role": role, "content": [["type": "text", "text": text]],
        ])
        return String(decoding: data, as: UTF8.self)
    }

    private func receive(_ chunk: OpaquePointer?) {
        if let error = litert_lm_stream_chunk_get_error(chunk) {
            streamError = String(cString: error)
            finished.signal()
            return
        }
        if let json = litert_lm_stream_chunk_get_text(chunk) {
            do {
                let data = Data(String(cString: json).utf8)
                let message = try JSONSerialization.jsonObject(with: data) as? [String: Any]
                for content in message?["content"] as? [[String: Any]] ?? [] {
                    if content["type"] as? String == "text", let text = content["text"] as? String {
                        summary += text
                    }
                }
                callback?.progress(summary: summary.trimmingCharacters(in: .whitespacesAndNewlines))
            } catch {
                streamError = "Could not read LiteRT-LM's response: \(error.localizedDescription)"
                cancel()
            }
        }
        if litert_lm_stream_chunk_is_final(chunk) { finished.signal() }
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
        if let conversation { litert_lm_conversation_cancel_process(conversation) }
        lock.unlock()
    }
}

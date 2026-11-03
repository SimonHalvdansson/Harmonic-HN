import Foundation
import HarmonicKit
import CLiteRTLM

/// Native creation, inference and release stay on one utility worker, away from scrolling.
final class IosArticleEmbeddingService: IosArticleEmbeddingBridge {
    private let worker = DispatchQueue(label: "com.simon.harmonic.embeddings", qos: .utility)
    private var engine: OpaquePointer?

    deinit {
        if let engine {
            worker.async { litert_lm_embedding_engine_delete(engine) }
        }
    }

    func load(modelPath: String, cacheDirectory: String, callback: IosArticleEmbeddingCallback) {
        perform(callback) {
            self.releaseEngine()
            guard FileManager.default.fileExists(atPath: modelPath) else {
                throw ArticleEmbeddingError("The downloaded embedding model file is missing.")
            }
            try FileManager.default.createDirectory(
                atPath: cacheDirectory, withIntermediateDirectories: true
            )
            let settings = try self.createHandle {
                litert_lm_embedding_engine_settings_create(modelPath, "cpu", nil, nil, $0)
            }
            defer { litert_lm_embedding_engine_settings_delete(settings) }
            try self.check(litert_lm_embedding_engine_settings_set_num_threads(settings, 1))
            try self.check(litert_lm_embedding_engine_settings_set_max_input_length(settings, 256))
            try self.check(litert_lm_embedding_engine_settings_set_cache_dir(settings, cacheDirectory))
            self.engine = try self.createHandle { litert_lm_embedding_engine_create(settings, $0) }
            return nil
        }
    }

    func embed(text: String, callback: IosArticleEmbeddingCallback) {
        perform(callback) {
            guard let engine = self.engine else {
                throw ArticleEmbeddingError("The embedding model is not loaded.")
            }
            let options = try self.createHandle { litert_lm_embedding_options_create($0) }
            defer { litert_lm_embedding_options_delete(options) }
            try self.check(litert_lm_embedding_options_set_normalize(options, true))
            try self.check(litert_lm_embedding_options_set_output_size(options, 768))

            let input = try text.utf8CString.withUnsafeBufferPointer { bytes in
                try self.createHandle {
                    litert_lm_input_data_create(kLiteRtLmInputDataTypeText, bytes.baseAddress, bytes.count - 1, $0)
                }
            }
            defer { litert_lm_input_data_delete(input) }
            let inputs: [OpaquePointer?] = [input]
            let response = try inputs.withUnsafeBufferPointer { buffer in
                try self.createHandle {
                    litert_lm_embedding_engine_compute_embedding(engine, buffer.baseAddress, buffer.count, options, $0)
                }
            }
            defer { litert_lm_embedding_response_delete(response) }
            var count = 0
            try self.check(litert_lm_embedding_response_get_size(response, &count))
            var values: UnsafePointer<Float>?
            try self.check(litert_lm_embedding_response_get_values(response, &values))
            guard count == 768, let values else {
                throw ArticleEmbeddingError("The model returned unexpected embedding dimensions.")
            }
            let embedding = KotlinFloatArray(size: Int32(count))
            for index in 0..<count {
                guard values[index].isFinite else {
                    throw ArticleEmbeddingError("The model returned an invalid embedding.")
                }
                embedding.set(index: Int32(index), value: values[index])
            }
            return embedding
        }
    }

    func close(callback: IosArticleEmbeddingCallback) {
        perform(callback) {
            self.releaseEngine()
            return nil
        }
    }

    private func perform(_ callback: IosArticleEmbeddingCallback, operation: @escaping () throws -> KotlinFloatArray?) {
        worker.async {
            do {
                callback.complete(embedding: try operation(), errorMessage: nil)
            } catch {
                callback.complete(embedding: nil, errorMessage: error.localizedDescription)
            }
        }
    }

    private func releaseEngine() {
        if let engine { litert_lm_embedding_engine_delete(engine) }
        engine = nil
    }

    private func createHandle(_ create: (UnsafeMutablePointer<OpaquePointer?>) -> LiteRtLmStatusCode) throws -> OpaquePointer {
        var handle: OpaquePointer?
        try check(create(&handle))
        guard let handle else { throw ArticleEmbeddingError("Could not initialize the embedding runtime.") }
        return handle
    }

    private func check(_ status: LiteRtLmStatusCode) throws {
        guard status == kLiteRtLmStatusOk else {
            let detail = litert_lm_get_last_error_message().map { String(cString: $0) }
            throw ArticleEmbeddingError(detail ?? "The embedding runtime failed (\(status.rawValue)).")
        }
    }
}

private struct ArticleEmbeddingError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

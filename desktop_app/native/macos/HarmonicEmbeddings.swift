import Foundation
import NaturalLanguage

// A persistent, line-delimited helper keeps NaturalLanguage in the native host.
@main struct HarmonicEmbeddings {
    static func main() {
        var embedding: NLEmbedding?
        while let line = readLine() {
            autoreleasepool {
                var response: [String: Any] = [:]
                do {
                    let request = try JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: String] ?? [:]
                    if request["text"] == nil || embedding == nil { embedding = NLEmbedding.sentenceEmbedding(for: .english) }
                    if let model = embedding {
                        response = ["signature": "apple-sentence-en-\(model.revision)-\(model.dimension)", "dimensions": model.dimension]
                        if let text = request["text"] {
                            guard let vector = model.vector(for: text) else { throw NSError(domain: "Embeddings", code: 1) }
                            response["vector"] = vector
                        }
                    } else { response["unavailable"] = true }
                } catch { response = ["error": "Could not generate a sentence embedding"] }
                if let data = try? JSONSerialization.data(withJSONObject: response), let json = String(data: data, encoding: .utf8) {
                    print(json)
                    fflush(stdout)
                }
            }
        }
    }
}

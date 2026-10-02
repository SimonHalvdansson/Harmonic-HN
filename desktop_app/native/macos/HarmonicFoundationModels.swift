import Foundation
#if canImport(FoundationModels)
import FoundationModels
#endif

// A short-lived helper keeps Swift concurrency and model lifetime separate from the JVM.
// Input and output are UTF-8 JSON lines over private pipes, never command-line article text.
@main
struct HarmonicFoundationModels {
    static func emit(_ value: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: value) else { return }
        FileHandle.standardOutput.write(data)
        FileHandle.standardOutput.write(Data([10]))
    }

    static func main() async {
#if canImport(FoundationModels)
        if #available(macOS 26.0, *) {
            let model = SystemLanguageModel.default
            if CommandLine.arguments.contains("--availability") {
                switch model.availability {
                case .available:
                    emit(["available": true, "message": "Available · system managed"])
                case .unavailable(let reason):
                    let message: String
                    switch reason {
                    case .appleIntelligenceNotEnabled:
                        message = "Enable Apple Intelligence in macOS System Settings"
                    case .deviceNotEligible:
                        message = "This Mac does not support Apple Intelligence"
                    case .modelNotReady:
                        message = "Apple Intelligence is still preparing its model"
                    @unknown default:
                        message = "Apple Intelligence is unavailable: \(reason)"
                    }
                    emit(["available": false, "message": message])
                @unknown default:
                    emit(["available": false, "message": "Apple Intelligence availability is unknown"])
                }
                return
            }
            do {
                guard let line = readLine(), let data = line.data(using: .utf8),
                      let request = try JSONSerialization.jsonObject(with: data) as? [String: String],
                      let text = request["text"], let instruction = request["instruction"] else {
                    emit(["error": "Invalid summary request"])
                    return
                }
                let session = LanguageModelSession(model: model, instructions: instruction)
                for try await response in session.streamResponse(to: text) {
                    emit(["text": response.content])
                }
                emit(["done": true])
            } catch {
                emit(["error": error.localizedDescription])
            }
            return
        }
#endif
        emit(["available": false, "error": "Apple Intelligence requires macOS 26 or newer", "message": "Requires macOS 26 or newer"])
    }
}

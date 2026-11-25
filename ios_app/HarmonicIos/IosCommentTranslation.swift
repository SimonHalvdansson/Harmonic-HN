import Foundation
import HarmonicKit
import NaturalLanguage
import SwiftUI
import Translation
import UIKit

final class IosCommentTranslation: IosCommentTranslationBridge {
    func languageOptions(completionHandler: @escaping (TranslationLanguages?, Error?) -> Void) {
        Task {
            let supported = await LanguageAvailability().supportedLanguages
            let locale = Foundation.Locale.current
            var names = [String: String]()
            for language in supported {
                let id = language.minimalIdentifier
                names[id] = locale.localizedString(forIdentifier: id) ?? id
            }
            let identifiers = names.keys.sorted()
            let preferred = identifiers.isEmpty ? [] : Bundle.preferredLocalizations(
                from: identifiers, forPreferences: Foundation.Locale.preferredLanguages + ["en"]
            )
            completionHandler(TranslationLanguages(defaultLanguage: preferred.first ?? "", names: names), nil)
        }
    }

    func start(sample: String, segments: [String], target: String,
               callback: IosCommentTranslationCallback) -> IosCommentTranslationRequest {
        let request = CommentTranslationRequest(callback: callback)
        Task { @MainActor in
            await request.start(sample: sample, segments: segments, target: Foundation.Locale.Language(identifier: target))
        }
        return request
    }
}

private final class CommentTranslationRequest: IosCommentTranslationRequest {
    private var callback: IosCommentTranslationCallback?
    private var host: UIViewController?
    private var session: TranslationSession?

    init(callback: IosCommentTranslationCallback) {
        self.callback = callback
    }

    @MainActor
    func start(sample: String, segments: [String], target: Foundation.Locale.Language) async {
        guard callback != nil else { return }
        guard let detected = NLLanguageRecognizer.dominantLanguage(for: sample), detected != .undetermined else {
            finish { $0.unsupported() }
            return
        }
        let source = Foundation.Locale.Language(identifier: detected.rawValue)
        if source.languageCode == target.languageCode && source.script == target.script {
            finish { $0.sameLanguage() }
            return
        }
#if DEBUG && targetEnvironment(simulator)
        // Apple's simulator lists languages but cannot run the downloaded translation models.
        let environment = ProcessInfo.processInfo.environment
        if harmonicIsUiTesting, target.languageCode?.identifier == "en",
           segments.count == 1, segments.first == environment["HARMONIC_TEST_TRANSLATION_SOURCE"],
           let result = environment["HARMONIC_TEST_TRANSLATION_RESULT"] {
            finish { $0.translated(segments: [result]) }
            return
        }
#endif
        let status = await LanguageAvailability().status(from: source, to: target)
        guard callback != nil else { return }
        guard status != .unsupported else {
            finish { $0.unsupported() }
            return
        }
        guard let presenter = UIViewController.harmonicTopPresenter else {
            finish { $0.failed(message: "No active window for translation") }
            return
        }
        // A view-backed session lets iOS present its language-download consent sheet.
        let view = SwiftUI.Color.clear.translationTask(source: source, target: target) { session in
            guard self.callback != nil else { return }
            self.session = session
            do {
                let batch = segments.map { TranslationSession.Request(sourceText: $0) }
                let responses = try await session.translations(from: batch)
                self.finish { $0.translated(segments: responses.map(\.targetText)) }
            } catch TranslationError.unsupportedSourceLanguage, TranslationError.unsupportedTargetLanguage,
                    TranslationError.unsupportedLanguagePairing, TranslationError.unableToIdentifyLanguage {
                self.finish { $0.unsupported() }
            } catch {
                self.finish { $0.failed(message: error.localizedDescription) }
            }
        }
        let host = UIHostingController(rootView: view)
        self.host = host
        presenter.addChild(host)
        host.view.frame = CGRect(x: 0, y: 0, width: 1, height: 1)
        host.view.backgroundColor = .clear
        host.view.isUserInteractionEnabled = false
        host.view.accessibilityElementsHidden = true
        presenter.view.addSubview(host.view)
        host.didMove(toParent: presenter)
    }

    func cancel() {
        Task { @MainActor in
            if #available(iOS 26, *) { session?.cancel() }
            finish { _ in }
        }
    }

    @MainActor
    private func finish(_ deliver: (IosCommentTranslationCallback) -> Void) {
        guard let callback else { return }
        self.callback = nil
        session = nil
        host?.willMove(toParent: nil)
        host?.view.removeFromSuperview()
        host?.removeFromParent()
        host = nil
        deliver(callback)
    }
}

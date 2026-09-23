import Foundation
import Network
import UIKit
import HarmonicKit
#if canImport(FoundationModels)
import FoundationModels
#endif

final class IosNativeServices {
    let credentials: CredentialStore
    let accounts: HackerNewsAccountRepository
    let externalLinks: ExternalLinkOpener
    let sharing: ShareService
    let textDocuments: TextDocumentService
    let clipboard: ClipboardService
    let connectivity: ConnectivityService
    let timeFormatting: PlatformTimeFormatter
    let appearance: IosAppearanceService
    let localSummary: IosNativeSummaryBridge

    init() {
        let vault = KeychainVault(service: "com.simon.harmonichackernews.ios")
        credentials = IosCredentialStore(vault: vault)
        accounts = IosAccountRepository(vault: vault)
        externalLinks = IosExternalLinkOpener()
        sharing = IosShareService()
        textDocuments = IosTextDocumentService()
        clipboard = IosClipboardService()
        connectivity = IosConnectivityService()
        timeFormatting = IosTimeFormatter()
        appearance = IosAppearanceService()
        localSummary = IosFoundationModelsSummaryBridge()
    }
}

final class IosFoundationModelsSummaryBridge: IosNativeSummaryBridge {
    func canAttempt() -> Bool {
#if canImport(FoundationModels)
        if #available(iOS 26.0, *) { return true }
#endif
        return false
    }

    func isAvailable() -> Bool {
#if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            return SystemLanguageModel.default.isAvailable
        }
#endif
        return false
    }

    func availabilityMessage() -> String? {
#if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                return nil
            case .unavailable(let reason):
                return "Apple Intelligence is unavailable: \(reason)"
            @unknown default:
                return "Apple Intelligence availability is unknown"
            }
        }
#endif
        return "Apple Intelligence summaries require iOS 26 or newer"
    }

    func summarize(
        text: String,
        instruction: String,
        callback: IosNativeSummaryCallback
    ) {
#if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            Task {
                do {
                    let session = LanguageModelSession(instructions: instruction)
                    let response = try await session.respond(to: text)
                    callback.complete(summary: response.content, errorMessage: nil)
                } catch {
                    callback.complete(summary: nil, errorMessage: error.localizedDescription)
                }
            }
            return
        }
#endif
        callback.complete(
            summary: nil,
            errorMessage: "Apple Intelligence summaries require iOS 26 or newer"
        )
    }
}

final class IosAppearanceService: IosAppearanceController {
    private weak var root: HarmonicRootViewController?
    private var dark = false

    func attach(_ root: HarmonicRootViewController) {
        self.root = root
        root.applyDarkAppearance(dark)
    }

    func setDarkAppearance(dark: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.dark = dark
            self.root?.applyDarkAppearance(dark)
        }
    }
}

private final class IosShareService: ShareService {
    func share(text: String, title: String?) {
        DispatchQueue.main.async {
            guard let presenter = UIViewController.harmonicTopPresenter else { return }
            let controller = UIActivityViewController(
                activityItems: [text],
                applicationActivities: nil
            )
            controller.title = title
            if let popover = controller.popoverPresentationController {
                popover.sourceView = presenter.view
                popover.sourceRect = CGRect(
                    x: presenter.view.bounds.midX,
                    y: presenter.view.bounds.midY,
                    width: 1,
                    height: 1
                )
            }
            presenter.present(controller, animated: true)
        }
    }
}

private final class IosClipboardService: ClipboardService {
    func doCopy(label: String, text: String) {
        DispatchQueue.main.async {
            UIPasteboard.general.string = text
        }
    }
}

private final class IosConnectivityService: ConnectivityService {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "Harmonic.NetworkPath")
    private let lock = NSLock()
    private var online = true
    private var unmetered = true

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            lock.lock()
            online = path.status == .satisfied
            unmetered = online && !path.isExpensive && !path.isConstrained
            lock.unlock()
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    func isOnline() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return online
    }

    func isUnmetered() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return unmetered
    }
}

private final class IosTimeFormatter: PlatformTimeFormatter {
    private let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter
    }()

    func time(epochMillis: Int64) -> String {
        timeFormatter.string(
            from: Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1_000)
        )
    }

    func localDate(epochMillis: Int64) -> LocalCalendarDate {
        let components = Calendar.current.dateComponents(
            [.year, .month, .day],
            from: Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1_000)
        )
        return LocalCalendarDate(
            year: Int32(components.year ?? 1970),
            month: Int32(components.month ?? 1),
            day: Int32(components.day ?? 1)
        )
    }

    func uses24HourClock() -> Bool {
        let pattern = DateFormatter.dateFormat(
            fromTemplate: "j",
            options: 0,
            locale: Locale.current
        ) ?? ""
        return !pattern.contains("a")
    }
}

extension UIViewController {
    static var harmonicTopPresenter: UIViewController? {
        guard
            let scene = UIApplication.shared.connectedScenes
                .compactMap({ $0 as? UIWindowScene })
                .first(where: { $0.activationState == .foregroundActive }),
            let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController
        else {
            return nil
        }
        return topPresenter(from: root)
    }

    private static func topPresenter(from controller: UIViewController) -> UIViewController {
        if let presented = controller.presentedViewController {
            return topPresenter(from: presented)
        }
        if let navigation = controller as? UINavigationController,
           let visible = navigation.visibleViewController {
            return topPresenter(from: visible)
        }
        if let tabs = controller as? UITabBarController,
           let selected = tabs.selectedViewController {
            return topPresenter(from: selected)
        }
        return controller
    }
}

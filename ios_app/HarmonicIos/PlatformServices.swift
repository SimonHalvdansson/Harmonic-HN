import Foundation
import Network
import Security
import UIKit
import WebKit
import HarmonicKit
#if canImport(FoundationModels)
import FoundationModels
#endif

final class IosNativeServices {
    let credentials: CredentialStore
    let accounts: HackerNewsAccountRepository
    let externalLinks: ExternalLinkOpener
    let sharing: ShareService
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

private final class KeychainVault {
    private let service: String
    private let lock = NSLock()

    init(service: String) {
        self.service = service
    }

    private func selector(account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    func read(account: String) -> Foundation.Data? {
        lock.lock()
        defer { lock.unlock() }
        var query = selector(account: account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess else {
            return nil
        }
        return result as? Foundation.Data
    }

    @discardableResult
    func write(_ data: Foundation.Data, account: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let updateSelector = selector(account: account)
        let attributes: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ]
        let update = SecItemUpdate(updateSelector as CFDictionary, attributes as CFDictionary)
        if update == errSecSuccess {
            return true
        }
        guard update == errSecItemNotFound else {
            return false
        }
        var insertion = updateSelector
        attributes.forEach { insertion[$0.key] = $0.value }
        return SecItemAdd(insertion as CFDictionary, nil) == errSecSuccess
    }

    @discardableResult
    func remove(account: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        let query = selector(account: account)
        let status = SecItemDelete(query as CFDictionary)
        return status == errSecSuccess || status == errSecItemNotFound
    }
}

private final class IosCredentialStore: CredentialStore {
    private let vault: KeychainVault

    init(vault: KeychainVault) {
        self.vault = vault
    }

    func read(id: String) -> String? {
        vault.read(account: "credential.\(id)").flatMap {
            String(data: $0, encoding: .utf8)
        }
    }

    func write(id: String, value: String) -> Bool {
        guard let data = value.data(using: .utf8) else { return false }
        return vault.write(data, account: "credential.\(id)")
    }

    func remove(id: String) -> Bool {
        vault.remove(account: "credential.\(id)")
    }
}

private struct StoredHackerNewsAccount: Codable {
    let username: String
    let password: String
}

private final class IosAccountRepository: HackerNewsAccountRepository {
    private let vault: KeychainVault
    private let accountKey = "hacker-news-account"

    init(vault: KeychainVault) {
        self.vault = vault
    }

    func load() -> HackerNewsAccount? {
        guard
            let data = vault.read(account: accountKey),
            let stored = try? JSONDecoder().decode(StoredHackerNewsAccount.self, from: data),
            !stored.username.isEmpty,
            !stored.password.isEmpty
        else {
            return nil
        }
        return HackerNewsAccount(username: stored.username, password: stored.password)
    }

    func save(account: HackerNewsAccount) -> Bool {
        let stored = StoredHackerNewsAccount(
            username: account.username,
            password: account.password
        )
        guard let data = try? JSONEncoder().encode(stored) else { return false }
        return vault.write(data, account: accountKey)
    }

    func clear__() -> Bool {
        vault.remove(account: accountKey)
    }
}

private final class IosExternalLinkOpener: ExternalLinkOpener {
    func open(request: ExternalLinkRequest) -> Bool {
        guard let url = URL(string: request.url) else { return false }
        if
            request.preferInApp,
            let scheme = url.scheme?.lowercased(),
            scheme == "http" || scheme == "https"
        {
            DispatchQueue.main.async {
                guard let presenter = UIViewController.harmonicTopPresenter else { return }
                let browser = HarmonicWebViewController(
                    url: url,
                    shareable: request.shareable
                )
                let navigation = UINavigationController(rootViewController: browser)
                navigation.modalPresentationStyle = .fullScreen
                navigation.view.backgroundColor = .systemBackground
                navigation.navigationBar.prefersLargeTitles = false
                navigation.navigationBar.isTranslucent = false
                navigation.toolbar.isTranslucent = false
                presenter.present(navigation, animated: true)
            }
            return true
        }
        guard UIApplication.shared.canOpenURL(url) else { return false }
        DispatchQueue.main.async {
            UIApplication.shared.open(url)
        }
        return true
    }
}

private final class HarmonicWebViewController:
    UIViewController,
    WKNavigationDelegate,
    WKUIDelegate,
    UIGestureRecognizerDelegate
{
    private let initialURL: URL
    private let shareable: Bool
    private let webView: WKWebView
    private let progressView = UIProgressView(progressViewStyle: .bar)
    private var progressObservation: NSKeyValueObservation?
    private var titleObservation: NSKeyValueObservation?
    private var canGoBackObservation: NSKeyValueObservation?
    private var canGoForwardObservation: NSKeyValueObservation?
    private lazy var backButton = UIBarButtonItem(
        image: UIImage(systemName: "chevron.backward"),
        style: .plain,
        target: self,
        action: #selector(goBack)
    )
    private lazy var forwardButton = UIBarButtonItem(
        image: UIImage(systemName: "chevron.forward"),
        style: .plain,
        target: self,
        action: #selector(goForward)
    )

    init(url: URL, shareable: Bool) {
        initialURL = url
        self.shareable = shareable
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = .default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        webView = WKWebView(frame: .zero, configuration: configuration)
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        edgesForExtendedLayout = []
        configureNavigation()
        configureWebView()
        observeWebView()
        webView.load(URLRequest(url: initialURL))
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        guard isBeingDismissed || navigationController?.isBeingDismissed == true else { return }
        webView.stopLoading()
        webView.navigationDelegate = nil
        webView.uiDelegate = nil
    }

    private func configureNavigation() {
        title = initialURL.host ?? "Article"
        navigationItem.largeTitleDisplayMode = .never
        let closeButton = UIBarButtonItem(
            barButtonSystemItem: .close,
            target: self,
            action: #selector(dismissBrowser)
        )
        closeButton.accessibilityLabel = "Close article"
        navigationItem.leftBarButtonItem = closeButton

        var trailingItems: [UIBarButtonItem] = []
        if shareable {
            let shareButton = UIBarButtonItem(
                image: UIImage(systemName: "square.and.arrow.up"),
                style: .plain,
                target: self,
                action: #selector(sharePage)
            )
            shareButton.accessibilityLabel = "Share article"
            trailingItems.append(shareButton)
        }
        let externalButton = UIBarButtonItem(
            image: UIImage(systemName: "safari"),
            style: .plain,
            target: self,
            action: #selector(openExternally)
        )
        externalButton.accessibilityLabel = "Open in Safari"
        trailingItems.append(externalButton)
        navigationItem.rightBarButtonItems = trailingItems

        backButton.accessibilityLabel = "Web back"
        forwardButton.accessibilityLabel = "Web forward"
        let reloadButton = UIBarButtonItem(
            barButtonSystemItem: .refresh,
            target: self,
            action: #selector(reloadPage)
        )
        reloadButton.accessibilityLabel = "Reload article"
        let flexible = UIBarButtonItem(
            barButtonSystemItem: .flexibleSpace,
            target: nil,
            action: nil
        )
        toolbarItems = [backButton, flexible, forwardButton, flexible, reloadButton]
        navigationController?.setToolbarHidden(false, animated: false)

        let appearance = UINavigationBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = .systemBackground
        appearance.shadowColor = .separator
        navigationController?.navigationBar.standardAppearance = appearance
        navigationController?.navigationBar.scrollEdgeAppearance = appearance
        navigationController?.navigationBar.compactAppearance = appearance

        let toolbarAppearance = UIToolbarAppearance()
        toolbarAppearance.configureWithOpaqueBackground()
        toolbarAppearance.backgroundColor = .systemBackground
        navigationController?.toolbar.standardAppearance = toolbarAppearance
        navigationController?.toolbar.scrollEdgeAppearance = toolbarAppearance
    }

    private func configureWebView() {
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = false
        webView.allowsLinkPreview = true
        webView.isOpaque = true
        webView.backgroundColor = .systemBackground
        webView.scrollView.backgroundColor = .systemBackground
        webView.accessibilityIdentifier = "harmonic_article_web_view"
        webView.accessibilityLabel = "Article web view"
        webView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(webView)

        progressView.translatesAutoresizingMaskIntoConstraints = false
        progressView.isHidden = true
        progressView.accessibilityIdentifier = "harmonic_web_progress"
        view.addSubview(progressView)

        NSLayoutConstraint.activate([
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            progressView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            progressView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            progressView.topAnchor.constraint(equalTo: view.topAnchor),
        ])

        let edgeBack = UIScreenEdgePanGestureRecognizer(
            target: self,
            action: #selector(handleEdgeBack(_:))
        )
        edgeBack.edges = .left
        edgeBack.delegate = self
        view.addGestureRecognizer(edgeBack)
    }

    private func observeWebView() {
        progressObservation = webView.observe(\.estimatedProgress, options: [.initial, .new]) {
            [weak self] webView, _ in
            guard let self else { return }
            progressView.progress = Float(webView.estimatedProgress)
            progressView.isHidden = webView.estimatedProgress >= 1
        }
        titleObservation = webView.observe(\.title, options: [.new]) { [weak self] webView, _ in
            guard let self else { return }
            let pageTitle = webView.title?.trimmingCharacters(in: .whitespacesAndNewlines)
            title = if let pageTitle, !pageTitle.isEmpty {
                pageTitle
            } else {
                webView.url?.host ?? initialURL.host ?? "Article"
            }
        }
        canGoBackObservation = webView.observe(\.canGoBack, options: [.initial, .new]) {
            [weak self] webView, _ in
            self?.backButton.isEnabled = webView.canGoBack
        }
        canGoForwardObservation = webView.observe(\.canGoForward, options: [.initial, .new]) {
            [weak self] webView, _ in
            self?.forwardButton.isEnabled = webView.canGoForward
        }
    }

    @objc
    private func dismissBrowser() {
        dismiss(animated: true)
    }

    @objc
    private func goBack() {
        if webView.canGoBack {
            webView.goBack()
        }
    }

    @objc
    private func goForward() {
        if webView.canGoForward {
            webView.goForward()
        }
    }

    @objc
    private func reloadPage() {
        webView.reload()
    }

    @objc
    private func openExternally() {
        UIApplication.shared.open(webView.url ?? initialURL)
    }

    @objc
    private func sharePage() {
        let activity = UIActivityViewController(
            activityItems: [webView.url ?? initialURL],
            applicationActivities: nil
        )
        if let popover = activity.popoverPresentationController {
            popover.barButtonItem = navigationItem.rightBarButtonItems?.first
        }
        present(activity, animated: true)
    }

    @objc
    private func handleEdgeBack(_ gesture: UIScreenEdgePanGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let distance = gesture.translation(in: view).x
        let velocity = gesture.velocity(in: view).x
        guard distance > 56 || velocity > 350 else { return }
        if webView.canGoBack {
            webView.goBack()
        } else {
            dismissBrowser()
        }
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        let scheme = url.scheme?.lowercased()
        if scheme == "http" || scheme == "https" || scheme == "about" {
            decisionHandler(.allow)
            return
        }
        if UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
        decisionHandler(.cancel)
    }

    func webView(
        _ webView: WKWebView,
        createWebViewWith configuration: WKWebViewConfiguration,
        for navigationAction: WKNavigationAction,
        windowFeatures: WKWindowFeatures
    ) -> WKWebView? {
        if navigationAction.targetFrame == nil,
           let requestURL = navigationAction.request.url {
            webView.load(URLRequest(url: requestURL))
        }
        return nil
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

private extension UIViewController {
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

    static func topPresenter(from controller: UIViewController) -> UIViewController {
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

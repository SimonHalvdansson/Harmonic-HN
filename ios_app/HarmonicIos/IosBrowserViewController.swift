import Foundation
import UIKit
import WebKit
import HarmonicKit

final class IosExternalLinkOpener: ExternalLinkOpener {
    func open(request: ExternalLinkRequest) -> Bool {
        guard let url = URL(string: request.url) else { return false }
        if
            request.preferInApp,
            let scheme = url.scheme?.lowercased(),
            scheme == "http" || scheme == "https"
        {
            DispatchQueue.main.async {
                guard let presenter = UIViewController.harmonicTopPresenter else { return }
                let browser = IosBrowserViewController(
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

private final class BrowserDialogCompletion<Value> {
    private var callback: ((Value) -> Void)?

    init(_ callback: @escaping (Value) -> Void) { self.callback = callback }

    func finish(_ value: Value) {
        let completion = callback
        callback = nil
        completion?(value)
    }
}

private final class IosBrowserViewController:
    UIViewController,
    WKNavigationDelegate,
    WKUIDelegate,
    UIGestureRecognizerDelegate
{
    private let initialURL: URL
    private let shareable: Bool
    private let webView: WKWebView
    private let progressView = UIProgressView(progressViewStyle: .bar)
    private let errorView = UIView()
    private let errorLabel = UILabel()
    private var failedURL: URL?
    private var requestedURL: URL
    private var cancelJavaScriptDialog: (() -> Void)?
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
        requestedURL = url
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
        configureErrorView()
        observeWebView()
        webView.load(URLRequest(url: initialURL))
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        guard isBeingDismissed || navigationController?.isBeingDismissed == true else { return }
        cancelJavaScriptDialog?()
        cancelJavaScriptDialog = nil
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
            progressView.isHidden = webView.estimatedProgress >= 1 || !errorView.isHidden
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
        if let failedURL {
            self.failedURL = nil
            errorView.isHidden = true
            webView.load(URLRequest(url: failedURL))
        } else {
            webView.reload()
        }
    }

    @objc
    private func openExternally() {
        UIApplication.shared.open(failedURL ?? webView.url ?? requestedURL)
    }

    @objc
    private func sharePage() {
        let activity = UIActivityViewController(
            activityItems: [failedURL ?? webView.url ?? requestedURL],
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
            if navigationAction.targetFrame?.isMainFrame != false { requestedURL = url }
            decisionHandler(.allow)
            return
        }
        if UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url)
        }
        decisionHandler(.cancel)
    }

    private func configureErrorView() {
        errorView.backgroundColor = .systemBackground
        errorView.isHidden = true
        errorView.translatesAutoresizingMaskIntoConstraints = false
        errorView.accessibilityIdentifier = "harmonic_article_error"
        errorLabel.numberOfLines = 0
        errorLabel.textAlignment = .center
        errorLabel.textColor = .label
        errorLabel.font = .preferredFont(forTextStyle: .body)
        errorLabel.adjustsFontForContentSizeCategory = true
        let retry = UIButton(type: .system)
        retry.setTitle("Retry", for: .normal)
        retry.addTarget(self, action: #selector(reloadPage), for: .touchUpInside)
        let external = UIButton(type: .system)
        external.setTitle("Open in Safari", for: .normal)
        external.addTarget(self, action: #selector(openExternally), for: .touchUpInside)
        let content = UIStackView(arrangedSubviews: [errorLabel, retry, external])
        content.axis = .vertical
        content.spacing = 16
        content.translatesAutoresizingMaskIntoConstraints = false
        errorView.addSubview(content)
        view.addSubview(errorView)
        NSLayoutConstraint.activate([
            errorView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            errorView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            errorView.topAnchor.constraint(equalTo: view.topAnchor),
            errorView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            content.centerYAnchor.constraint(equalTo: errorView.centerYAnchor),
            content.leadingAnchor.constraint(equalTo: errorView.leadingAnchor, constant: 24),
            content.trailingAnchor.constraint(equalTo: errorView.trailingAnchor, constant: -24),
        ])
    }

    private func showPageError(_ message: String, url: URL? = nil) {
        failedURL = url ?? requestedURL
        errorLabel.text = "Couldn't load this page.\n\n" + message
        errorView.isHidden = false
        progressView.isHidden = true
    }

    private func handleNavigationFailure(_ error: Error) {
        let failure = error as NSError
        if failure.domain == NSURLErrorDomain && failure.code == NSURLErrorCancelled { return }
        showPageError(
            failure.localizedDescription,
            url: failure.userInfo[NSURLErrorFailingURLErrorKey] as? URL
        )
    }

    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        failedURL = nil
        errorView.isHidden = true
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!,
                 withError error: Error) {
        handleNavigationFailure(error)
    }

    func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        handleNavigationFailure(error)
    }

    func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        showPageError("The page stopped responding. Try reloading it.", url: webView.url)
    }

    func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse,
                 decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        guard navigationResponse.isForMainFrame && !navigationResponse.canShowMIMEType else {
            decisionHandler(.allow)
            return
        }
        decisionHandler(.cancel)
        showPageError("This file can be opened in Safari.", url: navigationResponse.response.url)
    }

    private func presentJavaScriptDialog(_ alert: UIAlertController, cancel: @escaping () -> Void) {
        guard viewIfLoaded?.window != nil, presentedViewController == nil, !isBeingDismissed else {
            cancel()
            return
        }
        cancelJavaScriptDialog?()
        cancelJavaScriptDialog = cancel
        present(alert, animated: true)
    }

    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) {
        let completion = BrowserDialogCompletion<Void> { _ in completionHandler() }
        let alert = UIAlertController(title: frame.request.url?.host ?? "Website",
                                      message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completion.finish(()) })
        presentJavaScriptDialog(alert) { completion.finish(()) }
    }

    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String,
                 initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) {
        let completion = BrowserDialogCompletion(completionHandler)
        let alert = UIAlertController(title: frame.request.url?.host ?? "Website",
                                      message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completion.finish(false) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { _ in completion.finish(true) })
        presentJavaScriptDialog(alert) { completion.finish(false) }
    }

    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String,
                 defaultText: String?, initiatedByFrame frame: WKFrameInfo,
                 completionHandler: @escaping (String?) -> Void) {
        let completion = BrowserDialogCompletion(completionHandler)
        let alert = UIAlertController(title: frame.request.url?.host ?? "Website",
                                      message: prompt, preferredStyle: .alert)
        alert.addTextField { $0.text = defaultText }
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { _ in completion.finish(nil) })
        alert.addAction(UIAlertAction(title: "OK", style: .default) { [weak alert] _ in
            completion.finish(alert?.textFields?.first?.text ?? "")
        })
        presentJavaScriptDialog(alert) { completion.finish(nil) }
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

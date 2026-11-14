import UIKit
import UserNotifications
import HarmonicKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        prepareUiTestStateIfNeeded()
        _ = IosBackgroundModelTransfers.shared
        IosReplyNotifications.shared.registerBackgroundChecks { HarmonicProcess.shared.bootstrap.replyChecks }
        return true
    }

    func application(_ application: UIApplication, handleEventsForBackgroundURLSession identifier: String,
                     completionHandler: @escaping () -> Void) {
        IosBackgroundModelTransfers.shared.handleBackgroundEvents(identifier: identifier, completion: completionHandler)
    }

    func applicationSignificantTimeChange(_ application: UIApplication) {
        IosReplyNotifications.shared.rescheduleChecks()
        for scene in application.connectedScenes {
            (scene.delegate as? HarmonicSceneDelegate)?.refreshAppearance()
        }
    }

    private func prepareUiTestStateIfNeeded() {
#if DEBUG
        guard ProcessInfo.processInfo.environment["HARMONIC_UI_TESTING"] == "1",
              ProcessInfo.processInfo.environment["HARMONIC_UI_TEST_PRESERVE_DATA"] != "1" else {
            return
        }
        // UI tests use their own DataStore/files root; never reset the normal installation.
        try? FileManager.default.removeItem(at: harmonicUiTestDirectory)
#endif
    }
}

private var harmonicUiTestDirectory: URL {
    FileManager.default.temporaryDirectory.appendingPathComponent("HarmonicUITests", isDirectory: true)
}

var harmonicIsUiTesting: Bool {
#if DEBUG
    return ProcessInfo.processInfo.environment["HARMONIC_UI_TESTING"] == "1"
#else
    return false
#endif
}

var harmonicFilesDirectory: URL {
    if harmonicIsUiTesting { return harmonicUiTestDirectory.appendingPathComponent("files") }
    return FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
}

/// The background scheduler and every scene share one account, preferences and reply checkpoint.
final class HarmonicProcess {
    static let shared = HarmonicProcess()
    let services: IosNativeServices
    let bootstrap: IosHarmonicAppBootstrap

    private init() {
        services = IosNativeServices()
#if DEBUG
        let buildType = "debug"
        let debugBuild = true
#else
        let buildType = "release"
        let debugBuild = false
#endif
        let buildNumber = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0"
        let metadata = AppMetadata(
            name: "Harmonic",
            versionName: Bundle.main.object(
                forInfoDictionaryKey: "CFBundleShortVersionString"
            ) as? String ?? "1.0.0",
            versionCode: Int32(clamping: Int(buildNumber) ?? 0),
            buildNumber: buildNumber,
            buildType: buildType,
            debug: debugBuild,
            debugSettingsEnabled: debugBuild,
            projectUrl: "https://github.com/SimonHalvdansson/Harmonic-HN",
            privacyUrl: "https://simonhalvdansson.github.io/harmonic_privacy.html"
        )
        let fileManager = FileManager.default
        let filesDirectory = harmonicFilesDirectory
        var cacheDirectory = fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0]
#if DEBUG
        if ProcessInfo.processInfo.environment["HARMONIC_UI_TESTING"] == "1" {
            cacheDirectory = harmonicUiTestDirectory.appendingPathComponent("cache")
        }
#endif
        try? fileManager.createDirectory(
            at: filesDirectory,
            withIntermediateDirectories: true
        )
        try? fileManager.createDirectory(
            at: cacheDirectory,
            withIntermediateDirectories: true
        )
        let runtime = IosHostRuntimeBindings(
            metadata: metadata,
            currentMinutesFromMidnight: {
                let components = Calendar.current.dateComponents(
                    [.hour, .minute],
                    from: Date()
                )
                return KotlinInt(
                    int: Int32((components.hour ?? 0) * 60 + (components.minute ?? 0))
                )
            },
            systemDark: {
                let readAppearance = {
                    let style = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
                        .first?.traitCollection.userInterfaceStyle ?? .light
                    return KotlinBoolean(bool: style == .dark)
                }
                // Settings updates can resolve the appearance on a Kotlin worker thread.
                return Thread.isMainThread ? readAppearance() : DispatchQueue.main.sync(execute: readAppearance)
            },
            filesDirectory: filesDirectory.path,
            cacheDirectory: cacheDirectory.path,
            localModels: nil
        )
        let bindings = IosPlatformBindings(
            credentials: services.credentials,
            accountStorage: services.accounts,
            externalLinks: services.externalLinks,
            sharing: services.sharing,
            clipboard: services.clipboard,
            connectivity: services.connectivity,
            timeFormatting: services.timeFormatting,
            appearance: services.appearance,
            textDocuments: services.textDocuments,
            replyNotifications: IosReplyNotifications.shared,
            localSummary: nil,
            nativeLocalSummary: services.localSummary,
            nativeLiteRt: services.liteRt,
            nativeArticleEmbeddings: services.articleEmbeddings,
            modelTransfers: IosBackgroundModelTransfers.shared
        )
        bootstrap = IosHarmonicAppBootstrap(userAgent: "Harmonic-HN-iOS/\(metadata.versionName)", bindings: bindings, runtime: runtime)
    }
}

/// Keep the same application and navigation graph as this window resizes or changes displays.
final class HarmonicSceneDelegate: UIResponder, UIWindowSceneDelegate {
    private var harmonic: IosHarmonicApplication?
    private var services: IosNativeServices?
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else { return }
        if let window {
            window.windowScene = windowScene
            window.makeKeyAndVisible()
            return
        }
        let process = HarmonicProcess.shared
        let services = process.services
        let harmonic = IosHarmonicApplication(bootstrap: process.bootstrap, appearance: services.appearance)
        let root = HarmonicRootViewController(
            content: harmonic.makeViewController()
        )
        root.onScreenCornersChanged = { [weak harmonic] topLeft, topRight, bottomRight, bottomLeft in
            harmonic?.updateScreenCorners(
                topLeft: Float(topLeft), topRight: Float(topRight),
                bottomRight: Float(bottomRight), bottomLeft: Float(bottomLeft)
            )
        }
        services.appearance.attach(root)
        let window = HarmonicWindow(windowScene: windowScene)
        window.onSystemAppearanceChanged = { [weak harmonic] in harmonic?.refreshAppearance() }
        window.rootViewController = root
        window.backgroundColor = root.view.backgroundColor
        self.window = window
        window.makeKeyAndVisible()

        self.services = services
        self.harmonic = harmonic
        IosReplyNotifications.shared.attachReplyOpener { [weak harmonic] url in
            guard let harmonic else { return false }
            harmonic.openReply(url: url)
            return true
        }
        if let response = connectionOptions.notificationResponse {
            IosReplyNotifications.shared.openResponse(response)
        }
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        harmonic?.setForeground(active: true)
        IosReplyNotifications.shared.rescheduleChecks()
    }

    func sceneWillResignActive(_ scene: UIScene) {
        harmonic?.setForeground(active: false)
    }

    func refreshAppearance() {
        harmonic?.refreshAppearance()
    }

    func sceneDidDisconnect(_ scene: UIScene) {
        // UIKit may reconnect this session. Retain its navigation and native browser state.
        harmonic?.setForeground(active: false)
    }

    deinit {
        harmonic?.close()
    }
}

// Observe the window's inherited system style. The root controller deliberately overrides its
// own style to implement Harmonic's explicit themes, so its traits aren't the system preference.
final class HarmonicWindow: UIWindow {
    var onSystemAppearanceChanged: (() -> Void)?

    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if previousTraitCollection?.userInterfaceStyle != traitCollection.userInterfaceStyle {
            onSystemAppearanceChanged?()
        }
    }
}

final class HarmonicRootViewController: UIViewController {
    private let content: UIViewController
    private var darkAppearance = false
    private var themeBackground: UIColor = .systemBackground
    var onScreenCornersChanged: ((CGFloat, CGFloat, CGFloat, CGFloat) -> Void)?

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        view = HarmonicRootView()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content.view)
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        content.didMove(toParent: self)

        let cornerProbe = HarmonicScreenCornerProbe(frame: view.bounds)
        cornerProbe.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        cornerProbe.onChange = { [weak self] topLeft, topRight, bottomRight, bottomLeft in
            self?.onScreenCornersChanged?(topLeft, topRight, bottomRight, bottomLeft)
        }
        view.addSubview(cornerProbe)
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        darkAppearance ? .lightContent : .darkContent
    }

    func applyAppearance(dark: Bool, backgroundArgb: Int32) {
        let argb = UInt32(bitPattern: backgroundArgb)
        let background = UIColor(
            red: CGFloat((argb >> 16) & 0xff) / 255,
            green: CGFloat((argb >> 8) & 0xff) / 255,
            blue: CGFloat(argb & 0xff) / 255,
            alpha: CGFloat((argb >> 24) & 0xff) / 255
        )
        themeBackground = background
        if darkAppearance != dark || overrideUserInterfaceStyle == .unspecified {
            darkAppearance = dark
            overrideUserInterfaceStyle = dark ? .dark : .light
            setNeedsStatusBarAppearanceUpdate()
        }
        updateBackgrounds()
    }

    private func updateBackgrounds() {
        if view.backgroundColor != themeBackground { view.backgroundColor = themeBackground }
        if content.view.backgroundColor != themeBackground { content.view.backgroundColor = themeBackground }
        if let window = view.window, window.backgroundColor != themeBackground {
            window.backgroundColor = themeBackground
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Keep the host background current while Compose resizes its transparent surface.
        updateBackgrounds()
    }

    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        super.viewWillTransition(to: size, with: coordinator)
        updateBackgrounds()
        coordinator.animate(alongsideTransition: { [weak self] _ in
            self?.updateBackgrounds()
        }, completion: { [weak self] _ in
            self?.updateBackgrounds()
        })
    }

}

/// Draw edge to edge, but leave touches starting at the Home indicator with UIKit.
final class HarmonicRootView: UIView {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let target = super.hitTest(point, with: event)
        guard target != nil, let window, window.safeAreaInsets.bottom > 0 else { return target }
        let windowPoint = convert(point, to: window)
        if windowPoint.y >= window.bounds.maxY - window.safeAreaInsets.bottom {
            // Compose's recognizer can deliver a few drag pixels before UIKit cancels it
            // for Home/app switching. Keep this touch outside the Compose subtree from
            // touch-down; UIKit retains its normal system-gesture priority. Touches that
            // start above this area still belong to their original view for the whole drag.
            return self
        }
        return target
    }
}

/// A transparent geometry probe; it does not clip or alter the Compose view itself.
private final class HarmonicScreenCornerProbe: UIView {
    var onChange: ((CGFloat, CGFloat, CGFloat, CGFloat) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        accessibilityElementsHidden = true
        backgroundColor = .clear
#if compiler(>=6.2) && !targetEnvironment(macCatalyst)
        if #available(iOS 26.0, *) {
            cornerConfiguration = .corners(radius: .containerConcentric(minimum: 0))
        }
#endif
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
#if compiler(>=6.2) && !targetEnvironment(macCatalyst)
        if #available(iOS 26.0, *), !ProcessInfo.processInfo.isiOSAppOnMac,
           let window, let screen = window.windowScene?.screen {
            let windowFrame = window.convert(window.bounds, to: screen.coordinateSpace)
            let display = screen.bounds
            let fullScreen = abs(windowFrame.minX - display.minX) < 0.5 &&
                abs(windowFrame.minY - display.minY) < 0.5 &&
                abs(windowFrame.width - display.width) < 0.5 &&
                abs(windowFrame.height - display.height) < 0.5
            if fullScreen {
                // Query in layoutSubviews so UIKit invalidates this probe when the
                // effective corners change (rotation, resizing or a different display).
                onChange?(effectiveRadius(corner: .topLeft), effectiveRadius(corner: .topRight),
                    effectiveRadius(corner: .bottomRight), effectiveRadius(corner: .bottomLeft))
                return
            }
        }
#endif
        onChange?(0, 0, 0, 0)
    }
}

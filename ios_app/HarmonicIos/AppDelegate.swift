import UIKit
import HarmonicKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        prepareUiTestStateIfNeeded()
        return true
    }

    func applicationSignificantTimeChange(_ application: UIApplication) {
        for scene in application.connectedScenes {
            (scene.delegate as? HarmonicSceneDelegate)?.refreshAppearance()
        }
    }

    private func prepareUiTestStateIfNeeded() {
#if DEBUG
        guard ProcessInfo.processInfo.environment["HARMONIC_UI_TESTING"] == "1" else {
            return
        }
        if let bundleIdentifier = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleIdentifier)
        }
        [
            "com.simon.harmonichackernews.GLOBAL_SHARED_PREFERENCES_KEY",
            "com.simon.harmonichackernews.PREVIEW_IMAGE_CACHE_PREFERENCES",
            "file_access_times",
        ].forEach { suiteName in
            UserDefaults(suiteName: suiteName)?.removePersistentDomain(forName: suiteName)
        }
#endif
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
        let services = IosNativeServices()
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
            privacyUrl: "https://github.com/SimonHalvdansson/Harmonic-HN/blob/main/PRIVACY.md"
        )
        let fileManager = FileManager.default
        let filesDirectory = fileManager.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        )[0]
        let cacheDirectory = fileManager.urls(
            for: .cachesDirectory,
            in: .userDomainMask
        )[0]
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
            systemDark: { [weak self, weak windowScene] in
                let style = self?.window?.traitCollection.userInterfaceStyle
                    ?? windowScene?.traitCollection.userInterfaceStyle
                    ?? .light
                return KotlinBoolean(bool: style == .dark)
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
            replyNotifications: nil,
            localSummary: nil,
            nativeLocalSummary: services.localSummary
        )
        let harmonic = IosHarmonicApplication(bindings: bindings, runtime: runtime)
        let root = HarmonicRootViewController(
            content: harmonic.makeViewController()
        )
        services.appearance.attach(root)
        let window = HarmonicWindow(windowScene: windowScene)
        window.onSystemAppearanceChanged = { [weak harmonic] in harmonic?.refreshAppearance() }
        window.rootViewController = root
        self.window = window
        window.makeKeyAndVisible()

        self.services = services
        self.harmonic = harmonic
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        harmonic?.setForeground(active: true)
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

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
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

    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        darkAppearance ? .lightContent : .darkContent
    }

    func applyDarkAppearance(_ dark: Bool) {
        guard darkAppearance != dark || overrideUserInterfaceStyle == .unspecified else { return }
        darkAppearance = dark
        overrideUserInterfaceStyle = dark ? .dark : .light
        view.backgroundColor = dark ? .black : .systemBackground
        setNeedsStatusBarAppearanceUpdate()
    }

}

import UIKit
import HarmonicShared

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    private var harmonic: IosHarmonicApplication?
    private var services: IosNativeServices?
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        prepareUiTestStateIfNeeded()
        let services = IosNativeServices()
#if DEBUG
        let buildType = "debug"
        let debugBuild = true
#else
        let buildType = "release"
        let debugBuild = false
#endif
        let metadata = AppMetadata(
            name: "Harmonic",
            versionName: Bundle.main.object(
                forInfoDictionaryKey: "CFBundleShortVersionString"
            ) as? String ?? "1.0.0",
            versionCode: 1,
            buildNumber: Bundle.main.object(
                forInfoDictionaryKey: "CFBundleVersion"
            ) as? String ?? "1",
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
            systemDark: { [weak self] in
                let style = self?.window?.traitCollection.userInterfaceStyle
                    ?? UIScreen.main.traitCollection.userInterfaceStyle
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
            replyNotifications: nil,
            localSummary: nil,
            nativeLocalSummary: services.localSummary
        )
        let harmonic = IosHarmonicApplication(bindings: bindings, runtime: runtime)
        let root = HarmonicRootViewController(
            content: harmonic.makeViewController()
        )
        services.appearance.attach(root)
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = root
        self.window = window
        window.makeKeyAndVisible()

        self.services = services
        self.harmonic = harmonic
        return true
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

    func applicationWillTerminate(_ application: UIApplication) {
        harmonic?.close()
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

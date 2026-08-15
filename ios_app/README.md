# Harmonic iOS

Open `HarmonicIos.xcodeproj` in Xcode and run the `HarmonicIos` scheme. The first build invokes
Gradle's `:shared_ui:embedAndSignAppleFrameworkForXcode` task so Swift always links the matching
Kotlin/Compose framework.

The native shell owns Keychain credentials, the atomic Hacker News account record, system URL
routing, sharing, clipboard access, connectivity, locale-aware time formatting, the app icon, and
status-bar appearance. The comments scene embeds `WKWebView` with Compose UIKit interop so its
shared comments sheet remains available above the article. Compose Navigation Event dispatches the
interactive system left-edge back gesture through the shared navigation hierarchy. Application
logic and the rest of the UI remain in the shared KMP modules.

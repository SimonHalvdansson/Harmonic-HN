# Harmonic iOS

Open `HarmonicIos.xcodeproj` in Xcode and run the `HarmonicIos` scheme. The first build invokes
Gradle's `:shared_ui:embedAndSignAppleFrameworkForXcode` task so Swift always links the matching
Kotlin/Compose framework. The build phase uses an existing `JAVA_HOME`, Android Studio's bundled
runtime, or the macOS Java 21 resolver in that order. Its generated dependency file lets Xcode skip
the Gradle phase until shared sources, resources, or build inputs change.

The native shell owns Keychain credentials, the atomic Hacker News account record, system URL
routing, sharing, clipboard access, connectivity, locale-aware time formatting, the app icon, and
status-bar appearance. The comments scene embeds `WKWebView` with Compose UIKit interop so its
shared comments sheet remains available above the article. Compose Navigation Event dispatches the
interactive system left-edge back gesture through the shared navigation hierarchy. Application
logic and the rest of the UI remain in the shared KMP modules.

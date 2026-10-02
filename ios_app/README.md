# Harmonic iOS

Open `HarmonicIos.xcodeproj` in Xcode and run the `HarmonicIos` scheme. The first build invokes
Gradle's `:ui:embedAndSignAppleFrameworkForXcode` task so Swift always links the matching
Kotlin/Compose framework. The build phase uses an existing `JAVA_HOME`, Android Studio's bundled
runtime, or the macOS Java 21 resolver in that order. Its generated dependency file lets Xcode skip
the Gradle phase until KMP sources, resources, or build inputs change.

The native shell owns Keychain credentials, the atomic Hacker News account record, system URL
routing, sharing, clipboard access, connectivity, locale-aware time formatting, the app icon, and
status-bar appearance. The comments scene embeds `WKWebView` with Compose UIKit interop so its
Compose comments sheet remains available above the article. Compose Navigation Event dispatches the
interactive system left-edge back gesture through the KMP navigation hierarchy. Application logic
and the rest of the UI remain in the KMP modules.

Local summaries support Apple Intelligence and downloadable Gemma LiteRT-LM models. The local
Swift package in `LiteRTLM/` pins Google's official iOS XCFramework and checksum, avoiding the
multi-platform repository's large binary history. Inference runs on a serial CPU worker on both
devices and arm64 simulators, with streamed progress and cancellation. iOS does not include
llama.cpp or GGUF models. Downloads run while the app is active; interrupted downloads preserve
their partial file and resume when requested again.

The `HarmonicIosTests` target checks catalog/selection and the Kotlin-to-native summary bridge.
Its real Gemma tests check consecutive summaries and cancellation when `litert-test-fixture.litertlm`
(the catalog's Gemma E2B file) is present in the test host's Application Support directory; otherwise
those tests are skipped. Keep the large fixture outside the repository.

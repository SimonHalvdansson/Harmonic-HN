# Harmonic iOS

Open `HarmonicIos.xcodeproj` in Xcode and run the `HarmonicIos` scheme. The first build invokes
Gradle's `:ui:embedAndSignAppleFrameworkForXcode` task so Swift always links the matching
Kotlin/Compose framework. The build phase uses an existing `JAVA_HOME`, Android Studio's bundled
runtime, or the macOS Java 21 resolver in that order. Its generated dependency file lets Xcode skip
the Gradle phase until KMP sources, resources, or build inputs change.

Xcode Release builds allow Gradle a 12 GiB heap for Kotlin/Native optimization. Debug builds use
the shared settings in `gradle.properties`. Set `HARMONIC_IOS_RELEASE_JVM_ARGS` in the build
environment to override the Release JVM arguments (default: `-Xms1024m -Xmx12288m`).

The app's version and build number come from `harmonic.versionName` and
`harmonic.versionCode` in the root `gradle.properties`, shared with Android and desktop.
The Generate App Metadata build phase adds them to a generated copy of `HarmonicIos/Info.plist`
before Xcode processes and signs the app, for both Debug and Release builds.

Tag releases (`v*.*`) also build an unsigned iOS device archive on the same Xcode runner as iOS CI.
The tag must match the app version in `gradle.properties`. The GitHub release includes
`Harmonic-<version>-ios-unsigned.ipa`; the Actions run retains the zipped `.xcarchive` with debug
symbols and the Xcode build result. These artifacts are unsigned and require signing and provisioning
before installation or distribution. This job does not upload to TestFlight or the App Store.

The native shell owns Keychain credentials, the atomic Hacker News account record, system URL
routing, sharing, clipboard access, connectivity, locale-aware time formatting, the app icon, and
status-bar appearance. The comments scene embeds `WKWebView` with Compose UIKit interop so its
Compose comments sheet remains available above the article. Compose Navigation Event dispatches the
interactive system left-edge back gesture through the KMP navigation hierarchy. Application logic
and the rest of the UI remain in the KMP modules.

Account activity, read state, discussion muting, and Debug → Account's public-username preview are
shared and work without native notifications. A preview never changes Keychain credentials or
the account used to post and vote. For native alerts, supply `ReplyNotificationPlatform` through
`IosPlatformBindings.replyNotifications`; its optional `settingsHost: NotificationSettingsHost`
connects authorization refresh, permission requests, and the system notification-settings link.
The shared Account route refreshes authorization on resume. Until that adapter is supplied,
foreground checking stays available and native alert/background controls remain disabled.
Verify the native adapter, permission changes, and background scheduling on an iOS simulator/device.

Cloud AI is intentionally disabled on iOS. `IosHarmonicAppBootstrap` sets
`HarmonicHostConfiguration.cloudAiSupported = false`: cloud provider settings and search entries
are hidden, restored settings resolve to local mode, and `AiSummarySettingsRepository.cloudConfig()`
rejects cloud use. Shared cloud UI and networking code also serves Android and desktop; its presence
does not mean the feature is enabled on iOS. When reviewing iOS capabilities or release readiness,
trace the host configuration through the shared settings and runtime guards.

Local summaries support Apple Intelligence and downloadable Gemma, LFM3, Ministral, Nemotron, and Qwen
LiteRT-LM models. The local Swift package in `LiteRTLM/` pins Google's official iOS XCFramework and checksum, avoiding the
multi-platform repository's large binary history. Inference runs on a serial CPU worker on both
devices and arm64 simulators, with streamed progress and cancellation. Model downloads use an
app-wide background `URLSession`, reconnect to existing transfers on launch, and install completed
files even when no scene is open. Retry uses saved OS resume data when available; existing foreground
`.download` partials are resumed with HTTP Range requests. Cancel and Clear models wait for native
transfers to stop before deleting files and resume data. iOS controls background scheduling; explicitly
force-quitting the app cancels its background transfers and requires reopening the app to retry.
Free-space checks credit bytes retained with matching native resume data and reserve the additional
installation copy for legacy partials. A fallback to a fresh download checks available space again.

The `HarmonicIosTests` target checks catalog/selection and the Kotlin-to-native summary bridge.
Its background-transfer tests use a loopback HTTP server and isolated ephemeral sessions to cover
file delivery, HTTP errors, size validation, legacy partials, duplicate requests, cancellation, and
reloading interrupted transfer state. iOS CI executes this suite after building the test host.
OS suspension and relaunch delivery still need device verification with the production background session.
Its real Gemma tests check consecutive summaries and cancellation when `litert-test-fixture.litertlm`
(the catalog's Gemma E2B file) is present in the test host's Application Support directory; otherwise
those tests are skipped. Keep the large fixture outside the repository.

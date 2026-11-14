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

For App Store distribution, assign the app target to the intended Apple Developer Program team
and register `com.simon.harmonichackernews.ios` in that team's App Store Connect account. Use Xcode's
Archive/Distribute workflow to sign and validate the build. Complete the store listing, iPhone/iPad
screenshots, privacy details, age rating, export-compliance answers, support URL and review contact
before submission. The in-app privacy policy URL is
`https://simonhalvdansson.github.io/harmonic_privacy.html`.

Review notes should explain the App Transport Security exception: users open arbitrary third-party
Hacker News articles, including HTTP-only sites, in the browser and reader extraction. Authentication
and account actions use HTTPS. Explain that reply alerts are local notifications from periodic
background checks, not remote push. Browsing does not require an account; sign-in uses an existing
Hacker News account. Profile menus provide blocking and reporting, and About provides developer
contact and the privacy policy. Verify signed-device background refresh/download recovery and
available on-device AI before releasing; simulator fixtures cannot validate OS wakeup timing or
Apple Intelligence hardware availability.

The native shell owns Keychain credentials, the atomic Hacker News account record, system URL
routing, sharing, clipboard access, connectivity, locale-aware time formatting, the app icon, and
status-bar appearance. The comments scene embeds `WKWebView` with Compose UIKit interop so its
Compose comments sheet remains available above the article. Compose Navigation Event dispatches the
interactive system left-edge back gesture through the KMP navigation hierarchy. Application logic
and the rest of the UI remain in the KMP modules.

Account activity, read state, discussion muting, and Debug → Account's public-username preview are
shared and work without notification permission. A preview never changes Keychain credentials or
uses the account to post or vote. The native `IosReplyNotifications` adapter supplies notification
permission/settings, grouped local alerts, and `BGAppRefreshTask` scheduling. Notification taps open
the parent discussion at the reply through shared navigation, including on a cold launch. The
process-owned app graph supports refreshes without an open scene; expiration cancels the scanner,
and checkpoints are flushed before normal completion. Checks respect account changes, muted discussions,
Wi-Fi-only mode and local checking hours. iOS chooses the actual wakeup time: the selected frequency
is a minimum interval, not a delivery guarantee. A scan that cannot finish in a short refresh
queues a `BGProcessingTask` catch-up; iOS runs that longer task when the device is idle. Both paths
honor Wi-Fi and checking hours, and expiration never advances an incomplete scan's checkpoint.
Background App Refresh must be enabled, and
force-quitting prevents automatic checks until the app is opened again. No push server is used.
The Account route refreshes notification authorization on resume. Native unit tests cover delivery,
permission changes, scheduling, cancellation and cold-launch links; iOS Kotlin tests cover account
ownership, Wi-Fi/checking hours, persistence, expiry and the longer catch-up budget. The opt-in
`IosReplyNotificationUITests` smoke test uses `TEST_RUNNER_HARMONIC_REPLY_TEST_USER=<public username>`
to send a local alert for an existing public reply and tap it into the discussion. It never logs in
or performs public actions.

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
reloading interrupted transfer state. iOS CI runs the deterministic native tests for reply notifications,
packaging, model storage/selection, summary bridges and system gestures. It compiles the download,
live-website and real-inference tests but leaves their execution to local verification.
OS suspension and relaunch delivery still need device verification with the production background session.
Its real Gemma tests check consecutive summaries and cancellation when `litert-test-fixture.litertlm`
(the catalog's Gemma E2B file) is present in the test host's Application Support directory; otherwise
those tests are skipped. Keep the large fixture outside the repository.

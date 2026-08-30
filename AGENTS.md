# Agent Guidelines

This repository contains the Kotlin Multiplatform Harmonic for Hacker News app. Android is the
main distribution and the default target when the user does not specify a platform. The iOS and
desktop hosts are ready but have not been released. Core logic and Compose UI have automated
Kotlin tests, and Android release builds are checked by GitHub Actions.

General tips:
- `app/` is the Android application shell. Portable application logic belongs in `core/`,
  Compose UI belongs in `ui/`, portable assets belong in `resources/`, and
  `desktop_app/` is the ready, unreleased desktop application host.
- Building the app may require Android SDK components which may not be available in minimal environments.
- Keep commits small and descriptive.
- Run focused KMP tests for portable changes. `:core:desktopTest` and
  `:ui:desktopTest` execute their respective `commonTest` suites on the desktop JVM target.
- When adding features or bug fixes, ensure the app compiles with the debug build check below.
- For tiny, low-risk changes such as text copy, margins, padding, font weight, other simple XML/style tweaks, or Kotlin edits that only swap an existing helper call, adjust a constant, or update straightforward local control flow, do not run `assembleDebug` or `lintDebug` unless the user asks or there is a concrete reason to suspect a compile, build, resource, or API problem. Instead, inspect the diff and mention that the build was intentionally skipped.
- If Git reports dubious ownership because Codex is running as a sandbox user, use a per-command safe-directory override such as `git -c safe.directory=C:/Users/Simon/Documents/GitHub/Harmonic-HN status --short` instead of changing global Git config.

## Kotlin Multiplatform Boundaries

- `core/` targets Android, iOS, and desktop. It owns platform-neutral models, parsing,
  filtering, formatting, repositories, state machines, settings contracts, suspend-first networking,
  and portable filesystem implementations. Keep `commonMain` free of Android, AndroidX, Foundation,
  and JVM-only APIs.
- `ui/` targets Android, iOS, and desktop and owns the Compose screens and navigation.
  Platform source sets should contain only host-specific UI integration.
- Put platform facilities behind the contracts in
  `core/src/commonMain/kotlin/com/simon/harmonichackernews/platform/`; Android
  implementations belong in the app module's Android main source set, while
  Apple adapters belong in `core/src/iosMain/` until a dedicated Xcode host exists.
- Prefer coroutines and suspend APIs for portable networking. Ktor engines, native directory choices,
  credential/keychain access, notifications, intents, native browser views, and background-work
  schedulers stay in platform code.
- Use `HarmonicPersistentStorageFactory` for production story/article/PDF storage graphs. Hosts
  provide app-owned file/cache roots and key-value adapters; they should not recreate file layouts,
  cache metadata callbacks, or naming policies.
- Use `WebContentPagePolicy` and `ReaderModeFontResourcePolicy` in browser hosts so PDF/error-page
  routing, cache fallbacks, reader eligibility, and reader-font pairing remain consistent.
- Use `FileLocalModelStorage`, `LocalModelFilePolicy`, and
  `FileResumableDownloadDestination` for downloadable model files. Hosts retain free-space APIs,
  background scheduling, notifications, and native inference runtime delivery.
- iOS must supply `IosPlatformBindings` with an atomic, Keychain-backed
  `HackerNewsAccountRepository`; do not derive account persistence from separate credential reads.
  `IosHostRuntimeBindings` must receive the native files and cache directory paths.
- `core/src/commonTest/` and `ui/src/commonTest/` are active test source sets;
  `core/src/desktopMain/`, both modules' `iosMain/` directories, and desktop sources are not
  placeholders.
- After changing core logic, run `./gradlew :core:compileCommonMainKotlinMetadata` and
  `./gradlew :core:desktopTest`. Compile affected platform adapters with
  `:core:compileKotlinIosSimulatorArm64`, `:ui:compileKotlinIosSimulatorArm64`, or
  `:desktop_app:compileKotlinDesktop` as applicable, in addition to Android verification below.

## Navigation Transitions

- Full-screen forward navigation in a single-pane layout uses
  `ActivityNavigationTransitionViewport` from `ui/navigation/ActivityNavigationTransition.kt`.
  It mirrors Android's default activity surface animation: the destination moves from `+96dp` to
  rest over 450ms, its complete surface fades in after a 50ms delay over 83ms, and the retained
  source moves from rest to `-96dp` without fading. Use the shared constants and easing in that file.
- The destination's sampled edge must extend across the gap exposed by translation and must fade as
  part of the destination surface. A plain `NavDisplay` `slideInHorizontally` plus `fadeIn` leaves a
  moving background boundary and is not an equivalent implementation. Nested single-pane
  navigators, including Settings list/detail navigation, must use the same surface compositor.
- Adaptive two-pane detail changes, dialogs, and component-level content changes are not
  full-screen activity opens; keep their pane- or component-specific motion. Predictive-back hosts
  may supply their own gesture modifiers and should avoid replaying a completed exit animation.

## Icon Guidelines

When adding or replacing app icons, use **Material Symbols**, not legacy Material Icons. Prefer the **Rounded** style and the official Android vector export. Match the repo's current default symbol settings unless there is a specific selected/filled state: Fill `0`, Weight `400`, Grade `0`, Optical Size `24`, 24dp size. Use source-aligned drawable names such as `ic_thumb_up.xml`, preserve the existing tint/alpha behavior for the target context, and avoid replacing custom branded/provider/badge assets with generic symbols.

Prefer fetching the official Android vector from Google's Material Symbols repository:

```
https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<symbol_name>/materialsymbolsrounded/<symbol_name>_24px.xml
```

For selected or filled states, use the same path with `<symbol_name>_fill1_24px.xml` when that filled source asset is appropriate. If the raw Android vector is unavailable, use Google Fonts Material Symbols with the same settings above and export/download the Android vector; do not substitute a different icon family or style without checking with the user.

## Build Verification

Use Android Studio's bundled JBR runtime when invoking Gradle from Codex or other CLI environments on this machine. A plain `./gradlew` may fail because no compatible system runtime is installed.

For quick verification, run the debug build check:

```
/bin/zsh -lc 'PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew assembleDebug'
```

This is the preferred default Codex verification step for substantive code, resource, manifest, or behavior changes. It is substantially faster than a full `build` while still checking that the app compiles and packages in debug mode.

If a substantive change touches UI, resources, manifests, or other Android configuration that lint commonly flags, also run:

```
/bin/zsh -lc 'PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew lintDebug'
```

Use `assembleDebug` for the normal edit/verify loop when the change warrants compilation, and add `lintDebug` when the change justifies the extra time. Skip both for minor presentation-only edits where inspection of the diff is sufficient.

## Device Verification

Do not start, stop, install to, or control an Android device or emulator unless the user explicitly asks for device/emulator interaction or verification. A build or code-review request alone does not authorize device use.

When device use is authorized, use Google's Android CLI as the primary interface. It is installed as `android` (normally `/Users/simon/.local/bin/android`) together with its agent skill.

- Use `android emulator list` and `android emulator start <avd>` for AVD discovery and startup. Do not start a duplicate when the requested AVD is already running.
- Use `adb devices -l` only when needed to confirm connected state and obtain serials, because the current Android CLI does not reliably expose connected-device serials. If more than one target is connected, pass `--device=<serial>` to every device-specific `android` command and `-s <serial>` to every fallback `adb` command.
- Deploy and launch APKs with `android run --apks=<path> --device=<serial>`, or use `android install` when installation should not launch a component.
- Inspect UI state with `android layout --device=<serial> --pretty`. Re-observe after every action and prefer `--diff` for subsequent checks. Use semantic text, content descriptions, interactions, state, bounds, and center coordinates instead of raw hierarchy dumps.
- When layout data is insufficient, use `android screen capture --annotate`, visually inspect the image, and resolve a verified label with `android screen resolve --screenshot=<path> --string='tap #N'`.
- Keep ADB as a narrow fallback for capabilities Android CLI does not yet provide: discovering connected serials, `adb shell input` taps/text/swipes/key events, launching an already-installed package without reinstalling it, and screen recording. Confirm a text field is focused before typing, scroll slowly, and re-inspect with `android layout` after each input.

### Legacy/Compose side-by-side QA

On 2026-08-01, the `main` branch's `debugFast` build was installed on every connected QA target: the physical Pixel 8 Pro plus the Pixel 9a, foldable, and tablet emulators. It remains installed as `com.simon.harmonichackernews` with the label **Harmonic** for legacy View comparisons.

While the `codex/compose` migration branch is in progress, its debug and debugFast variants use the temporary application ID `com.simon.harmonichackernews.compose` and label **Harmonic Compose**. This lets both builds remain installed. Re-check serials with `adb devices -l`, then use the appropriate explicit package when launching an already-installed build:

```
adb -s <serial> shell monkey -p com.simon.harmonichackernews -c android.intent.category.LAUNCHER 1
adb -s <serial> shell monkey -p com.simon.harmonichackernews.compose -c android.intent.category.LAUNCHER 1
```

Do not remove the temporary suffix until legacy side-by-side QA is finished. Do not uninstall either package or clear its data as part of comparison testing.

If a required ADB fallback is not on `PATH`, use `/Users/simon/Library/Android/sdk/platform-tools/adb` rather than searching the system repeatedly.

### Debug Fixtures

Before searching live Hacker News content or adding temporary debug hooks, check Settings -> Debug. Its Sample content section provides repeatable link posts, reference-link posts, polls, internal HN links, and video examples. It also provides direct access to dialogs and arbitrary HN item IDs. Prefer these fixtures when they cover the behavior being tested. Do not duplicate their hardcoded item IDs in `AGENTS.md`; `ui/settings/DebugSettingsScreen.kt` is the source of truth.

### Device State Restoration

Before changing device or app state for verification, note the original state. Restore any connectivity mode, animation scales, orientation, app preferences, and other persistent changes when verification is complete. Do not clear app data, caches, accounts, or user content unless the user explicitly authorizes it.

### Screenshot and Animation Capture

Prefer Android CLI capture over raw ADB, desktop, or computer screenshots. Use desktop or computer screenshot tools only when the user explicitly requests them or the relevant surface cannot be captured from the device.

- For static screenshots and before/after comparisons, use `android screen capture --device=<serial> --output=<name>.png`, then visually inspect the resulting image before acting on it.
- When verification depends on text, visibility, state, clickability, or scrollability, use `android layout --device=<serial> --pretty`; use `--diff` after interactions.
- When semantic layout data is ambiguous or unavailable, capture with `--annotate`, visually inspect the labels, and use `android screen resolve` for coordinates.
- For animations or transitions, prefer `adb shell screenrecord /sdcard/<name>.mp4`, followed by `adb pull` and frame extraction from the video. This is faster and more consistent than repeatedly invoking `screencap`.
- For subtle animations, temporarily slow the emulator's animation scales, normally to 5x, and restore their original values afterward.
- Avoid high-frequency screenshot loops when a screen recording will work; rapid captures can interfere with rendering or produce black GPU tiles.
- When screenshots will be shown in the final response, save representative images in the current task's visualization directory rather than `/tmp` so the evidence remains available. Bulk diagnostic captures and extracted frames may remain in `/tmp`.

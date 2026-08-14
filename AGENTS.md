# Agent Guidelines

This repository contains the Kotlin Multiplatform Harmonic for Hacker News app. Android is the
current production host; iOS and desktop host implementations are in progress. Shared logic and UI
have automated Kotlin tests, and Android release builds are checked by GitHub Actions.

General tips:
- `app/` is the Android application shell. Portable application logic belongs in `shared_logic/`,
  shared Compose UI belongs in `shared_ui/`, portable assets belong in `shared_resources/`, and
  `desktop_app/` is the production desktop application host.
- The in-app changelog lives in `app/src/main/java/com/simon/harmonichackernews/utils/Changelog.java`.
- Do not update the changelog unless the user explicitly asks for it.
- Building the app may require Android SDK components which may not be available in minimal environments.
- Keep commits small and descriptive.
- Run focused KMP tests for shared changes. `:shared_logic:desktopTest` and
  `:shared_ui:desktopTest` execute their respective `commonTest` suites on the desktop JVM target.
- When adding features or bug fixes, ensure the app compiles with the debug build check below.
- For tiny, low-risk changes such as text copy, margins, padding, font weight, other simple XML/style tweaks, or Java/Kotlin edits that only swap an existing helper call, adjust a constant, or update straightforward local control flow, do not run `assembleDebug` or `lintDebug` unless the user asks or there is a concrete reason to suspect a compile, build, resource, or API problem. Instead, inspect the diff and mention that the build was intentionally skipped.
- If Git reports dubious ownership because Codex is running as a sandbox user, use a per-command safe-directory override such as `git -c safe.directory=C:/Users/Simon/Documents/GitHub/Harmonic-HN status --short` instead of changing global Git config.

## Kotlin Multiplatform Boundaries

- `shared_logic/` targets Android, iOS, and desktop. It owns platform-neutral models, parsing,
  filtering, formatting, repositories, state machines, settings contracts, suspend-first networking,
  and shared filesystem implementations. Keep `commonMain` free of Android, AndroidX, Foundation,
  and `java.*` APIs.
- `shared_ui/` targets Android, iOS, and desktop and owns the shared Compose screens and navigation.
  Platform source sets should contain only host-specific UI integration.
- Put platform facilities behind the contracts in
  `shared_logic/src/commonMain/kotlin/com/simon/harmonichackernews/platform/`; Android
  implementations belong in `app/src/main/java/com/simon/harmonichackernews/platform/`, while
  Apple adapters belong in `shared_logic/src/iosMain/` until a dedicated Xcode host exists.
- Prefer coroutines and suspend APIs for shared networking. Ktor engines, native directory choices,
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
- `shared_logic/src/commonTest/` and `shared_ui/src/commonTest/` are active test source sets;
  `shared_logic/src/desktopMain/`, both modules' `iosMain/` directories, and desktop sources are not
  placeholders.
- After changing shared logic, run `./gradlew :shared_logic:compileCommonMainKotlinMetadata` and
  `./gradlew :shared_logic:desktopTest`. Compile affected platform adapters with
  `:shared_logic:compileKotlinIosSimulatorArm64`, `:shared_ui:compileKotlinIosSimulatorArm64`, or
  `:desktop_app:compileKotlinDesktop` as applicable, in addition to Android verification below.

## Icon Guidelines

When adding or replacing app icons, use **Material Symbols**, not legacy Material Icons. Prefer the **Rounded** style and the official Android vector export. Match the repo's current default symbol settings unless there is a specific selected/filled state: Fill `0`, Weight `400`, Grade `0`, Optical Size `24`, 24dp size. Use source-aligned drawable names such as `ic_thumb_up.xml`, preserve the existing tint/alpha behavior for the target context, and avoid replacing custom branded/provider/badge assets with generic symbols.

Prefer fetching the official Android vector from Google's Material Symbols repository:

```
https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/<symbol_name>/materialsymbolsrounded/<symbol_name>_24px.xml
```

For selected or filled states, use the same path with `<symbol_name>_fill1_24px.xml` when that filled source asset is appropriate. If the raw Android vector is unavailable, use Google Fonts Material Symbols with the same settings above and export/download the Android vector; do not substitute a different icon family or style without checking with the user.

## Build Verification

Use the Android Studio Java runtime when invoking Gradle from Codex or other CLI environments on this machine. A plain `./gradlew` may fail because no system Java runtime is installed.

For quick verification, run the debug build check:

```
/bin/zsh -lc 'JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH" ./gradlew assembleDebug'
```

This is the preferred default Codex verification step for substantive code, resource, manifest, or behavior changes. It is substantially faster than a full `build` while still checking that the app compiles and packages in debug mode.

If a substantive change touches UI, resources, manifests, or other Android configuration that lint commonly flags, also run:

```
/bin/zsh -lc 'JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH" ./gradlew lintDebug'
```

Use `assembleDebug` for the normal edit/verify loop when the change warrants compilation, and add `lintDebug` when the change justifies the extra time. Skip both for minor presentation-only edits where inspection of the diff is sufficient.

## Device Verification

Do not run or control a connected Android device or emulator unless the user explicitly asks for device verification. When asked, use `adb devices` to confirm it is online. If more than one device is connected, select the intended target explicitly with `adb -s <serial>` for every install, navigation, inspection, and capture command. To verify UI changes, install the debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, navigate with `adb shell input tap ...`, and inspect the hierarchy with `adb shell uiautomator dump` when verifying text, visibility, state, or clickability.

### Legacy/Compose side-by-side QA

On 2026-08-01, the `main` branch's `debugFast` build was installed on every connected QA target: the physical Pixel 8 Pro plus the Pixel 9a, foldable, and tablet emulators. It remains installed as `com.simon.harmonichackernews` with the label **Harmonic** for legacy View comparisons.

While the `codex/compose` migration branch is in progress, its debug and debugFast variants use the temporary application ID `com.simon.harmonichackernews.compose` and label **Harmonic Compose**. This lets both builds remain installed. Re-check serials with `adb devices`, then use the appropriate explicit package when launching:

```
adb -s <serial> shell monkey -p com.simon.harmonichackernews -c android.intent.category.LAUNCHER 1
adb -s <serial> shell monkey -p com.simon.harmonichackernews.compose -c android.intent.category.LAUNCHER 1
```

Do not remove the temporary suffix until legacy side-by-side QA is finished. Do not uninstall either package or clear its data as part of comparison testing.

If `adb` or `emulator` is not on `PATH`, use the binaries under `/Users/simon/Library/Android/sdk/platform-tools/` and `/Users/simon/Library/Android/sdk/emulator/` rather than searching the system repeatedly.

### Debug Fixtures

Before searching live Hacker News content or adding temporary debug hooks, check Settings -> Debug. Its Sample content section provides repeatable link posts, reference-link posts, polls, internal HN links, and video examples. It also provides direct access to dialogs and arbitrary HN item IDs. Prefer these fixtures when they cover the behavior being tested. Do not duplicate their hardcoded item IDs in `AGENTS.md`; `ui/settings/DebugSettingsScreen.kt` is the source of truth.

### Device State Restoration

Before changing device or app state for verification, note the original state. Restore any connectivity mode, animation scales, orientation, app preferences, and other persistent changes when verification is complete. Do not clear app data, caches, accounts, or user content unless the user explicitly authorizes it.

### Screenshot and Animation Capture

Prefer native ADB capture over desktop or computer screenshots. Use desktop or computer screenshot tools only when the user explicitly requests them or the relevant surface cannot be captured through ADB.

- For static screenshots and before/after comparisons, use `adb exec-out screencap -p > <name>.png`. This captures a lossless, native-resolution image without creating a temporary file on the device.
- For animations or transitions, prefer `adb shell screenrecord /sdcard/<name>.mp4`, followed by `adb pull` and frame extraction from the video. This is faster and more consistent than repeatedly invoking `screencap`.
- For subtle animations, temporarily slow the emulator's animation scales, normally to 5x, and restore their original values afterward.
- Avoid high-frequency `screencap` loops when a screen recording will work; rapid captures can interfere with rendering or produce black GPU tiles.
- Use `uiautomator dump` alongside visual capture when verification depends on view state rather than appearance alone.
- When screenshots will be shown in the final response, save representative images in the current task's visualization directory rather than `/tmp` so the evidence remains available. Bulk diagnostic captures and extracted frames may remain in `/tmp`.

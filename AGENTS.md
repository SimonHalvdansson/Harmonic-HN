# Agent Guidelines

This repository contains the Harmonic for Hacker News Android app. There are **no** automated tests or CI scripts. When asked to run tests, respond that this project has none.

General tips:
- Source code lives in the `app/` directory and the project is built with Gradle.
- The in-app changelog lives in `app/src/main/java/com/simon/harmonichackernews/utils/Changelog.java`.
- Do not update the changelog unless the user explicitly asks for it.
- Building the app may require Android SDK components which may not be available in minimal environments.
- Keep commits small and descriptive.
- No test framework is configured. You can skip `./gradlew test` or similar commands.
- When adding features or bug fixes, ensure the app compiles with the debug build check below.
- For tiny, low-risk changes such as text copy, margins, padding, font weight, other simple XML/style tweaks, or Java/Kotlin edits that only swap an existing helper call, adjust a constant, or update straightforward local control flow, do not run `assembleDebug` or `lintDebug` unless the user asks or there is a concrete reason to suspect a compile, build, resource, or API problem. Instead, inspect the diff and mention that the build was intentionally skipped.
- If Git reports dubious ownership because Codex is running as a sandbox user, use a per-command safe-directory override such as `git -c safe.directory=C:/Users/Simon/Documents/GitHub/Harmonic-HN status --short` instead of changing global Git config.

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

Do not run or control a connected Android device or emulator unless the user explicitly asks for device verification. When asked, use `adb devices` to confirm it is online. If more than one device is connected, select the intended target explicitly with `adb -s <serial>` for every install, navigation, inspection, and capture command. To verify UI changes, install the debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, launch the app with `adb shell monkey -p com.simon.harmonichackernews -c android.intent.category.LAUNCHER 1`, navigate with `adb shell input tap ...`, and inspect the hierarchy with `adb shell uiautomator dump` when verifying text, visibility, state, or clickability.

If `adb` or `emulator` is not on `PATH`, use the binaries under `/Users/simon/Library/Android/sdk/platform-tools/` and `/Users/simon/Library/Android/sdk/emulator/` rather than searching the system repeatedly.

### Debug Fixtures

Before searching live Hacker News content or adding temporary debug hooks, check Settings -> Debug. Its Sample content section provides repeatable link posts, reference-link posts, polls, internal HN links, and video examples. It also provides direct access to dialogs and arbitrary HN item IDs. Prefer these fixtures when they cover the behavior being tested. Do not duplicate their hardcoded item IDs in `AGENTS.md`; `DebugFragment.java` is the source of truth.

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

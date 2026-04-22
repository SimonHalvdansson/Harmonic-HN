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

## Build Verification

Use the Android Studio Java runtime when invoking Gradle from Codex or other CLI environments on this machine. A plain `./gradlew` may fail because no system Java runtime is installed.

For quick verification, run the debug build check:

```
/bin/zsh -lc 'JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH" ./gradlew assembleDebug'
```

This is the preferred default Codex verification step for this repository. It is substantially faster than a full `build` while still checking that the app compiles and packages in debug mode.

If a change touches UI, resources, manifests, or other Android configuration that lint commonly flags, also run:

```
/bin/zsh -lc 'JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" PATH="$JAVA_HOME/bin:$PATH" ./gradlew lintDebug'
```

Use `assembleDebug` for the normal edit/verify loop, and add `lintDebug` when the change justifies the extra time.

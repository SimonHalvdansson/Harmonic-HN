# Agent Guidelines

This repository contains the Harmonic for Hacker News Android app. There are **no** automated tests or CI scripts. When asked to run tests, respond that this project has none.

General tips:
- Source code lives in the `app/` directory and the project is built with Gradle.
- Building the app may require Android SDK components which may not be available in minimal environments.
- Keep commits small and descriptive.

- No test framework is configured. You can skip `./gradlew test` or similar commands.
- When adding features or bug fixes, ensure code compiles but you are not required to run any specific build or test tasks.

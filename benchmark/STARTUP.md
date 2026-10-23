# Android startup benchmark

`HarmonicMacrobenchmark#coldStartupWithBaselineProfile` measures launcher cold starts with
the APK's baseline profile installed (`CompilationMode.Partial(BaselineProfileMode.Require)`).
The default is 15 iterations; `-e startup.iterations N` overrides it. The companion
`coldStartupWithoutCompilation` test uses `CompilationMode.None()`.

Two metrics are collected:

- **Time to initial display:** the first app frame, including process and activity startup.
- **Time to full display:** the frame reported by `ReportDrawnWhen` after the feed stops its
  initial loading state and contains a loaded story. An error or empty saved-list screen also
  completes reporting. The benchmark separately requires a ranked story row, so failures do
  not count as successful content samples. Deep-link destinations are outside this launcher metric.

The same reporting instrumentation must be present in both the before and after APKs. Changing
the reporter between versions invalidates the full-display comparison.

## Build and run

Use Android Studio's JBR. On this Windows machine it is installed under Android Studio1;
adjust `JAVA_HOME` on other hosts. Stop Android Studio device mirroring before measurement.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio1/jbr'
./gradlew.bat :app:assembleBenchmark :benchmark:assembleBenchmarkBenchmark `
    '-Pharmonic.benchmark.minify=true' `
    '-PbenchmarkRunner=androidx.test.runner.AndroidJUnitRunner'
adb devices -l
$startupDevice = '<selected physical device serial>'
android install --apks=app/build/outputs/apk/benchmark/app-benchmark.apk --device=$startupDevice
android install --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk --device=$startupDevice
adb -s $startupDevice shell am instrument -w -r `
    -e class com.simon.harmonichackernews.benchmark.HarmonicMacrobenchmark#coldStartupWithBaselineProfile `
    -e startup.iterations 15 `
    com.simon.harmonichackernews.benchmark/androidx.test.runner.AndroidJUnitRunner
adb -s $startupDevice pull /sdcard/Android/media/com.simon.harmonichackernews.benchmark/com.simon.harmonichackernews.benchmark-benchmarkData.json "$env:TEMP/startup-benchmark.json"
```

Save the JSON, instrumentation output, APK, R8 mapping and representative Perfetto traces
outside the repository before installing another version. Instrumentation prints the trace paths. Check for
`OK (1 test)` and both metric arrays: ADB's process exit code alone does not establish success.

The existing benchmark build is unminified by default, which is useful for other performance
tests. `harmonic.benchmark.minify=true` enables R8 and resource shrinking for startup tests;
Startup Profile DEX layout optimization requires R8. Both versions must use the same setting.
The two annotation warnings suppressed in `app/benchmark-rules.pro` are compile-time-only
Tink annotations missing from the benchmark's classpath.

The benchmark app uses `com.simon.harmonichackernews.benchmark.app`, separate from the
`com.simon.harmonichackernews.benchmark` test runner. Do not uninstall
or clear the normal Harmonic packages. Keep connectivity, animations, display settings and
battery conditions consistent. Do not suppress device-mirroring or thermal errors. Cold starts
kill the app process; they do not mean fresh installation or cleared HTTP caches.

## Profiles

`BaselineProfileGenerator` has four independent, deterministic journeys:

- `startup`: launch the fixed Stories host and wait for its first story, with
  `includeInStartupProfile = true`.
- `stories`: scroll the fixed feed down and back up.
- `comments`: seed and reopen the large cached thread, collapse/expand its first subtree three
  times (asserting the reply disappears/reappears), scroll, and return.
- `reader`: open a local article from the feed, enable reader mode, verify site navigation is
  removed, scroll down/up, disable reader mode, verify the original document returns, and go back.

The fixed host subclasses `MainActivity` and uses the production Stories store, Compose screen,
navigation, and local preview image fixture. These startup measurements describe an already-seeded
feed, not live feed download latency. Startup rules still cover the production activity superclass;
the benchmark-only subclass is absent from release APKs. The other journeys are not startup rules.

`ReadingProfileEnvironment` temporarily disables Wi-Fi and mobile data so background HN refreshes
cannot replace the cached thread. It restores their previous states in teardown (including test
failures). Its HTTP server binds only to `127.0.0.1:18765` in the instrumentation process, serves a
fixed article without external resources, and survives target-app process restarts. This exercises
the production WebView and reader extraction without relying on a public website. If instrumentation
is forcibly killed, restore the recorded radio settings manually. Use a dedicated benchmark install
with default reading/comment settings; the normal Harmonic package and its content are not touched.

`installBenchmarkBaselineProfile` merges and deduplicates every journey's rules into
`app/src/main/baseline-prof.txt` and `app/src/main/baselineProfiles/startup-prof.txt`,
where Android build tools consume them. Run profile generation with the
unminified benchmark build, then rebuild the R8 measurement APK.

For a full refresh, run `:app:installBenchmarkBaselineProfile` with the desired connected device
selected through `ANDROID_SERIAL`. Use `-PbenchmarkRunner=androidx.test.runner.AndroidJUnitRunner`.
Restrict instrumentation to the generator with
`-Pandroid.testInstrumentationRunnerArguments.class=com.simon.harmonichackernews.benchmark.BaselineProfileGenerator`.
For a focused refresh, run `BaselineProfileGenerator#startup` with the instrumentation command
above. Pull the printed `BaselineProfileGenerator_startup-startup-prof.txt` file, copy it to
`app/src/main/baselineProfiles/startup-prof.txt`, and merge its rules into the existing baseline profile without
discarding the scrolling/comments rules. Rebuild before measuring. Profile generation is an
on-device operation; do not run it implicitly as part of ordinary build verification.

## Comparing profile revisions

`ReadingProfileBenchmark` measures deterministic cold startup, story scrolling, cached comments
opening (`CommentsOpen.contentReady` plus frame timing), comment scrolling, collapse/expansion, and reader
toggle/scrolling. It requires the APK's profile (`CompilationMode.Partial(Require)`) and defaults
to 10 iterations per test; `-e reading.iterations N` overrides that count.

Build the same source with the old profiles and with the regenerated profiles using
`-Pharmonic.benchmark.minify=true`, preserving each APK, mapping, JSON and traces outside the repo.
Run each version on the same physical device with the same settings, for example:

```powershell
adb -s $startupDevice shell am instrument -w -r `
    -e class com.simon.harmonichackernews.benchmark.ReadingProfileBenchmark `
    -e reading.iterations 10 `
    com.simon.harmonichackernews.benchmark/androidx.test.runner.AndroidJUnitRunner
```

Require `OK (6 tests)` and inspect each metric array before comparing. A profile-generation pass
does not establish a speedup. Emulators can generate profiles and validate journeys, but their
timings must be reported separately from physical-device results. Stop device mirroring and keep
the phone unlocked without changing its lock policy. The harness rejects a locked screen and sends
a harmless Shift key press in iteration setup (outside timed measurements) to reset idle timers
that can otherwise ignore UiAutomator gestures. For direct emulator instrumentation, explicitly pass
`-e androidx.benchmark.suppressErrors EMULATOR`; do not suppress mirroring or thermal errors.

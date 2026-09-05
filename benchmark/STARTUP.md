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
adb -s $startupDevice pull /sdcard/Android/media/com.simon.harmonichackernews.benchmark/com.simon.harmonichackernews.benchmark-benchmarkData.json
```

Save the JSON, instrumentation output, APK, R8 mapping and representative Perfetto traces
before installing another version. Instrumentation prints the trace paths. Check for
`OK (1 test)` and both metric arrays: ADB's process exit code alone does not establish success.

The existing benchmark build is unminified by default, which is useful for other performance
tests. `harmonic.benchmark.minify=true` enables R8 and resource shrinking for startup tests;
Startup Profile DEX layout optimization requires R8. Both versions must use the same setting.
The two annotation warnings suppressed in `app/benchmark-rules.pro` are compile-time-only
Tink annotations missing from the benchmark's classpath.

The test uses its own `com.simon.harmonichackernews.compose.benchmark` package. Do not uninstall
or clear the normal Harmonic packages. Keep connectivity, animations, display settings and
battery conditions consistent. Do not suppress device-mirroring or thermal errors. Cold starts
kill the app process; they do not mean fresh installation or cleared HTTP caches.

## Profiles

`BaselineProfileGenerator#startup` captures the launcher journey with
`includeInStartupProfile = true`. `generate` retains the broader scrolling/comments journey.
`installBenchmarkBaselineProfile` copies the aggregated baseline and startup profiles into
`app/src/main/baseline-prof.txt` and `app/src/main/baselineProfiles/startup-prof.txt`,
where Android build tools consume them. Run profile generation with the
unminified benchmark build, then rebuild the R8 measurement APK.

For a focused refresh, run `BaselineProfileGenerator#startup` with the instrumentation command
above. Pull the printed `BaselineProfileGenerator_startup-startup-prof.txt` file, copy it to
`app/src/main/baselineProfiles/startup-prof.txt`, and merge its rules into the existing baseline profile without
discarding the scrolling/comments rules. Rebuild before measuring. Profile generation is an
on-device operation; do not run it implicitly as part of ordinary build verification.

## 2026-09-05 experiment

Original code: `a72884c4e9f679fc1eb407d93be7db0e62d559db`, plus the same full-display reporter
and benchmark changes used for the optimized APK. The original R8 APK was built from a separate
archive of that commit, with matching benchmark R8 configuration and annotation rules.

Device: physical Pixel 11 Pro XL, Android 17 / API 37. USB connected, device mirroring stopped,
all three animation scales 1.0. No normal app data or persistent device preferences were changed.
The test APK uses the **FOSS** source set: it measures shared networking and startup changes,
but does not measure Gemini Nano IPC or Play split-install startup savings.

The feed uses live Hacker News data with the production HTTP cache. Full-display time therefore
includes network/cache variation; first-frame time is the cleaner measure of local startup work.
These are repeated launches with existing benchmark app preferences, not first-ever onboarding.

### Results

Each row compares medians from 15 cold starts per version. No successful samples were removed.

| Configuration / metric | Before | After | Change |
| --- | ---: | ---: | ---: |
| R8, first frame | 198.41 ms | 187.30 ms | 11.11 ms faster (5.6%) |
| R8, populated feed | 1,468.32 ms | 1,455.02 ms | 13.30 ms faster (0.9%) |
| Unminified, first frame | 239.94 ms | 226.54 ms | 13.40 ms faster (5.6%) |
| Unminified, populated feed | 1,519.54 ms | 1,599.12 ms | 79.59 ms slower (5.2%) |

The R8 comparison includes the code changes, updated baseline rules, and the generated Startup
Profile. The preliminary unminified comparison used the same original baseline profile on both
versions and measures the code changes without Startup Profile DEX optimization. Do not compare
an unminified before row against an R8 after row. This experiment does not isolate the Startup
Profile's incremental effect from the code changes under R8.

**Conclusion:** a modest, consistent improvement in first-frame startup (about 6% on this phone).
There is no convincing improvement in time to populated stories. Live network/cache variation
dominates that metric: R8 before samples ranged from 1,214 to 4,219 ms, and after samples from
1,321 to 2,016 ms. First-frame ranges were 187–207 ms before and 154–194 ms after. The unusually
fast after sample is retained; the reported result is the median, not the fastest launch.

Raw Macrobenchmark outputs:

- [R8 before](results/startup-2026-09-05/before-r8.json)
- [R8 after](results/startup-2026-09-05/after-r8.json)
- [Unminified before](results/startup-2026-09-05/before-unminified.json)
- [Unminified after](results/startup-2026-09-05/after-unminified.json)

The generated Startup Profile contains 4,828 rules. Its SHA-256 is
`8db6b537f1b4d399b0fe5a42ec8bae4e425f238b3adc19dd4bfda1653b6d911b`;
the merged AGP input was verified to have the same hash before the final R8 build was measured.
The existing broader baseline profile was preserved and augmented with the newly collected rules.

Changes measured: lazy, dispatcher-backed HTTP client/cache initialization; AI warm-up after
launch readiness; skipping unavailable local-AI monitoring in FOSS; and a separate startup
profile journey. Model-transfer monitoring no longer waits for cloud catalog or Gemini Nano
availability requests. Native/test callers can still supply or explicitly access an HTTP client.

Validation passed: common Kotlin metadata compilation, 492 core tests (including four new
transport initialization/disposal tests), 124 UI tests, Android debug and Play-AI debug builds,
Android lint, desktop host compilation, both R8 benchmark builds, and all four 15-iteration
measurement runs. iOS compilation was not run on Windows. The temporary plugged-in keep-awake
setting was restored from 3 to its original value, 0; animation settings remained unchanged.

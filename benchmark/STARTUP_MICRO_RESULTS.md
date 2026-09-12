# Startup micro-optimizations — 2026-09-12

All three candidates improved the isolated operations in three repeated runs and are retained.
These are **warm microbenchmark timings on an emulator**, not measured cold-start reductions
or physical-phone results. Do not add the independent theme deltas: the two changes overlap.

## Results

Unit: microseconds per operation. Each before/after number is the median of the three run
medians; each run contains 50 AndroidX Benchmark timing samples, with warmup and batched
iterations managed by the library. Saved time is aggregate before minus aggregate after.

| Change | Before (µs) | After (µs) | Saved (µs) |
| --- | ---: | ---: | ---: |
| Reuse one theme selection | 13.4163 | 4.2004 | **9.2158** |
| Skip disabled nighttime schedule/clock | 4.2514 | 2.3429 | **1.9085** |
| Avoid empty launcher Bundle | 0.0433 | 0.0057 | **0.0376** |

Each run independently improved:

| Change | Run 1 saved (µs) | Run 2 saved (µs) | Run 3 saved (µs) | Allocations before → after |
| --- | ---: | ---: | ---: | ---: |
| Reuse one theme selection | 9.2303 | 9.2783 | 7.0147 | 153 → 39 |
| Skip disabled nighttime schedule/clock | 1.8903 | 1.9339 | 1.9085 | 39 → 29 |
| Avoid empty launcher Bundle | 0.0376 | 0.0370 | 0.0382 | 2 → 0 |

## What was measured

- **Theme reuse:** the theme-resolution portion of `ThemeUtils.setupTheme()` for Android 10+:
  three selections and two darkness checks versus one selection and its existing `dark` value.
  Both use the frozen pre-change selector to isolate reuse from the nighttime optimization.
  Unchanged theme-resource lookup and window setters are excluded. Below Android 10, the old
  code performed one additional selection; that case is not measured here.
- **Nighttime disabled:** one frozen pre-change `AppearanceRuntime.selection()` versus the
  production optimized method. Empty/default preferences mean special nighttime mode is off.
  This isolates skipping four schedule reads/parses, schedule construction, and the Android
  `Calendar.getInstance()` clock supplier. Nighttime mode remains live when enabled.
- **Launcher Bundle:** the extracted no-extras branch of
  `MainLaunchIntentRouter.directStoryDestination()`, before and after. A real Android `Intent`
  with `ACTION_MAIN`/`CATEGORY_LAUNCHER` is reused. Real `Bundle`, Intent extras access, and
  `AppDestinationCodec.decode()` are exercised; populated legacy extras and navigation dispatch
  are outside this measurement. The benchmark's populated-ID branch deliberately fails.

The baseline selector is frozen from commit `6d172d39`. Theme resolution uses Android
SharedPreferences through the benchmark adapter, Android resource configuration, and Calendar.
Volatile result fields keep the measured results observable. Setup and fixture writes happen
outside the timed region. Only the benchmark APK's dedicated fixture preferences are reset.
The benchmark APK is non-debuggable, unminified, and fully AOT compiled (`speed`). This isolates
work removal under ART; it does not measure release R8 effects, first-use class loading, disk
startup, rendering, or end-to-end first-frame time. Emulator host scheduling remains a limitation.

## Environment and evidence

- Connected Pixel 9a AVD, `emulator-5554`, arm64, Android 17 / API 37, four virtual CPU cores.
- AndroidX Benchmark 1.5.0-rc02; only `EMULATOR` is suppressed and reported as a warning.
  The initial eligibility check required full AOT compilation; it was fixed before these runs.
- Three complete successful instrumentation runs, six benchmarks each. No timing samples removed.
- APK SHA-256: `033c7d5ea590af97aa3468677cb6e4c8348df02c0cf0a0aa2623977496dbb35d`.
- Raw JSON: [run 1](results/startup-micro-2026-09-12/run1.json),
  [run 2](results/startup-micro-2026-09-12/run2.json),
  [run 3](results/startup-micro-2026-09-12/run3.json).
- Instrumentation output: [run 1](results/startup-micro-2026-09-12/run1.txt),
  [run 2](results/startup-micro-2026-09-12/run2.txt),
  [run 3](results/startup-micro-2026-09-12/run3.txt).

## Rerun

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
  ./gradlew :benchmark:assembleBenchmarkBenchmark
android install --device=emulator-5554 \
  --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk
adb -s emulator-5554 shell cmd package compile -m speed -f \
  com.simon.harmonichackernews.benchmark
adb -s emulator-5554 shell am instrument -w -r \
  -e androidx.benchmark.suppressErrors EMULATOR \
  -e class com.simon.harmonichackernews.benchmark.StartupMicroOptimizationBenchmark \
  com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
adb -s emulator-5554 pull \
  /sdcard/Android/media/com.simon.harmonichackernews.benchmark/com.simon.harmonichackernews.benchmark-benchmarkData.json
```

Use the actual serial for another target and omit the emulator suppression on a physical phone.
Pull the JSON after each run, before the next run overwrites it. Use JSON `timeNs.median`, not
the rounded console summary, to reproduce the table.

## Verification

`:core:compileCommonMainKotlinMetadata`, `:core:desktopTest` (566 tests, zero failures),
`assembleDebug`, `lintDebug`, and the benchmark build passed. Regression tests cover avoiding
schedule/clock reads when disabled, enabling nighttime mode, changing its schedule, crossing
its end boundary, and disabling it again. No platform adapters changed.

The existing favicon benchmark fixture also needed its obsolete `cardStyle = true` constructor
argument updated to `displayStyle = DisplayStyle.RAISED` for the benchmark module to compile.
Normal Harmonic installations and their preferences were not changed. No emulator settings were
changed; the previously foreground Harmonic Compose app was returned to the foreground afterward.

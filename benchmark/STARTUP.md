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

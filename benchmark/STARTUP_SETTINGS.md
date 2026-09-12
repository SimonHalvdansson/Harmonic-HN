# Startup settings microbenchmark

Measured on 2026-09-12 against baseline commit
`d2eedccbfa4bf2480a8e972c866d30fc9df27a47`.

## Startup path and change

`MainActivity.onCreate` installs `MainNavigationHost`, which constructs `StoriesCoordinator`.
The coordinator reads `AppSettingsRepository.snapshot()` during construction and subscribes to
`settings.updates`, which emits another initial snapshot. Every snapshot reads
`StoredUserSettings.reading`, including `NitterInstance.effectiveUrl`, even when Nitter redirects
are disabled. The stored startup profile also includes `AppSettingsRepository.snapshot`.

Previously, the default `https://nitter.net` was validated, parsed into a Ktor `Url`, and rebuilt
on each read. `effectiveUrl` now returns the canonical constant for an exact string match. All
other inputs still use the original normalization and fallback. No preference caching or
invalidation is introduced.

## Results

Times and allocations are the median of four process medians. Each operation is one helper
call, reading-settings snapshot, or complete app-settings snapshot as labeled.

| Operation | Before ns | After ns | Time reduction | Before bytes | After bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Default URL | 304.4 | 3.9 | 98.7% | 1,560 | 0 |
| Reading settings, default URL | 851.1 | 560.1 | 34.2% | 3,352 | 1,824 |
| Complete settings, default URL | 1,274.5 | 957.4 | 24.9% | 4,456 | 2,896 |
| Custom URL | 372.7 | 380.1 | -2.0% | 1,640 | 1,640 |
| Reading settings, custom URL | 943.0 | 959.4 | -1.7% | 3,448 | 3,448 |
| Complete settings, custom URL | 1,428.3 | 1,429.8 | -0.1% | 4,552 | 4,552 |

The complete default snapshot saves **317 ns (0.317 microseconds) and 1,560 allocated bytes**.
Its process medians were 1,273–1,276 ns before and 950–962 ns after. The two explicit initial
snapshots in `StoriesCoordinator` therefore represent about 0.63 microseconds of avoided work
at this measured rate; that is an extrapolation, not an Android launch measurement.

Custom URL controls have overlapping process ranges and unchanged allocations. Their point
estimates are slightly slower, consistent with the added comparison plus measurement variation;
there is no demonstrated custom-URL speedup. The full custom snapshot ranges are 1,411–1,439 ns
before and 1,409–1,439 ns after.

See [raw timing and allocation samples](results/startup-settings-2026-09-12/results.json).

## Method and limits

The standalone Java harness invokes actual compiled Kotlin production code from separate
baseline and candidate jars. Both jars were built with the same dependencies and compiler;
the sole production change is the exact-default fast path. The runner records their SHA-256
hashes and the harness hash.

Each implementation runs in four fresh JVM processes, alternating AB/BA order. Case order
rotates between pairs. Each case warms up for one second, calibrates batches to approximately
200 ms, and records seven samples. Results escape through a volatile object sink and a primitive
checksum. ThreadMXBean measures allocated bytes, including temporary allocations, rather than
object counts or retained memory. No builds ran during measurement.

The URL fixtures use 64 independently allocated copies of the default and 64 different custom
origins. Settings cases use production `StoredUserSettings` and `AppSettingsRepository` with
`InMemoryKeyValueStore`: one default fixture and one with a custom Nitter origin. The theme
callback returns a fixed theme; Android preferences locks, disk I/O, and theme resolution are
outside these measurements. Fixture setup and equivalence checks are outside timing. Checks
cover the default, whitespace/case/port normalization, custom origins, and malformed URLs.

Host: macOS 26.6.2 arm64, Android Studio OpenJDK 21.0.10, fixed 256 MiB heap, Serial GC.
This is a warmed standalone JVM microbenchmark, not JMH, Android ART, or a cold-start benchmark.
Nanosecond figures include loop/access/sink overhead. These measurements establish reduced work
in a startup dependency; they do not establish an Android startup-time reduction. ART, R8, and
Kotlin/Native can produce different results.

## Reproduction

Build the baseline and candidate in separate checkouts. In each checkout, export the runtime
classpath using the existing init script and Android Studio's JBR:

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew \
  -I benchmark/jvm/json-access.init.gradle \
  :core:jsonAccessBenchmarkClasspath --no-configuration-cache
```

Keep both compiled jars intact, then run from the candidate checkout:

```sh
python3 benchmark/jvm/run-json-access.py \
  --harness benchmark/jvm/StartupSettingsBenchmark.java \
  --baseline-classpath /path/to/baseline/core/build/json-access-benchmark.classpath \
  --candidate-classpath /path/to/candidate/core/build/json-access-benchmark.classpath \
  --baseline-ref d2eedccbfa4bf2480a8e972c866d30fc9df27a47 \
  --output /tmp/startup-settings-results.json
```

The existing JVM runner now accepts an optional harness; its default JSON-access benchmark
remains the same. No dependencies or timing tests were added to ordinary app builds.

Verification: common Kotlin metadata compilation, all 606 core desktop tests, and benchmark
equivalence checks passed. Android `assembleDebug` and `lintDebug` were intentionally skipped
under the repository's rule for small local control-flow changes. No device was used.

# Comment parsing benchmarks

## Prepared comment cache

Physical Pixel measurements and tradeoffs: [PREPARED_CACHE_RESULTS.md](PREPARED_CACHE_RESULTS.md).

`PreparedCommentsBenchmark` compares the retained raw-JSON parser with the prepared cache on
the same device and in the same APK. `rawReadMedium/Large`, `jsonReadMedium/Large`, and
`protobufReadMedium/Large` include production filesystem reads, decoding, filtering, sorting,
expanded-link HTML and initial immutable snapshots. Prepared reads also include checksum and
schema/structure validation. Network and screen rendering are excluded. Files are read on every
operation, with a warm OS filesystem cache; this is not a cold-storage benchmark.

The corresponding `*PrepareAndWriteMedium/Large` tests start with an in-memory API response and
include parsing/preparation, snapshots, raw JSON and summary writes, plus the additional prepared
encoding/write where applicable. `reportSizes` reports actual bytes for each encoding; add raw
and prepared sizes to obtain the retained payload footprint (compact summaries are additional).
Prepared JSON is a comparison format; production writes ProtoBuf.

Use the default microbenchmark runner and full AOT compilation after installing the test APK:

```powershell
./gradlew.bat :benchmark:assembleBenchmarkBenchmark :app:assembleBenchmark
android install --apks=app/build/outputs/apk/benchmark/app-benchmark.apk --device=PIXEL_SERIAL
android install --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk --device=PIXEL_SERIAL
adb -s PIXEL_SERIAL shell cmd package compile -m speed -f com.simon.harmonichackernews.benchmark
adb -s PIXEL_SERIAL shell am instrument -w -r -e class com.simon.harmonichackernews.benchmark.PreparedCommentsBenchmark com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
```

Repeat the suite, saving `com.simon.harmonichackernews.benchmark-benchmarkData.json` after each
run. Compare pooled timing/allocation medians and inspect individual runs for drift. AndroidX's
separate method-tracing phase is not a timed sample. Never suppress `DEBUGGABLE` or
`NOT-AOT-COMPILED`. On physical devices, record and temporarily enable staying awake while
powered, then restore the original setting; leave the normal app's data intact.

For CPU sampling, select only `PreparedCommentsBenchmark#profileLarge` and pass
`-e prepared.profile.mode raw` or `-e prepared.profile.mode protobuf`. It runs a separate
15-second workload after a two-second setup pause, marking iterations as `PreparedCache.raw`
or `PreparedCache.protobuf`. Record all process threads using Perfetto's `linux.perf` source;
sampling only the instrumentation thread misses coroutine workers. Do not use profiled
durations as benchmark results.

The existing medium/large screen benchmarks seed through production storage and perform an
unmeasured opening before the measured reopening. Thus they exercise a prepared-cache hit once
migration has completed, rather than its one-time raw-JSON rebuild.
Setup closes any Comments screen left from the previous iteration before seeding, so old rows
cannot satisfy the setup wait before the new cache write has completed.

## Raw JSON baseline

`CommentsParsingBenchmark` measures the existing medium (699 comments, 409,022 bytes)
and large (3,767 comments, 2,019,072 bytes) Algolia fixtures. It reuses the fixtures
packaged in the benchmark app; it does not fetch live Hacker News content.

- `parseMedium` / `parseLarge`: JSON decoding, filtering, ordering, HTML preparation,
  and the final flat comment list, including the production dispatcher hop.
- `compactMedium` / `compactLarge`: standalone cache-summary extraction and encoding.
- `parseAndCompactMedium` / `parseAndCompactLarge`: comment preparation followed by
  summary encoding using the metadata retained by that parse.

Fixture reads and parser construction happen outside measurement. Network, disk writes,
screen rendering, and the rest of comment-screen preparation are excluded. Allocation
counts are object allocations per operation, not bytes or retained heap.

`CommentsPreparationBenchmark` adds two large-thread measurements:

- `prepareLarge`: builds the initial thread snapshots using fresh parsed comments on every
  iteration. JSON parsing is excluded from timing and allocation counts.
- `parseAndPrepareLarge`: measures both stages together, including sorting, visibility,
  anchor expansion, and immutable snapshots. It excludes UI rendering and network/cache I/O.

Fresh comments are necessary: reusing comments would measure cached anchor text after the
first iteration and hide the initial snapshot cost. These tests use the public store operations
called by initial preparation; they exclude the final helper list copies and live-store commit.

`HarmonicMacrobenchmark#commentsOpenLarge` measures the actual cached-content journey. Setup
and measurement wait for a rendered `comment-row` test tag. The 550 ms pause still covers the
navigation animation, but no longer ends measurement while only the cached title is visible.
Use `-e comments.iterations 10` for a shorter run; the default remains 40. The
`CommentsOpen.contentReadyFirstMs` metric measures the app's ready marker, not the first
presented frame. Network ordering and emulator scheduling can affect this journey, so compare
CPU microbenchmarks separately and preserve the traces alongside before/after results.

Use the default `AndroidBenchmarkRunner` for microbenchmarks and build with the standard
`AndroidJUnitRunner` for macrobenchmarks. The micro runner's isolation activity can time out
during macrobenchmark teardown on the emulator, after metrics have been collected. The
`benchmarkRunner` Gradle property selects the runner without changing source files.

```powershell
./gradlew.bat :benchmark:assembleBenchmarkBenchmark "-PbenchmarkRunner=androidx.test.runner.AndroidJUnitRunner"
android install --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk --device=emulator-5554
adb -s emulator-5554 shell am instrument -w -r -e class com.simon.harmonichackernews.benchmark.HarmonicMacrobenchmark#commentsOpenLarge -e comments.iterations 10 -e androidx.benchmark.suppressErrors EMULATOR com.simon.harmonichackernews.benchmark/androidx.test.runner.AndroidJUnitRunner
```

## Running on an emulator

Use Android Studio's bundled JBR for Gradle. Inspect `adb devices -l` first and replace
`emulator-5554` with the intended serial. These commands install separate benchmark
packages and do not clear the normal app's data.

```powershell
./gradlew.bat :benchmark:assembleBenchmarkBenchmark :app:assembleBenchmark
android install --apks=app/build/outputs/apk/benchmark/app-benchmark.apk --device=emulator-5554
android install --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk --device=emulator-5554
adb -s emulator-5554 shell cmd package compile -m speed -f com.simon.harmonichackernews.benchmark
adb -s emulator-5554 shell am instrument -w -r -e class com.simon.harmonichackernews.benchmark.CommentsParsingBenchmark -e androidx.benchmark.suppressErrors EMULATOR -e androidx.benchmark.output.enable true com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
adb -s emulator-5554 pull /sdcard/Android/media/com.simon.harmonichackernews.benchmark/com.simon.harmonichackernews.benchmark-benchmarkData.json
```

Reapply `speed` compilation after each APK installation. The benchmark build config
explicitly disables debugging on variants generated by the baseline-profile plugin.
Do not suppress `DEBUGGABLE` or `NOT-AOT-COMPILED` when collecting these measurements.

Compare `metrics.timeNs.median` and `metrics.allocationCount.median` in the JSON output.
The console's timing summary is a minimum, not the median. Preserve each JSON file
before the next run overwrites it. Alternate baseline and optimized APKs, and avoid
Gradle builds or other heavy host work while the emulator is measuring.

Emulator measurements establish a local comparison, not physical-device latency or
end-to-end screen-opening times. These benchmarks use a non-debuggable, unminified
APK with full AOT compilation; release R8 and baseline-profile behavior can differ.

## Compatibility checks

The common tests cover stable ordering, filtered/deleted subtrees, nulls, permissive
numeric conversions, cache metadata, and passing retained summaries to cache writes.
`AlgoliaFixtureParityTest` checks golden outputs from commit `8586f059` for all three
fixtures, including reversed top-level order and filtered users. Before recording
the goldens, the optimized implementation was compared field-for-field with that
original parser and its cache compactor, plus 120 scalar variants.

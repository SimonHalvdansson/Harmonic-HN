# JSON accessor microbenchmarks

Measured on 2026-09-12 against baseline commit
`c52e0b1bcbe491b12fe0e7069e26bbc06ba490f5`.

The changes are confined to the shared `serialization/JsonValues.kt` adapters:

- Object/array access uses safe casts, avoiding an exception and stack trace for a type mismatch.
- Ordinary ASCII integers use Kotlin's string conversion without constructing another JSON lexer.
  Other representations and overflow retain the original conversion path. The ASCII guard is
  needed to preserve Unicode digit rejection and the rounding of plus-prefixed long strings.
- Generic scalar getters return the first successful conversion instead of parsing it again.

These accessors serve cached-story restoration, link-preview parsing, and the compatibility JSON
API. Algolia's per-comment integer serializer is separate; these results do not establish a
speedup for full comment-tree decoding.

## Results

Times are nanoseconds per operation, reported as the median of four process medians.
Allocation figures are bytes per operation, not object counts or retained heap.

| Operation | Before ns | After ns | Time reduction | Before bytes | After bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Generic object integer getter | 70.1 | 49.8 | 28.9% | 496 | 248 |
| Generic array integer getter | 67.2 | 37.4 | 44.3% | 496 | 248 |
| Ordinary `optInt` | 65.2 | 24.6 | 62.3% | 248 | 0 |
| Ordinary `optLong` | 79.8 | 32.8 | 58.9% | 248 | 0 |
| Optional object, wrong type | 403.2 | 4.2 | 99.0% | 1,600 | 0 |
| Optional array, wrong type | 559.1 | 4.2 | 99.2% | 1,600 | 0 |
| Optional object, matching type | 30.6 | 24.3 | 20.5% | 144 | 144 |
| Parse and restore one cached story | 1,218.1 | 1,111.6 | 8.7% | 5,142.7 | 3,486.7 |
| `optInt`, fallback-heavy inputs | 649.2 | 656.3 | -1.1% | 1,377 | 1,377 |
| `optLong`, fallback-heavy inputs | 645.9 | 658.9 | -2.0% | 1,377 | 1,377 |

The ASCII guard adds approximately 7–13 ns on the deliberately fallback-heavy workload. That
workload rotates exponents, fractions, nulls, plus-prefixed strings, Unicode digits, booleans,
objects, and overflow. Ordinary integer reads save 41–47 ns and 248 allocated bytes. Wrong-type
container improvements apply to non-container values; missing keys already avoided exceptions.

Cached-story process medians ranged from 1,208–1,282 ns before and 1,078–1,136 ns after. Each
operation creates a fresh `Story` and calls production `updateStoryWithCachedStorySummary`,
including JSON parsing and three child IDs. It saves about 106 ns and 1.6 KiB per story in this
fixture. Fixtures are synthetic, with 64 varying IDs/titles, and are constructed outside timing.

## Method and limits

The harness uses macOS 26.6.2 arm64 and Android Studio's OpenJDK 21.0.10, with a fixed 256 MiB
heap and Serial GC. Each case warms up for one second, calibrates a batch to approximately
200 ms, and records seven samples. Each implementation runs in four fresh processes, in
alternating AB/BA order; case order rotates between process pairs. Checksums consume primitive
results, and volatile sinks retain object results. ThreadMXBean measures allocated bytes.
No builds ran during timing.

This is an opt-in standalone JVM harness, not JMH or an Android ART benchmark. Nanosecond
figures include loop/access/sink overhead, and are local comparisons rather than portable
latency guarantees. Android R8, ART, and Kotlin/Native performance can differ.

Raw samples and jar hashes are in [results.json](results/json-access-2026-09-12/results.json)
and [fallback-results.json](results/json-access-2026-09-12/fallback-results.json). The two
fallback cases were added to the harness after the main measurement and run separately.

## Reproduction

Build the baseline revision and candidate in separate checkouts. In each checkout, run the
following with the absolute path to the candidate's init script (the baseline lacks that file):

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew \
  -I /path/to/candidate/benchmark/jvm/json-access.init.gradle \
  :core:jsonAccessBenchmarkClasspath --no-configuration-cache
```

Each checkout now has `core/build/json-access-benchmark.classpath`, containing absolute paths
to its jar and dependencies. Keep both compiled jars intact while comparing them:

```sh
python3 benchmark/jvm/run-json-access.py \
  --baseline-classpath /path/to/baseline/core/build/json-access-benchmark.classpath \
  --candidate-classpath /path/to/candidate/core/build/json-access-benchmark.classpath \
  --baseline-ref c52e0b1bcbe491b12fe0e7069e26bbc06ba490f5 \
  --output /tmp/json-access-results.json
```

This runs all ten cases. Use `--cases objectOptIntFallback,objectOptLongFallback` to isolate
the fallback comparison. The harness compiles with `javac` into a temporary directory and
adds no dependencies or timing tests to normal app builds.

Verification: common metadata compilation, all 606 desktop core tests, and Android
`assembleDebug` passed. The added common tests cover numeric coercion parity across more
than 1,000 inputs, generic scalar types, required/optional container access, and invalid roots.

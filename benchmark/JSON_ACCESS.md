# JSON accessor microbenchmarks

The Kotlin harness measures production JSON accessors and cached-story restoration.
It includes ordinary numeric reads, fallback conversions, and container type mismatches.

Each case warms up for one second and records seven samples. The runner alternates baseline
and candidate across four fresh JVM processes, using the same compiled harness. Outputs
include timings, allocated bytes, and jar hashes. Run without concurrent builds or heavy work.
These are warmed JVM comparisons, not Android startup or frame-time measurements.

## Reproduction

Build the baseline revision and candidate in separate checkouts. In each checkout, run the
following with the absolute path to the candidate's init script (the baseline lacks that file):

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" ./gradlew \
  -I /path/to/candidate/benchmark/jvm/json-access.init.gradle \
  :core:jsonAccessBenchmarkClasspath --no-configuration-cache
```

Each checkout now has `core/build/json-access-benchmark.classpath`, containing absolute paths
to its jar and dependencies, and `json-access-benchmark-compiler.classpath` for the project's
Kotlin compiler. Keep both compiled jars intact while comparing them:

```sh
python3 benchmark/jvm/run-json-access.py \
  --baseline-classpath /path/to/baseline/core/build/json-access-benchmark.classpath \
  --candidate-classpath /path/to/candidate/core/build/json-access-benchmark.classpath \
  --baseline-ref '<baseline-commit>' \
  --output /tmp/json-access-results.json
```

This runs all ten cases. Use `--cases objectOptIntFallback,objectOptLongFallback` to isolate
the fallback comparison. The harness compiles with the candidate project's Kotlin compiler
into a temporary directory and adds no dependencies or timing tests to normal app builds.

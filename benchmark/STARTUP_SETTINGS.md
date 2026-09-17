# Startup settings microbenchmark

The Kotlin harness measures production Nitter URL normalization, reading settings, and
complete app-settings snapshots with default and custom origins. Fixtures use
`InMemoryKeyValueStore`; Android preference locks, disk I/O, and theme resolution are excluded.

Each case warms up for one second and records seven samples. The runner alternates baseline
and candidate across four fresh JVM processes, using the same compiled harness. Outputs
include timings, allocated bytes, and jar hashes. Run without concurrent builds or heavy work.
These are warmed JVM comparisons, not Android startup or frame-time measurements.

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
  --harness benchmark/jvm/StartupSettingsBenchmark.kt \
  --baseline-classpath /path/to/baseline/core/build/json-access-benchmark.classpath \
  --candidate-classpath /path/to/candidate/core/build/json-access-benchmark.classpath \
  --baseline-ref '<baseline-commit>' \
  --output /tmp/startup-settings-results.json
```

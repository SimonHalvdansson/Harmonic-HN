# Harmonic palette extractor

The app uses its own Kotlin palette extractor. KMPalette is no longer a dependency;
fixed outputs captured from it remain regression fixtures. See also
[preview sampling](preview-palette.md).

## Implementation

`HarmonicPaletteExtractor` is a pure Kotlin specialization of KMPalette 4.0.0's
Apache-licensed quantizer and target selection. It accepts already-sampled ARGB
pixels, uses 16 colors and the default filter/targets, and preserves the ordered
swatches, populations, HSL values, and seven selected colors. It is not a general
replacement for custom KMPalette builders, filters, targets, regions, or resizing.

The app uses it for the existing 96 px preview, favicon, and fallback-painter
samples. Sampling and `PreviewTintPolicy` are unchanged. A local painter sampler
retains Main-thread rasterization and rounded 96 px dimensions; quantization uses
the existing four-job background gate. Settings and welcome previews also use
Harmonic, preserving their original 112² area limit, ceil-rounded dimensions,
and platform-native nearest-neighbor scaling. Resource decoding and extraction
use the same background gate. Source attribution remains in the license catalog.

Changes that reduce work:

- Reuse the 32,768-bin histogram and clear only touched bins, avoiding repeated
  128 KiB allocation and full scans through mostly empty bins.
- Calculate the default filter once for all 32,768 quantized colors, then use a
  shared lookup table.
- Use reusable primitive arrays for median-cut boxes, the heap, and sorting.
  Larger ranges use stable radix sorting of the complete channel key. Smaller
  ranges use the reference's channel permutation and primitive sort.
- Reuse the pixel readback buffer in a bounded pool of four exclusive workspaces.
  Cancellation returns slots, including cancellation of a suspended receiver.
- Calculate only the palette data the app consumes, without the general builder,
  target maps, or mutable library state objects.

The implementation deliberately retains volume ties in the heap, heap-array
iteration order, full secondary-channel sorting, exclusive-target order, Float
operation order, and ties-to-even rounding. These details affect the final tint.

The retained primitive storage is about 236 KiB per workspace, plus a shared
32 KiB filter table: about 1 MiB at the four-workspace bound, excluding small
objects. Returned palettes own their data and inputs are never modified.

## Regression coverage

Core tests compare output fingerprints captured from KMPalette 4.0.0, covering
quantized colors, seeded images, transparency, population ties, and workspace reuse.
The Android benchmark module keeps fixed sample/palette hashes in
`benchmark/src/main/assets/palette-goldens.txt`.

- `PreviewPaletteEquivalenceTest` checks painter/bitmap sample parity, golden palettes,
  production tint extraction, and fallback for non-shareable images.
- `HarmonicPaletteEquivalenceTest` checks settings-resource goldens, concurrent
  extraction, and workspace availability after cancellation.
- `HarmonicPaletteFirstUseTest` exercises initialization in a fresh instrumentation process.

These fixtures protect output compatibility. Do not regenerate them from the
implementation being tested merely to make a failing test pass.

## Measurements

`HarmonicPaletteBenchmark` measures quantization of pre-sampled pixels.
`HarmonicPalettePipelineBenchmark` measures production sampling, pixel readback,
quantization, and tint calculation. Images are already decoded and workspaces are
warm after benchmark warmup. Cold extraction means a tint-cache miss, not a fresh
process; downloads, decoding, recomposition, and frame rendering are excluded.
The checkout does not contain an executable KMPalette baseline.

## Run

Build using Android Studio's JBR:

```sh
PATH="/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:$PATH" \
  ./gradlew :benchmark:assembleBenchmarkBenchmark
```

On a device selected for benchmarking, install and fully compile the benchmark APK:

```sh
benchmark_device='<device-serial>'
android install --device="$benchmark_device" --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk
adb -s "$benchmark_device" shell cmd package compile -m speed -f com.simon.harmonichackernews.benchmark
```

Set `benchmark_classes` to one or more comma-separated class names from above,
each prefixed with `com.simon.harmonichackernews.benchmark.`. Run the first-use test
alone in a fresh instrumentation invocation.

```sh
benchmark_classes='com.simon.harmonichackernews.benchmark.HarmonicPaletteBenchmark'
adb -s "$benchmark_device" shell am instrument -w -r \
  -e class "$benchmark_classes" \
  com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
adb -s "$benchmark_device" pull \
  /sdcard/Android/media/com.simon.harmonichackernews.benchmark/com.simon.harmonichackernews.benchmark-benchmarkData.json \
  /tmp/harmonic-palette-results.json
```

Save each run outside the repository before another overwrites it. Check the test
result as well as the process exit code. Compare timing distributions and allocation
counts across repeated runs with consistent device conditions and no concurrent builds.
Emulator timings do not establish physical-device latency or scrolling smoothness.

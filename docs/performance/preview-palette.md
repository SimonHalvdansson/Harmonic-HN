# Preview palette extraction

Historical sampling-only investigation. The subsequent
[Harmonic extractor investigation](harmonic-palette.md) replaces the quantizer;
current production-path benchmarks include that newer change.

Measured on 2026-09-05 on `emulator-5554`, an Android API 37 x86_64 emulator
(`sdk_gphone16k_x86_64`, 1080 × 2424, 420 dpi). These are emulator microbenchmarks,
not physical-device or scrolling frame-rate measurements.

## Change

Story preview images and the shared `NetworkImage` now pass the decoded Coil image
to a preview-specific background sampler. Shareable bitmaps rendered by Coil's
`BitmapPainter` use this path. Other painters keep the existing KMPalette loader.
Sampling, palette generation, and tint selection share the existing four-job
background gate with favicon extraction.

The sampler draws into the same default `ImageBitmap` with the same low-quality
bitmap filtering as the old painter. It preserves the 96 px bound, upscaling of
small images, rounded dimensions, 16-color palette, default filters, target
selection, and tint policy. It avoids the painter wrapper and `prepareToDraw()`
on a bitmap that is only used for CPU pixel extraction. Cancellation propagates;
extraction errors retain the existing card color.

Two details prevent a direct reuse of the favicon sampler:

- The old preview painter rounds dimensions; the favicon helper truncates them.
  For example, a 101 × 67 image must produce a 96 × 64 preview sample.
- Coil 3.6.0's Android `Image.toBitmap(width, height)` draws the original bitmap
  without scaling to the new canvas. The preview sampler obtains the source at
  its original size and explicitly scales it using Compose's `drawImage`.

Favicon sampling and all tint-cache keys and versions remain unchanged.
No URL-only preview cache was added: different decode sizes or transformations
of the same URL are not necessarily identical sampling inputs.

## Equivalence

`PreviewPaletteEquivalenceTest` compares the production sampler against the real
KMPalette 4.0.0 `PainterLoader` with a frozen copy of the original 96 px wrapper.

- 29 source fixtures: eight bundled photos/logos/previews, 18 generated images
  spanning tiny/extreme/odd aspect ratios and transparency, RGB565, RGBA_F16,
  and Display P3.
- Two densities and both layout directions: **116 exact comparisons** of
  dimensions, every sampled ARGB pixel, ordered quantized swatches/populations,
  and all seven selected swatches.
- Three palette modes, light/dark bases, and three settings combinations:
  **2,088 tint comparisons**, checked both from the sampled palette and through
  the full production background extractor. All passed.
- Non-shareable bitmaps return no bitmap sample so callers retain painter fallback.

## Timing method

`PreviewPaletteBenchmark` is parameterized over `palette1.webp`, `palette3.webp`,
and `web_preview.webp`. Both old and new implementations are in the same APK.
The APK is non-debuggable and compiled with `cmd package compile -m speed -f`.
`AndroidBenchmarkRunner` provides an isolation activity; only the required
`EMULATOR` benchmark error is suppressed.

Each cold-extraction iteration creates a fresh sample and runs quantization and
tint selection. **Neither path reads or writes a tint cache.** Images are already
decoded, so these timings exclude downloads and image decoding. Both paths start
on `Dispatchers.Main` from identical instrumentation scaffolding. The old path
samples on Main and generates the palette on Default; the new path calls the
production extractor, which runs the entire operation on Default.

The separate sampling measurements use identical `runBlocking` scaffolding.
They measure sampling cost, not the latency of updating Compose state or drawing
a frame. Full-extraction measurements also exclude Compose recomposition.

Two final runs completed successfully (12 benchmarks per run). Values below are
the median timings reported by AndroidX Benchmark, in milliseconds.

| Image | Run | Old cold extraction | New cold extraction | Old sampling | New sampling |
|---|---:|---:|---:|---:|---:|
| palette1.webp | 1 | 3.319 | 2.821 | 0.239 | 0.220 |
| palette1.webp | 2 | 2.862 | 2.853 | 0.262 | 0.194 |
| palette3.webp | 1 | 2.808 | 2.515 | 0.254 | 0.218 |
| palette3.webp | 2 | 2.571 | 2.612 | 0.270 | 0.209 |
| web_preview.webp | 1 | 2.710 | 2.537 | 0.241 | 0.239 |
| web_preview.webp | 2 | 2.681 | 2.604 | 0.254 | 0.230 |

Sampling medians are lower in all six comparisons, although the smallest
difference is effectively noise. Full extraction was 6–15% faster in the first
run, but the repeat ranged from 1.6% slower to 2.9% faster. This does **not** establish
a substantial or reliable overall CPU/latency improvement. The repeatability
limit is material; do not quote just the first run's percentages.

The concrete reason to retain the change is that the roughly 0.24–0.27 ms
sampling stage now executes off Main, together with tint selection, and preview
bursts respect the four-job gate. The sampling figures include shared test
scaffolding, so they are an approximate indication of removed main-thread work,
not a measured frame-time saving. Palette quantization is unchanged. A scrolling
or physical-device benchmark would be required to establish an FPS/jank benefit.

As a separate control, the existing `sharedPaletteTintCacheHotRead` benchmark
returned a warm cached tint in a median **3.08 microseconds**, with its extraction
callback set to fail if invoked. This illustrates why cache hits must be excluded
from extraction comparisons; it is not a speedup introduced by this patch.

## Reproduction

Use Android Studio's JBR for Gradle. Build:

```text
./gradlew :benchmark:assembleBenchmarkBenchmark
android install --device=emulator-5554 --apks=benchmark/build/outputs/apk/benchmarkBenchmark/benchmark-benchmarkBenchmark.apk
adb -s emulator-5554 shell cmd package compile -m speed -f com.simon.harmonichackernews.benchmark
adb -s emulator-5554 shell am instrument -w -r -e class com.simon.harmonichackernews.benchmark.PreviewPaletteEquivalenceTest com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
adb -s emulator-5554 shell am instrument -w -r -e class com.simon.harmonichackernews.benchmark.PreviewPaletteBenchmark -e androidx.benchmark.suppressErrors EMULATOR -e additionalTestOutputDir /sdcard/Download/preview-palette-results com.simon.harmonichackernews.benchmark/androidx.benchmark.junit4.AndroidBenchmarkRunner
```

Use a different output directory for each repeat. No app-data or tint-cache
clearing is necessary for these cold tests: they bypass caching on every iteration.

## Build verification

Passed `:app:assembleDebug`, `:app:lintDebug`, `:ui:desktopTest` (129 tests),
and `:desktop_app:compileKotlinDesktop`. Apple compilation and device validation
were not performed on this Windows host.

The debug APK was also installed and launched on the emulator. Story-list preview
images and tinted cards rendered successfully, and the comments preview loaded
when returning to the story that had been open before testing. This was a visual
smoke check, not additional performance or pixel-equivalence evidence. No app
preferences, animation scales, connectivity settings, or user caches were changed.

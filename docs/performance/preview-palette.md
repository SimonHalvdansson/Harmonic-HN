# Preview palette sampling

Story previews and the shared `NetworkImage` pass decoded Coil images to the
background sampler when they support off-main sampling and use a `BitmapPainter`.
Other painters use the local painter sampler on Main. Palette quantization and
tint selection share the bounded background extraction gate.

The preview sampler preserves the painter's low-quality bitmap filtering,
96 px bound, rounded dimensions, and upscaling of small images. It draws the
original bitmap into a scaled canvas explicitly. Preview dimensions must not use
the favicon sampler's truncation: a 101 × 67 image produces a 96 × 64 preview sample.
Different decode sizes or transformations of the same URL can produce different
pixels, so a URL alone is insufficient as an extraction-cache key.

`PreviewPaletteEquivalenceTest` compares dimensions and every sampled pixel between
the bitmap and painter paths, verifies fixed palette goldens, and checks production
tints. Fixtures cover varied aspect ratios, transparency, color formats, densities,
and layout directions. Non-shareable images must retain the painter fallback.

`PreviewPaletteBenchmark` measures `painterSampling`, `bitmapSampling`, and
`bitmapColdExtraction` for bundled image fixtures. Images are already decoded;
extraction bypasses tint caching. These measurements exclude download, decoding,
recomposition, and frame rendering.

Follow the [palette benchmark instructions](harmonic-palette.md#run), selecting
`com.simon.harmonichackernews.benchmark.PreviewPaletteEquivalenceTest` for regression
checks or `com.simon.harmonichackernews.benchmark.PreviewPaletteBenchmark` for timing.

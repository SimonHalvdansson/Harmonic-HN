package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintSwatch

// Baked from the bundled images/favicon using the same 96px painter sampling as ImagePalette.
// Keep swatches, rather than final colors, so every theme and palette setting still applies.
// HeaderPreviewPaletteTest checks these against the assets when a source changes.
internal val HeaderPreviewSample.tintPalette: PreviewTintPalette?
    get() = when (this) {
        HeaderPreviewSample.LINK_WITH_IMAGE -> WebPreviewPalette
        HeaderPreviewSample.LINK_PREVIEW -> GitHubPreviewPalette
        HeaderPreviewSample.TEXT_POST -> HackerNewsPalette
        HeaderPreviewSample.LINK_WITHOUT_IMAGE -> null
    }

private val WebPreviewPalette = PreviewTintPalette(
    vibrant = PreviewTintSwatch(180f, 0.70000005f),
    lightVibrant = PreviewTintSwatch(90f, 0.4050633f),
    darkVibrant = PreviewTintSwatch(202.5f, 1f),
    dominant = PreviewTintSwatch(202.5f, 1f),
    muted = PreviewTintSwatch(135f, 0.25196853f),
    lightMuted = PreviewTintSwatch(135f, 0.33684212f),
    darkMuted = PreviewTintSwatch(153.33333f, 0.35999998f),
)

private val GitHubPreviewPalette = PreviewTintPalette(
    vibrant = PreviewTintSwatch(255f, 0.5517242f),
    lightVibrant = PreviewTintSwatch(261.17645f, 0.8192771f),
    dominant = PreviewTintSwatch(261.17645f, 0.8192771f),
    muted = PreviewTintSwatch(240f, 0.03225809f),
    lightMuted = PreviewTintSwatch(210f, 0.12698413f),
    darkMuted = PreviewTintSwatch(180f, 0.11111111f),
)

private val HackerNewsPalette = PreviewTintPalette(
    vibrant = PreviewTintSwatch(23.225807f, 1f),
    lightVibrant = PreviewTintSwatch(26.250004f, 0.90140843f),
    dominant = PreviewTintSwatch(23.225807f, 1f),
)

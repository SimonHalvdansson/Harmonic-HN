package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

/** True only for decoded images that can safely be sampled from a background dispatcher. */
internal expect fun Image.supportsOffMainPaletteSampling(): Boolean

/** Converts a supported Coil image into a bounded Compose bitmap for palette generation. */
internal expect fun Image.toPaletteImageBitmap(width: Int, height: Int): ImageBitmap?

package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.Image
import coil3.toBitmap

internal actual fun Image.supportsOffMainPaletteSampling(): Boolean =
    this is BitmapImage && shareable

internal actual fun Image.toPaletteImageBitmap(width: Int, height: Int): ImageBitmap? {
    if (!supportsOffMainPaletteSampling()) return null
    return toBitmap(width, height).asImageBitmap()
}

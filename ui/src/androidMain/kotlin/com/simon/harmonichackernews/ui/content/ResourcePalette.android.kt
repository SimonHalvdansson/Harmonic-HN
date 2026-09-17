package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale

internal actual fun ImageBitmap.scalePaletteResource(width: Int, height: Int): ImageBitmap =
    asAndroidBitmap().scale(width, height, filter = false).asImageBitmap()

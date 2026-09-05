package com.simon.harmonichackernews.ui.content

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun ImageBitmap.scalePaletteResource(width: Int, height: Int): ImageBitmap =
    Bitmap.createScaledBitmap(asAndroidBitmap(), width, height, false).asImageBitmap()

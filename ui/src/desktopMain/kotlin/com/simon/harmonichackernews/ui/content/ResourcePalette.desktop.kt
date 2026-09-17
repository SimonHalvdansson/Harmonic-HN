package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

internal actual fun ImageBitmap.scalePaletteResource(width: Int, height: Int): ImageBitmap {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    val surface = Surface.makeRasterN32Premul(width, height)
    try {
        surface.canvas.drawImageRect(
            image = image,
            src = Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            dst = Rect.makeWH(width.toFloat(), height.toFloat()),
            samplingMode = SamplingMode.DEFAULT,
            paint = null,
            strict = true,
        )
        return surface.makeImageSnapshot().toComposeImageBitmap()
    } finally {
        surface.close()
        image.close()
    }
}

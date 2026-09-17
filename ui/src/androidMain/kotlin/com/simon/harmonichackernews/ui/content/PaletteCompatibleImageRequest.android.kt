package com.simon.harmonichackernews.ui.content

import coil3.request.ImageRequest
import coil3.request.allowHardware

/** Compose samples the result into a software bitmap, which cannot draw Android hardware bitmaps. */
internal actual fun ImageRequest.Builder.paletteCompatible(): ImageRequest.Builder =
    allowHardware(false)

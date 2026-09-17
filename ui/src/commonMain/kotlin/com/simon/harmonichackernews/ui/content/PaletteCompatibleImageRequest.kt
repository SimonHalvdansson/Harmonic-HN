package com.simon.harmonichackernews.ui.content

import coil3.request.ImageRequest

/** Applies only the platform constraints needed to sample a Coil image in shared palette code. */
internal expect fun ImageRequest.Builder.paletteCompatible(): ImageRequest.Builder

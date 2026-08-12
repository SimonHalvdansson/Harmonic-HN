package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * Multiplatform Coil image with optional multiplatform palette extraction.
 *
 * The only platform adjustment is [paletteCompatible], which disables Android hardware bitmaps
 * when pixels must be sampled. Network fetching, rendering, result handling and tint extraction
 * are otherwise identical on Android, iOS and desktop.
 */
@Composable
fun SharedNetworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    crossfade: Boolean = false,
    tintBaseColorArgb: Int? = null,
    paletteTintConfigKey: String = "default",
    extractTint: Boolean = false,
    onSuccess: () -> Unit = {},
    onError: () -> Unit = {},
    onTintExtracted: (Int) -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val request = remember(context, url, crossfade, extractTint) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(crossfade)
            .let { builder -> if (extractTint) builder.paletteCompatible() else builder }
            .build()
    }
    var painter by remember(url) { mutableStateOf<Painter?>(null) }
    val extractedTint = rememberPainterPaletteTint(
        painter = painter,
        baseColorArgb = tintBaseColorArgb ?: 0,
        paletteTintConfigKey = paletteTintConfigKey,
        enabled = extractTint && tintBaseColorArgb != null,
    )
    LaunchedEffect(extractedTint) {
        extractedTint?.let(onTintExtracted)
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onSuccess = { result ->
            painter = result.painter
            onSuccess()
        },
        onError = { onError() },
    )
}

package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun StoryPreviewImage(
    model: StoryItemUiModel,
    modifier: Modifier,
    onLoadFailed: () -> Unit,
    onLoadSuccess: () -> Unit,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
) {
    if (model.previewImageUrl != null) {
        var loaded by remember(model.previewImageUrl) { mutableStateOf(false) }
        var loadedImage by remember(model.previewImageUrl) { mutableStateOf<coil3.Image?>(null) }
        var loadedPainter by remember(model.previewImageUrl) { mutableStateOf<Painter?>(null) }
        val extractedTint = rememberPreviewImagePaletteTint(
            image = loadedImage,
            fallbackPainter = loadedPainter,
            baseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            enabled = extractTint,
        )
        LaunchedEffect(extractedTint) {
            extractedTint?.let(onTintExtracted)
        }
        val loadProgress by animateFloatAsState(
            targetValue = if (loaded) 1f else 0f,
            animationSpec = tween(240, easing = ContentMotionEasing),
            label = "story image load",
        )
        val request = rememberPaletteCompatibleImageRequest(model.previewImageUrl)
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = modifier.graphicsLayer {
                alpha = loadProgress
                scaleX = 0.94f + 0.06f * loadProgress
                scaleY = 0.94f + 0.06f * loadProgress
            },
            contentScale = ContentScale.Crop,
            onSuccess = { success ->
                loaded = true
                loadedImage = success.result.image
                loadedPainter = success.painter
                onLoadSuccess()
            },
            onError = { onLoadFailed() },
        )
    } else {
        val painter = model.previewImageBitmap?.let { bitmap ->
            remember(bitmap) { BitmapPainter(bitmap) }
        } ?: model.previewImageFallback?.let { painterResource(it) }
        if (painter != null) {
            val extractedTint = rememberPainterPaletteTint(
                painter = painter,
                baseColorArgb = tintBaseColorArgb,
                paletteTintConfigKey = paletteTintConfigKey,
                enabled = extractTint,
            )
            LaunchedEffect(extractedTint) {
                extractedTint?.let(onTintExtracted)
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun rememberPaletteCompatibleImageRequest(url: String): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .paletteCompatible()
            .build()
    }
}

@Composable
internal fun StoryFavicon(
    model: StoryItemUiModel,
    dimAlpha: Float,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
) {
    if (model.faviconUrl != null) {
        var loaded by remember(model.faviconUrl) { mutableStateOf(false) }
        var failed by remember(model.faviconUrl) { mutableStateOf(false) }
        var loadedImage by remember(model.faviconUrl) { mutableStateOf<coil3.Image?>(null) }
        var loadedPainter by remember(model.faviconUrl) { mutableStateOf<Painter?>(null) }
        val extractedTint = rememberCoilImagePaletteTint(
            image = loadedImage,
            fallbackPainter = loadedPainter,
            baseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            enabled = extractTint,
            sharedCacheKey = model.faviconUrl,
        )
        LaunchedEffect(extractedTint) {
            extractedTint?.let(onTintExtracted)
        }
        val loadAlpha by animateFloatAsState(
            targetValue = if (loaded) 1f else 0f,
            animationSpec = contentTween(),
            label = "story favicon load",
        )
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(17.dp)
                .clip(RoundedCornerShape(3.dp)),
        ) {
            Icon(
                painter = painterResource(model.faviconFallback),
                contentDescription = null,
                tint = HarmonicTheme.colors.drawable,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        alpha = dimAlpha * if (failed) 1f else 1f - loadAlpha,
                    ),
            )
            if (!failed) {
                val request = rememberPaletteCompatibleImageRequest(model.faviconUrl)
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = dimAlpha * loadAlpha),
                    onSuccess = { success ->
                        loaded = true
                        loadedImage = success.result.image
                        loadedPainter = success.painter
                    },
                    onError = { failed = true },
                )
            }
        }
    } else {
        val fallbackPainter = painterResource(model.faviconFallback)
        val extractedTint = rememberPainterPaletteTint(
            painter = fallbackPainter,
            baseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            enabled = extractTint && !model.tintFaviconFallback,
        )
        LaunchedEffect(extractedTint) {
            extractedTint?.let(onTintExtracted)
        }
        if (model.tintFaviconFallback) {
            Icon(
                painter = fallbackPainter,
                contentDescription = null,
                tint = HarmonicTheme.colors.drawable,
                modifier = Modifier.padding(end = 4.dp).size(17.dp),
            )
        } else {
            Image(
                painter = fallbackPainter,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(17.dp),
            )
        }
    }
}

@Composable
internal fun StoryLargePreviewImage(
    model: StoryItemUiModel,
    style: StoryItemStyle,
    presentation: StoryItemPresentation,
    animate: Boolean,
    captureSourceContent: Boolean,
    itemGeometry: StoryItemGeometry,
    onLinkClick: (() -> Unit)?,
    onLinkLongClick: (() -> Unit)?,
) {
    val imageInset = if (animate) {
        val animatedInset by animateDpAsState(
            targetValue = if (style.borderlessLargeImage) 0.dp else 10.dp,
            animationSpec = contentTween(),
            label = "large story image inset",
        )
        animatedInset
    } else if (style.borderlessLargeImage) {
        0.dp
    } else {
        10.dp
    }
    val imageRadius = if (animate) {
        val animatedRadius by animateDpAsState(
            targetValue = if (style.borderlessLargeImage) 0.dp else 8.dp,
            animationSpec = contentTween(),
            label = "large story image radius",
        )
        animatedRadius
    } else if (style.borderlessLargeImage) {
        0.dp
    } else {
        8.dp
    }
    StoryPreviewImage(
        model = model,
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .combinedClickable(
                enabled = onLinkClick != null || onLinkLongClick != null,
                onClick = { onLinkClick?.invoke() },
                onLongClick = onLinkLongClick,
            )
            .onSecondaryClick(enabled = onLinkLongClick != null) {
                onLinkLongClick?.invoke()
            }
            .padding(start = imageInset, top = imageInset, end = imageInset)
            .clip(RoundedCornerShape(imageRadius))
            .captureStoryPreviewElement(
                enabled = captureSourceContent,
                onPositioned = { itemGeometry.largeImageCoordinates = it },
                onLayerChanged = { itemGeometry.largeImageLayer = it },
            )
            .graphicsLayer(alpha = presentation.dimAlpha),
        onLoadFailed = presentation.onPreviewLoadFailed,
        onLoadSuccess = presentation.onPreviewLoadSuccess,
        tintBaseColorArgb = presentation.tintBaseColorArgb,
        paletteTintConfigKey = style.paletteTintConfigKey,
        extractTint = style.tintCard && model.previewImageTintArgb == null,
        onTintExtracted = presentation.onPreviewTintExtracted,
    )
}

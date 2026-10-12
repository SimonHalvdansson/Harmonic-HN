package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.simon.harmonichackernews.ui.common.captureSharedTransformSourceContent
import com.simon.harmonichackernews.ui.common.onSecondaryClick
import com.simon.harmonichackernews.ui.content.NetworkImage

/** Shared by real thumbnails and the settings sample, including the compact layout. */
internal fun Modifier.headerPreviewImageGestures(onClick: () -> Unit, onInspect: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onInspect, onLongClickLabel = "Inspect image")
        .onSecondaryClick { onInspect() }

/** The sample and loaded header images reveal vertically without stretching or sliding sideways. */
@Composable
internal fun HeaderPreviewImageVisibility(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240, delayMillis = 80)) + expandVertically(
            animationSpec = tween(CommentsHeaderRevealDurationMillis, easing = FastOutSlowInEasing),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(120)) + shrinkVertically(
            animationSpec = tween(CommentsHeaderRevealDurationMillis, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top,
        ),
        content = content,
    )
}

/** Shared Coil loading, palette extraction and interaction for the comments header image. */
@Composable
fun HeaderPreviewImage(
    modifier: Modifier,
    imageUrl: String?,
    initiallyFailed: Boolean,
    visible: Boolean,
    suppressed: Boolean,
    tintBaseColorArgb: Int,
    paletteTintConfigKey: String,
    extractTint: Boolean,
    onTintExtracted: (Int) -> Unit,
    onImageResult: (success: Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: (bounds: Rect, sourceContentLayer: GraphicsLayer?, imageAspectRatio: Float?) -> Unit,
) {
    var failed by remember(imageUrl, initiallyFailed) { mutableStateOf(initiallyFailed) }
    var bounds by remember(imageUrl) { mutableStateOf(Rect.Zero) }
    var sourceContentLayer by remember(imageUrl) { mutableStateOf<GraphicsLayer?>(null) }
    var imageAspectRatio by remember(imageUrl) { mutableStateOf<Float?>(null) }
    HeaderPreviewImageVisibility(
        visible = visible && !imageUrl.isNullOrBlank() && !failed,
    ) {
        NetworkImage(
            url = imageUrl.orEmpty(),
            contentDescription = "Story preview image",
            modifier = modifier
                .graphicsLayer(alpha = if (suppressed) 0f else 1f)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .headerPreviewImageGestures(
                    onClick = onClick,
                    onInspect = { onLongClick(bounds, sourceContentLayer, imageAspectRatio) },
                )
                .captureSharedTransformSourceContent { sourceContentLayer = it },
            contentScale = ContentScale.Crop,
            tintBaseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            extractTint = visible && extractTint,
            onTintExtracted = onTintExtracted,
            onSuccess = {
                failed = false
                onImageResult(true)
            },
            onImageAspectRatio = { imageAspectRatio = it },
            onError = {
                failed = true
                onImageResult(false)
            },
        )
    }
}

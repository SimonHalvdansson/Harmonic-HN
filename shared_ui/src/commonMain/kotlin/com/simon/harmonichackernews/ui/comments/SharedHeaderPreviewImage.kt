package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.content.SharedNetworkImage

/** Shared Coil loading, palette extraction and interaction for the comments header image. */
@Composable
fun SharedHeaderPreviewImage(
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
    onLongClick: (Rect) -> Unit,
) {
    var failed by remember(imageUrl, initiallyFailed) { mutableStateOf(initiallyFailed) }
    var bounds by remember(imageUrl) { mutableStateOf(Rect.Zero) }
    AnimatedVisibility(
        visible = visible && !imageUrl.isNullOrBlank() && !failed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        SharedNetworkImage(
            url = imageUrl.orEmpty(),
            contentDescription = "Story preview image",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                .height(176.dp)
                .clip(RoundedCornerShape(8.dp))
                .graphicsLayer(alpha = if (suppressed) 0f else 1f)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onLongClick(bounds) },
                ),
            contentScale = ContentScale.Crop,
            tintBaseColorArgb = tintBaseColorArgb,
            paletteTintConfigKey = paletteTintConfigKey,
            extractTint = visible && extractTint,
            onTintExtracted = onTintExtracted,
            onSuccess = {
                failed = false
                onImageResult(true)
            },
            onError = {
                failed = true
                onImageResult(false)
            },
        )
    }
}

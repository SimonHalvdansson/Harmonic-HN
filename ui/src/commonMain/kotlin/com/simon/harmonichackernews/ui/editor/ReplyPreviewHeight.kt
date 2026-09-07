package com.simon.harmonichackernews.ui.editor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Return zero to offer the original comment in a dialog instead of an unreadable sliver. */
internal fun replyPreviewHeight(
    availableHeight: Dp,
    preferredPreviewHeight: Dp,
    reservedReplyHeight: Dp,
    minimumPreviewHeight: Dp = 112.dp,
): Dp {
    val height = preferredPreviewHeight.coerceAtMost(availableHeight - reservedReplyHeight)
    return if (height >= minimumPreviewHeight) height else 0.dp
}

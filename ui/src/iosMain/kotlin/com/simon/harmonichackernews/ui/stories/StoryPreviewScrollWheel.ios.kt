package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.Modifier

internal actual fun Modifier.storyPreviewScrollWheelPaging(
    onScroll: (deltaY: Float) -> Unit,
): Modifier = this

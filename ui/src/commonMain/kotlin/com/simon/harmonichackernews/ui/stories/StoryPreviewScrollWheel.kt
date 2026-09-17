package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.Modifier

/** Platform hook for desktop wheel events; touch-first hosts leave the modifier unchanged. */
internal expect fun Modifier.storyPreviewScrollWheelPaging(
    onScroll: (deltaY: Float) -> Unit,
): Modifier

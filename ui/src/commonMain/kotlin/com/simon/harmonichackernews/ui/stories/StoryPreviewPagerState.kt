package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState

@Composable
internal fun rememberStoryPreviewPagerState(
    overlay: StoryPreviewOverlayState,
    visibleStoryId: Int,
): PagerState {
    val pageCount by rememberUpdatedState(overlay.stories.size)
    // The host restores the preview by story ID. Restoring a second, independent page index can
    // select another story or exceed the rebuilt deck after a bookmark/favorite was removed.
    return remember(overlay.sessionId) {
        val page = overlay.stories.indexOfFirst { it.id == visibleStoryId }
            .takeIf { it >= 0 } ?: overlay.initialPage
        PagerState(currentPage = page) { pageCount }
    }
}

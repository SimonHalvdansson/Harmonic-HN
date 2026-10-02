package com.simon.harmonichackernews.ui.stories

import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ScrollWheelPageCooldownMillis = 100L

/** Serializes page animations without requiring trackpad events to stop before paging again. */
internal class StoryPreviewScrollWheelPagingState(private val scope: CoroutineScope) {
    private var pagingJob: Job? = null

    fun page(scrollToPage: suspend () -> Unit) {
        if (pagingJob?.isActive == true) return
        pagingJob = scope.launch {
            scrollToPage()
            // Ignore the immediate tail of a scroll burst, but do not let ignored events extend
            // the cooldown: trackpad momentum or repeated gestures could otherwise lock paging.
            delay(ScrollWheelPageCooldownMillis)
        }
    }
}

/** Platform hook for desktop wheel events; touch-first hosts leave the modifier unchanged. */
internal expect fun Modifier.storyPreviewScrollWheelPaging(
    onScroll: (deltaY: Float) -> Unit,
): Modifier

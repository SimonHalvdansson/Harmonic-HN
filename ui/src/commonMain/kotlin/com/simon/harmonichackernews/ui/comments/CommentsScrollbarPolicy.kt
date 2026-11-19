package com.simon.harmonichackernews.ui.comments

import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.gestures.ScrollableState

/** Lazy lists estimate their length from visible rows. Keep the thumb inside the track at both ends. */
internal class CommentsScrollIndicator(
    private val estimates: ScrollIndicatorState,
    private val list: ScrollableState,
) : ScrollIndicatorState by estimates {
    override val contentSize: Int
        get() = when {
            !list.canScrollForward && !list.canScrollBackward -> 0
            estimates.contentSize == Int.MAX_VALUE || estimates.scrollOffset == Int.MAX_VALUE -> 0
            else -> estimates.contentSize
        }

    override val scrollOffset: Int
        get() {
            val end = (contentSize - viewportSize).coerceAtLeast(0)
            return when {
                !list.canScrollBackward -> 0
                !list.canScrollForward -> end
                else -> estimates.scrollOffset.coerceIn(0, end)
            }
        }
}

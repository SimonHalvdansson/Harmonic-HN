package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

private val PreviewPageDragDistance = 96.dp

@Composable
internal fun rememberStoryPreviewFlingBehavior(state: PagerState): TargetedFlingBehavior {
    val pageSizePx by remember(state) { derivedStateOf { state.layoutInfo.pageSize } }
    val threshold = storyPreviewSnapPositionalThreshold(
        pageSizePx = pageSizePx,
        dragDistancePx = with(LocalDensity.current) { PreviewPageDragDistance.toPx() },
    )
    // Foundation 1.13's flingBehavior remembers the threshold without keying on it. Recreate
    // after the first layout and window resizing so it uses the actual page size.
    return key(threshold) {
        PagerDefaults.flingBehavior(
            state = state,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapPositionalThreshold = threshold,
        )
    }
}

internal fun storyPreviewSnapPositionalThreshold(pageSizePx: Int, dragDistancePx: Float): Float {
    if (pageSizePx <= 0) return 0.5f
    // Pager takes a fraction, but deliberate drags should require the same distance on phones
    // and tablets. Keep its velocity-based flicks, spring settling, and nested-scroll handling.
    return (dragDistancePx / pageSizePx).coerceAtMost(0.5f)
}

@Composable
internal fun rememberStoryPreviewNestedScrollConnection(state: PagerState): NestedScrollConnection {
    val defaults = PagerDefaults.pageNestedScrollConnection(state, Orientation.Vertical)
    return remember(defaults) {
        object : NestedScrollConnection by defaults {
            // The default connection consumes leftover velocity before it reaches Pager's
            // scrollable. Both the card and scrim are vertical scroll children, so that turns
            // every flick into a zero-velocity release. Let Pager settle with the velocity the
            // child could not use; keep the default drag handoff and reversal handling.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity.Zero
        }
    }
}

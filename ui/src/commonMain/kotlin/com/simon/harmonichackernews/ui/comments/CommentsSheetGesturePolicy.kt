package com.simon.harmonichackernews.ui.comments

/** A provisional or restored list position must never authorize lowering the comments sheet. */
internal fun canDragCommentsSheet(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollBackward: Boolean,
    layoutReady: Boolean,
    restoringScroll: Boolean,
): Boolean = layoutReady && !restoringScroll && !canScrollBackward &&
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

package com.simon.harmonichackernews.ui.comments

internal data class ScrollbarMetrics(
    val scrollPosition: Float,
    val visibleFraction: Float,
)

/** Normalize Compose's estimates while keeping the thumb at either end of the actual list. */
internal fun commentsScrollbarMetrics(
    scrollOffset: Int,
    contentSize: Int,
    viewportSize: Int,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): ScrollbarMetrics? {
    if ((!canScrollBackward && !canScrollForward) || contentSize <= 0 || viewportSize <= 0 ||
        contentSize == Int.MAX_VALUE || scrollOffset == Int.MAX_VALUE
    ) return null
    val scrollable = (contentSize - viewportSize).coerceAtLeast(0)
    return ScrollbarMetrics(
        scrollPosition = when {
            !canScrollBackward -> 0f
            !canScrollForward -> 1f
            scrollable == 0 -> 0f
            else -> (scrollOffset.toFloat() / scrollable).coerceIn(0f, 1f)
        },
        visibleFraction = (viewportSize.toFloat() / contentSize).coerceIn(0.04f, 1f),
    )
}

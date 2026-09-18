package com.simon.harmonichackernews.ui.comments

internal data class ScrollbarMetrics(
    val scrollPosition: Float,
    val visibleFraction: Float,
)

/** Estimate variable-height rows in item units, then normalize over the scrollable range. */
internal fun commentsScrollbarMetrics(
    totalItems: Int,
    firstIndex: Int,
    firstFraction: Float,
    lastIndex: Int,
    lastFraction: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): ScrollbarMetrics? {
    if (totalItems <= 0 || (!canScrollBackward && !canScrollForward)) return null
    val start = firstIndex + firstFraction
    val visible = (lastIndex + lastFraction - start).coerceIn(0f, totalItems.toFloat())
    val scrollable = totalItems - visible
    return ScrollbarMetrics(
        scrollPosition = when {
            !canScrollBackward -> 0f
            !canScrollForward -> 1f
            scrollable <= 0f -> 0f
            else -> (start / scrollable).coerceIn(0f, 1f)
        },
        visibleFraction = (visible / totalItems).coerceIn(0.04f, 1f),
    )
}

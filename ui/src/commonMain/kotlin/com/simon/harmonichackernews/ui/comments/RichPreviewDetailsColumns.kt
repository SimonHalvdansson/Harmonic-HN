package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.LinkPreviewDetail

data class RichPreviewDetailsColumns(
    val left: List<LinkPreviewDetail>,
    val right: List<LinkPreviewDetail>,
)

/** Splits alternating preview details in one pass for the two-column layout. */
fun splitRichPreviewDetails(details: List<LinkPreviewDetail>): RichPreviewDetailsColumns {
    val left = ArrayList<LinkPreviewDetail>((details.size + 1) / 2)
    val right = ArrayList<LinkPreviewDetail>(details.size / 2)
    details.forEachIndexed { index, detail ->
        if ((index and 1) == 0) left.add(detail) else right.add(detail)
    }
    return RichPreviewDetailsColumns(left, right)
}

package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewDisplayFormatter
import com.simon.harmonichackernews.network.toNetworkUrlOrNull

data class RichPreviewDetailsColumns(
    val left: List<LinkPreviewDetail>,
    val right: List<LinkPreviewDetail>,
    val fullWidth: List<LinkPreviewDetail> = emptyList(),
)

/** Keeps long names/links below compact metadata so they can use the card's full width. */
fun splitRichPreviewDetails(details: List<LinkPreviewDetail>): RichPreviewDetailsColumns {
    val left = ArrayList<LinkPreviewDetail>((details.size + 1) / 2)
    val right = ArrayList<LinkPreviewDetail>(details.size / 2)
    val fullWidth = ArrayList<LinkPreviewDetail>()
    details.forEach { detail ->
        if (details.size == 1 || detail.label.lowercase() in setOf("author", "authors", "project url", "publisher", "doi", "changes")) {
            fullWidth.add(detail)
        } else if (left.size == right.size) left.add(detail) else right.add(detail)
    }
    return RichPreviewDetailsColumns(left, right, fullWidth)
}

internal fun LinkPreviewDetail.projectLink(): String? = value.trim().takeIf {
    label == "Project URL" &&
        (it.startsWith("https://", true) || it.startsWith("http://", true)) &&
        it.toNetworkUrlOrNull()?.host?.isNotBlank() == true
}

internal fun LinkPreviewDetail.previewText(): String = displayText ?: when {
    projectLink() != null -> value.trim().substringAfter("://").removeSuffix("/")
    label.lowercase() in setOf("author", "authors") -> value
    else -> "$label: ${if (label.lowercase() in compactCountLabels) {
        value.toLongOrNull()?.let(LinkPreviewDisplayFormatter::formatCompactCount) ?: value
    } else value}"
}

private val compactCountLabels = setOf(
    "stars", "github stars", "followers", "likes", "upvotes", "downloads", "recent downloads",
    "comments", "replies", "files", "commits", "items", "assets", "dependencies", "installs (30d)",
)

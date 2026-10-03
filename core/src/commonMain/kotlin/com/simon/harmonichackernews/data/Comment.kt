package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.HtmlTextUtils

class Comment {
    var by: String? = null
    var id: Int = 0
    var parent: Int = 0
    var text: String? = null

    private var cachedExpandedAnchorTextSource: String? = null

    private var cachedExpandedAnchorText: String? = null

    /** Restores versioned, platform-neutral text preparation without repeating HTML parsing. */
    internal fun restorePreparedText(html: String, expandedHtml: String) {
        text = html
        cachedExpandedAnchorTextSource = html
        cachedExpandedAnchorText = expandedHtml
    }

    internal fun updateTextFrom(other: Comment) {
        text = other.text
        if (other.cachedExpandedAnchorTextSource == other.text) {
            cachedExpandedAnchorTextSource = other.text
            cachedExpandedAnchorText = other.cachedExpandedAnchorText
        }
    }

    var time: Int = 0
    var expanded: Boolean = false
    var depth: Int = 0
    var children: Int = 0
    var totalReplies: Int = 0

    private var mutableChildComments: MutableList<Comment>? = null

    // Most comments are leaves, and default-order threads never need a mutable child list.
    // Preserve the mutable accessor for callers that explicitly build or edit a tree.
    var childComments: MutableList<Comment>
        get() = mutableChildComments ?: mutableListOf<Comment>().also { mutableChildComments = it }
        set(value) { mutableChildComments = value }

    internal val childCommentsOrEmpty: List<Comment> get() = mutableChildComments.orEmpty()

    internal fun resetChildComments() { mutableChildComments = null }
    var sortOrder: Int = 0
    var kidsIds: IntArray? = null // For official HN API fallback - stores child comment IDs

    val timeFormatted: String
        get() = ItemTimeFormatter.formatNow(time)

    fun formatTime(nowMillis: Long): String = ItemTimeFormatter.format(time, nowMillis)

    val expandedAnchorText: String?
        get() {
            val currentText = text
            if (currentText == cachedExpandedAnchorTextSource) {
                return cachedExpandedAnchorText
            }

            val expandedText = expandShortenedAnchorText(currentText)
            cachedExpandedAnchorTextSource = currentText
            cachedExpandedAnchorText = expandedText
            return expandedText
        }
}

internal fun expandShortenedAnchorText(inputHtml: String?): String? =
    HtmlTextUtils.expandShortenedAnchorText(inputHtml)

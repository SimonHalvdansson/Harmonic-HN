package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.utils.Utils
import java.io.Serializable

class Comment : Serializable {
    var by: String? = null
    var id: Int = 0
    var parent: Int = 0
    var text: String? = null

    @Transient
    private var cachedExpandedAnchorTextSource: String? = null

    @Transient
    private var cachedExpandedAnchorText: String? = null

    var time: Int = 0
    var expanded: Boolean = false
    var depth: Int = 0
    var children: Int = 0
    var totalReplies: Int = 0

    var childComments: MutableList<Comment> = mutableListOf()
    var sortOrder: Int = 0
    var kidsIds: IntArray? = null // For official HN API fallback - stores child comment IDs

    val timeFormatted: String
        get() = Utils.getTimeAgo(this.time.toLong())

    val expandedAnchorText: String?
        get() {
            val currentText = text
            if (currentText == cachedExpandedAnchorTextSource) {
                return cachedExpandedAnchorText
            }

            val expandedText = Utils.expandShortenedAnchorText(currentText)
            cachedExpandedAnchorTextSource = currentText
            cachedExpandedAnchorText = expandedText
            return expandedText
        }
}

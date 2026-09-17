package com.simon.harmonichackernews.data

class CommentsScrollProgress {
    var initialized: Boolean = false
    var storyId: Int = 0
    var topCommentId: Int = 0
    var topCommentOffset: Int = 0
    var collapsedIDs: MutableSet<Int> = mutableSetOf()
}

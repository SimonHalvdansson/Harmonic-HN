package com.simon.harmonichackernews.data

class CommentsScrollProgress {
    var storyId: Int = 0
    var topCommentId: Int = 0
    var topCommentOffset: Int = 0
    var collapsedIDs: MutableSet<Int> = mutableSetOf()
}

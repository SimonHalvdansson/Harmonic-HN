package com.simon.harmonichackernews.data

import java.util.HashSet

class CommentsScrollProgress {
    var storyId: Int = 0
    var topCommentId: Int = 0
    var topCommentOffset: Int = 0
    var collapsedIDs: HashSet<Int> = HashSet()
}

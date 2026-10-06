package com.simon.harmonichackernews.summary

import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot

/** Immutable source snapshot for a single Ask conversation. */
sealed interface AskSource {
    val story: StoryListItemSnapshot

    data class Comment(
        override val story: StoryListItemSnapshot,
        val comment: PortableCommentItem,
        val comments: List<PortableCommentItem>,
    ) : AskSource

    data class Post(
        override val story: StoryListItemSnapshot,
        val summary: String,
        val articleText: String? = null,
    ) : AskSource
}

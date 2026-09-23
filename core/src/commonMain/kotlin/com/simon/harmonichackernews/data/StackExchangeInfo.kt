package com.simon.harmonichackernews.data

import kotlinx.serialization.Serializable

@Serializable
data class StackExchangeInfo(
    val title: String? = null,
    val author: String? = null,
    val questionText: String? = null,
    val tags: List<String?> = emptyList(),
    val site: String? = null,
    val score: Int = 0,
    val answerCount: Int = 0,
    val viewCount: Int = 0,
    val isAnswered: Boolean = false,
    val hasAcceptedAnswer: Boolean = false,
) {
    fun formatScore(): String = LinkPreviewDisplayFormatter.formatCount(score, "point", "points")
    fun formatAnswerCount(): String =
        LinkPreviewDisplayFormatter.formatCount(answerCount, "answer", "answers")
    fun formatViewCount(): String = LinkPreviewDisplayFormatter.formatCount(viewCount, "view", "views")
    fun formatAnswerState(): String = when {
        hasAcceptedAnswer -> "Accepted answer"
        isAnswered -> "Answered"
        else -> "Unanswered"
    }
    fun formatTags(): String? = tags.takeIf(List<String?>::isNotEmpty)?.joinToString(", ")
    fun formatBy(): String? = questionText ?: author?.let { "$it on $site" } ?: site
    fun formatAuthor(): String? = author ?: site
}

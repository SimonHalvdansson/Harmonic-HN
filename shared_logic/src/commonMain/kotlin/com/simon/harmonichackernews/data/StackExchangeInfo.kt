package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.data.LinkPreviewFormatUtils.formatCount

class StackExchangeInfo {
    var title: String? = null
    var author: String? = null
    var questionText: String? = null
    var tags: Array<String?>? = null
    var site: String? = null
    var score: Int = 0
    var answerCount: Int = 0
    var viewCount: Int = 0
    var isAnswered: Boolean = false
    var hasAcceptedAnswer: Boolean = false

    fun formatScore(): String = formatCount(score, "point", "points")

    fun formatAnswerCount(): String = formatCount(answerCount, "answer", "answers")

    fun formatViewCount(): String = formatCount(viewCount, "view", "views")

    fun formatAnswerState(): String {
        if (hasAcceptedAnswer) {
            return "Accepted answer"
        }

        if (isAnswered) {
            return "Answered"
        }

        return "Unanswered"
    }

    fun formatTags(): String? {
        val currentTags = tags
        if (currentTags.isNullOrEmpty()) {
            return null
        }

        return currentTags.joinToString(", ")
    }

    fun formatBy(): String? = questionText ?: author?.let { "$it on $site" } ?: site

    fun formatAuthor(): String? = author ?: site
}

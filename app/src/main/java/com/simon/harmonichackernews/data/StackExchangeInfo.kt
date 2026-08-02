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

    fun formatScore(): String {
        return formatCount(score, "point", "points")
    }

    fun formatAnswerCount(): String {
        return formatCount(answerCount, "answer", "answers")
    }

    fun formatViewCount(): String {
        return formatCount(viewCount, "view", "views")
    }

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

        val builder = StringBuilder()
        for (i in currentTags.indices) {
            if (i > 0) {
                builder.append(", ")
            }
            builder.append(currentTags[i])
        }
        return builder.toString()
    }

    fun formatBy(): String? {
        if (questionText != null) {
            return questionText
        }

        if (author == null) {
            return site
        }

        return author + " on " + site
    }

    fun formatAuthor(): String? {
        if (author == null) {
            return site
        }

        return author
    }
}

package com.simon.harmonichackernews.utils


object StoryTitlePolicy {
    private val pollWord = Regex("\\bpoll\\b", RegexOption.IGNORE_CASE)

    fun mayDescribePoll(title: String?): Boolean =
        !title.isNullOrEmpty() && pollWord.containsMatchIn(title)
}

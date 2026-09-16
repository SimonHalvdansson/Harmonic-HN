package com.simon.harmonichackernews.data

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
}

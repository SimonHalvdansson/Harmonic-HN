package com.simon.harmonichackernews.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser

class Comment {
    var by: String? = null
    var id: Int = 0
    var parent: Int = 0
    var text: String? = null

    private var cachedExpandedAnchorTextSource: String? = null

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
        get() = ItemTimeFormatter.formatNow(time)

    fun formatTime(nowMillis: Long): String = ItemTimeFormatter.format(time, nowMillis)

    val expandedAnchorText: String?
        get() {
            val currentText = text
            if (currentText == cachedExpandedAnchorTextSource) {
                return cachedExpandedAnchorText
            }

            val expandedText = expandShortenedAnchorText(currentText)
            cachedExpandedAnchorTextSource = currentText
            cachedExpandedAnchorText = expandedText
            return expandedText
        }

    private fun expandShortenedAnchorText(inputHtml: String?): String? {
        if (inputHtml.isNullOrEmpty() || !inputHtml.contains("<a")) return inputHtml

        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        document.select("a[href]").forEach { link ->
            val decodedLinkText = decodeAnchorPart(link.text())
            if (decodedLinkText.endsWith("...")) {
                val decodedHref = decodeAnchorPart(link.attr("href"))
                val prefix = decodedLinkText.dropLast(3)
                if (decodedHref.startsWith(prefix)) link.text(decodedHref)
            }
        }
        return document.body().html()
    }

    private fun decodeAnchorPart(value: String): String {
        // Attribute/text extraction has already decoded the outer HTML. Printable ASCII text
        // with normalized spaces and no markup/entities is unchanged by another HTML parse. Keep
        // Ksoup for whitespace, Unicode, nested entities and markup to preserve legacy output.
        var previousWasSpace = true
        for (character in value) {
            if (character !in ' '..'~' || character == '<' || character == '&' ||
                (character == ' ' && previousWasSpace)
            ) {
                return Ksoup.parse(value).text()
            }
            previousWasSpace = character == ' '
        }
        if (value.isNotEmpty() && previousWasSpace) return Ksoup.parse(value).text()
        return value
    }
}

package com.simon.harmonichackernews.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import kotlin.time.Clock

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
        get() = RelativeTimeFormatter.format(time.toLong(), Clock.System.now().toEpochMilliseconds())

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
            val decodedHref = Ksoup.parse(link.attr("href")).text()
            val decodedLinkText = Ksoup.parse(link.text()).text()
            if (decodedLinkText.endsWith("...")) {
                val prefix = decodedLinkText.dropLast(3)
                if (decodedHref.startsWith(prefix)) link.text(decodedHref)
            }
        }
        return document.body().html()
    }
}

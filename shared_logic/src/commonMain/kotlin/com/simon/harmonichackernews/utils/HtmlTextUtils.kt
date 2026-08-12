package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser

object HtmlTextUtils {
    fun plainText(inputHtml: String?): String =
        inputHtml?.takeIf(String::isNotEmpty)?.let { Ksoup.parse(it).text() }.orEmpty()

    fun expandShortenedAnchorText(inputHtml: String?): String? {
        if (inputHtml.isNullOrEmpty() || !inputHtml.contains("<a")) return inputHtml

        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        for (link in document.select("a[href]")) {
            val decodedHref = Ksoup.parse(link.attr("href")).text()
            val decodedLinkText = Ksoup.parse(link.text()).text()
            if (!decodedLinkText.endsWith("...")) continue
            val prefix = decodedLinkText.dropLast(3)
            if (decodedHref.startsWith(prefix)) link.text(decodedHref)
        }
        return document.body().html()
    }

    fun normalizeAndTruncatePlainText(value: String, maximumChars: Int): String {
        val normalized = value
            .replace('\u00a0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\t\\u000B\\f ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (maximumChars <= 0 || normalized.length <= maximumChars) return normalized
        val minimumBoundary = (maximumChars * 0.75f).toInt()
        val end = (maximumChars - 1 downTo minimumBoundary)
            .firstOrNull { normalized[it].isWhitespace() }
            ?: maximumChars
        return normalized.substring(0, end).trim() + "…"
    }
}

object StoryTitlePolicy {
    private val pollWord = Regex("\\bpoll\\b", RegexOption.IGNORE_CASE)

    fun mayDescribePoll(title: String?): Boolean =
        !title.isNullOrEmpty() && pollWord.containsMatchIn(title)
}

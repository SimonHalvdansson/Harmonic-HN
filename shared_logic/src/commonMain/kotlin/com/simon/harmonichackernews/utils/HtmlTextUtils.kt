package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser

object HtmlTextUtils {
    private val anchorPattern = Regex("(?is)<a\\b[^>]*>.*?</a>")
    private val urlPattern = Regex(
        "(https?:(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)",
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?)"

    fun plainText(inputHtml: String?): String =
        inputHtml?.takeIf(String::isNotEmpty)?.let { Ksoup.parse(it).text() }.orEmpty()

    fun linkify(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        if (!input.contains("http:") && !input.contains("https:")) return input

        val output = StringBuilder(input.length)
        var start = 0
        anchorPattern.findAll(input).forEach { anchor ->
            output.append(linkifySegment(input.substring(start, anchor.range.first)))
            output.append(anchor.value)
            start = anchor.range.last + 1
        }
        output.append(linkifySegment(input.substring(start)))
        return output.toString()
    }

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

    private fun linkifySegment(segment: String): String = urlPattern.replace(segment) { match ->
        createLinkReplacement(match.value)
    }

    private fun createLinkReplacement(url: String): String {
        var end = url.length
        while (end > 0 && url[end - 1] in TRAILING_PUNCTUATION) end--

        if (end > 0 && url[end - 1] == ')') {
            var opens = 0
            var closes = 0
            for (index in 0..<end) {
                when (url[index]) {
                    '(' -> opens++
                    ')' -> closes++
                }
            }
            if (closes > opens) end--
        }

        val normalized = url.substring(0, end)
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
        return "<a href=\"$normalized\">$normalized</a>${url.substring(end)}"
    }
}

object StoryTitlePolicy {
    private val pollWord = Regex("\\bpoll\\b", RegexOption.IGNORE_CASE)

    fun mayDescribePoll(title: String?): Boolean =
        !title.isNullOrEmpty() && pollWord.containsMatchIn(title)
}

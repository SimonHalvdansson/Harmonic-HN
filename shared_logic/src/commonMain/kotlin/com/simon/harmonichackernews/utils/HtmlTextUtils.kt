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
        val normalized = normalizePlainText(value)
        if (maximumChars <= 0 || normalized.length <= maximumChars) return normalized
        val minimumBoundary = (maximumChars * 0.75f).toInt()
        val end = (maximumChars - 1 downTo minimumBoundary)
            .firstOrNull { normalized[it].isWhitespace() }
            ?: maximumChars
        return normalized.substring(0, end).trim() + "…"
    }

    private fun normalizePlainText(value: String): String {
        if (value.isEmpty()) return value
        val normalized = StringBuilder(value.length)
        var pendingSpace = false
        var pendingNewlines = 0
        var index = 0

        fun flushPending() {
            if (pendingNewlines > 0) {
                repeat(minOf(pendingNewlines, 2)) { normalized.append('\n') }
            } else if (pendingSpace) {
                normalized.append(' ')
            }
            pendingSpace = false
            pendingNewlines = 0
        }

        while (index < value.length) {
            val character = value[index++]
            when {
                character == '\r' -> {
                    if (index < value.length && value[index] == '\n') index++
                    pendingSpace = false
                    pendingNewlines++
                }

                character == '\n' -> {
                    pendingSpace = false
                    pendingNewlines++
                }

                character.isCollapsibleHorizontalWhitespace() -> {
                    if (pendingNewlines == 0) pendingSpace = true
                }

                else -> {
                    flushPending()
                    normalized.append(character)
                }
            }
        }

        // Pending ASCII whitespace would be removed by the final trim, so avoid materializing it.
        return normalized.toString().trim()
    }

    private fun Char.isCollapsibleHorizontalWhitespace(): Boolean =
        this == ' ' || this == '\t' || this == '\u000B' || this == '\u000C' ||
            this == '\u00A0'
}

object StoryTitlePolicy {
    private val pollWord = Regex("\\bpoll\\b", RegexOption.IGNORE_CASE)

    fun mayDescribePoll(title: String?): Boolean =
        !title.isNullOrEmpty() && pollWord.containsMatchIn(title)
}

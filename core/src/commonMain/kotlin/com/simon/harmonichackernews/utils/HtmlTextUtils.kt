package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import com.fleeksoft.ksoup.select.Selector

object HtmlTextUtils {
    fun plainText(inputHtml: String?): String {
        if (inputHtml.isNullOrEmpty()) return ""
        // Printable ASCII with normalized spaces and no markup/entities is already DOM text.
        // Keep the parser for Unicode, control characters and anything that needs HTML decoding.
        var previousWasSpace = true
        for (character in inputHtml) {
            if (character !in ' '..'~' || character == '<' || character == '&' ||
                (character == ' ' && previousWasSpace)
            ) {
                return Ksoup.parse(inputHtml).text()
            }
            previousWasSpace = character == ' '
        }
        return if (previousWasSpace) Ksoup.parse(inputHtml).text() else inputHtml
    }

    fun expandShortenedAnchorText(inputHtml: String?): String? {
        if (inputHtml.isNullOrEmpty() || !inputHtml.contains("<a")) return inputHtml

        val document = Ksoup.parse(inputHtml, Parser.htmlParser(), "")
        for (link in document.select(AnchorTextSelector.anchorsWithHref)) {
            val decodedLinkText = plainText(link.text())
            if (!decodedLinkText.endsWith("...")) continue
            val decodedHref = plainText(link.attr("href"))
            val prefix = decodedLinkText.dropLast(3)
            if (decodedHref.startsWith(prefix)) link.text(decodedHref)
        }
        return document.body().html()
    }

    // Initialize only when an input contains anchors; the evaluator is reusable across documents.
    private object AnchorTextSelector {
        val anchorsWithHref = Selector.evaluatorOf("a[href]")
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

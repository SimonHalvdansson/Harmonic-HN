package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextUtilsTest {
    @Test
    fun plainTextKeepsHtmlParserOutputForAsciiUnicodeEntitiesAndWhitespace() {
        val inputs = buildList {
            addAll(listOf(
                null, "", "ordinary text", " leading", "trailing ", "double  spaces",
                "<p>one</p><p>two</p>", "<pre>  preserved\nwhitespace</pre>",
                "a &amp; b &lt;b&gt; &#0;", "café 中文 Ελληνικά 😀", "a\u00a0b\u2003c",
                "&amp;lt;em&amp;gt;nested&amp;lt;/em&amp;gt;", "a\u200bb\u00adc",
            ))
            for (code in 0..127) {
                val character = code.toChar()
                add("a${character}b")
                add("${character}leading")
                add("trailing$character")
                add(character.toString())
            }
        }
        for (input in inputs) {
            assertEquals(input?.let { Ksoup.parse(it).text() }.orEmpty(), HtmlTextUtils.plainText(input))
        }
    }

    @Test
    fun sharedAnchorExpansionKeepsLegacyDecodingAndHtmlSerialization() {
        val links = listOf(
            "<a href='https://example.com/article'>https://example.com/art...</a>",
            "<a href='https://example.com/?a=1&amp;amp;b=2'>https://example.com/?a=1...</a>",
            "<a href='https://example.com/café'>https://example.com/caf...</a>",
            "<a href='https://example.com/other'>different...</a>",
            "<a href='https://example.com'>&lt;b&gt;nested&lt;/b&gt;</a>",
            "<a href='https://example.com/whitespace'> https://example.com/white... </a>",
            "<a href='/relative'>relative</a>",
        )
        for (input in links + links.joinToString("<p>")) {
            val document = Ksoup.parse(input, Parser.htmlParser(), "")
            for (link in document.select("a[href]")) {
                val text = Ksoup.parse(link.text()).text()
                if (!text.endsWith("...")) continue
                val href = Ksoup.parse(link.attr("href")).text()
                if (href.startsWith(text.dropLast(3))) link.text(href)
            }
            assertEquals(document.body().html(), HtmlTextUtils.expandShortenedAnchorText(input))
        }
    }

    @Test
    fun normalizesLineEndingsHorizontalWhitespaceAndBlankLines() {
        assertEquals(
            "first line\nsecond line\n\nthird line",
            HtmlTextUtils.normalizeAndTruncatePlainText(
                " \u00A0first\t line \r\n  second\u000Bline\r\r\r\u000Cthird line\u00A0 ",
                maximumChars = 1_000,
            ),
        )
    }

    @Test
    fun preservesExactLegacyNormalizationAcrossEdgeCorpus() {
        val corpus = listOf(
            "",
            "plain text",
            "  leading and trailing  ",
            "one\r\ntwo\rthree\nfour",
            "one\n\n\n\n\nfive",
            "one \n two \n\n three",
            "\u00A0one\u00A0\u00A0two\u00A0",
            "\u2003 one \n \u2003 two \u2003",
            "tabs\t\tand\u000Bvertical\u000Cform feed",
            "line one\n   \n   \nline four",
        )
        val limits = listOf(-1, 0, 1, 2, 4, 8, 16, 80)

        corpus.forEach { input ->
            limits.forEach { limit ->
                assertEquals(
                    legacyNormalizeAndTruncate(input, limit),
                    HtmlTextUtils.normalizeAndTruncatePlainText(input, limit),
                    "input=${input.debugValue()}, maximumChars=$limit",
                )
            }
        }
    }

    @Test
    fun preservesExactLegacyNormalizationAcrossGeneratedMixedWhitespace() {
        val alphabet = charArrayOf(
            'a', 'B', '3', ' ', '\t', '\n', '\r', '\u000B', '\u000C', '\u00A0', '\u2003', '.',
        )
        var state = 0x13579BDF
        repeat(300) { sample ->
            val input = buildString {
                repeat(96) {
                    state = state * 1_103_515_245 + 12_345
                    append(alphabet[(state ushr 1) % alphabet.size])
                }
            }
            listOf(1, 7, 24, 64, 128).forEach { limit ->
                assertEquals(
                    legacyNormalizeAndTruncate(input, limit),
                    HtmlTextUtils.normalizeAndTruncatePlainText(input, limit),
                    "sample=$sample, maximumChars=$limit, input=${input.debugValue()}",
                )
            }
        }
    }

    @Test
    fun truncationKeepsTheSameBoundaryAndEllipsisBehavior() {
        val input = "alpha beta gamma delta epsilon"
        assertEquals(
            legacyNormalizeAndTruncate(input, 18),
            HtmlTextUtils.normalizeAndTruncatePlainText(input, 18),
        )
        assertEquals("alpha beta gamma…", HtmlTextUtils.normalizeAndTruncatePlainText(input, 18))
    }

    private fun legacyNormalizeAndTruncate(value: String, maximumChars: Int): String {
        val normalized = value
            .replace('\u00A0', ' ')
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

    private fun String.debugValue(): String = buildString {
        this@debugValue.forEach { character ->
            when (character) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}

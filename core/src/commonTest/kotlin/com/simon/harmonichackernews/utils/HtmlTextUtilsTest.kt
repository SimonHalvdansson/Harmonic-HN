package com.simon.harmonichackernews.utils

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlTextUtilsTest {
    @Test
    fun residualEntitiesDecodeWithoutRemovingLiteralMarkupOrDecodingRepeatedly() {
        assertEquals("read & write weights", HtmlTextUtils.decodeEntities("read &amp; write weights"))
        assertEquals("\"Qwen\" — café 😀", HtmlTextUtils.decodeEntities("&quot;Qwen&quot; &#8212; caf&#xE9; &#x1F600;"))
        assertEquals("List<T> & <b>literal</b>", HtmlTextUtils.decodeEntities("List<T> &amp; &lt;b&gt;literal&lt;/b&gt;"))
        assertEquals("&amp;", HtmlTextUtils.decodeEntities("&amp;amp;"))
        assertEquals("AT&T &unknown;", HtmlTextUtils.decodeEntities("AT&T &unknown;"))
    }

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

    @Test
    fun boundedNormalizationDistinguishesTrimmedTailsFromContentPastTheLimit() {
        val whitespace = " \t\r\n\u00a0\u2003\u2028".repeat(300)
        val inputs = listOf(
            "alpha beta gamma delta".repeat(1500),
            whitespace + "alpha beta" + whitespace,
            "alpha beta" + whitespace + "omega",
            "alpha beta " + "\u2003".repeat(3000),
            "alpha beta " + "\u2003".repeat(3000) + "omega",
            "café 中文 😀\n\nmore text".repeat(100),
        )
        for (input in inputs) {
            for (limit in listOf(-1, 0, 1, 5, 9, 10, 11, 18, 1200)) {
                assertEquals(legacyNormalizeAndTruncate(input, limit),
                    HtmlTextUtils.normalizeAndTruncatePlainText(input, limit),
                    "length=${input.length}, maximumChars=$limit")
            }
        }
    }

    @Test
    fun boundedNormalizationMatchesPreviousSinglePassImplementation() {
        val alphabet = charArrayOf('a', 'B', '3', ' ', '\t', '\n', '\r', '\u000B',
            '\u000C', '\u00A0', '\u2003', '\u2028', '\u2029', '\u0085', '\uD83D', '\uDE00', '.')
        var state = 0x2468ACE
        repeat(600) { sample ->
            val input = buildString {
                repeat(1 + sample % 193) {
                    state = state * 1_103_515_245 + 12_345
                    append(alphabet[(state ushr 1) % alphabet.size])
                }
            }
            for (limit in listOf(-1, 0, 1, 2, 3, 7, 23, 24, 25, 64, 193)) {
                assertEquals(PreviousPreviewNormalizer.normalizeAndTruncatePlainText(input, limit),
                    HtmlTextUtils.normalizeAndTruncatePlainText(input, limit),
                    "sample=$sample, maximumChars=$limit, input=${input.debugValue()}")
            }
        }
    }

    private object PreviousPreviewNormalizer {
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

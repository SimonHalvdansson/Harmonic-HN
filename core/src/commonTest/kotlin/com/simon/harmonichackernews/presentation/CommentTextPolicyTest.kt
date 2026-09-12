package com.simon.harmonichackernews.presentation

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentTextPolicyTest {
    @Test
    fun preservesLegacySpacingAcrossParagraphAndDivisionBoundaries() {
        assertEquals(
            "<br><br>First</p><br><p class=\"next\">Second</p></div><br><div>Third",
            CommentTextPolicy.preserveLegacyParagraphSpacing(
                "<P >First</p> \n <p class=\"next\">Second</p></div>\t<div>Third",
            ),
        )
    }

    @Test
    fun preservesSpacingForPlainTextAndUnclosedHackerNewsParagraphs() {
        assertEquals(
            "Plain &amp; literal text\n😀",
            CommentTextPolicy.preserveLegacyParagraphSpacing("Plain &amp; literal text\n😀"),
        )
        assertEquals(
            "First<br><br>Second<br><br>Third",
            CommentTextPolicy.preserveLegacyParagraphSpacing("First<p>Second<P \n>Third"),
        )
        assertEquals(
            "<span>inline text</span>",
            CommentTextPolicy.preserveLegacyParagraphSpacing("<span>inline text</span>"),
        )
    }

    @Test
    fun spacingMatchesLegacyForMixedAndMalformedMarkup() {
        val paragraphStart = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
        val adjacentParagraphs = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
        val adjacentDivisions = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)
        val fragments = listOf(
            "", "plain", "😀", "&lt;p&gt;", "<", "</", "<p>", "<P >", "<p\n>",
            "<p\t>", "<p\u000b>", "<p\u000c>", "<p\r\n>", "<p\u00a0>",
            "<p class='x'>", "</p>", "</P>", "</p >", "<pre>", "</pre>",
            "<div>", "<DiV class='x'>", "</DIV>", "<span>", "</span>",
            "<i>italic</i>", "<a href='https://example.com'>link</a>", " ", "\r\n", "\t",
        )
        fun verify(html: String) {
            val expected = html.replace(paragraphStart, "<br><br>")
                .replace(adjacentParagraphs, "</p><br><p")
                .replace(adjacentDivisions, "</div><br><div")
            assertEquals(expected, CommentTextPolicy.preserveLegacyParagraphSpacing(html), html)
        }
        for (left in fragments) for (right in fragments) verify(left + right)
        val random = Random(73)
        repeat(1_000) {
            verify(buildString { repeat(random.nextInt(1, 16)) { append(fragments.random(random)) } })
        }
    }
}

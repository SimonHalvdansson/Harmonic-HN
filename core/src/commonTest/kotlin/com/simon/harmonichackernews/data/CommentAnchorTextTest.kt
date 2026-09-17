package com.simon.harmonichackernews.data

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentAnchorTextTest {
    @Test
    fun expansionPreservesHtmlAndEntitySemantics() {
        val labels = listOf(
            "https://example.com/...", "https://example.com/&#46;&#46;&#46;",
            "https://example.com/full", "a link", "", " ... ",
            "<b>https://example.com/...</b>", "&amp;lt;b&amp;gt;...",
            "a\u00a0b...", "a\u200bb...", "a\tb...", "a\nb...",
            "&lt;https://example.com/...&gt;", "café...", "😀...",
        )
        val urls = listOf(
            "https://example.com/full", "https://example.com/?a=1&amp;b=2",
            "https://example.com/&amp;amp;x", "a b full", "",
            "&lt;b&gt;...&lt;/b&gt;", "a\u00a0b full", "café full", "😀 full",
        )
        for (label in labels) for (url in urls) {
            val html = "<p>Before <a href=\"$url\">$label</a> after</p>"
            assertEquals(legacyExpansion(html), Comment().apply { text = html }.expandedAnchorText, html)
        }
        for (html in listOf(null, "", "Plain text", "<a>no href</a>", "<A href=x>x</A>",
            "<p><a href='https://example.com/full'>https://example.com/...</p>",
            "<a href=x>x</a><a href=y>y...</a>")) {
            assertEquals(legacyExpansion(html), Comment().apply { text = html }.expandedAnchorText, html)
        }
    }

    @Test
    fun expansionCacheInvalidatesWhenCommentTextChanges() {
        val comment = Comment().apply { text = "<a href='https://a.com/full'>https://a.com/...</a>" }
        assertEquals(legacyExpansion(comment.text), comment.expandedAnchorText)
        comment.text = "<a href='https://b.com/full'>https://b.com/...</a>"
        assertEquals(legacyExpansion(comment.text), comment.expandedAnchorText)
        comment.text = null
        assertEquals(null, comment.expandedAnchorText)
    }

    @Test
    fun anchorSelectionIsIndependentAcrossSequentialAndConcurrentDocuments() = runTest {
        val documents = listOf(
            "<a href='https://one.example/full'>https://one.example/...</a>",
            "<a>missing href</a><a href=''>...</a><span href='https://skip.example'>skip</span>",
            "<article><A HREF='https://two.example/full'>https://two.example/...</A></article>",
            "<a href='https://three.example/full'>short title</a><a href=x>x...</a>",
            "<a href=x><i>x...</i></a><p>tail",
            "<a href='https://four.example/?a=1&amp;b=2'>https://four.example/...</a>",
        )
        val expected = documents.map(::legacyExpansion)
        repeat(3) {
            for (index in documents.indices.reversed()) {
                assertEquals(expected[index], Comment().apply { text = documents[index] }.expandedAnchorText)
            }
        }
        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) {
                    repeat(5) {
                        for (index in documents.indices) {
                            assertEquals(expected[index], Comment().apply { text = documents[index] }.expandedAnchorText)
                        }
                    }
                }
            }
        }
    }

    private fun legacyExpansion(html: String?): String? {
        if (html.isNullOrEmpty() || !html.contains("<a")) return html
        val document = Ksoup.parse(html, Parser.htmlParser(), "")
        document.select("a[href]").forEach { link ->
            val href = Ksoup.parse(link.attr("href")).text()
            val label = Ksoup.parse(link.text()).text()
            if (label.endsWith("...")) {
                if (href.startsWith(label.dropLast(3))) link.text(href)
            }
        }
        return document.body().html()
    }
}

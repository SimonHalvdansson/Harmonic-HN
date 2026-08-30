package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArxivLinkPreviewTest {
    @Test
    fun extractsVersionedHtmlArticleUrlFromAbstractPage() {
        val html = """
            <html><body>
              <a href="/pdf/2501.06425">View PDF</a>
              <a href="/html/2501.06425v7">HTML (experimental)</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://arxiv.org/html/2501.06425v7",
            LinkPreviewParsers.parseArxivHtmlUrl(html),
        )
    }

    @Test
    fun returnsNullWhenPaperHasNoHtmlConversion() {
        val html = """
            <html><body><a href="/pdf/0704.0001">View PDF</a></body></html>
        """.trimIndent()

        assertNull(LinkPreviewParsers.parseArxivHtmlUrl(html))
    }
}

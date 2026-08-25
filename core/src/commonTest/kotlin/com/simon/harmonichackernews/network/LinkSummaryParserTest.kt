package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinkSummaryParserTest {
    @Test
    fun indexedMetadataPreservesSelectorPriorityAndFirstDocumentValue() {
        val summary = LinkSummaryParser.extract(
            html = """
                <html lang="en"><head>
                  <meta property="og:title" content="OpenGraph title">
                  <meta property="og:title" content="Later duplicate">
                  <meta name="twitter:title" content="Twitter title">
                  <meta name="author" content="Ada Lovelace">
                  <meta property="article:published_time" content="2026-08-21">
                  <meta name="description" content="A sufficiently detailed description for the parser to retain unchanged.">
                  <meta property="og:image" content="/preview.webp">
                </head><body></body></html>
            """.trimIndent(),
            fallbackTitle = "Fallback",
            contentType = "text/html",
            finalUrl = "https://example.com/articles/test",
        )

        assertEquals("OpenGraph title", summary.title)
        assertEquals("Ada Lovelace", summary.author)
        assertEquals("2026-08-21", summary.publishedTime)
        assertEquals("https://example.com/preview.webp", summary.imageUrl)
    }

    @Test
    fun descriptionFallbackStillPrefersArticleContentAndExcludesNavigation() {
        val summary = LinkSummaryParser.extract(
            html = """
                <html><head><title>Example</title></head><body>
                  <nav><p>This navigation paragraph is deliberately long but must never be selected as article text.</p></nav>
                  <main><p>This useful article paragraph contains enough explanatory prose, words, and punctuation to be selected.</p></main>
                </body></html>
            """.trimIndent(),
            fallbackTitle = null,
            contentType = "text/html",
            finalUrl = "https://example.com/article",
        )

        assertFalse(summary.description.contains("navigation"))
        assertEquals(
            "This useful article paragraph contains enough explanatory prose, words, and punctuation to be selected.",
            summary.description,
        )
    }
}

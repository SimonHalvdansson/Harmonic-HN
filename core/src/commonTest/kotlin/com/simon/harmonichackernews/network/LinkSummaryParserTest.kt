package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkSummaryParserTest {
    @Test
    fun storyPreviewKeepsDiscussionMetadataSeparateFromItsBody() {
        val url = "https://news.ycombinator.com/item?id=1"
        val summary = LinkSummaryParser.extractHackerNewsItem(
            json = """{"type":"story","title":"Example","by":"pg","score":123,"descendants":45,"url":"https://example.com/article","text":"First paragraph.<p>Second paragraph."}""",
            pageUrl = url,
            fallbackTitle = null,
        )!!
        assertEquals(123, summary.storyPoints)
        assertEquals(45, summary.storyComments)
        assertEquals("https://example.com/article", summary.storyUrl)
        assertEquals("First paragraph.\n\nSecond paragraph.", summary.description)
        assertEquals(summary, LinkSummaryCodec.decode(LinkSummaryCodec.encode(summary)))
        assertTrue(StoryPreviewRepository.isValidSummary(url, summary))
        assertFalse(StoryPreviewRepository.isValidSummary(url, summary.copy(storyMetadataVersion = 0)))
    }

    @Test
    fun textOnlyStoryDoesNotInventArticleUrlOrStatistics() {
        val summary = LinkSummaryParser.extractHackerNewsItem(
            """{"type":"story","title":"Ask HN","text":"Question"}""",
            "https://news.ycombinator.com/item?id=1", null,
        )!!
        assertEquals("", summary.storyUrl)
        assertEquals(-1, summary.storyPoints)
        assertEquals(-1, summary.storyComments)
        assertEquals("Question", summary.description)
    }

    @Test
    fun commentReferencePreservesParagraphsWithoutRepeatingMetadata() {
        val summary = LinkSummaryParser.extractHackerNewsItem(
            json = """{"type":"comment","by":"tptacek","time":1739365814,"kids":[1,2,3],"text":"First paragraph.<p>* A &amp; B<br>Next line.<p>Final <i>paragraph</i>."}""",
            pageUrl = "https://news.ycombinator.com/item?id=43025038",
            fallbackTitle = null,
        )!!
        assertEquals("Comment by tptacek", summary.title)
        assertEquals("First paragraph.\n\n* A & B\nNext line.\n\nFinal paragraph.", summary.description)
        assertEquals(1, summary.commentTextVersion)
        assertEquals(summary, LinkSummaryCodec.decode(LinkSummaryCodec.encode(summary)))
    }

    @Test
    fun commentReferenceTruncationPreservesParagraphBreaks() {
        val summary = LinkSummaryParser.extractHackerNewsItem(
            json = """{"type":"comment","by":"pg","text":"Intro.<p>${"More words. ".repeat(700)}"}""",
            pageUrl = "https://news.ycombinator.com/item?id=1",
            fallbackTitle = null,
        )!!
        assertTrue(summary.description.startsWith("Intro.\n\nMore words."))
        assertTrue(summary.description.endsWith("…"))
    }

    @Test
    fun distinguishesHackerNewsPostsFromCommentsForReferenceTitles() {
        val story = LinkSummaryParser.extractHackerNewsItem(
            json = """{"type":"story","title":"The post title","by":"pg"}""",
            pageUrl = "https://news.ycombinator.com/item?id=1",
            fallbackTitle = null,
        )!!
        val comment = LinkSummaryParser.extractHackerNewsItem(
            json = """{"type":"comment","text":"A reply","by":"pg"}""",
            pageUrl = "https://news.ycombinator.com/item?id=2",
            fallbackTitle = null,
        )!!

        assertTrue(LinkSummaryParser.isHackerNewsStory(story))
        assertFalse(LinkSummaryParser.isHackerNewsStory(comment))
    }

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

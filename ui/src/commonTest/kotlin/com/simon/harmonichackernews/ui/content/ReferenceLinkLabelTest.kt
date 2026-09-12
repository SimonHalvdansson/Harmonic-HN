package com.simon.harmonichackernews.ui.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReferenceLinkLabelTest {
    @Test
    fun resolvesHackerNewsYoutubeAndWikipediaTitles() {
        assertTrue(shouldResolveReferenceLinkTitle("https://news.ycombinator.com/item?id=42"))
        assertTrue(shouldResolveReferenceLinkTitle("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(shouldResolveReferenceLinkTitle("https://en.wikipedia.org/wiki/Kotlin"))
        assertFalse(shouldResolveReferenceLinkTitle("https://example.com/article"))
    }

    @Test
    fun malformedSearchTemplateUsesTheFallbackLabelWithoutThrowing() {
        // The query from the story-opening crash log contains an unescaped search-template token.
        val query = "You%20are%20Google%20Search%20from%202004.%20Given%20a%20search%20request," +
            "%20provide%2010%20links%20to%20relevant%20pages,%20each%20with%20a%20short%20text" +
            "%20from%20the%20page,%20featuring%20the%20search%20terms.%20Avoid%20any%20pages" +
            "%20that%20do%20not%20contain%20the%20search%20terms.%20the%20search%20request:%20%s"

        assertFalse(shouldResolveReferenceLinkTitle("https://example.com/search?q=$query"))
        assertFalse(shouldResolveReferenceLinkTitle("https://news.ycombinator.com/item?id=%s"))
    }
}

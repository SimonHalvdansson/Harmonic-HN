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
}

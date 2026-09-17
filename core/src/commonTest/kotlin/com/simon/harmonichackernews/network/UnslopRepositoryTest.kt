package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UnslopRepositoryTest {
    @Test
    fun feedUsesHnGuidsAndPreservesOrderWithoutDuplicates() {
        assertEquals(
            listOf(42, 7),
            UnslopRepository.parseStoryIds(
                """<?xml version="1.0"?>
                    <rss version="2.0"><channel>
                        <item><link>https://example.com/article</link>
                            <guid isPermaLink="true">https://news.ycombinator.com/item?id=42&amp;foo=bar</guid></item>
                        <item><guid><![CDATA[https://news.ycombinator.com/item?id=7]]></guid></item>
                        <item><guid>https://news.ycombinator.com/item?id=42</guid></item>
                        <item><guid>https://example.com/item?id=99</guid></item>
                        <item><guid>https://news.ycombinator.com/item?id=-1</guid></item>
                        <item><guid>https://news.ycombinator.com/item?id=2147483648</guid></item>
                        <item><link>https://news.ycombinator.com/item?id=88</link></item>
                    </channel></rss>
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun emptyFeedIsValidButErrorPagesAndUnusableFeedsFail() {
        assertEquals(emptyList(), UnslopRepository.parseStoryIds("<rss><channel/></rss>"))
        for (body in listOf(
            "",
            "<html><body>Service unavailable</body></html>",
            "<rss><channel><item><guid>invalid</guid></item></channel></rss>",
        )) {
            assertFailsWith<ApiDecodingException> { UnslopRepository.parseStoryIds(body) }
        }
    }

    @Test
    fun storyFeedRoutesRssIdsAndHydratesFromHn() = runTest {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            urls += request.url.toString()
            when (request.url.toString()) {
                UnslopRepository.FEED_URL -> respond(
                    "<rss><channel><item><guid>https://news.ycombinator.com/item?id=42</guid></item></channel></rss>",
                )
                "https://hacker-news.firebaseio.com/v0/item/42.json" -> respond(
                    """{"id":42,"type":"story","title":"An article","by":"author","score":15,"descendants":2,"kids":[43,44]}""",
                )
                else -> error("Unexpected request: ${request.url}")
            }
        })
        try {
            val hn = DefaultHackerNewsRepository(KtorHackerNewsApi(client))
            val feeds = StoryFeedRepository(hn, KtorHackerNewsWebRepository(client), UnslopRepository(client))
            assertEquals(StoryFeedResult.ItemIds(listOf(42)), feeds.load(StoryType.UNSLOP))
            val story = hn.getStory(42)!!
            assertEquals("An article", story.title)
            assertEquals(15, story.score)
            assertEquals(2, story.descendants)
            assertEquals(2, urls.size)
        } finally {
            client.close()
        }
    }

    @Test
    fun httpFailurePropagatesInsteadOfReturningAnEmptyFeed() = runTest {
        val client = HttpClient(MockEngine { respond("Unavailable", HttpStatusCode.ServiceUnavailable) })
        try {
            assertFailsWith<HttpStatusException> { UnslopRepository(client).getStoryIds() }
        } finally {
            client.close()
        }
    }
}

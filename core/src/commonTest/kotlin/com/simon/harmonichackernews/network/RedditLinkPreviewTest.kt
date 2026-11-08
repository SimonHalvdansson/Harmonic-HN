package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedditLinkPreviewTest {
    private val url = "https://old.reddit.com/r/example/comments/abc/title/def/?context=3#fragment"

    @Test
    fun normalizesPostFeedWithoutForwardingTrackingOrCommentPaths() {
        assertEquals("https://www.reddit.com/r/example/comments/abc/title/.rss?limit=1", RedditLinkPreview.feedUrl(url))
        assertEquals("https://www.reddit.com/comments/abc/.rss?limit=1", RedditLinkPreview.feedUrl("https://reddit.com/comments/abc/"))
        assertEquals(
            "https://www.reddit.com/r/comments/comments/abc/title/.rss?limit=1",
            RedditLinkPreview.feedUrl("https://reddit.com/r/comments/comments/abc/title/def/"),
        )
        assertEquals("abc", RedditLinkPreview.postId("https://www.reddit.com/comments/ABC/"))
        assertNull(RedditLinkPreview.feedUrl("https://reddit.com/r/comments/"))
        assertNull(RedditLinkPreview.feedUrl("https://reddit.com/r/example/comments/../"))
        assertNull(RedditLinkPreview.feedUrl("https://reddit.com.evil.test/comments/abc/"))
    }

    @Test
    fun selectsMatchingPostAndDecodesBodyWithoutFeedAttribution() {
        val response = feed("""
            <content type="html">&lt;div class="md"&gt;&lt;p&gt;Hello &amp;amp; welcome.&lt;/p&gt;&lt;p&gt;It&amp;#39;s working.&lt;/p&gt;&lt;/div&gt; submitted by someone [link] [comments]</content>
        """)
        val result = RedditLinkPreview.parseFeed(response, url)
        assertEquals(LinkPreviewType.REDDIT_POST, result.type)
        assertEquals("Post & title", result.title)
        assertEquals("Reddit · r/example", result.subtitle)
        assertEquals("Hello & welcome. It's working.", result.description)
        assertEquals("2026-08-01", result.details.first().displayText)
        assertEquals("u/ada", result.details.last().displayText)
        assertEquals(url, result.url)
    }

    @Test
    fun extractsNamespacedThumbnailAndDecodesItsQuery() {
        val result = RedditLinkPreview.parseFeed(feed("""
            <media:thumbnail url="https://preview.redd.it/image.png?width=320&amp;format=png"/>
        """), url)
        assertEquals("https://preview.redd.it/image.png?width=320&format=png", result.imageUrl)
    }

    @Test
    fun extractsDirectRedditImageWithoutTreatingAttributionAsBody() {
        val result = RedditLinkPreview.parseFeed(feed("""
            <content type="html">&lt;a href="https://i.redd.it/example.jpeg"&gt;[link]&lt;/a&gt; submitted by ada</content>
        """), url)
        assertEquals("https://i.redd.it/example.jpeg", result.imageUrl)
        assertNull(result.description)
    }

    @Test
    fun handlesDeletedAuthorAndBodyAndRejectsNonHttpImages() {
        val result = RedditLinkPreview.parseFeed(feed("""
            <media:thumbnail url="file:///private/image.png"/>
            <content type="html">&lt;div class="md"&gt;[removed]&lt;/div&gt;&lt;img src="javascript:alert(1)"&gt;</content>
        """).replace("<author><name>/u/ada</name></author>", ""), url)
        assertEquals("Reddit · r/example", result.subtitle)
        assertEquals("[removed]", result.description)
        assertNull(result.imageUrl)
    }

    @Test
    fun limitsLongPostBodies() {
        val result = RedditLinkPreview.parseFeed(feed("""
            <content type="html">&lt;div class="md"&gt;${"word ".repeat(500)}&lt;/div&gt;</content>
        """), url)
        assertTrue(result.description!!.length <= 1201)
    }

    @Test
    fun rejectsBlockedPagesAndFeedsWithoutTheRequestedPost() {
        assertFailsWith<LinkPreviewException> { RedditLinkPreview.parseFeed("<html>Blocked</html>", url) }
        assertFailsWith<LinkPreviewException> {
            RedditLinkPreview.parseFeed(feed("").replace("t3_abc", "t3_other"), url)
        }
    }

    @Test
    fun repositoryUsesFeedWithoutRequestingOEmbedOnSuccess() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            respond(feed(""))
        })
        try {
            val result = KtorLinkPreviewRepository(client).load(LinkPreviewType.REDDIT_POST, url) as LinkPreviewData.Rich
            assertEquals("Post & title", result.value.title)
            assertEquals(listOf(RedditLinkPreview.feedUrl(url)), requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun fallsBackForRateLimitsInvalidFeedsAndTimeouts() = runTest {
        for (failure in listOf("rate-limit", "invalid", "timeout")) {
            val paths = mutableListOf<String>()
            val client = HttpClient(MockEngine.create {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    paths += request.url.encodedPath
                    if (request.url.encodedPath == "/oembed") {
                        assertEquals(url, request.url.parameters["url"])
                        respond("""{"title":"Fallback title","author_name":"ada"}""")
                    } else when (failure) {
                        "rate-limit" -> respond("Slow down", HttpStatusCode.TooManyRequests)
                        "timeout" -> {
                            delay(10_000)
                            respond(feed(""))
                        }
                        else -> respond("<html>Blocked</html>")
                    }
                }
            })
            try {
                val result = client.loadRedditPreview(url)
                assertEquals("Fallback title", result.title)
                assertEquals("Reddit · r/example", result.subtitle)
                assertEquals("u/ada", result.details.single().displayText)
                assertEquals(listOf("/r/example/comments/abc/title/.rss", "/oembed"), paths)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun cancellationDoesNotStartFallbackRequest() = runTest {
        val started = CompletableDeferred<Unit>()
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request ->
                paths += request.url.encodedPath
                started.complete(Unit)
                awaitCancellation()
            }
        })
        try {
            val loading = async { client.loadRedditPreview(url) }
            started.await()
            loading.cancelAndJoin()
            assertEquals(listOf("/r/example/comments/abc/title/.rss"), paths)
        } finally {
            client.close()
        }
    }

    private fun feed(content: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
          <title>Feed title</title>
          <entry><id>t1_comment</id><title>Wrong comment</title></entry>
          <entry><id>t3_other</id><title>Wrong post</title></entry>
          <entry>
            <id>t3_abc</id><title>Post &amp; title</title>
            <author><name>/u/ada</name></author><category label="r/example"/>
            <published>2026-08-01T14:57:53+00:00</published>
            $content
          </entry>
        </feed>
    """.trimIndent()
}

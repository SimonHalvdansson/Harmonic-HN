package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class RichLinkPreviewRepositoryTest {
    @Test
    fun substackPreviewUsesRssChannelImageInsteadOfArticleHero() = runTest {
        val articleUrl = "https://writer.substack.com/p/example"
        val articlePage = """
            <html><head>
              <meta property="og:image" content="https://example.com/article-hero.png">
              <script type="application/ld+json">
                {"@type":"NewsArticle","headline":"Example article","publisher":{"name":"Example Publication"}}
              </script>
            </head><body><div class="available-content"><p>Opening paragraph.</p></div></body></html>
        """.trimIndent()
        val feed = """
            <?xml version="1.0"?><rss><channel>
              <title>Example Publication</title>
              <image><url>https://writer.substack.com/img/substack.png</url></image>
              <item><title>Large article body follows</title></item>
            </channel></rss>
        """.trimIndent() + "x".repeat(100_000)
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/feed") {
                respond(feed, headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"))
            } else {
                respond(articlePage, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            }
        })

        val preview = withContext(Dispatchers.Default) {
            client.loadRichLinkPreview(LinkPreviewType.SUBSTACK_ARTICLE, articleUrl)
        }

        assertEquals("https://writer.substack.com/img/substack.png", preview.imageUrl)
        client.close()
    }
}

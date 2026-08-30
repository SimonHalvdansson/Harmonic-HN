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
import kotlin.test.assertIs

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

        val preview = assertIs<LinkPreviewData.Rich>(withContext(Dispatchers.Default) {
            KtorLinkPreviewRepository(client).load(LinkPreviewType.SUBSTACK_ARTICLE, articleUrl)
        })

        assertEquals("https://writer.substack.com/img/substack.png", preview.value.imageUrl)
        client.close()
    }

    @Test
    fun legacyProviderKeepsBespokePayloadAndCompatibilityAccessor() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                """{"name":"harmonic","owner":{"login":"simon"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val repository = KtorLinkPreviewRepository(client)

        val preview = assertIs<LinkPreviewData.GitHub>(withContext(Dispatchers.Default) {
            repository.load(LinkPreviewType.GITHUB_REPOSITORY, "https://github.com/simon/harmonic")
        })

        assertEquals("harmonic", preview.value.name)
        assertEquals(
            "simon",
            withContext(Dispatchers.Default) {
                repository.getGitHubInfo("https://github.com/simon/harmonic")
            }.owner,
        )
        client.close()
    }
}

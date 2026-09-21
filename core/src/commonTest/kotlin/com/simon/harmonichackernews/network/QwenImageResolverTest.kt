package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class QwenImageResolverTest {
    @Test
    fun onlyQwenBlogUrlsWithValidIdsRequestArticleData() {
        val api = QwenImageResolver.requestUrl(NetworkUrl.parse(PAGE))!!.let(NetworkUrl::parse)
        assertEquals("qwen.ai", api.host)
        assertEquals("/api/v2/article/", api.encodedPath)
        assertEquals("qwen-image-2.1", api.queryParameter("path"))
        assertEquals("en-US", api.queryParameter("language"))
        assertEquals("qwen_ai", api.queryParameter("type"))
        for (url in listOf(
            "https://example.com/blog?id=qwen-image-2.1",
            "https://qwen.ai.example.com/blog?id=qwen-image-2.1",
            "https://qwen.ai/home?id=qwen-image-2.1",
            "https://qwen.ai/blog", "https://qwen.ai/blog?id=",
            "https://qwen.ai/blog?id=..%2Fother", "https://qwen.ai/blog?id=a%26path=b",
        )) {
            assertNull(QwenImageResolver.requestUrl(NetworkUrl.parse(url)), url)
        }
    }

    @Test
    fun actualArticleStructureSelectsBannerInsteadOfLogoOrPlaceholderMetadata() {
        assertEquals(BANNER, extract(articleResponse()))
    }

    @Test
    fun coverIsUsedWhenOpeningBannerIsMissingOrUnsafe() {
        for (content in listOf(
            "", "<header><img src='/logo.png'></header>",
            "<article><div class=post-content><p>Introduction</p><figure><img src='/chart.png'></figure></div></article>",
            "<article><div class=post-content><figure><img src='data:image/png;base64,AAAA'></figure></div></article>",
        )) {
            assertEquals(COVER, extract(articleResponse(content = content)))
        }
        assertNull(extract(articleResponse(content = "", cover = "javascript:alert(1)")))
        assertNull(extract(articleResponse(content = "", cover = "")))
    }

    @Test
    fun relativeBannerUsesEmbeddedArticlesCanonicalUrl() {
        assertEquals(
            "https://qwenlm.github.io/blog/qwen-image-2.1/banner.png",
            extract(articleResponse(content = """
                <link rel=canonical href='https://qwenlm.github.io/blog/qwen-image-2.1/'>
                <article><div class=post-content><figure><img src='banner.png'></figure></div></article>
            """)),
        )
    }

    @Test
    fun failedOrMismatchedArticleIsIgnored() {
        for (response in listOf(
            """{"success":false}""", """{"success":true}""",
            articleResponse().replace("qwen-image-2.1", "another-article"),
            articleResponse().replace("qwen_ai", "another_type"),
        )) assertNull(extract(response))
    }

    @Test
    fun repositoryOverridesGenericImageAndPreservesHtmlMetadata() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.toString()
            if (request.url.encodedPath == "/api/v2/article/") {
                respond(articleResponse(), headers = headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond(HTML, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            }
        })
        try {
            val result = KtorLinkSummaryRepository(client).load(PAGE)
            assertEquals(BANNER, result.imageUrl)
            assertEquals("Qwen", result.title)
            assertEquals(PAGE, result.finalUrl)
            assertEquals(2, requests.size)
            assertEquals(PAGE, requests.first())
            assertEquals(QwenImageResolver.requestUrl(NetworkUrl.parse(PAGE)), requests.last())
        } finally {
            client.close()
        }
    }

    @Test
    fun providerFailuresKeepTheHtmlPreview() = runTest {
        for ((body, status) in listOf(
            "unavailable" to HttpStatusCode.ServiceUnavailable,
            "not JSON" to HttpStatusCode.OK,
            articleResponse(content = "", cover = "") to HttpStatusCode.OK,
        )) {
            val client = HttpClient(MockEngine { request ->
                if (request.url.encodedPath == "/api/v2/article/") respond(body, status)
                else respond(HTML, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            })
            try {
                assertEquals(GENERIC, KtorLinkSummaryRepository(client).load(PAGE).imageUrl)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun providerTimeoutKeepsHtmlPreview() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v2/article/") awaitCancellation()
            respond(HTML, headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val repository = KtorLinkSummaryRepository(
                client, parsingDispatcher = StandardTestDispatcher(testScheduler),
            )
            assertEquals(GENERIC, repository.load(PAGE).imageUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun unrelatedSitesDoNotFetchAndCallerCancellationPropagates() = runTest {
        assertNull(SiteImageResolvers.resolve("https://example.com/article") {
            error("Unexpected provider request")
        })
        assertFailsWith<CancellationException> {
            SiteImageResolvers.resolve(PAGE) { throw CancellationException("Caller cancelled") }
        }
    }

    private fun extract(response: String): String? =
        QwenImageResolver.extractImage(response, NetworkUrl.parse(PAGE))

    // Reduced fixture from the public article response, including its broken OG placeholder.
    private fun articleResponse(
        content: String = """
            <meta property='og:image' content='https://qwenlm.github.io/%3Clink%20or%20path%20of%20image%20for%20opengraph,%20twitter-cards%3E'>
            <header><img src='https://qwenlm.github.io/img/logo.png' height=30></header>
            <main><article class=post-single><div class=post-content>
                <figure><img src='$BANNER' alt='Qwen-Image-2.1 banner' width=100%></figure>
                <p>Introduction</p><figure><img src='/chart.png'></figure>
            </div></article></main>
        """,
        cover: String = COVER,
    ): String = JsonObject().put("success", true).put("data", JsonObject()
        .put("path", "qwen-image-2.1").put("type", "qwen_ai")
        .put("content", content).put("extra", JsonObject().put("cover_small", cover)),
    ).toString()

    private companion object {
        const val PAGE = "https://qwen.ai/blog?id=qwen-image-2.1"
        const val BANNER = "https://qianwen-res.oss-accelerate.aliyuncs.com/Qwen-Image/image2.1/banner_en.png"
        const val COVER = "https://img.alicdn.com/imgextra/i1/O1CN01XLHYPGwKvuI29R4a_!!6000000005961-2-tps-1060-636.png"
        const val GENERIC = "https://example.com/qwen-logo.png"
        const val HTML = "<title>Qwen</title><meta property='og:image' content='$GENERIC'>"
    }
}

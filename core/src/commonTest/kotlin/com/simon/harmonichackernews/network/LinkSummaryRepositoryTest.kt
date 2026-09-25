package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.writeFully
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.io.IOException

class LinkSummaryRepositoryTest {
    @Test
    fun arxivReferencesResolveCitationTitlesWithoutDownloadingPdfs() = runTest {
        val cases = listOf(
            "https://arxiv.org/abs/1706.03762" to "1706.03762",
            "https://arxiv.org/pdf/1706.03762v7.pdf#page=2" to "1706.03762v7",
            "https://arxiv.org/html/1706.03762v7?source=hn#S1" to "1706.03762v7",
            "http://arxiv.org/pdf/hep-th/9901001v2.pdf" to "hep-th/9901001v2",
            "https://arxiv.org/abs/math.GT/0309136" to "math.GT/0309136",
        )
        for ((url, id) in cases) {
            var requests = 0
            val client = HttpClient(MockEngine { request ->
                requests++
                assertEquals("https://arxiv.org/abs/$id", request.url.toString())
                respond(
                    """<html><head>
                        <title>[$id] Title with unwanted identifier</title>
                        <meta name="citation_arxiv_id" content="${id.substringBefore('v')}">
                        <meta name="citation_title" content="  Attention &amp; Learning
                          Across Domains  ">
                        <meta name="citation_author" content="Author One">
                        <meta name="citation_author" content="Author Two">
                        <meta name="citation_date" content="2017/06/12">
                        <meta name="citation_abstract" content="Paper summary.">
                    </head></html>""",
                    headers = headersOf(HttpHeaders.ContentType, "text/html"),
                )
            })
            try {
                val summary = KtorLinkSummaryRepository(client).load(url, "Original label")
                assertEquals("Attention & Learning Across Domains", summary.title)
                assertEquals("arXiv", summary.siteName)
                assertEquals("Author One, Author Two", summary.author)
                assertEquals("2017-06-12", summary.publishedTime)
                assertEquals("Paper summary.", summary.description)
                assertEquals(url, summary.finalUrl)
                assertEquals(1, requests)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun arxivErrorPagesAndMissingTitlesDoNotReplaceReferenceLabels() = runTest {
        for (html in listOf(
            "<title>Service unavailable</title>",
            """<meta name="citation_arxiv_id" content="1706.03762">""",
            """<meta name="citation_arxiv_id" content="1706.00001">
                <meta name="citation_title" content="Another paper">""",
        )) {
            val client = HttpClient(MockEngine { respond(html) })
            try {
                assertFailsWith<LinkPreviewException> {
                    KtorLinkSummaryRepository(client).load("https://arxiv.org/abs/1706.03762")
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun loadingAndParsingUseTheConfiguredBackgroundDispatcher() = runTest {
        val dispatcher = RecordingDispatcher(Dispatchers.Default)
        val client = HttpClient(MockEngine {
            respond(
                content = """
                    <html><head>
                      <title>Background parsing</title>
                      <meta name="description" content="Parsed away from the caller dispatcher.">
                    </head><body></body></html>
                """.trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        })

        val result = KtorLinkSummaryRepository(
            client = client,
            parsingDispatcher = dispatcher,
        ).load("https://example.com/article")

        assertEquals("Background parsing", result.title)
        assertTrue(dispatcher.dispatchCount > 0)
        client.close()
    }

    @Test
    fun largeHtmlUsesItsBoundedMetadataPrefix() = runTest {
        val html = buildString {
            append("<html><head><title>Large but previewable</title>")
            append("<meta name=\"description\" content=\"Useful metadata near the start.\">")
            append("</head><body>")
            repeat(2 * 1024 * 1024) { append('x') }
        }
        val client = HttpClient(MockEngine {
            respond(
                content = html,
                headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8"),
            )
        })

        val result = KtorLinkSummaryRepository(client).load("https://example.com/large")

        assertEquals("Large but previewable", result.title)
        assertEquals("Useful metadata near the start.", result.description)
        client.close()
    }

    @Test
    fun directImageBecomesAnImageSummaryInsteadOfAWebPageError() = runTest {
        val imageUrl = "https://cdn.example.com/media/benchmark.png"
        val client = HttpClient(MockEngine {
            respond(
                content = byteArrayOf(1, 2, 3),
                headers = headersOf(HttpHeaders.ContentType, "image/png"),
            )
        })

        val result = KtorLinkSummaryRepository(client).load(imageUrl, imageUrl)

        assertEquals("benchmark.png", result.title)
        assertEquals("cdn.example.com", result.siteName)
        assertEquals("image/png", result.contentType)
        assertEquals(imageUrl, result.imageUrl)
        assertEquals(imageUrl, result.finalUrl)
        client.close()
    }

    @Test
    fun metadataPrefixCompletesWithoutWaitingForTheRestOfTheResponse() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        val prefix = ByteArray(1024 * 1024) { ' '.code.toByte() }
        "<html><head><title>Bounded preview</title></head><body>".encodeToByteArray().copyInto(prefix)
        backgroundScope.launch {
            responseBody.writeFully(prefix)
            // Leave the channel open: waiting for EOF would time out instead of producing a preview.
        }
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load("https://example.com/stream")
            }
            assertEquals("Bounded preview", result.title)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun directImageDoesNotWaitForOrReadItsBody() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val imageUrl = "https://cdn.example.com/large.png"
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler), "image/png")
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load(imageUrl)
            }
            assertEquals(imageUrl, result.imageUrl)
            assertEquals("image/png", result.contentType)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun httpErrorReleasesTheUnreadResponse() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(
            responseBody, StandardTestDispatcher(testScheduler), status = HttpStatusCode.NotFound,
        )
        try {
            val error = assertFailsWith<LinkPreviewException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/missing")
                }
            }
            assertEquals("The page returned HTTP 404", error.message)
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun cancellationWhileReadingReleasesTheResponseAndPropagates() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        try {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/slow")
                }
            }
            assertTrue(responseBody.isClosedForRead)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun smallResponsePreservesUtf8AcrossNetworkChunks() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        val html = "<html><head><title>æ漢😀</title>" +
            "<meta name=\"description\" content=\"Crème brûlée &amp; café\"></head></html>"
        backgroundScope.launch {
            for (byte in html.encodeToByteArray()) {
                responseBody.writeFully(byteArrayOf(byte))
                yield()
            }
            responseBody.close()
        }
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                    .load("https://example.com/unicode")
            }
            assertEquals("æ漢😀", result.title)
            assertEquals("Crème brûlée & café", result.description)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun failedBodyReadDoesNotReturnAPartialPreview() = runTest {
        val responseBody = ByteChannel(autoFlush = true)
        val client = streamingClient(responseBody, StandardTestDispatcher(testScheduler))
        backgroundScope.launch {
            responseBody.writeFully("<title>Incomplete response</title>".encodeToByteArray())
            yield()
            responseBody.cancel(IOException("Connection interrupted"))
        }
        try {
            val error = assertFailsWith<IOException> {
                withTimeout(5_000) {
                    KtorLinkSummaryRepository(client, parsingDispatcher = StandardTestDispatcher(testScheduler))
                        .load("https://example.com/interrupted")
                }
            }
            assertEquals("Connection interrupted", error.message)
        } finally {
            responseBody.cancel()
            client.close()
        }
    }

    @Test
    fun completeHeadStopsBeforeEofAndPreservesRedirectedRelativeImages() = runTest {
        val body = ByteChannel(autoFlush = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val client = HttpClient(MockEngine.create {
            this.dispatcher = dispatcher
            addHandler { request ->
                if (request.url.encodedPath == "/redirect") respond(
                    "", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/article/page"),
                ) else respond(body, headers = headersOf(HttpHeaders.ContentType, "TeXt/HtMl"))
            }
        }) { install(HttpCache) }
        val html = completeHead + "</head>"
        backgroundScope.launch {
            // Exercise split tags, comments, attributes and UTF-8 without ever sending a body/EOF.
            for (byte in html.encodeToByteArray()) {
                body.writeFully(byteArrayOf(byte))
                yield()
            }
        }
        try {
            val result = withTimeout(5_000) {
                KtorLinkSummaryRepository(client, parsingDispatcher = dispatcher)
                    .load("https://example.com/redirect", "Fallback")
            }
            assertEquals(LinkSummaryParser.extract(html, "Fallback", "TeXt/HtMl",
                "https://example.com/article/page"), result)
            assertEquals("https://example.com/article/image.png", result.imageUrl)
            assertEquals("æ漢😀 article", result.title)
            assertTrue(body.isClosedForRead)
        } finally {
            body.cancel()
            client.close()
        }
    }

    @Test
    fun previewQualityMatchesFullParsingForScriptStyleBodyAndMalformedFixtures() = runTest {
        val prose = "This article explains the underlying system with useful examples and detailed context for readers."
        val cases = listOf(
            completeHead + "</head><body>Unneeded body</body></html>",
            completeHead + "<script>var fake = '</head>';/*" + "x".repeat(120_000) +
                "*/</script><style>/* </head> */</style></head><body>Text</body>",
            // Description duplicates the title; keep reading through scripts to find the article.
            completeHead.replace(previewDescription, "æ漢😀 article") +
                "</head><body><script>" + "x".repeat(120_000) +
                "</script><article><p>$prose</p></article></body>",
            // Complete title/image/description does not mean we can discard a body byline/date.
            "<html><head><title>Article</title><meta property=og:image content='/image.png'>" +
                "<meta name=description content='$prose'></head><body>" +
                "<a rel=author>Body author</a><time datetime='2026-01-01'>Today</time></body>",
            "<html><head><title>Missing metadata</title></head><body><article><p>$prose</p></article>",
            completeHead + "<template><head></head></template></head><body><p>Malformed page",
            completeHead + "<script><!--<script></script></head></script>" +
                "<meta property='og:title' content='Later real title'></head><body>Text",
            completeHead.replace("<head>", "<head-custom>") + "</head-custom>" +
                "<meta property='og:title' content='Later real title'><body>Text",
            completeHead + "<script>/* unclosed script </head><body><p>Ambiguous HTML",
            "<title>Implicit head</title><article><p>$prose</p>",
        )
        for (html in cases) {
            val client = HttpClient(MockEngine {
                respond(html, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            })
            try {
                assertEquals(LinkSummaryParser.extract(html, "Fallback", "text/html", "https://example.com/page"),
                    KtorLinkSummaryRepository(client).load("https://example.com/page", "Fallback"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun providerJsonKeepsItsSeparateBudgetBeyondOneMiB() = runTest {
        val json = "{\"padding\":\"${"x".repeat(1200 * 1024)}\",\"title\":\"Provider title\",\"author_name\":\"Author\"}"
        val client = HttpClient(MockEngine {
            respond(json, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        })
        try {
            val result = KtorLinkSummaryRepository(client).load("https://youtu.be/dQw4w9WgXcQ")
            assertEquals("Provider title", result.title)
            assertEquals("Author", result.author)
        } finally {
            client.close()
        }
    }

    private val previewDescription = "A meaningful description with practical examples and detailed context for readers of this article."
    private val completeHead get() = "<html lang='en'><head><!-- fake </head> -->" +
        "<title>æ漢😀 article</title><meta property='og:image' content='image.png'>" +
        "<meta name='author' content='An Author'><meta name='date' content='2026-09-25'>" +
        "<meta name='description' data-quoted='> </head>' content='$previewDescription'>"

    private fun streamingClient(
        responseBody: ByteChannel,
        dispatcher: CoroutineDispatcher,
        contentType: String = "text/html",
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient = HttpClient(MockEngine.create {
        this.dispatcher = dispatcher
        addHandler {
            respond(responseBody, status = status, headers = headersOf(
                HttpHeaders.ContentType to listOf(contentType),
                HttpHeaders.CacheControl to listOf("public, max-age=3600"),
            ))
        }
    }) { install(HttpCache) }

    private class RecordingDispatcher(
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        var dispatchCount: Int = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            delegate.dispatch(context, block)
        }
    }
}

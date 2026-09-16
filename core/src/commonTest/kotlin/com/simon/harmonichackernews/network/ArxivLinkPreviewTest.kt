package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ArxivLinkPreviewTest {
    @Test
    fun publicAbstractPagePreservesMathAuthorsAndCategories() {
        val info = assertNotNull(LinkPreviewParsers.parseArxivAbstractPage(abstractPage, "2609.15979v1"))
        assertEquals("The ${'$'}k${'$'}-server conjecture.", info.arxivAbstract)
        assertEquals(listOf("Christian Coester", "Marek Zbysiński"), info.authors.toList())
        assertEquals("cs.DS", info.primaryCategory)
        assertEquals(listOf("cs.GT"), info.secondaryCategories.toList())
        assertEquals("2026-09-14", info.formatDate())
        assertEquals("https://arxiv.org/pdf/2609.15979v1.pdf", info.pDFURL)
    }

    @Test
    fun rejectsErrorPagesAndMetadataForAnotherPaper() {
        assertNull(LinkPreviewParsers.parseArxivAbstractPage("<html>Try again</html>", "2609.15979"))
        assertNull(LinkPreviewParsers.parseArxivAbstractPage(abstractPage, "2609.14555"))
    }

    @Test
    fun unavailableApiFallsBackToAlreadyRequestedAbstractPage() = runTest {
        var pageRequests = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "export.arxiv.org") {
                respond("Service unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                pageRequests++
                respond(abstractPage)
            }
        })
        try {
            val info = withContext(Dispatchers.Default) {
                client.loadArxivInfo("https://arxiv.org/abs/2609.15979")
            }
            assertEquals("cs.DS", info.primaryCategory)
            assertEquals("https://arxiv.org/html/2609.15979v1", info.htmlUrl)
            assertEquals(1, pageRequests)
        } finally {
            client.close()
        }
    }

    @Test
    fun emptyApiEntryFallsBackToAbstractPage() = runTest {
        val client = HttpClient(MockEngine { request ->
            respond(if (request.url.host == "export.arxiv.org") "<feed><entry/></feed>" else abstractPage)
        })
        try {
            val info = withContext(Dispatchers.Default) {
                client.loadArxivInfo("https://arxiv.org/abs/2609.15979")
            }
            assertEquals("The ${'$'}k${'$'}-server conjecture.", info.arxivAbstract)
        } finally {
            client.close()
        }
    }

    @Test
    fun apiStillWorksWhenAbstractPageIsUnavailable() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "export.arxiv.org") {
                respond("""<feed xmlns:arxiv="http://arxiv.org/schemas/atom"><entry>
                    <summary>API abstract</summary><author><name>Author</name></author>
                    <published>2026-09-14T17:58:11Z</published>
                    <arxiv:primary_category term="cs.DS"/>
                </entry></feed>""")
            } else {
                respond("Unavailable", HttpStatusCode.ServiceUnavailable)
            }
        })
        try {
            val info = withContext(Dispatchers.Default) {
                client.loadArxivInfo("https://arxiv.org/abs/2609.15979")
            }
            assertEquals("API abstract", info.arxivAbstract)
            assertNull(info.htmlUrl)
        } finally {
            client.close()
        }
    }

    @Test
    fun cancellationDoesNotBecomeAFallbackSuccess() = runTest {
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "export.arxiv.org") throw CancellationException("Closed preview")
            respond(abstractPage)
        })
        try {
            assertFailsWith<CancellationException> {
                withContext(Dispatchers.Default) {
                    client.loadArxivInfo("https://arxiv.org/abs/2609.15979")
                }
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun extractsVersionedHtmlArticleUrlFromAbstractPage() {
        val html = """
            <html><body>
              <a href="/pdf/2501.06425">View PDF</a>
              <a href="/html/2501.06425v7">HTML (experimental)</a>
            </body></html>
        """.trimIndent()

        assertEquals(
            "https://arxiv.org/html/2501.06425v7",
            LinkPreviewParsers.parseArxivHtmlUrl(html),
        )
    }

    @Test
    fun returnsNullWhenPaperHasNoHtmlConversion() {
        val html = """
            <html><body><a href="/pdf/0704.0001">View PDF</a></body></html>
        """.trimIndent()

        assertNull(LinkPreviewParsers.parseArxivHtmlUrl(html))
    }

    private val abstractPage = """
        <html><head>
          <meta name="citation_arxiv_id" content="2609.15979">
          <meta name="citation_date" content="2026/09/14">
          <meta name="citation_abstract" content="The ${'$'}k${'$'}-server conjecture.">
        </head><body>
          <div class="authors"><a>Christian Coester</a>, <a>Marek Zbysiński</a></div>
          <table><tr><td class="subjects"><span class="primary-subject">Data Structures (cs.DS)</span>;
            Game Theory (cs.GT)</td></tr></table>
          <a href="/html/2609.15979v1">HTML</a>
        </body></html>
    """.trimIndent()
}

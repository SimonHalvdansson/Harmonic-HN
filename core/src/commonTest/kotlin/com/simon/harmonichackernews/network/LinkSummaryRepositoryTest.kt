package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

class LinkSummaryRepositoryTest {
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

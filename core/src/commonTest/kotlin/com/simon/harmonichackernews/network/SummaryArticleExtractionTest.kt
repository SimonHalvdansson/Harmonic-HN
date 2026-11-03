package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SummaryArticleExtractionTest {
    @Test fun rejectsEmptyOrVerificationPagesInsteadOfSendingThemToTheModel() = runTest {
        for ((html, expected) in listOf(
            "<html><body><script>renderLater()</script></body></html>" to "no readable text",
            "<title>Vercel Security Checkpoint</title><body>We're verifying your browser</body>" to "browser verification",
        )) {
            val client = HttpClient(MockEngine { respond(html, HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "text/html")) })
            try {
                val error = assertFailsWith<CloudSummaryException> {
                    KtorCloudSummaryRepository(KtorHttpClient(client), "test").extractMainContent("https://example.com/article")
                }
                assertTrue(error.message.orEmpty().contains(expected))
            } finally { client.close() }
        }
    }

    @Test fun rateLimitStatusIsPreserved() = runTest {
        val client = HttpClient(MockEngine { respond("Browser verification", HttpStatusCode.TooManyRequests,
            headersOf(HttpHeaders.ContentType, "text/html")) })
        try {
            val error = assertFailsWith<CloudSummaryException> {
                KtorCloudSummaryRepository(KtorHttpClient(client), "test").extractMainContent("https://example.com/article")
            }
            assertTrue(error.message.orEmpty().contains("HTTP 429"))
        } finally { client.close() }
    }
    @Test fun identifiesVercelVerificationSeparatelyFromOrdinaryRateLimiting() = runTest {
        val client = HttpClient(MockEngine { respond("Browser verification", HttpStatusCode.TooManyRequests,
            headersOf("x-vercel-mitigated", "challenge")) })
        try {
            val error = assertFailsWith<CloudSummaryException> {
                KtorCloudSummaryRepository(KtorHttpClient(client), "test").extractMainContent("https://example.com/article")
            }
            assertTrue(error.message.orEmpty().contains("browser verification"))
            assertTrue(error.message.orEmpty().contains("HTTP 429"))
        } finally { client.close() }
    }

}

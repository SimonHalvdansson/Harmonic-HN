package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.HackerNewsAccount
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HackerNewsActionRedirectTest {
    @Test
    fun successfulCommentRedirectIsFollowedAsGet() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine { request ->
            when (++requestCount) {
                1 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/comment", request.url.encodedPath)
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "/item?id=42#43"),
                    )
                }
                else -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/item", request.url.encodedPath)
                    assertEquals("42", request.url.parameters["id"])
                    respond("<html>comment posted</html>")
                }
            }
        })
        try {
            val repository = repository(transport)

            assertIs<HackerNewsActionResult.Success>(
                repository.comment(HackerNewsAccount("tester", "secret"), "42", "Hello"),
            )
            assertEquals(2, requestCount)
        } finally {
            transport.close()
        }
    }

    @Test
    fun crossOriginActionRedirectIsRejectedWithoutFollowingIt() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine {
            requestCount++
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://example.com/item?id=42"),
            )
        })
        try {
            val failure = assertIs<HackerNewsActionResult.Failure>(
                repository(transport).comment(
                    HackerNewsAccount("tester", "secret"),
                    "42",
                    "Hello",
                ),
            )

            assertEquals("Unexpected action redirect", failure.summary)
            assertEquals(HackerNewsActionFailureReason.INDETERMINATE, failure.reason)
            assertEquals(1, requestCount)
        } finally {
            transport.close()
        }
    }

    private fun repository(transport: HttpClient) = KtorHackerNewsActionRepository(
        client = KtorHttpClient(transport),
        cookieClient = KtorHttpClient(transport),
    )
}

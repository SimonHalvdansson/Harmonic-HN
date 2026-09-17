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
import kotlin.test.assertTrue

class HackerNewsLoginRedirectTest {
    @Test
    fun successfulLoginRedirectIsFollowedAsGetWithSessionCookie() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine { request ->
            when (++requestCount) {
                1 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/login", request.url.encodedPath)
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(
                            HttpHeaders.Location to listOf("/submit"),
                            HttpHeaders.SetCookie to listOf(
                                "user=session-token; Secure; HttpOnly",
                            ),
                        ),
                    )
                }
                else -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/submit", request.url.encodedPath)
                    assertTrue(request.headers[HttpHeaders.Cookie].orEmpty().contains("user=session-token"))
                    respond("<html><input name=\"fnid\" value=\"token\"></html>")
                }
            }
        }) {
            installHarmonicHttpCookies()
        }
        try {
            val repository = KtorHackerNewsActionRepository(
                client = KtorHttpClient(transport),
                cookieClient = KtorHttpClient(transport),
            )

            assertIs<HackerNewsActionResult.Success>(
                repository.login(HackerNewsAccount("tester", "secret")),
            )
            assertEquals(2, requestCount)
        } finally {
            transport.close()
        }
    }

    @Test
    fun crossOriginLoginRedirectIsRejectedWithoutFollowingIt() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine {
            requestCount++
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location, "https://example.com/submit"),
            )
        }) {
            installHarmonicHttpCookies()
        }
        try {
            val repository = KtorHackerNewsActionRepository(
                client = KtorHttpClient(transport),
                cookieClient = KtorHttpClient(transport),
            )

            val failure = assertIs<HackerNewsActionResult.Failure>(
                repository.login(HackerNewsAccount("tester", "secret")),
            )
            assertEquals("Unexpected login redirect", failure.summary)
            assertEquals(1, requestCount)
        } finally {
            transport.close()
        }
    }
}

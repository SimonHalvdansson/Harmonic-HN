package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedHttpClientFactoryTest {
    @Test
    fun declaredOversizeCancelsResponseBody() = runTest {
        val body = ByteReadChannel(byteArrayOf(1, 2, 3))
        val client = HttpClient(MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentLength,
                    (DEFAULT_MAX_BUFFERED_BODY_BYTES + 1L).toString(),
                ),
            )
        })

        assertFailsWith<HttpBodyLimitException> {
            client.getTextOrThrow("https://example.com/oversized")
        }

        assertTrue(body.isClosedForRead)
        client.close()
    }

    @Test
    fun streamedOversizeCancelsUnreadRemainder() = runTest {
        val body = ByteReadChannel(ByteArray(DEFAULT_MAX_BUFFERED_BODY_BYTES + 2))
        val client = HttpClient(MockEngine {
            respond(content = body, status = HttpStatusCode.OK)
        })

        assertFailsWith<HttpBodyLimitException> {
            client.getTextOrThrow("https://example.com/streamed-oversize")
        }

        assertTrue(body.isClosedForRead)
        client.close()
    }
}

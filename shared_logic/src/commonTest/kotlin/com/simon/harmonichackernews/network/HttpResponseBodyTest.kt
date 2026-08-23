package com.simon.harmonichackernews.network

import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class HttpResponseBodyTest {
    @Test
    fun rejectsDeclaredBodyLargerThanCallersLimit() = runTest {
        val body = HttpResponseBody(
            ByteReadChannel("hello".encodeToByteArray()),
            headersOf(HttpHeaders.ContentLength, "5"),
        )

        assertFailsWith<HttpBodyLimitException> { body.readText(maxBytes = 4) }
        body.close()
    }

    @Test
    fun rejectsStreamingBodyWhenItCrossesCallersLimit() = runTest {
        val body = HttpResponseBody(
            ByteReadChannel("hello".encodeToByteArray()),
            headersOf(),
        )

        assertFailsWith<HttpBodyLimitException> { body.readBytes(maxBytes = 4) }
        body.close()
    }

    @Test
    fun returnsBodyAtTheExactLimit() = runTest {
        val body = HttpResponseBody(
            ByteReadChannel("hello".encodeToByteArray()),
            headersOf(HttpHeaders.ContentLength, "5"),
        )

        assertEquals("hello", body.readText(maxBytes = 5))
        body.close()
    }
}

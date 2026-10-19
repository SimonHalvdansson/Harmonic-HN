package com.simon.harmonichackernews.network

import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteChannel
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class HttpResponseBodyTest {
    @Test
    fun bufferedBytesTransferOnceWithoutAnotherCopy() = runTest {
        val bytes = "héllo".encodeToByteArray()
        val body = HttpResponseBody(bytes, headersOf())
        assertSame(bytes, body.readBytes(bytes.size))
        assertEquals(0, body.readBytes().size)
        body.close()
    }

    @Test
    fun bufferedBodiesStillEnforceLimitsAndCloseDiscardsUnreadBytes() = runTest {
        for (headers in listOf(headersOf(), headersOf(HttpHeaders.ContentLength, "5"))) {
            val body = HttpResponseBody("hello".encodeToByteArray(), headers)
            assertFailsWith<HttpBodyLimitException> { body.readText(4) }
            assertEquals("hello", body.readText(5))
            body.close()
        }
        val closed = HttpResponseBody("unread".encodeToByteArray(), headersOf())
        closed.close()
        assertEquals("", closed.readText())
    }

    @Test
    fun bufferedBodiesSupportMixedLineChunkAndRemainingTextReads() = runTest {
        val body = HttpResponseBody("line\r\nhéllo".encodeToByteArray(), headersOf())
        assertEquals("line", body.readUtf8Line())
        val prefix = ByteArray(1)
        assertEquals(1, body.readAvailable(prefix))
        assertEquals("h", prefix.decodeToString())
        assertEquals("éllo", body.readText())
        assertEquals(-1, body.readAvailable(prefix))
        body.close()
    }

    @Test
    fun rejectsBodyAlreadyClosedWithReadFailure() = runTest {
        val body = HttpResponseBody(
            ByteChannel().apply { cancel(IOException("truncated body")) },
            headersOf(),
        )
        try {
            assertFailsWith<IOException> { body.readBytes() }
        } finally {
            body.close()
        }
    }

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

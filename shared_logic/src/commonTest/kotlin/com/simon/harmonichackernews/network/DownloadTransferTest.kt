package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

class DownloadTransferTest {
    @Test
    fun rangeTransferPreservesHeadersOffsetExpectedSizeAndProgress() = runTest {
        val body = ByteArrayTransferBody(byteArrayOf(1, 2, 3, 4))
        var receivedRequest: TransferRequest? = null
        val client = TransferClient { request ->
            receivedRequest = request
            FakeTransferResponse(statusCode = 206, body = body, contentLength = 4)
        }
        val sink = RecordingSink()
        val progress = mutableListOf<Long>()

        val receipt = HttpTransferEngine(client).transfer(
            request = TransferRequest(
                url = "https://example.com/model.gguf",
                headers = mapOf("Range" to "bytes=2-"),
            ),
            sink = sink,
            options = TransferOptions(
                maxTotalBytes = 10,
                initialBytes = 2,
                expectedTotalBytes = 6,
                acceptsStatus = { it == 206 },
            ),
            onProgress = { progress += it.bytesWritten },
        )

        assertEquals("bytes=2-", receivedRequest?.headers?.get("Range"))
        assertEquals(4L, receipt.responseBytes)
        assertEquals(6L, receipt.totalBytes)
        assertEquals(listOf(6L), progress)
        assertEquals(listOf<Byte>(1, 2, 3, 4), sink.bytes)
        assertTrue(sink.closed)
        assertFalse(sink.aborted)
    }

    @Test
    fun expectedSizeMismatchAbortsTemporarySink() = runTest {
        val client = TransferClient {
            FakeTransferResponse(
                statusCode = 206,
                body = ByteArrayTransferBody(byteArrayOf(1, 2)),
                contentLength = 2,
            )
        }
        val sink = RecordingSink()

        assertFailsWith<DownloadTransferException> {
            HttpTransferEngine(client).transfer(
                request = TransferRequest("https://example.com/model.gguf"),
                sink = sink,
                options = TransferOptions(
                    maxTotalBytes = 10,
                    initialBytes = 2,
                    expectedTotalBytes = 6,
                ),
            )
        }

        assertTrue(sink.aborted)
        assertFalse(sink.closed)
    }

    @Test
    fun responseAwareTransferCanRestartWhenServerIgnoresRange() = runTest {
        val client = TransferClient {
            FakeTransferResponse(
                statusCode = 200,
                body = ByteArrayTransferBody(byteArrayOf(8, 9)),
                contentLength = 2,
            )
        }
        val replacementSink = RecordingSink()

        val receipt = HttpTransferEngine(client).transfer(
            request = TransferRequest(
                "https://example.com/model.gguf",
                headers = mapOf("Range" to "bytes=20-"),
            ),
            sinkForResponse = { info ->
                assertEquals(200, info.statusCode)
                replacementSink
            },
            optionsForResponse = { info ->
                TransferOptions(
                    maxTotalBytes = 2,
                    initialBytes = if (info.statusCode == 206) 20 else 0,
                    expectedTotalBytes = 2,
                    acceptsStatus = { it == 200 || it == 206 },
                )
            },
        )

        assertEquals(2L, receipt.totalBytes)
        assertEquals(listOf<Byte>(8, 9), replacementSink.bytes)
    }

    @Test
    fun scopedClientStreamsInsideResponseLifetime() = runTest {
        val response = FakeTransferResponse(
            statusCode = 200,
            body = ByteArrayTransferBody(byteArrayOf(4, 5, 6)),
            contentLength = 3,
        )
        var streamed = false
        val client = object : TransferClient, ScopedTransferClient {
            override suspend fun open(request: TransferRequest): TransferResponse =
                error("The buffered response path must not be used")

            override suspend fun <T> withResponse(
                request: TransferRequest,
                block: suspend (TransferResponse) -> T,
            ): T {
                streamed = true
                return block(response)
            }
        }
        val sink = RecordingSink()

        val receipt = HttpTransferEngine(client).transfer(
            request = TransferRequest("https://example.com/model.gguf"),
            sink = sink,
            options = TransferOptions(
                maxTotalBytes = 3,
                expectedTotalBytes = 3,
            ),
        )

        assertTrue(streamed)
        assertEquals(3L, receipt.responseBytes)
        assertEquals(listOf<Byte>(4, 5, 6), sink.bytes)
    }

    @Test
    fun transferUsesOneSinkWriteSession() = runTest {
        val client = TransferClient {
            FakeTransferResponse(
                statusCode = 200,
                body = ByteArrayTransferBody(byteArrayOf(1, 2, 3)),
                contentLength = 3,
            )
        }
        val sink = RecordingSink()

        HttpTransferEngine(client).transfer(
            request = TransferRequest("https://example.com/file"),
            sink = sink,
            options = TransferOptions(maxTotalBytes = 3),
        )

        assertEquals(1, sink.writeSessions)
    }

    @Test
    fun cancellationStillCompletesSuspendingSinkAbort() = runTest {
        val bodyStarted = CompletableDeferred<Unit>()
        val client = TransferClient {
            FakeTransferResponse(
                statusCode = 200,
                body = object : TransferBody {
                    override suspend fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        bodyStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
                contentLength = -1,
            )
        }
        val sink = RecordingSink(suspendOnAbort = true)

        val transfer = launch {
            HttpTransferEngine(client).transfer(
                request = TransferRequest("https://example.com/file"),
                sink = sink,
                options = TransferOptions(maxTotalBytes = 3),
            )
        }
        bodyStarted.await()

        transfer.cancel()
        transfer.join()

        assertTrue(sink.aborted)
    }

    @Test
    fun cachePolicyExpiresOversizedOldAndStaleTemporaryFiles() {
        val policy = DownloadCachePolicy(
            maxFileBytes = 100,
            maxCacheBytes = 200,
            maxFileAgeMillis = 1_000,
            maxTemporaryAgeMillis = 100,
        )

        assertTrue(policy.expired(StoredDownload("empty", 0, 10_000), 10_000))
        assertTrue(policy.expired(StoredDownload("large", 101, 10_000), 10_000))
        assertTrue(policy.expired(StoredDownload("old", 50, 8_999), 10_000))
        assertTrue(policy.expired(StoredDownload("temp", 1, 9_899, temporary = true), 10_000))
        assertFalse(policy.expired(StoredDownload("fresh", 50, 9_500), 10_000))
    }

    private class ByteArrayTransferBody(private val source: ByteArray) : TransferBody {
        private var consumed = false
        override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (consumed) return -1
            val count = minOf(length, source.size)
            source.copyInto(buffer, offset, 0, count)
            consumed = true
            return count
        }
    }

    private class FakeTransferResponse(
        override val statusCode: Int,
        override val body: TransferBody,
        override val contentLength: Long,
    ) : TransferResponse {
        override val statusMessage: String = "Test"
        override val contentType: HttpMediaType? = HttpMediaType("application/octet-stream")
        override fun close() = Unit
    }

    private class RecordingSink(
        private val suspendOnAbort: Boolean = false,
    ) : DownloadSink {
        override val reference: String = "temporary"
        val bytes = mutableListOf<Byte>()
        var closed = false
        var aborted = false
        var writeSessions = 0

        override suspend fun <T> writeSession(block: suspend () -> T): T {
            writeSessions++
            return block()
        }

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) {
            repeat(length) { bytes += buffer[offset + it] }
        }

        override suspend fun close() {
            closed = true
        }

        override suspend fun abort() {
            if (suspendOnAbort) yield()
            aborted = true
        }
    }
}

package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

data class DownloadMetadata(
    val sourceUrl: String,
    val contentType: String?,
)

data class StoredDownload(
    val reference: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val temporary: Boolean = false,
)

interface DownloadSink {
    val reference: String

    /**
     * Runs a complete sequence of writes in the sink's preferred context. Filesystem-backed sinks
     * use this to pay for dispatcher selection once per transfer rather than once per buffer.
     */
    suspend fun <T> writeSession(block: suspend () -> T): T = block()

    suspend fun write(buffer: ByteArray, offset: Int, length: Int)
    suspend fun close()
    suspend fun abort()
}

/** Storage boundary for cached downloads; the default implementation is shared filesystem code. */
interface DownloadStore {
    suspend fun prepare(): Boolean
    suspend fun find(key: String): StoredDownload?
    suspend fun createTemporary(key: String): DownloadSink
    suspend fun commit(
        temporaryReference: String,
        key: String,
        metadata: DownloadMetadata,
        nowMillis: Long,
    ): StoredDownload
    suspend fun list(): List<StoredDownload>
    suspend fun touch(reference: String, nowMillis: Long)
    suspend fun remove(reference: String): Boolean
}

interface TransferBody {
    suspend fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int
}

data class TransferRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        fun accepting(url: String, accept: String): TransferRequest = TransferRequest(
            url = url,
            headers = mapOf("Accept" to accept),
        )
    }
}

interface TransferResponse {
    val statusCode: Int
    val statusMessage: String
    val contentLength: Long
    val contentType: HttpMediaType?
    val body: TransferBody
    fun close()
}

fun interface TransferClient {
    suspend fun open(request: TransferRequest): TransferResponse
}

/** Optional scoped response path used by clients that can stream without saving the body. */
interface ScopedTransferClient {
    suspend fun <T> withResponse(
        request: TransferRequest,
        block: suspend (TransferResponse) -> T,
    ): T
}

class KtorTransferClient(
    private val client: KtorHttpClient,
) : TransferClient, ScopedTransferClient {
    override suspend fun open(request: TransferRequest): TransferResponse {
        val response = client.execute(
            request.toHttpRequest(),
        )
        return response.asTransferResponse()
    }

    override suspend fun <T> withResponse(
        request: TransferRequest,
        block: suspend (TransferResponse) -> T,
    ): T {
        return client.executeStreaming(request.toHttpRequest()) { response ->
            block(response.asTransferResponse())
        }
    }

    private fun TransferRequest.toHttpRequest(): HttpRequest {
        val requestBuilder = HttpRequest.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        return requestBuilder.build()
    }

    private fun HttpResponse.asTransferResponse(): TransferResponse =
        object : TransferResponse {
            override val statusCode: Int get() = code
            override val statusMessage: String get() = message
            override val contentLength: Long get() = this@asTransferResponse.body.contentLength()
            override val contentType: HttpMediaType? get() =
                this@asTransferResponse.body.contentType()
            override val body: TransferBody = object : TransferBody {
                override suspend fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    this@asTransferResponse.body.readAvailable(buffer, offset, length)
            }

            override fun close() = this@asTransferResponse.close()
        }
}

data class TransferOptions(
    val maxTotalBytes: Long,
    val initialBytes: Long = 0L,
    val expectedTotalBytes: Long? = null,
    val requireResponseBytes: Boolean = true,
    val acceptsStatus: (Int) -> Boolean = { it in 200..299 },
    val acceptsContentType: (HttpMediaType?) -> Boolean = { true },
) {
    init {
        require(maxTotalBytes > 0L)
        require(initialBytes >= 0L)
        require(expectedTotalBytes == null || expectedTotalBytes >= initialBytes)
    }
}

data class TransferProgress(
    val bytesWritten: Long,
    val expectedTotalBytes: Long?,
)

data class TransferReceipt(
    val metadata: DownloadMetadata,
    val responseBytes: Long,
    val totalBytes: Long,
    val statusCode: Int,
)

data class TransferResponseInfo(
    val statusCode: Int,
    val statusMessage: String,
    val contentLength: Long,
    val contentType: HttpMediaType?,
)

/**
 * Streams a response into a platform sink without buffering the payload in memory.
 *
 * Arbitrary headers, byte offsets, status validation and expected-size checks also support
 * resumable `Range` transfers such as local-model downloads.
 */
class HttpTransferEngine(
    private val client: TransferClient,
) {
    suspend fun transfer(
        request: TransferRequest,
        sink: DownloadSink,
        options: TransferOptions,
        onProgress: suspend (TransferProgress) -> Unit = {},
    ): TransferReceipt = transfer(
        request = request,
        sinkForResponse = { sink },
        optionsForResponse = { options },
        onProgress = onProgress,
    )

    /**
     * Response-aware variant for resumable downloads. A caller can append on HTTP 206, or truncate
     * and restart on HTTP 200 when a server ignores the supplied Range header.
     */
    suspend fun transfer(
        request: TransferRequest,
        sinkForResponse: suspend (TransferResponseInfo) -> DownloadSink,
        optionsForResponse: (TransferResponseInfo) -> TransferOptions,
        onProgress: suspend (TransferProgress) -> Unit = {},
    ): TransferReceipt = if (client is ScopedTransferClient) {
        client.withResponse(request) { response ->
            consumeResponse(
                response = response,
                request = request,
                sinkForResponse = sinkForResponse,
                optionsForResponse = optionsForResponse,
                onProgress = onProgress,
            )
        }
    } else {
        val response = client.open(request)
        try {
            consumeResponse(
                response = response,
                request = request,
                sinkForResponse = sinkForResponse,
                optionsForResponse = optionsForResponse,
                onProgress = onProgress,
            )
        } finally {
            response.close()
        }
    }

    private suspend fun consumeResponse(
        response: TransferResponse,
        request: TransferRequest,
        sinkForResponse: suspend (TransferResponseInfo) -> DownloadSink,
        optionsForResponse: (TransferResponseInfo) -> TransferOptions,
        onProgress: suspend (TransferProgress) -> Unit,
    ): TransferReceipt {
        var sink: DownloadSink? = null
        try {
            val responseInfo = TransferResponseInfo(
                statusCode = response.statusCode,
                statusMessage = response.statusMessage,
                contentLength = response.contentLength,
                contentType = response.contentType,
            )
            val options = optionsForResponse(responseInfo)
            if (!options.acceptsStatus(response.statusCode)) {
                throw DownloadTransferException(
                    "Server returned HTTP ${response.statusCode} ${response.statusMessage}",
                )
            }
            if (!options.acceptsContentType(response.contentType)) {
                throw DownloadTransferException("Response had an unsupported content type")
            }
            if (response.contentLength >= 0L &&
                options.initialBytes + response.contentLength > options.maxTotalBytes
            ) {
                throw DownloadTransferException("Response exceeds the download limit")
            }
            val activeSink = sinkForResponse(responseInfo)
            sink = activeSink

            var responseBytes = 0L
            var totalBytes = options.initialBytes
            activeSink.writeSession {
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                while (true) {
                    val read = response.body.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue
                    responseBytes += read
                    totalBytes += read
                    if (totalBytes > options.maxTotalBytes) {
                        throw DownloadTransferException("Response exceeds the download limit")
                    }
                    activeSink.write(buffer, 0, read)
                    onProgress(TransferProgress(totalBytes, options.expectedTotalBytes))
                }
            }
            if (options.requireResponseBytes && responseBytes == 0L) {
                throw DownloadTransferException("Response was empty")
            }
            options.expectedTotalBytes?.let { expected ->
                if (totalBytes != expected) {
                    throw DownloadTransferException(
                        "Downloaded $totalBytes bytes but expected $expected",
                    )
                }
            }
            activeSink.close()
            return TransferReceipt(
                metadata = DownloadMetadata(request.url, response.contentType?.toString()),
                responseBytes = responseBytes,
                totalBytes = totalBytes,
                statusCode = response.statusCode,
            )
        } catch (error: CancellationException) {
            abortIgnoringFailure(sink)
            throw error
        } catch (error: Throwable) {
            abortIgnoringFailure(sink)
            throw error as? DownloadTransferException
                ?: DownloadTransferException(error.message ?: "Transfer failed", error)
        }
    }

    private suspend fun abortIgnoringFailure(sink: DownloadSink?) {
        if (sink == null) return
        withContext(NonCancellable) { runCatching { sink.abort() } }
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 256 * 1024
    }
}

data class DownloadCachePolicy(
    val maxFileBytes: Long,
    val maxCacheBytes: Long,
    val maxFileAgeMillis: Long? = null,
    val maxTemporaryAgeMillis: Long,
) {
    init {
        require(maxFileBytes > 0)
        require(maxCacheBytes >= maxFileBytes)
        require(maxTemporaryAgeMillis >= 0)
    }

    fun expired(entry: StoredDownload, nowMillis: Long): Boolean {
        val age = (nowMillis - entry.lastModifiedMillis).coerceAtLeast(0L)
        return if (entry.temporary) {
            age > maxTemporaryAgeMillis
        } else {
            entry.sizeBytes <= 0L ||
                entry.sizeBytes > maxFileBytes ||
                maxFileAgeMillis?.let { age > it } == true
        }
    }
}

class DownloadTransferException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** Suspend-first bounded transfer plus reusable LRU cache policy. */
class CachedDownloadService(
    client: TransferClient,
    private val store: DownloadStore,
    private val policy: DownloadCachePolicy,
    private val cacheLabel: String,
) {
    private val storeMutex = Mutex()
    private val transferEngine = HttpTransferEngine(client)

    suspend fun download(
        url: String,
        accept: String,
        key: String = url,
        nowMillis: Long,
        reuseExisting: Boolean = true,
        acceptsContentType: (HttpMediaType?) -> Boolean = { true },
    ): StoredDownload {
        require(url.isNotBlank()) { "A download URL is required" }
        val cached = storeMutex.withLock {
            if (!store.prepare()) throw DownloadTransferException("$cacheLabel cache is unavailable")
            val existing = store.find(key)
            cleanupLocked(nowMillis, existing?.reference)
            when {
                reuseExisting && existing != null && !policy.expired(existing, nowMillis) -> {
                    store.touch(existing.reference, nowMillis)
                    existing.copy(lastModifiedMillis = nowMillis)
                }
                reuseExisting && existing != null -> {
                    store.remove(existing.reference)
                    null
                }
                else -> null
            }
        }
        if (cached != null) return cached

        val sink = storeMutex.withLock { store.createTemporary(key) }
        try {
            val receipt = transferEngine.transfer(
                request = TransferRequest.accepting(url, accept),
                sink = sink,
                options = TransferOptions(
                    maxTotalBytes = policy.maxFileBytes,
                    acceptsContentType = acceptsContentType,
                ),
            )
            return storeMutex.withLock {
                val committed = store.commit(
                    temporaryReference = sink.reference,
                    key = key,
                    metadata = receipt.metadata,
                    nowMillis = nowMillis,
                )
                cleanupLocked(nowMillis, committed.reference)
                committed
            }
        } catch (error: CancellationException) {
            removeTemporaryIgnoringFailure(sink.reference)
            throw error
        } catch (error: Throwable) {
            removeTemporaryIgnoringFailure(sink.reference)
            if (error !is DownloadTransferException) {
                throw DownloadTransferException("$cacheLabel download failed", error)
            }
            throw DownloadTransferException("$cacheLabel: ${error.message}", error)
        }
    }

    suspend fun cleanup(nowMillis: Long) = storeMutex.withLock {
        if (store.prepare()) cleanupLocked(nowMillis, protectedReference = null)
    }

    private suspend fun removeTemporaryIgnoringFailure(reference: String) {
        withContext(NonCancellable) {
            runCatching { storeMutex.withLock { store.remove(reference) } }
        }
    }

    private suspend fun cleanupLocked(nowMillis: Long, protectedReference: String?) {
        val retained = mutableListOf<StoredDownload>()
        store.list().forEach { entry ->
            if (entry.reference != protectedReference && policy.expired(entry, nowMillis)) {
                store.remove(entry.reference)
            } else if (!entry.temporary) {
                retained += entry
            }
        }

        var totalBytes = retained.sumOf(StoredDownload::sizeBytes)
        retained.sortedBy(StoredDownload::lastModifiedMillis).forEach { entry ->
            if (totalBytes <= policy.maxCacheBytes) return@forEach
            if (entry.reference == protectedReference) return@forEach
            if (store.remove(entry.reference)) totalBytes -= entry.sizeBytes
        }
    }

}

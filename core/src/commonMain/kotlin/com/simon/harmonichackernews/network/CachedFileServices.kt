package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException

/** Shared policy and transfer workflow for PDFs opened by the embedded article viewer. */
class PdfDownloadService(
    httpClient: KtorHttpClient,
    store: DownloadStore?,
    private val nowMillis: () -> Long,
) {
    private val downloads = store?.let {
        CachedDownloadService(
            client = KtorTransferClient(httpClient),
            store = it,
            policy = DownloadCachePolicy(
                maxFileBytes = MAX_CACHE_BYTES,
                maxCacheBytes = MAX_CACHE_BYTES,
                maxFileAgeMillis = MAX_FILE_AGE_MILLIS,
                maxTemporaryAgeMillis = MAX_TEMPORARY_AGE_MILLIS,
            ),
            cacheLabel = "PDF",
        )
    }

    val supported: Boolean get() = downloads != null

    suspend fun download(url: String?): String {
        val target = url?.takeIf(String::isNotBlank)
            ?: throw DownloadTransferException("A PDF URL is required")
        val service = downloads ?: throw DownloadTransferException("PDF cache is unavailable")
        return service.download(
            url = target,
            accept = PDF_CONTENT_TYPE,
            nowMillis = nowMillis(),
            acceptsContentType = { type ->
                type == null ||
                    (type.type.equals("application", true) && type.subtype.equals("pdf", true))
            },
        ).reference
    }

    private companion object {
        const val PDF_CONTENT_TYPE = "application/pdf"
        const val MAX_CACHE_BYTES = 250L * 1_024L * 1_024L
        const val MAX_FILE_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        const val MAX_TEMPORARY_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

/** Platform filesystem boundary used by the common resumable model-transfer algorithm. */
interface ResumableDownloadDestination {
    suspend fun prepare(): Boolean
    suspend fun completedBytes(): Long
    suspend fun partialBytes(): Long
    suspend fun removeCompleted(): Boolean
    suspend fun removePartial(): Boolean
    suspend fun openPartial(append: Boolean): DownloadSink
    suspend fun promotePartial(): Boolean
}

data class ResumableDownloadReceipt(
    val resumedFromBytes: Long,
    val alreadyComplete: Boolean,
)

/** Shared validation, Range negotiation, restart, progress and promotion workflow. */
class ResumableDownloadService(
    client: TransferClient,
) {
    private val engine = HttpTransferEngine(client)

    suspend fun download(
        url: String,
        expectedBytes: Long,
        destination: ResumableDownloadDestination,
        onProgress: suspend (TransferProgress) -> Unit = {},
    ): ResumableDownloadReceipt {
        require(url.isNotBlank()) { "A download URL is required" }
        require(expectedBytes > 0L) { "Expected download size must be positive" }
        if (!destination.prepare()) throw DownloadTransferException("Could not create model storage")

        val completed = destination.completedBytes()
        if (completed == expectedBytes) {
            return ResumableDownloadReceipt(expectedBytes, alreadyComplete = true)
        }
        if (completed > 0L && !destination.removeCompleted()) {
            throw DownloadTransferException("Could not replace the incomplete model")
        }

        val partial = destination.partialBytes()
        if (partial > expectedBytes && !destination.removePartial()) {
            throw DownloadTransferException("Could not replace the invalid partial download")
        }
        if (partial == expectedBytes) {
            if (!destination.promotePartial()) {
                throw DownloadTransferException("Could not finish installing the model")
            }
            onProgress(TransferProgress(expectedBytes, expectedBytes))
            return ResumableDownloadReceipt(expectedBytes, alreadyComplete = false)
        }

        val resumeFrom = destination.partialBytes()
        val headers = buildMap {
            put("Accept-Encoding", "identity")
            if (resumeFrom > 0L) put("Range", "bytes=$resumeFrom-")
        }
        try {
            engine.transfer(
                request = TransferRequest(url, headers),
                sinkForResponse = { response ->
                    destination.openPartial(
                        append = response.statusCode == HTTP_PARTIAL && resumeFrom > 0L,
                    )
                },
                optionsForResponse = { response ->
                    val resumed = response.statusCode == HTTP_PARTIAL && resumeFrom > 0L
                    TransferOptions(
                        initialBytes = if (resumed) resumeFrom else 0L,
                        expectedTotalBytes = expectedBytes,
                        maxTotalBytes = expectedBytes,
                        acceptsStatus = { it == HTTP_OK || it == HTTP_PARTIAL },
                    )
                },
                onProgress = onProgress,
            )
        } catch (error: CancellationException) {
            throw error
        }

        val actual = destination.partialBytes()
        if (actual != expectedBytes) {
            throw DownloadTransferException(
                "Downloaded model size was $actual bytes; expected $expectedBytes",
            )
        }
        if (!destination.promotePartial()) {
            throw DownloadTransferException("Could not finish installing the model")
        }
        onProgress(TransferProgress(expectedBytes, expectedBytes))
        return ResumableDownloadReceipt(resumeFrom, alreadyComplete = false)
    }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_PARTIAL = 206
    }
}

package com.simon.harmonichackernews

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

internal fun isTrustedPdfViewerUrl(url: String?, viewerUrl: String): Boolean {
    if (url == null) return false
    val suffixStart = url.indexOfAny(charArrayOf('?', '#'))
    val documentUrl = if (suffixStart >= 0) url.substring(0, suffixStart) else url
    return documentUrl == viewerUrl
}

/** Bounded, synchronized file access for the local PDF viewer JavaScript bridge. */
internal class PdfFileChunkReader(
    private val file: File,
    private val maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES,
) : Closeable {
    private var randomAccessFile: RandomAccessFile? = null
    private var closed = false

    init {
        require(maxChunkBytes > 0) { "maxChunkBytes must be positive" }
    }

    @Synchronized
    fun size(): Long {
        if (closed) return 0L
        return openFile().length()
    }

    @Synchronized
    fun read(begin: Long, end: Long): ByteArray? {
        if (closed || begin < 0L || end <= begin) return null
        val length = end - begin
        if (length <= 0L || length > maxChunkBytes.toLong() || length > Int.MAX_VALUE) return null

        val source = openFile()
        if (end > source.length()) return null

        return ByteArray(length.toInt()).also { bytes ->
            source.seek(begin)
            source.readFully(bytes)
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        randomAccessFile?.close()
        randomAccessFile = null
    }

    private fun openFile(): RandomAccessFile =
        randomAccessFile ?: RandomAccessFile(file, "r").also { randomAccessFile = it }

    internal companion object {
        // PDF.js currently requests 256 KiB ranges. Leave headroom without allowing arbitrary allocation.
        const val DEFAULT_MAX_CHUNK_BYTES = 1024 * 1024
    }
}

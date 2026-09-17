package com.simon.harmonichackernews.platform

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TextDocumentTooLargeException(
    maxChars: Int,
) : IOException("Text document exceeds the $maxChars character import limit")

internal fun Reader.readBoundedText(maxChars: Int): String {
    require(maxChars >= 0) { "maxChars must not be negative" }
    val result = StringBuilder(minOf(maxChars, READ_BUFFER_CHARS))
    val buffer = CharArray(READ_BUFFER_CHARS)
    while (true) {
        val readCount = read(buffer)
        if (readCount < 0) break
        if (readCount == 0) {
            val next = read()
            if (next < 0) break
            if (result.length == maxChars) throw TextDocumentTooLargeException(maxChars)
            result.append(next.toChar())
            continue
        }
        if (readCount > maxChars - result.length) {
            throw TextDocumentTooLargeException(maxChars)
        }
        result.append(buffer, 0, readCount)
    }
    return result.toString()
}

/** Android Storage Access Framework bridge for plain-text import and export documents. */
object AndroidTextDocuments {
    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val outputStream = checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) {
            "Unable to open the selected text document for writing"
        }
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(text) }
    }

    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri)) {
            "Unable to open the selected text document for reading"
        }
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readBoundedText(MAX_IMPORTED_TEXT_CHARS)
        }
    }

    internal const val MAX_IMPORTED_TEXT_CHARS = 4 * 1024 * 1024
}

private const val READ_BUFFER_CHARS = 8 * 1024

package com.simon.harmonichackernews.platform

import android.content.Context
import android.net.Uri

/** Android Storage Access Framework bridge for plain-text import and export documents. */
object AndroidTextDocuments {
    fun write(context: Context, uri: Uri, text: String) {
        val outputStream = checkNotNull(context.contentResolver.openOutputStream(uri))
        outputStream.bufferedWriter().use { writer -> writer.write(text) }
    }

    fun read(context: Context, uri: Uri): String {
        val inputStream = checkNotNull(context.contentResolver.openInputStream(uri))
        return inputStream.bufferedReader().use { reader ->
            buildString { reader.forEachLine(::append) }
        }
    }
}

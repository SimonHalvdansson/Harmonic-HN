package com.simon.harmonichackernews.platform

/** Native document selection; the host owns picker presentation and temporary file cleanup. */
interface TextDocumentService {
    fun importText(callback: TextDocumentCallback)
    fun exportText(filename: String, content: String, callback: TextDocumentCallback)
}

interface TextDocumentCallback {
    /** Null values mean cancellation. Successful exports return an empty content string. */
    fun complete(content: String?, errorMessage: String?)
}

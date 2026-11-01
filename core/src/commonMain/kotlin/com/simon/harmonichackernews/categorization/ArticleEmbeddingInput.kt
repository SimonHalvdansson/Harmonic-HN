package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.network.HtmlDescriptionExtractor

/** Shared by on-device inference and the JVM corpus generator. */
object ArticleEmbeddingInput {
    const val VERSION = "document-preview-v2"

    fun headline(title: String): String = clean(title).take(300)

    fun details(title: String, description: String?): String {
        val text = clean(description.orEmpty()).take(600)
        // A login prompt or a repeated title adds no topic evidence.
        return text.takeIf { HtmlDescriptionExtractor.isMeaningful(it, title, title) }.orEmpty()
    }

    fun format(title: String, description: String?): String {
        val headline = headline(title)
        val details = details(headline, description)
        return "title: $headline | text: ${details.ifBlank { headline }}"
    }

    private fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}

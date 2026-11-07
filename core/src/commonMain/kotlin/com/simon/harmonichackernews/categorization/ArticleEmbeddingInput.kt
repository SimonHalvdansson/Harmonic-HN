package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import kotlin.coroutines.cancellation.CancellationException

/** Shared by on-device inference and the JVM corpus generator. */
object ArticleEmbeddingInput {
    const val VERSION = "document-preview-v2"
    const val DEFAULT_MAX_INPUT_TOKENS = 256
    val TOKEN_LIMITS = listOf(128, 256, 512, 1024)

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

    /**
     * LiteRT-LM 0.19's Kotlin binding does not expose its native token-truncation option.
     * Only retry its explicit pre-inference overflow error, using the reported token count
     * to shorten the text. Character counts alone cannot bound multilingual token counts.
     */
    internal suspend fun embedWithinLimit(input: String, maxInputTokens: Int, embed: suspend (String) -> FloatArray): FloatArray {
        var text = input
        while (true) {
            try {
                return embed(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val overflow = overflowPattern.find(error.message.orEmpty()) ?: throw error
                val count = overflow.groupValues[1].toIntOrNull() ?: throw error
                val limit = overflow.groupValues[2].toIntOrNull() ?: throw error
                if (limit != maxInputTokens || count <= limit || text.length <= 1) throw error
                // Leave room for special tokens and tokenization changes at the cut boundary.
                var end = (text.length.toLong() * (limit - 8) / count).toInt().coerceIn(1, text.length - 1)
                if (text[end - 1].isHighSurrogate()) end--
                if (end == 0) throw error
                text = text.take(end).trimEnd()
            }
        }
    }

    private val overflowPattern = Regex("Input sequence length \\((\\d+)\\) exceeds maximum supported signature length \\((\\d+)\\)")
    private fun clean(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}

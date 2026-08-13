package com.simon.harmonichackernews.summary

import kotlin.math.max
import kotlin.math.min

data class LocalSummaryInput(
    val text: String,
    val contextTokens: Int,
)

object LocalSummaryPreparation {
    const val MINIMUM_ARTICLE_CHARS = 400
    const val SYSTEM_INSTRUCTION =
        "Summarize the article as a concise, information-dense bullet-point list. " +
            "Focus on the key takeaways and noteworthy facts. Keep the entire summary under 500 " +
            "characters where possible. Return only the summary in Markdown, with no preamble."

    fun prepare(
        text: String,
        modelContextTokens: Int,
        totalMemoryBytes: Long,
    ): LocalSummaryInput {
        val lowMemory = totalMemoryBytes in 1 until LOW_MEMORY_THRESHOLD_BYTES
        val contextTokens = if (lowMemory) {
            min(modelContextTokens, LOW_MEMORY_MAX_CONTEXT_TOKENS)
        } else {
            modelContextTokens
        }
        val contextWordBudget = max(
            MINIMUM_WORD_BUDGET,
            (contextTokens - CONTEXT_OUTPUT_HEADROOM_TOKENS) / ESTIMATED_TOKENS_PER_WORD,
        )
        val preferredMaxWords = if (lowMemory) LOW_MEMORY_MAX_WORDS else DEFAULT_MAX_WORDS
        return LocalSummaryInput(
            text = truncateWords(text, min(preferredMaxWords, contextWordBudget)),
            contextTokens = contextTokens,
        )
    }

    /** Normalization used by managed on-device summarizers whose token budget is host-controlled. */
    fun prepareManagedText(text: String, maximumWords: Int = 3_000): String =
        truncateWords(text, maximumWords)

    fun isLongEnough(text: String): Boolean = text.length >= MINIMUM_ARTICLE_CHARS

    private fun truncateWords(text: String, maximumWords: Int): String {
        val trimmed = text.trim()
        val words = trimmed.split(whitespace)
        return if (words.size <= maximumWords) trimmed else words.take(maximumWords).joinToString(" ")
    }

    private val whitespace = Regex("\\s+")
    private const val LOW_MEMORY_THRESHOLD_BYTES = 8L * 1024L * 1024L * 1024L
    private const val LOW_MEMORY_MAX_WORDS = 500
    private const val DEFAULT_MAX_WORDS = 1500
    private const val LOW_MEMORY_MAX_CONTEXT_TOKENS = 2048
    private const val CONTEXT_OUTPUT_HEADROOM_TOKENS = 512
    private const val ESTIMATED_TOKENS_PER_WORD = 2
    private const val MINIMUM_WORD_BUDGET = 250
}

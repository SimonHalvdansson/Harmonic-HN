package com.simon.harmonichackernews.summary

import kotlinx.coroutines.CancellationException

/** Admission and overflow recovery for Apple's small, system-managed context window. */
object AppleSummaryPromptBudget {
    const val CONTEXT_LIMIT_MESSAGE =
        "This article or prompt is too long for Apple Intelligence. Try a shorter prompt or another model."
    const val MAX_OUTPUT_TOKENS = 256
    const val CONTEXT_HEADROOM_TOKENS = MAX_OUTPUT_TOKENS + 256

    suspend fun <T> generate(
        text: String,
        preserveInput: Boolean,
        generateAttempt: suspend (String) -> T,
    ): T {
        // A word limit alone cannot bound code, long URLs, or languages without spaces.
        // Native token counting (when available) and overflow recovery remain authoritative.
        var candidate = if (preserveInput) text.trim() else prefix(text.trim(), 6_000)
        while (true) {
            try {
                return generateAttempt(candidate)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.message != CONTEXT_LIMIT_MESSAGE || preserveInput ||
                    candidate.length <= LocalSummaryPreparation.MINIMUM_ARTICLE_CHARS) throw error
                // Each attempt gets a fresh native session. Never truncate an Ask payload.
                candidate = prefix(candidate, maxOf(
                    LocalSummaryPreparation.MINIMUM_ARTICLE_CHARS, candidate.length / 2,
                ))
            }
        }
    }

    private fun prefix(text: String, maximumCharacters: Int): String {
        var end = minOf(text.length, maximumCharacters)
        if (end > 0 && text[end - 1].isHighSurrogate()) end--
        return text.take(end).trimEnd()
    }
}

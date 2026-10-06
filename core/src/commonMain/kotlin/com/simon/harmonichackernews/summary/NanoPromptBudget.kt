package com.simon.harmonichackernews.summary

/** ML Kit counts the entire request, including system instructions, with its own tokenizer. */
object NanoPromptBudget {
    data class Input(val text: String, val tokens: Int, val outputTokens: Int)

    suspend fun fit(
        text: String,
        totalTokenLimit: Int,
        preserveInput: Boolean,
        countRequestTokens: suspend (String) -> Int,
    ): Input {
        // Prompt API accepts fewer than 4000 input tokens. Leave room for an answer/thinking.
        val inputLimit = minOf(3999, totalTokenLimit - 1024)
        var candidate = text
        while (true) {
            val tokens = countRequestTokens(candidate)
            if (tokens <= inputLimit) {
                return Input(candidate, tokens, minOf(4096, totalTokenLimit - tokens))
            }
            require(!preserveInput && candidate.isNotEmpty()) {
                "Input token count exceeds the model's context limit ($tokens tokens; $inputLimit available)."
            }
            // Article summarization may use a prefix. Never cut an Ask JSON payload or its question.
            var end = candidate.length * 3 / 4
            if (end > 0 && candidate[end - 1].isHighSurrogate()) end--
            candidate = candidate.take(end).trimEnd()
        }
    }
}

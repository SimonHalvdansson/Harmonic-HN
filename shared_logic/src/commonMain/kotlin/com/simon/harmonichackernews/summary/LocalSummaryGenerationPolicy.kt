package com.simon.harmonichackernews.summary

data class LocalSummaryGenerationConfig(
    val responsePrefix: String,
    val maxOutputTokens: Int,
)

/** Model-specific generation and streamed-output policy shared by native inference hosts. */
object LocalSummaryGenerationPolicy {
    const val DEFAULT_MAX_OUTPUT_TOKENS: Int = 256

    fun configuration(modelId: String): LocalSummaryGenerationConfig =
        LocalSummaryGenerationConfig(
            responsePrefix = if (modelId == LocalModelCatalog.MODEL_QWEN_08B) "- " else "",
            maxOutputTokens = DEFAULT_MAX_OUTPUT_TOKENS,
        )

    /**
     * Hides a model's reasoning preamble while it is incomplete and returns only visible output
     * once the closing tag arrives. A null result means the host should not publish progress yet.
     */
    fun visibleOutput(rawResponse: String): String? {
        val trimmed = rawResponse.trimStart()
        if ("<think>".startsWith(trimmed)) return null
        if (trimmed.startsWith("<think>")) {
            val end = trimmed.indexOf("</think>")
            if (end < 0) return null
            return trimmed.substring(end + "</think>".length).trimStart()
        }
        return trimmed
    }
}

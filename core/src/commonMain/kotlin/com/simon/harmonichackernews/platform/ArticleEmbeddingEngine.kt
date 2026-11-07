package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.categorization.ArticleEmbeddingInput

/** Hosts own native runtime delivery and background thread affinity. Text is already formatted. */
interface ArticleEmbeddingEngine {
    suspend fun load(modelPath: String, maxInputTokens: Int = ArticleEmbeddingInput.DEFAULT_MAX_INPUT_TOKENS)
    suspend fun embed(text: String): FloatArray
    suspend fun close()
}

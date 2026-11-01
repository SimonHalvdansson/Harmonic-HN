package com.simon.harmonichackernews.platform

/** Hosts own native runtime delivery and background thread affinity. Text is already formatted. */
interface ArticleEmbeddingEngine {
    suspend fun load(modelPath: String)
    suspend fun embed(text: String): FloatArray
    suspend fun close()
}

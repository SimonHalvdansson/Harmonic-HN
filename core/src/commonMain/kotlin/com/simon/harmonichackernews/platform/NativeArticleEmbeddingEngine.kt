package com.simon.harmonichackernews.platform

/** Signature includes the provider, language, model revision and input policy. */
data class NativeEmbeddingModel(
    val signature: String,
    val name: String,
    val dimensions: Int,
    val tokenLimit: Int? = null,
    val modelName: String? = null,
)

interface NativeArticleEmbeddingEngine {
    suspend fun model(): NativeEmbeddingModel?
    suspend fun embed(text: String): FloatArray
    suspend fun close() {}
}

class EmbeddingQuotaException : Exception("System embeddings are temporarily rate limited")

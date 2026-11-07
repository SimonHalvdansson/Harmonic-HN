package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.categorization.ArticleCategories
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Swift owns the native handles and serializes every operation on its background worker. */
interface IosArticleEmbeddingCallback {
    fun complete(embedding: FloatArray?, errorMessage: String?)
}

interface IosArticleEmbeddingBridge {
    fun load(modelPath: String, cacheDirectory: String, maxInputTokens: Int, callback: IosArticleEmbeddingCallback)
    fun embed(text: String, callback: IosArticleEmbeddingCallback)
    fun close(callback: IosArticleEmbeddingCallback)
}

class IosArticleEmbeddingEngine(
    private val bridge: IosArticleEmbeddingBridge,
    private val cacheDirectory: String,
) : ArticleEmbeddingEngine {
    private val mutex = Mutex()

    override suspend fun load(modelPath: String, maxInputTokens: Int) = mutex.withLock {
        operation { bridge.load(modelPath, cacheDirectory, maxInputTokens, it) }
        Unit
    }

    override suspend fun embed(text: String): FloatArray = mutex.withLock {
        val embedding = checkNotNull(operation { bridge.embed(text, it) }) { "The model returned no embedding" }
        require(embedding.size == ArticleCategories.DIMENSIONS) { "Unexpected embedding dimensions" }
        ArticleCategories.normalize(embedding)
    }

    override suspend fun close() = mutex.withLock {
        operation(bridge::close)
        Unit
    }

    private suspend fun operation(start: (IosArticleEmbeddingCallback) -> Unit): FloatArray? =
        suspendCancellableCoroutine { continuation ->
            start(object : IosArticleEmbeddingCallback {
                override fun complete(embedding: FloatArray?, errorMessage: String?) {
                    if (!continuation.isActive) return
                    continuation.resumeWith(
                        if (errorMessage.isNullOrBlank()) Result.success(embedding)
                        else Result.failure(IllegalStateException(errorMessage)),
                    )
                }
            })
        }
}

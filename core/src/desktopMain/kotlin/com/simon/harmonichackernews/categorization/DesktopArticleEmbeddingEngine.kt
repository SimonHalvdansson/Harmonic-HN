package com.simon.harmonichackernews.categorization

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.simon.harmonichackernews.platform.ArticleEmbeddingEngine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** All native creation, inference, and disposal stay on one background worker. */
internal class DesktopArticleEmbeddingEngine(
    private val cacheDirectory: String,
) : ArticleEmbeddingEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Article embeddings").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }.asCoroutineDispatcher()
    private val disposed = AtomicBoolean(false)
    private var engine: EmbeddingEngine? = null

    override suspend fun load(modelPath: String, maxInputTokens: Int) = withContext(dispatcher) {
        check(!disposed.get()) { "Embedding runtime is closed" }
        release()
        nativeOperation {
            val candidate = EmbeddingEngine(EmbeddingEngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(threadCount = 1),
                maxInputLength = maxInputTokens,
                cacheDir = cacheDirectory,
            ))
            candidate.initialize()
            // The native runtime only permits closing successfully initialized engines.
            engine = candidate
        }
    }

    override suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        check(!disposed.get()) { "Embedding runtime is closed" }
        nativeOperation {
            checkNotNull(engine) { "Embedding model is not loaded" }.computeEmbedding(
                listOf(InputData.Text(text)),
                EmbeddingOptions(normalize = true, outputSize = ArticleCategories.DIMENSIONS),
            ).embedding
        }
    }

    override suspend fun close() = withContext(dispatcher) { release() }

    /** App shutdown also releases the worker; normal close leaves the model reloadable. */
    fun shutdown() {
        if (!disposed.compareAndSet(false, true)) return
        try {
            runBlocking { withContext(dispatcher) { release() } }
        } finally {
            dispatcher.close()
        }
    }

    private fun release() {
        val previous = engine
        engine = null
        nativeOperation { previous?.close() }
    }

    private inline fun <T> nativeOperation(block: () -> T): T = try {
        block()
    } catch (error: LinkageError) {
        throw IllegalStateException("The installed LiteRT-LM runtime cannot load on this computer", error)
    }
}

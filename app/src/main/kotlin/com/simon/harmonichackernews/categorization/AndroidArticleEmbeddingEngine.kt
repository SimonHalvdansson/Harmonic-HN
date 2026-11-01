package com.simon.harmonichackernews.categorization

import android.content.Context
import android.os.Process
import com.simon.harmonichackernews.platform.ArticleEmbeddingEngine
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Implemented in the optional local-AI feature; no LiteRT linkage in the base application. */
interface AndroidArticleEmbeddingBackend {
    fun load(context: Context, modelPath: String)
    fun embed(text: String): FloatArray
    fun close()
}

class AndroidArticleEmbeddingEngine(context: Context) : ArticleEmbeddingEngine {
    private val context = context.applicationContext
    // All native creation, inference and disposal use one low-priority worker.
    private val dispatcher = Executors.newSingleThreadExecutor { task ->
        Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); task.run() }, "Article embeddings")
            .apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var backend: AndroidArticleEmbeddingBackend? = null

    override suspend fun load(modelPath: String) = withContext(dispatcher) {
        backend?.close()
        backend = null
        nativeOperation {
            val candidate = Class.forName("com.simon.harmonichackernews.localai.litert.LiteRtArticleEmbeddingBackend")
                .getDeclaredConstructor().newInstance() as AndroidArticleEmbeddingBackend
            candidate.load(context, modelPath)
            backend = candidate
        }
    }

    override suspend fun embed(text: String): FloatArray = withContext(dispatcher) {
        nativeOperation { checkNotNull(backend) { "Embedding model is not loaded" }.embed(text) }
    }

    override suspend fun close() = withContext(dispatcher) {
        val previous = backend
        backend = null
        previous?.close()
        Unit
    }

    private inline fun <T> nativeOperation(block: () -> T): T = try {
        block()
    } catch (error: LinkageError) {
        throw IllegalStateException("The installed LiteRT-LM runtime cannot load on this device", error)
    }
}

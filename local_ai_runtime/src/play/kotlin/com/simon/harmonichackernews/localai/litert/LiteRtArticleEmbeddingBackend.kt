package com.simon.harmonichackernews.localai.litert

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.simon.harmonichackernews.categorization.AndroidArticleEmbeddingBackend
import com.simon.harmonichackernews.categorization.ArticleCategories

/** Called exclusively on AndroidArticleEmbeddingEngine's background worker. */
class LiteRtArticleEmbeddingBackend : AndroidArticleEmbeddingBackend {
    private var engine: EmbeddingEngine? = null

    override fun load(context: Context, modelPath: String, maxInputTokens: Int) {
        close()
        val candidate = EmbeddingEngine(EmbeddingEngineConfig(
            modelPath = modelPath,
            // One CPU thread leaves the GPU and other cores available for scrolling.
            backend = Backend.CPU(threadCount = 1),
            maxInputLength = maxInputTokens,
            // Use the shared model cache location so FileLocalModelStorage can remove the
            // model's compiled/weight caches together with its downloaded weights.
            cacheDir = context.cacheDir.absolutePath,
        ))
        candidate.initialize()
        engine = candidate // An engine whose initialization failed must not be closed.
    }

    override fun embed(text: String): FloatArray = checkNotNull(engine).computeEmbedding(
        listOf(InputData.Text(text)),
        EmbeddingOptions(normalize = true, outputSize = ArticleCategories.DIMENSIONS),
    ).embedding

    override fun close() {
        val previous = engine
        engine = null
        previous?.close()
    }
}

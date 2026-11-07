package com.simon.harmonichackernews.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.NaturalLanguage.NLEmbedding
import platform.NaturalLanguage.NLLanguageEnglish
import platform.Foundation.NSNumber

@OptIn(ExperimentalForeignApi::class)
class IosSystemArticleEmbeddingEngine : NativeArticleEmbeddingEngine {
    private var embedding: NLEmbedding? = null
    override suspend fun model(): NativeEmbeddingModel? = withContext(Dispatchers.Default) {
        val model = NLEmbedding.sentenceEmbeddingForLanguage(NLLanguageEnglish) ?: return@withContext null
        embedding = model
        NativeEmbeddingModel("apple-sentence-en-${model.revision}-${model.dimension}", "Apple Natural Language", model.dimension.toInt())
    }
    override suspend fun embed(text: String): FloatArray = withContext(Dispatchers.Default) {
        val values = checkNotNull(embedding?.vectorForString(text)) { "Apple sentence embeddings are unavailable" }
        FloatArray(values.size) { (values[it] as NSNumber).floatValue }
    }
    override suspend fun close() { embedding = null }
}

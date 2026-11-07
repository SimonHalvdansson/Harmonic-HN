package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.categorization.ArticleCategories
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosArticleEmbeddingEngineTest {
    @Test
    fun nativeBridgeReceivesModelInputAndReleasesEngine() = runTest {
        val bridge = StubBridge()
        val engine = IosArticleEmbeddingEngine(bridge, "/cache/local_ai")
        engine.load("/files/model.litertlm", 1024)
        val text = ArticleCategories.input("A new compiler", "A programming language compiler.")
        val embedding = engine.embed(text)
        assertEquals(ArticleCategories.DIMENSIONS, embedding.size)
        assertEquals(1f, embedding[0])
        assertEquals(text, bridge.input)
        assertEquals("/files/model.litertlm", bridge.modelPath)
        assertEquals("/cache/local_ai", bridge.cacheDirectory)
        assertEquals(1024, bridge.maxInputTokens)
        engine.close()
        assertContentEquals(listOf("load", "embed", "close"), bridge.operations)
    }

    @Test
    fun rejectsInvalidDimensionsAndNativeErrors() = runTest {
        val bridge = StubBridge()
        val engine = IosArticleEmbeddingEngine(bridge, "/cache")
        bridge.embedding = floatArrayOf(1f)
        assertFailsWith<IllegalArgumentException> { engine.embed("text") }
        bridge.error = "The embedding model is not loaded"
        val failure = assertFailsWith<IllegalStateException> { engine.embed("text") }
        assertEquals(bridge.error, failure.message)
    }

    private class StubBridge : IosArticleEmbeddingBridge {
        var embedding = FloatArray(ArticleCategories.DIMENSIONS) { if (it == 0) 2f else 0f }
        var error: String? = null
        var modelPath = ""
        var cacheDirectory = ""
        var maxInputTokens = 0
        var input = ""
        val operations = mutableListOf<String>()

        override fun load(modelPath: String, cacheDirectory: String, maxInputTokens: Int, callback: IosArticleEmbeddingCallback) {
            this.modelPath = modelPath
            this.cacheDirectory = cacheDirectory
            this.maxInputTokens = maxInputTokens
            operations += "load"
            callback.complete(null, error)
        }

        override fun embed(text: String, callback: IosArticleEmbeddingCallback) {
            input = text
            operations += "embed"
            callback.complete(embedding, error)
        }

        override fun close(callback: IosArticleEmbeddingCallback) {
            operations += "close"
            callback.complete(null, error)
        }
    }
}

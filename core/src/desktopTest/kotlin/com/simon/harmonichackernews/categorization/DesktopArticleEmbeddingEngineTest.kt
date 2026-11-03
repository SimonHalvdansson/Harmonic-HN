package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.summary.DesktopLiteRtInference
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

class DesktopArticleEmbeddingEngineTest {
    @Test
    fun unloadedAndMissingModelsFailThroughTheNativeBridgeAndRemainClosable() = runBlocking {
        assumeTrue(DesktopLiteRtInference.supported)
        val cache = Files.createTempDirectory("harmonic-embedding-native-test")
        val engine = DesktopArticleEmbeddingEngine(cache.toString())
        try {
            assertFailsWith<IllegalStateException> { engine.embed("test") }
            // Linkage failures are wrapped separately: a missing file must reach native loading.
            val failure = assertFailsWith<Exception> { engine.load(cache.resolve("missing.litertlm").toString()) }
            assertTrue(failure.cause !is LinkageError)
            assertFailsWith<IllegalStateException> { engine.embed("test") }
            engine.close()
            engine.close()
        } finally {
            engine.shutdown()
            engine.shutdown()
            deleteRecursively(cache)
        }
    }

    /** Set a local downloaded fixture to verify native results without network work in CI. */
    @Test
    fun installedRuntimeProducesNormalizedCategoryEmbeddingsAndReloads() = runBlocking {
        val modelPath = System.getenv("HARMONIC_TEST_EMBEDDING_MODEL")
        assumeTrue("Set HARMONIC_TEST_EMBEDDING_MODEL to EmbeddingGemma 2 .litertlm", modelPath != null)
        assumeTrue(DesktopLiteRtInference.supported)
        val cache = Files.createTempDirectory("harmonic-embedding-test")
        val engine = DesktopArticleEmbeddingEngine(cache.toString())
        try {
            engine.load(requireNotNull(modelPath))
            val text = ArticleCategories.input("Linux kernel adds a new filesystem", null)
            val first = engine.embed(text)
            assertEquals(ArticleCategories.DIMENSIONS, first.size)
            assertTrue(first.all(Float::isFinite))
            assertEquals(1.0, sqrt(first.sumOf { it.toDouble() * it }), 0.001)
            val other = engine.embed(ArticleCategories.input("A recipe for chocolate cake", null))
            assertTrue(first.indices.any { first[it] != other[it] })
            engine.close()
            assertFailsWith<IllegalStateException> { engine.embed(text) }
            engine.load(modelPath)
            val reloaded = engine.embed(text)
            assertTrue(first.indices.all { kotlin.math.abs(first[it] - reloaded[it]) < 0.0001f })
        } finally {
            engine.shutdown()
            deleteRecursively(cache)
        }
    }

    private fun deleteRecursively(root: Path) {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}

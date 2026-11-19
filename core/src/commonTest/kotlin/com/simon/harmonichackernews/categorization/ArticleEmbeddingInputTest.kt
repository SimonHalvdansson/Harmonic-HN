package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArticleEmbeddingInputTest {
    @Test
    fun reusedWhitespacePatternPreservesInputAndDescriptionFallbacks() {
        val inputs = listOf("", "  ", "Kotlin\tand\nperformance", "Café\u00a0news\u2003today",
            "  Tools\r\nfor\u000bdevelopers\u000c  ", "😀 " + "Long title ".repeat(50))
        for (title in inputs) {
            for (description in inputs + listOf(null, title, "Subscribe to continue reading")) {
                val expectedTitle = title.replace(Regex("\\s+"), " ").trim().take(300)
                val expectedDetails = description.orEmpty().replace(Regex("\\s+"), " ").trim().take(600)
                    .takeIf { HtmlDescriptionExtractor.isMeaningful(it, expectedTitle, expectedTitle) }.orEmpty()
                assertEquals("title: $expectedTitle | text: ${expectedDetails.ifBlank { expectedTitle }}",
                    ArticleEmbeddingInput.format(title, description))
            }
        }
    }

    @Test
    fun onlyOverLimitInputsAreShortenedAndRetried() = runTest {
        val input = ArticleEmbeddingInput.format("Title", "An article about programming languages and compilers. ".repeat(15))
        val attempts = mutableListOf<String>()
        val vector = floatArrayOf(1f)
        val result = ArticleEmbeddingInput.embedWithinLimit(input, 128) { text ->
            attempts += text
            if (attempts.size == 1) error("INVALID_ARGUMENT: Input sequence length (240) exceeds maximum supported signature length (128).")
            vector
        }
        assertSame(vector, result)
        assertEquals(2, attempts.size)
        assertEquals(input, attempts.first())
        assertTrue(attempts.last().startsWith("title: Title | text: "))
        assertTrue(attempts.last().length < input.length)

        var calls = 0
        ArticleEmbeddingInput.embedWithinLimit(input, 1024) { text ->
            calls++
            assertEquals(input, text)
            vector
        }
        assertEquals(1, calls)
    }

    @Test
    fun failuresAndCancellationAreNotRetried() = runTest {
        for (failure in listOf(
            IllegalStateException("Model failed"),
            IllegalStateException("Input sequence length (600) exceeds maximum supported signature length (512)."),
            CancellationException("Cancelled"),
        )) {
            var calls = 0
            val thrown = assertFailsWith<Exception> {
                ArticleEmbeddingInput.embedWithinLimit("Some input", 128) { calls++; throw failure }
            }
            assertSame(failure, thrown)
            assertEquals(1, calls)
        }
    }

    @Test
    fun shorteningMultilingualTextDoesNotSplitSurrogatePairs() = runTest {
        val input = "😀".repeat(100)
        var calls = 0
        ArticleEmbeddingInput.embedWithinLimit(input, 128) { text ->
            calls++
            if (calls == 1) error("Input sequence length (202) exceeds maximum supported signature length (128).")
            assertTrue(text.isNotEmpty())
            assertTrue(text.length < input.length)
            assertEquals(0, text.length % 2)
            assertTrue(text.last().isLowSurrogate())
            floatArrayOf(1f)
        }
        assertEquals(2, calls)
    }
}

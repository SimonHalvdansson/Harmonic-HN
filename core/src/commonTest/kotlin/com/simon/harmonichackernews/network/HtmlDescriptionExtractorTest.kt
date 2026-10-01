package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HtmlDescriptionExtractorTest {
    private val article = "A detailed explanation of the implementation with useful examples for every reader."

    @Test fun metadataWhitespaceNormalizationKeepsTheSameText() {
        val document = Ksoup.parse("<p>unused fallback</p>")
        val variants = listOf(article, "  $article  ", article.replace(" ", "\t\n"),
            article.replace(" ", "\u00a0"), article.replace(" ", "\u2003"))
        variants.forEach { value ->
            assertEquals(article, HtmlDescriptionExtractor.chooseDescription(value, document, null, null))
        }
    }

    @Test fun bothTitlesAndProviderBoilerplateStillRejectDuplicatedDescriptions() {
        assertFalse(HtmlDescriptionExtractor.isMeaningful(article, article.uppercase(), null))
        assertFalse(HtmlDescriptionExtractor.isMeaningful(article, null, article.replace(" ", "—")))
        assertFalse(HtmlDescriptionExtractor.isMeaningful(
            "$article Contribute to example/project development by creating an account on GitHub.",
            article, null))
    }

    @Test fun excludedParagraphsAndEqualScoresPreserveSelection() {
        val second = "Another explanation of the implementation with useful examples for every reader."
        val document = Ksoup.parse("<nav><p>$article</p></nav><article>" +
            "<p hidden>$article</p><p>$second</p><p>$article</p></article>")
        assertEquals(second, HtmlDescriptionExtractor.chooseDescription(null, document, "Title", null))
    }
}

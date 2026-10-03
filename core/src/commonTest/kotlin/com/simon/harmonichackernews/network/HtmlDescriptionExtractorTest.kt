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

    @Test fun maximumScoreKeepsTheEarliestArticleParagraph() {
        val first = "First explanation. " + article.repeat(3)
        val later = "Later explanation. " + article.repeat(3)
        val document = Ksoup.parse("<article><p>$first</p>" +
            "<p>$later</p>".repeat(200) + "</article>")
        assertEquals(first, HtmlDescriptionExtractor.chooseDescription(null, document, "Title", null))
    }

    @Test fun laterArticleParagraphCanStillBeatMainAndShortParagraphs() {
        val later = "Later explanation. " + article.repeat(3)
        val document = Ksoup.parse("<main><p>${article.repeat(3)}</p></main>" +
            "<article><p>$article</p><p>$later</p></article>")
        assertEquals(later, HtmlDescriptionExtractor.chooseDescription(null, document, "Title", null))
    }

    @Test fun paragraphPositionPenaltyStopsGrowingAfterOneHundred() {
        val first = "First explanation. " + article.repeat(3)
        val later = "Later explanation. " + article.repeat(3)
        val document = Ksoup.parse("<nav>" + "<p>Navigation item</p>".repeat(130) +
            "</nav><article><p>$first</p><p>$later</p></article>")
        assertEquals(first, HtmlDescriptionExtractor.chooseDescription(null, document, "Title", null))
    }
}

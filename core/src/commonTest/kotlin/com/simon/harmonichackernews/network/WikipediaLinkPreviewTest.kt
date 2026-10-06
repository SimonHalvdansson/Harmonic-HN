package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikipediaLinkPreviewTest {
    @Test
    fun removesEmptyPronunciationAfterBoldIntroductionTitle() {
        val summary = parse("<p><b>Cherenkov radiation</b> (<span></span>) is radiation.</p>")

        assertEquals("Cherenkov radiation is radiation.", WikipediaLinkPreview.firstWikipediaParagraph(summary))
        assertTrue(summary.contains("<b>Cherenkov radiation</b>"))
    }

    @Test
    fun acceptsWhitespaceInsideEmptyPronunciation() {
        val summary = parse("<p><b>Cherenkov radiation</b> ( <span> </span> ) is radiation.</p>")

        assertEquals("Cherenkov radiation is radiation.", WikipediaLinkPreview.firstWikipediaParagraph(summary))
    }

    @Test
    fun preservesRealPronunciationAndOtherParentheses() {
        for (html in listOf(
            "<p><b>Cherenkov radiation</b> (<span>/tʃərɛŋˈkɒf/</span>) is radiation.</p>",
            "<p><b>Cherenkov radiation</b> (also called Vavilov–Cherenkov radiation) is radiation.</p>",
            "<p>Cherenkov radiation (<span></span>) is radiation.</p>",
            "<p>About <b>Cherenkov radiation</b> (<span></span>).</p>",
            "<p><b>Cherenkov radiation</b> is radiation (<span></span>).</p>",
            "<p>Introduction.</p><p><b>Cherenkov radiation</b> (<span></span>) is radiation.</p>",
        )) {
            assertTrue(parse(html).contains("("), html)
        }
    }

    private fun parse(html: String): String = requireNotNull(
        WikipediaLinkPreview.parseWikipedia(
            """{"query":{"pages":{"1":{"title":"Cherenkov radiation","extract":"$html"}}}}""",
        ),
    ).summary.orEmpty()
}

package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikipediaLinkPreviewTest {
    @Test
    fun redirectTitleLoadsTheCanonicalArticleSummary() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("USA", request.url.parameters["titles"])
            // Without redirect resolution MediaWiki returns the redirect page without an extract.
            if (request.url.parameters["redirects"] == "1") {
                respond("""{"query":{"redirects":[{"from":"USA","to":"United States"}],"pages":{"1":{"title":"United States","extract":"<p>The United States is a country.</p>"}}}}""")
            } else {
                respond("""{"query":{"pages":{"2":{"title":"USA"}}}}""")
            }
        })
        try {
            val preview = KtorLinkPreviewRepository(client).getWikipediaInfo("https://en.wikipedia.org/wiki/USA")
            assertEquals("United States", preview.title)
            assertEquals("The United States is a country.", WikipediaLinkPreview.firstWikipediaParagraph(preview.summary))
        } finally {
            client.close()
        }
    }

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

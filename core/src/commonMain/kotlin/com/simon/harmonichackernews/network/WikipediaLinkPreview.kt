package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

internal object WikipediaLinkPreview {
    fun isWikipediaUrl(url: String?): Boolean = url != null && wikipediaUrlRegex.matches(url)

    fun wikipediaTitle(url: String?): String? = url
        ?.takeIf(::isWikipediaUrl)
        ?.substringAfter("en.wikipedia.org/wiki/", missingDelimiterValue = "")
        ?.takeIf(String::isNotEmpty)

    private val wikipediaUrlRegex = Regex("^https?://en\\.wikipedia\\.org/wiki/.+")

    fun parseWikipedia(response: String): WikipediaInfo? {
        val json = JsonObject(response)
        val pages = json.getJSONObject("query").getJSONObject("pages")
        val pageKey = pages.keys().asSequence().firstOrNull() ?: return null
        val page = pages.getJSONObject(pageKey)
        val summary = page.optString("extract")
        if (summary.isEmpty()) return null
        val document = sanitizeWikipediaHtml(summary)
        if (!document.body().hasText()) return null
        return WikipediaInfo(
            title = page.optString("title").takeIf(String::isNotBlank),
            summary = document.body().html(),
        )
    }

    fun firstWikipediaParagraph(summaryHtml: String?): String {
        if (summaryHtml.isNullOrEmpty()) return ""
        val document = Ksoup.parse(summaryHtml)
        return document.selectFirst("p")?.text() ?: document.text()
    }

    private fun sanitizeWikipediaHtml(summaryHtml: String): Document {
        val document = Ksoup.parseBodyFragment(summaryHtml)
        document.select(UNSUPPORTED_WIKIPEDIA_ELEMENTS).remove()
        for (blockquote in document.select("blockquote")) blockquote.unwrap()
        for (element in document.select("p, ul, ol")) {
            if (!element.hasText()) element.remove()
        }
        return document
    }

    private const val UNSUPPORTED_WIKIPEDIA_ELEMENTS =
        "script, style, svg, wiki-chart, table, figure, iframe, canvas, noscript, object, embed"
}

internal suspend fun HttpClient.loadWikipediaInfo(url: String): WikipediaInfo {
    val title = WikipediaLinkPreview.wikipediaTitle(url)
        ?: throw LinkPreviewException("Invalid Wikipedia URL")
    val endpoint = URLBuilder("https://en.wikipedia.org/w/api.php").apply {
        parameters.append("format", "json")
        parameters.append("action", "query")
        parameters.append("prop", "extracts")
        parameters.append("exintro", "")
        parameters.append("titles", title)
    }.buildString()
    return WikipediaLinkPreview.parseWikipedia(getTextOrThrow(endpoint))
        ?: throw LinkPreviewException("Wikipedia did not return a visible summary")
}

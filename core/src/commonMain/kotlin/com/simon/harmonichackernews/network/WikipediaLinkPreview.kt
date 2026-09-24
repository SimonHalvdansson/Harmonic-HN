package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

internal object WikipediaLinkPreview {
    fun isWikipediaUrl(url: String?): Boolean = wikipediaTitle(url) != null

    fun wikipediaTitle(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.scheme !in setOf("http", "https") || parsed.host.lowercase() != "en.wikipedia.org") {
            return null
        }
        val segments = parsed.pathSegments.dropWhile(String::isEmpty)
        if (segments.firstOrNull() != "wiki") return null
        // Path segments are decoded once by the URL parser; preserve subpage slashes and pluses.
        val title = segments.drop(1).joinToString("/").takeIf(String::isNotBlank) ?: return null
        val namespace = title.trimStart(' ', '_', ':')
            .substringBefore(':', missingDelimiterValue = "")
            .replace(namespaceWhitespace, " ")
            .trim()
            .lowercase()
        return title.takeUnless { namespace in nonArticleNamespaces }
    }

    // English Wikipedia namespaces and their aliases. A colon alone does not imply a
    // namespace: article titles such as "Star Trek: Voyager" must remain eligible.
    // https://en.wikipedia.org/wiki/Wikipedia:Namespace
    private val nonArticleNamespaces = setOf(
        "talk", "user", "user talk", "wikipedia", "wikipedia talk",
        "file", "file talk", "mediawiki", "mediawiki talk", "template", "template talk",
        "help", "help talk", "category", "category talk", "portal", "portal talk",
        "draft", "draft talk", "mos", "mos talk", "timedtext", "timedtext talk",
        "module", "module talk", "event", "event talk", "special", "media",
        "wp", "wt", "project", "project talk", "image", "image talk", "tm",
    )
    private val namespaceWhitespace = Regex("[\\s_]+")

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

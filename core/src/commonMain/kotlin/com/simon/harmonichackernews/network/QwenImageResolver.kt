package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.http.URLBuilder

internal object QwenImageResolver : SiteImageResolver {
    override fun requestUrl(pageUrl: NetworkUrl): String? {
        if (pageUrl.scheme !in listOf("http", "https") ||
            !pageUrl.host.equals("qwen.ai", ignoreCase = true) ||
            pageUrl.encodedPath.trimEnd('/') != "/blog"
        ) return null
        val id = pageUrl.queryParameter("id")?.takeIf(articleId::matches) ?: return null
        // The same public article endpoint used by qwen.ai's client-rendered blog.
        return URLBuilder("https://qwen.ai/api/v2/article/").apply {
            parameters.append("language", "en-US")
            parameters.append("path", id)
            parameters.append("type", "qwen_ai")
        }.buildString()
    }

    override fun extractImage(response: String, pageUrl: NetworkUrl): String? {
        val envelope = JsonObject(response)
        if (!envelope.optBoolean("success")) return null
        val article = envelope.optJSONObject("data") ?: return null
        if (article.optString("path") != pageUrl.queryParameter("id") ||
            article.optString("type") != "qwen_ai"
        ) return null

        val document = Ksoup.parse(article.optString("content"), baseUri = pageUrl.toString())
        val canonical = document.selectFirst("link[rel=canonical]")?.attr("href")
            ?.let(LinkSummaryParser::normalizeHttpUrl)
        val base = canonical?.toNetworkUrlOrNull() ?: pageUrl
        // Qwen's embedded HTML can contain placeholder og:image URLs. Its opening
        // figure is the header image; limit selection to that position to avoid logos
        // and figures further down the article. The API also provides a card cover.
        val banner = document.selectFirst("article .post-content > figure:first-child img[src]")
            ?.attr("src")
        val cover = article.optJSONObject("extra")?.optString("cover_small")
        return sequenceOf(banner, cover).mapNotNull { value ->
            value?.trim()?.takeIf(String::isNotEmpty)
                ?.let(base::resolve)?.toString()?.let(LinkSummaryParser::normalizeHttpUrl)
        }.firstOrNull()
    }

    private val articleId = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")
}

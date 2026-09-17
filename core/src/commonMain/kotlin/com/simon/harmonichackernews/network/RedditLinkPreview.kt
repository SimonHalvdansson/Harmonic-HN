package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

internal object RedditLinkPreview {
    internal fun isRedditPost(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val host = parsed.host.lowercase().removePrefix("www.").removePrefix("old.")
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return host == "reddit.com" && "comments" in segments
    }

    fun parseOEmbed(type: LinkPreviewType, response: String, url: String): LinkPreviewInfo {
        val json = JsonObject(response)
        val author = json.nonBlankString("author_name")
        return LinkPreviewInfo(
            type,
            when (type) {
                LinkPreviewType.REDDIT_POST -> json.nonBlankString("title") ?: "Reddit post"
                else -> author ?: type.title
            },
            when (type) {
                LinkPreviewType.BLUESKY_POST -> "Bluesky"
                LinkPreviewType.REDDIT_POST -> author?.let { "$it · Reddit" } ?: "Reddit"
                else -> json.nonBlankString("provider_name")
            },
            null,
            null,
            url,
            details(
                "Provider" to json.nonBlankString("provider_name"),
                "Author" to author,
            ),
        )
    }
}

internal suspend fun HttpClient.loadOEmbedPreview(
    type: LinkPreviewType,
    baseUrl: String,
    url: String,
): LinkPreviewInfo {
    val endpoint = URLBuilder(baseUrl).apply { parameters.append("url", url) }.buildString()
    return RedditLinkPreview.parseOEmbed(type, getTextOrThrow(endpoint), url)
}

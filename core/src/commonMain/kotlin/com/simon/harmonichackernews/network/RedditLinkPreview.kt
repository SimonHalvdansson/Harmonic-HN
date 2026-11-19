package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal object RedditLinkPreview {
    internal fun isRedditPost(url: String?): Boolean {
        return isRedditPost(url?.toNetworkUrlOrNull() ?: return false)
    }

    internal fun isRedditPost(parsed: NetworkUrl): Boolean {
        val host = parsed.host.lowercase().removePrefix("www.").removePrefix("old.")
        return host == "reddit.com" && "comments" in parsed.pathSegments
    }

    internal fun postId(url: String): String? {
        val parsed = url.toNetworkUrlOrNull()?.takeIf(::isRedditPost) ?: return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        val index = when {
            segments.firstOrNull() == "comments" -> 1
            segments.size >= 4 && segments[0] == "r" && segments[2] == "comments" -> 3
            else -> return null
        }
        return segments.getOrNull(index)?.takeIf { it.matches(Regex("[a-zA-Z0-9]+")) }
            ?.lowercase()
    }

    internal fun feedUrl(url: String): String? {
        postId(url) ?: return null
        val segments = url.toNetworkUrlOrNull()?.pathSegments?.filter(String::isNotEmpty) ?: return null
        // Drop tracking parameters and comment paths; only request the parent post's feed.
        // Preserve the permalink's subreddit and slug: shortened /comments/id feeds are less reliable.
        val postPathLength = if (segments.first() == "comments") 3 else 5
        val postPath = apiPath(*segments.take(postPathLength).toTypedArray())
        return "https://www.reddit.com/$postPath/.rss?limit=1"
    }

    fun parseFeed(response: String, url: String): LinkPreviewInfo {
        val id = postId(url) ?: throw LinkPreviewException("Invalid Reddit post URL")
        val feed = Ksoup.parseXml(response).selectFirst("feed")
            ?: throw LinkPreviewException("Reddit feed not found")
        // A thread feed also contains comments. Never use a comment or another post as the preview.
        val post = feed.children().firstOrNull { entry ->
            entry.tagName() == "entry" && entry.selectFirst("id")?.text() == "t3_$id"
        } ?: throw LinkPreviewException("Reddit post not found in feed")
        val title = post.selectFirst("title")?.text().orEmpty()
            .requiredPreviewTitle(LinkPreviewType.REDDIT_POST)
        val author = post.selectFirst("author > name")?.text()?.trim()?.removePrefix("/")
            ?.takeIf(String::isNotBlank)
        val subreddit = post.selectFirst("category")?.attr("label")?.takeIf(String::isNotBlank)
        val content = Ksoup.parse(post.selectFirst("content")?.text().orEmpty(), baseUri = url)
        // Reddit wraps the actual self text in .md; everything outside is feed attribution.
        val description = content.selectFirst(".md")?.text()
            ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, 1200) }
            ?.takeIf(String::isNotBlank)
        val image = post.getElementsByTag("media:thumbnail").firstOrNull()?.attr("url")
            ?.httpUrlOrNull()
            ?: content.selectFirst("img[src]")?.attr("src")?.httpUrlOrNull()
            ?: content.select("a[href]").firstNotNullOfOrNull { link ->
                link.attr("href").httpUrlOrNull()?.takeIf { candidate ->
                    val parsed = candidate.toNetworkUrlOrNull() ?: return@takeIf false
                    parsed.host == "i.redd.it" &&
                        parsed.encodedPath.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif")
                }
            }
        return LinkPreviewInfo(
            type = LinkPreviewType.REDDIT_POST,
            title = title,
            subtitle = listOfNotNull("Reddit", subreddit).joinToString(" · "),
            description = description,
            imageUrl = image,
            url = url,
            details = redditDetails(post.selectFirst("published")?.text()?.dateOnly(), author),
        )
    }

    private fun redditDetails(published: String?, author: String?): List<LinkPreviewDetail> = listOfNotNull(
        published?.takeIf(String::isNotBlank)?.let { LinkPreviewDetail("Published", it, it) },
        author?.trim()?.removePrefix("/")?.takeIf(String::isNotBlank)?.let {
            val username = if (it.startsWith("u/") || it.startsWith("[")) it else "u/$it"
            LinkPreviewDetail("Author", username, username)
        },
    )

    private fun String.httpUrlOrNull(): String? = trim().takeIf { value ->
        val parsed = value.toNetworkUrlOrNull()
        parsed != null && parsed.host.isNotBlank() && parsed.scheme in setOf("https", "http")
    }

    fun parseOEmbed(type: LinkPreviewType, response: String, url: String): LinkPreviewInfo {
        val json = JsonObject(response)
        val author = json.nonBlankString("author_name")
        if (type == LinkPreviewType.REDDIT_POST) {
            val segments = url.toNetworkUrlOrNull()?.pathSegments?.filter(String::isNotEmpty).orEmpty()
            val subreddit = segments.getOrNull(1)?.takeIf { segments.firstOrNull() == "r" }?.let { "r/$it" }
            return LinkPreviewInfo(
                type = type,
                title = json.nonBlankString("title") ?: "Reddit post",
                subtitle = listOfNotNull("Reddit", subreddit).joinToString(" · "),
                url = url,
                details = redditDetails(published = null, author = author),
            )
        }
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

internal suspend fun HttpClient.loadRedditPreview(url: String): LinkPreviewInfo {
    val feedUrl = RedditLinkPreview.feedUrl(url)
    if (feedUrl != null) {
        val preview = try {
            withTimeoutOrNull(5_000L) {
                RedditLinkPreview.parseFeed(getTextOrThrow(feedUrl), url)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (preview != null) return preview
    }
    return loadOEmbedPreview(LinkPreviewType.REDDIT_POST, "https://www.reddit.com/oembed", url)
}

internal suspend fun HttpClient.loadOEmbedPreview(
    type: LinkPreviewType,
    baseUrl: String,
    url: String,
): LinkPreviewInfo {
    val endpoint = URLBuilder(baseUrl).apply { parameters.append("url", url) }.buildString()
    return RedditLinkPreview.parseOEmbed(type, getTextOrThrow(endpoint), url)
}

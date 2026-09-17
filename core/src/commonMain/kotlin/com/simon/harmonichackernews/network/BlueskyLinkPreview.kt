package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder

internal object BlueskyLinkPreview {
    internal fun isBlueskyPost(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return parsed.host.lowercase() == "bsky.app" && segments.size >= 4 &&
            segments[0] == "profile" && segments[2] == "post"
    }

    fun parseBluesky(response: String, url: String): LinkPreviewInfo {
        val post = JsonObject(response).getJSONObject("thread").getJSONObject("post")
        val author = post.getJSONObject("author")
        val record = post.getJSONObject("record")
        val embed = post.optJSONObject("embed")
        val image = embed?.optJSONArray("images")?.optJSONObject(0)?.nonBlankString("thumb")
            ?: embed?.optJSONObject("media")?.optJSONArray("images")
                ?.optJSONObject(0)?.nonBlankString("thumb")
        return LinkPreviewInfo(
            LinkPreviewType.BLUESKY_POST,
            author.nonBlankString("displayName") ?: author.optString("handle"),
            "@${author.optString("handle")}",
            record.nonBlankString("text"),
            image ?: author.nonBlankString("avatar"),
            url,
            details(
                "Replies" to post.optLong("replyCount").toString(),
                "Reposts" to post.optLong("repostCount").toString(),
                "Likes" to post.optLong("likeCount").toString(),
                "Quotes" to post.optLong("quoteCount").toString(),
                "Published" to record.nonBlankString("createdAt")?.dateOnly(),
            ),
        )
    }
}

internal suspend fun HttpClient.loadBlueskyPreview(url: String): LinkPreviewInfo {
    val parsed = url.toNetworkUrlOrNull()
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val segments = parsed.pathSegments.filter(String::isNotEmpty)
    val handle = segments.getOrNull(1)
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val rkey = segments.getOrNull(3)
        ?: throw LinkPreviewException("Invalid Bluesky post URL")
    val did = if (handle.startsWith("did:")) {
        handle
    } else {
        val resolveEndpoint = URLBuilder(
            "https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle",
        ).apply { parameters.append("handle", handle) }.buildString()
        JsonObject(getTextOrThrow(resolveEndpoint)).optString("did")
            .takeIf(String::isNotBlank)
            ?: throw LinkPreviewException("Bluesky handle not found")
    }
    val threadEndpoint = URLBuilder(
        "https://public.api.bsky.app/xrpc/app.bsky.feed.getPostThread",
    ).apply {
        parameters.append("uri", "at://$did/app.bsky.feed.post/$rkey")
        parameters.append("depth", "0")
    }.buildString()
    return BlueskyLinkPreview.parseBluesky(getTextOrThrow(threadEndpoint), url)
}

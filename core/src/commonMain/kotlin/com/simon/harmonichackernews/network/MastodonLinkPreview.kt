package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

internal object MastodonLinkPreview {
    internal fun mastodonStatus(url: String?): Pair<String, String>? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || !segments[0].startsWith("@") || segments[1].any { !it.isDigit() }) {
            return null
        }
        return parsed.host to segments[1]
    }

    fun parseMastodon(response: String, url: String): LinkPreviewInfo {
        val json = JsonObject(response)
        val account = json.getJSONObject("account")
        val displayName = account.nonBlankString("display_name") ?: account.optString("username")
        val media = json.optJSONArray("media_attachments")?.optJSONObject(0)
        val content = json.nonBlankString("content")
            ?.let(HtmlTextUtils::plainText)
            ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, MASTODON_DESCRIPTION_MAX_CHARS) }
            ?.takeIf(String::isNotBlank)
        val contentWarning = json.nonBlankString("spoiler_text")
            ?.let(HtmlTextUtils::plainText)
            ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, MASTODON_DESCRIPTION_MAX_CHARS) }
            ?.takeIf(String::isNotBlank)
        val description = listOfNotNull(
            contentWarning?.let { "Content warning: $it" },
            content,
        ).joinToString("\n\n").takeIf(String::isNotBlank)
        return LinkPreviewInfo(
            LinkPreviewType.MASTODON_POST,
            displayName.requiredPreviewTitle(LinkPreviewType.MASTODON_POST),
            "@${account.optString("acct")}",
            description,
            media?.nonBlankString("preview_url") ?: account.nonBlankString("avatar"),
            url,
            details(
                "Replies" to json.optLong("replies_count").toString(),
                "Boosts" to json.optLong("reblogs_count").toString(),
                "Favourites" to json.optLong("favourites_count").toString(),
                "Published" to json.nonBlankString("created_at")?.dateOnly(),
            ),
        )
    }

    private const val MASTODON_DESCRIPTION_MAX_CHARS = 600
}

internal suspend fun HttpClient.loadMastodonPreview(url: String): LinkPreviewInfo {
    val (host, statusId) = MastodonLinkPreview.mastodonStatus(url)
        ?: throw LinkPreviewException("Invalid Mastodon post URL")
    return MastodonLinkPreview.parseMastodon(
        getTextOrThrow("https://$host/api/v1/statuses/${statusId.encodeURLPathPart()}"),
        url,
    )
}

package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.LinkPreviewDetail
import com.simon.harmonichackernews.data.LinkPreviewInfo
import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal object SubstackLinkPreview {
    internal fun isSubstackArticle(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return parsed.host.lowercase().endsWith(".substack.com") &&
            segments.size >= 2 && segments[0] == "p"
    }

    fun parseSubstackChannelImage(response: String): String? =
        Ksoup.parseXml(response).selectFirst("channel > image > url")?.text()
            ?.takeIf(String::isNotBlank)

    fun parseSubstackPage(
        response: String,
        url: String,
        publicationImageUrl: String? = null,
    ): LinkPreviewInfo {
        val document = Ksoup.parse(response, baseUri = url)
        val article = document.select("script[type=application/ld+json]")
            .firstNotNullOfOrNull { script ->
                runCatching { JsonObject(script.data()) }.getOrNull()
                    ?.takeIf { it.optString("@type") == "NewsArticle" }
            }
        val articleTitle = (
            article?.nonBlankString("headline")
                ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ).orEmpty().requiredPreviewTitle(LinkPreviewType.SUBSTACK_ARTICLE)
        val publicationTitle = article?.optJSONObject("publisher")?.nonBlankString("name")
            ?: article?.optJSONArray("author")?.optJSONObject(0)?.nonBlankString("name")
        val headerTitle = publicationTitle ?: articleTitle
        val published = article?.nonBlankString("datePublished")
            ?: document.selectFirst("time[datetime]")?.attr("datetime")
        return LinkPreviewInfo(
            type = LinkPreviewType.SUBSTACK_ARTICLE,
            title = headerTitle,
            subtitle = articleTitle.takeUnless { it == headerTitle },
            description = document.selectFirst(".available-content p, .dt-post-body p")?.text()
                ?.let { HtmlTextUtils.normalizeAndTruncatePlainText(it, SUBSTACK_DESCRIPTION_MAX_CHARS) }
                ?.takeIf(String::isNotBlank)
                ?: document.selectFirst("meta[property=og:description]")?.attr("content"),
            imageUrl = publicationImageUrl,
            url = url,
            details = listOfNotNull(
                published?.compactPublishedDate()?.let { compactDate ->
                    LinkPreviewDetail("Published", published, compactDate)
                },
            ),
        )
    }

    private const val SUBSTACK_DESCRIPTION_MAX_CHARS = 600

    private val monthAbbreviations = listOf(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    )

    private fun String.compactPublishedDate(): String? {
        Regex("^(\\d{4})-(\\d{2})-(\\d{2})").find(trim())?.let { match ->
            val year = match.groupValues[1].toIntOrNull() ?: return@let
            val month = match.groupValues[2].toIntOrNull()?.takeIf { it in 1..12 } ?: return@let
            val day = match.groupValues[3].toIntOrNull()?.takeIf { it in 1..31 } ?: return@let
            return "${monthAbbreviations[month - 1]} $day, $year"
        }
        val parts = trim().split(Regex("\\s+"))
        val monthIndex = parts.indexOfFirst { value ->
            monthAbbreviations.any { it.equals(value, ignoreCase = true) }
        }
        if (monthIndex <= 0 || monthIndex >= parts.lastIndex) return null
        val month = monthAbbreviations.first { it.equals(parts[monthIndex], ignoreCase = true) }
        val day = parts[monthIndex - 1].trim(',').toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val year = parts[monthIndex + 1].trim(',').toIntOrNull()?.takeIf { it in 1000..9999 } ?: return null
        return "$month $day, $year"
    }
}

internal suspend fun HttpClient.loadSubstackPreview(url: String): LinkPreviewInfo {
    val parsed = url.toNetworkUrlOrNull()
        ?: throw LinkPreviewException("Invalid Substack article URL")
    return withTimeout(SUBSTACK_REQUEST_TIMEOUT_MILLIS) {
        coroutineScope {
            val pageResponse = async { getTextOrThrow(url) }
            val publicationImage = async {
                loadSubstackPublicationImage("${parsed.scheme}://${parsed.host}/feed")
            }
            SubstackLinkPreview.parseSubstackPage(
                pageResponse.await(),
                url,
                publicationImage.await(),
            )
        }
    }
}

private suspend fun HttpClient.loadSubstackPublicationImage(feedUrl: String): String? = try {
    withTimeoutOrNull(SUBSTACK_FEED_TIMEOUT_MILLIS) {
        val feedHeader = getTextPrefixOrThrow(
            url = feedUrl,
            maxBytes = SUBSTACK_FEED_HEADER_MAX_BYTES,
            stopMarkers = listOf("</image>", "<item>"),
        )
        SubstackLinkPreview.parseSubstackChannelImage(feedHeader)
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    null
}

private suspend fun HttpClient.getTextPrefixOrThrow(
    url: String,
    maxBytes: Int,
    stopMarkers: List<String>,
): String = prepareGet(url).execute { response ->
    val channel = response.bodyAsChannel()
    try {
        if (response.status.value !in 200..299) {
            throw HttpStatusException(response.status.value, response.status.description, url)
        }
        val bytes = ByteArray(maxBytes)
        var size = 0
        var text = ""
        while (size < maxBytes) {
            val read = channel.readAvailable(
                bytes,
                size,
                minOf(SUBSTACK_FEED_READ_CHUNK_BYTES, maxBytes - size),
            )
            if (read < 0) break
            if (read == 0) continue
            size += read
            text = bytes.decodeToString(endIndex = size)
            if (stopMarkers.any(text::contains)) break
        }
        text
    } finally {
        channel.cancel()
    }
}

private const val SUBSTACK_REQUEST_TIMEOUT_MILLIS = 15_000L

private const val SUBSTACK_FEED_TIMEOUT_MILLIS = 5_000L

private const val SUBSTACK_FEED_HEADER_MAX_BYTES = 64 * 1024

private const val SUBSTACK_FEED_READ_CHUNK_BYTES = 4 * 1024

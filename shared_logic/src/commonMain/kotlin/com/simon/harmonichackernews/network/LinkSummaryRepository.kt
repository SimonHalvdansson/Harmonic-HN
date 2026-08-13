package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import com.simon.harmonichackernews.utils.HackerNewsLinks
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.utils.io.readAvailable
import kotlinx.io.IOException
import kotlin.time.Clock

data class LinkSummary(
    val title: String = "",
    val siteName: String = "",
    val author: String = "",
    val publishedTime: String = "",
    val language: String = "",
    val contentType: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val finalUrl: String = "",
)

object LinkSummaryCodec {
    fun encode(summary: LinkSummary): String = JsonObject()
        .put("title", summary.title)
        .put("site", summary.siteName)
        .put("author", summary.author)
        .put("published", summary.publishedTime)
        .put("language", summary.language)
        .put("type", summary.contentType)
        .put("description", summary.description)
        .put("image", summary.imageUrl)
        .put("url", summary.finalUrl)
        .toString()

    fun decode(serialized: String?): LinkSummary? {
        if (serialized.isNullOrEmpty()) return null
        return runCatching {
            val json = JsonObject(serialized)
            LinkSummary(
                title = json.optString("title", ""),
                siteName = json.optString("site", ""),
                author = json.optString("author", ""),
                publishedTime = json.optString("published", ""),
                language = json.optString("language", ""),
                contentType = json.optString("type", ""),
                description = json.optString("description", ""),
                imageUrl = json.optString("image", ""),
                finalUrl = json.optString("url", ""),
            )
        }.getOrNull()
    }
}

interface LinkSummaryRepository {
    suspend fun load(pageUrl: String, fallbackTitle: String? = null): LinkSummary
}

class KtorLinkSummaryRepository(
    private val client: HttpClient,
    private val linkPreviews: LinkPreviewRepository = KtorLinkPreviewRepository(client),
) : LinkSummaryRepository {
    override suspend fun load(pageUrl: String, fallbackTitle: String?): LinkSummary {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
            ?: throw LinkPreviewException("This link does not use HTTP or HTTPS")

        LinkSummaryParser.hackerNewsItemId(normalizedUrl)?.let { itemId ->
            val response = fetchText(
                "https://hacker-news.firebaseio.com/v0/item/$itemId.json",
                "application/json",
            )
            return LinkSummaryParser.extractHackerNewsItem(
                response.body,
                normalizedUrl,
                fallbackTitle,
            ) ?: throw LinkPreviewException("Hacker News did not return this item")
        }

        if (LinkPreviewUrls.isWikipediaUrl(normalizedUrl)) {
            val wikipedia = linkPreviews.getWikipediaInfo(normalizedUrl)
            val description = LinkPreviewParsers.firstWikipediaParagraph(wikipedia.summary)
            if (description.isEmpty()) {
                throw LinkPreviewException("Wikipedia did not return a summary")
            }
            return LinkSummary(
                title = LinkSummaryParser.clean(fallbackTitle),
                siteName = "Wikipedia",
                language = "en",
                contentType = "application/json",
                description = description,
                finalUrl = normalizedUrl,
            )
        }

        val youtubeOEmbedUrl = LinkSummaryParser.buildYoutubeOEmbedUrl(normalizedUrl)
        val redditOEmbedUrl = LinkSummaryParser.buildRedditOEmbedUrl(normalizedUrl)
        val oEmbedUrl = youtubeOEmbedUrl ?: redditOEmbedUrl
        if (oEmbedUrl != null) {
            val response = fetchText(oEmbedUrl, "application/json")
            return LinkSummaryParser.extractOEmbed(
                response.body,
                normalizedUrl,
                if (youtubeOEmbedUrl != null) "YouTube" else "Reddit",
            ) ?: throw LinkPreviewException("The provider did not return link information")
        }

        val response = fetchText(
            normalizedUrl,
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        )
        if (
            response.contentType.isNotEmpty() &&
            !response.contentType.contains("html", ignoreCase = true) &&
            !response.contentType.contains("xml", ignoreCase = true)
        ) {
            throw LinkPreviewException(
                "This link contains ${response.contentType}, not a web page",
            )
        }
        return LinkSummaryParser.extract(
            response.body,
            fallbackTitle,
            response.contentType,
            response.finalUrl,
        )
    }

    private suspend fun fetchText(url: String, accept: String): FetchedText {
        val response = client.get(url) { header(HttpHeaders.Accept, accept) }
        if (response.status.value !in 200..299) {
            throw LinkPreviewException("The page returned HTTP ${response.status.value}")
        }
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_RESPONSE_BYTES) {
            throw LinkPreviewException("The page is too large to preview")
        }
        val channel = response.bodyAsChannel()
        val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
        var size = 0
        while (size < bytes.size) {
            val read = channel.readAvailable(bytes, size, bytes.size - size)
            if (read == -1) break
            if (read == 0) continue
            size += read
        }
        if (size > MAX_RESPONSE_BYTES) {
            throw LinkPreviewException("The page is too large to preview")
        }
        return FetchedText(
            body = bytes.decodeToString(0, size),
            contentType = LinkSummaryParser.normalizeContentType(
                response.headers[HttpHeaders.ContentType],
            ),
            finalUrl = response.call.request.url.toString(),
        )
    }

    private data class FetchedText(
        val body: String,
        val contentType: String,
        val finalUrl: String,
    )

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

object LinkSummaryParser {
    const val HACKER_NEWS_ITEM_CONTENT_TYPE = "application/vnd.hacker-news.item+json"
    private const val HACKER_NEWS_COMMENT_SITE_NAME = "Hacker News · comment"
    private const val HACKER_NEWS_STORY_SITE_NAME = "Hacker News · story"
    private const val MAX_DESCRIPTION_CHARS = 600
    private const val YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
    private const val REDDIT_OEMBED_ENDPOINT = "https://www.reddit.com/oembed"
    private val youtubeVideoUrlPattern = Regex(
        "^https?://(?:(?:www|m|music)\\.)?(?:youtube\\.com|youtube-nocookie\\.com)/" +
            "(?:watch\\?(?:[^#]*&)?v=|embed/|v/|shorts/|live/)" +
            "([A-Za-z0-9_-]{11})(?:[?&#/].*)?$" +
            "|^https?://(?:www\\.)?youtu\\.be/([A-Za-z0-9_-]{11})(?:[?&#/].*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val imageSelectors = arrayOf(
        "meta[property=og:image:secure_url]",
        "meta[property=og:image:url]",
        "meta[property=og:image]",
        "meta[name=twitter:image:src]",
        "meta[name=twitter:image]",
        "meta[itemprop=image]",
        "link[rel=image_src]",
    )

    fun buildYoutubeOEmbedUrl(pageUrl: String?): String? {
        if (!isYoutubeVideoUrl(pageUrl)) return null
        return URLBuilder(YOUTUBE_OEMBED_ENDPOINT).apply {
            parameters.append("url", pageUrl.orEmpty())
            parameters.append("format", "json")
        }.buildString()
    }

    fun isYoutubeVideoUrl(url: String?): Boolean =
        !url.isNullOrEmpty() && youtubeVideoUrlPattern.matches(url)

    fun buildRedditOEmbedUrl(pageUrl: String?): String? {
        if (!isRedditPostUrl(pageUrl)) return null
        return URLBuilder(REDDIT_OEMBED_ENDPOINT).apply {
            parameters.append("url", pageUrl.orEmpty())
            parameters.append("format", "json")
        }.buildString()
    }

    fun isRedditPostUrl(url: String?): Boolean {
        val parsed = url?.toNetworkUrlOrNull() ?: return false
        if (!isHttpScheme(parsed)) return false
        val host = parsed.host.lowercase()
        if (host == "redd.it" || host == "www.redd.it") {
            return parsed.pathSegments.any(String::isNotEmpty)
        }
        if (host != "reddit.com" && !host.endsWith(".reddit.com")) return false
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        return segments.indices.any { index ->
            segments[index].equals("comments", ignoreCase = true) &&
                index + 1 < segments.size && segments[index + 1].isNotEmpty()
        }
    }

    fun extractOEmbed(
        json: String?,
        pageUrl: String?,
        fallbackProviderName: String,
    ): LinkSummary? {
        if (json.isNullOrEmpty()) return null
        return runCatching {
            val value = JsonObject(json)
            LinkSummary(
                title = value.optString("title"),
                siteName = value.optString("provider_name", fallbackProviderName),
                author = value.optString("author_name"),
                contentType = "application/json",
                imageUrl = normalizeHttpUrl(value.optString("thumbnail_url", null)).orEmpty(),
                finalUrl = pageUrl.orEmpty(),
            )
        }.getOrNull()
    }

    fun extract(
        html: String,
        fallbackTitle: String?,
        contentType: String?,
        finalUrl: String,
    ): LinkSummary {
        val document = Ksoup.parse(html, baseUri = finalUrl)
        val title = firstNonEmpty(
            metaContent(document, "meta[property=og:title]"),
            metaContent(document, "meta[name=twitter:title]"),
            document.title(),
            fallbackTitle,
        )
        val siteName = firstNonEmpty(
            metaContent(document, "meta[property=og:site_name]"),
            finalUrl.toNetworkUrlOrNull()?.host,
        )
        val author = firstNonEmpty(
            metaContent(document, "meta[name=author]"),
            metaContent(document, "meta[property=article:author]"),
            metaContent(document, "meta[name=byl]"),
            elementText(document.selectFirst("[rel=author]")),
        )
        val publishedTime = firstNonEmpty(
            metaContent(document, "meta[property=article:published_time]"),
            metaContent(document, "meta[itemprop=datePublished]"),
            metaContent(document, "meta[name=date]"),
            elementAttribute(document.selectFirst("time[datetime]"), "datetime"),
        )
        val language = firstNonEmpty(
            elementAttribute(document.selectFirst("html[lang]"), "lang"),
            metaContent(document, "meta[http-equiv=content-language]"),
        )
        val metadataDescription = firstNonEmpty(
            metaContent(document, "meta[property=og:description]"),
            metaContent(document, "meta[name=description]"),
            metaContent(document, "meta[name=twitter:description]"),
        )
        return LinkSummary(
            title = clean(title),
            siteName = clean(siteName),
            author = clean(author),
            publishedTime = clean(publishedTime),
            language = clean(language),
            contentType = clean(contentType),
            description = truncate(
                HtmlDescriptionExtractor.chooseDescription(
                    metadataDescription,
                    document,
                    title,
                    fallbackTitle,
                ),
                MAX_DESCRIPTION_CHARS,
            ),
            imageUrl = extractImageUrl(document, finalUrl),
            finalUrl = finalUrl,
        )
    }

    fun hackerNewsItemId(url: String): String? {
        val parsed = url.toNetworkUrlOrNull() ?: return null
        if (!parsed.host.equals(HackerNewsLinks.HOST, ignoreCase = true) ||
            parsed.encodedPath != "/item"
        ) return null
        val candidate = parsed.fragment.takeIf(::isPositiveInteger) ?: parsed.queryParameter("id")
        return candidate?.takeIf(::isPositiveInteger)
    }

    fun extractHackerNewsItem(
        json: String?,
        pageUrl: String,
        fallbackTitle: String?,
    ): LinkSummary? {
        if (json.isNullOrBlank() || json.trim() == "null") return null
        return runCatching {
            val item = JsonObject(json)
            if (item.optBoolean("deleted") || item.optBoolean("dead")) return null
            val comment = item.optString("type") == "comment"
            val author = clean(item.optString("by"))
            val title = if (comment) {
                "Comment by ${firstNonEmpty(author, "unknown")}" 
            } else {
                firstNonEmpty(clean(item.optString("title")), fallbackTitle)
            }
            if (title.isNullOrEmpty()) return null
            val metadata = buildHackerNewsMetadata(item, comment, author)
            val body = cleanHackerNewsText(item.optString("text"))
            val description = when {
                body.isEmpty() -> metadata
                metadata.isEmpty() -> body
                else -> "$metadata — $body"
            }
            LinkSummary(
                title = title,
                siteName = if (comment) HACKER_NEWS_COMMENT_SITE_NAME else HACKER_NEWS_STORY_SITE_NAME,
                author = author,
                publishedTime = item.optInt("time").takeIf { it > 0 }?.let {
                    RelativeTimeFormatter.format(it.toLong(), Clock.System.now().toEpochMilliseconds())
                }.orEmpty(),
                language = "en",
                contentType = HACKER_NEWS_ITEM_CONTENT_TYPE,
                description = truncate(description, MAX_DESCRIPTION_CHARS),
                finalUrl = pageUrl,
            )
        }.getOrNull()
    }

    fun normalizeContentType(contentType: String?): String =
        contentType?.substringBefore(';')?.trim().orEmpty()

    fun normalizeHttpUrl(url: String?): String? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        return parsed.takeIf(::isHttpScheme)?.toString()
    }

    fun isLikelyImageUrl(url: String?): Boolean {
        val path = url?.toNetworkUrlOrNull()?.encodedPath?.lowercase() ?: return false
        return imageExtensions.any(path::endsWith)
    }

    fun clean(value: String?): String = value.orEmpty()
        .replace('\u00a0', ' ')
        .replace("\\s+".toRegex(), " ")
        .trim()

    private fun buildHackerNewsMetadata(
        item: JsonObject,
        comment: Boolean,
        author: String,
    ): String {
        val parts = mutableListOf<String>()
        if (!comment && item.has("score")) {
            parts += formatCount(item.optInt("score"), "point", "points")
        }
        if (author.isNotEmpty()) parts += "by $author"
        item.optInt("time").takeIf { it > 0 }?.let {
            parts += RelativeTimeFormatter.format(
                it.toLong(),
                Clock.System.now().toEpochMilliseconds(),
            )
        }
        if (comment) {
            item.optJSONArray("kids")?.length()?.takeIf { it > 0 }?.let {
                parts += formatCount(it, "reply", "replies")
            }
        } else if (item.has("descendants")) {
            parts += formatCount(item.optInt("descendants"), "comment", "comments")
        }
        return parts.joinToString(" · ")
    }

    private fun cleanHackerNewsText(html: String?): String =
        if (html.isNullOrEmpty()) "" else clean(Ksoup.parseBodyFragment(html).body().text())

    private fun formatCount(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private fun extractImageUrl(document: Document, baseUrl: String): String {
        val parsedBase = baseUrl.toNetworkUrlOrNull()
        for (selector in imageSelectors) {
            val element = document.selectFirst(selector) ?: continue
            val candidate = element.attr(if (element.tagName() == "link") "href" else "content")
                .trim()
            if (candidate.isEmpty() || candidate.startsWith("data:")) continue
            val parsedImage = parsedBase?.resolve(candidate) ?: candidate.toNetworkUrlOrNull()
            if (parsedImage != null && isHttpScheme(parsedImage)) return parsedImage.toString()
        }
        return ""
    }

    private fun metaContent(document: Document, selector: String): String =
        elementAttribute(document.selectFirst(selector), "content")

    private fun elementAttribute(element: Element?, attribute: String): String =
        element?.attr(attribute).orEmpty()

    private fun elementText(element: Element?): String = element?.text().orEmpty()

    private fun firstNonEmpty(vararg values: String?): String =
        values.firstOrNull { clean(it).isNotEmpty() }.orEmpty()

    private fun truncate(value: String?, maxChars: Int): String {
        val cleaned = clean(value)
        if (cleaned.length <= maxChars) return cleaned
        val lastSpace = cleaned.lastIndexOf(' ', maxChars - 1)
        val end = if (lastSpace >= maxChars * 0.75f) lastSpace else maxChars
        return cleaned.substring(0, end).trim() + "…"
    }

    private fun isPositiveInteger(value: String?): Boolean =
        value?.all(Char::isDigit) == true && (value.toIntOrNull() ?: 0) > 0

    private fun isHttpScheme(url: NetworkUrl): Boolean = url.scheme == "http" || url.scheme == "https"

    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif")
}

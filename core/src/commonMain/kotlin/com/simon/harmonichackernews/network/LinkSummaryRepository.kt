package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.utils.HtmlTextUtils
import com.simon.harmonichackernews.utils.RelativeTimeFormatter
import com.simon.harmonichackernews.utils.HackerNewsLinks
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.http.URLBuilder
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readAvailable
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
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
    val commentTextVersion: Int = 0,
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
        .put("commentTextVersion", summary.commentTextVersion)
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
                commentTextVersion = json.optInt("commentTextVersion", 0),
            )
        }.getOrNull()
    }
}

interface LinkSummaryRepository {
    suspend fun load(pageUrl: String, fallbackTitle: String? = null): LinkSummary
}

class KtorLinkSummaryRepository(
    private val client: suspend () -> HttpClient,
    private val linkPreviews: LinkPreviewRepository = KtorLinkPreviewRepository(client),
    private val parsingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : LinkSummaryRepository {
    constructor(
        client: HttpClient,
        linkPreviews: LinkPreviewRepository = KtorLinkPreviewRepository(client),
        parsingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this({ client }, linkPreviews, parsingDispatcher)
    override suspend fun load(pageUrl: String, fallbackTitle: String?): LinkSummary =
        withContext(parsingDispatcher) {
            val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
                ?: throw LinkPreviewException("This link does not use HTTP or HTTPS")

            LinkSummaryParser.buildXkcdApiUrl(normalizedUrl)?.let { apiUrl ->
                val response = fetchText(apiUrl, "application/json")
                return@withContext LinkSummaryParser.extractXkcd(response.body, normalizedUrl)
                    ?: throw LinkPreviewException("xkcd did not return a comic image")
            }

            LinkSummaryParser.hackerNewsItemId(normalizedUrl)?.let { itemId ->
                val response = fetchText(
                    "https://hacker-news.firebaseio.com/v0/item/$itemId.json",
                    "application/json",
                )
                return@withContext LinkSummaryParser.extractHackerNewsItem(
                    response.body,
                    normalizedUrl,
                    fallbackTitle,
                ) ?: throw LinkPreviewException("Hacker News did not return this item")
            }

            LinkPreviewUrls.arxivId(normalizedUrl)?.let { arxivId ->
                // PDF and HTML references share the small abstract page's citation metadata.
                val response = fetchText("https://arxiv.org/abs/$arxivId", "text/html")
                return@withContext LinkSummaryParser.extractArxiv(
                    response.body,
                    arxivId,
                    normalizedUrl,
                ) ?: throw LinkPreviewException("arXiv did not return this paper's title")
            }

            if (LinkPreviewUrls.isWikipediaUrl(normalizedUrl)) {
                val wikipedia = linkPreviews.getWikipediaInfo(normalizedUrl)
                val description = LinkPreviewParsers.firstWikipediaParagraph(wikipedia.summary)
                if (description.isEmpty()) {
                    throw LinkPreviewException("Wikipedia did not return a summary")
                }
                return@withContext LinkSummary(
                    title = LinkSummaryParser.clean(wikipedia.title)
                        .ifEmpty { LinkSummaryParser.clean(fallbackTitle) },
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
                return@withContext LinkSummaryParser.extractOEmbed(
                    response.body,
                    normalizedUrl,
                    if (youtubeOEmbedUrl != null) "YouTube" else "Reddit",
                ) ?: throw LinkPreviewException("The provider did not return link information")
            }

            val response = fetchText(
                normalizedUrl,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                previewTitle = fallbackTitle.orEmpty(),
            )
            if (response.contentType.startsWith("image/", ignoreCase = true)) {
                return@withContext LinkSummaryParser.directImage(
                    imageUrl = response.finalUrl,
                    fallbackTitle = fallbackTitle,
                    contentType = response.contentType,
                )
            }
            if (
                response.contentType.isNotEmpty() &&
                !response.contentType.contains("html", ignoreCase = true) &&
                !response.contentType.contains("xml", ignoreCase = true)
            ) {
                throw LinkPreviewException(
                    "This link contains ${response.contentType}, not a web page",
                )
            }
            val summary = response.summary ?: LinkSummaryParser.extract(
                response.body, fallbackTitle, response.contentType, response.finalUrl,
            )
            val siteImage = SiteImageResolvers.resolve(response.finalUrl) { url ->
                fetchText(url, "application/json").body
            }
            if (siteImage != null) summary.copy(imageUrl = siteImage) else summary
        }

    private suspend fun fetchText(
        url: String,
        accept: String,
        previewTitle: String? = null,
    ): FetchedText =
        client().prepareGet(url) {
            header(HttpHeaders.Accept, accept)
            // These reads intentionally stop at headers or a metadata prefix. HttpCache would
            // buffer the entire response first; preview results have their own persistent cache.
            header(HttpHeaders.CacheControl, "no-store")
        }.execute { response ->
            val channel = response.bodyAsChannel()
            try {
                if (response.status.value !in 200..299) {
                    throw LinkPreviewException("The page returned HTTP ${response.status.value}")
                }
                val contentType = LinkSummaryParser.normalizeContentType(
                    response.headers[HttpHeaders.ContentType],
                )
                val finalUrl = response.call.request.url.toString()
                // Images need only their headers. For text, retain the same bounded UTF-8 prefix,
                // allocating for bytes actually received instead of reserving the whole budget.
                val body = if (contentType.startsWith("image/", ignoreCase = true)) {
                    ""
                } else {
                    val bytes = Buffer()
                    val chunk = ByteArray(8 * 1024)
                    // Provider JSON has its own budget. HTML (including arXiv) is capped at 1 MiB.
                    val limit = if (accept == "application/json") MAX_PROVIDER_BYTES else MAX_HTML_BYTES
                    val head = if (previewTitle != null &&
                        (contentType.isEmpty() || contentType.contains("html", ignoreCase = true))
                    ) HtmlPreviewHeadBoundary() else null
                    while (bytes.size < limit) {
                        val read = channel.readAvailable(
                            chunk,
                            0,
                            minOf(chunk.size, limit - bytes.size.toInt()),
                        )
                        if (read == -1) break
                        if (read > 0) {
                            bytes.write(chunk, 0, read)
                            val headEnd = head?.accept(chunk, read)
                            if (headEnd != null) {
                                val prefix = bytes.readByteArray()
                                val summary = LinkSummaryParser.extractCompleteHead(
                                    prefix.decodeToString(endIndex = headEnd),
                                    previewTitle, contentType, finalUrl,
                                )
                                if (summary != null) {
                                    return@execute FetchedText("", contentType, finalUrl, summary)
                                }
                                bytes.write(prefix)
                            }
                        }
                    }
                    bytes.readByteArray().decodeToString()
                }
                FetchedText(body = body, contentType = contentType, finalUrl = finalUrl)
            } finally {
                // Stop oversized bodies at the prefix limit and release every response path.
                channel.cancel()
            }
        }

    private data class FetchedText(
        val body: String,
        val contentType: String,
        val finalUrl: String,
        val summary: LinkSummary? = null,
    )

    private companion object {
        const val MAX_HTML_BYTES = 1024 * 1024
        const val MAX_PROVIDER_BYTES = 2 * 1024 * 1024
    }
}

object LinkSummaryParser {
    const val XKCD_COMIC_CONTENT_TYPE = "application/vnd.xkcd.comic+json"
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
    private val whitespacePattern = Regex("\\s+")

    fun buildXkcdApiUrl(pageUrl: String?): String? {
        val parsed = pageUrl?.toNetworkUrlOrNull() ?: return null
        if (!isHttpScheme(parsed) || parsed.host.lowercase() !in
            setOf("xkcd.com", "www.xkcd.com", "m.xkcd.com")
        ) return null
        val path = parsed.encodedPath
        if (path.isEmpty() || path == "/") return "https://xkcd.com/info.0.json"
        val comic = path.removePrefix("/").removeSuffix("/")
        if (!isPositiveInteger(comic)) return null
        return "https://xkcd.com/$comic/info.0.json"
    }

    internal fun extractXkcd(json: String, pageUrl: String): LinkSummary? = runCatching {
        val comic = JsonObject(json)
        val imageUrl = normalizeHttpUrl(comic.optString("img")) ?: return null
        val title = clean(comic.optString("safe_title")).ifEmpty { clean(comic.optString("title")) }
        if (title.isEmpty() || comic.optInt("num") <= 0) return null
        LinkSummary(
            title = title,
            siteName = "xkcd",
            author = "Randall Munroe",
            language = "en",
            contentType = XKCD_COMIC_CONTENT_TYPE,
            description = clean(comic.optString("alt")),
            imageUrl = imageUrl,
            finalUrl = pageUrl,
        )
    }.getOrNull()

    internal fun extractArxiv(html: String, arxivId: String, pageUrl: String): LinkSummary? {
        val document = Ksoup.parse(html)
        fun citation(name: String) = clean(
            document.selectFirst("meta[name=citation_$name]")?.attr("content"),
        )
        if (citation("arxiv_id").substringBefore('v') != arxivId.substringBefore('v')) return null
        val title = citation("title").takeIf(String::isNotBlank) ?: return null
        return LinkSummary(
            title = title,
            siteName = "arXiv",
            author = document.select("meta[name=citation_author]")
                .map { clean(it.attr("content")) }.filter(String::isNotBlank).joinToString(", "),
            publishedTime = citation("date").replace('/', '-'),
            contentType = "text/html",
            description = truncate(citation("abstract"), MAX_DESCRIPTION_CHARS),
            finalUrl = pageUrl,
        )
    }

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
        return extract(Ksoup.parse(html, baseUri = finalUrl), fallbackTitle, contentType, finalUrl)
    }

    internal fun extractCompleteHead(
        html: String,
        fallbackTitle: String?,
        contentType: String,
        finalUrl: String,
    ): LinkSummary? {
        // A head boundary alone is insufficient: the body can supply a description, byline,
        // date or image. Only skip it when the head already supplies every such field.
        val document = Ksoup.parse(html, baseUri = finalUrl)
        val summary = extract(document, null, contentType, finalUrl)
        return summary.takeIf {
            it.title.isNotBlank() && it.imageUrl.isNotBlank() &&
                it.author.isNotBlank() && it.publishedTime.isNotBlank() &&
                HtmlDescriptionExtractor.isMeaningful(it.description, it.title, fallbackTitle)
        }
    }

    private fun extract(
        document: Document,
        fallbackTitle: String?,
        contentType: String?,
        finalUrl: String,
    ): LinkSummary {
        // CSS selection walks the document. Index the metadata once instead of repeating that
        // traversal for every OpenGraph, Twitter and article field.
        val metadata = MetadataIndex(document)
        val title = firstNonEmpty(
            metadata.property("og:title"),
            metadata.name("twitter:title"),
            document.title(),
            fallbackTitle,
        )
        val siteName = firstNonEmpty(
            metadata.property("og:site_name"),
            finalUrl.toNetworkUrlOrNull()?.host,
        )
        val author = firstNonEmpty(
            metadata.name("author"),
            metadata.property("article:author"),
            metadata.name("byl"),
            elementText(document.selectFirst("[rel=author]")),
        )
        val publishedTime = firstNonEmpty(
            metadata.property("article:published_time"),
            metadata.itemProp("datePublished"),
            metadata.name("date"),
            elementAttribute(document.selectFirst("time[datetime]"), "datetime"),
        )
        val language = firstNonEmpty(
            elementAttribute(document.selectFirst("html[lang]"), "lang"),
            metadata.httpEquiv("content-language"),
        )
        val metadataDescription = firstNonEmpty(
            metadata.property("og:description"),
            metadata.name("description"),
            metadata.name("twitter:description"),
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
            imageUrl = extractImageUrl(metadata, document, finalUrl),
            finalUrl = finalUrl,
        )
    }

    fun directImage(
        imageUrl: String,
        fallbackTitle: String?,
        contentType: String,
    ): LinkSummary {
        val parsed = imageUrl.toNetworkUrlOrNull()
        val fallback = clean(fallbackTitle)
            .takeUnless { normalizeHttpUrl(it) != null }
        val fileName = parsed?.pathSegments
            ?.lastOrNull(String::isNotBlank)
            ?.substringBefore('?')
            .orEmpty()
        return LinkSummary(
            title = firstNonEmpty(fallback, fileName, "Image"),
            siteName = parsed?.host.orEmpty(),
            contentType = clean(contentType),
            imageUrl = imageUrl,
            finalUrl = imageUrl,
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

    fun isHackerNewsStory(summary: LinkSummary): Boolean =
        summary.contentType == HACKER_NEWS_ITEM_CONTENT_TYPE &&
            summary.siteName == HACKER_NEWS_STORY_SITE_NAME

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
            if (title.isEmpty()) return null
            val metadata = buildHackerNewsMetadata(item, comment, author)
            val body = cleanHackerNewsText(item.optString("text"))
            val description = when {
                comment -> body
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
                description = if (comment) {
                    HtmlTextUtils
                        .normalizeAndTruncatePlainText(description, MAX_DESCRIPTION_CHARS)
                } else truncate(description, MAX_DESCRIPTION_CHARS),
                finalUrl = pageUrl,
                commentTextVersion = if (comment) 1 else 0,
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
        .replace(whitespacePattern, " ")
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

    private fun cleanHackerNewsText(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val output = StringBuilder()
        fun breakLine(count: Int) {
            while (output.isNotEmpty() && output.last() == ' ') output.deleteAt(output.lastIndex)
            if (output.isEmpty()) return
            val existing = output.takeLastWhile { it == '\n' }.length
            repeat((count - existing).coerceAtLeast(0)) { output.append('\n') }
        }
        fun visit(node: Node, preformatted: Boolean = false) {
            when (node) {
                is TextNode -> output.append(
                    if (preformatted) node.getWholeText() else node.getWholeText().replace(whitespacePattern, " "),
                )
                is Element -> {
                    val tag = node.normalName()
                    if (tag in listOf("script", "style")) return
                    if (tag == "br") { breakLine(1); return }
                    val block = tag in listOf("p", "div", "pre", "blockquote", "ul", "ol", "li")
                    if (block) breakLine(if (tag == "li") 1 else 2)
                    node.childNodes().forEach { visit(it, preformatted || tag == "pre") }
                    if (block) breakLine(if (tag == "li") 1 else 2)
                }
            }
        }
        visit(Ksoup.parseBodyFragment(html).body())
        return output.toString().replace('\u00a0', ' ').trim()
    }

    private fun formatCount(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

    private fun extractImageUrl(
        metadata: MetadataIndex,
        document: Document,
        baseUrl: String,
    ): String {
        val parsedBase = baseUrl.toNetworkUrlOrNull()
        val candidates = arrayOf(
            metadata.property("og:image:secure_url"),
            metadata.property("og:image:url"),
            metadata.property("og:image"),
            metadata.name("twitter:image:src"),
            metadata.name("twitter:image"),
            metadata.itemProp("image"),
            document.selectFirst("link[rel=image_src]")?.attr("href").orEmpty(),
        )
        for (value in candidates) {
            val candidate = value.trim()
            if (candidate.isEmpty() || candidate.startsWith("data:")) continue
            val parsedImage = parsedBase?.resolve(candidate) ?: candidate.toNetworkUrlOrNull()
            if (parsedImage != null && isHttpScheme(parsedImage)) return parsedImage.toString()
        }
        return ""
    }

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

    private class MetadataIndex(document: Document) {
        private val properties = mutableMapOf<String, String>()
        private val names = mutableMapOf<String, String>()
        private val itemProps = mutableMapOf<String, String>()
        private val httpEquivs = mutableMapOf<String, String>()

        init {
            for (element in document.select("meta")) {
                val content = element.attr("content")
                putFirst(properties, element.attr("property"), content)
                putFirst(names, element.attr("name"), content)
                putFirst(itemProps, element.attr("itemprop"), content)
                putFirst(httpEquivs, element.attr("http-equiv"), content)
            }
        }

        fun property(key: String): String = properties[key].orEmpty()
        fun name(key: String): String = names[key].orEmpty()
        fun itemProp(key: String): String = itemProps[key].orEmpty()
        fun httpEquiv(key: String): String = httpEquivs[key].orEmpty()

        private fun putFirst(target: MutableMap<String, String>, key: String, value: String) {
            if (key.isNotEmpty() && key !in target) target[key] = value
        }
    }

    private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".avif")
}

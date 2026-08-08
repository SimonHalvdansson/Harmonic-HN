package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.linkpreview.WikipediaGetter
import com.simon.harmonichackernews.linkpreview.WikipediaGetter.getFirstParagraphText
import com.simon.harmonichackernews.linkpreview.WikipediaGetter.getInfo
import com.simon.harmonichackernews.linkpreview.WikipediaGetter.isValidWikipediaUrl
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor.chooseDescription
import com.simon.harmonichackernews.utils.Utils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.min
import org.json.JSONObject
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element

object LinkSummaryLoader {
    private const val HACKER_NEWS_ITEM_CONTENT_TYPE = "application/vnd.hacker-news.item+json"
    private const val HACKER_NEWS_COMMENT_SITE_NAME = "Hacker News · comment"
    private const val HACKER_NEWS_STORY_SITE_NAME = "Hacker News · story"
    private const val HACKER_NEWS_ITEM_API = "https://hacker-news.firebaseio.com/v0/item/"
    private const val MAX_DESCRIPTION_CHARS = 600
    private val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private const val YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
    private const val REDDIT_OEMBED_ENDPOINT = "https://www.reddit.com/oembed"
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val YOUTUBE_VIDEO_URL_PATTERN: Pattern = Pattern.compile(
        ("^https?://(?:(?:www|m|music)\\.)?(?:youtube\\.com|youtube-nocookie\\.com)/"
                + "(?:watch\\?(?:[^#]*&)?v=|embed/|v/|shorts/|live/)"
                + "([A-Za-z0-9_-]{11})(?:[?&#/].*)?$"
                + "|^https?://(?:www\\.)?youtu\\.be/([A-Za-z0-9_-]{11})(?:[?&#/].*)?$"),
        Pattern.CASE_INSENSITIVE
    )
    private val IMAGE_SELECTORS: Array<String> = arrayOf(
        "meta[property=og:image:secure_url]",
        "meta[property=og:image:url]",
        "meta[property=og:image]",
        "meta[name=twitter:image:src]",
        "meta[name=twitter:image]",
        "meta[itemprop=image]",
        "link[rel=image_src]"
    )

    fun load(
        context: Context?,
        pageUrl: String,
        fallbackTitle: String?,
        callback: Callback
    ): SummaryRequest {
        val parsedUrl = pageUrl.toNetworkUrlOrNull()
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            postFailure(callback, "This link does not use HTTP or HTTPS")
            return SummaryRequest {}
        }

        val normalizedPageUrl = parsedUrl.toString()
        val hackerNewsItemId = getHackerNewsItemId(parsedUrl)
        val hackerNewsItemRequest = !TextUtils.isEmpty(hackerNewsItemId)
        val wikipediaSummaryRequest = isValidWikipediaUrl(normalizedPageUrl)
        val youtubeOEmbedUrl = buildYoutubeOEmbedUrl(normalizedPageUrl)
        val youtubeOEmbedRequest = !TextUtils.isEmpty(youtubeOEmbedUrl)
        val redditOEmbedUrl = buildRedditOEmbedUrl(normalizedPageUrl)
        val redditOEmbedRequest = !TextUtils.isEmpty(redditOEmbedUrl)
        val oEmbedRequest = youtubeOEmbedRequest || redditOEmbedRequest
        val cached = StoryPreviewImageLoader.getCachedLinkSummary(context, normalizedPageUrl)
        if (cached != null && (!hackerNewsItemRequest || isHackerNewsItemResult(cached))
            && (!oEmbedRequest || "application/json" == cached.contentType)
            && (!wikipediaSummaryRequest || "application/json" == cached.contentType)
        ) {
            MAIN_HANDLER.post(Runnable { callback.onSuccess(cached) })
            return SummaryRequest {}
        }

        if (hackerNewsItemRequest && hackerNewsItemId != null) {
            return LinkSummaryLoader.loadHackerNewsItem(
                context,
                normalizedPageUrl,
                hackerNewsItemId,
                fallbackTitle,
                callback
            )
        }

        if (wikipediaSummaryRequest) {
            return loadWikipediaSummary(
                context,
                normalizedPageUrl,
                fallbackTitle,
                callback
            )
        }

        val requestUrl = when {
            youtubeOEmbedRequest -> youtubeOEmbedUrl.orEmpty()
            redditOEmbedRequest -> redditOEmbedUrl.orEmpty()
            else -> normalizedPageUrl
        }
        val request = HttpRequest.Builder()
            .url(requestUrl)
            .header(
                "Accept", if (oEmbedRequest)
                    "application/json"
                else
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .get()
            .build()
        val call = NetworkComponent.httpClientInstance.newCall(request)
        call.enqueue(object : HttpCallback {
            override fun onFailure(call: HttpCall, e: IOException) {
                if (!call.isCanceled()) {
                    LinkSummaryLoader.postFailure(callback, getFailureMessage(e))
                }
            }

            override fun onResponse(call: HttpCall, response: HttpResponse) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful) {
                            postFailure(
                                callback,
                                "The page returned HTTP " + closeableResponse.code
                            )
                            return
                        }
                        if (closeableResponse.body == null) {
                            postFailure(callback, "The page did not return any content")
                            return
                        }

                        val contentType =
                            normalizeContentType(closeableResponse.header("Content-Type", ""))
                        if (!oEmbedRequest && !TextUtils.isEmpty(contentType) && !contentType.lowercase()
                                .contains("html") && !contentType.lowercase().contains("xml")
                        ) {
                            postFailure(
                                callback,
                                "This link contains " + contentType + ", not a web page"
                            )
                            return
                        }

                        if (oEmbedRequest) {
                            val result = extractOEmbedSummary(
                                readBoundedBody(closeableResponse.body),
                                normalizedPageUrl,
                                if (youtubeOEmbedRequest) "YouTube" else "Reddit"
                            )
                            if (result == null) {
                                postFailure(
                                    callback, (if (youtubeOEmbedRequest) "YouTube" else "Reddit")
                                            + " did not return link information"
                                )
                                return
                            }
                            StoryPreviewImageLoader.saveCachedLinkSummary(
                                context,
                                normalizedPageUrl,
                                result
                            )
                            MAIN_HANDLER.post(Runnable { callback.onSuccess(result) })
                            return
                        }

                        val finalUrl = closeableResponse.requestUrl.toString()
                        val result = extract(
                            readBoundedBody(closeableResponse.body),
                            fallbackTitle,
                            contentType,
                            finalUrl
                        )
                        StoryPreviewImageLoader.saveCachedLinkSummary(
                            context,
                            parsedUrl.toString(),
                            result
                        )
                        MAIN_HANDLER.post(Runnable { callback.onSuccess(result) })
                    }
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        LinkSummaryLoader.postFailure(callback, getFailureMessage(e))
                    }
                }
            }
        })
        return SummaryRequest { call.cancel() }
    }

    fun isHackerNewsItemResult(result: Result?): Boolean {
        return result != null
                && HACKER_NEWS_ITEM_CONTENT_TYPE == result.contentType
    }

    private fun getHackerNewsItemId(url: NetworkUrl): String? {
        if (!"news.ycombinator.com".equals(url.host, ignoreCase = true)
            || "/item" != url.encodedPath
        ) {
            return null
        }

        val fragment = url.fragment
        val id = if (isPositiveInteger(fragment))
            fragment
        else
            url.queryParameter("id")
        return if (isPositiveInteger(id)) id else null
    }

    private fun isPositiveInteger(value: String?): Boolean {
        if (value.isNullOrEmpty()) {
            return false
        }
        for (character in value) {
            if (!Character.isDigit(character)) {
                return false
            }
        }
        try {
            return value.toInt() > 0
        } catch (ignored: NumberFormatException) {
            return false
        }
    }

    private fun loadHackerNewsItem(
        context: Context?,
        pageUrl: String,
        itemId: String,
        fallbackTitle: String?,
        callback: Callback
    ): SummaryRequest {
        val request = HttpRequest.Builder()
            .url(HACKER_NEWS_ITEM_API + itemId + ".json")
            .header("Accept", "application/json")
            .get()
            .build()
        val call = NetworkComponent.httpClientInstance.newCall(request)
        call.enqueue(object : HttpCallback {
            override fun onFailure(call: HttpCall, e: IOException) {
                if (!call.isCanceled()) {
                    LinkSummaryLoader.postFailure(callback, getFailureMessage(e))
                }
            }

            override fun onResponse(call: HttpCall, response: HttpResponse) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful) {
                            postFailure(
                                callback,
                                "Hacker News returned HTTP " + closeableResponse.code
                            )
                            return
                        }
                        if (closeableResponse.body == null) {
                            postFailure(callback, "Hacker News did not return this item")
                            return
                        }

                        val result = extractHackerNewsItem(
                            readBoundedBody(closeableResponse.body),
                            pageUrl,
                            fallbackTitle
                        )
                        if (result == null) {
                            postFailure(callback, "Hacker News did not return this item")
                            return
                        }
                        StoryPreviewImageLoader.saveCachedLinkSummary(context, pageUrl, result)
                        MAIN_HANDLER.post(Runnable { callback.onSuccess(result) })
                    }
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        LinkSummaryLoader.postFailure(callback, getFailureMessage(e))
                    }
                }
            }
        })
        return SummaryRequest { call.cancel() }
    }

    private fun extractHackerNewsItem(
        json: String?,
        pageUrl: String,
        fallbackTitle: String?
    ): Result? {
        if (TextUtils.isEmpty(json) || "null" == json!!.trim { it <= ' ' }) {
            return null
        }

        try {
            val item = JSONObject(json)
            if (item.optBoolean("deleted") || item.optBoolean("dead")) {
                return null
            }

            val type = item.optString("type", "")
            val comment = "comment" == type
            val author = clean(item.optString("by", ""))
            val title = if (comment)
                "Comment by " + firstNonEmpty(author, "unknown")
            else
                firstNonEmpty(clean(item.optString("title", "")), fallbackTitle)
            if (TextUtils.isEmpty(title)) {
                return null
            }

            val metadata = buildHackerNewsMetadata(item, comment, author)
            val body = cleanHackerNewsText(item.optString("text", ""))
            val description = if (TextUtils.isEmpty(body))
                metadata
            else
                if (TextUtils.isEmpty(metadata)) body else metadata + " — " + body

            return Result(
                title,
                if (comment) HACKER_NEWS_COMMENT_SITE_NAME else HACKER_NEWS_STORY_SITE_NAME,
                author,
                if (item.optInt("time", 0) > 0)
                    Utils.getTimeAgo(item.optInt("time").toLong())
                else
                    "",
                "en",
                HACKER_NEWS_ITEM_CONTENT_TYPE,
                truncate(description, MAX_DESCRIPTION_CHARS),
                "",
                pageUrl
            )
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun buildHackerNewsMetadata(
        item: JSONObject,
        comment: Boolean,
        author: String
    ): String? {
        val parts: MutableList<String?> = ArrayList<String?>()
        if (!comment && item.has("score")) {
            val score = item.optInt("score", 0)
            parts.add(formatCount(score, "point", "points"))
        }
        if (!TextUtils.isEmpty(author)) {
            parts.add("by " + author)
        }
        val time = item.optInt("time", 0)
        if (time > 0) {
            parts.add(Utils.getTimeAgo(time.toLong()))
        }
        if (comment) {
            val replies = if (item.optJSONArray("kids") == null)
                0
            else
                item.optJSONArray("kids").length()
            if (replies > 0) {
                parts.add(formatCount(replies, "reply", "replies"))
            }
        } else if (item.has("descendants")) {
            val comments = item.optInt("descendants", 0)
            parts.add(formatCount(comments, "comment", "comments"))
        }
        return TextUtils.join(" · ", parts)
    }

    private fun formatCount(count: Int, singular: String, plural: String): String {
        return count.toString() + " " + (if (count == 1) singular else plural)
    }

    private fun cleanHackerNewsText(html: String?): String {
        if (TextUtils.isEmpty(html)) {
            return ""
        }
        return clean(Ksoup.parseBodyFragment(html.orEmpty()).body().text())
    }

    private fun loadWikipediaSummary(
        context: Context?,
        pageUrl: String,
        fallbackTitle: String?,
        callback: Callback
    ): SummaryRequest {
        if (context == null) {
            postFailure(callback, "Wikipedia summary unavailable")
            return SummaryRequest {}
        }

        val request = getInfo(
            pageUrl,
            context,
            object : WikipediaGetter.GetterCallback {
                override fun onSuccess(wikipediaInfo: WikipediaInfo) {
                    val firstParagraph = getFirstParagraphText(
                        wikipediaInfo.summary
                    )
                    if (TextUtils.isEmpty(firstParagraph)) {
                        callback.onFailure("Wikipedia did not return a summary")
                        return
                    }

                    val result = Result(
                        clean(fallbackTitle),
                        "Wikipedia",
                        "",
                        "",
                        "en",
                        "application/json",
                        firstParagraph,
                        "",
                        pageUrl
                    )
                    StoryPreviewImageLoader.saveCachedLinkSummary(context, pageUrl, result)
                    callback.onSuccess(result)
                }

                override fun onFailure(reason: String) {
                    callback.onFailure(reason)
                }
            })
        return if (request == null) SummaryRequest {} else SummaryRequest { request.cancel() }
    }

    fun buildYoutubeOEmbedUrl(pageUrl: String?): String? {
        if (!isYoutubeVideoUrl(pageUrl)) {
            return null
        }

        val endpoint = YOUTUBE_OEMBED_ENDPOINT.toNetworkUrlOrNull()
        if (endpoint == null) {
            return null
        }

        return endpoint.newBuilder()
            .addQueryParameter("url", pageUrl)
            .addQueryParameter("format", "json")
            .build()
            .toString()
    }

    fun isYoutubeVideoUrl(url: String?): Boolean {
        return !TextUtils.isEmpty(url) && YOUTUBE_VIDEO_URL_PATTERN.matcher(url).matches()
    }

    fun buildRedditOEmbedUrl(pageUrl: String?): String? {
        if (!isRedditPostUrl(pageUrl)) {
            return null
        }

        val endpoint = REDDIT_OEMBED_ENDPOINT.toNetworkUrlOrNull()
        if (endpoint == null) {
            return null
        }

        return endpoint.newBuilder()
            .addQueryParameter("url", pageUrl)
            .addQueryParameter("format", "json")
            .build()
            .toString()
    }

    fun isRedditPostUrl(url: String?): Boolean {
        if (TextUtils.isEmpty(url)) {
            return false
        }

        val parsedUrl = url!!.toNetworkUrlOrNull()
        if (parsedUrl == null || !isHttpScheme(parsedUrl)) {
            return false
        }

        val host = parsedUrl.host.lowercase()
        if ("redd.it" == host || "www.redd.it" == host) {
            return parsedUrl.pathSize > 0 && !TextUtils.isEmpty(parsedUrl.pathSegments.get(0))
        }
        if (!("reddit.com" == host || host.endsWith(".reddit.com"))) {
            return false
        }

        val segments: List<String> = parsedUrl.pathSegments
        for (i in segments.indices) {
            if ("comments".equals(
                    segments.get(i),
                    ignoreCase = true
                ) && i + 1 < segments.size && !TextUtils.isEmpty(segments.get(i + 1))
            ) {
                return true
            }
        }
        return false
    }

    fun extractYoutubeOEmbedSummary(json: String?, pageUrl: String?): Result? {
        return extractOEmbedSummary(json, pageUrl, "YouTube")
    }

    private fun extractOEmbedSummary(
        json: String?,
        pageUrl: String?,
        fallbackProviderName: String
    ): Result? {
        if (TextUtils.isEmpty(json)) {
            return null
        }

        try {
            val jsonObject = JSONObject(json)
            val imageUrl = normalizeHttpUrl(jsonObject.optString("thumbnail_url", null))
            return Result(
                jsonObject.optString("title", ""),
                jsonObject.optString("provider_name", fallbackProviderName),
                jsonObject.optString("author_name", ""),
                "",
                "",
                "application/json",
                "",
                if (imageUrl == null) "" else imageUrl,
                pageUrl
            )
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(IOException::class)
    fun readBoundedBody(body: HttpResponseBody): String {
        val contentLength = body.contentLength()
        if (contentLength > MAX_RESPONSE_BYTES) {
            throw IOException("The page is too large to preview")
        }

        val initialCapacity =
            if (contentLength > 0) min(contentLength, MAX_RESPONSE_BYTES.toLong()).toInt() else
                8192
        ByteArrayOutputStream(initialCapacity).use { output ->
            val source = body.source()
            val buffer = ByteArray(8192)
            var totalBytes = 0
            while (true) {
                val bytesRead = source.read(buffer)
                if (bytesRead == -1) break
                totalBytes += bytesRead
                if (totalBytes > MAX_RESPONSE_BYTES) {
                    throw IOException("The page is too large to preview")
                }
                output.write(buffer, 0, bytesRead)
            }

            val charset = body.contentType()?.charset(StandardCharsets.UTF_8)
                ?: StandardCharsets.UTF_8
            return String(output.toByteArray(), charset)
        }
    }

    fun extract(
        html: String,
        fallbackTitle: String?,
        contentType: String?,
        finalUrl: String
    ): Result {
        val document = Ksoup.parse(html, baseUri = finalUrl)
        val title = firstNonEmpty(
            metaContent(document, "meta[property=og:title]"),
            metaContent(document, "meta[name=twitter:title]"),
            document.title(),
            fallbackTitle
        )
        val siteName = firstNonEmpty(
            metaContent(document, "meta[property=og:site_name]"),
            getHost(finalUrl)
        )
        val author = firstNonEmpty(
            metaContent(document, "meta[name=author]"),
            metaContent(document, "meta[property=article:author]"),
            metaContent(document, "meta[name=byl]"),
            elementText(document.selectFirst("[rel=author]"))
        )
        val publishedTime = firstNonEmpty(
            metaContent(document, "meta[property=article:published_time]"),
            metaContent(document, "meta[itemprop=datePublished]"),
            metaContent(document, "meta[name=date]"),
            elementAttribute(document.selectFirst("time[datetime]"), "datetime")
        )
        val language = firstNonEmpty(
            elementAttribute(document.selectFirst("html[lang]"), "lang"),
            metaContent(document, "meta[http-equiv=content-language]")
        )
        val metadataDescription = firstNonEmpty(
            metaContent(document, "meta[property=og:description]"),
            metaContent(document, "meta[name=description]"),
            metaContent(document, "meta[name=twitter:description]")
        )
        val description = truncate(
            chooseDescription(
                metadataDescription,
                document,
                title,
                fallbackTitle
            ), MAX_DESCRIPTION_CHARS
        )
        val imageUrl = extractImageUrl(document, finalUrl)

        return Result(
            clean(title),
            clean(siteName),
            clean(author),
            clean(publishedTime),
            clean(language),
            clean(contentType),
            clean(description),
            imageUrl,
            finalUrl
        )
    }

    private fun extractImageUrl(document: Document, baseUrl: String): String {
        for (selector in IMAGE_SELECTORS) {
            val element = document.selectFirst(selector)
            if (element == null) {
                continue
            }
            val attribute = if ("link" == element.tagName()) "href" else "content"
            val candidate = element.attr(attribute)
            if (TextUtils.isEmpty(candidate) || candidate.trim { it <= ' ' }.startsWith("data:")) {
                continue
            }
            val parsedBase = baseUrl.toNetworkUrlOrNull()
            val parsedImage = if (parsedBase == null)
                candidate.trim { it <= ' ' }.toNetworkUrlOrNull()
            else
                parsedBase.resolve(candidate.trim { it <= ' ' })
            if (parsedImage != null && isHttpScheme(parsedImage)) {
                return parsedImage.toString()
            }
        }
        return ""
    }

    private fun metaContent(document: Document, selector: String): String {
        return elementAttribute(document.selectFirst(selector), "content")
    }

    private fun elementAttribute(element: Element?, attribute: String): String {
        return if (element == null) "" else element.attr(attribute)
    }

    private fun elementText(element: Element?): String {
        return if (element == null) "" else element.text()
    }

    private fun firstNonEmpty(vararg values: String?): String? {
        for (value in values) {
            if (!TextUtils.isEmpty(clean(value))) {
                return value
            }
        }
        return ""
    }

    private fun clean(value: String?): String {
        return if (value == null) "" else value.replace('\u00a0', ' ')
            .replace("\\s+".toRegex(), " ").trim { it <= ' ' }
    }

    private fun truncate(value: String?, maxChars: Int): String {
        val cleaned = clean(value)
        if (cleaned.length <= maxChars) {
            return cleaned
        }
        val lastSpace = cleaned.lastIndexOf(' ', maxChars - 1)
        val end = if (lastSpace >= maxChars * 0.75f) lastSpace else maxChars
        return cleaned.substring(0, end).trim { it <= ' ' } + "…"
    }

    private fun normalizeContentType(contentType: String?): String {
        if (TextUtils.isEmpty(contentType)) {
            return ""
        }
        val normalizedContentType = contentType ?: return ""
        val separator = normalizedContentType.indexOf(';')
        return (if (separator >= 0) normalizedContentType.substring(
            0,
            separator
        ) else normalizedContentType).trim { it <= ' ' }
    }

    private fun getHost(url: String): String {
        val parsedUrl = url.toNetworkUrlOrNull()
        return if (parsedUrl == null) "" else parsedUrl.host
    }

    private fun normalizeHttpUrl(url: String?): String? {
        if (TextUtils.isEmpty(url)) {
            return null
        }

        val parsedUrl = url?.toNetworkUrlOrNull()
        return if (parsedUrl == null || !isHttpScheme(parsedUrl)) null else parsedUrl.toString()
    }

    private fun isHttpScheme(url: NetworkUrl): Boolean {
        return "http" == url.scheme || "https" == url.scheme
    }

    private fun getFailureMessage(error: Exception): String =
        error.message?.takeUnless { it.isEmpty() } ?: "The page could not be read"

    private fun postFailure(callback: Callback, message: String) {
        MAIN_HANDLER.post(Runnable { callback.onFailure(message) })
    }

    interface Callback {
        fun onSuccess(result: Result)

        fun onFailure(message: String)
    }

    fun interface SummaryRequest {
        fun cancel()
    }

    class Result internal constructor(
        val title: String?,
        val siteName: String?,
        val author: String?,
        val publishedTime: String?,
        val language: String?,
        val contentType: String?,
        val description: String?,
        val imageUrl: String?,
        val finalUrl: String?
    )
}

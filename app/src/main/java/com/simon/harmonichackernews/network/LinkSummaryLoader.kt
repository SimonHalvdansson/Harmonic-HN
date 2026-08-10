package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.min

/** Android cache/callback adapter for the shared link-summary repository and parser. */
object LinkSummaryLoader {
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(
        context: Context?,
        pageUrl: String,
        fallbackTitle: String?,
        callback: Callback,
    ): SummaryRequest {
        val normalizedUrl = LinkSummaryParser.normalizeHttpUrl(pageUrl)
        if (normalizedUrl == null) {
            postFailure(callback, "This link does not use HTTP or HTTPS")
            return SummaryRequest {}
        }

        val hackerNewsRequest = LinkSummaryParser.hackerNewsItemId(normalizedUrl) != null
        val wikipediaRequest = LinkPreviewUrls.isWikipediaUrl(normalizedUrl)
        val oEmbedRequest = LinkSummaryParser.buildYoutubeOEmbedUrl(normalizedUrl) != null ||
            LinkSummaryParser.buildRedditOEmbedUrl(normalizedUrl) != null
        val cached = StoryPreviewImageLoader.getCachedLinkSummary(context, normalizedUrl)
        if (
            cached != null &&
            (!hackerNewsRequest || isHackerNewsItemResult(cached)) &&
            (!oEmbedRequest || cached.contentType == "application/json") &&
            (!wikipediaRequest || cached.contentType == "application/json")
        ) {
            mainHandler.post { callback.onSuccess(cached) }
            return SummaryRequest {}
        }

        val job = NetworkComponent.launchCallbackRequest(
            request = { NetworkComponent.linkSummaryRepository.load(normalizedUrl, fallbackTitle) },
            onSuccess = { summary ->
                val result = fromShared(summary)
                StoryPreviewImageLoader.saveCachedLinkSummary(context, normalizedUrl, result)
                callback.onSuccess(result)
            },
            onFailure = { callback.onFailure(it.message ?: "The page could not be read") },
        )
        return SummaryRequest { job.cancel() }
    }

    fun isHackerNewsItemResult(result: Result?): Boolean =
        result?.contentType == LinkSummaryParser.HACKER_NEWS_ITEM_CONTENT_TYPE

    fun buildYoutubeOEmbedUrl(pageUrl: String?): String? =
        LinkSummaryParser.buildYoutubeOEmbedUrl(pageUrl)

    fun isYoutubeVideoUrl(url: String?): Boolean = LinkSummaryParser.isYoutubeVideoUrl(url)

    fun buildRedditOEmbedUrl(pageUrl: String?): String? =
        LinkSummaryParser.buildRedditOEmbedUrl(pageUrl)

    fun isRedditPostUrl(url: String?): Boolean = LinkSummaryParser.isRedditPostUrl(url)

    fun extractYoutubeOEmbedSummary(json: String?, pageUrl: String?): Result? =
        LinkSummaryParser.extractOEmbed(json, pageUrl, "YouTube")?.let(::fromShared)

    fun extract(
        html: String,
        fallbackTitle: String?,
        contentType: String?,
        finalUrl: String,
    ): Result = LinkSummaryParser.extract(
        html,
        fallbackTitle,
        contentType,
        finalUrl,
    ).let(::fromShared)

    @Throws(IOException::class)
    fun readBoundedBody(body: HttpResponseBody): String {
        val contentLength = body.contentLength()
        if (contentLength > MAX_RESPONSE_BYTES) {
            throw IOException("The page is too large to preview")
        }
        val initialCapacity = if (contentLength > 0) {
            min(contentLength, MAX_RESPONSE_BYTES.toLong()).toInt()
        } else {
            8192
        }
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

    private fun postFailure(callback: Callback, message: String) {
        mainHandler.post { callback.onFailure(message) }
    }

    internal fun fromShared(summary: LinkSummary): Result = Result(
        summary.title,
        summary.siteName,
        summary.author,
        summary.publishedTime,
        summary.language,
        summary.contentType,
        summary.description,
        summary.imageUrl,
        summary.finalUrl,
    )

    internal fun toShared(result: Result): LinkSummary = LinkSummary(
        title = result.title.orEmpty(),
        siteName = result.siteName.orEmpty(),
        author = result.author.orEmpty(),
        publishedTime = result.publishedTime.orEmpty(),
        language = result.language.orEmpty(),
        contentType = result.contentType.orEmpty(),
        description = result.description.orEmpty(),
        imageUrl = result.imageUrl.orEmpty(),
        finalUrl = result.finalUrl.orEmpty(),
    )

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
        val finalUrl: String?,
    )
}
